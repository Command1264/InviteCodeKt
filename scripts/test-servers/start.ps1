[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Profile,
    [Alias("TestLabRoot")][AllowNull()][string] $InstanceHarborRoot,
    [AllowNull()][string] $Instance
)

. (Join-Path $PSScriptRoot "common.ps1")
[void] (Get-InviteCodeTestProfile -Id $Profile)
$lab = Resolve-PluginInstanceHarborRoot -ExplicitRoot $InstanceHarborRoot
$instanceName = if ([string]::IsNullOrWhiteSpace($Instance)) { "invitecode-$Profile" } else { $Instance }

& (Join-Path $lab "scripts\start-instance.ps1") -Project "InviteCode" -Audience Agent -Instance $instanceName
if ($LASTEXITCODE -ne 0) {
    throw "InstanceHarbor start-instance 失敗，結束碼為 $LASTEXITCODE。"
}
