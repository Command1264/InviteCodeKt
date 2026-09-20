package io.github.command1264.invitecode.api

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage

interface InviteCodeApi {
    fun getClaimEligibility(playerId: UUID): CompletionStage<ClaimEligibilitySnapshot>

    fun getInvitation(invitationId: UUID): CompletionStage<InvitationSnapshot?>
}

data class ClaimEligibilitySnapshot(
    val playerId: UUID,
    val eligible: Boolean,
    val reasons: List<String>,
    val evaluatedAt: Instant,
)

data class InvitationSnapshot(
    val id: UUID,
    val inviterId: UUID,
    val inviteeId: UUID,
    val status: InvitationStatus,
    val claimedAt: Instant,
    val qualifiedAt: Instant?,
)

enum class InvitationStatus {
    CLAIMED,
    QUALIFIED,
    UNLINKED,
}
