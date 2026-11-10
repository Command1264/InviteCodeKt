package io.github.command1264.invitecode.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class BukkitLocaleCompatibilityTest {
    @Test
    fun `uses modern player locale without touching the legacy receiver`() {
        var legacyCalled = false

        val locale = BukkitLocaleCompatibility.resolveLocale(LocaleReceiver("zh_tw")) {
            legacyCalled = true
            LocaleReceiver("en_us")
        }

        assertEquals("zh_tw", locale)
        assertFalse(legacyCalled)
    }

    @Test
    fun `falls back to legacy Spigot locale`() {
        val locale = BukkitLocaleCompatibility.resolveLocale(NoLocaleReceiver) {
            LocaleReceiver("en_us")
        }

        assertEquals("en_us", locale)
    }
}

class LocaleReceiver(private val locale: String) {
    fun getLocale(): String = locale
}

object NoLocaleReceiver
