package io.github.command1264.invitecode.storage.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.MysqlConfiguration
import java.util.UUID

class MysqlInviteCodeStorage private constructor(
    private val pool: HikariDataSource,
    private val delegate: JdbcInviteCodeStorage,
) : InviteCodeStorage by delegate {
    override fun close() = pool.close()
    fun previewAnonymization(playerId: UUID): AnonymizationPreview = delegate.previewAnonymization(playerId)
    fun anonymizePlayer(playerId: UUID): AnonymizationResult = delegate.anonymizePlayer(playerId)

    companion object {
        fun open(pool: HikariDataSource, guard: BlockingOperationGuard, reservationKey: CodeReservationKey? = null): MysqlInviteCodeStorage =
            MysqlInviteCodeStorage(pool, JdbcInviteCodeStorage.openMysql(pool, guard, reservationKey))

        fun createPool(config: MysqlConfiguration, password: String): HikariDataSource {
            val hikari = HikariConfig().apply {
                jdbcUrl = "jdbc:mysql://${config.host}:${config.port}/${config.database}" +
                    "?sslMode=${config.tlsMode.name}&connectionTimeZone=UTC"
                username = config.username
                this.password = password
                driverClassName = "com.mysql.cj.jdbc.Driver"
                poolName = "InviteCode-MySQL"
                maximumPoolSize = 8
                minimumIdle = 0
                connectionTimeout = config.connectionTimeout.toMillis()
                validationTimeout = minOf(config.connectionTimeout.toMillis(), 5_000L)
                initializationFailTimeout = config.connectionTimeout.toMillis()
            }
            try {
                return HikariDataSource(hikari)
            } catch (error: Exception) {
                throw MysqlStorageException(
                    "MySQL connection pool could not start at ${config.host}:${config.port}/${config.database}\n" +
                        "check server availability, TLS and credentials.",
                )
            }
        }
    }
}
