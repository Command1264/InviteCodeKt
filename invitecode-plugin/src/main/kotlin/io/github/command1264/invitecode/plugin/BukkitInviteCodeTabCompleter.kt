package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.InviteCodeTabCompletion
import io.github.command1264.invitecode.platform.bukkit.PlatformTaskDispatcher
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

internal class BukkitInviteCodeTabCompleter(
    private val tasks: PlatformTaskDispatcher,
    private val qualificationProfileIds: () -> Collection<String> = { emptyList() },
) : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> = InviteCodeTabCompletion.complete(
        arguments = args.toList(),
        player = sender is Player,
        hasPermission = sender::hasPermission,
        onlinePlayerNames = tasks.onlinePlayerNames().toList(),
        qualificationProfileIds = qualificationProfileIds(),
    )
}
