package io.github.command1264.invitecode.storage.json

import io.github.command1264.invitecode.core.StorageBackendDescriptor

object JsonStorageBackend : StorageBackendDescriptor {
    override val id: String = "json"
    override val productionReady: Boolean = false
}
