package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import java.time.Instant
import java.util.UUID

data class Invitation(
    val id: UUID,
    val inviterId: UUID,
    val inviteeId: UUID,
    val status: InvitationStatus,
    val claimedAt: Instant,
    val qualifiedAt: Instant? = null,
    val qualificationRevision: String? = null,
) {
    init {
        require(inviterId != inviteeId) { "An inviter cannot invite themselves." }
        require(status == InvitationStatus.QUALIFIED || qualifiedAt == null) {
            "Only a qualified invitation may have a qualification timestamp."
        }
    }

    fun qualify(at: Instant, revision: String): Invitation {
        check(status == InvitationStatus.CLAIMED) {
            "Only a claimed invitation can become qualified; current status is $status."
        }
        require(revision.isNotBlank()) { "Qualification revision must not be blank." }
        return copy(
            status = InvitationStatus.QUALIFIED,
            qualifiedAt = at,
            qualificationRevision = revision,
        )
    }

    fun unlink(): Invitation {
        check(status != InvitationStatus.UNLINKED) { "Invitation is already unlinked." }
        return copy(status = InvitationStatus.UNLINKED)
    }

    fun transfer(newInviterId: UUID): Invitation {
        check(status == InvitationStatus.CLAIMED) {
            "Only a claimed invitation can be transferred; current status is $status."
        }
        require(newInviterId != inviteeId) { "An invitee cannot become their own inviter." }
        return copy(inviterId = newInviterId)
    }
}
