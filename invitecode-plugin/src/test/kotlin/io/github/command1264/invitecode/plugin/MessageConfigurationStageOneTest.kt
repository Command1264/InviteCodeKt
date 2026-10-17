package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.AtomicRuntimeSnapshot
import io.github.command1264.invitecode.platform.bukkit.MessagePresenter
import io.github.command1264.invitecode.platform.bukkit.PrefixPlacement
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

class MessageConfigurationStageOneTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `validated reload can disable prefix while malformed replacement keeps installed snapshot`() {
        val loader = BukkitConfigurationLoader(directory) {}
        val snapshots = AtomicRuntimeSnapshot(loader.load())
        val custom = "messages-version: 1\npresentation:\n  prefix: ''\ncustom: '&aHello {player}'\n"
        val catalog = directory.resolve("messages/zh_TW.yml")
        Files.write(catalog, custom.toByteArray(UTF_8))
        snapshots.replace(loader.load())
        val presenter = MessagePresenter(snapshots.current().value.messages)
        assertEquals("Hello &c{other}", presenter.render(
            null, "custom", mapOf("player" to "&c{other}"), prefix = PrefixPlacement.OWN_LINE,
        ).plainText)
        assertArrayEquals(custom.toByteArray(UTF_8), Files.readAllBytes(catalog))

        val damaged = "presentation: [\n"
        Files.write(catalog, damaged.toByteArray(UTF_8))
        assertThrows<Exception> { snapshots.replace(loader.load()) }
        assertEquals(1L, snapshots.current().generation)
        assertEquals("Hello Alex", MessagePresenter(snapshots.current().value.messages).render(
            null, "custom", mapOf("player" to "Alex"),
        ).plainText)
        assertArrayEquals(damaged.toByteArray(UTF_8), Files.readAllBytes(catalog))
    }

    @Test
    fun `restoring deleted built in catalog keeps unrelated files secrets and data untouched`() {
        val loader = BukkitConfigurationLoader(directory) {}
        loader.load()
        val key = byteArrayOf(1, 2, 3, 4)
        Files.write(directory.resolve("anti-abuse.key"), key)
        Files.write(directory.resolve("invitecode.db"), byteArrayOf(7, 8, 9))
        Files.write(directory.resolve("messages/custom-locale.yml"),
            "messages-version: 1\ncustom: keep\n".toByteArray(UTF_8))
        val storage = Files.readAllBytes(directory.resolve("storage.yml"))
        Files.delete(directory.resolve("messages/en_US.yml"))

        val restored = loader.load()

        assertEquals("yes", restored.messages.resolve("en_US", "value.yes"))
        assertArrayEquals(storage, Files.readAllBytes(directory.resolve("storage.yml")))
        assertArrayEquals(key, Files.readAllBytes(directory.resolve("anti-abuse.key")))
        assertArrayEquals(byteArrayOf(7, 8, 9), Files.readAllBytes(directory.resolve("invitecode.db")))
        assertEquals("keep", restored.messages.resolve("custom-locale", "custom"))
        assertFalse(Files.exists(directory.resolve("config/config.yml")))
    }
}
