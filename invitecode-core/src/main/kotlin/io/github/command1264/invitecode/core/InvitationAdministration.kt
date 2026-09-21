package io.github.command1264.invitecode.core

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
        val qualificationRevision: String,
    ) : InvitationAuditDetails()
}

data class AuditedInvitationTransition(
    val invitation: Invitation,
    val auditRecord: AuditRecord,
)

data class AuditedClaimStateTransition(
    val state: PlayerClaimState,
    val auditRecord: AuditRecord,
)
