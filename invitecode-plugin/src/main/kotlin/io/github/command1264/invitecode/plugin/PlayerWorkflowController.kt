package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.api.QualificationEvaluationSnapshot
import io.github.command1264.invitecode.api.QualificationConditionContext
import io.github.command1264.invitecode.core.BuiltInQualificationProviders
import io.github.command1264.invitecode.core.ClaimConfirmation
import io.github.command1264.invitecode.core.ClaimEligibility
import io.github.command1264.invitecode.core.ClaimEligibilityPolicy
import io.github.command1264.invitecode.core.InvitationClaimResult
import io.github.command1264.invitecode.core.InvitationClaimService
import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeLifecycleService
import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.QualificationEvaluationStatus
import io.github.command1264.invitecode.core.QualificationEvaluator
import io.github.command1264.invitecode.core.QualificationProviderRegistry
import io.github.command1264.invitecode.core.QualificationRewardService
import io.github.command1264.invitecode.core.PermissionQualificationLookup
import io.github.command1264.invitecode.core.PlayerNetworkHash
import io.github.command1264.invitecode.core.PlaceholderQualificationLookup
import io.github.command1264.invitecode.core.RewardActionExecutionContext
import io.github.command1264.invitecode.core.RewardDeliveryCoordinator
import io.github.command1264.invitecode.core.StandardRewardActionExecutor
import io.github.command1264.invitecode.core.PlayerClaimState
import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.core.StoredRewardAction
import io.github.command1264.invitecode.platform.bukkit.AsyncRewardDeliveryWorkflow
import io.github.command1264.invitecode.platform.bukkit.BukkitRewardExecutionEnvironment
import io.github.command1264.invitecode.platform.bukkit.CoordinatorRewardActionStateStore
import io.github.command1264.invitecode.platform.bukkit.EconomyRewardGateway
import io.github.command1264.invitecode.platform.bukkit.AsyncOperationExecutor
import io.github.command1264.invitecode.platform.bukkit.AtomicRuntimeSnapshot
import io.github.command1264.invitecode.platform.bukkit.ClaimInteractionSessions
import io.github.command1264.invitecode.platform.bukkit.InputMode
import io.github.command1264.invitecode.platform.bukkit.InviteCodeCommandId
import io.github.command1264.invitecode.platform.bukkit.InviteCodeRuntimeConfiguration
import io.github.command1264.invitecode.platform.bukkit.generator
import io.github.command1264.invitecode.platform.bukkit.parser
import io.github.command1264.invitecode.platform.bukkit.ParsedInviteCodeCommand
import io.github.command1264.invitecode.platform.bukkit.RewardMessageResolver
import io.github.command1264.invitecode.platform.bukkit.NetworkAddressHasher
import net.milkbowl.vault.economy.Economy
import org.bukkit.ChatColor
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.time.Instant
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.logging.Level

class PlayerWorkflowController(
    private val plugin: Plugin,
    private val configuration: AtomicRuntimeSnapshot<InviteCodeRuntimeConfiguration>,
    private val storage: AtomicReference<InviteCodeStorage?>,
    private val operations: AsyncOperationExecutor,
    private val sessions: ClaimInteractionSessions,
    private val externalQualificationProviders: QualificationProviderRegistry,
    private val networkHasher: AtomicReference<NetworkAddressHasher?>,
    private val now: () -> Instant = Instant::now,
) : Listener {
    fun requestQualificationEvaluation(invitationId: UUID): CompletionStage<QualificationEvaluationSnapshot> {
        val future = CompletableFuture<QualificationEvaluationSnapshot>()
        val accepted = operations.submit(
            operation = {
                val store = requireNotNull(storage.get()) { "SQLite storage is unavailable." }
                requireNotNull(store.invitation(invitationId)) { "Invitation $invitationId does not exist." }
            },
            onSuccess = { invitation ->
                val player = plugin.server.getPlayer(invitation.inviteeId)
                val runtime = configuration.current().value
                val playTime = player?.let {
                    Duration.ofMillis(BukkitStatisticCompatibility.playTimeTicks(it) * 50L)
                }
                val inputs = player?.let { captureQualificationInputs(it, runtime) }
                    ?: QualificationInputs(emptyMap(), emptyMap())
                submitQualificationEvaluation(invitationId, playTime, runtime, inputs, future)
            },
            onFailure = future::completeExceptionally,
        )
        if (!accepted) future.completeExceptionally(IllegalStateException("InviteCode is stopping."))
        return future
    }

    fun handle(player: Player, request: ParsedInviteCodeCommand): Boolean = when (request.id) {
        InviteCodeCommandId.CODE -> showCode(player)
        InviteCodeCommandId.CLAIM -> claim(player, request.operands.singleOrNull())
        InviteCodeCommandId.CONFIRM -> confirm(player)
        InviteCodeCommandId.CANCEL -> cancel(player)
        InviteCodeCommandId.STATUS -> status(player)
        InviteCodeCommandId.STATS -> stats(player)
        else -> false
    }.let { true }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        registerPlayer(event.player)
    }

    fun registerPlayer(player: Player) {
        if (storage.get() == null || configuration.currentOrNull() == null) return
        val playerId = player.uniqueId
        val playerName = player.name
        val observedAt = now()
        val snapshot = configuration.current()
        val hasPlayedBefore = player.hasPlayedBefore()
        val playTime = Duration.ofMillis(BukkitStatisticCompatibility.playTimeTicks(player) * 50L)
        val externalInputs = captureQualificationInputs(player, snapshot.value)
        val networkAddress = player.address?.address?.address?.copyOf()
        submit(
            playerId,
            operation = { store ->
                val existing = store.player(playerId)
                val firstSeenAt = if (
                    existing == null && hasPlayedBefore &&
                    snapshot.value.general.claim.existingPlayerPolicy ==
                    io.github.command1264.invitecode.platform.bukkit.ExistingPlayerPolicy.EXPIRED
                ) {
                    observedAt.minus(snapshot.value.general.claim.window).minusNanos(1)
                } else {
                    observedAt
                }
                store.savePlayer(existing?.copy(lastKnownName = playerName) ?: StoredPlayer(playerId, firstSeenAt, playerName))
                store.recordLoginDay(
                    playerId,
                    observedAt.atZone(snapshot.value.general.timeZone).toLocalDate(),
                )
                store.purgeExpiredNetworkHashes(observedAt)
                networkAddress?.let { address ->
                    try {
                        val hasher = requireNotNull(networkHasher.get()) {
                            "Network hash secret is unavailable."
                        }
                        val hash = hasher.hash(address)
                        store.recordNetworkHash(
                            PlayerNetworkHash(
                                playerId,
                                hasher.keyId,
                                hash,
                                observedAt,
                                observedAt.plus(snapshot.value.general.antiAbuse.ipHashRetention),
                            ),
                        )
                    } finally {
                        address.fill(0)
                    }
                }
                evaluateQualificationsAndPendingRewards(
                    store,
                    playerId,
                    playerName,
                    playTime,
                    observedAt,
                    snapshot.value,
                    externalInputs,
                )
            },
            onSuccess = ::deliverRewards,
        )
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val playerId = event.player.uniqueId
        val code = sessions.consumeChatInput(playerId, event.message) ?: return
        event.isCancelled = true
        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.server.getPlayer(playerId)?.let { beginClaim(it, code) }
        })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sessions.disconnect(event.player.uniqueId)
    }

    private fun showCode(player: Player) {
        val playerId = player.uniqueId
        val snapshot = configuration.current()
        val generator = snapshot.value.general.inviteCode.format.generator()
        submit(
            playerId,
            operation = { store ->
                InviteCodeLifecycleService(store, generator, 64).assign(playerId, now())
            },
            onSuccess = { code ->
                send(playerId, "command.code.value", mapOf("code" to display(code.code)))
            },
        )
    }

    private fun claim(player: Player, code: String?) {
        val playerId = player.uniqueId
        if (code != null) {
            beginClaim(player, code)
            return
        }
        val claim = configuration.current().value.general.claim
        if (claim.inputMode == InputMode.COMMAND_ONLY) {
            send(playerId, "command.claim.command-only")
            return
        }
        sessions.startChatInput(playerId, claim.chatInputTimeout)
        scheduleTimeout(claim.chatInputTimeout) {
            if (sessions.expireChatInput(playerId)) send(playerId, "command.claim.chat-timeout")
        }
        send(
            playerId,
            "command.claim.chat-started",
            mapOf("seconds" to claim.chatInputTimeout.seconds.toString()),
        )
    }

    private fun beginClaim(player: Player, rawCode: String) {
        val playerId = player.uniqueId
        val snapshot = configuration.current()
        submit(
            playerId,
            operation = { store -> prepareClaim(playerId, rawCode, snapshot.value, store) },
            onSuccess = { prepared ->
                when (prepared) {
                    PreparedClaim.Invalid -> send(playerId, "command.claim.invalid-code")
                    PreparedClaim.Unavailable -> send(playerId, "command.claim.unavailable")
                    PreparedClaim.Self -> send(playerId, "command.claim.self")
                    is PreparedClaim.Ready -> {
                        sessions.startConfirmation(prepared.confirmation)
                        scheduleTimeout(snapshot.value.general.claim.confirmationTimeout) {
                            if (sessions.expireConfirmation(playerId)) {
                                send(playerId, "command.claim.confirmation-timeout")
                            }
                        }
                        send(
                            playerId,
                            "command.claim.confirmation",
                            mapOf(
                                "inviter" to prepared.confirmation.inviterName,
                                "code" to display(prepared.confirmation.inviteCode),
                                "seconds" to snapshot.value.general.claim.confirmationTimeout.seconds.toString(),
                            ),
                        )
                    }
                }
            },
        )
    }

    private fun prepareClaim(
        inviteeId: UUID,
        rawCode: String,
        runtime: InviteCodeRuntimeConfiguration,
        store: InviteCodeStorage,
    ): PreparedClaim {
        val format = runtime.general.inviteCode.format.parser()
        val code = try {
            InviteCode.parse(rawCode, format)
        } catch (_: IllegalArgumentException) {
            return PreparedClaim.Invalid
        }
        val active = store.activeInviteCode(code) ?: return PreparedClaim.Unavailable
        if (active.inviterId == inviteeId) return PreparedClaim.Self
        val inviterName = store.player(active.inviterId)?.lastKnownName ?: active.inviterId.toString()
        return PreparedClaim.Ready(
            ClaimConfirmation.present(
                inviteeId,
                active.inviterId,
                inviterName,
                active.code,
                now(),
                runtime.general.claim.confirmationTimeout,
            ),
        )
    }

    private fun confirm(player: Player) {
        val playerId = player.uniqueId
        val confirmation = sessions.consumeConfirmation(playerId)
        if (confirmation == null) {
            send(playerId, "command.claim.no-confirmation")
            return
        }
        val snapshot = configuration.current()
        submit(
            playerId,
            operation = { store ->
                val confirmed = confirmation.confirm(playerId, now())
                InvitationClaimService(
                    store,
                    ClaimEligibilityPolicy(snapshot.value.general.claim.window),
                    UUID::randomUUID,
                    snapshot.value.general.antiAbuse.matchingIpAction,
                ).claim(confirmed)
            },
            onSuccess = { result ->
                when (result) {
                    is InvitationClaimResult.Claimed -> {
                        send(playerId, "command.claim.claimed")
                        if (result.matchingNetworkHash) {
                            send(playerId, "command.claim.matching-network-warning")
                            plugin.logger.warning(
                                "允許網路雜湊相符的 Claim：inviter=${result.invitation.inviterId}，" +
                                    "invitee=${result.invitation.inviteeId}。",
                            )
                        }
                        plugin.server.getPlayer(playerId)?.let(::registerPlayer)
                    }
                    is InvitationClaimResult.MatchingNetworkDenied -> {
                        send(playerId, "command.claim.matching-network-denied")
                        plugin.logger.warning(
                            "拒絕網路雜湊相符的 Claim：inviter=${result.inviterId}，invitee=${result.inviteeId}。",
                        )
                    }
                    is InvitationClaimResult.InviteCodeUnavailable -> send(
                        playerId,
                        "command.claim.unavailable",
                    )
                    is InvitationClaimResult.Rejected -> sendEligibility(playerId, result.eligibility)
                }
            },
        )
    }

    private fun cancel(player: Player) {
        send(
            player.uniqueId,
            if (sessions.cancel(player.uniqueId)) "command.claim.cancelled" else "command.claim.nothing-to-cancel",
        )
    }

    private fun status(player: Player) {
        val playerId = player.uniqueId
        val snapshot = configuration.current()
        submit(
            playerId,
            operation = { store ->
                val state = store.claimState(playerId)
                PlayerStatus(
                    eligibility = ClaimEligibilityPolicy(snapshot.value.general.claim.window).evaluate(state, now()),
                    state = state,
                    invitationStatus = store.invitationsByInvitee(playerId).firstOrNull()?.status,
                    pendingRewards = store.pendingRewardActions(playerId).size,
                )
            },
            onSuccess = { result ->
                send(
                    playerId,
                    "command.status.summary",
                    mapOf(
                        "eligible" to resolve(
                            playerId,
                            if (result.eligibility.eligible) "value.yes" else "value.no",
                        ),
                        "invitation" to resolve(
                            playerId,
                            "value.invitation.${result.invitationStatus?.name?.lowercase() ?: "none"}",
                        ),
                        "pending_rewards" to result.pendingRewards.toString(),
                    ),
                )
                if (!result.eligibility.eligible) sendEligibility(playerId, result.eligibility)
            },
        )
    }

    private fun stats(player: Player) {
        val playerId = player.uniqueId
        submit(
            playerId,
            operation = { store -> store.inviterInvitationStats(playerId) },
            onSuccess = { result ->
                send(
                    playerId,
                    "command.stats.summary",
                    mapOf("claimed" to result.claimed.toString(), "qualified" to result.qualified.toString()),
                )
            },
        )
    }

    private fun sendEligibility(playerId: UUID, eligibility: ClaimEligibility) {
        val reasons = eligibility.reasons.joinToString("、") {
            resolve(playerId, "command.claim.reason.${it.name.lowercase()}")
        }
        send(playerId, "command.claim.ineligible", mapOf("reasons" to reasons))
    }

    private fun evaluateQualificationsAndPendingRewards(
        store: InviteCodeStorage,
        playerId: UUID,
        playerName: String,
        playTime: Duration,
        evaluatedAt: Instant,
        runtime: InviteCodeRuntimeConfiguration,
        externalInputs: QualificationInputs,
    ): List<PendingRewardTask> {
        val evaluator = QualificationEvaluator(
            QualificationProviderRegistry(
                BuiltInQualificationProviders.create(
                    PermissionQualificationLookup { requestedPlayer, permission ->
                        externalInputs.permissions[permission].takeIf { requestedPlayer == playerId }
                    },
                    PlaceholderQualificationLookup { requestedPlayer, placeholder ->
                        externalInputs.placeholders[placeholder].takeIf { requestedPlayer == playerId }
                    },
                ),
                fallback = externalQualificationProviders,
            ),
        )
        val service = QualificationRewardService(store, evaluator)
        store.invitationsByInvitee(playerId)
            .filter { it.status == InvitationStatus.CLAIMED }
            .forEach { invitation ->
                for (policy in runtime.qualifications.policies.toSortedMap().values) {
                    val result = service.reevaluate(
                        invitation.id,
                        policy,
                        QualificationConditionContext(
                            invitationId = invitation.id,
                            inviterId = invitation.inviterId,
                            inviteeId = invitation.inviteeId,
                            claimedAt = invitation.claimedAt,
                            evaluatedAt = evaluatedAt,
                            playTime = playTime,
                            loginDays = store.loginDays(playerId),
                        ),
                        runtime.rewards,
                    )
                    if (result.qualification.status == QualificationEvaluationStatus.QUALIFIED) break
                }
            }
        return store.pendingRewardActions(playerId).map { action ->
            val invitation = action.invitationId?.let(store::invitation)
            val inviterId = invitation?.inviterId ?: action.recipientId.takeIf { action.invitationId == null }
            PendingRewardTask(
                action,
                RewardActionExecutionContext(
                    recipientId = action.recipientId,
                    recipientName = playerName,
                    inviterId = inviterId,
                    inviterName = inviterId?.let { store.player(it)?.lastKnownName },
                    inviteeId = invitation?.inviteeId,
                    inviteeName = invitation?.inviteeId?.let { store.player(it)?.lastKnownName },
                ),
            )
        }
    }

    private fun submitQualificationEvaluation(
        invitationId: UUID,
        playTime: Duration?,
        runtime: InviteCodeRuntimeConfiguration,
        externalInputs: QualificationInputs,
        future: CompletableFuture<QualificationEvaluationSnapshot>,
    ) {
        val evaluatedAt = now()
        val accepted = operations.submit(
            operation = {
                val store = requireNotNull(storage.get()) { "SQLite storage is unavailable." }
                val invitation = requireNotNull(store.invitation(invitationId)) {
                    "Invitation $invitationId does not exist."
                }
                val evaluator = QualificationEvaluator(
                    QualificationProviderRegistry(
                        BuiltInQualificationProviders.create(
                            PermissionQualificationLookup { playerId, permission ->
                                externalInputs.permissions[permission]
                                    .takeIf { playerId == invitation.inviteeId }
                            },
                            PlaceholderQualificationLookup { playerId, placeholder ->
                                externalInputs.placeholders[placeholder]
                                    .takeIf { playerId == invitation.inviteeId }
                            },
                        ),
                        fallback = externalQualificationProviders,
                    ),
                )
                var current = invitation
                if (current.status == InvitationStatus.CLAIMED) {
                    val service = QualificationRewardService(store, evaluator)
                    for (policy in runtime.qualifications.policies.toSortedMap().values) {
                        val result = service.reevaluate(
                            current.id,
                            policy,
                            QualificationConditionContext(
                                current.id,
                                current.inviterId,
                                current.inviteeId,
                                current.claimedAt,
                                evaluatedAt,
                                playTime,
                                store.loginDays(current.inviteeId),
                            ),
                            runtime.rewards,
                        )
                        current = result.qualification.invitation
                        if (current.status == InvitationStatus.QUALIFIED) break
                    }
                }
                val rewards = store.pendingRewardActions(current.inviteeId).map { action ->
                    PendingRewardTask(
                        action,
                        RewardActionExecutionContext(
                            action.recipientId,
                            store.player(action.recipientId)?.lastKnownName,
                            current.inviterId,
                            store.player(current.inviterId)?.lastKnownName,
                            current.inviteeId,
                            store.player(current.inviteeId)?.lastKnownName,
                        ),
                    )
                }
                QualificationApiResult(
                    QualificationEvaluationSnapshot(
                        current.id,
                        current.status,
                        current.qualificationPolicyId,
                        current.qualificationRevision,
                        evaluatedAt,
                    ),
                    rewards,
                )
            },
            onSuccess = { result ->
                deliverRewards(result.pendingRewards)
                future.complete(result.snapshot)
            },
            onFailure = future::completeExceptionally,
        )
        if (!accepted) future.completeExceptionally(IllegalStateException("InviteCode is stopping."))
    }

    private fun captureQualificationInputs(
        player: Player,
        runtime: InviteCodeRuntimeConfiguration,
    ): QualificationInputs {
        val conditions = runtime.qualifications.policies.values.flatMap { policy ->
            policy.anyOf.flatMap { group -> group.allOf }
        }
        val permissions = conditions
            .filter { it.type.equals("PERMISSION", ignoreCase = true) }
            .mapNotNull { it.parameters["permission"] }
            .distinct()
            .associateWith(player::hasPermission)
        val placeholders = conditions
            .filter { it.type.equals("PLACEHOLDER", ignoreCase = true) }
            .mapNotNull { it.parameters["placeholder"] }
            .distinct()
            .associateWith { placeholder -> resolvePlaceholder(player, placeholder) }
        return QualificationInputs(permissions, placeholders)
    }

    private fun resolvePlaceholder(player: Player, placeholder: String): String? {
        if (plugin.server.pluginManager.getPlugin("PlaceholderAPI")?.isEnabled != true) return null
        return try {
            val type = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            val method = type.getMethod("setPlaceholders", OfflinePlayer::class.java, String::class.java)
            method.invoke(null, player, placeholder) as? String
        } catch (error: ReflectiveOperationException) {
            plugin.logger.log(Level.WARNING, "PlaceholderAPI 呼叫失敗，Qualification 將回報 UNKNOWN。", error)
            null
        }
    }

    private fun deliverRewards(tasks: List<PendingRewardTask>) {
        if (tasks.isEmpty()) return
        val store = storage.get() ?: return
        val messages = configuration.current().value.messages
        val economy = plugin.server.servicesManager.getRegistration(Economy::class.java)?.provider
        val environment = BukkitRewardExecutionEnvironment(
            plugin.server,
            RewardMessageResolver { playerId, localeKey ->
                val locale = plugin.server.getPlayer(playerId)?.let(BukkitLocaleCompatibility::locale)
                messages.resolve(locale, localeKey)
            },
            economy?.let { provider ->
                EconomyRewardGateway { playerId, amount ->
                    provider.depositPlayer(plugin.server.getOfflinePlayer(playerId), amount.toDouble())
                        .transactionSuccess()
                }
            },
        )
        val workflow = AsyncRewardDeliveryWorkflow(
            operations,
            CoordinatorRewardActionStateStore(RewardDeliveryCoordinator(store)),
            StandardRewardActionExecutor(environment),
        )
        tasks.forEach { task ->
            val accepted = workflow.process(
                task.action,
                task.context,
                onComplete = {},
                onFailure = { error ->
                    plugin.logger.log(Level.WARNING, "InviteCode Reward Action ${task.action.id} 處理失敗。", error)
                },
            )
            if (!accepted) return
        }
    }

    private fun <T> submit(
        playerId: UUID,
        operation: (InviteCodeStorage) -> T,
        onSuccess: (T) -> Unit,
    ) {
        val accepted = operations.submit(
            operation = { operation(requireNotNull(storage.get()) { "SQLite storage is unavailable." }) },
            onSuccess = onSuccess,
            onFailure = { error ->
                send(playerId, "command.error")
                plugin.logger.log(Level.WARNING, "InviteCode 玩家操作失敗。", error)
            },
        )
        if (!accepted) send(playerId, "command.stopping")
    }

    private fun send(playerId: UUID, key: String, variables: Map<String, String> = emptyMap()) {
        val player = plugin.server.getPlayer(playerId) ?: return
        val messages = configuration.currentOrNull()?.value?.messages
        if (messages == null) {
            player.sendMessage("InviteCode 設定尚未載入。")
            return
        }
        player.sendMessage(messages.resolve(BukkitLocaleCompatibility.locale(player), key, variables))
    }

    private fun resolve(playerId: UUID, key: String): String {
        val player = plugin.server.getPlayer(playerId) ?: return "[offline]"
        return configuration.current().value.messages.resolve(BukkitLocaleCompatibility.locale(player), key)
    }

    private fun display(code: InviteCode): String {
        val legacy = code.color?.nearestLegacyColor() ?: return code.displayValue()
        return "${ChatColor.COLOR_CHAR}${legacy.code}${code.displayValue()}${ChatColor.RESET}"
    }

    private fun scheduleTimeout(duration: Duration, operation: () -> Unit) {
        val ticks = ((duration.toMillis() + 49L) / 50L).coerceAtLeast(1L)
        plugin.server.scheduler.runTaskLater(plugin, Runnable(operation), ticks)
    }

    private sealed class PreparedClaim {
        object Invalid : PreparedClaim()
        object Unavailable : PreparedClaim()
        object Self : PreparedClaim()
        data class Ready(val confirmation: ClaimConfirmation) : PreparedClaim()
    }

    private data class PlayerStatus(
        val eligibility: ClaimEligibility,
        val state: PlayerClaimState,
        val invitationStatus: InvitationStatus?,
        val pendingRewards: Int,
    )

    private data class PendingRewardTask(
        val action: StoredRewardAction,
        val context: RewardActionExecutionContext,
    )

    private data class QualificationInputs(
        val permissions: Map<String, Boolean>,
        val placeholders: Map<String, String?>,
    )

    private data class QualificationApiResult(
        val snapshot: QualificationEvaluationSnapshot,
        val pendingRewards: List<PendingRewardTask>,
    )
}
