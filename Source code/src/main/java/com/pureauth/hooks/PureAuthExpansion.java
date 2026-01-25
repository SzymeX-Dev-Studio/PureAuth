package com.pureauth.hooks;

import com.pureauth.PureAuth;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PureAuthExpansion extends PlaceholderExpansion {

    private final PureAuth plugin;

    public PureAuthExpansion(PureAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pureauth";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SzymeX Dev Studio";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keep the expansion registered
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // %pureauth_logged%
        if (params.equalsIgnoreCase("logged")) {
            // Check if player is online and in the loggedInPlayers set
            if (player.isOnline() && player.getPlayer() != null) {
                return String.valueOf(plugin.isLoggedIn(player.getPlayer()));
            }
            return "false";
        }

        // %pureauth_has_2fa%
        if (params.equalsIgnoreCase("has_2fa")) {
            return String.valueOf(plugin.getDataManager().is2FAEnabled(player.getUniqueId()));
        }

        return null; // Unknown placeholder
    }
}