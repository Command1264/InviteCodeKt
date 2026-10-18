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
    fun `homepage keeps five entries and runs only read only help navigation`() {
        val capture = Capture()
        val player = permissionProxy(Player::class.java, basicPermissions, capture)
        execute(player, "ic", capture = capture)
        val clicks = capture.components.flatten().mapNotNull { it.clickEvent }
        assertEquals(setOf("/ic code", "/ic claim", "/ic status", "/ic stats", "/ic help"), clicks.map { it.value }.toSet())
        assertTrue(clicks.filter { it.value != "/ic help" }.all { it.action.name == "SUGGEST_COMMAND" })
        assertTrue(clicks.filter { it.value == "/ic help" }.all { it.action.name == "RUN_COMMAND" })
        assertEquals(1, Regex("\\[InviteCode]").findAll(capture.plainText()).count())
        assertFalse(capture.plainText().contains("confirm"))
        assertFalse(capture.plainText().contains("cancel"))
        assertFalse(capture.plainText().contains("<"))
        assertEquals(0, capture.operations)
    }

    @Test
    fun `admin homepage adds one read only help entrance rather than twenty usages`() {
        val capture = Capture()
        val player = permissionProxy(Player::class.java, CommandHelp.entries.map { it.permission }.toSet(), capture)
        execute(player, "invitecode", capture = capture)
        val clicks = capture.components.flatten().mapNotNull { it.clickEvent }.distinctBy { it.value }
        assertEquals(6, clicks.size)
        assertTrue(clicks.any { it.value == "/invitecode help admin" && it.action.name == "RUN_COMMAND" })
        assertTrue(clicks.none { it.value.contains("unlink") || it.value.contains("migrate") })
        assertEquals(0, capture.operations)
    }

    @Test
    fun `management page navigation runs only help while detail suggests operational usage`() {
        val permissions = CommandHelp.entries.map { it.permission }.toSet()
        val capture = Capture()
        val player = permissionProxy(Player::class.java, permissions, capture)
        execute(player, "ic", args = arrayOf("help", "admin"), capture = capture)
        val clicks = capture.components.flatten().mapNotNull { it.clickEvent }
        assertTrue(capture.plainText().contains("1/2"))
        assertTrue(clicks.any { it.value == "/ic help admin 2" })
        assertTrue(clicks.all { it.action.name == "RUN_COMMAND" && (it.value.startsWith("/ic help") || it.value == "/ic") })
        val detail = Capture()
        val detailPlayer = permissionProxy(Player::class.java, permissions, detail)
        execute(detailPlayer, "ic", args = arrayOf("help", "admin", "invitation", "transfer"), capture = detail)
        assertTrue(detail.plainText().contains("/ic admin invitation transfer <invitation-uuid> <player-reference> <reason>"))
        assertTrue(detail.components.flatten().any { it.clickEvent?.value?.startsWith("/ic admin invitation transfer") == true &&
            it.clickEvent?.action?.name == "SUGGEST_COMMAND" })
        assertTrue(detail.components.flatten().filter { it.clickEvent?.action?.name == "RUN_COMMAND" }
            .all { it.clickEvent!!.value.startsWith("/ic help") })
        assertEquals(0, capture.operations + detail.operations)
        assertEquals(1, Regex("\\[InviteCode]").findAll(detail.plainText()).count())
    }

    @Test
    fun `partial admin cannot see or open a forbidden help branch`() {
        val permissions = basicPermissions + "invitecode.admin.qualification"
        val capture = Capture()
        val player = permissionProxy(Player::class.java, permissions, capture)
        execute(player, "ic", args = arrayOf("help", "admin", "invitation"), capture = capture)
        assertTrue(capture.plainText().contains("force-qualify"))
        assertFalse(capture.plainText().contains("transfer"))
        assertFalse(capture.plainText().contains("unlink"))
        val denied = Capture()
        execute(permissionProxy(Player::class.java, permissions, denied), "ic",
            args = arrayOf("help", "admin", "invitation", "unlink"), capture = denied)
        assertTrue(denied.plainText().contains("No such help page"))
        assertFalse(denied.plainText().contains("<reason>"))
    }

    @Test
    fun `console gets short management index and explicit out of range failure`() {
        val capture = Capture()
        val sender = permissionProxy(CommandSender::class.java, CommandHelp.entries.map { it.permission }.toSet(), capture)
        execute(sender, "ic", args = arrayOf("help", "admin", "2"), capture = capture)
        assertTrue(capture.plainText().contains("2/2"))
        assertTrue(capture.plainText().contains("storage"))
        assertFalse(capture.plainText().contains("claim"))
        assertTrue(capture.components.isEmpty())
        assertEquals(1, Regex("\\[InviteCode]").findAll(capture.plainText()).count())
        val invalid = Capture()
        execute(permissionProxy(CommandSender::class.java, CommandHelp.entries.map { it.permission }.toSet(), invalid),
            "ic", args = arrayOf("help", "admin", "999"), capture = invalid)
        assertTrue(invalid.plainText().contains("No such help page"))
    }

    @Test
    fun `description formatting remains independent from the short usage row`() {
        val capture = Capture()
        val permissions = basicPermissions + "invitecode.admin.inspect"
        val player = permissionProxy(Player::class.java, permissions, capture)
        val key = CommandHelp.entries.single { it.id == InviteCodeCommandId.ADMIN_INSPECT_PLAYER }.descriptionKey
        execute(player, "ic", messageCatalog(mapOf("runtime.help-row" to "&l{usage} &7— &l{description}",
            key to "&cInspect &rPlayer")), args = arrayOf("help", "admin", "inspect"), capture = capture)
        assertFalse(capture.plainText().contains("&c"))
        assertTrue(capture.components.flatten().any { it.toPlainText() == "Inspect " && it.color == ChatColor.RED && !it.isBold })
        assertTrue(capture.components.flatten().any { it.toPlainText() == "Player" && it.color == ChatColor.WHITE && !it.isBold })
    }

    @Test
    fun `invalid help topics pages and excess arguments use help error without executing anything`() {
        listOf(arrayOf("help", "typo"), arrayOf("help", "admin", "0"),
            arrayOf("help", "admin", "invitation", "transfer", "extra")).forEach { args ->
            val capture = Capture()
            execute(permissionProxy(Player::class.java, basicPermissions, capture), "ic", args = args, capture = capture)
            assertTrue(capture.plainText().contains("No such help page"))
            assertEquals(0, capture.operations)
        }
    }

    @Test
    fun `help frames keep inline prefix and all navigation slots without disabled events`() {
        listOf(emptyArray(), arrayOf("help", "admin"), arrayOf("help", "admin", "2"),
            arrayOf("help", "admin", "invitation", "transfer")).forEach { args ->
            val capture = Capture()
            val permissions = CommandHelp.entries.map { it.permission }.toSet()
            execute(permissionProxy(Player::class.java, permissions, capture), "ic", args = args, capture = capture)
            val first = capture.components.first().joinToString("") { it.toPlainText() }
            assertTrue(first.contains("[InviteCode]") && first.contains("────"))
            val footer = capture.components.last()
            assertEquals("[Previous]  [Back]  [Next]", footer.joinToString("") { it.toPlainText() })
            assertTrue(footer.filter { it.clickEvent != null }.all {
                it.color == ChatColor.YELLOW && it.hoverEvent != null &&
                    it.clickEvent.action.name == "RUN_COMMAND" &&
                    (it.clickEvent.value.startsWith("/ic help") || it.clickEvent.value == "/ic")
            })
            val disabled = if (args.isEmpty()) listOf("Previous", "Back", "Next")
                else if (args.last() == "2") listOf("Next") else if (args.last() == "transfer") listOf("Previous", "Next")
                else listOf("Previous")
            disabled.forEach { name ->
                val parts = footer.filter { it.toPlainText().contains(name) }
                assertTrue(parts.isNotEmpty())
                assertTrue(parts.all { it.clickEvent == null && it.hoverEvent == null && it.color == ChatColor.DARK_GRAY })
            }
            assertEquals(1, Regex("\\[InviteCode]").findAll(capture.plainText()).count())
        }
    }

    @Test
    fun `empty custom prefix stays empty in frame and console retains all slots`() {
        val capture = Capture()
        execute(permissionProxy(CommandSender::class.java, basicPermissions, capture), "ic",
            messageCatalog(mapOf("presentation.prefix" to "")), capture = capture)
        assertFalse(capture.plainText().contains("[InviteCode]"))
        assertTrue(capture.plainText().contains("──── Home"))
        assertTrue(capture.plainText().contains("[Previous]  [Back]  [Next]"))
        assertEquals(0, capture.operations)
    }

    @Test
    fun `reload detail return button points to its second management page`() {
        val capture = Capture()
        val player = permissionProxy(Player::class.java, CommandHelp.entries.map { it.permission }.toSet(), capture)
        execute(player, "ic", args = arrayOf("help", "admin", "reload"), capture = capture)
        assertTrue(capture.components.last().any { it.clickEvent?.value == "/ic help admin 2" })
        assertEquals(0, capture.operations)
    }

    private val basicPermissions = setOf("invitecode.command.basic", "invitecode.command.code", "invitecode.command.claim",
        "invitecode.command.status", "invitecode.command.stats")

    private fun execute(sender: CommandSender, label: String, catalog: LocalizedMessages = messageCatalog(),
        args: Array<String> = emptyArray(), capture: Capture = Capture()) {
        val runtime = PluginRuntimeState().apply { activate() }
        val executor = RuntimeCommandExecutor(
            runtime = runtime,
            messages = { catalog },
            reload = { capture.operations++ },
            migrateStorage = { _, _ -> capture.operations++ },
            playerWorkflow = { _, _ -> capture.operations++; true },
            adminWorkflow = { _, _ -> capture.operations++; true },
            doctor = { capture.operations++ },
        )
        val command = object : Command("invitecode") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>) = true
        }
        assertTrue(executor.onCommand(sender, command, label, args))
    }

    private fun messageCatalog(overrides: Map<String, String> = emptyMap()): LocalizedMessages {
        val catalog = mutableMapOf(
            "presentation.prefix" to "[InviteCode]",
            "runtime.raw-output" to "{text}",
            "runtime.help-header" to "Help",
            "runtime.help-home-header" to "Home",
            "runtime.help-frame-header" to "&8──── {prefix}&6{title} &8────",
            "runtime.help-frame-divider" to "&8────────────────────────",
            "runtime.help-navigation-enabled" to "&e[{text}]",
            "runtime.help-navigation-disabled" to "&8[{text}]",
            "runtime.help-navigation-hover" to "Open help page",
            "runtime.help-page-header" to "{topic} {page}/{pages}",
            "runtime.help-page-error" to "No such help page",
            "runtime.help-open-detail" to "Open help",
            "runtime.help-previous" to "Previous", "runtime.help-next" to "Next", "runtime.help-back" to "Back",
            "runtime.help-player-section" to "Players",
            "runtime.help-admin-section" to "Administration",
            "runtime.help-row" to "{usage} - {description} ({usage})",
        )
        CommandHelp.entries.forEach { entry ->
            catalog[entry.descriptionKey] = "Description:${entry.descriptionKey.substringAfterLast('.')}"
            catalog[entry.descriptionKey.replace("help-description", "help-detail")] = "{usage}\nDetails\nExample: {example}"
        }
        listOf("player", "admin", "inspect", "invitation", "eligibility", "reward", "code", "storage", "reload", "doctor")
            .forEach { topic -> catalog["runtime.help-topic.$topic"] = "Topic:$topic"; catalog["runtime.help-topic-description.$topic"] = "Category:$topic" }
        catalog.putAll(overrides)
        return LocalizedMessages("en_US", false, mapOf("en_US" to catalog))
    }

    private class Capture {
        var operations = 0
        val lines = mutableListOf<String>()
        val components = mutableListOf<List<BaseComponent>>()

        fun playerSpigot(): Player.Spigot = object : Player.Spigot() {
            override fun sendMessage(vararg components: BaseComponent) {
                this@Capture.components.add(components.toList())
            }
        }

        fun plainText() = (lines.joinToString("\n") + components.joinToString("\n") { row ->
            row.joinToString("") { it.toPlainText() } }).replace(Regex("§[0-9a-fk-or]"), "")
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
