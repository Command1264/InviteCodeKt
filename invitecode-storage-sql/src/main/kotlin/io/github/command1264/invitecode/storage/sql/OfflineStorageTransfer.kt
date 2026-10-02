package io.github.command1264.invitecode.storage.sql

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

/** Backend whose current, already-initialized schema is used by an offline transfer. */
enum class StorageDialect { SQLITE, MYSQL }

/** Values have deliberately narrow types so the portable representation is independent of JDBC drivers. */
sealed class PortableValue {
    data class Text(val value: String) : PortableValue()
    data class Integer(val value: Long) : PortableValue()
    object Null : PortableValue()
}

data class PortableRow(val values: List<PortableValue>)

data class PortableTable(val name: String, val columns: List<String>, val rows: List<PortableRow>)

/** Immutable-by-convention payload. Table and column lists are checked against the built-in whitelist on use. */
data class PortableStorageSnapshot(
    val sourceDialect: StorageDialect,
    val sourceSchemaVersion: Int,
    val networkKeyId: String,
    val tables: List<PortableTable>,
    val formatVersion: Int = FORMAT_VERSION,
) {
    companion object { const val FORMAT_VERSION = 1 }
}

/**
 * Transfers a stopped backend's business records. This class never initializes schemas and never issues
 * source-side writes. The caller must stop every writer and initialize both schemas before calling it.
 */
object OfflineStorageTransfer {
    /**
     * Exports all business records. SQLite has no metadata table, so [keyId] comes from the caller;
     * MySQL verifies the supplied ID against its unique `invitecode_metadata` row.
     */
    @JvmStatic
    fun export(
        connection: Connection,
        dialect: StorageDialect,
        keyId: String,
    ): PortableStorageSnapshot = guarded("export") {
        requireCurrentSchema(connection, dialect)
        when (dialect) {
            StorageDialect.SQLITE -> Unit
            StorageDialect.MYSQL -> require(readMysqlKeyId(connection) == keyId) {
                "Supplied network key ID differs from MySQL metadata."
            }
        }
        validateKeyId(keyId)
        val tables = BUSINESS_TABLES.map { spec -> readTable(connection, spec) }
        requireKeyedRowsMatch(tables, keyId)
        PortableStorageSnapshot(dialect, currentVersion(dialect), keyId, tables)
    }

    /** Imports atomically into an empty business schema; identical pre-bound MySQL key metadata is allowed. */
    @JvmStatic
    fun `import`(connection: Connection, dialect: StorageDialect, snapshot: PortableStorageSnapshot) {
        guarded("import") {
            validateSnapshot(snapshot)
            requireCurrentSchema(connection, dialect)
            requireEmptyDestination(connection, dialect, snapshot.networkKeyId)
            val originalAutoCommit = connection.autoCommit
            check(originalAutoCommit) {
                "Storage import requires an idle connection in auto-commit mode so it owns the complete transaction."
            }
            if (dialect == StorageDialect.MYSQL) createMysqlTransferGuard(connection)
            try {
                if (dialect == StorageDialect.SQLITE) {
                    connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
                }
                connection.autoCommit = false
                if (dialect == StorageDialect.MYSQL) ensureMysqlMetadataKey(connection, snapshot.networkKeyId)
                BUSINESS_TABLES.forEachIndexed { index, spec ->
                    val table = snapshot.tables[index]
                    insertTable(connection, spec, table)
                }
                verifyRows(connection, snapshot)
                connection.commit()
            } catch (error: Exception) {
                try {
                    connection.rollback()
                } catch (rollbackError: SQLException) {
                    error.addSuppressed(rollbackError)
                    throw StorageTransferException(
                        "Storage import failed and rollback also failed; destination state is uncertain. " +
                            "Keep both backends offline and inspect the destination before retrying.", error,
                    )
                }
                throw if (error is StorageTransferException) error else StorageTransferException(
                    "Storage import failed; the destination transaction was rolled back. " +
                        "Inspect the cause before retrying.", error,
                )
            } finally {
                try {
                    connection.autoCommit = originalAutoCommit
                } catch (restoreError: SQLException) {
                    throw StorageTransferException(
                        "Storage import completed or rolled back, but the connection mode could not be restored; " +
                            "close the connection and inspect the destination state.", restoreError,
                    )
                }
            }
        }
    }

    /** Compares every persistent business cell and checks current schema plus destination key identity. */
    @JvmStatic
    fun verify(connection: Connection, dialect: StorageDialect, snapshot: PortableStorageSnapshot) {
        guarded("verify") {
            validateSnapshot(snapshot)
            requireCurrentSchema(connection, dialect)
            if (dialect == StorageDialect.MYSQL) {
                require(readMysqlKeyId(connection) == snapshot.networkKeyId) {
                    "Destination MySQL network_key_id does not match the snapshot."
                }
            }
            verifyRows(connection, snapshot)
        }
    }

    /** Creates the persistent MySQL maintenance marker before destination initialization or writes. */
    @JvmStatic
    fun createMysqlTransferGuard(connection: Connection) {
        guarded("create MySQL transfer guard") {
            connection.createStatement().use { it.execute(MYSQL_TRANSFER_GUARD_DDL) }
            verifyMysqlTransferGuard(connection)
        }
    }

    /** Removes the MySQL maintenance marker only after the caller has completed all transfer verification. */
    @JvmStatic
    fun releaseMysqlTransferGuard(connection: Connection) {
        guarded("release MySQL transfer guard") {
            requireCurrentSchema(connection, StorageDialect.MYSQL)
            verifyMysqlTransferGuard(connection)
            connection.createStatement().use { it.execute("DROP TABLE invitecode_transfer_guard") }
        }
    }

    internal fun verifyMysqlTransferGuard(connection: Connection) {
        val columns = connection.metaData.getColumns(connection.catalog, null, MYSQL_TRANSFER_GUARD, null).use { rows ->
            buildList {
                while (rows.next()) add(
                    Triple(rows.getString("COLUMN_NAME"), rows.getString("TYPE_NAME").uppercase(), rows.getInt("NULLABLE")),
                )
            }
        }
        check(columns == listOf(Triple("singleton", "INT", DatabaseMetaData.columnNoNulls))) {
            "MySQL transfer guard table has an unexpected structure; preserve it and inspect the database."
        }
        val primaryKey = connection.metaData.getPrimaryKeys(connection.catalog, null, MYSQL_TRANSFER_GUARD).use { rows ->
            if (rows.next()) rows.getString("COLUMN_NAME") else null
        }
        check(primaryKey == "singleton") { "MySQL transfer guard has an unexpected primary key." }
        val properties = connection.prepareStatement(
            "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
        ).use { statement ->
            statement.setString(1, requireNotNull(connection.catalog))
            statement.setString(2, MYSQL_TRANSFER_GUARD)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
        check(properties.equals("InnoDB", ignoreCase = true)) { "MySQL transfer guard must use InnoDB." }
        val checkCount = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'CHECK'",
        ).use { statement ->
            statement.setString(1, requireNotNull(connection.catalog))
            statement.setString(2, MYSQL_TRANSFER_GUARD)
            statement.executeQuery().use { it.next(); it.getInt(1) }
        }
        check(checkCount == 1) { "MySQL transfer guard has an unexpected check constraint." }
        val rows = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $MYSQL_TRANSFER_GUARD").use { it.next(); it.getLong(1) }
        }
        check(rows == 0L) { "MySQL transfer guard must be a marker table without data rows." }
    }

    private fun validateSnapshot(snapshot: PortableStorageSnapshot) {
        require(snapshot.formatVersion == PortableStorageSnapshot.FORMAT_VERSION) {
            "Unsupported portable snapshot format ${snapshot.formatVersion}."
        }
        require(snapshot.sourceSchemaVersion == currentVersion(snapshot.sourceDialect)) {
            "Snapshot source schema version ${snapshot.sourceSchemaVersion} is not current for ${snapshot.sourceDialect}."
        }
        validateKeyId(snapshot.networkKeyId)
        require(snapshot.tables.size == BUSINESS_TABLES.size) {
            "Snapshot must contain exactly ${BUSINESS_TABLES.size} business tables."
        }
        BUSINESS_TABLES.forEachIndexed { index, spec ->
            val table = snapshot.tables[index]
            require(table.name == spec.name && table.columns == spec.columns.map(Column::name)) {
                "Snapshot table layout is invalid at position $index."
            }
            require(table.rows.all { it.values.size == spec.columns.size }) {
                "Snapshot contains a row with an invalid column count in ${spec.name}."
            }
            table.rows.forEach { row ->
                row.values.forEachIndexed { columnIndex, value ->
                    val column = spec.columns[columnIndex]
                    require(value == PortableValue.Null && column.nullable || valueMatches(column.type, value)) {
                        "Snapshot contains an invalid value for ${spec.name}.${column.name}."
                    }
                }
            }
        }
        requireKeyedRowsMatch(snapshot.tables, snapshot.networkKeyId)
    }

    private fun requireKeyedRowsMatch(tables: List<PortableTable>, keyId: String) {
        listOf("invitecode_player_network_hashes", "invitecode_reserved_code_hashes").forEach { name ->
            val table = tables.single { it.name == name }
            val keyColumn = table.columns.indexOf("key_id")
            check(keyColumn >= 0 && table.rows.all { it.values[keyColumn] == PortableValue.Text(keyId) }) {
                "$name contains rows from another anti-abuse key; restore the matching key or resolve those rows before transfer."
            }
        }
    }

    private fun requireCurrentSchema(connection: Connection, dialect: StorageDialect) {
        when (dialect) {
            StorageDialect.SQLITE -> {
                val version = connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT version FROM invitecode_schema_version WHERE singleton = 1").use { rows ->
                        check(rows.next()) { "SQLite schema version row is missing; initialize the schema first." }
                        rows.getInt(1).also { check(!rows.next()) { "SQLite schema version row is not unique." } }
                    }
                }
                check(version == SqliteSchemaMigrator.currentVersion) {
                    "SQLite schema version $version is not current (${SqliteSchemaMigrator.currentVersion})."
                }
            }
            StorageDialect.MYSQL -> MysqlSchemaMigrator.requireCurrent(connection)
        }
    }

    private fun currentVersion(dialect: StorageDialect): Int = when (dialect) {
        StorageDialect.SQLITE -> SqliteSchemaMigrator.currentVersion
        StorageDialect.MYSQL -> MysqlSchemaMigrator.CURRENT_VERSION
    }

    private fun readMysqlKeyId(connection: Connection): String = connection.prepareStatement(
        "SELECT network_key_id FROM invitecode_metadata WHERE singleton = ?",
    ).use { statement ->
        statement.setInt(1, 1)
        statement.executeQuery().use { rows ->
            check(rows.next()) { "MySQL network_key_id metadata is missing; bind it before transfer." }
            rows.getString(1).also { check(!rows.next()) { "MySQL metadata singleton row is not unique." } }
        }
    }

    private fun ensureMysqlMetadataKey(connection: Connection, keyId: String) {
        val existing = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT network_key_id FROM invitecode_metadata WHERE singleton = 1").use { rows ->
                if (rows.next()) rows.getString(1).also { check(!rows.next()) { "MySQL metadata singleton row is not unique." } }
                else null
            }
        }
        if (existing == null) connection.prepareStatement(
            "INSERT INTO invitecode_metadata(singleton, network_key_id) VALUES (?, ?)",
        ).use { statement ->
            statement.setInt(1, 1)
            statement.setString(2, keyId)
            check(statement.executeUpdate() == 1) { "Could not bind destination MySQL metadata to the snapshot key ID." }
        } else check(existing == keyId) { "Destination MySQL network_key_id differs from the snapshot." }
    }

    private fun requireEmptyDestination(connection: Connection, dialect: StorageDialect, expectedKey: String) {
        BUSINESS_TABLES.forEach { spec ->
            val count = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM ${spec.name}").use { rows -> rows.next(); rows.getLong(1) }
            }
            check(count == 0L) { "Destination table ${spec.name} is not empty; refusing to overwrite existing data." }
        }
        if (dialect == StorageDialect.MYSQL) {
            val rows = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT singleton, network_key_id FROM invitecode_metadata").use { result ->
                    val values = mutableListOf<Pair<Int, String>>()
                    while (result.next()) values += result.getInt(1) to result.getString(2)
                    values
                }
            }
            check(rows.isEmpty() || rows == listOf(1 to expectedKey)) {
                "Destination MySQL metadata must be empty or contain only the matching singleton network_key_id row."
            }
        }
    }

    private fun readTable(connection: Connection, spec: TableSpec): PortableTable {
        val sql = "SELECT ${spec.columns.joinToString(", ") { it.name }} FROM ${spec.name}"
        val rows = connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                buildList {
                    while (result.next()) add(PortableRow(spec.columns.mapIndexed { index, column ->
                        readValue(result, index + 1, column)
                    }))
                }
            }
        }
        return PortableTable(spec.name, spec.columns.map(Column::name), rows)
    }

    private fun readValue(result: ResultSet, index: Int, column: Column): PortableValue = when (column.type) {
        ValueType.TEXT -> result.getString(index)?.let(PortableValue::Text) ?: PortableValue.Null
        ValueType.INTEGER -> {
            val value = result.getLong(index)
            if (result.wasNull()) PortableValue.Null else PortableValue.Integer(value)
        }
    }

    private fun insertTable(connection: Connection, spec: TableSpec, table: PortableTable) {
        if (table.rows.isEmpty()) return
        val sql = "INSERT INTO ${spec.name} (${spec.columns.joinToString(", ") { it.name }}) " +
            "VALUES (${spec.columns.joinToString(", ") { "?" }})"
        connection.prepareStatement(sql).use { statement ->
            table.rows.forEach { row ->
                row.values.forEachIndexed { index, value -> bind(statement, index + 1, value, spec.columns[index]) }
                statement.addBatch()
            }
            val outcomes = statement.executeBatch()
            check(outcomes.size == table.rows.size && outcomes.all { it >= 0 || it == java.sql.Statement.SUCCESS_NO_INFO }) {
                "Destination did not insert every row of ${spec.name}."
            }
        }
    }

    private fun bind(statement: PreparedStatement, index: Int, value: PortableValue, column: Column) {
        when (value) {
            is PortableValue.Text -> statement.setString(index, value.value)
            is PortableValue.Integer -> statement.setLong(index, value.value)
            PortableValue.Null -> statement.setNull(index, if (column.type == ValueType.TEXT) Types.VARCHAR else Types.BIGINT)
        }
    }

    private fun verifyRows(connection: Connection, snapshot: PortableStorageSnapshot) {
        BUSINESS_TABLES.forEachIndexed { index, spec ->
            val actual = readTable(connection, spec)
            val expected = snapshot.tables[index]
            check(actual.rows.size == expected.rows.size) {
                "Row count mismatch for ${spec.name}: expected ${expected.rows.size}, found ${actual.rows.size}."
            }
            check(actual.rows.toSet() == expected.rows.toSet() && actual.rows.size == expected.rows.size) {
                "Stored values do not match the snapshot in ${spec.name}."
            }
        }
    }

    private fun valueMatches(type: ValueType, value: PortableValue): Boolean = when (type) {
        ValueType.TEXT -> value is PortableValue.Text
        ValueType.INTEGER -> value is PortableValue.Integer
    }

    private fun validateKeyId(value: String) {
        require(Regex("[0-9a-f]{64}").matches(value)) { "network_key_id must be 64 lowercase hexadecimal characters." }
    }

    private inline fun <T> guarded(operation: String, block: () -> T): T = try {
        block()
    } catch (error: StorageTransferException) {
        throw error
    } catch (error: Exception) {
        val detail = if (error is SQLException) {
            "SQLState ${error.sqlState ?: "unknown"}, code ${error.errorCode}"
        } else {
            error.message ?: error.javaClass.simpleName
        }
        throw StorageTransferException("Storage transfer $operation failed ($detail).", error)
    }

    private enum class ValueType { TEXT, INTEGER }
    private data class Column(val name: String, val type: ValueType, val nullable: Boolean = false)
    private data class TableSpec(val name: String, val columns: List<Column>)
    private fun text(name: String, nullable: Boolean = false) = Column(name, ValueType.TEXT, nullable)
    private fun integer(name: String, nullable: Boolean = false) = Column(name, ValueType.INTEGER, nullable)

    /** SQL names and column lists are fixed here; values are always bound parameters. Order is FK-safe. */
    private val BUSINESS_TABLES = listOf(
        TableSpec("invitecode_players", listOf(text("player_id"), text("first_seen_at"), text("last_known_name", true), integer("eligibility_generation"))),
        TableSpec("invitecode_codes", listOf(text("code"), text("inviter_id"), text("activated_at"), text("retired_at", true))),
        TableSpec("invitecode_invitations", listOf(text("invitation_id"), text("inviter_id"), integer("inviter_eligibility_generation"), text("invitee_id"), integer("invitee_eligibility_generation"), text("status"), text("claimed_at"), text("qualified_at", true), text("qualification_policy_id", true), text("qualification_revision", true))),
        TableSpec("invitecode_qualification_facts", listOf(text("fact_id"), text("invitation_id"), text("policy_id"), text("qualification_revision"), text("condition_id"), text("outcome"), text("recorded_at"))),
        TableSpec("invitecode_reward_deliveries", listOf(text("delivery_id"), text("invitation_id", true), text("reward_id"), text("status"), text("idempotency_key"), text("created_at"), text("updated_at"))),
        TableSpec("invitecode_reward_actions", listOf(text("action_id"), text("reward_delivery_id"), text("invitation_id", true), text("recipient_id"), integer("position"), text("action_type"), text("payload"), text("status"), text("idempotency_key"), text("created_at"), text("updated_at"), text("last_error", true))),
        TableSpec("invitecode_audit_records", listOf(text("audit_id"), text("action"), text("actor"), text("reason"), text("occurred_at"), text("invitation_id", true), text("player_id", true), text("details_kind"), text("previous_inviter_id", true), text("new_inviter_id", true), text("qualification_policy_id", true), text("qualification_revision", true))),
        TableSpec("invitecode_reward_delivery_transitions", listOf(integer("transition_id"), text("delivery_id"), text("previous_status", true), text("current_status"), text("occurred_at"), text("actor", true), text("reason", true))),
        TableSpec("invitecode_reward_action_transitions", listOf(integer("transition_id"), text("action_id"), text("previous_status", true), text("current_status"), text("occurred_at"), text("last_error", true), text("actor", true), text("reason", true))),
        TableSpec("invitecode_player_login_days", listOf(text("player_id"), text("login_date"))),
        TableSpec("invitecode_player_network_hashes", listOf(text("player_id"), text("key_id"), text("network_hash"), text("observed_at"), text("expires_at"))),
        TableSpec("invitecode_reserved_code_hashes", listOf(text("code_hash"), text("key_id"))),
    )

    private const val MYSQL_TRANSFER_GUARD = "invitecode_transfer_guard"
    private const val MYSQL_TRANSFER_GUARD_DDL = """
        CREATE TABLE IF NOT EXISTS invitecode_transfer_guard (
            singleton INT NOT NULL PRIMARY KEY,
            CONSTRAINT invitecode_transfer_guard_singleton CHECK (singleton = 1)
        ) ENGINE=InnoDB
    """
}

class StorageTransferException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
