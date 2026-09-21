package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeFormats
import io.github.command1264.invitecode.core.InviteCodeGenerator
import io.github.command1264.invitecode.core.InviteCodeLifecycleService
import io.github.command1264.invitecode.core.ClaimEligibilityPolicy
import io.github.command1264.invitecode.core.ClaimConfirmation
import io.github.command1264.invitecode.core.InvitationClaimResult
import io.github.command1264.invitecode.core.InvitationClaimService
import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.AuditedInvitationTransition
import io.github.command1264.invitecode.core.InvitationAdministrationContext
import io.github.command1264.invitecode.core.PlayerClaimState
import io.github.command1264.invitecode.core.Invitation
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.core.InvitationAuditDetails
import io.github.command1264.invitecode.core.QualificationFact
import io.github.command1264.invitecode.core.QualificationFactOutcome
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.core.RewardAdministrationContext
import io.github.command1264.invitecode.core.UncertainRewardActionResolution
import io.github.command1264.invitecode.api.InvitationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SqliteInviteCodeStorageTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `explicit migration creates current schema and player survives reopen`() {
        val database = temporaryDirectory.resolve("invitecode.db")
        val offMainThread = BlockingOperationGuard { }
        val player = StoredPlayer(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            firstSeenAt = Instant.parse("2026-09-22T00:00:00Z"),
            lastKnownName = "Command1264",
        )

        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            storage.savePlayer(player)
        }

        SqliteInviteCodeStorage.open(database, offMainThread).use { reopened ->
            assertEquals(player, reopened.player(player.id))
        }
    }

    @Test
    fun `player upsert preserves the initial First Seen At`() {
        val database = temporaryDirectory.resolve("first-seen.db")
        val offMainThread = BlockingOperationGuard { }
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000009")
        SqliteSchemaMigrator(database, offMainThread).migrate()

        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            storage.savePlayer(StoredPlayer(playerId, Instant.parse("2026-09-22T02:00:00Z"), "NewName"))
            storage.savePlayer(StoredPlayer(playerId, Instant.parse("2026-09-22T02:00:00.100Z"), "LatestName"))

            assertEquals(
                StoredPlayer(playerId, Instant.parse("2026-09-22T02:00:00Z"), "LatestName"),
                storage.player(playerId),
            )
        }
    }

    @Test
    fun `thread guard rejects migration before database IO begins`() {
        val database = temporaryDirectory.resolve("main-thread.db")
        val guard = BlockingOperationGuard.rejectingServerMainThread { true }

        val error = assertThrows<IllegalStateException> {
            SqliteSchemaMigrator(database, guard).migrate()
        }

        assertEquals(
            "SQLite storage cannot run on the server main thread; " +
                "schedule the operation on a dedicated storage executor.",
            error.message,
        )
        assertEquals(false, Files.exists(database))
    }

    @Test
    fun `migration list must already be ordered and contiguous`() {
        val database = temporaryDirectory.resolve("unordered-migrations.db")
        val reversed = SqliteSchemaMigrator.DEFAULT_MIGRATIONS.reversed()

        val error = assertThrows<SqliteStorageException> {
            SqliteSchemaMigrator(database, BlockingOperationGuard { }, reversed).migrate()
        }

        assertEquals(true, error.message!!.contains("transaction was rolled back"))
        assertEquals(true, error.cause!!.message!!.contains("contiguous"))
    }

    @Test
    fun `failed migration rolls back schema version and preserves existing data`() {
        val database = temporaryDirectory.resolve("migration-failure.db")
        val offMainThread = BlockingOperationGuard { }
        val player = StoredPlayer(
            id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            firstSeenAt = Instant.parse("2026-09-22T01:00:00Z"),
        )
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { it.savePlayer(player) }
        val brokenMigration = SqliteSchemaMigration(
            version = SqliteSchemaMigrator.currentVersion + 1,
            description = "Intentionally broken test migration",
            statements = listOf(
                "CREATE TABLE should_be_rolled_back(value TEXT)",
                "THIS IS NOT VALID SQL",
            ),
        )

        val error = assertThrows<SqliteStorageException> {
            SqliteSchemaMigrator(
                database = database,
                blockingOperationGuard = offMainThread,
                migrations = SqliteSchemaMigrator.DEFAULT_MIGRATIONS + brokenMigration,
            ).migrate()
        }

        assertEquals(true, error.message!!.contains("transaction was rolled back"))
        SqliteInviteCodeStorage.open(database, offMainThread).use { reopened ->
            assertEquals(player, reopened.player(player.id))
        }
        connect(database).use { connection ->
            assertEquals(SqliteSchemaMigrator.currentVersion, readSchemaVersion(connection))
            val tableCount = connection.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'should_be_rolled_back'",
            ).use { statement ->
                statement.executeQuery().use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
            assertEquals(0, tableCount)
        }
    }

    @Test
    fun `rollback failure leaves auto commit disabled and reports uncertain database state`() {
        val database = temporaryDirectory.resolve("migration-rollback-failure.db")
        val delegate = connect(database)
        val autoCommitValues = mutableListOf<Boolean>()
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "rollback" -> throw SQLException("simulated rollback failure")
                "setAutoCommit" -> autoCommitValues += arguments!![0] as Boolean
            }
            try {
                method.invoke(delegate, *(arguments ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        } as Connection
        val migrations = listOf(
            SqliteSchemaMigration(
                1,
                "Intentionally fail after changing schema",
                listOf("CREATE TABLE partial_change(value TEXT)", "INVALID SQL"),
            ),
        )

        try {
            val error = assertThrows<SqliteStorageException> {
                SqliteSchemaMigrator(database, BlockingOperationGuard { }, migrations).migrate(connection)
            }

            assertEquals(true, error.message!!.contains("database state is uncertain"))
            assertEquals(listOf(false), autoCommitValues)
            assertEquals("simulated rollback failure", error.suppressed.single().message)
        } finally {
            delegate.close()
        }
    }

    @Test
    fun `Reward transition migration backfills existing version four records`() {
        val database = temporaryDirectory.resolve("reward-transition-backfill.db")
        val guard = BlockingOperationGuard { }
        val recipientId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
        val deliveryId = UUID.fromString("31000000-0000-0000-0000-0000000000a1")
        val actionId = UUID.fromString("30000000-0000-0000-0000-0000000000a1")
        SqliteSchemaMigrator(database, guard, SqliteSchemaMigrator.DEFAULT_MIGRATIONS.take(4)).migrate()
        connect(database).use { connection ->
            connection.prepareStatement(
                "INSERT INTO invitecode_players(player_id, first_seen_at) VALUES (?, ?)",
            ).use { statement ->
                statement.setString(1, recipientId.toString())
                statement.setString(2, Instant.EPOCH.toString())
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                INSERT INTO invitecode_reward_deliveries(
                    delivery_id, reward_id, status, idempotency_key, created_at, updated_at
                ) VALUES (?, 'legacy-reward', 'PENDING', 'legacy-delivery', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, deliveryId.toString())
                statement.setString(2, Instant.EPOCH.toString())
                statement.setString(3, Instant.EPOCH.toString())
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                INSERT INTO invitecode_reward_actions(
                    action_id, reward_delivery_id, recipient_id, position, action_type, payload,
                    status, idempotency_key, created_at, updated_at
                ) VALUES (?, ?, ?, 0, 'message', 'welcome', 'PENDING', 'legacy-action', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, actionId.toString())
                statement.setString(2, deliveryId.toString())
                statement.setString(3, recipientId.toString())
                statement.setString(4, Instant.EPOCH.toString())
                statement.setString(5, Instant.EPOCH.toString())
                statement.executeUpdate()
            }
        }

        assertEquals(SqliteSchemaMigrator.currentVersion, SqliteSchemaMigrator(database, guard).migrate())

        SqliteInviteCodeStorage.open(database, guard).use { storage ->
            assertEquals(
                listOf(RewardDeliveryStatus.PENDING),
                storage.rewardDeliveryTransitions(deliveryId).map { it.currentStatus },
            )
            assertEquals(
                listOf(RewardActionStatus.PENDING),
                storage.rewardActionTransitions(actionId).map { it.currentStatus },
            )
        }
    }

    @Test
    fun `opening an unmigrated database gives an actionable error`() {
        val database = temporaryDirectory.resolve("unmigrated.db")

        val error = assertThrows<SqliteStorageException> {
            SqliteInviteCodeStorage.open(database, BlockingOperationGuard { })
        }

        assertEquals(true, error.message!!.contains("run the explicit schema migration"))
    }

    @Test
    fun `active and retired invite codes survive reopen without becoming reusable`() {
        val database = temporaryDirectory.resolve("invite-codes.db")
        val offMainThread = BlockingOperationGuard { }
        val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val initial = InviteCode.parse("A25981", InviteCodeFormats.colorHex)
        val replacement = InviteCode.parse("0B35E7", InviteCodeFormats.colorHex)
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            storage.savePlayer(StoredPlayer(inviterId, Instant.parse("2026-09-22T00:00:00Z")))
            InviteCodeLifecycleService(storage, InviteCodeGenerator { initial }, 1)
                .assign(inviterId, Instant.parse("2026-09-22T01:00:00Z"))
            InviteCodeLifecycleService(storage, InviteCodeGenerator { replacement }, 1)
                .rotate(inviterId, Instant.parse("2026-09-22T02:00:00Z"))
        }

        SqliteInviteCodeStorage.open(database, offMainThread).use { reopened ->
            assertEquals(replacement, reopened.activeInviteCode(inviterId)!!.code)
            assertEquals(true, reopened.isInviteCodeReserved(initial))
            assertEquals(true, reopened.isInviteCodeReserved(replacement))
        }
    }

    @Test
    fun `concurrent claims persist exactly one invitation for an invitee`() {
        val database = temporaryDirectory.resolve("claims.db")
        val offMainThread = BlockingOperationGuard { }
        val firstInviter = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val secondInviter = UUID.fromString("00000000-0000-0000-0000-000000000012")
        val invitee = UUID.fromString("00000000-0000-0000-0000-000000000013")
        val firstSeenAt = Instant.parse("2026-09-22T00:00:00Z")
        val idSequence = AtomicInteger()
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            listOf(firstInviter, secondInviter, invitee).forEach { playerId ->
                storage.savePlayer(StoredPlayer(playerId, firstSeenAt))
            }
            val service = InvitationClaimService(
                transactions = storage,
                eligibilityPolicy = ClaimEligibilityPolicy(Duration.ofHours(24)),
                invitationIdGenerator = {
                    UUID(0x1000000000000000L, idSequence.incrementAndGet().toLong())
                },
            )
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val attempts = listOf(firstInviter, secondInviter).map { inviterId ->
                    executor.submit<InvitationClaimResult> {
                        start.await()
                        service.claim(
                            ClaimConfirmation.present(
                                inviteeId = invitee,
                                inviterId = inviterId,
                                inviterName = "Inviter",
                                inviteCode = InviteCode.parse("A25981"),
                                presentedAt = firstSeenAt.plusSeconds(30),
                                validity = Duration.ofSeconds(30),
                            ).confirm(invitee, firstSeenAt.plusSeconds(60)),
                        )
                    }
                }
                start.countDown()
                val results = attempts.map { it.get(10, TimeUnit.SECONDS) }

                assertEquals(1, results.count { it is InvitationClaimResult.Claimed })
                assertEquals(1, results.count { it is InvitationClaimResult.Rejected })
                val persistedId = results.filterIsInstance<InvitationClaimResult.Claimed>().single().invitation.id
                assertEquals(persistedId, storage.invitation(persistedId)!!.id)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `database constraint rejects a second invitation for one invitee`() {
        val database = temporaryDirectory.resolve("unique-invitee.db")
        val offMainThread = BlockingOperationGuard { }
        val firstInviter = UUID.fromString("00000000-0000-0000-0000-000000000031")
        val secondInviter = UUID.fromString("00000000-0000-0000-0000-000000000032")
        val invitee = UUID.fromString("00000000-0000-0000-0000-000000000033")
        val first = Invitation(
            UUID.fromString("10000000-0000-0000-0000-000000000031"),
            firstInviter,
            invitee,
            InvitationStatus.CLAIMED,
            Instant.parse("2026-09-22T01:00:00Z"),
        )
        val second = Invitation(
            UUID.fromString("10000000-0000-0000-0000-000000000032"),
            secondInviter,
            invitee,
            InvitationStatus.CLAIMED,
            Instant.parse("2026-09-22T02:00:00Z"),
        )
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            listOf(firstInviter, secondInviter, invitee).forEach {
                storage.savePlayer(StoredPlayer(it, Instant.EPOCH))
            }
            storage.saveInvitation(first)

            val error = assertThrows<SqliteStorageException> { storage.saveInvitation(second) }

            assertEquals(true, error.message!!.contains("operation was rejected"))
            assertEquals(first, storage.invitation(first.id))
            assertEquals(null, storage.invitation(second.id))
        }
    }

    @Test
    fun `qualification reward and audit records survive reopen`() {
        val database = temporaryDirectory.resolve("lifecycle-records.db")
        val offMainThread = BlockingOperationGuard { }
        val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000021")
        val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000022")
        val invitationId = UUID.fromString("10000000-0000-0000-0000-000000000021")
        val qualifiedAt = Instant.parse("2026-09-22T04:00:00Z")
        val invitation = Invitation(
            id = invitationId,
            inviterId = inviterId,
            inviteeId = inviteeId,
            status = InvitationStatus.QUALIFIED,
            claimedAt = Instant.parse("2026-09-22T01:00:00Z"),
            qualifiedAt = qualifiedAt,
            qualificationPolicyId = "default",
            qualificationRevision = "sha256:qualification-v1",
        )
        val fact = QualificationFact(
            id = UUID.fromString("20000000-0000-0000-0000-000000000021"),
            invitationId = invitationId,
            policyId = "default",
            qualificationRevision = "sha256:qualification-v1",
            conditionId = "play-time",
            outcome = QualificationFactOutcome.SATISFIED,
            recordedAt = qualifiedAt,
        )
        val action = StoredRewardAction(
            id = UUID.fromString("30000000-0000-0000-0000-000000000021"),
            rewardDeliveryId = UUID.fromString("31000000-0000-0000-0000-000000000021"),
            invitationId = invitationId,
            recipientId = inviterId,
            position = 0,
            type = "message",
            payload = "Thanks for inviting a player",
            status = RewardActionStatus.PENDING,
            idempotencyKey = "qualification:$invitationId:inviter:0",
            createdAt = qualifiedAt,
            updatedAt = qualifiedAt,
        )
        val delivery = StoredRewardDelivery(
            id = action.rewardDeliveryId,
            invitationId = invitationId,
            rewardId = "qualification-default",
            status = RewardDeliveryStatus.PENDING,
            idempotencyKey = "qualification:$invitationId",
            createdAt = qualifiedAt,
            updatedAt = qualifiedAt,
        )
        val audit = AuditRecord(
            id = UUID.fromString("40000000-0000-0000-0000-000000000021"),
            action = InvitationAuditAction.FORCE_QUALIFIED,
            actor = "console",
            reason = "Manual acceptance",
            occurredAt = qualifiedAt,
            invitationId = invitationId,
            details = InvitationAuditDetails.ForceQualification("default", "sha256:qualification-v1"),
        )
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            storage.savePlayer(StoredPlayer(inviterId, Instant.EPOCH, "Inviter"))
            storage.savePlayer(StoredPlayer(inviteeId, Instant.EPOCH, "Invitee"))
            storage.saveInvitation(invitation)
            storage.saveQualificationFact(fact)
            storage.saveRewardDelivery(delivery, listOf(action))
            storage.saveAuditRecord(audit)
        }

        SqliteInviteCodeStorage.open(database, offMainThread).use { reopened ->
            assertEquals(invitation, reopened.invitation(invitationId))
            assertEquals(fact, reopened.qualificationFact(fact.id))
            assertEquals(delivery, reopened.rewardDelivery(delivery.id))
            assertEquals(action, reopened.rewardAction(action.id))
            assertEquals(audit, reopened.auditRecord(audit.id))
        }
    }

    @Test
    fun `Reward Delivery and all actions roll back together`() {
        val database = temporaryDirectory.resolve("reward-rollback.db")
        val offMainThread = BlockingOperationGuard { }
        val recipientId = UUID.fromString("00000000-0000-0000-0000-000000000041")
        val delivery = StoredRewardDelivery(
            id = UUID.fromString("31000000-0000-0000-0000-000000000041"),
            invitationId = null,
            rewardId = "milestone-five",
            status = RewardDeliveryStatus.PENDING,
            idempotencyKey = "milestone:$recipientId:5",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val first = StoredRewardAction(
            UUID.fromString("30000000-0000-0000-0000-000000000041"), delivery.id, null,
            recipientId, 0, "message", "First", RewardActionStatus.PENDING,
            "duplicate-action-key", Instant.EPOCH, Instant.EPOCH,
        )
        val second = first.copy(
            id = UUID.fromString("30000000-0000-0000-0000-000000000042"),
            position = 1,
        )
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            storage.savePlayer(StoredPlayer(recipientId, Instant.EPOCH))

            assertThrows<SqliteStorageException> {
                storage.saveRewardDelivery(delivery, listOf(first, second))
            }

            assertEquals(null, storage.rewardDelivery(delivery.id))
            assertEquals(null, storage.rewardAction(first.id))
        }
    }

    @Test
    fun `Invitation transition rolls back when its Audit Record cannot be saved`() {
        val database = temporaryDirectory.resolve("audit-rollback.db")
        val offMainThread = BlockingOperationGuard { }
        val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000051")
        val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000052")
        val invitation = Invitation(
            UUID.fromString("10000000-0000-0000-0000-000000000051"),
            inviterId,
            inviteeId,
            InvitationStatus.CLAIMED,
            Instant.EPOCH,
        )
        val audit = AuditRecord(
            id = UUID.fromString("40000000-0000-0000-0000-000000000051"),
            action = InvitationAuditAction.FORCE_QUALIFIED,
            actor = "console",
            reason = "Test duplicate Audit Record",
            occurredAt = Instant.parse("2026-09-22T05:00:00Z"),
            invitationId = invitation.id,
            details = InvitationAuditDetails.ForceQualification("admin", "sha256:test"),
        )
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            storage.savePlayer(StoredPlayer(inviterId, Instant.EPOCH))
            storage.savePlayer(StoredPlayer(inviteeId, Instant.EPOCH))
            storage.saveInvitation(invitation)
            storage.saveAuditRecord(audit)
            val qualified = invitation.qualify(audit.occurredAt, "admin", "sha256:test")

            assertThrows<SqliteStorageException> {
                storage.saveAuditedInvitationTransition(
                    AuditedInvitationTransition(invitation, qualified, audit),
                )
            }

            assertEquals(invitation, storage.invitation(invitation.id))
        }
    }

    @Test
    fun `Eligibility Reset starts a new unique Claim generation and saves its Audit Record`() {
        val database = temporaryDirectory.resolve("eligibility-reset.db")
        val offMainThread = BlockingOperationGuard { }
        val oldInviter = UUID.fromString("00000000-0000-0000-0000-000000000061")
        val newInviter = UUID.fromString("00000000-0000-0000-0000-000000000062")
        val invitee = UUID.fromString("00000000-0000-0000-0000-000000000063")
        val initialFirstSeen = Instant.parse("2026-09-22T00:00:00Z")
        val resetAt = Instant.parse("2026-09-22T06:00:00Z")
        val oldInvitation = Invitation(
            UUID.fromString("10000000-0000-0000-0000-000000000061"),
            oldInviter,
            invitee,
            InvitationStatus.UNLINKED,
            Instant.parse("2026-09-22T01:00:00Z"),
        )
        val context = InvitationAdministrationContext(
            auditRecordId = UUID.fromString("40000000-0000-0000-0000-000000000061"),
            actor = "console",
            reason = "Restore eligibility after unlink",
            occurredAt = resetAt,
        )
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            listOf(oldInviter, newInviter, invitee).forEach {
                storage.savePlayer(StoredPlayer(it, initialFirstSeen))
            }
            storage.saveInvitation(oldInvitation)
            val reset = PlayerClaimState(invitee, initialFirstSeen, false, true)
                .adminResetEligibility(context)

            storage.saveAuditedClaimStateTransition(reset)

            assertEquals(resetAt, storage.player(invitee)!!.firstSeenAt)
            assertEquals(reset.auditRecord, storage.auditRecord(context.auditRecordId))
            val claim = InvitationClaimService(
                transactions = storage,
                eligibilityPolicy = ClaimEligibilityPolicy(Duration.ofHours(24)),
                invitationIdGenerator = {
                    UUID.fromString("10000000-0000-0000-0000-000000000062")
                },
            ).claim(
                ClaimConfirmation.present(
                    invitee, newInviter, "NewInviter", InviteCode.parse("A25981"),
                    resetAt, Duration.ofSeconds(30),
                ).confirm(invitee, resetAt.plusSeconds(1)),
            )
            assertEquals(true, claim is InvitationClaimResult.Claimed)
        }
    }

    @Test
    fun `Eligibility Reset rolls back when its Audit Record cannot be saved`() {
        val database = temporaryDirectory.resolve("eligibility-reset-rollback.db")
        val offMainThread = BlockingOperationGuard { }
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000071")
        val firstSeenAt = Instant.parse("2026-09-22T00:00:00Z")
        val context = InvitationAdministrationContext(
            auditRecordId = UUID.fromString("40000000-0000-0000-0000-000000000071"),
            actor = "console",
            reason = "Test duplicate Audit Record",
            occurredAt = Instant.parse("2026-09-22T07:00:00Z"),
        )
        val existingAudit = PlayerClaimState(playerId, firstSeenAt, false, false)
            .adminResetEligibility(context).auditRecord
        SqliteSchemaMigrator(database, offMainThread).migrate()
        SqliteInviteCodeStorage.open(database, offMainThread).use { storage ->
            storage.savePlayer(StoredPlayer(playerId, firstSeenAt))
            storage.saveAuditRecord(existingAudit)
            val reset = PlayerClaimState(playerId, firstSeenAt, false, false)
                .adminResetEligibility(context)

            assertThrows<SqliteStorageException> { storage.saveAuditedClaimStateTransition(reset) }

            assertEquals(firstSeenAt, storage.player(playerId)!!.firstSeenAt)
        }
    }

    @Test
    fun `stale Invitation transition cannot replace a newer permanent state`() {
        val database = temporaryDirectory.resolve("stale-invitation-transition.db")
        val guard = BlockingOperationGuard { }
        val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000081")
        val replacementId = UUID.fromString("00000000-0000-0000-0000-000000000082")
        val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000083")
        val claimed = Invitation(
            UUID.fromString("10000000-0000-0000-0000-000000000081"),
            inviterId,
            inviteeId,
            InvitationStatus.CLAIMED,
            Instant.EPOCH,
        )
        val qualified = claimed.adminForceQualify(
            Instant.parse("2026-09-22T08:00:00Z"),
            "default",
            "policy@1",
            InvitationAdministrationContext(
                UUID.fromString("40000000-0000-0000-0000-000000000081"),
                "console",
                "qualified first",
                Instant.parse("2026-09-22T08:00:00Z"),
            ),
        )
        val staleTransfer = claimed.adminTransfer(
            replacementId,
            InvitationAdministrationContext(
                UUID.fromString("40000000-0000-0000-0000-000000000082"),
                "console",
                "stale transfer",
                Instant.parse("2026-09-22T08:01:00Z"),
            ),
        )
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use { storage ->
            listOf(inviterId, replacementId, inviteeId).forEach {
                storage.savePlayer(StoredPlayer(it, Instant.EPOCH))
            }
            storage.saveInvitation(claimed)
            storage.saveAuditedInvitationTransition(qualified)

            assertThrows<SqliteStorageException> {
                storage.saveAuditedInvitationTransition(staleTransfer)
            }

            assertEquals(qualified.invitation, storage.invitation(claimed.id))
            assertEquals(null, storage.auditRecord(staleTransfer.auditRecord.id))
        }
    }

    @Test
    fun `pending Reward Actions can be recovered after reopen and transitions remain auditable`() {
        val database = temporaryDirectory.resolve("pending-reward-recovery.db")
        val guard = BlockingOperationGuard { }
        val recipientId = UUID.fromString("00000000-0000-0000-0000-000000000091")
        val delivery = StoredRewardDelivery(
            UUID.fromString("31000000-0000-0000-0000-000000000091"),
            null,
            "login-reward",
            RewardDeliveryStatus.PENDING,
            "login:$recipientId",
            Instant.EPOCH,
            Instant.EPOCH,
        )
        val action = StoredRewardAction(
            UUID.fromString("30000000-0000-0000-0000-000000000091"),
            delivery.id,
            null,
            recipientId,
            0,
            "player-command",
            "kit welcome",
            RewardActionStatus.PENDING,
            "login:$recipientId:0",
            Instant.EPOCH,
            Instant.EPOCH,
        )
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use { storage ->
            storage.savePlayer(StoredPlayer(recipientId, Instant.EPOCH))
            storage.saveRewardDelivery(delivery, listOf(action))
        }

        SqliteInviteCodeStorage.open(database, guard).use { reopened ->
            assertEquals(listOf(action), reopened.pendingRewardActions(recipientId))
            assertEquals(listOf(action), reopened.rewardActions(delivery.id))

            val processingAt = Instant.parse("2026-09-22T09:00:00Z")
            val deliveredAt = processingAt.plusSeconds(1)
            reopened.saveRewardAction(
                action.copy(status = RewardActionStatus.PROCESSING, updatedAt = processingAt),
            )
            reopened.saveRewardAction(
                action.copy(status = RewardActionStatus.DELIVERED, updatedAt = deliveredAt),
            )
            reopened.saveRewardDelivery(
                delivery.copy(status = RewardDeliveryStatus.DELIVERED, updatedAt = deliveredAt),
                listOf(action.copy(status = RewardActionStatus.DELIVERED, updatedAt = deliveredAt)),
            )

            assertEquals(emptyList<StoredRewardAction>(), reopened.pendingRewardActions(recipientId))
            assertEquals(
                listOf(RewardActionStatus.PENDING, RewardActionStatus.PROCESSING, RewardActionStatus.DELIVERED),
                reopened.rewardActionTransitions(action.id).map { it.currentStatus },
            )
            assertEquals(
                listOf(
                    RewardDeliveryStatus.PENDING,
                    RewardDeliveryStatus.PROCESSING,
                    RewardDeliveryStatus.DELIVERED,
                ),
                reopened.rewardDeliveryTransitions(delivery.id).map { it.currentStatus },
            )
        }
    }

    @Test
    fun `interrupted Reward Action becomes uncertain and only audited administration can retry it`() {
        val database = temporaryDirectory.resolve("reward-recovery.db")
        val guard = BlockingOperationGuard { }
        val recipientId = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
        val delivery = StoredRewardDelivery(
            UUID.fromString("31000000-0000-0000-0000-0000000000b1"), null, "recovery",
            RewardDeliveryStatus.PENDING, "recovery-delivery", Instant.EPOCH, Instant.EPOCH,
        )
        val pending = StoredRewardAction(
            UUID.fromString("30000000-0000-0000-0000-0000000000b1"), delivery.id, null,
            recipientId, 0, "player-command", "kit retry", RewardActionStatus.PENDING,
            "recovery-action", Instant.EPOCH, Instant.EPOCH,
        )
        val processingAt = Instant.parse("2026-09-22T10:00:00Z")
        val recoveredAt = processingAt.plusSeconds(1)
        val resolvedAt = recoveredAt.plusSeconds(1)
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use { storage ->
            storage.savePlayer(StoredPlayer(recipientId, Instant.EPOCH))
            storage.saveRewardDelivery(delivery, listOf(pending))
            storage.saveRewardAction(pending.copy(status = RewardActionStatus.PROCESSING, updatedAt = processingAt))

            assertEquals(1, storage.recoverInterruptedRewardActions(recoveredAt))
            val uncertain = storage.rewardAction(pending.id)!!
            assertEquals(RewardActionStatus.UNCERTAIN, uncertain.status)
            assertThrows<IllegalArgumentException> {
                storage.saveRewardAction(
                    uncertain.copy(status = RewardActionStatus.PENDING, updatedAt = resolvedAt),
                )
            }

            val retried = storage.resolveUncertainRewardAction(
                pending.id,
                UncertainRewardActionResolution.RETRY,
                RewardAdministrationContext("console", "verified no command ran", resolvedAt),
            )
            assertEquals(RewardActionStatus.PENDING, retried.status)
            val resolution = storage.rewardActionTransitions(pending.id).last()
            assertEquals("console", resolution.actor)
            assertEquals("verified no command ran", resolution.reason)

            val processingAgain = retried.copy(
                status = RewardActionStatus.PROCESSING,
                updatedAt = resolvedAt.plusSeconds(1),
            )
            storage.saveRewardAction(processingAgain)
            val delivered = processingAgain.copy(
                status = RewardActionStatus.DELIVERED,
                updatedAt = resolvedAt.plusSeconds(2),
            )
            storage.saveRewardAction(delivered)
            assertThrows<IllegalArgumentException> {
                storage.saveRewardAction(
                    delivered.copy(status = RewardActionStatus.PENDING, updatedAt = resolvedAt.plusSeconds(3)),
                )
            }
        }
    }

    @Test
    fun `Reward Delivery rejects a completed state with pending Actions`() {
        val database = temporaryDirectory.resolve("reward-aggregate-state.db")
        val guard = BlockingOperationGuard { }
        val recipientId = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
        val delivery = StoredRewardDelivery(
            UUID.fromString("31000000-0000-0000-0000-0000000000c1"), null, "invalid",
            RewardDeliveryStatus.DELIVERED, "invalid-delivery", Instant.EPOCH, Instant.EPOCH,
        )
        val pending = StoredRewardAction(
            UUID.fromString("30000000-0000-0000-0000-0000000000c1"), delivery.id, null,
            recipientId, 0, "message", "hello", RewardActionStatus.PENDING,
            "invalid-action", Instant.EPOCH, Instant.EPOCH,
        )
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use { storage ->
            storage.savePlayer(StoredPlayer(recipientId, Instant.EPOCH))

            assertThrows<IllegalArgumentException> { storage.saveRewardDelivery(delivery, listOf(pending)) }
            assertEquals(null, storage.rewardDelivery(delivery.id))
        }
    }

    @Test
    fun `Qualification Fact is immutable but accepts an identical replay`() {
        val database = temporaryDirectory.resolve("immutable-qualification-fact.db")
        val guard = BlockingOperationGuard { }
        val inviterId = UUID.fromString("00000000-0000-0000-0000-0000000000d1")
        val inviteeId = UUID.fromString("00000000-0000-0000-0000-0000000000d2")
        val invitation = Invitation(
            UUID.fromString("10000000-0000-0000-0000-0000000000d1"), inviterId, inviteeId,
            InvitationStatus.CLAIMED, Instant.EPOCH,
        )
        val fact = QualificationFact(
            UUID.fromString("20000000-0000-0000-0000-0000000000d1"), invitation.id,
            "default", "revision-1", "play-time", QualificationFactOutcome.SATISFIED, Instant.EPOCH,
        )
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use { storage ->
            storage.savePlayer(StoredPlayer(inviterId, Instant.EPOCH))
            storage.savePlayer(StoredPlayer(inviteeId, Instant.EPOCH))
            storage.saveInvitation(invitation)
            storage.saveQualificationFact(fact)
            storage.saveQualificationFact(fact)

            assertThrows<SqliteStorageException> {
                storage.saveQualificationFact(
                    fact.copy(outcome = QualificationFactOutcome.UNSATISFIED, recordedAt = Instant.EPOCH.plusSeconds(1)),
                )
            }
            assertEquals(fact, storage.qualificationFact(fact.id))
        }
    }

    @Test
    fun `write rollback failure reports uncertain database state`() {
        val database = temporaryDirectory.resolve("write-rollback-failure.db")
        val delegate = connect(database)
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, arguments ->
            if (method.name == "createStatement") {
                val statement = method.invoke(delegate, *(arguments ?: emptyArray())) as Statement
                return@newProxyInstance Proxy.newProxyInstance(
                    Statement::class.java.classLoader,
                    arrayOf(Statement::class.java),
                ) statementProxy@{ _, statementMethod, statementArguments ->
                    if (
                        statementMethod.name == "execute" &&
                        statementArguments?.firstOrNull() == "ROLLBACK"
                    ) {
                        throw SQLException("simulated write rollback failure")
                    }
                    try {
                        statementMethod.invoke(statement, *(statementArguments ?: emptyArray()))
                    } catch (error: InvocationTargetException) {
                        throw error.targetException
                    }
                }
            }
            try {
                method.invoke(delegate, *(arguments ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        } as Connection

        try {
            val error = assertThrows<SqliteStorageException> {
                executeImmediateWrite(connection, database) { active ->
                    active.createStatement().use { it.execute("CREATE TABLE partial_write(value TEXT)") }
                    throw SQLException("simulated write failure")
                }
            }
            assertEquals(true, error.message!!.contains("database state is uncertain"))
            assertEquals("simulated write rollback failure", error.suppressed.single().message)
        } finally {
            delegate.close()
        }
    }
}
