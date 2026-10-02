. (Join-Path $PSScriptRoot 'common.ps1')
$root = Join-Path ([IO.Path]::GetTempPath()) ('invitecode-boundary-' + [guid]::NewGuid().ToString('N'))
try {
    $instance = Join-Path $root 'Agent/InviteCode/issue12-boundary'
    New-Item -ItemType Directory -Path $instance -Force | Out-Null
    $metadata = [pscustomobject]@{ schemaVersion = 2; audience = 'Agent'; project = 'InviteCode'; instance = 'issue12-boundary'; profile = 'spigot-1.8.8'; eulaAccepted = $true; pluginFile = 'InviteCode.jar' }
    Assert-InviteCodeAgentInstance -Path $instance -InstancesRoot $root -Metadata $metadata -Profile 'spigot-1.8.8'
    foreach ($audience in @('Human', 'Legacy')) {
        $metadata.audience = $audience
        $rejected = $false
        try { Assert-InviteCodeAgentInstance -Path $instance -InstancesRoot $root -Metadata $metadata -Profile 'spigot-1.8.8' }
        catch { $rejected = $true }
        if (-not $rejected) { throw "未拒絕 $audience metadata。" }
    }
    $metadata.audience = 'Agent'
    $metadata.pluginFile = '../outside.jar'
    $rejected = $false
    try { Assert-InviteCodeAgentInstance -Path $instance -InstancesRoot $root -Metadata $metadata -Profile 'spigot-1.8.8' }
    catch { $rejected = $true }
    if (-not $rejected) { throw '未拒絕 pluginFile 路徑跳脫。' }
    $metadata.pluginFile = 'InviteCode.jar'
    $human = Join-Path $root 'Human/InviteCode/issue12-boundary'
    New-Item -ItemType Directory -Path $human -Force | Out-Null
    $rejected = $false
    try { Assert-InviteCodeAgentInstance -Path $human -InstancesRoot $root -Metadata $metadata -Profile 'spigot-1.8.8' }
    catch { $rejected = $true }
    if (-not $rejected) { throw '未拒絕 Human 路徑。' }
    $metadata.schemaVersion = 1
    $rejected = $false
    try { Assert-InviteCodeAgentInstance -Path $instance -InstancesRoot $root -Metadata $metadata -Profile 'spigot-1.8.8' }
    catch { $rejected = $true }
    if (-not $rejected) { throw '未拒絕舊 metadata schema。' }
    $metadata.schemaVersion = 2
    $linked = Join-Path $root 'Agent/InviteCode/issue12-linked'
    New-Item -ItemType Junction -Path $linked -Target $human -ErrorAction Stop | Out-Null
    $metadata.instance = 'issue12-linked'
    $rejected = $false
    try { Assert-InviteCodeAgentInstance -Path $linked -InstancesRoot $root -Metadata $metadata -Profile 'spigot-1.8.8' }
    catch { $rejected = $true }
    if (-not $rejected) { throw '未拒絕指向 Human 的 junction。' }
    $rejected = $false
    try { Resolve-PluginInstanceHarborRoot -ExplicitRoot $root | Out-Null }
    catch { $rejected = $true }
    if (-not $rejected) { throw '指定錯誤 repository 時不應靜默改用另一份。' }
    Write-Output 'PASS: Agent path and metadata boundaries'
}
finally {
    if ($root.StartsWith([IO.Path]::GetTempPath(), [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $root -Recurse -Force
    }
}
