package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ClaimEligibilityTest {
    private val policy = ClaimEligibilityPolicy(Duration.ofHours(24))
    private val firstSeenAt = Instant.parse("2026-09-21T00:00:00Z")

    @Test
    fun `new player remains eligible at the claim window deadline`() {
        val eligibility = policy.evaluate(
            state = eligibleState(),
            evaluatedAt = firstSeenAt.plus(Duration.ofHours(24)),
        )

        assertTrue(eligibility.eligible)
        assertEquals(emptySet<ClaimIneligibilityReason>(), eligibility.reasons)
    }

    @Test
    fun `player is rejected after the claim window`() {
        val eligibility = policy.evaluate(
            state = eligibleState(),
            evaluatedAt = firstSeenAt.plus(Duration.ofHours(24)).plusMillis(1),
        )

        assertEquals(setOf(ClaimIneligibilityReason.CLAIM_WINDOW_EXPIRED), eligibility.reasons)
    }

    @Test
    fun `inviter cannot claim another invite code`() {
        val eligibility = policy.evaluate(
            state = eligibleState().copy(isInviter = true),
            evaluatedAt = firstSeenAt,
        )

        assertEquals(setOf(ClaimIneligibilityReason.ALREADY_INVITER), eligibility.reasons)
    }

    @Test
    fun `invitee cannot create another invitation`() {
        val eligibility = policy.evaluate(
            state = eligibleState().copy(hasInvitation = true),
            evaluatedAt = firstSeenAt,
        )

        assertEquals(setOf(ClaimIneligibilityReason.ALREADY_INVITEE), eligibility.reasons)
    }

    @Test
    fun `all applicable rejection reasons are retained`() {
        val eligibility = policy.evaluate(
            state = eligibleState().copy(isInviter = true, hasInvitation = true),
            evaluatedAt = firstSeenAt.plus(Duration.ofDays(2)),
        )

        assertEquals(
            setOf(
                ClaimIneligibilityReason.ALREADY_INVITER,
                ClaimIneligibilityReason.ALREADY_INVITEE,
                ClaimIneligibilityReason.CLAIM_WINDOW_EXPIRED,
            ),
            eligibility.reasons,
        )
    }

    private fun eligibleState() = PlayerClaimState(
        playerId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        firstSeenAt = firstSeenAt,
        isInviter = false,
        hasInvitation = false,
    )
}
