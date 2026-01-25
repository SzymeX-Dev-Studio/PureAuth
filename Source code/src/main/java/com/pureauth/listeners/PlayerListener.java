package com.pureauth.listeners;

import com.pureauth.PureAuth;
import com.pureauth.Utils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;

public class PlayerListener implements Listener {

    private final PureAuth plugin;

    public PlayerListener(PureAuth plugin) {
        this.plugin = plugin;
    }

    // 1. PRE-LOGIN CHECKS
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        // A. Fail-Safe
        if (Bukkit.getOnlineMode()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, 
                Utils.format("&cFAIL-SAFE: SERVER.PROPERTIES ONLINE-MODE MUST BE FALSE!"));
            return;
        }

        // B. ALREADY ONLINE CHECK
        String incomingName = event.getName();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(incomingName)) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, 
                    Utils.format(plugin.getMessage("prefix") + plugin.getMessage("error_already_online")));
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String ip = p.getAddress().getAddress().getHostAddress();
        
        // 1. GHOST MODE (Hide items + Visibility)
        plugin.hidePlayerState(p);
        
        // 2. CHECK UPDATES
        if (plugin.getUpdateChecker() != null) {
            plugin.getUpdateChecker().notifyAdmin(p);
        }
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean isReg = plugin.getDataManager().isRegistered(p.getUniqueId());

            // 3. LOCK CHECK
            if (isReg && plugin.getDataManager().isLocked(p.getUniqueId())) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Title title = Title.title(
                        Utils.format(plugin.getMessage("title_locked")),
                        Utils.format(plugin.getMessage("subtitle_locked")),
                        Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(10000), Duration.ofMillis(1000))
                    );
                    p.showTitle(title);
                    p.sendMessage(Utils.format(plugin.getMessage("prefix") + plugin.getMessage("error_locked_chat")));
                    // Blindness already applied by hidePlayerState
                });
                return;
            }

            // 4. AUTO LOGIN (IP Trust) - REMOVED LOCALHOST BLOCK
            if (isReg && plugin.getConfig().getBoolean("settings.premium_login.enabled")) {
                if (plugin.getDataManager().isPremium(p.getUniqueId())) {
                    String lastIp = plugin.getDataManager().getLastIp(p.getUniqueId());
                    
                    // Standard IP Check (Works on Localhost now too)
                    if (lastIp != null && lastIp.equals(ip)) {
                        plugin.getDataManager().updateLoginInfo(p.getUniqueId(), ip);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.loginPlayer(p); 
                            p.sendMessage(Utils.format(plugin.getMessage("prefix") + plugin.getMessage("success_premium_login")));
                            
                            Location lastLoc = plugin.getDataManager().getLastLocation(p.getUniqueId());
                            if (lastLoc != null) p.teleport(lastLoc);
                        });
                        return; // DONE
                    } else {
                        // IP Mismatch
                        p.sendMessage(Utils.format(plugin.getMessage("prefix") + plugin.getMessage("premium_ip_changed")));
                    }
                }
            }

            // 5. SESSION CHECK
            if (isReg && plugin.checkSession(p)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.loginPlayer(p); 
                    p.sendMessage(Utils.format(plugin.getMessage("prefix") + plugin.getMessage("success_session")));
                });
                return;
            }
            
            // 6. CAPTCHA
            if (!isReg && plugin.getConfig().getBoolean("settings.captcha.enabled")) {
                plugin.generateCaptcha(p.getUniqueId());
            }

            // 7. START FLOW
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Location loc = isReg ? plugin.getSpawnLocation("spawn_login") : plugin.getSpawnLocation("spawn_register");
                if (loc != null) p.teleport(loc);
                plugin.startLoginFlow(p, isReg);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (!plugin.isLoggedIn(event.getPlayer())) plugin.restorePlayerState(event.getPlayer());
        if (plugin.isLoggedIn(event.getPlayer())) {
            plugin.getDataManager().updateLogoutLocation(event.getPlayer());
            plugin.logoutPlayer(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.isLoggedIn(event.getPlayer())) plugin.restorePlayerState(event.getPlayer());
        if (plugin.isLoggedIn(event.getPlayer())) {
            plugin.getDataManager().updateLogoutLocation(event.getPlayer());
            plugin.logoutPlayer(event.getPlayer(), true);
        } else {
            plugin.logoutPlayer(event.getPlayer(), false);
        }
    }

    // --- Blockers ---
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.isLoggedIn(event.getPlayer())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ() || event.getFrom().getY() != event.getTo().getY()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (plugin.isSetup2FA(p)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.verify2FASetup(p, msg));
            return;
        }
        if (plugin.isRemove2FA(p)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.verify2FARemove(p, msg));
            return;
        }
        if (plugin.isLogin2FA(p)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.verify2FALogin(p, msg));
            return;
        }

        if (!plugin.isLoggedIn(p)) {
            event.setCancelled(true);
            p(p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.isLoggedIn(event.getPlayer())) return;
        String cmd = event.getMessage().split(" ")[0].toLowerCase();
        if (!cmd.equals("/register") && !cmd.equals("/login")) {
            event.setCancelled(true);
            p(event.getPlayer());
        }
    }
    
    @EventHandler public void onInteract(PlayerInteractEvent e) { if (!plugin.isLoggedIn(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (!plugin.isLoggedIn(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player && !plugin.isLoggedIn((Player)e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onBreak(BlockBreakEvent e) { if (!plugin.isLoggedIn(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (!plugin.isLoggedIn(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onInv(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player && !plugin.isLoggedIn((Player)e.getWhoClicked())) e.setCancelled(true); }

    private void p(Player p) { 
        p.sendMessage(Utils.format(plugin.getMessage("prefix") + plugin.getMessage("error_not_logged"))); 
    }
}