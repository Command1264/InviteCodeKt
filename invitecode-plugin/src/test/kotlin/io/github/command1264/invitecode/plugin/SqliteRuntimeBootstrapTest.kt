package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SqliteRuntimeBootstrapTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `fresh installation creates SQLite schema and network secret automatically`() {
        val database = temporaryDirectory.resolve("invitecode.db")
        val secret = temporaryDirectory.resolve("anti-abuse.key")

        val result = SqliteRuntimeBootstrap(database, secret, BlockingOperationGuard { }).initializeOrOpen()

        result.storage.use {
            result.networkHasher.use {
                assertTrue(result.initialized)
                assertTrue(Files.isRegularFile(database))
                assertEquals(32, Files.size(secret))
            }
        }
    }

    @Test
    fun `existing database with a missing secret requires explicit recovery`() {
        val database = temporaryDirectory.resolve("invitecode.db")
        val secret = temporaryDirectory.resolve("anti-abuse.key")
        SqliteSchemaMigrator(database, BlockingOperationGuard { }).migrate()

        assertThrows<IllegalArgumentException> {
            SqliteRuntimeBootstrap(database, secret, BlockingOperationGuard { }).initializeOrOpen()
        }

        assertFalse(Files.exists(secret))
    }

    @Test
    fun `existing older schema is never upgraded during ordinary startup`() {
        val database = temporaryDirectory.resolve("invitecode.db")
        val secret = temporaryDirectory.resolve("anti-abuse.key")
        SqliteSchemaMigrator(
            database,
            BlockingOperationGuard { },
            SqliteSchemaMigrator.DEFAULT_MIGRATIONS.take(7),
        ).migrate()
        NetworkAddressHasher.loadOrCreate(secret).close()

        val error = assertThrows<IllegalStateException> {
            SqliteRuntimeBootstrap(database, secret, BlockingOperationGuard { }).initializeOrOpen()
        }

        assertTrue(error.message!!.contains("run the explicit schema migration"))
    }
}
