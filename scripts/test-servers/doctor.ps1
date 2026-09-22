[CmdletBinding()]
param([AllowNull()][string] $TestLabRoot)

. (Join-Path $PSScriptRoot "common.ps1")
$lab = Resolve-PluginTestLabRoot -ExplicitRoot $TestLabRoot
$matrix = Get-InviteCodeTestMatrix
Import-Module (Join-Path $lab "scripts\TestLab.psm1") -Force
$labProfiles = @(Get-TestLabProfiles)

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
        TestLab = $labProfile.Count -eq 1
        Java = $javaAvailable
        Runtime = $javaDetail
    }
}

Write-Host "TestLab：$lab"
$rows | Format-Table -AutoSize
if (@($rows | Where-Object { -not $_.TestLab -or -not $_.Java }).Count -gt 0) {
    throw "InviteCode 測試矩陣仍有缺少的 TestLab profile 或 Java runtime。"
}
