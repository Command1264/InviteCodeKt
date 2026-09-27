package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class OfflineAnonymizeMainTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `offline command copies and verifies backup before anonymizing the requested player`() {
        val database = directory.resolve("players.db")
        val secret = directory.resolve("anti-abuse.key")
        val backup = directory.resolve("players.before-anonymization.db")
        val playerId = UUID.randomUUID()
        val guard = BlockingOperationGuard { }
        NetworkAddressHasher.loadOrCreate(secret).close()
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use { storage ->
            storage.savePlayer(StoredPlayer(playerId, Instant.parse("2026-01-01T00:00:00Z"), "Alice"))
        }

        OfflineAnonymizeMain.execute(arrayOf(
            "--backend", "sqlite", "--source", database.toString(), "--secret", secret.toString(),
            "--player", playerId.toString(), "--apply", "--stopped", "--backup", backup.toString(),
        ))

        assertTrue(Files.isRegularFile(backup))
        SqliteInviteCodeStorage.open(backup, guard).use { assertNotNull(it.player(playerId)) }
        SqliteInviteCodeStorage.open(database, guard).use { assertNull(it.player(playerId)) }
    }
}
