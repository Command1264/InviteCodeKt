package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.MatchingNetworkAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.ZoneId

class BukkitPluginConfigurationTest {
    @Test
    fun `parses the versioned general configuration`() {
        val configuration = BukkitPluginConfigurationParser.parse(validConfiguration())

        assertEquals("zh_TW", configuration.language.defaultLocale)
        assertTrue(configuration.language.useClientLocale)
        assertEquals(InputMode.AUTO, configuration.claim.inputMode)
        assertEquals(Duration.ofHours(24), configuration.claim.window)
        assertEquals(Duration.ofSeconds(30), configuration.claim.confirmationTimeout)
        assertEquals(Duration.ofSeconds(60), configuration.claim.chatInputTimeout)
        assertEquals(ZoneId.of("Asia/Taipei"), configuration.timeZone)
        assertFalse(configuration.inviteCode.customCodesEnabled)
        assertEquals(MatchingNetworkAction.DENY, configuration.antiAbuse.matchingIpAction)
    }

    @Test
    fun `rejects unknown fields and unsupported versions`() {
        val unknown = validConfiguration().toMutableMap().apply { put("surprise", true) }
        assertThrows<IllegalArgumentException> { BukkitPluginConfigurationParser.parse(unknown) }

        val unsupported = validConfiguration().toMutableMap().apply { put("config-version", 2) }
        assertThrows<IllegalArgumentException> { BukkitPluginConfigurationParser.parse(unsupported) }
    }

    @Test
    fun `rejects Dialog mode until the modern adapter owns it`() {
        val claim = (validConfiguration().getValue("claim") as Map<String, Any?>).toMutableMap()
        claim["input-mode"] = "DIALOG"
        val root = validConfiguration().toMutableMap().apply { put("claim", claim) }

        assertThrows<IllegalArgumentException> { BukkitPluginConfigurationParser.parse(root) }
    }

    private fun validConfiguration(): Map<String, Any?> = mapOf(
        "config-version" to 1,
        "language" to mapOf(
            "default" to "zh_TW",
            "use-client-locale" to true,
        ),
        "invite-code" to mapOf(
            "format" to "HEX_COLOR",
            "custom-codes-enabled" to false,
        ),
        "claim" to mapOf(
            "window" to "24h",
            "input-mode" to "AUTO",
            "confirmation-timeout" to "30s",
            "chat-input-timeout" to "60s",
            "existing-player-policy" to "START_WINDOW",
        ),
        "anti-abuse" to mapOf(
            "matching-ip-action" to "DENY",
            "ip-hash-retention" to "30d",
        ),
        "time-zone" to "Asia/Taipei",
    )
}
