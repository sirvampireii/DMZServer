package com.dbzlegacy.adaptivedifficulty.rival;

import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

/**
 * Proving Grounds from Rival System 4.7.10 — capture location on mutual battles,
 * underdog reclaim bonuses, list flavor.
 * Offense STR/SKP aura stays disabled ({@link RivalConstants#OFFENSE_ENABLED}=false).
 */
public final class ProvingGrounds {
    public static final boolean ENABLED = true;
    public static final double RADIUS = 48.0;
    public static final int TIER2_WINS = 5;
    public static final int TIER3_WINS = 15;
    public static final double ON_GROUNDS_TP_MULT = 1.35;
    public static final double UNDERDOG_TP_MULT = 1.25;
    public static final int ON_GROUNDS_RP_BONUS = 12;
    public static final int RECLAIM_TP = 12000;
    public static final int RECLAIM_RP = 45;
    public static final double CHALLENGE_TP_BONUS = 0.40;
    /** Script PG_UNDERDOG_OFFENSE: the underdog's melee damage on the grounds in a challenge. */
    public static final double UNDERDOG_OFFENSE = 1.12;

    private ProvingGrounds() {}

    public static final class Grounds {
        public boolean active;
        public String name = "";
        public String biome = "";
        public String dim = "";
        public double x;
        public double y;
        public double z;
        public double radius = RADIUS;
        public String championUuid = "";
        public String championName = "";
        public long claimedAt;
        public int battles;
        public int championWins;
        public double damageTotal;
        public long longestFightMs;
        public double strongestHit;
        public int tier;
        public long lastBattleAt;
    }

    public static final class BattleResult {
        public boolean onGrounds;
        public boolean created;
        public boolean reclaimed;
        public boolean strengthened;
        public boolean tierUp;
        public boolean winnerWasUnderdog;
        public String oldChampionName = "";
        public Grounds pg;
    }

    public static Grounds fresh() {
        return new Grounds();
    }

    public static Grounds normalize(Grounds pg) {
        Grounds out = fresh();
        if (pg == null) {
            return out;
        }
        out.active = pg.active;
        out.name = pg.name == null ? "" : pg.name;
        out.biome = pg.biome == null ? "" : pg.biome;
        out.dim = pg.dim == null ? "" : pg.dim;
        out.x = pg.x;
        out.y = pg.y;
        out.z = pg.z;
        out.radius = Math.max(16.0, pg.radius > 0 ? pg.radius : RADIUS);
        out.championUuid = pg.championUuid == null ? "" : pg.championUuid;
        out.championName = pg.championName == null ? "" : pg.championName;
        out.claimedAt = pg.claimedAt;
        out.battles = pg.battles;
        out.championWins = pg.championWins;
        out.damageTotal = pg.damageTotal;
        out.longestFightMs = pg.longestFightMs;
        out.strongestHit = pg.strongestHit;
        out.tier = pg.tier;
        out.lastBattleAt = pg.lastBattleAt;
        if (out.active && out.tier <= 0) {
            out.tier = computeTier(out.championWins);
        }
        return out;
    }

    public static int computeTier(int championWins) {
        if (championWins >= TIER3_WINS) {
            return 3;
        }
        if (championWins >= TIER2_WINS) {
            return 2;
        }
        if (championWins >= 1) {
            return 1;
        }
        return 0;
    }

    public static String tierName(int tier) {
        if (tier >= 3) {
            return "Legendary Proving Grounds";
        }
        if (tier >= 2) {
            return "Dominant Grounds";
        }
        if (tier >= 1) {
            return "Claimed Proving Grounds";
        }
        return "Unclaimed";
    }

    public static String battlefieldName(String biome, String dim) {
        String b = biome == null ? "" : biome.toLowerCase(Locale.ROOT);
        String d = dim == null ? "" : dim.toLowerCase(Locale.ROOT);
        if (d.contains("nether") || b.contains("nether") || b.contains("soul")
                || b.contains("crimson") || b.contains("warped")) {
            return "Hell";
        }
        if (d.contains("end") || b.contains("end")) {
            return "Kami's Lookout";
        }
        if (b.contains("desert") || b.contains("badlands") || b.contains("eroded")) {
            return "Rocky Wastelands";
        }
        if (b.contains("ocean") || b.contains("beach") || b.contains("river")) {
            return "Planet Namek";
        }
        if (b.contains("mushroom")) {
            return "Beerus' Planet";
        }
        if (b.contains("plains") || b.contains("sunflower")) {
            return "the Tournament Arena";
        }
        if (b.contains("jungle")) {
            return "the Sacred Land";
        }
        if (b.contains("taiga") || b.contains("snow") || b.contains("ice") || b.contains("frozen")) {
            return "the Northern Mountains";
        }
        if (b.contains("swamp") || b.contains("mangrove")) {
            return "the Dark Wetlands";
        }
        if (b.contains("forest") || b.contains("grove")) {
            return "the Whispering Woods";
        }
        if (b.contains("mountain") || b.contains("peak") || b.contains("stony")) {
            return "the High Cliffs";
        }
        if (b.contains("cherry")) {
            return "the Sacred Blossoms";
        }
        if (b.contains("time_chamber") || d.contains("time_chamber")) {
            return "the Hyperbolic Time Chamber";
        }
        String clean = biome == null ? "" : biome.replaceFirst("^[a-z0-9_.-]+:", "").replace('_', ' ');
        return clean.isBlank() ? "Unknown Lands" : clean;
    }

    /**
     * Stored place name for display. Claims made before 4.6.246 could store a raw biome id
     * (for example the time chamber); those are renamed from their biome and dimension.
     */
    private static String placeName(Grounds pg) {
        String place = pg.name == null || pg.name.isBlank() ? "Unknown Lands" : pg.name;
        return place.indexOf(':') >= 0 ? battlefieldName(pg.biome, pg.dim) : place;
    }

    public static String displayName(Grounds pg) {
        if (pg == null || !pg.active) {
            return "-";
        }
        String place = placeName(pg);
        if (place.startsWith("the ") || place.startsWith("The ")) {
            return "Proving Grounds of " + place;
        }
        if ("Hell".equals(place) || "Kami's Lookout".equals(place) || "Planet Namek".equals(place)
                || place.contains("'")) {
            return "Proving Grounds of " + place;
        }
        return "Proving Grounds of the " + place;
    }

    public static String shortPlace(Grounds pg) {
        if (pg == null || !pg.active) {
            return "-";
        }
        return placeName(pg);
    }

    public static Grounds captureLocation(ServerPlayer player) {
        Grounds out = fresh();
        if (player == null) {
            out.name = "Unknown Lands";
            return out;
        }
        try {
            out.x = Math.floor(player.m_20185_());
            out.y = Math.floor(player.m_20186_());
            out.z = Math.floor(player.m_20189_());
            ServerLevel level = player.m_284548_();
            if (level != null) {
                ResourceKey<?> dimKey = level.m_46472_();
                out.dim = dimKey == null ? "" : String.valueOf(dimKey.m_135782_());
                try {
                    Holder<Biome> holder = level.m_204166_(player.m_20183_());
                    out.biome = holder.m_203543_()
                            .map(k -> k.m_135782_().toString())
                            .orElse("");
                } catch (Throwable ignored) {
                    out.biome = "";
                }
            }
            out.name = battlefieldName(out.biome, out.dim);
        } catch (Throwable t) {
            out.name = "Unknown Lands";
        }
        return out;
    }

    public static boolean sameDim(ServerPlayer player, Grounds pg) {
        if (player == null || pg == null) {
            return false;
        }
        if (pg.dim == null || pg.dim.isBlank()) {
            return true;
        }
        try {
            ResourceLocation id = player.m_9236_().m_46472_().m_135782_();
            return pg.dim.equalsIgnoreCase(String.valueOf(id));
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean onGrounds(ServerPlayer player, Grounds pg) {
        if (!ENABLED || player == null || pg == null || !pg.active) {
            return false;
        }
        if (!sameDim(player, pg)) {
            return false;
        }
        try {
            double dx = player.m_20185_() - pg.x;
            double dz = player.m_20189_() - pg.z;
            return Math.sqrt(dx * dx + dz * dz) <= (pg.radius > 0 ? pg.radius : RADIUS);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void syncPair(RivalLink a, RivalLink b, Grounds pg) {
        Grounds copy = normalize(pg);
        if (a != null) {
            a.provingGrounds = normalize(copy);
        }
        if (b != null) {
            b.provingGrounds = normalize(copy);
        }
    }

    public static BattleResult processBattle(
            ServerPlayer winnerPlayer,
            ServerPlayer loserPlayer,
            RivalPlayerRecord winnerRecord,
            RivalPlayerRecord loserRecord,
            double wDamage,
            double lDamage,
            double wBiggest,
            double lBiggest,
            long battleDurationMs
    ) {
        BattleResult out = new BattleResult();
        if (!ENABLED || winnerRecord == null || loserRecord == null) {
            return out;
        }
        RivalLink wLink = winnerRecord.rivals.get(loserRecord.uuid);
        RivalLink lLink = loserRecord.rivals.get(winnerRecord.uuid);
        if (wLink == null || lLink == null) {
            return out;
        }
        // The claim from either side: a winner whose own link lost it must not start a new claim
        // over the pair's existing one. syncPair below writes it back to both links.
        Grounds pg = pairGrounds(wLink, lLink);
        long now = System.currentTimeMillis();
        double totalDmg = wDamage + lDamage;
        double bestHit = Math.max(wBiggest, lBiggest);
        long duration = Math.max(0L, battleDurationMs);

        if (!pg.active) {
            Grounds loc = captureLocation(winnerPlayer != null ? winnerPlayer : loserPlayer);
            pg.active = true;
            pg.name = loc.name;
            pg.biome = loc.biome;
            pg.dim = loc.dim;
            pg.x = loc.x;
            pg.y = loc.y;
            pg.z = loc.z;
            pg.radius = RADIUS;
            pg.championUuid = winnerRecord.uuid;
            pg.championName = winnerRecord.name;
            pg.claimedAt = now;
            pg.battles = 1;
            pg.championWins = 1;
            pg.damageTotal = totalDmg;
            pg.longestFightMs = duration;
            pg.strongestHit = bestHit;
            pg.tier = 1;
            pg.lastBattleAt = now;
            syncPair(wLink, lLink, pg);
            out.created = true;
            out.pg = pg;
            if (loserPlayer != null) {
                DmzRewards.msg(loserPlayer, LmChat.fail("Proving", "Defeat marked §e" + displayName(pg) + "§7."));
                DmzRewards.msg(loserPlayer, LmChat.info("Proving",
                        "Return here to face " + winnerRecord.name + " for greater rewards."));
            }
            if (winnerPlayer != null) {
                DmzRewards.msg(winnerPlayer, LmChat.info("Proving", "You claimed §e" + displayName(pg) + "§7."));
                DmzRewards.msg(winnerPlayer, LmChat.info("Proving", "Tier I — " + tierName(1) + ". Defend your claim."));
            }
            RivalStore.get().markDirty();
            return out;
        }

        boolean on = (winnerPlayer != null && onGrounds(winnerPlayer, pg))
                || (loserPlayer != null && onGrounds(loserPlayer, pg));
        out.onGrounds = on;
        out.pg = pg;
        if (!on) {
            return out;
        }

        out.winnerWasUnderdog = !pg.championUuid.isBlank()
                && !pg.championUuid.equals(winnerRecord.uuid);
        pg.battles++;
        pg.damageTotal += totalDmg;
        pg.longestFightMs = Math.max(pg.longestFightMs, duration);
        pg.strongestHit = Math.max(pg.strongestHit, bestHit);
        pg.lastBattleAt = now;

        if (out.winnerWasUnderdog) {
            out.reclaimed = true;
            out.oldChampionName = pg.championName.isBlank() ? "their rival" : pg.championName;
            pg.championUuid = winnerRecord.uuid;
            pg.championName = winnerRecord.name;
            pg.championWins = 1;
            pg.claimedAt = now;
            pg.tier = 1;
            syncPair(wLink, lLink, pg);
            if (winnerPlayer != null) {
                DmzRewards.msg(winnerPlayer, LmChat.ok("Proving", "You have reclaimed your honor."));
                DmzRewards.msg(winnerPlayer, LmChat.info("Proving", displayName(pg) + " §8is yours again."));
                // RECLAIM_TP is paid by RivalChallengeManager.endChallenge in the winner's one grant.
            }
            if (loserPlayer != null) {
                DmzRewards.msg(loserPlayer, LmChat.fail("Proving", "§f" + winnerRecord.name
                        + " §7reclaimed these grounds from you."));
            }
            RivalStore.get().markDirty();
            return out;
        }

        int oldTier = pg.tier <= 0 ? 1 : pg.tier;
        pg.championWins++;
        pg.tier = computeTier(pg.championWins);
        out.strengthened = true;
        out.tierUp = pg.tier > oldTier;
        syncPair(wLink, lLink, pg);
        if (winnerPlayer != null) {
            DmzRewards.msg(winnerPlayer, LmChat.info("Proving", "Your claim grows stronger. §e"
                    + tierName(pg.tier) + " §8(" + pg.championWins + " wins here)"));
            if (out.tierUp) {
                DmzRewards.msg(winnerPlayer, LmChat.ok("Proving", "Tier up! §e" + tierName(pg.tier)));
            }
        }
        if (loserPlayer != null) {
            DmzRewards.msg(loserPlayer, LmChat.fail("Proving",
                    "Your rival's dominance over these grounds grows stronger."));
        }
        RivalStore.get().markDirty();
        return out;
    }

    public static void touchDraw(
            ServerPlayer playerA,
            ServerPlayer playerB,
            RivalPlayerRecord recordA,
            RivalPlayerRecord recordB,
            double dmgA,
            double dmgB,
            double hitA,
            double hitB,
            long battleDurationMs
    ) {
        if (!ENABLED || recordA == null || recordB == null) {
            return;
        }
        RivalLink linkA = recordA.rivals.get(recordB.uuid);
        RivalLink linkB = recordB.rivals.get(recordA.uuid);
        if (linkA == null || linkB == null) {
            return;
        }
        Grounds pg = pairGrounds(linkA, linkB);
        if (!pg.active) {
            return;
        }
        boolean on = (playerA != null && onGrounds(playerA, pg))
                || (playerB != null && onGrounds(playerB, pg));
        if (!on) {
            return;
        }
        pg.battles++;
        pg.damageTotal += dmgA + dmgB;
        pg.longestFightMs = Math.max(pg.longestFightMs, battleDurationMs);
        pg.strongestHit = Math.max(pg.strongestHit, Math.max(hitA, hitB));
        pg.lastBattleAt = System.currentTimeMillis();
        syncPair(linkA, linkB, pg);
        RivalStore.get().markDirty();
    }

    /** Not the champion (someone else holds the claim) and standing on it. */
    public static boolean isUnderdogOnGrounds(ServerPlayer player, Grounds pg) {
        return player != null && pg != null && pg.active && !pg.championUuid.isBlank()
                && !pg.championUuid.equals(player.m_20148_().toString()) && onGrounds(player, pg);
    }

    /** Challenge start: tells the underdog on the grounds that their boost is on. Nobody else. */
    public static void underdogNotice(ServerPlayer player, Grounds pg) {
        if (!isUnderdogOnGrounds(player, pg)) {
            return;
        }
        DmzRewards.msg(player, LmChat.info("Proving", "Underdog bonus active on " + shortPlace(pg)
                + ": your melee hits deal 12% more damage while you fight here."));
        DmzRewards.msg(player, LmChat.info("Proving", "Win on these grounds to reclaim your honor."));
    }

    public static List<String> listLines(RivalLink link) {
        List<String> lines = new ArrayList<>();
        Grounds pg = normalize(link == null ? null : link.provingGrounds);
        if (!pg.active) {
            return lines;
        }
        String tierLabel = pg.tier >= 3 ? "Legendary" : (pg.tier >= 2 ? "Dominant" : "Claimed");
        lines.add("§8    Grounds  §e" + shortPlace(pg) + " §8(" + tierLabel + ")");
        lines.add("§8    Champion  §f" + (pg.championName.isBlank() ? "-" : pg.championName)
                + " §8  Battles  §f" + pg.battles);
        return lines;
    }

    /** The pair's claim, normalized: {@code a}'s when active, else {@code b}'s, else an inactive one. */
    public static Grounds pairGrounds(RivalLink a, RivalLink b) {
        if (a != null && a.provingGrounds != null && a.provingGrounds.active) {
            return normalize(a.provingGrounds);
        }
        if (b != null && b.provingGrounds != null && b.provingGrounds.active) {
            return normalize(b.provingGrounds);
        }
        return fresh();
    }

    /** Script {@code fightingOnGrounds}: the claim is active and EITHER fighter stands on it. */
    public static boolean fightingOnGrounds(ServerPlayer a, ServerPlayer b, Grounds pg) {
        return pg != null && pg.active
                && ((a != null && onGrounds(a, pg)) || (b != null && onGrounds(b, pg)));
    }

    /**
     * Winner TP multiplier: x{@link #ON_GROUNDS_TP_MULT} x(1 + {@link #CHALLENGE_TP_BONUS}) when the
     * fight is on grounds, and x{@link #UNDERDOG_TP_MULT} more when someone else was champion.
     */
    public static float winnerTpMultiplier(Grounds pg, UUID winner, boolean fightingOnGrounds) {
        if (!fightingOnGrounds || pg == null || !pg.active) {
            return 1.0f;
        }
        float mult = (float) ON_GROUNDS_TP_MULT;
        mult *= (float) (1.0 + CHALLENGE_TP_BONUS);
        if (!pg.championUuid.isBlank() && (winner == null || !pg.championUuid.equals(winner.toString()))) {
            mult *= (float) UNDERDOG_TP_MULT;
        }
        return mult;
    }
}
