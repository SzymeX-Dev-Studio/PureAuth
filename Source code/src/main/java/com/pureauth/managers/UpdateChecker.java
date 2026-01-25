package com.pureauth.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pureauth.PureAuth;
import com.pureauth.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class UpdateChecker {

    private final PureAuth plugin;
    private final String PROJECT_ID = "a0H5ojum"; 
    private final String CURRENT_VERSION;
    
    private boolean updateAvailable = false;
    private String latestVersion = "";

    public UpdateChecker(PureAuth plugin) {
        this.plugin = plugin;
        this.CURRENT_VERSION = plugin.getDescription().getVersion();
    }

    public void check() {
        plugin.getLogger().info("Checking for updates...");
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version"))
                        .header("User-Agent", "SzymeX/PureAuth/" + CURRENT_VERSION)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonElement element = JsonParser.parseString(response.body());
                    if (element.isJsonArray()) {
                        JsonArray versions = element.getAsJsonArray();
                        if (versions.size() > 0) {
                            JsonObject latest = versions.get(0).getAsJsonObject();
                            String versionNumber = latest.get("version_number").getAsString();
                            
                            if (!CURRENT_VERSION.equalsIgnoreCase(versionNumber)) {
                                updateAvailable = true;
                                latestVersion = versionNumber;
                                logConsole();
                            } else {
                                plugin.getLogger().info("You are running the latest version.");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }

    private void logConsole() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLogger().warning("---------------------------------------");
            plugin.getLogger().warning("A new version of PureAuth is available!");
            plugin.getLogger().warning("Current: " + CURRENT_VERSION);
            plugin.getLogger().warning("Latest: " + latestVersion);
            plugin.getLogger().warning("Download: https://modrinth.com/plugin/" + PROJECT_ID);
            plugin.getLogger().warning("---------------------------------------");
        });
    }

    public void notifyAdmin(Player player) {
        if (updateAvailable && player.hasPermission("pureauth.admin")) {
            String prefix = plugin.getMessage("prefix");
            player.sendMessage(Utils.format(prefix + "&eA new update is available! (&c" + CURRENT_VERSION + " &7-> &a" + latestVersion + "&e)"));
            player.sendMessage(Utils.format(prefix + "&eDownload at: &bhttps://modrinth.com/plugin/" + PROJECT_ID));
        }
    }
}