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
import io.github.command1264.invitecode.core.Invitation
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.core.InvitationAuditDetails
import io.github.command1264.invitecode.core.QualificationFact
import io.github.command1264.invitecode.core.QualificationFactOutcome
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.api.InvitationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files
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
            details = InvitationAuditDetails.ForceQualification("sha256:qualification-v1"),
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
}
