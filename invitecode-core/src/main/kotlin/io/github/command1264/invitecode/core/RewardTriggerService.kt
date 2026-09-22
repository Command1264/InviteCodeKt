package io.github.command1264.invitecode.core

class RewardTriggerService(
    private val records: RewardRecords,
) {
    private val coordinator = RewardDeliveryCoordinator(records)

    fun prepareForQualification(
        invitation: Invitation,
        configuration: RewardConfiguration,
    ): List<RewardDeliveryPlan> {
        val qualificationPlans = configuration.qualificationRewards
            .toSortedMap()
            .values
            .map { RewardDeliveryPlanner.planQualification(it, invitation) }
        val qualifiedCount = records.qualifiedInvitationCount(invitation.inviterId)
        val achievedAt = requireNotNull(invitation.qualifiedAt) {
            "Qualified Invitation requires qualifiedAt before planning Rewards."
        }
        val milestonePlans = configuration.milestoneRewards
            .toSortedMap()
            .values
            .filter { it.qualifiedInvitations == qualifiedCount }
            .map { RewardDeliveryPlanner.planMilestone(it, invitation.inviterId, achievedAt) }
        return (qualificationPlans + milestonePlans).map(coordinator::prepare)
    }
}
