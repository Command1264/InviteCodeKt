const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { spawn, spawnSync } = require('node:child_process')
const mineflayer = require('mineflayer')
const { assertAgentInstance } = require('./instance-boundary.cjs')

let instancePath = path.resolve(process.argv[2] || '')
const resolution = process.argv[3]
if (!['mark-delivered', 'retry'].includes(resolution)) {
  throw new Error('Usage: node uncertain.cjs <fresh Spigot 1.8.8 Agent instance> <mark-delivered|retry>')
}
const metadata = JSON.parse(fs.readFileSync(path.join(instancePath, 'workspace.json'), 'utf8'))
instancePath = assertAgentInstance(instancePath, metadata)
const dataDirectory = path.join(instancePath, 'plugins', 'InviteCode')
const marker = path.join(instancePath, 'plugins', 'InviteCodeFaultProbe', 'effect-entered.log')
const properties = fs.readFileSync(path.join(instancePath, 'server.properties'), 'utf8')
const rewardConfig = fs.readFileSync(path.join(dataDirectory, 'rewards.yml'), 'utf8')
if (metadata.profile !== 'spigot-1.8.8' ||
    !metadata.eulaAccepted || !/^server-ip=127\.0\.0\.1\s*$/m.test(properties) ||
    !/^online-mode=false\s*$/m.test(properties) ||
    !properties.includes(`server-port=${metadata.port}`) ||
    !rewardConfig.includes('command: icfault') ||
    !fs.existsSync(path.join(instancePath, 'plugins', 'InviteCodeFaultProbe.jar')) ||
    fs.existsSync(marker) || fs.existsSync(path.join(dataDirectory, 'invitecode.db'))) {
  throw new Error('Expected a fresh, EULA-accepted, loopback-only reward-interruption instance')
}
function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').toUpperCase()
}
if (sha256(path.join(instancePath, 'server.jar')) !== metadata.serverSha256 ||
    sha256(path.join(instancePath, 'plugins', metadata.pluginFile)) !== metadata.pluginSha256) {
  throw new Error('Server or InviteCode JAR differs from workspace.json')
}

const mise = process.env.MISE_EXE || 'mise'
let expectedCrash = false
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
        if (/InviteCode|FaultProbe|Reward backlog|RewardAction|ERROR|Exception/.test(line)) {
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
    if (expectedCrash && error.code === 'ECONNRESET') return
    clientError = error
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
function markerCount() {
  return fs.existsSync(marker) ? fs.readFileSync(marker, 'ascii').trim().split('\n').length : 0
}
function actionId() {
  const db = path.join(dataDirectory, 'invitecode.db')
  if (!fs.existsSync(db)) throw new Error('SQLite database is missing')
  const query = [
    'import sqlite3,sys',
    'con=sqlite3.connect(sys.argv[1])',
    "rows=con.execute(\"SELECT action_id FROM invitecode_reward_actions WHERE action_type='CONSOLE_COMMAND'\").fetchall()",
    'assert len(rows)==1, rows',
    'print(rows[0][0])'
  ].join(';')
  const result = spawnSync('py', ['-3', '-c', query, db], { encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`Cannot discover test Reward Action: ${result.stderr}`)
  return result.stdout.trim()
}

let server
let inviter
let invitee
async function main() {
  try {
    server = startServer()
    await ready(server)
    inviter = await bot('IcFaultA')
    const code = (await playerCommand(inviter, '/ic code', /Your Invite Code is ([A-F0-9]{6})/))[1]
    inviter.client.quit()
    inviter = null
    await delay(500)
    invitee = await bot('IcFaultB')
    await playerCommand(invitee, `/ic claim ${code}`, /Inviter: IcFaultA/)
    await until(() => invitee.messages.some(message => message.includes(`Invite Code: ${code}`)) &&
      invitee.messages.some(message => message.includes('[Agree]') && message.includes('[Disagree]')),
    'multiline claim confirmation')
    expectedCrash = true
    invitee.client.chat('/ic confirm')
    await until(() => server.child.exitCode !== null, 'deliberate JVM halt', 30000)
    if (server.child.exitCode !== 83 || markerCount() !== 1) {
      throw new Error(`Fault probe did not create exactly one effect and halt with 83: exit=${server.child.exitCode}, marker=${markerCount()}`)
    }
    invitee = null
    expectedCrash = false
    server = startServer()
    await ready(server)
    await consoleCommand(server, 'ic admin doctor', /Reward backlog: PENDING=1, PROCESSING=0, UNCERTAIN=1/)
    const id = actionId()
    await consoleCommand(server, `ic admin inspect reward ${id}`, new RegExp(`RewardAction=${id} .*status=UNCERTAIN`))
    invitee = await bot('IcFaultB')
    await delay(1000)
    if (markerCount() !== 1) throw new Error('UNCERTAIN Action was automatically replayed on login')
    await consoleCommand(server, `ic admin reward ${resolution} ${id} isolated-test`,
      new RegExp(`Reward Action ${id} was resolved as ${resolution === 'retry' ? 'PENDING' : 'DELIVERED'}`))
    if (resolution === 'retry') {
      await until(() => markerCount() === 2, 'explicit administrator retry')
      await consoleCommand(server, `ic admin inspect reward ${id}`, new RegExp(`RewardAction=${id} .*status=DELIVERED`))
    } else if (markerCount() !== 1) {
      throw new Error('MARK_DELIVERED unexpectedly repeated external effect')
    }
    await consoleCommand(server, 'ic admin doctor', /Reward backlog: PENDING=1, PROCESSING=0, UNCERTAIN=0/)
    invitee.client.quit()
    invitee = null
    await stop(server)
    server = startServer()
    await ready(server)
    await consoleCommand(server, `ic admin inspect reward ${id}`, new RegExp(`RewardAction=${id} .*status=DELIVERED`))
    await consoleCommand(server, 'ic admin doctor', /Reward backlog: PENDING=1, PROCESSING=0, UNCERTAIN=0/)
    if (markerCount() !== (resolution === 'retry' ? 2 : 1)) {
      throw new Error('Administrator resolution did not remain stable after restart')
    }
    process.stdout.write(JSON.stringify({
      result: 'PASS', scenario: 'reward-interruption', resolution, instance: metadata.instance,
      profile: metadata.profile, pluginSha256: metadata.pluginSha256, markerCount: markerCount(),
      checks: ['effect-entered', 'abrupt-halt', 'restart-recovery', 'no-automatic-retry', 'admin-resolution', 'resolution-after-restart']
    }) + '\n')
  } finally {
    if (inviter) inviter.client.quit()
    if (invitee) invitee.client.quit()
    if (server) await stop(server)
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
