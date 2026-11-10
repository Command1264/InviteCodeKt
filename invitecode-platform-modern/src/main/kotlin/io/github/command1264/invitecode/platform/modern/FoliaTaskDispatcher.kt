package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.platform.bukkit.BukkitTaskDispatcher
import io.github.command1264.invitecode.platform.bukkit.PlatformTaskDispatcher
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/** Reflective so the same Java 8 JAR can still load on Spigot 1.8.8. */
object PlatformTaskDispatchers {
    fun create(plugin: Plugin): PlatformTaskDispatcher =
        if (isFolia(plugin)) FoliaTaskDispatcher(plugin) else BukkitTaskDispatcher(plugin)

    private fun isFolia(plugin: Plugin): Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, plugin.server.javaClass.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}

private class FoliaTaskDispatcher(private val plugin: Plugin) : PlatformTaskDispatcher {
    private val global = plugin.server.javaClass.getMethod("getGlobalRegionScheduler").invoke(plugin.server)
    private val context = ThreadLocal<Any>()
    private val players = ConcurrentHashMap<UUID, Player>()
    private val playerNames = ConcurrentHashMap<UUID, String>()

    override fun track(player: Player) {
        players[player.uniqueId] = player
        playerNames[player.uniqueId] = player.name
    }

    override fun untrack(player: Player) {
        if (players.remove(player.uniqueId, player)) playerNames.remove(player.uniqueId)
    }

    override fun onlinePlayers(): Collection<Player> = players.values.toList()

    override fun onlinePlayerNames(): Collection<String> = playerNames.values.toList()

    private fun inContext(owner: Any, operation: () -> Unit) {
        val previous = context.get()
        context.set(owner)
        try {
            operation()
        } finally {
            if (previous == null) context.remove() else context.set(previous)
        }
    }

    override fun global(operation: () -> Unit) {
        val method = global.javaClass.getMethod("run", Plugin::class.java, Consumer::class.java)
        method.invoke(global, plugin, Consumer<Any> { inContext(GLOBAL, operation) })
    }

    override fun player(player: Player, operation: () -> Unit, retired: () -> Unit) {
        val scheduler = player.javaClass.getMethod("getScheduler").invoke(player)
        val method = scheduler.javaClass.getMethod(
            "run", Plugin::class.java, Consumer::class.java, Runnable::class.java,
        )
        val task = method.invoke(scheduler, plugin,
            Consumer<Any> { inContext(player.uniqueId, operation) }, Runnable { global(retired) })
        if (task == null) global(retired)
    }

    override fun repeatGlobal(delayTicks: Long, periodTicks: Long, operation: () -> Unit): AutoCloseable {
        val method = global.javaClass.getMethod(
            "runAtFixedRate", Plugin::class.java, Consumer::class.java,
            java.lang.Long.TYPE, java.lang.Long.TYPE,
        )
        val task = requireNotNull(method.invoke(global, plugin,
            Consumer<Any> { inContext(GLOBAL, operation) }, delayTicks, periodTicks))
        val taskType = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask")
        return AutoCloseable { taskType.getMethod("cancel").invoke(task) }
    }

    override fun delayPlayer(player: Player, delayTicks: Long, operation: () -> Unit, retired: () -> Unit) {
        val scheduler = player.javaClass.getMethod("getScheduler").invoke(player)
        val method = scheduler.javaClass.getMethod(
            "runDelayed", Plugin::class.java, Consumer::class.java, Runnable::class.java, java.lang.Long.TYPE,
        )
        val task = method.invoke(
            scheduler, plugin, Consumer<Any> { inContext(player.uniqueId, operation) },
            Runnable { global(retired) }, delayTicks,
        )
        if (task == null) global(retired)
    }

    override fun onlinePlayer(playerId: UUID): Player? = players[playerId]

    override fun currentPlayer(playerId: UUID): Player? =
        if (context.get() == playerId) players[playerId] else null

    override fun isManagedThread(): Boolean = context.get() != null

    private companion object {
        val GLOBAL = Any()
    }
}
