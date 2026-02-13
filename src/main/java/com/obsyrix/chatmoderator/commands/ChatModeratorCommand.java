package com.obsyrix.chatmoderator.commands;

import com.obsyrix.chatmoderator.ChatModerator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChatModeratorCommand implements CommandExecutor, TabCompleter {

    private final ChatModerator plugin;

    public ChatModeratorCommand(ChatModerator plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("chatmoderator.admin") && !sender.hasPermission("chatmoderator.listen")) {
            sender.sendMessage(plugin.colorize("&rUnknown command. Type \"/help\" for help."));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                if (!sender.hasPermission("chatmoderator.admin")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getProfanityService().reloadConfig();
                plugin.getPunishmentManager().reloadPunishments();
                sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.reload-success", "&aConfiguration reloaded.")));
                break;

            case "mute":
                if (!sender.hasPermission("chatmoderator.admin")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.command-usage", "&cUsage: /chatmod mute <player> <duration> [reason]")));
                    return true;
                }
                handleMute(sender, args);
                break;

            case "unmute":
                if (!sender.hasPermission("chatmoderator.admin")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.command-usage", "&cUsage: /chatmod unmute <player>")));
                    return true;
                }
                handleUnmute(sender, args);
                break;

            case "ban":
            case "tempban":
                if (!sender.hasPermission("chatmoderator.admin")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.command-usage", "&cUsage: /chatmod ban <player> <duration> [reason]")));
                    sender.sendMessage(
                            plugin.colorize("&7Duration: Use 'perm' for permanent or time like 1d, 12h, 30m"));
                    return true;
                }
                handleBan(sender, args);
                break;

            case "unban":
                if (!sender.hasPermission("chatmoderator.admin")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.command-usage", "&cUsage: /chatmod unban <player>")));
                    return true;
                }
                handleUnban(sender, args);
                break;

            case "listen":
                 if (!sender.hasPermission("chatmoderator.listen") && !sender.hasPermission("chatmoderator.admin")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                 if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.listen-only-players", "&cThis command is only for players.")));
                    return true;
                }
                Player player = (Player) sender;
                boolean isListening = plugin.getListenerManager().toggle(player.getUniqueId());
                if (isListening) {
                    player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.listen-enabled", "&aYou are now listening to chat moderation alerts.")));
                } else {
                    player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.listen-disabled", "&cYou are no longer listening to chat moderation alerts.")));
                }
                break;

            case "status":
                if (!sender.hasPermission("chatmoderator.admin")) {
                    sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                sender.sendMessage(plugin.colorize("&7Checking status..."));

                java.util.concurrent.CompletableFuture<String> versionFuture = new com.obsyrix.chatmoderator.utils.UpdateChecker(plugin, "https://www.obsyron.com/versions/premiumchatmoderator.txt").getVersion();
                java.util.concurrent.CompletableFuture<java.util.Map<String, Boolean>> healthFuture = plugin.getProfanityService().checkHealth();

                java.util.concurrent.CompletableFuture.allOf(versionFuture, healthFuture).thenRun(() -> {
                    String version = versionFuture.join();
                    java.util.Map<String, Boolean> health = healthFuture.join();
                    
                    sender.sendMessage(plugin.colorize("&b&l-- Premium Chat Moderator Status --"));
                    sender.sendMessage(plugin.colorize("&7Current Version: &a" + plugin.getPluginMeta().getVersion()));
                    sender.sendMessage(plugin.colorize("&7Latest Version: &e" + (version == null ? "&cError checking" : version)));
                    sender.sendMessage(plugin.colorize("&7Moderation Mode: &f" + plugin.getConfig().getString("moderation-mode", "BLOCK")));
                    sender.sendMessage(plugin.colorize("&7Score System: " + (plugin.getConfig().getBoolean("score-system.enabled", true) ? "&aEnabled" : "&cDisabled")));
                    
                    sender.sendMessage(plugin.colorize("&b&l-- API Health --"));
                    sender.sendMessage(plugin.colorize("&7V2 API (Main): " + (health.get("v2") ? "&aONLINE" : "&cOFFLINE")));
                    sender.sendMessage(plugin.colorize("&7V1 API (Fallback): " + (health.get("v1") ? "&aONLINE" : "&cOFFLINE")));
                });
                break;
                
            case "help":
                showHelp(sender);
                break;

            default:
                showHelp(sender);
                break;
        }

        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(plugin.colorize("&bPremiumChatModerator v" + plugin.getPluginMeta().getVersion()));
        if (sender.hasPermission("chatmoderator.admin")) {
            sender.sendMessage(plugin.colorize("&7/chatmod reload"));
            sender.sendMessage(plugin.colorize("&7/chatmod mute <player> <duration> [reason]"));
            sender.sendMessage(plugin.colorize("&7/chatmod unmute <player>"));
            sender.sendMessage(plugin.colorize("&7/chatmod ban <player> <duration> [reason]"));
            sender.sendMessage(plugin.colorize("&7/chatmod tempban <player> <duration> [reason]"));
            sender.sendMessage(plugin.colorize("&7/chatmod unban <player>"));
            sender.sendMessage(plugin.colorize("&7/chatmod status"));
        }
        if (sender.hasPermission("chatmoderator.listen") || sender.hasPermission("chatmoderator.admin")) {
            sender.sendMessage(plugin.colorize("&7/chatmod listen"));
        }
    }

    private void handleMute(CommandSender sender, String[] args) {
        String playerName = args[1];
        String durationStr = args[2];
        String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "No reason";

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found.")));
            return;
        }

        if (target.isOp()) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.cannot-mute-op", "&cYou cannot mute an Operator.")));
            return;
        }

        if (target.isOnline() && (target.getPlayer().hasPermission("chatmoderator.bypass") || target.getPlayer().hasPermission("chatmoderator.admin"))) {
             sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.cannot-mute-bypass", "&cYou cannot mute a player with bypass permissions.")));
             return;
        }

        long durationMillis;
        if (durationStr.equalsIgnoreCase("perm") || durationStr.equalsIgnoreCase("permanent")) {
            durationMillis = -1; // Permanent
        } else {
            durationMillis = parseDuration(durationStr);
            if (durationMillis <= 0) {
                sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.invalid-duration", "&cInvalid duration. Use 'perm' or time like 10m, 1h, 1d etc.")));
                return;
            }
        }

        String punisherUuid = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        plugin.getMuteManager().mutePlayer(target.getUniqueId(), durationMillis, reason, punisherUuid);
        
        String successMsg = plugin.getConfig().getString("messages.mute-success", "&aMuted %player% for %duration%.")
                .replace("%player%", playerName)
                .replace("%duration%", durationStr);
        sender.sendMessage(plugin.colorize(successMsg));
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found.")));
            return;
        }

        plugin.getMuteManager().unmutePlayer(target.getUniqueId());
        sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.unmute-success", "&aUnmuted %player%."))
                .replace("%player%", playerName));
    }

    private void handleBan(CommandSender sender, String[] args) {
        String playerName = args[1];
        String durationStr = args[2];
        String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "No reason";

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found.")));
            return;
        }

        if (target.isOp()) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.cannot-ban-op", "&cYou cannot ban an Operator.")));
            return;
        }

        if (target.isOnline() && (target.getPlayer().hasPermission("chatmoderator.bypass") || target.getPlayer().hasPermission("chatmoderator.admin"))) {
             sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.cannot-ban-bypass", "&cYou cannot ban a player with bypass permissions.")));
             return;
        }

        long durationMillis;
        if (durationStr.equalsIgnoreCase("perm") || durationStr.equalsIgnoreCase("permanent")) {
            durationMillis = -1; // Permanent
        } else {
            durationMillis = parseDuration(durationStr);
            if (durationMillis <= 0) {
                sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.invalid-duration", "&cInvalid duration. Use 'perm' or time like 10m, 1h, 1d etc.")));
                return;
            }
        }

        String punisherUuid = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        plugin.getBanManager().banPlayer(target.getUniqueId(), durationMillis, reason, punisherUuid);

        String msg = plugin.getConfig().getString("messages.ban-success", "&aBanned %player% for %duration%.")
                .replace("%player%", playerName)
                .replace("%duration%", durationStr);
        sender.sendMessage(plugin.colorize(msg));

        // Kick if online
        if (target.isOnline()) {
            ((Player) target).kickPlayer(plugin.colorize(plugin.getConfig().getString("messages.ban-message", "&cYou have been banned!\n&7Reason: &f%reason%\n&7Expires: &f%time%")
                    .replace("%reason%", reason)
                    .replace("%time%", durationStr)));
        }
    }

    private void handleUnban(CommandSender sender, String[] args) {
        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found.")));
            return;
        }

        plugin.getBanManager().unbanPlayer(target.getUniqueId());
        sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.unban-success", "&aUnbanned %player%."))
                .replace("%player%", playerName));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("chatmoderator.admin")) {
                suggestions.addAll(Arrays.asList("reload", "mute", "unmute", "ban", "tempban", "unban", "listen", "status", "help"));
            } 
            if (sender.hasPermission("chatmoderator.listen")) {
                if (!suggestions.contains("listen")) suggestions.add("listen");
            }
            return suggestions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("mute") || sub.equals("ban") || sub.equals("tempban")) {
            if (!sender.hasPermission("chatmoderator.admin")) {
                return new ArrayList<>();
            }
            if (args.length == 2) {
                return null; // Bukkit default player suggestions
            }
            if (args.length == 3) {
                return Arrays.asList("10m", "30m", "1h", "6h", "1d", "7d", "perm").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 4) {
                return Arrays.asList("Toxicity", "Spam", "Excessive_Profanity", "Advertising", "Harassment").stream()
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());
            }
        }

        if (sub.equals("unmute")) {
            if (!sender.hasPermission("chatmoderator.admin")) {
                return new ArrayList<>();
            }
            if (args.length == 2) {
                // Show only muted players
                return plugin.getMuteManager().getMutedPlayers().stream()
                        .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                        .filter(name -> name != null && name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (sub.equals("unban")) {
            if (!sender.hasPermission("chatmoderator.admin")) {
                return new ArrayList<>();
            }
            if (args.length == 2) {
                return plugin.getBanManager().getBannedPlayers().stream()
                        .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                        .filter(name -> name != null && name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }

    private long parseDuration(String input) {
        try {
            char unit = input.charAt(input.length() - 1);
            long value = Long.parseLong(input.substring(0, input.length() - 1));

            switch (unit) {
                case 's':
                    return value * 1000;
                case 'm':
                    return value * 60 * 1000;
                case 'h':
                    return value * 60 * 60 * 1000;
                case 'd':
                    return value * 24 * 60 * 60 * 1000;
                default:
                    return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }
}
