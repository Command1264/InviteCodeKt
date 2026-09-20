package io.github.command1264.invitecode.plugin

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class InviteCodePlugin : JavaPlugin() {
    override fun onEnable() {
        saveDefaultConfig()
        getCommand("invitecode")?.setExecutor(ScaffoldCommand())
        logger.info("InviteCodeKt ${description.version} 專案骨架已啟用；領域功能尚未實作。")
    }
}

private class ScaffoldCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        sender.sendMessage("InviteCodeKt 已安裝；目前的專案骨架尚未實作玩家操作流程。")
        return true
    }
}
