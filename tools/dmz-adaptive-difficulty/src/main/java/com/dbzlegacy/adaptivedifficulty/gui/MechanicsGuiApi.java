package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public static API for Bukkit companion reflection — Legacy Mechanics hub ({@code /lm}).
 * Players are guided to {@code /lm} only; hub buttons use {@code lm do open &lt;system&gt;}.
 */
public final class MechanicsGuiApi {
    private MechanicsGuiApi() {}

    public static Map<String, String> placeholders(ServerPlayer player) {
        Map<String, String> out = new HashMap<>();
        out.put("bridge_ok", "false");
        if (player == null) {
            return out;
        }
        DifficultyConfig c = DifficultyConfig.get();
        out.put("bridge_ok", "true");
        out.put("staff", StaffAccess.isStaff(player) ? "true" : "false");
        out.put("skillcheck", SkillCheckService.canUse(player) ? "true" : "false");
        out.put("skillcheck_session", SkillCheckService.inSession(player) ? "true" : "false");
        out.put("progression", c.enableProgression ? "true" : "false");
        out.put("rival", c.enableRivalSystem ? "true" : "false");
        out.put("spar", c.enableSparringSystem ? "true" : "false");
        out.put("prestige", c.enablePrestigeSystem ? "true" : "false");
        out.put("skills", c.enableSkillUnlockService ? "true" : "false");
        out.put("syslog", c.enableSystemTelemetry ? "true" : "false");
        out.put("syslog_status", SystemTelemetry.statusLine());
        LmOverhaulScaledCombat.putPlaceholders(out, player);
        return out;
    }

    public static List<String> linesForPage(ServerPlayer player, String page) {
        String p = page == null || page.isBlank() ? "main" : page.toLowerCase(Locale.ROOT);
        boolean staff = player != null && StaffAccess.isStaff(player);
        boolean skillCheck = player != null && SkillCheckService.canUse(player);
        return switch (p) {
            case "help" -> List.of(
                    "§7Legacy Mechanics - use the menu or §f/lm §7if chat is enabled"
            );
            case "logs", "syslog" -> {
                if (player == null || !StaffAccess.isStaff(player)) {
                    yield List.of("§cStaff only.");
                }
                boolean on = DifficultyConfig.get().enableSystemTelemetry;
                List<String> lines = new ArrayList<>();
                lines.add("§7Event log §f" + (on ? "ON" : "OFF"));
                lines.add("§8" + SystemTelemetry.statusLine());
                lines.add(UltraStyle.DIM + "Use the buttons below to turn the log on or off, see its status, or flush it.");
                lines.add("§8/lm admin syslog on|off|status|flush");
                yield lines;
            }
            default -> {
                Map<String, String> ph = placeholders(player);
                List<String> lore = new ArrayList<>();
                lore.add("§7Systems on this server:");
                lore.add("§7Rival §f" + onOff(ph.get("rival"))
                        + " §8- §7Spar §f" + onOff(ph.get("spar"))
                        + " §8- §7Prestige §f" + onOff(ph.get("prestige")));
                if (staff) {
                    lore.add("§7Progression §f" + onOff(ph.get("progression"))
                            + " §8- §7Skills §f" + onOff(ph.get("skills"))
                            + " §8- §7Event log §f" + onOff(ph.get("syslog")));
                } else if (skillCheck) {
                    lore.add("§7Skill Check is unlocked for you - open it from the hub.");
                } else {
                    lore.add("§8Skill Check is a donator perk - ask staff if interested.");
                }
                yield lore;
            }
        };
    }

    /**
     * Dispatch {@code /lm do} actions. Does not reopen GUI — caller reopens.
     * {@code open} is handled by the Bukkit companion (inventory open); Forge returns a hint.
     */
    public static String handleDo(ServerPlayer player, String action, String arg, String page) {
        if (player == null) {
            return "§cThis only works in-game - console can't open this menu.";
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        String a = arg == null ? "" : arg.trim();
        if ("page".equals(act) || "refresh".equals(act)) {
            return "";
        }
        if ("open".equals(act)) {
            // Bukkit plugin opens inventories; Forge chat fallback hints the click path.
            return "";
        }
        if ("syslog".equals(act)) {
            if (!StaffAccess.isStaff(player)) {
                return "§cStaff only.";
            }
            String sub = a.toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "on", "true", "enable" -> {
                    SystemTelemetry.setEnabled(true);
                    yield "§eEvent log ON";
                }
                case "off", "false", "disable" -> {
                    SystemTelemetry.setEnabled(false);
                    yield "§eEvent log OFF";
                }
                case "flush" -> {
                    SystemTelemetry.flushAndClose();
                    yield "§eLogs flushed.";
                }
                case "status", "0", "" -> "§e" + SystemTelemetry.statusLine();
                default -> "§cSomething went wrong. Try again, or ask staff if it keeps happening."
                        + "\n§8/lm do syslog on|off|status|flush";
            };
        }
        if ("reload".equals(act) || "reloadconfig".equals(act)) {
            if (!StaffAccess.isStaff(player)) {
                return "§cStaff only.";
            }
            return DifficultyConfig.reload()
                    ? "§eLegacy Mechanics config reloaded."
                    : UltraStyle.dangerLine("Config reload failed - check the server log. Previous live values kept.");
        }
        return "§cSomething went wrong. Try again, or ask staff if it keeps happening.";
    }

    private static String onOff(String v) {
        return "true".equalsIgnoreCase(v) ? "ON" : "OFF";
    }
}
