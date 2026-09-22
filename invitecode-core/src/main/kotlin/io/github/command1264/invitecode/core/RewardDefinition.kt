package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

enum class RewardActionType {
    CONSOLE_COMMAND,
    PLAYER_COMMAND,
    MESSAGE,
    VAULT_ECONOMY,
}

data class RewardActionDefinition(
    val type: RewardActionType,
    val payload: String,
) {
    init {
        require(payload.isNotBlank()) { "Reward Action payload must not be blank." }
    }
}

data class QualificationRewardDefinition(
    val id: String,
    val inviterActions: List<RewardActionDefinition>,
    val inviteeActions: List<RewardActionDefinition>,
) {
    init {
        require(id.isNotBlank()) { "Reward ID must not be blank." }
        require(inviterActions.isNotEmpty() || inviteeActions.isNotEmpty()) {
            "Qualification Reward $id must contain at least one action."
        }
    }
}

data class MilestoneRewardDefinition(
    val id: String,
    val qualifiedInvitations: Int,
    val inviterActions: List<RewardActionDefinition>,
) {
    init {
        require(id.isNotBlank()) { "Milestone Reward ID must not be blank." }
        require(qualifiedInvitations > 0) { "Milestone Reward threshold must be positive." }
        require(inviterActions.isNotEmpty()) { "Milestone Reward $id must contain at least one Inviter action." }
    }
}

data class RewardConfiguration(
    val version: Int,
    val qualificationRewards: Map<String, QualificationRewardDefinition>,
    val milestoneRewards: Map<String, MilestoneRewardDefinition>,
) {
    init {
        require(qualificationRewards.isNotEmpty() || milestoneRewards.isNotEmpty()) {
            "Reward configuration must contain at least one reward."
        }
    }
}

object RewardConfigurationParser {
    const val CURRENT_VERSION = 1

    fun parse(value: Map<String, Any?>): RewardConfiguration {
        require(value.keys == setOf("config-version", "qualification-rewards", "milestone-rewards")) {
            "Reward configuration must contain only config-version, qualification-rewards, and milestone-rewards."
        }
        val version = (value["config-version"] as? Number)?.toString()?.toIntOrNull()
            ?: throw IllegalArgumentException("Reward configuration requires numeric integer config-version.")
        require(version == CURRENT_VERSION) {
            "Reward config-version $version is unsupported; expected $CURRENT_VERSION."
        }
        val milestoneRewards = value["milestone-rewards"].requiredStringMap("milestone-rewards")
            .mapValues { (rewardId, rawReward) -> parseMilestoneReward(rewardId, rawReward) }
        val rewards = value["qualification-rewards"].requiredStringMap("qualification-rewards")
            .mapValues { (rewardId, rawReward) -> parseQualificationReward(rewardId, rawReward) }
        return RewardConfiguration(version, rewards, milestoneRewards)
    }

    private fun parseMilestoneReward(
        rewardId: String,
        rawReward: Any?,
    ): MilestoneRewardDefinition {
        val reward = rawReward.requiredStringMap("Milestone Reward $rewardId")
        require(reward.keys == setOf("at", "inviter")) {
            "Milestone Reward $rewardId must contain only at and inviter."
        }
        val threshold = (reward["at"] as? Number)?.toString()?.toIntOrNull()
            ?: throw IllegalArgumentException("Milestone Reward $rewardId requires integer at.")
        return MilestoneRewardDefinition(
            id = rewardId,
            qualifiedInvitations = threshold,
            inviterActions = reward["inviter"].requiredList("Milestone Reward $rewardId inviter")
                .mapIndexed { index, action -> parseAction(action, "$rewardId inviter action ${index + 1}") },
        )
    }

    private fun parseQualificationReward(
        rewardId: String,
        rawReward: Any?,
    ): QualificationRewardDefinition {
        val reward = rawReward.requiredStringMap("Qualification Reward $rewardId")
        require(reward.keys == setOf("inviter", "invitee")) {
            "Qualification Reward $rewardId must contain only inviter and invitee."
        }
        return QualificationRewardDefinition(
            id = rewardId,
            inviterActions = reward["inviter"].requiredList("Qualification Reward $rewardId inviter")
                .mapIndexed { index, action -> parseAction(action, "$rewardId inviter action ${index + 1}") },
            inviteeActions = reward["invitee"].requiredList("Qualification Reward $rewardId invitee")
                .mapIndexed { index, action -> parseAction(action, "$rewardId invitee action ${index + 1}") },
        )
    }

    private fun parseAction(rawAction: Any?, location: String): RewardActionDefinition {
        val action = rawAction.requiredStringMap(location)
        val rawType = action["type"] as? String
            ?: throw IllegalArgumentException("$location requires string type.")
        val type = try {
            RewardActionType.valueOf(rawType.uppercase(Locale.ROOT))
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("$location has unsupported type '$rawType'.", error)
        }
        val payloadKey = when (type) {
            RewardActionType.MESSAGE -> "message"
            RewardActionType.CONSOLE_COMMAND,
            RewardActionType.PLAYER_COMMAND -> "command"
            RewardActionType.VAULT_ECONOMY -> "amount"
        }
        require(action.keys == setOf("type", payloadKey)) {
            "$location type $type must contain only type and $payloadKey."
        }
        val rawPayload = action[payloadKey]
            ?: throw IllegalArgumentException("$location requires $payloadKey.")
        val payload = when (type) {
            RewardActionType.MESSAGE -> parseMessageKey(rawPayload, location)
            RewardActionType.CONSOLE_COMMAND,
            RewardActionType.PLAYER_COMMAND -> parseCommand(rawPayload, location)
            RewardActionType.VAULT_ECONOMY -> parseAmount(rawPayload, location)
        }
        return RewardActionDefinition(type, payload)
    }

    private fun parseMessageKey(rawValue: Any?, location: String): String {
        val value = rawValue as? String
            ?: throw IllegalArgumentException("$location message must be a string locale key.")
        require(MESSAGE_KEY.matches(value)) { "$location message '$value' is not a valid locale key." }
        return value
    }

    private fun parseCommand(rawValue: Any?, location: String): String {
        val value = rawValue as? String
            ?: throw IllegalArgumentException("$location command must be a string.")
        require(value.isNotBlank()) { "$location command must not be blank." }
        require(!value.startsWith("/")) { "$location command must not start with '/'." }
        require(value.none { it == '\n' || it == '\r' || it.code < 0x20 }) {
            "$location command must not contain control characters."
        }
        val placeholders = PLACEHOLDER.findAll(value).map { it.groupValues[1] }.toSet()
        require(placeholders.all { it in ALLOWED_PLACEHOLDERS }) {
            "$location command contains an unsupported placeholder."
        }
        require(value.replace(PLACEHOLDER, "").none { it == '{' || it == '}' }) {
            "$location command contains malformed placeholder braces."
        }
        return value
    }

    private fun parseAmount(rawValue: Any?, location: String): String {
        val amount = try {
            BigDecimal(rawValue.toString())
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$location amount must be numeric.", error)
        }
        require(amount > BigDecimal.ZERO) { "$location amount must be greater than zero." }
        return amount.stripTrailingZeros().toPlainString()
    }

    private fun Any?.requiredStringMap(location: String): Map<String, Any?> {
        require(this is Map<*, *> && keys.all { it is String }) { "$location must be a map with string keys." }
        @Suppress("UNCHECKED_CAST")
        return this as Map<String, Any?>
    }

    private fun Any?.requiredList(location: String): List<Any?> {
        require(this is List<*>) { "$location must be a list." }
        return this
    }

    private val MESSAGE_KEY = Regex("^[a-z0-9][a-z0-9._-]*$")
    private val PLACEHOLDER = Regex("\\{([a-z_]+)}")
    private val ALLOWED_PLACEHOLDERS = setOf(
        "recipient_uuid",
        "recipient_name",
        "inviter_uuid",
        "inviter_name",
        "invitee_uuid",
        "invitee_name",
    )
}

data class RewardDeliveryPlan(
    val delivery: StoredRewardDelivery,
    val actions: List<StoredRewardAction>,
) {
    init {
        require(actions.isNotEmpty()) { "Reward Delivery plan must contain at least one action." }
        require(actions.all { it.rewardDeliveryId == delivery.id }) {
            "Every planned Reward Action must belong to Reward Delivery ${delivery.id}."
        }
    }
}

object RewardDeliveryPlanner {
    fun planQualification(
        definition: QualificationRewardDefinition,
        invitation: Invitation,
    ): RewardDeliveryPlan {
        require(invitation.status == InvitationStatus.QUALIFIED) {
            "Qualification Reward requires a Qualified Invitation."
        }
        val occurredAt = requireNotNull(invitation.qualifiedAt) {
            "Qualified Invitation requires qualifiedAt."
        }
        val deliveryKey = "qualification:${invitation.id}:${definition.id}"
        val deliveryId = stableUuid("delivery:$deliveryKey")
        val planned = buildList {
            definition.inviterActions.forEachIndexed { index, action ->
                add(PlannedAction("inviter", index, invitation.inviterId, action))
            }
            definition.inviteeActions.forEachIndexed { index, action ->
                add(PlannedAction("invitee", index, invitation.inviteeId, action))
            }
        }
        val actions = planned.mapIndexed { position, plannedAction ->
            val actionKey = "$deliveryKey:${plannedAction.role}:${plannedAction.rolePosition}"
            StoredRewardAction(
                id = stableUuid("action:$actionKey"),
                rewardDeliveryId = deliveryId,
                invitationId = invitation.id,
                recipientId = plannedAction.recipientId,
                position = position,
                type = plannedAction.definition.type.name,
                payload = plannedAction.definition.payload,
                status = RewardActionStatus.PENDING,
                idempotencyKey = actionKey,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            )
        }
        return RewardDeliveryPlan(
            delivery = StoredRewardDelivery(
                id = deliveryId,
                invitationId = invitation.id,
                rewardId = definition.id,
                status = RewardDeliveryStatus.PENDING,
                idempotencyKey = deliveryKey,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            ),
            actions = actions,
        )
    }

    fun planMilestone(
        definition: MilestoneRewardDefinition,
        inviterId: UUID,
        achievedAt: java.time.Instant,
    ): RewardDeliveryPlan {
        val deliveryKey = "milestone:$inviterId:${definition.qualifiedInvitations}:${definition.id}"
        val deliveryId = stableUuid("delivery:$deliveryKey")
        val actions = definition.inviterActions.mapIndexed { position, action ->
            val actionKey = "$deliveryKey:inviter:$position"
            StoredRewardAction(
                id = stableUuid("action:$actionKey"),
                rewardDeliveryId = deliveryId,
                invitationId = null,
                recipientId = inviterId,
                position = position,
                type = action.type.name,
                payload = action.payload,
                status = RewardActionStatus.PENDING,
                idempotencyKey = actionKey,
                createdAt = achievedAt,
                updatedAt = achievedAt,
            )
        }
        return RewardDeliveryPlan(
            StoredRewardDelivery(
                id = deliveryId,
                invitationId = null,
                rewardId = definition.id,
                status = RewardDeliveryStatus.PENDING,
                idempotencyKey = deliveryKey,
                createdAt = achievedAt,
                updatedAt = achievedAt,
            ),
            actions,
        )
    }

    private fun stableUuid(value: String): UUID =
        UUID.nameUUIDFromBytes("InviteCodeKt:$value".toByteArray(StandardCharsets.UTF_8))

    private data class PlannedAction(
        val role: String,
        val rolePosition: Int,
        val recipientId: UUID,
        val definition: RewardActionDefinition,
    )
}
