package io.github.command1264.invitecode.plugin

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PlayerChatActionsTest {
    @Test
    fun `the displayed code itself offers the raw code to a legacy client`() {
        val components = PlayerChatActions.codeMessage(
            "你的邀請碼是 {code}", "§aA1B2C3§r", "A1B2C3", "點擊複製到剪貼簿", "點擊填入聊天列",
        )
        val codeComponents = components.filter { it.toPlainText().contains("A1B2C3") }

        assertEquals("你的邀請碼是 A1B2C3", components.joinToString("") { it.toPlainText() })
        assertEquals(1, codeComponents.size)
        assertEquals(ChatColor.GREEN, codeComponents.single().color)
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, codeComponents.single().clickEvent.action)
        assertEquals("A1B2C3", codeComponents.single().clickEvent.value)
        assertEquals("點擊填入聊天列", codeComponents.single().hoverEvent.value.single().toPlainText())
        assertEquals(null, components.first().clickEvent)
    }

    @Test
    fun `confirmation actions execute only their respective commands`() {
        val buttons = PlayerChatActions.confirmation("同意", "不同意")

        assertEquals(2, buttons.size)
        assertEquals("[同意]", buttons[0].text)
        assertEquals(ChatColor.GREEN, buttons[0].color)
        assertEquals(ClickEvent.Action.RUN_COMMAND, buttons[0].clickEvent.action)
        assertEquals("/invitecode confirm", buttons[0].clickEvent.value)
        assertEquals("  [不同意]", buttons[1].text)
        assertEquals(ChatColor.RED, buttons[1].color)
        assertEquals(ClickEvent.Action.RUN_COMMAND, buttons[1].clickEvent.action)
        assertEquals("/invitecode cancel", buttons[1].clickEvent.value)
        assertFalse(buttons[0].clickEvent.value == buttons[1].clickEvent.value)
    }
}
