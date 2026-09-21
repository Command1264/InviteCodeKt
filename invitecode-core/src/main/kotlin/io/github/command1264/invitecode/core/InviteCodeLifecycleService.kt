package io.github.command1264.invitecode.core

import java.time.Instant
import java.util.UUID

data class ActiveInviteCode(
    val ownerId: UUID,
    val code: InviteCode,
    val activatedAt: Instant,
)

data class RetiredInviteCode(
    val ownerId: UUID,
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
 * operations from activating the same normalized code or more than one active code for an owner.
 */
interface InviteCodeRegistry {
    fun <T> atomically(operation: (InviteCodeTransaction) -> T): T
}

interface InviteCodeTransaction {
    fun activeCode(ownerId: UUID): ActiveInviteCode?

    fun activeOwner(code: InviteCode): UUID?

    fun activate(activeInviteCode: ActiveInviteCode)

    fun replace(
        previous: RetiredInviteCode,
        current: ActiveInviteCode,
    )
}

class InviteCodeCollisionException(
    val attempts: Int,
) : IllegalStateException(
    "Could not allocate a unique Invite Code after $attempts attempts; " +
        "increase the code space or inspect the active code registry.",
)

class ActiveInviteCodeNotFoundException(
    ownerId: UUID,
) : IllegalStateException("Player $ownerId does not have an active Invite Code to rotate.")

class InviteCodeLifecycleService(
    private val registry: InviteCodeRegistry,
    private val generator: InviteCodeGenerator,
    private val maxCollisionAttempts: Int,
) {
    init {
        require(maxCollisionAttempts > 0) { "Maximum collision attempts must be positive." }
    }

    fun assign(
        ownerId: UUID,
        activatedAt: Instant,
    ): ActiveInviteCode {
        repeat(maxCollisionAttempts) {
            when (val attempt = tryAssign(ownerId, activatedAt)) {
                is AssignmentAttempt.Assigned -> return attempt.activeInviteCode
                AssignmentAttempt.Collision -> Unit
            }
        }
        throw InviteCodeCollisionException(maxCollisionAttempts)
    }

    fun rotate(
        ownerId: UUID,
        rotatedAt: Instant,
    ): InviteCodeRotation {
        repeat(maxCollisionAttempts) {
            when (val attempt = tryRotate(ownerId, rotatedAt)) {
                is RotationAttempt.Rotated -> return attempt.rotation
                RotationAttempt.Collision -> Unit
            }
        }
        throw InviteCodeCollisionException(maxCollisionAttempts)
    }

    private fun tryAssign(
        ownerId: UUID,
        activatedAt: Instant,
    ): AssignmentAttempt = registry.atomically { transaction ->
        transaction.activeCode(ownerId)?.let { existing ->
            return@atomically AssignmentAttempt.Assigned(existing)
        }

        val candidate = generator.generate()
        if (transaction.activeOwner(candidate) != null) {
            return@atomically AssignmentAttempt.Collision
        }

        val activeInviteCode = ActiveInviteCode(ownerId, candidate, activatedAt)
        transaction.activate(activeInviteCode)
        AssignmentAttempt.Assigned(activeInviteCode)
    }

    private fun tryRotate(
        ownerId: UUID,
        rotatedAt: Instant,
    ): RotationAttempt = registry.atomically { transaction ->
        val existing = transaction.activeCode(ownerId)
            ?: throw ActiveInviteCodeNotFoundException(ownerId)
        val candidate = generator.generate()
        if (candidate == existing.code || transaction.activeOwner(candidate) != null) {
            return@atomically RotationAttempt.Collision
        }

        val previous = RetiredInviteCode(
            ownerId = existing.ownerId,
            code = existing.code,
            activatedAt = existing.activatedAt,
            retiredAt = rotatedAt,
        )
        val current = ActiveInviteCode(
            ownerId = ownerId,
            code = candidate,
            activatedAt = rotatedAt,
        )
        transaction.replace(previous, current)
        RotationAttempt.Rotated(InviteCodeRotation(previous, current))
    }
}

private sealed class AssignmentAttempt {
    data class Assigned(val activeInviteCode: ActiveInviteCode) : AssignmentAttempt()

    object Collision : AssignmentAttempt()
}

private sealed class RotationAttempt {
    data class Rotated(val rotation: InviteCodeRotation) : RotationAttempt()

    object Collision : RotationAttempt()
}
