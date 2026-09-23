[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('spigot-1.8.8', 'paper-1.20.6', 'paper-1.21.8')]
    [string] $Profile,
    [Parameter(Mandatory = $true)][string] $Instance,
    [Parameter(Mandatory = $true)][string] $Artifact,
    [switch] $AcceptEula,
    [AllowNull()][string] $TestLabRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

if ($Instance -cnotmatch '^issue12-[a-zA-Z0-9-]{1,72}$') {
    throw '合成玩家測試需要新的 issue12-* TestLab instance 名稱。'
}
$lab = Resolve-PluginTestLabRoot -ExplicitRoot $TestLabRoot
Import-Module (Join-Path $lab 'scripts\TestLab.psm1') -Force
$instancePath = Get-TestLabInstancePath -Project 'InviteCode' -Instance $Instance
if (Test-Path -LiteralPath $instancePath) {
    throw "合成玩家測試必須使用全新隔離實例；目標已存在：$instancePath"
}
& (Join-Path $PSScriptRoot 'prepare.ps1') -Profile $Profile -Instance $Instance `
    -Artifact $Artifact -TestLabRoot $lab -AcceptEula:$AcceptEula

$data = Join-Path $instancePath 'plugins\InviteCode'
New-Item -ItemType Directory -Path $data -Force | Out-Null
$source = Join-Path (Get-InviteCodeRepositoryRoot) 'invitecode-plugin\src\main\resources\config\config.yml'
$configuration = Get-Content -LiteralPath $source -Raw -Encoding UTF8
$configuration = $configuration.Replace('default: zh_TW', 'default: en_US').Replace(
    'use-client-locale: true', 'use-client-locale: false').Replace(
    'input-mode: AUTO', 'input-mode: COMMAND_ONLY').Replace(
    'matching-ip-action: DENY', 'matching-ip-action: ALLOW')
[System.IO.File]::WriteAllText(
    (Join-Path $data 'config.yml'), $configuration, [System.Text.UTF8Encoding]::new($false)
)
$qualification = @'
config-version: 1

policies:
  new-player:
    any-of:
      - all-of:
          - id: first-login
            type: LOGIN_DAYS
            at-least: 1
'@
[System.IO.File]::WriteAllText(
    (Join-Path $data 'qualifications.yml'), $qualification, [System.Text.UTF8Encoding]::new($false)
)
Write-Output "已建立合成玩家測試實例：$instancePath"
