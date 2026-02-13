package com.obsyrix.chatmoderator.listeners;

import com.obsyrix.chatmoderator.ChatModerator;
import com.obsyrix.chatmoderator.service.ProfanityService.ModerationResult;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.concurrent.TimeUnit;

public class ChatListener implements Listener {

    private final ChatModerator plugin;

    public ChatListener(ChatModerator plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // 1. Check Internal Mute
        if (plugin.getMuteManager().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            String reason = plugin.getMuteManager().getMuteReason(player.getUniqueId());
            String time = plugin.getMuteManager().getMuteTimeRemaining(player.getUniqueId());

            String msg = plugin.getConfig().getString("messages.muted-error", "&cYou are muted! Expires in: %time%")
                    .replace("%time%", time)
                    .replace("%reason%", reason);
            player.sendMessage(plugin.colorize(msg));
            return;
        }

        if (player.hasPermission("chatmoderator.bypass"))
            return;

        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());

        try {
            // 2. Call Profanity API
            ModerationResult result = plugin.getProfanityService().checkMessage(message)
                    .get(5, TimeUnit.SECONDS);

            if (result.error()) {
                plugin.getLogger()
                        .warning("Chat moderation failed (API error). Allowed message from " + player.getName());
                return;
            }

            // 3. Check Score Threshold
            double minScoreToFlag = plugin.getConfig().getDouble("score-system.min-score-to-flag", 0.9);
            boolean isInfraction = result.isProfanity() && result.score() >= minScoreToFlag;

            if (isInfraction) {
                // Determine Mode
                String mode = plugin.getConfig().getString("moderation-mode", "BLOCK").toUpperCase();
                String actionTaken = mode; // For logging/alert

                boolean blocked = false;

                if (mode.equals("CENSOR")) {
                    if (result.cleanedMessage() != null) {
                        // V2 Replace
                        event.message(Component.text(result.cleanedMessage()));
                        player.sendMessage(plugin.colorize(plugin.getConfig()
                                .getString("messages.censored-notification", "&cYour message was censored.")));
                    } else {
                        // V1 Fallback (No cleaned message available) 
                        event.setCancelled(true);
                        blocked = true;
                        actionTaken = "BLOCK (Fallback)";
                        player.sendMessage(
                                plugin.colorize(plugin.getConfig().getString("messages.blocked", "&cBlocked.")));
                    }
                } else {
                    // BLOCK
                    event.setCancelled(true);
                    blocked = true;
                    player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.blocked", "&cBlocked.")));
                }

                // 4. Log Infraction (Async if possible, but here blocking is fine on async chat
                // thread)
                plugin.getDatabaseManager().logInfraction(
                        player.getUniqueId().toString(),
                        player.getName(),
                        message,
                        result.cleanedMessage(),
                        result.score(),
                        result.apiVersion(),
                        actionTaken);

                // 5. Accumulate Score & Punish
                if (plugin.getConfig().getBoolean("score-system.enabled", true)) {
                    plugin.getPunishmentManager().addScore(player, result.score());
                }

                // 6. Admin Alert
                notifyAdmins(player, result.score(), result.apiVersion(), message);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error in chat listener: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void notifyAdmins(Player violator, double currentMsgScore, String apiVersion, String originalMessage) {
        // Get Total Score
        double totalScore = plugin.getPunishmentManager().getScore(violator.getUniqueId());
        
        String format = plugin.getConfig().getString("score-system.admin-listen.format", "&c%player% &7score: &e%score% &8(&f%message%&8)");
        String msg = format
                .replace("%player%", violator.getName())
                .replace("%score%", String.format("%.2f", totalScore)) // Display TOTAL
                .replace("%api%", apiVersion)
                .replace("%message%", originalMessage);

        String type = plugin.getConfig().getString("score-system.admin-listen.type", "ACTIONBAR").toUpperCase();
        String permission = plugin.getConfig().getString("score-system.admin-listen.permission", "chatmoderator.listen");

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(permission)) {
                // Check if they have toggled it off
                if (!plugin.getListenerManager().isListening(p.getUniqueId())) {
                    continue;
                }

                if (type.equals("CHAT")) {
                    p.sendMessage(plugin.colorize(msg));
                } else {
                    p.sendActionBar(Component.text(plugin.colorize(msg)));
                }
            }
        }
    }
}
