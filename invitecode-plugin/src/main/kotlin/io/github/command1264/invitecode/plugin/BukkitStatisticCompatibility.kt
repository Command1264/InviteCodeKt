package io.github.command1264.invitecode.plugin

import org.bukkit.Statistic
import org.bukkit.entity.Player

internal object BukkitStatisticCompatibility {
    private val playTimeStatistic: Statistic by lazy {
        val name = resolvePlayTimeStatisticName(Statistic.values().map(Statistic::name))
        Statistic.valueOf(name)
    }

    fun playTimeTicks(player: Player): Long = player.getStatistic(playTimeStatistic).toLong()

    internal fun resolvePlayTimeStatisticName(availableNames: Collection<String>): String = when {
        MODERN_PLAY_TIME_STATISTIC in availableNames -> MODERN_PLAY_TIME_STATISTIC
        LEGACY_PLAY_TIME_STATISTIC in availableNames -> LEGACY_PLAY_TIME_STATISTIC
        else -> error("This Bukkit version does not expose a supported play-time statistic.")
    }

    private const val MODERN_PLAY_TIME_STATISTIC = "PLAY_ONE_MINUTE"
    private const val LEGACY_PLAY_TIME_STATISTIC = "PLAY_ONE_TICK"
}
