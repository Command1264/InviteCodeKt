const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { spawn, spawnSync } = require('node:child_process')

const instancePath = path.resolve(process.argv[2] || '')
const metadata = JSON.parse(fs.readFileSync(path.join(instancePath, 'workspace.json'), 'utf8'))
const properties = fs.readFileSync(path.join(instancePath, 'server.properties'), 'utf8')
const dataPath = path.join(instancePath, 'plugins', 'InviteCode')
const jarPath = path.join(instancePath, 'plugins', metadata.pluginFile)
const database = `invitecode_issue12_test_${metadata.instance.replace(/^issue12-/, '').replace(/-/g, '_')}`
const mysqlExe = path.join(path.dirname(path.dirname(path.dirname(instancePath))),
  'artifacts', 'invitecode-issue12-mysql-8.4.11', 'mysql-8.4.11-winx64', 'bin', 'mysql.exe')
if (metadata.project !== 'InviteCode' || metadata.instance !== path.basename(instancePath) ||
    path.basename(path.dirname(instancePath)) !== 'InviteCode' ||
    path.basename(path.dirname(path.dirname(instancePath))) !== 'instances' ||
    path.basename(path.dirname(path.dirname(path.dirname(instancePath)))) !== 'TestLab' ||
    !/^issue12-[a-z0-9-]+$/.test(metadata.instance) || metadata.profile !== 'paper-1.20.6' ||
    metadata.port !== 62060 || !metadata.eulaAccepted ||
    !/^server-ip=127\.0\.0\.1\s*$/m.test(properties) ||
    !/^online-mode=false\s*$/m.test(properties) ||
    !properties.includes(`server-port=${metadata.port}`) ||
    !/^invitecode_issue12_test_[a-z0-9_]{1,35}$/.test(database) ||
    fs.existsSync(path.join(dataPath, 'storage.yml')) ||
    fs.existsSync(path.join(dataPath, 'invitecode.db')) ||
    fs.existsSync(path.join(dataPath, 'anti-abuse.key')) ||
    !fs.existsSync(mysqlExe)) {
  throw new Error('Expected a fresh, EULA-accepted, loopback-only MySQL TestLab instance')
}
for (const name of ['INVITECODE_TEST_MYSQL_ROOT_PASSWORD', 'INVITECODE_DB_PASSWORD', 'INVITECODE_NETWORK_SECRET']) {
  if (!process.env[name]) throw new Error(`Missing test-only environment variable ${name}`)
}
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex')
if (hash(fs.readFileSync(jarPath)).toUpperCase() !== metadata.pluginSha256 ||
    hash(fs.readFileSync(path.join(instancePath, 'server.jar'))).toUpperCase() !== metadata.serverSha256) {
  throw new Error('Plugin or server JAR differs from workspace.json')
}
const resource = spawnSync('tar', ['-xOf', jarPath, 'mysql/V1__initial_schema.sql'], { encoding: 'utf8' })
if (resource.status !== 0 || !resource.stdout.startsWith('-- @table invitecode_players')) {
  throw new Error('Cannot read the bundled MySQL schema resource')
}
const checksum = hash(Buffer.from(resource.stdout, 'utf8'))
const firstTable = resource.stdout.match(/^-- @table invitecode_players\s*\r?\n([\s\S]*?)(?=^-- @table invitecode_metadata)/m)
if (!firstTable) throw new Error('Bundled MySQL schema has no players table')
const playersDdl = firstTable[1].trim().replace(/;$/, '')
const sentinel = '00000000-0000-0000-0000-000000000012'

function sql(statement) {
  const result = spawnSync(mysqlExe, [
    '--host=127.0.0.1', '--port=63384', '--protocol=TCP', '--user=root',
    '--batch', '--skip-column-names', '--execute', statement
  ], {
    encoding: 'utf8',
    env: { ...process.env, MYSQL_PWD: process.env.INVITECODE_TEST_MYSQL_ROOT_PASSWORD },
    maxBuffer: 1024 * 1024
  })
  if (result.error || result.status !== 0) {
    throw new Error(`Isolated MySQL fixture query failed: ${result.error?.message || result.stderr}`)
  }
  return result.stdout.trim()
}
function db(statement) { return sql(`USE ${database}; ${statement}`) }
function state(expectedState, expectedTables) {
  const summary = db("SELECT CONCAT(version, ':', state, ':', checksum) FROM invitecode_schema_migrations")
  if (summary !== `1:${expectedState}:${checksum}`) throw new Error(`Unexpected migration ledger: ${summary}`)
  const tables = Number(sql(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${database}'`))
  if (tables !== expectedTables) throw new Error(`Expected ${expectedTables} tables, got ${tables}`)
  const player = db(`SELECT last_known_name FROM invitecode_players WHERE player_id='${sentinel}'`)
  if (player !== 'MigrationSentinel') throw new Error('Sentinel player was lost')
  if (expectedState === 'APPLIED') {
    const audits = Number(db("SELECT COUNT(*) FROM invitecode_audit_records WHERE action='STORAGE_MIGRATED'"))
    if (audits !== 1) throw new Error(`Expected one initialization audit, got ${audits}`)
  }
}

const mise = process.env.MISE_EXE || 'mise'
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
async function until(check, label, timeout = 120000) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const result = check()
    if (result) return result
    await delay(100)
  }
  throw new Error(`Timed out: ${label}`)
}
function start() {
  const serverEnvironment = { ...process.env }
  delete serverEnvironment.INVITECODE_TEST_MYSQL_ROOT_PASSWORD
  delete serverEnvironment.MYSQL_PWD
  const child = spawn(mise, ['exec', `java@${metadata.javaAlias}`, '--', 'java',
    '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
    '-Xms512M', '-Xmx1G', '-jar', 'server.jar', 'nogui'],
  { cwd: instancePath, stdio: ['pipe', 'pipe', 'pipe'], env: serverEnvironment })
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
    if (server.child.exitCode !== null) throw new Error(`Server exited before ready: ${server.child.exitCode}`)
    return /Done \(/.test(server.output) &&
      (phase === 'ACTIVE' ? /InviteCode \S+ 已啟用/.test(server.output) : /已進入維護模式/.test(server.output))
  }, `Paper ${phase}`)
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
    await until(() => server.child.exitCode !== null, 'Paper stop', 30000)
    if (server.child.exitCode !== 0) throw new Error(`Paper exit: ${server.child.exitCode}`)
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
    if (sql(`SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${database}'`) !== '0') {
      throw new Error(`Test database already exists: ${database}`)
    }
    sql(`CREATE DATABASE ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`)
    sql(`GRANT ALL PRIVILEGES ON ${database}.* TO 'invitecode_test'@'127.0.0.1'`)
    db(`CREATE TABLE invitecode_schema_migrations (
      version INT NOT NULL PRIMARY KEY,
      checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
      state VARCHAR(16) CHARACTER SET ascii NOT NULL
    ) ENGINE=InnoDB`)
    db(`INSERT INTO invitecode_schema_migrations(version,checksum,state) VALUES (1,'${checksum}','APPLYING')`)
    db(playersDdl)
    db(`INSERT INTO invitecode_players(player_id,first_seen_at,last_known_name) VALUES
      ('${sentinel}','2026-09-24T00:00:00Z','MigrationSentinel')`)
    db('ALTER TABLE invitecode_players MODIFY last_known_name VARCHAR(32) NULL')
    state('APPLYING', 2)

    fs.mkdirSync(dataPath, { recursive: true })
    fs.writeFileSync(path.join(dataPath, 'storage.yml'), `config-version: 1
backend: MYSQL
sqlite:
  file: invitecode.db
json:
  file: invitecode.json
mysql:
  host: 127.0.0.1
  port: 63384
  database: ${database}
  username: invitecode_test
  password: "\${INVITECODE_DB_PASSWORD}"
  network-secret: "\${INVITECODE_NETWORK_SECRET}"
  connection-timeout: 5s
  tls-mode: REQUIRED
`, 'utf8')

    server = start()
    await ready(server, 'MAINTENANCE')
    await command(server, 'ic admin doctor', /Runtime=MAINTENANCE/)
    await stop(server)
    state('APPLYING', 2)

    db('ALTER TABLE invitecode_players MODIFY last_known_name VARCHAR(64) NULL')
    db(`UPDATE invitecode_schema_migrations SET checksum='${'0'.repeat(64)}' WHERE version=1`)
    server = start()
    await ready(server, 'MAINTENANCE')
    await command(server, 'ic admin doctor', /Runtime=MAINTENANCE/)
    await stop(server)
    const badChecksum = db('SELECT checksum FROM invitecode_schema_migrations WHERE version=1')
    if (badChecksum !== '0'.repeat(64)) throw new Error('Checksum was unexpectedly changed')
    if (Number(sql(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${database}'`)) !== 2) {
      throw new Error('Checksum mismatch created tables')
    }

    db(`UPDATE invitecode_schema_migrations SET checksum='${checksum}' WHERE version=1`)
    server = start()
    await ready(server, 'ACTIVE')
    await command(server, 'ic admin doctor', /Runtime=ACTIVE/)
    await stop(server)
    state('APPLIED', 13)

    server = start()
    await ready(server, 'ACTIVE')
    await command(server, 'ic admin doctor', /Runtime=ACTIVE/)
    await stop(server)
    state('APPLIED', 13)
    if (fs.existsSync(path.join(dataPath, 'anti-abuse.key')) || fs.existsSync(path.join(dataPath, 'invitecode.db'))) {
      throw new Error('MySQL mode unexpectedly created local SQLite files')
    }
    process.stdout.write(JSON.stringify({ result: 'PASS', scenario: 'mysql-initialization-recovery',
      instance: metadata.instance, database, pluginSha256: metadata.pluginSha256,
      checks: ['malformed-table-fails-closed', 'checksum-mismatch-fails-closed',
        'applying-ledger-resumes', 'sentinel-preserved', 'applied-ledger-durable', 'no-local-secret'] }) + '\n')
  } finally {
    if (server) await stop(server)
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
