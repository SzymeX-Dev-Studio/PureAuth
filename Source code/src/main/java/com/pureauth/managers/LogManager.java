package com.pureauth.managers;

import com.pureauth.PureAuth;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogManager {

    private final PureAuth plugin;
    private final File logFile;

    public LogManager(PureAuth plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "admin_log.txt");
        createFile();
    }

    private void createFile() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void logAction(String actor, String command) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                pw.println("[" + date + "] " + actor + " executed: " + command);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write to admin log: " + e.getMessage());
            }
        });
    }
}