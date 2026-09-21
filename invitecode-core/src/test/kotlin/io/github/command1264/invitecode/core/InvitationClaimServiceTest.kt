package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class InvitationClaimServiceTest {
    private val firstSeenAt = Instant.parse("2026-09-21T00:00:00Z")
    private val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val invitationId = UUID.fromString("10000000-0000-0000-0000-000000000001")

    @Test
    fun `confirmed eligible claim creates one claimed invitation`() {
        val transactions = InMemoryInvitationTransactions(
            firstSeenAtByPlayer = mapOf(inviteeId to firstSeenAt, inviterId to firstSeenAt),
        )
        val service = InvitationClaimService(
            transactions = transactions,
            eligibilityPolicy = ClaimEligibilityPolicy(Duration.ofHours(24)),
            invitationIdGenerator = { invitationId },
        )

        val result = service.claim(confirmedClaim(inviteeId, inviterId))

        val claimed = assertInstanceOf(InvitationClaimResult.Claimed::class.java, result)
        assertEquals(invitationId, claimed.invitation.id)
        assertEquals(InvitationStatus.CLAIMED, claimed.invitation.status)
        assertEquals(inviterId, claimed.invitation.inviterId)
        assertEquals(inviteeId, claimed.invitation.inviteeId)
    }

    @Test
    fun `one inviter can receive invitations from multiple invitees`() {
        val secondInviteeId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val service = service(
            mapOf(
                inviterId to firstSeenAt,
                inviteeId to firstSeenAt,
                secondInviteeId to firstSeenAt,
            ),
        )

        val first = service.claim(confirmedClaim(inviteeId, inviterId))
        val second = service.claim(confirmedClaim(secondInviteeId, inviterId))

        assertInstanceOf(InvitationClaimResult.Claimed::class.java, first)
        assertInstanceOf(InvitationClaimResult.Claimed::class.java, second)
    }

    @Test
    fun `player who has become an inviter cannot claim another invite code`() {
        val originalInviteeId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val otherInviterId = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val service = service(
            mapOf(
                inviterId to firstSeenAt,
                inviteeId to firstSeenAt,
                originalInviteeId to firstSeenAt,
                otherInviterId to firstSeenAt,
            ),
        )

        service.claim(confirmedClaim(originalInviteeId, inviteeId))
        val result = service.claim(confirmedClaim(inviteeId, otherInviterId))

        val rejected = assertInstanceOf(InvitationClaimResult.Rejected::class.java, result)
        assertEquals(setOf(ClaimIneligibilityReason.ALREADY_INVITER), rejected.eligibility.reasons)
    }

    @Test
    fun `concurrent claims for one invitee cannot both succeed`() {
        val secondInviterId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val service = service(
            mapOf(
                inviterId to firstSeenAt,
                secondInviterId to firstSeenAt,
                inviteeId to firstSeenAt,
            ),
        )
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val attempts = listOf(inviterId, secondInviterId).map { candidateInviterId ->
                executor.submit<InvitationClaimResult> {
                    start.await()
                    service.claim(confirmedClaim(inviteeId, candidateInviterId))
                }
            }
            start.countDown()
            val results = attempts.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it is InvitationClaimResult.Claimed })
            val rejected = results.filterIsInstance<InvitationClaimResult.Rejected>().single()
            assertEquals(setOf(ClaimIneligibilityReason.ALREADY_INVITEE), rejected.eligibility.reasons)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun service(firstSeenAtByPlayer: Map<UUID, Instant>): InvitationClaimService {
        val sequence = AtomicInteger()
        return InvitationClaimService(
            transactions = InMemoryInvitationTransactions(firstSeenAtByPlayer),
            eligibilityPolicy = ClaimEligibilityPolicy(Duration.ofHours(24)),
            invitationIdGenerator = {
                UUID(0x1000000000000000L, sequence.incrementAndGet().toLong())
            },
        )
    }

    private fun confirmedClaim(inviteeId: UUID, inviterId: UUID) = ConfirmedClaim(
        inviteeId = inviteeId,
        inviterId = inviterId,
        inviteCode = InviteCode.parse("A25981"),
        confirmedAt = firstSeenAt.plusSeconds(60),
    )
}

private class InMemoryInvitationTransactions(
    private val firstSeenAtByPlayer: Map<UUID, Instant>,
) : InvitationTransactions {
    private val locks = ConcurrentHashMap<UUID, ReentrantLock>()
    private val invitations = CopyOnWriteArrayList<Invitation>()

    override fun <T> atomically(
        playerIds: Set<UUID>,
        operation: (InvitationTransaction) -> T,
    ): T {
        val acquired = playerIds
            .sortedBy(UUID::toString)
            .map { locks.computeIfAbsent(it) { ReentrantLock() } }
        acquired.forEach(ReentrantLock::lock)
        try {
            return operation(object : InvitationTransaction {
                override fun claimState(playerId: UUID): PlayerClaimState = PlayerClaimState(
                    playerId = playerId,
                    firstSeenAt = requireNotNull(firstSeenAtByPlayer[playerId]),
                    isInviter = invitations.any { it.inviterId == playerId },
                    hasInvitation = invitations.any { it.inviteeId == playerId },
                )

                override fun add(invitation: Invitation) {
                    invitations += invitation
                }
            })
        } finally {
            acquired.asReversed().forEach(ReentrantLock::unlock)
        }
    }
}
