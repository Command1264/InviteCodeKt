package io.github.command1264.invitecode.plugin

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class InviteCodePlugin : JavaPlugin() {
    override fun onEnable() {
        saveDefaultConfig()
        getCommand("invitecode")?.setExecutor(ScaffoldCommand())
        logger.info("InviteCodeKt ${description.version} scaffold enabled; domain features are not implemented yet.")
    }
}

private class ScaffoldCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        sender.sendMessage("InviteCodeKt is installed; this scaffold does not implement player workflows yet.")
        return true
    }
}
