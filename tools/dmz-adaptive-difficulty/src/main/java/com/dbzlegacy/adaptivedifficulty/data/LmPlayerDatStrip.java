package com.dbzlegacy.adaptivedifficulty.data;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayOutputStream;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * One-time strip of the frozen LM keys from the {@code ForgeData} root of every saved player
 * file (4.6.259). Since 4.6.253 the LM store files ({@link LmPlayerStore}) are the source of
 * truth and these keys were only a rollback fallback; after this pass the fallback is the
 * per-player backup under {@link #backupDir()}.
 *
 * <p>Runs on server start, before anyone can join, while {@link #markerPath()} is absent. A
 * player is stripped only when their store file exists; a player with LM keys and no store file
 * is skipped untouched (the .dat is their only copy). Re-running strips only what is still there
 * and never overwrites a backup. Store files are never modified.
 */
public final class LmPlayerDatStrip {
    /** The 11 durable keys plus the 3 extra keys the old death copy carried. Root level only. */
    public static final Set<String> FROZEN_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            PlayerDifficultyData.NBT_ROOT,
            "lm_progression",
            "prestige_total_completed",
            "prestige_total_staff_override",
            "prestige_need_floor",
            "lm_prestige_held",
            "lm_prestige_points",
            "pp_level_breakthroughs",
            "lm_personal_level_cap",
            "lm_personal_breakthroughs",
            "lmNemesisSpawnMode",
            "lm_prestige_confirm_until",
            "lm_cnpc_player_migrated",
            "lm_shadow_dummy_cd_until")));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Kind { STRIPPED, SKIPPED_NO_STORE, NO_DATA }

    private LmPlayerDatStrip() {}

    public static Path markerPath() {
        return ConfigPaths.dataDir().resolve("player-data-nbt-strip.json");
    }

    /** {@code config/legacymechanics/player-data-nbt-strip-backup/<uuid>.dat} - the stripped keys. */
    public static Path backupDir() {
        return ConfigPaths.dataDir().resolve("player-data-nbt-strip-backup");
    }

    /** Frozen keys present at the ForgeData root of a saved player compound, in list order. */
    public static List<String> frozenKeysIn(CompoundTag root) {
        List<String> out = new ArrayList<>();
        CompoundTag forge = forgeData(root);
        if (forge != null) {
            for (String key : FROZEN_KEYS) {
                if (forge.m_128441_(key)) {
                    out.add(key);
                }
            }
        }
        return out;
    }

    /**
     * The strip rule on one saved player compound. With no store file the compound is left
     * untouched; otherwise only the frozen root keys are removed from ForgeData.
     */
    public static Kind strip(CompoundTag root, boolean storeExists) {
        List<String> keys = frozenKeysIn(root);
        if (keys.isEmpty()) {
            return Kind.NO_DATA;
        }
        if (!storeExists) {
            return Kind.SKIPPED_NO_STORE;
        }
        CompoundTag forge = forgeData(root);
        for (String key : keys) {
            forge.m_128473_(key);
        }
        return Kind.STRIPPED;
    }

    /** The frozen keys as their own compound (deep copies), the backup body. */
    static CompoundTag backupOf(CompoundTag root) {
        CompoundTag out = new CompoundTag();
        CompoundTag forge = forgeData(root);
        if (forge != null) {
            for (String key : frozenKeysIn(root)) {
                out.m_128365_(key, forge.m_128423_(key).m_6426_());
            }
        }
        return out;
    }

    private static CompoundTag forgeData(CompoundTag root) {
        return root != null && root.m_128425_("ForgeData", 10) ? root.m_128469_("ForgeData") : null;
    }

    public static void runIfNeeded(MinecraftServer server) {
        Path marker = markerPath();
        if (Files.exists(marker)) {
            AdaptiveDifficultyMod.LOGGER.info("[{}] player .dat strip already done ({}); skipped",
                    AdaptiveDifficultyMod.MOD_ID, marker.getFileName());
            return;
        }
        List<String> broken = selfTest();
        if (!broken.isEmpty()) {
            // Never touch a .dat with a strip rule that fails its own test; no marker, so it retries.
            AdaptiveDifficultyMod.LOGGER.error("[{}] player .dat strip self-test FAILED, pass not run: {}",
                    AdaptiveDifficultyMod.MOD_ID, broken);
            return;
        }
        Path dir;
        try {
            dir = server.m_129843_(LevelResource.f_78176_); // getWorldPath(PLAYER_DATA_DIR)
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] player .dat strip: playerdata folder not resolved ({}); will retry next boot",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
            return;
        }
        Result result = new Result();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.dat")) {
                for (Path file : files) {
                    stripFile(file, null, backupDir(), result);
                }
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.error(
                        "[{}] player .dat strip: could not list {} ({}); will retry next boot",
                        AdaptiveDifficultyMod.MOD_ID, dir, t.toString());
                return;
            }
        }
        writeMarker(marker, dir, result);
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] player .dat strip done: {} scanned, {} stripped ({} keys), {} skipped, {} with no LM keys; "
                        + "backups in {}",
                AdaptiveDifficultyMod.MOD_ID, result.scanned, result.stripped, result.keysRemoved,
                result.skipped, result.noData, backupDir());
    }

    /**
     * Strip one saved player file. {@code storeDir} null means the live store
     * ({@link LmPlayerStore#path}); the self-test passes its own folders.
     */
    static void stripFile(Path file, Path storeDir, Path backups, Result result) {
        String name = file.getFileName().toString();
        UUID id;
        try {
            id = UUID.fromString(name.substring(0, name.length() - ".dat".length()));
        } catch (Throwable notUuid) {
            result.skipped++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] player .dat strip: {} is not a player file; skipped",
                    AdaptiveDifficultyMod.MOD_ID, name);
            return;
        }
        result.scanned++;
        CompoundTag root;
        try {
            root = NbtIo.m_128937_(file.toFile()); // readCompressed, same IO as PlayerDataStorage
        } catch (Throwable t) {
            result.skipped++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] player .dat strip: {} unreadable ({}); left untouched",
                    AdaptiveDifficultyMod.MOD_ID, name, t.toString());
            return;
        }
        Path store = storeDir == null ? LmPlayerStore.path(id) : storeDir.resolve(LmPlayerStore.fileName(id));
        boolean storeExists = Files.isRegularFile(store);
        List<String> keys = frozenKeysIn(root);
        if (keys.isEmpty()) {
            result.noData++;
            return;
        }
        if (!storeExists) {
            result.skipped++;
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] player .dat strip: {} has {} LM key(s) but no LM store file; skipped untouched "
                            + "(the .dat is their only copy)",
                    AdaptiveDifficultyMod.MOD_ID, id, keys.size());
            return;
        }
        try {
            Path backup = backups.resolve(id + ".dat");
            if (!Files.exists(backup)) {
                // A backup from an interrupted earlier run is complete (atomic move) and is kept.
                Files.createDirectories(backups);
                Path tmp = backups.resolve(id + ".dat.tmp");
                NbtIo.m_128944_(backupOf(root), tmp.toFile()); // writeCompressed
                Files.move(tmp, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            strip(root, true);
            Path tmp = file.resolveSibling(name + ".lmstrip.tmp");
            NbtIo.m_128944_(root, tmp.toFile());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable t) {
            result.skipped++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] player .dat strip: {} not written ({}); left as it was",
                    AdaptiveDifficultyMod.MOD_ID, id, t.toString());
            return;
        }
        result.stripped++;
        result.keysRemoved += keys.size();
        AdaptiveDifficultyMod.LOGGER.info("[{}] player .dat strip: {} -> {} key(s) removed {}",
                AdaptiveDifficultyMod.MOD_ID, id, keys.size(), keys);
    }

    private static void writeMarker(Path marker, Path dir, Result result) {
        try {
            Files.createDirectories(marker.getParent());
            Marker m = new Marker();
            m.completed = true;
            m.version = AdaptiveDifficultyMod.VERSION;
            m.completedAt = System.currentTimeMillis();
            m.playerDataDir = dir.toString();
            m.backupDir = backupDir().toString();
            m.scanned = result.scanned;
            m.stripped = result.stripped;
            m.keysRemoved = result.keysRemoved;
            m.skipped = result.skipped;
            m.noData = result.noData;
            try (Writer writer = Files.newBufferedWriter(marker)) {
                GSON.toJson(m, writer);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] player .dat strip: marker write failed ({}); the pass runs again next boot and only "
                            + "strips what is still there",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    // ── Self-test (runs before the pass; a failure stops the pass) ────

    /** Empty when the strip rule and the file round-trip behave; otherwise what broke. */
    public static List<String> selfTest() {
        List<String> broken = new ArrayList<>();
        try {
            if (!FROZEN_KEYS.containsAll(LmPlayerStore.DURABLE_KEYS) || FROZEN_KEYS.size() != 14) {
                broken.add("frozen key list must be the 11 durable keys + 3 extras");
            }
            if (FROZEN_KEYS.contains(DeathTpPenalty.KEY) || FROZEN_KEYS.contains(DeathTpPenalty.FRAC_KEY)) {
                broken.add("death penalty keys must never be stripped");
            }
            CompoundTag root = sampleRoot(true);
            CompoundTag before = unrelatedOnly(root);
            CompoundTag untouched = root.m_6426_();
            if (strip(root.m_6426_(), false) != Kind.SKIPPED_NO_STORE) {
                broken.add("no store file must skip");
            }
            CompoundTag skipCopy = root.m_6426_();
            strip(skipCopy, false);
            if (!skipCopy.equals(untouched)) {
                broken.add("a skipped player must be left untouched");
            }
            if (backupOf(root).m_128440_() != 14) {
                broken.add("backup should hold all 14 frozen keys, has " + backupOf(root).m_128440_());
            }
            if (strip(root, true) != Kind.STRIPPED || !frozenKeysIn(root).isEmpty()) {
                broken.add("frozen keys must come out when the store file exists");
            }
            if (!sameBytes(before, root)) {
                broken.add("unrelated tags changed in the strip");
            }
            if (strip(root, true) != Kind.NO_DATA) {
                broken.add("a second pass must find nothing to strip");
            }
            if (strip(sampleRoot(false), true) != Kind.NO_DATA) {
                broken.add("a player with no LM keys must be a no-op");
            }
            fileRoundTrip(broken);
        } catch (Throwable t) {
            broken.add("self-test threw " + t);
        }
        return broken;
    }

    /** Real gzip NBT files in a temp folder: strip, backup, skip without a store, second run. */
    private static void fileRoundTrip(List<String> broken) throws Exception {
        Path tmp = Files.createTempDirectory("lm-dat-strip-test");
        try {
            Path players = Files.createDirectories(tmp.resolve("playerdata"));
            Path stores = Files.createDirectories(tmp.resolve("players"));
            Path backups = tmp.resolve("backup");
            UUID withStore = UUID.randomUUID();
            UUID noStore = UUID.randomUUID();
            CompoundTag sample = sampleRoot(true);
            NbtIo.m_128944_(sample, players.resolve(withStore + ".dat").toFile());
            NbtIo.m_128944_(sample, players.resolve(noStore + ".dat").toFile());
            Files.writeString(stores.resolve(LmPlayerStore.fileName(withStore)), "{}");
            byte[] noStoreBefore = Files.readAllBytes(players.resolve(noStore + ".dat"));
            Result r = new Result();
            stripFile(players.resolve(withStore + ".dat"), stores, backups, r);
            stripFile(players.resolve(noStore + ".dat"), stores, backups, r);
            CompoundTag after = NbtIo.m_128937_(players.resolve(withStore + ".dat").toFile());
            if (r.stripped != 1 || r.skipped != 1 || !frozenKeysIn(after).isEmpty()) {
                broken.add("file pass: want 1 stripped + 1 skipped, got " + r.stripped + " / " + r.skipped);
            }
            if (!sameBytes(unrelatedOnly(sample), after)) {
                broken.add("file pass: unrelated tags did not survive the read / strip / write");
            }
            CompoundTag backup = NbtIo.m_128937_(backups.resolve(withStore + ".dat").toFile());
            if (backup.m_128440_() != 14 || !backup.equals(backupOf(sample))) {
                broken.add("file pass: backup does not hold the stripped keys");
            }
            if (!Arrays.equals(noStoreBefore, Files.readAllBytes(players.resolve(noStore + ".dat")))) {
                broken.add("file pass: player with no store file was modified");
            }
            Result again = new Result();
            stripFile(players.resolve(withStore + ".dat"), stores, backups, again);
            if (again.noData != 1 || again.stripped != 0) {
                broken.add("file pass: second run should find nothing to strip");
            }
        } finally {
            deleteTree(tmp);
        }
    }

    /** A saved-player-like compound: frozen keys (optional) plus unrelated root and ForgeData tags. */
    private static CompoundTag sampleRoot(boolean withFrozen) {
        CompoundTag root = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(StringTag.m_129297_("probe"));
        root.m_128365_("Pos", pos);
        root.m_128405_("DataVersion", 3465);
        CompoundTag forge = new CompoundTag();
        CompoundTag persisted = new CompoundTag();
        persisted.m_128359_("keep", "PlayerPersisted stays");
        forge.m_128365_("PlayerPersisted", persisted);
        forge.m_128365_("dmzTransformMobEffects", new ListTag());
        forge.m_128359_("noea_marker", "Noea stays");
        forge.m_128356_(DeathTpPenalty.KEY, 123456789L);
        forge.m_128347_(DeathTpPenalty.FRAC_KEY, 0.25);
        forge.m_128356_("dmz_minigame_shadow_dummy_cooldown_until", 42L);
        if (withFrozen) {
            for (String key : FROZEN_KEYS) {
                CompoundTag v = new CompoundTag();
                v.m_128356_("v", key.length());
                forge.m_128365_(key, v);
            }
        }
        root.m_128365_("ForgeData", forge);
        return root;
    }

    private static CompoundTag unrelatedOnly(CompoundTag root) {
        CompoundTag copy = root.m_6426_();
        CompoundTag forge = forgeData(copy);
        if (forge != null) {
            for (String key : FROZEN_KEYS) {
                forge.m_128473_(key);
            }
        }
        return copy;
    }

    /**
     * Same keys at every level, and every non-compound tag serializes to the same bytes. Key
     * order is not compared: compounds are hash maps, so a copy or a file read may list them
     * in another order.
     */
    private static boolean sameBytes(CompoundTag a, CompoundTag b) throws java.io.IOException {
        if (!a.m_128431_().equals(b.m_128431_())) {
            return false;
        }
        for (String key : a.m_128431_()) {
            Tag x = a.m_128423_(key);
            Tag y = b.m_128423_(key);
            if (x instanceof CompoundTag cx && y instanceof CompoundTag cy) {
                if (!sameBytes(cx, cy)) {
                    return false;
                }
            } else if (!Arrays.equals(bytes(x), bytes(y))) {
                return false;
            }
        }
        return true;
    }

    private static byte[] bytes(Tag tag) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.DataOutputStream data = new java.io.DataOutputStream(out);
        data.writeByte(tag.m_7060_()); // type id
        tag.m_6434_(data); // write
        data.flush();
        return out.toByteArray();
    }

    private static void deleteTree(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Collections.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    static final class Result {
        int scanned;
        int stripped;
        int keysRemoved;
        int skipped;
        int noData;
    }

    private static final class Marker {
        boolean completed;
        String version;
        long completedAt;
        String playerDataDir;
        String backupDir;
        int scanned;
        int stripped;
        int keysRemoved;
        int skipped;
        int noData;
    }
}
