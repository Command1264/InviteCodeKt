package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.storage.json.JsonInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
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
    fun `SQLite to JSON to SQLite preserves players and never overwrites source`() {
        val key = directory.resolve("anti-abuse.key")
        Files.write(key, ByteArray(32) { 7 })
        val source = directory.resolve("source.db")
        val json = directory.resolve("destination.json")
        val result = directory.resolve("result.db")
        val player = StoredPlayer(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"), "Alice")
        SqliteSchemaMigrator(source, guard).migrate()
        SqliteInviteCodeStorage.open(source, guard).use { it.savePlayer(player) }
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
        JsonInviteCodeStorage.open(json, guard).use { assertEquals(player, it.player(player.id)) }

        val second = arrayOf(
            "--from-backend", "json", "--from-file", json.toString(), "--from-secret-file", key.toString(),
            "--to-backend", "sqlite", "--to-file", result.toString(), "--to-secret-file", key.toString(),
            "--stopped", "--apply", "--backup", directory.resolve("json-backup.json").toString(),
        )
        OfflineStorageTransferMain.execute(second)
        SqliteInviteCodeStorage.open(result, guard).use { assertEquals(player, it.player(player.id)) }
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
