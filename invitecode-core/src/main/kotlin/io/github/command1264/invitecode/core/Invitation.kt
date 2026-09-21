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
        require((qualifiedAt == null) == (qualificationRevision == null)) {
            "Qualification timestamp and revision must either both be present or both be absent."
        }
        require(qualificationRevision == null || qualificationRevision.isNotBlank()) {
            "Qualification revision must not be blank."
        }
        require(status != InvitationStatus.CLAIMED || qualifiedAt == null) {
            "A claimed Invitation cannot contain qualification metadata."
        }
        require(status != InvitationStatus.QUALIFIED || qualifiedAt != null) {
            "A qualified Invitation requires qualification metadata."
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

    fun adminUnlink(context: InvitationAdministrationContext): AuditedInvitationTransition {
        check(status != InvitationStatus.UNLINKED) { "Invitation is already unlinked." }
        return AuditedInvitationTransition(
            previousInvitation = this,
            invitation = copy(status = InvitationStatus.UNLINKED),
            auditRecord = AuditRecord(
                id = context.auditRecordId,
                action = InvitationAuditAction.UNLINKED,
                actor = context.actor,
                reason = context.reason,
                occurredAt = context.occurredAt,
                invitationId = id,
            ),
        )
    }

    fun adminTransfer(
        newInviterId: UUID,
        context: InvitationAdministrationContext,
    ): AuditedInvitationTransition {
        check(status == InvitationStatus.CLAIMED) {
            "Only a claimed invitation can be transferred; current status is $status."
        }
        require(newInviterId != inviteeId) { "An invitee cannot become their own inviter." }
        return AuditedInvitationTransition(
            previousInvitation = this,
            invitation = copy(inviterId = newInviterId),
            auditRecord = AuditRecord(
                id = context.auditRecordId,
                action = InvitationAuditAction.TRANSFERRED,
                actor = context.actor,
                reason = context.reason,
                occurredAt = context.occurredAt,
                invitationId = id,
                details = InvitationAuditDetails.Transfer(
                    previousInviterId = inviterId,
                    newInviterId = newInviterId,
                ),
            ),
        )
    }

    fun adminForceQualify(
        qualifiedAt: Instant,
        qualificationRevision: String,
        context: InvitationAdministrationContext,
    ): AuditedInvitationTransition {
        val qualified = qualify(qualifiedAt, qualificationRevision)
        return AuditedInvitationTransition(
            previousInvitation = this,
            invitation = qualified,
            auditRecord = AuditRecord(
                id = context.auditRecordId,
                action = InvitationAuditAction.FORCE_QUALIFIED,
                actor = context.actor,
                reason = context.reason,
                occurredAt = context.occurredAt,
                invitationId = id,
                details = InvitationAuditDetails.ForceQualification(qualificationRevision),
            ),
        )
    }
}
