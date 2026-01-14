package me.rrs.titleInfo.commands;

import me.rrs.titleInfo.TitleInfo;
import me.rrs.titleInfo.Waypoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public class TitleInfoCommand implements CommandExecutor, TabCompleter {

    private final TitleInfo plugin;
    private static final List<String> MAIN_SUBCOMMANDS = List.of("display", "share", "waypoint", "admin");
    private static final List<String> WAYPOINT_SUBCOMMANDS = List.of("set", "remove", "list", "view", "tp");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("waypoint", "display");

    public TitleInfoCommand(TitleInfo plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c⚠ This command is only for players!");
            return true;
        }
        if (!command.getName().equalsIgnoreCase("titleinfo")) return false;
        if (args.length == 0) { sendMainUsage(player); return true; }

        switch (args[0].toLowerCase()) {
            case "display" -> handleDisplay(player, args);
            case "share" -> handleShare(player, args);
            case "waypoint" -> handleWaypoint(player, args);
            case "admin" -> handleAdmin(player, args);
            default -> sendMainUsage(player);
        }
        return true;
    }

    // ========================
    // Display
    // ========================
    private void handleDisplay(Player player, String[] args) {
        if (!checkPerm(player, "titleinfo.display", "/titleinfo display")) return;

        if (args.length < 2) {
            player.sendMessage("§6⚡ §lDisplay Usage §6⚡\n§e➜ /titleinfo display <type> [on|off]\n§aTypes: §fcoordinates, direction, time, biome, waypoint");
            return;
        }

        String type = args[1].toLowerCase();
        if (!plugin.getConfiguration().getBoolean("display_options." + type, false)) {
            player.sendMessage("§c✖ §e'" + type + "' §7is disabled by server admins!");
            return;
        }

        Set<String> prefs = plugin.getPlayerDisplayPrefs().computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        boolean enable = args.length > 2 ? args[2].equalsIgnoreCase("on") : !prefs.contains(type);

        if (enable) { prefs.add(type); player.sendMessage("§a✔ §e" + type + " §7display §lENABLED§7!"); }
        else { prefs.remove(type); player.sendMessage("§c✖ §e" + type + " §7display §lDISABLED§7!"); }

        plugin.getDbManager().savePlayerDisplayPrefs(player.getUniqueId(), prefs);
    }

    // ========================
    // Share
    // ========================
    private void handleShare(Player player, String[] args) {
        if (!checkPerm(player, "titleinfo.share", "/titleinfo share")) return;

        Location loc = player.getLocation();
        Component message = Component.text("§6[").append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text("§6] §7is at §eX: " + loc.getBlockX() +
                        "§7, §eY: " + loc.getBlockY() + "§7, §eZ: " + loc.getBlockZ() + " §7in §f" + loc.getWorld().getName()));

        if (args.length == 1) {
            if (!player.hasPermission("titleinfo.share.global")) { player.sendMessage("§c✖ You cannot share coordinates globally."); return; }
            Bukkit.getServer().sendMessage(message);
        } else {
            if (!player.hasPermission("titleinfo.share.private")) { player.sendMessage("§c✖ You cannot share coordinates privately."); return; }
            Player target = Bukkit.getPlayer(args[1]);
            if (!isOnline(player, target, args[1])) return;
            target.sendMessage(message); player.sendMessage("§a✔ Coordinates sent to §e" + target.getName() + "§7!");
        }
    }

    // ========================
    // Waypoint
    // ========================
    private void handleWaypoint(Player player, String[] args) {
        if (!checkPerm(player, "titleinfo.waypoint", "/titleinfo waypoint")) return;
        if (args.length < 2) { sendWaypointUsage(player); return; }

        switch (args[1].toLowerCase()) {
            case "set" -> handleWaypointSet(player, args);
            case "remove" -> handleWaypointRemove(player, args);
            case "list" -> handleWaypointList(player);
            case "view" -> handleWaypointView(player, args);
            default -> sendWaypointUsage(player);
        }
    }

    private void handleWaypointSet(Player player, String[] args) {
        if (!checkPerm(player, "titleinfo.waypoint.set", "/titleinfo waypoint set")) return;
        if (args.length < 3) { player.sendMessage("§6⚡ §lSet Waypoint Usage §6⚡\n§e➜ /titleinfo waypoint set <name>"); return; }

        String name = args[2];
        List<Waypoint> list = plugin.getPlayerWaypoints().computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        list.removeIf(wp -> wp.getName().equalsIgnoreCase(name));

        Location loc;
        if (args.length == 3) loc = player.getLocation();
        else if (args.length == 6) {
            try { loc = new Location(player.getWorld(), Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5])); }
            catch (NumberFormatException e) { player.sendMessage("§c✖ Invalid Coordinates!"); return; }
        } else { player.sendMessage("§6⚡ §lSet Waypoint Usage §6⚡\n§e➜ /titleinfo waypoint set <name>"); return; }

        list.add(new Waypoint(name, loc));
        plugin.getDbManager().savePlayerWaypoints(player.getUniqueId(), list);
        player.sendMessage("§a✔ Waypoint §e'" + name + "' §7set at §eX: " + loc.getBlockX() +
                "§7, §eY: " + loc.getBlockY() + "§7, §eZ: " + loc.getBlockZ() + "§7!");
    }

    private void handleWaypointRemove(Player player, String[] args) {
        if (!checkPerm(player, "titleinfo.waypoint.remove", "/titleinfo waypoint remove")) return;
        if (args.length != 3) { player.sendMessage("§6⚡ §lRemove Waypoint Usage §6⚡\n§e➜ /titleinfo waypoint remove <name>"); return; }

        String name = args[2];
        List<Waypoint> list = plugin.getPlayerWaypoints().get(player.getUniqueId());
        if (list != null && list.removeIf(wp -> wp.getName().equalsIgnoreCase(name))) {
            plugin.getDbManager().savePlayerWaypoints(player.getUniqueId(), list);
            String active = plugin.getActiveWaypointNames().get(player.getUniqueId());
            if (name.equalsIgnoreCase(active)) { plugin.getActiveWaypointNames().remove(player.getUniqueId()); plugin.getDbManager().saveActiveWaypointName(player.getUniqueId(), null); player.sendMessage("§7Active waypoint cleared!"); }
            player.sendMessage("§a✔ Waypoint §e'" + name + "' §7removed!");
        } else player.sendMessage("§c✖ Waypoint §e'" + name + "' §7not found!");
    }

    private void handleWaypointList(Player player) {
        if (!checkPerm(player, "titleinfo.waypoint.list", "/titleinfo waypoint list")) return;
        List<Waypoint> list = plugin.getPlayerWaypoints().get(player.getUniqueId());
        if (list == null || list.isEmpty()) { player.sendMessage("§c✖ You have no waypoints!"); return; }

        String active = plugin.getActiveWaypointNames().get(player.getUniqueId());
        player.sendMessage("§6✨ §lYour Waypoints §6✨");
        list.forEach(wp -> { Location l = wp.getLocation(); player.sendMessage("§e- " + wp.getName() + (wp.getName().equalsIgnoreCase(active) ? " §a(active)" : "") + "§7: §eX: " + l.getBlockX() + "§7, §eY: " + l.getBlockY() + "§7, §eZ: " + l.getBlockZ()); });
    }

    private void handleWaypointView(Player player, String[] args) {
        if (!checkPerm(player, "titleinfo.waypoint.view", "/titleinfo waypoint view")) return;

        List<Waypoint> list = plugin.getPlayerWaypoints().get(player.getUniqueId());
        if (list == null || list.isEmpty()) { player.sendMessage("§c✖ You have no waypoints!"); return; }

        String active = plugin.getActiveWaypointNames().get(player.getUniqueId());
        if (args.length == 2) { toggleWaypointView(player, null, active); return; }

        Waypoint wp = list.stream().filter(w -> w.getName().equalsIgnoreCase(args[2])).findFirst().orElse(null);
        if (wp == null) { player.sendMessage("§c✖ Waypoint §e'" + args[2] + "' §7not found!"); return; }
        toggleWaypointView(player, wp.getName(), active);
    }

    private void toggleWaypointView(Player player, String name, String active) {
        if (active != null && (name == null || name.equalsIgnoreCase(active))) {
            plugin.getActiveWaypointNames().remove(player.getUniqueId());
            plugin.getDbManager().saveActiveWaypointName(player.getUniqueId(), null);
            player.sendMessage("§a✔ Waypoint view cleared!");
        } else if (name != null) {
            plugin.getActiveWaypointNames().put(player.getUniqueId(), name);
            plugin.getDbManager().saveActiveWaypointName(player.getUniqueId(), name);
            player.sendMessage("§a✔ Now viewing waypoint §e'" + name + "'§7!");
        }
    }

    // ========================
// Admin
// ========================
    private void handleAdmin(Player player, String[] args) {
        if (!checkPerm(player, "titleinfo.admin", "/titleinfo admin")) return;
        if (args.length < 3) { sendAdminUsage(player); return; }

        String adminType = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);
        if (!isOnline(player, target, args[2])) return;

        switch (adminType) {
            case "waypoint" -> handleAdminWaypoint(player, target, args);
            case "display" -> handleAdminDisplay(player, target, args);
            default -> sendAdminUsage(player);
        }
    }

    // ========================
// Admin Waypoint & Display
// ========================
    private void handleAdminWaypoint(Player player, Player target, String[] args) {
        if (args.length < 4) { sendAdminWaypointUsage(player); return; }

        String sub = args[3].toLowerCase();
        UUID targetUUID = target.getUniqueId();
        List<Waypoint> list = plugin.getPlayerWaypoints().computeIfAbsent(targetUUID, k -> new ArrayList<>());

        switch (sub) {

            case "set" -> {
                if (args.length < 5) { sendAdminWaypointUsage(player); return; }
                String name = args[4];
                list.removeIf(wp -> wp.getName().equalsIgnoreCase(name));

                Location loc;
                if (args.length == 5) loc = player.getLocation();
                else if (args.length == 8) {
                    try {
                        loc = new Location(player.getWorld(), Double.parseDouble(args[5]), Double.parseDouble(args[6]), Double.parseDouble(args[7]));
                    } catch (NumberFormatException e) { player.sendMessage("§c✖ Invalid Coordinates!"); return; }
                } else { sendAdminWaypointUsage(player); return; }

                list.add(new Waypoint(name, loc));
                plugin.getDbManager().savePlayerWaypoints(targetUUID, list);
                player.sendMessage("§a✔ Set waypoint §e'" + name + "' §7for §e" + target.getName() + "§7 at §eX:" + loc.getBlockX() + " §eY:" + loc.getBlockY() + " §eZ:" + loc.getBlockZ() + "§7!");
                target.sendMessage("§a✔ Admin set your waypoint §e'" + name + "'§7!");
            }
            case "remove" -> {
                if (args.length != 5) { sendAdminWaypointUsage(player); return; }
                String name = args[4];
                if (list.removeIf(wp -> wp.getName().equalsIgnoreCase(name))) {
                    plugin.getDbManager().savePlayerWaypoints(targetUUID, list);
                    String active = plugin.getActiveWaypointNames().get(targetUUID);
                    if (name.equalsIgnoreCase(active)) { plugin.getActiveWaypointNames().remove(targetUUID); plugin.getDbManager().saveActiveWaypointName(targetUUID, null); target.sendMessage("§7Active waypoint cleared!"); }
                    player.sendMessage("§a✔ Removed waypoint §e'" + name + "' §7for §e" + target.getName() + "§7!");
                } else player.sendMessage("§c✖ Waypoint §e'" + name + "' §7not found for §e" + target.getName() + "§7!");
            }
            case "list" -> {
                if (list.isEmpty()) { player.sendMessage("§c✖ §e" + target.getName() + " §7has no waypoints!"); return; }
                player.sendMessage("§6✨ §lWaypoints for " + target.getName() + " §6✨");
                String active = plugin.getActiveWaypointNames().get(targetUUID);
                list.forEach(wp -> {
                    Location l = wp.getLocation();
                    player.sendMessage("§e- " + wp.getName() + (wp.getName().equalsIgnoreCase(active) ? " §a(active)" : "") +
                            "§7: §eX:" + l.getBlockX() + " §eY:" + l.getBlockY() + " §eZ:" + l.getBlockZ());
                });
            }
            case "view" -> {
                if (args.length != 5) { sendAdminWaypointUsage(player); return; }
                String name = args[4];
                Waypoint wp = list.stream().filter(w -> w.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
                if (wp == null) { player.sendMessage("§c✖ Waypoint §e'" + name + "' §7not found for §e" + target.getName() + "§7!"); return; }
                String active = plugin.getActiveWaypointNames().get(targetUUID);
                if (name.equalsIgnoreCase(active)) {
                    plugin.getActiveWaypointNames().remove(targetUUID);
                    plugin.getDbManager().saveActiveWaypointName(targetUUID, null);
                    player.sendMessage("§a✔ Cleared active waypoint view for §e" + target.getName() + "§7!");
                    target.sendMessage("§a✔ Admin stopped your view of waypoint §e'" + name + "'§7!");
                } else {
                    plugin.getActiveWaypointNames().put(targetUUID, name);
                    plugin.getDbManager().saveActiveWaypointName(targetUUID, name);
                    player.sendMessage("§a✔ Set §e" + target.getName() + " §7to view waypoint §e'" + name + "'§7!");
                    target.sendMessage("§a✔ Admin set you to view waypoint §e'" + name + "'§7!");
                }
            }
            case "tp" -> { // Admin teleport to a player's waypoint
                if (!player.hasPermission("titleinfo.admin.waypoint.tp")) {
                    player.sendMessage("§c✖ You do not have permission to teleport!");
                    return;
                }

                if (args.length != 5) { // /titleinfo admin waypoint tp <player> <name>
                    sendAdminWaypointUsage(player);
                    return;
                }

                String wpName = args[4];
                Waypoint wp = list.stream()
                        .filter(w -> w.getName().equalsIgnoreCase(wpName))
                        .findFirst()
                        .orElse(null);

                if (wp == null) {
                    player.sendMessage("§c✖ Waypoint §e'" + wpName + "' §7not found for §e" + target.getName() + "§7!");
                    return;
                }

                // Teleport
                player.teleport(wp.getLocation());
                player.sendMessage("§a✔ Teleported to §e" + target.getName() + "'s §7waypoint §e'" + wpName + "'§7!");
            }

            default -> sendAdminWaypointUsage(player);
        }
    }

    private void handleAdminDisplay(Player player, Player target, String[] args) {
        if (args.length != 5) { sendAdminDisplayUsage(player); return; }
        String action = args[3].toLowerCase();
        String type = args[4].toLowerCase();
        if (!plugin.getConfiguration().getBoolean("display_options." + type, false)) {
            player.sendMessage("§c✖ Display type §e'" + type + "' §7is disabled!"); return;
        }

        Set<String> prefs = plugin.getPlayerDisplayPrefs().computeIfAbsent(target.getUniqueId(), k -> new HashSet<>());
        boolean enable = action.equals("enable");
        if (enable) {
            prefs.add(type);
            player.sendMessage("§a✔ Enabled §e" + type + " §7for §e" + target.getName() + "§7!");
            target.sendMessage("§a✔ Admin enabled your §e" + type + " §7display!");
        } else {
            prefs.remove(type);
            player.sendMessage("§a✔ Disabled §e" + type + " §7for §e" + target.getName() + "§7!");
            target.sendMessage("§a✔ Admin disabled your §e" + type + " §7display!");
        }

        plugin.getDbManager().savePlayerDisplayPrefs(target.getUniqueId(), prefs);
    }


    private void sendAdminWaypointUsage(Player player) {
        player.sendMessage("§6⚡ §lAdmin Waypoint Usage §6⚡\n§e➜ /titleinfo admin waypoint <set|remove|list|view|tp> <player> [args]");
    }

    private void sendAdminDisplayUsage(Player player) {
        player.sendMessage("§6⚡ §lAdmin Display Usage §6⚡\n§e➜ /titleinfo admin display <enable|disable> <player> <type>");
    }


    // ========================
    // Helpers
    // ========================
    private boolean checkPerm(Player p, String perm, String cmd) {
        if (!p.hasPermission(perm)) { sendNoPermission(p, cmd); return false; } return true;
    }

    private boolean isOnline(Player sender, Player target, String name) {
        if (target == null || !target.isOnline()) { sender.sendMessage("§c✖ §e'" + name + "' §7not found or offline!"); return false; }
        return true;
    }

    private void sendMainUsage(Player player) {
        player.sendMessage("§6✨ §lTitleInfo Usage §6✨");
        player.sendMessage("§e➜ /titleinfo <display|share|waypoint" + (player.hasPermission("titleinfo.admin") ? "|admin" : "") + ">");
        player.sendMessage("§a  • /titleinfo display <type> [on|off]");
        player.sendMessage("§a  • /titleinfo share [player]");
        player.sendMessage("§a  • /titleinfo waypoint <set|remove|list|view>");
        if (player.hasPermission("titleinfo.admin")) player.sendMessage("§a  • /titleinfo admin <waypoint|display> [args]");
    }

    private void sendWaypointUsage(Player player) {
        player.sendMessage("§6⚡ §lWaypoint Usage §6⚡\n§e➜ /titleinfo waypoint <set|remove|list|view>");
    }

    private void sendNoPermission(Player player, String cmd) {
        player.sendMessage("§c✖ No Permission! §7You cannot use §e" + cmd + "§7.");
    }

    private void sendAdminUsage(Player player) {
        player.sendMessage("§6⚡ §lAdmin Usage §6⚡\n§e➜ /titleinfo admin <waypoint|display>");
    }

    // ========================
    // Tab Completion
    // ========================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (!command.getName().equalsIgnoreCase("titleinfo")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        switch (args.length) {
            case 1 -> { // Main subcommands
                List<String> subs = new ArrayList<>();
                if (player.hasPermission("titleinfo.display")) subs.add("display");
                if (player.hasPermission("titleinfo.share")) subs.add("share");
                if (player.hasPermission("titleinfo.waypoint")) subs.add("waypoint");
                if (player.hasPermission("titleinfo.admin")) subs.add("admin");
                return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
            }
            case 2 -> { // Subcommand arguments
                switch (args[0].toLowerCase()) {
                    case "display" -> {
                        plugin.getConfig().getConfigurationSection("display_options").getKeys(false)
                                .stream().filter(type -> plugin.getConfiguration().getBoolean("display_options." + type))
                                .forEach(completions::add);
                        return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
                    }
                    case "share" -> {
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(completions::add);
                        completions.add("global");
                        return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
                    }
                    case "waypoint" -> {
                        return StringUtil.copyPartialMatches(args[1], WAYPOINT_SUBCOMMANDS, new ArrayList<>());
                    }
                    case "admin" -> {
                        return StringUtil.copyPartialMatches(args[1], ADMIN_SUBCOMMANDS, new ArrayList<>());
                    }
                }
            }
            case 3 -> { // Admin subcommands or waypoint subcommands
                switch (args[0].toLowerCase()) {
                    case "waypoint" -> {
                        String sub = args[1].toLowerCase();
                        if (sub.equals("remove") || sub.equals("view")) {
                            plugin.getPlayerWaypoints().getOrDefault(player.getUniqueId(), new ArrayList<>())
                                    .stream().map(Waypoint::getName).forEach(completions::add);
                            return StringUtil.copyPartialMatches(args[2], completions, new ArrayList<>());
                        }
                        return Collections.emptyList();
                    }
                    case "share" -> {
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(completions::add);
                        completions.add("global");
                        return StringUtil.copyPartialMatches(args[2], completions, new ArrayList<>());
                    }
                    case "admin" -> {
                        String adminSub = args[1].toLowerCase();
                        if (adminSub.equals("waypoint") || adminSub.equals("display")) {
                            // Suggest target player names
                            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(completions::add);
                            return StringUtil.copyPartialMatches(args[2], completions, new ArrayList<>());
                        }
                    }
                }
            }
            case 4 -> { // Admin: waypoint/display subcommands
                if (args[0].equalsIgnoreCase("admin")) {
                    String adminSub = args[1].toLowerCase();
                    Player target = Bukkit.getPlayer(args[2]);
                    if (adminSub.equals("waypoint") && target != null) {
                        return StringUtil.copyPartialMatches(args[3], WAYPOINT_SUBCOMMANDS, new ArrayList<>());
                    } else if (adminSub.equals("display") && target != null) {
                        completions.add("enable");
                        completions.add("disable");
                        return StringUtil.copyPartialMatches(args[3], completions, new ArrayList<>());
                    }
                }
            }
            case 5 -> { // Admin: waypoint name or display type
                if (args[0].equalsIgnoreCase("admin")) {
                    String adminSub = args[1].toLowerCase();
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null) return Collections.emptyList();

                    if (adminSub.equals("waypoint") && (args[3].equalsIgnoreCase("remove")
                            || args[3].equalsIgnoreCase("view")
                            || args[3].equalsIgnoreCase("tp"))) { // <-- add tp here
                        plugin.getPlayerWaypoints().getOrDefault(target.getUniqueId(), new ArrayList<>())
                                .stream().map(Waypoint::getName).forEach(completions::add);
                        return StringUtil.copyPartialMatches(args[4], completions, new ArrayList<>());
                    } else if (adminSub.equals("display") && (args[3].equalsIgnoreCase("enable") || args[3].equalsIgnoreCase("disable"))) {
                        plugin.getConfig().getConfigurationSection("display_options").getKeys(false)
                                .stream().filter(type -> plugin.getConfiguration().getBoolean("display_options." + type))
                                .forEach(completions::add);
                        return StringUtil.copyPartialMatches(args[4], completions, new ArrayList<>());
                    }
                }
            }

            case 6, 7, 8 -> { // Admin: waypoint set coordinates
                if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("waypoint") && args[3].equalsIgnoreCase("set")) {
                    // No suggestions for numeric coordinates
                    return Collections.emptyList();
                }
            }
        }

        return completions;
    }

}
