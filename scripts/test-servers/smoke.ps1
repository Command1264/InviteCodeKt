[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Profile,
    [Parameter(Mandatory = $true)][string] $Instance,
    [ValidateSet('SQLITE', 'JSON')][string] $Backend = 'SQLITE',
    [AllowNull()][string] $TestLabRoot,
    [ValidateRange(10, 600)][int] $TimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

# Only operate on an already prepared, isolated TestLab instance. EULA acceptance
# and replacement remain explicit prepare-instance operations.
if ($Instance -cnotmatch '^[a-zA-Z0-9][a-zA-Z0-9-]{0,79}$') {
    throw "無效的 TestLab instance 名稱：$Instance"
}
[void] (Get-InviteCodeTestProfile -Id $Profile)
$lab = Resolve-PluginTestLabRoot -ExplicitRoot $TestLabRoot
Import-Module (Join-Path $lab 'scripts\TestLab.psm1') -Force
$instancePath = Get-TestLabInstancePath -Project 'InviteCode' -Instance $Instance
$metadataPath = Join-Path $instancePath 'workspace.json'
if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    throw "找不到 TestLab instance：$metadataPath"
}
$metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($metadata.project -cne 'InviteCode' -or $metadata.instance -cne $Instance -or
    $metadata.profile -cne $Profile -or -not $metadata.eulaAccepted) {
    throw "TestLab metadata 不符，或尚未記錄 EULA 接受狀態：$Instance"
}
$pluginPath = Join-Path (Join-Path $instancePath 'plugins') $metadata.pluginFile
$serverPath = Join-Path $instancePath 'server.jar'
if (-not (Test-Path -LiteralPath $serverPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $pluginPath -PathType Leaf)) {
    throw "TestLab instance 缺少 server.jar 或插件 JAR：$Instance"
}
$pluginSha = (Get-FileHash -LiteralPath $pluginPath -Algorithm SHA256).Hash
if ($pluginSha -cne $metadata.pluginSha256) {
    throw "插件 JAR 雜湊與 workspace.json 不符：$Instance"
}
if ((Get-FileHash -LiteralPath $serverPath -Algorithm SHA256).Hash -cne $metadata.serverSha256) {
    throw "伺服器 JAR 雜湊與 workspace.json 不符：$Instance"
}
$logPath = Join-Path $instancePath 'logs\latest.log'
$dataPath = Join-Path $instancePath 'plugins\InviteCode'
$storagePath = Join-Path $dataPath 'storage.yml'
if (Test-Path -LiteralPath $storagePath -PathType Leaf) {
    $configured = Get-Content -LiteralPath $storagePath -Raw -Encoding UTF8
    if ($configured -notmatch "(?m)^backend:\s*$Backend\s*$") {
        throw "storage.yml 的 backend 與預期 $Backend 不符：$Instance"
    }
}
elseif ($Backend -cne 'SQLITE') {
    throw "非預設 backend 需要先建立 storage.yml：$Instance"
}
$mise = (Get-Command mise -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $mise
$startInfo.WorkingDirectory = $instancePath
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
foreach ($argument in @('exec', "java@$($metadata.javaAlias)", '--', 'java',
    '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
    '-Xms512M', '-Xmx1G', '-jar', 'server.jar', 'nogui')) {
    [void] $startInfo.ArgumentList.Add($argument)
}
$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$started = $false
$doctorSent = $false
$stopped = $false
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
function Read-LiveLog {
    param([Parameter(Mandatory = $true)][string] $Path)
    $stream = [System.IO.FileStream]::new($Path, [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $reader = [System.IO.StreamReader]::new($stream, $utf8Strict, $true)
        return $reader.ReadToEnd()
    }
    finally { $stream.Dispose() }
}
try {
    $runStartedAt = [DateTime]::UtcNow
    if (-not $process.Start()) { throw "無法啟動 TestLab instance：$Instance" }
    $started = $true
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($process.HasExited) { break }
        Start-Sleep -Milliseconds 500
        if (-not (Test-Path -LiteralPath $logPath -PathType Leaf)) { continue }
        if ((Get-Item -LiteralPath $logPath).LastWriteTimeUtc -lt $runStartedAt) { continue }
        try { $log = Read-LiveLog -Path $logPath }
        catch [System.Text.DecoderFallbackException] {
            # The writer may be in the middle of one UTF-8 character. The final
            # read after a clean shutdown still enforces strict UTF-8.
            continue
        }
        if ($log -match "Could not load plugin 'InviteCode.*not marked as supporting Folia") {
            throw "Folia 拒絕載入尚未宣告支援的 InviteCode：$Instance"
        }
        if (-not $doctorSent -and $log -match 'Done \(' -and
            $log -match '\[InviteCode\].*已啟用') {
            $process.StandardInput.WriteLine('ic admin doctor')
            $process.StandardInput.Flush()
            $doctorSent = $true
        }
        if ($doctorSent -and $log -match 'Runtime=ACTIVE' -and
            $log -match 'Integration：' -and $log -match "$Backend 儲存版本=") {
            $process.StandardInput.WriteLine('stop')
            $process.StandardInput.Flush()
            $stopped = $true
            break
        }
    }
    if (-not $stopped) {
        throw "Smoke 未在 $TimeoutSeconds 秒內取得 ACTIVE doctor 結果：$Instance"
    }
    if (-not $process.WaitForExit(30000)) {
        throw "伺服器未在 stop 後 30 秒內結束：$Instance"
    }
    if ($process.ExitCode -ne 0) {
        throw "伺服器結束碼為 $($process.ExitCode)：$Instance"
    }
    $log = Read-LiveLog -Path $logPath
    if ($log -match '(?i)NoSuchMethodError|NoSuchFieldError|Could not load.*InviteCode|Could not pass event.*InviteCode|Error occurred while (enabling|disabling) InviteCode') {
        throw "伺服器 log 含 InviteCode linkage 或事件錯誤：$Instance"
    }
    $keyPath = Join-Path $dataPath 'anti-abuse.key'
    $databasePath = Join-Path $dataPath $(if ($Backend -ceq 'JSON') { 'invitecode.json' } else { 'invitecode.db' })
    if (-not (Test-Path -LiteralPath $keyPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $databasePath -PathType Leaf)) {
        throw "$Backend 或 anti-abuse key 尚未建立：$Instance"
    }
    [pscustomobject]@{
        profile = $Profile
        instance = $Instance
        backend = $Backend
        pluginSha256 = $pluginSha
        serverSha256 = $metadata.serverSha256
        javaAlias = $metadata.javaAlias
        doctorActive = $true
        storageSha256 = (Get-FileHash -LiteralPath $databasePath -Algorithm SHA256).Hash
        keySha256 = (Get-FileHash -LiteralPath $keyPath -Algorithm SHA256).Hash
        logPath = $logPath
    } | ConvertTo-Json -Compress
}
finally {
    if ($started -and -not $process.HasExited) {
        try {
            $process.StandardInput.WriteLine('stop')
            $process.StandardInput.Flush()
            if (-not $process.WaitForExit(30000)) {
                Write-Warning "測試伺服器未接受 stop，將終止本 runner 建立的程序樹：$Instance"
                $process.Kill($true)
                [void] $process.WaitForExit(10000)
            }
        }
        catch {
            if (-not $process.HasExited) { $process.Kill($true) }
        }
    }
    $process.Dispose()
}
