package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager;
import com.dbzlegacy.adaptivedifficulty.rival.RivalConstants;
import com.dbzlegacy.adaptivedifficulty.rival.RivalInstinct;
import com.dbzlegacy.adaptivedifficulty.rival.RivalLink;
import com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord;
import com.dbzlegacy.adaptivedifficulty.rival.RivalProgression;
import com.dbzlegacy.adaptivedifficulty.rival.RivalSpectator;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStore;
import com.dbzlegacy.adaptivedifficulty.rival.RivalSystem;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public static API for Bukkit companion reflection ({@code LegacyMechanicsGUI}).
 * Status maps, lore lines, and {@code /rival do} action dispatch without reopening menus —
 * the companion plugin owns inventory reopen.
 */
public final class RivalGuiApi {
    private RivalGuiApi() {}

    public static Map<String, String> placeholders(ServerPlayer player) {
        Map<String, String> out = new HashMap<>();
        out.put("bridge_ok", "false");
        out.put("system_enabled", "false");
        if (player == null) {
            return out;
        }
        boolean enabled = DifficultyConfig.get().enableRivalSystem;
        out.put("bridge_ok", "true");
        out.put("system_enabled", enabled ? "true" : "false");
        if (!enabled) {
            return out;
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        RivalConstants.RpTier tier = RivalConstants.tierFor(me.totalRp);
        out.put("rp", String.valueOf((int) me.totalRp));
        out.put("tier", tier.name());
        out.put("tier_color", String.valueOf(tier.color()));
        out.put("mutual", String.valueOf(me.countMutual()));
        out.put("mutual_max", String.valueOf(RivalConstants.MAX_MUTUAL_RIVALS));
        out.put("wins", String.valueOf(me.officialWins));
        out.put("losses", String.valueOf(me.officialLosses));
        out.put("draws", String.valueOf(me.officialDraws));
        out.put("tpMsg", me.tpMessages ? "true" : "false");
        out.put("tp_msg", me.tpMessages ? "true" : "false");
        out.put("instinct", RivalInstinct.isEnabled(player) ? "true" : "false");
        out.put("instinct_feature", DifficultyConfig.get().rivalInstinct ? "true" : "false");
        out.put("challengeActive",
                RivalChallengeManager.get().isInChallenge(player.m_20148_()) ? "true" : "false");
        out.put("challenge_active", out.get("challengeActive"));
        out.put("name", me.name == null ? "" : me.name);
        int pendingCount = 0;
        long now = System.currentTimeMillis();
        if (me.rivals != null) {
            for (RivalLink link : me.rivals.values()) {
                if (link == null || link.mutual) {
                    continue;
                }
                boolean mutualConfirm = link.needsMutualConfirm();
                if (!link.inviteSent && !link.inviteReceived && !mutualConfirm) {
                    continue;
                }
                if (link.pendingExpireAt > 0L && now > link.pendingExpireAt) {
                    continue;
                }
                pendingCount++;
            }
        }
        out.put("pending_invites", String.valueOf(pendingCount));
        out.put("pendingInvites", String.valueOf(pendingCount));
        out.put("pending_mutual_accept",
                me.pendingMutualAcceptUuid == null ? "" : me.pendingMutualAcceptUuid.trim());
        out.put("pending_mutual_accept_name", RivalSystem.pendingMutualAcceptName(player));
        out.put("needs_mutual_replace", RivalSystem.needsMutualReplacePick(player) ? "true" : "false");
        LmOverhaulScaledCombat.putPlaceholders(out, player);
        return out;
    }

    public static List<String> listLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalSystem.listLines(player);
    }

    /** Encoded current-rival cards for inventory GUIs (see RivalSystem.currentRivalCards). */
    public static List<String> currentRivalCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of();
        }
        return RivalSystem.currentRivalCards(player);
    }

    /** Encoded past-rival cards for history board. */
    public static List<String> pastRivalCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of();
        }
        return RivalSystem.pastRivalCards(player);
    }

    /** True when Accept is waiting for the player to pick which Mutual to replace. */
    public static boolean needsMutualReplacePick(ServerPlayer player) {
        return RivalSystem.needsMutualReplacePick(player);
    }

    /** Encoded pending declare invites (incoming + outgoing). */
    public static List<String> pendingInviteCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of();
        }
        return RivalSystem.pendingInviteCards(player);
    }

    /**
     * Current-rival picker args ({@code uuid:&lt;uuid&gt;} preferred) for remove —
     * only existing rivals, not all online players.
     */
    public static List<String> currentRivalArgs(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (String card : currentRivalCards(player)) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            if (p.length < 2) {
                continue;
            }
            String uuid = p[0] == null ? "" : p[0].trim();
            String name = p[1] == null ? "" : p[1].trim();
            if (!uuid.isBlank()) {
                out.add("uuid:" + uuid);
            } else if (!name.isBlank()) {
                out.add(name);
            }
        }
        return out;
    }

    public static List<String> statsLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalSystem.statsLines(player);
    }

    public static List<String> topLines(ServerPlayer player) {
        if (!DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalSystem.topLines(10);
    }

    public static List<String> challengeLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.LABEL
                + "Timed fights against a rival. Challenge deaths can raise Nemesis.");
        if (player != null && RivalChallengeManager.get().isInChallenge(player.m_20148_())) {
            lines.add(UltraStyle.NOTICE_BODY + "You are in an active challenge.");
        } else {
            lines.add(UltraStyle.LABEL + "No active fight right now.");
        }
        int pending = pendingChallengeCards(player).size();
        if (pending > 0) {
            lines.add(UltraStyle.YELLOW + pending + " pending challenge request"
                    + (pending == 1 ? "" : "s") + " below.");
        } else {
            lines.add(UltraStyle.LABEL + "No pending challenge requests.");
        }
        return lines;
    }

    /** Comma-separated Nemesis display names (or {@code -} when none). */
    public static String nemesisNames(ServerPlayer player) {
        return joinRivalNamesByStatus(player, "Nemesis");
    }

    /** Comma-separated Mutual display names (or {@code -} when none). */
    public static String mutualNames(ServerPlayer player) {
        return joinRivalNamesByStatus(player, "Mutual");
    }

    private static String joinRivalNamesByStatus(ServerPlayer player, String statusLabel) {
        if (player == null || statusLabel == null || statusLabel.isBlank()) {
            return "-";
        }
        List<String> names = new ArrayList<>();
        String want = statusLabel.trim();
        for (String card : currentRivalCards(player)) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            if (p.length < 3) {
                continue;
            }
            String status = p[2] == null ? "" : p[2].trim();
            if (!want.equalsIgnoreCase(status)) {
                continue;
            }
            String uuid = p[0] == null ? "" : p[0].trim();
            String name = p[1] == null ? "" : p[1].trim();
            String shown = displayPickerArg(player, uuid.isBlank() ? name : "uuid:" + uuid);
            if (shown == null || shown.isBlank()) {
                shown = name;
            }
            // Strip section-sign color codes from picker display names.
            shown = UltraStyle.plain(shown);
            if (!shown.isBlank() && !names.contains(shown)) {
                names.add(shown);
            }
        }
        // Fallback: persistent nemesis uuid when no Nemesis-status card yet.
        if (names.isEmpty() && "Nemesis".equalsIgnoreCase(want)) {
            RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
            if (me != null && me.nemesisUuid != null && !me.nemesisUuid.isBlank()) {
                String shown = displayPickerArg(player, "uuid:" + me.nemesisUuid.trim());
                if (shown != null && !shown.isBlank()) {
                    names.add(UltraStyle.plain(shown));
                }
            }
        }
        if (names.isEmpty()) {
            return "-";
        }
        return String.join(", ", names);
    }

    /** Encoded pending duel requests (incoming + outgoing). */
    public static List<String> pendingChallengeCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().rivalChallenges) {
            return List.of();
        }
        return RivalChallengeManager.get().pendingRequestCards(player);
    }

    public static List<String> pendingChallengeLines(ServerPlayer player) {
        List<String> cards = pendingChallengeCards(player);
        List<String> lines = new ArrayList<>();
        if (cards.isEmpty()) {
            return lines;
        }
        lines.add(UltraStyle.YELLOW + "Pending challenges");
        for (String card : cards) {
            String[] p = card.split("\t", -1);
            if (p.length < 3) {
                continue;
            }
            String name = p[1] == null || p[1].isBlank() ? "-" : p[1].trim();
            String dir = p[2];
            boolean online = p.length > 4 && "1".equals(p[4]);
            String mins = p.length > 5 && p[5] != null && !p[5].isBlank() ? p[5] : "-";
            String onlineWord = online ? "online" : "offline";
            if ("IN".equalsIgnoreCase(dir)) {
                lines.add(UltraStyle.GREEN + "Incoming " + UltraStyle.WHITE + name
                        + UltraStyle.DIM + " - " + UltraStyle.WHITE + mins + " min "
                        + UltraStyle.DIM + "(" + onlineWord + ")");
            } else {
                lines.add(UltraStyle.GOLD + "Outgoing " + UltraStyle.WHITE + name
                        + UltraStyle.DIM + " - " + UltraStyle.WHITE + mins + " min "
                        + UltraStyle.DIM + "(" + onlineWord + ")");
            }
        }
        return lines;
    }

    /** Online player names for GUI head pickers (excludes {@code exclude} when non-null). */
    public static List<String> onlinePlayerNames(ServerPlayer exclude) {
        List<String> names = new ArrayList<>();
        MinecraftServer server = exclude == null ? null : exclude.m_20194_();
        if (server == null) {
            return names;
        }
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            if (p == null) {
                continue;
            }
            if (exclude != null && p.m_20148_().equals(exclude.m_20148_())) {
                continue;
            }
            String name = p.m_7755_().getString();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * Online player picker args as {@code uuid:&lt;uuid&gt;}, sorted by display name.
     * Prefer these over bare names so renames cannot break declare/challenge/spectate.
     */
    public static List<String> onlinePlayerArgs(ServerPlayer exclude) {
        List<String[]> rows = new ArrayList<>();
        MinecraftServer server = exclude == null ? null : exclude.m_20194_();
        if (server == null) {
            return List.of();
        }
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            if (p == null) {
                continue;
            }
            if (exclude != null && p.m_20148_().equals(exclude.m_20148_())) {
                continue;
            }
            String name = p.m_7755_().getString();
            if (name == null || name.isBlank()) {
                name = p.m_20148_().toString();
            }
            rows.add(new String[] {"uuid:" + p.m_20148_(), name});
        }
        rows.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a[1], b[1]));
        List<String> out = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            out.add(row[0]);
        }
        return out;
    }

    /**
     * Online players who are not already on {@code viewer}'s rival list.
     * The add-rival picker uses this so Silent / Declare never offers a current rival.
     */
    public static List<String> excludingCurrentRivals(ServerPlayer viewer, List<String> onlineArgs) {
        List<String> out = new ArrayList<>();
        if (onlineArgs == null || onlineArgs.isEmpty()) {
            return out;
        }
        java.util.Set<String> current = currentRivalKeys(viewer);
        for (String arg : onlineArgs) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String trimmed = arg.trim();
            if (current.contains(trimmed.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (trimmed.regionMatches(true, 0, "uuid:", 0, 5)
                    && current.contains(trimmed.substring(5).trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            String shown = UltraStyle.plain(displayPickerArg(viewer, trimmed));
            if (!shown.isBlank() && current.contains(shown.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(arg);
        }
        return out;
    }

    /**
     * Players in a live or countdown challenge, as {@code uuid:…} args.
     * The viewer is left out. Pending requests are not a fight yet.
     */
    public static List<String> activeChallengeArgs(ServerPlayer viewer) {
        List<String> out = new ArrayList<>();
        String self = viewer == null ? "" : "uuid:" + viewer.m_20148_();
        for (String arg : RivalChallengeManager.get().activeFighterArgs()) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (!self.isBlank() && self.equalsIgnoreCase(arg.trim())) {
                continue;
            }
            out.add(arg);
        }
        return out;
    }

    /**
     * Status label on the viewer's current rival list ({@code Silent}, {@code Declared}, …).
     * Blank when {@code pickerArg} is not on that list.
     */
    public static String rivalStatusLabel(ServerPlayer viewer, String pickerArg) {
        if (viewer == null || pickerArg == null || pickerArg.isBlank()) {
            return "";
        }
        String want = pickerArg.trim();
        String wantUuid = want.regionMatches(true, 0, "uuid:", 0, 5)
                ? want.substring(5).trim()
                : "";
        for (String card : currentRivalCards(viewer)) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            if (p.length < 3) {
                continue;
            }
            String uuid = p[0] == null ? "" : p[0].trim();
            String name = p[1] == null ? "" : p[1].trim();
            boolean match = (!wantUuid.isBlank() && wantUuid.equalsIgnoreCase(uuid))
                    || want.equalsIgnoreCase(name)
                    || want.equalsIgnoreCase("uuid:" + uuid);
            if (match) {
                return p[2] == null ? "" : p[2].trim();
            }
        }
        return "";
    }

    private static java.util.Set<String> currentRivalKeys(ServerPlayer viewer) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String card : currentRivalCards(viewer)) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            if (p.length < 2) {
                continue;
            }
            String uuid = p[0] == null ? "" : p[0].trim();
            String name = p[1] == null ? "" : p[1].trim();
            if (!uuid.isBlank()) {
                keys.add(uuid.toLowerCase(Locale.ROOT));
                keys.add(("uuid:" + uuid).toLowerCase(Locale.ROOT));
            }
            if (!name.isBlank()) {
                keys.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    /** Mutual / Nemesis rivals only — for Mutual slot replace pickers. */
    public static List<String> mutualRivalArgs(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (String card : currentRivalCards(player)) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            if (p.length < 2) {
                continue;
            }
            String status = p.length > 2 && p[2] != null ? p[2].trim() : "";
            if (!"Mutual".equalsIgnoreCase(status) && !"Nemesis".equalsIgnoreCase(status)) {
                continue;
            }
            String uuid = p[0] == null ? "" : p[0].trim();
            String name = p[1] == null ? "" : p[1].trim();
            if (!uuid.isBlank()) {
                out.add("uuid:" + uuid);
            } else if (!name.isBlank()) {
                out.add(name);
            }
        }
        return out;
    }

    /** True when {@code pickerArg} is a pending incoming declare (or Mutual-confirm) on this player. */
    public static boolean hasIncomingDeclareFrom(ServerPlayer player, String pickerArg) {
        if (player == null || pickerArg == null || pickerArg.isBlank()) {
            return false;
        }
        String want = pickerArg.trim();
        for (String arg : acceptCandidateArgs(player)) {
            if (arg != null && want.equalsIgnoreCase(arg.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String pendingMutualAcceptName(ServerPlayer player) {
        return RivalSystem.pendingMutualAcceptName(player);
    }

    public static String cancelMutualReplace(ServerPlayer player) {
        return RivalSystem.cancelMutualReplace(player);
    }

    /**
     * Names of players with a pending incoming declare ({@code inviteReceived}) on this player.
     * Online declarers are listed first; offline still included (accept/decline use name lookup).
     */
    public static List<String> pendingIncomingDeclareNames(ServerPlayer player) {
        return collectIncomingNames(player, false);
    }

    /**
     * Accept candidates: pending incoming declare or Declared (both Silent).
     * Online first; offline still included.
     */
    public static List<String> acceptCandidateNames(ServerPlayer player) {
        return collectIncomingNames(player, true);
    }

    private static List<String> collectIncomingNames(ServerPlayer player, boolean includeDeclared) {
        List<String> online = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return online;
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (me == null || me.rivals.isEmpty()) {
            return online;
        }
        MinecraftServer server = player.m_20194_();
        long now = System.currentTimeMillis();
        for (RivalLink link : me.rivals.values()) {
            if (link == null || !matchesIncoming(link, now, includeDeclared)) {
                continue;
            }
            String name = link.name == null || link.name.isBlank() ? link.uuid : link.name;
            if (name == null || name.isBlank()) {
                continue;
            }
            boolean isOnline = false;
            if (server != null && link.uuid != null && !link.uuid.isBlank()) {
                try {
                    isOnline = server.m_6846_().m_11259_(java.util.UUID.fromString(link.uuid)) != null;
                } catch (IllegalArgumentException ignored) {
                    isOnline = false;
                }
            }
            if (isOnline) {
                online.add(name);
            } else {
                offline.add(name);
            }
        }
        online.sort(String.CASE_INSENSITIVE_ORDER);
        offline.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> out = new ArrayList<>(online.size() + offline.size());
        out.addAll(online);
        out.addAll(offline);
        return out;
    }

    /** Invite-only pending, or also Declared Mutual-confirm when {@code includeDeclared}. */
    private static boolean matchesIncoming(RivalLink link, long now, boolean includeDeclared) {
        if (link.mutual) {
            return false;
        }
        if (link.inviteReceived) {
            return link.pendingExpireAt <= 0L || now <= link.pendingExpireAt;
        }
        if (includeDeclared && link.needsMutualConfirm()) {
            return link.pendingExpireAt <= 0L || now <= link.pendingExpireAt;
        }
        if (!includeDeclared) {
            return false;
        }
        return link.declaredByMe && link.declaredByThem
                && !link.inviteSent && !link.inviteReceived;
    }

    /**
     * Declares this player sent that the other player has not answered yet: invite sent,
     * unexpired, not mutual, and not a mutual confirm. A plain Declared status with no pending
     * invite is not counted. Shown on the Rival tab as "Awaiting reply".
     */
    public static int outgoingDeclareCount(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return 0;
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (me == null || me.rivals == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int n = 0;
        for (RivalLink link : me.rivals.values()) {
            if (link == null || link.mutual || !link.inviteSent || link.needsMutualConfirm()) {
                continue;
            }
            if (link.pendingExpireAt > 0L && now > link.pendingExpireAt) {
                continue;
            }
            n++;
        }
        return n;
    }

    /**
     * Pending incoming declare picker args: {@code uuid:&lt;uuid&gt;} when online, else stored name
     * (decline uses this — invite only). Online first.
     */
    public static List<String> pendingIncomingDeclareArgs(ServerPlayer player) {
        return collectIncomingArgs(player, false);
    }

    /**
     * Accept picker args: pending invite or Declared (both Silent).
     * {@code uuid:&lt;uuid&gt;} when online, else stored name. Online first.
     */
    public static List<String> acceptCandidateArgs(ServerPlayer player) {
        return collectIncomingArgs(player, true);
    }

    private static List<String> collectIncomingArgs(ServerPlayer player, boolean includeDeclared) {
        List<String> online = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return online;
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
        if (me == null || me.rivals.isEmpty()) {
            return online;
        }
        MinecraftServer server = player.m_20194_();
        long now = System.currentTimeMillis();
        for (RivalLink link : me.rivals.values()) {
            if (link == null || !matchesIncoming(link, now, includeDeclared)) {
                continue;
            }
            String name = link.name == null || link.name.isBlank() ? "" : link.name;
            String uuid = link.uuid == null ? "" : link.uuid.trim();
            boolean isOnline = false;
            if (server != null && !uuid.isBlank()) {
                try {
                    isOnline = server.m_6846_().m_11259_(java.util.UUID.fromString(uuid)) != null;
                } catch (IllegalArgumentException ignored) {
                    isOnline = false;
                }
            }
            if (isOnline) {
                online.add("uuid:" + uuid);
            } else if (!name.isBlank()) {
                offline.add(name);
            } else if (!uuid.isBlank()) {
                offline.add(uuid);
            }
        }
        online.sort(String.CASE_INSENSITIVE_ORDER);
        offline.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> out = new ArrayList<>(online.size() + offline.size());
        out.addAll(online);
        out.addAll(offline);
        return out;
    }

    /**
     * Resolve an online {@link ServerPlayer} by {@code uuid:&lt;uuid&gt;} or by exact /
     * case-insensitive name.
     */
    public static ServerPlayer resolveOnline(MinecraftServer server, String arg) {
        if (server == null || arg == null || arg.isBlank()) {
            return null;
        }
        String raw = arg.trim();
        if (raw.regionMatches(true, 0, "uuid:", 0, 5)) {
            String id = raw.substring(5).trim();
            if (id.isBlank()) {
                return null;
            }
            try {
                java.util.UUID uuid = java.util.UUID.fromString(id);
                ServerPlayer direct = server.m_6846_().m_11259_(uuid);
                if (direct != null) {
                    return direct;
                }
                for (ServerPlayer p : server.m_6846_().m_11314_()) {
                    if (p != null && uuid.equals(p.m_20148_())) {
                        return p;
                    }
                }
                return null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return RivalSystem.findOnline(server, raw);
    }

    public static ServerPlayer resolveOnline(ServerPlayer from, String name) {
        if (from == null) {
            return null;
        }
        return resolveOnline(from.m_20194_(), name);
    }

    /** Resolve GUI arg to a stored/display name (supports {@code uuid:} for accept/decline/remove). */
    public static String resolveNameArg(ServerPlayer from, String arg) {
        if (arg == null || arg.isBlank()) {
            return "";
        }
        String raw = arg.trim();
        if (raw.regionMatches(true, 0, "uuid:", 0, 5)) {
            ServerPlayer online = resolveOnline(from, raw);
            if (online != null) {
                return online.m_7755_().getString();
            }
            String id = raw.substring(5).trim();
            String fromLink = rivalLinkDisplayName(from, id);
            if (!fromLink.isBlank()) {
                return fromLink;
            }
            RivalPlayerRecord rec = RivalStore.get().get(id);
            if (rec != null && rec.name != null && !rec.name.isBlank()) {
                return rec.name;
            }
            return "";
        }
        return raw;
    }

    /** Player-facing label for CNPC pick lists ({@code uuid:…} → stored rival name). */
    public static String displayPickerArg(ServerPlayer from, String arg) {
        String name = resolveNameArg(from, arg);
        if (!name.isBlank()) {
            return UltraStyle.VALUE + name;
        }
        return com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraText.humanizePickerArg(arg);
    }

    /** Action token for {@code /rival do} from a {@link RivalSystem#currentRivalCards} row. */
    public static String pickerArgFromRivalCard(String card) {
        if (card == null || card.isBlank()) {
            return "";
        }
        String[] p = card.split("\t", -1);
        String uuid = p.length > 0 && p[0] != null ? p[0].trim() : "";
        String name = p.length > 1 && p[1] != null ? p[1].trim() : "";
        if (!uuid.isBlank()) {
            return "uuid:" + uuid;
        }
        return name;
    }

    private static String rivalLinkDisplayName(ServerPlayer from, String rivalUuid) {
        if (from == null || rivalUuid == null || rivalUuid.isBlank()) {
            return "";
        }
        RivalPlayerRecord me = RivalStore.get().ensurePlayer(from);
        if (me == null || me.rivals == null) {
            return "";
        }
        RivalLink link = me.rivals.get(rivalUuid);
        if (link != null && link.name != null && !link.name.isBlank()) {
            return link.name;
        }
        return "";
    }

    public static List<String> seasonLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalProgression.get().seasonLines(player);
    }

    public static List<String> questLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalProgression.get().questLines(player);
    }

    public static List<String> achievementLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalProgression.get().achievementLines(player);
    }

    public static List<String> hofLines(ServerPlayer player) {
        if (!DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalProgression.get().hofLines();
    }

    public static List<String> journalLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalProgression.get().journalLines(player);
    }

    public static List<String> titleLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableRivalSystem) {
            return List.of(UltraStyle.DANGER + "Rival system is disabled.");
        }
        return RivalProgression.get().titleLines(player);
    }

    /** Lore lines for a GUI page (main status is placeholders — use page-specific lists). */
    public static List<String> linesForPage(ServerPlayer player, String page) {
        String p = page == null || page.isBlank() ? "main" : page.toLowerCase(Locale.ROOT);
        return switch (p) {
            case "list" -> listLines(player);
            case "history", "past", "previous" -> RivalSystem.historyLines(player);
            case "pending", "invites", "pendinginvites" -> RivalSystem.pendingInviteLines(player);
            case "actions" -> List.of(
                    UltraStyle.HEADER + "Rival actions",
                    UltraStyle.LABEL + "Declare invites - tap a name to accept, decline, or withdraw",
                    UltraStyle.LABEL + "Declare a rivalry, mark a silent rival, or remove someone from your list",
                    UltraStyle.LABEL + "How rivalries work:",
                    UltraStyle.LABEL + "1. " + UltraStyle.VALUE + "Declare " + UltraStyle.LABEL + "someone as your rival (or mark them " + UltraStyle.VALUE + "Silent" + UltraStyle.LABEL + ")",
                    UltraStyle.LABEL + "2. " + UltraStyle.VALUE + "They accept " + UltraStyle.LABEL + "your invite",
                    UltraStyle.LABEL + "3. " + UltraStyle.VALUE + "You're now Mutual " + UltraStyle.LABEL + "- bonuses activate"
            );
            case "stats", "statistics" -> statsLines(player);
            case "challenge", "challenges" -> challengeLines(player);
            case "challenge_pending", "challenge_requests", "challengepending" -> pendingChallengeLines(player);
            case "top", "leaderboard" -> topLines(player);
            case "progress" -> List.of(
                    UltraStyle.AQUA + UltraStyle.BOLD + "Rival Progress",
                    UltraStyle.LABEL + "Season - Quests - Achievements",
                    UltraStyle.LABEL + "Hall of Fame - Journal - Title",
                    UltraStyle.DIM + "Open a page from the Progress menu"
            );
            case "season" -> seasonLines(player);
            case "quests", "quest" -> questLines(player);
            case "achievements", "achs", "ach" -> achievementLines(player);
            case "hof", "hall" -> hofLines(player);
            case "journal" -> journalLines(player);
            case "title", "titles" -> titleLines(player);
            case "help" -> List.of(
                    UltraStyle.HEADER + "/rival " + UltraStyle.DIM + "- Rival System",
                    UltraStyle.LABEL + "Prefer " + UltraStyle.YELLOW + "/lm " + UltraStyle.LABEL + "-> Rival from the hub",
                    UltraStyle.LABEL + "GUI: List - Challenge - Top - Progress",
                    UltraStyle.YELLOW + "/rival <player> " + UltraStyle.LABEL + "silent rival",
                    UltraStyle.YELLOW + "/rival declare|accept|decline|remove <player>",
                    UltraStyle.YELLOW + "/rival challenge send <player> [minutes]",
                    UltraStyle.LABEL + "GUI: pick rival -> choose 1-10 minutes",
                    UltraStyle.YELLOW + "/rival spectate [player]|stop",
                    UltraStyle.YELLOW + "/rival admin save|refresh|status (staff)"
            );
            default -> {
                Map<String, String> ph = placeholders(player);
                List<String> lore = new ArrayList<>();
                lore.add(UltraStyle.LABEL + "RP " + UltraStyle.VALUE + ph.getOrDefault("rp", "0")
                        + " " + UltraStyle.DIM + "(" + UltraStyle.MARK + ph.getOrDefault("tier_color", "7")
                        + ph.getOrDefault("tier", "?") + UltraStyle.DIM + ")");
                lore.add(UltraStyle.LABEL + "Mutual " + UltraStyle.VALUE + ph.getOrDefault("mutual", "0")
                        + UltraStyle.DIM + "/" + UltraStyle.VALUE + ph.getOrDefault("mutual_max",
                        String.valueOf(RivalConstants.MAX_MUTUAL_RIVALS)));
                lore.add(UltraStyle.LABEL + "Record " + UltraStyle.GREEN + ph.getOrDefault("wins", "0")
                        + UltraStyle.LABEL + "/" + UltraStyle.DANGER + ph.getOrDefault("losses", "0")
                        + UltraStyle.LABEL + "/" + UltraStyle.YELLOW + ph.getOrDefault("draws", "0"));
                lore.add(UltraStyle.LABEL + "TP " + UltraStyle.VALUE
                        + ("true".equalsIgnoreCase(ph.get("tpMsg")) ? "ON" : "OFF"));
                if ("true".equalsIgnoreCase(ph.get("challengeActive"))) {
                    lore.add(UltraStyle.YELLOW + "Challenge active");
                }
                yield lore;
            }
        };
    }

    /**
     * Dispatch {@code /rival do} actions. Does not reopen GUI — caller reopens.
     *
     * @param action e.g. {@code page}, {@code tpmsg}, {@code instinct}, {@code challenge}
     * @param arg    e.g. page name, {@code toggle}, {@code accept}/{@code decline}/{@code cancel}
     * @param page   return page (unused for page action; used by caller to reopen)
     */
    public static String handleDo(ServerPlayer player, String action, String arg, String page) {
        if (player == null) {
            return UltraStyle.DANGER + "This only works in-game - console can't open this menu.";
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        String a = arg == null ? "" : arg.trim();
        // Staff admin works even when the rival system flag is off (save/status/reload).
        if ("admin".equals(act)) {
            return handleAdmin(player, a);
        }
        if (!DifficultyConfig.get().enableRivalSystem) {
            return UltraStyle.DANGER + "Rival system is disabled.";
        }
        if ("page".equals(act) || "refresh".equals(act)) {
            return "";
        }
        if ("tpmsg".equals(act) || "tp_msg".equals(act)) {
            if ("toggle".equalsIgnoreCase(a) || a.isBlank()) {
                RivalPlayerRecord me = RivalStore.get().ensurePlayer(player);
                boolean next = me == null || !me.tpMessages;
                return RivalSystem.setTpMsg(player, next);
            }
            if ("on".equalsIgnoreCase(a) || "true".equalsIgnoreCase(a)) {
                return RivalSystem.setTpMsg(player, true);
            }
            if ("off".equalsIgnoreCase(a) || "false".equalsIgnoreCase(a)) {
                return RivalSystem.setTpMsg(player, false);
            }
            return UltraStyle.DANGER + "Usage: rival do tpmsg toggle";
        }
        if ("instinct".equals(act)) {
            if (!DifficultyConfig.get().rivalInstinct) {
                return UltraStyle.DANGER + "Rival Instinct is disabled.";
            }
            boolean on = RivalInstinct.toggle(player);
            return UltraStyle.YELLOW + "Rival Instinct " + (on ? "ON" : "OFF");
        }
        if ("challenge".equals(act)) {
            String sub = a.toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "accept" -> RivalChallengeManager.get().acceptChallenge(player);
                case "decline", "deny" -> RivalChallengeManager.get().declineChallenge(player);
                case "cancel" -> RivalChallengeManager.get().cancelChallenge(player);
                default -> UltraStyle.DANGER + "Usage: rival do challenge accept|decline|cancel";
            };
        }
        if ("challenge_accept".equals(act) || "challengeaccept".equals(act)) {
            return RivalChallengeManager.get().acceptChallenge(player, a);
        }
        if ("challenge_decline".equals(act) || "challengedecline".equals(act)) {
            return RivalChallengeManager.get().declineChallenge(player, a);
        }
        if ("challenge_cancel".equals(act) || "challengecancel".equals(act)) {
            return RivalChallengeManager.get().cancelChallenge(player, a);
        }
        if ("declare".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a player below to declare as your rival.";
            }
            ServerPlayer target = resolveOnline(player, a);
            if (target == null) {
                return UltraStyle.DANGER + "Player not online: " + a;
            }
            return RivalSystem.declare(player, target);
        }
        if ("accept".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a player below, then accept their rivalry.";
            }
            // Pass uuid: args through — name-only lookup misses offline declarers.
            return RivalSystem.accept(player, a);
        }
        if ("accept_replace".equals(act) || "acceptreplace".equals(act) || "replacemutual".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select which Mutual rivalry to replace.";
            }
            return RivalSystem.acceptReplace(player, a);
        }
        if ("cancel_mutual_replace".equals(act)
                || "cancel_replace".equals(act)
                || "cancelmutualreplace".equals(act)) {
            return RivalSystem.cancelMutualReplace(player);
        }
        if ("decline".equals(act) || "deny".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a player below, then decline their rivalry.";
            }
            return RivalSystem.decline(player, a);
        }
        if ("remove".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a player below, then remove that rivalry.";
            }
            String name = resolveNameArg(player, a);
            if (name.isBlank()) {
                return UltraStyle.DANGER + "Select a player below, then remove that rivalry.";
            }
            return RivalSystem.remove(player, name);
        }
        if ("silent".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a player below to mark as a silent rival.";
            }
            ServerPlayer target = resolveOnline(player, a);
            if (target == null) {
                return UltraStyle.DANGER + "Player not online: " + a;
            }
            return RivalSystem.silentRival(player, target);
        }
        if ("challenge_send".equals(act) || "challengesend".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a player below, then send the challenge.";
            }
            ChallengeSendArg parsed = parseChallengeSendArg(a);
            if (parsed.targetRaw().isBlank()) {
                return UltraStyle.DANGER + "Select a player below, then send the challenge.";
            }
            ServerPlayer target = resolveOnline(player, parsed.targetRaw());
            if (target == null) {
                return UltraStyle.DANGER + "Player not online: " + parsed.targetRaw();
            }
            return RivalChallengeManager.get().sendChallenge(player, target, parsed.minutes());
        }
        if ("spectate".equals(act)) {
            if (a.isBlank() || "any".equalsIgnoreCase(a) || "0".equals(a)) {
                return RivalSpectator.start(player, null);
            }
            ServerPlayer target = resolveOnline(player, a);
            if (target == null) {
                return UltraStyle.DANGER + "Player not online: " + a;
            }
            return RivalSpectator.start(player, target);
        }
        if ("spectate_stop".equals(act) || "spectatestop".equals(act)) {
            return RivalSpectator.stop(player);
        }
        return UltraStyle.DANGER + "Something went wrong. Try again, or ask staff if it keeps happening.";
    }

    /** Staff: save / refresh / status / help — used by GUI + {@code /rival admin …}. */
    public static String handleAdmin(ServerPlayer player, String arg) {
        if (!StaffAccess.isStaff(player)) {
            return UltraStyle.DANGER + "Staff only.";
        }
        String sub = arg == null || arg.isBlank() ? "help" : arg.trim().toLowerCase(Locale.ROOT);
        // Allow "admin save" style where arg may be "save" or compound.
        if (sub.startsWith("save")) {
            RivalStore.get().markDirty();
            RivalStore.get().save();
            RivalProgression.get().save();
            return UltraStyle.YELLOW + "Rival data saved.";
        }
        if (sub.startsWith("refresh") || sub.startsWith("reload")) {
            RivalStore.get().load();
            RivalProgression.get().load();
            return UltraStyle.YELLOW + "Rival data reloaded.";
        }
        if (sub.startsWith("status")) {
            boolean on = DifficultyConfig.get().enableRivalSystem;
            int players = RivalStore.get().players.size();
            return UltraStyle.GOLD + "Rival admin status\n"
                    + UltraStyle.LABEL + "enabled " + UltraStyle.VALUE + (on ? "ON" : "OFF") + "\n"
                    + UltraStyle.LABEL + "players " + UltraStyle.VALUE + players + "\n"
                    + UltraStyle.DIM + RivalStore.path() + "\n"
                    + UltraStyle.DIM + RivalProgression.path();
        }
        return UltraStyle.HEADER + "/rival admin\n"
                + UltraStyle.YELLOW + "/rival admin save " + UltraStyle.LABEL + "- save rivalry and progress data to disk\n"
                + UltraStyle.YELLOW + "/rival admin refresh|reload " + UltraStyle.LABEL + "- reload stores from disk\n"
                + UltraStyle.YELLOW + "/rival admin status " + UltraStyle.LABEL + "- enabled + path summary\n"
                + UltraStyle.YELLOW + "/rival admin open [page] " + UltraStyle.LABEL + "- open rival GUI\n"
                + UltraStyle.DIM + "GUI buttons run these directly.";
    }

    /** Detail lore for a tab-encoded {@link com.dbzlegacy.adaptivedifficulty.rival.RivalSystem#currentRivalCards} row. */
    public static List<String> rivalCardDetailLines(String card) {
        if (card == null || card.isBlank()) {
            return List.of(UltraStyle.LABEL + "Unknown rival.");
        }
        String[] p = card.split("\t", -1);
        String name = p.length > 1 && p[1] != null && !p[1].isBlank() ? p[1] : "?";
        String status = p.length > 2 ? p[2] : "?";
        String tier = p.length > 3 ? p[3] : "?";
        String rp = p.length > 4 ? p[4] : "0";
        String wins = p.length > 5 ? p[5] : "0";
        String losses = p.length > 6 ? p[6] : "0";
        String draws = p.length > 7 ? p[7] : "0";
        boolean online = p.length > 10 && "1".equals(p[10]);
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.VALUE + name + (online ? " " + UltraStyle.GREEN + "* online" : " " + UltraStyle.DIM + "? offline"));
        lines.add(UltraStyle.LABEL + "Status " + UltraStyle.VALUE + status + "  " + UltraStyle.DIM + "-  " + UltraStyle.LABEL + "Tier " + UltraStyle.GOLD + tier);
        lines.add(UltraStyle.LABEL + "RP " + UltraStyle.VALUE + rp + "  " + UltraStyle.DIM + "-  " + UltraStyle.LABEL + "W/L/D " + UltraStyle.VALUE + wins + "/" + losses + "/" + draws);
        if (p.length > 8) {
            lines.add(UltraStyle.LABEL + "Challenge deaths " + UltraStyle.VALUE + "you " + p[8] + " " + UltraStyle.DIM + "/ them " + (p.length > 9 ? p[9] : "0"));
        }
        lines.add(UltraStyle.DIM + "Tap Remove to end this rivalry.");
        return lines;
    }

    /** Detail for a tab-encoded {@link com.dbzlegacy.adaptivedifficulty.rival.RivalSystem#pendingInviteCards} row. */
    public static List<String> pendingInviteDetailLines(String card) {
        if (card == null || card.isBlank()) {
            return List.of(UltraStyle.LABEL + "Unknown invite.");
        }
        String[] p = card.split("\t", -1);
        String name = p.length > 1 && p[1] != null && !p[1].isBlank() ? p[1] : "?";
        String dir = p.length > 2 ? p[2] : "?";
        boolean online = p.length > 4 && "1".equals(p[4]);
        boolean mutual = p.length > 5 && "mutual".equalsIgnoreCase(p[5]);
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.VALUE + name + (online ? " " + UltraStyle.GREEN + "*" : " " + UltraStyle.DIM + "?"));
        if ("IN".equalsIgnoreCase(dir)) {
            if (mutual) {
                lines.add(UltraStyle.YELLOW + "Incoming mutual confirm");
                lines.add(UltraStyle.LABEL + "You both picked Silent - Accept to go Mutual.");
            } else {
                lines.add(UltraStyle.GREEN + "Incoming declare");
                lines.add(UltraStyle.LABEL + "Accept to become Mutual - Decline to ignore.");
            }
        } else {
            lines.add(UltraStyle.GOLD + "Outgoing declare");
            lines.add(UltraStyle.LABEL + "Waiting for them to Accept or Decline.");
        }
        return lines;
    }

    public static boolean pendingInviteCardIncoming(String card) {
        if (card == null) {
            return false;
        }
        String[] p = card.split("\t", -1);
        return p.length >= 3 && "IN".equalsIgnoreCase(p[2]);
    }

    public static boolean pendingInviteCardOutgoing(String card) {
        if (card == null) {
            return false;
        }
        String[] p = card.split("\t", -1);
        return p.length >= 3 && "OUT".equalsIgnoreCase(p[2]);
    }

    /** Scroll row labels for the pending board (direction + name). */
    public static String[] pendingInviteScrollLabels(List<String> cards) {
        if (cards == null || cards.isEmpty()) {
            return new String[0];
        }
        String[] out = new String[cards.size()];
        for (int i = 0; i < cards.size(); i++) {
            String card = cards.get(i);
            String[] p = card == null ? new String[0] : card.split("\t", -1);
            String name = p.length > 1 && p[1] != null && !p[1].isBlank() ? p[1].trim() : "?";
            if (pendingInviteCardOutgoing(card)) {
                out[i] = UltraStyle.GOLD + "? " + UltraStyle.VALUE + name;
            } else if (pendingInviteCardMutualConfirm(card)) {
                out[i] = UltraStyle.YELLOW + "? " + UltraStyle.VALUE + name + " " + UltraStyle.DIM + "- mutual";
            } else {
                out[i] = UltraStyle.GREEN + "? " + UltraStyle.VALUE + name;
            }
        }
        return out;
    }

    public static boolean pendingInviteCardMutualConfirm(String card) {
        if (card == null || card.isBlank()) {
            return false;
        }
        String[] p = card.split("\t", -1);
        return p.length > 5 && "mutual".equalsIgnoreCase(p[5]);
    }

    public static List<String> pendingChallengeDetailLines(String card) {
        if (card == null || card.isBlank()) {
            return List.of(UltraStyle.LABEL + "Unknown challenge request.");
        }
        String[] p = card.split("\t", -1);
        String name = p.length > 1 && p[1] != null && !p[1].isBlank() ? p[1] : "?";
        String dir = p.length > 2 ? p[2] : "?";
        boolean online = p.length > 4 && "1".equals(p[4]);
        String mins = p.length > 5 ? p[5] : "?";
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.VALUE + name + (online ? " " + UltraStyle.GREEN + "* online" : " " + UltraStyle.DIM + "? offline"));
        if ("IN".equalsIgnoreCase(dir)) {
            lines.add(UltraStyle.GREEN + "Incoming challenge");
            lines.add(UltraStyle.LABEL + "Length " + UltraStyle.VALUE + mins + " min");
            lines.add(UltraStyle.LABEL + "Accept to start countdown - Decline to refuse.");
        } else {
            lines.add(UltraStyle.GOLD + "Outgoing challenge");
            lines.add(UltraStyle.LABEL + "Length " + UltraStyle.VALUE + mins + " min");
            lines.add(UltraStyle.LABEL + "Waiting for them - Cancel to withdraw.");
        }
        if (p.length > 3) {
            long exp = 0L;
            try {
                exp = Long.parseLong(p[3].trim());
            } catch (NumberFormatException ignored) {
                exp = 0L;
            }
            if (exp > 0L) {
                long left = exp - System.currentTimeMillis();
                if (left > 0L) {
                    lines.add(UltraStyle.LABEL + "Expires in " + UltraStyle.VALUE + (left / 1000L) + "s");
                }
            }
        }
        return lines;
    }

    public static boolean pendingChallengeCardIncoming(String card) {
        if (card == null) {
            return false;
        }
        String[] p = card.split("\t", -1);
        return p.length >= 3 && "IN".equalsIgnoreCase(p[2]);
    }

    public static boolean pendingChallengeCardOutgoing(String card) {
        if (card == null) {
            return false;
        }
        String[] p = card.split("\t", -1);
        return p.length >= 3 && "OUT".equalsIgnoreCase(p[2]);
    }

    public static String[] pendingChallengeScrollLabels(List<String> cards) {
        if (cards == null || cards.isEmpty()) {
            return new String[0];
        }
        String[] out = new String[cards.size()];
        for (int i = 0; i < cards.size(); i++) {
            String card = cards.get(i);
            String[] p = card == null ? new String[0] : card.split("\t", -1);
            String name = p.length > 1 && p[1] != null && !p[1].isBlank() ? p[1].trim() : "?";
            String mins = p.length > 5 ? p[5] : "?";
            if (pendingChallengeCardOutgoing(card)) {
                out[i] = UltraStyle.GOLD + "? " + UltraStyle.VALUE + name + " " + UltraStyle.DIM + "- " + mins + "m";
            } else {
                out[i] = UltraStyle.DANGER + "? " + UltraStyle.VALUE + name + " " + UltraStyle.DIM + "- " + mins + "m";
            }
        }
        return out;
    }

    /**
     * GUI arg formats: {@code uuid:&lt;id&gt;@&lt;minutes&gt;} or plain target (defaults to
     * {@link RivalConstants#CH_MIN_MINUTES}).
     */
    private static ChallengeSendArg parseChallengeSendArg(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return new ChallengeSendArg("", RivalConstants.CH_MIN_MINUTES);
        }
        int at = s.lastIndexOf('@');
        if (at <= 0 || at >= s.length() - 1) {
            return new ChallengeSendArg(s, RivalConstants.CH_MIN_MINUTES);
        }
        String target = s.substring(0, at).trim();
        String minsRaw = s.substring(at + 1).trim();
        int minutes = RivalConstants.CH_MIN_MINUTES;
        try {
            minutes = Integer.parseInt(minsRaw);
        } catch (NumberFormatException ignored) {
            minutes = RivalConstants.CH_MIN_MINUTES;
        }
        minutes = Math.max(RivalConstants.CH_MIN_MINUTES,
                Math.min(RivalConstants.CH_MAX_MINUTES, minutes));
        return new ChallengeSendArg(target, minutes);
    }

    private record ChallengeSendArg(String targetRaw, int minutes) {}
}
