package io.github.command1264.invitecode.storage.json

import com.google.gson.JsonParser
import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.core.*
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.CodeReservationKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import java.util.UUID

class JsonOfflineAnonymizationTest {
    @TempDir lateinit var directory: Path
    private val guard = BlockingOperationGuard { }
    private val key = CodeReservationKey("a".repeat(64)) { "b".repeat(64) }

    @Test
    fun `anonymization preserves full history while removing identity from durable snapshot`() {
        val file = directory.resolve("players.json")
        val inviter = UUID.randomUUID()
        val invitee = UUID.randomUUID()
        val unrelatedPlayer = UUID.randomUUID()
        val invitationId = UUID.randomUUID()
        val auditId = UUID.randomUUID()
        val code = InviteCode.fromNormalized("A1B2C3")
        val claimedAt = Instant.parse("2026-01-02T03:04:05Z")
        val qualifiedAt = Instant.parse("2026-01-03T03:04:05Z")
        val rewardAt = Instant.parse("2026-01-04T03:04:05Z")
        val deliveredAt = rewardAt.plusSeconds(1)
        val delivery = StoredRewardDelivery(
            UUID.randomUUID(), invitationId, "reward-Alice", RewardDeliveryStatus.PENDING,
            "delivery:$inviter", qualifiedAt, qualifiedAt,
        )
        val reward = StoredRewardAction(
            UUID.randomUUID(), delivery.id, invitationId, inviter, 0, "MESSAGE", "Hello Alice $inviter",
            RewardActionStatus.PENDING, "action:$inviter", qualifiedAt, qualifiedAt,
        )
        val claimedInvitation = Invitation(invitationId, inviter, invitee, InvitationStatus.CLAIMED, claimedAt)
        val invitation = Invitation(
            invitationId, inviter, invitee, InvitationStatus.QUALIFIED, claimedAt,
            qualifiedAt, "days-online", "revision-7",
        )
        val fact = QualificationFact(
            UUID.randomUUID(), invitationId, "days-online", "revision-7", "three-days",
            QualificationFactOutcome.SATISFIED, qualifiedAt,
        )
        val loginDay = LocalDate.parse("2026-01-01")
        val networkHash = "c".repeat(64)

        var historicalId: UUID
        JsonInviteCodeStorage.open(file, guard, key).use { storage ->
            storage.savePlayer(StoredPlayer(inviter, Instant.parse("2025-12-01T00:00:00Z"), "Alice"))
            storage.savePlayer(StoredPlayer(invitee, Instant.parse("2025-12-02T00:00:00Z"), "Bob"))
            storage.savePlayer(StoredPlayer(unrelatedPlayer, Instant.parse("2025-12-03T00:00:00Z"), "Carol"))
            storage.atomically { it.activate(ActiveInviteCode(inviter, code, claimedAt.minusSeconds(60))) }
            storage.saveInvitation(claimedInvitation)
            storage.saveQualifiedInvitationWithRewards(
                invitation, listOf(fact), listOf(RewardDeliveryPlan(delivery, listOf(reward))),
            )
            storage.saveRewardAction(reward.copy(status = RewardActionStatus.PROCESSING, updatedAt = rewardAt))
            val deliveredAction = reward.copy(status = RewardActionStatus.DELIVERED, updatedAt = deliveredAt)
            storage.saveRewardAction(deliveredAction)
            // saveRewardAction updates the aggregate automatically; persist the final delivery
            // state explicitly through the complete-set API as a separate contract assertion.
            storage.saveRewardDelivery(
                delivery.copy(status = RewardDeliveryStatus.DELIVERED, updatedAt = deliveredAt),
                listOf(deliveredAction),
            )
            storage.recordLoginDay(inviter, loginDay)
            storage.recordNetworkHash(PlayerNetworkHash(inviter, "a".repeat(64), networkHash, claimedAt, rewardAt))
            storage.saveAuditRecord(AuditRecord(
                auditId, InvitationAuditAction.ELIGIBILITY_RESET, "Alice", "Alice requested for $inviter",
                rewardAt, playerId = inviter,
            ))

            val result = storage.anonymizePlayer(inviter)
            historicalId = result.historicalId
            assertNotEquals(inviter, historicalId)
            assertNull(storage.player(inviter))
            assertEquals(historicalId, storage.invitation(invitationId)?.inviterId)
            assertEquals(invitee, storage.invitation(invitationId)?.inviteeId)
            assertEquals(invitation.status, storage.invitation(invitationId)?.status)
            assertEquals(invitation.claimedAt, storage.invitation(invitationId)?.claimedAt)
            assertEquals(invitation.qualifiedAt, storage.invitation(invitationId)?.qualifiedAt)
            assertEquals(listOf(fact.copy(
                policyId = "[anonymized]", qualificationRevision = "[anonymized]", conditionId = "[anonymized]",
            )), storage.qualificationFacts(invitationId))
            assertEquals(1, storage.previewAnonymization(invitee).invitations)
            assertTrue(storage.isInviteCodeReserved(code))
            assertNull(storage.activeInviteCode(code))
            assertTrue(storage.auditHistoryByPlayer(historicalId, 10).any {
                it.action == InvitationAuditAction.ELIGIBILITY_RESET && it.playerId == historicalId
            })
            assertEquals(0, storage.loginDays(inviter))
        }

        val snapshot = readUtf8(file)
        listOf(inviter.toString(), "Alice", networkHash).forEach { assertFalse(snapshot.contains(it), "leaked $it") }
        assertTrue(snapshot.contains(historicalId.toString()))
        val json = Files.newBufferedReader(file, StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        assertTrue(json.getAsJsonObject("tables").getAsJsonArray("invitecode_qualification_facts").size() > 0)

        JsonInviteCodeStorage.open(file, guard, key).use { reopened ->
            assertNull(reopened.player(inviter))
            assertNotNull(reopened.player(historicalId))
            assertNull(reopened.player(historicalId)?.lastKnownName)
            val persistedInvitation = reopened.invitation(invitationId)
            assertEquals(historicalId, persistedInvitation?.inviterId)
            assertEquals(invitee, persistedInvitation?.inviteeId)
            assertEquals(invitation.status, persistedInvitation?.status)
            assertEquals(claimedAt, persistedInvitation?.claimedAt)
            assertEquals(qualifiedAt, persistedInvitation?.qualifiedAt)
            assertEquals(
                listOf(fact.copy(
                    policyId = "[anonymized]", qualificationRevision = "[anonymized]", conditionId = "[anonymized]",
                )),
                reopened.qualificationFacts(invitationId),
            )
            val persistedDelivery = reopened.rewardDeliveries(invitationId).single()
            assertEquals(RewardDeliveryStatus.DELIVERED, persistedDelivery.status)
            assertEquals(delivery.createdAt, persistedDelivery.createdAt)
            assertEquals(deliveredAt, persistedDelivery.updatedAt)
            assertEquals("[anonymized]", persistedDelivery.rewardId)
            val persistedReward = reopened.rewardActions(persistedDelivery.id).single()
            assertEquals(historicalId, persistedReward.recipientId)
            assertEquals(RewardActionStatus.DELIVERED, persistedReward.status)
            assertEquals(qualifiedAt, persistedReward.createdAt)
            assertEquals(deliveredAt, persistedReward.updatedAt)
            assertEquals("[anonymized]", persistedReward.payload)
            assertFalse(persistedReward.idempotencyKey.contains(inviter.toString()))
            assertEquals(deliveredAt, reopened.rewardActionTransitions(persistedReward.id).last().occurredAt)
            assertEquals(1, reopened.previewAnonymization(historicalId).invitations)
            assertTrue(reopened.isInviteCodeReserved(code))
            assertNull(reopened.activeInviteCode(code))
            reopened.savePlayer(StoredPlayer(inviter, Instant.parse("2026-02-01T00:00:00Z"), "AliceAgain"))
            assertNotNull(reopened.player(inviter))
            assertTrue(reopened.invitationsByInviter(inviter).isEmpty())
            assertThrows(IllegalStateException::class.java) {
                reopened.atomically { it.activate(ActiveInviteCode(inviter, code, rewardAt.plusSeconds(1))) }
            }
            val claimingPlayer = UUID.randomUUID()
            reopened.savePlayer(StoredPlayer(claimingPlayer, rewardAt))
            val confirmation = ClaimConfirmation.present(
                claimingPlayer, inviter, "AliceAgain", code, rewardAt, Duration.ofMinutes(1),
            ).confirm(claimingPlayer, rewardAt.plusSeconds(1))
            val claimService = InvitationClaimService(reopened, ClaimEligibilityPolicy(Duration.ofDays(7)), UUID::randomUUID)
            assertTrue(claimService.claim(confirmation) is InvitationClaimResult.InviteCodeUnavailable)
        }
    }

    @Test
    fun `unresolved reward action states reject anonymization without changing snapshot`() {
        listOf(RewardActionStatus.PENDING, RewardActionStatus.PROCESSING, RewardActionStatus.UNCERTAIN).forEach { status ->
            val file = directory.resolve("${status.name.lowercase()}.json")
            val playerId = UUID.randomUUID()
            val whenCreated = Instant.parse("2026-01-01T00:00:00Z")
            JsonInviteCodeStorage.open(file, guard, key).use { storage ->
                storage.savePlayer(StoredPlayer(playerId, whenCreated, "Alice"))
                val delivery = StoredRewardDelivery(UUID.randomUUID(), null, "reward", RewardDeliveryStatus.PENDING,
                    "delivery:$playerId", whenCreated, whenCreated)
                val action = StoredRewardAction(UUID.randomUUID(), delivery.id, null, playerId, 0, "MESSAGE", "Alice",
                    RewardActionStatus.PENDING, "action:$playerId", whenCreated, whenCreated)
                storage.saveRewardDelivery(delivery, listOf(action))
                if (status != RewardActionStatus.PENDING) {
                    val processing = action.copy(status = RewardActionStatus.PROCESSING, updatedAt = whenCreated.plusSeconds(1))
                    storage.saveRewardAction(processing)
                    if (status == RewardActionStatus.UNCERTAIN) {
                        storage.saveRewardAction(processing.copy(
                            status = RewardActionStatus.UNCERTAIN, updatedAt = whenCreated.plusSeconds(2),
                        ))
                    }
                }
                val before = readUtf8(file)
                assertThrows(IllegalStateException::class.java) { storage.anonymizePlayer(playerId) }
                assertEquals(before, readUtf8(file))
                assertEquals("Alice", storage.player(playerId)?.lastKnownName)
            }
        }
    }

    @Test
    fun `second open is rejected while first handle owns JSON file lock`() {
        val file = directory.resolve("locked.json")
        JsonInviteCodeStorage.open(file, guard).use {
            assertThrows(JsonStorageException::class.java) { JsonInviteCodeStorage.open(file, guard) }
        }
        JsonInviteCodeStorage.open(file, guard).close()
    }

    @Test
    fun `failed anonymization snapshot leaves previous complete identity after restart`() {
        val file = directory.resolve("snapshot-failure.json")
        val playerId = UUID.randomUUID()
        val code = InviteCode.fromNormalized("D4E5F6")
        JsonInviteCodeStorage.open(file, guard, key).use { storage ->
            storage.savePlayer(StoredPlayer(playerId, Instant.parse("2026-01-01T00:00:00Z"), "Alice"))
            storage.atomically { it.activate(ActiveInviteCode(playerId, code, Instant.parse("2026-01-01T00:00:00Z"))) }
        }
        val original = readUtf8(file)
        // A directory at the backup destination makes the atomic backup replacement fail reliably.
        val previous = file.resolveSibling("${file.fileName}.previous")
        // Replace this test's generated backup with an unwritable target type before the operation.
        Files.deleteIfExists(previous)
        Files.createDirectory(previous)
        JsonInviteCodeStorage.open(file, guard, key).use { storage ->
            assertThrows(JsonStorageException::class.java) { storage.anonymizePlayer(playerId) }
            assertNull(storage.player(playerId))
        }
        assertEquals(original, readUtf8(file))
        Files.delete(file.resolveSibling("${file.fileName}.previous"))
        JsonInviteCodeStorage.open(file, guard, key).use { reopened ->
            assertEquals("Alice", reopened.player(playerId)?.lastKnownName)
            assertEquals(playerId, reopened.activeInviteCode(code)?.inviterId)
        }
    }

    @Test
    fun `explicit version-eight migration preserves JSON player`() {
        val file = directory.resolve("legacy.json")
        val playerId = UUID.randomUUID()
        JsonInviteCodeStorage.open(file, guard).use { storage ->
            storage.savePlayer(StoredPlayer(playerId, Instant.parse("2026-01-01T00:00:00Z"), "Alice"))
        }
        val root = Files.newBufferedReader(file, StandardCharsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject
        }
        root.addProperty("schema-version", 8)
        val tables = root.getAsJsonObject("tables")
        tables.remove("invitecode_reserved_code_hashes")
        tables.getAsJsonArray("invitecode_schema_version")[0].asJsonObject.addProperty("version", 8)
        Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { it.write(root.toString()) }

        JsonInviteCodeStorage.migrateV8ToV9(file, guard)
        assertTrue(Files.exists(file.resolveSibling("legacy.json.previous")))
        JsonInviteCodeStorage.open(file, guard).use { storage ->
            assertEquals("Alice", storage.player(playerId)?.lastKnownName)
        }
    }

    private fun readUtf8(path: Path): String =
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { it.readText() }
}
