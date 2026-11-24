package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LM 4.7.68 dojo data ({@code config/legacymechanics/dojos.json}): every dojo (keyed by its master's UUID, so a
 * closed dojo reopens as the same dojo), each player's spar record for the ranked gate and milestones, war records,
 * wager escrow (so a crash refunds instead of losing stakes) and the spots players return to after a visit or a
 * fight. Mentor bonds stay in {@code sparring.json}; a dojo's members are its master's apprentices.
 */
public final class DojoStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DojoStore INSTANCE = new DojoStore();

    public final Map<String, Dojo> dojos = new ConcurrentHashMap<>();
    public final Map<String, PlayerRecord> players = new ConcurrentHashMap<>();
    public final List<WarRecord> wars = new ArrayList<>();
    public final Map<String, Escrow> escrow = new ConcurrentHashMap<>();
    public final Map<String, ReturnSpot> returns = new ConcurrentHashMap<>();
    /** Next free plot index in the dojo dimension (plots are never reused, so builds never overlap). */
    public int nextPlot;
    /** Next free war ring index. */
    public int nextRing;
    private final AtomicBoolean dirty = new AtomicBoolean();

    private DojoStore() {}

    public static DojoStore get() {
        return INSTANCE;
    }

    public static Path path() {
        return ConfigPaths.dataDir().resolve("dojos.json");
    }

    public void markDirty() {
        dirty.set(true);
    }

    /** One dojo: identity, style, economy, upgrades, elites, season clout. */
    public static final class Dojo {
        public String masterUuid = "";
        public String masterName = "";
        public String name = "";
        public boolean open = true;
        public long foundedAt;
        public long closedAt;
        public int timesReopened;
        public int plot = -1;
        /** Style focus id ({@link DojoCatalog.Style}), blank until the master picks one. */
        public String style = "";
        /** Active bonus id from the style catalog, blank for none. */
        public String bonus = "";
        public long bonusChangedAt;
        /** Dojo Points balance. */
        public int points;
        public int pointsEarnedTotal;
        /** Upgrade id to tier (rooms 0..3, arenas 1..6, capacity 0..5). */
        public Map<String, Integer> upgrades = new LinkedHashMap<>();
        /** Up to two elite disciples (UUID strings). */
        public List<String> elites = new ArrayList<>();
        /** Elites the master gave standing war rights (no sign-off needed). */
        public List<String> warRights = new ArrayList<>();
        /** Member UUID to the time they joined (for the elite minimum). */
        public Map<String, Long> joinedAt = new LinkedHashMap<>();
        public int clout;
        public int cloutSeason;
        /** Past seasons' clout, newest last (season id to clout). */
        public Map<String, Integer> cloutHistory = new LinkedHashMap<>();
        public int warWins;
        public int warLosses;
        public int hostedSpars;
        /** "source|day" to points or clout earned that day (daily caps). */
        public Map<String, Integer> daily = new LinkedHashMap<>();
        /** Upgrade tiers the plot was last built at, so a change rebuilds it. */
        public String builtSignature = "";
        /**
         * 4.7.78: the dojo's banner, copied from a loom-made banner the master picked (the item is never taken):
         * the base colour id ({@code red}, {@code light_blue}, ...) and the patterns as {@code code:colour id}, in order.
         * Blank base: no banner picked yet.
         */
        public String bannerBase = "";
        public List<String> bannerPatterns = new ArrayList<>();
        /** 4.7.78: when the name, style or banner last changed after founding (one shared 12-hour cooldown). */
        public long identityChangedAt;
    }

    /** One player's dojo-side record. */
    public static final class PlayerRecord {
        public String name = "";
        /** Friendly (unranked) board spars that counted; ranked unlocks at {@link DojoCatalog#RANKED_GATE}. */
        public int unrankedSpars;
        public int rankedSpars;
        public int rankedWins;
        public boolean firstWarFightPaid;
        public boolean firstRankedWinPaid;
    }

    /** A dojo war: two dojos, a format, the bouts, and how it ended. */
    public static final class WarRecord {
        public String id = "";
        public String fromDojo = "";
        public String fromName = "";
        public String toDojo = "";
        public String toName = "";
        public String format = "BRACKET";
        /** PENDING_SIGNOFF, CHALLENGED, RUNNING, DONE, DECLINED, EXPIRED, CANCELLED. */
        public String status = "CHALLENGED";
        public String declaredBy = "";
        public long createdAt;
        public long expiresAt;
        public long startedAt;
        public long endedAt;
        public String winnerDojo = "";
        public String winnerName = "";
        public int fromScore;
        public int toScore;
        public String champion = "";
        public List<WarFight> fights = new ArrayList<>();
    }

    public static final class WarFight {
        public int round;
        public String a = "";
        public String aName = "";
        public String aDojo = "";
        public String b = "";
        public String bName = "";
        public String bDojo = "";
        public String winner = "";
        public String winnerName = "";
        /** KO, RING_OUT, TIMEOUT, FORFEIT, BYE. */
        public String how = "";
        public long durationMs;
    }

    /** Stakes held while a wagered board spar runs. */
    public static final class Escrow {
        public String boutId = "";
        /** COINS (copper value) or POINTS. */
        public String kind = "";
        public long amount;
        public String a = "";
        public String aDojo = "";
        public String b = "";
        public String bDojo = "";
        public long at;
    }

    /** Where a player stood before a visit, a fight or a war, to put them back exactly there. */
    public static final class ReturnSpot {
        public String dimension = "minecraft:overworld";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String why = "";
    }

    private static final class Persist {
        Map<String, Dojo> dojos;
        Map<String, PlayerRecord> players;
        List<WarRecord> wars;
        Map<String, Escrow> escrow;
        Map<String, ReturnSpot> returns;
        int nextPlot;
        int nextRing;
    }

    public synchronized void load() {
        dojos.clear();
        players.clear();
        wars.clear();
        escrow.clear();
        returns.clear();
        nextPlot = 0;
        nextRing = 0;
        Path file = path();
        if (!Files.isRegularFile(file)) {
            dirty.set(false);
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Persist p = GSON.fromJson(reader, Persist.class);
            if (p != null) {
                if (p.dojos != null) {
                    dojos.putAll(p.dojos);
                }
                if (p.players != null) {
                    players.putAll(p.players);
                }
                if (p.wars != null) {
                    wars.addAll(p.wars);
                }
                if (p.escrow != null) {
                    escrow.putAll(p.escrow);
                }
                if (p.returns != null) {
                    returns.putAll(p.returns);
                }
                nextPlot = p.nextPlot;
                nextRing = p.nextRing;
            }
            for (Dojo d : dojos.values()) {
                normalize(d);
            }
        } catch (Exception e) {
            AdaptiveDifficultyMod.LOGGER.error("[{}] dojos.json could not be read: {}", AdaptiveDifficultyMod.MOD_ID,
                    e.toString());
        }
        dirty.set(false);
    }

    static void normalize(Dojo d) {
        if (d.upgrades == null) {
            d.upgrades = new LinkedHashMap<>();
        }
        if (d.elites == null) {
            d.elites = new ArrayList<>();
        }
        if (d.warRights == null) {
            d.warRights = new ArrayList<>();
        }
        if (d.joinedAt == null) {
            d.joinedAt = new LinkedHashMap<>();
        }
        if (d.cloutHistory == null) {
            d.cloutHistory = new LinkedHashMap<>();
        }
        if (d.daily == null) {
            d.daily = new LinkedHashMap<>();
        }
        if (d.style == null) {
            d.style = "";
        }
        if (d.bonus == null) {
            d.bonus = "";
        }
        d.upgrades.putIfAbsent(DojoCatalog.ARENA, 1);
        if (d.upgrades.get(DojoCatalog.ARENA) < 1) {
            d.upgrades.put(DojoCatalog.ARENA, 1);
        }
    }

    public synchronized void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            Persist p = new Persist();
            p.dojos = new LinkedHashMap<>(dojos);
            p.players = new LinkedHashMap<>(players);
            p.wars = new ArrayList<>(wars);
            p.escrow = new LinkedHashMap<>(escrow);
            p.returns = new LinkedHashMap<>(returns);
            p.nextPlot = nextPlot;
            p.nextRing = nextRing;
            Path tmp = file.resolveSibling("dojos.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(p, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty.set(false);
        } catch (Exception e) {
            AdaptiveDifficultyMod.LOGGER.error("[{}] dojos.json could not be saved: {}", AdaptiveDifficultyMod.MOD_ID,
                    e.toString());
        }
    }

    public void saveIfNeeded() {
        if (dirty.get()) {
            save();
        }
    }

    public PlayerRecord player(String uuid, String name) {
        PlayerRecord r = players.computeIfAbsent(uuid, k -> new PlayerRecord());
        if (name != null && !name.isBlank()) {
            r.name = name;
        }
        return r;
    }
}
