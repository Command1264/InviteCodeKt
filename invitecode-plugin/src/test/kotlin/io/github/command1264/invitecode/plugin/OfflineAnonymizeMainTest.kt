package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@ResourceLock(Resources.SYSTEM_OUT)
class OfflineAnonymizeMainTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `preview leaves source unchanged and does not create backup`() {
        val fixture = createFixture()
        val before = sha256(fixture.database)
        val backup = directory.resolve("preview-backup.db")

        val result = capture(commandArgs(fixture))

        assertNull(result.failure)
        assertTrue(result.stdout.contains("影響範圍："))
        assertTrue(result.stderr.isEmpty())
        assertEquals(before, sha256(fixture.database))
        assertFalse(Files.exists(backup))
        assertPrivateOutput(result, fixture)
    }

    @Test
    fun `missing player stopped confirmation backup and explicit UUID are rejected without writes`() {
        val fixture = createFixture()
        val before = sha256(fixture.database)
        val backup = directory.resolve("must-not-be-created.db")
        val rejected = listOf(
            commandArgs(fixture, omit = setOf("player")) to "缺少 --player",
            commandArgs(fixture, flags = setOf("apply")) to "停止所有 InviteCode 實例",
            commandArgs(fixture, flags = setOf("apply", "stopped")) to "必須指定 --backup",
            commandArgs(fixture, overrides = mapOf("player" to "Alice")) to "UUID",
        )

        rejected.forEach { (command, reason) ->
            val result = capture(command)
            assertRejection(result, reason, fixture.database, before, backup)
            assertPrivateOutput(result, fixture)
        }
    }

    @Test
    fun `nonexistent target is rejected without changing source or creating backup`() {
        val fixture = createFixture()
        val before = sha256(fixture.database)
        val backup = directory.resolve("nonexistent-target-backup.db")

        val absentPlayerId = UUID.randomUUID()
        val result = capture(commandArgs(
            fixture,
            overrides = mapOf("player" to absentPlayerId.toString(), "backup" to backup.toString()),
            flags = setOf("apply", "stopped"),
        ))

        assertRejection(result, "is absent", fixture.database, before, backup)
        assertPrivateOutput(result, fixture, allowedCallerUuids = setOf(absentPlayerId.toString()))
    }

    @Test
    fun `invalid secret is rejected without changing source or creating backup`() {
        val fixture = createFixture()
        val before = sha256(fixture.database)
        val invalidSecret = directory.resolve("invalid-secret.key")
        val invalidSecretText = "not-a-valid-anti-abuse-secret"
        Files.write(invalidSecret, invalidSecretText.toByteArray(Charsets.UTF_8))
        val backup = directory.resolve("invalid-secret-backup.db")

        val result = capture(commandArgs(
            fixture,
            overrides = mapOf("secret" to invalidSecret.toString(), "backup" to backup.toString()),
            flags = setOf("apply", "stopped"),
        ))

        assertRejection(result, "must contain exactly 32 bytes", fixture.database, before, backup)
        assertPrivateOutput(result, fixture, extraSecrets = listOf(invalidSecretText))
    }

    @Test
    fun `invalid source file is rejected without changing its bytes`() {
        val fixture = createFixture()
        val invalidSource = directory.resolve("not-a-database.db")
        Files.write(invalidSource, "not a SQLite database".toByteArray(Charsets.UTF_8))
        val before = sha256(invalidSource)
        val backup = directory.resolve("invalid-source-backup.db")

        val result = capture(commandArgs(
            fixture,
            overrides = mapOf("source" to invalidSource.toString(), "backup" to backup.toString()),
            flags = setOf("apply", "stopped"),
        ))

        assertRejection(result, "is not initialized", invalidSource, before, backup)
        assertPrivateOutput(result, fixture)
    }

    @Test
    fun `existing backup is preserved and source remains unchanged`() {
        val fixture = createFixture()
        val before = sha256(fixture.database)
        val backup = directory.resolve("existing-backup.db")
        val originalBackup = "preserve this existing backup".toByteArray(Charsets.UTF_8)
        Files.write(backup, originalBackup)

        val result = capture(commandArgs(
            fixture,
            overrides = mapOf("backup" to backup.toString()),
            flags = setOf("apply", "stopped"),
        ))

        assertRejection(result, "備份檔已存在", fixture.database, before)
        assertEquals(before, sha256(fixture.database))
        assertTrue(Files.readAllBytes(backup).contentEquals(originalBackup))
        assertPrivateOutput(result, fixture)
    }

    @Test
    fun `valid length but different reservation secret is rejected without modifying source`() {
        val fixture = createFixture()
        val priorPlayer = UUID.randomUUID()
        NetworkAddressHasher.loadExisting(fixture.secret).use { hasher ->
            val key = io.github.command1264.invitecode.storage.sql.CodeReservationKey(hasher.keyId, hasher::hashReservedCode)
            SqliteInviteCodeStorage.open(fixture.database, fixture.guard, key).use { storage ->
                storage.savePlayer(StoredPlayer(priorPlayer, Instant.parse("2026-01-01T00:00:00Z"), "PriorPlayer"))
                storage.atomically { it.activate(io.github.command1264.invitecode.core.ActiveInviteCode(
                    priorPlayer, io.github.command1264.invitecode.core.InviteCode.fromNormalized("D4E5F6"),
                    Instant.parse("2026-01-01T00:00:00Z"),
                )) }
                storage.anonymizePlayer(priorPlayer)
            }
        }
        val wrongSecret = directory.resolve("different-secret.key")
        Files.write(wrongSecret, ByteArray(32) { 7 })
        val backup = directory.resolve("different-key-backup.db")
        val before = sha256(fixture.database)
        val result = capture(commandArgs(fixture,
            overrides = mapOf("secret" to wrongSecret.toString(), "backup" to backup.toString()),
            flags = setOf("apply", "stopped"),
        ))
        assertRejection(result, "use another anti-abuse key", fixture.database, before, backup)
        assertPrivateOutput(result, fixture, extraSecrets = listOf("07".repeat(32)))
    }

    @Test
    fun `offline command copies and verifies backup before anonymizing the requested player`() {
        val fixture = createFixture()
        val backup = directory.resolve("players.before-anonymization.db")
        val otherPlayer = UUID.randomUUID()
        SqliteInviteCodeStorage.open(fixture.database, fixture.guard).use {
            it.savePlayer(StoredPlayer(otherPlayer, Instant.parse("2026-01-01T00:00:00Z"), "OtherPlayer"))
        }

        val result = capture(commandArgs(
            fixture,
            overrides = mapOf("backup" to backup.toString()),
            flags = setOf("apply", "stopped"),
        ))

        assertNull(result.failure)
        assertTrue(Files.isRegularFile(backup))
        SqliteInviteCodeStorage.open(backup, fixture.guard).use { assertNotNull(it.player(fixture.playerId)) }
        SqliteInviteCodeStorage.open(fixture.database, fixture.guard).use {
            assertNull(it.player(fixture.playerId))
            assertEquals("OtherPlayer", it.player(otherPlayer)?.lastKnownName)
        }
        assertPrivateOutput(result, fixture)
    }

    private fun createFixture(): Fixture {
        val database = directory.resolve("players-${UUID.randomUUID()}.db")
        val secret = directory.resolve("anti-abuse-${UUID.randomUUID()}.key")
        val playerId = UUID.randomUUID()
        val guard = BlockingOperationGuard { }
        NetworkAddressHasher.loadOrCreate(secret).close()
        val secretText = Files.readAllBytes(secret).joinToString("") { "%02x".format(it) }
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use { storage ->
            storage.savePlayer(StoredPlayer(playerId, Instant.parse("2026-01-01T00:00:00Z"), STORED_NAME))
        }
        return Fixture(database, secret, secretText, playerId, guard)
    }

    private fun commandArgs(
        fixture: Fixture,
        overrides: Map<String, String> = emptyMap(),
        omit: Set<String> = emptySet(),
        flags: Set<String> = emptySet(),
    ): Array<String> {
        val values = linkedMapOf(
            "backend" to "sqlite",
            "source" to fixture.database.toString(),
            "secret" to fixture.secret.toString(),
            "player" to fixture.playerId.toString(),
        )
        values.putAll(overrides)
        omit.forEach { values.remove(it) }
        val valueArgs = values.flatMap { (name, value) -> listOf("--$name", value) }
        return (valueArgs + flags.map { "--$it" }).toTypedArray()
    }

    private fun capture(args: Array<String>): CapturedExecution {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val previousOut = System.out
        val previousErr = System.err
        var failure: Throwable? = null
        try {
            System.setOut(PrintStream(stdout, true, "UTF-8"))
            System.setErr(PrintStream(stderr, true, "UTF-8"))
            try {
                OfflineAnonymizeMain.execute(args)
            } catch (error: Exception) {
                failure = error
            }
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
        }
        return CapturedExecution(
            String(stdout.toByteArray(), Charsets.UTF_8),
            String(stderr.toByteArray(), Charsets.UTF_8),
            failure,
        )
    }

    private fun assertRejection(
        result: CapturedExecution,
        reason: String,
        source: Path,
        sourceHash: String,
        backupThatMustNotExist: Path? = null,
    ) {
        val message = result.failure?.message
        assertNotNull(message, "Expected command rejection")
        assertTrue(message!!.contains(reason, ignoreCase = true), "Expected rejection reason '$reason', got '$message'")
        assertEquals(sourceHash, sha256(source))
        backupThatMustNotExist?.let { assertFalse(Files.exists(it)) }
    }

    private fun assertPrivateOutput(
        result: CapturedExecution,
        fixture: Fixture,
        allowedCallerUuids: Set<String> = emptySet(),
        extraSecrets: List<String> = emptyList(),
    ) {
        val output = result.stdout + result.stderr + result.failure?.message.orEmpty()
        assertFalse(output.contains(fixture.secretText), "CLI output must not contain the secret contents")
        extraSecrets.forEach { secret ->
            assertFalse(output.contains(secret), "CLI output must not contain supplied secret material")
        }
        assertFalse(output.contains(STORED_NAME), "CLI output must not contain the stored player name")
        val printedUuids = UUID_PATTERN.findAll(output).map { it.value }.toSet()
        assertTrue(
            printedUuids.all { it in allowedCallerUuids },
            "CLI output must not contain generated UUIDs or an ID mapping: $printedUuids",
        )
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }

    private data class Fixture(
        val database: Path,
        val secret: Path,
        val secretText: String,
        val playerId: UUID,
        val guard: BlockingOperationGuard,
    )

    private data class CapturedExecution(val stdout: String, val stderr: String, val failure: Throwable?)

    private companion object {
        const val STORED_NAME = "StoredAliceForPrivacyCheck"
        val UUID_PATTERN = Regex("(?i)\\b[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\\b")
    }
}
