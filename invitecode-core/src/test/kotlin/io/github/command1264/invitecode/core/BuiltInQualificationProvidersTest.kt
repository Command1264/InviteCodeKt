package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import io.github.command1264.invitecode.api.QualificationConditionContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class BuiltInQualificationProvidersTest {
    private val invitation = Invitation(
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        status = InvitationStatus.CLAIMED,
        claimedAt = Instant.parse("2026-09-20T00:00:00Z"),
    )

    @Test
    fun `evaluates built in measurable conditions`() {
        val registry = QualificationProviderRegistry(
            BuiltInQualificationProviders.create(),
        )
        val policy = parsePolicy(
            condition("play-time", "PLAY_TIME", "at-least" to "30m"),
            condition("elapsed", "ELAPSED_TIME", "at-least" to "1d"),
            condition("login-days", "LOGIN_DAYS", "at-least" to 2),
            condition("approved", "ADMIN_APPROVAL"),
        )

        val result = QualificationEvaluator(registry).evaluate(
            invitation,
            policy,
            context(playTime = Duration.ofMinutes(45), loginDays = 2, adminApproved = true),
        )

        assertEquals(true, result.qualified)
        assertEquals(List(4) { QualificationFactOutcome.SATISFIED }, result.facts.map { it.outcome })
    }

    @Test
    fun `evaluates permission and numeric placeholder comparisons through injected lookups`() {
        val requestedPermissions = mutableListOf<Pair<UUID, String>>()
        val registry = QualificationProviderRegistry(
            BuiltInQualificationProviders.create(
                permissionLookup = PermissionQualificationLookup { playerId, permission ->
                    requestedPermissions += playerId to permission
                    true
                },
                placeholderLookup = PlaceholderQualificationLookup { _, placeholder ->
                    if (placeholder == "%player_level%") "12" else null
                },
            ),
        )
        val policy = parsePolicy(
            condition("permission", "PERMISSION", "permission" to "invitecode.qualify"),
            condition(
                "level",
                "PLACEHOLDER",
                "placeholder" to "%player_level%",
                "operator" to "GREATER_THAN_OR_EQUAL",
                "value" to 10,
            ),
        )

        val result = QualificationEvaluator(registry).evaluate(invitation, policy, context())

        assertEquals(true, result.qualified)
        assertEquals(
            listOf(invitation.inviteeId to "invitecode.qualify"),
            requestedPermissions,
        )
    }

    @Test
    fun `placeholder lookup errors become UNKNOWN and fail closed`() {
        val registry = QualificationProviderRegistry(
            BuiltInQualificationProviders.create(
                placeholderLookup = PlaceholderQualificationLookup { _, _ -> error("integration unavailable") },
            ),
        )
        val policy = parsePolicy(
            condition(
                "placeholder",
                "PLACEHOLDER",
                "placeholder" to "%broken%",
                "operator" to "EQUALS",
                "value" to "yes",
            ),
        )

        val result = QualificationEvaluator(registry).evaluate(invitation, policy, context())

        assertEquals(false, result.qualified)
        assertEquals(QualificationFactOutcome.UNKNOWN, result.facts.single().outcome)
    }

    private fun parsePolicy(vararg conditions: Map<String, Any?>): QualificationPolicy =
        QualificationPolicyParser.parse(
            "new-player",
            mapOf("any-of" to listOf(mapOf("all-of" to conditions.toList()))),
        )

    private fun condition(
        id: String,
        type: String,
        vararg parameters: Pair<String, Any?>,
    ): Map<String, Any?> = mapOf("id" to id, "type" to type) + parameters

    private fun context(
        playTime: Duration? = null,
        loginDays: Int? = null,
        adminApproved: Boolean? = null,
    ) = QualificationConditionContext(
        invitationId = invitation.id,
        inviterId = invitation.inviterId,
        inviteeId = invitation.inviteeId,
        claimedAt = invitation.claimedAt,
        evaluatedAt = Instant.parse("2026-09-22T00:00:00Z"),
        playTime = playTime,
        loginDays = loginDays,
        adminApproved = adminApproved,
    )
}
