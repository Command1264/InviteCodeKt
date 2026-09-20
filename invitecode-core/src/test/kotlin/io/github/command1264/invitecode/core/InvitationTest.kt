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

        val qualified = invitation.qualify(qualifiedAt, "new-player@abc123")

        assertEquals(InvitationStatus.QUALIFIED, qualified.status)
        assertEquals(qualifiedAt, qualified.qualifiedAt)
        assertEquals("new-player@abc123", qualified.qualificationRevision)
        assertThrows<IllegalStateException> {
            qualified.qualify(qualifiedAt, "new-player@abc123")
        }
    }

    @Test
    fun `qualified invitation cannot be transferred`() {
        val qualified = claimedInvitation().qualify(Instant.EPOCH, "policy@1")

        assertThrows<IllegalStateException> {
            qualified.transfer(UUID.randomUUID())
        }
    }

    private fun claimedInvitation() = Invitation(
        id = UUID.randomUUID(),
        inviterId = inviterId,
        inviteeId = inviteeId,
        status = InvitationStatus.CLAIMED,
        claimedAt = Instant.EPOCH,
    )
}
