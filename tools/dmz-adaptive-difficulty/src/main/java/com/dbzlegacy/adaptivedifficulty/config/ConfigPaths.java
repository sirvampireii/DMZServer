package com.dbzlegacy.adaptivedifficulty.config;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Shared LegacyMechanics data directory resolver with one-time migration from
 * {@code config/adaptivedifficulty/}.
 */
public final class ConfigPaths {
    private static final Object MIGRATE_LOCK = new Object();
    private static volatile boolean migrated;

    private ConfigPaths() {}

    /** Preferred data root: {@code config/legacymechanics/}. */
    public static Path dataDir() {
        Path preferred = FMLPaths.CONFIGDIR.get().resolve("legacymechanics");
        ensureMigrated(preferred);
        return preferred;
    }

    public static Path telemetryDir() {
        return dataDir().resolve("telemetry");
    }

    public static Path rivalryPath() {
        return dataDir().resolve("rivalry-v4.json");
    }

    public static Path sparringPath() {
        return dataDir().resolve("sparring.json");
    }

    public static Path progressionPath() {
        return dataDir().resolve("progression-v4.json");
    }

    /** Active global TP boost window (survives restart). */
    public static Path globalTpBoostPath() {
        return dataDir().resolve("global-tp-boost.json");
    }

    public static Path globalMasteryBoostPath() {
        return dataDir().resolve("global-mastery-boost.json");
    }

    private static void ensureMigrated(Path preferred) {
        if (migrated) {
            return;
        }
        synchronized (MIGRATE_LOCK) {
            if (migrated) {
                return;
            }
            try {
                Files.createDirectories(preferred);
                Path legacy = FMLPaths.CONFIGDIR.get().resolve("adaptivedifficulty");
                if (Files.isDirectory(legacy)) {
                    copyMissing(legacy, preferred);
                }
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.debug(
                        "[{}] data-dir migrate skip: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            }
            migrated = true;
        }
    }

    private static void copyMissing(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(from)) {
            walk.forEach(src -> {
                try {
                    Path rel = from.relativize(src);
                    Path dest = to.resolve(rel);
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else if (!Files.exists(dest)) {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest, StandardCopyOption.COPY_ATTRIBUTES);
                        AdaptiveDifficultyMod.LOGGER.info(
                                "[{}] migrated data {} → {}",
                                AdaptiveDifficultyMod.MOD_ID,
                                src.getFileName(),
                                dest);
                    }
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        }
    }
}
