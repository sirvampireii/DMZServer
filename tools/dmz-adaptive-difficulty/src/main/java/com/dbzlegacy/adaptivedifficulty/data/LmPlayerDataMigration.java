package com.dbzlegacy.adaptivedifficulty.data;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * One-time bulk move of durable LM records from Forge persistent data ({@code ForgeData} in each
 * saved player file) into {@link LmPlayerStore} files. Runs on server start, before anyone can
 * join, and only while the completion marker is absent. The old keys stay in the player files
 * as a rollback fallback; there is no per-player migration on login.
 * <p>
 * The marker is written only when no player file was skipped (unreadable, or its LM file could
 * not be written). Otherwise the pass runs again next boot, so a file that was briefly unreadable
 * is not left behind with empty LM records. A re-run is safe: {@code writeIfAbsent} never
 * overwrites an LM file, so players already moved keep theirs. A {@code .dat} that is not named
 * by a UUID is not a player file; it is counted apart and does not hold the marker back.
 */
public final class LmPlayerDataMigration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LmPlayerDataMigration() {}

    public static Path markerPath() {
        return ConfigPaths.dataDir().resolve("player-data-migration.json");
    }

    public static void runIfNeeded(MinecraftServer server) {
        Path marker = markerPath();
        if (Files.exists(marker)) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] player data migration already done ({}); skipped",
                    AdaptiveDifficultyMod.MOD_ID, marker.getFileName());
            return;
        }
        Path dir;
        try {
            dir = server.m_129843_(LevelResource.f_78176_); // getWorldPath(PLAYER_DATA_DIR)
        } catch (Throwable t) {
            // No marker: the pass runs again next boot.
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] player data migration: playerdata folder not resolved ({}); will retry next boot",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
            return;
        }
        Result result = new Result();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.dat")) {
                for (Path file : files) {
                    migrateFile(file, result);
                }
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.error(
                        "[{}] player data migration: could not list {} ({}); will retry next boot",
                        AdaptiveDifficultyMod.MOD_ID, dir, t.toString());
                return;
            }
        }
        if (result.skipped > 0) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] player data migration: {} player file(s) skipped (unreadable or not written); "
                            + "no marker written, the pass runs again next boot ({} scanned, {} migrated, "
                            + "{} with no LM data, {} already had an LM file, {} not player files)",
                    AdaptiveDifficultyMod.MOD_ID, result.skipped, result.scanned, result.migrated,
                    result.noData, result.existing, result.notPlayer);
            return;
        }
        writeMarker(marker, dir, result);
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] player data migration done: {} player file(s) scanned, {} migrated, {} with no LM data, "
                        + "{} already had an LM file, {} not player files",
                AdaptiveDifficultyMod.MOD_ID, result.scanned, result.migrated, result.noData,
                result.existing, result.notPlayer);
    }

    private static void migrateFile(Path file, Result result) {
        String name = file.getFileName().toString();
        UUID id;
        try {
            id = UUID.fromString(name.substring(0, name.length() - ".dat".length()));
        } catch (Throwable notUuid) {
            result.notPlayer++;
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] player data migration: {} is not a player file; skipped",
                    AdaptiveDifficultyMod.MOD_ID, name);
            return;
        }
        result.scanned++;
        CompoundTag root;
        try {
            root = NbtIo.m_128937_(file.toFile()); // readCompressed
        } catch (Throwable t) {
            result.skipped++;
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] player data migration: {} unreadable ({}); skipped",
                    AdaptiveDifficultyMod.MOD_ID, name, t.toString());
            return;
        }
        CompoundTag forge = root == null || !root.m_128441_("ForgeData") ? null : root.m_128469_("ForgeData");
        CompoundTag records = new CompoundTag();
        if (forge != null) {
            for (String key : LmPlayerStore.DURABLE_KEYS) {
                if (forge.m_128441_(key)) {
                    records.m_128365_(key, forge.m_128423_(key).m_6426_()); // copy
                }
            }
        }
        if (records.m_128456_()) {
            result.noData++;
            return;
        }
        if (LmPlayerStore.writeIfAbsent(id, records)) {
            result.migrated++;
            LmLog.event(
                    "[{}] player data migration: {} -> {} record(s) {}",
                    AdaptiveDifficultyMod.MOD_ID, id, records.m_128440_(), records.m_128431_());
        } else if (Files.exists(LmPlayerStore.path(id))) {
            // Expected on a re-run after a skipped file: this player was moved last time.
            result.existing++;
            LmLog.event(
                    "[{}] player data migration: {} already has an LM file; left as is",
                    AdaptiveDifficultyMod.MOD_ID, id);
        } else {
            result.skipped++;
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] player data migration: {} could not be written; skipped",
                    AdaptiveDifficultyMod.MOD_ID, id);
        }
    }

    private static void writeMarker(Path marker, Path dir, Result result) {
        try {
            Files.createDirectories(marker.getParent());
            Marker m = new Marker();
            m.completed = true;
            m.version = AdaptiveDifficultyMod.VERSION;
            m.completedAt = System.currentTimeMillis();
            m.playerDataDir = dir.toString();
            m.scanned = result.scanned;
            m.migrated = result.migrated;
            m.noData = result.noData;
            m.existing = result.existing;
            m.skipped = result.skipped;
            m.notPlayer = result.notPlayer;
            try (Writer writer = Files.newBufferedWriter(marker)) {
                GSON.toJson(m, writer);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] player data migration: marker write failed ({}); the pass will run again next boot "
                            + "but never overwrites existing LM files",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    private static final class Result {
        int scanned;
        int migrated;
        int noData;
        int existing;
        int skipped;
        int notPlayer;
    }

    private static final class Marker {
        boolean completed;
        String version;
        long completedAt;
        String playerDataDir;
        int scanned;
        int migrated;
        int noData;
        int existing;
        int skipped;
        int notPlayer;
    }
}
