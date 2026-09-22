package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.MysqlConfiguration
import io.github.command1264.invitecode.core.MysqlTlsMode
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.MysqlStorageException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.DriverManager
import java.time.Duration
import java.util.Base64
import java.util.UUID

class MysqlRuntimeBootstrapTest {
    @Test
    fun `first startup initializes and later startup uses the same database and shared key`() {
        val host = System.getenv("INVITECODE_TEST_MYSQL_HOST")
        val password = System.getenv("INVITECODE_TEST_MYSQL_PASSWORD")
        assumeTrue(!host.isNullOrBlank() && !password.isNullOrBlank())
        val port = System.getenv("INVITECODE_TEST_MYSQL_PORT")?.toIntOrNull() ?: 3306
        val user = System.getenv("INVITECODE_TEST_MYSQL_USER") ?: "root"
        val schema = "invitecode_test_${UUID.randomUUID().toString().replace("-", "")}"
        check(Regex("invitecode_test_[0-9a-f]{32}").matches(schema))
        val url = "jdbc:mysql://$host:$port/?sslMode=REQUIRED&connectionTimeZone=UTC"
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { it.execute("CREATE DATABASE $schema") }
        }
        try {
            val config = MysqlConfiguration(
                host, port, schema, user, "${'$'}{DB_PASSWORD}", "${'$'}{NETWORK_SECRET}", Duration.ofSeconds(5),
                MysqlTlsMode.REQUIRED,
            )
            val secret = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
            val environment: (String) -> String? = { name ->
                when (name) {
                    "DB_PASSWORD" -> password
                    "NETWORK_SECRET" -> secret
                    else -> null
                }
            }
            val guard = BlockingOperationGuard { }
            val first = MysqlRuntimeBootstrap(config, guard, environment).initializeOrOpen()
            try {
                assertEquals(true, first.initialized)
                val reopened = MysqlRuntimeBootstrap(config, guard, environment).initializeOrOpen()
                try {
                    assertEquals(false, reopened.initialized)
                    assertEquals(first.networkHasher.keyId, reopened.networkHasher.keyId)
                } finally {
                    reopened.storage.close()
                    reopened.networkHasher.close()
                }
                assertThrows(MysqlStorageException::class.java) {
                    MysqlRuntimeBootstrap(config, guard, environment = { name ->
                        if (name == "DB_PASSWORD") password else Base64.getEncoder()
                            .encodeToString(ByteArray(32) { 7 })
                    }).initializeOrOpen()
                }
            } finally {
                first.storage.close()
                first.networkHasher.close()
            }
        } finally {
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { it.execute("DROP DATABASE $schema") }
            }
        }
    }

    @Test
    fun `missing secret fails before connecting to MySQL`() {
        val config = MysqlConfiguration(
            "localhost", 3306, "invitecode_test", "test",
            "${'$'}{DB_PASSWORD}", "${'$'}{NETWORK_SECRET}", Duration.ofSeconds(1),
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            MysqlRuntimeBootstrap(config, BlockingOperationGuard { }, environment = { name ->
                if (name == "DB_PASSWORD") "dummy" else null
            }).initializeOrOpen()
        }
        assertEquals(true, error.message.orEmpty().contains("NETWORK_SECRET"))
    }
}
