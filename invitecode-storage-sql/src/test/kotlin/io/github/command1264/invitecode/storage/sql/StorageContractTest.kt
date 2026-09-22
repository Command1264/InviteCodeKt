package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

abstract class StorageContractTest {
    protected abstract fun withStorage(test: (InviteCodeStorage) -> Unit)

    @Test
    fun `player identity and unique login days survive storage operations`() = withStorage { storage ->
        val id = UUID.randomUUID()
        val firstSeen = Instant.parse("2026-09-22T00:00:00Z")
        storage.savePlayer(StoredPlayer(id, firstSeen, "Original"))
        storage.savePlayer(StoredPlayer(id, firstSeen.plusSeconds(5), "Latest"))
        storage.recordLoginDay(id, LocalDate.parse("2026-09-22"))
        storage.recordLoginDay(id, LocalDate.parse("2026-09-22"))
        assertEquals(StoredPlayer(id, firstSeen, "Latest"), storage.player(id))
        assertEquals(1, storage.loginDays(id))
    }

    @Test
    fun `reward state and transition history are durable`() = withStorage { storage ->
        val recipient = UUID.randomUUID()
        val now = Instant.parse("2026-09-22T00:00:00Z")
        val deliveryId = UUID.randomUUID()
        val actionId = UUID.randomUUID()
        storage.savePlayer(StoredPlayer(recipient, now))
        val delivery = StoredRewardDelivery(
            deliveryId, null, "first-milestone", RewardDeliveryStatus.PENDING,
            "milestone:$recipient:1:first-milestone", now, now,
        )
        val action = StoredRewardAction(
            actionId, deliveryId, null, recipient, 0, "MESSAGE", "hello",
            RewardActionStatus.PENDING, "${delivery.idempotencyKey}:0", now, now,
        )
        storage.saveRewardDelivery(delivery, listOf(action))
        assertEquals(delivery, storage.rewardDelivery(deliveryId))
        assertEquals(listOf(action), storage.rewardActions(deliveryId))
        storage.saveRewardAction(action.copy(status = RewardActionStatus.PROCESSING, updatedAt = now.plusSeconds(1)))
        assertEquals(1, storage.recoverInterruptedRewardActions(now.plusSeconds(2)))
        assertEquals(RewardActionStatus.UNCERTAIN, storage.rewardAction(actionId)?.status)
        assertEquals(3, storage.rewardActionTransitions(actionId).size)
    }
}

class SqliteStorageContractTest : StorageContractTest() {
    @TempDir lateinit var temporaryDirectory: Path

    override fun withStorage(test: (InviteCodeStorage) -> Unit) {
        val database = temporaryDirectory.resolve("contract.db")
        val guard = BlockingOperationGuard { }
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use(test)
    }
}
