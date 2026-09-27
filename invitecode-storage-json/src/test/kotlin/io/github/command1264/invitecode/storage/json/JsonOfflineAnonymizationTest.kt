package io.github.command1264.invitecode.storage.json

import com.google.gson.JsonParser
import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.CodeReservationKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class JsonOfflineAnonymizationTest {
    @TempDir lateinit var directory: Path
    private val guard = BlockingOperationGuard { }
    private val key = CodeReservationKey("a".repeat(64)) { "b".repeat(64) }

    @Test
    fun `anonymization persists across JSON restart`() {
        val file = directory.resolve("players.json")
        val oldId = UUID.randomUUID()
        var historicalId: UUID? = null
        JsonInviteCodeStorage.open(file, guard, key).use { storage ->
            storage.savePlayer(StoredPlayer(oldId, Instant.parse("2026-01-01T00:00:00Z"), "Alice"))
            historicalId = storage.anonymizePlayer(oldId).historicalId
        }
        JsonInviteCodeStorage.open(file, guard, key).use { storage ->
            assertNull(storage.player(oldId))
            assertNotNull(storage.player(requireNotNull(historicalId)))
            assertNull(storage.player(requireNotNull(historicalId))?.lastKnownName)
            storage.savePlayer(StoredPlayer(oldId, Instant.now(), "AliceAgain"))
            assertTrue(storage.invitationsByInviter(oldId).isEmpty())
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
}
