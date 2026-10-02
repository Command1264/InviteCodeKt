package io.github.command1264.invitecode.storage.sql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class OfflineStorageTransferTest {
    @TempDir lateinit var directory: Path
    private val keyId = "a".repeat(64)

    @Test
    fun `sqlite transfer preserves all business rows including rewards transitions and reserved hashes`() {
        val source = database("source.db")
        val destination = database("destination.db")
        SqliteSchemaMigrator(source, BlockingOperationGuard { }).migrate()
        SqliteSchemaMigrator(destination, BlockingOperationGuard { }).migrate()
        seed(source)

        val snapshot = connect(source).use { OfflineStorageTransfer.export(it, StorageDialect.SQLITE, keyId) }
        assertEquals(12, snapshot.tables.size)
        assertEquals(keyId, snapshot.networkKeyId)
        assertEquals(9, snapshot.sourceSchemaVersion)

        connect(destination).use { OfflineStorageTransfer.`import`(it, StorageDialect.SQLITE, snapshot) }
        connect(destination).use { OfflineStorageTransfer.verify(it, StorageDialect.SQLITE, snapshot) }
        connect(destination).use { connection ->
            assertEquals(1, count(connection, "invitecode_reward_deliveries"))
            assertEquals(1, count(connection, "invitecode_reward_actions"))
            assertEquals(2, count(connection, "invitecode_reward_delivery_transitions"))
            assertEquals(2, count(connection, "invitecode_reward_action_transitions"))
            assertEquals(1, count(connection, "invitecode_reserved_code_hashes"))
            assertEquals(1, count(connection, "invitecode_player_network_hashes"))
        }
    }

    @Test
    fun `sqlite import rejects a nonempty destination without changing its rows`() {
        val source = database("nonempty-source.db")
        val destination = database("nonempty-destination.db")
        SqliteSchemaMigrator(source, BlockingOperationGuard { }).migrate()
        SqliteSchemaMigrator(destination, BlockingOperationGuard { }).migrate()
        seed(source)
        connect(destination).use { connection ->
            connection.prepareStatement(
                "INSERT INTO invitecode_players(player_id, first_seen_at) VALUES (?, ?)",
            ).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, "2026-01-01T00:00:00Z")
                statement.executeUpdate()
            }
        }
        val snapshot = connect(source).use { OfflineStorageTransfer.export(it, StorageDialect.SQLITE, keyId) }

        assertThrows(StorageTransferException::class.java) {
            connect(destination).use { OfflineStorageTransfer.`import`(it, StorageDialect.SQLITE, snapshot) }
        }
        connect(destination).use { assertEquals(1, count(it, "invitecode_players")) }
    }

    @Test
    fun `source with a different historical hash key is rejected`() {
        val source = database("wrong-key.db")
        SqliteSchemaMigrator(source, BlockingOperationGuard { }).migrate()
        seed(source)
        val error = assertThrows(StorageTransferException::class.java) {
            connect(source).use { OfflineStorageTransfer.export(it, StorageDialect.SQLITE, "d".repeat(64)) }
        }
        assertEquals(true, error.message.orEmpty().contains("another anti-abuse key"))
    }

    @Test
    fun `sqlite import failure rolls back every inserted table`() {
        val source = database("rollback-source.db")
        val destination = database("rollback-destination.db")
        SqliteSchemaMigrator(source, BlockingOperationGuard { }).migrate()
        SqliteSchemaMigrator(destination, BlockingOperationGuard { }).migrate()
        seed(source)
        val snapshot = connect(source).use { OfflineStorageTransfer.export(it, StorageDialect.SQLITE, keyId) }
        connect(destination).use { connection ->
            connection.createStatement().use {
                it.execute(
                    "CREATE TRIGGER injected_transfer_failure BEFORE INSERT ON invitecode_reserved_code_hashes " +
                        "BEGIN SELECT RAISE(ABORT, 'injected transfer failure'); END",
                )
            }
            assertThrows(StorageTransferException::class.java) {
                OfflineStorageTransfer.`import`(connection, StorageDialect.SQLITE, snapshot)
            }
            BUSINESS_TABLES.forEach { table -> assertEquals(0, count(connection, table)) }
        }
    }

    private fun seed(path: Path) = connect(path).use { connection ->
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        val inviter = UUID.randomUUID().toString()
        val invitee = UUID.randomUUID().toString()
        val invitation = UUID.randomUUID().toString()
        val delivery = UUID.randomUUID().toString()
        val action = UUID.randomUUID().toString()
        fun insert(sql: String, vararg values: Any?) = connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
        insert("INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name, eligibility_generation) VALUES (?, ?, ?, ?)", inviter, "2026-01-01T00:00:00Z", null, 2)
        insert("INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name, eligibility_generation) VALUES (?, ?, ?, ?)", invitee, "2026-01-02T00:00:00Z", "Invitee", 0)
        insert("INSERT INTO invitecode_codes(code, inviter_id, activated_at, retired_at) VALUES (?, ?, ?, ?)", "A1B2C3", inviter, "2026-01-01T00:00:00Z", null)
        insert("""INSERT INTO invitecode_invitations(invitation_id, inviter_id, inviter_eligibility_generation,
            invitee_id, invitee_eligibility_generation, status, claimed_at, qualified_at, qualification_policy_id,
            qualification_revision) VALUES (?, ?, 2, ?, 0, 'QUALIFIED', ?, ?, ?, ?)""",
            invitation, inviter, invitee, "2026-01-03T00:00:00Z", "2026-01-04T00:00:00Z", "default", "rev-1")
        insert("INSERT INTO invitecode_qualification_facts(fact_id, invitation_id, policy_id, qualification_revision, condition_id, outcome, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(), invitation, "default", "rev-1", "playtime", "PASSED", "2026-01-04T00:00:00Z")
        insert("INSERT INTO invitecode_reward_deliveries(delivery_id, invitation_id, reward_id, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            delivery, invitation, "invitee-reward", "DELIVERED", "delivery:$delivery", "2026-01-04T00:00:00Z", "2026-01-05T00:00:00Z")
        insert("INSERT INTO invitecode_reward_actions(action_id, reward_delivery_id, invitation_id, recipient_id, position, action_type, payload, status, idempotency_key, created_at, updated_at, last_error) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            action, delivery, invitation, invitee, 0, "MESSAGE", "welcome", "DELIVERED", "action:$action", "2026-01-04T00:00:00Z", "2026-01-05T00:00:00Z", null)
        insert("INSERT INTO invitecode_audit_records(audit_id, action, actor, reason, occurred_at, invitation_id, player_id, details_kind, previous_inviter_id, new_inviter_id, qualification_policy_id, qualification_revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(), "QUALIFIED", "admin", "accepted", "2026-01-04T00:00:00Z", invitation, invitee, "NONE", null, inviter, "default", "rev-1")
        insert("INSERT INTO invitecode_reward_delivery_transitions(transition_id, delivery_id, previous_status, current_status, occurred_at, actor, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
            101, delivery, null, "PENDING", "2026-01-04T00:00:00Z", null, null)
        insert("INSERT INTO invitecode_reward_delivery_transitions(transition_id, delivery_id, previous_status, current_status, occurred_at, actor, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
            108, delivery, "PENDING", "DELIVERED", "2026-01-05T00:00:00Z", "admin", "completed")
        insert("INSERT INTO invitecode_reward_action_transitions(transition_id, action_id, previous_status, current_status, occurred_at, last_error, actor, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            202, action, null, "PROCESSING", "2026-01-04T00:00:00Z", null, null, null)
        insert("INSERT INTO invitecode_reward_action_transitions(transition_id, action_id, previous_status, current_status, occurred_at, last_error, actor, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            209, action, "PROCESSING", "DELIVERED", "2026-01-05T00:00:00Z", null, "admin", "completed")
        insert("INSERT INTO invitecode_player_login_days(player_id, login_date) VALUES (?, ?)", invitee, "2026-01-02")
        insert("INSERT INTO invitecode_player_network_hashes(player_id, key_id, network_hash, observed_at, expires_at) VALUES (?, ?, ?, ?, ?)",
            inviter, keyId, "b".repeat(64), "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z")
        insert("INSERT INTO invitecode_reserved_code_hashes(code_hash, key_id) VALUES (?, ?)", "c".repeat(64), keyId)
    }

    private fun database(name: String): Path = directory.resolve(name)
    private fun connect(path: Path): Connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
    private fun count(connection: Connection, table: String): Int = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT COUNT(*) FROM $table").use { rows -> rows.next(); rows.getInt(1) }
    }

    private companion object {
        val BUSINESS_TABLES = listOf(
            "invitecode_players", "invitecode_codes", "invitecode_invitations", "invitecode_qualification_facts",
            "invitecode_reward_deliveries", "invitecode_reward_actions", "invitecode_audit_records",
            "invitecode_reward_delivery_transitions", "invitecode_reward_action_transitions",
            "invitecode_player_login_days", "invitecode_player_network_hashes", "invitecode_reserved_code_hashes",
        )
    }
}
