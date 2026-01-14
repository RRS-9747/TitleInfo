package me.rrs.titleInfo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final TitleInfo plugin;

    public DatabaseManager(TitleInfo plugin) {
        this.plugin = plugin;
        this.dataSource = setupDataSource();
        initializeDatabase();
    }

    private HikariDataSource setupDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + File.separator + "titleinfo.db");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(30000);
        return new HikariDataSource(config);
    }

    private void initializeDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_prefs (" +
                    "uuid TEXT PRIMARY KEY, prefs TEXT NOT NULL)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_waypoints (" +
                    "uuid TEXT, name TEXT, world TEXT, x DOUBLE, y DOUBLE, z DOUBLE," +
                    "PRIMARY KEY (uuid, name))");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS active_waypoints (" +
                    "uuid TEXT PRIMARY KEY, active_name TEXT)");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------- Player Display Prefs --------------------
    public void savePlayerDisplayPrefs(UUID uuid, Set<String> prefs) {
        String prefsString = String.join(",", prefs);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO player_prefs (uuid, prefs) VALUES (?, ?)")) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, prefsString);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save display prefs for " + uuid + ": " + e.getMessage());
        }
    }

    public Set<String> getPlayerDisplayPrefs(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT prefs FROM player_prefs WHERE uuid = ?")) {

            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String prefsString = rs.getString("prefs");
                    return new HashSet<>(Arrays.asList(prefsString.split(",")));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get display prefs for " + uuid + ": " + e.getMessage());
        }

        return new HashSet<>();
    }

    // -------------------- Player Waypoints --------------------
    public void savePlayerWaypoints(UUID uuid, List<Waypoint> waypoints) {
        try (Connection conn = dataSource.getConnection()) {
            // Delete old waypoints
            try (PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM player_waypoints WHERE uuid = ?")) {
                deleteStmt.setString(1, uuid.toString());
                deleteStmt.executeUpdate();
            }

            // Insert new waypoints
            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO player_waypoints (uuid, name, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?)")) {

                for (Waypoint wp : waypoints) {
                    Location loc = wp.getLocation();
                    insertStmt.setString(1, uuid.toString());
                    insertStmt.setString(2, wp.getName());
                    insertStmt.setString(3, loc.getWorld().getName());
                    insertStmt.setDouble(4, loc.getX());
                    insertStmt.setDouble(5, loc.getY());
                    insertStmt.setDouble(6, loc.getZ());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save waypoints for " + uuid + ": " + e.getMessage());
        }
    }

    public List<Waypoint> getPlayerWaypoints(UUID uuid) {
        List<Waypoint> waypoints = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT name, world, x, y, z FROM player_waypoints WHERE uuid = ?")) {

            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String worldName = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        waypoints.add(new Waypoint(name, new Location(world, x, y, z)));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get waypoints for " + uuid + ": " + e.getMessage());
        }

        return waypoints;
    }

    // -------------------- Active Waypoint --------------------
    public void saveActiveWaypointName(UUID uuid, String activeName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO active_waypoints (uuid, active_name) VALUES (?, ?)")) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, activeName);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save active waypoint for " + uuid + ": " + e.getMessage());
        }
    }

    public String getActiveWaypointName(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT active_name FROM active_waypoints WHERE uuid = ?")) {

            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("active_name");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active waypoint for " + uuid + ": " + e.getMessage());
        }

        return null;
    }

    // -------------------- Load All Data --------------------
    public void loadAllData(Map<UUID, Set<String>> playerDisplayPrefs,
                            Map<UUID, List<Waypoint>> playerWaypoints,
                            Map<UUID, String> activeWaypointNames) {

        try (Connection conn = dataSource.getConnection()) {

            // Load display prefs
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT uuid, prefs FROM player_prefs")) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String prefsString = rs.getString("prefs");
                    playerDisplayPrefs.put(uuid, new HashSet<>(Arrays.asList(prefsString.split(","))));
                }
            }

            // Load waypoints
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT uuid, name, world, x, y, z FROM player_waypoints")) {

                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("name");
                    String worldName = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        playerWaypoints.computeIfAbsent(uuid, k -> new ArrayList<>())
                                .add(new Waypoint(name, new Location(world, x, y, z)));
                    }
                }
            }

            // Load active waypoints
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT uuid, active_name FROM active_waypoints")) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String activeName = rs.getString("active_name");
                    activeWaypointNames.put(uuid, activeName);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load data from database: " + e.getMessage());
        }
    }

    // -------------------- Close --------------------
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
