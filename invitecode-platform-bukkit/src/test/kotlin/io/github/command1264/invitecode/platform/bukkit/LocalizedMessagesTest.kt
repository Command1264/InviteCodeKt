package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalizedMessagesTest {
    @Test
    fun `bundled fallback preserves custom client and default priority and warns once`() {
        val missing = mutableListOf<String>()
        val messages = LocalizedMessages("zh_TW", true,
            catalogs = mapOf(
                "zh_TW" to mapOf("default.only" to "自訂預設"),
                "en_US" to mapOf("custom" to "Custom"),
            ),
            onMissingKey = missing::add,
            bundledCatalogs = mapOf(
                "zh_TW" to mapOf("new" to "內建", "last" to "最後備援"),
                "en_US" to mapOf("new" to "Bundled", "default.only" to "Do not override custom default"),
            ),
        )

        assertEquals("Custom", messages.resolve("en_US", "custom"))
        assertEquals("自訂預設", messages.resolve("en_US", "default.only"))
        assertEquals("Bundled", messages.resolve("en_US", "new"))
        assertEquals("Bundled", messages.resolve("en_US", "new"))
        assertEquals("最後備援", messages.resolve("en_US", "last"))
        assertEquals(listOf("new", "last"), missing)
    }

    @Test
    fun `variable values are literal and never expanded as other placeholders`() {
        val messages = LocalizedMessages("zh_TW", false, mapOf(
            "zh_TW" to mapOf("result" to "Player {player}: {status}"),
        ))

        assertEquals("Player {status} &a: ready", messages.resolve(null, "result", linkedMapOf(
            "player" to "{status} &a", "status" to "ready",
        )))
    }

    @Test
    fun `replaces named variables after locale fallback`() {
        val messages = LocalizedMessages(
            defaultLocale = "zh_TW",
            useClientLocale = true,
            catalogs = mapOf("zh_TW" to mapOf("claim.ready" to "邀請人：{inviter}")),
        )

        assertEquals(
            "邀請人：Alex",
            messages.resolve("ja_JP", "claim.ready", mapOf("inviter" to "Alex")),
        )
    }

    @Test
    fun `uses client locale then default locale then visible missing key`() {
        val missing = mutableListOf<String>()
        val messages = LocalizedMessages(
            defaultLocale = "zh_TW",
            useClientLocale = true,
            catalogs = mapOf(
                "zh_TW" to mapOf("claim.ok" to "完成", "default.only" to "預設"),
                "en_US" to mapOf("claim.ok" to "Done"),
            ),
            onMissingKey = missing::add,
        )

        assertEquals("Done", messages.resolve("en_us", "claim.ok"))
        assertEquals("預設", messages.resolve("en_US", "default.only"))
        assertEquals("[missing: unknown.key]", messages.resolve("en_US", "unknown.key"))
        assertEquals(listOf("unknown.key"), missing)
    }

    @Test
    fun `ignores client locale when disabled`() {
        val messages = LocalizedMessages(
            defaultLocale = "zh_TW",
            useClientLocale = false,
            catalogs = mapOf(
                "zh_TW" to mapOf("claim.ok" to "完成"),
                "en_US" to mapOf("claim.ok" to "Done"),
            ),
        )

        assertEquals("完成", messages.resolve("en_US", "claim.ok"))
    }
}
