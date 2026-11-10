package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.api.QualificationConditionContext
import java.util.UUID

data class QualificationRewardResult(
    val qualification: QualificationServiceResult,
    val rewardPlans: List<RewardDeliveryPlan>,
)

class QualificationRewardService(
    private val records: QualificationRewardRecords,
    private val evaluator: QualificationEvaluator,
) {
    fun reevaluate(
        invitationId: UUID,
        policy: QualificationPolicy,
        context: QualificationConditionContext,
        rewards: RewardConfiguration,
    ): QualificationRewardResult {
        val invitation = requireNotNull(records.invitation(invitationId)) {
            "Invitation $invitationId does not exist."
        }
        return when (invitation.status) {
            InvitationStatus.QUALIFIED -> result(
                QualificationEvaluationStatus.ALREADY_QUALIFIED,
                invitation,
            )
            InvitationStatus.UNLINKED -> result(
                QualificationEvaluationStatus.NOT_APPLICABLE,
                invitation,
            )
            InvitationStatus.CLAIMED -> evaluateClaimed(invitation, policy, context, rewards)
        }
    }

    private fun evaluateClaimed(
        invitation: Invitation,
        policy: QualificationPolicy,
        context: QualificationConditionContext,
        rewards: RewardConfiguration,
    ): QualificationRewardResult {
        val evaluation = evaluator.evaluate(invitation, policy, context)
        if (!evaluation.qualified) {
            records.saveQualificationFacts(evaluation.facts)
            return QualificationRewardResult(
                QualificationServiceResult(
                    QualificationEvaluationStatus.NOT_QUALIFIED,
                    evaluation.invitation,
                    evaluation.facts,
                ),
                emptyList(),
            )
        }
        val qualified = evaluation.invitation
        val qualifiedCount = records.qualifiedInvitationCount(qualified.inviterId) + 1
        val achievedAt = requireNotNull(qualified.qualifiedAt)
        val qualificationPlans = rewards.qualificationRewards.toSortedMap().values.map { definition ->
            RewardDeliveryPlanner.planQualification(definition, qualified)
        }
        val milestonePlans = rewards.milestoneRewards.toSortedMap().values
            .filter { it.qualifiedInvitations == qualifiedCount }
            .map { definition ->
                RewardDeliveryPlanner.planMilestone(definition, qualified.inviterId, achievedAt)
            }
        val plans = qualificationPlans + milestonePlans
        records.saveQualifiedInvitationWithRewards(qualified, evaluation.facts, plans)
        return QualificationRewardResult(
            QualificationServiceResult(
                QualificationEvaluationStatus.QUALIFIED,
                qualified,
                evaluation.facts,
            ),
            plans,
        )
    }

    private fun result(
        status: QualificationEvaluationStatus,
        invitation: Invitation,
    ) = QualificationRewardResult(
        QualificationServiceResult(status, invitation, emptyList()),
        emptyList(),
    )
}
