package com.dbzlegacy.adaptivedifficulty.title;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * Persistent title-system progress: kill counters, mastery ranks, score milestones,
 * mutation collection, and sense-chat toggle. Survives death / tier reset.
 */
public final class TitleProgress {
    public static final int MASTERY_MAX = 5;

    // Kill counters
    private long elitesKilled;
    private long elitesT4;
    private long elitesT5;
    private long elitesT7;
    private long elitesNoDeath;
    private long elitesPersonal;
    private long bossesKilled;
    private long bossesT6;
    private long bossesT7;
    private long bossesNoDeath;
    private long bossesSolo;
    private long killsT6;
    private long killsT7;
    private long killsT6NoDeath;
    private long killsNoDeath;
    private long killsPersonal;
    private long mutatedKilled;
    private long copperFromKills;
    private long playSecondsWithTier;
    private long noDeathStreak;

    private final Set<String> mutationsSeen = new LinkedHashSet<>();
    private final Map<String, Integer> mastery = new LinkedHashMap<>();
    private final Set<Integer> claimedScoreMilestones = new LinkedHashSet<>();
    private boolean titleSenseChat = true;
    /** Cached score; recomputed when dirty. */
    private int cachedScore = -1;

    public long elitesKilled() { return elitesKilled; }
    public long elitesT4() { return elitesT4; }
    public long elitesT5() { return elitesT5; }
    public long elitesT7() { return elitesT7; }
    public long elitesNoDeath() { return elitesNoDeath; }
    public long elitesPersonal() { return elitesPersonal; }
    public long bossesKilled() { return bossesKilled; }
    public long bossesT6() { return bossesT6; }
    public long bossesT7() { return bossesT7; }
    public long bossesNoDeath() { return bossesNoDeath; }
    public long bossesSolo() { return bossesSolo; }
    public long killsT6() { return killsT6; }
    public long killsT7() { return killsT7; }
    public long killsT6NoDeath() { return killsT6NoDeath; }
    public long killsNoDeath() { return killsNoDeath; }
    public long killsPersonal() { return killsPersonal; }
    public long mutatedKilled() { return mutatedKilled; }
    public long copperFromKills() { return copperFromKills; }
    public long playSecondsWithTier() { return playSecondsWithTier; }
    public long noDeathStreak() { return noDeathStreak; }

    public Set<String> mutationsSeen() {
        return Collections.unmodifiableSet(mutationsSeen);
    }

    public boolean titleSenseChat() {
        return titleSenseChat;
    }

    public void setTitleSenseChat(boolean on) {
        this.titleSenseChat = on;
    }

    public int masteryLevel(String titleId) {
        if (titleId == null || titleId.isBlank()) {
            return 0;
        }
        return Math.max(0, Math.min(MASTERY_MAX, mastery.getOrDefault(normalize(titleId), 0)));
    }

    public boolean setMasteryLevel(String titleId, int level) {
        String id = normalize(titleId);
        if (id.isEmpty()) {
            return false;
        }
        int clamped = Math.max(0, Math.min(MASTERY_MAX, level));
        Integer prev = mastery.put(id, clamped);
        cachedScore = -1;
        return prev == null || prev != clamped;
    }

    public Map<String, Integer> masterySnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(mastery));
    }

    public boolean hasClaimedMilestone(int score) {
        return claimedScoreMilestones.contains(score);
    }

    public boolean claimMilestone(int score) {
        cachedScore = -1;
        return claimedScoreMilestones.add(score);
    }

    public Set<Integer> claimedMilestones() {
        return Collections.unmodifiableSet(claimedScoreMilestones);
    }

    public void invalidateScore() {
        cachedScore = -1;
    }

    public int cachedScoreOrNeg() {
        return cachedScore;
    }

    public void setCachedScore(int score) {
        this.cachedScore = Math.max(0, score);
    }

    public void noteKill(
            int activeTier,
            boolean elite,
            boolean boss,
            boolean mutated,
            String mutationId,
            boolean personalOn,
            boolean solo,
            long copperValue
    ) {
        if (personalOn) {
            killsPersonal++;
        }
        if (activeTier >= 6) {
            killsT6++;
            killsT6NoDeath++;
        }
        if (activeTier >= 7) {
            killsT7++;
        }
        killsNoDeath++;
        noDeathStreak++;
        if (copperValue > 0L) {
            copperFromKills = Math.min(Long.MAX_VALUE / 4L, copperFromKills + copperValue);
        }
        if (mutated) {
            mutatedKilled++;
            if (mutationId != null && !mutationId.isBlank()) {
                mutationsSeen.add(mutationId.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (elite) {
            elitesKilled++;
            elitesNoDeath++;
            if (personalOn) {
                elitesPersonal++;
            }
            if (activeTier >= 4) {
                elitesT4++;
            }
            if (activeTier >= 5) {
                elitesT5++;
            }
            if (activeTier >= 7) {
                elitesT7++;
            }
        }
        if (boss) {
            bossesKilled++;
            bossesNoDeath++;
            if (solo) {
                bossesSolo++;
            }
            if (activeTier >= 6) {
                bossesT6++;
            }
            if (activeTier >= 7) {
                bossesT7++;
            }
        }
        cachedScore = -1;
    }

    public void noteDeath() {
        elitesNoDeath = 0;
        bossesNoDeath = 0;
        killsNoDeath = 0;
        killsT6NoDeath = 0;
        noDeathStreak = 0;
        cachedScore = -1;
    }

    public void addPlaySeconds(long seconds) {
        if (seconds <= 0L) {
            return;
        }
        playSecondsWithTier = Math.min(Long.MAX_VALUE / 4L, playSecondsWithTier + seconds);
        cachedScore = -1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.m_128356_("elitesKilled", elitesKilled);
        tag.m_128356_("elitesT4", elitesT4);
        tag.m_128356_("elitesT5", elitesT5);
        tag.m_128356_("elitesT7", elitesT7);
        tag.m_128356_("elitesNoDeath", elitesNoDeath);
        tag.m_128356_("elitesPersonal", elitesPersonal);
        tag.m_128356_("bossesKilled", bossesKilled);
        tag.m_128356_("bossesT6", bossesT6);
        tag.m_128356_("bossesT7", bossesT7);
        tag.m_128356_("bossesNoDeath", bossesNoDeath);
        tag.m_128356_("bossesSolo", bossesSolo);
        tag.m_128356_("killsT6", killsT6);
        tag.m_128356_("killsT7", killsT7);
        tag.m_128356_("killsT6NoDeath", killsT6NoDeath);
        tag.m_128356_("killsNoDeath", killsNoDeath);
        tag.m_128356_("killsPersonal", killsPersonal);
        tag.m_128356_("mutatedKilled", mutatedKilled);
        tag.m_128356_("copperFromKills", copperFromKills);
        tag.m_128356_("playSecondsWithTier", playSecondsWithTier);
        tag.m_128356_("noDeathStreak", noDeathStreak);
        tag.m_128379_("titleSenseChat", titleSenseChat);

        ListTag mutList = new ListTag();
        for (String m : mutationsSeen) {
            mutList.add(StringTag.m_129297_(m));
        }
        tag.m_128365_("mutationsSeen", mutList);

        CompoundTag masteryTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : mastery.entrySet()) {
            masteryTag.m_128405_(e.getKey(), e.getValue());
        }
        tag.m_128365_("mastery", masteryTag);

        ListTag milestones = new ListTag();
        for (Integer score : claimedScoreMilestones) {
            CompoundTag c = new CompoundTag();
            c.m_128405_("s", score);
            milestones.add(c);
        }
        tag.m_128365_("scoreMilestones", milestones);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        elitesKilled = Math.max(0L, tag.m_128454_("elitesKilled"));
        elitesT4 = Math.max(0L, tag.m_128454_("elitesT4"));
        elitesT5 = Math.max(0L, tag.m_128454_("elitesT5"));
        elitesT7 = Math.max(0L, tag.m_128454_("elitesT7"));
        elitesNoDeath = Math.max(0L, tag.m_128454_("elitesNoDeath"));
        elitesPersonal = Math.max(0L, tag.m_128454_("elitesPersonal"));
        bossesKilled = Math.max(0L, tag.m_128454_("bossesKilled"));
        bossesT6 = Math.max(0L, tag.m_128454_("bossesT6"));
        bossesT7 = Math.max(0L, tag.m_128454_("bossesT7"));
        bossesNoDeath = Math.max(0L, tag.m_128454_("bossesNoDeath"));
        bossesSolo = Math.max(0L, tag.m_128454_("bossesSolo"));
        killsT6 = Math.max(0L, tag.m_128454_("killsT6"));
        killsT7 = Math.max(0L, tag.m_128454_("killsT7"));
        killsT6NoDeath = Math.max(0L, tag.m_128454_("killsT6NoDeath"));
        killsNoDeath = Math.max(0L, tag.m_128454_("killsNoDeath"));
        killsPersonal = Math.max(0L, tag.m_128454_("killsPersonal"));
        mutatedKilled = Math.max(0L, tag.m_128454_("mutatedKilled"));
        copperFromKills = Math.max(0L, tag.m_128454_("copperFromKills"));
        playSecondsWithTier = Math.max(0L, tag.m_128454_("playSecondsWithTier"));
        noDeathStreak = Math.max(0L, tag.m_128454_("noDeathStreak"));
        titleSenseChat = !tag.m_128441_("titleSenseChat") || tag.m_128471_("titleSenseChat");

        mutationsSeen.clear();
        // Lists need TAG_LIST (9) in contains(); the element type stays in getList (4.6.238 fix).
        if (tag.m_128425_("mutationsSeen", 9)) {
            ListTag list = tag.m_128437_("mutationsSeen", 8);
            for (int i = 0; i < list.size(); i++) {
                String s = list.m_128778_(i);
                if (s != null && !s.isBlank()) {
                    mutationsSeen.add(s.trim().toUpperCase(Locale.ROOT));
                }
            }
        }

        mastery.clear();
        if (tag.m_128441_("mastery")) {
            CompoundTag masteryTag = tag.m_128469_("mastery");
            for (String key : masteryTag.m_128431_()) {
                mastery.put(normalize(key), Math.max(0, Math.min(MASTERY_MAX, masteryTag.m_128451_(key))));
            }
        }

        claimedScoreMilestones.clear();
        if (tag.m_128425_("scoreMilestones", 9)) {
            ListTag list = tag.m_128437_("scoreMilestones", 10);
            for (int i = 0; i < list.size(); i++) {
                claimedScoreMilestones.add(Math.max(0, list.m_128728_(i).m_128451_("s")));
            }
        }
        cachedScore = -1;
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
