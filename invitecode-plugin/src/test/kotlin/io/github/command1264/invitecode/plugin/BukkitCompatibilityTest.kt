package io.github.command1264.invitecode.plugin

import org.bukkit.Statistic
import org.bukkit.plugin.PluginDescriptionFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BukkitCompatibilityTest {
    @Test
    fun `play-time statistic resolves on legacy and modern Bukkit names`() {
        assertEquals(
            "PLAY_ONE_MINUTE",
            BukkitStatisticCompatibility.resolvePlayTimeStatisticName(listOf("PLAY_ONE_MINUTE")),
        )
        assertEquals(
            "PLAY_ONE_TICK",
            BukkitStatisticCompatibility.resolvePlayTimeStatisticName(listOf("PLAY_ONE_TICK")),
        )
        assertEquals(
            "PLAY_ONE_TICK",
            BukkitStatisticCompatibility.resolvePlayTimeStatisticName(Statistic.values().map(Statistic::name)),
        )
    }

    @Test
    fun `Spigot 1_8 loader accepts modern and Folia metadata`() {
        val resource = requireNotNull(javaClass.getResourceAsStream("/plugin.yml"))
        val text = resource.bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(text.lineSequence().any { it.trim() == "api-version: '1.13'" })
        assertTrue(text.lineSequence().any { it.trim() == "folia-supported: true" })

        val description = javaClass.getResourceAsStream("/plugin.yml").use { input ->
            PluginDescriptionFile(requireNotNull(input))
        }

        assertEquals("InviteCode", description.name)
    }
}
