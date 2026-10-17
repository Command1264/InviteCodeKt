package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.CommandHelp
import io.github.command1264.invitecode.platform.bukkit.InviteCodeCommandId
import io.github.command1264.invitecode.platform.bukkit.LocalizedMessages
import io.github.command1264.invitecode.platform.bukkit.PluginRuntimeState
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class RuntimeCommandHelpTest {
    @Test
    fun `ordinary player sees all seven player and common usages with one prefix`() {
        val capture = Capture()
        val player = permissionProxy(Player::class.java, setOf(
            "invitecode.command.basic", "invitecode.command.code", "invitecode.command.claim",
            "invitecode.command.status", "invitecode.command.stats",
        ), capture)
        execute(player, "ic")
        val clicks = capture.components.flatten().mapNotNull { it.clickEvent }
        assertEquals(setOf("/ic help", "/ic code", "/ic claim [code]", "/ic confirm",
            "/ic cancel", "/ic status", "/ic stats"), clicks.map { it.value }.toSet())
        assertTrue(clicks.all { it.action.name == "SUGGEST_COMMAND" })
        assertEquals(1, Regex("\\[InviteCode]").findAll(capture.plainText()).count())
        assertFalse(capture.plainText().contains("Administration"))
    }

    @Test
    fun `fully permitted player sees all twenty descriptions and suggested usages`() {
        val capture = Capture()
        val player = permissionProxy(Player::class.java, CommandHelp.entries.map { it.permission }.toSet(), capture)
        execute(player, "invitecode")
        val clicks = capture.components.flatten().mapNotNull { it.clickEvent }
        assertEquals(20, clicks.map { it.value }.toSet().size)
        CommandHelp.entries.forEach { entry ->
            assertTrue(clicks.any { it.value == "/invitecode ${entry.usage}" }, entry.usage)
            assertTrue(capture.plainText().contains("Description:${entry.descriptionKey.substringAfterLast('.')}"))
        }
        assertTrue(clicks.all { it.action.name == "SUGGEST_COMMAND" })
        assertEquals(1, Regex("\\[InviteCode]").findAll(capture.plainText()).count())
    }

    @Test
    fun `player help renders filtered localized rows with suggest-only usage clicks`() {
        val permissions = setOf(
            "invitecode.command.basic",
            "invitecode.command.claim",
            "invitecode.admin.inspect",
        )
        val capture = Capture()
        val permissionAwarePlayer = permissionProxy(Player::class.java, permissions, capture)
        execute(permissionAwarePlayer, "ic")

        val allText = capture.plainText()
        assertTrue(allText.contains("Description:help"))
        assertTrue(allText.contains("Description:claim"))
        assertTrue(allText.contains("Description:inspect-player"))
        assertFalse(allText.contains("Description:code"))
        assertFalse(allText.contains("Description:doctor"))
        assertTrue(allText.indexOf("Description:help") < allText.indexOf("Players"))
        assertTrue(allText.indexOf("Players") < allText.indexOf("Description:claim"))
        assertTrue(allText.indexOf("Description:claim") < allText.indexOf("Administration"))
        assertTrue(allText.indexOf("Administration") < allText.indexOf("Description:inspect-player"))

        val clicks = capture.components.flatten().mapNotNull { it.clickEvent }
        assertTrue(clicks.isNotEmpty(), "visible help usage must be clickable")
        assertTrue(clicks.all { it.action.name == "SUGGEST_COMMAND" })
        assertEquals(2, clicks.count { it.value == "/ic claim [code]" })
        assertTrue(clicks.none { it.action.name == "RUN_COMMAND" })
    }

    @Test
    fun `console help hides player-only routes and keeps each available command description`() {
        val permissions = CommandHelp.entries.map { it.permission }.toSet()
        val capture = Capture()
        val console = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommandSender::class.java)) { _, method, args ->
            when (method.name) {
                "hasPermission" -> permissions.contains(args?.get(0))
                "sendMessage" -> {
                    if (args?.firstOrNull() is String) capture.lines.add(args[0] as String)
                    null
                }
                "getName" -> "CONSOLE"
                "isOp" -> true
                else -> defaultValue(method.returnType)
            }
        } as CommandSender

        execute(console, "invitecode")

        val output = capture.lines.joinToString("\n")
        assertTrue(output.contains("Description:doctor"))
        assertTrue(output.contains("Description:inspect-invitation"))
        assertTrue(output.contains("Description:help"))
        assertFalse(output.contains("Players"))
        assertTrue(output.contains("Administration"))
        assertFalse(output.contains("Description:code"))
        assertFalse(output.contains("Description:claim"))
        assertFalse(output.contains("Description:status"))
    }

    @Test
    fun `common help row remains visible without empty audience sections`() {
        val capture = Capture()
        val player = permissionProxy(Player::class.java, setOf("invitecode.command.basic"), capture)

        execute(player, "ic")

        val output = capture.plainText()
        assertTrue(output.contains("Description:help"))
        assertFalse(output.contains("Players"))
        assertFalse(output.contains("Administration"))
        assertTrue(capture.components.flatten().any {
            it.clickEvent?.action?.name == "SUGGEST_COMMAND" && it.clickEvent?.value == "/ic help"
        })
    }

    @Test
    fun `description formatting and reset remain independent from row style`() {
        val capture = Capture()
        val permissions = setOf("invitecode.command.basic", "invitecode.admin.inspect")
        val player = permissionProxy(Player::class.java, permissions, capture)
        val key = CommandHelp.entries.single { it.id == InviteCodeCommandId.ADMIN_INSPECT_PLAYER }.descriptionKey

        execute(
            player,
            "ic",
            messageCatalog(mapOf(
                "runtime.help-row" to "&l{usage} &7— &l{description}",
                key to "&cInspect &rPlayer",
            )),
        )

        val output = capture.plainText()
        assertFalse(output.contains("&c"))
        assertFalse(output.contains("&r"))
        val components = capture.components.flatten()
        assertTrue(components.any { it.toPlainText() == "Inspect " && it.color == ChatColor.RED && !it.isBold })
        assertTrue(components.any { it.toPlainText() == "Player" && it.color == ChatColor.WHITE && !it.isBold })
        assertTrue(components.any { it.toPlainText().startsWith("/ic admin inspect player") && it.isBold })
    }

    private fun execute(sender: CommandSender, label: String, catalog: LocalizedMessages = messageCatalog()) {
        val runtime = PluginRuntimeState().apply { activate() }
        val executor = RuntimeCommandExecutor(
            runtime = runtime,
            messages = { catalog },
            reload = {},
            migrateStorage = { _, _ -> },
            playerWorkflow = { _, _ -> true },
            adminWorkflow = { _, _ -> true },
            doctor = {},
        )
        val command = object : Command("invitecode") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>) = true
        }
        assertTrue(executor.onCommand(sender, command, label, arrayOf("help")))
    }

    private fun messageCatalog(overrides: Map<String, String> = emptyMap()): LocalizedMessages {
        val catalog = mutableMapOf(
            "presentation.prefix" to "[InviteCode]",
            "runtime.raw-output" to "{text}",
            "runtime.help-header" to "Help",
            "runtime.help-player-section" to "Players",
            "runtime.help-admin-section" to "Administration",
            "runtime.help-row" to "{usage} - {description} ({usage})",
        )
        CommandHelp.entries.forEach { entry ->
            catalog[entry.descriptionKey] = "Description:${entry.descriptionKey.substringAfterLast('.')}"
        }
        catalog.putAll(overrides)
        return LocalizedMessages("en_US", false, mapOf("en_US" to catalog))
    }

    private class Capture {
        val lines = mutableListOf<String>()
        val components = mutableListOf<List<BaseComponent>>()

        fun playerSpigot(): Player.Spigot = object : Player.Spigot() {
            override fun sendMessage(vararg components: BaseComponent) {
                this@Capture.components.add(components.toList())
            }
        }

        fun plainText() = lines.joinToString("\n") + components.flatten()
            .joinToString("\n") { it.toPlainText() }
    }

    private fun <T> permissionProxy(type: Class<T>, permissions: Set<String>, capture: Capture): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "hasPermission" -> permissions.contains(args?.get(0))
                "spigot" -> capture.playerSpigot()
                "sendMessage" -> {
                    if (args?.firstOrNull() is String) capture.lines.add(args[0] as String)
                    null
                }
                "getName" -> "Tester"
                "isOnline" -> true
                else -> defaultValue(method.returnType)
            }
        } as T

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0.0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
