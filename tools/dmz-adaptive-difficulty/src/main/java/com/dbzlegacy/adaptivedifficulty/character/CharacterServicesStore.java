package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-player cooldown timestamps for character services. */
public final class CharacterServicesStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final CharacterServicesStore INSTANCE = new CharacterServicesStore();

    private final Map<String, PlayerRecord> records = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private volatile long lastSaveAt;

    private CharacterServicesStore() {}

    public static CharacterServicesStore get() {
        return INSTANCE;
    }

    public static Path path() {
        return ConfigPaths.dataDir().resolve("character-services-players.json");
    }

    public synchronized void load() {
        records.clear();
        try {
            Path file = path();
            if (!Files.isRegularFile(file)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                Persist blob = GSON.fromJson(reader, Persist.class);
                if (blob != null && blob.records != null) {
                    records.putAll(blob.records);
                }
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] character-services player store load failed: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
        }
        dirty.set(false);
    }

    public synchronized void saveIfNeeded(long now) {
        if (dirty.get() && now - lastSaveAt >= 60_000L) {
            save();
        }
    }

    public synchronized void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Persist blob = new Persist();
            blob.records = new ConcurrentHashMap<>(records);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(blob, writer);
            }
            dirty.set(false);
            lastSaveAt = System.currentTimeMillis();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] character-services player store save failed: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
        }
    }

    public void markDirty() {
        dirty.set(true);
    }

    public PlayerRecord record(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return new PlayerRecord();
        }
        return records.computeIfAbsent(uuid.toLowerCase(), k -> new PlayerRecord());
    }

    /**
     * Staff reset of cooldown timestamps.
     *
     * @param kind {@code race}, {@code class}, {@code reskin}, or {@code all}
     * @return short summary for chat (may include § codes)
     */
    public synchronized String clearCooldowns(String uuid, String kind) {
        if (uuid == null || uuid.isBlank()) {
            return "§7Character: §eno uuid";
        }
        String k = kind == null || kind.isBlank() ? "all" : kind.toLowerCase(Locale.ROOT).trim();
        PlayerRecord rec = records.get(uuid.toLowerCase(Locale.ROOT));
        if (rec == null) {
            return "§7Character: §eno prior usage";
        }
        List<String> cleared = new ArrayList<>();
        boolean race = "all".equals(k) || "race".equals(k) || "racechange".equals(k);
        boolean cls = "all".equals(k) || "class".equals(k) || "classchange".equals(k);
        boolean reskin = "all".equals(k) || "reskin".equals(k) || "skin".equals(k);
        if (!race && !cls && !reskin) {
            return "§cUnknown kind: §f" + kind + " §8(race · class · reskin · all)";
        }
        if (race && rec.lastRaceChangeAt != 0L) {
            cleared.add("race");
        }
        if (cls && rec.lastClassChangeAt != 0L) {
            cleared.add("class");
        }
        if (reskin && rec.lastReskinAt != 0L) {
            cleared.add("reskin");
        }
        if (race) {
            rec.lastRaceChangeAt = 0L;
        }
        if (cls) {
            rec.lastClassChangeAt = 0L;
        }
        if (reskin) {
            rec.lastReskinAt = 0L;
        }
        markDirty();
        save();
        if (cleared.isEmpty()) {
            return "§7Character: §enothing on cooldown §8(" + k + ")";
        }
        return "§7Character: §acleared §f" + String.join(", ", cleared);
    }

    public static final class PlayerRecord {
        public long lastRaceChangeAt;
        public long lastClassChangeAt;
        public long lastReskinAt;
        /**
         * Permanent unlocks from the global head-bone shop (cross-race cosmetics).
         * Not cleared by race change, reskin, or class change.
         */
        public Set<String> unlockedHeadBones = new LinkedHashSet<>();
        /**
         * Last head part the player chose to wear (shop equip / race default / unequip).
         * Re-applied after DMZ form changes that clear {@code activeHeadBone} on the model.
         */
        public String equippedHeadBone = "";
        /**
         * Permanent unlocks from the custom body parts shop (DMZUltra FormParts pieces).
         * Not cleared by race change, reskin, or class change.
         */
        public Set<String> unlockedBodyParts = new LinkedHashSet<>();
        /**
         * Custom body pieces the player chose to wear (ids from the Body Parts Shop).
         * Sent on the KaiPath model preview via {@code playerModel.parts.bodyParts}.
         */
        public List<String> equippedBodyParts = new ArrayList<>();
        /**
         * Part-color overrides picked in the Body Parts Shop (own/trim/horn/light channels,
         * packed RGB 0xRRGGBB). Persisted with the equipped parts; null means no override -
         * the model falls back to the form design constant, or the base-form palette in base
         * form. Not cleared by race change, reskin, or class change.
         */
        public Integer partColorOwn;
        public Integer partColorTrim;
        public Integer partColorHorn;
        public Integer partColorLight;
    }

    private static final class Persist {
        Map<String, PlayerRecord> records;
    }
}
