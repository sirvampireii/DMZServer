package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import java.util.Locale;

/**
 * LM 4.7.68: what a dojo's plot looks like, the way DojoKit (the mod-toolkit export) builds it: the hall tier (it
 * follows the rings: 1-2 basic, 3-4 refined, 5-6 masterwork), the style ("none" until the master picks one), the
 * number of rings, and the level of each room. Stored as a signature on the dojo once it is built, so a purchase
 * rebuilds only what changed.
 */
public final class DojoState {
    public int tier = 1;
    public String style = "none";
    public int arenas = 1;
    public int training, meditation, trophy, quarters, spectator, garden;
    /**
     * 4.8.2: the banner the hall's banner spots fly: {@link #NO_BANNER} for the style's own banners (no pick), else
     * a short fingerprint of the picked design (base colour and every layer in order), so a new pick marks the hall
     * stale and rebuilds it.
     */
    public String banner = NO_BANNER;

    /** 4.8.2: the banner part of a dojo with no pick (and of every build made before 4.8.2). */
    public static final String NO_BANNER = "-";

    /** The plot as {@code d}'s upgrades and style say it should be. */
    public static DojoState of(Dojo d) {
        DojoState s = new DojoState();
        s.arenas = Math.max(1, Math.min(DojoCatalog.MAX_ARENAS, DojoService.arenas(d)));
        s.tier = DojoCatalog.lookTier(DojoCatalog.ARENA, s.arenas);
        DojoCatalog.Style st = d == null ? null : DojoCatalog.Style.of(d.style);
        s.style = st == null ? "none" : st.name().toLowerCase(Locale.ROOT);
        s.training = level(d, DojoCatalog.TRAINING);
        s.meditation = level(d, DojoCatalog.MEDITATION);
        s.trophy = level(d, DojoCatalog.TROPHY);
        s.quarters = level(d, DojoCatalog.QUARTERS);
        s.spectator = level(d, DojoCatalog.SPECTATOR);
        s.garden = level(d, DojoCatalog.GARDEN);
        s.banner = bannerKey(d);
        return s;
    }

    /**
     * 4.8.2: the banner part for {@code d}: {@link #NO_BANNER} when no banner is picked (or the pick's colour is not a
     * dye), else 16 hex characters of a SHA-256 over the design as it is placed ({@link DojoPlacer#design(Dojo)}).
     */
    public static String bannerKey(Dojo d) {
        DojoPlacer.BannerDesign b = DojoPlacer.design(d);
        if (b == null) {
            return NO_BANNER;
        }
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(b.text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", h[i] & 0xFF));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(b.text().hashCode());
        }
    }

    private static int level(Dojo d, String up) {
        return Math.max(0, Math.min(DojoCatalog.MAX_ROOM_TIER, DojoService.tier(d, up)));
    }

    /** The structure set this plot is built from: hall tier and style. */
    public String key() {
        return "t" + tier + "_" + style;
    }

    public String signature() {
        return "v2|t=" + tier + "|s=" + style + "|a=" + arenas + "|" + training + "|" + meditation + "|" + trophy + "|"
                + quarters + "|" + spectator + "|" + garden + "|b=" + banner;
    }

    /**
     * A signature back to a state; null for anything else (an old build or none). 4.8.2: a v2 signature without the
     * banner part (built before 4.8.2) reads as {@link #NO_BANNER}, because those builds placed the style's own
     * banners: a dojo with no pick is already right, a dojo with a pick rebuilds once so its banner goes up.
     */
    public static DojoState parse(String sig) {
        if (sig == null || !sig.startsWith("v2|")) {
            return null;
        }
        try {
            String[] p = sig.split("\\|");
            DojoState s = new DojoState();
            s.tier = Integer.parseInt(p[1].substring(2));
            s.style = p[2].substring(2);
            s.arenas = Integer.parseInt(p[3].substring(2));
            s.training = Integer.parseInt(p[4]);
            s.meditation = Integer.parseInt(p[5]);
            s.trophy = Integer.parseInt(p[6]);
            s.quarters = Integer.parseInt(p[7]);
            s.spectator = Integer.parseInt(p[8]);
            s.garden = Integer.parseInt(p[9]);
            s.banner = p.length > 10 && p[10].startsWith("b=") && p[10].length() > 2 ? p[10].substring(2) : NO_BANNER;
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 4.8.2: {@code sig} in today's form (a pre-4.8.2 signature gains its banner part), or {@code sig} unchanged
     *  when it does not parse. */
    public static String normalize(String sig) {
        DojoState s = parse(sig);
        return s == null ? sig : s.signature();
    }

    /** The level an upgrade id has in this state (arena: rings). */
    public int level(String upgrade) {
        return switch (upgrade) {
            case DojoCatalog.ARENA -> arenas;
            case DojoCatalog.TRAINING -> training;
            case DojoCatalog.MEDITATION -> meditation;
            case DojoCatalog.TROPHY -> trophy;
            case DojoCatalog.QUARTERS -> quarters;
            case DojoCatalog.SPECTATOR -> spectator;
            case DojoCatalog.GARDEN -> garden;
            default -> 0;
        };
    }

    /** This state with one more level of {@code upgrade} (the hall tier follows the rings). */
    public DojoState next(String upgrade) {
        DojoState s = parse(signature());
        switch (upgrade) {
            case DojoCatalog.ARENA -> {
                s.arenas = Math.min(DojoCatalog.MAX_ARENAS, arenas + 1);
                s.tier = DojoCatalog.lookTier(DojoCatalog.ARENA, s.arenas);
            }
            case DojoCatalog.TRAINING -> s.training = Math.min(3, training + 1);
            case DojoCatalog.MEDITATION -> s.meditation = Math.min(3, meditation + 1);
            case DojoCatalog.TROPHY -> s.trophy = Math.min(3, trophy + 1);
            case DojoCatalog.QUARTERS -> s.quarters = Math.min(3, quarters + 1);
            case DojoCatalog.SPECTATOR -> s.spectator = Math.min(3, spectator + 1);
            case DojoCatalog.GARDEN -> s.garden = Math.min(3, garden + 1);
            default -> { }
        }
        return s;
    }

    /** The layout's part id for an upgrade (arena: the ring this purchase builds, from {@code arenasBefore}). */
    public static String part(String upgrade, int arenasBefore) {
        return switch (upgrade) {
            case DojoCatalog.ARENA -> "arena_" + Math.min(DojoCatalog.MAX_ARENAS, arenasBefore + 1);
            case DojoCatalog.TRAINING -> "training_hall";
            case DojoCatalog.TROPHY -> "trophy_hall";
            default -> upgrade;
        };
    }
}
