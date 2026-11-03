package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessagePresentationTest {
    @Test
    fun `prefix styling cannot leak into body and unknown syntax stays literal`() {
        val result = presenter("Ready &g &#12Z456", "&6&l[InviteCode]").render(
            null, "message", audience = MessageAudience.RGB_PLAYER,
        )
        assertEquals("[InviteCode] Ready &g &#12Z456", result.plainText)
        assertEquals(setOf(MessageDecoration.BOLD), result.segments.first().decorations)
        assertEquals(null, result.segments.last().color)
        assertEquals(emptySet<MessageDecoration>(), result.segments.last().decorations)
    }

    @Test
    fun `empty or style only prefix produces no separator or blank line`() {
        for (prefix in listOf("", "&7&r")) {
            val presenter = presenter("Ready", prefix)
            assertEquals("Ready", presenter.render(null, "message").plainText)
            assertEquals("Ready", presenter.render(null, "message", prefix = PrefixPlacement.OWN_LINE).plainText)
        }
    }

    @Test
    fun `dynamic values cannot inject colors decorations or more placeholders`() {
        val result = presenter("&a{player} {status}").render(
            null, "message", linkedMapOf("player" to "&#000000&l{status}", "status" to "OK"),
            audience = MessageAudience.RGB_PLAYER, prefix = PrefixPlacement.NONE,
        )
        assertEquals("&#000000&l{status} OK", result.plainText)
        assertEquals(setOf(0x55FF55), result.segments.map { it.color }.toSet())
        assertEquals(emptySet<MessageDecoration>(), result.segments.first().decorations)
    }

    @Test
    fun `hex colors follow player capability while reset ends decorations`() {
        val presenter = presenter("&#123456&lHello&r end")
        val rgb = presenter.render(null, "message", audience = MessageAudience.RGB_PLAYER, prefix = PrefixPlacement.NONE)
        assertEquals("Hello end", rgb.plainText)
        assertEquals(0x123456, rgb.segments.first().color)
        assertEquals(setOf(MessageDecoration.BOLD), rgb.segments.first().decorations)
        assertEquals(null, rgb.segments.last().color)
        assertEquals(emptySet<MessageDecoration>(), rgb.segments.last().decorations)

        val legacy = presenter.render(null, "message", prefix = PrefixPlacement.NONE)
        assertEquals(0x555555, legacy.segments.first().color)
        val plain = presenter.render(null, "message", audience = MessageAudience.PLAIN, prefix = PrefixPlacement.NONE)
        assertEquals("Hello end", plain.plainText)
        assertEquals(setOf<Int?>(null), plain.segments.map { it.color }.toSet())
    }

    @Test
    fun `console retains success warning error colors without bold or invisible black`() {
        val presenter = presenter("&a&lSuccess &eWarning &cFailure &#000000Dark")
        val console = presenter.render(null, "message", audience = MessageAudience.CONSOLE, prefix = PrefixPlacement.NONE)
        assertEquals("Success Warning Failure Dark", console.plainText)
        assertEquals(listOf(0x55FF55, 0xFFFF55, 0xFF5555, 0xAAAAAA), console.segments.map { it.color })
        assertEquals(true, console.segments.all { it.decorations.isEmpty() })
    }

    @Test
    fun `whole replies receive one prefix but value fragments do not`() {
        val presenter = presenter("&fStatus\n&7Pending: {count}")

        assertEquals("[InviteCode] Status\nPending: 2", presenter.render(null, "message", mapOf("count" to "2")).plainText)
        assertEquals("[InviteCode]\nStatus\nPending: 2", presenter.render(
            null, "message", mapOf("count" to "2"), prefix = PrefixPlacement.OWN_LINE,
        ).plainText)
        assertEquals("Status\nPending: 2", presenter.render(
            null, "message", mapOf("count" to "2"), prefix = PrefixPlacement.NONE,
        ).plainText)
    }

    private fun presenter(template: String, prefix: String = "&7[&6InviteCode&7]&r") = MessagePresenter(
        LocalizedMessages("zh_TW", false, mapOf("zh_TW" to mapOf(
            "presentation.prefix" to prefix, "message" to template,
        ))),
    )
}
