package com.obsyrix.chatmoderator.utils;

import com.obsyrix.chatmoderator.ChatModerator;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

public class UpdateChecker {

    private final ChatModerator plugin;
    private final String updateUrl;

    public UpdateChecker(ChatModerator plugin, String updateUrl) {
        this.plugin = plugin;
        this.updateUrl = updateUrl;
    }

    public java.util.concurrent.CompletableFuture<String> getVersion() {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try (InputStream inputStream = new URL(updateUrl).openStream();
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    return scanner.next();
                }
            } catch (IOException exception) {
                plugin.getLogger().info("Cannot look for updates: " + exception.getMessage());
            }
            return null;
        });
    }
}
