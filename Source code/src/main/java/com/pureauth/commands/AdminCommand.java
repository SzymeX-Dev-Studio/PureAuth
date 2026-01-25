package com.pureauth.commands;

import com.pureauth.PureAuth;
import com.pureauth.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final PureAuth plugin;

    public AdminCommand(PureAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("pureauth.admin")) {
            sender.sendMessage(Utils.format("&cNo permission."));
            return true;
        }
        
        // --- LOGGING ---
        String fullCommand = "/pureauth " + String.join(" ", args);
        plugin.getLogManager().logAction(sender.getName(), fullCommand);
        // ----------------

        String prefix = plugin.getMessage("prefix");

        if (args.length == 0) {
            sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_help")));
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. RELOAD
        if (sub.equals("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_reload")));
            plugin.getDiscordManager().sendEmbed("admin_usage", "🔄 Config Reloaded", "Admin **" + sender.getName() + "** reloaded the configuration.", 16776960);
            return true;
        }

        // 2. STATS
        else if (sub.equals("stats")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_console_only")));
                return true;
            }
            plugin.getDiscordManager().sendEmbed("admin_usage", "📊 Stats Checked", "Console checked plugin stats.", 16776960);
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                int count = plugin.getDataManager().getTotalPlayers();
                FileConfiguration cfg = plugin.getConfig();
                sender.sendMessage(Utils.format("&8&m---------------------------------------"));
                sender.sendMessage(Utils.format(" &b&lPureAuth Stats (v3.6)"));
                sender.sendMessage(Utils.format(" &7Users: &f" + count));
                sender.sendMessage(Utils.format(" &7Safe Mode: &f" + cfg.getBoolean("settings.safe_mode")));
                sender.sendMessage(Utils.format(" &7Discord: &f" + cfg.getBoolean("settings.discord.enabled")));
                sender.sendMessage(Utils.format(" &7Backup: &f" + cfg.getBoolean("settings.backup.enabled")));
                sender.sendMessage(Utils.format("&8&m---------------------------------------"));
            });
            return true;
        }

        // 3. UNLOCK
        else if (sub.equals("unlock")) {
            if (isSafeModeBlocked(sender)) return true;
            if (args.length != 2) {
                sender.sendMessage(Utils.format(prefix + "&cUsage: /pureauth unlock <player>"));
                return true;
            }
            String target = args[1];
            OfflinePlayer op = Bukkit.getOfflinePlayer(target);
            UUID uuid = op.getUniqueId();

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (!plugin.getDataManager().isRegistered(uuid)) {
                    sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_player_not_found")));
                    return;
                }
                plugin.getDataManager().unlockUser(uuid);
                sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_unlock").replace("%player%", target)));
                
                plugin.getDiscordManager().sendEmbed("admin_sensitive", "🔓 Account Unlocked", 
                    "Admin **" + sender.getName() + "** unlocked **" + target + "**.", 65280);
                
                if (op.isOnline() && op.getPlayer() != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> 
                        op.getPlayer().kick(Utils.format("&aAccount Unlocked.\n&fPlease rejoin to login."))
                    );
                }
            });
            return true;
        }

        // 4. UNREGISTER
        else if (sub.equals("unregister")) {
            if (isSafeModeBlocked(sender)) return true;
            if (args.length != 2) {
                sender.sendMessage(Utils.format(prefix + "&cUsage: /pureauth unregister <player>"));
                return true;
            }
            String target = args[1];
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDataManager().unregisterUser(target);
                sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_unregister").replace("%player%", target)));
                plugin.getDiscordManager().sendEmbed("admin_sensitive", "🗑️ Account Unregistered", 
                    "Admin **" + sender.getName() + "** deleted account **" + target + "**.", 16711680);
                
                Player onlineTarget = Bukkit.getPlayer(target);
                if (onlineTarget != null) {
                     plugin.getServer().getScheduler().runTask(plugin, () -> 
                        onlineTarget.kick(Utils.format("&cAccount Unregistered.\n&fPlease rejoin to register again."))
                    );
                }
            });
            return true;
        }

        // 5. REGISTER
        else if (sub.equals("register")) {
            if (isSafeModeBlocked(sender)) return true;
            if (args.length != 3) {
                sender.sendMessage(Utils.format(prefix + "&cUsage: /pureauth register <player> <password>"));
                return true;
            }
            String targetName = args[1];
            String password = args[2];
            UUID targetUUID = Bukkit.getOfflinePlayer(targetName).getUniqueId();
            Player targetOnline = Bukkit.getPlayer(targetName);
            String ip = (targetOnline != null) ? targetOnline.getAddress().getAddress().getHostAddress() : "0.0.0.0";
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getDataManager().isRegistered(targetUUID)) {
                    sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_register_fail").replace("%player%", targetName)));
                    return;
                }
                String hash = Utils.hashPassword(password);
                plugin.getDataManager().registerUser(targetUUID, targetName, hash, ip);
                sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_register_success").replace("%player%", targetName)));
                
                plugin.getDiscordManager().sendEmbed("admin_sensitive", "👤 Admin Register", 
                    "Admin **" + sender.getName() + "** manually registered **" + targetName + "**.", 65535);
            });
            return true;
        }

        // 6. FORCELOGIN
        else if (sub.equals("forcelogin")) {
            if (isSafeModeBlocked(sender)) return true;
            if (args.length != 2) {
                sender.sendMessage(Utils.format(prefix + "&cUsage: /pureauth forcelogin <player>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Utils.format(prefix + "&cPlayer offline."));
                return true;
            }
            plugin.loginPlayer(target);
            sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_force_login").replace("%player%", target.getName())));
            plugin.getDiscordManager().sendEmbed("admin_sensitive", "⚡ Force Login", 
                "Admin **" + sender.getName() + "** force-logged **" + target.getName() + "**.", 16753920);
            return true;
        }

        // 7. INFO
        else if (sub.equals("info")) {
            if (args.length != 2) {
                sender.sendMessage(Utils.format(prefix + "&cUsage: /pureauth info <player>"));
                return true;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                String info = plugin.getDataManager().getPlayerInfo(args[1]);
                if (info == null) sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_player_not_found")));
                else {
                    sender.sendMessage(Utils.format("&8&m---&r &b" + args[1] + " &8&m---"));
                    sender.sendMessage(Utils.format(info));
                }
            });
            return true;
        }

        // 8. SETPASSWORD
        else if (sub.equals("setpassword")) {
            if (isSafeModeBlocked(sender)) return true;
            if (args.length != 3) {
                sender.sendMessage(Utils.format(prefix + "&cUsage: /pureauth setpassword <player> <newpass>"));
                return true;
            }
            String targetName = args[1];
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
            UUID uuid = op.getUniqueId();
            String newHash = Utils.hashPassword(args[2]);

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (!plugin.getDataManager().isRegistered(uuid)) {
                    sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_player_not_found")));
                    return;
                }
                plugin.getDataManager().updatePassword(uuid, newHash);
                sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_pass_set").replace("%player%", targetName)));
                
                plugin.getDiscordManager().sendEmbed("admin_sensitive", "🔑 Admin Password Change", 
                    "Admin **" + sender.getName() + "** changed password for **" + targetName + "**.", 16711680);
            });
            return true;
        }

        // 9. ACCOUNTS
        else if (sub.equals("accounts")) {
            if (args.length != 2) {
                sender.sendMessage(Utils.format(prefix + "&cUsage: /pureauth accounts <IP>"));
                return true;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<String> list = plugin.getDataManager().getAccountsByIp(args[1]);
                sender.sendMessage(Utils.format(prefix + "&eAccounts: &f" + String.join(", ", list)));
            });
            return true;
        }

        // 10. LOCATION
        else if (sub.equals("location")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(Utils.format(prefix + "&cUsage: /pureauth location <login|register>"));
                return true;
            }
            Player p = (Player) sender;
            String type = args[1].toLowerCase();
            if (type.equals("login")) {
                plugin.setSpawnLocation("spawn_login", p.getLocation());
                sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_loc_set").replace("%type%", "Login")));
            } else if (type.equals("register")) {
                plugin.setSpawnLocation("spawn_register", p.getLocation());
                sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_loc_set").replace("%type%", "Register")));
            } else {
                sender.sendMessage(Utils.format(prefix + "&cInvalid type."));
            }
            return true;
        }

        // FALLBACK: Unknown Command
        else {
            sender.sendMessage(Utils.format(prefix + plugin.getMessage("admin_unknown_command")));
            return true;
        }
    }

    private boolean isSafeModeBlocked(CommandSender sender) {
        if (plugin.getConfig().getBoolean("settings.safe_mode") && sender instanceof Player) {
            String prefix = plugin.getMessage("prefix");
            String msg = plugin.getMessage("admin_safe_mode");
            sender.sendMessage(Utils.format(prefix + msg));
            return true;
        }
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("pureauth.admin")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("reload", "unregister", "register", "location", "forcelogin", "unlock", "info", "setpassword", "accounts"));
            if (sender instanceof ConsoleCommandSender) list.add("stats");
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("location")) return Arrays.asList("login", "register");
        return null;
    }
}
