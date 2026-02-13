package com.obsyrix.chatmoderator.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.obsyrix.chatmoderator.ChatModerator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProfanityService {

    private final ChatModerator plugin;
    private final HttpClient httpClient;
    private final Map<Integer, CachedResult> cache = new ConcurrentHashMap<>();

    private String v2Url;
    private String v1Url;
    private int timeout;
    private boolean cacheEnabled;
    private int cacheDurationMinutes;

    public ProfanityService(ChatModerator plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        reloadConfig();
    }

    public void reloadConfig() {
        this.v2Url = plugin.getConfig().getString("profanity-api.v2-url", "https://profanity-api.xeven.workers.dev");
        this.v1Url = plugin.getConfig().getString("profanity-api.v1-url", "https://vector.profanity.dev");
        this.timeout = plugin.getConfig().getInt("profanity-api.timeout-seconds", 5);
        this.cacheEnabled = plugin.getConfig().getBoolean("profanity-api.cache.enabled", true);
        this.cacheDurationMinutes = plugin.getConfig().getInt("profanity-api.cache.duration-minutes", 10);
    }

    public CompletableFuture<ModerationResult> checkMessage(String message) {
        if (cacheEnabled) {
            int hash = message.hashCode();
            CachedResult cached = cache.get(hash);
            if (cached != null) {
                if (System.currentTimeMillis() - cached.timestamp < TimeUnit.MINUTES.toMillis(cacheDurationMinutes)) {
                    // Cache hit
                    return CompletableFuture.completedFuture(cached.result);
                } else {
                    cache.remove(hash);
                }
            }
        }

        // Try V2
        return callApi(v2Url, message, true)
                .thenCompose(result -> {
                    if (result != null && !result.error) {
                        return CompletableFuture.completedFuture(result);
                    }
                    // Fallback to V1
                    plugin.getLogger().warning("Profanity API v2 failed or returned error. Falling back to v1.");
                    return callApi(v1Url, message, false);
                })
                .thenApply(result -> {
                    if (result == null) {
                        // Total failure
                        return new ModerationResult(false, 0, null, "none", true);
                    }
                    if (cacheEnabled && !result.error) {
                        cache.put(message.hashCode(), new CachedResult(result, System.currentTimeMillis()));
                    }
                    return result;
                });
    }

    private CompletableFuture<ModerationResult> callApi(String url, String message, boolean isV2) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(timeout))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return new ModerationResult(false, 0, null, isV2 ? "v2" : "v1", true);
                    }
                    return parseResponse(response.body(), isV2);
                })
                .exceptionally(ex -> new ModerationResult(false, 0, null, isV2 ? "v2" : "v1", true));
    }

    public CompletableFuture<Map<String, Boolean>> checkHealth() {
        CompletableFuture<Boolean> v2Health = callApi(v2Url, "health check", true)
                .thenApply(result -> !result.error);
        CompletableFuture<Boolean> v1Health = callApi(v1Url, "health check", false)
                .thenApply(result -> !result.error);

        return CompletableFuture.allOf(v2Health, v1Health)
                .thenApply(v -> {
                    Map<String, Boolean> health = new HashMap<>();
                    health.put("v2", v2Health.join());
                    health.put("v1", v1Health.join());
                    return health;
                });
    }

    private ModerationResult parseResponse(String jsonBody, boolean isV2) {
        try {
            JsonObject json = JsonParser.parseString(jsonBody).getAsJsonObject();
            boolean isProfanity = json.get("isProfanity").getAsBoolean();
            double score = json.get("score").getAsDouble();

            String cleanedVersion = null;
            if (isV2 && json.has("cleanedVersion") && !json.get("cleanedVersion").isJsonNull()) {
                cleanedVersion = json.get("cleanedVersion").getAsString();
            }

            // Normilize V1 fallback - V1 does not return cleanedVersion
            // But we might want to flag V1 specifically in the result

            return new ModerationResult(isProfanity, score, cleanedVersion, isV2 ? "v2" : "v1", false);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to parse API response: " + e.getMessage());
            return new ModerationResult(false, 0, null, isV2 ? "v2" : "v1", true);
        }
    }

    public static record ModerationResult(boolean isProfanity, double score, String cleanedMessage, String apiVersion,
            boolean error) {
    }

    private static record CachedResult(ModerationResult result, long timestamp) {
    }
}
