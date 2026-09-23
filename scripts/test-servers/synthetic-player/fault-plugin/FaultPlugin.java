package io.github.command1264.invitecode.test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only external effect that terminates its isolated server before the caller can commit success. */
public final class FaultPlugin extends JavaPlugin {
    private boolean armed;

    @Override
    public void onEnable() {
        File instance = getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
        armed = instance.getName().startsWith("issue12-")
            && new File(instance, "workspace.json").isFile()
            && "127.0.0.1".equals(getServer().getIp())
            && !getServer().getOnlineMode();
        if (!armed) {
            getLogger().severe("Fault probe disabled outside an isolated loopback TestLab instance.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!armed || !sender.equals(getServer().getConsoleSender()) || args.length != 0) return false;
        getDataFolder().mkdirs();
        File marker = new File(getDataFolder(), "effect-entered.log");
        boolean repeated = marker.isFile() && marker.length() > 0;
        try (FileOutputStream output = new FileOutputStream(marker, true)) {
            output.write("effect-entered\n".getBytes(StandardCharsets.US_ASCII));
            output.getFD().sync();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot persist fault-injection marker", error);
        }
        if (!repeated) Runtime.getRuntime().halt(83);
        return true;
    }
}
