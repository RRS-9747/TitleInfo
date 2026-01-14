package me.rrs.titleInfo;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.Pattern;
import dev.dejvokep.boostedyaml.dvs.segment.Segment;
import dev.dejvokep.boostedyaml.settings.Settings;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import me.rrs.titleInfo.commands.TitleInfoCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class TitleInfo extends JavaPlugin {

    // Config & Database
    private YamlDocument config;
    private DatabaseManager dbManager;

    // Player Data
    private final Map<UUID, Set<String>> playerDisplayPrefs = new HashMap<>();
    private final Map<UUID, List<Waypoint>> playerWaypoints = new HashMap<>();
    private final Map<UUID, String> activeWaypointNames = new HashMap<>();

    // Title handler
    private Title title;

    // Update checker
    private static final String SPIGOT_RESOURCE_ID = "YOUR_RESOURCE_ID";
    private String latestVersion;

    public YamlDocument getConfiguration() {
        return config;
    }

    public Map<UUID, Set<String>> getPlayerDisplayPrefs() {
        return playerDisplayPrefs;
    }

    public Map<UUID, List<Waypoint>> getPlayerWaypoints() {
        return playerWaypoints;
    }

    public Map<UUID, String> getActiveWaypointNames() {
        return activeWaypointNames;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    @Override
    public void onEnable() {

        // Load configuration
        try {
            loadConfigurations();
        } catch (IOException e) {
            getLogger().severe("Failed to load configurations! Disabling plugin.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Setup database
        dbManager = new DatabaseManager(this);
        dbManager.loadAllData(playerDisplayPrefs, playerWaypoints, activeWaypointNames);

        // Initialize title handler
        title = new Title(this);
        getServer().getPluginManager().registerEvents(title, this);

        // Register command
        TitleInfoCommand commandExecutor = new TitleInfoCommand(this);
        Objects.requireNonNull(getCommand("titleinfo")).setExecutor(commandExecutor);
        getCommand("titleinfo").setTabCompleter(commandExecutor);

        // Schedule action bar updates
        new ActionBarUpdateTask().runTaskTimer(this, 0L, 1L);

        // Check for updates
        checkForUpdates();
    }

    @Override
    public void onDisable() {
        if (dbManager != null) {
            // Save all player data
            playerDisplayPrefs.forEach(dbManager::savePlayerDisplayPrefs);
            playerWaypoints.forEach(dbManager::savePlayerWaypoints);
            activeWaypointNames.forEach(dbManager::saveActiveWaypointName);
            dbManager.close();
        }
    }

    // ========================
    // Config loading
    // ========================
    private void loadConfigurations() throws IOException {
        config = createYamlDocument("config.yml", "Config.Version");

        // Initialize online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayerPrefs(player);
        }
    }

    private YamlDocument createYamlDocument(String fileName, String versionKey) throws IOException {
        return YamlDocument.create(
                new File(getDataFolder(), fileName),
                getResource(fileName),
                new Settings[]{
                        GeneralSettings.DEFAULT,
                        LoaderSettings.builder().setAutoUpdate(true).build(),
                        DumperSettings.DEFAULT,
                        UpdaterSettings.builder()
                                .setAutoSave(true)
                                .setVersioning(new Pattern(new Segment[]{
                                        Segment.range(1, Integer.MAX_VALUE),
                                        Segment.literal("."),
                                        Segment.range(0, 100)
                                }), versionKey)
                                .build()
                }
        );
    }

    public void initializePlayerPrefs(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerDisplayPrefs.containsKey(uuid)) {
            Set<String> enabledOptions = dbManager.getPlayerDisplayPrefs(uuid);

            // Enable default options if none exist
            if (enabledOptions.isEmpty() && config != null) {
                for (Object keyObj : config.getSection("display_options").getKeys()) {
                    String option = keyObj.toString();
                    if (config.getBoolean("display_options." + option, false)) {
                        enabledOptions.add(option);
                    }
                }
            }

            playerDisplayPrefs.put(uuid, enabledOptions);
            dbManager.savePlayerDisplayPrefs(uuid, enabledOptions);
            getLogger().info("Initialized prefs for " + player.getName() + ": " + enabledOptions);
        }
    }

    // ========================
    // Updates
    // ========================
    private void checkForUpdates() {
        UpdateAPI updateAPI = new UpdateAPI();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String currentVersion = getDescription().getVersion();
                    latestVersion = updateAPI.getSpigotVersion(SPIGOT_RESOURCE_ID, TitleInfo.this);

                    if (latestVersion != null && !currentVersion.equals(latestVersion)) {
                        getLogger().warning("§6[TitleInfo] §eA new version (§f" + latestVersion + "§e) is available! You’re running §f" + currentVersion + "§e.");
                        getLogger().warning("§eDownload it at: §fhttps://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID + "/");
                        notifyAdminsOfUpdate();
                    } else {
                        getLogger().info("§a[TitleInfo] You’re running the latest version (§f" + currentVersion + "§a)!");
                    }
                } catch (Exception e) {
                    getLogger().warning("§c[TitleInfo] Failed to check for updates: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(this);
    }

    private void notifyAdminsOfUpdate() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("titleinfo.admin")) {
                        player.sendMessage("§6[TitleInfo] §eA new version (§f" + latestVersion + "§e) is available!");
                        player.sendMessage("§eYou’re running §f" + getDescription().getVersion() + "§e. Update at: §fhttps://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID + "/");
                    }
                }
            }
        }.runTask(this);
    }

    // ========================
    // Inner class: Action Bar Updates
    // ========================
    private class ActionBarUpdateTask extends BukkitRunnable {
        @Override
        public void run() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                title.updatePlayerInfo(player);
            }
        }
    }
}
