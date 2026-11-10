package io.github.command1264.invitecode.core

import io.github.command1264.invitecode.api.InvitationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class RewardDefinitionTest {
    @Test
    fun `parses qualification reward actions and creates deterministic delivery plan`() {
        val definition = RewardConfigurationParser.parse(
            mapOf(
                "config-version" to 1,
                "qualification-rewards" to mapOf(
                    "default" to mapOf(
                        "inviter" to listOf(
                            mapOf("type" to "MESSAGE", "message" to "invite.qualified.inviter"),
                            mapOf(
                                "type" to "CONSOLE_COMMAND",
                                "command" to "give {inviter_name} diamond 1",
                            ),
                        ),
                        "invitee" to listOf(
                            mapOf("type" to "VAULT_ECONOMY", "amount" to "100.50"),
                        ),
                    ),
                ),
                "milestone-rewards" to emptyMap<String, Any?>(),
            ),
        ).qualificationRewards.getValue("default")

        val first = RewardDeliveryPlanner.planQualification(definition, invitation())
        val replay = RewardDeliveryPlanner.planQualification(definition, invitation())

        assertEquals(first, replay)
        assertEquals(listOf(inviterId, inviterId, inviteeId), first.actions.map { it.recipientId })
        assertEquals(listOf(0, 1, 2), first.actions.map { it.position })
        assertEquals(
            listOf("MESSAGE", "CONSOLE_COMMAND", "VAULT_ECONOMY"),
            first.actions.map { it.type },
        )
    }

    @Test
    fun `rejects unsafe reward action payloads`() {
        assertThrows<IllegalArgumentException> {
            parseAction(mapOf("type" to "PLAYER_COMMAND", "command" to "/op {recipient_name}"))
        }
        assertThrows<IllegalArgumentException> {
            parseAction(mapOf("type" to "CONSOLE_COMMAND", "command" to "give {unknown} diamond"))
        }
        assertThrows<IllegalArgumentException> {
            parseAction(mapOf("type" to "VAULT_ECONOMY", "amount" to "0"))
        }
        assertThrows<IllegalArgumentException> {
            parseAction(mapOf("type" to "MESSAGE", "message" to "bad key with spaces"))
        }
    }

    @Test
    fun `parses milestone reward and plans deterministic Inviter delivery`() {
        val definition = RewardConfigurationParser.parse(
            mapOf(
                "config-version" to 1,
                "qualification-rewards" to emptyMap<String, Any?>(),
                "milestone-rewards" to mapOf(
                    "five-qualified" to mapOf(
                        "at" to 5,
                        "inviter" to listOf(
                            mapOf("type" to "MESSAGE", "message" to "invite.milestone.five"),
                        ),
                    ),
                ),
            ),
        ).milestoneRewards.getValue("five-qualified")
        val achievedAt = Instant.parse("2026-09-22T00:00:00Z")

        val first = RewardDeliveryPlanner.planMilestone(definition, inviterId, achievedAt)
        val replay = RewardDeliveryPlanner.planMilestone(definition, inviterId, achievedAt)

        assertEquals(first, replay)
        assertEquals(null, first.delivery.invitationId)
        assertEquals(listOf(inviterId), first.actions.map { it.recipientId })
        assertEquals("milestone:$inviterId:5:five-qualified", first.delivery.idempotencyKey)
    }

    @Test
    fun `rejects non-positive milestone thresholds`() {
        assertThrows<IllegalArgumentException> {
            RewardConfigurationParser.parse(
                mapOf(
                    "config-version" to 1,
                    "qualification-rewards" to emptyMap<String, Any?>(),
                    "milestone-rewards" to mapOf(
                        "invalid" to mapOf(
                            "at" to 0,
                            "inviter" to listOf(
                                mapOf("type" to "MESSAGE", "message" to "invite.milestone.invalid"),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    private fun parseAction(action: Map<String, Any?>) = RewardConfigurationParser.parse(
        mapOf(
            "config-version" to 1,
            "qualification-rewards" to mapOf(
                "test" to mapOf("inviter" to listOf(action), "invitee" to emptyList<Any>()),
            ),
            "milestone-rewards" to emptyMap<String, Any?>(),
        ),
    )

    private fun invitation() = Invitation(
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        inviterId = inviterId,
        inviteeId = inviteeId,
        status = InvitationStatus.QUALIFIED,
        claimedAt = Instant.EPOCH,
        qualifiedAt = Instant.EPOCH,
        qualificationPolicyId = "new-player",
        qualificationRevision = "sha256:" + "1".repeat(64),
    )

    companion object {
        private val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
