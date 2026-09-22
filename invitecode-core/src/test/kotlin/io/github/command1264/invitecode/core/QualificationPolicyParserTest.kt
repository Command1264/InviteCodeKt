package io.github.command1264.invitecode.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QualificationPolicyParserTest {
    @Test
    fun `parses OR groups containing AND conditions`() {
        val policy = QualificationPolicyParser.parse(
            policyId = "new-player",
            value = mapOf(
                "any-of" to listOf(
                    mapOf(
                        "all-of" to listOf(
                            mapOf(
                                "id" to "play-time",
                                "type" to "PLAY_TIME",
                                "at-least" to "30m",
                            ),
                            mapOf(
                                "id" to "login-days",
                                "type" to "LOGIN_DAYS",
                                "at-least" to 2,
                            ),
                        ),
                    ),
                    mapOf(
                        "all-of" to listOf(
                            mapOf(
                                "id" to "approved",
                                "type" to "ADMIN_APPROVAL",
                                "on-unavailable" to "UNSATISFIED",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("new-player", policy.id)
        assertTrue(policy.revision.startsWith("sha256:"))
        assertEquals(2, policy.anyOf.size)
        assertEquals(listOf("play-time", "login-days"), policy.anyOf[0].allOf.map { it.id })
        assertEquals("30m", policy.anyOf[0].allOf[0].parameters["at-least"])
        assertEquals("2", policy.anyOf[0].allOf[1].parameters["at-least"])
        assertEquals(
            MissingQualificationProviderOutcome.UNSATISFIED,
            policy.anyOf[1].allOf.single().onUnavailable,
        )
    }

    @Test
    fun `parses versioned configuration containing multiple policies`() {
        val configuration = QualificationConfigurationParser.parse(
            mapOf(
                "config-version" to 1,
                "policies" to mapOf(
                    "new-player" to mapOf(
                        "any-of" to listOf(
                            mapOf("all-of" to listOf(mapOf("type" to "PLAY_TIME", "at-least" to "30m"))),
                        ),
                    ),
                    "approved" to mapOf(
                        "any-of" to listOf(
                            mapOf("all-of" to listOf(mapOf("type" to "ADMIN_APPROVAL"))),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, configuration.version)
        assertEquals(setOf("new-player", "approved"), configuration.policies.keys)
    }

    @Test
    fun `rejects configuration versions that are not supported`() {
        val error = assertThrows<IllegalArgumentException> {
            QualificationConfigurationParser.parse(
                mapOf("config-version" to 2, "policies" to emptyMap<String, Any?>()),
            )
        }

        assertTrue(error.message!!.contains("config-version 2"))
    }

    @Test
    fun `rejects fractional configuration versions instead of truncating them`() {
        val error = assertThrows<IllegalArgumentException> {
            QualificationConfigurationParser.parse(
                mapOf(
                    "config-version" to 1.5,
                    "policies" to mapOf(
                        "new-player" to mapOf(
                            "any-of" to listOf(
                                mapOf("all-of" to listOf(mapOf("type" to "ADMIN_APPROVAL"))),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertTrue(error.message!!.contains("numeric integer"))
    }
}
