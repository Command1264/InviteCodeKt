package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.MessageDecoration
import io.github.command1264.invitecode.platform.bukkit.MessageSegment
import io.github.command1264.invitecode.platform.bukkit.PresentedMessage
import net.md_5.bungee.api.ChatColor
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class BukkitMessageOutputTest {
    @Test
    fun `console preserves color without decoration and never executes text controls`() {
        val sent = mutableListOf<String>()
        val console = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommandSender::class.java)) { _, method, args ->
            if (method.name == "sendMessage" && args?.firstOrNull() is String) sent.add(args[0] as String)
            null
        } as CommandSender
        BukkitMessageOutput.sendPresented(console, PresentedMessage(listOf(
            MessageSegment("OK", 0x55FF55, setOf(MessageDecoration.BOLD)),
            MessageSegment(" &c literal §c text \u001B[31m", 0xAAAAAA),
        )))
        assertEquals("§r§aOK§r§7 &c literal &c text [31m§r", sent.single())
        assertFalse(sent.single().contains("§l"))
    }

    @Test
    fun `player components retain literal values and explicit formatting independently`() {
        val components = BukkitMessageOutput.components(PresentedMessage(listOf(
            MessageSegment("&c{player} §c\u001B", 0x55FF55, setOf(MessageDecoration.BOLD)),
            MessageSegment(" plain"),
        )))
        assertEquals(2, components.size)
        assertEquals("&c{player} &c", components[0].toPlainText())
        assertEquals(ChatColor.GREEN, components[0].color)
        assertEquals(true, components[0].isBold)
        assertEquals(false, components[1].isBold)
        assertEquals(ChatColor.WHITE, components[1].color)
        assertEquals(null, components[0].clickEvent)
    }
}
