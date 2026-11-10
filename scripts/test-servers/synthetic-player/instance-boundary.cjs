const fs = require('node:fs')
const path = require('node:path')

const DEFAULT_INSTANCES_ROOT = 'D:\\Code\\Java\\Plugin\\InstanceHarbor\\instances'

function assertAgentInstance(instancePath, metadata, instancesRoot = DEFAULT_INSTANCES_ROOT) {
  const requestedPath = path.resolve(instancePath)
  let physicalPath
  let physicalRoot
  try {
    if (fs.lstatSync(instancesRoot).isSymbolicLink()) {
      throw new Error('instances root must not be a link or junction')
    }
    physicalPath = fs.realpathSync(requestedPath)
    physicalRoot = fs.realpathSync(instancesRoot)
  } catch (error) {
    throw new Error(`Expected an existing physical Agent instance path: ${error.message}`)
  }
  const relative = path.relative(physicalRoot, physicalPath)
  const parts = relative.split(path.sep)
  const instance = path.basename(physicalPath)
  if (relative.startsWith('..') || path.isAbsolute(relative) || parts.length !== 3 ||
      parts[0] !== 'Agent' || parts[1] !== 'InviteCode' || parts[2] !== instance ||
      !/^issue12-[a-z0-9-]+$/.test(instance) ||
      !metadata || metadata.schemaVersion !== 2 || metadata.audience !== 'Agent' ||
      metadata.project !== 'InviteCode' || metadata.instance !== instance) {
    throw new Error('Expected an Agent instance at the physical InstanceHarbor/instances/Agent/InviteCode/issue12-* path')
  }
  if (metadata.pluginFile !== undefined &&
      (typeof metadata.pluginFile !== 'string' || !metadata.pluginFile ||
       path.basename(metadata.pluginFile) !== metadata.pluginFile || /[\\/]/.test(metadata.pluginFile) ||
       /[:]/.test(metadata.pluginFile) || metadata.pluginFile.length > 64 ||
       metadata.pluginFile === '.' || metadata.pluginFile === '..')) {
    throw new Error('workspace.json pluginFile must be a safe basename')
  }
  return physicalPath
}

module.exports = { assertAgentInstance }
