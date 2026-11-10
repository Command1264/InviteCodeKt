package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.api.QualificationConditionContext
import io.github.command1264.invitecode.api.QualificationConditionOutcome
import io.github.command1264.invitecode.api.QualificationConditionProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class QualificationEvaluatorTest {
    @Test
    fun `short circuits failed AND groups and successful OR policies`() {
        val calls = mutableListOf<String>()
        val registry = QualificationProviderRegistry()
        registry.register(provider("NO") { calls += "NO"; QualificationConditionOutcome.UNSATISFIED })
        registry.register(provider("SKIPPED_IN_GROUP") {
            calls += "SKIPPED_IN_GROUP"
            QualificationConditionOutcome.SATISFIED
        })
        registry.register(provider("YES_ONE") { calls += "YES_ONE"; QualificationConditionOutcome.SATISFIED })
        registry.register(provider("YES_TWO") { calls += "YES_TWO"; QualificationConditionOutcome.SATISFIED })
        registry.register(provider("SKIPPED_AFTER_MATCH") {
            calls += "SKIPPED_AFTER_MATCH"
            QualificationConditionOutcome.SATISFIED
        })
        val policy = policy(
            group(condition("no", "NO"), condition("skip-in-group", "SKIPPED_IN_GROUP")),
            group(condition("yes-one", "YES_ONE"), condition("yes-two", "YES_TWO")),
            group(condition("skip-after-match", "SKIPPED_AFTER_MATCH")),
        )

        val result = QualificationEvaluator(registry).evaluate(claimedInvitation(), policy, context())

        assertTrue(result.qualified)
        assertEquals(InvitationStatus.QUALIFIED, result.invitation.status)
        assertEquals(listOf("NO", "YES_ONE", "YES_TWO"), calls)
        assertEquals(listOf("no", "yes-one", "yes-two"), result.facts.map { it.conditionId })
        assertEquals(
            listOf(
                QualificationFactOutcome.UNSATISFIED,
                QualificationFactOutcome.SATISFIED,
                QualificationFactOutcome.SATISFIED,
            ),
            result.facts.map { it.outcome },
        )
    }

    @Test
    fun `missing providers fail closed according to configured outcome`() {
        val policy = policy(
            group(condition("unknown", "MISSING", MissingQualificationProviderOutcome.UNKNOWN)),
            group(condition("false", "ALSO_MISSING", MissingQualificationProviderOutcome.UNSATISFIED)),
        )

        val result = QualificationEvaluator(QualificationProviderRegistry())
            .evaluate(claimedInvitation(), policy, context())

        assertEquals(false, result.qualified)
        assertEquals(InvitationStatus.CLAIMED, result.invitation.status)
        assertEquals(
            listOf(QualificationFactOutcome.UNKNOWN, QualificationFactOutcome.UNSATISFIED),
            result.facts.map { it.outcome },
        )
    }

    @Test
    fun `external provider registration is exclusive and can be released`() {
        val registry = QualificationProviderRegistry()
        val first = provider("CUSTOM") { QualificationConditionOutcome.SATISFIED }
        val registration = registry.register(first)

        assertThrows<IllegalArgumentException> {
            registry.register(provider("custom") { QualificationConditionOutcome.UNSATISFIED })
        }

        registration.close()
        registry.register(provider("custom") { QualificationConditionOutcome.UNSATISFIED })
    }

    @Test
    fun `local built-ins take precedence and unknown types fall back to external providers`() {
        val external = QualificationProviderRegistry(
            listOf(provider("CUSTOM") { QualificationConditionOutcome.SATISFIED }),
        )
        val local = QualificationProviderRegistry(
            listOf(provider("LOCAL") { QualificationConditionOutcome.SATISFIED }),
            fallback = external,
        )
        val policy = policy(
            group(condition("local", "LOCAL"), condition("external", "CUSTOM")),
        )

        val result = QualificationEvaluator(local).evaluate(claimedInvitation(), policy, context())

        assertTrue(result.qualified)
        assertEquals(
            listOf(QualificationFactOutcome.SATISFIED, QualificationFactOutcome.SATISFIED),
            result.facts.map { it.outcome },
        )
    }

    private fun provider(
        type: String,
        result: () -> QualificationConditionOutcome,
    ) = object : QualificationConditionProvider {
        override val type = type

        override fun evaluate(
            context: QualificationConditionContext,
            parameters: Map<String, String>,
        ): QualificationConditionOutcome = result()
    }

    private fun policy(vararg groups: QualificationConditionGroup) = QualificationPolicy(
        id = "new-player",
        revision = "sha256:" + "1".repeat(64),
        anyOf = groups.toList(),
    )

    private fun group(vararg conditions: QualificationCondition) =
        QualificationConditionGroup(conditions.toList())

    private fun condition(
        id: String,
        type: String,
        onUnavailable: MissingQualificationProviderOutcome = MissingQualificationProviderOutcome.UNKNOWN,
    ) = QualificationCondition(id, type, emptyMap(), onUnavailable)

    private fun claimedInvitation() = Invitation(
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        status = InvitationStatus.CLAIMED,
        claimedAt = Instant.parse("2026-09-21T00:00:00Z"),
    )

    private fun context() = QualificationConditionContext(
        invitationId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        claimedAt = Instant.parse("2026-09-21T00:00:00Z"),
        evaluatedAt = Instant.parse("2026-09-22T00:00:00Z"),
    )
}
