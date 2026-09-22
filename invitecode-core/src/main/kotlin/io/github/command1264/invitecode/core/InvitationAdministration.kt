package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import java.time.Instant
import java.util.UUID

data class InvitationAdministrationContext(
    val auditRecordId: UUID,
    val actor: String,
    val reason: String,
    val occurredAt: Instant,
) {
    init {
        require(actor.isNotBlank()) { "Audit actor must not be blank." }
        require(reason.isNotBlank()) { "Audit reason must not be blank." }
    }
}

enum class InvitationAuditAction {
    UNLINKED,
    TRANSFERRED,
    FORCE_QUALIFIED,
    ELIGIBILITY_RESET,
    CODE_ROTATED,
    STORAGE_MIGRATED,
}

data class AuditRecord(
    val id: UUID,
    val action: InvitationAuditAction,
    val actor: String,
    val reason: String,
    val occurredAt: Instant,
    val invitationId: UUID? = null,
    val playerId: UUID? = null,
    val details: InvitationAuditDetails = InvitationAuditDetails.None,
)

sealed class InvitationAuditDetails {
    object None : InvitationAuditDetails()

    data class Transfer(
        val previousInviterId: UUID,
        val newInviterId: UUID,
    ) : InvitationAuditDetails()

    data class ForceQualification(
        val qualificationPolicyId: String,
        val qualificationRevision: String,
    ) : InvitationAuditDetails() {
        init {
            require(qualificationPolicyId.isNotBlank()) { "Qualification policy ID must not be blank." }
            require(qualificationRevision.isNotBlank()) { "Qualification revision must not be blank." }
        }
    }
}

data class AuditedInvitationTransition(
    val previousInvitation: Invitation,
    val invitation: Invitation,
    val auditRecord: AuditRecord,
) {
    init {
        require(
            previousInvitation.id == invitation.id &&
                previousInvitation.inviteeId == invitation.inviteeId &&
                previousInvitation.claimedAt == invitation.claimedAt,
        ) { "Invitation transition must preserve its identity, Invitee, and Claim timestamp." }
        require(auditRecord.invitationId == invitation.id && auditRecord.playerId == null) {
            "Invitation transition Audit Record must target Invitation ${invitation.id}."
        }
        when (auditRecord.action) {
            InvitationAuditAction.UNLINKED -> require(
                previousInvitation.status != InvitationStatus.UNLINKED &&
                    previousInvitation.inviterId == invitation.inviterId &&
                    previousInvitation.qualifiedAt == invitation.qualifiedAt &&
                    previousInvitation.qualificationPolicyId == invitation.qualificationPolicyId &&
                    previousInvitation.qualificationRevision == invitation.qualificationRevision &&
                invitation.status == InvitationStatus.UNLINKED &&
                    auditRecord.details == InvitationAuditDetails.None,
            ) { "Unlink Audit Record must describe an unlinked Invitation without extra details." }
            InvitationAuditAction.TRANSFERRED -> {
                val details = auditRecord.details as? InvitationAuditDetails.Transfer
                    ?: throw IllegalArgumentException("Transfer Audit Record requires transfer details.")
                require(
                    previousInvitation.status == InvitationStatus.CLAIMED &&
                    invitation.status == InvitationStatus.CLAIMED &&
                        previousInvitation.inviterId != invitation.inviterId &&
                        details.previousInviterId == previousInvitation.inviterId &&
                        details.newInviterId == invitation.inviterId,
                ) { "Transfer Audit Record must identify the Invitation's new Inviter." }
            }
            InvitationAuditAction.FORCE_QUALIFIED -> {
                val details = auditRecord.details as? InvitationAuditDetails.ForceQualification
                    ?: throw IllegalArgumentException(
                        "Force Qualification Audit Record requires qualification details.",
                    )
                require(
                    previousInvitation.status == InvitationStatus.CLAIMED &&
                    invitation.status == InvitationStatus.QUALIFIED &&
                        previousInvitation.inviterId == invitation.inviterId &&
                        details.qualificationPolicyId == invitation.qualificationPolicyId &&
                        details.qualificationRevision == invitation.qualificationRevision,
                ) { "Force Qualification Audit Record must match the qualified Invitation revision." }
            }
            InvitationAuditAction.ELIGIBILITY_RESET -> throw IllegalArgumentException(
                "Eligibility Reset cannot audit an Invitation transition.",
            )
            InvitationAuditAction.CODE_ROTATED,
            InvitationAuditAction.STORAGE_MIGRATED -> throw IllegalArgumentException(
                "${auditRecord.action} cannot audit an Invitation transition.",
            )
        }
    }
}

data class AuditedClaimStateTransition(
    val state: PlayerClaimState,
    val auditRecord: AuditRecord,
) {
    init {
        require(
            auditRecord.action == InvitationAuditAction.ELIGIBILITY_RESET &&
                auditRecord.playerId == state.playerId &&
                auditRecord.invitationId == null &&
                auditRecord.details == InvitationAuditDetails.None,
        ) { "Eligibility Reset Audit Record must target player ${state.playerId} without extra details." }
        require(
            !state.isInviter && !state.hasInvitation && state.firstSeenAt == auditRecord.occurredAt,
        ) { "Eligibility Reset state must restore eligibility from the Audit Record timestamp." }
    }
}
