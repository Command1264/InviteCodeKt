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
        assertEquals("${'$'}{INVITECODE_DB_PASSWORD}", configuration.mysql.passwordSetting)
        assertEquals(MysqlTlsMode.VERIFY_IDENTITY, configuration.mysql.tlsMode)
    }

    @Test
    fun `rejects unsafe SQLite paths`() {
        val sqlite = mapOf("file" to "../outside.db")
        val value = validConfiguration().toMutableMap().apply { put("sqlite", sqlite) }

        assertThrows<IllegalArgumentException> { StorageConfigurationParser.parse(value) }
    }

    @Test
    fun `supports MySQL and JSON with a safe default path`() {
        assertEquals(
            StorageBackendType.MYSQL,
            StorageConfigurationParser.parse(validConfiguration().toMutableMap().apply { put("backend", "MYSQL") })
                .backend,
        )
        val json = StorageConfigurationParser.parse(
            validConfiguration().toMutableMap().apply { put("backend", "JSON") },
        )
        assertEquals(StorageBackendType.JSON, json.backend)
        assertEquals("invitecode.json", json.json.file)
    }

    @Test
    fun `JSON file rejects escaping paths`() {
        val value = validConfiguration().toMutableMap().apply {
            put("json", mapOf("file" to "../outside.json"))
        }
        assertThrows<IllegalArgumentException> { StorageConfigurationParser.parse(value) }
    }

    @Test
    fun `MySQL config accepts literal password without exposing it in configuration text`() {
        val literal = "sample-only:p@ss#word"
        val value = validConfiguration().toMutableMap().apply {
            put("mysql", (get("mysql") as Map<String, Any?>) + ("password" to literal))
        }
        val configuration = StorageConfigurationParser.parse(value)
        assertEquals(literal, configuration.mysql.passwordSetting)
        assertEquals(false, configuration.toString().contains(literal))
    }

    @Test
    fun `MySQL config rejects blank or malformed password and URL injection`() {
        fun withMysql(changed: Map<String, Any?>): Map<String, Any?> = validConfiguration().toMutableMap().apply {
            put("mysql", (get("mysql") as Map<String, Any?>) + changed)
        }
        assertThrows<IllegalArgumentException> {
            StorageConfigurationParser.parse(withMysql(mapOf("password" to " ")))
        }
        assertThrows<IllegalArgumentException> {
            StorageConfigurationParser.parse(withMysql(mapOf("password" to "${'$'}{BROKEN")))
        }
        assertThrows<IllegalArgumentException> {
            StorageConfigurationParser.parse(withMysql(mapOf("host" to "localhost/?allowPublicKeyRetrieval=true")))
        }
        assertThrows<IllegalArgumentException> {
            StorageConfigurationParser.parse(withMysql(mapOf("tls-mode" to "DISABLED")))
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
            "network-secret" to "${'$'}{INVITECODE_NETWORK_SECRET}",
            "connection-timeout" to "5s",
            "tls-mode" to "VERIFY_IDENTITY",
        ),
    )
}
