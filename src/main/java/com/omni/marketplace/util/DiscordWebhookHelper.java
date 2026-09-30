package com.omni.marketplace.util;

import com.omni.marketplace.config.MarketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscordWebhookHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordWebhookHelper.class);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OmniMarket-WebhookThread");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void sendTradeAlert(String itemId, String itemName, int quantity, long unitPrice, long totalValue, String buyerName, String sellerName) {
        MarketConfig config = MarketConfig.get();
        if (!config.isEnableDiscordWebhook()) return;

        String webhookUrl = config.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank() || !webhookUrl.startsWith("http")) return;

        EXECUTOR.submit(() -> {
            try {
                String unitPriceFormatted = CurrencyUtils.format(unitPrice).replaceAll("§[0-9a-fk-or]", "");
                String totalFormatted = CurrencyUtils.format(totalValue).replaceAll("§[0-9a-fk-or]", "");
                String cleanItemName = (itemName != null && !itemName.isBlank()) ? itemName.replaceAll("§[0-9a-fk-or]", "") : itemId;
                String isoTime = Instant.now().toString();

                String jsonPayload = String.format("""
                    {
                      "username": "Omni Marketplace",
                      "avatar_url": "https://raw.githubusercontent.com/ignight-dev/omni-market/main/src/main/resources/logo.png",
                      "embeds": [
                        {
                          "title": "⚖ High-Value Market Trade Executed!",
                          "color": 13938487,
                          "fields": [
                            { "name": "Item", "value": "%s", "inline": true },
                            { "name": "Quantity", "value": "%d", "inline": true },
                            { "name": "Total Paid", "value": "%s", "inline": true },
                            { "name": "Unit Price", "value": "%s", "inline": true },
                            { "name": "Buyer", "value": "%s", "inline": true },
                            { "name": "Seller", "value": "%s", "inline": true }
                          ],
                          "footer": { "text": "Omni Marketplace Intelligence • %s" },
                          "timestamp": "%s"
                        }
                      ]
                    }
                    """,
                    escapeJson(cleanItemName),
                    quantity,
                    escapeJson(totalFormatted),
                    escapeJson(unitPriceFormatted),
                    escapeJson(buyerName != null ? buyerName : "Anonymous"),
                    escapeJson(sellerName != null ? sellerName : "Anonymous"),
                    escapeJson(config.getServerName()),
                    isoTime
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "OmniMarketplace-NeoForge/1.21.1")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    LOGGER.warn("Discord Webhook responded with HTTP {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to dispatch Discord trade alert webhook", e);
            }
        });
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
