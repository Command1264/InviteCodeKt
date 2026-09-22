package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RewardTriggerServiceTest {
    @Test
    fun `qualification prepares qualification and newly reached milestone rewards once`() {
        val records = TriggerRewardRecords(qualifiedCount = 5)
        val service = RewardTriggerService(records)
        val configuration = RewardConfiguration(
            version = 1,
            qualificationRewards = mapOf(
                "default" to QualificationRewardDefinition(
                    "default",
                    inviterActions = listOf(RewardActionDefinition(RewardActionType.MESSAGE, "qualified.inviter")),
                    inviteeActions = emptyList(),
                ),
            ),
            milestoneRewards = mapOf(
                "five" to MilestoneRewardDefinition(
                    "five",
                    5,
                    listOf(RewardActionDefinition(RewardActionType.MESSAGE, "milestone.five")),
                ),
            ),
        )

        val first = service.prepareForQualification(invitation(), configuration)
        val replay = service.prepareForQualification(invitation(), configuration)

        assertEquals(2, first.size)
        assertEquals(first, replay)
        assertEquals(2, records.saveDeliveryCalls)
        assertEquals(setOf("default", "five"), first.map { it.delivery.rewardId }.toSet())
    }

    private fun invitation() = Invitation(
        invitationId,
        inviterId,
        inviteeId,
        InvitationStatus.QUALIFIED,
        Instant.EPOCH,
        Instant.parse("2026-09-22T00:00:00Z"),
        "new-player",
        "sha256:" + "1".repeat(64),
    )

    companion object {
        private val invitationId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        private val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}

private class TriggerRewardRecords(
    private val qualifiedCount: Int,
) : RewardRecords {
    private val deliveries = linkedMapOf<UUID, StoredRewardDelivery>()
    private val actions = linkedMapOf<UUID, StoredRewardAction>()
    var saveDeliveryCalls = 0

    override fun qualifiedInvitationCount(inviterId: UUID) = qualifiedCount

    override fun saveRewardDelivery(delivery: StoredRewardDelivery, actions: List<StoredRewardAction>) {
        deliveries[delivery.id] = delivery
        this.actions.putAll(actions.associateBy { it.id })
        saveDeliveryCalls++
    }

    override fun rewardDelivery(deliveryId: UUID) = deliveries[deliveryId]
    override fun rewardActions(deliveryId: UUID) = actions.values.filter { it.rewardDeliveryId == deliveryId }
    override fun rewardAction(actionId: UUID) = actions[actionId]
    override fun saveRewardAction(action: StoredRewardAction) { actions[action.id] = action }
    override fun pendingRewardActions(recipientId: UUID) =
        actions.values.filter { it.recipientId == recipientId && it.status == RewardActionStatus.PENDING }
    override fun recoverInterruptedRewardActions(recoveredAt: Instant) = 0
    override fun resolveUncertainRewardAction(
        actionId: UUID,
        resolution: UncertainRewardActionResolution,
        context: RewardAdministrationContext,
    ) = error("Not needed by this test")
}
