package io.github.command1264.invitecode.test;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only PlaceholderAPI expansion whose value changes through a console command. */
public final class PapiProbePlugin extends JavaPlugin implements Listener {
    private final Set<UUID> ready = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> playersByName = new ConcurrentHashMap<>();
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
        getServer().getPluginManager().registerEvents(this, this);
        if (!new ProbeExpansion().register()) throw new IllegalStateException("Cannot register icprobe expansion");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playersByName.put(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!armed || !sender.equals(getServer().getConsoleSender()) || args.length != 2
            || !"ready".equals(args[0])) return false;
        UUID playerId = playersByName.get(args[1]);
        if (playerId == null) {
            sender.sendMessage("ICPROBE player-offline " + args[1]);
            return true;
        }
        ready.add(playerId);
        sender.sendMessage("ICPROBE ready " + args[1] + " yes");
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
