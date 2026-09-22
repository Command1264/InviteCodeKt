Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

$matrix = Get-InviteCodeTestMatrix
if (@($matrix.profiles).Count -ne 8) {
    throw "InviteCode test matrix 預期為 8 個 profile。"
}
$ports = @($matrix.profiles | ForEach-Object { [int] $_.port })
if (@($ports | Group-Object | Where-Object Count -gt 1).Count -gt 0) {
    throw "InviteCode test matrix 包含重複 port。"
}
if (@($matrix.profiles | Where-Object { $_.id -ceq "folia-latest" -and $_.requiredForRelease }).Count -ne 0) {
    throw "Folia 應維持實驗性，不得標記為必要發行 gate。"
}

Write-Host "InviteCode test-server matrix 驗證通過：8 profiles，port 無衝突。"
