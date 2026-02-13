package com.obsyrix.chatmoderator;

import com.obsyrix.chatmoderator.commands.ChatModeratorCommand;
import com.obsyrix.chatmoderator.listeners.ChatListener;
import com.obsyrix.chatmoderator.listeners.LoginListener;
import com.obsyrix.chatmoderator.managers.BanManager;
import com.obsyrix.chatmoderator.managers.DatabaseManager;
import com.obsyrix.chatmoderator.managers.MuteManager;
import com.obsyrix.chatmoderator.managers.PunishmentManager;
import com.obsyrix.chatmoderator.managers.ListenerManager;
import com.obsyrix.chatmoderator.service.ProfanityService;
import com.obsyrix.chatmoderator.utils.UpdateChecker;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatModerator extends JavaPlugin implements Listener {

    private static ChatModerator instance;
    private ProfanityService profanityService;
    private DatabaseManager databaseManager;
    private MuteManager muteManager;
    private BanManager banManager;
    private PunishmentManager punishmentManager;
    private ListenerManager listenerManager;
    private String latestVersion = null;

    @Override
    public void onEnable() {
        instance = this;
        try {
            saveDefaultConfig();

            // Initialize Database
            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.initialize();

            // Initialize Managers
            this.muteManager = new MuteManager(this);
            this.banManager = new BanManager(this);
            this.punishmentManager = new PunishmentManager(this);
            this.profanityService = new ProfanityService(this);
            this.listenerManager = new ListenerManager(this);


            // Register Commands
            ChatModeratorCommand cmd = new ChatModeratorCommand(this);
            if (getCommand("chatmoderator") != null) {
                getCommand("chatmoderator").setExecutor(cmd);
                getCommand("chatmoderator").setTabCompleter(cmd);
            } else {
                getLogger().severe("Command 'chatmoderator' not found in plugin.yml!");
            }

            // Register Listeners
            getServer().getPluginManager().registerEvents(new ChatListener(this), this);
            getServer().getPluginManager().registerEvents(new LoginListener(this), this);
            getServer().getPluginManager().registerEvents(this, this);

            getLogger().info("ChatModerator Premium v" + getPluginMeta().getVersion() + " has been enabled!");

            // Version Checker
            if (getConfig().getBoolean("update-checker", true)) {
                new UpdateChecker(this, "https://www.obsyron.com/versions/premiumchatmoderator.txt").getVersion().thenAccept(version -> {
                    if (version != null) {
                        this.latestVersion = version;
                        if (!this.getPluginMeta().getVersion().equals(version)) {
                            getLogger().warning("A new version of PremiumChatModerator is available: v" + version);
                            getLogger().warning("Download it at: www.obsyron.com/plugins");
                        }
                    }
                });
            }
        } catch (Exception e) {
            getLogger().severe("Failed to enable ChatModerator Premium: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() && latestVersion != null && !this.getPluginMeta().getVersion().equals(latestVersion)) {
            player.sendMessage(colorize("&8[&5PremiumChat&8] &aA new version is available: &e v" + latestVersion));
            player.sendMessage(colorize("&8[&5PremiumChat&8] &7Download it at &bwww.obsyron.com/plugins"));
        }
        
        // Apply Score Decay
        getPunishmentManager().applyDecay(event.getPlayer().getUniqueId());
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("ChatModerator Premium disabled!");
    }

    public static ChatModerator getInstance() {
        return instance;
    }

    public ProfanityService getProfanityService() {
        return profanityService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public ListenerManager getListenerManager() {
        return listenerManager;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String colorize(String message) {
        if (message == null)
            return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
