package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import java.util.UUID

/**
 * 對指定玩家集合執行原子操作。實作必須序列化彼此重疊的玩家集合，避免角色檢查與
 * Invitation 建立之間發生競爭。
 */
interface InvitationTransactions {
    fun <T> atomically(
        playerIds: Set<UUID>,
        operation: (InvitationTransaction) -> T,
    ): T
}

interface InvitationTransaction {
    fun claimState(playerId: UUID): PlayerClaimState

    fun add(invitation: Invitation)
}

sealed class InvitationClaimResult {
    data class Claimed(val invitation: Invitation) : InvitationClaimResult()

    data class Rejected(val eligibility: ClaimEligibility) : InvitationClaimResult()
}

class InvitationClaimService(
    private val transactions: InvitationTransactions,
    private val eligibilityPolicy: ClaimEligibilityPolicy,
    private val invitationIdGenerator: () -> UUID,
) {
    fun claim(confirmedClaim: ConfirmedClaim): InvitationClaimResult = transactions.atomically(
        playerIds = setOf(confirmedClaim.inviterId, confirmedClaim.inviteeId),
    ) { transaction ->
        val eligibility = eligibilityPolicy.evaluate(
            state = transaction.claimState(confirmedClaim.inviteeId),
            evaluatedAt = confirmedClaim.confirmedAt,
        )
        if (!eligibility.eligible) {
            return@atomically InvitationClaimResult.Rejected(eligibility)
        }

        val invitation = Invitation(
            id = invitationIdGenerator(),
            inviterId = confirmedClaim.inviterId,
            inviteeId = confirmedClaim.inviteeId,
            status = InvitationStatus.CLAIMED,
            claimedAt = confirmedClaim.confirmedAt,
        )
        transaction.add(invitation)
        InvitationClaimResult.Claimed(invitation)
    }
}
