package io.github.command1264.invitecode.storage.sql

import com.zaxxer.hikari.HikariDataSource
import io.github.command1264.invitecode.core.ClaimConfirmation
import io.github.command1264.invitecode.core.ClaimEligibilityPolicy
import io.github.command1264.invitecode.core.InvitationClaimResult
import io.github.command1264.invitecode.core.InvitationClaimService
import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeFormats
import io.github.command1264.invitecode.core.InviteCodeGenerator
import io.github.command1264.invitecode.core.InviteCodeLifecycleService
import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.MysqlConfiguration
import io.github.command1264.invitecode.core.MysqlTlsMode
import io.github.command1264.invitecode.core.PlayerNetworkHash
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.core.StoredPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MysqlStorageContractTest : StorageContractTest() {
    override fun withStorage(test: (InviteCodeStorage) -> Unit) = withMysqlSchema { _, pool ->
        val guard = BlockingOperationGuard { }
        MysqlSchemaMigrator(pool, guard).migrate()
        MysqlInviteCodeStorage.open(pool, guard).use(test)
    }
}

class MysqlIntegrationTest {
    @Test
    fun `interrupted DDL resumes only after checking existing tables`() = withMysqlSchema { _, pool ->
        val guard = BlockingOperationGuard { }
        assertThrows(IllegalStateException::class.java) {
            MysqlSchemaMigrator(pool, guard) { name ->
                if (name == "invitecode_players") error("simulated power loss after committed DDL")
            }.migrate()
        }
        assertEquals(1, MysqlSchemaMigrator(pool, guard).migrate())
        pool.connection.use(MysqlSchemaMigrator::requireCurrent)
    }

    @Test
    fun `changed table blocks interrupted migration`() = withMysqlSchema { _, pool ->
        val guard = BlockingOperationGuard { }
        assertThrows(IllegalStateException::class.java) {
            MysqlSchemaMigrator(pool, guard) { name ->
                if (name == "invitecode_players") error("simulated interruption")
            }.migrate()
        }
        pool.connection.use { connection ->
            connection.createStatement().use { it.execute("ALTER TABLE invitecode_players MODIFY last_known_name VARCHAR(32) NULL") }
        }
        assertThrows(MysqlStorageException::class.java) { MysqlSchemaMigrator(pool, guard).migrate() }
    }

    @Test
    fun `nonempty database without ledger is never treated as fresh`() = withMysqlSchema { _, pool ->
        pool.connection.use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE unrelated_data (id INT PRIMARY KEY) ENGINE=InnoDB") }
        }
        assertThrows(MysqlStorageException::class.java) {
            MysqlSchemaMigrator(pool, BlockingOperationGuard { }).migrate()
        }
    }

    @Test
    fun `table collation drift blocks reopening`() = withMysqlSchema { _, pool ->
        MysqlSchemaMigrator(pool, BlockingOperationGuard { }).migrate()
        pool.connection.use { connection ->
            connection.createStatement().use {
                it.execute("ALTER TABLE invitecode_metadata COLLATE utf8mb4_bin")
            }
        }
        assertThrows(MysqlStorageException::class.java) {
            pool.connection.use(MysqlSchemaMigrator::requireCurrent)
        }
    }

    @Test
    fun `fractional timestamps preserve network hash expiry ordering`() = withMysqlSchema { _, pool ->
        val guard = BlockingOperationGuard { }
        MysqlSchemaMigrator(pool, guard).migrate()
        MysqlInviteCodeStorage.open(pool, guard).use { storage ->
            val player = UUID.randomUUID()
            val observed = Instant.parse("2026-09-22T00:00:00Z")
            storage.savePlayer(StoredPlayer(player, observed))
            storage.recordNetworkHash(
                PlayerNetworkHash(player, "a".repeat(64), "b".repeat(64), observed, observed.plusNanos(500_000_000)),
            )
            assertEquals(0, storage.purgeExpiredNetworkHashes(observed.plusNanos(100_000_000)))
            assertEquals(1, storage.purgeExpiredNetworkHashes(observed.plusSeconds(1)))
        }
    }

    @Test
    fun `duplicate reward idempotency key cannot mutate another delivery`() = withMysqlSchema { _, pool ->
        val guard = BlockingOperationGuard { }
        MysqlSchemaMigrator(pool, guard).migrate()
        MysqlInviteCodeStorage.open(pool, guard).use { storage ->
            val now = Instant.parse("2026-09-22T00:00:00Z")
            val original = StoredRewardDelivery(
                UUID.randomUUID(), null, "reward", RewardDeliveryStatus.PENDING,
                "same-idempotency-key", now, now,
            )
            val recipient = UUID.randomUUID()
            storage.savePlayer(StoredPlayer(recipient, now))
            val action = StoredRewardAction(
                UUID.randomUUID(), original.id, null, recipient, 0, "MESSAGE", "hello",
                RewardActionStatus.PENDING, "original-action", now, now,
            )
            storage.saveRewardDelivery(original, listOf(action))
            val conflicting = original.copy(id = UUID.randomUUID(), status = RewardDeliveryStatus.PROCESSING)
            val conflictingAction = action.copy(
                id = UUID.randomUUID(), rewardDeliveryId = conflicting.id, idempotencyKey = "conflicting-action",
            )
            assertThrows(Exception::class.java) { storage.saveRewardDelivery(conflicting, listOf(conflictingAction)) }
            assertEquals(original, storage.rewardDelivery(original.id))
            assertEquals(null, storage.rewardDelivery(conflicting.id))
        }
    }

    @Test
    fun `two independent instances serialize competing claims`() = withMysqlSchema { config, pool ->
        val guard = BlockingOperationGuard { }
        MysqlSchemaMigrator(pool, guard).migrate()
        val first = MysqlInviteCodeStorage.open(pool, guard)
        val secondPool = MysqlInviteCodeStorage.createPool(
            config, System.getenv("INVITECODE_TEST_MYSQL_PASSWORD") ?: "",
        )
        val second = MysqlInviteCodeStorage.open(secondPool, guard)
        val inviters = listOf(UUID.randomUUID(), UUID.randomUUID())
        val invitee = UUID.randomUUID()
        val now = Instant.parse("2026-09-22T00:00:00Z")
        (inviters + invitee).forEach { first.savePlayer(StoredPlayer(it, now)) }
        val codes = listOf("A25981", "0B35E7").map { InviteCode.parse(it, InviteCodeFormats.colorHex) }
        inviters.zip(codes).forEach { (id, code) ->
            InviteCodeLifecycleService(first, InviteCodeGenerator { code }, 1).assign(id, now)
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val attempts = inviters.indices.map { index ->
                executor.submit<InvitationClaimResult> {
                    start.await()
                    InvitationClaimService(
                        if (index == 0) first else second,
                        ClaimEligibilityPolicy(Duration.ofHours(24)),
                        UUID::randomUUID,
                    ).claim(
                        ClaimConfirmation.present(
                            invitee, inviters[index], "Inviter", codes[index],
                            now.plusSeconds(30), Duration.ofSeconds(30),
                        ).confirm(invitee, now.plusSeconds(60)),
                    )
                }
            }
            start.countDown()
            val results = attempts.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is InvitationClaimResult.Claimed })
            assertEquals(1, results.count { it is InvitationClaimResult.Rejected })
        } finally {
            executor.shutdownNow()
            second.close()
            first.close()
        }
    }

    @Test
    fun `another network key cannot join the same database`() = withMysqlSchema { _, pool ->
        MysqlSchemaMigrator(pool, BlockingOperationGuard { }).migrate()
        pool.connection.use { MysqlSchemaMigrator.bindNetworkKey(it, "a".repeat(64)) }
        assertThrows(MysqlStorageException::class.java) {
            pool.connection.use { MysqlSchemaMigrator.bindNetworkKey(it, "b".repeat(64)) }
        }
    }

    @Test
    fun `pool exhaustion has a bounded redacted error`() = withMysqlSchema { config, _ ->
        val constrained = MysqlInviteCodeStorage.createPool(
            config.copy(connectionTimeout = Duration.ofMillis(500)),
            System.getenv("INVITECODE_TEST_MYSQL_PASSWORD") ?: "",
        )
        try {
            MysqlSchemaMigrator(constrained, BlockingOperationGuard { }).migrate()
            val storage = MysqlInviteCodeStorage.open(constrained, BlockingOperationGuard { })
            val held = List(8) { constrained.connection }
            try {
                val started = System.nanoTime()
                val error = assertThrows(MysqlStorageException::class.java) { storage.player(UUID.randomUUID()) }
                val elapsed = Duration.ofNanos(System.nanoTime() - started)
                assertEquals(true, elapsed < Duration.ofSeconds(3))
                assertEquals(true, error.message.orEmpty().contains("pool capacity"))
            } finally {
                held.forEach { it.close() }
            }
        } finally {
            constrained.close()
        }
    }

    @Test
    fun `unavailable database error excludes password`() = withMysqlSchema { config, _ ->
        val error = assertThrows(MysqlStorageException::class.java) {
            MysqlInviteCodeStorage.createPool(
                config.copy(port = 1, connectionTimeout = Duration.ofMillis(500)), "sensitive-password",
            )
        }
        assertEquals(false, error.message.orEmpty().contains("sensitive-password"))
    }
}

private fun withMysqlSchema(test: (MysqlConfiguration, HikariDataSource) -> Unit) {
    val host = System.getenv("INVITECODE_TEST_MYSQL_HOST")
    assumeTrue(!host.isNullOrBlank(), "MySQL integration requires explicit test server environment variables.")
    val port = System.getenv("INVITECODE_TEST_MYSQL_PORT")?.toIntOrNull() ?: 3306
    val username = System.getenv("INVITECODE_TEST_MYSQL_USER") ?: "root"
    val password = System.getenv("INVITECODE_TEST_MYSQL_PASSWORD") ?: ""
    val schema = "invitecode_test_${UUID.randomUUID().toString().replace("-", "")}"
    check(Regex("invitecode_test_[0-9a-f]{32}").matches(schema))
    val serverUrl = "jdbc:mysql://$host:$port/?sslMode=REQUIRED&connectionTimeZone=UTC"
    DriverManager.getConnection(serverUrl, username, password).use { admin ->
        admin.createStatement().use { it.execute("CREATE DATABASE $schema") }
    }
    try {
        val config = MysqlConfiguration(
            host, port, schema, username, "${'$'}{INVITECODE_TEST_MYSQL_PASSWORD}",
            "${'$'}{INVITECODE_TEST_NETWORK_SECRET}", Duration.ofSeconds(5),
            MysqlTlsMode.REQUIRED,
        )
        MysqlInviteCodeStorage.createPool(config, password).use { pool -> test(config, pool) }
    } finally {
        DriverManager.getConnection(serverUrl, username, password).use { admin ->
            admin.createStatement().use { it.execute("DROP DATABASE $schema") }
        }
    }
}
