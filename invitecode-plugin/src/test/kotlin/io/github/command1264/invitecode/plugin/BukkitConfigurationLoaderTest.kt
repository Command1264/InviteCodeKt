package io.github.command1264.invitecode.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class BukkitConfigurationLoaderTest {
    @TempDir
    lateinit var dataDirectory: Path

    @Test
    fun `load restores missing bundled configuration and message catalogs from jar resources`() {
        val configuration = BukkitConfigurationLoader(dataDirectory) {}.load()

        assertEquals(1, configuration.general.version)
        assertEquals("是", configuration.messages.resolve("zh_TW", "value.yes"))
        assertEquals("yes", configuration.messages.resolve("en_US", "value.yes"))
        assertEquals(true, Files.isRegularFile(dataDirectory.resolve("config.yml")))
        assertEquals(true, Files.isRegularFile(dataDirectory.resolve("storage.yml")))
        assertEquals(true, Files.isRegularFile(dataDirectory.resolve("qualifications.yml")))
        assertEquals(true, Files.isRegularFile(dataDirectory.resolve("rewards.yml")))
        assertEquals(true, Files.isRegularFile(dataDirectory.resolve("messages/zh_TW.yml")))
        assertEquals(true, Files.isRegularFile(dataDirectory.resolve("messages/en_US.yml")))
    }

    @Test
    fun `load fills missing message keys from bundled catalogs without replacing existing files`() {
        BundledConfigurationInstaller(dataDirectory) { resourcePath ->
            javaClass.classLoader.getResourceAsStream(resourcePath)
        }.installMissing()
        val config = dataDirectory.resolve("config.yml")
        val configBefore = String(Files.readAllBytes(config), StandardCharsets.UTF_8)
        val catalog = dataDirectory.resolve("messages/zh_TW.yml")
        Files.write(
            catalog,
            "messages-version: 1\nvalue:\n  'yes': '自訂'\n".toByteArray(StandardCharsets.UTF_8),
        )

        val configuration = BukkitConfigurationLoader(dataDirectory) {}.load()

        assertEquals("自訂", configuration.messages.resolve("zh_TW", "value.yes"))
        assertEquals("否", configuration.messages.resolve("zh_TW", "value.no"))
        assertEquals(configBefore, String(Files.readAllBytes(config), StandardCharsets.UTF_8))
        assertEquals(
            "messages-version: 1\nvalue:\n  'yes': '自訂'\n",
            String(Files.readAllBytes(catalog), StandardCharsets.UTF_8),
        )
    }

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
    fun `loads quoted literal MySQL password from storage yaml without echoing it`() {
        BundledConfigurationInstaller(dataDirectory) { resourcePath ->
            javaClass.classLoader.getResourceAsStream(resourcePath)
        }.installMissing()
        val path = dataDirectory.resolve("storage.yml")
        val sample = "sample-only:p@ss#word"
        val updated = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
            .replace("${'$'}{INVITECODE_DB_PASSWORD}", sample)
        Files.write(path, updated.toByteArray(StandardCharsets.UTF_8))

        val configuration = BukkitConfigurationLoader(dataDirectory) {}.load()

        assertEquals(sample, configuration.storage.mysql.passwordSetting)
        assertEquals(false, configuration.toString().contains(sample))
    }

    @Test
    fun `invalid storage yaml does not expose a literal password in the error`() {
        BundledConfigurationInstaller(dataDirectory) { resourcePath ->
            javaClass.classLoader.getResourceAsStream(resourcePath)
        }.installMissing()
        val sample = "sample-only-password"
        Files.write(
            dataDirectory.resolve("storage.yml"),
            "mysql:\n  password: \"$sample".toByteArray(StandardCharsets.UTF_8),
        )

        val error = assertThrows<IllegalArgumentException> {
            BukkitConfigurationLoader(dataDirectory) {}.load()
        }
        assertEquals(false, error.toString().contains(sample))
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
