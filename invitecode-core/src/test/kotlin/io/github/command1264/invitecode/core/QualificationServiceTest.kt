package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.api.QualificationConditionContext
import io.github.command1264.invitecode.api.QualificationConditionOutcome
import io.github.command1264.invitecode.api.QualificationConditionProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class QualificationServiceTest {
    private val invitation = Invitation(
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        status = InvitationStatus.CLAIMED,
        claimedAt = Instant.parse("2026-09-21T00:00:00Z"),
    )

    @Test
    fun `persists qualified Invitation with the facts that caused qualification`() {
        val records = RecordingQualificationRecords(invitation)
        val service = service(records, QualificationConditionOutcome.SATISFIED)

        val result = service.reevaluate(invitation.id, policy(), context())

        assertEquals(QualificationEvaluationStatus.QUALIFIED, result.status)
        assertEquals(InvitationStatus.QUALIFIED, records.current.status)
        assertEquals("new-player", records.current.qualificationPolicyId)
        assertEquals(policy().revision, records.current.qualificationRevision)
        assertEquals(listOf("condition"), records.persistedFacts.map { it.conditionId })
        assertEquals(1, records.qualifiedWrites)
        assertEquals(0, records.factBatchWrites)
    }

    @Test
    fun `ordinary reevaluation never reverses an existing qualification`() {
        var providerCalls = 0
        val records = RecordingQualificationRecords(
            invitation.qualify(context().evaluatedAt, "new-player", policy().revision),
        )
        val provider = provider {
            providerCalls++
            QualificationConditionOutcome.UNSATISFIED
        }
        val service = QualificationService(
            records,
            QualificationEvaluator(QualificationProviderRegistry(listOf(provider))),
        )

        val result = service.reevaluate(invitation.id, policy(), context())

        assertEquals(QualificationEvaluationStatus.ALREADY_QUALIFIED, result.status)
        assertEquals(InvitationStatus.QUALIFIED, result.invitation.status)
        assertEquals(0, providerCalls)
        assertEquals(0, records.qualifiedWrites)
        assertEquals(0, records.factBatchWrites)
    }

    @Test
    fun `persists evaluated facts when policy remains unsatisfied`() {
        val records = RecordingQualificationRecords(invitation)
        val service = service(records, QualificationConditionOutcome.UNSATISFIED)

        val result = service.reevaluate(invitation.id, policy(), context())

        assertEquals(QualificationEvaluationStatus.NOT_QUALIFIED, result.status)
        assertEquals(InvitationStatus.CLAIMED, records.current.status)
        assertEquals(0, records.qualifiedWrites)
        assertEquals(1, records.factBatchWrites)
        assertEquals(QualificationFactOutcome.UNSATISFIED, records.persistedFacts.single().outcome)
    }

    private fun service(
        records: QualificationRecords,
        outcome: QualificationConditionOutcome,
    ) = QualificationService(
        records,
        QualificationEvaluator(QualificationProviderRegistry(listOf(provider { outcome }))),
    )

    private fun provider(result: () -> QualificationConditionOutcome) =
        object : QualificationConditionProvider {
            override val type = "TEST"

            override fun evaluate(
                context: QualificationConditionContext,
                parameters: Map<String, String>,
            ): QualificationConditionOutcome = result()
        }

    private fun policy() = QualificationPolicy(
        id = "new-player",
        revision = "sha256:" + "2".repeat(64),
        anyOf = listOf(
            QualificationConditionGroup(
                listOf(
                    QualificationCondition(
                        id = "condition",
                        type = "TEST",
                        parameters = emptyMap(),
                        onUnavailable = MissingQualificationProviderOutcome.UNKNOWN,
                    ),
                ),
            ),
        ),
    )

    private fun context() = QualificationConditionContext(
        invitationId = invitation.id,
        inviterId = invitation.inviterId,
        inviteeId = invitation.inviteeId,
        claimedAt = invitation.claimedAt,
        evaluatedAt = Instant.parse("2026-09-22T00:00:00Z"),
    )
}

private class RecordingQualificationRecords(initial: Invitation) : QualificationRecords {
    var current = initial
    var qualifiedWrites = 0
    var factBatchWrites = 0
    var persistedFacts = emptyList<QualificationFact>()

    override fun invitation(invitationId: UUID): Invitation? =
        current.takeIf { it.id == invitationId }

    override fun saveQualifiedInvitation(invitation: Invitation, facts: List<QualificationFact>) {
        current = invitation
        persistedFacts = facts
        qualifiedWrites++
    }

    override fun saveQualificationFacts(facts: List<QualificationFact>) {
        persistedFacts = facts
        factBatchWrites++
    }
}
