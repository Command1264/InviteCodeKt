package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.MysqlConfiguration
import io.github.command1264.invitecode.core.MysqlTlsMode
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.json.JsonInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.CodeReservationKey
import io.github.command1264.invitecode.storage.sql.MysqlInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.MysqlSchemaMigrator
import io.github.command1264.invitecode.storage.sql.OfflineStorageTransfer
import io.github.command1264.invitecode.storage.sql.PortableStorageSnapshot
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import io.github.command1264.invitecode.storage.sql.SqliteSnapshotAccess
import io.github.command1264.invitecode.storage.sql.StorageDialect
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.security.MessageDigest
import java.sql.Connection
import java.time.Duration
import java.util.Locale

/** Explicit offline transfer entry point. It never changes storage.yml or deletes the source. */
object OfflineStorageTransferMain {
    private fun printMessage(value: String) {
        value.lineSequence().forEach { line -> println(if (line.startsWith("[InviteCode]")) line else "[InviteCode] $line") }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            execute(args)
        } catch (error: Exception) {
            System.err.println("離線搬移已拒絕：${error.message}")
            kotlin.system.exitProcess(1)
        }
    }

    internal fun execute(args: Array<String>, environment: (String) -> String? = System::getenv) {
        val options = parse(args)
        require("stopped" in options) { "先停止所有使用來源或目的地的 InviteCode 實例，再指定 --stopped。" }
        val source = endpoint("from", options, mustExist = true)
        val destination = endpoint("to", options, mustExist = false)
        require(source.backend != destination.backend) { "來源與目的 backend 必須不同。" }
        if (source.file != null && destination.file != null) {
            require(source.file != destination.file) { "來源與目的資料檔不可相同。" }
        }
        val apply = "apply" in options
        if (apply) require("backup" in options) { "正式搬移必須指定已驗證的來源備份 --backup。" }
        val backupPath = if (apply) path(options.required("backup")) else null
        require(backupPath == null || backupPath != destination.file) { "備份路徑不可與目的資料檔相同。" }
        val mysql = if (source.backend == "mysql" || destination.backend == "mysql") mysqlConfig(options) else null
        val password = if (mysql != null) environment(options.required("mysql-password-env"), environment) else null
        val guard = BlockingOperationGuard { }
        hasher(source, options, environment).use { sourceHasher ->
            hasher(destination, options, environment).use { destinationHasher ->
                require(sourceHasher.keyId == destinationHasher.keyId) {
                    "兩端 anti-abuse 密鑰不同\n請先提供相同密鑰。既有 IP hash 與舊邀請碼保留值不可重新計算。"
                }
                val keyId = sourceHasher.keyId
                val snapshot = export(source, mysql, password, sourceHasher, guard)
                require(snapshot.networkKeyId == keyId) { "來源 metadata 與提供的密鑰不一致。" }
                preflightDestination(destination, mysql, password)
                printMessage("預檢通過：${source.backend} → ${destination.backend}\n" +
                    snapshot.tables.joinToString("\n") { "${it.name.removePrefix("invitecode_")}=${it.rows.size}" })
                if (!apply) {
                    printMessage("本次只預覽\n備份來源後，使用相同選項加 --apply --backup <路徑> 執行。")
                    return
                }
                backup(source, requireNotNull(backupPath))
                // Reject changes between preview and the final source read; the backup is made before publication.
                val finalSnapshot = export(source, mysql, password, sourceHasher, guard)
                require(finalSnapshot == snapshot) { "來源在預檢後改變\n目的地未寫入。停止所有寫入者並重新執行。" }
                val checkSourceUnchanged = {
                    require(export(source, mysql, password, sourceHasher, guard) == finalSnapshot) {
                        "搬移期間來源發生變化\n目的地仍在 staging，禁止 cutover。請檢查所有寫入者。"
                    }
                }
                when (destination.backend) {
                    "sqlite" -> writeSqlite(requireNotNull(destination.file), keyId, finalSnapshot, guard, checkSourceUnchanged)
                    "json" -> writeJson(requireNotNull(destination.file), destinationHasher, finalSnapshot, guard, checkSourceUnchanged)
                    "mysql" -> writeMysql(requireNotNull(mysql), requireNotNull(password), finalSnapshot, guard, checkSourceUnchanged)
                }
                printMessage("目的地已完成全表驗證\n來源未改動。確認 storage.yml 僅指向目的地後，再啟動一組 InviteCode 實例。")
            }
        }
    }

    private data class Endpoint(val backend: String, val file: Path?, val secretFile: Path?)

    private fun endpoint(prefix: String, options: Map<String, String>, mustExist: Boolean): Endpoint {
        val backend = options.required("$prefix-backend").lowercase(Locale.ROOT)
        require(backend in setOf("sqlite", "json", "mysql")) { "不支援的 $prefix backend：$backend。" }
        if (backend == "mysql") return Endpoint(backend, null, null)
        val file = path(options.required("$prefix-file"))
        val secretFile = path(options.required("$prefix-secret-file"))
        require(Files.isRegularFile(secretFile, NOFOLLOW_LINKS)) { "$prefix anti-abuse.key 必須已存在。" }
        if (mustExist) {
            require(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "來源資料檔必須已存在。" }
            if (backend == "sqlite") requireNoWal(file)
        } else {
            require(!Files.exists(file, NOFOLLOW_LINKS)) { "目的資料檔已存在\n不可覆寫。" }
            require(Files.isDirectory(requireNotNull(file.parent))) { "目的資料夾必須已存在。" }
        }
        return Endpoint(backend, file, secretFile)
    }

    private fun hasher(
        endpoint: Endpoint, options: Map<String, String>, environment: (String) -> String?,
    ): NetworkAddressHasher =
        if (endpoint.backend == "mysql") NetworkAddressHasher.fromBase64(
            environment(options.required("mysql-secret-env"), environment),
        )
        else NetworkAddressHasher.loadExisting(requireNotNull(endpoint.secretFile))

    private fun export(
        source: Endpoint, mysql: MysqlConfiguration?, password: String?,
        hasher: NetworkAddressHasher, guard: BlockingOperationGuard,
    ): PortableStorageSnapshot = when (source.backend) {
        "sqlite" -> SqliteSnapshotAccess.withConnection(requireNotNull(source.file), guard) { connection ->
            requireNoWal(requireNotNull(source.file))
            connection.createStatement().use { it.execute("PRAGMA query_only=ON") }
            OfflineStorageTransfer.export(connection, StorageDialect.SQLITE, hasher.keyId)
        }
        "json" -> JsonInviteCodeStorage.open(
            requireNotNull(source.file), guard, CodeReservationKey(hasher.keyId, hasher::hashReservedCode),
        ).use { storage ->
            storage.inspectOffline { OfflineStorageTransfer.export(it, StorageDialect.SQLITE, hasher.keyId) }
        }
        else -> MysqlInviteCodeStorage.createPool(requireNotNull(mysql), requireNotNull(password)).use { pool ->
            MysqlTransferCoordinator.withLock(pool) { connection ->
                requireNoOtherMysqlSessions(connection)
                connection.isReadOnly = true
                connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
                connection.autoCommit = false
                try {
                    OfflineStorageTransfer.export(connection, StorageDialect.MYSQL, hasher.keyId)
                } finally {
                    connection.rollback()
                }
            }
        }
    }

    private fun preflightDestination(destination: Endpoint, mysql: MysqlConfiguration?, password: String?) {
        if (destination.backend != "mysql") {
            require(!Files.exists(requireNotNull(destination.file), NOFOLLOW_LINKS)) { "目的資料檔已出現\n停止操作。" }
            return
        }
        MysqlInviteCodeStorage.createPool(requireNotNull(mysql), requireNotNull(password)).use { pool ->
            pool.connection.use { connection ->
                requireNoOtherMysqlSessions(connection)
                val catalog = requireNotNull(connection.catalog) { "MySQL 沒有選定 database。" }
                connection.metaData.getTables(catalog, null, "%", arrayOf("TABLE", "VIEW")).use { tables ->
                    require(!tables.next()) { "MySQL 目的 database 必須完全空白\n不可覆寫或重試既有 staging。" }
                }
            }
        }
    }

    private fun writeSqlite(
        destination: Path, keyId: String, snapshot: PortableStorageSnapshot, guard: BlockingOperationGuard,
        checkSourceUnchanged: () -> Unit,
    ) {
        require(!Files.exists(destination, NOFOLLOW_LINKS)) { "SQLite 目的檔已存在。" }
        val staged = Files.createTempFile(requireNotNull(destination.parent), ".${destination.fileName}.", ".staging")
        try {
            SqliteSchemaMigrator(staged, guard).migrate()
            SqliteSnapshotAccess.withConnection(staged, guard) { connection ->
                OfflineStorageTransfer.import(connection, StorageDialect.SQLITE, snapshot)
                OfflineStorageTransfer.verify(connection, StorageDialect.SQLITE, snapshot)
            }
            require(snapshot.networkKeyId == keyId) { "SQLite 目的密鑰已改變。" }
            checkSourceUnchanged()
            // A hard link publishes the fully verified file without replacing an existing destination.
            Files.createLink(destination, staged)
            SqliteSnapshotAccess.withConnection(destination, guard) {
                OfflineStorageTransfer.verify(it, StorageDialect.SQLITE, snapshot)
            }
        } finally {
            Files.deleteIfExists(staged)
        }
    }

    private fun writeJson(
        destination: Path, hasher: NetworkAddressHasher,
        snapshot: PortableStorageSnapshot, guard: BlockingOperationGuard, checkSourceUnchanged: () -> Unit,
    ) {
        JsonInviteCodeStorage.createOfflineFromSqlite(destination, guard) { connection ->
            OfflineStorageTransfer.import(connection, StorageDialect.SQLITE, snapshot)
            OfflineStorageTransfer.verify(connection, StorageDialect.SQLITE, snapshot)
            checkSourceUnchanged()
        }
        JsonInviteCodeStorage.open(destination, guard, CodeReservationKey(hasher.keyId, hasher::hashReservedCode)).use {
            it.inspectOffline { connection -> OfflineStorageTransfer.verify(connection, StorageDialect.SQLITE, snapshot) }
        }
    }

    private fun writeMysql(
        config: MysqlConfiguration, password: String, snapshot: PortableStorageSnapshot, guard: BlockingOperationGuard,
        checkSourceUnchanged: () -> Unit,
    ) {
        MysqlInviteCodeStorage.createPool(config, password).use { pool ->
            MysqlTransferCoordinator.withLock(pool) { connection ->
                requireNoOtherMysqlSessions(connection)
                val catalog = requireNotNull(connection.catalog)
                connection.metaData.getTables(catalog, null, "%", arrayOf("TABLE", "VIEW")).use {
                    require(!it.next()) { "MySQL 目的 database 已有表格\n停止操作。" }
                }
                // This marker blocks plugin startup if DDL, import, or verification is interrupted.
                OfflineStorageTransfer.createMysqlTransferGuard(connection)
                MysqlSchemaMigrator(pool, guard).migrate()
                MysqlSchemaMigrator.bindNetworkKey(connection, snapshot.networkKeyId)
                OfflineStorageTransfer.import(connection, StorageDialect.MYSQL, snapshot)
                OfflineStorageTransfer.verify(connection, StorageDialect.MYSQL, snapshot)
                checkSourceUnchanged()
                OfflineStorageTransfer.releaseMysqlTransferGuard(connection)
            }
        }
    }

    /** Requires a dedicated database account, so another plugin connection cannot be hidden. */
    private fun requireNoOtherMysqlSessions(connection: Connection) {
        val others = connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB = DATABASE() AND ID <> CONNECTION_ID()",
            ).use { rows -> rows.next(); rows.getLong(1) }
        }
        require(others == 0L) { "偵測到其他 MySQL 連線\n請先停止所有使用這個 database 的程序。" }
    }

    private fun backup(source: Endpoint, backup: Path) {
        if (source.backend == "mysql") {
            require(Files.isRegularFile(backup, NOFOLLOW_LINKS) && Files.size(backup) > 0) {
                "MySQL 來源需要既有且非空的完整備份 dump。"
            }
            return
        }
        val file = requireNotNull(source.file)
        require(file != backup && !Files.exists(backup, NOFOLLOW_LINKS)) { "備份檔不可覆寫來源或既有檔案。" }
        require(Files.isDirectory(requireNotNull(backup.parent))) { "備份資料夾必須已存在。" }
        if (source.backend == "sqlite") requireNoWal(file)
        Files.copy(file, backup, COPY_ATTRIBUTES)
        require(digest(file).contentEquals(digest(backup))) { "來源與備份的 SHA-256 不同\n停止搬移。" }
    }

    private fun requireNoWal(file: Path) {
        require(!Files.exists(file.resolveSibling("${file.fileName}-wal")) &&
            !Files.exists(file.resolveSibling("${file.fileName}-shm"))) {
            "SQLite 仍有 WAL／SHM\n先停止實例、checkpoint 並做一致性備份。"
        }
    }

    private fun digest(file: Path): ByteArray = MessageDigest.getInstance("SHA-256").let { sha ->
        Files.newInputStream(file).use { input ->
            val bytes = ByteArray(8192)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                sha.update(bytes, 0, count)
            }
        }
        sha.digest()
    }

    private fun mysqlConfig(options: Map<String, String>): MysqlConfiguration = MysqlConfiguration(
        options.required("mysql-host"), options.required("mysql-port").toInt(),
        options.required("mysql-database"), options.required("mysql-username"),
        "\${INVITECODE_OFFLINE_PASSWORD}", "\${INVITECODE_OFFLINE_SECRET}", Duration.ofSeconds(10),
        MysqlTlsMode.valueOf(options.getOrDefault("mysql-tls", "VERIFY_IDENTITY")),
    )

    private fun environment(name: String, lookup: (String) -> String?): String {
        require(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name)) { "環境變數名稱無效。" }
        return lookup(name)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("環境變數 $name 不存在或為空。")
    }

    private fun path(raw: String): Path = Paths.get(raw).toAbsolutePath().normalize()

    private fun parse(args: Array<String>): Map<String, String> {
        val flags = setOf("apply", "stopped")
        val values = setOf("from-backend", "to-backend", "from-file", "to-file", "from-secret-file", "to-secret-file",
            "backup", "mysql-host", "mysql-port", "mysql-database", "mysql-username", "mysql-password-env",
            "mysql-secret-env", "mysql-tls")
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < args.size) {
            val option = args[index]
            val name = option.removePrefix("--")
            require(option.startsWith("--") && name in flags + values) { "未知選項 $option。" }
            require(name !in result) { "選項 --$name 不可重複。" }
            if (name in flags) {
                result[name] = "true"
                index++
            } else {
                require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "選項 --$name 需要值。" }
                result[name] = args[index + 1]
                index += 2
            }
        }
        return result
    }

    private fun Map<String, String>.required(name: String): String = get(name)?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("缺少 --$name。")
}
