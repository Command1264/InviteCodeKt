package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.CommandHelp
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class BundledMessageCatalogTest {
    @Test
    fun `complete bundled locales have matching keys variables and real help descriptions`() {
        val chinese = catalog("zh_TW")
        val english = catalog("en_US")
        assertEquals(chinese.keys, english.keys)
        chinese.forEach { (key, value) ->
            assertEquals(variables(value), variables(english.getValue(key)), key)
            assertTrue(value.isNotBlank(), key)
        }
        CommandHelp.entries.forEach { entry ->
            assertTrue(chinese.containsKey(entry.descriptionKey), entry.descriptionKey)
            assertTrue(english.containsKey(entry.descriptionKey), entry.descriptionKey)
            val detailKey = entry.descriptionKey.replace("help-description", "help-detail")
            listOf(chinese, english).forEach { locale ->
                val detail = locale.getValue(detailKey)
                assertEquals(setOf("usage", "example"), variables(detail), detailKey)
                assertTrue(detail.lines().size in 3..7, detailKey)
            }
        }
        assertFalse(chinese.getValue("command.claim.confirmation-prompt").endsWith("："))
        assertFalse(english.getValue("command.claim.confirmation-prompt").endsWith(":"))
    }

    @Test
    fun `bundled user messages avoid semicolon prose and diagnostics have one field per line`() {
        listOf("zh_TW", "en_US").forEach { locale ->
            val entries = catalog(locale)
            entries.forEach { (key, value) -> assertFalse(value.contains(';') || value.contains('；'), "$locale:$key") }
            listOf("admin.doctor-runtime", "admin.doctor-integrations", "admin.doctor-storage",
                "admin.inspect-player", "admin.inspect-invitation", "admin.inspect-reward").forEach { key ->
                val lines = entries.getValue(key).lines()
                assertTrue(lines.size >= 3, key)
                lines.forEach { assertEquals(1, variables(it).size, "$locale:$key:$it") }
            }
        }
    }

    private fun catalog(locale: String): Map<String, String> {
        val yaml = YamlConfiguration()
        InputStreamReader(requireNotNull(javaClass.getResourceAsStream("/config/messages/$locale.yml")), Charsets.UTF_8)
            .use(yaml::load)
        return yaml.getKeys(true).filter { yaml.isString(it) }.associateWith { requireNotNull(yaml.getString(it)) }
    }

    private fun variables(text: String) = Regex("\\{([a-z][a-z0-9_]*)}")
        .findAll(text).map { it.groupValues[1] }.toSet()
}
