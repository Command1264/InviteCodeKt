package io.github.command1264.invitecode.storage.json

import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class JsonOfflineTransferTest {
    @TempDir lateinit var directory: Path
    private val guard = BlockingOperationGuard { }

    @Test
    fun `destination is published only after import succeeds and survives restart`() {
        val destination = directory.resolve("destination.json")
        val id = UUID.randomUUID()
        assertThrows(IllegalStateException::class.java) {
            JsonInviteCodeStorage.createOfflineFromSqlite(destination, guard) { connection ->
                connection.prepareStatement(
                    "INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, id.toString())
                    statement.setString(2, "2026-01-01T00:00:00Z")
                    statement.setString(3, "Alice")
                    statement.executeUpdate()
                }
                error("verification failed")
            }
        }
        assertFalse(Files.exists(destination))

        JsonInviteCodeStorage.createOfflineFromSqlite(destination, guard) { connection ->
            connection.prepareStatement(
                "INSERT INTO invitecode_players(player_id, first_seen_at, last_known_name) VALUES (?, ?, ?)",
            ).use { statement ->
                statement.setString(1, id.toString())
                statement.setString(2, "2026-01-01T00:00:00Z")
                statement.setString(3, "Alice")
                statement.executeUpdate()
            }
        }
        assertTrue(Files.isRegularFile(destination))
        JsonInviteCodeStorage.open(destination, guard).use { storage ->
            assertEquals("Alice", storage.player(id)?.lastKnownName)
        }
        assertThrows(IllegalArgumentException::class.java) {
            JsonInviteCodeStorage.createOfflineFromSqlite(destination, guard) { }
        }
    }

    @Test
    fun `destination created during transfer is never replaced`() {
        val destination = directory.resolve("concurrent.json")
        val other = "another writer".toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            JsonInviteCodeStorage.createOfflineFromSqlite(destination, guard) {
                Files.write(destination, other)
            }
        }
        assertTrue(other.contentEquals(Files.readAllBytes(destination)))
    }
}
