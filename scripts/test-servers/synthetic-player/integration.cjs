const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { spawn, spawnSync } = require('node:child_process')
const mineflayer = require('mineflayer')
const { assertAgentInstance } = require('./instance-boundary.cjs')

let instancePath = path.resolve(process.argv[2] || '')
const scenario = process.argv[3]
if (!['Vault', 'Placeholder'].includes(scenario)) {
  throw new Error('Usage: node integration.cjs <fresh InstanceHarbor Agent instance> <Vault|Placeholder>')
}
const metadata = JSON.parse(fs.readFileSync(path.join(instancePath, 'workspace.json'), 'utf8'))
instancePath = assertAgentInstance(instancePath, metadata)
const expectedProfiles = scenario === 'Vault' ? ['paper-1.21.8'] : ['paper-1.20.6', 'folia-latest']
const dataDirectory = path.join(instancePath, 'plugins', 'InviteCode')
const properties = fs.readFileSync(path.join(instancePath, 'server.properties'), 'utf8')
const pluginDirectory = path.join(instancePath, 'plugins')
const requiredJars = scenario === 'Vault'
  ? ['Vault.jar', 'EssentialsX.jar', 'InviteCodeVaultProbe.jar']
  : ['PlaceholderAPI.jar', 'InviteCodePapiProbe.jar']
if (!expectedProfiles.includes(metadata.profile) ||
    !metadata.eulaAccepted || !/^server-ip=127\.0\.0\.1\s*$/m.test(properties) ||
    !/^online-mode=false\s*$/m.test(properties) ||
    !properties.includes(`server-port=${metadata.port}`) ||
    requiredJars.some(name => !fs.existsSync(path.join(pluginDirectory, name))) ||
    fs.existsSync(path.join(dataDirectory, 'invitecode.db')) ||
    fs.existsSync(path.join(dataDirectory, 'anti-abuse.key'))) {
  throw new Error('Expected a fresh, EULA-accepted, loopback-only integration instance')
}
const qualification = fs.readFileSync(path.join(dataDirectory, 'qualifications.yml'), 'utf8')
const rewards = scenario === 'Vault'
  ? fs.readFileSync(path.join(dataDirectory, 'rewards.yml'), 'utf8') : ''
if (scenario === 'Vault' ? !rewards.includes('amount: 37.5')
  : !qualification.includes('placeholder: "%icprobe_ready%"')) {
  throw new Error('Integration fixture configuration does not match the requested scenario')
}
function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').toUpperCase()
}
if (sha256(path.join(instancePath, 'server.jar')) !== metadata.serverSha256 ||
    sha256(path.join(pluginDirectory, metadata.pluginFile)) !== metadata.pluginSha256) {
  throw new Error('Server or InviteCode JAR differs from workspace.json')
}

const mise = process.env.MISE_EXE || 'mise'
let clientError
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
async function until(check, description, timeoutMs = 30000) {
  const end = Date.now() + timeoutMs
  while (Date.now() < end) {
    if (clientError) throw clientError
    const result = check()
    if (result) return result
    await delay(100)
  }
  throw new Error(`Timed out waiting for ${description}`)
}
function startServer() {
  const child = spawn(mise, [
    'exec', `java@${metadata.javaAlias}`, '--', 'java', '-Dfile.encoding=UTF-8',
    '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
    '-Xms512M', '-Xmx1G', '-jar', 'server.jar', 'nogui'
  ], { cwd: instancePath, stdio: ['pipe', 'pipe', 'pipe'] })
  const state = { child, output: '' }
  child.on('error', error => { state.error = error })
  for (const stream of [child.stdout, child.stderr]) {
    stream.setEncoding('utf8')
    stream.on('data', chunk => {
      state.output += chunk
      for (const line of chunk.split(/\r?\n/)) {
        if (/InviteCode|ICBALANCE|ICPROBE|Reward backlog|ERROR|Exception|Economy hooked|Successfully registered internal expansion/.test(line)) {
          process.stdout.write(`SERVER ${line}\n`)
        }
      }
    })
  }
  return state
}
async function ready(state) {
  await until(() => {
    if (state.error) throw state.error
    if (state.child.exitCode !== null) throw new Error(`Server exited before ready: ${state.child.exitCode}`)
    return /InviteCode \S+ 已啟用/.test(state.output) && /Done \(/.test(state.output)
  }, 'InviteCode active', 100000)
}
async function stop(state) {
  if (!state || state.child.exitCode !== null) return
  state.child.stdin.write('stop\n')
  try {
    await until(() => state.child.exitCode !== null, 'server stop', 30000)
  } catch (error) {
    if (process.platform === 'win32' && state.child.pid) {
      spawnSync('taskkill', ['/PID', String(state.child.pid), '/T', '/F'], { stdio: 'ignore' })
    } else {
      state.child.kill()
    }
    throw error
  }
}
async function bot(name) {
  const client = mineflayer.createBot({
    host: '127.0.0.1', port: metadata.port, username: name,
    auth: 'offline', version: metadata.minecraftVersion
  })
  const messages = []
  client.on('messagestr', message => { messages.push(message); process.stdout.write(`${name}: ${message}\n`) })
  client.on('error', error => {
    if (error.name === 'PartialReadError') {
      process.stderr.write(`${name} protocol warning: ${error.message}\n`)
    } else clientError = error
  })
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${name} spawn timeout`)), 30000)
    client.once('spawn', () => { clearTimeout(timer); resolve() })
    client.once('end', reason => { clearTimeout(timer); reject(new Error(`${name} ended before spawn: ${reason}`)) })
  })
  return { client, messages }
}
async function playerCommand(player, input, expected) {
  const start = player.messages.length
  player.client.chat(input)
  return until(() => player.messages.slice(start).map(message => message.match(expected)).find(Boolean), input)
}
async function consoleCommand(state, input, expected) {
  const start = state.output.length
  state.child.stdin.write(`${input}\n`)
  return until(() => state.output.slice(start).match(expected), input)
}
async function balance(state, name, expected) {
  const deadline = Date.now() + 30000
  while (Date.now() < deadline) {
    const match = await consoleCommand(state, `icbalance ${name}`, /ICBALANCE IcVaultB ([0-9.]+)/)
    if (Number(match[1]) === expected) return
    await delay(250)
  }
  throw new Error(`Vault balance for ${name} did not reach ${expected}`)
}

let server
let inviter
let invitee
async function main() {
  try {
    server = startServer()
    await ready(server)
    await consoleCommand(server, 'ic admin doctor', scenario === 'Vault'
      ? /Integrations: PlaceholderAPI=false, Vault Economy=true/
      : /Integrations: PlaceholderAPI=true, Vault Economy=false/)
    const inviterName = scenario === 'Vault' ? 'IcVaultA' : 'IcPapiA'
    const inviteeName = scenario === 'Vault' ? 'IcVaultB' : 'IcPapiB'
    inviter = await bot(inviterName)
    const code = (await playerCommand(inviter, '/ic code', /Your Invite Code is ([A-F0-9]{6})/))[1]
    inviter.client.quit()
    inviter = null
    await delay(500)
    invitee = await bot(inviteeName)
    if (scenario === 'Vault') {
      await balance(server, inviteeName, 0)
    }
    await playerCommand(invitee, `/ic claim ${code}`, new RegExp(`Inviter: ${inviterName}`))
    await until(() => invitee.messages.some(message => message.includes(`Invite Code: ${code}`)) &&
      invitee.messages.some(message => message.includes('[Agree]') && message.includes('[Disagree]')),
    'multiline claim confirmation')
    await playerCommand(invitee, '/ic confirm', /The Invitation has been created\./)
    if (scenario === 'Vault') {
      await balance(server, inviteeName, 37.5)
    } else {
      await playerCommand(invitee, '/ic status', /Invitation=awaiting qualification/)
      await consoleCommand(server, `icprobe ready ${inviteeName}`, /ICPROBE ready IcPapiB yes/)
      invitee.client.quit()
      invitee = null
      await delay(500)
      invitee = await bot(inviteeName)
      await playerCommand(invitee, '/ic status', /Invitation=qualified/)
      await until(() => invitee.messages.some(message => message.includes('You have qualified as an invitee.')),
        'Placeholder Qualification Reward')
    }
    await consoleCommand(server, 'ic admin doctor', /Reward backlog: PENDING=1, PROCESSING=0, UNCERTAIN=0/)
    invitee.client.quit()
    invitee = null
    await delay(10000)
    await stop(server)
    server = startServer()
    await ready(server)
    invitee = await bot(inviteeName)
    await playerCommand(invitee, '/ic status', /Invitation=qualified/)
    if (scenario === 'Vault') {
      await balance(server, inviteeName, 37.5)
    }
    await consoleCommand(server, 'ic admin doctor', /Reward backlog: PENDING=1, PROCESSING=0, UNCERTAIN=0/)
    process.stdout.write(JSON.stringify({
      result: 'PASS', scenario, instance: metadata.instance, profile: metadata.profile,
      pluginSha256: metadata.pluginSha256,
      checks: scenario === 'Vault'
        ? ['balance-before-0', 'qualification', 'vault-deposit-37.5', 'balance-after-restart-37.5']
        : ['placeholder-unsatisfied', 'placeholder-ready', 'qualified-after-rejoin', 'qualified-after-restart']
    }) + '\n')
  } finally {
    if (inviter) inviter.client.quit()
    if (invitee) invitee.client.quit()
    if (server) await stop(server)
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
