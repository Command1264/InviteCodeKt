package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.json.JsonInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.CodeReservationKey
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

internal data class JsonBootstrapResult(
    val storage: JsonInviteCodeStorage,
    val networkHasher: NetworkAddressHasher,
    val initialized: Boolean,
)

internal class JsonRuntimeBootstrap(
    private val file: Path,
    private val networkSecret: Path,
    private val guard: BlockingOperationGuard,
    private val now: () -> Instant = Instant::now,
) {
    fun initializeOrOpen(): JsonBootstrapResult {
        require(!Files.exists(file) || Files.isRegularFile(file)) {
            "JSON storage path ${file.toAbsolutePath()} exists but is not a regular file."
        }
        val initialize = !Files.exists(file)
        var storage: JsonInviteCodeStorage? = null
        var hasher: NetworkAddressHasher? = null
        try {
            storage = JsonInviteCodeStorage.openWithKeyProvider(file, guard) {
                val activeHasher = if (initialize) NetworkAddressHasher.loadOrCreate(networkSecret)
                    else NetworkAddressHasher.loadExisting(networkSecret)
                hasher = activeHasher
                CodeReservationKey(activeHasher.keyId, activeHasher::hashReservedCode)
            }
            val readyHasher = requireNotNull(hasher)
            storage.purgeNetworkHashesWithDifferentKey(readyHasher.keyId)
            if (initialize) {
                storage.saveAuditRecord(AuditRecord(
                    UUID.randomUUID(), InvitationAuditAction.STORAGE_MIGRATED,
                    "system", "automatic first initialization", now(),
                ))
            }
            return JsonBootstrapResult(storage, readyHasher, initialize)
        } catch (error: Exception) {
            storage?.close()
            hasher?.close()
            // Preserve a possibly initialized snapshot/key for inspection; another process may acquire
            // the JSON lock immediately after close, so deleting them here could race that process.
            throw error
        }
    }
}
