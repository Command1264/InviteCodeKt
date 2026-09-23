package io.github.command1264.invitecode.test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only PlaceholderAPI expansion whose value changes through a console command. */
public final class PapiProbePlugin extends JavaPlugin {
    private final Set<UUID> ready = new HashSet<>();
    private boolean armed;

    @Override
    public void onEnable() {
        File instance = getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
        armed = instance.getName().startsWith("issue12-")
            && new File(instance, "workspace.json").isFile()
            && "127.0.0.1".equals(getServer().getIp())
            && !getServer().getOnlineMode();
        if (!armed) {
            getLogger().severe("Placeholder probe disabled outside an isolated loopback TestLab instance.");
            return;
        }
        if (!new ProbeExpansion().register()) throw new IllegalStateException("Cannot register icprobe expansion");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!armed || !sender.equals(getServer().getConsoleSender()) || args.length != 2
            || !"ready".equals(args[0])) return false;
        Player player = getServer().getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("ICPROBE player-offline " + args[1]);
            return true;
        }
        ready.add(player.getUniqueId());
        sender.sendMessage("ICPROBE ready " + args[1] + " "
            + PlaceholderAPI.setPlaceholders(player, "%icprobe_ready%"));
        return true;
    }

    private final class ProbeExpansion extends PlaceholderExpansion {
        @Override public String getIdentifier() { return "icprobe"; }
        @Override public String getAuthor() { return "InviteCodeTest"; }
        @Override public String getVersion() { return "1.0.0"; }
        @Override public boolean persist() { return true; }
        @Override public String onRequest(OfflinePlayer player, String identifier) {
            if (!"ready".equals(identifier) || player == null) return null;
            return ready.contains(player.getUniqueId()) ? "yes" : "no";
        }
    }
}
