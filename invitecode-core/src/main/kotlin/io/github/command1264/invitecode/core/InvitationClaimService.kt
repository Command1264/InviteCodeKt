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
    fun activeInviter(inviteCode: InviteCode): UUID?

    fun claimState(playerId: UUID): PlayerClaimState

    fun hasMatchingNetworkHash(firstPlayerId: UUID, secondPlayerId: UUID, evaluatedAt: java.time.Instant): Boolean

    fun add(invitation: Invitation)
}

sealed class InvitationClaimResult {
    data class Claimed(
        val invitation: Invitation,
        val matchingNetworkHash: Boolean = false,
    ) : InvitationClaimResult()

    data class Rejected(val eligibility: ClaimEligibility) : InvitationClaimResult()

    data class InviteCodeUnavailable(val inviteCode: InviteCode) : InvitationClaimResult()

    data class MatchingNetworkDenied(
        val inviterId: UUID,
        val inviteeId: UUID,
    ) : InvitationClaimResult()
}

enum class MatchingNetworkAction {
    ALLOW,
    WARN,
    DENY,
}

class InvitationClaimService(
    private val transactions: InvitationTransactions,
    private val eligibilityPolicy: ClaimEligibilityPolicy,
    private val invitationIdGenerator: () -> UUID,
    private val matchingNetworkAction: MatchingNetworkAction = MatchingNetworkAction.ALLOW,
) {
    fun claim(confirmedClaim: ConfirmedClaim): InvitationClaimResult = transactions.atomically(
        playerIds = setOf(confirmedClaim.inviterId, confirmedClaim.inviteeId),
    ) { transaction ->
        if (transaction.activeInviter(confirmedClaim.inviteCode) != confirmedClaim.inviterId) {
            return@atomically InvitationClaimResult.InviteCodeUnavailable(confirmedClaim.inviteCode)
        }
        val eligibility = eligibilityPolicy.evaluate(
            state = transaction.claimState(confirmedClaim.inviteeId),
            evaluatedAt = confirmedClaim.confirmedAt,
        )
        if (!eligibility.eligible) {
            return@atomically InvitationClaimResult.Rejected(eligibility)
        }
        val matchingNetworkHash = matchingNetworkAction != MatchingNetworkAction.ALLOW &&
            transaction.hasMatchingNetworkHash(
                confirmedClaim.inviterId,
                confirmedClaim.inviteeId,
                confirmedClaim.confirmedAt,
            )
        if (matchingNetworkHash && matchingNetworkAction == MatchingNetworkAction.DENY) {
            return@atomically InvitationClaimResult.MatchingNetworkDenied(
                confirmedClaim.inviterId,
                confirmedClaim.inviteeId,
            )
        }

        val invitation = Invitation(
            id = invitationIdGenerator(),
            inviterId = confirmedClaim.inviterId,
            inviteeId = confirmedClaim.inviteeId,
            status = InvitationStatus.CLAIMED,
            claimedAt = confirmedClaim.confirmedAt,
        )
        transaction.add(invitation)
        InvitationClaimResult.Claimed(
            invitation,
            matchingNetworkHash && matchingNetworkAction == MatchingNetworkAction.WARN,
        )
    }
}
