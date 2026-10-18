package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.AuditRecord
import io.github.command1264.invitecode.core.InvitationAdministrationContext
import io.github.command1264.invitecode.core.InvitationAuditAction
import io.github.command1264.invitecode.core.InviteCodeLifecycleService
import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.PlayerReferenceResolution
import io.github.command1264.invitecode.core.PlayerReferenceResolver
import io.github.command1264.invitecode.core.RewardAdministrationContext
import io.github.command1264.invitecode.core.RewardDeliveryPlan
import io.github.command1264.invitecode.core.RewardDeliveryPlanner
import io.github.command1264.invitecode.core.UncertainRewardActionResolution
import io.github.command1264.invitecode.platform.bukkit.AsyncOperationExecutor
import io.github.command1264.invitecode.platform.bukkit.AtomicRuntimeSnapshot
import io.github.command1264.invitecode.platform.bukkit.InviteCodeCommandId
import io.github.command1264.invitecode.platform.bukkit.InviteCodeRuntimeConfiguration
import io.github.command1264.invitecode.platform.bukkit.generator
import io.github.command1264.invitecode.platform.bukkit.ParsedInviteCodeCommand
import io.github.command1264.invitecode.platform.bukkit.PlatformTaskDispatcher
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level

class AdminWorkflowController(
    private val plugin: Plugin,
    private val configuration: AtomicRuntimeSnapshot<InviteCodeRuntimeConfiguration>,
    private val storage: AtomicReference<InviteCodeStorage?>,
    private val operations: AsyncOperationExecutor,
    private val tasks: PlatformTaskDispatcher,
    private val refreshPlayer: (UUID) -> Unit,
    private val now: () -> Instant = Instant::now,
) {
    fun handle(sender: CommandSender, request: ParsedInviteCodeCommand): Boolean {
        val actor = sender.name
        val reason = request.reason
        return when (request.id) {
            InviteCodeCommandId.ADMIN_INSPECT_PLAYER -> inspectPlayer(sender, request.operands.single())
            InviteCodeCommandId.ADMIN_INSPECT_INVITATION -> inspectInvitation(sender, request.operands.single())
            InviteCodeCommandId.ADMIN_INSPECT_REWARD -> inspectReward(sender, request.operands.single())
            InviteCodeCommandId.ADMIN_INVITATION_UNLINK -> mutate(sender) { store ->
                val invitation = requireInvitation(store, request.operands.single())
                store.saveAuditedInvitationTransition(invitation.adminUnlink(context(actor, reason)))
                AdminMessage("admin.invitation-unlinked", mapOf("invitation" to invitation.id.toString()))
            }
            InviteCodeCommandId.ADMIN_INVITATION_TRANSFER -> mutate(sender) { store ->
                val invitation = requireInvitation(store, request.operands[0])
                val inviter = resolvePlayer(store, request.operands[1])
                store.saveAuditedInvitationTransition(invitation.adminTransfer(inviter, context(actor, reason)))
                AdminMessage(
                    "admin.invitation-transferred",
                    mapOf("invitation" to invitation.id.toString(), "inviter" to inviter.toString()),
                )
            }
            InviteCodeCommandId.ADMIN_INVITATION_FORCE_QUALIFY -> forceQualify(sender, request, actor)
            InviteCodeCommandId.ADMIN_ELIGIBILITY_RESET -> mutate(sender) { store ->
                val playerId = resolvePlayer(store, request.operands.single())
                val transition = store.claimState(playerId).adminResetEligibility(context(actor, reason))
                store.saveAuditedClaimStateTransition(transition)
                AdminMessage("admin.eligibility-reset", mapOf("player" to playerId.toString()))
            }
            InviteCodeCommandId.ADMIN_REWARD_RETRY -> resolveReward(
                sender, request, actor, UncertainRewardActionResolution.RETRY,
            )
            InviteCodeCommandId.ADMIN_REWARD_MARK_DELIVERED -> resolveReward(
                sender, request, actor, UncertainRewardActionResolution.MARK_DELIVERED,
            )
            InviteCodeCommandId.ADMIN_CODE_ROTATE -> rotateCode(sender, request, actor)
            else -> false
        }
    }

    private fun inspectPlayer(sender: CommandSender, reference: String): Boolean = query(sender, "admin.inspect-player-title") { store ->
        val playerId = resolvePlayer(store, reference)
        val state = store.claimState(playerId)
        val code = store.activeInviteCode(playerId)?.code?.displayValue() ?: "NONE"
        val stats = store.inviterInvitationStats(playerId)
        AdminMessage("admin.inspect-player", mapOf(
            "player" to playerId.toString(), "name" to (store.player(playerId)?.lastKnownName ?: "UNKNOWN"),
            "first_seen" to state.firstSeenAt.toString(), "inviter" to state.isInviter.toString(),
            "invitee" to state.hasInvitation.toString(), "code" to code,
            "claimed" to stats.claimed.toString(), "qualified" to stats.qualified.toString(),
            "audit" to store.auditHistoryByPlayer(playerId, 20).size.toString(),
        ))
    }

    private fun inspectInvitation(sender: CommandSender, reference: String): Boolean = query(sender, "admin.inspect-invitation-title") { store ->
        val id = parseUuid(reference, "Invitation ID")
        val invitation = requireNotNull(store.invitation(id)) { "Invitation $id 不存在。" }
        AdminMessage("admin.inspect-invitation", mapOf(
            "invitation" to invitation.id.toString(), "status" to invitation.status.name,
            "inviter" to invitation.inviterId.toString(), "invitee" to invitation.inviteeId.toString(),
            "claimed_at" to invitation.claimedAt.toString(), "facts" to store.qualificationFacts(id).size.toString(),
            "rewards" to store.rewardDeliveries(id).size.toString(), "audit" to store.auditHistoryByInvitation(id, 20).size.toString(),
        ))
    }

    private fun inspectReward(sender: CommandSender, reference: String): Boolean = query(sender, "admin.inspect-reward-title") { store ->
        val id = parseUuid(reference, "Reward Action ID")
        val action = requireNotNull(store.rewardAction(id)) { "Reward Action $id 不存在。" }
        AdminMessage("admin.inspect-reward", mapOf(
            "action" to action.id.toString(), "delivery" to action.rewardDeliveryId.toString(),
            "status" to action.status.name, "recipient" to action.recipientId.toString(), "type" to action.type,
            "updated_at" to action.updatedAt.toString(), "last_error" to (action.lastError ?: "NONE"),
            "transitions" to store.rewardActionTransitions(id).size.toString(),
        ))
    }

    private fun forceQualify(
        sender: CommandSender,
        request: ParsedInviteCodeCommand,
        actor: String,
    ): Boolean {
        val runtime = configuration.current().value
        return mutate(sender, onSuccess = { result ->
            send(sender, result.message)
            result.players.forEach(refreshPlayer)
        }) { store ->
            val invitation = requireInvitation(store, request.operands[0])
            val policy = requireNotNull(runtime.qualifications.policies[request.operands[1]]) {
                "Qualification Policy ${request.operands[1]} 不存在。"
            }
            val transition = invitation.adminForceQualify(
                now(),
                policy.id,
                policy.revision,
                context(actor, request.reason),
            )
            val plans = rewardPlans(store, transition.invitation, runtime)
            store.saveForcedQualificationWithRewards(transition, plans)
            MutationResult(
                AdminMessage(
                    "admin.invitation-force-qualified",
                    mapOf(
                        "invitation" to invitation.id.toString(),
                        "rewards" to plans.size.toString(),
                    ),
                ),
                setOf(invitation.inviterId, invitation.inviteeId),
            )
        }
    }

    private fun rewardPlans(
        store: InviteCodeStorage,
        invitation: io.github.command1264.invitecode.core.Invitation,
        runtime: InviteCodeRuntimeConfiguration,
    ): List<RewardDeliveryPlan> {
        val qualification = runtime.rewards.qualificationRewards.toSortedMap().values.map {
            RewardDeliveryPlanner.planQualification(it, invitation)
        }
        val count = store.qualifiedInvitationCount(invitation.inviterId) + 1
        val achievedAt = requireNotNull(invitation.qualifiedAt)
        val milestones = runtime.rewards.milestoneRewards.toSortedMap().values
            .filter { it.qualifiedInvitations == count }
            .map { RewardDeliveryPlanner.planMilestone(it, invitation.inviterId, achievedAt) }
        return qualification + milestones
    }

    private fun resolveReward(
        sender: CommandSender,
        request: ParsedInviteCodeCommand,
        actor: String,
        resolution: UncertainRewardActionResolution,
    ): Boolean = mutate(
        sender,
        onSuccess = { result ->
            send(sender, result.message)
            if (resolution == UncertainRewardActionResolution.RETRY) result.players.forEach(refreshPlayer)
        },
    ) { store ->
        val id = parseUuid(request.operands.single(), "Reward Action ID")
        val result = store.resolveUncertainRewardAction(
            id,
            resolution,
            RewardAdministrationContext(actor, requireNotNull(request.reason), now()),
        )
        MutationResult(
            AdminMessage(
                "admin.reward-resolved",
                mapOf("action" to id.toString(), "status" to result.status.name),
            ),
            setOf(result.recipientId),
        )
    }

    private fun rotateCode(
        sender: CommandSender,
        request: ParsedInviteCodeCommand,
        actor: String,
    ): Boolean {
        val runtime = configuration.current().value
        return mutate(sender) { store ->
            val playerId = resolvePlayer(store, request.operands.single())
            val generator = runtime.general.inviteCode.format.generator()
            val rotatedAt = now()
            val auditRecord = AuditRecord(
                UUID.randomUUID(),
                InvitationAuditAction.CODE_ROTATED,
                actor,
                requireNotNull(request.reason),
                rotatedAt,
                playerId = playerId,
            )
            val rotation = InviteCodeLifecycleService(store, generator, 64)
                .rotate(playerId, rotatedAt, auditRecord)
            AdminMessage(
                "admin.code-rotated",
                mapOf(
                    "player" to playerId.toString(),
                    "previous" to rotation.previous.code.displayValue(),
                    "current" to rotation.current.code.displayValue(),
                ),
            )
        }
    }

    private fun <T> mutate(
        sender: CommandSender,
        onSuccess: (T) -> Unit = {
            if (it is AdminMessage) send(sender, it)
            else BukkitMessageOutput.sendLiteral(sender, configuration.current().value.messages, it.toString())
        },
        operation: (InviteCodeStorage) -> T,
    ): Boolean = submit(sender, operation, onSuccess)

    private fun query(sender: CommandSender, title: String, operation: (InviteCodeStorage) -> AdminMessage): Boolean =
        submit(sender, operation) { result ->
            BukkitMessageOutput.sendReport(sender, configuration.current().value.messages, title,
                listOf(result.key to result.variables))
        }

    private fun <T> submit(
        sender: CommandSender,
        operation: (InviteCodeStorage) -> T,
        onSuccess: (T) -> Unit,
    ): Boolean {
        val accepted = operations.callbacksOn { callback ->
            if (sender is Player) tasks.player(sender, operation = callback)
            else tasks.global(callback)
        }.submit(
            operation = { operation(requireNotNull(storage.get()) { "SQLite storage is unavailable." }) },
            onSuccess = onSuccess,
            onFailure = { error ->
                send(sender, AdminMessage("admin.error"))
                plugin.logger.log(Level.WARNING, "InviteCode 管理操作失敗。", error)
            },
        )
        if (!accepted) send(sender, AdminMessage("admin.stopping"))
        return true
    }

    private fun resolvePlayer(store: InviteCodeStorage, reference: String): UUID =
        when (val result = PlayerReferenceResolver(store).resolve(reference)) {
            is PlayerReferenceResolution.Found -> result.player.id
            PlayerReferenceResolution.Unknown -> throw IllegalArgumentException("找不到玩家：$reference")
            is PlayerReferenceResolution.Ambiguous -> throw IllegalArgumentException(
                "玩家名稱 $reference 對應多筆資料，請改用 UUID。",
            )
        }

    private fun requireInvitation(store: InviteCodeStorage, reference: String) =
        parseUuid(reference, "Invitation ID").let { id ->
            requireNotNull(store.invitation(id)) { "Invitation $id 不存在。" }
        }

    private fun context(actor: String, reason: String?) = InvitationAdministrationContext(
        UUID.randomUUID(),
        actor,
        requireNotNull(reason),
        now(),
    )

    private fun parseUuid(value: String, label: String): UUID = try {
        UUID.fromString(value)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("$label 必須是有效 UUID。", error)
    }

    private fun send(sender: CommandSender, message: AdminMessage) {
        BukkitMessageOutput.send(
            sender,
            configuration.current().value.messages,
            message.key,
            message.variables,
        )
    }

    private data class AdminMessage(
        val key: String,
        val variables: Map<String, String> = emptyMap(),
    )

    private data class MutationResult(val message: AdminMessage, val players: Set<UUID>)
}
