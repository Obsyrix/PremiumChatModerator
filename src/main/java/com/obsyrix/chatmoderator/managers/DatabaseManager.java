package com.obsyrix.chatmoderator.managers;

import com.obsyrix.chatmoderator.ChatModerator;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final ChatModerator plugin;
    private Connection connection;

    public DatabaseManager(ChatModerator plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        String filename = plugin.getConfig().getString("database.filename", "database.db");
        File dataFolder = new File(plugin.getDataFolder(), filename);

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder.getAbsolutePath());
            createTables();
            plugin.getLogger().info("Database connected successfully.");
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() {
        // Infractions table for chat violations
        String infractionsSql = "CREATE TABLE IF NOT EXISTS infractions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "username TEXT NOT NULL," +
                "original_message TEXT NOT NULL," +
                "censored_message TEXT," +
                "score REAL NOT NULL," +
                "api_version TEXT NOT NULL," +
                "moderation_mode TEXT NOT NULL," +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";

        // Punishments table for mutes/bans history
        String punishmentsSql = "CREATE TABLE IF NOT EXISTS punishments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "username TEXT NOT NULL," +
                "punishment_type TEXT NOT NULL," +
                "duration_millis INTEGER NOT NULL," +
                "reason TEXT NOT NULL," +
                "issued_by TEXT NOT NULL," +
                "issued_by_uuid TEXT," +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "expires_at DATETIME" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(infractionsSql);
            stmt.execute(punishmentsSql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    public void logInfraction(String uuid, String username, String originalMessage, String censoredMessage, double score, String apiVersion, String moderationMode) {
        String sql = "INSERT INTO infractions(uuid, username, original_message, censored_message, score, api_version, moderation_mode) VALUES(?,?,?,?,?,?,?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, username);
            pstmt.setString(3, originalMessage);
            pstmt.setString(4, censoredMessage); // Can be null
            pstmt.setDouble(5, score);
            pstmt.setString(6, apiVersion);
            pstmt.setString(7, moderationMode);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log infraction: " + e.getMessage());
        }
    }

    public void logPunishment(String uuid, String username, String punishmentType, long durationMillis, String reason, String issuedBy, String issuedByUuid) {
        String sql = "INSERT INTO punishments(uuid, username, punishment_type, duration_millis, reason, issued_by, issued_by_uuid, expires_at) VALUES(?,?,?,?,?,?,?,?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, username);
            pstmt.setString(3, punishmentType); // "MUTE", "BAN", "TEMPBAN"
            pstmt.setLong(4, durationMillis); // -1 for permanent
            pstmt.setString(5, reason);
            pstmt.setString(6, issuedBy); // Punisher Name or "Console"
            pstmt.setString(7, issuedByUuid); // Punisher UUID or "CONSOLE"
            
            // Calculate expiry timestamp
            if (durationMillis == -1) {
                pstmt.setString(8, null); // Permanent
            } else {
                long expiryTime = System.currentTimeMillis() + durationMillis;
                pstmt.setLong(8, expiryTime);
            }
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log punishment: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
        }
    }
}
