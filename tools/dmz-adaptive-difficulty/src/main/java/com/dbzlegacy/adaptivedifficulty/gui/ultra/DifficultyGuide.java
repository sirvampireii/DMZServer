package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plain-words helpers for the 4.7.59 Difficulty guide (Home step flow, Tiers list, tier detail). Pure
 * functions, self-tested at boot.
 */
public final class DifficultyGuide {
    private DifficultyGuide() {}

    public static final int STEPS = 3;

    /**
     * Recommended tier: the highest tier whose requirement the player meets. A player with no tier yet starts
     * at Tier 1. Never below 1, never above 7.
     */
    public static int recommend(int activeTier, int highestEligible) {
        if (activeTier <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(7, highestEligible));
    }

    /** {@code Step 2 of 3}. */
    public static String stepLabel(int step) {
        return "Step " + step + " of " + STEPS;
    }

    /** Progress dots for a step: {@code [x] [x] [ ]} (filled up to the current step). */
    public static String dots(int step) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= STEPS; i++) {
            if (i > 1) {
                sb.append(' ');
            }
            sb.append(i <= step ? "[x]" : "[ ]");
        }
        return sb.toString();
    }

    /** {@code 42%} for a stat share of 0.42. */
    public static String percent(double share) {
        return Math.round(share * 100.0) + "%";
    }

    /**
     * Change from {@code before} to {@code after} as {@code +X%} / {@code -X%}; {@code ""} when there is no
     * {@code before} to compare with (no tier yet).
     */
    public static String change(double before, double after) {
        if (!(before > 0.0)) {
            return "";
        }
        long pct = Math.round((after / before - 1.0) * 100.0);
        return (pct >= 0 ? "+" : "") + pct + "%";
    }

    /**
     * Enemy strength row: {@code 42% of your power (now 21%, +100%)}, or {@code (now normal mobs)} with no
     * tier. Enemy damage and health both scale from this share of the player's stats.
     */
    public static String strength(double before, double after) {
        String c = change(before, after);
        return percent(after) + " of your power (now " + (before > 0.0 ? percent(before) : "normal mobs")
                + (c.isEmpty() ? "" : ", " + c) + ")";
    }

    /**
     * TP row: kill TP follows the beaten enemy's health, so it moves with enemy strength.
     */
    public static String tp(double before, double after) {
        String c = change(before, after);
        return c.isEmpty() ? "Grows with enemy health" : c + " per kill vs now (follows enemy health)";
    }

    /** Mastery row: Momentum's boost, e.g. {@code +50% while Momentum time is banked}. */
    public static String mastery(double multiplier) {
        long pct = Math.round((multiplier - 1.0) * 100.0);
        return pct <= 0 ? "No boost (Momentum is off)" : "+" + pct + "% while Momentum time is banked";
    }

    /** Text bar {@code [######----]} for {@code left} of {@code max}. */
    public static String bar(long left, long max, int cells) {
        int n = Math.max(1, cells);
        int filled = max <= 0L ? 0 : (int) Math.max(0L, Math.min(n, Math.round((double) left / max * n)));
        if (left > 0L && filled == 0) {
            filled = 1;
        }
        return "[" + "#".repeat(filled) + "-".repeat(n - filled) + "]";
    }

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (recommend(0, 5) != 1 || recommend(0, 0) != 1) {
            bad.add("a player with no tier gets Tier 1 recommended");
        }
        if (recommend(2, 5) != 5 || recommend(3, 3) != 3 || recommend(4, 0) != 1 || recommend(1, 9) != 7) {
            bad.add("recommended is the highest tier the player meets");
        }
        if (!"Step 2 of 3".equals(stepLabel(2)) || !"[x] [x] [ ]".equals(dots(2))) {
            bad.add("step label / dots");
        }
        if (!"+100%".equals(change(0.21, 0.42)) || !"".equals(change(0.0, 0.42)) || !"-50%".equals(change(0.42, 0.21))) {
            bad.add("change percent");
        }
        if (!"42% of your power (now 21%, +100%)".equals(strength(0.21, 0.42))
                || !"21% of your power (now normal mobs)".equals(strength(0.0, 0.21))) {
            bad.add("strength row");
        }
        if (!"+50% while Momentum time is banked".equals(mastery(1.5))) {
            bad.add("mastery row");
        }
        if (!"[#####-----]".equals(bar(150_000L, 300_000L, 10)) || !"[#---------]".equals(bar(1_000L, 300_000L, 10))
                || !"[----------]".equals(bar(0L, 300_000L, 10))) {
            bad.add("bar");
        }
        for (String s : new String[] {strength(0.21, 2.0), tp(0.9, 1.35), mastery(1.5), dots(3)}) {
            for (char ch : s.toCharArray()) {
                if (ch > 0x7F) {
                    bad.add("non-ASCII in guide text: " + s.toLowerCase(Locale.ROOT));
                    break;
                }
            }
        }
        return bad;
    }
}
