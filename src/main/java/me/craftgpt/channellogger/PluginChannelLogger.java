package me.craftgpt.channellogger;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PluginChannelLogger extends JavaPlugin implements Listener {

    public enum LogMode { CONSOLE, LOG, BOTH }
    private LogMode logMode = LogMode.BOTH;
    private boolean listening = true;
    private final Set<UUID> received = new HashSet<>();

    @Override
    public void onEnable() {
        // Load state from config
        listening = getConfig().getBoolean("listening", true);
        String logModeStr = getConfig().getString("logMode", "BOTH");
        try {
            logMode = LogMode.valueOf(logModeStr.toUpperCase());
        } catch (Exception e) {
            logMode = LogMode.BOTH;
        }
        received.clear();

        // ASCII art warning
        getLogger().warning(
                "\n" +
                " _       __                 _             __\n" +
                "| |     / /___ __________  (_)___  ____ _/ /\n" +
                "| | /| / / __ `/ ___/ __ \\/ / __ \\/ __ `/ / \n" +
                "| |/ |/ / /_/ / /  / / / / / / / / /_/ /_/  \n" +
                "|__/|__/\\__,_/_/  /_/ /_/_/_/ /_/\\__, (_)   \n" +
                "                                /____/      \n" +
                "Warning!\n" +
                "This mod is only meant for development purpose and not suitable for normal smp!\n"
        );
        // Register ProtocolLib packet listener
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Client.CUSTOM_PAYLOAD) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!listening) return;

                String channel = event.getPacket().getStrings().size() > 0 ? event.getPacket().getStrings().read(0) : null;
                UUID uuid = event.getPlayer().getUniqueId();
                received.add(uuid);

                if (channel != null && event.getPacket().getByteArrays().size() > 0) {
                    byte[] data = event.getPacket().getByteArrays().read(0);
                    if ("minecraft:register".equals(channel)) {
                        String channels = new String(data, StandardCharsets.UTF_8);
                        String logMsg = "[PluginChannelLogger] " + event.getPlayer().getName() + " registered channels: " + channels;
                        pluginLog(logMsg);
                    } else {
                        String logMsg = "[PluginChannelLogger] " + event.getPlayer().getName() + " sent custom payload channel: " + channel;
                        pluginLog(logMsg);
                    }
                }
            }
        });

        // Register event listener for join/quit
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("[PluginChannelLogger] Listening for Plugin Channels!");
    }

    @Override
    public void onDisable() {
        getConfig().set("listening", listening);
        getConfig().set("logMode", logMode.name());
        saveConfig();
    }

    // Listen for player join/quit to check for channel registration
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        received.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!received.contains(event.getPlayer().getUniqueId())) {
                String logMsg = "[PluginChannelLogger] " + event.getPlayer().getName() + " did not send any custom plugin channel.";
                pluginLog(logMsg);
            }
        }, 40L); // ~2 seconds after join
    }

    // Handle commands
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("pcl")) return false;
        if (args.length == 0) {
            sender.sendMessage("§e/pcl start§7 - Start listening for plugin channels");
            sender.sendMessage("§e/pcl stop§7 - Stop listening for plugin channels");
            sender.sendMessage("§e/pcl logto <console|log|both>§7 - Set log output");
            sender.sendMessage("§eCurrently: §a" + (listening ? "listening" : "not listening") + " §7| Log mode: §a" + logMode.name());
            return true;
        }
        if (args[0].equalsIgnoreCase("start")) {
            listening = true;
            getConfig().set("listening", true);
            saveConfig();
            sender.sendMessage("§aPluginChannelLogger is now listening for plugin channels.");
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            listening = false;
            getConfig().set("listening", false);
            saveConfig();
            sender.sendMessage("§cPluginChannelLogger has stopped listening for plugin channels.");
            return true;
        }
        if (args[0].equalsIgnoreCase("logto")) {
            if (args.length < 2) {
                sender.sendMessage("§eUsage: /pcl logto <console|log|both>");
                return true;
            }
            String arg = args[1].toLowerCase();
            switch (arg) {
                case "console":
                    logMode = LogMode.CONSOLE;
                    break;
                case "log":
                    logMode = LogMode.LOG;
                    break;
                case "both":
                    logMode = LogMode.BOTH;
                    break;
                default:
                    sender.sendMessage("§cInvalid log mode. Use console, log, or both.");
                    return true;
            }
            getConfig().set("logMode", logMode.name());
            saveConfig();
            sender.sendMessage("§aPluginChannelLogger log mode set to: " + logMode.name());
            return true;
        }
        sender.sendMessage("§cUnknown subcommand. Use §e/pcl start§c, §e/pcl stop§c, or §e/pcl logto <console|log|both>§c.");
        return true;
    }

    // Unified logging method
    private void pluginLog(String msg) {
        switch (logMode) {
            case CONSOLE:
                getLogger().info(msg);
                break;
            case LOG:
                logToFile(msg);
                break;
            case BOTH:
                getLogger().info(msg);
                logToFile(msg);
                break;
        }
    }

    // Log to file method
    private void logToFile(String msg) {
        try {
            java.nio.file.Path logPath = getDataFolder().toPath().resolve("PlayerPluginChannel.log");
            Files.createDirectories(logPath.getParent());
            Files.write(
                logPath,
                (msg + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            getLogger().warning("Failed to write to PlayerPluginChannel.log: " + e.getMessage());
        }
    }
}