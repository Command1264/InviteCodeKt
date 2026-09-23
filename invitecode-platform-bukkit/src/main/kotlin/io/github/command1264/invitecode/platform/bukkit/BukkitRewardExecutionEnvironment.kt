package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.OnlineRewardActionResult
import io.github.command1264.invitecode.core.RewardExecutionEnvironment
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.entity.Player
import java.math.BigDecimal
import java.util.UUID

fun interface RewardMessageResolver {
    fun resolve(playerId: UUID, localeKey: String): String
}

fun interface EconomyRewardGateway {
    fun deposit(playerId: UUID, amount: BigDecimal): Boolean
}

class BukkitRewardExecutionEnvironment(
    private val server: Server,
    private val messages: RewardMessageResolver,
    private val economy: EconomyRewardGateway? = null,
    private val isPrimaryThread: () -> Boolean = Bukkit::isPrimaryThread,
    private val playerLookup: (UUID) -> Player? = server::getPlayer,
) : RewardExecutionEnvironment {
    override fun isOnline(playerId: UUID): Boolean {
        requireMainThread()
        return playerLookup(playerId)?.isOnline == true
    }

    override fun dispatchConsole(command: String): Boolean {
        requireMainThread()
        return server.dispatchCommand(server.consoleSender, command)
    }

    override fun dispatchPlayer(playerId: UUID, command: String): OnlineRewardActionResult {
        requireMainThread()
        val player = playerLookup(playerId)
            ?: return OnlineRewardActionResult.RECIPIENT_OFFLINE
        return if (player.performCommand(command)) {
            OnlineRewardActionResult.ACCEPTED
        } else {
            OnlineRewardActionResult.REJECTED
        }
    }

    override fun sendMessage(playerId: UUID, localeKey: String): OnlineRewardActionResult {
        requireMainThread()
        val player = playerLookup(playerId)
            ?: return OnlineRewardActionResult.RECIPIENT_OFFLINE
        player.sendMessage(messages.resolve(playerId, localeKey))
        return OnlineRewardActionResult.ACCEPTED
    }

    override fun economyAvailable(): Boolean {
        requireMainThread()
        return economy != null
    }

    override fun deposit(playerId: UUID, amount: BigDecimal): Boolean {
        requireMainThread()
        return requireNotNull(economy) { "Vault economy integration is unavailable." }
            .deposit(playerId, amount)
    }

    private fun requireMainThread() {
        check(isPrimaryThread()) {
            "Bukkit Reward operations must be called on a platform-owned thread by AsyncRewardDeliveryWorkflow."
        }
    }
}
