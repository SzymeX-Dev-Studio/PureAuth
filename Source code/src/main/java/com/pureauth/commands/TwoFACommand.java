package com.pureauth.commands;

import com.pureauth.PureAuth;
import com.pureauth.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TwoFACommand implements CommandExecutor, TabCompleter {

    private final PureAuth plugin;

    public TwoFACommand(PureAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        
        Player p = (Player) sender;
        String prefix = plugin.getMessage("prefix");

        if (!plugin.isLoggedIn(p)) {
            p.sendMessage(Utils.format(prefix + plugin.getMessage("error_not_logged")));
            return true;
        }

        if (args.length != 1) {
            p.sendMessage(Utils.format(prefix + "&cUsage: /2fa <setup|remove>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("setup")) {
            if (plugin.getDataManager().is2FAEnabled(p.getUniqueId())) {
                p.sendMessage(Utils.format(prefix + plugin.getMessage("2fa_already")));
                return true;
            }
            plugin.start2FASetup(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            if (!plugin.getDataManager().is2FAEnabled(p.getUniqueId())) {
                p.sendMessage(Utils.format(prefix + plugin.getMessage("2fa_not_enabled")));
                return true;
            }
            plugin.start2FARemove(p);
            return true;
        }

        p.sendMessage(Utils.format(prefix + "&cUsage: /2fa <setup|remove>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("setup", "remove");
        }
        return Collections.emptyList();
    }
}