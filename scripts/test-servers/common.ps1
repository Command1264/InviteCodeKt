Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-InviteCodeRepositoryRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
}

function Get-InviteCodeTestMatrix {
    $path = Join-Path (Get-InviteCodeRepositoryRoot) "test-servers\matrix.json"
    $document = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    if ($document.schemaVersion -ne 1 -or $document.project -cne "InviteCode") {
        throw "不支援的 InviteCode test-server matrix。"
    }
    $profiles = @($document.profiles)
    $duplicates = @($profiles | Group-Object id | Where-Object Count -gt 1)
    if ($profiles.Count -eq 0 -or $duplicates.Count -gt 0) {
        throw "InviteCode test-server matrix 為空或包含重複 profile。"
    }
    return $document
}

function Get-InviteCodeTestProfile {
    param([Parameter(Mandatory = $true)][string] $Id)
    $matches = @((Get-InviteCodeTestMatrix).profiles | Where-Object id -CEQ $Id)
    if ($matches.Count -ne 1) {
        throw "InviteCode matrix 找不到唯一 profile：$Id"
    }
    return $matches[0]
}

function Resolve-PluginTestLabRoot {
    param([AllowNull()][string] $ExplicitRoot)
    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($ExplicitRoot)) {
        $candidates.Add($ExplicitRoot)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:PLUGIN_TEST_LAB_HOME)) {
        $candidates.Add($env:PLUGIN_TEST_LAB_HOME)
    }
    $cursor = [System.IO.DirectoryInfo]::new((Get-InviteCodeRepositoryRoot))
    while ($null -ne $cursor) {
        $candidates.Add((Join-Path $cursor.FullName "TestLab\repo"))
        $cursor = $cursor.Parent
    }
    foreach ($candidate in $candidates) {
        $resolved = [System.IO.Path]::GetFullPath($candidate)
        if (Test-Path -LiteralPath (Join-Path $resolved "scripts\prepare-instance.ps1") -PathType Leaf) {
            return $resolved
        }
    }
    throw "找不到 TestLab。請合併 TestLab foundation、設定 PLUGIN_TEST_LAB_HOME，或傳入 -TestLabRoot。"
}

function Resolve-InviteCodePluginArtifact {
    param([AllowNull()][string] $ExplicitArtifact)
    if (-not [string]::IsNullOrWhiteSpace($ExplicitArtifact)) {
        $resolved = [System.IO.Path]::GetFullPath($ExplicitArtifact)
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "找不到 InviteCode JAR：$resolved"
        }
        return $resolved
    }
    $directory = Join-Path (Get-InviteCodeRepositoryRoot) "invitecode-plugin\build\libs"
    $matches = @(Get-ChildItem -LiteralPath $directory -File -Filter "InviteCode-*.jar" -ErrorAction SilentlyContinue)
    if ($matches.Count -ne 1) {
        throw "無法唯一判定 InviteCode JAR；請先建置或傳入 -Artifact。"
    }
    return $matches[0].FullName
}
