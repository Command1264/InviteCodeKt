package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardDeliveryCoordinator
import io.github.command1264.invitecode.core.RewardDeliveryStatus
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.core.StoredRewardDelivery
import io.github.command1264.invitecode.storage.json.JsonInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import io.github.command1264.invitecode.storage.sql.SqliteSnapshotAccess
import io.github.command1264.invitecode.storage.sql.StorageTransferException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class OfflineStorageTransferMainTest {
    @TempDir lateinit var directory: Path
    private val guard = BlockingOperationGuard { }

    @Test
    fun `preview rejects an orphaned source before reporting success or creating a backup`() {
        val key = directory.resolve("orphan.key")
        Files.write(key, ByteArray(32) { 7 })
        val source = directory.resolve("orphan.db")
        val destination = directory.resolve("orphan.json")
        val backup = directory.resolve("orphan-backup.db")
        SqliteSchemaMigrator(source, guard).migrate()
        SqliteSnapshotAccess.withConnection(source, guard) { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys=OFF") }
            connection.prepareStatement(
                "INSERT INTO invitecode_codes(code, inviter_id, activated_at) VALUES (?, ?, ?)",
            ).use { statement ->
                statement.setString(1, "A1B2C3")
                statement.setString(2, UUID.randomUUID().toString())
                statement.setString(3, "2026-01-01T00:00:00Z")
                statement.executeUpdate()
            }
        }
        val original = Files.readAllBytes(source)
        val options = arrayOf(
            "--from-backend", "sqlite", "--from-file", source.toString(), "--from-secret-file", key.toString(),
            "--to-backend", "json", "--to-file", destination.toString(), "--to-secret-file", key.toString(),
            "--stopped",
        )
        val error = assertThrows(StorageTransferException::class.java) {
            OfflineStorageTransferMain.execute(options)
        }
        assertTrue(error.message.orEmpty().contains("foreign-key"))
        assertThrows(StorageTransferException::class.java) {
            OfflineStorageTransferMain.execute(options + arrayOf("--apply", "--backup", backup.toString()))
        }
        assertFalse(Files.exists(destination))
        assertFalse(Files.exists(backup))
        assertTrue(original.contentEquals(Files.readAllBytes(source)))
    }

    @Test
    fun `SQLite to JSON to SQLite preserves uncertain rewards after reopening and never overwrites source`() {
        val key = directory.resolve("anti-abuse.key")
        Files.write(key, ByteArray(32) { 7 })
        val source = directory.resolve("source.db")
        val json = directory.resolve("destination.json")
        val result = directory.resolve("result.db")
        val player = StoredPlayer(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"), "Alice")
        val delivery = StoredRewardDelivery(
            UUID.randomUUID(), null, "welcome", RewardDeliveryStatus.PENDING,
            "welcome:${player.id}", player.firstSeenAt, player.firstSeenAt,
        )
        val action = StoredRewardAction(
            UUID.randomUUID(), delivery.id, null, player.id, 0, "MESSAGE", "welcome",
            RewardActionStatus.PENDING, "${delivery.idempotencyKey}:0", player.firstSeenAt, player.firstSeenAt,
        )
        SqliteSchemaMigrator(source, guard).migrate()
        SqliteInviteCodeStorage.open(source, guard).use {
            it.savePlayer(player)
            it.saveRewardDelivery(delivery, listOf(action))
            RewardDeliveryCoordinator(it).apply {
                begin(action.id, player.firstSeenAt.plusSeconds(1))
                uncertain(action.id, "test interrupted action", player.firstSeenAt.plusSeconds(2))
            }
        }
        val original = Files.readAllBytes(source)
        val first = arrayOf(
            "--from-backend", "sqlite", "--from-file", source.toString(), "--from-secret-file", key.toString(),
            "--to-backend", "json", "--to-file", json.toString(), "--to-secret-file", key.toString(), "--stopped",
        )
        OfflineStorageTransferMain.execute(first)
        assertFalse(Files.exists(json))
        val conflictingBackup = assertThrows(IllegalArgumentException::class.java) {
            OfflineStorageTransferMain.execute(first + arrayOf("--apply", "--backup", json.toString()))
        }
        assertTrue(conflictingBackup.message!!.contains("備份路徑不可與目的資料檔相同"))
        assertFalse(Files.exists(json))
        OfflineStorageTransferMain.execute(first + arrayOf("--apply", "--backup", directory.resolve("source-backup.db").toString()))
        assertTrue(original.contentEquals(Files.readAllBytes(source)))
        assertTrue(original.contentEquals(Files.readAllBytes(directory.resolve("source-backup.db"))))
        JsonInviteCodeStorage.open(json, guard).use {
            assertEquals(player, it.player(player.id))
            assertEquals(RewardActionStatus.UNCERTAIN, it.rewardAction(action.id)?.status)
            assertEquals(action.idempotencyKey, it.rewardAction(action.id)?.idempotencyKey)
            assertEquals(3, it.rewardActionTransitions(action.id).size)
            assertTrue(it.pendingRewardActions(player.id).isEmpty())
            assertEquals(0, it.recoverInterruptedRewardActions(player.firstSeenAt.plusSeconds(10)))
        }

        val second = arrayOf(
            "--from-backend", "json", "--from-file", json.toString(), "--from-secret-file", key.toString(),
            "--to-backend", "sqlite", "--to-file", result.toString(), "--to-secret-file", key.toString(),
            "--stopped", "--apply", "--backup", directory.resolve("json-backup.json").toString(),
        )
        OfflineStorageTransferMain.execute(second)
        SqliteInviteCodeStorage.open(result, guard).use {
            assertEquals(player, it.player(player.id))
            assertEquals(RewardActionStatus.UNCERTAIN, it.rewardAction(action.id)?.status)
            assertEquals(action.idempotencyKey, it.rewardAction(action.id)?.idempotencyKey)
            assertEquals(3, it.rewardActionTransitions(action.id).size)
            assertTrue(it.pendingRewardActions(player.id).isEmpty())
            assertEquals(0, it.recoverInterruptedRewardActions(player.firstSeenAt.plusSeconds(10)))
        }
    }

    @Test
    fun `mismatched key and occupied destination are rejected without source changes`() {
        val key = directory.resolve("source.key")
        val different = directory.resolve("different.key")
        Files.write(key, ByteArray(32) { 7 })
        Files.write(different, ByteArray(32) { 8 })
        val source = directory.resolve("source.db")
        val destination = directory.resolve("destination.json")
        SqliteSchemaMigrator(source, guard).migrate()
        val arguments = arrayOf(
            "--from-backend", "sqlite", "--from-file", source.toString(), "--from-secret-file", key.toString(),
            "--to-backend", "json", "--to-file", destination.toString(), "--to-secret-file", different.toString(),
            "--stopped", "--apply", "--backup", directory.resolve("backup.db").toString(),
        )
        val mismatch = assertThrows(IllegalArgumentException::class.java) {
            OfflineStorageTransferMain.execute(arguments)
        }
        assertTrue(mismatch.message!!.contains("密鑰不同"))
        assertFalse(Files.exists(destination))
        assertFalse(Files.exists(directory.resolve("backup.db")))
        Files.write(destination, byteArrayOf(1))
        val occupied = assertThrows(IllegalArgumentException::class.java) {
            OfflineStorageTransferMain.execute(arguments)
        }
        assertTrue(occupied.message!!.contains("目的資料檔已存在"))
    }
}
