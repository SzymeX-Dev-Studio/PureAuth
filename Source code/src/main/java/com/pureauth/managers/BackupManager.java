package com.pureauth.managers;

import com.pureauth.PureAuth;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

public class BackupManager {

    private final PureAuth plugin;
    private int taskId = -1;

    public BackupManager(PureAuth plugin) {
        this.plugin = plugin;
    }

    public void startBackupTask() {
        if (!plugin.getConfig().getBoolean("settings.backup.enabled")) return;
        
        int interval = plugin.getConfig().getInt("settings.backup.interval_hours", 24);
        long ticks = interval * 60L * 60L * 20L;

        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::performBackup, ticks, ticks).getTaskId();
        plugin.getLogger().info("Backup system enabled. Running every " + interval + " hours.");
    }

    public void stopBackupTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void performBackup() {
        plugin.getLogger().info("Starting automatic database backup...");
        
        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) backupDir.mkdirs();

        String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String type = plugin.getConfig().getString("database.type", "SQLITE");

        try {
            if (type.equalsIgnoreCase("SQLITE")) {
                backupSQLite(backupDir, date);
            } else if (type.equalsIgnoreCase("MYSQL")) {
                backupMySQL(backupDir, date);
            }
            
            cleanupOldBackups(backupDir);
            plugin.getLogger().info("Backup completed successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Backup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void backupSQLite(File dir, String date) throws Exception {
        File dbFile = new File(plugin.getDataFolder(), "database.db");
        if (!dbFile.exists()) return;
        File target = new File(dir, "database_" + date + ".db");
        Files.copy(dbFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void backupMySQL(File dir, String date) throws Exception {
        File target = new File(dir, "mysql_dump_" + date + ".sql");
        try (FileWriter writer = new FileWriter(target)) {
            String table = plugin.getConfig().getString("database.mysql.table_prefix", "pureauth_") + "users";
            plugin.getDataManager().performBackupDump(writer, table);
        }
    }
    
    private void cleanupOldBackups(File dir) {
        int max = plugin.getConfig().getInt("settings.backup.max_backups", 10);
        File[] files = dir.listFiles();
        if (files == null || files.length <= max) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - max;
        for (int i = 0; i < toDelete; i++) files[i].delete();
    }
}