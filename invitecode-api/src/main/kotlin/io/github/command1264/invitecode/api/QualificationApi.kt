package io.github.command1264.invitecode.api

import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class QualificationConditionOutcome {
    SATISFIED,
    UNSATISFIED,
    UNKNOWN,
}

data class QualificationConditionContext(
    val invitationId: UUID,
    val inviterId: UUID,
    val inviteeId: UUID,
    val claimedAt: Instant,
    val evaluatedAt: Instant,
    val playTime: Duration? = null,
    val loginDays: Int? = null,
    val adminApproved: Boolean? = null,
) {
    init {
        require(!evaluatedAt.isBefore(claimedAt)) { "Qualification cannot be evaluated before Claim." }
        require(playTime == null || !playTime.isNegative) { "Play time must not be negative." }
        require(loginDays == null || loginDays >= 0) { "Login days must not be negative." }
    }
}

interface QualificationConditionProvider {
    val type: String

    fun evaluate(
        context: QualificationConditionContext,
        parameters: Map<String, String>,
    ): QualificationConditionOutcome
}

interface QualificationProviderRegistration : AutoCloseable {
    override fun close()
}
