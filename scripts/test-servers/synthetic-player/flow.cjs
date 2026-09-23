const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { spawn, spawnSync } = require('node:child_process')

const instancePath = path.resolve(process.argv[2] || '')
const metadataPath = path.join(instancePath, 'workspace.json')
const metadata = JSON.parse(fs.readFileSync(metadataPath, 'utf8'))
const supportedProfiles = new Set(['spigot-1.8.8', 'paper-1.20.6', 'paper-1.21.8', 'paper-latest', 'folia-latest'])
if (metadata.project !== 'InviteCode' || metadata.instance !== path.basename(instancePath) ||
    !metadata.instance.startsWith('issue12-') || !supportedProfiles.has(metadata.profile) ||
    !metadata.eulaAccepted) {
  throw new Error('Expected a prepared, EULA-accepted, isolated InviteCode synthetic-player instance')
}
const clientPath = process.env.INVITECODE_SYNTHETIC_CLIENT_262
if (metadata.profile === 'paper-latest' && metadata.minecraftVersion !== '26.2') {
  throw new Error('The pinned independent test client only supports Paper 26.2')
}
if (metadata.profile === 'folia-latest' && metadata.minecraftVersion !== '26.1.2') {
  throw new Error('The pinned official test client only supports the selected Folia 26.1.2 fixture')
}
if (metadata.profile === 'paper-latest' &&
    (!clientPath || !path.isAbsolute(clientPath) || !fs.existsSync(path.join(clientPath, 'package.json')))) {
  throw new Error('Paper 26.2 needs an absolute test-only client path in INVITECODE_SYNTHETIC_CLIENT_262')
}
const mineflayer = metadata.profile === 'paper-latest' ? require(clientPath) : require('mineflayer')
if (metadata.profile === 'paper-latest' &&
    require(path.join(clientPath, 'package.json')).version !== '4.37.1+complexity.26.2.3') {
  throw new Error('Unexpected Paper 26.2 test client version')
}

const dataDirectory = path.join(instancePath, 'plugins', 'InviteCode')
const config = fs.readFileSync(path.join(dataDirectory, 'config.yml'), 'utf8')
const qualifications = fs.readFileSync(path.join(dataDirectory, 'qualifications.yml'), 'utf8')
const chatMode = metadata.profile === 'folia-latest' && process.env.INVITECODE_FOLIA_CHAT === '1'
if (!/default: en_US/.test(config) || !/use-client-locale: false/.test(config) ||
    !(chatMode ? /input-mode: CHAT/.test(config) : /input-mode: COMMAND_ONLY/.test(config)) ||
    !/matching-ip-action: ALLOW/.test(config) ||
    !/type: LOGIN_DAYS\s+at-least: 1/.test(qualifications)) {
  throw new Error('Synthetic-player fixture configuration does not match the expected isolated test policy')
}
if (fs.existsSync(path.join(dataDirectory, 'invitecode.db')) ||
    fs.existsSync(path.join(dataDirectory, 'anti-abuse.key'))) {
  throw new Error('Synthetic-player flow requires a fresh instance with no existing player data')
}
const properties = fs.readFileSync(path.join(instancePath, 'server.properties'), 'utf8')
if (!/^server-ip=127\.0\.0\.1\s*$/m.test(properties) ||
    !/^online-mode=false\s*$/m.test(properties) ||
    !properties.includes(`server-port=${metadata.port}`)) {
  throw new Error('Synthetic-player server must use its expected loopback port and offline test mode')
}
function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').toUpperCase()
}
if (sha256(path.join(instancePath, 'server.jar')) !== metadata.serverSha256 ||
    sha256(path.join(instancePath, 'plugins', metadata.pluginFile)) !== metadata.pluginSha256) {
  throw new Error('TestLab server or plugin artifact differs from workspace.json')
}

const mise = process.env.MISE_EXE || 'mise'
let fatalError
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
async function until(check, description, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (fatalError) throw fatalError
    const result = check()
    if (result) return result
    await delay(100)
  }
  throw new Error(`Timed out waiting for ${description}`)
}

async function startServer() {
  const child = spawn(mise, [
    'exec', `java@${metadata.javaAlias}`, '--', 'java', '-Dfile.encoding=UTF-8',
    '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
    '-Xms512M', '-Xmx1G', '-jar', 'server.jar', 'nogui'
  ], { cwd: instancePath, stdio: ['pipe', 'pipe', 'pipe'] })
  const state = { child, output: '' }
  child.on('error', error => { fatalError = error })
  for (const stream of [child.stdout, child.stderr]) {
    stream.setEncoding('utf8')
    stream.on('data', chunk => {
      state.output += chunk
      for (const line of chunk.split(/\r?\n/)) {
        if (/InviteCode|Reward backlog|ERROR|Exception|Stopping server/.test(line)) {
          process.stdout.write(`SERVER ${line}\n`)
        }
      }
    })
  }
  await until(() => {
    if (child.exitCode !== null) throw new Error(`Server exited before InviteCode was ready: ${child.exitCode}`)
    return /InviteCode \S+ 已啟用/.test(state.output) && /Done \(/.test(state.output)
  }, 'InviteCode ACTIVE', 100000)
  return state
}

async function stopServer(state) {
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

async function createBot(name) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1', port: metadata.port, username: name,
    auth: 'offline', version: metadata.minecraftVersion
  })
  const messages = []
  bot.on('messagestr', message => {
    messages.push(message)
    process.stdout.write(`${name}: ${message}\n`)
  })
  bot.on('error', error => {
    if (error.name !== 'PartialReadError') fatalError = error
    process.stderr.write(`${name} protocol warning: ${error.name}: ${error.message}\n`)
  })
  bot.on('kicked', reason => { fatalError = new Error(`${name} kicked: ${reason}`) })
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${name} spawn timeout`)), 30000)
    bot.once('spawn', () => { clearTimeout(timer); resolve() })
    bot.once('end', reason => { clearTimeout(timer); reject(new Error(`${name} ended before spawn: ${reason}`)) })
  })
  return { bot, messages }
}

async function command(player, input, expected) {
  const start = player.messages.length
  player.bot.chat(input)
  return until(() => player.messages.slice(start).map(message => message.match(expected)).find(Boolean), `${input}: ${expected}`)
}

async function doctor(state, expected) {
  const start = state.output.length
  state.child.stdin.write('ic admin doctor\n')
  await until(() => expected.test(state.output.slice(start)), `doctor ${expected}`)
}

let server
let inviter
let invitee
let regionA
let regionB
async function main() {
  try {
    server = await startServer()
    if (metadata.profile === 'folia-latest' && process.env.INVITECODE_FOLIA_MULTI_REGION === '1') {
      regionA = await createBot('IcRegionA')
      regionB = await createBot('IcRegionB')
      server.child.stdin.write('tp IcRegionA 10000 80 10000\n')
      server.child.stdin.write('tp IcRegionB -10000 80 -10000\n')
      await until(() => Math.abs(regionA.bot.entity.position.x - 10000) < 2 &&
        Math.abs(regionB.bot.entity.position.x + 10000) < 2, 'bots in separate Folia regions')
      await Promise.all([
        command(regionA, '/ic status', /Invitation status: eligible=/),
        command(regionB, '/ic status', /Invitation status: eligible=/)
      ])
      regionA.bot.quit()
      regionB.bot.quit()
      regionA = null
      regionB = null
    }
    inviter = await createBot('IcTestA')
    const code = (await command(inviter, '/ic code', /Your Invite Code is ([A-F0-9]{6})/))[1]
    inviter.bot.quit()
    inviter = null
    await delay(500)
    invitee = await createBot('IcTestB')
    if (chatMode) {
      await command(invitee, '/ic claim', /Enter the Invite Code in chat/)
      const start = invitee.messages.length
      invitee.bot.chat(code)
      await until(() => invitee.messages.slice(start).some(message =>
        /Inviter: IcTestA; Invite Code:/.test(message)), 'private chat claim confirmation')
    } else {
      await command(invitee, `/ic claim ${code}`, /Inviter: IcTestA; Invite Code:/)
    }
    await command(invitee, '/ic confirm', /The Invitation has been created\./)
    await until(() => invitee.messages.some(message => message.includes('You have qualified as an invitee.')), 'Invitee Reward')
    await doctor(server, /Reward backlog: PENDING=1, PROCESSING=0, UNCERTAIN=0/)
    invitee.bot.quit()
    invitee = null
    await stopServer(server)
    server = await startServer()
    await doctor(server, /Reward backlog: PENDING=1, PROCESSING=0, UNCERTAIN=0/)
    inviter = await createBot('IcTestA')
    await until(() => inviter.messages.some(message => message.includes('Your invitation has qualified.')), 'offline Inviter Reward')
    await doctor(server, /Reward backlog: PENDING=0, PROCESSING=0, UNCERTAIN=0/)
    await command(inviter, '/ic stats', /qualified=1/)
    process.stdout.write(JSON.stringify({
      result: 'PASS', profile: metadata.profile, minecraftVersion: metadata.minecraftVersion,
      instance: metadata.instance, pluginSha256: metadata.pluginSha256,
      checks: [
        ...(metadata.profile === 'folia-latest' && process.env.INVITECODE_FOLIA_MULTI_REGION === '1'
          ? ['separate-region-status'] : []),
        ...(chatMode ? ['private-chat-input'] : []),
        'code', 'claim', 'confirm', 'qualification', 'pending-after-restart', 'offline-reward-on-login', 'stats'
      ]
    }) + '\n')
  } finally {
    if (inviter) inviter.bot.quit()
    if (invitee) invitee.bot.quit()
    if (regionA) regionA.bot.quit()
    if (regionB) regionB.bot.quit()
    if (server) await stopServer(server)
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
