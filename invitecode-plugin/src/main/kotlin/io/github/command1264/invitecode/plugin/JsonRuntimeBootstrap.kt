package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.json.JsonInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
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
        val secretExisted = Files.isRegularFile(networkSecret)
        var storage: JsonInviteCodeStorage? = null
        var hasher: NetworkAddressHasher? = null
        try {
            // Acquire the JSON writer lock before touching the shared anti-abuse key.
            storage = JsonInviteCodeStorage.open(file, guard)
            hasher = if (initialize) NetworkAddressHasher.loadOrCreate(networkSecret)
                else NetworkAddressHasher.loadExisting(networkSecret)
            storage.purgeNetworkHashesWithDifferentKey(hasher.keyId)
            if (initialize) {
                storage.saveAuditRecord(AuditRecord(
                    UUID.randomUUID(), InvitationAuditAction.STORAGE_MIGRATED,
                    "system", "automatic first initialization", now(),
                ))
            }
            return JsonBootstrapResult(storage, hasher, initialize)
        } catch (error: Exception) {
            val ownedStorage = storage != null
            storage?.close()
            hasher?.close()
            if (initialize && ownedStorage) {
                listOf(file, file.resolveSibling("${file.fileName}.previous")).forEach { created ->
                    try { Files.deleteIfExists(created) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                }
                if (!secretExisted) {
                    try { Files.deleteIfExists(networkSecret) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                }
            }
            throw error
        }
    }
}
