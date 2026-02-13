package com.obsyrix.chatmoderator.managers;

import com.obsyrix.chatmoderator.ChatModerator;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PunishmentManager {

    private final ChatModerator plugin;
    private File scoresFile;
    private FileConfiguration scoresConfig;

    public PunishmentManager(ChatModerator plugin) {
        this.plugin = plugin;
        reloadScores();
    }

    public void reloadPunishments() {
        reloadScores();
    }

    private void reloadScores() {
        scoresFile = new File(plugin.getDataFolder(), "scores.yml");
        if (!scoresFile.exists()) {
            try {
                scoresFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create scores.yml!");
            }
        }
        scoresConfig = YamlConfiguration.loadConfiguration(scoresFile);
    }

    private void saveScores() {
        try {
            scoresConfig.save(scoresFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save scores.yml!");
        }
    }

    public void addScore(Player player, double amount) {
        // Strict Fairness Check: Ops and Bypass players should NEVER gain score.
        if (player.hasPermission("chatmoderator.bypass") || player.isOp()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        double current = getScore(uuid);
        double newScore = current + amount;

        // Save new score
        scoresConfig.set(uuid.toString() + ".score", newScore);
        saveScores();

        checkThresholds(player, newScore);
    }

    public double getScore(UUID uuid) {
        return scoresConfig.getDouble(uuid.toString() + ".score", 0.0);
    }

    /**
     * Applies score decay if enough time has passed since the last check.
     * Usually called on player join.
     */
    public void applyDecay(UUID uuid) {
        if (!plugin.getConfig().getBoolean("score-system.decay.enabled", true)) {
            return;
        }

        double currentScore = getScore(uuid);
        if (currentScore <= 0) {
            return; // No score to decay
        }

        long lastCheck = scoresConfig.getLong(uuid.toString() + ".last-decay-check", 0);
        long now = System.currentTimeMillis();

        if (lastCheck == 0) {
            // First time check, just set timestamp and return
            scoresConfig.set(uuid.toString() + ".last-decay-check", now);
            saveScores();
            return;
        }

        double decayAmount = plugin.getConfig().getDouble("score-system.decay.amount", 1.0);
        int intervalHours = plugin.getConfig().getInt("score-system.decay.interval-hours", 24);
        long intervalMillis = intervalHours * 3600000L;

        long elapsed = now - lastCheck;
        if (elapsed >= intervalMillis) {
            // Calculate how many intervals passed
            long intervalsPassed = elapsed / intervalMillis;
            double totalDecay = decayAmount * intervalsPassed;
            
            double newScore = Math.max(0.0, currentScore - totalDecay);
            
            // Update file
            scoresConfig.set(uuid.toString() + ".score", newScore);
            scoresConfig.set(uuid.toString() + ".last-decay-check", now); // Reset timer
            saveScores();
            
            if (currentScore != newScore) {
                plugin.getLogger().info("Applied score decay to " + uuid + ". Old: " + String.format("%.2f", currentScore) + " New: " + String.format("%.2f", newScore));
            }
        }
    }

    private void checkThresholds(Player player, double score) {
        ConfigurationSection thresholds = plugin.getConfig().getConfigurationSection("score-system.thresholds");
        if (thresholds == null) return;

        UUID uuid = player.getUniqueId();
        java.util.List<String> executed = scoresConfig.getStringList(uuid.toString() + ".executed-thresholds");
        boolean changed = false;

        for (String key : thresholds.getKeys(false)) {
            try {
                // Support both "10.0" and "10_0" formats
                String cleanKey = key.replace("_", ".");
                double threshold = Double.parseDouble(cleanKey);
                
                if (score >= threshold) {
                    // Check if already executed
                    if (!executed.contains(key)) {
                        // Execute command if threshold met AND not already executed
                        String command = thresholds.getString(key);
                        if (command != null) {
                            String finalCommand = command.replace("%player%", player.getName());
                            // Dispatch as console on MAIN THREAD
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                            });
                        }
                        // Mark as executed
                        executed.add(key);
                        changed = true;
                    }
                } else {
                    // Score is BELOW threshold. If it was previously executed, remove it (reset)
                    // This allows the punishment to trigger again if they go down and come back up.
                    if (executed.contains(key)) {
                        executed.remove(key);
                        changed = true;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        if (changed) {
            scoresConfig.set(uuid.toString() + ".executed-thresholds", executed);
            saveScores();
        }
    }
}
