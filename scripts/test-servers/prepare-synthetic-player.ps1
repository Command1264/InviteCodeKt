[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('spigot-1.8.8', 'spigot-latest', 'paper-1.20.6', 'paper-1.21.8', 'paper-latest', 'folia-latest')]
    [string] $Profile,
    [Parameter(Mandatory = $true)][string] $Instance,
    [Parameter(Mandatory = $true)][string] $Artifact,
    [switch] $AcceptEula,
    [switch] $RewardInterruption,
    [Alias("TestLabRoot")][AllowNull()][string] $InstanceHarborRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

if ($Instance -cnotmatch '^issue12-[a-zA-Z0-9-]{1,56}$') {
    throw '合成玩家測試需要新的 issue12-* InstanceHarbor instance 名稱。'
}
if ($RewardInterruption -and $Profile -cne 'spigot-1.8.8') {
    throw 'Reward 中斷測試目前只在 Spigot 1.8.8 的隔離實例執行。'
}
$lab = Resolve-PluginInstanceHarborRoot -ExplicitRoot $InstanceHarborRoot
Import-Module (Join-Path $lab 'scripts\InstanceHarbor.psm1') -Force
$instancePath = Get-InstanceHarborInstancePath -Project 'InviteCode' -Audience Agent -Instance $Instance
if (Test-Path -LiteralPath $instancePath) {
    throw "合成玩家測試必須使用全新隔離實例；目標已存在：$instancePath"
}
& (Join-Path $PSScriptRoot 'prepare.ps1') -Profile $Profile -Instance $Instance `
    -Artifact $Artifact -InstanceHarborRoot $lab -AcceptEula:$AcceptEula

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
if ($RewardInterruption) {
    $rewards = @'
config-version: 1

qualification-rewards:
  default:
    inviter:
      - type: MESSAGE
        message: invite.qualified.inviter
    invitee:
      - type: CONSOLE_COMMAND
        command: icfault

milestone-rewards: {}
'@
    [System.IO.File]::WriteAllText(
        (Join-Path $data 'rewards.yml'), $rewards, [System.Text.UTF8Encoding]::new($false)
    )
    $faultSource = Join-Path $PSScriptRoot 'synthetic-player\fault-plugin'
    $classes = Join-Path $instancePath 'fault-plugin-classes'
    $faultJar = Join-Path $instancePath 'plugins\InviteCodeFaultProbe.jar'
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    & mise exec java@local-8 -- javac -source 8 -target 8 -cp (Join-Path $instancePath 'server.jar') `
        -d $classes (Join-Path $faultSource 'FaultPlugin.java')
    if ($LASTEXITCODE -ne 0) { throw '測試專用 fault plugin 編譯失敗。' }
    Copy-Item -LiteralPath (Join-Path $faultSource 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml')
    & mise exec java@local-8 -- jar cf $faultJar -C $classes .
    if ($LASTEXITCODE -ne 0) { throw '測試專用 fault plugin 封裝失敗。' }
}
Write-Output "已建立合成玩家測試實例：$instancePath"
