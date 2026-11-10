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
    fun `player report has one heading prefix for all fields`() {
        val sent = mutableListOf<String>()
        val spigot = object : org.bukkit.entity.Player.Spigot() {
            override fun sendMessage(vararg components: net.md_5.bungee.api.chat.BaseComponent) {
                sent.add(components.joinToString("") { it.toPlainText() })
            }
        }
        val player = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(org.bukkit.entity.Player::class.java)) { _, method, _ ->
            when (method.name) { "spigot" -> spigot; "getLocale" -> "en_US"; else -> null }
        } as org.bukkit.entity.Player
        val catalog = io.github.command1264.invitecode.platform.bukkit.LocalizedMessages("en_US", false,
            mapOf("en_US" to mapOf("presentation.prefix" to "[IC]", "title" to "Health", "body" to "Backend: SQLITE\nSchema: 9")))
        BukkitMessageOutput.sendReport(player, catalog, "title", listOf("body" to emptyMap()))
        assertEquals(1, Regex("\\[IC]").findAll(sent.joinToString("\n")).count())
        assertEquals("[IC] Health", sent.first())
        assertEquals("Backend: SQLITE\nSchema: 9", sent.last())
    }

    @Test
    fun `console report prefixes every field and leaves timestamps to the server`() {
        val sent = mutableListOf<String>()
        val console = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommandSender::class.java)) { _, method, args ->
            if (method.name == "sendMessage" && args?.firstOrNull() is String) sent.add(args[0] as String)
            null
        } as CommandSender
        val catalog = io.github.command1264.invitecode.platform.bukkit.LocalizedMessages("en_US", false,
            mapOf("en_US" to mapOf("presentation.prefix" to "[IC]", "title" to "Health", "body" to "Backend: SQLITE\nSchema: 9")))
        BukkitMessageOutput.sendReport(console, catalog, "title", listOf("body" to emptyMap()))
        assertEquals(3, sent.size)
        sent.forEach { line ->
            assertEquals(1, Regex("\\[IC]").findAll(line).count())
            assertFalse(line.contains("INFO") || line.contains('\n'))
        }
    }

    @Test
    fun `console sends each logical line separately with its own prefix and retained color`() {
        val sent = mutableListOf<String>()
        val console = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommandSender::class.java)) { _, method, args ->
            if (method.name == "sendMessage" && args?.firstOrNull() is String) sent.add(args[0] as String)
            null
        } as CommandSender
        val messages = io.github.command1264.invitecode.platform.bukkit.LocalizedMessages("en_US", false,
            mapOf("en_US" to mapOf("presentation.prefix" to "[InviteCode]", "test" to "&aFirst\n&cSecond\nThird")))
        BukkitMessageOutput.send(console, messages, "test")
        assertEquals(3, sent.size)
        assertFalse(sent.any { it.contains('\n') || it.contains('\r') })
        sent.forEach { assertEquals(1, Regex("\\[InviteCode]").findAll(it).count()) }
        org.junit.jupiter.api.Assertions.assertTrue(sent[1].contains("§cSecond"))
        org.junit.jupiter.api.Assertions.assertTrue(sent[2].contains("§cThird"))
    }

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
