[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('Vault', 'Placeholder')][string] $Scenario,
    [Parameter(Mandatory = $true)][string] $Instance,
    [Parameter(Mandatory = $true)][string] $Artifact,
    [Parameter(Mandatory = $true)][string] $SpigotApiJar,
    [AllowNull()][string] $VaultJar,
    [AllowNull()][string] $EconomyJar,
    [AllowNull()][string] $PlaceholderApiJar,
    [AllowNull()][string] $TestLabRoot,
    [switch] $AcceptEula
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

$profile = if ($Scenario -ceq 'Vault') { 'paper-1.21.8' } else { 'paper-1.20.6' }
$required = if ($Scenario -ceq 'Vault') { @($SpigotApiJar, $VaultJar, $EconomyJar) } `
    else { @($SpigotApiJar, $PlaceholderApiJar) }
foreach ($candidate in $required) {
    if ([string]::IsNullOrWhiteSpace($candidate) -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "缺少測試依賴 JAR：$candidate"
    }
}
$lab = Resolve-PluginTestLabRoot -ExplicitRoot $TestLabRoot
& (Join-Path $PSScriptRoot 'prepare-synthetic-player.ps1') -Profile $profile -Instance $Instance `
    -Artifact $Artifact -TestLabRoot $lab -AcceptEula:$AcceptEula
Import-Module (Join-Path $lab 'scripts\TestLab.psm1') -Force
$instancePath = Get-TestLabInstancePath -Project 'InviteCode' -Instance $Instance
$plugins = Join-Path $instancePath 'plugins'
$data = Join-Path $plugins 'InviteCode'
$probe = Join-Path $PSScriptRoot 'synthetic-player\integration-probe'
$classes = Join-Path $instancePath 'integration-probe-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null

if ($Scenario -ceq 'Vault') {
    Copy-Item -LiteralPath $VaultJar -Destination (Join-Path $plugins 'Vault.jar')
    Copy-Item -LiteralPath $EconomyJar -Destination (Join-Path $plugins 'EssentialsX.jar')
    $classpath = "$SpigotApiJar;$VaultJar"
    $source = Join-Path $probe 'VaultProbePlugin.java'
    $descriptor = Join-Path $probe 'vault-plugin.yml'
    $probeJar = Join-Path $plugins 'InviteCodeVaultProbe.jar'
    $rewards = @'
config-version: 1

qualification-rewards:
  default:
    inviter:
      - type: MESSAGE
        message: invite.qualified.inviter
    invitee:
      - type: VAULT_ECONOMY
        amount: 37.5

milestone-rewards: {}
'@
    [System.IO.File]::WriteAllText(
        (Join-Path $data 'rewards.yml'), $rewards, [System.Text.UTF8Encoding]::new($false)
    )
} else {
    Copy-Item -LiteralPath $PlaceholderApiJar -Destination (Join-Path $plugins 'PlaceholderAPI.jar')
    $classpath = "$SpigotApiJar;$PlaceholderApiJar"
    $source = Join-Path $probe 'PapiProbePlugin.java'
    $descriptor = Join-Path $probe 'papi-plugin.yml'
    $probeJar = Join-Path $plugins 'InviteCodePapiProbe.jar'
    $qualification = @'
config-version: 1

policies:
  new-player:
    any-of:
      - all-of:
          - id: external-ready
            type: PLACEHOLDER
            placeholder: "%icprobe_ready%"
            operator: EQUALS
            value: "yes"
'@
    [System.IO.File]::WriteAllText(
        (Join-Path $data 'qualifications.yml'), $qualification, [System.Text.UTF8Encoding]::new($false)
    )
}
& mise exec java@local-21 -- javac --release 8 -cp $classpath -d $classes $source
if ($LASTEXITCODE -ne 0) { throw 'Integration probe 編譯失敗。' }
Copy-Item -LiteralPath $descriptor -Destination (Join-Path $classes 'plugin.yml')
& mise exec java@local-21 -- jar cf $probeJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Integration probe 封裝失敗。' }
Write-Output "已建立 $Scenario 合成玩家測試實例：$instancePath"
