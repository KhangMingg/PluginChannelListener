package me.craftgpt.pluginchannellogger;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PluginChannelLogger extends JavaPlugin {

    private boolean listening = true;
    private final Set<UUID> received = new HashSet<>();

    @Override
    public void onEnable() {
        // Load state
        listening = getConfig().getBoolean("listening", true);
        received.clear();

        // Print ascii art warning
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

        // ProtocolLib
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
                        String channels = new String(data);
                        getLogger().info("[PluginChannelLogger] " + event.getPlayer().getName() + " registered channels: " + channels);
                    } else {
                        getLogger().info("[PluginChannelLogger] " + event.getPlayer().getName() + " sent custom payload channel: " + channel);
                    }
                }
            }
        });

        // Listen for player quit to clean up
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                received.remove(event.getPlayer().getUniqueId());
            }
            @org.bukkit.event.EventHandler
            public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                // 1 tick later, check if player registered any channels
                Bukkit.getScheduler().runTaskLater(PluginChannelLogger.this, () -> {
                    if (!received.contains(event.getPlayer().getUniqueId())) {
                        getLogger().info("[PluginChannelLogger] " + event.getPlayer().getName() + " did not send any custom plugin channel.");
                    }
                }, 40L); // ~2 seconds
            }
        }, this);

        getLogger().info("[PluginChannelLogger] Listening for Plugin Channels!");
    }

    @Override
    public void onDisable() {
        getConfig().set("listening", listening);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("pcl")) return false;
        if (args.length == 0) {
            sender.sendMessage("§e/pcl start§7 - Start listening for plugin channels");
            sender.sendMessage("§e/pcl stop§7 - Stop listening for plugin channels");
            sender.sendMessage("§eCurrently: §a" + (listening ? "listening" : "not listening"));
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
        sender.sendMessage("§cUnknown subcommand. Use §e/pcl start§c or §e/pcl stop§c.");
        return true;
    }
}