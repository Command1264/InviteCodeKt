[CmdletBinding()]
param([Alias("TestLabRoot")][AllowNull()][string] $InstanceHarborRoot)

. (Join-Path $PSScriptRoot "common.ps1")
$lab = Resolve-PluginInstanceHarborRoot -ExplicitRoot $InstanceHarborRoot
$matrix = Get-InviteCodeTestMatrix
Import-Module (Join-Path $lab "scripts\InstanceHarbor.psm1") -Force
$labProfiles = @(Get-InstanceHarborProfiles)

$rows = foreach ($profile in $matrix.profiles) {
    $labProfile = @($labProfiles | Where-Object id -CEQ $profile.id)
    $javaAvailable = $false
    $javaDetail = "profile missing"
    if ($labProfile.Count -eq 1) {
        $output = (& mise exec "java@$($labProfile[0].javaAlias)" -- java -version 2>&1 | Out-String).Trim()
        $javaAvailable = $LASTEXITCODE -eq 0
        $javaDetail = ($output -split "`r?`n" | Select-Object -First 1)
    }
    [pscustomobject]@{
        Profile = $profile.id
        Purpose = $profile.purpose
        Required = $profile.requiredForRelease
        InstanceHarbor = $labProfile.Count -eq 1
        Java = $javaAvailable
        Runtime = $javaDetail
    }
}

Write-Host "InstanceHarbor：$lab"
$rows | Format-Table -AutoSize
if (@($rows | Where-Object { -not $_.InstanceHarbor -or -not $_.Java }).Count -gt 0) {
    throw "InviteCode 測試矩陣仍有缺少的 InstanceHarbor profile 或 Java runtime。"
}
