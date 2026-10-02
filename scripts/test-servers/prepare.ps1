[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Profile,
    [AllowNull()][string] $Artifact,
    [Alias("TestLabRoot")][AllowNull()][string] $InstanceHarborRoot,
    [AllowNull()][string] $Instance,
    [switch] $AcceptEula,
    [switch] $Replace
)

. (Join-Path $PSScriptRoot "common.ps1")
$matrixProfile = Get-InviteCodeTestProfile -Id $Profile
$lab = Resolve-PluginInstanceHarborRoot -ExplicitRoot $InstanceHarborRoot
$jar = Resolve-InviteCodePluginArtifact -ExplicitArtifact $Artifact
$instanceName = if ([string]::IsNullOrWhiteSpace($Instance)) { "invitecode-$Profile" } else { $Instance }

& (Join-Path $lab "scripts\prepare-instance.ps1") `
    -Project "InviteCode" -Audience Agent `
    -Instance $instanceName `
    -Profile $Profile `
    -PluginJar $jar `
    -Port $matrixProfile.port `
    -AcceptEula:$AcceptEula `
    -Replace:$Replace
if ($LASTEXITCODE -ne 0) {
    throw "InstanceHarbor prepare-instance 失敗，結束碼為 $LASTEXITCODE。"
}
