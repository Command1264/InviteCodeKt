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

    @Test
    fun `claim labels and feedback follow locale and style contracts`() {
        val chinese = catalog("zh_TW")
        val english = catalog("en_US")
        assertEquals("邀請碼", chinese.getValue("command.claim.dialog-label"))
        assertFalse(chinese.getValue("command.claim.dialog-label").any { it.isDigit() })
        assertFalse(chinese.getValue("command.claim.dialog-label").contains("六位"))
        assertEquals("Invite code", english.getValue("command.claim.dialog-label"))
        assertFalse(english.getValue("command.claim.dialog-label").any { it.isDigit() })
        assertFalse(english.getValue("command.claim.dialog-label").contains("six", ignoreCase = true))

        val claimRetryKeys = listOf(
            "command.claim.chat-timeout", "command.claim.dialog-timeout",
            "command.claim.confirmation-timeout",
        )
        listOf("zh_TW" to chinese, "en_US" to english).forEach { (locale, entries) ->
            claimRetryKeys.forEach { key ->
                assertSingleRedSentence(entries.getValue(key), locale == "zh_TW", key)
                assertTrue(entries.getValue(key).contains("/invitecode claim"), key)
            }
            assertSingleRedSentence(entries.getValue("command.stopping"), locale == "zh_TW", "command.stopping")
            assertSingleRedSentence(entries.getValue("command.claim.invalid-code"), locale == "zh_TW",
                "command.claim.invalid-code")
            listOf("runtime.usage", "runtime.help-page-error", "command.claim.unavailable",
                "command.claim.no-confirmation", "command.claim.matching-network-denied", "command.error",
                "admin.error", "admin.reload-failed", "admin.storage-config-unavailable", "admin.storage-failed",
                "admin.doctor-storage-failed").forEach { key ->
                assertSingleRedSentence(entries.getValue(key), locale == "zh_TW", key)
            }
        }

        assertTrue(chinese.getValue("runtime.usage").contains("/{label} help"))
        assertTrue(english.getValue("runtime.usage").contains("/{label} help"))
        assertTrue(chinese.getValue("runtime.help-page-error").contains("/ic help"))
        assertTrue(english.getValue("runtime.help-page-error").contains("/ic help"))
        assertTrue(chinese.getValue("command.claim.unavailable").contains("不存在"))
        assertTrue(chinese.getValue("command.claim.unavailable").contains("已失效"))
        assertTrue(chinese.getValue("command.claim.unavailable").contains("邀請人"))
        assertTrue(english.getValue("command.claim.unavailable").contains("does not exist"))
        assertTrue(english.getValue("command.claim.unavailable").contains("no longer active"))
        assertTrue(english.getValue("command.claim.unavailable").contains("inviter"))
        assertTrue(chinese.getValue("command.claim.matching-network-denied").contains("相同網路"))
        assertTrue(chinese.getValue("command.claim.matching-network-denied").contains("無法登記"))
        assertTrue(chinese.getValue("command.claim.matching-network-denied").contains("聯絡管理員"))
        assertTrue(english.getValue("command.claim.matching-network-denied").contains("same network"))
        assertTrue(english.getValue("command.claim.matching-network-denied").contains("prevent this claim"))
        assertTrue(english.getValue("command.claim.matching-network-denied").contains("contact an administrator"))
        assertTrue(chinese.getValue("command.claim.no-confirmation").contains("沒有可確認的邀請"))
        assertTrue(chinese.getValue("command.claim.no-confirmation").contains("已逾時"))
        assertTrue(chinese.getValue("command.claim.no-confirmation").contains("/invitecode claim"))
        assertTrue(english.getValue("command.claim.no-confirmation").contains("no invitation to confirm"))
        assertTrue(english.getValue("command.claim.no-confirmation").contains("expired"))
        assertTrue(english.getValue("command.claim.no-confirmation").contains("/invitecode claim"))
        assertTrue(chinese.getValue("command.error").contains("操作失敗"))
        assertTrue(chinese.getValue("command.error").contains("檢查伺服器紀錄"))
        assertTrue(english.getValue("command.error").contains("operation failed"))
        assertTrue(english.getValue("command.error").contains("check the server log"))
        assertTrue(chinese.getValue("admin.error").contains("管理操作失敗"))
        assertTrue(english.getValue("admin.error").contains("administrative operation failed"))
        assertTrue(chinese.getValue("admin.reload-failed").contains("上一份有效設定"))
        assertTrue(chinese.getValue("admin.reload-failed").contains("修正設定"))
        assertTrue(english.getValue("admin.reload-failed").contains("previous configuration remains active"))
        assertTrue(english.getValue("admin.reload-failed").contains("fix the configuration"))
        assertTrue(chinese.getValue("admin.storage-config-unavailable").contains("設定尚未成功載入"))
        assertTrue(chinese.getValue("admin.storage-config-unavailable").contains("無法判定儲存位置"))
        assertTrue(english.getValue("admin.storage-config-unavailable").contains("configuration is not loaded"))
        assertTrue(english.getValue("admin.storage-config-unavailable").contains("storage location is unknown"))
        assertTrue(chinese.getValue("admin.storage-failed").contains("儲存結構更新失敗"))
        assertTrue(english.getValue("admin.storage-failed").contains("storage schema update failed", ignoreCase = true))
        assertTrue(chinese.getValue("admin.doctor-storage-failed").contains("儲存健康檢查失敗"))
        assertTrue(english.getValue("admin.doctor-storage-failed").contains("storage health check failed"))

        val chineseInvalidCode = chinese.getValue("command.claim.invalid-code")
        assertEquals(1, chineseInvalidCode.count { it == '。' })
        assertTrue(chineseInvalidCode.contains('，'))
        val englishInvalidCode = english.getValue("command.claim.invalid-code")
        assertEquals(1, Regex("[.!?](?=\\s|\\z)").findAll(englishInvalidCode).count())
    }

    private fun assertSingleRedSentence(message: String, isChinese: Boolean, key: String) {
        assertFalse(message.contains('\n') || message.contains('\r'), "$key must be one line")
        assertTrue(message.startsWith("&c"), "$key must start red")
        assertEquals(1, Regex("(?:&[0-9a-fk-or]|&#[0-9a-fA-F]{6})").findAll(message).count(),
            "$key must contain only one style code")
        val endings = if (isChinese) message.count { it == '。' }
            else Regex("[.!?](?=\\s|\\z)").findAll(message).count()
        assertEquals(1, endings, "$key must be one sentence")
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
