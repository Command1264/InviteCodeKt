[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Profile,
    [AllowNull()][string] $TestLabRoot,
    [AllowNull()][string] $Instance
)

. (Join-Path $PSScriptRoot "common.ps1")
[void] (Get-InviteCodeTestProfile -Id $Profile)
$lab = Resolve-PluginTestLabRoot -ExplicitRoot $TestLabRoot
$instanceName = if ([string]::IsNullOrWhiteSpace($Instance)) { "invitecode-$Profile" } else { $Instance }

& (Join-Path $lab "scripts\start-instance.ps1") -Project "InviteCode" -Instance $instanceName
if ($LASTEXITCODE -ne 0) {
    throw "TestLab start-instance 失敗，結束碼為 $LASTEXITCODE。"
}
