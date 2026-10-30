package com.dbzlegacy.adaptivedifficulty.sparring;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;

/**
 * Dojo-vs-dojo rankings keyed by mentor UUID (the dojo master).
 * <p>Supports custom dojo names, banner icons, per-member contributions, and Hall of Fame.
 */
public final class DojoRankings {
    private DojoRankings() {}

    public static final long SEASON_MS = 75L * 24L * 60L * 60L * 1000L;
    public static final long CHALLENGE_TTL_MS = 24L * 60L * 60L * 1000L;
    public static final double BASE_RP_PER_WIN = 12.0;
    public static final double CHALLENGE_RP_MULT = 2.0;
    public static final double DRAW_RP = 4.0;
    public static final int MAX_HOF_SEASONS = 24;
    public static final int MIN_NAME_LEN = 3;
    public static final int MAX_NAME_LEN = 20;

    public static final List<String> BANNER_OPTIONS = List.of(
            "WHITE_BANNER", "ORANGE_BANNER", "MAGENTA_BANNER", "LIGHT_BLUE_BANNER",
            "YELLOW_BANNER", "LIME_BANNER", "PINK_BANNER", "GRAY_BANNER",
            "LIGHT_GRAY_BANNER", "CYAN_BANNER", "PURPLE_BANNER", "BLUE_BANNER",
            "BROWN_BANNER", "GREEN_BANNER", "RED_BANNER", "BLACK_BANNER"
    );

    public static String homeDojoKey(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        if (bond == null) {
            return null;
        }
        if (bond.mentorUuid != null && !bond.mentorUuid.isBlank()) {
            return bond.mentorUuid.trim().toLowerCase(Locale.ROOT);
        }
        if (bond.apprenticeCount() > 0) {
            return player.m_20148_().toString().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    public static boolean isDojoMaster(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return SparStore.get().bond(player.m_20148_()).apprenticeCount() > 0;
    }

    /** UUID keys that represent this player as a dojo master in war pending lists. */
    private static List<String> dojoWarSelfKeys(ServerPlayer player) {
        List<String> keys = new ArrayList<>();
        if (player == null) {
            return keys;
        }
        String self = SparStore.canonicalDojoKey(player.m_20148_().toString());
        if (!self.isEmpty()) {
            keys.add(self);
        }
        if (isDojoMaster(player)) {
            String home = homeDojoKey(player);
            if (home != null && !home.isBlank()) {
                String canon = SparStore.canonicalDojoKey(home);
                if (!canon.isEmpty() && keys.stream().noneMatch(k -> k.equalsIgnoreCase(canon))) {
                    keys.add(canon);
                }
            }
        }
        return keys;
    }

    private static boolean matchesDojoWarSelf(ServerPlayer player, String dojoUuid) {
        if (dojoUuid == null || dojoUuid.isBlank()) {
            return false;
        }
        String canon = SparStore.canonicalDojoKey(dojoUuid);
        if (canon.isEmpty()) {
            return false;
        }
        for (String self : dojoWarSelfKeys(player)) {
            if (canon.equalsIgnoreCase(self)) {
                return true;
            }
        }
        return false;
    }

    public static String dojoDisplayName(String dojoKey) {
        if (dojoKey == null || dojoKey.isBlank()) {
            return "?";
        }
        SparStore.DojoProfile profile = profile(dojoKey);
        if (profile != null && profile.displayName != null && !profile.displayName.isBlank()) {
            return profile.displayName;
        }
        SparStore.DojoEntry e = entry(dojoKey);
        if (e != null && e.mentorName != null && !e.mentorName.isBlank()) {
            return e.mentorName;
        }
        return resolveMentorName(dojoKey, null);
    }

    public static String dojoBannerMaterial(String dojoKey) {
        SparStore.DojoProfile profile = profile(dojoKey);
        if (profile != null && profile.bannerMaterial != null && !profile.bannerMaterial.isBlank()) {
            return profile.bannerMaterial.toUpperCase(Locale.ROOT);
        }
        return "WHITE_BANNER";
    }

    private static SparStore.DojoProfile profile(String dojoKey) {
        if (dojoKey == null || dojoKey.isBlank()) {
            return null;
        }
        return SparStore.get().dojoProfiles.get(dojoKey.toLowerCase(Locale.ROOT));
    }

    private static SparStore.DojoProfile profileOrCreate(String dojoKey) {
        return SparStore.get().dojoProfiles.computeIfAbsent(
                dojoKey.toLowerCase(Locale.ROOT), k -> new SparStore.DojoProfile());
    }

    public static void ensureSeason() {
        SparStore store = SparStore.get();
        long now = System.currentTimeMillis();
        SparStore.DojoSeason season = store.dojoSeason;
        if (season == null) {
            season = freshSeason(now);
            store.dojoSeason = season;
            store.markDirty();
            return;
        }
        if (season.startedAt <= 0L) {
            season.startedAt = now;
            season.seasonId = 1;
            store.markDirty();
            return;
        }
        if (now - season.startedAt >= SEASON_MS) {
            archiveSeason(season);
            store.dojoSeason = freshSeason(now);
            store.markDirty();
        }
    }

    private static SparStore.DojoSeason freshSeason(long now) {
        SparStore.DojoSeason s = new SparStore.DojoSeason();
        s.seasonId = 1;
        s.startedAt = now;
        s.leaderboard = new ConcurrentHashMap<>();
        SparStore.DojoSeason prev = SparStore.get().dojoSeason;
        if (prev != null && prev.seasonId > 0) {
            s.seasonId = prev.seasonId + 1;
        }
        return s;
    }

    private static void archiveSeason(SparStore.DojoSeason season) {
        if (season == null || season.leaderboard == null || season.leaderboard.isEmpty()) {
            return;
        }
        List<Map.Entry<String, SparStore.DojoEntry>> top = sortedEntries(season.leaderboard, "rp", 3);
        if (top.isEmpty()) {
            return;
        }
        SparStore.DojoSeasonRecord rec = new SparStore.DojoSeasonRecord();
        rec.seasonId = season.seasonId;
        rec.endedAt = System.currentTimeMillis();
        Map.Entry<String, SparStore.DojoEntry> first = top.get(0);
        SparStore.DojoEntry d1 = first.getValue();
        rec.championUuid = first.getKey();
        rec.championName = d1 == null ? "?" : dojoDisplayName(first.getKey());
        rec.championRp = d1 == null ? 0 : (int) d1.seasonRp;
        if (top.size() > 1) {
            SparStore.DojoEntry d2 = top.get(1).getValue();
            rec.secondName = d2 == null ? "?" : dojoDisplayName(top.get(1).getKey());
            rec.secondRp = d2 == null ? 0 : (int) d2.seasonRp;
        }
        if (top.size() > 2) {
            SparStore.DojoEntry d3 = top.get(2).getValue();
            rec.thirdName = d3 == null ? "?" : dojoDisplayName(top.get(2).getKey());
            rec.thirdRp = d3 == null ? 0 : (int) d3.seasonRp;
        }
        SparStore.get().dojoHallOfFame.add(0, rec);
        while (SparStore.get().dojoHallOfFame.size() > MAX_HOF_SEASONS) {
            SparStore.get().dojoHallOfFame.remove(SparStore.get().dojoHallOfFame.size() - 1);
        }
        SparStore.get().markDirty();

        StringBuilder sb = new StringBuilder();
        sb.append(UltraStyle.HEADER + "Dojo season " + UltraStyle.VALUE + "#").append(season.seasonId).append(" " + UltraStyle.GOLD + "ended!");
        sb.append("\n" + UltraStyle.YELLOW + "#1 " + UltraStyle.VALUE).append(rec.championName).append(" " + UltraStyle.LABEL).append(rec.championRp).append(" RP");
        if (rec.secondName != null && !rec.secondName.isBlank()) {
            sb.append("\n" + UltraStyle.YELLOW + "#2 " + UltraStyle.VALUE).append(rec.secondName).append(" " + UltraStyle.LABEL).append(rec.secondRp).append(" RP");
        }
        if (rec.thirdName != null && !rec.thirdName.isBlank()) {
            sb.append("\n" + UltraStyle.YELLOW + "#3 " + UltraStyle.VALUE).append(rec.thirdName).append(" " + UltraStyle.LABEL).append(rec.thirdRp).append(" RP");
        }
        broadcast(sb.toString());
    }

    private static void broadcast(String msg) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            DmzRewards.msg(p, msg);
        }
    }

    public static void onInterDojoSession(
            ServerPlayer a,
            SparPlayerRuntime aRt,
            ServerPlayer b,
            SparPlayerRuntime bRt,
            long durationMs
    ) {
        if (durationMs < SparringSystem.MIN_COUNTED_SESSION_MS || a == null || b == null || aRt == null || bRt == null) {
            return;
        }
        if (SparringSystem.isSparringWithOwnMentor(a, b) || SparringSystem.isSparringWithDojoPeer(a, b)) {
            return;
        }
        String keyA = homeDojoKey(a);
        String keyB = homeDojoKey(b);
        if (keyA == null || keyB == null || keyA.equalsIgnoreCase(keyB)) {
            return;
        }
        ensureSeason();
        bumpActivity(keyA, a, aRt.sessionTp, durationMs);
        bumpActivity(keyB, b, bRt.sessionTp, durationMs);
        bumpMemberSession(keyA, a, aRt.sessionTp);
        bumpMemberSession(keyB, b, bRt.sessionTp);

        double tpA = aRt.sessionTp;
        double tpB = bRt.sessionTp;
        boolean challenge = isActiveChallenge(keyA, keyB);
        double mult = challenge ? CHALLENGE_RP_MULT : 1.0;

        // Season RP and spar activity count for EVERY inter-dojo spar — rival dojos legitimately
        // spar outside of wars. Only the WAR RECORD (dojo- and member-level wins / losses / draws)
        // is gated on an actually-declared, still-active war; a friendly spar must not inflate it.
        if (Math.abs(tpA - tpB) < 0.5) {
            double rp = DRAW_RP * mult;
            awardRp(keyA, rp);
            awardRp(keyB, rp);
            bumpMemberRp(keyA, a, rp);
            bumpMemberRp(keyB, b, rp);
            if (challenge) {
                recordWarResult(keyA, true, false, false);
                recordWarResult(keyB, true, false, false);
                recordMemberWarDraw(keyA, a);
                recordMemberWarDraw(keyB, b);
            }
            notifyDojo(a, b, keyA, keyB, UltraStyle.LABEL + "Draw", (int) rp, challenge);
            return;
        }
        boolean aWins = tpA > tpB;
        ServerPlayer winner = aWins ? a : b;
        ServerPlayer loser = aWins ? b : a;
        String winKey = aWins ? keyA : keyB;
        String loseKey = aWins ? keyB : keyA;
        double rp = BASE_RP_PER_WIN * mult;
        awardRp(winKey, rp);
        bumpMemberRp(winKey, winner, rp);
        if (challenge) {
            recordWarResult(winKey, false, true, false);
            recordWarResult(loseKey, false, false, true);
            recordMemberWarWin(winKey, winner);
            recordMemberWarLoss(loseKey, loser);
        }
        notifyDojo(winner, loser, winKey, loseKey,
                UltraStyle.GREEN + winner.m_7755_().getString() + " " + UltraStyle.LABEL + "won", (int) rp, challenge);
    }

    private static void bumpActivity(String dojoKey, ServerPlayer fighter, double tp, long durationMs) {
        SparStore.DojoEntry e = entryOrCreate(dojoKey, fighter);
        e.totalTp += tp;
        e.totalTimeMs += durationMs;
        e.sessions++;
        e.rosterSize = rosterSize(dojoKey);
        e.updatedAt = System.currentTimeMillis();
        SparStore.get().markDirty();
    }

    private static void bumpMemberSession(String dojoKey, ServerPlayer fighter, double tp) {
        SparStore.DojoMemberStats m = memberOrCreate(dojoKey, fighter);
        m.tp += tp;
        m.sessions++;
        SparStore.get().markDirty();
    }

    /** Member's share of the base spar RP — unconditional, feeds the contribution board. */
    private static void bumpMemberRp(String dojoKey, ServerPlayer fighter, double rp) {
        SparStore.DojoMemberStats m = memberOrCreate(dojoKey, fighter);
        m.rpContributed += rp;
        SparStore.get().markDirty();
    }

    /** Member WAR win — only recorded while a war is actually active between the dojos. */
    private static void recordMemberWarWin(String dojoKey, ServerPlayer fighter) {
        SparStore.DojoMemberStats m = memberOrCreate(dojoKey, fighter);
        m.wins++;
        SparStore.get().markDirty();
    }

    /** Member WAR loss — only recorded while a war is actually active between the dojos. */
    private static void recordMemberWarLoss(String dojoKey, ServerPlayer fighter) {
        SparStore.DojoMemberStats m = memberOrCreate(dojoKey, fighter);
        m.losses++;
        SparStore.get().markDirty();
    }

    /** Member WAR draw — only recorded while a war is actually active between the dojos. */
    private static void recordMemberWarDraw(String dojoKey, ServerPlayer fighter) {
        SparStore.DojoMemberStats m = memberOrCreate(dojoKey, fighter);
        m.draws++;
        SparStore.get().markDirty();
    }

    private static SparStore.DojoMemberStats memberOrCreate(String dojoKey, ServerPlayer fighter) {
        SparStore.DojoEntry e = entryOrCreate(dojoKey, fighter);
        if (e.members == null) {
            e.members = new ConcurrentHashMap<>();
        }
        String id = fighter.m_20148_().toString().toLowerCase(Locale.ROOT);
        return e.members.computeIfAbsent(id, k -> {
            SparStore.DojoMemberStats s = new SparStore.DojoMemberStats();
            s.uuid = id;
            s.name = fighter.m_7755_().getString();
            return s;
        });
    }

    /** Base season RP for an inter-dojo spar — unconditional, feeds the dojo ranking. */
    private static void awardRp(String dojoKey, double rp) {
        SparStore.DojoEntry e = entryOrCreate(dojoKey, null);
        e.seasonRp += rp;
        e.updatedAt = System.currentTimeMillis();
        SparStore.get().markDirty();
    }

    /**
     * Dojo-level WAR result (win / loss / draw). Only called while a war is actually active
     * between the two dojos — a plain rival-dojo spar must not move the war record.
     */
    private static void recordWarResult(String dojoKey, boolean draw, boolean win, boolean loss) {
        SparStore.DojoEntry e = entryOrCreate(dojoKey, null);
        if (draw) {
            e.draws++;
        } else if (win) {
            e.wins++;
        } else if (loss) {
            e.losses++;
        }
        e.updatedAt = System.currentTimeMillis();
        SparStore.get().markDirty();
    }

    private static SparStore.DojoEntry entryOrCreate(String dojoKey, ServerPlayer nameSource) {
        ensureSeason();
        SparStore.DojoSeason season = SparStore.get().dojoSeason;
        return season.leaderboard.computeIfAbsent(dojoKey, k -> {
            SparStore.DojoEntry d = new SparStore.DojoEntry();
            d.mentorUuid = dojoKey;
            d.mentorName = resolveMentorName(dojoKey, nameSource);
            d.rosterSize = rosterSize(dojoKey);
            d.members = new ConcurrentHashMap<>();
            return d;
        });
    }

    private static SparStore.DojoEntry entry(String dojoKey) {
        SparStore.DojoSeason season = SparStore.get().dojoSeason;
        if (season == null || season.leaderboard == null) {
            return null;
        }
        return season.leaderboard.get(dojoKey);
    }

    private static String resolveMentorName(String dojoKey, ServerPlayer fallback) {
        if (fallback != null && fallback.m_20148_().toString().equalsIgnoreCase(dojoKey)) {
            return fallback.m_7755_().getString();
        }
        SparStore.LeaderboardEntry lb = SparStore.get().leaderboard.get(dojoKey);
        if (lb != null && lb.name != null && !lb.name.isBlank()) {
            return lb.name;
        }
        com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord rec =
                com.dbzlegacy.adaptivedifficulty.rival.RivalStore.get().players.get(
                        com.dbzlegacy.adaptivedifficulty.rival.RivalUuid.canonical(dojoKey));
        if (rec != null && rec.name != null && !rec.name.isBlank()) {
            return rec.name;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                ServerPlayer online = server.m_6846_().m_11259_(UUID.fromString(dojoKey));
                if (online != null) {
                    return online.m_7755_().getString();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return "Dojo";
    }

    private static int rosterSize(String dojoKey) {
        SparStore.MentorBond bond = SparStore.get().bondsByPlayer.get(dojoKey);
        if (bond == null) {
            return 1;
        }
        bond.normalizeApprentices();
        return 1 + bond.apprenticeCount();
    }

    /**
     * Online members of the dojo this player is actively at war with, in the same world.
     * Empty when there is no active war.
     */
    public static List<ServerPlayer> onlineWarOpponents(ServerPlayer viewer) {
        List<ServerPlayer> out = new ArrayList<>();
        if (viewer == null) {
            return out;
        }
        String key = homeDojoKey(viewer);
        if (key == null) {
            return out;
        }
        SparStore.DojoChallenge active = activeChallengeFor(key);
        if (active == null) {
            return out;
        }
        String enemy = key.equalsIgnoreCase(active.fromDojoUuid)
                ? active.toDojoUuid
                : active.fromDojoUuid;
        if (enemy == null || enemy.isBlank()) {
            return out;
        }
        MinecraftServer server = viewer.m_20194_();
        if (server == null) {
            return out;
        }
        for (ServerPlayer other : server.m_6846_().m_11314_()) {
            if (other == null || other == viewer) {
                continue;
            }
            if (!other.m_6084_() || other.m_5833_()) {
                continue;
            }
            if (other.m_9236_() != viewer.m_9236_()) {
                continue;
            }
            String theirDojo = homeDojoKey(other);
            if (theirDojo != null && theirDojo.equalsIgnoreCase(enemy)) {
                out.add(other);
            }
        }
        return out;
    }

    private static boolean isActiveChallenge(String keyA, String keyB) {
        SparStore.DojoChallenge c = findChallenge(keyA, keyB);
        return c != null && c.active && c.expiresAt > System.currentTimeMillis();
    }

    private static SparStore.DojoChallenge findChallenge(String keyA, String keyB) {
        return findLiveChallengeIn(SparStore.get().dojoChallenges, keyA, keyB);
    }

    /**
     * First UNEXPIRED challenge pairing the two dojos in either direction, or null. Expired
     * records are skipped: a war or a pending challenge is one-shot, so a finished war must
     * never block the pair from challenging again. A live pending challenge and an active war
     * both keep a future {@code expiresAt}, so they still block duplicates.
     *
     * <p>Takes the challenge map as a parameter so the startup self-test can exercise this
     * expiry rule over a local map without touching live {@link SparStore} state.
     */
    public static SparStore.DojoChallenge findLiveChallengeIn(
            Map<String, SparStore.DojoChallenge> challenges, String keyA, String keyB) {
        if (challenges == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (SparStore.DojoChallenge c : challenges.values()) {
            if (c == null || c.expiresAt <= now) {
                continue;
            }
            String from = challengerUuid(c);
            String to = targetUuid(c, "");
            if (from.isBlank() || to.isBlank()) {
                continue;
            }
            if ((keyA.equalsIgnoreCase(from) && keyB.equalsIgnoreCase(to))
                    || (keyB.equalsIgnoreCase(from) && keyA.equalsIgnoreCase(to))) {
                return c;
            }
        }
        return null;
    }

    /** Drop expired challenge records so a finished war can never block a rematch. */
    private static void pruneExpiredChallenges() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, SparStore.DojoChallenge>> it =
                SparStore.get().dojoChallenges.entrySet().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            SparStore.DojoChallenge c = it.next().getValue();
            if (c == null || c.expiresAt <= now) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            SparStore.get().markDirty();
        }
    }

    private static String challengerUuid(SparStore.DojoChallenge c) {
        if (c == null) {
            return "";
        }
        String from = SparStore.canonicalDojoKey(c.fromDojoUuid);
        if (from.isEmpty()) {
            from = SparStore.canonicalDojoKey(c.fromMentorUuid);
        }
        return from;
    }

    private static String targetUuid(SparStore.DojoChallenge c, String mapKey) {
        if (c == null) {
            return "";
        }
        String to = SparStore.canonicalDojoKey(c.toDojoUuid);
        if (to.isEmpty()) {
            to = SparStore.canonicalDojoKey(mapKey);
        }
        return to;
    }

    private static void notifyDojo(
            ServerPlayer winner,
            ServerPlayer loser,
            String winKey,
            String loseKey,
            String result,
            int rp,
            boolean challenge
    ) {
        String tag = challenge ? " " + UltraStyle.HEADER + "DOJO WAR" : "";
        String msg = LmChat.note("Dojo", result + tag
                + " " + UltraStyle.DIM + "· " + UltraStyle.VALUE + dojoDisplayName(winKey)
                + " " + UltraStyle.LABEL + "+" + rp + " RP vs " + UltraStyle.VALUE + dojoDisplayName(loseKey));
        DmzRewards.msg(winner, msg);
        DmzRewards.msg(loser, msg);
        pingDojoMaster(winKey, msg);
        pingDojoMaster(loseKey, msg);
    }

    private static void pingDojoMaster(String dojoKey, String msg) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        try {
            ServerPlayer master = server.m_6846_().m_11259_(UUID.fromString(dojoKey));
            if (master != null && master.m_6084_()) {
                DmzRewards.msg(master, msg);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    public static List<String> topLines(String category, int limit) {
        ensureSeason();
        List<String> lines = new ArrayList<>();
        String cat = category == null || category.isBlank() ? "rp" : category.trim().toLowerCase(Locale.ROOT);
        SparStore.DojoSeason season = SparStore.get().dojoSeason;
        Map<String, SparStore.DojoEntry> board = season == null || season.leaderboard == null
                ? Map.of()
                : season.leaderboard;
        lines.add(UltraStyle.HEADER + "Dojo Top " + UltraStyle.DIM + "- " + UltraStyle.VALUE + cat
                + (season != null ? " " + UltraStyle.DIM + "S#" + season.seasonId : ""));
        List<Map.Entry<String, SparStore.DojoEntry>> sorted = sortedEntries(board, cat, limit);
        int i = 1;
        for (Map.Entry<String, SparStore.DojoEntry> e : sorted) {
            SparStore.DojoEntry d = e.getValue();
            if (d == null) {
                continue;
            }
            String value = formatValue(d, cat);
            lines.add(UltraStyle.YELLOW + "#" + i + " " + UltraStyle.VALUE + dojoDisplayName(e.getKey())
                    + " " + UltraStyle.LABEL + value
                    + " " + UltraStyle.DIM + "(" + d.rosterSize + " fighters)");
            i++;
        }
        if (i == 1) {
            lines.add(UltraStyle.LABEL + "No dojo rankings yet - spar across dojos!");
        }
        return lines;
    }

    /**
     * GUI leaderboard cards: {@code rank\tdojoKey\tdisplayName\tbannerMaterial\tvalue\trosterSize}.
     */
    public static List<String> topCards(String category, int limit) {
        ensureSeason();
        String cat = category == null || category.isBlank() ? "rp" : category.trim().toLowerCase(Locale.ROOT);
        SparStore.DojoSeason season = SparStore.get().dojoSeason;
        Map<String, SparStore.DojoEntry> board = season == null || season.leaderboard == null
                ? Map.of()
                : season.leaderboard;
        List<Map.Entry<String, SparStore.DojoEntry>> sorted = sortedEntries(board, cat, limit);
        List<String> out = new ArrayList<>();
        int i = 1;
        for (Map.Entry<String, SparStore.DojoEntry> e : sorted) {
            SparStore.DojoEntry d = e.getValue();
            if (d == null) {
                continue;
            }
            String name = dojoDisplayName(e.getKey()).replace('\t', ' ').replace('\n', ' ');
            String value = formatValue(d, cat);
            out.add(i + "\t" + e.getKey() + "\t" + name + "\t" + dojoBannerMaterial(e.getKey())
                    + "\t" + value + "\t" + d.rosterSize);
            i++;
        }
        return out;
    }

    public static List<String> hallOfFameLines() {
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.HEADER + "Hall of Chosen");
        List<SparStore.DojoSeasonRecord> hof = SparStore.get().dojoHallOfFame;
        if (hof == null || hof.isEmpty()) {
            lines.add(UltraStyle.LABEL + "No past seasons yet.");
            return lines;
        }
        int shown = 0;
        for (SparStore.DojoSeasonRecord rec : hof) {
            if (rec == null || shown >= 12) {
                break;
            }
            lines.add(UltraStyle.YELLOW + "S#" + rec.seasonId + " " + UltraStyle.VALUE + blank(rec.championName, "?")
                    + " " + UltraStyle.LABEL + rec.championRp + " RP");
            if (rec.secondName != null && !rec.secondName.isBlank()) {
                lines.add(UltraStyle.DIM + "  #2 " + UltraStyle.LABEL + rec.secondName + " " + UltraStyle.DIM + rec.secondRp);
            }
            if (rec.thirdName != null && !rec.thirdName.isBlank()) {
                lines.add(UltraStyle.DIM + "  #3 " + UltraStyle.LABEL + rec.thirdName + " " + UltraStyle.DIM + rec.thirdRp);
            }
            shown++;
        }
        return lines;
    }

    public static List<String> memberLines(ServerPlayer player) {
        return memberLinesForKey(homeDojoKey(player), "Dojo members");
    }

    /** Contribution lines for the dojo the player owns (master), or empty guidance. */
    public static List<String> ownedDojoMemberLines(ServerPlayer player) {
        if (player == null || !isDojoMaster(player)) {
            List<String> lines = new ArrayList<>();
            lines.add(UltraStyle.GOLD + "Your dojo");
            lines.add(UltraStyle.LABEL + "You do not own a dojo yet.");
            lines.add(UltraStyle.DIM + "Recruit apprentices from Actions to start one.");
            return lines;
        }
        String key = player.m_20148_().toString().toLowerCase(Locale.ROOT);
        return memberLinesForKey(key, "Your dojo");
    }

    /** Contribution / roster lines for the dojo the player joined (has a mentor). */
    public static List<String> joinedDojoMemberLines(ServerPlayer player) {
        if (player == null) {
            List<String> lines = new ArrayList<>();
            lines.add(UltraStyle.GOLD + "Your mentor's dojo");
            lines.add(UltraStyle.LABEL + "You are not in a mentor's dojo.");
            lines.add(UltraStyle.DIM + "Request a master from Actions.");
            return lines;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        if (bond == null || bond.mentorUuid == null || bond.mentorUuid.isBlank()) {
            List<String> lines = new ArrayList<>();
            lines.add(UltraStyle.GOLD + "Your mentor's dojo");
            lines.add(UltraStyle.LABEL + "You are not in a mentor's dojo.");
            lines.add(UltraStyle.DIM + "Request a master from Actions.");
            return lines;
        }
        String key = bond.mentorUuid.trim().toLowerCase(Locale.ROOT);
        List<String> lines = memberLinesForKey(key, "Your mentor's dojo");
        // Roster roles from membership cards (mentor + peers).
        for (String card : SparringSystem.membershipDojoCards(player)) {
            if (card == null || card.isBlank()) {
                continue;
            }
            String[] p = card.split("\t", -1);
            if (p.length < 3) {
                continue;
            }
            String role = p[0] == null ? "" : p[0].trim();
            String name = p[2] == null || p[2].isBlank() ? p[1] : p[2].trim();
            String roleLabel = switch (role.toLowerCase(Locale.ROOT)) {
                case "mentor" -> "Master";
                case "you" -> "You";
                default -> "Apprentice";
            };
            lines.add(UltraStyle.DIM + "  - " + UltraStyle.LABEL + roleLabel + " " + UltraStyle.VALUE + blank(name, "-"));
        }
        return lines;
    }

    private static List<String> memberLinesForKey(String key, String heading) {
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + (heading == null || heading.isBlank() ? "Dojo members" : heading));
        if (key == null || key.isBlank()) {
            lines.add(UltraStyle.LABEL + "Join a dojo to see contributions.");
            return lines;
        }
        lines.add(UltraStyle.LABEL + "Dojo " + UltraStyle.VALUE + dojoDisplayName(key));
        SparStore.DojoEntry e = entry(key);
        if (e == null || e.members == null || e.members.isEmpty()) {
            lines.add(UltraStyle.LABEL + "No rival-dojo spars logged this season.");
            return lines;
        }
        List<SparStore.DojoMemberStats> sorted = new ArrayList<>(e.members.values());
        sorted.sort(Comparator.comparingDouble((SparStore.DojoMemberStats m) -> m.rpContributed).reversed());
        int rank = 1;
        for (SparStore.DojoMemberStats m : sorted) {
            if (m == null) {
                continue;
            }
            lines.add(UltraStyle.YELLOW + "#" + rank + " " + UltraStyle.VALUE + blank(m.name, "-")
                    + " " + UltraStyle.LABEL + (int) m.rpContributed + " RP"
                    + " " + UltraStyle.DIM + "| " + UltraStyle.GREEN + m.wins + "W " + UltraStyle.DANGER + m.losses + "L"
                    + " " + UltraStyle.DIM + "| " + UltraStyle.VALUE + DmzRewards.formatWhole(m.tp) + " TP");
            rank++;
            if (rank > 10) {
                break;
            }
        }
        return lines;
    }

    /** Encoded member cards: {@code uuid\tname\trp\twins\ttp}. */
    public static List<String> memberContributionCards(String dojoKey) {
        List<String> out = new ArrayList<>();
        SparStore.DojoEntry e = entry(dojoKey);
        if (e == null || e.members == null || e.members.isEmpty()) {
            return out;
        }
        List<SparStore.DojoMemberStats> sorted = new ArrayList<>(e.members.values());
        sorted.sort(Comparator.comparingDouble((SparStore.DojoMemberStats m) -> m.rpContributed).reversed());
        for (SparStore.DojoMemberStats m : sorted) {
            if (m == null) {
                continue;
            }
            String name = blank(m.name, m.uuid).replace('\t', ' ').replace('\n', ' ');
            out.add(m.uuid + "\t" + name + "\t" + (int) m.rpContributed + "\t" + m.wins
                    + "\t" + (int) m.tp);
            if (out.size() >= 21) {
                break;
            }
        }
        return out;
    }

    public static List<String> memberContributionCards(ServerPlayer player) {
        String key = homeDojoKey(player);
        return key == null ? List.of() : memberContributionCards(key);
    }

    public static List<String> infoLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + "Dojo rankings");
        ensureSeason();
        SparStore.DojoSeason season = SparStore.get().dojoSeason;
        if (season != null) {
            long left = Math.max(0L, SEASON_MS - (System.currentTimeMillis() - season.startedAt));
            lines.add(UltraStyle.LABEL + "Season " + UltraStyle.VALUE + "#" + season.seasonId + " " + UltraStyle.DIM + "| " + UltraStyle.LABEL + (left / 86_400_000L) + " days left");
        }
        String key = homeDojoKey(player);
        if (key == null) {
            lines.add(UltraStyle.LABEL + "Join a dojo to compete on the ladder.");
            lines.add(UltraStyle.DIM + "Invite apprentices or ask a mentor from Actions.");
            return lines;
        }
        SparStore.DojoProfile prof = profile(key);
        String custom = prof != null && prof.displayName != null && !prof.displayName.isBlank()
                ? prof.displayName : null;
        if (custom != null) {
            lines.add(UltraStyle.LABEL + "Dojo " + UltraStyle.VALUE + custom + " " + UltraStyle.DIM + "(" + resolveMentorName(key, null) + ")");
        } else {
            lines.add(UltraStyle.LABEL + "Dojo " + UltraStyle.VALUE + dojoDisplayName(key));
        }
        SparStore.DojoEntry e = entry(key);
        if (e != null) {
            lines.add(UltraStyle.LABEL + "Season RP " + UltraStyle.VALUE + (int) e.seasonRp
                    + " " + UltraStyle.DIM + "| " + UltraStyle.GREEN + e.wins + "W " + UltraStyle.DANGER + e.losses + "L " + UltraStyle.LABEL + e.draws + "D");
            lines.add(UltraStyle.LABEL + "Spar TP " + UltraStyle.VALUE + DmzRewards.formatWhole(e.totalTp)
                    + " " + UltraStyle.DIM + "| " + UltraStyle.LABEL + e.sessions + " rival-dojo spars");
        }
        int rank = dojoRank(key, "rp");
        if (rank > 0) {
            lines.add(UltraStyle.LABEL + "Ladder rank " + UltraStyle.VALUE + "#" + rank);
        }
        if (isDojoMaster(player)) {
            lines.add(UltraStyle.DIM + "Rename: " + UltraStyle.LABEL + "/spar dojo name <your name>");
            lines.add(UltraStyle.DIM + "Wars: " + UltraStyle.LABEL + "Sparring - Wars tab");
        }
        SparStore.DojoChallenge pending = SparStore.get().dojoChallenges.get(
                player.m_20148_().toString().toLowerCase(Locale.ROOT));
        if (pending != null && pending.expiresAt > System.currentTimeMillis() && !pending.active) {
            lines.add(UltraStyle.YELLOW + "Challenge from " + UltraStyle.VALUE + blank(pending.fromDojoName, "?")
                    + " " + UltraStyle.DIM + "- accept in Dojo War");
        }
        SparStore.DojoChallenge active = activeChallengeFor(key);
        if (active != null) {
            String rival = key.equalsIgnoreCase(active.fromDojoUuid)
                    ? active.toDojoName
                    : active.fromDojoName;
            lines.add(UltraStyle.HEADER + "Active war " + UltraStyle.VALUE + "vs " + blank(rival, "?")
                    + " " + UltraStyle.DIM + "(2x RP)");
            lines.add(UltraStyle.DIM + "Their members show in Noea tracking.");
        }
        return lines;
    }

    /** Lore for the Dojo War actions hub (masters manage declare / accept / banner). */
    public static List<String> warInfoLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.DANGER + "Dojo wars");
        if (player == null) {
            lines.add(UltraStyle.DANGER + "Unavailable.");
            return lines;
        }
        if (!isDojoMaster(player)) {
            lines.add(UltraStyle.LABEL + "Only dojo masters manage wars.");
            lines.add(UltraStyle.DIM + "Your mentor accepts challenges for the dojo.");
            return lines;
        }
        String key = homeDojoKey(player);
        lines.add(UltraStyle.LABEL + "Challenge another dojo for " + UltraStyle.VALUE + "2x season RP" + UltraStyle.LABEL + ".");
        lines.add(UltraStyle.DIM + "Wars run 24 hours once accepted. Challenges work while offline.");
        SparStore.DojoChallenge pending = SparStore.get().dojoChallenges.get(
                player.m_20148_().toString().toLowerCase(Locale.ROOT));
        if (pending != null && pending.expiresAt > System.currentTimeMillis() && !pending.active) {
            lines.add(UltraStyle.YELLOW + "Incoming challenge " + UltraStyle.VALUE + blank(pending.fromDojoName, "-"));
            lines.add(UltraStyle.DIM + "Open Pending requests to accept or deny.");
        }
        if (pendingDojoWarCount(player) > 0) {
            lines.add(UltraStyle.LABEL + "Pending requests " + UltraStyle.VALUE + pendingDojoWarCount(player));
        }
        if (key != null) {
            SparStore.DojoChallenge active = activeChallengeFor(key);
            if (active != null) {
                String rival = key.equalsIgnoreCase(active.fromDojoUuid)
                        ? active.toDojoName
                        : active.fromDojoName;
                lines.add(UltraStyle.GOLD + "Active war " + UltraStyle.VALUE + "vs " + blank(rival, "-"));
            }
        }
        return lines;
    }

    public static boolean hasIncomingWarChallenge(ServerPlayer player) {
        if (player == null || !isDojoMaster(player)) {
            return false;
        }
        SparStore.DojoChallenge pending = SparStore.get().dojoChallenges.get(
                player.m_20148_().toString().toLowerCase(Locale.ROOT));
        return pending != null && !pending.active
                && pending.expiresAt > System.currentTimeMillis();
    }

    public static String incomingWarFromName(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        SparStore.DojoChallenge pending = SparStore.get().dojoChallenges.get(
                player.m_20148_().toString().toLowerCase(Locale.ROOT));
        if (pending == null) {
            return "";
        }
        return blank(pending.fromDojoName, "?");
    }

    public static String setDojoName(ServerPlayer master, String rawName) {
        if (master == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        if (!isDojoMaster(master)) {
            return UltraStyle.DANGER + "Only dojo masters can rename their dojo.";
        }
        String name = sanitizeName(rawName);
        if (name == null) {
            return UltraStyle.DANGER + "Name must be " + MIN_NAME_LEN + "–" + MAX_NAME_LEN
                    + " letters, numbers, or spaces.";
        }
        String key = master.m_20148_().toString().toLowerCase(Locale.ROOT);
        SparStore.DojoProfile prof = profileOrCreate(key);
        prof.displayName = name;
        prof.updatedAt = System.currentTimeMillis();
        SparStore.get().markDirty();
        return UltraStyle.GREEN + "Dojo renamed to " + UltraStyle.VALUE + name + UltraStyle.GREEN + ".";
    }

    public static String setDojoBanner(ServerPlayer master, String material) {
        if (master == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        if (!isDojoMaster(master)) {
            return UltraStyle.DANGER + "Only dojo masters can set a dojo banner.";
        }
        if (material == null || material.isBlank()) {
            return UltraStyle.DANGER + "Pick a banner color.";
        }
        String mat = material.trim().toUpperCase(Locale.ROOT);
        if (!mat.endsWith("_BANNER")) {
            mat = mat + "_BANNER";
        }
        if (!BANNER_OPTIONS.contains(mat)) {
            return UltraStyle.DANGER + "Invalid banner. Pick from the Banner menu.";
        }
        String key = master.m_20148_().toString().toLowerCase(Locale.ROOT);
        SparStore.DojoProfile prof = profileOrCreate(key);
        prof.bannerMaterial = mat;
        prof.updatedAt = System.currentTimeMillis();
        SparStore.get().markDirty();
        return UltraStyle.GREEN + "Dojo banner set to " + UltraStyle.VALUE + mat.replace('_', ' ') + UltraStyle.GREEN + ".";
    }

    private static String sanitizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("§.", "");
        if (s.length() < MIN_NAME_LEN || s.length() > MAX_NAME_LEN) {
            return null;
        }
        if (!s.matches("[\\p{L}\\p{N} ][\\p{L}\\p{N} ]*")) {
            return null;
        }
        return s;
    }

    private static SparStore.DojoChallenge activeChallengeFor(String dojoKey) {
        long now = System.currentTimeMillis();
        for (SparStore.DojoChallenge c : SparStore.get().dojoChallenges.values()) {
            if (c == null || !c.active || c.expiresAt <= now) {
                continue;
            }
            if (dojoKey.equalsIgnoreCase(c.fromDojoUuid) || dojoKey.equalsIgnoreCase(c.toDojoUuid)) {
                return c;
            }
        }
        return null;
    }

    public static List<String> rivalDojoCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null || !isDojoMaster(player)) {
            return out;
        }
        ensureSeason();
        String self = player.m_20148_().toString().toLowerCase(Locale.ROOT);
        SparStore.DojoSeason season = SparStore.get().dojoSeason;
        if (season == null || season.leaderboard == null) {
            return out;
        }
        List<Map.Entry<String, SparStore.DojoEntry>> sorted =
                sortedEntries(season.leaderboard, "rp", 50);
        for (Map.Entry<String, SparStore.DojoEntry> e : sorted) {
            if (e.getKey().equalsIgnoreCase(self)) {
                continue;
            }
            SparStore.DojoEntry d = e.getValue();
            if (d == null) {
                continue;
            }
            String name = dojoDisplayName(e.getKey()).replace('\t', ' ').replace('\n', ' ');
            out.add(e.getKey() + "\t" + name + "\t" + (int) d.seasonRp);
        }
        return out;
    }

    public static String challengeDojo(ServerPlayer challenger, ServerPlayer target) {
        if (target == null) {
            return challengeDojoByMasterKey(challenger, null);
        }
        return challengeDojoByMasterKey(challenger, target.m_20148_().toString());
    }

    /** Declare war on a rival dojo by the defending master's UUID (master may be offline). */
    public static String challengeDojoByMasterKey(ServerPlayer challenger, String targetMasterRaw) {
        if (challenger == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        if (!isDojoMaster(challenger)) {
            return UltraStyle.DANGER + "Only dojo masters (with apprentices) can declare war.";
        }
        if (targetMasterRaw == null || targetMasterRaw.isBlank()) {
            return UltraStyle.DANGER + "Pick a rival dojo master.";
        }
        String toKey = SparStore.canonicalDojoKey(normalizeMasterUuid(targetMasterRaw));
        if (toKey.isBlank()) {
            return UltraStyle.DANGER + "Invalid dojo target.";
        }
        String fromKey = SparStore.canonicalDojoKey(challenger.m_20148_().toString());
        if (fromKey.equalsIgnoreCase(toKey)) {
            return UltraStyle.DANGER + "You cannot challenge your own dojo.";
        }
        if (!isDojoMasterUuid(toKey)) {
            return UltraStyle.DANGER + "That dojo has no master with apprentices.";
        }
        // Drop any expired records first so a finished war can never linger as a stale blocker.
        pruneExpiredChallenges();
        if (findChallenge(fromKey, toKey) != null) {
            return UltraStyle.DANGER + "A challenge already exists between these dojos.";
        }
        long now = System.currentTimeMillis();
        SparStore.DojoChallenge c = new SparStore.DojoChallenge();
        c.fromDojoUuid = fromKey;
        c.fromDojoName = dojoDisplayName(fromKey);
        c.toDojoUuid = toKey;
        c.toDojoName = dojoDisplayName(toKey);
        c.fromMentorUuid = fromKey;
        c.expiresAt = now + CHALLENGE_TTL_MS;
        c.active = false;
        c = SparStore.normalizeDojoChallenge(toKey, c);
        SparStore.get().dojoChallenges.put(c.toDojoUuid, c);
        SparStore.get().markDirty();
        MinecraftServer server = challenger.m_20194_();
        ServerPlayer targetOnline = null;
        if (server != null) {
            try {
                targetOnline = server.m_6846_().m_11259_(UUID.fromString(toKey));
            } catch (IllegalArgumentException ignored) {
                targetOnline = null;
            }
        }
        if (targetOnline != null && isDojoMaster(targetOnline)) {
            DmzRewards.msg(targetOnline, LmChat.note("Dojo", UltraStyle.VALUE + c.fromDojoName
                    + " " + UltraStyle.YELLOW + " challenged your dojo to war!"));
            DmzRewards.msg(targetOnline, LmChat.tip("/spar", "- Dojo War - War pending"));
        }
        return UltraStyle.GREEN + "War challenge sent to " + UltraStyle.VALUE + blank(c.toDojoName, dojoDisplayName(toKey)) + UltraStyle.GREEN + ".";
    }

    private static boolean isDojoMasterUuid(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            return false;
        }
        try {
            UUID u = UUID.fromString(masterKey);
            return SparStore.get().bond(u).apprenticeCount() > 0;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String acceptChallenge(ServerPlayer master) {
        if (master == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        if (!isDojoMaster(master)) {
            LmLog.event(
                    "[{}] dojoAcceptWar rejected: not dojo master player={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    master.m_7755_().getString());
            return UltraStyle.DANGER + "Only dojo masters can accept war.";
        }
        String key = master.m_20148_().toString().toLowerCase(Locale.ROOT);
        SparStore.DojoChallenge c = SparStore.get().dojoChallenges.get(key);
        long now = System.currentTimeMillis();
        LmLog.event(
                "[{}] dojoAcceptWar player={} key={} pendingFound={} expiresAt={} now={} active={} from={}",
                AdaptiveDifficultyMod.MOD_ID,
                master.m_7755_().getString(),
                key,
                c != null,
                c == null ? -1L : c.expiresAt,
                now,
                c != null && c.active,
                c == null ? null : c.fromDojoName);
        if (c == null || c.expiresAt <= now) {
            return UltraStyle.DANGER + "No pending dojo war challenge.";
        }
        c.active = true;
        c.expiresAt = System.currentTimeMillis() + CHALLENGE_TTL_MS;
        SparStore.get().markDirty();
        broadcast(LmChat.ok("Dojo", UltraStyle.HEADER + "DOJO WAR " + UltraStyle.VALUE + c.fromDojoName
                + " " + UltraStyle.LABEL + "vs " + UltraStyle.VALUE + c.toDojoName
                + " " + UltraStyle.DIM + "- spars vs that dojo earn 2x ranking points. Their members show in Noea tracking."));
        return UltraStyle.GREEN + "Dojo war accepted! " + UltraStyle.LABEL + "Spars vs " + UltraStyle.VALUE + c.fromDojoName + " " + UltraStyle.GREEN + "now earn double ranking points.";
    }

    public static String declineChallenge(ServerPlayer master) {
        if (master == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        String key = master.m_20148_().toString().toLowerCase(Locale.ROOT);
        SparStore.DojoChallenge c = SparStore.get().dojoChallenges.remove(key);
        if (c == null) {
            return UltraStyle.DANGER + "No pending challenge.";
        }
        SparStore.get().markDirty();
        return UltraStyle.LABEL + "Declined war challenge from " + UltraStyle.VALUE + blank(c.fromDojoName, "?") + UltraStyle.LABEL + ".";
    }

    /**
     * Pending dojo war cards for GUI: {@code masterUuid\tdojoName\tIN|OUT\texpiresMs\tonline\twar}.
     */
    public static List<String> pendingDojoWarCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null || !isDojoMaster(player)) {
            return out;
        }
        long now = System.currentTimeMillis();
        MinecraftServer server = player.m_20194_();

        for (Map.Entry<String, SparStore.DojoChallenge> e : SparStore.get().dojoChallenges.entrySet()) {
            SparStore.DojoChallenge c = e.getValue();
            if (c == null || c.active) {
                continue;
            }
            if (c.expiresAt > 0L && c.expiresAt <= now) {
                continue;
            }
            String toUuid = targetUuid(c, e.getKey());
            String fromUuid = challengerUuid(c);
            if (toUuid.isEmpty() || fromUuid.isEmpty()) {
                continue;
            }
            if (matchesDojoWarSelf(player, toUuid)) {
                String name = blank(c.fromDojoName, "?").replace('\t', ' ').replace('\n', ' ');
                boolean online = isOnline(server, fromUuid);
                out.add(fromUuid + "\t" + name + "\tIN\t" + c.expiresAt + "\t"
                        + (online ? "1" : "0") + "\twar");
            } else if (matchesDojoWarSelf(player, fromUuid)) {
                String name = blank(c.toDojoName, "?").replace('\t', ' ').replace('\n', ' ');
                boolean online = isOnline(server, toUuid);
                out.add(toUuid + "\t" + name + "\tOUT\t" + c.expiresAt + "\t"
                        + (online ? "1" : "0") + "\twar");
            }
        }
        return out;
    }

    public static int pendingDojoWarCount(ServerPlayer player) {
        return pendingDojoWarCards(player).size();
    }

    public static String revokeOutgoingChallenge(ServerPlayer master, String targetMasterRaw) {
        if (master == null) {
            return UltraStyle.DANGER + "Players only.";
        }
        if (!isDojoMaster(master)) {
            return UltraStyle.DANGER + "Only dojo masters can cancel war challenges.";
        }
        String fromKey = SparStore.canonicalDojoKey(master.m_20148_().toString());
        String targetKey = SparStore.canonicalDojoKey(normalizeMasterUuid(targetMasterRaw));
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, SparStore.DojoChallenge>> it =
                SparStore.get().dojoChallenges.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SparStore.DojoChallenge> e = it.next();
            SparStore.DojoChallenge c = e.getValue();
            if (c == null || c.active || c.expiresAt <= now) {
                continue;
            }
            if (!fromKey.equalsIgnoreCase(challengerUuid(c))) {
                continue;
            }
            String toKey = targetUuid(c, e.getKey());
            if (targetKey != null && !targetKey.isBlank()
                    && !targetKey.equalsIgnoreCase(toKey)
                    && !targetKey.equalsIgnoreCase(e.getKey())) {
                continue;
            }
            String name = blank(c.toDojoName, "?");
            it.remove();
            SparStore.get().markDirty();
            return UltraStyle.LABEL + "Cancelled war challenge to " + UltraStyle.VALUE + name + UltraStyle.LABEL + ".";
        }
        return UltraStyle.DANGER + "No outgoing war challenge to cancel.";
    }

    private static String normalizeMasterUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if (s.regionMatches(true, 0, "uuid:", 0, 5)) {
            s = s.substring(5).trim();
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private static boolean isOnline(MinecraftServer server, String uuid) {
        if (server == null || uuid == null || uuid.isBlank()) {
            return false;
        }
        for (net.minecraft.server.level.ServerPlayer p : server.m_6846_().m_11314_()) {
            if (p != null && uuid.equalsIgnoreCase(p.m_20148_().toString())) {
                return true;
            }
        }
        return false;
    }

    public static int dojoRank(String dojoKey, String category) {
        if (dojoKey == null || dojoKey.isBlank()) {
            return 0;
        }
        ensureSeason();
        SparStore.DojoSeason season = SparStore.get().dojoSeason;
        if (season == null || season.leaderboard == null) {
            return 0;
        }
        List<Map.Entry<String, SparStore.DojoEntry>> sorted =
                sortedEntries(season.leaderboard, category == null ? "rp" : category, 9999);
        int rank = 1;
        for (Map.Entry<String, SparStore.DojoEntry> e : sorted) {
            if (dojoKey.equalsIgnoreCase(e.getKey())) {
                return rank;
            }
            rank++;
        }
        return 0;
    }

    private static List<Map.Entry<String, SparStore.DojoEntry>> sortedEntries(
            Map<String, SparStore.DojoEntry> board,
            String cat,
            int limit
    ) {
        List<Map.Entry<String, SparStore.DojoEntry>> list = new ArrayList<>(board.entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<String, SparStore.DojoEntry> e) ->
                score(e.getValue(), cat)).reversed());
        if (limit > 0 && list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    private static double score(SparStore.DojoEntry d, String cat) {
        if (d == null) {
            return 0;
        }
        return switch (cat) {
            case "wins", "win", "wars", "war" -> d.wins;
            case "tp" -> d.totalTp;
            case "sessions", "session" -> d.sessions;
            default -> d.seasonRp;
        };
    }

    private static String formatValue(SparStore.DojoEntry d, String cat) {
        return switch (cat) {
            case "wins", "win", "wars", "war" -> d.wins + " war wins";
            case "tp" -> DmzRewards.formatWhole(d.totalTp) + " TP";
            case "sessions", "session" -> d.sessions + " spars";
            default -> (int) d.seasonRp + " RP";
        };
    }

    private static String blank(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
