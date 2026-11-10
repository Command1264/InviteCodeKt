package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.LocalizedMessages
import io.github.command1264.invitecode.platform.bukkit.PluginRuntimeState
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class RuntimeCommandOutputTest {
    @Test
    fun `console only rejects player routes while administrative command remains executable`() {
        val sent = mutableListOf<String>()
        val console = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommandSender::class.java)) { _, method, args ->
            when (method.name) {
                "hasPermission" -> true
                "sendMessage" -> { if (args?.firstOrNull() is String) sent.add(args[0] as String); null }
                else -> null
            }
        } as CommandSender
        val messages = LocalizedMessages("zh_TW", false, mapOf("zh_TW" to mapOf(
            "presentation.prefix" to "&7[&6InviteCode&7]&r",
            "runtime.player-only" to "&e此指令只能由玩家執行。",
        )))
        var doctorCalls = 0
        val executor = RuntimeCommandExecutor(PluginRuntimeState().apply { activate() }, { messages },
            reload = {}, migrateStorage = { _, _ -> },
            playerWorkflow = { _, _ -> error("console must not execute player workflow") },
            adminWorkflow = { _, _ -> true }, doctor = { doctorCalls++ },
        )
        val command = object : Command("invitecode") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>) = true
        }
        executor.onCommand(console, command, "ic", arrayOf("code"))
        assertEquals("[InviteCode] 此指令只能由玩家執行。", sent.single().replace(Regex("§."), ""))
        assertTrue(sent.single().contains("§e"))
        executor.onCommand(console, command, "ic", arrayOf("admin", "doctor"))
        assertEquals(1, doctorCalls)
        assertEquals(1, sent.size)
    }
}
