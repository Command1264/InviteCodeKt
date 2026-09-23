package io.github.command1264.invitecode.platform.bukkit

import org.bukkit.Server
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID

/** Routes server-wide work and entity work to the threads that own them. */
interface PlatformTaskDispatcher {
    fun track(player: Player)
    fun untrack(player: Player)
    fun onlinePlayers(): Collection<Player>
    fun onlinePlayerNames(): Collection<String>
    fun global(operation: () -> Unit)
    fun player(player: Player, operation: () -> Unit, retired: () -> Unit = {})
    fun repeatGlobal(delayTicks: Long, periodTicks: Long, operation: () -> Unit): AutoCloseable
    fun delayPlayer(player: Player, delayTicks: Long, operation: () -> Unit, retired: () -> Unit = {})
    fun onlinePlayer(playerId: UUID): Player?
    fun currentPlayer(playerId: UUID): Player?
    fun isManagedThread(): Boolean
}

class BukkitTaskDispatcher(private val plugin: Plugin) : PlatformTaskDispatcher {
    private val server: Server = plugin.server

    override fun track(player: Player) = Unit

    override fun untrack(player: Player) = Unit

    override fun onlinePlayers(): Collection<Player> = server.onlinePlayers

    override fun onlinePlayerNames(): Collection<String> = server.onlinePlayers.map(Player::getName)

    override fun global(operation: () -> Unit) {
        server.scheduler.runTask(plugin, Runnable(operation))
    }

    override fun player(player: Player, operation: () -> Unit, retired: () -> Unit) {
        global { if (player.isOnline) operation() else retired() }
    }

    override fun repeatGlobal(delayTicks: Long, periodTicks: Long, operation: () -> Unit): AutoCloseable {
        val task = server.scheduler.runTaskTimer(plugin, Runnable(operation), delayTicks, periodTicks)
        return AutoCloseable { task.cancel() }
    }

    override fun delayPlayer(player: Player, delayTicks: Long, operation: () -> Unit, retired: () -> Unit) {
        server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) operation() else retired()
        }, delayTicks)
    }

    override fun onlinePlayer(playerId: UUID): Player? = server.getPlayer(playerId)

    override fun currentPlayer(playerId: UUID): Player? = server.getPlayer(playerId)

    override fun isManagedThread(): Boolean = Bukkit.isPrimaryThread()
}
