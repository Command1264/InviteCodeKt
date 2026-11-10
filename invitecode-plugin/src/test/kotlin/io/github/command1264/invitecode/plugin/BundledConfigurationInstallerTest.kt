package io.github.command1264.invitecode.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class BundledConfigurationInstallerTest {
    @TempDir
    lateinit var dataDirectory: Path

    @Test
    fun `installs nested jar resources into the flat plugin data layout without overwriting settings`() {
        Files.write(dataDirectory.resolve("config.yml"), "existing".toByteArray(StandardCharsets.UTF_8))
        Files.write(dataDirectory.resolve("storage.yml"), "corrupt: [".toByteArray(StandardCharsets.UTF_8))
        val requested = mutableListOf<String>()
        val installer = BundledConfigurationInstaller(dataDirectory) { resourcePath ->
            requested += resourcePath
            ByteArrayInputStream(resourcePath.toByteArray(StandardCharsets.UTF_8))
        }

        installer.installMissing()

        assertEquals("existing", String(Files.readAllBytes(dataDirectory.resolve("config.yml")), StandardCharsets.UTF_8))
        assertEquals("corrupt: [", String(Files.readAllBytes(dataDirectory.resolve("storage.yml")), StandardCharsets.UTF_8))
        assertEquals(
            "config/messages/zh_TW.yml",
            String(Files.readAllBytes(dataDirectory.resolve("messages/zh_TW.yml")), StandardCharsets.UTF_8),
        )
        assertFalse(Files.exists(dataDirectory.resolve("config/config.yml")))
        assertTrue("config/config.yml" !in requested)
        assertFalse("config/storage.yml" in requested)
        assertTrue("config/qualifications.yml" in requested)
        assertTrue("config/messages/en_US.yml" in requested)
    }

    @Test
    fun `missing bundled resource fails clearly without creating its target`() {
        val error = assertThrows<IllegalArgumentException> {
            BundledConfigurationInstaller(dataDirectory) { null }.installMissing()
        }

        assertTrue(error.message.orEmpty().contains("Bundled resource does not exist: config/config.yml"))
        assertFalse(Files.exists(dataDirectory.resolve("config.yml")))
    }

    @Test
    fun `interrupted resource write leaves no partial target or temporary file`() {
        val source = object : InputStream() {
            private var firstRead = true

            override fun read(): Int {
                if (firstRead) {
                    firstRead = false
                    return 'x'.code
                }
                throw IOException("simulated resource read failure")
            }
        }

        val error = assertThrows<Exception> {
            BundledConfigurationInstaller(dataDirectory) { source }.installMissing()
        }

        assertTrue(
            error.message.orEmpty().contains("simulated resource read failure") ||
                error.cause?.message.orEmpty().contains("simulated resource read failure") == true,
        )
        assertFalse(Files.exists(dataDirectory.resolve("config.yml")))
        Files.list(dataDirectory).use { children -> assertEquals(0L, children.count()) }
    }
}
