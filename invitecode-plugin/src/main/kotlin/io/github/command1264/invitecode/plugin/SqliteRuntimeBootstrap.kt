package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

internal data class SqliteBootstrapResult(
    val storage: SqliteInviteCodeStorage,
    val networkHasher: NetworkAddressHasher,
    val initialized: Boolean,
)

internal class SqliteRuntimeBootstrap(
    private val database: Path,
    private val networkSecret: Path,
    private val blockingOperationGuard: BlockingOperationGuard,
    private val now: () -> Instant = Instant::now,
) {
    fun initializeOrOpen(): SqliteBootstrapResult {
        require(!Files.exists(database) || Files.isRegularFile(database)) {
            "SQLite path ${database.toAbsolutePath()} exists but is not a regular file."
        }
        val initialize = !Files.exists(database)
        val secretExisted = Files.isRegularFile(networkSecret)
        var storage: SqliteInviteCodeStorage? = null
        var hasher: NetworkAddressHasher? = null
        try {
            if (initialize) {
                hasher = NetworkAddressHasher.loadOrCreate(networkSecret)
                SqliteSchemaMigrator(database, blockingOperationGuard).migrate()
            } else {
                hasher = NetworkAddressHasher.loadExisting(networkSecret)
            }
            storage = SqliteInviteCodeStorage.open(database, blockingOperationGuard)
            storage.purgeNetworkHashesWithDifferentKey(hasher.keyId)
            if (initialize) {
                storage.saveAuditRecord(
                    AuditRecord(
                        UUID.randomUUID(),
                        InvitationAuditAction.STORAGE_MIGRATED,
                        "system",
                        "automatic first initialization",
                        now(),
                    ),
                )
            }
            return SqliteBootstrapResult(storage, hasher, initialize)
        } catch (error: Exception) {
            storage?.close()
            hasher?.close()
            if (initialize) {
                cleanupCreatedFile(database.resolveSibling("${database.fileName}-wal"), error)
                cleanupCreatedFile(database.resolveSibling("${database.fileName}-shm"), error)
                cleanupCreatedFile(database, error)
                if (!secretExisted) cleanupCreatedFile(networkSecret, error)
            }
            throw error
        }
    }

    private fun cleanupCreatedFile(path: Path, original: Exception) {
        try {
            Files.deleteIfExists(path)
        } catch (cleanupError: Exception) {
            original.addSuppressed(cleanupError)
        }
    }
}
