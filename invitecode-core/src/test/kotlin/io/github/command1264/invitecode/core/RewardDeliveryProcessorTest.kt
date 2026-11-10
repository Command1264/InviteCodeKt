package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class RewardDeliveryProcessorTest {
    @Test
    fun `failed initial persistence creates no executable action`() {
        val records = RecordingRewardRecords().apply { failDeliveryWrite = true }
        val coordinator = RewardDeliveryCoordinator(records)
        val plan = plan()

        assertThrows<IllegalStateException> { coordinator.prepare(plan) }

        assertEquals(null, coordinator.action(plan.actions.single().id))
        assertEquals(0, records.saveDeliveryCalls)
    }

    @Test
    fun `preparing the same logical delivery is idempotent`() {
        val records = RecordingRewardRecords()
        val coordinator = RewardDeliveryCoordinator(records)
        val plan = plan()

        val first = coordinator.prepare(plan)
        val replay = coordinator.prepare(plan)

        assertEquals(first, replay)
        assertEquals(1, records.saveDeliveryCalls)
    }

    @Test
    fun `persists plan and PROCESSING before executing external effect`() {
        val records = RecordingRewardRecords()
        val coordinator = RewardDeliveryCoordinator(records)
        val plan = plan()
        coordinator.prepare(plan)
        var effectCalls = 0
        val executor = executor(
            execute = {
                assertEquals(RewardActionStatus.PROCESSING, records.action(it.id)!!.status)
                assertEquals(listOf("save-delivery", "save-action:PROCESSING"), records.events)
                effectCalls++
                RewardActionExecutionResult.Delivered
            },
        )

        val result = RewardDeliveryProcessor(coordinator, executor) { Instant.parse("2026-09-22T00:00:01Z") }
            .process(plan.actions.single(), context())

        assertEquals(RewardActionProcessingStatus.DELIVERED, result.status)
        assertEquals(1, effectCalls)
        assertEquals(RewardActionStatus.DELIVERED, records.action(plan.actions.single().id)!!.status)
    }

    @Test
    fun `offline recipient remains pending until login retry`() {
        val records = RecordingRewardRecords()
        val coordinator = RewardDeliveryCoordinator(records)
        val plan = plan()
        coordinator.prepare(plan)
        var effectCalls = 0
        val processor = RewardDeliveryProcessor(
            coordinator,
            executor(
                readiness = { RewardActionReadiness.RECIPIENT_OFFLINE },
                execute = { effectCalls++; RewardActionExecutionResult.Delivered },
            ),
        ) { Instant.parse("2026-09-22T00:00:01Z") }

        val result = processor.process(plan.actions.single(), context())

        assertEquals(RewardActionProcessingStatus.DEFERRED, result.status)
        assertEquals(0, effectCalls)
        assertEquals(RewardActionStatus.PENDING, records.action(plan.actions.single().id)!!.status)
        assertEquals(listOf(plan.actions.single()), coordinator.pendingForRecipient(recipientId))
    }

    @Test
    fun `recipient disconnect after PROCESSING returns action to pending`() {
        val records = RecordingRewardRecords()
        val coordinator = RewardDeliveryCoordinator(records)
        val plan = plan()
        coordinator.prepare(plan)
        val processor = RewardDeliveryProcessor(
            coordinator,
            executor(
                execute = {
                    RewardActionExecutionResult.Deferred(RewardActionReadiness.RECIPIENT_OFFLINE)
                },
            ),
        ) { Instant.parse("2026-09-22T00:00:01Z") }

        val result = processor.process(plan.actions.single(), context())

        assertEquals(RewardActionProcessingStatus.DEFERRED, result.status)
        assertEquals(RewardActionReadiness.RECIPIENT_OFFLINE, result.readiness)
        assertEquals(RewardActionStatus.PENDING, records.action(plan.actions.single().id)!!.status)
        assertEquals(
            listOf("save-delivery", "save-action:PROCESSING", "save-action:PENDING"),
            records.events,
        )
    }

    @Test
    fun `failed final persistence never repeats an external effect`() {
        val records = RecordingRewardRecords()
        val coordinator = RewardDeliveryCoordinator(records)
        val plan = plan()
        coordinator.prepare(plan)
        var effectCalls = 0
        records.failDeliveredWrite = true
        val processor = RewardDeliveryProcessor(
            coordinator,
            executor(execute = { effectCalls++; RewardActionExecutionResult.Delivered }),
        ) { Instant.parse("2026-09-22T00:00:01Z") }

        assertThrows<IllegalStateException> { processor.process(plan.actions.single(), context()) }
        assertEquals(1, effectCalls)
        assertEquals(RewardActionStatus.PROCESSING, records.action(plan.actions.single().id)!!.status)

        val replay = processor.process(plan.actions.single(), context())
        assertEquals(RewardActionProcessingStatus.ALREADY_PROCESSING, replay.status)
        assertEquals(1, effectCalls)

        coordinator.recoverInterrupted(Instant.parse("2026-09-22T00:00:02Z"))
        assertEquals(RewardActionStatus.UNCERTAIN, records.action(plan.actions.single().id)!!.status)
    }

    @Test
    fun `executor exception becomes uncertain without exposing exception message`() {
        val records = RecordingRewardRecords()
        val coordinator = RewardDeliveryCoordinator(records)
        val plan = plan()
        coordinator.prepare(plan)
        val processor = RewardDeliveryProcessor(
            coordinator,
            executor(execute = { throw IllegalStateException("secret token value") }),
        ) { Instant.parse("2026-09-22T00:00:01Z") }

        val result = processor.process(plan.actions.single(), context())

        assertEquals(RewardActionProcessingStatus.UNCERTAIN, result.status)
        val stored = records.action(plan.actions.single().id)!!
        assertEquals(RewardActionStatus.UNCERTAIN, stored.status)
        assertTrue(!stored.lastError.orEmpty().contains("secret token value"))
    }

    @Test
    fun `failed PROCESSING persistence prevents external effect`() {
        val records = RecordingRewardRecords()
        val coordinator = RewardDeliveryCoordinator(records)
        val plan = plan()
        coordinator.prepare(plan)
        records.failProcessingWrite = true
        var effectCalls = 0
        val processor = RewardDeliveryProcessor(
            coordinator,
            executor(execute = { effectCalls++; RewardActionExecutionResult.Delivered }),
        ) { Instant.parse("2026-09-22T00:00:01Z") }

        assertThrows<IllegalStateException> { processor.process(plan.actions.single(), context()) }

        assertEquals(0, effectCalls)
        assertEquals(RewardActionStatus.PENDING, records.action(plan.actions.single().id)!!.status)
    }

    private fun executor(
        readiness: (StoredRewardAction) -> RewardActionReadiness = { RewardActionReadiness.READY },
        execute: (StoredRewardAction) -> RewardActionExecutionResult,
    ) = object : RewardActionExecutor {
        override fun readiness(
            action: StoredRewardAction,
            context: RewardActionExecutionContext,
        ) = readiness(action)

        override fun execute(
            action: StoredRewardAction,
            context: RewardActionExecutionContext,
        ) = execute(action)
    }

    private fun plan(): RewardDeliveryPlan {
        val deliveryId = UUID.fromString("00000000-0000-0000-0000-000000000010")
        val at = Instant.parse("2026-09-22T00:00:00Z")
        return RewardDeliveryPlan(
            StoredRewardDelivery(
                deliveryId, invitationId, "default", RewardDeliveryStatus.PENDING,
                "qualification:$invitationId:default", at, at,
            ),
            listOf(
                StoredRewardAction(
                    actionId, deliveryId, invitationId, recipientId, 0, "MESSAGE", "invite.qualified",
                    RewardActionStatus.PENDING, "action-key", at, at,
                ),
            ),
        )
    }

    private fun context() = RewardActionExecutionContext(
        recipientId = recipientId,
        recipientName = "Invitee",
        inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        inviterName = "Inviter",
        inviteeId = recipientId,
        inviteeName = "Invitee",
    )

    companion object {
        private val invitationId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        private val recipientId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        private val actionId = UUID.fromString("00000000-0000-0000-0000-000000000011")
    }
}

private class RecordingRewardRecords : RewardRecords {
    private var delivery: StoredRewardDelivery? = null
    private val actions = linkedMapOf<UUID, StoredRewardAction>()
    val events = mutableListOf<String>()
    var failProcessingWrite = false
    var failDeliveredWrite = false
    var failDeliveryWrite = false
    var saveDeliveryCalls = 0

    override fun qualifiedInvitationCount(inviterId: UUID) = 0

    override fun saveRewardDelivery(delivery: StoredRewardDelivery, actions: List<StoredRewardAction>) {
        if (failDeliveryWrite) error("delivery write failed")
        this.delivery = delivery
        this.actions.putAll(actions.associateBy { it.id })
        saveDeliveryCalls++
        events += "save-delivery"
    }

    override fun rewardDelivery(deliveryId: UUID): StoredRewardDelivery? =
        delivery?.takeIf { it.id == deliveryId }

    override fun rewardActions(deliveryId: UUID): List<StoredRewardAction> =
        actions.values.filter { it.rewardDeliveryId == deliveryId }.sortedBy { it.position }

    override fun rewardAction(actionId: UUID): StoredRewardAction? = actions[actionId]

    fun action(actionId: UUID) = actions[actionId]

    override fun saveRewardAction(action: StoredRewardAction) {
        if (action.status == RewardActionStatus.PROCESSING && failProcessingWrite) error("processing write failed")
        if (action.status == RewardActionStatus.DELIVERED && failDeliveredWrite) error("delivered write failed")
        actions[action.id] = action
        events += "save-action:${action.status}"
    }

    override fun pendingRewardActions(recipientId: UUID): List<StoredRewardAction> =
        actions.values.filter { it.recipientId == recipientId && it.status == RewardActionStatus.PENDING }

    override fun recoverInterruptedRewardActions(recoveredAt: Instant): Int {
        val interrupted = actions.values.filter { it.status == RewardActionStatus.PROCESSING }
        interrupted.forEach {
            actions[it.id] = it.copy(
                status = RewardActionStatus.UNCERTAIN,
                updatedAt = recoveredAt,
                lastError = "Interrupted while processing; delivery result is unknown.",
            )
        }
        return interrupted.size
    }

    override fun resolveUncertainRewardAction(
        actionId: UUID,
        resolution: UncertainRewardActionResolution,
        context: RewardAdministrationContext,
    ): StoredRewardAction {
        val current = requireNotNull(actions[actionId])
        val resolved = current.copy(
            status = when (resolution) {
                UncertainRewardActionResolution.RETRY -> RewardActionStatus.PENDING
                UncertainRewardActionResolution.MARK_DELIVERED -> RewardActionStatus.DELIVERED
            },
            updatedAt = context.occurredAt,
            lastError = null,
        )
        actions[actionId] = resolved
        return resolved
    }
}
