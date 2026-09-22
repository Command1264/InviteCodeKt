package io.github.command1264.invitecode.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class BundledConfigurationInstallerTest {
    @TempDir
    lateinit var dataDirectory: Path

    @Test
    fun `installs nested jar resources into the flat plugin data layout without overwriting settings`() {
        Files.write(dataDirectory.resolve("config.yml"), "existing".toByteArray(StandardCharsets.UTF_8))
        val requested = mutableListOf<String>()
        val installer = BundledConfigurationInstaller(dataDirectory) { resourcePath ->
            requested += resourcePath
            ByteArrayInputStream(resourcePath.toByteArray(StandardCharsets.UTF_8))
        }

        installer.installMissing()

        assertEquals("existing", String(Files.readAllBytes(dataDirectory.resolve("config.yml")), StandardCharsets.UTF_8))
        assertEquals(
            "config/storage.yml",
            String(Files.readAllBytes(dataDirectory.resolve("storage.yml")), StandardCharsets.UTF_8),
        )
        assertEquals(
            "config/messages/zh_TW.yml",
            String(Files.readAllBytes(dataDirectory.resolve("messages/zh_TW.yml")), StandardCharsets.UTF_8),
        )
        assertFalse(Files.exists(dataDirectory.resolve("config/config.yml")))
        assertTrue("config/config.yml" !in requested)
        assertTrue("config/storage.yml" in requested)
        assertTrue("config/messages/en_US.yml" in requested)
    }
}
