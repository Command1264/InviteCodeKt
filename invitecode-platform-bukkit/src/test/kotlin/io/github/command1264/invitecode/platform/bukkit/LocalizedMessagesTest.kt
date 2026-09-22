package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalizedMessagesTest {
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
