package io.github.command1264.invitecode.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class BukkitConfigurationLoaderTest {
    @TempDir
    lateinit var dataDirectory: Path

    @Test
    fun `bundled message catalogs preserve yes and no keys`() {
        BundledConfigurationInstaller(dataDirectory) { resourcePath ->
            javaClass.classLoader.getResourceAsStream(resourcePath)
        }.installMissing()

        val configuration = BukkitConfigurationLoader(dataDirectory) {}.load()

        assertEquals("是", configuration.messages.resolve("zh_TW", "value.yes"))
        assertEquals("否", configuration.messages.resolve("zh_TW", "value.no"))
        assertEquals("yes", configuration.messages.resolve("en_US", "value.yes"))
        assertEquals("no", configuration.messages.resolve("en_US", "value.no"))
    }

    @Test
    fun `catalogs generated with unquoted yaml boolean keys remain compatible`() {
        BundledConfigurationInstaller(dataDirectory) { resourcePath ->
            javaClass.classLoader.getResourceAsStream(resourcePath)
        }.installMissing()
        listOf("zh_TW", "en_US").forEach { locale ->
            val path = dataDirectory.resolve("messages/$locale.yml")
            val legacy = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("  'yes':", "  yes:")
                .replace("  'no':", "  no:")
            Files.write(path, legacy.toByteArray(StandardCharsets.UTF_8))
        }

        val configuration = BukkitConfigurationLoader(dataDirectory) {}.load()

        assertEquals("是", configuration.messages.resolve("zh_TW", "value.yes"))
        assertEquals("否", configuration.messages.resolve("zh_TW", "value.no"))
        assertEquals("yes", configuration.messages.resolve("en_US", "value.yes"))
        assertEquals("no", configuration.messages.resolve("en_US", "value.no"))
    }
}
