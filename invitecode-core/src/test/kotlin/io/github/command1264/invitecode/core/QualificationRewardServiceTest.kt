package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.api.QualificationConditionContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class QualificationRewardServiceTest {
    @Test
    fun `first successful qualification saves reward plans in the same persistence call`() {
        val invitation = Invitation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            InvitationStatus.CLAIMED, Instant.EPOCH,
        )
        val records = QualificationRewardRecordsFake(invitation)
        val service = QualificationRewardService(
            records,
            QualificationEvaluator(QualificationProviderRegistry(BuiltInQualificationProviders.create())),
        )
        val policy = QualificationPolicyParser.parse(
            "new-player",
            mapOf(
                "any-of" to listOf(
                    mapOf("all-of" to listOf(mapOf("type" to "PLAY_TIME", "at-least" to "30m"))),
                ),
            ),
        )
        val rewards = RewardConfigurationParser.parse(
            mapOf(
                "config-version" to 1,
                "qualification-rewards" to mapOf(
                    "default" to mapOf(
                        "inviter" to listOf(mapOf("type" to "MESSAGE", "message" to "invite.done")),
                        "invitee" to emptyList<Any>(),
                    ),
                ),
                "milestone-rewards" to emptyMap<String, Any>(),
            ),
        )

        val result = service.reevaluate(
            invitation.id,
            policy,
            QualificationConditionContext(
                invitation.id,
                invitation.inviterId,
                invitation.inviteeId,
                invitation.claimedAt,
                Instant.EPOCH.plusSeconds(1),
                playTime = Duration.ofMinutes(30),
            ),
            rewards,
        )

        assertEquals(QualificationEvaluationStatus.QUALIFIED, result.qualification.status)
        assertEquals(1, result.rewardPlans.size)
        assertTrue(records.atomicQualificationSaved)
        assertEquals(InvitationStatus.QUALIFIED, records.current.status)
    }
}

private class QualificationRewardRecordsFake(
    var current: Invitation,
) : QualificationRewardRecords {
    var atomicQualificationSaved = false

    override fun invitation(invitationId: UUID): Invitation? = current.takeIf { it.id == invitationId }
    override fun qualifiedInvitationCount(inviterId: UUID) = 0
    override fun saveQualificationFacts(facts: List<QualificationFact>) = Unit
    override fun saveQualifiedInvitation(invitation: Invitation, facts: List<QualificationFact>) =
        error("Qualification and Reward plans must be persisted together")

    override fun saveQualifiedInvitationWithRewards(
        invitation: Invitation,
        facts: List<QualificationFact>,
        rewardPlans: List<RewardDeliveryPlan>,
    ) {
        atomicQualificationSaved = true
        current = invitation
    }

    override fun saveRewardAction(action: StoredRewardAction) = unsupported()
    override fun rewardAction(actionId: UUID): StoredRewardAction? = unsupported()
    override fun rewardActions(deliveryId: UUID): List<StoredRewardAction> = unsupported()
    override fun pendingRewardActions(recipientId: UUID): List<StoredRewardAction> = unsupported()
    override fun recoverInterruptedRewardActions(recoveredAt: Instant): Int = unsupported()
    override fun resolveUncertainRewardAction(
        actionId: UUID,
        resolution: UncertainRewardActionResolution,
        context: RewardAdministrationContext,
    ): StoredRewardAction = unsupported()
    override fun saveRewardDelivery(delivery: StoredRewardDelivery, actions: List<StoredRewardAction>) = unsupported()
    override fun rewardDelivery(deliveryId: UUID): StoredRewardDelivery? = unsupported()

    private fun unsupported(): Nothing = error("Not used by this test")
}
