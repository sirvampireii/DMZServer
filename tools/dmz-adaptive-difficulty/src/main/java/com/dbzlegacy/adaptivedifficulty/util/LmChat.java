package com.dbzlegacy.adaptivedifficulty.util;

import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Legacy Mechanics chat look — one style for every system:
 * {@code §5§l[System] §r…} tags, card titles, and {@code §8────────────} dividers.
 *
 * <p>Body colors: {@code §a} success · {@code §c} fail · {@code §e} highlight ·
 * {@code §7} prose · {@code §8} meta · {@code §f} emphasis.
 */
public final class LmChat {
    /** Horizontal rule used under card titles (Meditation Trial style). */
    public static final String DIVIDER = "§8────────────";

    /**
     * Long / legacy tag names → short stable labels shown in chat.
     * Applied by {@link #normalize} and {@link #tagged}.
     */
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("sparring", "Spar"),
            Map.entry("mentor bond", "Mentor"),
            Map.entry("mentor", "Mentor"),
            Map.entry("rival challenge", "Challenge"),
            Map.entry("rival instinct", "Instinct"),
            Map.entry("rival quest", "Rival"),
            Map.entry("proving grounds", "Proving"),
            Map.entry("potential unlock", "Potential"),
            Map.entry("potential", "Potential"),
            Map.entry("race lock", "Race"),
            Map.entry("shadow dummy", "Dummy"),
            Map.entry("global tp", "TP"),
            Map.entry("tp", "TP"),
            Map.entry("legacy mechanics", "LM"),
            Map.entry("stats", "Stats"),
            Map.entry("spiritualist", "Spirit"),
            Map.entry("android", "Android"),
            Map.entry("absorb", "Absorb"),
            Map.entry("flight", "Flight"),
            Map.entry("meditation", "Meditation"),
            Map.entry("prestige", "Prestige"),
            Map.entry("difficulty", "Difficulty"),
            Map.entry("end", "End"),
            Map.entry("spec", "Spec")
    );

    private LmChat() {}

    /** Canonical system label (aliases applied). */
    public static String systemName(String system) {
        if (system == null || system.isBlank()) {
            return "LM";
        }
        String raw = system.trim();
        String alias = TAG_ALIASES.get(raw.toLowerCase(Locale.ROOT));
        return alias != null ? alias : raw;
    }

    /** {@code §5§l[system] §r} + body (Meditation tag style). */
    public static String tagged(String system, String body) {
        String text = body == null ? "" : body;
        return "§5§l[" + systemName(system) + "] §r" + text;
    }

    /** Success line. */
    public static String ok(String system, String body) {
        return tagged(system, "§a" + stripLeadingColor(body));
    }

    /** Failure / deny line. */
    public static String fail(String system, String body) {
        return tagged(system, "§c" + stripLeadingColor(body));
    }

    /** Neutral info line. */
    public static String info(String system, String body) {
        return tagged(system, "§7" + stripLeadingColor(body));
    }

    /** Highlight / invite line (names stay §f / §e in body). */
    public static String note(String system, String body) {
        return tagged(system, body == null ? "" : body);
    }

    /** TP award: {@code [System] §a+N TP §8(reason)}. */
    public static String tp(String system, String amountFmt, String reason) {
        String why = reason == null || reason.isBlank() ? "" : " §8(" + reason + ")";
        return tagged(system, "§a+" + amountFmt + " TP" + why);
    }

    /** Bold purple card title (like {@code §d§lMeditation Trial}). */
    public static String title(String name) {
        String n = name == null || name.isBlank() ? "Legacy Mechanics" : name.trim();
        return "§d§l" + n;
    }

    /** Tip footer line: {@code §8Tip · §e/cmd §7rest}. */
    public static String tip(String command, String rest) {
        String cmd = command == null || command.isBlank() ? "/lm" : command.trim();
        String r = rest == null ? "" : rest;
        if (r.isBlank()) {
            return "§8Tip · §e" + cmd;
        }
        return "§8Tip · §e" + cmd + " §7" + r;
    }

    /**
     * Multi-line card: title, divider, body lines, optional tip.
     * Blank body lines are kept; nulls skipped.
     */
    public static String card(String titleName, String tipCommand, String tipRest, String... bodyLines) {
        StringBuilder sb = new StringBuilder();
        sb.append(title(titleName)).append('\n');
        sb.append(DIVIDER);
        if (bodyLines != null) {
            for (String line : bodyLines) {
                if (line == null) {
                    continue;
                }
                sb.append('\n').append(line);
            }
        }
        if (tipCommand != null && !tipCommand.isBlank()) {
            sb.append('\n').append(tip(tipCommand, tipRest));
        }
        return sb.toString();
    }

    /**
     * Rewrite leading {@code [Tag]} prefixes to Meditation style.
     * Leaves card titles, dividers, labeled rows, and help walls alone.
     * Also collapses {@code §8Open §e/cmd…} tips into {@link #tip}.
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.indexOf('\n') < 0) {
            return normalizeLine(text);
        }
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(normalizeLine(lines[i]));
        }
        return sb.toString();
    }

    /** Send one chat line (or multi-line) with Meditation-style tags applied. */
    public static void send(ServerPlayer player, String text) {
        if (player == null || text == null) {
            return;
        }
        String normalized = normalize(text);
        if (normalized.indexOf('\n') < 0) {
            sendRaw(player, normalized);
            return;
        }
        for (String line : normalized.split("\n", -1)) {
            sendRaw(player, line);
        }
    }

    private static void sendRaw(ServerPlayer player, String line) {
        com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.vanilla(player, "chat", line);
        try {
            player.m_213846_(Component.m_237113_(line == null ? "" : line));
        } catch (Throwable ignored) {
        }
    }

    private static String normalizeLine(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        // Already Meditation-style tag — still alias the label if needed.
        if (line.startsWith("§5§l[") && line.contains("] §r")) {
            return realiasCanonical(line);
        }
        // Card chrome / help — do not wrap.
        if (line.startsWith("§d§l")
                || line.startsWith(DIVIDER)
                || line.equals("§8────────────")
                || line.startsWith("§8Tip ·")
                || line.startsWith("§8Staff ·")
                || looksLikeHelpHeader(line)) {
            return line;
        }
        // Legacy "Open /cmd → …" tips → Tip · form.
        String openTip = normalizeOpenTip(line);
        if (openTip != null) {
            return openTip;
        }
        // Prestige-style dashed rules → divider.
        if (line.matches("§8-{6,}")) {
            return DIVIDER;
        }

        int i = 0;
        // Skip leading formatting codes (§ + char).
        while (i + 1 < line.length() && line.charAt(i) == '§') {
            i += 2;
        }
        if (i >= line.length() || line.charAt(i) != '[') {
            return line;
        }
        int close = line.indexOf(']', i + 1);
        if (close < 0) {
            return line;
        }
        String tag = line.substring(i + 1, close).trim();
        if (tag.isEmpty() || tag.length() > 40) {
            return line;
        }
        // Avoid rewriting inspect prefixes like [PlayerName] that are not systems —
        // system tags are short words / phrases. Allow letters/digits/spaces/-.
        if (!tag.matches("[A-Za-z0-9][A-Za-z0-9 \\-]{0,38}")) {
            return line;
        }
        int bodyStart = close + 1;
        while (bodyStart < line.length() && line.charAt(bodyStart) == ' ') {
            bodyStart++;
        }
        // Drop a leading §r after the tag if present.
        if (bodyStart + 1 < line.length()
                && line.charAt(bodyStart) == '§'
                && line.charAt(bodyStart + 1) == 'r') {
            bodyStart += 2;
            while (bodyStart < line.length() && line.charAt(bodyStart) == ' ') {
                bodyStart++;
            }
        }
        String body = bodyStart < line.length() ? line.substring(bodyStart) : "";
        return tagged(tag, body);
    }

    /** §5§l[Long Name] §r… → short alias when mapped. */
    private static String realiasCanonical(String line) {
        // Expected: §5§l[Tag] §rbody
        if (!line.startsWith("§5§l[")) {
            return line;
        }
        int close = line.indexOf(']', 5);
        if (close < 0) {
            return line;
        }
        String tag = line.substring(5, close).trim();
        String canonical = systemName(tag);
        if (canonical.equals(tag)) {
            return line;
        }
        String body = "";
        if (close + 4 <= line.length() && line.startsWith("] §r", close)) {
            body = line.substring(close + 4);
        } else if (close + 1 < line.length()) {
            body = line.substring(close + 1).trim();
        }
        return tagged(canonical, body);
    }

    /**
     * {@code §8Open §e/cmd §8→ rest} → Tip line. Returns null when not an Open tip.
     */
    private static String normalizeOpenTip(String line) {
        if (line == null || !line.contains("Open") || !line.contains("/")) {
            return null;
        }
        // Strip leading § codes.
        int i = 0;
        while (i + 1 < line.length() && line.charAt(i) == '§') {
            i += 2;
        }
        if (i >= line.length() || !line.regionMatches(i, "Open", 0, 4)) {
            return null;
        }
        int slash = line.indexOf('/', i);
        if (slash < 0) {
            return null;
        }
        int endCmd = slash + 1;
        while (endCmd < line.length()) {
            char c = line.charAt(endCmd);
            if (c == ' ' || c == '§' || c == '→') {
                break;
            }
            endCmd++;
        }
        String cmd = line.substring(slash, endCmd).trim();
        if (cmd.length() < 2) {
            return null;
        }
        String rest = line.substring(endCmd).trim();
        while (!rest.isEmpty()) {
            if (rest.startsWith("§") && rest.length() >= 2) {
                rest = rest.substring(2).trim();
                continue;
            }
            if (rest.startsWith("→") || rest.startsWith("->")) {
                rest = rest.replaceFirst("^(→|->)\\s*", "").trim();
                continue;
            }
            if (rest.regionMatches(true, 0, "to ", 0, 3)) {
                rest = rest.substring(3).trim();
                continue;
            }
            break;
        }
        return tip(cmd, rest.isBlank() ? null : rest);
    }

    private static boolean looksLikeHelpHeader(String line) {
        // §6§l/lm admin  or  §e/rival …
        int i = 0;
        while (i + 1 < line.length() && line.charAt(i) == '§') {
            i += 2;
        }
        return i < line.length() && line.charAt(i) == '/';
    }

    private static String stripLeadingColor(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String out = body;
        while (out.length() >= 2 && out.charAt(0) == '§') {
            out = out.substring(2);
        }
        return out;
    }
}
