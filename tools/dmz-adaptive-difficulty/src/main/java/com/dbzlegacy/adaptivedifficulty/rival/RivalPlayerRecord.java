package com.dbzlegacy.adaptivedifficulty.rival;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent rivalry record for one player. */
public final class RivalPlayerRecord {
    public String uuid = "";
    public String name = "";
    public double totalRp;
    public long lastChallengeEndAt;
    public long lastDeclareAt;
    public long lastSurpassAt;
    public long createdAt;
    public long lastSeenAt;
    public String nemesisUuid = "";

    public Map<String, RivalLink> rivals = new ConcurrentHashMap<>();
    /** Archived links after {@code /lm rival remove} — restored on re-declare (Command Handler). */
    public Map<String, RivalLink> pastRivals = new ConcurrentHashMap<>();
    /** Per-rival surpass cooldown timestamps (ms). */
    public Map<String, Long> surpassCooldown = new ConcurrentHashMap<>();

    public int officialWins;
    public int officialLosses;
    public int officialDraws;
    public int knockouts;
    public int challengesPlayed;
    public int surpassAwards;
    public long presenceMs;
    public int declarationsSent;
    public int declarationsAccepted;
    public int declarationsDeclined;
    public int rivalsRemoved;
    /**
     * When Accept would exceed {@link RivalConstants#MAX_MUTUAL_RIVALS}, stores the
     * uuid of the new Mutual until the player picks which existing Mutual to replace.
     */
    public String pendingMutualAcceptUuid = "";
    public boolean tpMessages = true;
    /** Rival Instinct alerts (per-player). */
    public boolean instinctEnabled = true;

    public int currentWinStreak;
    public int bestWinStreak;
    public int careerHighestCombo;
    public double careerDamageDealt;
    public int careerHits;

    public static RivalPlayerRecord create(String uuid, String name, long now) {
        RivalPlayerRecord r = new RivalPlayerRecord();
        r.uuid = uuid == null ? "" : uuid;
        r.name = name == null ? "" : name;
        r.createdAt = now;
        r.lastSeenAt = now;
        return r;
    }

    public void recalcTotalRp() {
        double total = 0.0;
        for (RivalLink link : rivals.values()) {
            if (link != null) {
                total += Math.max(0.0, link.points);
            }
        }
        totalRp = total;
    }

    public int countMutual() {
        int n = 0;
        for (RivalLink link : rivals.values()) {
            if (link != null && link.mutual) {
                n++;
            }
        }
        return n;
    }

    public RivalLink getOrCreateLink(String otherUuid, String otherName, long now) {
        String key = otherUuid == null ? "" : RivalUuid.canonical(otherUuid);
        if (key == null) {
            key = "";
        }
        RivalLink link = rivals.get(key);
        if (link == null) {
            link = new RivalLink();
            link.uuid = key;
            link.name = otherName == null ? "" : otherName;
            link.createdAt = now;
            link.firstMetAt = now;
            rivals.put(key, link);
        }
        if (otherName != null && !otherName.isBlank()) {
            link.name = otherName;
        }
        link.touch(now);
        return link;
    }
}
