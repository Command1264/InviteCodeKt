package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HelpFrameLayoutTest {
    private fun text(value: String) = PresentedMessage(listOf(MessageSegment(value, 0x555555)))

    @Test
    fun `console uses terminal cells instead of the player font estimate`() {
        val title = text("── 中文 ──")
        val (top, bottom) = HelpFrameLayout.align(title, text("─"), MessageAudience.CONSOLE)
        assertEquals(title, top)
        assertEquals(10, bottom.plainText.length)
    }

    @Test
    fun `short heading grows its rule while long translated heading grows the divider`() {
        val divider = text("─".repeat(24))
        val short = text("──── [IC] Help ────")
        val (top, bottom) = HelpFrameLayout.align(short, divider)
        assertTrue(top.plainText.startsWith(short.plainText))
        assertTrue(top.plainText.length > short.plainText.length)
        assertEquals(divider, bottom)
        val long = text("──── [InviteCode] Administration configuration reload details 2/2 ────")
        val (longTop, longBottom) = HelpFrameLayout.align(long, divider)
        assertEquals(long, longTop)
        assertTrue(longBottom.plainText.length > divider.plainText.length)
        assertEquals(0x555555, longBottom.segments.last().color)
    }

    @Test
    fun `wide titles retain literal text and padding is bounded`() {
        val title = text("──── [邀請碼] 管理員說明 ────")
        val (top, bottom) = HelpFrameLayout.align(title, text("────"))
        assertEquals(title, top)
        assertTrue(bottom.plainText.length > 4)
        assertTrue(HelpFrameLayout.align(text("x".repeat(10000)), text("─")).second.plainText.length <= 257)
    }
}
