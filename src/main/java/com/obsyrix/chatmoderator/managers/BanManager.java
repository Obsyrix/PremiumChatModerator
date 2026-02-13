package com.obsyrix.chatmoderator.managers;

import com.obsyrix.chatmoderator.ChatModerator;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class BanManager {

    private final ChatModerator plugin;
    private File bansFile;
    private FileConfiguration bansConfig;

    public BanManager(ChatModerator plugin) {
        this.plugin = plugin;
        reloadBans();
    }

    private void reloadBans() {
        bansFile = new File(plugin.getDataFolder(), "bans.yml");
        if (!bansFile.exists()) {
            try {
                bansFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create bans.yml!");
            }
        }
        bansConfig = YamlConfiguration.loadConfiguration(bansFile);
    }

    private void saveBans() {
        try {
            bansConfig.save(bansFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save bans.yml!");
        }
    }

    public void banPlayer(UUID uuid, long durationMillis, String reason) {
        banPlayer(uuid, durationMillis, reason, "CONSOLE");
    }

    public void banPlayer(UUID uuid, long durationMillis, String reason, String issuedByUuid) {
        long expiry = durationMillis > 0 ? System.currentTimeMillis() + durationMillis : -1; // -1 = permanent
        bansConfig.set(uuid.toString() + ".expiry", expiry);
        bansConfig.set(uuid.toString() + ".reason", reason);
        bansConfig.set(uuid.toString() + ".punisher", issuedByUuid);
        saveBans();
        
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
        String punishmentType = durationMillis == -1 ? "BAN" : "TEMPBAN";
        plugin.getDatabaseManager().logPunishment(uuid.toString(), username, punishmentType, durationMillis, reason,
                punisherName, issuedByUuid);

        // Broadcast to staff
        String durationStr;
        if (durationMillis == -1) {
            durationStr = "Permanent";
        } else {
             long s = durationMillis / 1000;
             durationStr = (s / 86400) + "d " + ((s % 86400) / 3600) + "h";
        }

        String broadcastMsg = plugin.getConfig().getString("messages.ban-broadcast", "&8[&cChatModerator&8] &e%player% &7was banned by &e%punisher% &7for &e%duration%&7. Reason: &f%reason%")
                .replace("%player%", username)
                .replace("%punisher%", punisherName)
                .replace("%duration%", durationStr)
                .replace("%reason%", reason);

        org.bukkit.Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("chatmoderator.listen") || p.hasPermission("chatmoderator.admin"))
                .forEach(p -> p.sendMessage(plugin.colorize(broadcastMsg)));
        plugin.getLogger().info(ChatColor.stripColor(plugin.colorize(broadcastMsg)));
    }

    public void unbanPlayer(UUID uuid) {
        bansConfig.set(uuid.toString(), null);
        saveBans();
    }

    public boolean isBanned(UUID uuid) {
        if (!bansConfig.contains(uuid.toString()))
            return false;

        long expiry = bansConfig.getLong(uuid.toString() + ".expiry");
        if (expiry == -1)
            return true; // Permanent ban

        if (System.currentTimeMillis() > expiry) {
            unbanPlayer(uuid);
            return false;
        }
        return true;
    }

    public String getBanReason(UUID uuid) {
        return bansConfig.getString(uuid.toString() + ".reason", "No reason provided");
    }

    public String getBanTimeRemaining(UUID uuid) {
        long expiry = bansConfig.getLong(uuid.toString() + ".expiry");
        if (expiry == -1)
            return "Permanent";

        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0)
            return "Expired";

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0)
            return days + "d " + (hours % 24) + "h";
        if (hours > 0)
            return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0)
            return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    public java.util.Set<UUID> getBannedPlayers() {
        java.util.Set<UUID> banned = new java.util.HashSet<>();
        for (String key : bansConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (isBanned(uuid)) {
                    banned.add(uuid);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return banned;
    }
}
