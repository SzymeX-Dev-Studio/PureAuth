package com.pureauth.managers;

import com.pureauth.PureAuth;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DiscordManager {
    private final PureAuth plugin;
    private final HttpClient client;

    public DiscordManager(PureAuth plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newHttpClient();
    }

    public void sendEmbed(String category, String title, String description, int color) {
        if (!plugin.getConfig().getBoolean("settings.discord.enabled")) return;
        if (!plugin.getConfig().getBoolean("settings.discord.events." + category, false)) return;

        String url = plugin.getConfig().getString("settings.discord.webhook_url");
        if (url == null || url.contains("YOUR_WEBHOOK") || url.isEmpty()) return;

        String safeTitle = title.replace("\"", "'");
        String safeDesc = description.replace("\"", "'");

        String json = String.format(
            "{\"embeds\": [{" +
            "\"title\": \"%s\"," +
            "\"description\": \"%s\"," +
            "\"color\": %d," +
            "\"footer\": {\"text\": \"PureAuth Security System\"}" +
            "}]}", 
            safeTitle, safeDesc, color
        );

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }
}