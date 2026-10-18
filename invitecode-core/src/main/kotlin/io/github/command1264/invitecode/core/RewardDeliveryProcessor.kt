package io.github.command1264.invitecode.core

import java.time.Instant
import java.util.UUID

data class RewardActionExecutionContext(
    val recipientId: UUID,
    val recipientName: String?,
    val inviterId: UUID?,
    val inviterName: String?,
    val inviteeId: UUID?,
    val inviteeName: String?,
) {
    init {
        require(recipientName == null || recipientName.isNotBlank()) { "Recipient name must not be blank." }
        require(inviterName == null || inviterName.isNotBlank()) { "Inviter name must not be blank." }
        require(inviteeName == null || inviteeName.isNotBlank()) { "Invitee name must not be blank." }
    }
}

enum class RewardActionReadiness {
    READY,
    RECIPIENT_OFFLINE,
    INTEGRATION_UNAVAILABLE,
}

sealed class RewardActionExecutionResult {
    object Delivered : RewardActionExecutionResult()

    data class Deferred(val readiness: RewardActionReadiness) : RewardActionExecutionResult() {
        init {
            require(readiness != RewardActionReadiness.READY) {
                "A deferred Reward Action must explain why it is not ready."
            }
        }
    }

    data class Failed(val reason: String) : RewardActionExecutionResult() {
        init {
            require(reason.isNotBlank()) { "Reward Action failure reason must not be blank." }
            require(reason.length <= 512) { "Reward Action failure reason must not exceed 512 characters." }
            require(reason.none { it == '\r' || it == '\n' }) {
                "Reward Action failure reason must not contain line breaks."
            }
        }
    }
}

interface RewardActionExecutor {
    fun readiness(
        action: StoredRewardAction,
        context: RewardActionExecutionContext,
    ): RewardActionReadiness

    fun execute(
        action: StoredRewardAction,
        context: RewardActionExecutionContext,
    ): RewardActionExecutionResult
}

class RewardDeliveryCoordinator(
    private val records: RewardRecords,
) {
    fun prepare(plan: RewardDeliveryPlan): RewardDeliveryPlan {
        val existing = records.rewardDelivery(plan.delivery.id)
        if (existing != null) {
            require(existing.idempotencyKey == plan.delivery.idempotencyKey) {
                "Reward Delivery ${plan.delivery.id} has an unexpected idempotency key."
            }
            return RewardDeliveryPlan(existing, records.rewardActions(existing.id))
        }
        records.saveRewardDelivery(plan.delivery, plan.actions)
        return plan
    }

    fun action(actionId: UUID): StoredRewardAction? = records.rewardAction(actionId)

    fun pendingForRecipient(recipientId: UUID): List<StoredRewardAction> =
        records.pendingRewardActions(recipientId)

    fun begin(actionId: UUID, occurredAt: Instant): StoredRewardAction = transition(
        actionId,
        requiredStatus = RewardActionStatus.PENDING,
        nextStatus = RewardActionStatus.PROCESSING,
        occurredAt = occurredAt,
        lastError = null,
    )

    fun delivered(actionId: UUID, occurredAt: Instant): StoredRewardAction = transition(
        actionId,
        requiredStatus = RewardActionStatus.PROCESSING,
        nextStatus = RewardActionStatus.DELIVERED,
        occurredAt = occurredAt,
        lastError = null,
    )

    fun failed(actionId: UUID, reason: String, occurredAt: Instant): StoredRewardAction = transition(
        actionId,
        requiredStatus = RewardActionStatus.PROCESSING,
        nextStatus = RewardActionStatus.FAILED,
        occurredAt = occurredAt,
        lastError = reason,
    )

    fun deferred(actionId: UUID, occurredAt: Instant): StoredRewardAction = transition(
        actionId,
        requiredStatus = RewardActionStatus.PROCESSING,
        nextStatus = RewardActionStatus.PENDING,
        occurredAt = occurredAt,
        lastError = null,
    )

    fun uncertain(actionId: UUID, reason: String, occurredAt: Instant): StoredRewardAction = transition(
        actionId,
        requiredStatus = RewardActionStatus.PROCESSING,
        nextStatus = RewardActionStatus.UNCERTAIN,
        occurredAt = occurredAt,
        lastError = reason,
    )

    fun recoverInterrupted(recoveredAt: Instant): Int =
        records.recoverInterruptedRewardActions(recoveredAt)

    fun resolveUncertain(
        actionId: UUID,
        resolution: UncertainRewardActionResolution,
        context: RewardAdministrationContext,
    ): StoredRewardAction = records.resolveUncertainRewardAction(actionId, resolution, context)

    private fun transition(
        actionId: UUID,
        requiredStatus: RewardActionStatus,
        nextStatus: RewardActionStatus,
        occurredAt: Instant,
        lastError: String?,
    ): StoredRewardAction {
        val current = requireNotNull(records.rewardAction(actionId)) { "Reward Action $actionId does not exist." }
        check(current.status == requiredStatus) {
            "Reward Action $actionId must be $requiredStatus\ncurrent status is ${current.status}."
        }
        val transitionAt = if (occurredAt.isAfter(current.updatedAt)) {
            occurredAt
        } else {
            current.updatedAt.plusNanos(1)
        }
        val updated = current.copy(
            status = nextStatus,
            updatedAt = transitionAt,
            lastError = lastError,
        )
        records.saveRewardAction(updated)
        return updated
    }
}

enum class RewardActionProcessingStatus {
    DELIVERED,
    FAILED,
    DEFERRED,
    UNCERTAIN,
    ALREADY_PROCESSING,
    ALREADY_TERMINAL,
}

data class RewardActionProcessingResult(
    val status: RewardActionProcessingStatus,
    val action: StoredRewardAction,
    val readiness: RewardActionReadiness? = null,
)

class RewardDeliveryProcessor(
    private val coordinator: RewardDeliveryCoordinator,
    private val executor: RewardActionExecutor,
    private val now: () -> Instant = Instant::now,
) {
    fun process(
        requested: StoredRewardAction,
        context: RewardActionExecutionContext,
    ): RewardActionProcessingResult {
        require(requested.recipientId == context.recipientId) {
            "Reward Action recipient does not match execution context."
        }
        val current = requireNotNull(coordinator.action(requested.id)) {
            "Reward Action ${requested.id} does not exist."
        }
        when (current.status) {
            RewardActionStatus.PROCESSING -> return result(RewardActionProcessingStatus.ALREADY_PROCESSING, current)
            RewardActionStatus.DELIVERED,
            RewardActionStatus.FAILED,
            RewardActionStatus.UNCERTAIN,
            RewardActionStatus.EXPIRED -> return result(RewardActionProcessingStatus.ALREADY_TERMINAL, current)
            RewardActionStatus.PENDING -> Unit
        }
        val readiness = executor.readiness(current, context)
        if (readiness != RewardActionReadiness.READY) {
            return RewardActionProcessingResult(RewardActionProcessingStatus.DEFERRED, current, readiness)
        }

        val processing = coordinator.begin(current.id, now())
        val execution = try {
            executor.execute(processing, context)
        } catch (error: Exception) {
            val uncertain = coordinator.uncertain(
                processing.id,
                "External Reward Action execution threw ${error.javaClass.simpleName}\nresult is unknown.",
                now(),
            )
            return result(RewardActionProcessingStatus.UNCERTAIN, uncertain)
        }
        return when (execution) {
            RewardActionExecutionResult.Delivered -> result(
                RewardActionProcessingStatus.DELIVERED,
                coordinator.delivered(processing.id, now()),
            )
            is RewardActionExecutionResult.Deferred -> RewardActionProcessingResult(
                RewardActionProcessingStatus.DEFERRED,
                coordinator.deferred(processing.id, now()),
                execution.readiness,
            )
            is RewardActionExecutionResult.Failed -> result(
                RewardActionProcessingStatus.FAILED,
                coordinator.failed(processing.id, execution.reason, now()),
            )
        }
    }

    private fun result(status: RewardActionProcessingStatus, action: StoredRewardAction) =
        RewardActionProcessingResult(status, action)
}
