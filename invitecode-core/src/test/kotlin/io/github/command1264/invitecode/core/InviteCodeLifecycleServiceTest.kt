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
        val occupiedOwner = UUID.randomUUID()
        val newOwner = UUID.randomUUID()
        val occupiedCode = InviteCode.parse("AAAAAA", InviteCodeFormats.colorHex)
        val availableCode = InviteCode.parse("BBBBBB", InviteCodeFormats.colorHex)
        val registry = InMemoryInviteCodeRegistry(
            ActiveInviteCode(occupiedOwner, occupiedCode, Instant.EPOCH),
        )
        val service = InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(occupiedCode, availableCode),
            maxCollisionAttempts = 3,
        )

        val assigned = service.assign(newOwner, Instant.parse("2026-09-21T02:00:00Z"))

        assertEquals(availableCode, assigned.code)
        assertEquals(newOwner, registry.resolveActiveOwner(availableCode))
        assertEquals(occupiedOwner, registry.resolveActiveOwner(occupiedCode))
    }

    @Test
    fun `rotation retires the old code and activates a new code for the same owner`() {
        val ownerId = UUID.randomUUID()
        val oldCode = InviteCode.parse("A25981", InviteCodeFormats.colorHex)
        val newCode = InviteCode.parse("FFAA00", InviteCodeFormats.colorHex)
        val activatedAt = Instant.parse("2026-09-01T00:00:00Z")
        val rotatedAt = Instant.parse("2026-09-21T02:30:00Z")
        val registry = InMemoryInviteCodeRegistry(
            ActiveInviteCode(ownerId, oldCode, activatedAt),
        )
        val service = InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(newCode),
            maxCollisionAttempts = 3,
        )

        val rotation = service.rotate(ownerId, rotatedAt)

        assertEquals(oldCode, rotation.previous.code)
        assertEquals(rotatedAt, rotation.previous.retiredAt)
        assertEquals(newCode, rotation.current.code)
        assertEquals(null, registry.resolveActiveOwner(oldCode))
        assertEquals(ownerId, registry.resolveActiveOwner(newCode))
    }

    @Test
    fun `assignment returns the existing active code without generating another candidate`() {
        val ownerId = UUID.randomUUID()
        val existing = ActiveInviteCode(
            ownerId = ownerId,
            code = InviteCode.parse("A25981", InviteCodeFormats.colorHex),
            activatedAt = Instant.EPOCH,
        )
        val service = InviteCodeLifecycleService(
            registry = InMemoryInviteCodeRegistry(existing),
            generator = SequenceInviteCodeGenerator(),
            maxCollisionAttempts = 3,
        )

        assertEquals(existing, service.assign(ownerId, Instant.parse("2026-09-21T03:00:00Z")))
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
                "increase the code space or inspect the active code registry.",
            error.message,
        )
    }

    @Test
    fun `rotation retries candidates that are current or owned by another player`() {
        val ownerId = UUID.randomUUID()
        val otherOwnerId = UUID.randomUUID()
        val currentCode = InviteCode.parse("AAAAAA", InviteCodeFormats.colorHex)
        val occupiedCode = InviteCode.parse("BBBBBB", InviteCodeFormats.colorHex)
        val replacementCode = InviteCode.parse("CCCCCC", InviteCodeFormats.colorHex)
        val registry = InMemoryInviteCodeRegistry(
            ActiveInviteCode(ownerId, currentCode, Instant.EPOCH),
            ActiveInviteCode(otherOwnerId, occupiedCode, Instant.EPOCH),
        )
        val service = InviteCodeLifecycleService(
            registry = registry,
            generator = SequenceInviteCodeGenerator(currentCode, occupiedCode, replacementCode),
            maxCollisionAttempts = 3,
        )

        val rotation = service.rotate(ownerId, Instant.parse("2026-09-21T03:00:00Z"))

        assertEquals(replacementCode, rotation.current.code)
        assertEquals(otherOwnerId, registry.resolveActiveOwner(occupiedCode))
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
    private val byOwner = initialCodes.associateByTo(mutableMapOf()) { it.ownerId }
    private val ownerByCode = initialCodes.associateTo(mutableMapOf()) { it.code to it.ownerId }

    override fun <T> atomically(operation: (InviteCodeTransaction) -> T): T = synchronized(this) {
        operation(object : InviteCodeTransaction {
            override fun activeCode(ownerId: UUID): ActiveInviteCode? = byOwner[ownerId]

            override fun activeOwner(code: InviteCode): UUID? = ownerByCode[code]

            override fun activate(activeInviteCode: ActiveInviteCode) {
                check(byOwner[activeInviteCode.ownerId] == null)
                check(ownerByCode[activeInviteCode.code] == null)
                byOwner[activeInviteCode.ownerId] = activeInviteCode
                ownerByCode[activeInviteCode.code] = activeInviteCode.ownerId
            }

            override fun replace(
                previous: RetiredInviteCode,
                current: ActiveInviteCode,
            ) {
                check(byOwner[previous.ownerId]?.code == previous.code)
                check(ownerByCode[previous.code] == previous.ownerId)
                check(ownerByCode[current.code] == null)
                byOwner[current.ownerId] = current
                ownerByCode.remove(previous.code)
                ownerByCode[current.code] = current.ownerId
            }
        })
    }

    fun resolveActiveOwner(code: InviteCode): UUID? = synchronized(this) {
        ownerByCode[code]
    }
}
