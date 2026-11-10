package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.MysqlConfiguration
import io.github.command1264.invitecode.core.MysqlTlsMode
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.storage.json.JsonInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.CodeReservationKey
import io.github.command1264.invitecode.storage.sql.MysqlInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import io.github.command1264.invitecode.storage.sql.MysqlSchemaMigrator
import io.github.command1264.invitecode.storage.sql.AnonymizationPreview
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.Locale

/** Invoke directly from the shaded plugin JAR while all InviteCode servers are stopped. */
object OfflineAnonymizeMain {
    private fun printMessage(value: String) {
        value.lineSequence().forEach { line -> println(if (line.startsWith("[InviteCode]")) line else "[InviteCode] $line") }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            execute(args)
        } catch (error: Exception) {
            System.err.println("離線匿名化已拒絕：${error.message}")
            kotlin.system.exitProcess(1)
        }
    }

    internal fun execute(args: Array<String>) {
            val options = parse(args)
            if ("migrate-json" in options) {
                require(options.required("backend").equals("json", ignoreCase = true) && "stopped" in options) {
                    "JSON 升級需要 --backend json --stopped。"
                }
                val source = path(options.required("source"))
                makeBackup(source, path(options.required("backup")))
                JsonInviteCodeStorage.migrateV8ToV9(source, BlockingOperationGuard { })
                printMessage("JSON 快照已升級至 schema 9\n請驗證快照並保留備份。")
                return
            }
            if ("migrate-sqlite" in options) {
                require(options.required("backend").equals("sqlite", ignoreCase = true) && "stopped" in options) {
                    "SQLite 升級需要 --backend sqlite --stopped。"
                }
                val source = path(options.required("source"))
                makeBackup(source, path(options.required("backup")))
                val version = SqliteSchemaMigrator(source, BlockingOperationGuard { }).migrate()
                printMessage("SQLite schema 已升級至 $version\n請驗證資料庫並保留備份。")
                return
            }
            if ("migrate-mysql" in options) {
                require(options.required("backend").equals("mysql", ignoreCase = true) && "stopped" in options) {
                    "MySQL 升級需要 --backend mysql --stopped。"
                }
                requireBackupDump(path(options.required("backup")))
                val password = environment(options.required("password-env"))
                NetworkAddressHasher.fromBase64(environment(options.required("secret-env"))).use { hasher ->
                    val pool = MysqlInviteCodeStorage.createPool(mysqlConfig(options), password)
                    pool.use {
                        val version = MysqlSchemaMigrator(it, BlockingOperationGuard { }).migrate()
                        it.connection.use { connection -> MysqlSchemaMigrator.bindNetworkKey(connection, hasher.keyId) }
                        printMessage("MySQL schema 已升級至 $version\n請驗證資料庫並保留 dump。")
                    }
                }
                return
            }
            val playerId = UUID.fromString(options.required("player"))
            val apply = "apply" in options
            val backend = options.required("backend").lowercase(Locale.ROOT)
            require(backend in setOf("sqlite", "json", "mysql")) { "不支援的儲存 backend。" }
            if (apply) {
                require("stopped" in options) { "停止所有 InviteCode 實例後才可指定 --stopped。" }
                require("backup" in options) { "變更玩家資料前必須指定 --backup。" }
            }
            val guard = BlockingOperationGuard { }
            if (backend == "mysql") {
                val password = environment(options.required("password-env"))
                val secret = environment(options.required("secret-env"))
                NetworkAddressHasher.fromBase64(secret).use { hasher ->
                    val config = mysqlConfig(options)
                    val pool = MysqlInviteCodeStorage.createPool(config, password)
                    val key = CodeReservationKey(hasher.keyId, hasher::hashReservedCode)
                    pool.use {
                        MysqlInviteCodeStorage.open(it, guard, key).use { storage ->
                            val preview = storage.previewAnonymization(playerId)
                            printPreview(preview)
                            if (apply) {
                                requireBackupDump(path(options.required("backup")))
                                storage.anonymizePlayer(playerId)
                                printMessage("玩家資料已匿名化\n請驗證資料並保留備份。")
                            }
                        }
                    }
                }
            } else {
                val source = path(options.required("source"))
                val secret = path(options.required("secret"))
                require(Files.isRegularFile(source, NOFOLLOW_LINKS)) { "儲存來源必須是既有的一般檔案。" }
                NetworkAddressHasher.loadExisting(secret).use { hasher ->
                    val key = CodeReservationKey(hasher.keyId, hasher::hashReservedCode)
                    if (backend == "sqlite") {
                        SqliteInviteCodeStorage.open(source, guard, key).use { storage ->
                            val preview = storage.previewAnonymization(playerId)
                            printPreview(preview)
                            if (apply) {
                                makeBackup(source, path(options.required("backup")))
                                storage.anonymizePlayer(playerId)
                                printMessage("玩家資料已匿名化\n請驗證資料並保留備份。")
                            }
                        }
                    } else {
                        JsonInviteCodeStorage.open(source, guard, key).use { storage ->
                            val preview = storage.previewAnonymization(playerId)
                            printPreview(preview)
                            if (apply) {
                                makeBackup(source, path(options.required("backup")))
                                storage.anonymizePlayer(playerId)
                                printMessage("玩家資料已匿名化\n請驗證資料並保留備份。")
                            }
                        }
                    }
                }
            }
    }

    private fun printPreview(preview: AnonymizationPreview) {
        printMessage("[InviteCode] 影響範圍：\n邀請：${preview.invitations} 筆\n獎勵交付：${preview.rewardDeliveries} 筆\n舊邀請碼：${preview.retiredCodes} 組")
    }

    private fun mysqlConfig(options: Map<String, String>): MysqlConfiguration = MysqlConfiguration(
        options.required("host"), options.required("port").toInt(), options.required("database"),
        options.required("username"), "${'$'}{INVITECODE_OFFLINE_PASSWORD}",
        "${'$'}{INVITECODE_OFFLINE_SECRET}", Duration.ofSeconds(10),
        MysqlTlsMode.valueOf(options.getOrDefault("tls", "VERIFY_IDENTITY")),
    )

    private fun requireBackupDump(backup: Path) {
        require(Files.isRegularFile(backup, NOFOLLOW_LINKS) && Files.size(backup) > 0) {
            "執行前必須提供既有且非空的 MySQL 備份 dump。"
        }
    }

    private fun makeBackup(source: Path, destination: Path) {
        require(Files.isRegularFile(source, NOFOLLOW_LINKS)) { "儲存來源必須是既有的一般檔案。" }
        require(source != destination) { "備份路徑不能與來源相同。" }
        require(!Files.exists(destination, NOFOLLOW_LINKS)) { "備份檔已存在\n請選擇新路徑。" }
        require(Files.isDirectory(destination.parent)) { "備份檔的上層資料夾必須存在。" }
        // A leftover WAL can contain committed rows absent from the main SQLite file.
        if (!source.fileName.toString().endsWith(".json", ignoreCase = true)) {
            require(!Files.exists(source.resolveSibling("${source.fileName}-wal")) &&
                !Files.exists(source.resolveSibling("${source.fileName}-shm"))) {
                "SQLite 仍有 WAL／SHM 檔案\n請先確認實例已停止、執行 checkpoint 並完整備份。"
            }
        }
        Files.copy(source, destination, COPY_ATTRIBUTES)
        require(digest(destination).contentEquals(digest(source))) { "備份與來源的 SHA-256 不一致\n已停止操作。" }
    }

    private fun digest(path: Path): ByteArray = MessageDigest.getInstance("SHA-256").let { sha ->
        Files.newInputStream(path).use { input ->
            val chunk = ByteArray(8192)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                sha.update(chunk, 0, count)
            }
        }
        sha.digest()
    }

    private fun path(raw: String): Path = Paths.get(raw).toAbsolutePath().normalize()

    private fun environment(name: String): String {
        require(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name)) { "環境變數名稱無效。" }
        return System.getenv(name)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("環境變數 $name 不存在或為空。")
    }

    private fun parse(args: Array<String>): Map<String, String> {
        val flags = setOf("apply", "stopped", "migrate-json", "migrate-sqlite", "migrate-mysql")
        val values = setOf("backend", "source", "secret", "player", "backup", "host", "port", "database",
            "username", "password-env", "secret-env", "tls")
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < args.size) {
            val name = args[index].removePrefix("--")
            require(args[index].startsWith("--") && name in flags + values) { "未知選項 ${args[index]}。" }
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
