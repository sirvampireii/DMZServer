package com.dbzlegacy.adaptivedifficulty.rival;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerPlayer;

/**
 * In-memory rivalry database persisted to {@code config/legacymechanics/rivalry-v4.json}.
 * <p>Mod JSON only — no CustomNPCs (CNPC) script writes. Rival progression (seasons/quests)
 * lives in {@code config/legacymechanics/progression-v4.json} via {@link RivalProgression}.
 */
public final class RivalStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final RivalStore INSTANCE = new RivalStore();

    public final Map<String, RivalPlayerRecord> players = new ConcurrentHashMap<>();
    /** Pending visible declare requests: key = fromUuid + ">" + toUuid */
    public final Map<String, DeclareRequest> declareRequests = new ConcurrentHashMap<>();
    /** Pending duel challenge requests (persisted); key = request id. */
    public final Map<String, StoredChallengeRequest> challengeRequests = new ConcurrentHashMap<>();
    /** 4.8.3: final standings of each rival season staff ended ({@link #advanceSeason}), oldest first. */
    public final List<SeasonArchive> seasonArchive = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private long lastSaveAt;

    public static RivalStore get() {
        return INSTANCE;
    }

    private RivalStore() {}

    public static Path path() {
        return ConfigPaths.rivalryPath();
    }

    public void markDirty() {
        dirty.set(true);
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public synchronized void load() {
        Path file = path();
        try {
            if (!Files.isRegularFile(file)) {
                players.clear();
                declareRequests.clear();
                challengeRequests.clear();
                seasonArchive.clear();
                dirty.set(false);
                RivalChallengeManager.get().replaceRequestsFromStore(challengeRequests);
                return;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                Persist blob = GSON.fromJson(reader, Persist.class);
                players.clear();
                declareRequests.clear();
                challengeRequests.clear();
                seasonArchive.clear();
                if (blob != null) {
                    if (blob.seasonArchive != null) {
                        for (SeasonArchive a : blob.seasonArchive) {
                            if (a != null) {
                                if (a.standings == null) {
                                    a.standings = new ArrayList<>();
                                }
                                seasonArchive.add(a);
                            }
                        }
                    }
                    if (blob.players != null) {
                        for (Map.Entry<String, RivalPlayerRecord> e : blob.players.entrySet()) {
                            if (e.getKey() != null && e.getValue() != null) {
                                RivalPlayerRecord rec = e.getValue();
                                if (rec.rivals == null) {
                                    // gson may leave null if type erased oddly — recreate
                                }
                                String key = RivalUuid.canonical(e.getKey());
                                players.put(key, normalize(key, rec));
                            }
                        }
                    }
                    if (blob.declareRequests != null) {
                        declareRequests.putAll(blob.declareRequests);
                    }
                    if (blob.challengeRequests != null) {
                        challengeRequests.putAll(blob.challengeRequests);
                    }
                }
                dirty.set(false);
                RivalChallengeManager.get().replaceRequestsFromStore(challengeRequests);
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] RivalStore loaded {} players from {}",
                        AdaptiveDifficultyMod.MOD_ID, players.size(), file);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] RivalStore load failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    public synchronized void save() {
        if (!dirty.get() && Files.isRegularFile(path())) {
            // still allow forced saves via callers that mark dirty
        }
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Persist blob = new Persist();
            blob.players = new ConcurrentHashMap<>(players);
            blob.declareRequests = new ConcurrentHashMap<>(declareRequests);
            blob.challengeRequests = new ConcurrentHashMap<>(challengeRequests);
            blob.seasonArchive = new ArrayList<>(seasonArchive);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(blob, writer);
            }
            dirty.set(false);
            lastSaveAt = System.currentTimeMillis();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] RivalStore save failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    public void saveIfNeeded(long now) {
        if (dirty.get() && now - lastSaveAt >= 60_000L) {
            save();
        }
    }

    public RivalPlayerRecord ensurePlayer(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        return ensurePlayer(player.m_20148_().toString(), player.m_7755_().getString());
    }

    public RivalPlayerRecord ensurePlayer(String uuid, String name) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        long now = System.currentTimeMillis();
        String canon = RivalUuid.canonical(uuid);
        if (canon == null || canon.isBlank()) {
            return null;
        }
        RivalPlayerRecord rec = get(canon);
        if (rec == null) {
            rec = RivalPlayerRecord.create(canon, name, now);
            players.put(canon, rec);
            markDirty();
        } else {
            if (name != null && !name.isBlank() && !name.equals(rec.name)) {
                rec.name = name;
                markDirty();
            }
            rec.lastSeenAt = now;
        }
        return rec;
    }

    public RivalPlayerRecord get(UUID uuid) {
        return uuid == null ? null : get(uuid.toString());
    }

    public RivalPlayerRecord get(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        String canon = RivalUuid.canonical(uuid);
        RivalPlayerRecord rec = canon == null ? null : players.get(canon);
        if (rec != null) {
            return rec;
        }
        rec = players.get(uuid);
        if (rec != null) {
            return rec;
        }
        for (Map.Entry<String, RivalPlayerRecord> e : players.entrySet()) {
            if (e.getKey() != null && RivalUuid.samePlayer(e.getKey(), uuid)) {
                return e.getValue();
            }
        }
        return null;
    }

    public RivalLink getLink(String ownerUuid, String otherUuid) {
        RivalPlayerRecord rec = get(ownerUuid);
        if (rec == null || otherUuid == null || otherUuid.isBlank()) {
            return null;
        }
        RivalLink link = rec.rivals.get(otherUuid);
        if (link != null) {
            return link;
        }
        String canon = RivalUuid.canonical(otherUuid);
        if (canon != null) {
            link = rec.rivals.get(canon);
            if (link != null) {
                return link;
            }
        }
        for (Map.Entry<String, RivalLink> e : rec.rivals.entrySet()) {
            if (e.getKey() != null && RivalUuid.samePlayer(e.getKey(), otherUuid)) {
                return e.getValue();
            }
        }
        return null;
    }

    public RivalLink setLink(String ownerUuid, String otherUuid, RivalLink link) {
        RivalPlayerRecord rec = get(ownerUuid);
        if (rec == null || otherUuid == null || link == null) {
            return null;
        }
        rec.rivals.put(otherUuid, link);
        rec.recalcTotalRp();
        markDirty();
        return link;
    }

    public int addRp(RivalPlayerRecord owner, String rivalUuid, int amount, String reason) {
        if (owner == null || rivalUuid == null || amount == 0) {
            return 0;
        }
        RivalLink link = owner.rivals.get(rivalUuid);
        if (link == null) {
            return 0;
        }
        link.points = Math.max(0.0, link.points + amount);
        link.touch(System.currentTimeMillis());
        owner.recalcTotalRp();
        markDirty();
        return amount;
    }

    /**
     * 4.8.3: staff change of a player's total RP by {@code delta}, through {@link #addRp} on their rivalries (RP lives
     * on each rivalry). A gain goes to the rivalry with the most RP (a mutual one first); a loss comes off the
     * rivalries with the most RP first, and the total never drops below 0. Returns the change made (0 when the player
     * has no rivalry to hold RP).
     */
    public int adjustTotalRp(RivalPlayerRecord owner, int delta, String reason) {
        if (owner == null || delta == 0 || owner.rivals.isEmpty()) {
            return 0;
        }
        List<Map.Entry<String, RivalLink>> links = new ArrayList<>();
        for (Map.Entry<String, RivalLink> e : owner.rivals.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                links.add(e);
            }
        }
        if (links.isEmpty()) {
            return 0;
        }
        links.sort((x, y) -> {
            if (delta > 0 && x.getValue().mutual != y.getValue().mutual) {
                return x.getValue().mutual ? -1 : 1;
            }
            return Double.compare(y.getValue().points, x.getValue().points);
        });
        if (delta > 0) {
            return addRp(owner, links.get(0).getKey(), delta, reason);
        }
        owner.recalcTotalRp();
        double before = owner.totalRp;
        int left = -delta;
        for (Map.Entry<String, RivalLink> e : links) {
            if (left <= 0) {
                break;
            }
            double has = Math.max(0.0, e.getValue().points);
            if (has <= 0.0) {
                continue;
            }
            // addRp stops each rivalry at 0, so taking the rounded-up amount empties a fractional rivalry fully.
            int take = Math.min((int) Math.ceil(has), left);
            addRp(owner, e.getKey(), -take, reason);
            left -= take;
        }
        return (int) Math.round(owner.totalRp - before);
    }

    /** 4.8.3: the RP standings right now (players with RP above 0, highest first), as an archive would keep them. */
    public List<SeasonStanding> currentStandings() {
        List<RivalPlayerRecord> list = new ArrayList<>(players.values());
        list.removeIf(r -> r == null || r.totalRp <= 0.0);
        list.sort((a, b) -> Double.compare(b.totalRp, a.totalRp));
        List<SeasonStanding> out = new ArrayList<>();
        int rank = 1;
        for (RivalPlayerRecord r : list) {
            SeasonStanding st = new SeasonStanding();
            st.rank = rank++;
            st.uuid = r.uuid == null ? "" : r.uuid;
            st.name = r.name == null || r.name.isBlank() ? "Unknown player" : r.name;
            st.rp = Math.round(r.totalRp);
            out.add(st);
        }
        return out;
    }

    /**
     * 4.8.3: end rival season {@code endedSeason} now: keep its final RP standings in {@link #seasonArchive} (season
     * number, date, rank, player, RP), then set every rivalry's RP, and so every player's total RP, to 0. Saves at
     * once. Returns the archive entry.
     */
    public synchronized SeasonArchive advanceSeason(int endedSeason) {
        SeasonArchive a = new SeasonArchive();
        a.season = endedSeason;
        a.endedAt = System.currentTimeMillis();
        a.endedDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        a.standings = currentStandings();
        seasonArchive.add(a);
        for (RivalPlayerRecord rec : players.values()) {
            if (rec == null) {
                continue;
            }
            for (RivalLink link : rec.rivals.values()) {
                if (link != null) {
                    link.points = 0.0;
                }
            }
            rec.recalcTotalRp();
        }
        markDirty();
        save();
        return a;
    }

    public int mutualCount(RivalPlayerRecord record) {
        return record == null ? 0 : record.countMutual();
    }

    public void expireRequests(long now) {
        Iterator<Map.Entry<String, DeclareRequest>> it = declareRequests.entrySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Map.Entry<String, DeclareRequest> e = it.next();
            DeclareRequest req = e.getValue();
            if (req == null || now > req.expiresAt) {
                if (req != null) {
                    clearInviteFlags(req.fromUuid, req.toUuid);
                }
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    public void clearInviteFlags(String fromUuid, String toUuid) {
        RivalLink a = getLink(fromUuid, toUuid);
        RivalLink b = getLink(toUuid, fromUuid);
        if (a != null) {
            a.inviteSent = false;
            a.pendingExpireAt = 0L;
        }
        if (b != null) {
            b.inviteReceived = false;
            b.pendingExpireAt = 0L;
        }
        // Dual Silent Mutual confirm used inviteReceived on both sides.
        if (a != null && b != null
                && a.declaredByMe && a.declaredByThem
                && b.declaredByMe && b.declaredByThem
                && !a.mutual && !b.mutual) {
            a.inviteReceived = false;
            a.inviteSent = false;
            a.acceptedMutualOffer = false;
            a.pendingExpireAt = 0L;
            b.inviteReceived = false;
            b.inviteSent = false;
            b.acceptedMutualOffer = false;
            b.pendingExpireAt = 0L;
        }
    }

    public List<RivalPlayerRecord> topByRp(int limit) {
        return topBy(limit, "rp");
    }

    public List<RivalPlayerRecord> topBy(int limit, String category) {
        String cat = category == null || category.isBlank() ? "rp" : category.trim().toLowerCase();
        List<RivalPlayerRecord> list = new ArrayList<>(players.values());
        list.sort((a, b) -> Double.compare(metric(b, cat), metric(a, cat)));
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    private static double metric(RivalPlayerRecord r, String cat) {
        if (r == null) {
            return 0.0;
        }
        return switch (cat) {
            case "wins", "win" -> r.officialWins;
            case "streak", "beststreak" -> r.bestWinStreak;
            case "damage", "dmg" -> r.careerDamageDealt;
            case "combo", "hit", "hits" -> cat.startsWith("hit") ? r.careerHits : r.careerHighestCombo;
            case "battles", "battle", "challenges" -> r.challengesPlayed;
            default -> r.totalRp;
        };
    }

    private static RivalPlayerRecord normalize(String ownerKey, RivalPlayerRecord rec) {
        if (ownerKey != null && !ownerKey.isBlank()) {
            rec.uuid = ownerKey;
        } else if (rec.uuid != null && !rec.uuid.isBlank()) {
            rec.uuid = Objects.requireNonNullElse(RivalUuid.canonical(rec.uuid), rec.uuid);
        }
        if (rec.uuid == null) {
            rec.uuid = "";
        }
        if (rec.name == null) {
            rec.name = "";
        }
        if (rec.nemesisUuid == null) {
            rec.nemesisUuid = "";
        }
        // Ensure concurrent maps after Gson
        rec.rivals = canonicalizeLinkMap(rec.rivals);
        Map<String, RivalLink> past = new ConcurrentHashMap<>();
        if (rec.pastRivals != null) {
            for (Map.Entry<String, RivalLink> e : rec.pastRivals.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String key = RivalUuid.canonical(e.getKey());
                    if (key != null && !key.isBlank()) {
                        RivalLink link = e.getValue();
                        if (link.uuid == null || link.uuid.isBlank()) {
                            link.uuid = key;
                        } else {
                            link.uuid = Objects.requireNonNullElse(RivalUuid.canonical(link.uuid), link.uuid);
                        }
                        past.putIfAbsent(key, link);
                    }
                }
            }
        }
        rec.pastRivals = past;
        Map<String, Long> cds = new ConcurrentHashMap<>();
        if (rec.surpassCooldown != null) {
            cds.putAll(rec.surpassCooldown);
        }
        rec.surpassCooldown = cds;
        rec.recalcTotalRp();
        return rec;
    }

    private static Map<String, RivalLink> canonicalizeLinkMap(Map<String, RivalLink> in) {
        Map<String, RivalLink> links = new ConcurrentHashMap<>();
        if (in == null) {
            return links;
        }
        for (Map.Entry<String, RivalLink> e : in.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = RivalUuid.canonical(e.getKey());
            if (key == null || key.isBlank()) {
                continue;
            }
            RivalLink link = e.getValue();
            if (link.uuid == null || link.uuid.isBlank()) {
                link.uuid = key;
            } else {
                link.uuid = Objects.requireNonNullElse(RivalUuid.canonical(link.uuid), link.uuid);
            }
            links.putIfAbsent(key, link);
        }
        return links;
    }

    public static final class DeclareRequest {
        public String fromUuid = "";
        public String fromName = "";
        public String toUuid = "";
        public String toName = "";
        public long createdAt;
        public long expiresAt;
    }

    /** Gson-friendly mirror of {@link RivalChallengeManager.ChallengeRequest}. */
    public static final class StoredChallengeRequest {
        public String id = "";
        public String from = "";
        public String to = "";
        public String fromName = "";
        public String toName = "";
        public long createdAt;
        public long expiresAt;
        public long durationMs;
    }

    public void upsertChallengeRequest(RivalChallengeManager.ChallengeRequest req) {
        if (req == null || req.id == null || req.id.isBlank()) {
            return;
        }
        StoredChallengeRequest s = new StoredChallengeRequest();
        s.id = req.id;
        s.from = req.from == null ? "" : req.from.toString();
        s.to = req.to == null ? "" : req.to.toString();
        s.fromName = req.fromName == null ? "" : req.fromName;
        s.toName = req.toName == null ? "" : req.toName;
        s.createdAt = req.createdAt;
        s.expiresAt = req.expiresAt;
        s.durationMs = req.durationMs;
        challengeRequests.put(s.id, s);
        markDirty();
    }

    public void removeChallengeRequest(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (challengeRequests.remove(id) != null) {
            markDirty();
        }
    }

    /** 4.8.3: one ended rival season's final standings (Gson-friendly; missing in data saved before 4.8.3). */
    public static final class SeasonArchive {
        public int season;
        public long endedAt;
        /** ISO date (UTC) the season ended, e.g. 2026-11-26. */
        public String endedDate = "";
        public List<SeasonStanding> standings = new ArrayList<>();
    }

    /** 4.8.3: one row of an archived season: rank, player and RP at the end. */
    public static final class SeasonStanding {
        public int rank;
        public String uuid = "";
        public String name = "";
        public long rp;
    }

    private static final class Persist {
        Map<String, RivalPlayerRecord> players;
        Map<String, DeclareRequest> declareRequests;
        Map<String, StoredChallengeRequest> challengeRequests;
        List<SeasonArchive> seasonArchive;
    }
}
