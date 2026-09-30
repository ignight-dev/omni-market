package com.omni.marketplace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarketConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketConfig.class);
    private static final MarketConfig INSTANCE = new MarketConfig();

    private String webhookUrl = "";
    private boolean enableDiscordWebhook = false;
    private boolean enableInGameBroadcasts = true;
    private long minBroadcastValueCopper = 10000L; // 1 Gold (100 silver = 10,000 copper)
    private String serverName = "Imperial Realm";

    private Path configPath;

    public static MarketConfig get() {
        return INSTANCE;
    }

    public synchronized void initialize(Path gameDir) {
        Path configDir = gameDir.resolve("config");
        try {
            Files.createDirectories(configDir);
            this.configPath = configDir.resolve("omni_marketplace.json");
            if (Files.exists(this.configPath)) {
                load();
            } else {
                save();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Omni Marketplace config", e);
        }
    }

    public synchronized void load() {
        if (configPath == null || !Files.exists(configPath)) return;
        try {
            String json = Files.readString(configPath);
            this.webhookUrl = extractString(json, "webhookUrl", this.webhookUrl);
            this.enableDiscordWebhook = extractBoolean(json, "enableDiscordWebhook", this.enableDiscordWebhook);
            this.enableInGameBroadcasts = extractBoolean(json, "enableInGameBroadcasts", this.enableInGameBroadcasts);
            this.minBroadcastValueCopper = extractLong(json, "minBroadcastValueCopper", this.minBroadcastValueCopper);
            this.serverName = extractString(json, "serverName", this.serverName);
            LOGGER.info("Loaded Omni Marketplace config: Webhook enabled={}, Chat Broadcasts={}", enableDiscordWebhook, enableInGameBroadcasts);
        } catch (Exception e) {
            LOGGER.error("Error reading config file at {}", configPath, e);
        }
    }

    public synchronized void save() {
        if (configPath == null) {
            configPath = Paths.get("config", "omni_marketplace.json");
        }
        try {
            Files.createDirectories(configPath.getParent());
            String json = String.format("""
                {
                  "// Note": "Omni Marketplace Server & Market Intelligence Configuration",
                  "webhookUrl": "%s",
                  "enableDiscordWebhook": %b,
                  "enableInGameBroadcasts": %b,
                  "minBroadcastValueCopper": %d,
                  "serverName": "%s"
                }
                """,
                escapeJson(webhookUrl),
                enableDiscordWebhook,
                enableInGameBroadcasts,
                minBroadcastValueCopper,
                escapeJson(serverName)
            );
            Files.writeString(configPath, json);
        } catch (IOException e) {
            LOGGER.error("Failed to write config file to {}", configPath, e);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractString(String json, String key, String def) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return def;
    }

    private static boolean extractBoolean(String json, String key, boolean def) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return def;
    }

    private static long extractLong(String json, String key, long def) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public boolean isEnableDiscordWebhook() { return enableDiscordWebhook; }
    public void setEnableDiscordWebhook(boolean enableDiscordWebhook) { this.enableDiscordWebhook = enableDiscordWebhook; }

    public boolean isEnableInGameBroadcasts() { return enableInGameBroadcasts; }
    public void setEnableInGameBroadcasts(boolean enableInGameBroadcasts) { this.enableInGameBroadcasts = enableInGameBroadcasts; }

    public long getMinBroadcastValueCopper() { return minBroadcastValueCopper; }
    public void setMinBroadcastValueCopper(long minBroadcastValueCopper) { this.minBroadcastValueCopper = minBroadcastValueCopper; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
}
