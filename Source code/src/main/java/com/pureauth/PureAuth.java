package com.pureauth;

import com.pureauth.commands.AdminCommand;
import com.pureauth.commands.AuthCommands;
import com.pureauth.commands.TwoFACommand;
import com.pureauth.database.DataManager;
import com.pureauth.hooks.PureAuthExpansion;
import com.pureauth.listeners.PlayerListener;
import com.pureauth.managers.BackupManager;
import com.pureauth.managers.DiscordManager;
import com.pureauth.managers.LogManager;
import com.pureauth.managers.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PureAuth extends JavaPlugin {

    private static PureAuth instance;
    
    // Managers
    private DataManager dataManager;
    private DiscordManager discordManager;
    private BackupManager backupManager;
    private LogManager logManager;
    private UpdateChecker updateChecker;
    
    // Auth State
    private final Set<UUID> loggedInPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> loginStrikes = new ConcurrentHashMap<>();
    private final Map<UUID, String> captchaCodes = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final Map<UUID, Long> sessionExpiry = new ConcurrentHashMap<>();
    
    // Ghost Mode Cache
    private final Map<UUID, ItemStack[]> inventoryCache = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> armorCache = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> extraCache = new ConcurrentHashMap<>();
    
    // 2FA States
    private final Map<UUID, String> pending2FASecret = new ConcurrentHashMap<>(); 
    private final Set<UUID> pending2FARemove = ConcurrentHashMap.newKeySet(); 
    private final Set<UUID> pending2FALogin = ConcurrentHashMap.newKeySet(); 
    
    // Tasks
    private final Map<UUID, Integer> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> messageTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> titleTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> actionBarTasks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        
        // 1. FAIL-SAFE
        if (Bukkit.getOnlineMode()) {
            getLogger().severe("FAIL-SAFE: SERVER.PROPERTIES ONLINE-MODE MUST BE FALSE!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        saveDefaultConfig();

        // 2. Database
        dataManager = new DataManager(this);
        if (!dataManager.initialize()) {
            getLogger().severe("Database failed!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 3. Managers
        logManager = new LogManager(this); // Logging System
        
        discordManager = new DiscordManager(this);
        discordManager.sendEmbed("server_lifecycle", "🟢 Server Started", "PureAuth v" + getDescription().getVersion() + " started.", 65280);

        backupManager = new BackupManager(this); // Backup System
        backupManager.startBackupTask();
        
        updateChecker = new UpdateChecker(this);
        updateChecker.check();

        // 4. Commands
        AuthCommands authCmd = new AuthCommands(this);
        getCommand("register").setExecutor(authCmd);
        getCommand("login").setExecutor(authCmd);
        getCommand("changepass").setExecutor(authCmd);
        getCommand("premium").setExecutor(authCmd);
        getCommand("cracked").setExecutor(authCmd);
        getCommand("pureauth").setExecutor(new AdminCommand(this));
        getCommand("2fa").setExecutor(new TwoFACommand(this));

        // 5. Listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // 6. Hooks
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PureAuthExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked.");
        }

        getLogger().info("PureAuth v4.0 COMPLETE EDITION ENABLED.");
    }

    @Override
    public void onDisable() {
        if (discordManager != null) discordManager.sendEmbed("server_lifecycle", "🔴 Server Stopped", "PureAuth disabled.", 16711680);
        if (backupManager != null) backupManager.stopBackupTask();
        if (dataManager != null) dataManager.close();
        
        // Restore inventories to prevent loss on shutdown
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isLoggedIn(p)) restorePlayerState(p);
        }

        loggedInPlayers.clear();
        captchaCodes.clear();
        cancelAllTasks();
    }

    // --- Getters ---
    public static PureAuth getInstance() { return instance; }
    public DataManager getDataManager() { return dataManager; }
    public DiscordManager getDiscordManager() { return discordManager; }
    public LogManager getLogManager() { return logManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }

    public String getMessage(String key) {
        String lang = getConfig().getString("settings.language", "EN").toLowerCase();
        if (!lang.equals("en") && !lang.equals("pl")) lang = "en";
        String val = getConfig().getString("messages." + lang + "." + key);
        return val != null ? val : "Msg missing: " + key;
    }

    // --- Ghost Mode Logic ---
    public void hidePlayerState(Player p) {
        inventoryCache.put(p.getUniqueId(), p.getInventory().getContents());
        armorCache.put(p.getUniqueId(), p.getInventory().getArmorContents());
        extraCache.put(p.getUniqueId(), p.getInventory().getExtraContents());
        
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setExtraContents(null);
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(p.getUniqueId())) online.hidePlayer(this, p);
        }
        
        // Apply Blindness immediately
        if (getConfig().getBoolean("settings.blindness_effect", true)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 999999, 255, false, false));
        }
    }

    public void restorePlayerState(Player p) {
        if (inventoryCache.containsKey(p.getUniqueId())) p.getInventory().setContents(inventoryCache.remove(p.getUniqueId()));
        if (armorCache.containsKey(p.getUniqueId())) p.getInventory().setArmorContents(armorCache.remove(p.getUniqueId()));
        if (extraCache.containsKey(p.getUniqueId())) p.getInventory().setExtraContents(extraCache.remove(p.getUniqueId()));
        
        for (Player online : Bukkit.getOnlinePlayers()) online.showPlayer(this, p);
        
        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    // --- Auth Logic ---
    public boolean isLoggedIn(Player player) {
        return loggedInPlayers.contains(player.getUniqueId());
    }

    public void loginPlayer(Player player) {
        if (dataManager.is2FAEnabled(player.getUniqueId())) {
            start2FALogin(player);
        } else {
            completeLogin(player);
        }
    }
    
    private void completeLogin(Player player) {
        loggedInPlayers.add(player.getUniqueId());
        loginStrikes.remove(player.getUniqueId());
        captchaCodes.remove(player.getUniqueId());
        
        cancelLoginTasks(player);
        
        // Restore items/visibility on main thread
        Bukkit.getScheduler().runTask(this, () -> restorePlayerState(player));
        
        getServer().getScheduler().runTaskAsynchronously(this, () -> dataManager.resetKickCount(player.getUniqueId()));
    }

    public void logoutPlayer(Player player, boolean saveSession) {
        loggedInPlayers.remove(player.getUniqueId());
        loginStrikes.remove(player.getUniqueId());
        captchaCodes.remove(player.getUniqueId());
        
        pending2FASecret.remove(player.getUniqueId());
        pending2FARemove.remove(player.getUniqueId());
        pending2FALogin.remove(player.getUniqueId());
        
        cancelLoginTasks(player);
        
        if (saveSession && getConfig().getBoolean("settings.sessions.enabled")) {
            long minutes = getConfig().getLong("settings.sessions.time_minutes", 1);
            sessionExpiry.put(player.getUniqueId(), System.currentTimeMillis() + (minutes * 60 * 1000));
        }
    }

    public boolean checkSession(Player player) {
        if (!getConfig().getBoolean("settings.sessions.enabled")) return false;
        Long expiry = sessionExpiry.get(player.getUniqueId());
        if (expiry != null && System.currentTimeMillis() < expiry) {
            return true;
        }
        sessionExpiry.remove(player.getUniqueId());
        return false;
    }

    // --- 2FA Methods ---
    public boolean isSetup2FA(Player player) { return pending2FASecret.containsKey(player.getUniqueId()); }
    public boolean isRemove2FA(Player player) { return pending2FARemove.contains(player.getUniqueId()); }
    public boolean isLogin2FA(Player player) { return pending2FALogin.contains(player.getUniqueId()); }

    public void start2FASetup(Player p) {
        String secret = TOTP.generateSecretKey();
        pending2FASecret.put(p.getUniqueId(), secret);
        p.sendMessage(Utils.format(getMessage("prefix") + getMessage("2fa_setup_start")));
        p.sendMessage(Utils.format(getMessage("2fa_key_msg").replace("%key%", secret)));
        p.sendMessage(Utils.format(getMessage("2fa_enter_code")));
        showTitle(p, "2fa_title", "2fa_subtitle_setup");
    }
    
    public void verify2FASetup(Player p, String code) {
        String secret = pending2FASecret.get(p.getUniqueId());
        if (TOTP.validate(secret, code)) {
            pending2FASecret.remove(p.getUniqueId());
            dataManager.set2FASecret(p.getUniqueId(), secret);
            dataManager.set2FAEnabled(p.getUniqueId(), true);
            p.sendMessage(Utils.format(getMessage("prefix") + getMessage("2fa_setup_success")));
            showTitle(p, "title_success_2fa", "subtitle_success_2fa");
        } else {
            p.sendMessage(Utils.format(getMessage("prefix") + getMessage("2fa_setup_fail")));
            pending2FASecret.remove(p.getUniqueId());
        }
    }
    
    public void start2FARemove(Player p) {
        pending2FARemove.add(p.getUniqueId());
        p.sendMessage(Utils.format(getMessage("prefix") + getMessage("2fa_verify_remove")));
    }
    
    public void verify2FARemove(Player p, String code) {
        String secret = dataManager.get2FASecret(p.getUniqueId());
        if (TOTP.validate(secret, code)) {
            pending2FARemove.remove(p.getUniqueId());
            dataManager.set2FAEnabled(p.getUniqueId(), false);
            p.sendMessage(Utils.format(getMessage("prefix") + getMessage("2fa_removed")));
            showTitle(p, "title_success_2fa", "subtitle_success_2fa");
        } else {
            p.sendMessage(Utils.format(getMessage("prefix") + getMessage("error_wrong_captcha")));
            pending2FARemove.remove(p.getUniqueId());
        }
    }
    
    public void start2FALogin(Player p) {
        pending2FALogin.add(p.getUniqueId());
        p.sendMessage(Utils.format(getMessage("prefix") + getMessage("2fa_login_req")));
        showTitle(p, "2fa_title", "2fa_subtitle_login");
    }
    
    public void verify2FALogin(Player p, String code) {
        String secret = dataManager.get2FASecret(p.getUniqueId());
        if (TOTP.validate(secret, code)) {
            pending2FALogin.remove(p.getUniqueId());
            p.sendMessage(Utils.format(getMessage("prefix") + getMessage("success_2fa_verify")));
            showTitle(p, "title_success_2fa", "subtitle_success_2fa");
            completeLogin(p);
        } else {
            addStrike(p);
        }
    }

    public String generateCaptcha(UUID uuid) {
        String chars = getConfig().getString("settings.captcha.characters");
        int length = getConfig().getInt("settings.captcha.length");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        String code = sb.toString();
        captchaCodes.put(uuid, code);
        return code;
    }
    
    public boolean validateCaptcha(UUID uuid, String input) {
        if (!getConfig().getBoolean("settings.captcha.enabled")) return true;
        String correct = captchaCodes.get(uuid);
        return correct != null && correct.equalsIgnoreCase(input);
    }

    private void cancelAllTasks() {
        timeoutTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        messageTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        titleTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        actionBarTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
    }

    private void cancelLoginTasks(Player player) {
        UUID uuid = player.getUniqueId();
        if (timeoutTasks.containsKey(uuid)) Bukkit.getScheduler().cancelTask(timeoutTasks.remove(uuid));
        if (messageTasks.containsKey(uuid)) Bukkit.getScheduler().cancelTask(messageTasks.remove(uuid));
        if (titleTasks.containsKey(uuid)) Bukkit.getScheduler().cancelTask(titleTasks.remove(uuid));
        if (actionBarTasks.containsKey(uuid)) Bukkit.getScheduler().cancelTask(actionBarTasks.remove(uuid));
        player.resetTitle();
        player.sendActionBar(Component.empty());
    }

    public void startLoginFlow(Player player, boolean isRegistered) {
        UUID uuid = player.getUniqueId();
        int timeLimit = getConfig().getInt("settings.login_timeout", 60);
        long startTime = System.currentTimeMillis();

        int timeoutId = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isLoggedIn(player) && player.isOnline()) {
                player.kick(Utils.format(getMessage("error_kick_timeout")));
            }
        }, timeLimit * 20L).getTaskId();
        timeoutTasks.put(uuid, timeoutId);

        int msgTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || isLoggedIn(player)) return;
            
            if (isLogin2FA(player)) {
               player.sendMessage(Utils.format(getMessage("prefix") + getMessage("2fa_login_req")));
               return; 
            }
            
            String msgKey = isRegistered ? "reminder_login" : "reminder_register";
            String msg = getMessage(msgKey);
            if (!isRegistered && getConfig().getBoolean("settings.captcha.enabled")) {
                String code = captchaCodes.getOrDefault(uuid, "?????");
                msg = msg.replace("%code%", code);
                player.sendMessage(Utils.format(getMessage("prefix") + getMessage("captcha_message").replace("%code%", code)));
            } else {
                msg = msg.replace("%code%", "");
            }
            player.sendMessage(Utils.format(getMessage("prefix") + msg));
        }, 0L, 200L).getTaskId();
        messageTasks.put(uuid, msgTaskId);

        int titleTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || isLoggedIn(player)) return;
            
            String tTitle = getMessage("join_title");
            String tSub = isRegistered ? getMessage("join_subtitle_login") : getMessage("join_subtitle_register");
            
            if (isLogin2FA(player)) {
                tTitle = getMessage("2fa_title");
                tSub = getMessage("2fa_subtitle_login");
            }
            
            Title title = Title.title(
                Utils.format(tTitle),
                Utils.format(tSub),
                Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(3000), Duration.ofMillis(1000))
            );
            player.showTitle(title);
        }, 0L, 40L).getTaskId();
        titleTasks.put(uuid, titleTaskId);

        int abTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || isLoggedIn(player)) return;
            long remaining = timeLimit - ((System.currentTimeMillis() - startTime) / 1000);
            if (remaining < 0) remaining = 0;
            player.sendActionBar(Utils.format(getMessage("actionbar_timer").replace("%time%", String.valueOf(remaining))));
        }, 0L, 20L).getTaskId();
        actionBarTasks.put(uuid, abTaskId);
    }
    
    private void showTitle(Player p, String tKey, String sKey) {
        Title title = Title.title(
            Utils.format(getMessage(tKey)),
            Utils.format(getMessage(sKey)),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(5000), Duration.ofMillis(1000))
        );
        p.showTitle(title);
    }

    public void addStrike(Player player) {
        UUID uuid = player.getUniqueId();
        int strikes = loginStrikes.getOrDefault(uuid, 0) + 1;
        loginStrikes.put(uuid, strikes);
        
        int max = getConfig().getInt("settings.max_attempts", 3);
        
        if (strikes >= max) {
            getDiscordManager().sendEmbed("failed_attempts", "⚠️ Failed Login Attempts", 
                "Player **" + player.getName() + "** exceeded max attempts and was kicked.", 16753920);
            
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                dataManager.incrementKickCount(uuid);
                if (getConfig().getBoolean("settings.security.account_lock_enabled")) {
                    int kicks = dataManager.getKickCount(uuid);
                    int limit = getConfig().getInt("settings.security.kicks_before_lock");
                    if (kicks >= limit) {
                        dataManager.setLocked(uuid, true);
                        getDiscordManager().sendEmbed("account_locked", "⛔ Account Locked", 
                            "Account **" + player.getName() + "** has been PERMANENTLY LOCKED.", 16711680);
                    }
                }
            });
            Bukkit.getScheduler().runTask(this, () -> player.kick(Utils.format(getMessage("error_kick_attempts"))));
        } else {
            String msg = getMessage("error_wrong_pass").replace("%attempts%", String.valueOf(strikes)).replace("%max%", String.valueOf(max));
            player.sendMessage(Utils.format(getMessage("prefix") + msg));
        }
    }
    
    public void setSpawnLocation(String key, Location loc) {
        if (loc == null) return;
        String s = loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ() + ":" + loc.getYaw() + ":" + loc.getPitch();
        getConfig().set("settings.locations." + key, s);
        saveConfig();
    }
    
    public Location getSpawnLocation(String key) {
        String s = getConfig().getString("settings.locations." + key);
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(":");
            return new Location(Bukkit.getWorld(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (Exception e) { return null; }
    }
}