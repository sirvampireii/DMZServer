package com.dbzlegacy.adaptivedifficulty.data;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.Entity;

/**
 * LM-owned durable player records, one JSON file per player under
 * {@code config/legacymechanics/players/<uuid>.json}. DMZ resets and player-data wipes never
 * touch these files.
 * <p>
 * Each player has one in-memory {@link CompoundTag} root with the same layout the records used
 * to have in Forge persistent data ({@link #DURABLE_KEYS}), so callers keep their NBT code and
 * only swap where the root comes from. Each top-level key is written as an exact SNBT string,
 * which keeps every value's NBT type. A file is rewritten only when its content changed.
 */
public final class LmPlayerStore {
    /** Every durable LM record that lived at the Forge persistent-data root before 4.6.253. */
    public static final Set<String> DURABLE_KEYS = Set.of(
            PlayerDifficultyData.NBT_ROOT,       // tiers, titles, title progress, settings
            "lm_progression",                    // prestige shop wallet + purchases, skills, meditation
            "prestige_total_completed",          // lifetime prestiges
            "prestige_total_staff_override",
            "prestige_need_floor",
            "lm_prestige_held",                  // prestige favor
            "lm_prestige_points",
            "pp_level_breakthroughs",
            "lm_personal_level_cap",
            "lm_personal_breakthroughs",
            "lmNemesisSpawnMode");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<UUID, Entry> LOADED = new ConcurrentHashMap<>();

    private LmPlayerStore() {}

    public static Path dir() {
        return ConfigPaths.dataDir().resolve("players");
    }

    public static Path path(UUID id) {
        return dir().resolve(fileName(id));
    }

    /** {@code <uuid>.json}, lower case. */
    public static String fileName(UUID id) {
        return id.toString().toLowerCase(Locale.ROOT) + ".json";
    }

    /** The player's live record root. Always writable; changes reach disk on the next flush. */
    public static CompoundTag root(Entity player) {
        return root(player == null ? null : player.m_20148_());
    }

    /** A {@code null} id gets a throwaway compound that is never saved. */
    public static CompoundTag root(UUID id) {
        if (id == null) {
            return new CompoundTag();
        }
        return LOADED.computeIfAbsent(id, LmPlayerStore::read).root;
    }

    public static boolean has(Entity player, String key) {
        return root(player).m_128441_(key);
    }

    public static int getInt(Entity player, String key, int fallback) {
        CompoundTag tag = root(player);
        if (!tag.m_128441_(key)) {
            return fallback;
        }
        try {
            return tag.m_128451_(key);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static long getLong(Entity player, String key, long fallback) {
        return getLong(root(player), key, fallback);
    }

    /** A numeric record (or a string holding one) from {@code tag}; {@code fallback} when missing. */
    public static long getLong(CompoundTag tag, String key, long fallback) {
        if (!tag.m_128441_(key)) {
            return fallback;
        }
        try {
            if (tag.m_128425_(key, 99)) { // TAG_ANY_NUMERIC
                return tag.m_128454_(key);
            }
            String v = tag.m_128461_(key);
            if (v != null && !v.isBlank()) {
                return Long.parseLong(v.trim());
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public static void putInt(Entity player, String key, int value) {
        root(player).m_128405_(key, value);
    }

    public static void putLong(Entity player, String key, long value) {
        root(player).m_128356_(key, value);
    }

    /** Write the player's file if it changed since the last write. */
    public static void flush(UUID id) {
        Entry entry = id == null ? null : LOADED.get(id);
        if (entry != null) {
            write(id, entry);
        }
    }

    public static void flushAll() {
        LOADED.forEach(LmPlayerStore::write);
    }

    /** Flush and drop the in-memory copy (logout). */
    public static void unload(UUID id) {
        if (id == null) {
            return;
        }
        flush(id);
        LOADED.remove(id);
    }

    /** Server start: forget every cached root so files are the source of truth. */
    public static void clearCache() {
        LOADED.clear();
    }

    /**
     * Write a migrated record set for a player who has no file yet.
     *
     * @return false when a file already exists (never overwritten) or the write failed
     */
    static boolean writeIfAbsent(UUID id, CompoundTag records) {
        if (Files.exists(path(id))) {
            return false;
        }
        Entry entry = new Entry(records, "");
        if (!write(id, entry)) {
            return false;
        }
        LOADED.remove(id);
        return true;
    }

    private static Entry read(UUID id) {
        Path file = path(id);
        if (!Files.isRegularFile(file)) {
            return new Entry(new CompoundTag(), "");
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Persist blob = GSON.fromJson(reader, Persist.class);
            CompoundTag root = new CompoundTag();
            if (blob != null && blob.records != null) {
                for (Map.Entry<String, String> e : blob.records.entrySet()) {
                    Tag value = parse(e.getValue());
                    if (e.getKey() != null && value != null) {
                        root.m_128365_(e.getKey(), value);
                    }
                }
            }
            return new Entry(root, snapshot(root));
        } catch (Throwable t) {
            // Keep the unreadable file untouched; this session runs on an empty root that is
            // never written back over it.
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] player store read failed for {} ({}); file left as is, records not loaded",
                    AdaptiveDifficultyMod.MOD_ID, id, t.toString());
            Entry broken = new Entry(new CompoundTag(), "");
            broken.readOnly = true;
            return broken;
        }
    }

    private static synchronized boolean write(UUID id, Entry entry) {
        if (entry.readOnly) {
            return false;
        }
        String snap = snapshot(entry.root);
        if (snap.equals(entry.lastWritten)) {
            return true;
        }
        if (entry.root.m_128456_() && entry.lastWritten.isEmpty()) {
            return true; // nothing ever stored: no file for this player
        }
        Path file = path(id);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Persist blob = new Persist();
            blob.uuid = id.toString();
            blob.updatedAt = System.currentTimeMillis();
            blob.records = new LinkedHashMap<>();
            for (String key : entry.root.m_128431_().stream().sorted().toList()) {
                Tag value = entry.root.m_128423_(key);
                if (value != null) {
                    blob.records.put(key, value.toString()); // SNBT; StringTag.getAsString() would drop the quotes
                }
            }
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(blob, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable atomic) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            entry.lastWritten = snap;
            return true;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] player store write failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID, id, t.toString());
            try {
                Files.deleteIfExists(tmp);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    private static String snapshot(CompoundTag root) {
        return root.toString();
    }

    /** SNBT for one value: wrapped in a compound so any tag type parses back exactly. */
    private static Tag parse(String snbt) throws Exception {
        if (snbt == null || snbt.isBlank()) {
            return null;
        }
        return TagParser.m_129359_("{v:" + snbt + "}").m_128423_("v");
    }

    private static final class Entry {
        final CompoundTag root;
        String lastWritten;
        boolean readOnly;

        Entry(CompoundTag root, String lastWritten) {
            this.root = root;
            this.lastWritten = lastWritten;
        }
    }

    private static final class Persist {
        String uuid;
        long updatedAt;
        Map<String, String> records;
    }
}
