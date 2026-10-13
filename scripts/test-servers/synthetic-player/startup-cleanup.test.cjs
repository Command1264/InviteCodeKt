const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const { spawn, spawnSync } = require('node:child_process')
const test = require('node:test')

// This Windows-only runner check substitutes the external Java process, never Minecraft data.
// Preserve evidence in the Worktree, then remove only the exact self-created fixture.
async function checkStartupCleanup(closeInput = false) {
  const root = 'D:\\Code\\Java\\Plugin\\InstanceHarbor\\instances'
  assert.equal(fs.lstatSync(root).isSymbolicLink(), false)
  const parent = path.join(root, 'Agent', 'InviteCode')
  assert.equal(fs.realpathSync(parent), path.join(fs.realpathSync(root), 'Agent', 'InviteCode'), 'self-test parent must not redirect into other data')
  const name = `issue12-startup-cleanup-${crypto.randomBytes(6).toString('hex')}`
  const instance = path.join(root, 'Agent', 'InviteCode', name)
  fs.mkdirSync(instance)
  const data = path.join(instance, 'plugins', 'InviteCode')
  fs.mkdirSync(data, { recursive: true })
  const server = Buffer.from('runner self-test server placeholder')
  const plugin = Buffer.from('runner self-test plugin placeholder')
  const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex').toUpperCase()
  fs.writeFileSync(path.join(instance, 'server.jar'), server)
  fs.writeFileSync(path.join(instance, 'plugins', 'InviteCode.jar'), plugin)
  fs.writeFileSync(path.join(instance, 'workspace.json'), JSON.stringify({
    schemaVersion: 2, audience: 'Agent', project: 'InviteCode', instance: name,
    profile: 'spigot-1.8.8', minecraftVersion: '1.8.8', javaAlias: 'local-8', port: 63819,
    pluginFile: 'InviteCode.jar', pluginSha256: hash(plugin), serverSha256: hash(server),
    eulaAccepted: true, runnerSelfTest: true
  }))
  fs.writeFileSync(path.join(instance, 'server.properties'), 'server-ip=127.0.0.1\nonline-mode=false\nserver-port=63819\n')
  fs.writeFileSync(path.join(data, 'config.yml'), 'default: en_US\nuse-client-locale: false\ninput-mode: COMMAND_ONLY\nmatching-ip-action: ALLOW\n')
  fs.writeFileSync(path.join(data, 'qualifications.yml'), 'type: LOGIN_DAYS\nat-least: 1\n')
  fs.writeFileSync(path.join(instance, 'exec'), String.raw`
const fs = require('node:fs')
fs.writeFileSync('external-process.pid', String(process.pid))
process.stdout.write('Done (0.01s)!\n')
${closeInput ? 'process.stdin.destroy()' : ''}
process.stdin.setEncoding('utf8')
process.stdin.on('data', input => {
  if (input.trim() === 'stop') {
    fs.writeFileSync('external-process-stopped.txt', 'normal stop received')
    process.exit(0)
  }
})
setInterval(() => {}, 1000)
`)
  const runner = spawn(process.execPath, [path.join(__dirname, 'flow.cjs'), instance], {
    env: { ...process.env, MISE_EXE: process.execPath, INVITECODE_SERVER_START_TIMEOUT_MS: '500', INVITECODE_SERVER_STOP_TIMEOUT_MS: '500',
      INVITECODE_CHAT_INPUT: '0', INVITECODE_FOLIA_CHAT: '0', INVITECODE_DIALOG_PROTOCOL: '0' },
    stdio: ['ignore', 'pipe', 'pipe']
  })
  let output = ''
  for (const stream of [runner.stdout, runner.stderr]) stream.on('data', chunk => { output += chunk.toString('utf8') })
  let timer
  try {
    const code = await Promise.race([
      new Promise((resolve, reject) => { runner.once('error', reject); runner.once('exit', resolve) }),
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('runner exceeded configured startup deadline and cleanup allowance')), 6000) })
    ])
    assert.notEqual(code, 0, 'failure must be reported')
    assert.match(output, /Timed out waiting for InviteCode ACTIVE/)
    if (!closeInput) assert.equal(fs.readFileSync(path.join(instance, 'external-process-stopped.txt'), 'utf8'), 'normal stop received')
    const pid = Number(fs.readFileSync(path.join(instance, 'external-process.pid'), 'utf8'))
    assert.throws(() => process.kill(pid, 0), /ESRCH|no such process/i)
  } finally {
    clearTimeout(timer)
    if (runner.exitCode === null && runner.pid) {
      // Only the explicitly created self-test process tree; no real server is targeted.
      const killed = spawnSync('taskkill', ['/PID', String(runner.pid), '/T', '/F'], { encoding: 'utf8' })
      assert.equal(killed.status, 0, killed.stderr)
    }
    const pidPath = path.join(instance, 'external-process.pid')
    if (fs.existsSync(pidPath)) {
      const pid = Number(fs.readFileSync(pidPath, 'utf8'))
      let alive = false
      try { process.kill(pid, 0); alive = true } catch (error) { if (error.code !== 'ESRCH') throw error }
      if (alive) {
        const killed = spawnSync('taskkill', ['/PID', String(pid), '/T', '/F'], { encoding: 'utf8' })
        assert.equal(killed.status, 0, killed.stderr)
      }
    }
    fs.writeFileSync(path.join(instance, 'runner.log'), output)
    const evidence = path.resolve(__dirname, '..', '..', '..', 'invitecode-plugin', 'build', 'current-artifact', 'startup-cleanup-fixtures', name)
    fs.cpSync(instance, evidence, { recursive: true, errorOnExist: true, force: false })
    assert.equal(fs.realpathSync(instance), path.join(fs.realpathSync(parent), name))
    assert.equal(path.basename(instance), name)
    fs.rmSync(instance, { recursive: true })
  }
}

test('startup deadline stops the owned server normally before reporting failure', { timeout: 15000 }, () => checkStartupCleanup())
test('startup failure ends its owned process even when the console input pipe is broken', { timeout: 15000 }, () => checkStartupCleanup(true))
