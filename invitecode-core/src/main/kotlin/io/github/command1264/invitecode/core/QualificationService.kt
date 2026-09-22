package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.api.QualificationConditionContext
import java.util.UUID

enum class QualificationEvaluationStatus {
    QUALIFIED,
    NOT_QUALIFIED,
    ALREADY_QUALIFIED,
    NOT_APPLICABLE,
}

data class QualificationServiceResult(
    val status: QualificationEvaluationStatus,
    val invitation: Invitation,
    val facts: List<QualificationFact>,
)

class QualificationService(
    private val records: QualificationRecords,
    private val evaluator: QualificationEvaluator,
) {
    fun reevaluate(
        invitationId: UUID,
        policy: QualificationPolicy,
        context: QualificationConditionContext,
    ): QualificationServiceResult {
        val invitation = requireNotNull(records.invitation(invitationId)) {
            "Invitation $invitationId does not exist."
        }
        return when (invitation.status) {
            InvitationStatus.QUALIFIED -> QualificationServiceResult(
                QualificationEvaluationStatus.ALREADY_QUALIFIED,
                invitation,
                emptyList(),
            )
            InvitationStatus.UNLINKED -> QualificationServiceResult(
                QualificationEvaluationStatus.NOT_APPLICABLE,
                invitation,
                emptyList(),
            )
            InvitationStatus.CLAIMED -> evaluateClaimed(invitation, policy, context)
        }
    }

    private fun evaluateClaimed(
        invitation: Invitation,
        policy: QualificationPolicy,
        context: QualificationConditionContext,
    ): QualificationServiceResult {
        val evaluation = evaluator.evaluate(invitation, policy, context)
        if (evaluation.qualified) {
            records.saveQualifiedInvitation(evaluation.invitation, evaluation.facts)
            return QualificationServiceResult(
                QualificationEvaluationStatus.QUALIFIED,
                evaluation.invitation,
                evaluation.facts,
            )
        }
        records.saveQualificationFacts(evaluation.facts)
        return QualificationServiceResult(
            QualificationEvaluationStatus.NOT_QUALIFIED,
            evaluation.invitation,
            evaluation.facts,
        )
    }
}
