package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionConfig;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionModuleCatalog;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.GodKiEnhancementShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopHooks;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.SkillUnlockService;
import com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.dragonminez.common.stats.StatsData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public static API for Bukkit companion reflection ({@code LegacyMechanicsGUI}).
 * Progression / Prestige / Skills status maps, lore lines, and {@code do} dispatch —
 * the companion plugin owns inventory reopen.
 */
public final class ProgressionGuiApi {
    private ProgressionGuiApi() {}

    /** Full chat help for {@code /progression} (Bukkit command tree). Staff-only list. */
    public static String commandHelp() {
        return commandHelp(true);
    }

    /** Chat help. Non-staff: Potential Unlock (other actions via /lm GUI). */
    public static String commandHelp(boolean staff) {
        if (!staff) {
            return String.join("\n",
                    "§d§lPotential Unlock",
                    "§8------------",
                    "§7Spar to raise Potential Unlock.",
                    "",
                    "§7Other actions: §f/lm §7-> Prestige - Remove Android - Skill Check");
        }
        return String.join("\n",
                "§6§l/progression §8(alias §7/prog§8) §7- command tree",
                "§e/progression §7- §e/progression gui [page] §8- open GUI",
                "§e/progression help §8- this list",
                "§e/progression status §8- flag + boost summary",
                "§e/progression flags §7- §eadmin §8- flags GUI",
                "§e/progression admin <flag> <on|off> §8- toggle a module flag",
                "§e/progression android [player] §8- Gero android convert",
                "§e/progression android remove [player] §8- remove Android upgrade",
                "§e/progression boost §8- TP boost status",
                "§e/progression boost start <mult> <minutes> [name]",
                "§e/progression boost start <encoded> [name]",
                "§e/progression boost end",
                "§e/progression do <action> [arg] [page] §8- GUI actions",
                "§8Pages: main - skills - tp - race - combat - end - utility - shop - status - economy - admin - help",
                "§8Economy: staff free Ancient Coin costs - §f/lm §7-> Staff Admin -> Progression panel -> Ancient Coins",
                "§8Flags: potential boost bio",
                "§8       yardrat spiritualist android kiweapons piercing dot apothic",
                "§8       end endportal shadow statchecker");
    }

    /** Text status (flags + active boost). */
    public static String statusText() {
        return ProgressionSystem.statusSummary();
    }

    /** Progression section pages → toggle keys (staff GUIs). */
    public static String[] flagKeysForPage(String page) {
        return ProgressionModuleCatalog.flagKeysForPage(page);
    }

    /** Staff chat lines: where to edit one module (Java + config, not CNPC scripts). */
    public static List<String> moduleDocLines(String flagKey) {
        return ProgressionModuleCatalog.formatModuleDoc(flagKey);
    }

    /** Staff chat lines: all modules on a progression section page. */
    public static List<String> sectionModuleDocLines(String page) {
        return ProgressionModuleCatalog.formatSectionDoc(page);
    }

    /** Staff: {@code /progression admin <flag> <on|off>}. */
    public static String adminFlag(ServerPlayer actor, String flag, String value) {
        if (actor == null) {
            return "§cThis only works in-game - console can't open this menu.";
        }
        if (!StaffAccess.isStaff(actor)) {
            return "§cStaff only.";
        }
        if (!DifficultyConfig.get().enableProgression) {
            return "§cProgression system is disabled.";
        }
        if (flag == null || flag.isBlank()) {
            return "§cName a setting and ON or OFF, for example §f/progression admin potential on§c.";
        }
        boolean on = "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
        boolean off = "off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "0".equals(value);
        if (!on && !off) {
            return "§cUse ON or OFF (you typed §f" + value + "§c).";
        }
        if (!ProgressionSystem.setFlag(flag, on)) {
            return "§cSomething went wrong. Try again, or ask staff if it keeps happening.";
        }
        return "§e" + ProgressionSystem.flagLabel(flag) + " - " + (on ? "ON" : "OFF");
    }

    /**
     * Staff End dragon clear/repair — optional Bukkit {@code /lmdo enddragon …}
     * bridge. Prefer Forge {@code /enddragon clear} / {@code /cleardragons} (no CMI).
     * Staff spawn is disabled; players summon from the Difficulty GUI.
     */
    public static String endDragon(ServerPlayer actor, String action) {
        if (actor == null) {
            return "§cThis only works in-game - console can't open this menu.";
        }
        if (!StaffAccess.isStaff(actor)) {
            return "§cStaff only. §7Use §f/enddragon clear§7 (op) or staff.";
        }
        if (!DifficultyConfig.get().enableEndDimensionStrength) {
            return "§cEnd Dimension Strength is disabled.";
        }
        String a = action == null ? "" : action.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (a) {
            case "clear", "cleanup", "kill", "cleardragons", "killdragons" -> {
                com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength.cmdCleanupDragons(actor);
                yield "";
            }
            case "repair" -> {
                com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength.cmdRepairPodium(actor);
                yield "";
            }
            case "spawn", "spawndragon" ->
                    "§cDragon spawn is disabled. §7Admins clear dragons with §f/enddragon clear§7.";
            default -> "§7Staff: §fclear§7 / §frepair§7. §8Player summons: Difficulty GUI only.";
        };
    }

    /**
     * Staff: Dr. Gero android upgrade for {@code actor} (blank target) or an online player name.
     * Used by Bukkit {@code /progression android} — avoids Mohist brigadier forwardCommand.
     * <p>
     * Console / Saga: pass {@code actor == null} with a non-blank online {@code targetName}
     * (also exposed as {@link #androidConvertConsole(String)}).
     */
    public static String androidConvert(ServerPlayer actor, String targetName) {
        if (!DifficultyConfig.get().enableProgression) {
            return "§cProgression system is disabled.";
        }
        if (!DifficultyConfig.get().enableAndroidConversion) {
            return "§cAndroid conversion is disabled.";
        }
        String name = targetName == null ? "" : targetName.trim();
        if (actor == null) {
            if (name.isBlank()) {
                return "§cConsole usage: §fandroidify <player>";
            }
            ServerPlayer target = resolveOnlineByName(name);
            if (target == null) {
                return "§cPlayer not found (must be online): §f" + name;
            }
            return ProgressionSystem.androidConvert(target);
        }
        ServerPlayer target = actor;
        if (!name.isBlank()) {
            target = resolveOnline(actor, name);
            if (target == null) {
                return "§cPlayer not found: §f" + name;
            }
        }
        boolean self = target.m_20148_().equals(actor.m_20148_());
        if (!self && !StaffAccess.isStaff(actor)) {
            return "§cStaff only - you can convert yourself at Dr. Gero or use Android convert in the menu.";
        }
        String who = self ? "yourself" : target.m_7755_().getString();
        if (!GuiClickConfirm.confirmed(actor.m_20148_(), "android-convert:" + target.m_20148_())) {
            return "§eConvert §f" + who + " §eto Android?\n"
                    + "§cThis deletes Super forms and Legendary forms.\n"
                    + "§eClick again within 10 seconds.";
        }
        return ProgressionSystem.androidConvert(target);
    }

    /**
     * Console / Saga / Fabled: {@code androidify <playerName>} (player must be online).
     */
    public static String androidConvertConsole(String targetName) {
        return androidConvert(null, targetName);
    }

    /**
     * Remove Android upgrade (two-click confirm).
     * Players may only remove themselves; staff may target any online player.
     */
    public static String androidRemove(ServerPlayer actor, String targetName) {
        if (actor == null) {
            return "§cThis only works in-game - console can't open this menu.";
        }
        if (!DifficultyConfig.get().enableProgression) {
            return "§cProgression system is disabled.";
        }
        if (!DifficultyConfig.get().enableAndroidConversion) {
            return "§cAndroid tools are disabled.";
        }
        ServerPlayer target = actor;
        String name = targetName == null ? "" : targetName.trim();
        if (!name.isBlank()) {
            target = resolveOnline(actor, name);
            if (target == null) {
                return "§cPlayer not found: §f" + name;
            }
        }
        boolean self = target.m_20148_().equals(actor.m_20148_());
        if (!self && !StaffAccess.isStaff(actor)) {
            return "§cYou can only remove your own Android upgrade.";
        }
        return ProgressionSystem.androidRemove(actor, target);
    }

    /**
     * Global TP boost controls (staff in-game, or console for store purchases).
     * Args (space-separated after {@code /progression boost}):
     * <ul>
     *   <li>empty / status / help — status + usage</li>
     *   <li>end / stop — end active boost</li>
     *   <li>start &lt;encoded&gt; [purchaser…] — Fabled-style encoded start</li>
     *   <li>start &lt;mult&gt; &lt;minutes&gt; [purchaser…] — direct start</li>
     *   <li>&lt;encoded&gt; [purchaser…] — shorthand</li>
     *   <li>&lt;mult&gt; &lt;minutes&gt; [purchaser…] — shorthand</li>
     * </ul>
     * Also accepts GUI args: {@code end}, {@code 2.0:30}, {@code encoded:1250030}.
     * {@code actor} may be {@code null} when run from console (Tebex / panel).
     */
    public static String boost(ServerPlayer actor, String argsJoined) {
        if (actor != null && !StaffAccess.isStaff(actor)) {
            return "§cStaff only.";
        }
        if (!DifficultyConfig.get().enableProgression) {
            return "§cProgression system is disabled.";
        }
        String raw = argsJoined == null ? "" : argsJoined.trim();
        if (raw.isBlank() || "status".equalsIgnoreCase(raw) || "help".equalsIgnoreCase(raw)
                || "?".equals(raw)) {
            return GlobalTpBoost.statusLine() + "\n" + boostUsage();
        }
        String defaultPurchaser = actor == null ? "Server" : actor.m_7755_().getString();
        // GUI compact forms: end | 2.0:30 | encoded:1250030
        if ("end".equalsIgnoreCase(raw) || "stop".equalsIgnoreCase(raw)) {
            String ask = confirmBoost(actor, "boost:end",
                    "§eEnd the active global TP boost for everyone?");
            if (ask != null) {
                return ask;
            }
            return ProgressionSystem.boostEnd();
        }
        if (raw.toLowerCase(Locale.ROOT).startsWith("encoded:")) {
            String num = raw.substring("encoded:".length()).trim();
            try {
                int encoded = Integer.parseInt(num);
                String ask = confirmBoost(actor, "boost:encoded:" + encoded,
                        "§eStart this global TP boost? This broadcasts server-wide.");
                if (ask != null) {
                    return ask;
                }
                return ProgressionSystem.boostStartEncoded(actor, encoded, defaultPurchaser);
            } catch (NumberFormatException e) {
                return "§cThat boost code is not a number: §f" + num + "§c. Use one of the forms below.\n" + boostUsage();
            }
        }
        if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            try {
                double mult = Double.parseDouble(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                String ask = confirmBoost(actor, "boost:" + mult + ":" + minutes, boostStartPrompt(mult, minutes));
                if (ask != null) {
                    return ask;
                }
                return ProgressionSystem.boostStart(actor, mult, minutes, defaultPurchaser);
            } catch (NumberFormatException e) {
                return "§cThat boost needs a multiplier and minutes, like §f2:30§c. You typed: §f" + raw + "\n" + boostUsage();
            }
        }

        String[] parts = raw.split("\\s+");
        String head = parts[0].toLowerCase(Locale.ROOT);
        if ("end".equals(head) || "stop".equals(head)) {
            String ask = confirmBoost(actor, "boost:end",
                    "§eEnd the active global TP boost for everyone?");
            if (ask != null) {
                return ask;
            }
            return ProgressionSystem.boostEnd();
        }
        int i = 0;
        if ("start".equals(head)) {
            i = 1;
            if (parts.length <= 1) {
                return "§cMissing boost args.\n" + boostUsage();
            }
        }
        if (i >= parts.length) {
            return "§cMissing boost args.\n" + boostUsage();
        }
        // Prefer: <mult> <minutes> when two numeric tokens (mult small, minutes < 10000)
        if (i + 1 < parts.length && looksLikeDouble(parts[i]) && looksLikeInt(parts[i + 1])) {
            double mult = Double.parseDouble(parts[i]);
            int minutes = Integer.parseInt(parts[i + 1]);
            // Heuristic: encoded values are huge (e.g. 1250030); mult+minutes are small.
            if (mult < 100.0 && minutes < 10_000) {
                String purchaser = joinFrom(parts, i + 2);
                if (purchaser.isBlank()) {
                    purchaser = defaultPurchaser;
                }
                String ask = confirmBoost(actor, "boost:" + mult + ":" + minutes, boostStartPrompt(mult, minutes));
                if (ask != null) {
                    return ask;
                }
                return ProgressionSystem.boostStart(actor, mult, minutes, purchaser);
            }
        }
        // encoded [purchaser...]
        if (looksLikeInt(parts[i])) {
            try {
                int encoded = Integer.parseInt(parts[i]);
                String purchaser = joinFrom(parts, i + 1);
                if (purchaser.isBlank()) {
                    purchaser = defaultPurchaser;
                }
                String ask = confirmBoost(actor, "boost:encoded:" + encoded,
                        "§eStart this global TP boost? This broadcasts server-wide.");
                if (ask != null) {
                    return ask;
                }
                return ProgressionSystem.boostStartEncoded(actor, encoded, purchaser);
            } catch (NumberFormatException e) {
                return "§cThat boost code is not a number: §f" + parts[i] + "§c. Use one of the forms below.\n" + boostUsage();
            }
        }
        return "§cThat boost amount did not work. Try again, or ask staff if it keeps happening.";
    }

    /**
     * Global mastery boost controls - mirrors {@link #boost(ServerPlayer, String)} on
     * GlobalMasteryBoost with the same arg forms. Confirm keys use a {@code masteryboost:} prefix,
     * so a pending TP confirm never starts or ends a mastery boost, and the reverse.
     * {@code actor} may be {@code null} when run from console.
     */
    public static String masteryBoost(ServerPlayer actor, String argsJoined) {
        if (actor != null && !StaffAccess.isStaff(actor)) {
            return UltraStyle.DANGER + "Staff only. Ask a staff member to start or end a mastery boost.";
        }
        if (!DifficultyConfig.get().enableProgression) {
            return UltraStyle.DANGER
                    + "The progression system is off. Turn it on in the progression settings first.";
        }
        String raw = argsJoined == null ? "" : argsJoined.trim();
        if (raw.isBlank() || "status".equalsIgnoreCase(raw) || "help".equalsIgnoreCase(raw)
                || "?".equals(raw)) {
            return com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalMasteryBoost.statusLine() + "\n" + masteryBoostUsage();
        }
        String defaultPurchaser = actor == null ? "Server" : actor.m_7755_().getString();
        // GUI compact forms: end | 2.0:30 | encoded:1250030
        if ("end".equalsIgnoreCase(raw) || "stop".equalsIgnoreCase(raw)) {
            String ask = confirmBoost(actor, "masteryboost:end", MASTERY_END_PROMPT);
            if (ask != null) {
                return ask;
            }
            return ProgressionSystem.masteryBoostEnd();
        }
        if (raw.toLowerCase(Locale.ROOT).startsWith("encoded:")) {
            String num = raw.substring("encoded:".length()).trim();
            try {
                int encoded = Integer.parseInt(num);
                String ask = confirmBoost(actor, "masteryboost:encoded:" + encoded, MASTERY_CODE_PROMPT);
                if (ask != null) {
                    return ask;
                }
                return ProgressionSystem.masteryBoostStartEncoded(actor, encoded, defaultPurchaser);
            } catch (NumberFormatException e) {
                return masteryCodeNotNumber(num);
            }
        }
        if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            try {
                double mult = Double.parseDouble(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                String ask = confirmBoost(actor, "masteryboost:" + mult + ":" + minutes,
                        masteryBoostStartPrompt(mult, minutes));
                if (ask != null) {
                    return ask;
                }
                return ProgressionSystem.masteryBoostStart(actor, mult, minutes, defaultPurchaser);
            } catch (NumberFormatException e) {
                return UltraStyle.DANGER + "That boost needs a multiplier and minutes, like "
                        + UltraStyle.WHITE + "2:30" + UltraStyle.DANGER + ". You typed: "
                        + UltraStyle.WHITE + raw + "\n" + masteryBoostUsage();
            }
        }

        String[] parts = raw.split("\\s+");
        String head = parts[0].toLowerCase(Locale.ROOT);
        if ("end".equals(head) || "stop".equals(head)) {
            String ask = confirmBoost(actor, "masteryboost:end", MASTERY_END_PROMPT);
            if (ask != null) {
                return ask;
            }
            return ProgressionSystem.masteryBoostEnd();
        }
        int i = 0;
        if ("start".equals(head)) {
            i = 1;
        }
        if (i >= parts.length) {
            return UltraStyle.DANGER + "Add a multiplier and minutes, or a store code, after start.\n"
                    + masteryBoostUsage();
        }
        // Prefer: <mult> <minutes> when two numeric tokens (mult small, minutes < 10000)
        if (i + 1 < parts.length && looksLikeDouble(parts[i]) && looksLikeInt(parts[i + 1])) {
            double mult = Double.parseDouble(parts[i]);
            int minutes = Integer.parseInt(parts[i + 1]);
            // Heuristic: encoded values are huge (e.g. 1250030); mult+minutes are small.
            if (mult < 100.0 && minutes < 10_000) {
                String purchaser = joinFrom(parts, i + 2);
                if (purchaser.isBlank()) {
                    purchaser = defaultPurchaser;
                }
                String ask = confirmBoost(actor, "masteryboost:" + mult + ":" + minutes,
                        masteryBoostStartPrompt(mult, minutes));
                if (ask != null) {
                    return ask;
                }
                return ProgressionSystem.masteryBoostStart(actor, mult, minutes, purchaser);
            }
        }
        // encoded [purchaser...]
        if (looksLikeInt(parts[i])) {
            try {
                int encoded = Integer.parseInt(parts[i]);
                String purchaser = joinFrom(parts, i + 1);
                if (purchaser.isBlank()) {
                    purchaser = defaultPurchaser;
                }
                String ask = confirmBoost(actor, "masteryboost:encoded:" + encoded, MASTERY_CODE_PROMPT);
                if (ask != null) {
                    return ask;
                }
                return ProgressionSystem.masteryBoostStartEncoded(actor, encoded, purchaser);
            } catch (NumberFormatException e) {
                return masteryCodeNotNumber(parts[i]);
            }
        }
        return UltraStyle.DANGER + "That boost amount did not work. Try again, or ask staff if it keeps happening.";
    }

    private static final String MASTERY_END_PROMPT =
            UltraStyle.YELLOW + "End the active global mastery boost for everyone?";

    private static final String MASTERY_CODE_PROMPT =
            UltraStyle.YELLOW + "Start this global mastery boost? This broadcasts server-wide.";

    private static String masteryCodeNotNumber(String typed) {
        return UltraStyle.DANGER + "That boost code is not a number: " + UltraStyle.WHITE + typed
                + UltraStyle.DANGER + ". Use one of the forms below.\n" + masteryBoostUsage();
    }

    private static String masteryBoostStartPrompt(double mult, int minutes) {
        String shown = mult == Math.rint(mult) ? ((int) mult) + "x" : mult + "x";
        return UltraStyle.YELLOW + "Start a " + shown + " mastery boost for " + minutes
                + " minutes? This broadcasts server-wide.";
    }

    private static String masteryBoostUsage() {
        return UltraStyle.YELLOW + "/progression masteryboost " + UltraStyle.GRAY + "- status\n"
                + UltraStyle.YELLOW + "/progression masteryboost end " + UltraStyle.GRAY + "- stop the active boost\n"
                + UltraStyle.YELLOW + "/progression masteryboost start <multiplier> <minutes> [name] "
                + UltraStyle.GRAY + "- for example " + UltraStyle.WHITE + "2 30 PlayerName\n"
                + UltraStyle.YELLOW + "/progression masteryboost start <code> [name] "
                + UltraStyle.GRAY + "- store code, same format as the TP boost\n"
                + UltraStyle.DARK_GRAY + "Console works too (store): "
                + UltraStyle.WHITE + "progression masteryboost start 2 30 {username}\n"
                + UltraStyle.DARK_GRAY + "[Staff] Progression panel -> Boosts tab -> Mastery Boost";
    }

    private static String boostUsage() {
        return "§e/progression boost §7- status\n"
                + "§e/progression boost end §7- stop active boost\n"
                + "§e/progression boost start <mult> <minutes> [name] §7- e.g. §f2 30 PlayerName\n"
                + "§e/progression boost start <encoded> [name] §7- Fabled encoded\n"
                + "§8Console OK (store): §fprogression boost start 2 30 {username}\n"
                + "§8[Staff] CNPC: Staff Admin -> Progression panel -> TP gains -> Global TP boost";
    }

    /** {@code null} when the click may proceed. Console ({@code actor == null}) skips the prompt. */
    private static String confirmBoost(ServerPlayer actor, String key, String prompt) {
        if (actor == null) {
            return null;
        }
        if (GuiClickConfirm.confirmed(actor.m_20148_(), key)) {
            return null;
        }
        return prompt + "\n§eClick again within 10 seconds.";
    }

    private static String boostStartPrompt(double mult, int minutes) {
        String shown = mult == Math.rint(mult) ? ((int) mult) + "x" : mult + "x";
        return "§eStart a " + shown + " TP boost for " + minutes
                + " minutes? This broadcasts server-wide.";
    }

    private static boolean looksLikeInt(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean looksLikeDouble(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String joinFrom(String[] parts, int start) {
        if (parts == null || start >= parts.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        return sb.toString().trim();
    }

    private static ServerPlayer resolveOnline(ServerPlayer actor, String name) {
        String needle = normalizePlayerName(name);
        if (needle.isEmpty()
                || "me".equalsIgnoreCase(needle)
                || "self".equalsIgnoreCase(needle)
                || "@s".equalsIgnoreCase(needle)) {
            return actor;
        }
        if (actor != null) {
            try {
                var server = actor.m_20194_(); // getServer
                if (server != null) {
                    ServerPlayer found = matchOnline(server, needle);
                    if (found != null) {
                        return found;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return resolveOnlineByName(needle);
    }

    /** Resolve an online player without an actor (console / Saga). */
    private static ServerPlayer resolveOnlineByName(String name) {
        String needle = normalizePlayerName(name);
        if (needle.isEmpty()) {
            return null;
        }
        try {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return null;
            }
            return matchOnline(server, needle);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String normalizePlayerName(String name) {
        if (name == null) {
            return "";
        }
        String n = name.trim();
        if (n.length() >= 2
                && ((n.startsWith("\"") && n.endsWith("\""))
                || (n.startsWith("'") && n.endsWith("'")))) {
            n = n.substring(1, n.length() - 1).trim();
        }
        if (n.indexOf('§') >= 0) {
            n = n.replaceAll("§.", "");
        }
        return n;
    }

    private static ServerPlayer matchOnline(net.minecraft.server.MinecraftServer server, String needle) {
        if (server == null || needle == null || needle.isBlank()) {
            return null;
        }
        ServerPlayer exact = server.m_6846_().m_11255_(needle);
        if (exact != null) {
            return exact;
        }
        ServerPlayer prefix = null;
        int prefixHits = 0;
        String lower = needle.toLowerCase(Locale.ROOT);
        for (ServerPlayer online : server.m_6846_().m_11314_()) {
            if (online == null) {
                continue;
            }
            if (playerNameMatches(online, needle)) {
                return online;
            }
            try {
                String login = online.m_6302_();
                if (login != null && login.toLowerCase(Locale.ROOT).startsWith(lower)) {
                    prefix = online;
                    prefixHits++;
                }
            } catch (Throwable ignored) {
            }
        }
        return prefixHits == 1 ? prefix : null;
    }

    private static boolean playerNameMatches(ServerPlayer online, String needle) {
        try {
            String login = online.m_6302_();
            if (login != null && login.equalsIgnoreCase(needle)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            String display = online.m_7755_().getString();
            if (display != null) {
                String plain = display.indexOf('§') >= 0 ? display.replaceAll("§.", "") : display;
                if (plain.trim().equalsIgnoreCase(needle)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ── Progression ────────────────────────────────────────────────────

    public static Map<String, String> placeholders(ServerPlayer player) {
        Map<String, String> out = new HashMap<>();
        out.put("bridge_ok", "false");
        out.put("system_enabled", "false");
        if (player == null) {
            return out;
        }
        DifficultyConfig c = DifficultyConfig.get();
        boolean enabled = c.enableProgression;
        out.put("bridge_ok", "true");
        out.put("system_enabled", enabled ? "true" : "false");
        out.put("staff", StaffAccess.isStaff(player) ? "true" : "false");
        out.put("boost", GlobalTpBoost.statusLine());
        out.put("mastery_boost", com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalMasteryBoost.statusLine());
        out.put("flags", ProgressionConfig.statusSummary());
        out.put("prestige_enabled", c.enablePrestigeSystem ? "true" : "false");
        out.put("skills_enabled", c.enableSkillUnlockService ? "true" : "false");
        out.put("staff_free_ancient_coin_costs", c.staffFreeAncientCoinCosts ? "true" : "false");
        out.put("bypass_ancient_cost", PaidFeatureAccess.bypassAncientCoinCost(player) ? "true" : "false");
        if (!enabled) {
            return out;
        }
        // Skills
        out.put("flag_master", c.enableProgression ? "true" : "false");
        out.put("flag_potential", c.enablePotential ? "true" : "false");
        // TP Gains
        out.put("flag_boost", c.enableGlobalTpBoost ? "true" : "false");
        out.put("flag_bio", c.enableBioAndroid ? "true" : "false");
        // Race & Form
        out.put("flag_yardrat", c.enableYardrat ? "true" : "false");
        out.put("flag_spiritualist", c.enableSpiritualistKi ? "true" : "false");
        out.put("flag_android", c.enableAndroidConversion ? "true" : "false");
        // Combat
        out.put("flag_kiweapons", c.enableKiWeapons ? "true" : "false");
        out.put("flag_piercing", c.enablePiercingBonus ? "true" : "false");
        out.put("flag_dot", c.enableDotExtraDamage ? "true" : "false");
        out.put("flag_apothic", c.enableApothicElemental ? "true" : "false");
        // End
        out.put("flag_end", c.enableEndDimensionStrength ? "true" : "false");
        out.put("flag_endportal", c.enableEndPortalGuard ? "true" : "false");
        out.put("flag_endsummon", c.enableEndPlayerDragonSummon ? "true" : "false");
        // Shop
        out.put("flag_skills", c.enableSkillUnlockService ? "true" : "false");
        out.put("flag_prestige", c.enablePrestigeSystem ? "true" : "false");
        // Utility
        out.put("flag_shadow", c.enableShadowDummyLimiter ? "true" : "false");
        out.put("flag_statchecker", c.enablePlayerStatChecker ? "true" : "false");
        out.put("flag_playerstatchecker", c.enablePlayerStatChecker ? "true" : "false");
        LmOverhaulScaledCombat.putPlaceholders(out, DmzProgression.stats(player));
        return out;
    }

    public static List<String> linesForPage(ServerPlayer player, String page) {
        String p = page == null || page.isBlank() ? "main" : page.toLowerCase(Locale.ROOT);
        if (!DifficultyConfig.get().enableProgression && !"help".equals(p)) {
            return List.of("§cProgression system is disabled.");
        }
        Map<String, String> ph = placeholders(player);
        return switch (p) {
            case "status" -> statusLines(player);
            case "skills" -> categoryLines(
                    "§e§lSkills",
                    "§7Potential Unlock. Spar to raise it.",
                    "",
                    ph,
                    flagLine("Potential", "flag_potential"));
            case "tp" -> categoryLines(
                    "§6§lTP Gains",
                    "§7Training-point sources: global boost",
                    "§7and Bio-Android absorb.",
                    ph,
                    flagLine("Global TP Boost", "flag_boost"),
                    flagLine("Bio-Android Absorb", "flag_bio"));
            case "race" -> categoryLines(
                    "§b§lRace & Form",
                    "§7Yardrat, Spiritualist Ki,",
                    "§7and Android conversion.",
                    ph,
                    flagLine("Yardrat", "flag_yardrat"),
                    flagLine("Spiritualist Ki", "flag_spiritualist"),
                    flagLine("Android convert", "flag_android"));
            case "combat" -> categoryLines(
                    "§c§lCombat",
                    "§7Ki weapons, piercing, DoT extra damage,",
                    "§7and Apothic elemental bridges.",
                    ph,
                    flagLine("Ki Weapons", "flag_kiweapons"),
                    flagLine("Piercing", "flag_piercing"),
                    flagLine("DoT Extra Damage", "flag_dot"),
                    flagLine("Apothic Elemental", "flag_apothic"));
            case "end" -> categoryLines(
                    "§5§lEnd",
                    "§7End Dimension Strength and portal guard.",
                    "",
                    ph,
                    flagLine("End Dimension Strength", "flag_end"),
                    flagLine("End Portal Guard", "flag_endportal"));
            case "shop" -> categoryLines(
                    "§a§lShop",
                    "§7Prestige levels and skill unlock service.",
                    "§7Use buttons below to open those GUIs.",
                    ph,
                    flagLine("Prestige System", "flag_prestige"),
                    flagLine("Skill Unlock Service", "flag_skills"));
            case "utility" -> categoryLines(
                    "§7§lUtility",
                    "§7Shadow dummy limiter and sneak-inspect",
                    "§7player stat checker.",
                    ph,
                    flagLine("Shadow Dummy Limiter", "flag_shadow"),
                    flagLine("Player Stat Checker", "flag_statchecker"));
            case "economy", "ancient_coins", "coins" -> {
                if (player == null || !StaffAccess.isStaff(player)) {
                    yield List.of("§cStaff only.");
                }
                boolean staffFree = "true".equalsIgnoreCase(ph.getOrDefault("staff_free_ancient_coin_costs", "false"));
                List<String> lore = new ArrayList<>();
                lore.add("§6§lAncient Coin economy");
                lore.add("§7Server-wide staff pricing for any LM");
                lore.add("§7feature that charges Ancient Coins.");
                lore.add("");
                lore.add("§7Staff free costs §f" + (staffFree ? "ON" : "OFF"));
                lore.add("");
                lore.add("§8These features charge Ancient Coins:");
                lore.add("§8Difficulty tiers - Character Services - End dragon summon - Model customization");
                lore.add("");
                lore.add("§8/lm §7-> Staff Admin -> Progression panel -> Ancient Coins");
                yield lore;
            }
            case "admin", "flags", "disable" -> {
                if (player == null || !StaffAccess.isStaff(player)) {
                    yield List.of("§cStaff only.");
                }
                yield flagLines(ph);
            }
            case "help" -> {
                List<String> help = new ArrayList<>();
                help.add("§6§l/progression §8- Potential Unlock");
                help.add("§7Spar to raise Potential Unlock.");
                if (player != null && StaffAccess.isStaff(player)) {
                    help.add("§e/progression §7- Category hub (flags per section)");
                    help.add("§e/prog do page skills|tp|race|combat|end|utility");
                    help.add("§e/progression boost §7- status - start &lt;mult&gt; &lt;min&gt; - end");
                    help.add("§e/progression android [player] §7- Android convert (Gero)");
                    help.add("§e/progression android remove [player] §7- remove Android upgrade");
                    help.add("§e/prestige §7- Prestige GUI (staff slash; players use /lm)");
                    help.add("§e/skills §7- Skill unlocks (staff)");
                    help.add("§e/skillcheck §7- Skill Check (donator)");
                    help.add("§8Staff - tap flags in section GUIs - §f/prog do module_doc <section>");
                } else {
                    help.add("§7Other actions: §f/lm §7-> Prestige - Remove Android - Skill Check");
                }
                yield help;
            }
            default -> {
                List<String> lore = new ArrayList<>();
                lore.add(ph.getOrDefault("boost", "§7Global TP boost: §cOFF"));
                lore.add(ph.getOrDefault("mastery_boost",
                        UltraStyle.GRAY + "Global mastery boost: " + UltraStyle.RED + "OFF"));
                lore.add("§8Browse categories to see script ports.");
                yield lore;
            }
        };
    }

    private static List<String> categoryLines(
            String title, String desc1, String desc2, Map<String, String> ph, String... featureLines) {
        List<String> lore = new ArrayList<>();
        lore.add(title);
        if (desc1 != null && !desc1.isEmpty()) {
            lore.add(desc1);
        }
        if (desc2 != null && !desc2.isEmpty()) {
            lore.add(desc2);
        }
        lore.add("");
        for (String line : featureLines) {
            // line format: "Label|flag_key"
            int bar = line.indexOf('|');
            if (bar < 0) {
                lore.add(line);
                continue;
            }
            String label = line.substring(0, bar);
            String key = line.substring(bar + 1);
            boolean on = "true".equalsIgnoreCase(ph.getOrDefault(key, "false"));
            lore.add("§7" + label + " " + (on ? "§aON" : "§cOFF"));
        }
        return lore;
    }

    private static String flagLine(String label, String placeholderKey) {
        return label + "|" + placeholderKey;
    }

    private static List<String> statusLines(ServerPlayer player) {
        List<String> lore = new ArrayList<>();
        for (String line : ProgressionSystem.statusSummary().split("\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            lore.add(line.startsWith("§") ? line : "§7" + line);
        }
        return lore;
    }

    private static List<String> flagLines(Map<String, String> ph) {
        List<String> lore = new ArrayList<>();
        lore.add("§c§lStaff Flags");
        lore.add("§8Grouped by script category");
        lore.add("");
        lore.add("§e§lSkills");
        lore.add(flag("potential", ph));
        lore.add("§6§lTP Gains");
        lore.add(flag("boost", ph));
        lore.add(flag("bio", ph));
        lore.add("§b§lRace & Form");
        lore.add(flag("yardrat", ph));
        lore.add(flag("spiritualist", ph));
        lore.add(flag("android", ph));
        lore.add("§c§lCombat");
        lore.add(flag("kiweapons", ph));
        lore.add(flag("piercing", ph));
        lore.add(flag("dot", ph));
        lore.add(flag("apothic", ph));
        lore.add("§5§lEnd");
        lore.add(flag("end", ph));
        lore.add(flag("endportal", ph));
        lore.add("§a§lShop");
        lore.add(flag("prestige", ph));
        lore.add(flag("skills", ph));
        lore.add("§7§lUtility");
        lore.add(flag("shadow", ph));
        lore.add(flag("statchecker", ph));
        return lore;
    }

    private static String flag(String key, Map<String, String> ph) {
        boolean on = "true".equalsIgnoreCase(ph.getOrDefault("flag_" + key, "false"));
        return "§7" + key + " " + (on ? "§aON" : "§cOFF");
    }

    /**
     * Dispatch {@code /progression do} actions. Does not reopen GUI — caller reopens.
     *
     * @param action {@code page}, {@code flag}, {@code refresh}
     * @param arg    page name, or flag key (toggles), or {@code key:on}/{@code key:off}
     */
    public static String handleDo(ServerPlayer player, String action, String arg, String page) {
        if (player == null) {
            return UltraStyle.DANGER + "This only works in-game - console can't open this menu.";
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        String a = arg == null ? "" : arg.trim();
        boolean staff = StaffAccess.isStaff(player);
        boolean staffMaintenance = staff && ("flag".equals(act) || "toggle".equals(act)
                || "module_doc".equals(act) || "moduledoc".equals(act) || "module".equals(act)
                || "boost".equals(act) || "tpboost".equals(act) || "globaltpboost".equals(act)
                || "masteryboost".equals(act) || "globalmasteryboost".equals(act)
                || "toggle_staff_free_coins".equals(act) || "staff_free_coins".equals(act)
                || "stafffree".equals(act) || "staff_free".equals(act));
        if (!DifficultyConfig.get().enableProgression && !staffMaintenance) {
            return UltraStyle.DANGER + "Progression system is disabled.";
        }
        if ("page".equals(act) || "refresh".equals(act)) {
            return "";
        }
        if ("flag".equals(act) || "toggle".equals(act)) {
            if (!StaffAccess.isStaff(player)) {
                return UltraStyle.DANGER + "Staff only.";
            }
            String key = a;
            Boolean force = null;
            int colon = a.indexOf(':');
            if (colon > 0) {
                key = a.substring(0, colon).trim();
                String val = a.substring(colon + 1).trim();
                if ("on".equalsIgnoreCase(val) || "true".equalsIgnoreCase(val) || "1".equals(val)) {
                    force = true;
                } else if ("off".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val) || "0".equals(val)) {
                    force = false;
                }
            }
            if (key.isBlank()) {
                return UltraStyle.DANGER + "Pick a setting to turn on or off on the Progression panel.";
            }
            boolean next;
            if (force != null) {
                next = force;
            } else {
                Map<String, String> ph = placeholders(player);
                String cur = ph.getOrDefault("flag_" + key.toLowerCase(Locale.ROOT), "false");
                next = !"true".equalsIgnoreCase(cur);
            }
            if (!ProgressionSystem.setFlag(key, next)) {
                return UltraStyle.DANGER + "Something went wrong. Try again, or ask staff if it keeps happening.";
            }
            return UltraStyle.YELLOW + ProgressionSystem.flagLabel(key) + " - " + (next ? "ON" : "OFF");
        }
        if ("module_doc".equals(act) || "moduledoc".equals(act) || "module".equals(act)) {
            if (!StaffAccess.isStaff(player)) {
                return UltraStyle.DANGER + "Staff only.";
            }
            String targetPage = page == null || page.isBlank() ? "main" : page;
            if (a == null || a.isBlank()) {
                return String.join("\n", sectionModuleDocLines(targetPage));
            }
            if (ProgressionModuleCatalog.isSectionPage(a)) {
                return String.join("\n", sectionModuleDocLines(a));
            }
            return String.join("\n", moduleDocLines(a));
        }
        if ("android".equals(act) || "androidconvert".equals(act) || "convertandroid".equals(act)) {
            return androidConvert(player, a);
        }
        if ("android_remove".equals(act) || "androidremove".equals(act)
                || "removeandroid".equals(act) || "remove_android".equals(act)) {
            return androidRemove(player, a);
        }
        if ("boost".equals(act) || "tpboost".equals(act) || "globaltpboost".equals(act)) {
            return boost(player, a);
        }
        if ("masteryboost".equals(act) || "globalmasteryboost".equals(act)) {
            return masteryBoost(player, a);
        }
        if ("toggle_staff_free_coins".equals(act) || "staff_free_coins".equals(act)
                || "stafffree".equals(act) || "staff_free".equals(act)) {
            return toggleStaffFreeAncientCoinCosts(player, a);
        }
        return UltraStyle.DANGER + "Something went wrong. Try again, or ask staff if it keeps happening.";
    }

    /**
     * Server-wide: staff/OP skip Ancient Coin charges on LM paid features when enabled.
     *
     * @param arg blank toggles; {@code on}/{@code off} forces state
     */
    public static String toggleStaffFreeAncientCoinCosts(ServerPlayer player, String arg) {
        if (!StaffAccess.isStaff(player)) {
            return "§cStaff only.";
        }
        DifficultyConfig cfg = DifficultyConfig.get();
        String a = arg == null ? "" : arg.trim().toLowerCase(Locale.ROOT);
        boolean on;
        if ("on".equals(a) || "true".equals(a) || "1".equals(a)) {
            on = true;
        } else if ("off".equals(a) || "false".equals(a) || "0".equals(a)) {
            on = false;
        } else {
            on = !cfg.staffFreeAncientCoinCosts;
        }
        cfg.staffFreeAncientCoinCosts = on;
        DifficultyConfig.save();
        return on
                ? "§aStaff free Ancient Coin costs ON - staff/OP skip coin charges (tiers, Character Services, End dragon, ...)."
                : "§eStaff free Ancient Coin costs OFF - staff/OP pay normal prices.";
    }

    // ── Prestige ───────────────────────────────────────────────────────

    public static Map<String, String> prestigePlaceholders(ServerPlayer player) {
        Map<String, String> out = new HashMap<>();
        out.put("bridge_ok", "false");
        out.put("system_enabled", "false");
        if (player == null) {
            PrestigeShopHooks.decoratePlaceholders(out, player);
            return out;
        }
        boolean enabled = DifficultyConfig.get().enablePrestigeSystem;
        out.put("bridge_ok", "true");
        out.put("system_enabled", enabled ? "true" : "false");
        if (!enabled) {
            PrestigeShopHooks.decoratePlaceholders(out, player);
            return out;
        }
        StatsData data = DmzProgression.stats(player);
        int level = 0;
        if (data != null) {
            try {
                level = Math.max(0, data.getLevel());
            } catch (Throwable ignored) {
            }
        }
        int completed = PrestigeSystem.getCompleted(player);
        int held = PrestigeSystem.heldCountForNeed(player);
        int required = PrestigeSystem.requiredLevel(player);
        out.put("level", String.valueOf(level));
        out.put("level_fmt", DmzRewards.formatWhole(level));
        out.put("completed", String.valueOf(completed));
        out.put("held", String.valueOf(held));
        out.put("held_max", String.valueOf(PrestigeSystem.maxHeld()));
        out.put("required", String.valueOf(required));
        out.put("required_fmt", DmzRewards.formatWhole(required));
        // The requirement is the Overhaul level cap; it is the same after the next prestige.
        out.put("next_required", String.valueOf(required));
        out.put("next_required_fmt", DmzRewards.formatWhole(required));
        out.put("ready", PrestigeSystem.meetsRequirement(level, required) && held < PrestigeSystem.maxHeld()
                ? "true" : "false");
        int points = PrestigePointsSystem.getPoints(player);
        out.put("points", String.valueOf(points));
        out.put("level_cap", String.valueOf(required));
        out.put("level_cap_fmt", DmzRewards.formatWhole(required));
        out.put("majin", PrestigePointsSystem.hasMajin(player) ? "true" : "false");
        out.put("mutant", PrestigePointsSystem.hasMutant(player) ? "true" : "false");
        out.put("majin_can_buy", PrestigePointsSystem.canBuyMajin(player) ? "true" : "false");
        out.put("mutant_can_buy", PrestigePointsSystem.canBuyMutant(player) ? "true" : "false");
        out.put("form_cost", String.valueOf(PrestigePointsSystem.FORM_COST));
        out.put("ultimate", PrestigePointsSystem.hasUltimate(player) ? "true" : "false");
        out.put("ultimate_can_buy", PrestigePointsSystem.canBuyUltimate(player) ? "true" : "false");
        out.put("ultimate_cost", String.valueOf(PrestigePointsSystem.ULTIMATE_COST));
        // DMZ ultimate skill from Old Kai, shop flag unset — the form is already unlocked, so
        // the Effects page must show it as owned (no Buy / no Remove) instead of offering a buy.
        out.put("ultimate_earned",
                (PrestigePointsSystem.hasUltimateSkill(player)
                        && !PrestigePointsSystem.hasUltimate(player)) ? "true" : "false");
        out.put("godki_invested", String.valueOf(GodKiEnhancementShop.invested(player)));
        out.put("godki_maxed", GodKiEnhancementShop.canBuy(player) ? "false" : "true");
        out.put("godki_cost", String.valueOf(GodKiEnhancementShop.INVESTMENT_COST));
        out.put("godki_per_point",
                DmzRewards.formatWhole(GodKiEnhancementShop.MASTERY_PER_POINT));
        out.put("godki_mastery",
                DmzRewards.formatWhole((long) GodKiEnhancementShop.mastery(player)));
        for (int t = 1; t <= 7; t++) {
            out.put("tier_" + t + "_cost", String.valueOf(PrestigePointsSystem.tierPointCost(t)));
            boolean purchased = PrestigePointsSystem.hasPurchasedTier(player, t);
            out.put("tier_" + t + "_owned", purchased ? "true" : "false");
            // Actual unlock bit / purchase only — not bare eligibility (T1 @ DMZ 1).
            boolean unlocked = PrestigePointsSystem.isTierUnlockedOrPurchased(player, t);
            out.put("tier_" + t + "_unlocked", unlocked ? "true" : "false");
            boolean canBuy = PrestigePointsSystem.canBuyDifficultyTier(player, t);
            out.put("tier_" + t + "_can_buy", canBuy ? "true" : "false");
            var ut = com.dbzlegacy.adaptivedifficulty.tier.UnlockTier.byId(t);
            out.put("tier_" + t + "_label", ut == null ? ("T" + t) : ut.display);
        }
        out.put("tier_highest_purchased",
                String.valueOf(PrestigePointsSystem.highestPurchasedTier(player)));
        List<PrestigePointsSystem.SkillOffer> offers = PrestigePointsSystem.skillOffers();
        StringBuilder ids = new StringBuilder();
        for (var offer : offers) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(offer.id());
            int bought = PrestigePointsSystem.getPurchasedSkillLevels(player, offer.id());
            out.put("skill_" + offer.id(), String.valueOf(bought));
            out.put("skill_" + offer.id() + "_max", String.valueOf(offer.maxLevel()));
            out.put("skill_" + offer.id() + "_cost", String.valueOf(PrestigePointsSystem.SKILL_POINT_COST));
            out.put("skill_" + offer.id() + "_label", offer.label());
        }
        out.put("shop_skill_ids", ids.toString());
        out.put("shop_skill_count", String.valueOf(offers.size()));
        out.put("shop_pages", String.valueOf(PrestigePointsSystem.skillShopPageCount()));
        out.put("shop_page_size", String.valueOf(PrestigePointsSystem.SKILL_SHOP_PAGE_SIZE));
        // Turn-in previews (1 / 2 / 3 / 6 / 9 only)
        for (int n : PrestigePointsSystem.TURN_IN_AMOUNTS) {
            out.put("turnin_" + n + "_points", String.valueOf(PrestigePointsSystem.pointsForTurnIn(n)));
        }
        out.put("last_spend_ok", ProgressionData.tempGet(player, "pp_last_spend_ok", "false"));
        PrestigeShopHooks.decoratePlaceholders(out, player);
        return out;
    }

    public static List<String> prestigeLines(ServerPlayer player, String page) {
        Map<String, String> ph = prestigePlaceholders(player);
        if (!"true".equalsIgnoreCase(ph.get("bridge_ok"))) {
            return List.of("§cCouldn't reach the server's systems. Try relogging.");
        }
        if (!"true".equalsIgnoreCase(ph.get("system_enabled"))) {
            return List.of("§cPrestige system is disabled.");
        }
        String p = page == null || page.isBlank() ? "main" : page.toLowerCase(Locale.ROOT);
        List<String> lore = new ArrayList<>();
        lore.add("§7Lifetime prestiges §f" + ph.getOrDefault("completed", "0")
                + " §7- Current prestige amount §f" + ph.getOrDefault("held", "0")
                + "§7/§f" + ph.getOrDefault("held_max", String.valueOf(com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE)));
        switch (p) {
            case "turnin", "points" -> {
                lore.add("§7Prestige favor: §e" + ph.getOrDefault("points", "0"));
                lore.add("§7Exchange current prestige for favor: §f1§7, §f2§7, §f3§7, §f6§7, or §f9 §7at a time.");
                lore.add("§7Bigger turn-ins give bonus favor: §f3->4 §8- §f6->9 §8- §f9->15");
                lore.add("§81-2 give 1 favor each (no pack bonus)");
            }
            case "shop", "skills" -> {
                lore.add("§7Prestige favor: §e" + ph.getOrDefault("points", "0"));
                lore.add("§71 favor -> +1 skill level - §dPotential Unlock §7-> +2");
                lore.add("§aPermanent purchases §7- survive prestige - §8Natural + Saga skills only");
                lore.add("§7Catalog: §f" + ph.getOrDefault("shop_skill_count", "0")
                        + " §7skills - §f" + ph.getOrDefault("shop_pages", "1") + " §7page(s)");
            }
            case "forms", "form", "effects", "effect" -> {
                lore.add("§7Prestige favor: §e" + ph.getOrDefault("points", "0"));
                lore.add("§aPermanent §7Majin / Mutant: §e"
                        + ph.getOrDefault("form_cost", "5") + " §7favor each");
                lore.add("§7You can only have one special form at a time. Remove your current one (Majin or Mutant) before picking the other.");
                lore.add("§7Majin: " + ("true".equals(ph.get("majin")) ? "§aOwned" : "§cNot owned"));
                lore.add("§7Mutant: " + ("true".equals(ph.get("mutant")) ? "§aOwned" : "§cNot owned"));
                if ("true".equals(ph.get("mutant")) && !"true".equals(ph.get("majin"))) {
                    lore.add("§8Remove Mutant to unlock Majin purchase");
                } else if ("true".equals(ph.get("majin")) && !"true".equals(ph.get("mutant"))) {
                    lore.add("§8Remove Majin to unlock Mutant purchase");
                }
            }
            case "cap" -> {
                lore.add("§7Your level cap: §f" + ph.getOrDefault("level_cap_fmt", "100,000"));
                lore.add("§7Reaching your current cap earns your next prestige.");
            }
            case "tiers", "tier", "difficulty" -> {
                lore.add("§7Prestige favor: §e" + ph.getOrDefault("points", "0"));
                lore.add("§7Buy permanent difficulty tier unlocks with prestige favor");
                lore.add("§7T1-2 §e1pt §8- §7T3-4 §e2pt §8- §7T5-6 §e3pt §8- §7T7 §e4pt");
                lore.add("§aPermanent §7- survives prestige - unlock T(n-1) first");
                lore.add("§7Highest purchased: §fT" + ph.getOrDefault("tier_highest_purchased", "0"));
            }
            default -> {
                lore.add("§7Prestige favor: §e" + ph.getOrDefault("points", "0"));
                lore.add("§7Lifetime prestiges: §f" + ph.getOrDefault("completed", "0"));
                lore.add("§7Need: §e" + ph.getOrDefault("required_fmt", "0") + " §7power (your level cap)");
                lore.add("§7Reaching your current cap earns your next prestige.");
                lore.add("§7Each prestige you hold raises your cap.");
                if ("true".equalsIgnoreCase(ph.get("ready"))) {
                    lore.add("§eYou can prestige now.");
                } else {
                    lore.add("§cYou need a higher level first.");
                }
            }
        }
        return lore;
    }

    /**
     * Dispatch {@code /prestige do} — confirm, turn-in, shop buys.
     */
    public static String handlePrestigeDo(ServerPlayer player, String action, String arg, String page) {
        String shop = PrestigeShopHooks.handleDo(player, action, arg, page);
        if (shop != null) {
            return shop;
        }
        if (player == null) {
            return "§cThis only works in-game - console can't open this menu.";
        }
        if (!DifficultyConfig.get().enablePrestigeSystem) {
            return "§cPrestige system is disabled.";
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        if ("page".equals(act) || "refresh".equals(act)) {
            return "";
        }
        if ("confirm_now".equals(act) || "confirm_gui".equals(act)) {
            // Ultra confirm subpage already showed warnings - purchase without chat double-click.
            return PrestigeSystem.confirmFromGui(player);
        }
        if ("confirm".equals(act) || "buy".equals(act) || "purchase".equals(act)) {
            return PrestigeSystem.confirmOrPrompt(player);
        }
        if ("turnin".equals(act) || "turn_in".equals(act) || "redeem".equals(act)) {
            int amount;
            try {
                amount = Integer.parseInt(arg == null || arg.isBlank() ? "0" : arg.trim());
            } catch (NumberFormatException e) {
                return "§cUsage: turn in 1 / 2 / 3 / 6 / 9.";
            }
            return PrestigePointsSystem.turnIn(player, amount);
        }
        if ("skill".equals(act) || "buy_skill".equals(act) || "skillup".equals(act)) {
            return PrestigePointsSystem.buySkillLevel(player, arg);
        }
        if ("majin".equals(act) || "buy_majin".equals(act)) {
            return PrestigePointsSystem.buyMajin(player);
        }
        if ("mutant".equals(act) || "buy_mutant".equals(act)) {
            return PrestigePointsSystem.buyMutant(player);
        }
        if ("unmajin".equals(act) || "unbuy_majin".equals(act) || "remove_majin".equals(act)) {
            return PrestigePointsSystem.unbuyMajin(player);
        }
        if ("unmutant".equals(act) || "unbuy_mutant".equals(act) || "remove_mutant".equals(act)) {
            return PrestigePointsSystem.unbuyMutant(player);
        }
        if ("ultimate".equals(act) || "buy_ultimate".equals(act)) {
            return PrestigePointsSystem.buyUltimate(player);
        }
        if ("unultimate".equals(act) || "unbuy_ultimate".equals(act)
                || "remove_ultimate".equals(act)) {
            return PrestigePointsSystem.unbuyUltimate(player);
        }
        if ("godki".equals(act) || "godki_enhancement".equals(act) || "buy_godki".equals(act)) {
            return GodKiEnhancementShop.buy(player);
        }
        if ("breakthrough".equals(act) || "cap".equals(act) || "buy_cap".equals(act)) {
            return "§7There is nothing to buy for your level cap. Reaching your current cap earns your next prestige.";
        }
        if ("tier".equals(act) || "buy_tier".equals(act) || "unlock_tier".equals(act)) {
            int tierId;
            try {
                tierId = Integer.parseInt(arg == null || arg.isBlank() ? "0" : arg.trim());
            } catch (NumberFormatException e) {
                return "§cUsage: buy tier 1-7.";
            }
            return PrestigePointsSystem.buyDifficultyTier(player, tierId);
        }
        if ("spend".equals(act) || "pay".equals(act) || "npc_spend".equals(act)) {
            int amount;
            try {
                amount = Integer.parseInt(arg == null || arg.isBlank() ? "0" : arg.trim());
            } catch (NumberFormatException e) {
                return "§cUsage: spend <amount> [reason].";
            }
            // page doubles as optional reason/tag for NPC shops when present
            String reason = page == null || page.isBlank() || "main".equalsIgnoreCase(page) ? "npc" : page;
            return PrestigePointsSystem.spendForNpc(player, amount, reason);
        }
        if ("grant".equals(act) || "give_points".equals(act) || "add_points".equals(act)) {
            // Spend stays open for NPC shops; grant is staff-only.
            if (!StaffAccess.isStaff(player)) {
                return "§cStaff only.";
            }
            int amount;
            try {
                amount = Integer.parseInt(arg == null || arg.isBlank() ? "0" : arg.trim());
            } catch (NumberFormatException e) {
                return "§cUsage: grant <amount> [reason].";
            }
            String reason = page == null || page.isBlank() || "main".equalsIgnoreCase(page) ? "admin" : page;
            return PrestigePointsSystem.grantForNpc(player, amount, reason);
        }
        if ("admin".equals(act) || "admin_help".equals(act)) {
            if (!StaffAccess.isStaff(player)) {
                return "§cStaff only.";
            }
            return PrestigeAdmin.help();
        }
        if ("admin_info".equals(act) || "info".equals(act)) {
            if (!StaffAccess.isStaff(player)) {
                return "§cStaff only.";
            }
            return PrestigeAdmin.info(player);
        }
        if ("admin_held".equals(act) || "admin_completed".equals(act)
                || "admin_points".equals(act) || "admin_godki".equals(act)) {
            if (!StaffAccess.isStaff(player)) {
                return "§cStaff only.";
            }
            // arg: mode:amount  (e.g. set:3) — page unused
            String raw = arg == null ? "" : arg.trim();
            String mode;
            int amount;
            int colon = raw.indexOf(':');
            if (colon > 0) {
                mode = raw.substring(0, colon);
                try {
                    amount = Integer.parseInt(raw.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    return "§cUsage: " + act + " <set|add|remove>:<n>";
                }
            } else {
                String[] parts = raw.split("\\s+");
                if (parts.length < 2) {
                    return "§cUsage: " + act + " <set|add|remove> <n>";
                }
                mode = parts[0];
                try {
                    amount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    return "§cUsage: " + act + " <set|add|remove> <n>";
                }
            }
            return switch (act) {
                case "admin_held" -> PrestigeAdmin.adjustHeld(player, mode, amount);
                case "admin_completed" -> PrestigeAdmin.adjustCompleted(player, mode, amount);
                case "admin_points" -> PrestigeAdmin.adjustPoints(player, mode, amount);
                case "admin_godki" -> PrestigeAdmin.adjustGodKi(player, mode, amount);
                default -> "§cSomething went wrong. Try again, or ask staff if it keeps happening.";
            };
        }
        if ("balance".equals(act) || "points".equals(act)) {
            return "§7Prestige favor: §e" + PrestigePointsSystem.getPoints(player)
                    + " §8| §7Current prestige amount: §6" + PrestigeSystem.getHeld(player)
                    + " §8| §7Need (your level cap): §e" + PrestigeSystem.requiredLevel(player);
        }
        return "§cSomething went wrong. Try again, or ask staff if it keeps happening.";
    }

    /**
     * Staff prestige admin — used by Bukkit {@code /prestige admin …} / {@code /padmin}
     * via ForgeBridge (avoids Mohist brigadier forward failures).
     * {@code actor} may be {@code null} when invoked from console (already privileged).
     *
     * @param rawArgs text after {@code admin} (may be blank for help)
     */
    public static String handlePrestigeAdmin(ServerPlayer actor, String rawArgs) {
        if (actor != null && !StaffAccess.isStaff(actor)) {
            return "§cStaff only.";
        }
        if (!DifficultyConfig.get().enablePrestigeSystem) {
            return "§cPrestige system is disabled.";
        }
        String trimmed = rawArgs == null ? "" : rawArgs.trim();
        if (trimmed.isBlank() || "help".equalsIgnoreCase(trimmed)) {
            return PrestigeAdmin.help();
        }
        String[] parts = trimmed.split("\\s+");
        // /padmin admin points … (staff following "/prestige admin" help on the dedicated command)
        if (parts.length > 1 && "admin".equalsIgnoreCase(parts[0])) {
            String[] shifted = new String[parts.length - 1];
            System.arraycopy(parts, 1, shifted, 0, shifted.length);
            parts = shifted;
        }
        String sub = parts[0].toLowerCase(Locale.ROOT);

        // Shorthand: /padmin addpoints|setpoints|removepoints <player> <n>
        if ("addpoints".equals(sub) || "givepoints".equals(sub) || "grantpoints".equals(sub)
                || "setpoints".equals(sub)
                || "removepoints".equals(sub) || "takepoints".equals(sub)) {
            String mode = ("removepoints".equals(sub) || "takepoints".equals(sub))
                    ? "remove"
                    : ("setpoints".equals(sub) ? "set" : "add");
            String[] synth = new String[parts.length];
            synth[0] = "points";
            System.arraycopy(parts, 1, synth, 1, parts.length - 1);
            FieldAdjust adj = parseFieldAdjust(actor, "points", synth);
            if (adj.error != null) {
                return adj.error.contains("Usage")
                        ? "§cUsage: /padmin " + sub + " <player> <n>"
                        : adj.error;
            }
            return PrestigeAdmin.adjustPoints(adj.target, mode, adj.amount);
        }

        if ("info".equals(sub)) {
            ServerPlayer target = parts.length > 1 ? resolveOnline(actor, parts[1]) : actor;
            if (target == null) {
                return parts.length > 1
                        ? "§cPlayer not online: §f" + parts[1]
                        : "§cUsage: /padmin info <player>";
            }
            return PrestigeAdmin.info(target);
        }
        if ("tier".equals(sub) || "tiers".equals(sub) || "difficulty".equals(sub)) {
            if (parts.length < 4) {
                return "§cUsage: /padmin tier <player> <set|add|remove> <0-7>"
                        + "\n§c       /padmin tier <player> give <1-7>"
                        + "\n§c       /padmin tier <player> clear <1-7|all>";
            }
            ServerPlayer target = resolveOnline(actor, parts[1]);
            if (target == null) {
                return "§cPlayer not online: §f" + parts[1];
            }
            String mode = parts[2];
            int tierId;
            if ("all".equalsIgnoreCase(parts[3])) {
                tierId = 0;
            } else {
                try {
                    tierId = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    return "§cTier must be 0-7 or all.";
                }
            }
            return PrestigeAdmin.adjustTier(target, mode, tierId);
        }
        if ("skills".equals(sub) || "invested".equals(sub)) {
            if (parts.length < 2) {
                return "§cUsage: /padmin skills <player>";
            }
            ServerPlayer target = resolveOnline(actor, parts[1]);
            if (target == null) {
                return "§cPlayer not online: §f" + parts[1];
            }
            return PrestigeAdmin.listSkills(target);
        }
        if ("skill".equals(sub) || "invest".equals(sub)) {
            if (parts.length == 2) {
                ServerPlayer target = resolveOnline(actor, parts[1]);
                if (target == null) {
                    return "§cPlayer not online: §f" + parts[1];
                }
                return PrestigeAdmin.listSkills(target);
            }
            if (parts.length < 5) {
                return "§cUsage: /padmin skill <player> <skillId> <set|add|remove> <levels>"
                        + "\n§8Example: /padmin skill Steve potentialunlock set 10"
                        + "\n§8List: /padmin skills <player>";
            }
            ServerPlayer target = resolveOnline(actor, parts[1]);
            if (target == null) {
                return "§cPlayer not online: §f" + parts[1];
            }
            String skillId = parts[2];
            String mode = parts[3];
            int amount;
            try {
                amount = Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                return "§cLevels must be a number.";
            }
            return PrestigeAdmin.adjustSkill(target, skillId, mode, amount);
        }
        if ("held".equals(sub) || "completed".equals(sub) || "points".equals(sub)
                || "godki".equals(sub)) {
            String field = sub;
            FieldAdjust adj = parseFieldAdjust(actor, field, parts);
            if (adj.error != null) {
                return adj.error;
            }
            return switch (field) {
                case "held" -> PrestigeAdmin.adjustHeld(adj.target, adj.mode, adj.amount);
                case "completed" -> PrestigeAdmin.adjustCompleted(adj.target, adj.mode, adj.amount);
                case "points" -> PrestigeAdmin.adjustPoints(adj.target, adj.mode, adj.amount);
                case "godki" -> PrestigeAdmin.adjustGodKi(adj.target, adj.mode, adj.amount);
                default -> "§cSomething went wrong. Try again, or ask staff if it keeps happening.";
            };
        }
        return "§cUnknown: /padmin " + sub + "\n" + PrestigeAdmin.help();
    }

    private static final class FieldAdjust {
        final ServerPlayer target;
        final String mode;
        final int amount;
        final String error;

        private FieldAdjust(ServerPlayer target, String mode, int amount, String error) {
            this.target = target;
            this.mode = mode;
            this.amount = amount;
            this.error = error;
        }

        static FieldAdjust ok(ServerPlayer target, String mode, int amount) {
            return new FieldAdjust(target, mode, amount, null);
        }

        static FieldAdjust err(String error) {
            return new FieldAdjust(null, null, 0, error);
        }
    }

    private static boolean isAdjustMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "set", "add", "remove", "take", "sub" -> true;
            default -> false;
        };
    }

    private static Integer tryParseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Accepts {@code <player> set 10}, {@code set 10} (self), {@code set <player> 10},
     * and {@code <player> 10} (implied set).
     */
    private static FieldAdjust parseFieldAdjust(ServerPlayer actor, String field, String[] parts) {
        String usage = "§cUsage: /padmin " + field + " <player> <set|add|remove> <n>"
                + "\n§8Also: /padmin " + field + " set <n>  -  /padmin " + field + " <player> <n>";
        if (parts == null || parts.length < 2) {
            return FieldAdjust.err(usage);
        }
        String mode;
        String playerTok;
        String amountTok;
        if (parts.length == 2) {
            Integer n = tryParseInt(parts[1]);
            if (n == null) {
                return FieldAdjust.err(usage);
            }
            if (actor == null) {
                return FieldAdjust.err("§cUsage: /padmin " + field + " <player> set <n>");
            }
            return FieldAdjust.ok(actor, "set", n);
        }
        if (isAdjustMode(parts[1])) {
            mode = parts[1];
            if (parts.length == 3) {
                Integer n = tryParseInt(parts[2]);
                if (n == null) {
                    return FieldAdjust.err(usage);
                }
                if (actor == null) {
                    return FieldAdjust.err("§cUsage: /padmin " + field + " <player> " + mode + " <n>");
                }
                return FieldAdjust.ok(actor, mode, n);
            }
            if (parts.length < 4) {
                return FieldAdjust.err(usage);
            }
            playerTok = parts[2];
            amountTok = parts[3];
        } else if (parts.length >= 4 && isAdjustMode(parts[2])) {
            playerTok = parts[1];
            mode = parts[2];
            amountTok = parts[3];
        } else if (tryParseInt(parts[2]) != null) {
            playerTok = parts[1];
            mode = "set";
            amountTok = parts[2];
        } else {
            return FieldAdjust.err(usage);
        }
        Integer amount = tryParseInt(amountTok);
        if (amount == null) {
            return FieldAdjust.err("§cAmount must be a number.");
        }
        ServerPlayer target = resolveOnline(actor, playerTok);
        if (target == null) {
            return FieldAdjust.err("§cPlayer not online: §f" + playerTok);
        }
        return FieldAdjust.ok(target, mode, amount);
    }

    // ── Skills ─────────────────────────────────────────────────────────

    public static Map<String, String> skillsPlaceholders(ServerPlayer player) {
        Map<String, String> out = new HashMap<>();
        out.put("bridge_ok", "false");
        out.put("system_enabled", "false");
        if (player == null) {
            return out;
        }
        boolean enabled = DifficultyConfig.get().enableSkillUnlockService;
        out.put("bridge_ok", "true");
        out.put("system_enabled", enabled ? "true" : "false");
        if (!enabled) {
            return out;
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            out.put("has_data", "false");
            return out;
        }
        out.put("has_data", "true");
        try {
            out.put("level", String.valueOf(Math.max(1, data.getLevel())));
        } catch (Throwable t) {
            out.put("level", "1");
        }
        try {
            out.put("ki_damage", String.format(Locale.ROOT, "%.1f", Math.max(0.0, data.getKiDamage())));
        } catch (Throwable t) {
            out.put("ki_damage", "0");
        }
        try {
            out.put("max_energy", String.format(Locale.ROOT, "%.1f", Math.max(0.0, data.getMaxEnergy())));
        } catch (Throwable t) {
            out.put("max_energy", "0");
        }
        try {
            out.put("strength", String.valueOf(Math.max(0, data.getStats().getStrength())));
        } catch (Throwable t) {
            out.put("strength", "0");
        }
        LmOverhaulScaledCombat.putPlaceholders(out, data);
        return out;
    }

    public static List<String> skillsLines(ServerPlayer player, String page) {
        if (player == null || !DifficultyConfig.get().enableSkillUnlockService) {
            return List.of("§cSkill unlock service is disabled.");
        }
        String p = page == null || page.isBlank() ? "core" : page.toLowerCase(Locale.ROOT);
        if ("help".equals(p)) {
            return List.of(
                    "§6§l/skills §8- Skill Progress (staff)",
                    "§e/skills §7- every tracked skill on one page",
                    "§e/skillcheck §7- Donator Skill Check"
            );
        }
        if ("skillcheck".equals(p) || "check".equals(p) || "compact".equals(p)) {
            return SkillUnlockService.skillCheckGuiLines(player);
        }
        return SkillUnlockService.coreLines(player);
    }

    /**
     * Dispatch {@code /skills do} — {@code page} with core/saga (reopen only).
     */
    public static String handleSkillsDo(ServerPlayer player, String action, String arg, String page) {
        if (player == null) {
            return "§cThis only works in-game - console can't open this menu.";
        }
        if (!DifficultyConfig.get().enableSkillUnlockService) {
            return "§cSkill unlock service is disabled.";
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        if ("page".equals(act) || "refresh".equals(act)) {
            return "";
        }
        return "§cSomething went wrong. Try again, or ask staff if it keeps happening.";
    }
}
