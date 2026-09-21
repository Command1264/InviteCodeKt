package io.github.command1264.invitecode.core

import java.time.Duration
import java.time.Instant
import java.util.UUID

class ConfirmedClaim internal constructor(
    val inviteeId: UUID,
    val inviterId: UUID,
    val inviteCode: InviteCode,
    val confirmedAt: Instant,
) {
    init {
        require(inviteeId != inviterId) { "An Invitee cannot Claim their own Invite Code." }
    }
}

enum class ClaimConfirmationFailure {
    WRONG_PLAYER,
    EXPIRED,
}

class ClaimConfirmationException(
    val failure: ClaimConfirmationFailure,
    message: String,
) : IllegalStateException(message)

class ClaimConfirmation private constructor(
    val inviteeId: UUID,
    val inviterId: UUID,
    val inviterName: String,
    val inviteCode: InviteCode,
    val presentedAt: Instant,
    private val expiresAt: Instant,
) {
    fun confirm(confirmingPlayerId: UUID, confirmedAt: Instant): ConfirmedClaim {
        if (confirmingPlayerId != inviteeId) {
            throw ClaimConfirmationException(
                ClaimConfirmationFailure.WRONG_PLAYER,
                "Only the Invitee shown this Claim Confirmation may confirm it.",
            )
        }
        if (confirmedAt.isAfter(expiresAt)) {
            throw ClaimConfirmationException(
                ClaimConfirmationFailure.EXPIRED,
                "Claim Confirmation expired at $expiresAt.",
            )
        }
        return ConfirmedClaim(inviteeId, inviterId, inviteCode, confirmedAt)
    }

    companion object {
        fun present(
            inviteeId: UUID,
            inviterId: UUID,
            inviterName: String,
            inviteCode: InviteCode,
            presentedAt: Instant,
            validity: Duration,
        ): ClaimConfirmation {
            require(inviteeId != inviterId) { "An Invitee cannot Claim their own Invite Code." }
            require(inviterName.isNotBlank()) { "Inviter name must not be blank." }
            require(!validity.isNegative && !validity.isZero) {
                "Claim Confirmation validity must be greater than zero."
            }
            return ClaimConfirmation(
                inviteeId = inviteeId,
                inviterId = inviterId,
                inviterName = inviterName,
                inviteCode = inviteCode,
                presentedAt = presentedAt,
                expiresAt = presentedAt.plus(validity),
            )
        }
    }
}
