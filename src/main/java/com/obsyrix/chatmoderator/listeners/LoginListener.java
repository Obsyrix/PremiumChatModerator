package com.obsyrix.chatmoderator.listeners;

import com.obsyrix.chatmoderator.ChatModerator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class LoginListener implements Listener {

    private final ChatModerator plugin;

    public LoginListener(ChatModerator plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.getBanManager().isBanned(event.getUniqueId())) {
            String reason = plugin.getBanManager().getBanReason(event.getUniqueId());
            String time = plugin.getBanManager().getBanTimeRemaining(event.getUniqueId());
            
            String message = plugin.colorize(
                plugin.getConfig().getString("messages.ban-message", 
                    "&cYou are banned!\n&7Reason: &f%reason%\n&7Expires: &f%time%")
                    .replace("%reason%", reason)
                    .replace("%time%", time)
            );
            
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message);
        }
    }
}
