[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Profile,
    [Parameter(Mandatory = $true)][string] $Instance,
    [ValidateSet('SQLITE', 'JSON', 'MYSQL')][string] $Backend = 'SQLITE',
    [switch] $ExpectPlaceholderApi,
    [switch] $ExpectVaultEconomy,
    [Alias("TestLabRoot")][AllowNull()][string] $InstanceHarborRoot,
    [ValidateRange(10, 600)][int] $TimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

# Only operate on an already prepared, isolated InstanceHarbor instance. EULA acceptance
# and replacement remain explicit prepare-instance operations.
if ($Instance -cnotmatch '^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$') {
    throw "無效的 InstanceHarbor instance 名稱：$Instance"
}
[void] (Get-InviteCodeTestProfile -Id $Profile)
$lab = Resolve-PluginInstanceHarborRoot -ExplicitRoot $InstanceHarborRoot
Import-Module (Join-Path $lab 'scripts\InstanceHarbor.psm1') -Force
$instancePath = Get-InstanceHarborInstancePath -Project 'InviteCode' -Audience Agent -Instance $Instance
$metadataPath = Join-Path $instancePath 'workspace.json'
if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    throw "找不到 InstanceHarbor instance：$metadataPath"
}
$metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-InviteCodeAgentInstance -Path $instancePath -InstancesRoot (Get-InstanceHarborContext).InstancesRoot -Metadata $metadata -Profile $Profile
if ($metadata.project -cne 'InviteCode' -or $metadata.instance -cne $Instance -or
    $metadata.profile -cne $Profile -or -not $metadata.eulaAccepted) {
    throw "InstanceHarbor metadata 不符，或尚未記錄 EULA 接受狀態：$Instance"
}
$pluginPath = Join-Path (Join-Path $instancePath 'plugins') $metadata.pluginFile
$serverPath = Join-Path $instancePath 'server.jar'
$properties = Get-Content -LiteralPath (Join-Path $instancePath 'server.properties') -Raw -Encoding UTF8
if ($properties -notmatch '(?m)^server-ip=127\.0\.0\.1\s*$' -or
    $properties -notmatch '(?m)^online-mode=false\s*$' -or
    $properties -notmatch "(?m)^server-port=$($metadata.port)\s*$" -or
    @(Get-NetTCPConnection -LocalPort $metadata.port -State Listen -ErrorAction SilentlyContinue).Count -gt 0) {
    throw 'Smoke 需要 loopback、offline mode、正確且未被監聽的 port。'
}
if (-not (Test-Path -LiteralPath $serverPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $pluginPath -PathType Leaf)) {
    throw "InstanceHarbor instance 缺少 server.jar 或插件 JAR：$Instance"
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
if ($Backend -ceq 'MYSQL' -and
    ([string]::IsNullOrEmpty($env:INVITECODE_DB_PASSWORD) -or
     [string]::IsNullOrEmpty($env:INVITECODE_NETWORK_SECRET))) {
    throw 'MYSQL smoke 需要先在測試程序環境提供資料庫密碼與 network secret。'
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
$commandsSent = $false
$stopped = $false
$placeholderExpected = $ExpectPlaceholderApi.IsPresent.ToString().ToLowerInvariant()
$vaultExpected = $ExpectVaultEconomy.IsPresent.ToString().ToLowerInvariant()
$integrationPattern = '(?:Integration：|Integrations: )PlaceholderAPI=(true|false)(?:，|, )Vault Economy=(true|false)'
$expectedIntegrationPattern = "(?:Integration：|Integrations: )PlaceholderAPI=$placeholderExpected(?:，|, )Vault Economy=$vaultExpected"
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
    if (-not $process.Start()) { throw "無法啟動 InstanceHarbor instance：$Instance" }
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
        if ($doctorSent -and
            $log -match $integrationPattern -and
            $log -notmatch $expectedIntegrationPattern) {
            throw "外部整合診斷與預期不符：PlaceholderAPI=$placeholderExpected，Vault Economy=$vaultExpected；$Instance"
        }
        if (-not $doctorSent -and $log -match 'Done \(' -and
            $log -match '\[InviteCode\].*已啟用') {
            $process.StandardInput.WriteLine('ic admin doctor')
            $process.StandardInput.Flush()
            $doctorSent = $true
        }
        if ($doctorSent -and $log -match 'Runtime=ACTIVE' -and
            $log -match $expectedIntegrationPattern -and
            $log -match "$Backend (?:儲存版本|storage version)=" -and
            -not $commandsSent) {
            $process.StandardInput.WriteLine('ic help')
            $process.StandardInput.WriteLine('ic reload')
            $process.StandardInput.Flush()
            $commandsSent = $true
        }
        if ($commandsSent -and $log -match '(?:InviteCode 可用指令：|Available InviteCode commands:)' -and
            $log -match '(?:InviteCode 設定已完整驗證並套用。|InviteCode validated and applied the complete configuration snapshot\.)') {
            $process.StandardInput.WriteLine('stop')
            $process.StandardInput.Flush()
            $stopped = $true
            break
        }
    }
    if (-not $stopped) {
        throw "Smoke 未在 $TimeoutSeconds 秒內取得 ACTIVE doctor、help 與 reload 結果：$Instance"
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
    $storageSha256 = $null
    $keySha256 = $null
    if ($Backend -ceq 'MYSQL') {
        if (Test-Path -LiteralPath $keyPath -PathType Leaf) {
            throw "MYSQL 模式不應在本機建立 anti-abuse key：$Instance"
        }
    }
    else {
        $databasePath = Join-Path $dataPath $(if ($Backend -ceq 'JSON') { 'invitecode.json' } else { 'invitecode.db' })
        if (-not (Test-Path -LiteralPath $keyPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $databasePath -PathType Leaf)) {
            throw "$Backend 或 anti-abuse key 尚未建立：$Instance"
        }
        $storageSha256 = (Get-FileHash -LiteralPath $databasePath -Algorithm SHA256).Hash
        $keySha256 = (Get-FileHash -LiteralPath $keyPath -Algorithm SHA256).Hash
    }
    [pscustomobject]@{
        profile = $Profile
        instance = $Instance
        backend = $Backend
        pluginSha256 = $pluginSha
        serverSha256 = $metadata.serverSha256
        javaAlias = $metadata.javaAlias
        doctorActive = $true
        placeholderApi = $ExpectPlaceholderApi.IsPresent
        vaultEconomy = $ExpectVaultEconomy.IsPresent
        consoleCommands = @('ic admin doctor', 'ic help', 'ic reload')
        storageSha256 = $storageSha256
        keySha256 = $keySha256
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
