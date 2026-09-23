package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class JsonRuntimeBootstrapTest {
    @TempDir lateinit var directory: Path
    private val guard = BlockingOperationGuard { }

    @Test
    fun `fresh JSON installation creates data and secret and reopens existing player`() {
        val file = directory.resolve("invitecode.json")
        val secret = directory.resolve("anti-abuse.key")
        val first = JsonRuntimeBootstrap(file, secret, guard).initializeOrOpen()
        val player = StoredPlayer(UUID.randomUUID(), Instant.parse("2026-09-23T00:00:00Z"))
        first.storage.use { it.savePlayer(player) }
        first.networkHasher.close()
        assertTrue(first.initialized)
        assertTrue(Files.isRegularFile(file))
        assertEquals(32, Files.size(secret))

        val second = JsonRuntimeBootstrap(file, secret, guard).initializeOrOpen()
        second.storage.use { assertEquals(player, it.player(player.id)) }
        second.networkHasher.close()
        assertFalse(second.initialized)
    }

    @Test
    fun `existing JSON file without secret is preserved and rejected`() {
        val file = directory.resolve("invitecode.json")
        val secret = directory.resolve("anti-abuse.key")
        val first = JsonRuntimeBootstrap(file, secret, guard).initializeOrOpen()
        first.storage.close()
        first.networkHasher.close()
        Files.delete(secret)
        val before = Files.readAllBytes(file)

        assertThrows<IllegalArgumentException> { JsonRuntimeBootstrap(file, secret, guard).initializeOrOpen() }
        assertTrue(before.contentEquals(Files.readAllBytes(file)))
        assertFalse(Files.exists(secret))
    }
}
