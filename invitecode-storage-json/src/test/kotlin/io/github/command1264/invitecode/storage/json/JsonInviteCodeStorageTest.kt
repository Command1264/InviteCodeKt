package io.github.command1264.invitecode.storage.json

import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Instant
import java.util.UUID

class JsonInviteCodeStorageTest {
    @TempDir lateinit var directory: Path
    private val guard = BlockingOperationGuard { }

    @Test
    fun `player survives reopen and prior snapshot remains recoverable`() {
        val file = directory.resolve("invitecode.json")
        val first = StoredPlayer(UUID.randomUUID(), Instant.parse("2026-09-23T00:00:00Z"), "First")
        val second = StoredPlayer(UUID.randomUUID(), first.firstSeenAt, "Second")
        JsonInviteCodeStorage.open(file, guard).use { storage ->
            storage.savePlayer(first)
            storage.savePlayer(second)
        }
        JsonInviteCodeStorage.open(file, guard).use { storage ->
            assertEquals(first, storage.player(first.id))
            assertEquals(second, storage.player(second.id))
        }
        val previous = file.resolveSibling("invitecode.json.previous")
        assertTrue(Files.exists(previous))
        val text = String(Files.readAllBytes(previous), StandardCharsets.UTF_8)
        assertTrue(text.contains(first.id.toString()))
    }

    @Test
    fun `corrupt JSON is rejected without changing source`() {
        val file = directory.resolve("invitecode.json")
        Files.write(file, "{not-json".toByteArray(StandardCharsets.UTF_8))
        val before = Files.readAllBytes(file)
        val error = assertThrows(JsonStorageException::class.java) {
            JsonInviteCodeStorage.open(file, guard)
        }
        assertTrue(error.message!!.contains("damaged"))
        assertTrue(before.contentEquals(Files.readAllBytes(file)))
    }

    @Test
    fun `unsupported format fails closed`() {
        val file = directory.resolve("invitecode.json")
        JsonInviteCodeStorage.open(file, guard).close()
        val content = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            .replace("\"format-version\": 1", "\"format-version\": 999")
        Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
        val error = assertThrows(JsonStorageException::class.java) {
            JsonInviteCodeStorage.open(file, guard)
        }
        assertTrue(error.message!!.contains("unsupported"))
        assertTrue(String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("999"))
    }

    @Test
    fun `second writer is rejected and first writer remains usable`() {
        val file = directory.resolve("invitecode.json")
        JsonInviteCodeStorage.open(file, guard).use { first ->
            assertThrows(JsonStorageException::class.java) { JsonInviteCodeStorage.open(file, guard) }
            val player = StoredPlayer(UUID.randomUUID(), Instant.parse("2026-09-23T00:00:00Z"))
            first.savePlayer(player)
            assertEquals(player, first.player(player.id))
        }
        JsonInviteCodeStorage.open(file, guard).close()
    }

    @Test
    fun `operator can restore previous valid snapshot after corruption`() {
        val file = directory.resolve("invitecode.json")
        val first = StoredPlayer(UUID.randomUUID(), Instant.parse("2026-09-23T00:00:00Z"))
        JsonInviteCodeStorage.open(file, guard).use { storage ->
            storage.savePlayer(first)
            storage.savePlayer(StoredPlayer(UUID.randomUUID(), first.firstSeenAt))
        }
        Files.write(file, "broken".toByteArray(StandardCharsets.UTF_8))
        assertThrows(JsonStorageException::class.java) { JsonInviteCodeStorage.open(file, guard) }
        Files.copy(file.resolveSibling("invitecode.json.previous"), file, REPLACE_EXISTING)
        JsonInviteCodeStorage.open(file, guard).use { assertEquals(first, it.player(first.id)) }
    }

    @Test
    fun `reward delivery and transition survive reopen`() {
        val file = directory.resolve("invitecode.json")
        val now = Instant.parse("2026-09-23T00:00:00Z")
        val recipient = UUID.randomUUID()
        val delivery = StoredRewardDelivery(
            UUID.randomUUID(), null, "welcome", RewardDeliveryStatus.PENDING,
            "welcome:$recipient", now, now,
        )
        val action = StoredRewardAction(
            UUID.randomUUID(), delivery.id, null, recipient, 0, "MESSAGE", "welcome.message",
            RewardActionStatus.PENDING, "${delivery.idempotencyKey}:0", now, now,
        )
        JsonInviteCodeStorage.open(file, guard).use { storage ->
            storage.savePlayer(StoredPlayer(recipient, now))
            storage.saveRewardDelivery(delivery, listOf(action))
            storage.saveRewardAction(action.copy(status = RewardActionStatus.PROCESSING, updatedAt = now.plusSeconds(1)))
        }
        JsonInviteCodeStorage.open(file, guard).use { storage ->
            assertEquals(RewardActionStatus.PROCESSING, storage.rewardAction(action.id)?.status)
            assertEquals(2, storage.rewardActionTransitions(action.id).size)
            assertEquals(1, storage.recoverInterruptedRewardActions(now.plusSeconds(2)))
        }
        JsonInviteCodeStorage.open(file, guard).use { storage ->
            assertEquals(RewardActionStatus.UNCERTAIN, storage.rewardAction(action.id)?.status)
            assertEquals(3, storage.rewardActionTransitions(action.id).size)
        }
    }
}
