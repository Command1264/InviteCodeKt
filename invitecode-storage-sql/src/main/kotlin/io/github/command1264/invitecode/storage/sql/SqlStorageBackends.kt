package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.StorageBackendDescriptor

object SqliteStorageBackend : StorageBackendDescriptor {
    override val id: String = "sqlite"
    override val productionReady: Boolean = true
}

object MysqlStorageBackend : StorageBackendDescriptor {
    override val id: String = "mysql"
    override val productionReady: Boolean = true
}
