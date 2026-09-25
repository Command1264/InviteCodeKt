package io.github.command1264.invitecode.plugin

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PlayerChatActionsTest {
    @Test
    fun `code action offers the raw code to a legacy client for copying`() {
        val component = PlayerChatActions.copyCode("A1B2C3", "複製邀請碼", "選取邀請碼")

        assertEquals("[選取邀請碼]", component.text)
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, component.clickEvent.action)
        assertEquals("A1B2C3", component.clickEvent.value)
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
