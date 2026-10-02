package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import io.github.command1264.invitecode.storage.sql.SqliteSnapshotAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant
import java.util.Base64
import java.util.UUID

class OfflineStorageTransferMysqlIntegrationTest {
    @TempDir lateinit var directory: Path
    private val guard = BlockingOperationGuard { }

    @Test
    fun `all four MySQL directions preserve pending reward through repeated cutovers`() {
        val host = System.getenv("INVITECODE_TEST_MYSQL_HOST")
        val password = System.getenv("INVITECODE_TEST_MYSQL_PASSWORD")
        assumeTrue(!host.isNullOrBlank() && !password.isNullOrBlank())
        val port = System.getenv("INVITECODE_TEST_MYSQL_PORT")?.toIntOrNull() ?: 3306
        val user = System.getenv("INVITECODE_TEST_MYSQL_USER") ?: "root"
        val adminUrl = "jdbc:mysql://$host:$port/?sslMode=REQUIRED&connectionTimeZone=UTC"
        val firstDb = databaseName()
        val secondDb = databaseName()
        val keyBytes = ByteArray(32) { 7 }
        val key = directory.resolve("anti-abuse.key")
        Files.write(key, keyBytes)
        val secret = Base64.getEncoder().encodeToString(keyBytes)
        val lookup: (String) -> String? = { name ->
            when (name) {
                "TEST_PASSWORD" -> password
                "TEST_SECRET" -> secret
                else -> null
            }
        }
        val player = StoredPlayer(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"), "Alice")
        val delivery = StoredRewardDelivery(
            UUID.randomUUID(), null, "welcome", RewardDeliveryStatus.PENDING,
            "welcome:${player.id}", player.firstSeenAt, player.firstSeenAt,
        )
        val action = StoredRewardAction(
            UUID.randomUUID(), delivery.id, null, player.id, 0, "MESSAGE", "hello",
            RewardActionStatus.PENDING, "${delivery.idempotencyKey}:0", player.firstSeenAt, player.firstSeenAt,
        )
        val source = directory.resolve("source.db")
        val json = directory.resolve("intermediate.json")
        val result = directory.resolve("result.db")
        SqliteSchemaMigrator(source, guard).migrate()
        SqliteInviteCodeStorage.open(source, guard).use { storage ->
            storage.savePlayer(player)
            storage.saveRewardDelivery(delivery, listOf(action))
        }
        val invitee = UUID.randomUUID()
        val invitation = UUID.randomUUID()
        val keyId = NetworkAddressHasher.fromBase64(secret).use { it.keyId }
        SqliteSnapshotAccess.withConnection(source, guard) { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
            fun insert(sql: String, vararg values: Any?) = connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
            insert("INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name) VALUES (?, ?, ?)",
                invitee.toString(), "2026-01-02T00:00:00Z", "Bob")
            insert("INSERT INTO invitecode_codes(code, inviter_id, activated_at) VALUES (?, ?, ?)",
                "A1B2C3", player.id.toString(), "2026-01-01T00:00:00Z")
            insert("INSERT INTO invitecode_invitations(invitation_id, inviter_id, inviter_eligibility_generation, " +
                "invitee_id, invitee_eligibility_generation, status, claimed_at, qualified_at, " +
                "qualification_policy_id, qualification_revision) VALUES (?, ?, 0, ?, 0, 'QUALIFIED', ?, ?, ?, ?)",
                invitation.toString(), player.id.toString(), invitee.toString(),
                "2026-01-03T00:00:00Z", "2026-01-04T00:00:00Z", "default", "rev-1")
            insert("INSERT INTO invitecode_qualification_facts(fact_id, invitation_id, policy_id, " +
                "qualification_revision, condition_id, outcome, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), invitation.toString(), "default", "rev-1", "playtime", "PASSED", "2026-01-04T00:00:00Z")
            insert("INSERT INTO invitecode_audit_records(audit_id, action, actor, reason, occurred_at, " +
                "invitation_id, player_id, details_kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), "QUALIFIED", "admin", "accepted", "2026-01-04T00:00:00Z",
                invitation.toString(), invitee.toString(), "NONE")
            insert("INSERT INTO invitecode_player_login_days(player_id, login_date) VALUES (?, ?)",
                invitee.toString(), "2026-01-02")
            insert("INSERT INTO invitecode_player_network_hashes(player_id, key_id, network_hash, observed_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?)", player.id.toString(), keyId, "a".repeat(64),
                "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z")
            insert("INSERT INTO invitecode_reserved_code_hashes(code_hash, key_id) VALUES (?, ?)", "b".repeat(64), keyId)
        }
        DriverManager.getConnection(adminUrl, user, password).use { connection ->
            connection.createStatement().use {
                it.execute("CREATE DATABASE $firstDb")
                it.execute("CREATE DATABASE $secondDb")
            }
        }
        try {
            val mysqlOptions = arrayOf(
                "--mysql-host", host!!, "--mysql-port", port.toString(), "--mysql-username", user,
                "--mysql-password-env", "TEST_PASSWORD", "--mysql-secret-env", "TEST_SECRET",
                "--mysql-tls", "REQUIRED", "--stopped", "--apply",
            )
            val mysqlDump = directory.resolve("synthetic-dump.sql")
            // Only exercises the CLI's nonempty-dump gate; restore testing uses real dumps outside this test.
            Files.write(mysqlDump, "-- isolated test dump marker\n".toByteArray(StandardCharsets.UTF_8))
            OfflineStorageTransferMain.execute(
                arrayOf("--from-backend", "sqlite", "--from-file", source.toString(),
                    "--from-secret-file", key.toString(), "--to-backend", "mysql",
                    "--mysql-database", firstDb, "--backup", directory.resolve("source-backup.db").toString()) + mysqlOptions,
                lookup,
            )
            DriverManager.getConnection(
                "jdbc:mysql://$host:$port/$firstDb?sslMode=REQUIRED&connectionTimeZone=UTC", user, password,
            ).use {
                val activeWriter = assertThrows(IllegalArgumentException::class.java) {
                    OfflineStorageTransferMain.execute(
                        arrayOf("--from-backend", "mysql", "--to-backend", "json", "--to-file", json.toString(),
                            "--to-secret-file", key.toString(), "--mysql-database", firstDb) + mysqlOptions.dropLast(1),
                        lookup,
                    )
                }
                assertTrue(activeWriter.message!!.contains("其他 MySQL 連線"))
            }
            OfflineStorageTransferMain.execute(
                arrayOf("--from-backend", "mysql", "--to-backend", "json", "--to-file", json.toString(),
                    "--to-secret-file", key.toString(), "--mysql-database", firstDb,
                    "--backup", mysqlDump.toString()) + mysqlOptions,
                lookup,
            )
            OfflineStorageTransferMain.execute(
                arrayOf("--from-backend", "json", "--from-file", json.toString(),
                    "--from-secret-file", key.toString(), "--to-backend", "mysql",
                    "--mysql-database", secondDb, "--backup", directory.resolve("json-backup.json").toString()) + mysqlOptions,
                lookup,
            )
            OfflineStorageTransferMain.execute(
                arrayOf("--from-backend", "mysql", "--to-backend", "sqlite", "--to-file", result.toString(),
                    "--to-secret-file", key.toString(), "--mysql-database", secondDb,
                    "--backup", mysqlDump.toString()) + mysqlOptions,
                lookup,
            )
            SqliteInviteCodeStorage.open(result, guard).use { storage ->
                assertEquals(player, storage.player(player.id))
                assertEquals(RewardActionStatus.PENDING, storage.rewardAction(action.id)?.status)
                assertEquals(1, storage.rewardActionTransitions(action.id).size)
            }
            SqliteSnapshotAccess.withConnection(result, guard) { connection ->
                listOf("invitecode_codes", "invitecode_invitations", "invitecode_qualification_facts",
                    "invitecode_audit_records", "invitecode_player_login_days",
                    "invitecode_player_network_hashes", "invitecode_reserved_code_hashes").forEach { table ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT COUNT(*) FROM $table").use { rows ->
                            rows.next()
                            assertEquals(1, rows.getInt(1), table)
                        }
                    }
                }
            }
        } finally {
            DriverManager.getConnection(adminUrl, user, password).use { connection ->
                connection.createStatement().use {
                    it.execute("DROP DATABASE IF EXISTS $firstDb")
                    it.execute("DROP DATABASE IF EXISTS $secondDb")
                }
            }
        }
    }

    private fun databaseName(): String = "invitecode_test_${UUID.randomUUID().toString().replace("-", "")}"
}
