package io.github.command1264.invitecode.core

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class PlayerClaimState(
    val playerId: UUID,
    val firstSeenAt: Instant,
    val isInviter: Boolean,
    val hasInvitation: Boolean,
) {
    fun adminResetEligibility(
        context: InvitationAdministrationContext,
    ): AuditedClaimStateTransition = AuditedClaimStateTransition(
        state = copy(
            firstSeenAt = context.occurredAt,
            isInviter = false,
            hasInvitation = false,
        ),
        auditRecord = AuditRecord(
            id = context.auditRecordId,
            action = InvitationAuditAction.ELIGIBILITY_RESET,
            actor = context.actor,
            reason = context.reason,
            occurredAt = context.occurredAt,
            playerId = playerId,
        ),
    )
}

enum class ClaimIneligibilityReason {
    ALREADY_INVITER,
    ALREADY_INVITEE,
    CLAIM_WINDOW_EXPIRED,
}

data class ClaimEligibility(
    val reasons: Set<ClaimIneligibilityReason>,
) {
    val eligible: Boolean
        get() = reasons.isEmpty()
}

class ClaimEligibilityPolicy(
    private val claimWindow: Duration,
) {
    init {
        require(!claimWindow.isNegative && !claimWindow.isZero) {
            "Claim Window must be greater than zero."
        }
    }

    fun evaluate(state: PlayerClaimState, evaluatedAt: Instant): ClaimEligibility {
        val reasons = buildSet {
            if (state.isInviter) add(ClaimIneligibilityReason.ALREADY_INVITER)
            if (state.hasInvitation) add(ClaimIneligibilityReason.ALREADY_INVITEE)
            if (evaluatedAt.isAfter(state.firstSeenAt.plus(claimWindow))) {
                add(ClaimIneligibilityReason.CLAIM_WINDOW_EXPIRED)
            }
        }
        return ClaimEligibility(reasons)
    }
}
