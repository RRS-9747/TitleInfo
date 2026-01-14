package me.rrs.titleInfo;

import org.bukkit.Location;

public class Waypoint {
    private final String name;
    private final Location location;

    public Waypoint(String name, Location location) {
        this.name = name;
        this.location = new Location(location.getWorld(), location.getX(), location.getY(), location.getZ());
    }

    public String getName() {
        return this.name;
    }

    public Location getLocation() {
        return this.location;
    }
}
