const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')
const { assertAgentInstance } = require('./instance-boundary.cjs')

function fixture() {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'invitecode-boundary-'))
  const instancesRoot = path.join(temporary, 'InstanceHarbor', 'instances')
  const instancePath = path.join(instancesRoot, 'Agent', 'InviteCode', 'issue12-test')
  fs.mkdirSync(instancePath, { recursive: true })
  return {
    temporary,
    instancesRoot,
    instancePath,
    metadata: {
      schemaVersion: 2,
      audience: 'Agent',
      project: 'InviteCode',
      instance: 'issue12-test',
      pluginFile: 'InviteCode.jar'
    }
  }
}

test('accepts a physical Agent/InviteCode issue12 instance', t => {
  const f = fixture()
  t.after(() => fs.rmSync(f.temporary, { recursive: true, force: true }))
  assert.equal(assertAgentInstance(f.instancePath, f.metadata, f.instancesRoot), f.instancePath)
})

test('rejects Human and Legacy directories even when renamed issue12', t => {
  const f = fixture()
  t.after(() => fs.rmSync(f.temporary, { recursive: true, force: true }))
  for (const audience of ['Human', 'Legacy']) {
    const candidate = path.join(f.instancesRoot, audience, 'InviteCode', 'issue12-test')
    fs.mkdirSync(candidate, { recursive: true })
    assert.throws(() => assertAgentInstance(candidate, f.metadata, f.instancesRoot), /Agent instance/)
  }
})

test('rejects incorrect metadata and unsafe plugin filenames', t => {
  const f = fixture()
  t.after(() => fs.rmSync(f.temporary, { recursive: true, force: true }))
  for (const patch of [
    { schemaVersion: 1 }, { audience: 'Human' }, { project: 'Other' }, { instance: 'issue12-other' },
    { pluginFile: '..\\InviteCode.jar' }, { pluginFile: 'subdir/InviteCode.jar' },
    { pluginFile: 'InviteCode.jar:stream' }, { pluginFile: `${'a'.repeat(61)}.jar` }
  ]) {
    assert.throws(() => assertAgentInstance(f.instancePath, { ...f.metadata, ...patch }, f.instancesRoot), /Agent instance|pluginFile/)
  }
})

test('rejects a symlink or junction escaping the Agent boundary', t => {
  const f = fixture()
  t.after(() => fs.rmSync(f.temporary, { recursive: true, force: true }))
  const human = path.join(f.instancesRoot, 'Human', 'InviteCode', 'issue12-test')
  fs.mkdirSync(human, { recursive: true })
  fs.rmSync(f.instancePath, { recursive: true })
  try {
    fs.symlinkSync(human, f.instancePath, process.platform === 'win32' ? 'junction' : 'dir')
  } catch (error) {
    if (process.platform === 'win32' && ['EPERM', 'EACCES', 'UNKNOWN'].includes(error.code)) {
      t.skip(`junction creation unavailable: ${error.code}`)
      return
    }
    throw error
  }
  assert.throws(() => assertAgentInstance(f.instancePath, f.metadata, f.instancesRoot), /physical path|Agent instance/)
})

test('rejects a redirected instances root even with a matching Agent subtree', t => {
  const f = fixture()
  t.after(() => fs.rmSync(f.temporary, { recursive: true, force: true }))
  const redirected = path.join(f.temporary, 'outside')
  fs.mkdirSync(path.join(redirected, 'Agent', 'InviteCode', 'issue12-test'), { recursive: true })
  fs.rmSync(f.instancesRoot, { recursive: true })
  fs.symlinkSync(redirected, f.instancesRoot, process.platform === 'win32' ? 'junction' : 'dir')
  assert.throws(() => assertAgentInstance(f.instancePath, f.metadata, f.instancesRoot), /root.*link|root.*junction/)
})
