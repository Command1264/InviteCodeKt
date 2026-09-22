package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.OnlineRewardActionResult
import io.github.command1264.invitecode.core.RewardExecutionEnvironment
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.plugin.Plugin
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

fun interface RewardMessageResolver {
    fun resolve(playerId: UUID, localeKey: String): String
}

fun interface EconomyRewardGateway {
    fun deposit(playerId: UUID, amount: BigDecimal): Boolean
}

class BukkitMainThreadExecutor(
    private val server: Server,
    private val plugin: Plugin,
) {
    fun <T> call(operation: () -> T): T {
        if (Bukkit.isPrimaryThread()) return operation()
        return try {
            server.scheduler.callSyncMethod(plugin, Callable(operation)).get()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for a Bukkit main-thread Reward operation.", error)
        } catch (error: ExecutionException) {
            val cause = error.cause ?: error
            if (cause is RuntimeException) throw cause
            throw IllegalStateException("Bukkit main-thread Reward operation failed.", cause)
        }
    }
}

class BukkitRewardExecutionEnvironment(
    private val server: Server,
    private val mainThread: BukkitMainThreadExecutor,
    private val messages: RewardMessageResolver,
    private val economy: EconomyRewardGateway? = null,
) : RewardExecutionEnvironment {
    override fun isOnline(playerId: UUID): Boolean = mainThread.call {
        server.getPlayer(playerId)?.isOnline == true
    }

    override fun dispatchConsole(command: String): Boolean = mainThread.call {
        server.dispatchCommand(server.consoleSender, command)
    }

    override fun dispatchPlayer(playerId: UUID, command: String): OnlineRewardActionResult = mainThread.call {
        val player = server.getPlayer(playerId)
            ?: return@call OnlineRewardActionResult.RECIPIENT_OFFLINE
        if (player.performCommand(command)) {
            OnlineRewardActionResult.ACCEPTED
        } else {
            OnlineRewardActionResult.REJECTED
        }
    }

    override fun sendMessage(playerId: UUID, localeKey: String): OnlineRewardActionResult = mainThread.call {
        val player = server.getPlayer(playerId)
            ?: return@call OnlineRewardActionResult.RECIPIENT_OFFLINE
        player.sendMessage(messages.resolve(playerId, localeKey))
        OnlineRewardActionResult.ACCEPTED
    }

    override fun economyAvailable(): Boolean = economy != null

    override fun deposit(playerId: UUID, amount: BigDecimal): Boolean = mainThread.call {
        requireNotNull(economy) { "Vault economy integration is unavailable." }
            .deposit(playerId, amount)
    }
}
