package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.OnlineRewardActionResult
import io.github.command1264.invitecode.platform.bukkit.BukkitRewardExecutionEnvironment
import io.github.command1264.invitecode.platform.bukkit.RewardMessageResolver
import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.Server
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.UUID

class RewardMessagePresentationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `MESSAGE reward uses the same formatted player output exactly once`() {
        val sent = mutableListOf<Array<out BaseComponent>>()
        val spigot = object : Player.Spigot() {
            override fun sendMessage(vararg components: BaseComponent) { sent.add(components) }
            override fun getLocale(): String = "zh_TW"
        }
        val player = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) { "spigot" -> spigot; "isOnline" -> true; else -> null }
        } as Player
        val server = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Server::class.java)) { _, _, _ -> null } as Server
        val messages = BukkitConfigurationLoader(directory) {}.load().messages
        val environment = BukkitRewardExecutionEnvironment(server, RewardMessageResolver { _, key -> key },
            isPrimaryThread = { true }, playerLookup = { player },
            messageDelivery = { target, key -> BukkitMessageOutput.send(target, messages, key) },
        )

        assertEquals(OnlineRewardActionResult.ACCEPTED, environment.sendMessage(UUID.randomUUID(), "invite.qualified.invitee"))
        assertEquals(1, sent.size)
        assertEquals("[InviteCode] 你已達成邀請資格。", sent.single().joinToString("") { it.toPlainText() })
        assertEquals(OnlineRewardActionResult.RECIPIENT_OFFLINE,
            BukkitRewardExecutionEnvironment(server, RewardMessageResolver { _, key -> key },
                isPrimaryThread = { true }, playerLookup = { null },
                messageDelivery = { _, _ -> error("offline delivery must not run") },
            ).sendMessage(UUID.randomUUID(), "invite.qualified.invitee"))
        assertEquals(1, sent.size)
    }
}
