const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { spawn, spawnSync } = require('node:child_process')

const instancePath = path.resolve(process.argv[2] || '')
const metadata = JSON.parse(fs.readFileSync(path.join(instancePath, 'workspace.json'), 'utf8'))
const properties = fs.readFileSync(path.join(instancePath, 'server.properties'), 'utf8')
const dataPath = path.join(instancePath, 'plugins', 'InviteCode')
const dbPath = path.join(dataPath, 'invitecode.db')
const keyPath = path.join(dataPath, 'anti-abuse.key')
if (metadata.project !== 'InviteCode' || metadata.instance !== path.basename(instancePath) ||
    path.basename(path.dirname(instancePath)) !== 'InviteCode' ||
    path.basename(path.dirname(path.dirname(instancePath))) !== 'instances' ||
    path.basename(path.dirname(path.dirname(path.dirname(instancePath)))) !== 'TestLab' ||
    !metadata.instance.startsWith('issue12-') || metadata.profile !== 'spigot-1.8.8' ||
    metadata.port !== 51888 ||
    !metadata.eulaAccepted || !/^server-ip=127\.0\.0\.1\s*$/m.test(properties) ||
    !/^online-mode=false\s*$/m.test(properties) ||
    !properties.includes(`server-port=${metadata.port}`) ||
    fs.existsSync(dbPath) || fs.existsSync(keyPath)) {
  throw new Error('Expected a fresh, EULA-accepted, loopback-only Spigot test instance')
}
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').toUpperCase()
if (hash(path.join(instancePath, 'server.jar')) !== metadata.serverSha256 ||
    hash(path.join(instancePath, 'plugins', metadata.pluginFile)) !== metadata.pluginSha256) {
  throw new Error('Server or plugin JAR differs from workspace.json')
}

const fixture = `
import sqlite3, sys
mode, filename = sys.argv[1:]
con = sqlite3.connect(filename)
try:
    if mode == 'downgrade':
        assert con.execute('SELECT version FROM invitecode_schema_version').fetchone()[0] == 8
        with con:
            con.execute('DROP TABLE invitecode_player_network_hashes')
            con.execute('UPDATE invitecode_schema_version SET version=7')
            con.execute('INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name) VALUES (?, ?, ?)',
                        ('00000000-0000-0000-0000-000000000012', '2026-09-24T00:00:00Z', 'MigrationSentinel'))
            # This valid index name makes v8 fail after CREATE TABLE, testing transactional DDL rollback.
            con.execute('CREATE INDEX invitecode_network_hash_expiry ON invitecode_players(first_seen_at)')
    elif mode == 'repair':
        with con:
            con.execute('DROP INDEX invitecode_network_hash_expiry')
    elif mode in ('assert-old', 'assert-current'):
        expected = 8 if mode == 'assert-current' else 7
        assert con.execute('SELECT version FROM invitecode_schema_version').fetchone()[0] == expected
        assert con.execute("SELECT last_known_name FROM invitecode_players WHERE player_id='00000000-0000-0000-0000-000000000012'").fetchone() == ('MigrationSentinel',)
        table = con.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='invitecode_player_network_hashes'").fetchone()
        index = con.execute("SELECT tbl_name FROM sqlite_master WHERE type='index' AND name='invitecode_network_hash_expiry'").fetchone()
        if expected == 7:
            assert table is None and index == ('invitecode_players',), (table, index)
        else:
            assert table == (1,) and index == ('invitecode_player_network_hashes',), (table, index)
        assert con.execute('PRAGMA integrity_check').fetchone() == ('ok',)
    else:
        raise ValueError(mode)
finally:
    con.close()
`
function sqlite(mode) {
  const result = spawnSync('py', ['-3', '-c', fixture, mode, dbPath], { encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`SQLite fixture ${mode} failed: ${result.stderr}`)
}
const mise = process.env.MISE_EXE || 'mise'
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
async function until(check, what, timeout = 100000) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const result = check()
    if (result) return result
    await delay(100)
  }
  throw new Error(`Timed out: ${what}`)
}
function start() {
  const child = spawn(mise, ['exec', `java@${metadata.javaAlias}`, '--', 'java',
    '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
    '-Xms512M', '-Xmx1G', '-jar', 'server.jar', 'nogui'],
  { cwd: instancePath, stdio: ['pipe', 'pipe', 'pipe'] })
  const server = { child, output: '' }
  child.on('error', error => { server.error = error })
  for (const stream of [child.stdout, child.stderr]) {
    stream.setEncoding('utf8')
    stream.on('data', text => {
      server.output += text
      for (const line of text.split(/\r?\n/)) {
        if (/InviteCode|Runtime=|儲存|migration|Exception|ERROR/.test(line)) process.stdout.write(`SERVER ${line}\n`)
      }
    })
  }
  return server
}
async function ready(server, phase) {
  await until(() => {
    if (server.error) throw server.error
    if (server.child.exitCode !== null) throw new Error(`Server exited early: ${server.child.exitCode}`)
    return /Done \(/.test(server.output) &&
      (phase === 'ACTIVE' ? /InviteCode \S+ 已啟用/.test(server.output) : /已進入維護模式/.test(server.output))
  }, `server ${phase}`)
}
async function command(server, input, expected) {
  const offset = server.output.length
  server.child.stdin.write(`${input}\n`)
  return until(() => server.output.slice(offset).match(expected), input, 30000)
}
async function stop(server) {
  if (!server || server.child.exitCode !== null) return
  server.child.stdin.write('stop\n')
  try {
    await until(() => server.child.exitCode !== null, 'server stop', 30000)
    if (server.child.exitCode !== 0) throw new Error(`Server exit: ${server.child.exitCode}`)
  } catch (error) {
    if (server.child.exitCode === null) {
      if (process.platform === 'win32') spawnSync('taskkill', ['/PID', String(server.child.pid), '/T', '/F'], { stdio: 'ignore' })
      else server.child.kill()
    }
    throw error
  }
}
let server
async function main() {
  try {
    server = start()
    await ready(server, 'ACTIVE')
    await stop(server)
    const keyHash = hash(keyPath)
    sqlite('downgrade')
    sqlite('assert-old')

    server = start()
    await ready(server, 'MAINTENANCE')
    await command(server, 'ic admin doctor', /Runtime=MAINTENANCE/)
    await stop(server)
    sqlite('assert-old')
    if (hash(keyPath) !== keyHash) throw new Error('Network secret changed during rejected startup')

    server = start()
    await ready(server, 'MAINTENANCE')
    await command(server, 'ic admin storage migrate SQLITE injected-index-conflict', /Storage schema migration 失敗/)
    await command(server, 'ic admin doctor', /Runtime=MAINTENANCE/)
    await stop(server)
    sqlite('assert-old')
    if (hash(keyPath) !== keyHash) throw new Error('Network secret changed after failed migration')

    sqlite('repair')
    server = start()
    await ready(server, 'MAINTENANCE')
    await command(server, 'ic admin storage migrate SQLITE repaired-index-conflict', /Storage schema migration 已完成，目前版本為 8/)
    await command(server, 'ic admin doctor', /Runtime=ACTIVE/)
    await stop(server)
    sqlite('assert-current')

    server = start()
    await ready(server, 'ACTIVE')
    await command(server, 'ic admin doctor', /Runtime=ACTIVE/)
    await stop(server)
    sqlite('assert-current')
    if (hash(keyPath) !== keyHash) throw new Error('Network secret changed after recovery')
    process.stdout.write(JSON.stringify({ result: 'PASS', scenario: 'sqlite-migration-recovery',
      instance: metadata.instance, profile: metadata.profile, pluginSha256: metadata.pluginSha256,
      checks: ['no-auto-upgrade', 'maintenance', 'ddl-rollback', 'sentinel-preserved',
        'explicit-recovery', 'restart-durable', 'secret-preserved'] }) + '\n')
  } finally {
    if (server) await stop(server)
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
