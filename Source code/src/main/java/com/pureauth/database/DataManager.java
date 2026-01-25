package com.pureauth.database;

import com.pureauth.PureAuth;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataManager {

    private final PureAuth plugin;
    private HikariDataSource dataSource;
    private String tablePrefix;

    public DataManager(PureAuth plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        String type = plugin.getConfig().getString("database.type", "SQLITE");
        tablePrefix = plugin.getConfig().getString("database.mysql.table_prefix", "pureauth_");
        
        HikariConfig config = new HikariConfig();

        if (type.equalsIgnoreCase("MYSQL")) {
            config.setJdbcUrl("jdbc:mysql://" + plugin.getConfig().getString("database.mysql.host") + ":" + 
                              plugin.getConfig().getString("database.mysql.port") + "/" + 
                              plugin.getConfig().getString("database.mysql.database"));
            config.setUsername(plugin.getConfig().getString("database.mysql.username"));
            config.setPassword(plugin.getConfig().getString("database.mysql.password"));
            config.addDataSourceProperty("cachePrepStmts", "true");
        } else {
            File file = new File(plugin.getDataFolder(), "database.db");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
        }

        try {
            dataSource = new HikariDataSource(config);
            createTable();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "users (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "username VARCHAR(16), " +
                "password VARCHAR(255), " +
                "reg_ip VARCHAR(45), " +
                "last_ip VARCHAR(45), " +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "is_premium BOOLEAN DEFAULT FALSE, " +
                "is_locked BOOLEAN DEFAULT FALSE, " +
                "kick_count INT DEFAULT 0, " +
                "is_2fa_enabled BOOLEAN DEFAULT FALSE, " +
                "two_fa_secret VARCHAR(32), " +
                "loc_world VARCHAR(50), loc_x DOUBLE, loc_y DOUBLE, loc_z DOUBLE, loc_yaw FLOAT, loc_pitch FLOAT)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            
            // Migrations
            tryAddColumn(conn, "is_premium", "BOOLEAN DEFAULT FALSE");
            tryAddColumn(conn, "is_locked", "BOOLEAN DEFAULT FALSE");
            tryAddColumn(conn, "kick_count", "INT DEFAULT 0");
            tryAddColumn(conn, "last_ip", "VARCHAR(45)");
            tryAddColumn(conn, "is_2fa_enabled", "BOOLEAN DEFAULT FALSE");
            tryAddColumn(conn, "two_fa_secret", "VARCHAR(32)");
            
        } catch (SQLException e) { e.printStackTrace(); }
    }
    
    private void tryAddColumn(Connection conn, String colName, String type) {
        try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE " + tablePrefix + "users ADD COLUMN " + colName + " " + type)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // --- BACKUP HELPER ---
    public void performBackupDump(FileWriter writer, String tableName) throws SQLException, IOException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tableName)) {
             
            ResultSet rs = ps.executeQuery();
            int columnCount = rs.getMetaData().getColumnCount();
            
            writer.write("-- PureAuth MySQL Dump\n");
            writer.write("-- Table: " + tableName + "\n\n");
            
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append("INSERT INTO ").append(tableName).append(" VALUES (");
                
                for (int i = 1; i <= columnCount; i++) {
                    Object obj = rs.getObject(i);
                    if (obj == null) {
                        sb.append("NULL");
                    } else if (obj instanceof String) {
                        sb.append("'").append(obj.toString().replace("'", "''")).append("'");
                    } else if (obj instanceof Boolean) {
                        sb.append(((Boolean) obj) ? 1 : 0);
                    } else {
                        sb.append(obj.toString());
                    }
                    if (i < columnCount) sb.append(", ");
                }
                sb.append(");\n");
                writer.write(sb.toString());
            }
        }
    }

    public void close() { if (dataSource != null) dataSource.close(); }

    public boolean isRegistered(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM " + tablePrefix + "users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public void registerUser(UUID uuid, String name, String password, String ip) {
        String sql = "INSERT INTO " + tablePrefix + "users (uuid, username, password, reg_ip, last_ip, last_login) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, password);
            ps.setString(4, ip);
            ps.setString(5, ip);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- 2FA Methods ---
    public boolean is2FAEnabled(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT is_2fa_enabled FROM " + tablePrefix + "users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean("is_2fa_enabled");
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public String get2FASecret(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT two_fa_secret FROM " + tablePrefix + "users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("two_fa_secret");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public void set2FASecret(UUID uuid, String secret) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET two_fa_secret=? WHERE uuid=?")) {
            ps.setString(1, secret);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void set2FAEnabled(UUID uuid, boolean enabled) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET is_2fa_enabled=? WHERE uuid=?")) {
            ps.setBoolean(1, enabled);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- Locking Logic ---
    public boolean isLocked(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT is_locked FROM " + tablePrefix + "users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean("is_locked");
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public void setLocked(UUID uuid, boolean locked) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET is_locked=? WHERE uuid=?")) {
            ps.setBoolean(1, locked);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public int getKickCount(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT kick_count FROM " + tablePrefix + "users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("kick_count");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public void incrementKickCount(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET kick_count = kick_count + 1 WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void resetKickCount(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET kick_count = 0 WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    
    public void unlockUser(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET is_locked=FALSE, kick_count=0 WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- Premium Logic ---
    public boolean isPremium(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT is_premium FROM " + tablePrefix + "users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean("is_premium");
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public void setPremium(UUID uuid, boolean premium) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET is_premium=? WHERE uuid=?")) {
            ps.setBoolean(1, premium);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    
    public String getLastIp(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT last_ip FROM " + tablePrefix + "users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("last_ip");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // --- Standard Methods ---
    public String getPassword(UUID uuid) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT password FROM " + tablePrefix + "users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("password");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public void updatePassword(UUID uuid, String newHash) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET password=? WHERE uuid=?")) {
            ps.setString(1, newHash);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void updateLoginInfo(UUID uuid, String ip) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET last_ip=?, last_login=CURRENT_TIMESTAMP WHERE uuid=?")) {
            ps.setString(1, ip);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    
    public int getAccountCount(String ip) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tablePrefix + "users WHERE reg_ip=?")) {
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public void updateLogoutLocation(Player p) {
        if (!plugin.isLoggedIn(p)) return;
        Location loc = p.getLocation();
        String sql = "UPDATE " + tablePrefix + "users SET loc_world=?, loc_x=?, loc_y=?, loc_z=?, loc_yaw=?, loc_pitch=? WHERE uuid=?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.setString(7, p.getUniqueId().toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public Location getLastLocation(UUID uuid) {
        String sql = "SELECT loc_world, loc_x, loc_y, loc_z, loc_yaw, loc_pitch FROM " + tablePrefix + "users WHERE uuid=?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getString("loc_world") != null) {
                return new Location(Bukkit.getWorld(rs.getString("loc_world")), rs.getDouble("loc_x"), rs.getDouble("loc_y"), rs.getDouble("loc_z"), rs.getFloat("loc_yaw"), rs.getFloat("loc_pitch"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public void unregisterUser(String username) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tablePrefix + "users WHERE username=?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void setPasswordByName(String username, String hash) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE " + tablePrefix + "users SET password=? WHERE username=?")) {
            ps.setString(1, hash);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String getPlayerInfo(String username) {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT reg_ip, last_ip, last_login, is_premium, is_locked, kick_count, is_2fa_enabled FROM " + tablePrefix + "users WHERE username=?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return "&7Reg IP: &b" + rs.getString("reg_ip") + 
                       "\n&7Last IP: &b" + rs.getString("last_ip") + 
                       "\n&7Premium: &e" + rs.getBoolean("is_premium") + 
                       "\n&72FA: &e" + rs.getBoolean("is_2fa_enabled") +
                       "\n&7Locked: &c" + rs.getBoolean("is_locked") + " (" + rs.getInt("kick_count") + " kicks)" +
                       "\n&7Last Login: &e" + rs.getString("last_login");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
    
    public List<String> getAccountsByIp(String ip) {
        List<String> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT username FROM " + tablePrefix + "users WHERE reg_ip=?")) {
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString("username"));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    
    public int getTotalPlayers() {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tablePrefix + "users")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }
}
