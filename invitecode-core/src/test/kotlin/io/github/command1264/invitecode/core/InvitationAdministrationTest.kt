package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class InvitationAdministrationTest {
    private val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val occurredAt = Instant.parse("2026-09-21T01:00:00Z")

    @Test
    fun `administrative unlink returns the unlinked invitation and its audit record`() {
        val invitation = claimedInvitation()

        val transition = invitation.adminUnlink(context())

        assertEquals(InvitationStatus.UNLINKED, transition.invitation.status)
        assertEquals(InvitationAuditAction.UNLINKED, transition.auditRecord.action)
        assertEquals(invitation.id, transition.auditRecord.invitationId)
        assertEquals("console", transition.auditRecord.actor)
        assertEquals("duplicate account", transition.auditRecord.reason)
        assertEquals(occurredAt, transition.auditRecord.occurredAt)
    }

    @Test
    fun `administrative transfer is limited to claimed invitations and records both inviters`() {
        val invitation = claimedInvitation()
        val newInviterId = UUID.fromString("00000000-0000-0000-0000-000000000003")

        val transition = invitation.adminTransfer(newInviterId, context())

        assertEquals(newInviterId, transition.invitation.inviterId)
        assertEquals(InvitationAuditAction.TRANSFERRED, transition.auditRecord.action)
        val details = assertInstanceOf(
            InvitationAuditDetails.Transfer::class.java,
            transition.auditRecord.details,
        )
        assertEquals(inviterId, details.previousInviterId)
        assertEquals(newInviterId, details.newInviterId)

        val qualified = invitation.qualify(occurredAt, "policy@1")
        assertThrows<IllegalStateException> {
            qualified.adminTransfer(newInviterId, context())
        }
    }

    @Test
    fun `force qualification is permanent and records the policy revision`() {
        val transition = claimedInvitation().adminForceQualify(
            qualifiedAt = occurredAt,
            qualificationRevision = "admin@1",
            context = context(),
        )

        assertEquals(InvitationStatus.QUALIFIED, transition.invitation.status)
        assertEquals(occurredAt, transition.invitation.qualifiedAt)
        assertEquals("admin@1", transition.invitation.qualificationRevision)
        assertEquals(InvitationAuditAction.FORCE_QUALIFIED, transition.auditRecord.action)
        val details = assertInstanceOf(
            InvitationAuditDetails.ForceQualification::class.java,
            transition.auditRecord.details,
        )
        assertEquals("admin@1", details.qualificationRevision)

        assertThrows<IllegalStateException> {
            transition.invitation.adminForceQualify(occurredAt, "admin@2", context())
        }
    }

    @Test
    fun `eligibility reset restores a fresh claim window and produces an audit record`() {
        val state = PlayerClaimState(
            playerId = inviteeId,
            firstSeenAt = Instant.EPOCH,
            isInviter = true,
            hasInvitation = true,
        )

        val transition = state.adminResetEligibility(context())

        assertEquals(occurredAt, transition.state.firstSeenAt)
        assertEquals(false, transition.state.isInviter)
        assertEquals(false, transition.state.hasInvitation)
        assertEquals(InvitationAuditAction.ELIGIBILITY_RESET, transition.auditRecord.action)
        assertEquals(inviteeId, transition.auditRecord.playerId)
    }

    private fun context() = InvitationAdministrationContext(
        auditRecordId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        actor = "console",
        reason = "duplicate account",
        occurredAt = occurredAt,
    )

    private fun claimedInvitation() = Invitation(
        id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        inviterId = inviterId,
        inviteeId = inviteeId,
        status = InvitationStatus.CLAIMED,
        claimedAt = Instant.parse("2026-09-21T00:00:00Z"),
    )
}
