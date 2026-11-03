package com.dbzlegacy.adaptivedifficulty.progression.form;

import java.util.Locale;

/**
 * Picks the one form a mastery grant writes.
 *
 * <p>The first superform is the earliest superform with unlock level 1 or
 * higher. Level-0 forms such as Oozaru for Saiyans are skipped, so a race
 * whose only superform sits at level 0 gets no selection. The first legendary
 * form is the earliest legendary form, and it is a separate grant. The first
 * god form (Super Saiyan God for Saiyans) is the earliest god form, chosen
 * like the legendary one with no level-0 rule.
 *
 * <p>A form belongs to a tier when its group name or its formType contains the
 * tier token ({@code superform}, {@code legendaryform}, {@code godform}); Ultra
 * gives some races their own formType (angel {@code legendaryforms} group is
 * {@code unboundforms}), so the group name has to count too (4.7.27). When a
 * race has no god or legendary tier match, the earliest form whose name holds
 * {@code god} / {@code legendary} is used instead (glind
 * {@code demongodawakened}, makaijin {@code demongod}, ancient_saiyan
 * {@code primalgod}), skipping the God Ki toggle, dev and test groups.
 */
public final class FirstSuperformChoice {
    private FirstSuperformChoice() {}

    public enum Kind {
        SUPER,
        LEGENDARY,
        GOD
    }

    /** One configured form. {@code stack} marks a stack-form fallback. */
    public static final class Candidate {
        public final String group;
        public final String form;
        public final String formType;
        public final int unlockLevel;
        public final boolean stack;

        public Candidate(String group, String form, String formType, int unlockLevel, boolean stack) {
            this.group = group == null ? "" : group;
            this.form = form == null ? "" : form;
            this.formType = formType == null ? "" : formType;
            this.unlockLevel = unlockLevel;
            this.stack = stack;
        }

        public String label() {
            return group + "." + form;
        }
    }

    /** Group formTypes the name fallback never picks: the God Ki toggle, dev and test groups. */
    private static final java.util.Set<String> NAME_FALLBACK_EXCLUDED_TYPES =
            java.util.Set.of("godkienhancement", "duktemp", "testformstype");

    public static Candidate select(java.util.List<Candidate> forms, Kind kind) {
        if (forms == null || forms.isEmpty() || kind == null) {
            return null;
        }
        if (kind == Kind.LEGENDARY || kind == Kind.GOD) {
            Candidate tier = earliest(forms, kind, 0);
            return tier != null ? tier : earliestByName(forms, kind == Kind.GOD ? "god" : "legendary");
        }
        if (kind != Kind.SUPER) {
            return null;
        }
        // The first real superform is the earliest superform past the level-0
        // form (Oozaru for Saiyans). A race with no superform at level 1 or
        // higher gets no selection -- never fall back to the level-0 form.
        return earliest(forms, Kind.SUPER, 1);
    }

    public static boolean isSuperform(Candidate candidate) {
        return inTier(candidate, "superform");
    }

    public static boolean isLegendary(Candidate candidate) {
        return inTier(candidate, "legendaryform");
    }

    public static boolean isGodform(Candidate candidate) {
        return inTier(candidate, "godform");
    }

    private static boolean inTier(Candidate candidate, String token) {
        return candidate != null && (contains(candidate.group, token) || contains(candidate.formType, token));
    }

    /**
     * Earliest qualifying form: lowest unlock level, then group name, then form name.
     *
     * @param minUnlockLevel minimum unlock level a superform must reach to qualify;
     *                       legendary and god forms ignore it, so a level-0 one still counts
     */
    private static Candidate earliest(java.util.List<Candidate> forms, Kind kind, int minUnlockLevel) {
        Candidate best = null;
        for (Candidate candidate : forms) {
            if (candidate == null || candidate.stack || candidate.form.isBlank() || candidate.group.isBlank()) {
                continue;
            }
            if (kind == Kind.SUPER) {
                if (!isSuperform(candidate)) {
                    continue;
                }
                if (candidate.unlockLevel < minUnlockLevel) {
                    continue;
                }
            } else if (kind == Kind.GOD ? !isGodform(candidate) : !isLegendary(candidate)) {
                continue;
            }
            if (better(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    /** Name fallback for god / legendary: earliest form whose name holds {@code token}. */
    private static Candidate earliestByName(java.util.List<Candidate> forms, String token) {
        Candidate best = null;
        for (Candidate candidate : forms) {
            if (candidate == null || candidate.stack || candidate.form.isBlank() || candidate.group.isBlank()) {
                continue;
            }
            if (NAME_FALLBACK_EXCLUDED_TYPES.contains(candidate.formType.toLowerCase(Locale.ROOT))
                    || !contains(candidate.form, token)) {
                continue;
            }
            if (better(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean better(Candidate candidate, Candidate best) {
        if (best == null) {
            return true;
        }
        if (candidate.unlockLevel != best.unlockLevel) {
            return candidate.unlockLevel < best.unlockLevel;
        }
        int group = candidate.group.compareToIgnoreCase(best.group);
        if (group != 0) {
            return group < 0;
        }
        return candidate.form.compareToIgnoreCase(best.form) < 0;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
