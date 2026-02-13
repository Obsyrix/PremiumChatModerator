package com.obsyrix.chatmoderator.managers;

import com.obsyrix.chatmoderator.ChatModerator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ListenerManager {

    private final ChatModerator plugin;
    private final Set<UUID> ignoringPlayers;
    private File listenersFile;
    private FileConfiguration listenersConfig;

    public ListenerManager(ChatModerator plugin) {
        this.plugin = plugin;
        this.ignoringPlayers = new HashSet<>();
        load();
    }

    public void load() {
        listenersFile = new File(plugin.getDataFolder(), "listeners.yml");
        if (!listenersFile.exists()) {
            try {
                listenersFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create listeners.yml!");
            }
        }
        listenersConfig = YamlConfiguration.loadConfiguration(listenersFile);

        List<String> stored = listenersConfig.getStringList("ignoring");
        ignoringPlayers.clear();
        for (String s : stored) {
            try {
                ignoringPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                // Ignore invalid UUIDs
            }
        }
    }

    public void save() {
        List<String> stored = ignoringPlayers.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        listenersConfig.set("ignoring", stored);
        try {
            listenersConfig.save(listenersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save listeners.yml!");
        }
    }

    public boolean isListening(UUID uuid) {
        // Default is listening (true), so return true if NOT in ignoring set
        return !ignoringPlayers.contains(uuid);
    }

    public void setListening(UUID uuid, boolean listening) {
        if (listening) {
            ignoringPlayers.remove(uuid);
        } else {
            ignoringPlayers.add(uuid);
        }
        save(); // Auto-save on change for simplicity
    }

    public boolean toggle(UUID uuid) {
        boolean isNowListening = !isListening(uuid);
        setListening(uuid, isNowListening);
        return isNowListening;
    }
}
