package io.github.command1264.invitecode.test;

import java.io.File;
import java.math.BigDecimal;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only, read-only observation of the registered Vault Economy provider. */
public final class VaultProbePlugin extends JavaPlugin {
    private boolean armed;

    @Override
    public void onEnable() {
        File instance;
        try {
            instance = getDataFolder().getCanonicalFile().getParentFile().getParentFile();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot resolve isolated Agent instance", error);
        }
        File project = instance.getParentFile();
        File audience = project == null ? null : project.getParentFile();
        File instances = audience == null ? null : audience.getParentFile();
        armed = project != null && "InviteCode".equals(project.getName())
            && audience != null && "Agent".equals(audience.getName())
            && instances != null && "instances".equals(instances.getName())
            && instance.getName().startsWith("issue12-")
            && new File(instance, "workspace.json").isFile()
            && "127.0.0.1".equals(getServer().getIp())
            && !getServer().getOnlineMode();
        if (!armed) getLogger().severe("Vault probe disabled outside an isolated loopback InstanceHarbor Agent instance.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!armed || !sender.equals(getServer().getConsoleSender()) || args.length != 1) return false;
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager()
            .getRegistration(Economy.class);
        if (registration == null) {
            sender.sendMessage("ICBALANCE unavailable");
            return true;
        }
        OfflinePlayer player = getServer().getOfflinePlayer(args[0]);
        double balance = registration.getProvider().getBalance(player);
        sender.sendMessage("ICBALANCE " + args[0] + " "
            + BigDecimal.valueOf(balance).stripTrailingZeros().toPlainString());
        return true;
    }
}
