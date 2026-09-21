package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ClaimConfirmationTest {
    private val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val presentedAt = Instant.parse("2026-09-21T00:00:00Z")

    @Test
    fun `invitee can confirm the presented inviter and invite code within thirty seconds`() {
        val confirmation = confirmation()

        val confirmed = confirmation.confirm(
            confirmingPlayerId = inviteeId,
            confirmedAt = presentedAt.plusSeconds(30),
        )

        assertEquals(inviteeId, confirmed.inviteeId)
        assertEquals(inviterId, confirmed.inviterId)
        assertEquals("A25981", confirmed.inviteCode.normalized)
        assertEquals(presentedAt.plusSeconds(30), confirmed.confirmedAt)
    }

    @Test
    fun `another player cannot confirm the claim`() {
        val error = assertThrows<ClaimConfirmationException> {
            confirmation().confirm(UUID.randomUUID(), presentedAt.plusSeconds(1))
        }

        assertEquals(ClaimConfirmationFailure.WRONG_PLAYER, error.failure)
    }

    @Test
    fun `confirmation is rejected after thirty seconds`() {
        val error = assertThrows<ClaimConfirmationException> {
            confirmation().confirm(inviteeId, presentedAt.plusSeconds(31))
        }

        assertEquals(ClaimConfirmationFailure.EXPIRED, error.failure)
    }

    @Test
    fun `player cannot be shown a confirmation for their own invite code`() {
        assertThrows<IllegalArgumentException> {
            ClaimConfirmation.present(
                inviteeId = inviteeId,
                inviterId = inviteeId,
                inviterName = "Invitee",
                inviteCode = InviteCode.parse("A25981"),
                presentedAt = presentedAt,
                validity = Duration.ofSeconds(30),
            )
        }
    }

    private fun confirmation() = ClaimConfirmation.present(
        inviteeId = inviteeId,
        inviterId = inviterId,
        inviterName = "Inviter",
        inviteCode = InviteCode.parse("A25981"),
        presentedAt = presentedAt,
        validity = Duration.ofSeconds(30),
    )
}
