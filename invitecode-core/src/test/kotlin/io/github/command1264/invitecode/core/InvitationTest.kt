package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class InvitationTest {
    private val inviterId = UUID.randomUUID()
    private val inviteeId = UUID.randomUUID()

    @Test
    fun `claimed invitation qualifies exactly once`() {
        val invitation = claimedInvitation()
        val qualifiedAt = Instant.parse("2026-09-21T00:00:00Z")

        val qualified = invitation.qualify(qualifiedAt, "new-player", "abc123")

        assertEquals(InvitationStatus.QUALIFIED, qualified.status)
        assertEquals(qualifiedAt, qualified.qualifiedAt)
        assertEquals("new-player", qualified.qualificationPolicyId)
        assertEquals("abc123", qualified.qualificationRevision)
        assertThrows<IllegalStateException> {
            qualified.qualify(qualifiedAt, "new-player", "abc123")
        }
    }

    @Test
    fun `qualified invitation cannot be transferred`() {
        val qualified = claimedInvitation().qualify(Instant.EPOCH, "policy", "1")

        assertThrows<IllegalStateException> {
            qualified.adminTransfer(
                newInviterId = UUID.randomUUID(),
                context = InvitationAdministrationContext(
                    auditRecordId = UUID.randomUUID(),
                    actor = "console",
                    reason = "test transfer",
                    occurredAt = Instant.EPOCH,
                ),
            )
        }
    }

    @Test
    fun `qualified invitation requires both timestamp and policy revision`() {
        assertThrows<IllegalArgumentException> {
            claimedInvitation().copy(status = InvitationStatus.QUALIFIED)
        }
        assertThrows<IllegalArgumentException> {
            claimedInvitation().copy(
                status = InvitationStatus.QUALIFIED,
                qualifiedAt = Instant.EPOCH,
            )
        }
    }

    @Test
    fun `claimed invitation cannot contain qualification metadata`() {
        assertThrows<IllegalArgumentException> {
            claimedInvitation().copy(qualificationRevision = "policy@1")
        }
    }

    @Test
    fun `administrative unlink preserves completed qualification metadata`() {
        val qualified = claimedInvitation().qualify(Instant.EPOCH, "policy", "1")

        val transition = qualified.adminUnlink(
            InvitationAdministrationContext(
                auditRecordId = UUID.randomUUID(),
                actor = "console",
                reason = "privacy request",
                occurredAt = Instant.EPOCH,
            ),
        )

        assertEquals(InvitationStatus.UNLINKED, transition.invitation.status)
        assertEquals(Instant.EPOCH, transition.invitation.qualifiedAt)
        assertEquals("policy", transition.invitation.qualificationPolicyId)
        assertEquals("1", transition.invitation.qualificationRevision)
    }

    private fun claimedInvitation() = Invitation(
        id = UUID.randomUUID(),
        inviterId = inviterId,
        inviteeId = inviteeId,
        status = InvitationStatus.CLAIMED,
        claimedAt = Instant.EPOCH,
    )
}
