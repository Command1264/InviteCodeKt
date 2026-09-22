package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class StorageConfigurationTest {
    @Test
    fun `parses SQLite while retaining restart-only MySQL settings`() {
        val configuration = StorageConfigurationParser.parse(validConfiguration())

        assertEquals(StorageBackendType.SQLITE, configuration.backend)
        assertEquals("invitecode.db", configuration.sqlite.file)
        assertEquals(3306, configuration.mysql.port)
        assertEquals(Duration.ofSeconds(5), configuration.mysql.connectionTimeout)
        assertEquals("${'$'}{INVITECODE_DB_PASSWORD}", configuration.mysql.passwordReference)
    }

    @Test
    fun `rejects unsafe SQLite paths`() {
        val sqlite = mapOf("file" to "../outside.db")
        val value = validConfiguration().toMutableMap().apply { put("sqlite", sqlite) }

        assertThrows<IllegalArgumentException> { StorageConfigurationParser.parse(value) }
    }

    @Test
    fun `marks unfinished backends unsupported`() {
        assertThrows<UnsupportedStorageBackendException> {
            StorageConfigurationParser.parse(validConfiguration().toMutableMap().apply { put("backend", "MYSQL") })
                .requireSupportedBackend()
        }
    }

    private fun validConfiguration(): Map<String, Any?> = mapOf(
        "config-version" to 1,
        "backend" to "SQLITE",
        "sqlite" to mapOf("file" to "invitecode.db"),
        "mysql" to mapOf(
            "host" to "localhost",
            "port" to 3306,
            "database" to "invitecode",
            "username" to "invitecode",
            "password" to "${'$'}{INVITECODE_DB_PASSWORD}",
            "connection-timeout" to "5s",
        ),
    )
}
