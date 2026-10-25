package com.dbzlegacy.adaptivedifficulty.data;

import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle;
import com.dbzlegacy.adaptivedifficulty.title.TitleProgress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * V3 player difficulty data.
 * <p>
 * Persistent: highest DMZ level (resets on prestige-up), unlock bits (live-reconciled), titles.<br>
 * Temporary: active tier / active difficulty level (reset on death or when no longer eligible).<br>
 * Ancient Coins are real Lightman's {@code coin_ancient} inventory items;
 * {@code ancientCopper} is only kept for one-time migration from older builds.
 */
public final class PlayerDifficultyData {
    public static final String NBT_ROOT = "dmz_adaptive_difficulty";

    // ── Permanent ──────────────────────────────────────────────────────────
    private long highestDmzLevel;
    private final Set<Integer> unlockedTiers = new LinkedHashSet<>();
    private long ancientCopper;
    private final Set<String> titles = new LinkedHashSet<>();
    private String activeTitle = "";
    /** Secondary title progression (mastery, counters, score milestones). */
    private final TitleProgress titleProgress = new TitleProgress();
    /**
     * Player opt-in: when false, no scaling / kill coins / AI / tier buy for them.
     * Death reset still applies. Default OFF; also forced OFF on each server boot
     * (see {@code DifficultyEvents} first-login reset).
     */
    private boolean personalEnabled = false;
    /** When true, chat notifies on Ancient Coin kill drops. Default off (less spam). */
    private boolean coinDropChat = false;
    /**
     * Last observed DMZ prestige skill. {@code -1} = unset (first sync).
     * Used to detect a prestige-up and restart the level high-water mark.
     */
    private int lastSeenPrestige = -1;

    // ── Temporary (death / character-reset clears) ─────────────────────────
    private int activeTier;
    private long activeDifficultyLevel;
    private TeamMode teamMode = TeamMode.PERSONAL_ONLY;

    // Legacy migration fields (not used by V3 logic after load)
    private long legacyPurchased;
    private long legacyActive;

    public long getHighestDmzLevel() {
        try {
            long max = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.configuredMaxDmzLevel((com.dragonminez.common.stats.StatsData) null);
            if (highestDmzLevel > max) {
                highestDmzLevel = max;
            }
        } catch (Throwable ignored) {
        }
        return highestDmzLevel;
    }

    public void noteDmzLevel(int level) {
        int clamped = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.clampDmzLevel(level);
        if (clamped > highestDmzLevel) {
            highestDmzLevel = clamped;
        }
        // Repair legacy NBT that stored an unclamped / inflated high-water mark.
        try {
            long max = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.configuredMaxDmzLevel((com.dragonminez.common.stats.StatsData) null);
            if (highestDmzLevel > max) {
                highestDmzLevel = max;
            }
        } catch (Throwable ignored) {
        }
    }

    /** Restart level high-water after a prestige-up (level ladder resets). */
    public void resetHighestDmzLevel(int level) {
        int clamped = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.clampDmzLevel(level);
        this.highestDmzLevel = Math.max(0L, clamped);
    }

    public int getLastSeenPrestige() {
        return lastSeenPrestige;
    }

    public void setLastSeenPrestige(int prestige) {
        this.lastSeenPrestige = Math.max(-1, prestige);
    }

    public Set<Integer> getUnlockedTiers() {
        return new LinkedHashSet<>(unlockedTiers);
    }

    public boolean hasUnlockedTier(int tierId) {
        return tierId > 0 && unlockedTiers.contains(tierId);
    }

    /** @return true if newly unlocked */
    public boolean unlockTier(int tierId) {
        if (UnlockTier.byId(tierId) == null) {
            return false;
        }
        return unlockedTiers.add(tierId);
    }

    /** @return true if the tier was present and removed */
    public boolean revokeTier(int tierId) {
        return tierId > 0 && unlockedTiers.remove(tierId);
    }

    public int getActiveTier() {
        return Math.max(0, activeTier);
    }

    public void setActiveTier(int tierId) {
        if (tierId <= 0) {
            this.activeTier = 0;
            return;
        }
        this.activeTier = UnlockTier.byId(tierId) == null ? 0 : tierId;
    }

    public long getActiveDifficultyLevel() {
        return Math.max(0L, activeDifficultyLevel);
    }

    public void setActiveDifficultyLevel(long level) {
        this.activeDifficultyLevel = Math.max(0L, level);
    }

    public boolean isPersonalEnabled() {
        return personalEnabled;
    }

    public void setPersonalEnabled(boolean enabled) {
        this.personalEnabled = enabled;
    }

    public boolean togglePersonalEnabled() {
        this.personalEnabled = !this.personalEnabled;
        return this.personalEnabled;
    }

    public boolean isCoinDropChat() {
        return coinDropChat;
    }

    public void setCoinDropChat(boolean enabled) {
        this.coinDropChat = enabled;
    }

    public boolean toggleCoinDropChat() {
        this.coinDropChat = !this.coinDropChat;
        return this.coinDropChat;
    }

    /**
     * Death / character-reset: clear temporary activation only.
     * Keeps prestige, unlock tiers, toggles, and Ancient Coin wallet.
     */
    public void resetTemporary() {
        this.activeTier = 0;
        this.activeDifficultyLevel = 0L;
        this.teamMode = TeamMode.PERSONAL_ONLY;
    }

    public long getAncientCopper() {
        return Math.max(0L, ancientCopper);
    }

    public void setAncientCopper(long amount) {
        this.ancientCopper = Math.max(0L, amount);
    }

    public boolean spendAncientCopper(long cost) {
        if (cost <= 0L) {
            return true;
        }
        if (ancientCopper < cost) {
            return false;
        }
        ancientCopper -= cost;
        return true;
    }

    public void addAncientCopper(long amount) {
        if (amount > 0L) {
            long base = Math.max(0L, ancientCopper);
            long next = base + amount;
            ancientCopper = next < base ? Long.MAX_VALUE / 4L : next;
        }
    }

    /** Persisted team scaling mode (rival mutual teammates). */
    public TeamMode getTeamMode() {
        return teamMode == null ? TeamMode.PERSONAL_ONLY : teamMode;
    }

    public void setTeamMode(TeamMode teamMode) {
        this.teamMode = teamMode == null ? TeamMode.PERSONAL_ONLY : teamMode;
    }

    public List<String> getTitles() {
        return new ArrayList<>(titles);
    }

    public boolean hasTitle(String titleId) {
        if (titleId == null || titleId.isBlank()) {
            return false;
        }
        return titles.contains(normalizeId(titleId));
    }

    public boolean unlockTitle(String title) {
        DifficultyTitle known = DifficultyTitle.byId(title);
        String id = known != null ? known.id : normalizeId(title);
        if (id == null || id.isBlank()) {
            return false;
        }
        return titles.add(id);
    }

    public String getActiveTitle() {
        return activeTitle == null ? "" : activeTitle;
    }

    public TitleProgress titleProgress() {
        return titleProgress;
    }

    public void setActiveTitle(String titleId) {
        if (titleId == null || titleId.isBlank()) {
            this.activeTitle = "";
            return;
        }
        DifficultyTitle known = DifficultyTitle.byId(titleId);
        this.activeTitle = known != null ? known.id : normalizeId(titleId);
    }

    public boolean normalizeTitles() {
        boolean dirty = false;
        List<String> snapshot = new ArrayList<>(titles);
        titles.clear();
        for (String raw : snapshot) {
            DifficultyTitle known = DifficultyTitle.byId(raw);
            String id = known != null ? known.id : normalizeId(raw);
            if (id == null || id.isBlank()) {
                dirty = true;
                continue;
            }
            if (known == null && !id.equals(raw)) {
                dirty = true;
            } else if (known != null && !known.id.equals(raw)) {
                dirty = true;
            }
            titles.add(id);
        }
        if (snapshot.size() != titles.size()) {
            dirty = true;
        }
        if (activeTitle != null && !activeTitle.isBlank()) {
            DifficultyTitle known = DifficultyTitle.byId(activeTitle);
            String id = known != null ? known.id : normalizeId(activeTitle);
            if (id == null || id.isBlank() || !titles.contains(id)) {
                activeTitle = "";
                dirty = true;
            } else if (!id.equals(activeTitle)) {
                activeTitle = id;
                dirty = true;
            }
        } else if (activeTitle == null) {
            activeTitle = "";
            dirty = true;
        }
        return dirty;
    }

    public CompoundTag save() {
        normalizeTitles();
        CompoundTag tag = new CompoundTag();
        tag.m_128356_("highestDmzLevel", highestDmzLevel);
        tag.m_128405_("activeTier", activeTier); // putInt
        tag.m_128356_("activeLevel", activeDifficultyLevel);
        tag.m_128356_("ancientCopper", ancientCopper);
        tag.m_128359_("teamMode", getTeamMode().name());
        ListTag tiers = new ListTag();
        for (Integer id : unlockedTiers) {
            CompoundTag t = new CompoundTag();
            t.m_128405_("id", id);
            tiers.add(t);
        }
        tag.m_128365_("unlockedTiers", tiers);
        ListTag list = new ListTag();
        for (String t : titles) {
            list.add(StringTag.m_129297_(t));
        }
        tag.m_128365_("titles", list);
        tag.m_128359_("activeTitle", getActiveTitle());
        tag.m_128365_("titleProgress", titleProgress.save());
        tag.m_128379_("personalEnabled", personalEnabled);
        tag.m_128379_("coinDropChat", coinDropChat);
        if (lastSeenPrestige >= 0) {
            tag.m_128405_("lastSeenPrestige", lastSeenPrestige);
            tag.m_128379_("lastSeenPrestigeSet", true);
        } else {
            tag.m_128379_("lastSeenPrestigeSet", false);
        }
        // Keep legacy keys written as 0 so old tools don't explode on read.
        tag.m_128356_("purchased", 0L);
        tag.m_128356_("active", activeDifficultyLevel);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        highestDmzLevel = Math.max(0L, tag.m_128454_("highestDmzLevel"));
        // Clamp legacy inflated high-water (pre-1.0.35 unclamped getLevel reads).
        try {
            long max = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.configuredMaxDmzLevel((com.dragonminez.common.stats.StatsData) null);
            if (highestDmzLevel > max) {
                highestDmzLevel = max;
            }
        } catch (Throwable ignored) {
        }
        activeTier = Math.max(0, tag.m_128451_("activeTier")); // getInt
        activeDifficultyLevel = Math.max(0L, tag.m_128454_("activeLevel"));
        ancientCopper = Math.max(0L, tag.m_128454_("ancientCopper"));
        teamMode = TeamMode.fromString(tag.m_128461_("teamMode"));
        // Missing keys → defaults (OFF for personal, OFF for coin chat).
        personalEnabled = tag.m_128441_("personalEnabled") && tag.m_128471_("personalEnabled");
        coinDropChat = tag.m_128441_("coinDropChat") && tag.m_128471_("coinDropChat");
        if (tag.m_128441_("lastSeenPrestigeSet") && tag.m_128471_("lastSeenPrestigeSet")
                && tag.m_128441_("lastSeenPrestige")) {
            lastSeenPrestige = Math.max(0, tag.m_128451_("lastSeenPrestige"));
        } else {
            lastSeenPrestige = -1;
        }
        unlockedTiers.clear();
        if (tag.m_128425_("unlockedTiers", 10)) { // TAG_COMPOUND=10
            ListTag tiers = tag.m_128437_("unlockedTiers", 10);
            for (int i = 0; i < tiers.size(); i++) {
                CompoundTag t = tiers.m_128728_(i); // getCompound
                int id = t.m_128451_("id");
                if (UnlockTier.byId(id) != null) {
                    unlockedTiers.add(id);
                }
            }
        }
        titles.clear();
        // "titles" is a list of strings: contains() needs TAG_LIST (9). Checking 8 (TAG_STRING)
        // never matched, so every load dropped all titles (4.6.238 fix).
        if (tag.m_128425_("titles", 9)) {
            ListTag list = tag.m_128437_("titles", 8);
            for (int i = 0; i < list.size(); i++) {
                String s = list.m_128778_(i);
                if (s != null && !s.isBlank()) {
                    titles.add(s);
                }
            }
        }
        activeTitle = tag.m_128461_("activeTitle");
        if (activeTitle == null) {
            activeTitle = "";
        }
        if (tag.m_128441_("titleProgress")) {
            titleProgress.load(tag.m_128469_("titleProgress"));
        }

        // Pre-V3 purchased/active were difficulty POINTS, not unlock tiers.
        // Never map those thresholds onto permanent unlocks (that granted T7 for free).
        // Soft-migrate a copper token only; player must re-qualify + buy.
        legacyPurchased = Math.max(0L, tag.m_128454_("purchased"));
        legacyActive = Math.max(0L, tag.m_128454_("active"));
        if (activeTier <= 0 && (legacyActive > 0L || legacyPurchased > 0L)) {
            if (ancientCopper <= 0L && legacyPurchased > 0L) {
                ancientCopper = Math.min(1_000L, legacyPurchased);
            }
            activeTier = 0;
            activeDifficultyLevel = 0L;
        }
        if (activeTier > 0 && UnlockTier.byId(activeTier) == null) {
            activeTier = 0;
            activeDifficultyLevel = 0L;
        }
        normalizeTitles();
    }

    public static PlayerDifficultyData fromPlayerNbt(CompoundTag persistent) {
        PlayerDifficultyData data = new PlayerDifficultyData();
        if (persistent != null && persistent.m_128441_(NBT_ROOT)) {
            data.load(persistent.m_128469_(NBT_ROOT));
        }
        return data;
    }

    public void writeToPlayerNbt(CompoundTag persistent) {
        if (persistent == null) {
            return;
        }
        persistent.m_128365_(NBT_ROOT, save());
    }

    private static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
