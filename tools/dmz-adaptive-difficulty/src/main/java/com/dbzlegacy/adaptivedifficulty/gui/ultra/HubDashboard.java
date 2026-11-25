package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.Momentum;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.rival.RivalConstants;
import com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStore;
import com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings;
import com.dbzlegacy.adaptivedifficulty.sparring.SparBoard;
import com.dbzlegacy.adaptivedifficulty.sparring.SparCombat;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.64 central hub: the one-line "where you stand" texts the hub dashboard and each system's Home share.
 * Every value is read from the system that owns it (prestige ladder, tier table, Momentum, rival store, dojo
 * season, spar bond, Potential Unlock, meditation); nothing here pays or changes anything. Pure text helpers are
 * self-tested at boot.
 */
public final class HubDashboard {
    private HubDashboard() {}

    // ---- prestige (the spine) ----

    /** Level, next-prestige level, ready, system on: from the same placeholders Prestige Home shows. */
    public record Prestige(long level, long required, boolean ready, boolean enabled) {}

    public static Prestige prestige(ServerPlayer player) {
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(player);
        return new Prestige(num(ph.get("level")), num(ph.get("required")), "true".equalsIgnoreCase(ph.get("ready")),
                "true".equalsIgnoreCase(ph.get("system_enabled")));
    }

    /** {@code Level 42,310 of 50,000 [#######---]}: the native client draws the bar. */
    public static String levelBar(long level, long required) {
        return "Level " + DmzRewards.formatWhole(level) + " of " + DmzRewards.formatWhole(required) + " "
                + DifficultyGuide.bar(Math.min(level, required), Math.max(1L, required), 12);
    }

    /** {@code 7,690 levels to your next prestige}, or that the player can prestige now. */
    public static String levelsToGo(long level, long required, boolean ready) {
        if (ready) {
            return "You can prestige now.";
        }
        long left = Math.max(0L, required - level);
        if (left == 0L) {
            return "You hold the most prestiges allowed. Trade some for favor in Prestige.";
        }
        return DmzRewards.formatWhole(left) + (left == 1L ? " level" : " levels") + " to your next prestige";
    }

    /** Both prestige lines, or one line saying the system is off. */
    public static List<String> prestigeLines(ServerPlayer player) {
        Prestige p = prestige(player);
        List<String> out = new ArrayList<>();
        if (!p.enabled()) {
            out.add(UltraStyle.LABEL + "Prestige is off on this server.");
            return out;
        }
        out.add(UltraStyle.VALUE + levelBar(p.level(), p.required()));
        out.add((p.ready() ? UltraStyle.GREEN : UltraStyle.VALUE) + levelsToGo(p.level(), p.required(), p.ready()));
        return out;
    }

    // ---- "Fastest now": the one thing to do next ----

    /**
     * The quickest existing way toward the next prestige, as one line. Order: prestige now; the next difficulty
     * tier the player meets (TP per kill follows enemy health, a share of the player's stats from the tier table,
     * so the gain is the ratio of the two shares, the same number the tier detail page shows); players on the
     * spar board; a close-power spar (the spar closeness bonus tops out at x3).
     */
    public static String fastest(boolean ready, boolean difficultyOn, int nextTier, String nextName, double before,
            double after, int looking) {
        if (ready) {
            return "Fastest now: prestige. Open Prestige.";
        }
        if (difficultyOn && nextTier > 0) {
            String name = "T" + nextTier + " " + nextName;
            String c = DifficultyGuide.change(before, after);
            return c.isEmpty() ? "Fastest now: start Difficulty at " + name + "."
                    : "Fastest now: " + name + ", " + c + " TP per kill.";
        }
        if (looking > 0) {
            return "Fastest now: " + looking + (looking == 1 ? " player wants" : " players want") + " a spar.";
        }
        // 4.7.84 (H4): one body line (the walk's "spar someone close to your power" wrapped to two).
        return "Fastest now: spar near your power for up to "
                + SparBoard.mult(SparCombat.MAX_RIVAL_MULTIPLIER).substring(1) + "x TP.";
    }

    /**
     * 4.7.84 (H3): where the "Fastest now" line points, as the button that opens it: {action, label}. Same order as
     * {@link #fastest}: prestige; the next difficulty tier; the spar board (players looking, or a close-power spar).
     */
    public static String[] fastestGo(boolean ready, boolean difficultyOn, int nextTier) {
        if (ready) {
            return new String[] {"go:prestige|main", "Go: Prestige"};
        }
        if (difficultyOn && nextTier > 0) {
            return new String[] {"go:difficulty|tiers", "Go: Difficulty Tiers"};
        }
        return new String[] {"go:spar|sparboard", "Go: Spar Board"};
    }

    /** The "Fastest now" line and its button for {@code player}: {line, action, label}. */
    public static String[] fastestNowWithGo(ServerPlayer player) {
        Fastest f = fastestFor(player);
        String[] go = fastestGo(f.ready(), f.on(), f.next());
        return new String[] {fastest(f.ready(), f.on(), f.next(), f.nextName(), f.before(), f.after(), f.looking()),
                go[0], go[1]};
    }

    private record Fastest(boolean ready, boolean on, int next, String nextName, double before, double after,
            int looking) {}

    public static String fastestNow(ServerPlayer player) {
        Fastest f = fastestFor(player);
        return fastest(f.ready(), f.on(), f.next(), f.nextName(), f.before(), f.after(), f.looking());
    }

    private static Fastest fastestFor(ServerPlayer player) {
        Prestige p = prestige(player);
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean on = DifficultyConfig.isEnabled() && data != null && data.isPersonalEnabled();
        int active = data == null ? 0 : data.getActiveTier();
        int next = 0;
        String nextName = "";
        double before = 0.0;
        double after = 0.0;
        if (on && active < 7) {
            UnlockTier ut = UnlockTier.byId(active + 1);
            if (ut != null && UltraDifficultyTierUi.isEligible(player, ut)) {
                next = ut.id;
                nextName = ut.display;
                UnlockTier now = active > 0 ? UnlockTier.byId(active) : null;
                before = now == null ? 0.0 : now.enemyScalingMultiplier();
                after = ut.enemyScalingMultiplier();
            }
        }
        return new Fastest(p.enabled() && p.ready(), on, next, nextName, before, after,
                SparBoard.looking(player).size());
    }

    // ---- snapshots, one line each ----

    /** {@code Difficulty: T3 Hardened, Momentum 1:40 banked}. */
    public static String difficultyLine(ServerPlayer player) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        int active = data == null ? 0 : data.getActiveTier();
        UnlockTier ut = active > 0 ? UnlockTier.byId(active) : null;
        String tier = ut == null ? "no tier yet" : "T" + active + " " + ut.display;
        long left = DifficultyConfig.get().momentumEnabled ? Momentum.remainingMillis(player) : 0L;
        return "Difficulty: " + tier + (left > 0L ? ", Momentum " + Momentum.clock(left) + " banked" : "");
    }

    /** {@code Your spar streak: 4 days}, or null with no streak (the line is left out). */
    public static String streakLine(ServerPlayer player) {
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        if (bond == null || bond.streakCurrent <= 0 || SparCombat.streakMultiplier(bond) <= 1.0f) {
            return null;
        }
        return "Your spar streak: " + bond.streakCurrent + (bond.streakCurrent == 1 ? " day" : " days");
    }

    /** {@code Rival: Adversary, 420 RP, #37}. */
    public static String rivalLine(ServerPlayer player) {
        RivalPlayerRecord rec = RivalStore.get().get(player.m_20148_());
        double rp = rec == null ? 0.0 : rec.totalRp;
        if (!(rp > 0.0)) {
            return "Rival: no rank points yet. Challenge someone to start.";
        }
        return rivalText(RivalConstants.tierFor(rp).name(), Math.round(rp), rivalRank(player));
    }

    static String rivalText(String tier, long rp, int rank) {
        return "Rival: " + tier + ", " + DmzRewards.formatWhole(rp) + " RP" + (rank > 0 ? ", #" + rank : "");
    }

    private static int rivalRank(ServerPlayer player) {
        String me = player.m_20148_().toString();
        List<RivalPlayerRecord> top = RivalStore.get().topByRp(Integer.MAX_VALUE);
        for (int i = 0; i < top.size(); i++) {
            if (me.equalsIgnoreCase(top.get(i).uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** {@code Dojo: 2nd of 5 this season}, or how to join one. */
    public static String dojoLine(ServerPlayer player) {
        String key = DojoRankings.homeDojoKey(player);
        if (key == null) {
            return "Dojo: none yet. Find a master in Dojo > Training bonds.";
        }
        SparStore.DojoSeason season = SparStore.get().dojoSeason;
        if (season == null || season.leaderboard == null || season.leaderboard.isEmpty()) {
            return "Dojo: " + DojoRankings.dojoDisplayName(key) + ", no season wins yet";
        }
        List<Map.Entry<String, SparStore.DojoEntry>> rows = new ArrayList<>(season.leaderboard.entrySet());
        rows.sort((a, b) -> Double.compare(b.getValue().seasonRp, a.getValue().seasonRp));
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getKey().equalsIgnoreCase(key)) {
                return "Dojo: " + ordinal(i + 1) + " of " + rows.size() + " this season";
            }
        }
        return "Dojo: " + DojoRankings.dojoDisplayName(key) + ", no season wins yet";
    }

    /**
     * What a spar is worth to the player's dojo (Sparring Home). 4.7.68+: a counted ranked board spar adds clout to
     * each fighter's dojo (capped a day); clout ranks the dojo leaderboard and resets with the season.
     */
    public static String sparWinLine(ServerPlayer player) {
        int clout = com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.CLOUT_MEMBER_SPAR;
        int cap = com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.CLOUT_MEMBER_DAILY;
        if (com.dbzlegacy.adaptivedifficulty.dojo.DojoService.dojoOf(player) == null) {
            return "Join a dojo: each counted ranked spar adds " + clout + " clout to it (up to " + cap + " a day).";
        }
        return "Each counted ranked spar: +" + clout + " clout for your dojo (up to " + cap + " a day).";
    }

    /** {@code Potential Unlock 4 of 30: 340 / 500 points}. */
    public static String potentialLine(ServerPlayer player) {
        try {
            int level = Math.max(0, DmzSkillUtil.level(DmzProgression.stats(player).getSkills(), "potentialunlock"));
            if (level >= 30) {
                return "Potential Unlock: max (30)";
            }
            int next = level + 1;
            long required = next * 100L;
            long progress = Math.min(required,
                    ProgressionData.storedGetLong(player, "potentialunlock_points_to_level_" + next, 0L));
            return potentialText(level, progress, required);
        } catch (Throwable t) {
            return null;
        }
    }

    /** {@code Potential Unlock: level 4 of 30 (340 of 500 points to next)} (4.7.84, H4). */
    static String potentialText(int level, long progress, long required) {
        return "Potential Unlock: level " + level + " of 30 (" + progress + " of " + required + " points to next)";
    }

    /** One meditation line (LM's own progress layer), or null when meditation is off. */
    public static String meditationLine(ServerPlayer player) {
        try {
            List<String> out = new ArrayList<>();
            com.dbzlegacy.adaptivedifficulty.progression.skills.LivingWorldNearbyMeditation.appendCompactLine(out,
                    player, DmzProgression.stats(player).getSkills());
            return out.isEmpty() ? null : out.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    static String ordinal(int n) {
        int m100 = n % 100;
        String suf = m100 >= 11 && m100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suf;
    }

    private static long num(String s) {
        try {
            return s == null ? 0L : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (!"7,690 levels to your next prestige".equals(levelsToGo(42_310L, 50_000L, false))
                || !"1 level to your next prestige".equals(levelsToGo(49_999L, 50_000L, false))
                || !"You can prestige now.".equals(levelsToGo(50_000L, 50_000L, true))) {
            bad.add("levels to your next prestige");
        }
        if (!levelBar(25_000L, 50_000L).endsWith("[######------]")) {
            bad.add("prestige bar");
        }
        if (!"Fastest now: T4 Veteran, +38% TP per kill.".equals(fastest(false, true, 4, "Veteran", 0.65, 0.90, 0))) {
            bad.add("fastest now: next tier uses the tier table ratio");
        }
        if (!"Fastest now: start Difficulty at T1 Awakened."
                .equals(fastest(false, true, 1, "Awakened", 0.0, 0.21, 0))) {
            bad.add("fastest now: no tier yet");
        }
        if (!fastest(true, true, 4, "Veteran", 0.65, 0.90, 3).contains("prestige")
                || !fastest(false, false, 0, "", 0, 0, 2).contains("2 players want a spar")
                || !fastest(false, true, 0, "", 0, 0, 0).contains("for up to 3x TP")) {
            bad.add("fastest now order");
        }
        // 4.7.84 (H3): the Fastest now button opens the system the line names.
        if (!fastestGo(true, true, 4)[0].equals("go:prestige|main")
                || !fastestGo(false, true, 4)[0].equals("go:difficulty|tiers")
                || !fastestGo(false, false, 0)[0].equals("go:spar|sparboard")) {
            bad.add("fastest now button");
        }
        if (!"Potential Unlock: level 0 of 30 (0 of 100 points to next)".equals(potentialText(0, 0L, 100L))) {
            bad.add("potential unlock line");
        }
        if (!"Rival: Adversary, 420 RP, #37".equals(rivalText("Adversary", 420L, 37))) {
            bad.add("rival line");
        }
        if (!"1st".equals(ordinal(1)) || !"2nd".equals(ordinal(2)) || !"11th".equals(ordinal(11))
                || !"23rd".equals(ordinal(23))) {
            bad.add("ordinal");
        }
        for (String s : new String[] {levelBar(1L, 2L), levelsToGo(1L, 9L, false),
                fastest(false, true, 4, "Veteran", 0.65, 0.90, 0)}) {
            for (char ch : s.toCharArray()) {
                if (ch > 0x7F) {
                    bad.add("non-ASCII in hub text: " + s.toLowerCase(Locale.ROOT));
                    break;
                }
            }
        }
        return bad;
    }
}
