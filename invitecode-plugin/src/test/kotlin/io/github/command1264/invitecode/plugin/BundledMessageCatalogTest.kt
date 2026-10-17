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
        }
        assertFalse(chinese.getValue("command.claim.confirmation-prompt").endsWith("："))
        assertFalse(english.getValue("command.claim.confirmation-prompt").endsWith(":"))
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
