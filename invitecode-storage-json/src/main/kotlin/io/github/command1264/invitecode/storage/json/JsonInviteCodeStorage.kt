package io.github.command1264.invitecode.storage.json

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import io.github.command1264.invitecode.core.*
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.CodeReservationKey
import io.github.command1264.invitecode.storage.sql.AnonymizationPreview
import io.github.command1264.invitecode.storage.sql.AnonymizationResult
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import io.github.command1264.invitecode.storage.sql.SqliteSnapshotAccess
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.sql.Connection
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A readable JSON snapshot backed by the already validated SQLite transaction engine. The SQLite
 * working file is temporary; only the JSON snapshot is durable. A failed snapshot stops all writes.
 */
class JsonInviteCodeStorage private constructor(
    private val file: Path,
    private val workingDirectory: Path,
    private val database: Path,
    private val guard: BlockingOperationGuard,
    private val lockChannel: FileChannel,
    private val fileLock: FileLock,
    private val delegate: SqliteInviteCodeStorage,
) : InviteCodeStorage by delegate {
    private var closed = false
    private var failed = false

    private fun <T> persist(operation: () -> T): T = synchronized(this) {
        check(!closed && !failed) { "JSON storage is closed or a previous write failed\nrestart after inspecting ${file.toAbsolutePath()}." }
        guard.checkAllowed()
        val result = operation()
        try {
            val snapshot = exportSnapshot(database, guard)
            writeSnapshot(file, snapshot)
        } catch (error: Exception) {
            failed = true
            throw JsonStorageException(
                "JSON snapshot write failed for ${file.toAbsolutePath()}\nwrites are stopped. " +
                    "Preserve the JSON file and its .previous copy, then restart after fixing the cause.", error,
            )
        }
        result
    }

    override fun savePlayer(player: StoredPlayer) = persist { delegate.savePlayer(player) }
    override fun recordLoginDay(playerId: UUID, loginDate: LocalDate) =
        persist { delegate.recordLoginDay(playerId, loginDate) }
    override fun recordNetworkHash(record: PlayerNetworkHash) = persist { delegate.recordNetworkHash(record) }
    override fun purgeExpiredNetworkHashes(expiredAt: Instant): Int =
        persist { delegate.purgeExpiredNetworkHashes(expiredAt) }
    override fun purgeNetworkHashesWithDifferentKey(keyId: String): Int =
        persist { delegate.purgeNetworkHashesWithDifferentKey(keyId) }
    override fun saveInvitation(invitation: Invitation) = persist { delegate.saveInvitation(invitation) }
    override fun saveQualifiedInvitation(invitation: Invitation, facts: List<QualificationFact>) =
        persist { delegate.saveQualifiedInvitation(invitation, facts) }
    override fun saveQualifiedInvitationWithRewards(
        invitation: Invitation, facts: List<QualificationFact>, rewardPlans: List<RewardDeliveryPlan>,
    ) = persist { delegate.saveQualifiedInvitationWithRewards(invitation, facts, rewardPlans) }
    override fun saveQualificationFact(fact: QualificationFact) = persist { delegate.saveQualificationFact(fact) }
    override fun saveQualificationFacts(facts: List<QualificationFact>) =
        persist { delegate.saveQualificationFacts(facts) }
    override fun saveRewardAction(action: StoredRewardAction) = persist { delegate.saveRewardAction(action) }
    override fun recoverInterruptedRewardActions(recoveredAt: Instant): Int =
        persist { delegate.recoverInterruptedRewardActions(recoveredAt) }
    override fun resolveUncertainRewardAction(
        actionId: UUID, resolution: UncertainRewardActionResolution, context: RewardAdministrationContext,
    ): StoredRewardAction = persist { delegate.resolveUncertainRewardAction(actionId, resolution, context) }
    override fun saveRewardDelivery(delivery: StoredRewardDelivery, actions: List<StoredRewardAction>) =
        persist { delegate.saveRewardDelivery(delivery, actions) }
    override fun saveAuditRecord(record: AuditRecord) = persist { delegate.saveAuditRecord(record) }
    override fun saveAuditedInvitationTransition(transition: AuditedInvitationTransition) =
        persist { delegate.saveAuditedInvitationTransition(transition) }
    override fun saveForcedQualificationWithRewards(
        transition: AuditedInvitationTransition, rewardPlans: List<RewardDeliveryPlan>,
    ) = persist { delegate.saveForcedQualificationWithRewards(transition, rewardPlans) }
    override fun saveAuditedClaimStateTransition(transition: AuditedClaimStateTransition) =
        persist { delegate.saveAuditedClaimStateTransition(transition) }
    override fun <T> atomically(operation: (InviteCodeTransaction) -> T): T =
        persist { delegate.atomically(operation) }
    override fun <T> atomically(playerIds: Set<UUID>, operation: (InvitationTransaction) -> T): T =
        persist { delegate.atomically(playerIds, operation) }

    fun previewAnonymization(playerId: UUID): AnonymizationPreview = delegate.previewAnonymization(playerId)
    fun anonymizePlayer(playerId: UUID): AnonymizationResult = persist { delegate.anonymizePlayer(playerId) }

    /** The file lock remains held while an offline migration reads the loaded snapshot. */
    fun <T> inspectOffline(operation: (Connection) -> T): T = synchronized(this) {
        check(!closed && !failed) { "JSON storage is closed or failed." }
        SqliteSnapshotAccess.withConnection(database, guard) { connection ->
            connection.createStatement().use { it.execute("PRAGMA query_only=ON") }
            operation(connection)
        }
    }

    override fun close() = synchronized(this) {
        if (closed) return@synchronized
        closed = true
        delegate.close()
        fileLock.release()
        lockChannel.close()
        Files.deleteIfExists(database)
        Files.deleteIfExists(workingDirectory)
    }

    companion object {
        const val FORMAT_VERSION = 1

        /** Publish a new JSON destination only after the imported SQLite image passes verification. */
        fun createOfflineFromSqlite(
            file: Path, guard: BlockingOperationGuard, importAndVerify: (Connection) -> Unit,
        ) {
            guard.checkAllowed()
            val target = file.toAbsolutePath().normalize()
            require(Files.isDirectory(requireNotNull(target.parent))) { "JSON destination directory must exist." }
            require(!Files.exists(target)) { "JSON destination already exists\ndo not overwrite it." }
            val lockChannel = FileChannel.open(target.resolveSibling("${target.fileName}.lock"), CREATE, WRITE)
            val lock = try {
                lockChannel.tryLock() ?: throw JsonStorageException("JSON destination is already open\nstop every instance.")
            } catch (error: Exception) {
                lockChannel.close()
                throw error
            }
            val workingDirectory = try { Files.createTempDirectory("invitecode-json-transfer-") } catch (error: Exception) {
                lock.release()
                lockChannel.close()
                throw error
            }
            val database = workingDirectory.resolve("working.db")
            var staged: Path? = null
            try {
                val stagedFile = Files.createTempFile(target.parent, ".${target.fileName}.", ".staging")
                staged = stagedFile
                SqliteSchemaMigrator(database, guard).migrate()
                SqliteSnapshotAccess.withConnection(database, guard, importAndVerify)
                writeSnapshot(stagedFile, exportSnapshot(database, guard))
                require(!Files.exists(target)) { "JSON destination appeared while transfer was in progress." }
                // The hard link publishes complete bytes and cannot replace a concurrently created target.
                Files.createLink(target, stagedFile)
            } finally {
                try {
                    staged?.let(Files::deleteIfExists)
                    Files.deleteIfExists(database)
                    Files.deleteIfExists(workingDirectory)
                } finally {
                    lock.release()
                    lockChannel.close()
                }
            }
        }

        /** Offline-only, explicit snapshot migration; the .previous file keeps the original snapshot. */
        fun migrateV8ToV9(file: Path, guard: BlockingOperationGuard) {
            guard.checkAllowed()
            val target = file.toAbsolutePath().normalize()
            require(Files.isRegularFile(target)) { "JSON storage must exist before migration." }
            val lockChannel = FileChannel.open(target.resolveSibling("${target.fileName}.lock"), CREATE, WRITE)
            val lock = try {
                lockChannel.tryLock() ?: throw JsonStorageException("JSON storage is already open\nstop every instance.")
            } catch (error: Exception) {
                lockChannel.close()
                throw error
            }
            val workingDirectory = try { Files.createTempDirectory("invitecode-json-migrate-") } catch (error: Exception) {
                lock.release()
                lockChannel.close()
                throw error
            }
            val database = workingDirectory.resolve("working.db")
            try {
                SqliteSchemaMigrator(database, guard, SqliteSchemaMigrator.DEFAULT_MIGRATIONS.take(8)).migrate()
                importSnapshot(target, database, guard, expectedSchemaVersion = 8)
                SqliteSchemaMigrator(database, guard).migrate()
                writeSnapshot(target, exportSnapshot(database, guard))
            } finally {
                try {
                    Files.deleteIfExists(database)
                    Files.deleteIfExists(workingDirectory)
                } finally {
                    lock.release()
                    lockChannel.close()
                }
            }
        }

        fun open(file: Path, guard: BlockingOperationGuard, reservationKey: CodeReservationKey? = null): JsonInviteCodeStorage =
            openAfterLock(file, guard) { reservationKey }

        /** Loads the shared key only after the JSON writer lock has been acquired. */
        fun openWithKeyProvider(
            file: Path, guard: BlockingOperationGuard, provider: () -> CodeReservationKey,
        ): JsonInviteCodeStorage = openAfterLock(file, guard, provider)

        private fun openAfterLock(
            file: Path, guard: BlockingOperationGuard, provider: () -> CodeReservationKey?,
        ): JsonInviteCodeStorage {
            guard.checkAllowed()
            val target = file.toAbsolutePath().normalize()
            Files.createDirectories(requireNotNull(target.parent))
            // The OS releases this advisory lock even after a crash; the sidecar may remain safely.
            val lockChannel = FileChannel.open(target.resolveSibling("${target.fileName}.lock"), CREATE, WRITE)
            val fileLock = try {
                lockChannel.tryLock()
                    ?: throw JsonStorageException("JSON storage ${target} is already open by another process.")
            } catch (error: Exception) {
                lockChannel.close()
                throw JsonStorageException("Cannot lock JSON storage ${target}\nanother instance may be using it.", error)
            }
            val workingDirectory = try {
                Files.createTempDirectory("invitecode-json-")
            } catch (error: Exception) {
                fileLock.release()
                lockChannel.close()
                throw error
            }
            val database = workingDirectory.resolve("working.db")
            try {
                SqliteSchemaMigrator(database, guard).migrate()
                if (Files.exists(target)) importSnapshot(target, database, guard)
                val delegate = SqliteInviteCodeStorage.open(database, guard, provider())
                try {
                    if (!Files.exists(target)) writeSnapshot(target, exportSnapshot(database, guard))
                    return JsonInviteCodeStorage(
                        target, workingDirectory, database, guard, lockChannel, fileLock, delegate,
                    )
                } catch (error: Exception) {
                    delegate.close()
                    throw error
                }
            } catch (error: Exception) {
                fileLock.release()
                lockChannel.close()
                Files.deleteIfExists(database)
                Files.deleteIfExists(workingDirectory)
                throw error
            }
        }

        private fun schema(connection: Connection): Map<String, List<Pair<String, Int>>> {
            val tables = sortedMapOf<String, List<Pair<String, Int>>>()
            connection.createStatement().use { query ->
                query.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'invitecode_%' ORDER BY name",
                ).use { result ->
                    while (result.next()) {
                        val table = result.getString(1)
                        require(Regex("invitecode_[a-z_]+").matches(table)) { "Invalid storage table name." }
                        val columns = mutableListOf<Pair<String, Int>>()
                        connection.createStatement().use { statement ->
                            statement.executeQuery("PRAGMA table_info($table)").use { metadata ->
                                while (metadata.next()) {
                                    val name = metadata.getString("name")
                                    val type = metadata.getString("type").uppercase()
                                    require(Regex("[a-z_]+").matches(name)) { "Invalid storage column name." }
                                    columns += name to if (type.contains("INT")) Types.BIGINT else Types.VARCHAR
                                }
                            }
                        }
                        tables[table] = columns
                    }
                }
            }
            return tables
        }

        private fun exportSnapshot(database: Path, guard: BlockingOperationGuard): JsonObject =
            SqliteSnapshotAccess.withConnection(database, guard) { connection ->
            val root = JsonObject()
            root.addProperty("format-version", FORMAT_VERSION)
            root.addProperty("schema-version", SqliteSchemaMigrator.currentVersion)
            val tables = JsonObject()
            schema(connection).forEach { (table, columns) ->
                val rows = JsonArray()
                connection.createStatement().use { query ->
                    query.executeQuery("SELECT * FROM $table ORDER BY rowid").use { result ->
                        while (result.next()) {
                            val row = JsonObject()
                            columns.forEachIndexed { index, (column, type) ->
                                val value = result.getObject(index + 1)
                                when {
                                    value == null -> row.add(column, null)
                                    type == Types.BIGINT -> row.addProperty(column, (value as Number).toLong())
                                    else -> row.addProperty(column, value.toString())
                                }
                            }
                            rows.add(row)
                        }
                    }
                }
                tables.add(table, rows)
            }
            root.add("tables", tables)
            root
        }

        private fun importSnapshot(
            file: Path, database: Path, guard: BlockingOperationGuard,
            expectedSchemaVersion: Int = SqliteSchemaMigrator.currentVersion,
        ) {
            val root = try {
                Files.newBufferedReader(file, StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
            } catch (error: Exception) {
                throw JsonStorageException(
                    "JSON storage ${file.toAbsolutePath()} is damaged. Preserve it and inspect ${file.fileName}.previous\n" +
                        "no new database was created over it.", error,
                )
            }
            try {
                val format = root.get("format-version")?.asString?.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
                require(format == FORMAT_VERSION) {
                    "JSON format version $format is unsupported\nexpected $FORMAT_VERSION. Preserve the file and upgrade InviteCode."
                }
                val schemaVersion = root.get("schema-version")?.asString?.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
                require(schemaVersion == expectedSchemaVersion) {
                    "JSON schema version $schemaVersion is unsupported\nexpected $expectedSchemaVersion. " +
                        "Preserve the file and run an explicit migration with a compatible InviteCode version."
                }
                require(root.keySet() == setOf("format-version", "schema-version", "tables")) {
                    "JSON storage has unknown or missing root fields."
                }
                SqliteSnapshotAccess.withConnection(database, guard) { connection ->
                    val expected = schema(connection)
                    val tables = root.getAsJsonObject("tables")
                    require(tables.keySet() == expected.keys) { "JSON storage table set does not match the current schema." }
                    connection.createStatement().use { it.execute("PRAGMA foreign_keys=OFF") }
                    connection.autoCommit = false
                    try {
                        expected.forEach { (table, columns) ->
                            connection.createStatement().use { it.executeUpdate("DELETE FROM $table") }
                            val names = columns.joinToString(",") { it.first }
                            val placeholders = columns.joinToString(",") { "?" }
                            connection.prepareStatement("INSERT INTO $table ($names) VALUES ($placeholders)").use { insert ->
                                tables.getAsJsonArray(table).forEach { element ->
                                    val row = element.asJsonObject
                                    require(row.keySet() == columns.map { it.first }.toSet()) {
                                        "JSON storage row in $table has unknown or missing columns."
                                    }
                                    columns.forEachIndexed { index, (name, type) ->
                                        val value = row.get(name)
                                        when {
                                            value.isJsonNull -> insert.setNull(index + 1, type)
                                            type == Types.BIGINT -> {
                                                val raw = value.asString
                                                require(raw.matches(Regex("-?[0-9]+"))) { "Invalid integer in $table.$name." }
                                                insert.setLong(index + 1, raw.toLong())
                                            }
                                            else -> insert.setString(index + 1, value.asString)
                                        }
                                    }
                                    insert.executeUpdate()
                                }
                            }
                        }
                        connection.createStatement().use { query ->
                            query.executeQuery("PRAGMA foreign_key_check").use { result ->
                                check(!result.next()) { "JSON storage violates a foreign key constraint." }
                            }
                            query.executeQuery("PRAGMA integrity_check").use { result ->
                                check(result.next() && result.getString(1) == "ok") { "JSON storage failed integrity check." }
                            }
                        }
                        connection.commit()
                    } catch (error: Exception) {
                        connection.rollback()
                        throw error
                    }
                }
            } catch (error: Exception) {
                throw JsonStorageException(
                    "Cannot load JSON storage ${file.toAbsolutePath()}: ${error.message}. " +
                        "Preserve it and ${file.fileName}.previous\nno existing JSON data was overwritten.", error,
                )
            }
        }

        private fun writeSnapshot(file: Path, root: JsonObject) {
            val temporary = Files.createTempFile(requireNotNull(file.parent), ".${file.fileName}.", ".tmp")
            try {
                val bytes = (GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(root) + "\n")
                    .toByteArray(StandardCharsets.UTF_8)
                FileChannel.open(temporary, WRITE).use { channel ->
                    val buffer = java.nio.ByteBuffer.wrap(bytes)
                    while (buffer.hasRemaining()) channel.write(buffer)
                    channel.force(true)
                }
                if (Files.exists(file)) {
                    val previous = file.resolveSibling("${file.fileName}.previous")
                    val pendingPrevious = Files.createTempFile(file.parent, ".${file.fileName}.", ".backup")
                    try {
                        Files.copy(file, pendingPrevious, REPLACE_EXISTING)
                        FileChannel.open(pendingPrevious, WRITE).use { it.force(true) }
                        Files.move(pendingPrevious, previous, ATOMIC_MOVE, REPLACE_EXISTING)
                    } finally {
                        Files.deleteIfExists(pendingPrevious)
                    }
                }
                Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                throw JsonStorageException("Filesystem does not support atomic JSON replacement for ${file.toAbsolutePath()}.", error)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}

class JsonStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
