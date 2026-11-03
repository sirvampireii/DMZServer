package com.dbzlegacy.adaptivedifficulty.command;

/** Player- and staff-facing chat lines for Legacy Mechanics commands (warm, consistent tone). */
public final class LmCommandMessages {
    private LmCommandMessages() {}

    public static final String NEED_STAFF =
            "§cYou need staff permission for that (op or difficulty.admin).";

    public static final String STAFF_ONLY = "§cStaff only.";

    public static final String PLAYERS_ONLY = "§cRun that while logged in as a player.";

    public static final String PLAYERS_ONLY_CONSOLE =
            "§cThat’s for in-game players. From console, see §f/difficulty admin help§7.";

    public static final String PLAYERS_ONLY_INSPECT =
            "§cRun inspect while logged in on your staff account.";

    public static final String NEED_IN_GAME = "§cRun that in-game on your account.";

    public static final String NO_PERMISSION_BRIDGE = "§cYou don’t have permission for that.";

    public static final String SKILLCHECK_DONATOR =
            "§cSkill Check is for donators—use §f/lm open skillcheck §7when you have access.";

    public static String tryCommand(String command) {
        return "§cTry: §f" + command;
    }

    public static String playerOffline(String name) {
        if (name == null || name.isBlank()) {
            return "§cThat player isn’t online right now.";
        }
        return "§cThat player isn’t online: §f" + name.trim();
    }

    public static String playerNotFound(String name) {
        if (name == null || name.isBlank()) {
            return "§cCouldn’t find that player.";
        }
        return "§cCouldn’t find that player: §f" + name.trim();
    }

    public static String unknownOpenTarget(String raw, String examples) {
        String label = raw == null || raw.isBlank() ? "?" : raw.trim();
        return "§cUnknown menu §f" + label + "§c. Examples: §f" + examples;
    }

    public static String systemDisabled(String systemName) {
        return "§c" + systemName + " is turned off on this server.";
    }

    public static String turnedOn(String label) {
        return "§a" + label + " is §fon§a now.";
    }

    public static String turnedOff(String label) {
        return "§e" + label + " is §foff§e now.";
    }

    public static String saved(String what) {
        return "§aSaved " + what + ".";
    }

    public static String reloaded(String what) {
        return "§aReloaded " + what + ".";
    }
}
