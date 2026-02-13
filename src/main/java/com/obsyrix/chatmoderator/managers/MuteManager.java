package com.obsyrix.chatmoderator.managers;

import com.obsyrix.chatmoderator.ChatModerator;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class MuteManager {

    private final ChatModerator plugin;
    private File mutesFile;
    private FileConfiguration mutesConfig;

    public MuteManager(ChatModerator plugin) {
        this.plugin = plugin;
        reloadMutes();
    }

    private void reloadMutes() {
        mutesFile = new File(plugin.getDataFolder(), "mutes.yml");
        if (!mutesFile.exists()) {
            try {
                mutesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create mutes.yml!");
            }
        }
        mutesConfig = YamlConfiguration.loadConfiguration(mutesFile);
    }

    private void saveMutes() {
        try {
            mutesConfig.save(mutesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save mutes.yml!");
        }
    }

    public void mutePlayer(UUID uuid, long durationMillis, String reason) {
        mutePlayer(uuid, durationMillis, reason, "CONSOLE");
    }

    public void mutePlayer(UUID uuid, long durationMillis, String reason, String issuedByUuid) {
        long expiry = System.currentTimeMillis() + durationMillis;
        mutesConfig.set(uuid.toString() + ".expiry", expiry);
        mutesConfig.set(uuid.toString() + ".reason", reason);
        mutesConfig.set(uuid.toString() + ".punisher", issuedByUuid);
        saveMutes();

        // Resolve punisher name
        String punisherName = "Console";
        if (!issuedByUuid.equals("CONSOLE")) {
            try {
                UUID punisherUuid = UUID.fromString(issuedByUuid);
                org.bukkit.OfflinePlayer p = plugin.getServer().getOfflinePlayer(punisherUuid);
                if (p != null && p.getName() != null) punisherName = p.getName();
                else punisherName = "Unknown";
            } catch (IllegalArgumentException e) {
                punisherName = issuedByUuid; // Fallback if not a UUID
            }
        }

        // Log to database
        String username = plugin.getServer().getOfflinePlayer(uuid).getName();
        if (username == null)
            username = "Unknown";
        plugin.getDatabaseManager().logPunishment(uuid.toString(), username, "MUTE", durationMillis, reason, punisherName, issuedByUuid);

        // Broadcast to staff
        String durationStr = durationMillis == -1 ? "Permanent" : plugin.getMuteManager().getMuteTimeRemaining(uuid); 
        if (durationMillis != -1) {
             long s = durationMillis / 1000;
             durationStr = (s / 3600) + "h " + ((s % 3600) / 60) + "m";
        }
        
        String broadcastMsg = plugin.getConfig().getString("messages.mute-broadcast", "&8[&cChatModerator&8] &e%player% &7was muted by &e%punisher% &7for &e%duration%&7. Reason: &f%reason%")
                .replace("%player%", username)
                .replace("%punisher%", punisherName)
                .replace("%duration%", durationMillis == -1 ? "Permanent" : durationStr)
                .replace("%reason%", reason);

        org.bukkit.Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("chatmoderator.listen") || p.hasPermission("chatmoderator.admin"))
                .forEach(p -> p.sendMessage(plugin.colorize(broadcastMsg)));
        plugin.getLogger().info(ChatColor.stripColor(plugin.colorize(broadcastMsg)));

        // Notify the target player
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) {
             String msg = plugin.getConfig().getString("messages.mute-reason", "&cYou are muted for %duration%. Reason: %reason%");
             msg = msg.replace("%duration%", durationMillis == -1 ? "Permanent" : durationStr)
                      .replace("%reason%", reason);
             target.sendMessage(plugin.colorize(msg));
        }
    }

    public void unmutePlayer(UUID uuid) {
        mutesConfig.set(uuid.toString(), null);
        saveMutes();
    }

    public boolean isMuted(UUID uuid) {
        if (!mutesConfig.contains(uuid.toString()))
            return false;

        long expiry = mutesConfig.getLong(uuid.toString() + ".expiry");
        if (System.currentTimeMillis() > expiry) {
            unmutePlayer(uuid);
            return false;
        }
        return true;
    }

    public String getMuteReason(UUID uuid) {
        return mutesConfig.getString(uuid.toString() + ".reason", "No reason provided");
    }

    public String getMuteTimeRemaining(UUID uuid) {
        long expiry = mutesConfig.getLong(uuid.toString() + ".expiry");
        long remaining = expiry - System.currentTimeMillis();

        if (remaining <= 0)
            return "Expired";

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0)
            return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0)
            return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
    public java.util.Set<UUID> getMutedPlayers() {
        java.util.Set<UUID> muted = new java.util.HashSet<>();
        for (String key : mutesConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (isMuted(uuid)) {
                    muted.add(uuid);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return muted;
    }
}
