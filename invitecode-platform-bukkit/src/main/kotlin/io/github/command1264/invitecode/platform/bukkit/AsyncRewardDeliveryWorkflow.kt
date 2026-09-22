package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.RewardActionExecutionContext
import io.github.command1264.invitecode.core.RewardActionExecutionResult
import io.github.command1264.invitecode.core.RewardActionExecutor
import io.github.command1264.invitecode.core.RewardActionProcessingResult
import io.github.command1264.invitecode.core.RewardActionProcessingStatus
import io.github.command1264.invitecode.core.RewardActionReadiness
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.RewardDeliveryCoordinator
import io.github.command1264.invitecode.core.StoredRewardAction
import java.time.Instant
import java.util.UUID
import java.util.concurrent.RejectedExecutionException

interface RewardActionStateStore {
    fun action(actionId: UUID): StoredRewardAction?
    fun begin(actionId: UUID, occurredAt: Instant): StoredRewardAction
    fun delivered(actionId: UUID, occurredAt: Instant): StoredRewardAction
    fun failed(actionId: UUID, reason: String, occurredAt: Instant): StoredRewardAction
    fun deferred(actionId: UUID, occurredAt: Instant): StoredRewardAction
    fun uncertain(actionId: UUID, reason: String, occurredAt: Instant): StoredRewardAction
}

class CoordinatorRewardActionStateStore(
    private val coordinator: RewardDeliveryCoordinator,
) : RewardActionStateStore {
    override fun action(actionId: UUID) = coordinator.action(actionId)
    override fun begin(actionId: UUID, occurredAt: Instant) = coordinator.begin(actionId, occurredAt)
    override fun delivered(actionId: UUID, occurredAt: Instant) = coordinator.delivered(actionId, occurredAt)
    override fun failed(actionId: UUID, reason: String, occurredAt: Instant) =
        coordinator.failed(actionId, reason, occurredAt)
    override fun deferred(actionId: UUID, occurredAt: Instant) = coordinator.deferred(actionId, occurredAt)
    override fun uncertain(actionId: UUID, reason: String, occurredAt: Instant) =
        coordinator.uncertain(actionId, reason, occurredAt)
}

class AsyncRewardDeliveryWorkflow(
    private val operations: BackgroundOperationExecutor,
    private val store: RewardActionStateStore,
    private val executor: RewardActionExecutor,
    private val now: () -> Instant = Instant::now,
) {
    fun process(
        requested: StoredRewardAction,
        context: RewardActionExecutionContext,
        onComplete: (RewardActionProcessingResult) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): Boolean {
        require(requested.recipientId == context.recipientId) {
            "Reward Action recipient does not match execution context."
        }
        return operations.submit(
            operation = { requireAction(requested.id) },
            onSuccess = { current -> inspectReadiness(current, context, onComplete, onFailure) },
            onFailure = onFailure,
        )
    }

    private fun inspectReadiness(
        current: StoredRewardAction,
        context: RewardActionExecutionContext,
        onComplete: (RewardActionProcessingResult) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        existingResult(current)?.let {
            onComplete(it)
            return
        }
        val readiness = try {
            executor.readiness(current, context)
        } catch (error: Exception) {
            onFailure(error)
            return
        }
        if (readiness != RewardActionReadiness.READY) {
            onComplete(
                RewardActionProcessingResult(RewardActionProcessingStatus.DEFERRED, current, readiness),
            )
            return
        }
        submitNested(
            operation = {
                val latest = requireAction(current.id)
                existingResult(latest)?.let(BeginOutcome::Existing)
                    ?: BeginOutcome.Begun(store.begin(latest.id, now()))
            },
            onSuccess = { outcome ->
                when (outcome) {
                    is BeginOutcome.Existing -> onComplete(outcome.result)
                    is BeginOutcome.Begun -> executeExternal(outcome.action, context, onComplete, onFailure)
                }
            },
            onFailure = onFailure,
        )
    }

    private fun executeExternal(
        processing: StoredRewardAction,
        context: RewardActionExecutionContext,
        onComplete: (RewardActionProcessingResult) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val external = try {
            ExternalOutcome.Result(executor.execute(processing, context))
        } catch (error: Exception) {
            ExternalOutcome.Unknown(error.javaClass.simpleName)
        }
        submitNested(
            operation = { persist(processing.id, external) },
            onSuccess = onComplete,
            onFailure = onFailure,
        )
    }

    private fun persist(actionId: UUID, outcome: ExternalOutcome): RewardActionProcessingResult = when (outcome) {
        is ExternalOutcome.Unknown -> result(
            RewardActionProcessingStatus.UNCERTAIN,
            store.uncertain(
                actionId,
                "External Reward Action execution threw ${outcome.errorType}; result is unknown.",
                now(),
            ),
        )
        is ExternalOutcome.Result -> when (val execution = outcome.value) {
            RewardActionExecutionResult.Delivered -> result(
                RewardActionProcessingStatus.DELIVERED,
                store.delivered(actionId, now()),
            )
            is RewardActionExecutionResult.Deferred -> RewardActionProcessingResult(
                RewardActionProcessingStatus.DEFERRED,
                store.deferred(actionId, now()),
                execution.readiness,
            )
            is RewardActionExecutionResult.Failed -> result(
                RewardActionProcessingStatus.FAILED,
                store.failed(actionId, execution.reason, now()),
            )
        }
    }

    private fun existingResult(action: StoredRewardAction): RewardActionProcessingResult? = when (action.status) {
        RewardActionStatus.PENDING -> null
        RewardActionStatus.PROCESSING -> result(RewardActionProcessingStatus.ALREADY_PROCESSING, action)
        RewardActionStatus.DELIVERED,
        RewardActionStatus.FAILED,
        RewardActionStatus.UNCERTAIN,
        RewardActionStatus.EXPIRED -> result(RewardActionProcessingStatus.ALREADY_TERMINAL, action)
    }

    private fun requireAction(actionId: UUID): StoredRewardAction = requireNotNull(store.action(actionId)) {
        "Reward Action $actionId does not exist."
    }

    private fun <T> submitNested(
        operation: () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        if (!operations.submit(operation, onSuccess, onFailure)) {
            onFailure(RejectedExecutionException("Reward workflow executor is stopping."))
        }
    }

    private fun result(status: RewardActionProcessingStatus, action: StoredRewardAction) =
        RewardActionProcessingResult(status, action)

    private sealed class BeginOutcome {
        data class Begun(val action: StoredRewardAction) : BeginOutcome()
        data class Existing(val result: RewardActionProcessingResult) : BeginOutcome()
    }

    private sealed class ExternalOutcome {
        data class Result(val value: RewardActionExecutionResult) : ExternalOutcome()
        data class Unknown(val errorType: String) : ExternalOutcome()
    }
}
