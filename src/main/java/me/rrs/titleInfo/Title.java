package me.rrs.titleInfo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Title implements Listener {

    private final TitleInfo plugin;

    private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] WAYPOINT_DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    public Title(TitleInfo plugin) {
        this.plugin = plugin;
    }

    /**
     * Updates the action bar for the player with coordinates, direction, time, biome, and waypoint info.
     */
    public void updatePlayerInfo(Player player) {
        if (player == null || !player.isOnline()) return;

        var config = plugin.getConfiguration();
        if (config == null) return;

        Set<String> prefs = plugin.getPlayerDisplayPrefs().getOrDefault(player.getUniqueId(), new HashSet<>());
        Location loc = player.getLocation();
        World world = player.getWorld();

        Component message = Component.empty();
        boolean hasContent = false;

        // ----------------- COORDINATES -----------------
        if (prefs.contains("coordinates") && config.getBoolean("display_options.coordinates", false)) {
            message = message.append(Component.text("XYZ: ", NamedTextColor.GOLD))
                    .append(Component.text(loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + " ", NamedTextColor.WHITE));
            hasContent = true;
        }

        // ----------------- DIRECTION -----------------
        if (prefs.contains("direction") && config.getBoolean("display_options.direction", false)) {
            message = message.append(Component.text(getDirection(loc) + " ", NamedTextColor.GOLD));
            hasContent = true;
        }

        // ----------------- TIME -----------------
        if (prefs.contains("time") && config.getBoolean("display_options.time", false) && world.getEnvironment() == World.Environment.NORMAL) {
            long ticks = world.getTime();
            int hour = (int) ((ticks / 1000 + 6) % 24);
            int minute = (int) ((ticks % 1000) * 60 / 1000);
            String meridian = hour < 12 ? "AM" : "PM";
            int hour12 = hour % 12 == 0 ? 12 : hour % 12;

            message = message.append(Component.text(String.format("%02d:%02d %s ", hour12, minute, meridian), NamedTextColor.WHITE));
            hasContent = true;
        }

        // ----------------- BIOME -----------------
        if (prefs.contains("biome") && config.getBoolean("display_options.biome", false)) {
            String biomeName = formatBiomeName(loc.getBlock().getBiome());
            message = message.append(Component.text("[" + biomeName + "] ", NamedTextColor.GREEN));
            hasContent = true;
        }

        // ----------------- WAYPOINT -----------------
        if (prefs.contains("waypoint") && config.getBoolean("display_options.waypoint", false)) {
            String activeName = plugin.getActiveWaypointNames().get(player.getUniqueId());
            List<Waypoint> waypoints = plugin.getPlayerWaypoints().get(player.getUniqueId());

            if (activeName != null && waypoints != null) {
                for (Waypoint wp : waypoints) {
                    if (wp.getName().equalsIgnoreCase(activeName)) {
                        Location wpLoc = wp.getLocation();

                        if (!loc.getWorld().equals(wpLoc.getWorld())) {
                            message = message.append(Component.text(
                                    "WP: " + wp.getName() + " (in " + getWorldName(wpLoc.getWorld()) + ") ",
                                    NamedTextColor.AQUA));
                        } else {
                            double distance = loc.distance(wpLoc);
                            String dir = getWaypointDirection(loc, wpLoc);
                            message = message.append(Component.text(
                                    "WP: " + wp.getName() + " " + String.format("%.0f", distance) + "m " + dir + " ",
                                    NamedTextColor.AQUA));
                        }
                        hasContent = true;
                        break; // Only show active waypoint
                    }
                }
            }
        }

        if (hasContent) {
            player.sendActionBar(message);
        }
    }

    // ----------------- UTILS -----------------

    private String getWorldName(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Unknown";
        };
    }

    private String formatBiomeName(Biome biome) {
        String[] parts = biome.getKey().getKey().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private String getDirection(Location loc) {
        float yaw = (loc.getYaw() + 360) % 360;
        int index = (int) Math.floor((yaw + 22.5) / 45) % 8;
        return DIRECTIONS[index];
    }

    private String getWaypointDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        return WAYPOINT_DIRECTIONS[(int) Math.round(angle / 45) % 8];
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.initializePlayerPrefs(event.getPlayer());
    }
}
