package io.github.command1264.invitecode.core

import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InviteCodeLifecycleServiceTest {
    @Test
    fun `assignment retries a collision and activates the next candidate`() {
        val occupiedInviterId = UUID.randomUUID()
        val newInviterId = UUID.randomUUID()
        val occupiedCode = InviteCode.parse("AAAAAA", InviteCodeFormats.colorHex)
        val availableCode = InviteCode.parse("BBBBBB", InviteCodeFormats.colorHex)
        val registry = InMemoryInviteCodeRegistry(
            ActiveInviteCode(occupiedInviterId, occupiedCode, Instant.EPOCH),
        )
        val service = InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(occupiedCode, availableCode),
            maxCollisionAttempts = 3,
        )

        val assigned = service.assign(newInviterId, Instant.parse("2026-09-21T02:00:00Z"))

        assertEquals(availableCode, assigned.code)
        assertEquals(newInviterId, registry.resolveActiveInviter(availableCode))
        assertEquals(occupiedInviterId, registry.resolveActiveInviter(occupiedCode))
    }

    @Test
    fun `rotation retires the old code and activates a new code for the same inviter`() {
        val inviterId = UUID.randomUUID()
        val oldCode = InviteCode.parse("A25981", InviteCodeFormats.colorHex)
        val newCode = InviteCode.parse("FFAA00", InviteCodeFormats.colorHex)
        val activatedAt = Instant.parse("2026-09-01T00:00:00Z")
        val rotatedAt = Instant.parse("2026-09-21T02:30:00Z")
        val registry = InMemoryInviteCodeRegistry(
            ActiveInviteCode(inviterId, oldCode, activatedAt),
        )
        val service = InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(newCode),
            maxCollisionAttempts = 3,
        )

        val rotation = service.rotate(inviterId, rotatedAt)

        assertEquals(oldCode, rotation.previous.code)
        assertEquals(rotatedAt, rotation.previous.retiredAt)
        assertEquals(newCode, rotation.current.code)
        assertEquals(null, registry.resolveActiveInviter(oldCode))
        assertEquals(inviterId, registry.resolveActiveInviter(newCode))
    }

    @Test
    fun `assignment returns the existing active code without generating another candidate`() {
        val inviterId = UUID.randomUUID()
        val existing = ActiveInviteCode(
            inviterId = inviterId,
            code = InviteCode.parse("A25981", InviteCodeFormats.colorHex),
            activatedAt = Instant.EPOCH,
        )
        val service = InviteCodeLifecycleService(
            registry = InMemoryInviteCodeRegistry(existing),
            generator = SequenceInviteCodeGenerator(),
            maxCollisionAttempts = 3,
        )

        assertEquals(existing, service.assign(inviterId, Instant.parse("2026-09-21T03:00:00Z")))
    }

    @Test
    fun `assignment reports an actionable error after the collision limit`() {
        val occupiedCode = InviteCode.parse("AAAAAA", InviteCodeFormats.colorHex)
        val registry = InMemoryInviteCodeRegistry(
            ActiveInviteCode(UUID.randomUUID(), occupiedCode, Instant.EPOCH),
        )
        val service = InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(occupiedCode, occupiedCode),
            maxCollisionAttempts = 2,
        )

        val error = assertThrows<InviteCodeCollisionException> {
            service.assign(UUID.randomUUID(), Instant.parse("2026-09-21T03:00:00Z"))
        }

        assertEquals(2, error.attempts)
        assertEquals(
            "Could not allocate a unique Invite Code after 2 attempts; " +
                "increase the Invite Code namespace or inspect the Invite Code registry.",
            error.message,
        )
    }

    @Test
    fun `rotation retries current and reserved candidates`() {
        val inviterId = UUID.randomUUID()
        val otherInviterId = UUID.randomUUID()
        val currentCode = InviteCode.parse("AAAAAA", InviteCodeFormats.colorHex)
        val occupiedCode = InviteCode.parse("BBBBBB", InviteCodeFormats.colorHex)
        val replacementCode = InviteCode.parse("CCCCCC", InviteCodeFormats.colorHex)
        val registry = InMemoryInviteCodeRegistry(
            ActiveInviteCode(inviterId, currentCode, Instant.EPOCH),
            ActiveInviteCode(otherInviterId, occupiedCode, Instant.EPOCH),
        )
        val service = InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(currentCode, occupiedCode, replacementCode),
            maxCollisionAttempts = 3,
        )

        val rotation = service.rotate(inviterId, Instant.parse("2026-09-21T03:00:00Z"))

        assertEquals(replacementCode, rotation.current.code)
        assertEquals(otherInviterId, registry.resolveActiveInviter(occupiedCode))
    }

    @Test
    fun `retired code remains reserved and cannot be assigned to another inviter`() {
        val originalInviterId = UUID.randomUUID()
        val newInviterId = UUID.randomUUID()
        val retiredCode = InviteCode.parse("AAAAAA", InviteCodeFormats.colorHex)
        val rotatedCode = InviteCode.parse("BBBBBB", InviteCodeFormats.colorHex)
        val newInviterCode = InviteCode.parse("CCCCCC", InviteCodeFormats.colorHex)
        val registry = InMemoryInviteCodeRegistry(
            ActiveInviteCode(originalInviterId, retiredCode, Instant.EPOCH),
        )
        InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(rotatedCode),
            maxCollisionAttempts = 2,
        ).rotate(originalInviterId, Instant.parse("2026-09-21T03:00:00Z"))

        val assigned = InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(retiredCode, newInviterCode),
            maxCollisionAttempts = 2,
        ).assign(newInviterId, Instant.parse("2026-09-21T04:00:00Z"))

        assertEquals(newInviterCode, assigned.code)
        assertEquals(null, registry.resolveActiveInviter(retiredCode))
    }
}

private class SequenceInviteCodeGenerator(
    vararg codes: InviteCode,
) : InviteCodeGenerator {
    private val remaining = ArrayDeque(codes.toList())

    override fun generate(): InviteCode = remaining.removeFirst()
}

private class InMemoryInviteCodeRegistry(
    vararg initialCodes: ActiveInviteCode,
) : InviteCodeRegistry {
    private val byInviter = initialCodes.associateByTo(mutableMapOf()) { it.inviterId }
    private val activeInviterByCode = initialCodes.associateTo(mutableMapOf()) { it.code to it.inviterId }
    private val reservedCodes = initialCodes.mapTo(mutableSetOf()) { it.code }

    override fun <T> atomically(operation: (InviteCodeTransaction) -> T): T = synchronized(this) {
        operation(object : InviteCodeTransaction {
            override fun activeCode(inviterId: UUID): ActiveInviteCode? = byInviter[inviterId]

            override fun isReserved(code: InviteCode): Boolean = code in reservedCodes

            override fun activate(activeInviteCode: ActiveInviteCode) {
                check(byInviter[activeInviteCode.inviterId] == null)
                check(activeInviteCode.code !in reservedCodes)
                byInviter[activeInviteCode.inviterId] = activeInviteCode
                activeInviterByCode[activeInviteCode.code] = activeInviteCode.inviterId
                reservedCodes += activeInviteCode.code
            }

            override fun replace(
                previous: RetiredInviteCode,
                current: ActiveInviteCode,
            ) {
                check(byInviter[previous.inviterId]?.code == previous.code)
                check(activeInviterByCode[previous.code] == previous.inviterId)
                check(current.code !in reservedCodes)
                byInviter[current.inviterId] = current
                activeInviterByCode.remove(previous.code)
                activeInviterByCode[current.code] = current.inviterId
                reservedCodes += current.code
            }
        })
    }

    fun resolveActiveInviter(code: InviteCode): UUID? = synchronized(this) {
        activeInviterByCode[code]
    }
}
