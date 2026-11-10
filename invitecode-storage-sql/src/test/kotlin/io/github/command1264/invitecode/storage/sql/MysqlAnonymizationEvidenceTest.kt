package io.github.command1264.invitecode.storage.sql

import com.zaxxer.hikari.HikariDataSource
import io.github.command1264.invitecode.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MysqlAnonymizationEvidenceTest {
    private val guard = BlockingOperationGuard { }
    private val key = CodeReservationKey("a".repeat(64)) { "b".repeat(64) }
    private val code = InviteCode.fromNormalized("A1B2C3")
    private val time = Instant.parse("2026-01-01T00:00:00Z")
    private data class Fixture(val player: UUID, val invitation: UUID, val fact: UUID)

    @Test
    fun `complete anonymous history persists through a new MySQL connection pool`() = withMysqlSchema { config, pool ->
        val fixture = seed(pool, "DELIVERED")
        val historical = MysqlInviteCodeStorage.open(pool, guard, key).use { it.anonymizePlayer(fixture.player).historicalId }
        newPool(config).use { reopened ->
            MysqlInviteCodeStorage.open(reopened, guard, key).use { storage ->
                assertNull(storage.player(fixture.player))
                assertNull(storage.player(historical)?.lastKnownName)
                assertEquals(historical, storage.invitation(fixture.invitation)?.inviterId)
                assertEquals(time.plusSeconds(1), storage.invitation(fixture.invitation)?.claimedAt)
                assertEquals(time.plusSeconds(2), storage.invitation(fixture.invitation)?.qualifiedAt)
                assertEquals(fixture.fact, storage.qualificationFacts(fixture.invitation).single().id)
                assertEquals(time.plusSeconds(2), storage.qualificationFacts(fixture.invitation).single().recordedAt)
                assertTrue(storage.isInviteCodeReserved(code))
                assertNull(storage.activeInviteCode(code))
                assertTrue(storage.pendingRewardActions(historical).isEmpty())
                assertTrue(storage.auditHistoryByPlayer(historical, 10).any { it.action.name == "PLAYER_ANONYMIZED" })
                val exported = reopened.connection.use { OfflineStorageTransfer.export(it, StorageDialect.MYSQL, key.keyId) }
                assertFalse(exported.toString().contains(fixture.player.toString()))
                assertFalse(exported.toString().contains("Alice"))
                assertFalse(exported.toString().contains("A1B2C3"))
                assertFalse(exported.toString().contains("c".repeat(64)))
                storage.savePlayer(StoredPlayer(fixture.player, time.plusSeconds(10), "Returner"))
                assertTrue(storage.invitationsByInviter(fixture.player).isEmpty())
                assertThrows(IllegalStateException::class.java) {
                    storage.atomically { it.activate(ActiveInviteCode(fixture.player, code, time.plusSeconds(11))) }
                }
            }
        }
        // The newly joined player is separate; delivered history remains settled.
        newPool(config).use { reopened ->
            reopened.connection.use { connection ->
                val snapshot = OfflineStorageTransfer.export(connection, StorageDialect.MYSQL, key.keyId)
                assertFalse(snapshot.toString().contains("Alice"))
                assertFalse(snapshot.toString().contains("A1B2C3"))
                val actions = snapshot.tables.single { it.name == "invitecode_reward_actions" }
                val status = actions.columns.indexOf("status")
                assertEquals(PortableValue.Text("DELIVERED"), actions.rows.single().values[status])
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["PENDING", "PROCESSING", "UNCERTAIN"])
    fun `unresolved MySQL rewards reject anonymization without changing any record`(status: String) = withMysqlSchema { config, pool ->
        val fixture = seed(pool, status)
        val before = snapshot(config)
        MysqlInviteCodeStorage.open(pool, guard, key).use { storage ->
            assertThrows(IllegalStateException::class.java) { storage.anonymizePlayer(fixture.player) }
        }
        assertEquals(before, snapshot(config))
    }

    @Test
    fun `independent MySQL maintenance pools serialize the same player`() = withMysqlSchema { config, pool ->
        val fixture = seed(pool, "DELIVERED")
        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val attempts = (1..2).map {
                executor.submit<java.util.Optional<AnonymizationResult>> {
                    newPool(config).use { connectionPool ->
                        MysqlInviteCodeStorage.open(connectionPool, guard, key).use { storage ->
                            ready.countDown()
                            check(start.await(10, TimeUnit.SECONDS))
                            try { java.util.Optional.of(storage.anonymizePlayer(fixture.player)) }
                            catch (failure: IllegalStateException) {
                                assertTrue(failure.message.orEmpty().contains("absent"), failure.message)
                                java.util.Optional.empty<AnonymizationResult>()
                            }
                        }
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val results = attempts.map { it.get(25, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.isPresent })
            val historical = results.single { it.isPresent }.get().historicalId
            newPool(config).use { reopened ->
                MysqlInviteCodeStorage.open(reopened, guard, key).use { storage ->
                    assertNull(storage.player(fixture.player))
                    assertEquals(historical, storage.invitation(fixture.invitation)?.inviterId)
                    assertEquals(1, storage.auditHistoryByPlayer(historical, 10).count { it.action.name == "PLAYER_ANONYMIZED" })
                }
            }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `MySQL write failure preserves the complete source and permits an explicit retry`() = withMysqlSchema { config, pool ->
        val fixture = seed(pool, "DELIVERED")
        pool.connection.use { connection ->
            connection.createStatement().use { it.execute("CREATE TRIGGER fail_anonymization BEFORE DELETE ON invitecode_players FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected anonymization failure'") }
        }
        val before = snapshot(config)
        MysqlInviteCodeStorage.open(pool, guard, key).use { storage ->
            val failure = assertThrows(MysqlStorageException::class.java) { storage.anonymizePlayer(fixture.player) }
            assertTrue(failure.message.orEmpty().contains("SQLState 45000"))
        }
        assertEquals(before, snapshot(config))
        newPool(config).use { reopened ->
            reopened.connection.use { it.createStatement().use { statement -> statement.execute("DROP TRIGGER fail_anonymization") } }
            MysqlInviteCodeStorage.open(reopened, guard, key).use { storage ->
                storage.anonymizePlayer(fixture.player)
                assertNull(storage.player(fixture.player))
            }
        }
    }

    private fun newPool(config: MysqlConfiguration) = MysqlInviteCodeStorage.createPool(config, System.getenv("INVITECODE_TEST_MYSQL_PASSWORD") ?: "")
    private fun snapshot(config: MysqlConfiguration) = newPool(config).use { pool ->
        pool.connection.use { OfflineStorageTransfer.export(it, StorageDialect.MYSQL, key.keyId) }
    }

    private fun seed(pool: HikariDataSource, status: String): Fixture {
        MysqlSchemaMigrator(pool, guard).migrate()
        val player = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        val invitation = UUID.randomUUID()
        val fact = UUID.randomUUID()
        val delivery = UUID.randomUUID()
        val action = UUID.randomUUID()
        pool.connection.use { connection ->
            MysqlSchemaMigrator.bindNetworkKey(connection, key.keyId)
            fun insert(sql: String, vararg values: Any?) = connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
            insert("INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name) VALUES (?, ?, 'Alice'), (?, ?, 'Bob')", player.toString(), time.toString(), invitee.toString(), time.toString())
            insert("INSERT INTO invitecode_codes(code, inviter_id, activated_at) VALUES ('A1B2C3', ?, ?)", player.toString(), time.toString())
            insert("INSERT INTO invitecode_invitations(invitation_id, inviter_id, inviter_eligibility_generation, invitee_id, invitee_eligibility_generation, status, claimed_at, qualified_at, qualification_policy_id, qualification_revision) VALUES (?, ?, 0, ?, 0, 'QUALIFIED', ?, ?, 'policy', 'revision')", invitation.toString(), player.toString(), invitee.toString(), time.plusSeconds(1).toString(), time.plusSeconds(2).toString())
            insert("INSERT INTO invitecode_qualification_facts(fact_id, invitation_id, policy_id, qualification_revision, condition_id, outcome, recorded_at) VALUES (?, ?, 'policy', 'revision', 'condition', 'SATISFIED', ?)", fact.toString(), invitation.toString(), time.plusSeconds(2).toString())
            insert("INSERT INTO invitecode_reward_deliveries(delivery_id, invitation_id, reward_id, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 'Alice reward', ?, ?, ?, ?)", delivery.toString(), invitation.toString(), status, "milestone:$player", time.plusSeconds(3).toString(), time.plusSeconds(4).toString())
            insert("INSERT INTO invitecode_reward_actions(action_id, reward_delivery_id, invitation_id, recipient_id, position, action_type, payload, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, ?, 0, 'MESSAGE', 'Hello Alice', ?, ?, ?, ?)", action.toString(), delivery.toString(), invitation.toString(), player.toString(), status, "action:$player", time.plusSeconds(3).toString(), time.plusSeconds(4).toString())
            insert("INSERT INTO invitecode_reward_action_transitions(action_id, previous_status, current_status, occurred_at, actor, reason) VALUES (?, 'PROCESSING', ?, ?, 'Alice', 'Alice approved')", action.toString(), status, time.plusSeconds(4).toString())
            insert("INSERT INTO invitecode_reward_delivery_transitions(delivery_id, previous_status, current_status, occurred_at, actor, reason) VALUES (?, 'PROCESSING', ?, ?, 'Alice', 'Alice approved')", delivery.toString(), status, time.plusSeconds(4).toString())
            insert("INSERT INTO invitecode_player_login_days(player_id, login_date) VALUES (?, '2026-01-01')", player.toString())
            insert("INSERT INTO invitecode_player_network_hashes(player_id, key_id, network_hash, observed_at, expires_at) VALUES (?, ?, ?, ?, ?)", player.toString(), key.keyId, "c".repeat(64), time.toString(), time.plusSeconds(60).toString())
            insert("INSERT INTO invitecode_audit_records(audit_id, action, actor, reason, occurred_at, player_id, details_kind) VALUES (?, 'ELIGIBILITY_RESET', 'Alice', ?, ?, ?, 'NONE')", UUID.randomUUID().toString(), "Request by $player", time.toString(), player.toString())
        }
        return Fixture(player, invitation, fact)
    }
}
