package com.dbzlegacy.adaptivedifficulty.goals;

import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Weekly goals (4.7.80; the approved 4.7.64 concept, item 8): three goals a week, each from a different system, the
 * same three for everyone, on the rival weekly clock (weeks counted from the epoch, like RivalProgression). Each
 * player's progress counts from the moment the week starts: the hooks call {@link #note} as things happen, and a new
 * week starts every counter at zero. Progress only: no rewards or payouts in this build.
 */
public final class WeeklyGoals {
    public static final long WEEK_MS = 7L * 86_400_000L;
    static final String KEY = "lm_weekly_goals";

    public static final String SPAR_WINS = "spar_wins";
    public static final String SPAR_MINUTES = "spar_minutes";
    public static final String DOJO_WAR = "dojo_war";
    public static final String RIVAL_WIN = "rival_win";
    public static final String TIER_KILLS = "tier_kills";
    public static final String TRY_TIER = "try_tier";
    public static final String MEDITATE = "meditate";

    /**
     * One goal: what it asks, the target (in the goal's own unit; minute goals count milliseconds), the system it
     * belongs to (no two goals of a week share one), and where "Go to" takes the player.
     */
    public record Goal(String id, String title, long target, String system, String goTitle, String go, boolean minutes) {}

    public static final List<Goal> POOL = List.of(
            new Goal(SPAR_WINS, "Win 3 spars on the Spar Board", 3, "sparring", "Spar Board", "go:spar|sparboard", false),
            new Goal(SPAR_MINUTES, "Spar for 30 minutes", 30L * 60_000L, "sparring", "Sparring", "go:spar|home", true),
            new Goal(DOJO_WAR, "Fight in a dojo war", 1, "dojo", "Dojo Wars", "go:dojo|dojo_wars", false),
            new Goal(RIVAL_WIN, "Win a rival challenge", 1, "rival", "Rival", "go:rival|home", false),
            new Goal(TIER_KILLS, "Defeat 150 mobs with difficulty on", 150, "difficulty", "Difficulty", "go:difficulty|main", false),
            new Goal(TRY_TIER, "Try a tier for free", 1, "difficulty", "Tiers", "go:difficulty|tiers", false),
            new Goal(MEDITATE, "Meditate 20 minutes near another player", 20L * 60_000L, "meditation", "More", "go:hub|more", true));

    private WeeklyGoals() {}

    public static long week(long now) {
        return now / WEEK_MS;
    }

    public static long resetsInMs(long now) {
        return (week(now) + 1L) * WEEK_MS - now;
    }

    /** "3d 4h", "5h 12m", "9m". */
    public static String resetsIn(long now) {
        long m = Math.max(1L, resetsInMs(now) / 60_000L);
        long d = m / 1440L;
        long h = (m % 1440L) / 60L;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        return h > 0 ? h + "h " + (m % 60L) + "m" : m + "m";
    }

    /** The week's three goals: the pool shuffled by the week number, the first goal of each system, three of them. */
    public static List<Goal> goals(long week) {
        List<Goal> shuffled = new ArrayList<>(POOL);
        java.util.Collections.shuffle(shuffled, new Random(week * 0x9E3779B97F4A7C15L + 0x4C4D));
        List<Goal> out = new ArrayList<>();
        List<String> systems = new ArrayList<>();
        for (Goal g : shuffled) {
            if (out.size() < 3 && !systems.contains(g.system())) {
                out.add(g);
                systems.add(g.system());
            }
        }
        return out;
    }

    public static List<Goal> thisWeek() {
        return goals(week(System.currentTimeMillis()));
    }

    /** The player's counters for this week; a stored older week starts over at zero. */
    static CompoundTag counters(UUID id, long week) {
        CompoundTag root = LmPlayerStore.root(id);
        CompoundTag t = root.m_128469_(KEY);
        if (t.m_128454_("week") != week) {
            t = new CompoundTag();
            t.m_128356_("week", week);
            root.m_128365_(KEY, t);
        }
        return t;
    }

    /** Counts {@code amount} toward {@code goal} for this week, if it is one of this week's goals. */
    public static void note(UUID id, String goal, long amount) {
        if (id == null || goal == null || amount <= 0L) {
            return;
        }
        long week = week(System.currentTimeMillis());
        for (Goal g : goals(week)) {
            if (g.id().equals(goal)) {
                CompoundTag t = counters(id, week);
                t.m_128356_(goal, Math.min(g.target(), t.m_128454_(goal) + amount));
                return;
            }
        }
    }

    public static long progress(UUID id, String goal) {
        return id == null ? 0L : counters(id, week(System.currentTimeMillis())).m_128454_(goal);
    }

    /** "12 of 30 minutes", "1 of 3", "Done". */
    public static String progressText(Goal g, long have) {
        if (have >= g.target()) {
            return "Done";
        }
        if (g.minutes()) {
            return (have / 60_000L) + " of " + (g.target() / 60_000L) + " minutes";
        }
        return have + " of " + g.target();
    }

    /** Self-test: every week gives three goals from three systems, every go link is a page link. */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        for (long w = 2900; w < 3020; w++) {
            List<Goal> g = goals(w);
            if (g.size() != 3 || g.stream().map(Goal::system).distinct().count() != 3) {
                bad.add("weekly goals: week " + w + " has " + g.size() + " goals or a repeated system");
            }
        }
        for (Goal g : POOL) {
            if (!g.go().startsWith("go:") || !g.go().contains("|")) {
                bad.add("weekly goals: " + g.id() + " go link " + g.go());
            }
        }
        if (!"3d 0h".equals(resetsIn(WEEK_MS * 10 + WEEK_MS - 3L * 86_400_000L))) {
            bad.add("weekly goals: reset text " + resetsIn(WEEK_MS * 10 + WEEK_MS - 3L * 86_400_000L));
        }
        return bad;
    }
}
