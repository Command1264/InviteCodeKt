package io.github.command1264.invitecode.storage.sql

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/** 在任何 SQLite I/O 前確認目前 thread 允許執行 blocking operation。 */
fun interface BlockingOperationGuard {
    fun checkAllowed()

    companion object {
        fun rejectingServerMainThread(isServerMainThread: () -> Boolean): BlockingOperationGuard =
            BlockingOperationGuard {
                check(!isServerMainThread()) {
                    "SQLite storage cannot run on the server main thread; " +
                        "schedule the operation on a dedicated storage executor."
                }
            }
    }
}

class SqliteStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

data class SqliteSchemaMigration(
    val version: Int,
    val description: String,
    val statements: List<String>,
) {
    init {
        require(version > 0) { "Schema migration version must be positive." }
        require(description.isNotBlank()) { "Schema migration description must not be blank." }
        require(statements.isNotEmpty()) { "Schema migration must contain at least one statement." }
    }
}

class SqliteSchemaMigrator(
    private val database: Path,
    private val blockingOperationGuard: BlockingOperationGuard,
    private val migrations: List<SqliteSchemaMigration> = DEFAULT_MIGRATIONS,
) {
    fun migrate(): Int {
        blockingOperationGuard.checkAllowed()
        database.toAbsolutePath().parent?.let(Files::createDirectories)
        return connect(database).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("PRAGMA busy_timeout = 5000")
            }
            migrate(connection)
        }
    }

    private fun migrate(connection: Connection): Int {
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS invitecode_schema_version (
                        singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
                        version INTEGER NOT NULL CHECK (version >= 0)
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    "INSERT OR IGNORE INTO invitecode_schema_version(singleton, version) VALUES (1, 0)",
                )
            }
            var current = readSchemaVersion(connection)
            val ordered = migrations
            require(ordered.map(SqliteSchemaMigration::version) == (1..ordered.size).toList()) {
                "Schema migrations must be contiguous and start at version 1."
            }
            if (current > ordered.size) {
                throw SqliteStorageException(
                    "SQLite schema version $current is newer than supported version ${ordered.size}; " +
                        "upgrade InviteCodeKt before opening ${database.toAbsolutePath()}.",
                )
            }
            ordered.filter { it.version > current }.forEach { migration ->
                if (migration.version != current + 1) {
                    throw SqliteStorageException(
                        "SQLite schema migration ${current + 1} is missing before ${migration.version}.",
                    )
                }
                migration.statements.forEach { sql ->
                    connection.createStatement().use { it.execute(sql) }
                }
                connection.prepareStatement(
                    "UPDATE invitecode_schema_version SET version = ? WHERE singleton = 1",
                ).use { statement ->
                    statement.setInt(1, migration.version)
                    statement.executeUpdate()
                }
                current = migration.version
            }
            connection.commit()
            return current
        } catch (error: Exception) {
            try {
                connection.rollback()
            } catch (rollbackError: SQLException) {
                error.addSuppressed(rollbackError)
            }
            if (error is SqliteStorageException) throw error
            throw SqliteStorageException(
                "SQLite schema migration failed for ${database.toAbsolutePath()}; " +
                    "the transaction was rolled back. Preserve the database and inspect the cause before retrying.",
                error,
            )
        } finally {
            connection.autoCommit = true
        }
    }

    companion object {
        val DEFAULT_MIGRATIONS: List<SqliteSchemaMigration> = listOf(
            SqliteSchemaMigration(
                version = 1,
                description = "Create player identity records",
                statements = listOf(
                    """
                    CREATE TABLE invitecode_players (
                        player_id TEXT PRIMARY KEY,
                        first_seen_at TEXT NOT NULL,
                        last_known_name TEXT NULL
                    )
                    """.trimIndent(),
                ),
            ),
            SqliteSchemaMigration(
                version = 2,
                description = "Create durable Invite Code registry",
                statements = listOf(
                    """
                    CREATE TABLE invitecode_codes (
                        code TEXT PRIMARY KEY,
                        inviter_id TEXT NOT NULL,
                        activated_at TEXT NOT NULL,
                        retired_at TEXT NULL,
                        FOREIGN KEY (inviter_id) REFERENCES invitecode_players(player_id)
                    )
                    """.trimIndent(),
                    """
                    CREATE UNIQUE INDEX invitecode_one_active_code_per_inviter
                    ON invitecode_codes(inviter_id)
                    WHERE retired_at IS NULL
                    """.trimIndent(),
                ),
            ),
            SqliteSchemaMigration(
                version = 3,
                description = "Create durable Invitation records",
                statements = listOf(
                    """
                    CREATE TABLE invitecode_invitations (
                        invitation_id TEXT PRIMARY KEY,
                        inviter_id TEXT NOT NULL,
                        invitee_id TEXT NOT NULL UNIQUE,
                        status TEXT NOT NULL,
                        claimed_at TEXT NOT NULL,
                        qualified_at TEXT NULL,
                        qualification_revision TEXT NULL,
                        FOREIGN KEY (inviter_id) REFERENCES invitecode_players(player_id),
                        FOREIGN KEY (invitee_id) REFERENCES invitecode_players(player_id),
                        CHECK (
                            (qualified_at IS NULL AND qualification_revision IS NULL) OR
                            (qualified_at IS NOT NULL AND qualification_revision IS NOT NULL)
                        )
                    )
                    """.trimIndent(),
                    "CREATE INDEX invitecode_invitations_by_inviter ON invitecode_invitations(inviter_id)",
                ),
            ),
            SqliteSchemaMigration(
                version = 4,
                description = "Create qualification, reward, and audit records",
                statements = listOf(
                    """
                    CREATE TABLE invitecode_qualification_facts (
                        fact_id TEXT PRIMARY KEY,
                        invitation_id TEXT NOT NULL,
                        policy_id TEXT NOT NULL,
                        qualification_revision TEXT NOT NULL,
                        condition_id TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        recorded_at TEXT NOT NULL,
                        FOREIGN KEY (invitation_id) REFERENCES invitecode_invitations(invitation_id)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE invitecode_reward_deliveries (
                        delivery_id TEXT PRIMARY KEY,
                        invitation_id TEXT NULL,
                        reward_id TEXT NOT NULL,
                        status TEXT NOT NULL,
                        idempotency_key TEXT NOT NULL UNIQUE,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        FOREIGN KEY (invitation_id) REFERENCES invitecode_invitations(invitation_id)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE invitecode_reward_actions (
                        action_id TEXT PRIMARY KEY,
                        reward_delivery_id TEXT NOT NULL,
                        invitation_id TEXT NULL,
                        recipient_id TEXT NOT NULL,
                        position INTEGER NOT NULL CHECK (position >= 0),
                        action_type TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        status TEXT NOT NULL,
                        idempotency_key TEXT NOT NULL UNIQUE,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        last_error TEXT NULL,
                        UNIQUE (reward_delivery_id, position),
                        FOREIGN KEY (reward_delivery_id) REFERENCES invitecode_reward_deliveries(delivery_id),
                        FOREIGN KEY (invitation_id) REFERENCES invitecode_invitations(invitation_id),
                        FOREIGN KEY (recipient_id) REFERENCES invitecode_players(player_id)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE invitecode_audit_records (
                        audit_id TEXT PRIMARY KEY,
                        action TEXT NOT NULL,
                        actor TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        occurred_at TEXT NOT NULL,
                        invitation_id TEXT NULL,
                        player_id TEXT NULL,
                        details_kind TEXT NOT NULL,
                        previous_inviter_id TEXT NULL,
                        new_inviter_id TEXT NULL,
                        qualification_revision TEXT NULL,
                        FOREIGN KEY (invitation_id) REFERENCES invitecode_invitations(invitation_id),
                        FOREIGN KEY (player_id) REFERENCES invitecode_players(player_id)
                    )
                    """.trimIndent(),
                ),
            ),
        )

        val currentVersion: Int
            get() = DEFAULT_MIGRATIONS.last().version
    }
}

internal fun connect(database: Path): Connection =
    DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}")

internal fun readSchemaVersion(connection: Connection): Int =
    connection.createStatement().use { statement ->
        statement.executeQuery(
            "SELECT version FROM invitecode_schema_version WHERE singleton = 1",
        ).use { result ->
            if (!result.next()) {
                throw SqliteStorageException("SQLite schema version record is missing.")
            }
            result.getInt("version")
        }
    }
