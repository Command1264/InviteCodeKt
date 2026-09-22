package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.RewardActionExecutionContext
import io.github.command1264.invitecode.core.RewardActionExecutionResult
import io.github.command1264.invitecode.core.RewardActionExecutor
import io.github.command1264.invitecode.core.RewardActionProcessingStatus
import io.github.command1264.invitecode.core.RewardActionReadiness
import io.github.command1264.invitecode.core.RewardActionStatus
import io.github.command1264.invitecode.core.StoredRewardAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AsyncRewardDeliveryWorkflowTest {
    @Test
    fun `persists processing before executing external effect`() {
        val store = InMemoryRewardActionStateStore(action())
        var executed = false
        val workflow = AsyncRewardDeliveryWorkflow(
            ImmediateOperations,
            store,
            object : RewardActionExecutor {
                override fun readiness(
                    action: StoredRewardAction,
                    context: RewardActionExecutionContext,
                ) = RewardActionReadiness.READY

                override fun execute(
                    action: StoredRewardAction,
                    context: RewardActionExecutionContext,
                ): RewardActionExecutionResult {
                    assertEquals(RewardActionStatus.PROCESSING, store.current.status)
                    executed = true
                    return RewardActionExecutionResult.Delivered
                }
            },
        )
        var status: RewardActionProcessingStatus? = null

        assertTrue(
            workflow.process(store.current, context(store.current), onComplete = { status = it.status }),
        )

        assertTrue(executed)
        assertEquals(RewardActionStatus.DELIVERED, store.current.status)
        assertEquals(RewardActionProcessingStatus.DELIVERED, status)
    }

    @Test
    fun `offline recipient stays pending without external effect`() {
        val store = InMemoryRewardActionStateStore(action())
        var executed = false
        val workflow = AsyncRewardDeliveryWorkflow(
            ImmediateOperations,
            store,
            object : RewardActionExecutor {
                override fun readiness(
                    action: StoredRewardAction,
                    context: RewardActionExecutionContext,
                ) = RewardActionReadiness.RECIPIENT_OFFLINE

                override fun execute(
                    action: StoredRewardAction,
                    context: RewardActionExecutionContext,
                ): RewardActionExecutionResult {
                    executed = true
                    return RewardActionExecutionResult.Delivered
                }
            },
        )
        var status: RewardActionProcessingStatus? = null

        workflow.process(store.current, context(store.current), onComplete = { status = it.status })

        assertFalse(executed)
        assertEquals(RewardActionStatus.PENDING, store.current.status)
        assertEquals(RewardActionProcessingStatus.DEFERRED, status)
    }

    @Test
    fun `unknown external result becomes uncertain`() {
        val store = InMemoryRewardActionStateStore(action())
        val workflow = AsyncRewardDeliveryWorkflow(
            ImmediateOperations,
            store,
            object : RewardActionExecutor {
                override fun readiness(
                    action: StoredRewardAction,
                    context: RewardActionExecutionContext,
                ) = RewardActionReadiness.READY

                override fun execute(
                    action: StoredRewardAction,
                    context: RewardActionExecutionContext,
                ): RewardActionExecutionResult = throw IllegalStateException("unknown result")
            },
        )
        var status: RewardActionProcessingStatus? = null

        workflow.process(store.current, context(store.current), onComplete = { status = it.status })

        assertEquals(RewardActionStatus.UNCERTAIN, store.current.status)
        assertEquals(RewardActionProcessingStatus.UNCERTAIN, status)
    }

    private fun action(): StoredRewardAction {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        return StoredRewardAction(
            id = UUID.randomUUID(),
            rewardDeliveryId = UUID.randomUUID(),
            invitationId = UUID.randomUUID(),
            recipientId = UUID.randomUUID(),
            position = 0,
            type = "MESSAGE",
            payload = "invite.completed",
            status = RewardActionStatus.PENDING,
            idempotencyKey = "test-action",
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun context(action: StoredRewardAction) = RewardActionExecutionContext(
        recipientId = action.recipientId,
        recipientName = "Player",
        inviterId = null,
        inviterName = null,
        inviteeId = null,
        inviteeName = null,
    )
}

private object ImmediateOperations : BackgroundOperationExecutor {
    override fun <T> submit(
        operation: () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Boolean {
        runCatching(operation).fold(onSuccess, onFailure)
        return true
    }
}

private class InMemoryRewardActionStateStore(initial: StoredRewardAction) : RewardActionStateStore {
    var current = initial
        private set

    override fun action(actionId: UUID): StoredRewardAction? = current.takeIf { it.id == actionId }

    override fun begin(actionId: UUID, occurredAt: Instant) = transition(RewardActionStatus.PROCESSING, occurredAt)
    override fun delivered(actionId: UUID, occurredAt: Instant) = transition(RewardActionStatus.DELIVERED, occurredAt)
    override fun failed(actionId: UUID, reason: String, occurredAt: Instant) =
        transition(RewardActionStatus.FAILED, occurredAt, reason)
    override fun deferred(actionId: UUID, occurredAt: Instant) = transition(RewardActionStatus.PENDING, occurredAt)
    override fun uncertain(actionId: UUID, reason: String, occurredAt: Instant) =
        transition(RewardActionStatus.UNCERTAIN, occurredAt, reason)

    private fun transition(status: RewardActionStatus, occurredAt: Instant, error: String? = null): StoredRewardAction {
        current = current.copy(status = status, updatedAt = occurredAt, lastError = error)
        return current
    }
}
