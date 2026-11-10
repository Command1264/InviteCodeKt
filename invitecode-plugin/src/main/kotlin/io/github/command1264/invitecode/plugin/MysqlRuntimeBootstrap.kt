package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.core.MysqlConfiguration
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.CodeReservationKey
import io.github.command1264.invitecode.storage.sql.MysqlInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.MysqlSchemaMigrator
import io.github.command1264.invitecode.storage.sql.MysqlStorageException
import java.time.Instant
import java.sql.SQLException
import java.util.UUID

internal data class MysqlBootstrapResult(
    val storage: MysqlInviteCodeStorage,
    val networkHasher: NetworkAddressHasher,
    val initialized: Boolean,
)

internal class MysqlRuntimeBootstrap(
    private val config: MysqlConfiguration,
    private val guard: BlockingOperationGuard,
    private val environment: (String) -> String? = System::getenv,
    private val now: () -> Instant = Instant::now,
    private val explicitMigration: Boolean = false,
) {
    fun initializeOrOpen(): MysqlBootstrapResult {
        guard.checkAllowed()
        val password = if (config.passwordSetting.startsWith("${'$'}{")) {
            resolve(config.passwordSetting, "mysql.password")
        } else config.passwordSetting
        val secret = resolve(config.networkSecretReference, "mysql.network-secret")
        val hasher = NetworkAddressHasher.fromBase64(secret)
        var storage: MysqlInviteCodeStorage? = null
        val pool = try {
            MysqlInviteCodeStorage.createPool(config, password)
        } catch (error: Exception) {
            hasher.close()
            throw error
        }
        try {
            return MysqlTransferCoordinator.withLock(pool) { connection ->
                val catalog = requireNotNull(connection.catalog) { "MySQL database is not selected." }
                connection.metaData.getTables(catalog, null, "invitecode_transfer_guard", arrayOf("TABLE")).use { tables ->
                    if (tables.next()) throw MysqlStorageException(
                        "MySQL backend transfer is incomplete\nkeep InviteCode stopped, inspect the destination " +
                            "and restore or restart the offline transfer before enabling this database.",
                    )
                }
                val initializedBefore = MysqlSchemaMigrator.isInitialized(connection)
                if (initializedBefore && !explicitMigration) {
                    MysqlSchemaMigrator.requireCurrent(connection)
                } else {
                    MysqlSchemaMigrator(pool, guard).migrate()
                }
                MysqlSchemaMigrator.bindNetworkKey(connection, hasher.keyId)
                val activeStorage = MysqlInviteCodeStorage.open(
                    pool, guard, CodeReservationKey(hasher.keyId, hasher::hashReservedCode),
                )
                storage = activeStorage
                if (!initializedBefore) {
                    activeStorage.saveAuditRecord(
                        AuditRecord(
                            UUID.randomUUID(), InvitationAuditAction.STORAGE_MIGRATED,
                            "system", "automatic first MySQL initialization", now(),
                        ),
                    )
                }
                MysqlBootstrapResult(activeStorage, hasher, !initializedBefore)
            }
        } catch (error: Exception) {
            storage?.close() ?: pool.close()
            hasher.close()
            if (error is MysqlStorageException) throw error
            val diagnostic = if (error is SQLException) {
                "SQLState ${error.sqlState ?: "unknown"}, code ${error.errorCode}"
            } else error.javaClass.simpleName
            throw MysqlStorageException(
                "MySQL initialization failed at ${config.host}:${config.port}/${config.database}\n" +
                    "check connectivity, schema and shared network secret ($diagnostic).",
            )
        }
    }

    private fun resolve(reference: String, field: String): String {
        val name = reference.substring(2, reference.length - 1)
        return environment(name)?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("$field environment variable $name is missing or empty.")
    }
}
