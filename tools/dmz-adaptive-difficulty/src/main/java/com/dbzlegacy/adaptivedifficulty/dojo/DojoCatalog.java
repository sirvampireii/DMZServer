package com.dbzlegacy.adaptivedifficulty.dojo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LM 4.7.68 dojo numbers in one place: upgrades and their Dojo Point costs, look tiers, capacity, the four styles
 * and their bonus catalog (shaped like DUK's empire benefits: an id, a name, a description, a cost and an optional
 * race scope), Dojo Point and clout earnings with their daily caps, the ranked gate, wagers and anti-farm rules.
 * Nothing here touches prestige, TP rates or scaling constants; style bonuses are a small multiplier on spar TP.
 */
public final class DojoCatalog {
    private DojoCatalog() {}

    // ------------------------------------------------------------------ upgrades
    public static final String TRAINING = "training_hall";
    public static final String ARENA = "arena";
    public static final String MEDITATION = "meditation";
    public static final String TROPHY = "trophy_hall";
    public static final String QUARTERS = "quarters";
    public static final String SPECTATOR = "spectator";
    public static final String GARDEN = "garden";
    public static final String CAPACITY = "capacity";

    /** Menu order. */
    public static final List<String> UPGRADES = List.of(ARENA, TRAINING, MEDITATION, SPECTATOR, TROPHY, QUARTERS,
            GARDEN, CAPACITY);
    /** Rooms built with basic / refined / masterwork looks (tier 1..3). */
    public static final List<String> ROOMS = List.of(TRAINING, MEDITATION, SPECTATOR, TROPHY, QUARTERS, GARDEN);

    public static final int MAX_ROOM_TIER = 3;
    public static final int MAX_ARENAS = 6;
    public static final int MAX_CAPACITY_LEVEL = 5;
    public static final int BASE_DISCIPLES = 5;
    public static final int MAX_MEMBERS = 30;
    public static final int MAX_ELITES = 2;

    public static String upgradeName(String id) {
        return switch (id == null ? "" : id) {
            case TRAINING -> "Training hall";
            case ARENA -> "Sparring rings";
            case MEDITATION -> "Meditation room";
            case TROPHY -> "Trophy hall";
            case QUARTERS -> "Master quarters";
            case SPECTATOR -> "Spectator balcony";
            case GARDEN -> "Garden grounds";
            case CAPACITY -> "Disciple capacity";
            default -> "Upgrade";
        };
    }

    public static String upgradeWhat(String id) {
        return switch (id == null ? "" : id) {
            case TRAINING -> "A room in your hall with training dummies and drill posts.";
            case ARENA -> "Another ring on the hall floor, so more board spars can run at once.";
            case MEDITATION -> "A quiet room with tatami, cushions and a stone garden (decoration).";
            case TROPHY -> "A trophy room with pedestals for your hall (decoration).";
            case QUARTERS -> "The master's own room in the hall (decoration).";
            case SPECTATOR -> "A balcony over the rings. Lets players watch your spars.";
            case GARDEN -> "Lanterns, trees and a torii on the grounds round your hall.";
            case CAPACITY -> "Room for more disciples.";
            default -> "";
        };
    }

    /**
     * 4.7.84: what the next level of {@code id} from {@code tier} does, with the real numbers (the last capacity step
     * adds 4, not 5, because a dojo holds 30 members at most).
     */
    public static String upgradeWhat(String id, int tier) {
        if (CAPACITY.equals(id) && tier < MAX_CAPACITY_LEVEL) {
            int more = disciplesAllowed(tier + 1) - disciplesAllowed(tier);
            return "Room for " + more + " more " + (more == 1 ? "disciple" : "disciples") + ".";
        }
        return upgradeWhat(id);
    }

    public static int maxTier(String id) {
        return switch (id == null ? "" : id) {
            case ARENA -> MAX_ARENAS;
            case CAPACITY -> MAX_CAPACITY_LEVEL;
            default -> MAX_ROOM_TIER;
        };
    }

    public static int minTier(String id) {
        return ARENA.equals(id) ? 1 : 0;
    }

    /** Dojo Points for the next level of {@code id} from {@code tier}, -1 when it is already at the top. */
    public static int cost(String id, int tier) {
        if (tier >= maxTier(id)) {
            return -1;
        }
        return switch (id) {
            case ARENA -> 40 * tier;
            case CAPACITY -> 25 * (tier + 1);
            default -> switch (tier) {
                case 0 -> 30;
                case 1 -> 60;
                default -> 120;
            };
        };
    }

    /** Look tier 1..3 (basic, refined, masterwork) a level of {@code id} is drawn at; 0 for not built. */
    public static int lookTier(String id, int tier) {
        if (ARENA.equals(id)) {
            return tier <= 0 ? 0 : 1 + (Math.min(tier, MAX_ARENAS) - 1) / 2;
        }
        if (CAPACITY.equals(id)) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_ROOM_TIER, tier));
    }

    public static String lookName(int look) {
        return switch (look) {
            case 1 -> "basic";
            case 2 -> "refined";
            case 3 -> "masterwork";
            default -> "not built";
        };
    }

    /** Members a dojo can hold at capacity level {@code level}: master plus 5 disciples per level, 30 at most. */
    public static int disciplesAllowed(int level) {
        return Math.min(MAX_MEMBERS - 1, BASE_DISCIPLES + 5 * Math.max(0, Math.min(MAX_CAPACITY_LEVEL, level)));
    }

    /** One level down for a reopened dojo (arenas keep at least one). */
    public static int afterReopen(String id, int tier) {
        return Math.max(minTier(id), tier - 1);
    }

    // ------------------------------------------------------------------ styles and bonuses
    public enum Style {
        KI("Ki Masters", "ki attacks and beams", "light_blue"),
        MELEE("Melee", "punches and kicks", "red"),
        STRIKE("Strike Moves", "strike clashes and heavy blows", "orange"),
        EVASION("Evasion", "blocking and guarding", "green");

        public final String label;
        public final String covers;
        /**
         * 4.7.84 (owner pick d): the banner colour id a dojo of this style flies until its master picks a banner of
         * their own (display only; the first pick stays free).
         */
        public final String bannerColour;

        Style(String label, String covers, String bannerColour) {
            this.label = label;
            this.covers = covers;
            this.bannerColour = bannerColour;
        }

        public static Style of(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            try {
                return valueOf(id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /** True when a spar TP award of {@code hitKind} (SparCombat's kinds) is this style of fighting. */
        public boolean covers(String hitKind) {
            String k = hitKind == null ? "" : hitKind.toLowerCase(Locale.ROOT);
            return switch (this) {
                case KI -> k.equals("ki") || k.equals("release") || k.equals("beam");
                case MELEE -> k.equals("melee");
                case STRIKE -> k.equals("clash") || k.equals("strike");
                case EVASION -> k.equals("block");
            };
        }
    }

    /** DMZ race ids a bonus can be scoped to, with their names. */
    public static final List<String[]> RACES = List.of(new String[] {"human", "Human"},
            new String[] {"saiyan", "Saiyan"}, new String[] {"namekian", "Namekian"},
            new String[] {"frost_demon", "Frost Demon"}, new String[] {"bio_android", "Bio-Android"},
            new String[] {"majin", "Majin"});

    /** Style focus bonus: spar TP on that style's hits, every member. */
    public static final double FOCUS_PCT = 0.05;
    /** Race tradition bonus: spar TP on that style's hits, members of one race only. */
    public static final double RACE_PCT = 0.08;
    public static final int BONUS_COST = 40;
    /** 4.7.78: changing the dojo's identity after founding (master only), from the Dojo Points balance. */
    public static final int STYLE_CHANGE_COST = 40;
    public static final int NAME_CHANGE_COST = 20;
    public static final int BANNER_CHANGE_COST = 20;
    /** One cooldown for all three identity changes, from the moment any of them is confirmed. */
    public static final long IDENTITY_COOLDOWN_MS = 12L * 60L * 60L * 1000L;
    public static final long BONUS_COOLDOWN_MS = 7L * 24L * 60L * 60L * 1000L;
    public static final int STYLE_COST = 0;
    /**
     * 4.7.84 (owner pick c): Dojo Points a player's first founding starts with. Paid once per player; reopening a
     * closed dojo or founding again later pays nothing.
     */
    public static final int FOUNDING_GRANT = 15;

    /** One catalog bonus, like a DUK benefit: id, name, description, cost and an optional required race. */
    public record Bonus(String id, Style style, String name, String description, double pct, String requiredRace) {}

    public static List<Bonus> bonuses(Style style) {
        List<Bonus> out = new ArrayList<>();
        if (style == null) {
            return out;
        }
        out.add(new Bonus(style.name().toLowerCase(Locale.ROOT) + ":focus", style, style.label + " focus",
                "+" + pct(FOCUS_PCT) + " spar TP from " + style.covers + ", every member.", FOCUS_PCT, ""));
        for (String[] r : RACES) {
            out.add(new Bonus(style.name().toLowerCase(Locale.ROOT) + ":race:" + r[0], style, r[1] + " tradition",
                    "+" + pct(RACE_PCT) + " spar TP from " + style.covers + ", " + r[1] + " members only.", RACE_PCT,
                    r[0]));
        }
        return out;
    }

    public static Bonus bonus(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Style s = Style.of(id.split(":", 2)[0]);
        for (Bonus b : bonuses(s)) {
            if (b.id().equalsIgnoreCase(id.trim())) {
                return b;
            }
        }
        return null;
    }

    static String pct(double p) {
        return Math.round(p * 100) + "%";
    }

    // ------------------------------------------------------------------ Dojo Points and clout
    public enum Earn {
        HOST(2, 20, "a spar hosted in your arena"),
        WAR_BOUT(5, 40, "a war bout won"),
        WAR_WIN(25, 50, "a war won"),
        TRAINING(3, 15, "training with a disciple"),
        MILESTONE(5, 25, "a disciple milestone");

        public final int points;
        public final int dailyCap;
        public final String what;

        Earn(int points, int dailyCap, String what) {
            this.points = points;
            this.dailyCap = dailyCap;
            this.what = what;
        }
    }

    public static final int CLOUT_WAR_WIN = 50;
    public static final int CLOUT_WAR_BOUT = 5;
    public static final int CLOUT_HOST = 2;
    public static final int CLOUT_HOST_DAILY = 20;
    public static final int CLOUT_MEMBER_SPAR = 1;
    public static final int CLOUT_MEMBER_DAILY = 10;
    /** Clout a dojo starts the next season with for last season's place (1st, 2nd, 3rd). */
    public static final int[] CLOUT_PLACEMENT = {30, 20, 10};

    // ------------------------------------------------------------------ board, ranked gate, wagers, anti-farm
    public static final int RANKED_GATE = 30;
    /** A board spar counts only after this long with both fighters landing damage. */
    public static final long MIN_FIGHT_MS = 60_000L;
    public static final double MIN_FIGHT_DAMAGE = 10.0;
    /** The same pair earns ranked credit (and host points) at most this often. */
    public static final long PAIR_COOLDOWN_MS = 15L * 60L * 1000L;
    public static final long BOUT_TIMEOUT_MS = 5L * 60L * 1000L;
    /** Leaving the ring by this many blocks forfeits (board bouts and war bouts). */
    public static final double RING_OUT_BLOCKS = 60.0;
    public static final long NETHERITE_COPPER = 100_000L;
    public static final long MAX_COIN_WAGER = 5L * NETHERITE_COPPER;
    public static final int MAX_POINT_WAGER = 20;
    /** Wager steps the poster cycles through: coins in copper value, then Dojo Points. */
    public static final long[] COIN_STEPS = {1_000L, 10_000L, NETHERITE_COPPER, 3L * NETHERITE_COPPER, MAX_COIN_WAGER};
    public static final int[] POINT_STEPS = {5, 10, MAX_POINT_WAGER};
    /** Level spread a listing accepts: any, or within 25 / 50 / 100 levels of the poster. */
    public static final int[] LEVEL_RANGES = {0, 25, 50, 100};
    /** Elite minimum time in the dojo. */
    public static final long ELITE_MIN_MS = 3L * 24L * 60L * 60L * 1000L;
    public static final long WAR_CHALLENGE_MS = 24L * 60L * 60L * 1000L;

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (cost(ARENA, 1) != 40 || cost(ARENA, 5) != 200 || cost(ARENA, 6) != -1) {
            bad.add("arena costs 40 x arenas owned, six at most");
        }
        if (cost(TRAINING, 0) != 30 || cost(TRAINING, 2) != 120 || cost(TRAINING, 3) != -1) {
            bad.add("room tiers cost 30 / 60 / 120");
        }
        if (disciplesAllowed(0) != 5 || disciplesAllowed(5) != 29) {
            bad.add("capacity: 5 disciples to start, 30 members at the top");
        }
        if (afterReopen(ARENA, 1) != 1 || afterReopen(TRAINING, 0) != 0 || afterReopen(TRAINING, 3) != 2) {
            bad.add("a reopened dojo is one tier down everywhere, never below the first arena");
        }
        if (lookTier(ARENA, 1) != 1 || lookTier(ARENA, 4) != 2 || lookTier(ARENA, 6) != 3) {
            bad.add("arena looks: 1-2 basic, 3-4 refined, 5-6 masterwork");
        }
        if (!Style.KI.covers("ki") || Style.KI.covers("melee") || !Style.EVASION.covers("block")
                || !Style.STRIKE.covers("clash") || Style.MELEE.covers("ki")) {
            bad.add("styles cover only their own kind of fighting");
        }
        Bonus b = bonus("melee:race:saiyan");
        if (b == null || !"saiyan".equals(b.requiredRace()) || bonus("nope") != null || bonuses(Style.KI).size() != 7) {
            bad.add("bonus catalog: one focus and six race traditions per style");
        }
        if (FOCUS_PCT > 0.10 || RACE_PCT > 0.10) {
            bad.add("style bonuses stay modest (10% or less)");
        }
        if (!upgradeWhat(CAPACITY, 0).contains("5 more") || !upgradeWhat(CAPACITY, 4).contains("4 more")) {
            bad.add("capacity: +5 a step, +4 at the last step (30 members at most)");
        }
        if (FOUNDING_GRANT != 15) {
            bad.add("founding grant: 15 Dojo Points on a first founding");
        }
        if (MAX_COIN_WAGER != 500_000L || MAX_POINT_WAGER != 20 || COIN_STEPS[COIN_STEPS.length - 1] != MAX_COIN_WAGER) {
            bad.add("wager caps: 5 netherite coins or 20 Dojo Points");
        }
        return bad;
    }
}
