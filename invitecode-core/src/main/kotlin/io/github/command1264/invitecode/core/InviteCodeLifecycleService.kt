package io.github.command1264.invitecode.core

import java.time.Instant
import java.util.UUID

data class ActiveInviteCode(
    val inviterId: UUID,
    val code: InviteCode,
    val activatedAt: Instant,
)

data class RetiredInviteCode(
    val inviterId: UUID,
    val code: InviteCode,
    val activatedAt: Instant,
    val retiredAt: Instant,
) {
    init {
        require(!retiredAt.isBefore(activatedAt)) {
            "An Invite Code cannot be retired before it was activated."
        }
    }
}

data class InviteCodeRotation(
    val previous: RetiredInviteCode,
    val current: ActiveInviteCode,
)

/**
 * Runs Invite Code allocation operations atomically. Implementations must prevent two overlapping
 * operations from activating the same normalized Invite Code, reusing a retired Invite Code, or
 * activating more than one Invite Code for an Inviter.
 */
interface InviteCodeRegistry {
    fun <T> atomically(operation: (InviteCodeTransaction) -> T): T
}

interface InviteCodeTransaction {
    fun activeCode(inviterId: UUID): ActiveInviteCode?

    fun isReserved(code: InviteCode): Boolean

    fun activate(activeInviteCode: ActiveInviteCode)

    fun replace(
        previous: RetiredInviteCode,
        current: ActiveInviteCode,
    )

    fun saveAuditRecord(record: AuditRecord)
}

class InviteCodeCollisionException(
    val attempts: Int,
) : IllegalStateException(
    "Could not allocate a unique Invite Code after $attempts attempts; " +
        "increase the Invite Code namespace or inspect the Invite Code registry.",
)

class ActiveInviteCodeNotFoundException(
    inviterId: UUID,
) : IllegalStateException("Inviter $inviterId does not have an active Invite Code to rotate.")

class InviteCodeLifecycleService(
    private val registry: InviteCodeRegistry,
    private val generator: InviteCodeGenerator,
    private val maxCollisionAttempts: Int,
) {
    init {
        require(maxCollisionAttempts > 0) { "Maximum collision attempts must be positive." }
    }

    fun assign(
        inviterId: UUID,
        activatedAt: Instant,
    ): ActiveInviteCode = retryCollisions {
        tryAssign(inviterId, activatedAt)
    }

    fun rotate(
        inviterId: UUID,
        rotatedAt: Instant,
    ): InviteCodeRotation = retryCollisions {
        tryRotate(inviterId, rotatedAt, null)
    }

    fun rotate(
        inviterId: UUID,
        rotatedAt: Instant,
        auditRecord: AuditRecord,
    ): InviteCodeRotation {
        require(
            auditRecord.action == InvitationAuditAction.CODE_ROTATED &&
                auditRecord.playerId == inviterId &&
                auditRecord.invitationId == null &&
                auditRecord.occurredAt == rotatedAt,
        ) { "Invite Code rotation audit must identify the Inviter and rotation time." }
        return retryCollisions {
            tryRotate(inviterId, rotatedAt, auditRecord)
        }
    }

    private fun tryAssign(
        inviterId: UUID,
        activatedAt: Instant,
    ): CollisionAttempt<ActiveInviteCode> = registry.atomically { transaction ->
        transaction.activeCode(inviterId)?.let { existing ->
            return@atomically CollisionAttempt.Success(existing)
        }

        val candidate = generator.generate()
        if (transaction.isReserved(candidate)) {
            return@atomically CollisionAttempt.Collision
        }

        val activeInviteCode = ActiveInviteCode(inviterId, candidate, activatedAt)
        transaction.activate(activeInviteCode)
        CollisionAttempt.Success(activeInviteCode)
    }

    private fun tryRotate(
        inviterId: UUID,
        rotatedAt: Instant,
        auditRecord: AuditRecord?,
    ): CollisionAttempt<InviteCodeRotation> = registry.atomically { transaction ->
        val existing = transaction.activeCode(inviterId)
            ?: throw ActiveInviteCodeNotFoundException(inviterId)
        val candidate = generator.generate()
        if (transaction.isReserved(candidate)) {
            return@atomically CollisionAttempt.Collision
        }

        val previous = RetiredInviteCode(
            inviterId = existing.inviterId,
            code = existing.code,
            activatedAt = existing.activatedAt,
            retiredAt = rotatedAt,
        )
        val current = ActiveInviteCode(
            inviterId = inviterId,
            code = candidate,
            activatedAt = rotatedAt,
        )
        transaction.replace(previous, current)
        auditRecord?.let(transaction::saveAuditRecord)
        CollisionAttempt.Success(InviteCodeRotation(previous, current))
    }

    private inline fun <T> retryCollisions(attempt: () -> CollisionAttempt<T>): T {
        repeat(maxCollisionAttempts) {
            when (val result = attempt()) {
                is CollisionAttempt.Success -> return result.value
                CollisionAttempt.Collision -> Unit
            }
        }
        throw InviteCodeCollisionException(maxCollisionAttempts)
    }
}

private sealed class CollisionAttempt<out T> {
    data class Success<T>(val value: T) : CollisionAttempt<T>()

    object Collision : CollisionAttempt<Nothing>()
}
