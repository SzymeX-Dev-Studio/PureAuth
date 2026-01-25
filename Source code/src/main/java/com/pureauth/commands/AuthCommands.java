package com.pureauth.commands;

import com.pureauth.PureAuth;
import com.pureauth.Utils;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class AuthCommands implements CommandExecutor, TabCompleter {

    private final PureAuth plugin;

    public AuthCommands(PureAuth plugin) {
        this.plugin = plugin;
    }
    
    private String getPrefix() {
        return plugin.getMessage("prefix");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) sender;
        String prefix = getPrefix();

        // --- PREMIUM COMMAND ---
        if (command.getName().equalsIgnoreCase("premium")) {
            if (!plugin.isLoggedIn(p)) {
                p.sendMessage(Utils.format(prefix + plugin.getMessage("error_not_logged")));
                return true;
            }
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getDataManager().isLocked(p.getUniqueId())) return;

                if (plugin.getDataManager().isPremium(p.getUniqueId())) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("premium_already")));
                    return;
                }
                
                try {
                    // API CHECK
                    URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + p.getName());
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    
                    if (conn.getResponseCode() == 200) {
                        // Success - Mark as Premium AND Save current IP
                        plugin.getDataManager().setPremium(p.getUniqueId(), true);
                        
                        // Update IP so next login works
                        String currentIp = p.getAddress().getAddress().getHostAddress();
                        plugin.getDataManager().updateLoginInfo(p.getUniqueId(), currentIp);
                        
                        p.sendMessage(Utils.format(prefix + plugin.getMessage("premium_enabled")));
                        
                        // Discord Log
                        plugin.getDiscordManager().sendEmbed("user_premium_toggle", "💎 Premium Enabled", 
                            "Player **" + p.getName() + "** enabled Premium Auto-Login.", 3066993);
                    } else {
                        p.sendMessage(Utils.format(prefix + plugin.getMessage("premium_fail_api")));
                    }
                } catch (Exception e) {
                    p.sendMessage(Utils.format(prefix + "&cAPI Error: " + e.getMessage()));
                }
            });
            return true;
        }

        // --- CRACKED COMMAND ---
        if (command.getName().equalsIgnoreCase("cracked")) {
            if (!plugin.isLoggedIn(p)) {
                p.sendMessage(Utils.format(prefix + plugin.getMessage("error_not_logged")));
                return true;
            }
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getDataManager().isLocked(p.getUniqueId())) return;

                if (!plugin.getDataManager().isPremium(p.getUniqueId())) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("premium_not_enabled")));
                    return;
                }
                
                // Disable Premium
                plugin.getDataManager().setPremium(p.getUniqueId(), false);
                p.sendMessage(Utils.format(prefix + plugin.getMessage("premium_disabled")));
                
                // Discord Log
                plugin.getDiscordManager().sendEmbed("user_premium_toggle", "🛡️ Premium Disabled", 
                    "Player **" + p.getName() + "** disabled Premium Auto-Login.", 15158332);
            });
            return true;
        }

        // --- REGISTER ---
        if (command.getName().equalsIgnoreCase("register")) {
            if (plugin.isLoggedIn(p)) {
                p.sendMessage(Utils.format(prefix + plugin.getMessage("error_already_logged")));
                return true;
            }
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getDataManager().isLocked(p.getUniqueId())) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_locked_chat")));
                    return;
                }

                if (plugin.getDataManager().isRegistered(p.getUniqueId())) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_already_reg")));
                    return;
                }
                
                int current = plugin.getDataManager().getAccountCount(p.getAddress().getAddress().getHostAddress());
                if (current >= plugin.getConfig().getInt("settings.max_accounts_per_ip", 3)) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_max_ip")));
                    return;
                }

                boolean captchaEnabled = plugin.getConfig().getBoolean("settings.captcha.enabled");
                int requiredArgs = captchaEnabled ? 3 : 2;

                if (args.length != requiredArgs) {
                    String msg = captchaEnabled ? plugin.getMessage("error_usage_captcha") : plugin.getMessage("error_usage_reg");
                    p.sendMessage(Utils.format(prefix + msg));
                    return;
                }

                if (captchaEnabled) {
                    if (!plugin.validateCaptcha(p.getUniqueId(), args[2])) {
                        p.sendMessage(Utils.format(prefix + plugin.getMessage("error_wrong_captcha")));
                        return;
                    }
                }

                if (!args[0].equals(args[1])) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_match_pass")));
                    return;
                }
                int min = plugin.getConfig().getInt("settings.min_password_length", 4);
                if (args[0].length() < min) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_length_pass").replace("%length%", String.valueOf(min))));
                    return;
                }

                String hash = Utils.hashPassword(args[0]);
                plugin.getDataManager().registerUser(p.getUniqueId(), p.getName(), hash, p.getAddress().getAddress().getHostAddress());
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.loginPlayer(p);
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("success_register")));
                    showSuccessTitle(p, "register");
                    
                    // Discord Log
                    plugin.getDiscordManager().sendEmbed("user_register", "👤 New Registration", 
                        "Player **" + p.getName() + "** registered a new account.", 65535);
                });
            });
            return true;
        }

        // --- LOGIN ---
        if (command.getName().equalsIgnoreCase("login")) {
            if (plugin.isLoggedIn(p)) {
                p.sendMessage(Utils.format(prefix + plugin.getMessage("error_already_logged")));
                return true;
            }
            if (args.length != 1) {
                p.sendMessage(Utils.format(prefix + "&cUsage: /login <password>"));
                return true;
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getDataManager().isLocked(p.getUniqueId())) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_locked_chat")));
                    return;
                }

                if (!plugin.getDataManager().isRegistered(p.getUniqueId())) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_not_reg")));
                    return;
                }

                String stored = plugin.getDataManager().getPassword(p.getUniqueId());
                if (Utils.checkPassword(args[0], stored)) {
                    // Update IP
                    plugin.getDataManager().updateLoginInfo(p.getUniqueId(), p.getAddress().getAddress().getHostAddress());
                    
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.loginPlayer(p);
                        p.sendMessage(Utils.format(prefix + plugin.getMessage("success_login")));
                        
                        Location lastLoc = plugin.getDataManager().getLastLocation(p.getUniqueId());
                        if (lastLoc != null) p.teleport(lastLoc);
                        
                        showSuccessTitle(p, "login");
                        
                        // Discord Log
                        plugin.getDiscordManager().sendEmbed("user_login", "🔓 User Login", 
                            "Player **" + p.getName() + "** logged in manually.", 65280);
                    });
                } else {
                    plugin.getServer().getScheduler().runTask(plugin, () -> plugin.addStrike(p));
                }
            });
            return true;
        }

        // --- CHANGEPASS ---
        if (command.getName().equalsIgnoreCase("changepass")) {
            if (!plugin.isLoggedIn(p)) {
                p.sendMessage(Utils.format(prefix + plugin.getMessage("error_not_logged")));
                return true;
            }
            if (args.length != 3) {
                p.sendMessage(Utils.format(prefix + "&cUsage: /changepass <old> <new> <new>"));
                return true;
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getDataManager().isLocked(p.getUniqueId())) return;

                String stored = plugin.getDataManager().getPassword(p.getUniqueId());
                if (!Utils.checkPassword(args[0], stored)) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_wrong_old")));
                    return;
                }
                if (!args[1].equals(args[2])) {
                    p.sendMessage(Utils.format(prefix + plugin.getMessage("error_match_pass")));
                    return;
                }
                
                String newHash = Utils.hashPassword(args[1]);
                plugin.getDataManager().updatePassword(p.getUniqueId(), newHash);
                p.sendMessage(Utils.format(prefix + plugin.getMessage("password_changed")));
                
                // Discord Log
                plugin.getDiscordManager().sendEmbed("user_changepass", "🔑 Password Changed", 
                    "Player **" + p.getName() + "** changed their own password.", 16753920);
            });
            return true;
        }
        return false;
    }

    private void showSuccessTitle(Player p, String key) {
        Title title = Title.title(
            Utils.format(plugin.getMessage("title_success_" + key)),
            Utils.format(plugin.getMessage("subtitle_success_" + key)),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))
        );
        p.showTitle(title);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && (command.getName().equalsIgnoreCase("login") || command.getName().equalsIgnoreCase("register"))) {
            return Collections.singletonList("password");
        }
        return Collections.emptyList();
    }
}