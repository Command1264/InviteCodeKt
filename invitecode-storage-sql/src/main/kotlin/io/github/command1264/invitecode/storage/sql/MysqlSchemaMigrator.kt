package io.github.command1264.invitecode.storage.sql

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.util.Locale
import javax.sql.DataSource

/** MySQL DDL commits independently. The ledger stays APPLYING until every table is verified. */
class MysqlSchemaMigrator(
    private val dataSource: DataSource,
    private val guard: BlockingOperationGuard,
    private val afterTable: (String) -> Unit = {},
) {
    fun migrate(): Int {
        guard.checkAllowed()
        try {
            dataSource.connection.use { connection ->
                val schema = schemaName(connection)
                val lockName = "invitecode:" + sha256(schema).take(40)
                val acquired = connection.prepareStatement("SELECT GET_LOCK(?, 10)").use { statement ->
                    statement.setString(1, lockName)
                    statement.executeQuery().use { result -> result.next() && result.getInt(1) == 1 }
                }
                if (!acquired) throw MysqlStorageException("MySQL schema lock timed out\nretry when other plugin instances finish migration.")
                try {
                    migrateLocked(connection, schema)
                } finally {
                    connection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement ->
                        statement.setString(1, lockName)
                        statement.executeQuery().close()
                    }
                }
            }
            return CURRENT_VERSION
        } catch (error: SQLException) {
            throw MysqlStorageException(
                "MySQL schema migration failed (SQLState ${error.sqlState ?: "unknown"}, code ${error.errorCode})\n" +
                    "inspect the database before retrying. No secret was logged.",
            )
        }
    }

    private fun migrateLocked(connection: Connection, schema: String) {
        val ledgerExists = tableExists(connection, schema, LEDGER)
        if (!ledgerExists) {
            val existingObjects = connection.metaData.getTables(schema, null, "%", arrayOf("TABLE", "VIEW")).use { rows ->
                buildSet { while (rows.next()) add(rows.getString("TABLE_NAME")) }
            }
            if (existingObjects.isNotEmpty() && existingObjects != setOf(TRANSFER_GUARD)) throw MysqlStorageException(
                "MySQL database is not empty and has no migration ledger\npreserve the database and inspect it manually.",
            )
            if (existingObjects == setOf(TRANSFER_GUARD)) OfflineStorageTransfer.verifyMysqlTransferGuard(connection)
            connection.createStatement().use { statement -> statement.execute(LEDGER_DDL) }
        }
        val otherVersions = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $LEDGER WHERE version NOT IN (1, 2)").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
        if (otherVersions != 0) throw MysqlStorageException(
            "MySQL schema contains another version\na separate explicit migration is required.",
        )
        val state = connection.prepareStatement(
            "SELECT checksum, state FROM $LEDGER WHERE version = ?",
        ).use { statement ->
            statement.setInt(1, 1)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getString(1) to rows.getString(2) else null
            }
        }
        if (state == null) {
            val hasLaterVersion = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT 1 FROM $LEDGER WHERE version = 2").use { it.next() }
            }
            if (hasLaterVersion) throw MysqlStorageException(
                "MySQL migration ledger contains version 2 without version 1\npreserve the database.",
            )
            if (TABLES.any { tableExists(connection, schema, it.name) }) {
                throw MysqlStorageException("MySQL schema ledger has no version record, but InviteCode tables exist\ninspect manually.")
            }
            connection.prepareStatement(
                "INSERT INTO $LEDGER(version, checksum, state) VALUES (?, ?, 'APPLYING')",
            ).use { statement ->
                statement.setInt(1, 1)
                statement.setString(2, CHECKSUM)
                statement.executeUpdate()
            }
        } else {
            if (state.first != CHECKSUM) throw MysqlStorageException(
                "MySQL schema checksum differs from the bundled migration\npreserve the database and inspect manually.",
            )
            if (state.second !in setOf("APPLYING", "APPLIED")) throw MysqlStorageException(
                "MySQL schema ledger has unknown state ${state.second}\npreserve the database.",
            )
        }
        TABLES.forEach { table ->
            if (!tableExists(connection, schema, table.name)) {
                if (state?.second == "APPLIED") throw MysqlStorageException("MySQL table ${table.name} is missing\npreserve the database.")
                connection.createStatement().use { statement -> statement.execute(table.ddl) }
                afterTable(table.name)
            }
            verifyTable(connection, schema, table)
        }
        if (state?.second != "APPLIED") {
            connection.prepareStatement("UPDATE $LEDGER SET state = 'APPLIED' WHERE version = ? AND state = 'APPLYING'").use {
                it.setInt(1, 1)
                check(it.executeUpdate() == 1) { "MySQL schema ledger changed during migration." }
            }
        }
        val second = connection.prepareStatement("SELECT checksum, state FROM $LEDGER WHERE version = 2").use { statement ->
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) to rows.getString(2) else null }
        }
        if (second == null) {
            if (tableExists(connection, schema, V2_TABLE.name)) throw MysqlStorageException(
                "MySQL reserved-code table exists without a migration record\ninspect the database.",
            )
            connection.prepareStatement("INSERT INTO $LEDGER(version, checksum, state) VALUES (2, ?, 'APPLYING')").use {
                it.setString(1, V2_CHECKSUM)
                it.executeUpdate()
            }
        } else if (second.first != V2_CHECKSUM || second.second !in setOf("APPLYING", "APPLIED")) {
            throw MysqlStorageException("MySQL reserved-code migration checksum or state differs\npreserve the database.")
        }
        if (!tableExists(connection, schema, V2_TABLE.name)) {
            if (second?.second == "APPLIED") throw MysqlStorageException("MySQL reserved-code table is missing\npreserve the database.")
            connection.createStatement().use { it.execute(V2_TABLE.ddl) }
            afterTable(V2_TABLE.name)
        }
        verifyTable(connection, schema, V2_TABLE)
        if (second?.second != "APPLIED") {
            connection.prepareStatement("UPDATE $LEDGER SET state = 'APPLIED' WHERE version = 2 AND state = 'APPLYING'").use {
                check(it.executeUpdate() == 1) { "MySQL reserved-code migration ledger changed." }
            }
        }
    }

    companion object {
        const val CURRENT_VERSION = 2
        internal const val TRANSFER_GUARD = "invitecode_transfer_guard"
        private const val LEDGER = "invitecode_schema_migrations"
        private const val LEDGER_DDL = """
            CREATE TABLE invitecode_schema_migrations (
                version INT NOT NULL PRIMARY KEY,
                checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                state VARCHAR(16) CHARACTER SET ascii NOT NULL
            ) ENGINE=InnoDB
        """
        private val RESOURCE = requireNotNull(MysqlSchemaMigrator::class.java.getResource("/mysql/V1__initial_schema.sql")) {
            "Bundled MySQL schema resource is missing."
        }.readText(StandardCharsets.UTF_8)
        private val TABLES = Regex("(?m)^-- @table ([a-z_]+)\\s*$")
            .findAll(RESOURCE).toList().mapIndexed { index, match ->
                val next = Regex("(?m)^-- @table ([a-z_]+)\\s*$").find(RESOURCE, match.range.last + 1)
                val ddl = RESOURCE.substring(match.range.last + 1, next?.range?.first ?: RESOURCE.length).trim()
                    .removeSuffix(";")
                check(index == 0 || ddl.isNotBlank()) { "Empty MySQL schema statement." }
                TableDefinition(match.groupValues[1], ddl)
            }
        private val CHECKSUM = sha256(RESOURCE)
        private val V2_RESOURCE = requireNotNull(MysqlSchemaMigrator::class.java.getResource("/mysql/V2__reserved_code_hashes.sql")) {
            "Bundled MySQL reserved-code migration resource is missing."
        }.readText(StandardCharsets.UTF_8)
        private val V2_TABLE = TableDefinition(
            "invitecode_reserved_code_hashes",
            V2_RESOURCE.substringAfter("-- @table invitecode_reserved_code_hashes").trim().removeSuffix(";"),
        )
        private val V2_CHECKSUM = sha256(V2_RESOURCE)

        fun isInitialized(connection: Connection): Boolean {
            val schema = schemaName(connection)
            if (!tableExists(connection, schema, LEDGER)) return false
            return connection.prepareStatement("SELECT state FROM $LEDGER WHERE version = ?").use { statement ->
                statement.setInt(1, 1)
                statement.executeQuery().use { rows -> rows.next() && rows.getString(1) == "APPLIED" }
            }
        }

        fun requireCurrent(connection: Connection) {
            val schema = schemaName(connection)
            if (!tableExists(connection, schema, LEDGER)) throw MysqlStorageException(
                "MySQL schema is not initialized\nrun explicit storage migration or use an empty database for first startup.",
            )
            val otherVersions = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM $LEDGER WHERE version NOT IN (1, 2)").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
            if (otherVersions != 0) throw MysqlStorageException(
                "MySQL schema contains another version\nuse a matching InviteCode version or explicit migration.",
            )
            val row = connection.prepareStatement("SELECT checksum, state FROM $LEDGER WHERE version = ?").use { statement ->
                statement.setInt(1, 1)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getString(1) to rows.getString(2) else null
                }
            }
            if (row != (CHECKSUM to "APPLIED")) throw MysqlStorageException(
                "MySQL schema version/checksum is not current\nrun explicit storage migration after inspecting the database.",
            )
            TABLES.forEach { verifyTable(connection, schema, it) }
            val version2 = connection.prepareStatement("SELECT checksum, state FROM $LEDGER WHERE version = 2").use { statement ->
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getString(1) to rows.getString(2) else null
                }
            }
            if (version2 != (V2_CHECKSUM to "APPLIED")) throw MysqlStorageException(
                "MySQL reserved-code migration is not current\nrun explicit migration after a backup.",
            )
            verifyTable(connection, schema, V2_TABLE)
        }

        fun bindNetworkKey(connection: Connection, keyId: String) {
            require(Regex("[0-9a-f]{64}").matches(keyId)) { "Network key ID is invalid." }
            connection.prepareStatement(
                "INSERT IGNORE INTO invitecode_metadata(singleton, network_key_id) VALUES (1, ?)",
            ).use { statement ->
                statement.setString(1, keyId)
                statement.executeUpdate()
            }
            val stored = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT network_key_id FROM invitecode_metadata WHERE singleton = 1").use { rows ->
                    check(rows.next()) { "MySQL network key metadata is missing." }
                    rows.getString(1)
                }
            }
            if (stored != keyId) throw MysqlStorageException(
                "MySQL network secret differs from the key used by another plugin instance\nuse the shared secret.",
            )
        }

        private fun schemaName(connection: Connection): String = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT DATABASE()").use { rows ->
                check(rows.next()) { "MySQL did not return a database name." }
                requireNotNull(rows.getString(1)) { "MySQL connection must select an existing database." }
            }
        }

        private fun tableExists(connection: Connection, schema: String, name: String): Boolean =
            connection.metaData.getTables(schema, null, name, arrayOf("TABLE")).use { it.next() }

        private fun verifyTable(connection: Connection, schema: String, table: TableDefinition) {
            val expected = Regex(
                "(?m)^    ([a-z_]+) (CHAR|VARCHAR|TEXT|INT|BIGINT)(?:\\((\\d+)\\))? (.*)$",
                RegexOption.IGNORE_CASE,
            ).findAll(table.ddl).associate { match ->
                match.groupValues[1] to ColumnShape(
                    match.groupValues[2].uppercase(Locale.ROOT),
                    match.groupValues[3].toIntOrNull(),
                    "NOT NULL" !in match.groupValues[4].uppercase(Locale.ROOT),
                    when {
                        "COLLATE ascii_bin" in match.groupValues[4] -> "ascii_bin"
                        "COLLATE utf8mb4_bin" in match.groupValues[4] -> "utf8mb4_bin"
                        else -> null
                    },
                )
            }
            val actual = connection.metaData.getColumns(schema, null, table.name, null).use { rows ->
                buildMap {
                    while (rows.next()) put(
                        rows.getString("COLUMN_NAME"),
                        ColumnShape(
                            rows.getString("TYPE_NAME").uppercase(Locale.ROOT),
                            rows.getInt("COLUMN_SIZE"),
                            rows.getInt("NULLABLE") != 0,
                            null,
                        ),
                    )
                }
            }
            if (actual.keys != expected.keys || expected.any { (name, shape) ->
                    val found = actual[name]
                    found == null || found.type != shape.type || found.nullable != shape.nullable ||
                        (shape.length != null && found.length != shape.length)
                }) {
                throw MysqlStorageException("MySQL table ${table.name} has unexpected columns\npreserve the database and inspect manually.")
            }
            val collations = connection.prepareStatement(
                "SELECT COLUMN_NAME, COLLATION_NAME FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
            ).use { statement ->
                statement.setString(1, schema)
                statement.setString(2, table.name)
                statement.executeQuery().use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                }
            }
            if (expected.any { (name, shape) -> shape.collation != null && collations[name] != shape.collation }) {
                throw MysqlStorageException("MySQL table ${table.name} has an unsafe collation\npreserve the database.")
            }
            val generated = Regex("(?m)^    ([a-z_]+) [^\\r\\n]*(?:\\r?\\n        [^\\r\\n]*)?GENERATED ALWAYS AS")
                .findAll(table.ddl).map { it.groupValues[1] }.toSet()
            if (generated.isNotEmpty()) {
                val actualGenerated = connection.prepareStatement(
                    "SELECT COLUMN_NAME, EXTRA, GENERATION_EXPRESSION FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND EXTRA LIKE '%STORED GENERATED%'",
                ).use { statement ->
                    statement.setString(1, schema)
                    statement.setString(2, table.name)
                    statement.executeQuery().use { rows ->
                        buildMap { while (rows.next()) put(rows.getString(1), rows.getString(3)) }
                    }
                }
                if (actualGenerated.keys != generated || actualGenerated.values.any(String::isNullOrBlank)) {
                    throw MysqlStorageException("MySQL table ${table.name} has unexpected generated columns\npreserve the database.")
                }
            }
            val indexes = connection.metaData.getIndexInfo(schema, null, table.name, false, false).use { rows ->
                val columns = mutableMapOf<String, MutableList<Pair<Int, String>>>()
                val unique = mutableMapOf<String, Boolean>()
                while (rows.next()) {
                    val name = rows.getString("INDEX_NAME") ?: continue
                    val column = rows.getString("COLUMN_NAME") ?: continue
                    columns.getOrPut(name) { mutableListOf() }.add(rows.getInt("ORDINAL_POSITION") to column)
                    unique[name] = !rows.getBoolean("NON_UNIQUE")
                }
                columns.mapValues { (name, values) ->
                    IndexShape(values.sortedBy { it.first }.map { it.second }, unique.getValue(name))
                }
            }
            val requiredIndexes = Regex("(?m)^    (UNIQUE )?KEY ([a-z_]+) \\(([^)]+)\\)")
                .findAll(table.ddl).associate { match ->
                    match.groupValues[2] to IndexShape(
                        match.groupValues[3].split(',').map(String::trim),
                        match.groupValues[1].isNotEmpty(),
                    )
                }
            val primary = Regex("(?m)^    PRIMARY KEY \\(([^)]+)\\)").find(table.ddl)?.groupValues?.get(1)
                ?.split(',')?.map(String::trim)
            if (primary == null || indexes["PRIMARY"] != IndexShape(primary, true) ||
                requiredIndexes.any { (name, shape) -> indexes[name] != shape }) {
                throw MysqlStorageException("MySQL table ${table.name} has unexpected indexes\npreserve the database and inspect manually.")
            }
            val expectedForeignKeys = Regex(
                "(?m)^    CONSTRAINT ([a-z_]+) FOREIGN KEY \\(([^)]+)\\) REFERENCES ([a-z_]+)\\(([^)]+)\\)",
            ).findAll(table.ddl).associate { match ->
                match.groupValues[1] to Triple(match.groupValues[2], match.groupValues[3], match.groupValues[4])
            }
            val actualForeignKeys = connection.metaData.getImportedKeys(schema, null, table.name).use { rows ->
                buildMap {
                    while (rows.next()) put(
                        rows.getString("FK_NAME"),
                        Triple(rows.getString("FKCOLUMN_NAME"), rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME")),
                    )
                }
            }
            if (expectedForeignKeys != actualForeignKeys) throw MysqlStorageException(
                "MySQL table ${table.name} has unexpected foreign keys\npreserve the database and inspect manually.",
            )
            val checkCount = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'CHECK'",
            ).use { statement ->
                statement.setString(1, schema)
                statement.setString(2, table.name)
                statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
            val expectedCheckCount = Regex("(?m)^    CONSTRAINT [a-z_]+ CHECK ").findAll(table.ddl).count()
            if (checkCount != expectedCheckCount) throw MysqlStorageException(
                "MySQL table ${table.name} has unexpected check constraints\npreserve the database.",
            )
            val properties = connection.prepareStatement(
                "SELECT ENGINE, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
            ).use { statement ->
                statement.setString(1, schema)
                statement.setString(2, table.name)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getString(1) to rows.getString(2) else null
                }
            }
            if (!"InnoDB".equals(properties?.first, ignoreCase = true) || properties?.second != "utf8mb4_0900_ai_ci") throw MysqlStorageException(
                "MySQL table ${table.name} must use InnoDB and utf8mb4_0900_ai_ci\npreserve the database.",
            )
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

private data class TableDefinition(val name: String, val ddl: String)
private data class ColumnShape(val type: String, val length: Int?, val nullable: Boolean, val collation: String?)
private data class IndexShape(val columns: List<String>, val unique: Boolean)
