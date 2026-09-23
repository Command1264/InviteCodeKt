package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.StorageBackendType
import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.core.RewardDeliveryCoordinator
import io.github.command1264.invitecode.core.QualificationProviderRegistry
import io.github.command1264.invitecode.api.InviteCodeApi
import io.github.command1264.invitecode.platform.bukkit.AsyncOperationExecutor
import io.github.command1264.invitecode.platform.bukkit.AtomicRuntimeSnapshot
import io.github.command1264.invitecode.platform.bukkit.ClaimInteractionSessions
import io.github.command1264.invitecode.platform.bukkit.InvalidInviteCodeCommand
import io.github.command1264.invitecode.platform.bukkit.InviteCodeCommandId
import io.github.command1264.invitecode.platform.bukkit.InviteCodeCommandParser
import io.github.command1264.invitecode.platform.bukkit.InviteCodeRuntimeConfiguration
import io.github.command1264.invitecode.platform.bukkit.ParsedInviteCodeCommand
import io.github.command1264.invitecode.platform.bukkit.PluginRuntimeState
import io.github.command1264.invitecode.platform.bukkit.RuntimePhase
import io.github.command1264.invitecode.platform.bukkit.RuntimeConfigurationReload
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import io.github.command1264.invitecode.platform.bukkit.PlatformTaskDispatcher
import io.github.command1264.invitecode.platform.modern.ModernPlatformCapabilities
import io.github.command1264.invitecode.platform.modern.ModernPlatformIntegration
import io.github.command1264.invitecode.platform.modern.PlatformTaskDispatchers
import io.github.command1264.invitecode.storage.json.JsonInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import io.github.command1264.invitecode.storage.sql.MysqlSchemaMigrator
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.ServicePriority
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import java.util.logging.Level

class InviteCodePlugin : JavaPlugin() {
    private val runtime = PluginRuntimeState()
    private val configuration = AtomicRuntimeSnapshot<InviteCodeRuntimeConfiguration>()
    private val storage = AtomicReference<InviteCodeStorage?>()
    private val networkHasher = AtomicReference<NetworkAddressHasher?>()
    private val sessions = ClaimInteractionSessions()
    private var operations: AsyncOperationExecutor? = null
    private var playerWorkflows: PlayerWorkflowController? = null
    private var qualificationTask: AutoCloseable? = null
    private lateinit var tasks: PlatformTaskDispatcher
    private var modernIntegration: ModernPlatformIntegration? = null

    override fun onEnable() {
        saveBundledResources()
        tasks = PlatformTaskDispatchers.create(this)
        server.onlinePlayers.forEach(tasks::track)
        val executor = AsyncOperationExecutor("InviteCode-storage") { operation ->
            tasks.global(operation)
        }
        operations = executor
        val modern = ModernPlatformCapabilities.initialize(this)
        modernIntegration = modern
        val externalQualificationProviders = QualificationProviderRegistry()
        val playerWorkflows = PlayerWorkflowController(
            this,
            configuration,
            storage,
            executor,
            tasks,
            sessions,
            externalQualificationProviders,
            networkHasher,
            modern.capabilities,
            modern.dialogInput,
            modern.displayFormatter,
            economyGateway = { OptionalVaultIntegration.find(server, logger) },
        )
        this.playerWorkflows = playerWorkflows
        server.servicesManager.register(
            InviteCodeApi::class.java,
            BukkitInviteCodeApi(
                configuration,
                storage,
                executor,
                externalQualificationProviders,
                playerWorkflows::requestQualificationEvaluation,
            ),
            this,
            ServicePriority.Normal,
        )
        server.pluginManager.registerEvents(playerWorkflows, this)
        val adminWorkflows = AdminWorkflowController(
            this,
            configuration,
            storage,
            executor,
            tasks,
            refreshPlayer = { playerId ->
                tasks.onlinePlayer(playerId)?.let { player ->
                    tasks.player(player, operation = { playerWorkflows.registerPlayer(player) })
                }
            },
        )
        getCommand("invitecode")?.let { command ->
            command.setExecutor(
                RuntimeCommandExecutor(
                    runtime,
                    configuration,
                    reload = ::reload,
                    migrateStorage = ::migrateStorage,
                    playerWorkflow = playerWorkflows::handle,
                    adminWorkflow = adminWorkflows::handle,
                    doctor = ::doctor,
                ),
            )
            command.setTabCompleter(BukkitInviteCodeTabCompleter(tasks))
        }
        qualificationTask = tasks.repeatGlobal(20L * 60L, 20L * 60L) {
            if (runtime.phase == RuntimePhase.ACTIVE) {
                tasks.onlinePlayers().forEach { player ->
                    tasks.player(player, operation = { playerWorkflows.registerPlayer(player) })
                }
            }
        }
        start(executor)
    }

    override fun onDisable() {
        runtime.beginStopping()
        qualificationTask?.close()
        qualificationTask = null
        sessions.clear()
        modernIntegration?.dialogInput?.close()
        modernIntegration = null
        server.servicesManager.unregisterAll(this)
        operations?.close(Duration.ofSeconds(2))
        operations = null
        playerWorkflows = null
        networkHasher.getAndSet(null)?.close()
        storage.getAndSet(null)?.close()
        runtime.finishStopping()
    }

    private fun start(executor: AsyncOperationExecutor) {
        val accepted = executor.submit(
            operation = ::loadStartup,
            onSuccess = { result ->
                result.configuration?.let(configuration::replace)
                if (result.storage != null) {
                    storage.set(result.storage)
                    networkHasher.set(requireNotNull(result.networkHasher))
                    runtime.activate()
                    if (result.recoveredRewardActions > 0) {
                        logger.warning(
                            "已將 ${result.recoveredRewardActions} 筆中斷的 Reward Action 標記為 UNCERTAIN。",
                        )
                    }
                    if (result.storageInitialized) {
                        logger.info("已自動完成首次 ${result.configuration?.storage?.backend} 儲存與防濫用密鑰初始化。")
                    }
                    tasks.onlinePlayers().forEach { player ->
                        tasks.player(player, operation = { playerWorkflows?.registerPlayer(player) })
                    }
                    logger.info("InviteCode ${description.version} 已啟用。")
                } else {
                    val message = result.error?.message ?: "未知的啟動錯誤。"
                    runtime.enterMaintenance(message)
                    logger.log(Level.SEVERE, "InviteCode 已進入維護模式：$message", result.error)
                }
            },
            onFailure = { error ->
                runtime.enterMaintenance(error.message ?: "未知的啟動錯誤。")
                logger.log(Level.SEVERE, "InviteCode 啟動失敗，已進入維護模式。", error)
            },
        )
        if (!accepted) runtime.enterMaintenance("背景執行器未接受啟動工作。")
    }

    private fun loadStartup(): StartupResult {
        val candidate = try {
            BukkitConfigurationLoader(dataFolder.toPath()) { key ->
                logger.warning("找不到語系訊息：$key")
            }.load()
        } catch (error: Exception) {
            return StartupResult(error = error)
        }
        var opened: InviteCodeStorage? = null
        var hasher: NetworkAddressHasher? = null
        return try {
            val guard = BlockingOperationGuard.rejectingServerMainThread(Bukkit::isPrimaryThread)
            val initialized = when (candidate.storage.backend) {
                StorageBackendType.SQLITE -> {
                    val dataDirectory = dataFolder.toPath().normalize()
                    val database = dataDirectory.resolve(candidate.storage.sqlite.file).normalize()
                    require(database.startsWith(dataDirectory)) { "SQLite database path escapes the plugin data directory." }
                    val bootstrap = SqliteRuntimeBootstrap(
                        database, dataDirectory.resolve(NETWORK_SECRET_FILE), guard,
                    ).initializeOrOpen()
                    opened = bootstrap.storage
                    hasher = bootstrap.networkHasher
                    bootstrap.initialized
                }
                StorageBackendType.MYSQL -> {
                    val bootstrap = MysqlRuntimeBootstrap(candidate.storage.mysql, guard).initializeOrOpen()
                    opened = bootstrap.storage
                    hasher = bootstrap.networkHasher
                    bootstrap.initialized
                }
                StorageBackendType.JSON -> {
                    val dataDirectory = dataFolder.toPath().normalize()
                    val file = dataDirectory.resolve(candidate.storage.json.file).normalize()
                    require(file.startsWith(dataDirectory)) { "JSON storage path escapes the plugin data directory." }
                    val bootstrap = JsonRuntimeBootstrap(
                        file, dataDirectory.resolve(NETWORK_SECRET_FILE), guard,
                    ).initializeOrOpen()
                    opened = bootstrap.storage
                    hasher = bootstrap.networkHasher
                    bootstrap.initialized
                }
            }
            val recovered = RewardDeliveryCoordinator(opened).recoverInterrupted(Instant.now())
            StartupResult(
                configuration = candidate,
                storage = opened,
                networkHasher = hasher,
                storageInitialized = initialized,
                recoveredRewardActions = recovered,
            )
        } catch (error: Exception) {
            hasher?.close()
            opened?.close()
            StartupResult(configuration = candidate, error = error)
        }
    }

    private fun reload(sender: CommandSender) {
        val executor = operations
        if (executor == null) {
            sendLocalized(sender, "admin.reload-stopping")
            return
        }
        val accepted = executor.callbacksOn { callback -> dispatchSender(sender, callback) }.submit(
            operation = {
                val candidate = BukkitConfigurationLoader(dataFolder.toPath()) { key ->
                    logger.warning("找不到語系訊息：$key")
                }.load()
                val current = configuration.current()
                RuntimeConfigurationReload.reconcile(current.value, candidate)
            },
            onSuccess = { result ->
                configuration.replace(result.configuration)
                sendLocalized(sender, "admin.reload-success")
                if (result.storageRestartRequired) {
                    sendLocalized(sender, "admin.reload-storage-restart")
                }
            },
            onFailure = { error ->
                sendLocalized(sender, "admin.reload-failed")
                logger.log(Level.WARNING, "InviteCode reload 驗證失敗；仍使用原設定。", error)
            },
        )
        if (!accepted) sendLocalized(sender, "admin.reload-stopping")
    }

    private fun migrateStorage(sender: CommandSender, request: ParsedInviteCodeCommand) {
        val candidate = configuration.currentOrNull()?.value
        if (candidate == null) {
            sendLocalized(sender, "admin.storage-config-unavailable")
            return
        }
        val target = request.operands.single().uppercase(java.util.Locale.ROOT)
        if (target != candidate.storage.backend.name || target == "JSON") {
            sendLocalized(sender, "admin.storage-unsupported")
            return
        }
        val executor = operations
        if (executor == null) {
            sendLocalized(sender, "admin.storage-stopping")
            return
        }
        val actor = sender.name
        val reason = requireNotNull(request.reason)
        val accepted = executor.callbacksOn { callback -> dispatchSender(sender, callback) }.submit(
            operation = {
                val guard = BlockingOperationGuard.rejectingServerMainThread(Bukkit::isPrimaryThread)
                when (candidate.storage.backend) {
                    StorageBackendType.SQLITE -> {
                        val dataDirectory = dataFolder.toPath().normalize()
                        val database = dataDirectory.resolve(candidate.storage.sqlite.file).normalize()
                        require(database.startsWith(dataDirectory)) {
                            "SQLite database path escapes the plugin data directory."
                        }
                        val version = SqliteSchemaMigrator(database, guard).migrate()
                        val opened = SqliteInviteCodeStorage.open(database, guard)
                        var newHasher: NetworkAddressHasher? = null
                        try {
                            val loadedHasher = networkHasher.get()
                            newHasher = if (loadedHasher == null) {
                                NetworkAddressHasher.loadOrCreate(dataDirectory.resolve(NETWORK_SECRET_FILE))
                            } else null
                            opened.purgeNetworkHashesWithDifferentKey((loadedHasher ?: requireNotNull(newHasher)).keyId)
                            opened.saveAuditRecord(AuditRecord(UUID.randomUUID(), InvitationAuditAction.STORAGE_MIGRATED, actor, reason, Instant.now()))
                            StorageMigrationResult(version, opened, newHasher)
                        } catch (error: Exception) {
                            newHasher?.close()
                            opened.close()
                            throw error
                        }
                    }
                    StorageBackendType.MYSQL -> {
                        val bootstrap = MysqlRuntimeBootstrap(
                            candidate.storage.mysql, guard, explicitMigration = true,
                        ).initializeOrOpen()
                        try {
                            bootstrap.storage.saveAuditRecord(
                                AuditRecord(UUID.randomUUID(), InvitationAuditAction.STORAGE_MIGRATED, actor, reason, Instant.now()),
                            )
                            StorageMigrationResult(MysqlSchemaMigrator.CURRENT_VERSION, bootstrap.storage, bootstrap.networkHasher)
                        } catch (error: Exception) {
                            bootstrap.storage.close()
                            bootstrap.networkHasher.close()
                            throw error
                        }
                    }
                    StorageBackendType.JSON -> error("JSON storage is not implemented.")
                }
            },
            onSuccess = { result ->
                storage.getAndSet(result.storage)?.close()
                result.networkHasher?.let { hasher ->
                    networkHasher.getAndSet(hasher)?.close()
                }
                if (runtime.phase == RuntimePhase.MAINTENANCE) runtime.activate()
                tasks.onlinePlayers().forEach { player ->
                    tasks.player(player, operation = { playerWorkflows?.registerPlayer(player) })
                }
                logger.info("$actor 以理由「$reason」完成 $target schema migration 至版本 ${result.version}。")
                sendLocalized(sender, "admin.storage-success", mapOf("version" to result.version.toString()))
            },
            onFailure = { error ->
                sendLocalized(sender, "admin.storage-failed")
                logger.log(Level.SEVERE, "$actor 執行 $target schema migration 失敗；理由：$reason", error)
            },
        )
        if (!accepted) sendLocalized(sender, "admin.storage-stopping")
    }

    private fun doctor(sender: CommandSender) {
        val generation = configuration.currentOrNull()?.generation?.toString() ?: "尚未載入"
        val executor = operations
        val store = storage.get()
        val placeholder = server.pluginManager.getPlugin("PlaceholderAPI")?.isEnabled == true
        val vault = OptionalVaultIntegration.find(server, logger) != null
        val capabilities = modernIntegration?.capabilities
        sendLocalized(
            sender,
            "admin.doctor-runtime",
            mapOf(
                "phase" to runtime.phase.name,
                "generation" to generation,
                "executor" to if (executor?.acceptingOperations == true) "ACCEPTING" else "STOPPED",
            ),
        )
        sendLocalized(
            sender,
            "admin.doctor-integrations",
            mapOf(
                "placeholder" to placeholder.toString(),
                "vault" to vault.toString(),
                "dialog" to (capabilities?.dialogProvider ?: "NONE"),
                "rgb" to (capabilities?.rgbText ?: false).toString(),
            ),
        )
        runtime.maintenanceReason?.let {
            sendLocalized(sender, "admin.doctor-maintenance", mapOf("reason" to it))
        }
        if (store == null || executor == null) {
            sendLocalized(sender, "admin.doctor-storage-offline")
            return
        }
        val accepted = executor.callbacksOn { callback -> dispatchSender(sender, callback) }.submit(
            operation = { store.rewardBacklogCounts() },
            onSuccess = { backlog ->
                sendLocalized(
                    sender,
                    "admin.doctor-storage",
                    mapOf(
                        "backend" to configuration.current().value.storage.backend.name,
                        "schema" to when (configuration.current().value.storage.backend) {
                            StorageBackendType.SQLITE -> SqliteSchemaMigrator.currentVersion.toString()
                            StorageBackendType.MYSQL -> MysqlSchemaMigrator.CURRENT_VERSION.toString()
                            StorageBackendType.JSON -> JsonInviteCodeStorage.FORMAT_VERSION.toString()
                        },
                        "pending" to backlog.pending.toString(),
                        "processing" to backlog.processing.toString(),
                        "uncertain" to backlog.uncertain.toString(),
                    ),
                )
            },
            onFailure = { sendLocalized(sender, "admin.doctor-storage-failed") },
        )
        if (!accepted) sendLocalized(sender, "admin.doctor-executor-stopping")
    }

    private fun sendLocalized(
        sender: CommandSender,
        key: String,
        variables: Map<String, String> = emptyMap(),
    ) {
        val messages = configuration.currentOrNull()?.value?.messages
        if (messages == null) {
            sender.sendMessage("InviteCode configuration is not loaded.")
            return
        }
        val locale = (sender as? Player)?.let(BukkitLocaleCompatibility::locale)
        sender.sendMessage(messages.resolve(locale, key, variables))
    }

    private fun dispatchSender(sender: CommandSender, operation: () -> Unit) {
        if (sender is Player) tasks.player(sender, operation = operation)
        else tasks.global(operation)
    }

    private fun saveBundledResources() {
        BundledConfigurationInstaller(dataFolder.toPath(), ::getResource).installMissing()
    }

    companion object {
        private const val NETWORK_SECRET_FILE = "anti-abuse.key"
    }
}

private data class StartupResult(
    val configuration: InviteCodeRuntimeConfiguration? = null,
    val storage: InviteCodeStorage? = null,
    val networkHasher: NetworkAddressHasher? = null,
    val storageInitialized: Boolean = false,
    val error: Throwable? = null,
    val recoveredRewardActions: Int = 0,
)

private data class StorageMigrationResult(
    val version: Int,
    val storage: InviteCodeStorage,
    val networkHasher: NetworkAddressHasher?,
)

private class RuntimeCommandExecutor(
    private val runtime: PluginRuntimeState,
    private val configuration: AtomicRuntimeSnapshot<InviteCodeRuntimeConfiguration>,
    private val reload: (CommandSender) -> Unit,
    private val migrateStorage: (CommandSender, ParsedInviteCodeCommand) -> Unit,
    private val playerWorkflow: (Player, ParsedInviteCodeCommand) -> Boolean,
    private val adminWorkflow: (CommandSender, ParsedInviteCodeCommand) -> Boolean,
    private val doctor: (CommandSender) -> Unit,
) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean = when (val parsed = InviteCodeCommandParser.parse(args.toList())) {
        is InvalidInviteCodeCommand -> {
            send(sender, "runtime.usage", mapOf("label" to label))
            true
        }
        is ParsedInviteCodeCommand -> execute(sender, label, parsed)
    }

    private fun execute(sender: CommandSender, label: String, request: ParsedInviteCodeCommand): Boolean {
        if (!sender.hasPermission(request.permission)) {
            send(sender, "runtime.no-permission")
            return true
        }
        if (request.playerOnly && sender !is Player) {
            send(sender, "runtime.player-only")
            return true
        }
        if (runtime.phase != RuntimePhase.ACTIVE && !request.allowedDuringMaintenance) {
            send(
                sender,
                "runtime.unavailable",
                mapOf("detail" to (runtime.maintenanceReason ?: runtime.phase.name)),
            )
            return true
        }
        when (request.id) {
            InviteCodeCommandId.HELP -> help(sender, label)
            InviteCodeCommandId.ADMIN_RELOAD -> reload(sender)
            InviteCodeCommandId.ADMIN_STORAGE_MIGRATE -> migrateStorage(sender, request)
            InviteCodeCommandId.ADMIN_DOCTOR -> {
                doctor(sender)
            }
            InviteCodeCommandId.CODE,
            InviteCodeCommandId.CLAIM,
            InviteCodeCommandId.CONFIRM,
            InviteCodeCommandId.CANCEL,
            InviteCodeCommandId.STATUS,
            InviteCodeCommandId.STATS -> playerWorkflow(sender as Player, request)
            else -> adminWorkflow(sender, request)
        }
        return true
    }

    private fun send(sender: CommandSender, key: String, variables: Map<String, String> = emptyMap()) {
        val messages = configuration.currentOrNull()?.value?.messages
        if (messages == null) {
            sender.sendMessage("InviteCode configuration is not loaded.")
            return
        }
        val locale = (sender as? Player)?.let(BukkitLocaleCompatibility::locale)
        sender.sendMessage(messages.resolve(locale, key, variables))
    }

    private fun help(sender: CommandSender, label: String) {
        send(sender, "runtime.help-header")
        val routes = listOf(
            Triple("invitecode.command.code", true, "/$label code"),
            Triple("invitecode.command.claim", true, "/$label claim [code] | confirm | cancel"),
            Triple("invitecode.command.status", true, "/$label status"),
            Triple("invitecode.command.stats", true, "/$label stats"),
            Triple("invitecode.admin.inspect", false, "/$label admin inspect player|invitation|reward ..."),
            Triple("invitecode.admin.invitation", false, "/$label admin invitation unlink|transfer ..."),
            Triple("invitecode.admin.qualification", false, "/$label admin invitation force-qualify ..."),
            Triple("invitecode.admin.eligibility", false, "/$label admin eligibility reset ..."),
            Triple("invitecode.admin.reward", false, "/$label admin reward retry|mark-delivered ..."),
            Triple("invitecode.admin.code", false, "/$label admin code rotate ..."),
            Triple("invitecode.admin.storage", false, "/$label admin storage migrate SQLITE <reason>"),
            Triple("invitecode.admin.reload", false, "/$label reload"),
            Triple("invitecode.admin.doctor", false, "/$label admin doctor"),
        )
        routes.filter { (permission, playerOnly, _) ->
            sender.hasPermission(permission) && (!playerOnly || sender is Player)
        }.forEach { (_, _, usage) -> sender.sendMessage(usage) }
    }
}
