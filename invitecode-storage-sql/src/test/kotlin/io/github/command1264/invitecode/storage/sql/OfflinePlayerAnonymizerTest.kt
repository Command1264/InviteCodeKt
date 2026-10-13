package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.ActiveInviteCode
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.util.UUID
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OfflinePlayerAnonymizerTest {
    @TempDir lateinit var directory: Path
    private val guard = BlockingOperationGuard { }
    private val key = CodeReservationKey("a".repeat(64)) { code ->
        MessageDigest.getInstance("SHA-256").digest(("test secret:" + code).toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Test
    fun `completed history survives without original identity or reusable code`() {
        val database = directory.resolve("history.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        val invitation = UUID.randomUUID()
        val delivery = UUID.nameUUIDFromBytes("milestone:$inviter".toByteArray())
        val action = UUID.nameUUIDFromBytes("action:$inviter".toByteArray())
        seed(database, inviter, invitee, invitation, delivery, action, "DELIVERED")

        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            assertEquals(1, storage.previewAnonymization(inviter).invitations)
            val result = storage.anonymizePlayer(inviter)
            assertEquals(1, result.preview.retiredCodes)
            assertNull(storage.player(inviter))
            assertNull(storage.activeInviteCode(InviteCode.fromNormalized("A1B2C3")))
            assertTrue(storage.isInviteCodeReserved(InviteCode.fromNormalized("A1B2C3")))
            assertEquals(result.historicalId, storage.invitation(invitation)?.inviterId)
            assertNull(storage.player(result.historicalId)?.lastKnownName)
            assertNull(storage.rewardDelivery(delivery))
            assertNull(storage.rewardAction(action))
            assertTrue(storage.auditHistoryByPlayer(inviter, 10).isEmpty())
            assertTrue(storage.auditHistoryByPlayer(result.historicalId, 10).isNotEmpty())
            assertTrue(storage.auditHistoryByPlayer(result.historicalId, 10).all {
                !it.reason.contains(inviter.toString()) && !it.actor.contains("Alice")
            })
            connect(database).use { connection ->
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM invitecode_player_network_hashes WHERE player_id = ?", inviter))
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM invitecode_player_login_days WHERE player_id = ?", inviter))
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM invitecode_codes WHERE inviter_id = ?", inviter))
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM invitecode_reward_actions WHERE recipient_id = ?", result.historicalId))
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM invitecode_reward_actions WHERE idempotency_key LIKE ?", inviter))
            }
            storage.savePlayer(io.github.command1264.invitecode.core.StoredPlayer(inviter, java.time.Instant.now(), "Returner"))
            assertNotNull(storage.player(inviter))
            assertTrue(storage.invitationsByInviter(inviter).isEmpty())
            assertThrows(IllegalStateException::class.java) {
                storage.atomically { transaction ->
                    transaction.activate(ActiveInviteCode(inviter, InviteCode.fromNormalized("A1B2C3"), Instant.now()))
                }
            }
        }
        SqliteInviteCodeStorage.open(database, guard).use { withoutKey ->
            assertThrows(IllegalStateException::class.java) {
                withoutKey.isInviteCodeReserved(InviteCode.fromNormalized("FFFFFF"))
            }
        }
        assertThrows(IllegalStateException::class.java) {
            SqliteInviteCodeStorage.open(database, guard, CodeReservationKey("c".repeat(64)) { "d".repeat(64) })
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["PENDING", "PROCESSING", "UNCERTAIN"])
    fun `unresolved reward fails before any identity mutation`(status: String) {
        val database = directory.resolve("unresolved.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        val invitation = UUID.randomUUID()
        seed(database, inviter, invitee, invitation, UUID.randomUUID(), UUID.randomUUID(), status)
        val before = snapshot(database)
        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            assertThrows(IllegalStateException::class.java) { storage.anonymizePlayer(inviter) }
            assertNotNull(storage.player(inviter))
            assertFalse(storage.isInviteCodeReserved(InviteCode.fromNormalized("FFFFFF")))
            assertNotNull(storage.activeInviteCode(InviteCode.fromNormalized("A1B2C3")))
        }
        assertEquals(before, snapshot(database))
    }

    @Test
    fun `anonymized history remains private and linked after reopening`() {
        val database = directory.resolve("restart.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        val invitation = UUID.randomUUID()
        seed(database, inviter, invitee, invitation, UUID.randomUUID(), UUID.randomUUID(), "DELIVERED")
        val fact = io.github.command1264.invitecode.core.QualificationFact(
            UUID.randomUUID(), invitation, "policy", "revision", "condition",
            io.github.command1264.invitecode.core.QualificationFactOutcome.SATISFIED,
            Instant.parse("2026-01-03T00:00:00Z"),
        )
        val historical = SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            storage.saveQualifiedInvitation(requireNotNull(storage.invitation(invitation)).qualify(fact.recordedAt, fact.policyId, fact.qualificationRevision), listOf(fact))
            storage.anonymizePlayer(inviter).historicalId
        }
        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            assertNull(storage.player(inviter))
            assertEquals(historical, storage.invitation(invitation)?.inviterId)
            assertEquals(Instant.parse("2026-01-02T00:00:00Z"), storage.invitation(invitation)?.claimedAt)
            assertEquals(fact.recordedAt, storage.invitation(invitation)?.qualifiedAt)
            assertEquals(fact.id, storage.qualificationFacts(invitation).single().id)
            assertEquals(fact.recordedAt, storage.qualificationFacts(invitation).single().recordedAt)
            assertNull(storage.player(historical)?.lastKnownName)
            assertTrue(storage.isInviteCodeReserved(InviteCode.fromNormalized("A1B2C3")))
            assertTrue(storage.pendingRewardActions(historical).isEmpty())
            assertTrue(storage.auditHistoryByPlayer(historical, 10).any { it.action.name == "PLAYER_ANONYMIZED" })
        }
        val exported = snapshot(database).toString()
        assertFalse(exported.contains(inviter.toString()))
        assertFalse(exported.contains("Alice"))
        assertFalse(exported.contains("A1B2C3"))
    }

    @Test
    fun `write failure rolls back identity history and code reservation`() {
        val database = directory.resolve("failed.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        seed(database, inviter, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DELIVERED")
        connect(database).use { connection ->
            connection.createStatement().use { it.execute("CREATE TRIGGER fail_anonymization BEFORE DELETE ON invitecode_players BEGIN SELECT RAISE(ABORT, 'injected anonymization failure'); END") }
        }
        val before = snapshot(database)
        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            val failure = assertThrows(SqliteStorageException::class.java) { storage.anonymizePlayer(inviter) }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any {
                it.message.orEmpty().contains("injected anonymization failure")
            })
        }
        assertEquals(before, snapshot(database))
        connect(database).use { it.createStatement().use { statement -> statement.execute("DROP TRIGGER fail_anonymization") } }
        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            storage.anonymizePlayer(inviter)
            assertNull(storage.player(inviter))
        }
    }

    @Test
    fun `two SQLite maintenance connections cannot anonymize a player twice`() {
        val database = directory.resolve("parallel.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        seed(database, inviter, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DELIVERED")
        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val attempts = (1..2).map {
                executor.submit<Boolean> {
                    SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
                        ready.countDown()
                        check(start.await(10, TimeUnit.SECONDS))
                        try { storage.anonymizePlayer(inviter); true } catch (failure: IllegalStateException) {
                            assertTrue(failure.message.orEmpty().contains("absent"), failure.message)
                            false
                        }
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(1, attempts.count { it.get(20, TimeUnit.SECONDS) })
            SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
                assertNull(storage.player(inviter))
                assertTrue(storage.isInviteCodeReserved(InviteCode.fromNormalized("A1B2C3")))
            }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun snapshot(database: Path): PortableStorageSnapshot = connect(database).use {
        OfflineStorageTransfer.export(it, StorageDialect.SQLITE, key.keyId)
    }

    @Test
    fun `invitee anonymization keeps inviter code and severs old invitee UUID`() {
        val database = directory.resolve("invitee.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        val invitation = UUID.randomUUID()
        seed(database, inviter, invitee, invitation, UUID.randomUUID(), UUID.randomUUID(), "DELIVERED")
        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            val result = storage.anonymizePlayer(invitee)
            assertEquals(0, result.preview.retiredCodes)
            assertEquals(result.historicalId, storage.invitation(invitation)?.inviteeId)
            assertNull(storage.player(invitee))
            assertNotNull(storage.activeInviteCode(InviteCode.fromNormalized("A1B2C3")))
            assertNotNull(storage.player(inviter))
        }
    }

    @Test
    fun `unrelated free text containing original UUID rolls back the entire operation`() {
        val database = directory.resolve("unrelated-text.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        seed(database, inviter, invitee, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DELIVERED")
        connect(database).use { connection ->
            connection.prepareStatement("""
                INSERT INTO invitecode_audit_records(audit_id, action, actor, reason, occurred_at, player_id, details_kind)
                VALUES (?, 'ELIGIBILITY_RESET', 'admin', ?, '2026-01-04T00:00:00Z', ?, 'NONE')
            """).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, "mentions $inviter")
                statement.setString(3, invitee.toString())
                statement.executeUpdate()
            }
        }
        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            assertThrows(IllegalStateException::class.java) { storage.anonymizePlayer(inviter) }
            assertNotNull(storage.player(inviter))
            assertNotNull(storage.activeInviteCode(InviteCode.fromNormalized("A1B2C3")))
            assertFalse(storage.isInviteCodeReserved(InviteCode.fromNormalized("FFFFFF")))
        }
    }

    @Test
    fun `administrator name in another player's audit is redacted`() {
        val database = directory.resolve("admin-name.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        seed(database, inviter, invitee, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DELIVERED")
        val unrelatedDelivery = UUID.randomUUID()
        val unrelatedAction = UUID.randomUUID()
        connect(database).use { connection ->
            connection.prepareStatement("""
                INSERT INTO invitecode_audit_records(audit_id, action, actor, reason, occurred_at, player_id, details_kind)
                VALUES (?, 'ELIGIBILITY_RESET', 'Alice', 'Alice approved Bob', '2026-01-04T00:00:00Z', ?, 'NONE')
            """).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, invitee.toString())
                statement.executeUpdate()
            }
            connection.prepareStatement("""
                INSERT INTO invitecode_reward_deliveries(delivery_id, reward_id, status, idempotency_key, created_at, updated_at)
                VALUES (?, 'other', 'DELIVERED', ?, '2026-01-04T00:00:00Z', '2026-01-04T00:00:00Z')
            """).use { statement ->
                statement.setString(1, unrelatedDelivery.toString())
                statement.setString(2, "other:${unrelatedDelivery}")
                statement.executeUpdate()
            }
            connection.prepareStatement("""
                INSERT INTO invitecode_reward_actions(action_id, reward_delivery_id, recipient_id, position,
                    action_type, payload, status, idempotency_key, created_at, updated_at)
                VALUES (?, ?, ?, 0, 'MESSAGE', 'Hello Bob', 'DELIVERED', ?, '2026-01-04T00:00:00Z', '2026-01-04T00:00:00Z')
            """).use { statement ->
                statement.setString(1, unrelatedAction.toString())
                statement.setString(2, unrelatedDelivery.toString())
                statement.setString(3, invitee.toString())
                statement.setString(4, "other-action:${unrelatedAction}")
                statement.executeUpdate()
            }
            connection.prepareStatement("""
                INSERT INTO invitecode_reward_action_transitions(action_id, previous_status, current_status,
                    occurred_at, actor, reason)
                VALUES (?, 'PENDING', 'DELIVERED', '2026-01-04T00:00:00Z', 'Alice', 'Alice approved')
            """).use { statement ->
                statement.setString(1, unrelatedAction.toString())
                statement.executeUpdate()
            }
        }
        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            storage.anonymizePlayer(inviter)
            assertTrue(storage.auditHistoryByPlayer(invitee, 10).all {
                !it.actor.contains("Alice") && !it.reason.contains("Alice")
            })
            assertTrue(storage.rewardActionTransitions(unrelatedAction).all {
                it.actor?.contains("Alice") != true && it.reason?.contains("Alice") != true
            })
        }
    }

    @Test
    fun `another player's pending reward mentioning the name blocks anonymization`() {
        val database = directory.resolve("other-reward.db")
        SqliteSchemaMigrator(database, guard).migrate()
        val inviter = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        seed(database, inviter, invitee, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DELIVERED")
        val delivery = UUID.randomUUID()
        connect(database).use { connection ->
            connection.prepareStatement("""
                INSERT INTO invitecode_reward_deliveries(delivery_id, reward_id, status, idempotency_key, created_at, updated_at)
                VALUES (?, 'other', 'PENDING', ?, '2026-01-04T00:00:00Z', '2026-01-04T00:00:00Z')
            """).use { statement ->
                statement.setString(1, delivery.toString())
                statement.setString(2, "other:$delivery")
                statement.executeUpdate()
            }
            connection.prepareStatement("""
                INSERT INTO invitecode_reward_actions(action_id, reward_delivery_id, recipient_id, position,
                    action_type, payload, status, idempotency_key, created_at, updated_at)
                VALUES (?, ?, ?, 0, 'MESSAGE', 'Hello Alice', 'PENDING', ?, '2026-01-04T00:00:00Z', '2026-01-04T00:00:00Z')
            """).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, delivery.toString())
                statement.setString(3, invitee.toString())
                statement.setString(4, "other-action:$delivery")
                statement.executeUpdate()
            }
        }
        SqliteInviteCodeStorage.open(database, guard, key).use { storage ->
            assertThrows(IllegalStateException::class.java) { storage.anonymizePlayer(inviter) }
            assertNotNull(storage.player(inviter))
            assertNotNull(storage.activeInviteCode(InviteCode.fromNormalized("A1B2C3")))
        }
    }

    private fun seed(db: Path, inviter: UUID, invitee: UUID, invitation: UUID, delivery: UUID, action: UUID, status: String) {
        connect(db).use { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            fun insert(sql: String, vararg values: Any?) = connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
            listOf(inviter to "Alice", invitee to "Bob").forEach { (id, name) ->
                insert("INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name) VALUES (?, ?, ?)",
                    id.toString(), "2026-01-01T00:00:00Z", name)
            }
            insert("INSERT INTO invitecode_codes(code, inviter_id, activated_at) VALUES (?, ?, ?)",
                "A1B2C3", inviter.toString(), "2026-01-01T00:00:00Z")
            insert("""
                INSERT INTO invitecode_invitations(invitation_id, inviter_id, inviter_eligibility_generation,
                    invitee_id, invitee_eligibility_generation, status, claimed_at)
                VALUES (?, ?, 0, ?, 0, 'CLAIMED', ?)
            """, invitation.toString(), inviter.toString(), invitee.toString(), "2026-01-02T00:00:00Z")
            insert("""
                INSERT INTO invitecode_reward_deliveries(delivery_id, invitation_id, reward_id, status,
                    idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)
            """, delivery.toString(), invitation.toString(), "reward-Alice", status,
                "milestone:$inviter", "2026-01-03T00:00:00Z", "2026-01-03T00:00:00Z")
            insert("""
                INSERT INTO invitecode_reward_actions(action_id, reward_delivery_id, invitation_id, recipient_id,
                    position, action_type, payload, status, idempotency_key, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0, 'MESSAGE', ?, ?, ?, ?, ?)
            """, action.toString(), delivery.toString(), invitation.toString(), inviter.toString(),
                "Hello Alice", status, "action:$inviter", "2026-01-03T00:00:00Z", "2026-01-03T00:00:00Z")
            insert("""
                INSERT INTO invitecode_audit_records(audit_id, action, actor, reason, occurred_at, player_id, details_kind)
                VALUES (?, 'ELIGIBILITY_RESET', ?, ?, ?, ?, 'NONE')
            """, UUID.randomUUID().toString(), "Alice", "Request by $inviter", "2026-01-03T00:00:00Z", inviter.toString())
            insert("INSERT INTO invitecode_player_login_days(player_id, login_date) VALUES (?, ?)", inviter.toString(), "2026-01-01")
            insert("""
                INSERT INTO invitecode_player_network_hashes(player_id, key_id, network_hash, observed_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
            """, inviter.toString(), "a".repeat(64), "b".repeat(64), "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z")
        }
    }

    private fun count(connection: Connection, sql: String, id: UUID): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, id.toString())
            statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
        }
}
