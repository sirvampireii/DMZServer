package com.dbzlegacy.adaptivedifficulty.data;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.rival.ProvingGrounds;
import com.dbzlegacy.adaptivedifficulty.rival.RivalLink;
import com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord;
import com.dbzlegacy.adaptivedifficulty.rival.RivalProgression;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStore;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * One-time CustomNPCs storeddata → LegacyMechanics JSON/NBT migration.
 * <p>
 * World (server start / {@code /lm admin migrate-cnpc}): Rival DB, Rival progression,
 * Spar leaderboard → {@code rivalry-v4.json} / {@code progression-v4.json} /
 * {@code sparring.json}. CNPC world keys are cleared <b>only after a successful import</b>.
 * <p>
 * Sources (first hit wins): live CNPC storeddata → {@code cnpc-import-backup/} →
 * CNPC {@code world_data.json} on disk (ScriptController path or scan).
 * <p>
 * Player (login): Spar bonds/streaks + flight/meditation/potential progress keys →
 * Forge {@code lm_progression} NBT / SparStore, then clears those CNPC player keys.
 * <p>
 * Guarded by {@code config/legacymechanics/cnpc-world-migration.done} and per-player
 * {@code lm_cnpc_player_migrated}. Raw CNPC blobs are copied to
 * {@code config/legacymechanics/cnpc-import-backup/} before clear.
 */
public final class CnpcDataMigrator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicBoolean WORLD_RAN = new AtomicBoolean(false);

    private static final String MARKER_FILE = "cnpc-world-migration.done";
    private static final String PLAYER_MARK = "lm_cnpc_player_migrated";

    private static final String RIVAL_DB = "dlr.rivalry.v4.database";
    private static final String RIVAL_DB_BAK = "dlr.rivalry.v4.database.backup";
    private static final String RIVAL_PROG = "dlr.rivalry.v4.progression";
    private static final String RIVAL_PROG_BAK = "dlr.rivalry.v4.progression.backup";
    private static final String RIVAL_CH = "dlr.rivalry.v4.challenges";
    private static final String RIVAL_CH_BAK = "dlr.rivalry.v4.challenges.backup";
    private static final String SPAR_LB_NAMES = "spar.leaderboard.names";

    private CnpcDataMigrator() {}

    /** ServerStarted — CNPC worlds are available. Safe to call repeatedly. */
    public static void migrateWorldIfNeeded(MinecraftServer server) {
        if (server == null || !DifficultyConfig.get().enableCnpcDataMigration) {
            return;
        }
        if (!WORLD_RAN.compareAndSet(false, true)) {
            return;
        }
        Path marker = ConfigPaths.dataDir().resolve(MARKER_FILE);
        if (Files.isRegularFile(marker)) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] CNPC world migration already done ({})",
                    AdaptiveDifficultyMod.MOD_ID, marker.getFileName());
            return;
        }
        String msg = runWorldMigrate(server, false);
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] CNPC world migrate: {}",
                AdaptiveDifficultyMod.MOD_ID,
                msg == null ? "" : msg.replace('§', ' '));
        // Allow another auto attempt next boot if nothing was marked done yet.
        if (!Files.isRegularFile(marker)) {
            WORLD_RAN.set(false);
        }
    }

    /**
     * Staff re-import. {@code forceOverwrite} clears LM Rival/Spar LB/progression first.
     * Always searches live CNPC → import-backup → world_data.json.
     */
    public static String forceMigrateWorld(MinecraftServer server, boolean forceOverwrite) {
        if (server == null) {
            return "§cNo server.";
        }
        try {
            Path marker = ConfigPaths.dataDir().resolve(MARKER_FILE);
            Files.deleteIfExists(marker);
            WORLD_RAN.set(false);
            // Resolve source FIRST — never wipe LM if we have nothing worth importing.
            WorldBlob preview = resolveWorldBlob(server);
            int previewPlayers = preview == null ? 0 : countRivalPlayers(preview.rivalRaw);
            int previewRich = preview == null ? 0 : countRichCnpcPlayers(preview.rivalRaw);
            boolean worthForce = preview != null && preview.hasAnything()
                    && (previewPlayers > 0 || previewRich > 0
                    || (preview.fileMap != null && preview.fileMap.keySet().stream()
                    .anyMatch(k -> k != null && k.startsWith("spar.leaderboard.")))
                    || notBlank(preview.progRaw));
            if (forceOverwrite && !worthForce) {
                return "§cForce aborted — no usable CNPC/backup Rival/Spar data found.\n"
                        + "§7LM stores were NOT cleared.\n"
                        + "§8Drop world_data.json into config/legacymechanics/cnpc-import-backup/ and retry.";
            }
            if (forceOverwrite) {
                RivalStore.get().players.clear();
                RivalStore.get().declareRequests.clear();
                RivalStore.get().markDirty();
                RivalProgression.get().resetForImport();
                SparStore.get().leaderboard.clear();
                SparStore.get().recentSessions.clear();
                SparStore.get().markDirty();
            }
            return runWorldMigrate(server, forceOverwrite);
        } catch (Throwable t) {
            return "§cMigration error: " + t;
        }
    }

    /**
     * Core world import. Never clears CNPC keys unless at least one blob was imported.
     * Never reports success when zero players/LB rows were taken.
     */
    private static String runWorldMigrate(MinecraftServer server, boolean forceOverwrite) {
        Path marker = ConfigPaths.dataDir().resolve(MARKER_FILE);
        try {
            WorldBlob blob = resolveWorldBlob(server);
            if (blob == null || !blob.hasAnything()) {
                return "§cNo CNPC Rival/Spar data found.\n"
                        + "§7Checked: ScriptController compound, §f<world>/customnpcs/scripts/world_data.json§7,\n"
                        + "§7and §fcnpc-import-backup/§7.\n"
                        + "§8Put a pre-wipe world_data.json into "
                        + "config/legacymechanics/cnpc-import-backup/ then "
                        + "/lm admin migrate-cnpc force";
            }

            Path backupDir = ConfigPaths.dataDir().resolve("cnpc-import-backup");
            Files.createDirectories(backupDir);
            backupRaw(backupDir, "rivalry-database.json", blob.rivalRaw);
            backupRaw(backupDir, "rivalry-progression.json", blob.progRaw);
            backupRaw(backupDir, "rivalry-challenges.json", blob.chRaw);
            backupRaw(backupDir, "spar-leaderboard-names.txt", blob.sparNames);
            if (blob.fileMap != null && !blob.fileMap.isEmpty()
                    && !Files.isRegularFile(backupDir.resolve("world_data.json"))) {
                // Keep a full dump for future force-migrate if we loaded from live/API only.
                try {
                    Files.writeString(backupDir.resolve("world_data-from-" + blob.source + ".json"),
                            GSON.toJson(blob.fileMap), StandardCharsets.UTF_8);
                } catch (Throwable ignored) {
                }
            }

            int lmPlayers = RivalStore.get().players.size();
            int cnpcPlayers = countRivalPlayers(blob.rivalRaw);
            int lmRich = countRichLmPlayers();
            int cnpcRich = countRichCnpcPlayers(blob.rivalRaw);

            boolean takeRival = notBlank(blob.rivalRaw) && (
                    forceOverwrite
                            || lmPlayers == 0
                            || cnpcPlayers > lmPlayers
                            || (cnpcRich > lmRich && cnpcRich > 0));

            int rivalPlayers = 0;
            if (takeRival) {
                if (!forceOverwrite && lmPlayers > 0) {
                    AdaptiveDifficultyMod.LOGGER.warn(
                            "[{}] Replacing rivalry-v4 ({} LM / {} rich) with CNPC ({} / {} rich) from {}",
                            AdaptiveDifficultyMod.MOD_ID, lmPlayers, lmRich, cnpcPlayers, cnpcRich, blob.source);
                    RivalStore.get().players.clear();
                    RivalStore.get().declareRequests.clear();
                }
                rivalPlayers = importRivalDatabase(blob.rivalRaw);
            } else if (notBlank(blob.rivalRaw)) {
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] Skipping Rival import — LM already has {} players (CNPC {} from {}). "
                                + "Use /lm admin migrate-cnpc force to overwrite.",
                        AdaptiveDifficultyMod.MOD_ID, lmPlayers, cnpcPlayers, blob.source);
            }

            boolean progImported = false;
            if (notBlank(blob.progRaw) && (forceOverwrite || RivalProgression.get().isImportEmpty())) {
                progImported = importRivalProgression(blob.progRaw);
            }

            int sparLb = importSparLeaderboard(blob);

            RivalStore.get().markDirty();
            RivalStore.get().save();
            SparStore.get().markDirty();
            SparStore.get().save();
            if (progImported) {
                RivalProgression.get().save();
            }

            boolean imported = rivalPlayers > 0 || progImported || sparLb > 0;
            if (imported && blob.liveStored != null) {
                // Only clear keys we actually consumed — never wipe rival DB after a spar-only import.
                if (rivalPlayers > 0) {
                    storedRemove(blob.liveStored, RIVAL_DB);
                    storedRemove(blob.liveStored, RIVAL_DB_BAK);
                }
                if (progImported) {
                    storedRemove(blob.liveStored, RIVAL_PROG);
                    storedRemove(blob.liveStored, RIVAL_PROG_BAK);
                }
                if (sparLb > 0) {
                    storedRemove(blob.liveStored, SPAR_LB_NAMES);
                    List<String> toRemove = new ArrayList<>();
                    for (String key : storedKeys(blob.liveStored)) {
                        if (key != null && (key.startsWith("spar.leaderboard.")
                                || key.startsWith("dlr.rivalry.v4.challenge.announce.")
                                || key.startsWith("dlr.rivalry.v4.challenge.end."))) {
                            toRemove.add(key);
                        }
                    }
                    for (String key : toRemove) {
                        storedRemove(blob.liveStored, key);
                    }
                }
                if (rivalPlayers > 0) {
                    storedRemove(blob.liveStored, RIVAL_CH);
                    storedRemove(blob.liveStored, RIVAL_CH_BAK);
                }
            } else if (!imported && blob.liveStored != null) {
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] Leaving CNPC storeddata keys intact (nothing imported)",
                        AdaptiveDifficultyMod.MOD_ID);
            }

            if (imported) {
                writeMarker(marker, "rivalPlayers=" + rivalPlayers
                        + " prog=" + progImported
                        + " sparLb=" + sparLb
                        + " source=" + blob.source
                        + " at=" + System.currentTimeMillis());
                WORLD_RAN.set(true);
                return "§aCNPC→LM import OK from §f" + blob.source
                        + "\n§7Rival players §f" + rivalPlayers
                        + " §7· progression §f" + progImported
                        + " §7· spar LB rows §f" + sparLb
                        + "\n§8Backup: config/legacymechanics/cnpc-import-backup/";
            }

            return "§eNo rows imported from §f" + blob.source
                    + "\n§7LM rivals §f" + lmPlayers
                    + " §7· CNPC rivals §f" + cnpcPlayers
                    + "\n§8CNPC keys were NOT cleared. Try §f/lm admin migrate-cnpc force";
        } catch (Throwable t) {
            WORLD_RAN.set(false);
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] CNPC world migration failed: {}",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
            return "§cMigration failed: " + t;
        }
    }

    /* ========================= Source resolution ========================= */

    private static final class WorldBlob {
        String source = "unknown";
        String rivalRaw;
        String progRaw;
        String chRaw;
        String sparNames;
        /** Optional flat key→value map (file sources) for spar LB fields. */
        Map<String, String> fileMap;
        /** Live CNPC IDataObject — only set when source is live API (safe to clear). */
        Object liveStored;

        boolean hasAnything() {
            return notBlank(rivalRaw) || notBlank(progRaw) || notBlank(sparNames)
                    || (fileMap != null && fileMap.keySet().stream()
                    .anyMatch(k -> k != null && k.startsWith("spar.leaderboard.")));
        }
    }

    private static WorldBlob resolveWorldBlob(MinecraftServer server) {
        // Reload disk → compound, then pick the *richest* source (stubs must not beat backup).
        ensureCnpcStoredDataLoaded();
        List<WorldBlob> candidates = new ArrayList<>();
        addCandidate(candidates, fromScriptControllerCompound());
        addCandidate(candidates, fromLiveStoreddata());
        addCandidate(candidates, fromWorldDataFile(server));
        addCandidate(candidates, fromBackupDir());
        WorldBlob best = pickRichestBlob(candidates);
        if (best != null) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] CNPC source selected: {} (rivalPlayers={} rich={} sparKeys={})",
                    AdaptiveDifficultyMod.MOD_ID,
                    best.source,
                    countRivalPlayers(best.rivalRaw),
                    countRichCnpcPlayers(best.rivalRaw),
                    best.fileMap == null ? 0 : best.fileMap.size());
        }
        return best;
    }

    private static void addCandidate(List<WorldBlob> out, WorldBlob blob) {
        if (blob == null) {
            return;
        }
        enrichRivalRawFromBackupKeys(blob);
        if (blob.hasAnything()) {
            out.add(blob);
        }
    }

    /** Prefer `.backup` CNPC keys when the main key is empty/stub. */
    private static void enrichRivalRawFromBackupKeys(WorldBlob blob) {
        if (blob == null) {
            return;
        }
        int main = countRivalPlayers(blob.rivalRaw);
        int mainRich = countRichCnpcPlayers(blob.rivalRaw);
        String bak = null;
        if (blob.fileMap != null) {
            bak = blob.fileMap.get(RIVAL_DB_BAK);
        }
        if (!notBlank(bak) && blob.liveStored != null) {
            bak = storedGet(blob.liveStored, RIVAL_DB_BAK);
        }
        int bakPlayers = countRivalPlayers(bak);
        int bakRich = countRichCnpcPlayers(bak);
        if (bakPlayers > main || (bakRich > mainRich && bakRich > 0)) {
            blob.rivalRaw = bak;
        }
        // Same for progression
        int progMain = notBlank(blob.progRaw) ? blob.progRaw.length() : 0;
        String progBak = blob.fileMap != null ? blob.fileMap.get(RIVAL_PROG_BAK) : null;
        if (!notBlank(progBak) && blob.liveStored != null) {
            progBak = storedGet(blob.liveStored, RIVAL_PROG_BAK);
        }
        if (notBlank(progBak) && progBak.length() > progMain) {
            blob.progRaw = progBak;
        }
    }

    private static WorldBlob pickRichestBlob(List<WorldBlob> candidates) {
        WorldBlob best = null;
        int bestScore = -1;
        for (WorldBlob b : candidates) {
            if (b == null) {
                continue;
            }
            int score = blobScore(b);
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return best;
    }

    /** Higher = better recovery candidate. Empty stubs score near 0. */
    private static int blobScore(WorldBlob b) {
        int players = countRivalPlayers(b.rivalRaw);
        int rich = countRichCnpcPlayers(b.rivalRaw);
        int spar = 0;
        if (b.fileMap != null) {
            for (String k : b.fileMap.keySet()) {
                if (k != null && k.startsWith("spar.leaderboard.")) {
                    spar++;
                }
            }
        }
        if (notBlank(b.sparNames)) {
            spar = Math.max(spar, b.sparNames.split(",").length);
        }
        int prog = notBlank(b.progRaw) ? Math.min(50, b.progRaw.length() / 200) : 0;
        // Prefer sources with real rival rows heavily over leftover spar-only stubs.
        return rich * 1000 + players * 10 + spar + prog;
    }

    /** Reload CNPC world_data.json into ScriptController.compound (no-op if CNPC missing). */
    private static void ensureCnpcStoredDataLoaded() {
        try {
            Class<?> scClass = Class.forName("noppes.npcs.controllers.ScriptController");
            Object instance = scClass.getField("Instance").get(null);
            if (instance == null) {
                return;
            }
            scClass.getMethod("loadStoredData").invoke(instance);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Read Rival/Spar keys straight from {@code ScriptController.compound}
     * (backed by {@code <world>/customnpcs/scripts/world_data.json}).
     */
    private static WorldBlob fromScriptControllerCompound() {
        try {
            Class<?> scClass = Class.forName("noppes.npcs.controllers.ScriptController");
            Object instance = scClass.getField("Instance").get(null);
            if (instance == null) {
                return null;
            }
            Object compound = scClass.getField("compound").get(instance);
            Map<String, String> map = compoundToStringMap(compound);
            if (map.isEmpty()) {
                return null;
            }
            WorldBlob b = blobFromMap(map, "scriptcontroller-compound");
            // Clearing goes through the real IData wrapper when available.
            b.liveStored = overworldStoreddata();
            if (b.liveStored == null) {
                b.liveStored = new CompoundClearAdapter(instance, compound);
            }
            return b;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] ScriptController compound read fail: {}",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
            return null;
        }
    }

    private static WorldBlob fromLiveStoreddata() {
        Object stored = overworldStoreddata();
        if (stored == null) {
            return null;
        }
        WorldBlob b = new WorldBlob();
        b.source = "live-cnpc-iworld";
        b.liveStored = stored;
        b.rivalRaw = firstNonBlank(storedGet(stored, RIVAL_DB), storedGet(stored, RIVAL_DB_BAK));
        b.progRaw = firstNonBlank(storedGet(stored, RIVAL_PROG), storedGet(stored, RIVAL_PROG_BAK));
        b.chRaw = firstNonBlank(storedGet(stored, RIVAL_CH), storedGet(stored, RIVAL_CH_BAK));
        b.sparNames = storedGet(stored, SPAR_LB_NAMES);
        Map<String, String> map = new HashMap<>();
        for (String key : storedKeys(stored)) {
            if (key != null && key.startsWith("spar.leaderboard.")) {
                String v = storedGet(stored, key);
                if (v != null) {
                    map.put(key, v);
                }
            }
        }
        if (!map.isEmpty()) {
            b.fileMap = map;
        }
        return b;
    }

    private static WorldBlob fromBackupDir() {
        Path dir = ConfigPaths.dataDir().resolve("cnpc-import-backup");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Path[] dumps = {
                dir.resolve("world_data.json"),
                dir.resolve("world_data-from-live-cnpc.json"),
        };
        for (Path dump : dumps) {
            WorldBlob fromDump = readWorldDataFile(dump, "backup:" + dump.getFileName());
            if (fromDump != null && fromDump.hasAnything()) {
                return fromDump;
            }
        }
        try {
            try (Stream<Path> walk = Files.list(dir)) {
                for (Path p : walk.toList()) {
                    String name = p.getFileName().toString();
                    if (name.startsWith("world_data") && name.endsWith(".json")) {
                        WorldBlob fromDump = readWorldDataFile(p, "backup:" + name);
                        if (fromDump != null && fromDump.hasAnything()) {
                            return fromDump;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        WorldBlob b = new WorldBlob();
        b.source = "cnpc-import-backup";
        b.rivalRaw = readFileString(dir.resolve("rivalry-database.json"));
        if (!notBlank(b.rivalRaw)) {
            b.rivalRaw = readFileString(dir.resolve("rivalry-database.backup.json"));
        }
        b.progRaw = readFileString(dir.resolve("rivalry-progression.json"));
        if (!notBlank(b.progRaw)) {
            b.progRaw = readFileString(dir.resolve("rivalry-progression.backup.json"));
        }
        b.chRaw = readFileString(dir.resolve("rivalry-challenges.json"));
        b.sparNames = readFileString(dir.resolve("spar-leaderboard-names.txt"));
        return b.hasAnything() ? b : null;
    }

    private static WorldBlob fromWorldDataFile(MinecraftServer server) {
        List<Path> candidates = new ArrayList<>();
        // Canonical CNPC path: server.getWorldPath(customnpcs)/scripts/world_data.json
        Path levelScripts = cnpcLevelScriptsDir(server);
        if (levelScripts != null) {
            candidates.add(levelScripts.resolve("world_data.json"));
        }
        Path scriptControllerFile = cnpcWorldDataFile();
        if (scriptControllerFile != null) {
            candidates.add(scriptControllerFile);
        }
        try {
            Class<?> custom = Class.forName("noppes.npcs.CustomNpcs");
            Object dir = custom.getMethod("getLevelSaveDirectory", String.class, boolean.class)
                    .invoke(null, "scripts", true);
            if (dir instanceof File f) {
                candidates.add(f.toPath().resolve("world_data.json"));
            }
        } catch (Throwable ignored) {
        }

        Path gameDir = FMLPaths.GAMEDIR.get();
        if (gameDir != null) {
            candidates.add(gameDir.resolve("customnpcs/scripts/world_data.json"));
            candidates.add(gameDir.resolve("uploads/scripts/world_data.json"));
            for (String world : List.of("world", "AdventureWorld", "world_1", "Main", "Dim1")) {
                candidates.add(gameDir.resolve(world).resolve("customnpcs/scripts/world_data.json"));
            }
            try (Stream<Path> walk = Files.walk(gameDir, 6)) {
                walk.filter(p -> {
                            String s = p.toString().replace('\\', '/');
                            return s.endsWith("/customnpcs/scripts/world_data.json");
                        })
                        .limit(12)
                        .forEach(candidates::add);
            } catch (Throwable ignored) {
            }
        }

        StringBuilder tried = new StringBuilder();
        for (Path p : candidates) {
            if (p == null) {
                continue;
            }
            tried.append("\n§8- ").append(p.toAbsolutePath());
            if (!Files.isRegularFile(p)) {
                continue;
            }
            WorldBlob b = readWorldDataFile(p, "file:" + p.toAbsolutePath());
            if (b != null && b.hasAnything()) {
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] CNPC world_data loaded from {}", AdaptiveDifficultyMod.MOD_ID, p.toAbsolutePath());
                return b;
            }
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] CNPC world_data present but no Rival/Spar keys: {}",
                    AdaptiveDifficultyMod.MOD_ID, p.toAbsolutePath());
        }
        if (tried.length() > 0) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] CNPC world_data paths checked:{}", AdaptiveDifficultyMod.MOD_ID, tried);
        }
        return null;
    }

    /** {@code <level-save>/customnpcs/scripts} via MinecraftServer#getWorldPath(LevelResource). */
    private static Path cnpcLevelScriptsDir(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        try {
            Class<?> levelResource = Class.forName("net.minecraft.world.level.storage.LevelResource");
            Object customNpcs = levelResource.getConstructor(String.class).newInstance("customnpcs");
            Object path = server.getClass()
                    .getMethod("m_129843_", levelResource)
                    .invoke(server, customNpcs);
            if (path instanceof Path p) {
                return p.resolve("scripts");
            }
            if (path instanceof File f) {
                return f.toPath().resolve("scripts");
            }
        } catch (Throwable ignored) {
            try {
                Class<?> levelResource = Class.forName("net.minecraft.world.level.storage.LevelResource");
                Object customNpcs = levelResource.getConstructor(String.class).newInstance("customnpcs");
                Object path = server.getClass()
                        .getMethod("getWorldPath", levelResource)
                        .invoke(server, customNpcs);
                if (path instanceof Path p) {
                    return p.resolve("scripts");
                }
            } catch (Throwable ignored2) {
            }
        }
        return null;
    }

    private static Path cnpcWorldDataFile() {
        try {
            Class<?> sc = Class.forName("noppes.npcs.controllers.ScriptController");
            Object instance = sc.getField("Instance").get(null);
            if (instance == null) {
                return null;
            }
            var m = sc.getDeclaredMethod("worldDataFile");
            m.setAccessible(true);
            Object file = m.invoke(instance);
            if (file instanceof File f) {
                return f.toPath();
            }
            if (file instanceof Path p) {
                return p;
            }
        } catch (Throwable ignored) {
        }
        // Fallback via public localDir field
        try {
            Class<?> sc = Class.forName("noppes.npcs.controllers.ScriptController");
            Object instance = sc.getField("Instance").get(null);
            Object localDir = sc.getField("localDir").get(instance);
            if (localDir instanceof File f) {
                return f.toPath().resolve("world_data.json");
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Load CNPC {@code world_data.json}. Prefer NBTJsonUtil (real CNPC format with {@code 1b}/{@code 123L});
     * fall back to plain Gson flat maps (export dumps).
     */
    private static WorldBlob readWorldDataFile(Path path, String sourceLabel) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        Map<String, String> map = readWorldDataMap(path);
        if (map == null || map.isEmpty()) {
            return null;
        }
        return blobFromMap(map, sourceLabel);
    }

    private static WorldBlob blobFromMap(Map<String, String> map, String sourceLabel) {
        WorldBlob b = new WorldBlob();
        b.source = sourceLabel;
        b.fileMap = map;
        b.rivalRaw = firstNonBlank(map.get(RIVAL_DB), map.get(RIVAL_DB_BAK));
        b.progRaw = firstNonBlank(map.get(RIVAL_PROG), map.get(RIVAL_PROG_BAK));
        b.chRaw = firstNonBlank(map.get(RIVAL_CH), map.get(RIVAL_CH_BAK));
        b.sparNames = map.get(SPAR_LB_NAMES);
        return b;
    }

    private static Map<String, String> readWorldDataMap(Path path) {
        // 1) Real CNPC NBT-JSON
        try {
            Class<?> util = Class.forName("noppes.npcs.util.NBTJsonUtil");
            Object compound = util.getMethod("LoadFile", File.class).invoke(null, path.toFile());
            Map<String, String> nbtMap = compoundToStringMap(compound);
            if (nbtMap != null && !nbtMap.isEmpty()) {
                return nbtMap;
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] NBTJsonUtil.LoadFile failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID, path, t.toString());
        }
        // 2) Plain JSON object dump
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = parseObject(text);
            if (root == null) {
                return null;
            }
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isJsonNull()) {
                    continue;
                }
                try {
                    if (e.getValue().isJsonPrimitive()) {
                        map.put(e.getKey(), e.getValue().getAsString());
                    } else {
                        map.put(e.getKey(), e.getValue().toString());
                    }
                } catch (Throwable ignored) {
                }
            }
            return map;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] Gson world_data read fail {}: {}",
                    AdaptiveDifficultyMod.MOD_ID, path, t.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> compoundToStringMap(Object compound) {
        Map<String, String> map = new HashMap<>();
        if (compound == null) {
            return map;
        }
        try {
            Object keysObj;
            try {
                keysObj = compound.getClass().getMethod("m_128431_").invoke(compound);
            } catch (NoSuchMethodException e) {
                keysObj = compound.getClass().getMethod("getAllKeys").invoke(compound);
            }
            if (!(keysObj instanceof Iterable<?> keys)) {
                return map;
            }
            for (Object keyObj : keys) {
                if (keyObj == null) {
                    continue;
                }
                String key = String.valueOf(keyObj);
                Object tag;
                try {
                    tag = compound.getClass().getMethod("m_128423_", String.class).invoke(compound, key);
                } catch (NoSuchMethodException e) {
                    tag = compound.getClass().getMethod("get", String.class).invoke(compound, key);
                }
                if (tag == null) {
                    continue;
                }
                String value;
                if (tag.getClass().getName().contains("NumericTag")
                        || Number.class.isAssignableFrom(tag.getClass())) {
                    try {
                        Object d = tag.getClass().getMethod("m_7061_").invoke(tag);
                        value = String.valueOf(d);
                    } catch (Throwable t) {
                        value = String.valueOf(tag);
                    }
                } else {
                    try {
                        // StringTag / Tag#getAsString
                        Object s = tag.getClass().getMethod("m_7916_").invoke(tag);
                        value = s == null ? null : String.valueOf(s);
                    } catch (Throwable t) {
                        try {
                            Object s = tag.getClass().getMethod("getAsString").invoke(tag);
                            value = s == null ? null : String.valueOf(s);
                        } catch (Throwable t2) {
                            value = String.valueOf(tag);
                        }
                    }
                }
                if (value != null) {
                    map.put(key, value);
                }
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] compoundToStringMap fail: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
        return map;
    }

    /**
     * Minimal IData-like clearer so we can remove migrated keys from ScriptController.compound
     * when {@code getIWorld} is unavailable.
     */
    private static final class CompoundClearAdapter {
        private final Object scriptController;
        private Object compound;

        private CompoundClearAdapter(Object scriptController, Object compound) {
            this.scriptController = scriptController;
            this.compound = compound;
        }

        @SuppressWarnings("unused") // invoked via reflection from storedRemove/storedGet/storedKeys
        public boolean has(String key) {
            try {
                refresh();
                Object r = compound.getClass().getMethod("m_128441_", String.class).invoke(compound, key);
                return r instanceof Boolean b && b;
            } catch (Throwable t) {
                return false;
            }
        }

        @SuppressWarnings("unused")
        public Object get(String key) {
            try {
                refresh();
                if (!has(key)) {
                    return null;
                }
                Object tag = compound.getClass().getMethod("m_128423_", String.class).invoke(compound, key);
                if (tag == null) {
                    return null;
                }
                try {
                    return tag.getClass().getMethod("m_7916_").invoke(tag);
                } catch (Throwable t) {
                    return String.valueOf(tag);
                }
            } catch (Throwable t) {
                return null;
            }
        }

        @SuppressWarnings("unused")
        public void remove(String key) {
            try {
                refresh();
                compound.getClass().getMethod("m_128473_", String.class).invoke(compound, key);
                scriptController.getClass().getField("shouldSave").setBoolean(scriptController, true);
            } catch (Throwable ignored) {
            }
        }

        @SuppressWarnings("unused")
        public String[] getKeys() {
            try {
                refresh();
                Object keys = compound.getClass().getMethod("m_128431_").invoke(compound);
                if (keys instanceof java.util.Set<?> set) {
                    return set.stream().map(String::valueOf).toArray(String[]::new);
                }
            } catch (Throwable ignored) {
            }
            return new String[0];
        }

        private void refresh() throws Exception {
            compound = scriptController.getClass().getField("compound").get(scriptController);
        }
    }

    private static String readFileString(Path path) {
        try {
            if (path != null && Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (notBlank(a)) {
            return a;
        }
        return notBlank(b) ? b : null;
    }

    private static int countRivalPlayers(String raw) {
        JsonObject root = parseObject(raw);
        if (root == null || !root.has("players") || !root.get("players").isJsonObject()) {
            return 0;
        }
        return root.getAsJsonObject("players").size();
    }

    private static int countRichLmPlayers() {
        int n = 0;
        for (RivalPlayerRecord rec : RivalStore.get().players.values()) {
            if (rec == null) {
                continue;
            }
            boolean rich = rec.totalRp > 0
                    || rec.challengesPlayed > 0
                    || rec.officialWins + rec.officialLosses > 0
                    || (rec.rivals != null && !rec.rivals.isEmpty());
            if (rich) {
                n++;
            }
        }
        return n;
    }

    private static int countRichCnpcPlayers(String raw) {
        JsonObject root = parseObject(raw);
        if (root == null || !root.has("players") || !root.get("players").isJsonObject()) {
            return 0;
        }
        int n = 0;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("players").entrySet()) {
            if (e.getValue() == null || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject o = e.getValue().getAsJsonObject();
            JsonObject career = o.has("career") && o.get("career").isJsonObject()
                    ? o.getAsJsonObject("career") : o;
            double rp = dbl(career, "rivalPointsTotal", dbl(o, "totalRp", 0.0));
            long played = lng(career, "challengesPlayed", lng(o, "challengesPlayed", 0L));
            long wins = lng(career, "officialWins", lng(o, "officialWins", 0L));
            long losses = lng(career, "officialLosses", lng(o, "officialLosses", 0L));
            boolean hasRivals = o.has("rivals") && o.get("rivals").isJsonObject()
                    && o.getAsJsonObject("rivals").size() > 0;
            if (rp > 0 || played > 0 || wins + losses > 0 || hasRivals) {
                n++;
            }
        }
        return n;
    }

    /** Login — copy player CNPC storeddata into LM, then clear those keys. */
    public static void migratePlayerIfNeeded(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableCnpcDataMigration) {
            return;
        }
        if ("1".equals(ProgressionData.storedGet(player, PLAYER_MARK, ""))) {
            return;
        }
        try {
            Object stored = playerStoreddata(player);
            if (stored == null) {
                // CNPC missing entirely — nothing to import; mark so we don't retry forever.
                if (!cnpcAvailable()) {
                    ProgressionData.storedPut(player, PLAYER_MARK, "1");
                }
                return;
            }

            migrateSparBond(player, stored);
            migrateProgressionKeys(player, stored);

            // Clear migrated player keys
            clearPlayerSparKeys(stored);
            clearPlayerProgressionKeys(stored);

            ProgressionData.storedPut(player, PLAYER_MARK, "1");
            SparStore.get().markDirty();
            LmLog.event(
                    "[{}] CNPC→LM player migration for {}",
                    AdaptiveDifficultyMod.MOD_ID, player.m_6302_());
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] CNPC player migration soft-fail {}: {}",
                    AdaptiveDifficultyMod.MOD_ID, player.m_6302_(), t.toString());
        }
    }

    /* ========================= Rival import ========================= */

    private static int importRivalDatabase(String raw) {
        JsonObject root = parseObject(raw);
        if (root == null || !root.has("players")) {
            return 0;
        }
        JsonObject players = root.getAsJsonObject("players");
        if (players == null) {
            return 0;
        }
        RivalStore store = RivalStore.get();
        int count = 0;
        for (Map.Entry<String, JsonElement> e : players.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isJsonObject()) {
                continue;
            }
            RivalPlayerRecord rec = mapPlayer(e.getKey(), e.getValue().getAsJsonObject());
            if (rec != null) {
                store.players.put(rec.uuid, rec);
                count++;
            }
        }
        // Optional requests → declareRequests
        if (root.has("requests") && root.get("requests").isJsonObject()) {
            JsonObject reqs = root.getAsJsonObject("requests");
            for (Map.Entry<String, JsonElement> e : reqs.entrySet()) {
                if (e.getValue() == null || !e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                RivalStore.DeclareRequest req = new RivalStore.DeclareRequest();
                req.fromUuid = str(o, "fromUuid", str(o, "from", ""));
                req.fromName = str(o, "fromName", "");
                req.toUuid = str(o, "toUuid", str(o, "to", ""));
                req.toName = str(o, "toName", "");
                req.createdAt = lng(o, "createdAt", 0L);
                req.expiresAt = lng(o, "expiresAt", 0L);
                if (!req.fromUuid.isBlank() && !req.toUuid.isBlank()) {
                    store.declareRequests.put(req.fromUuid + ">" + req.toUuid, req);
                }
            }
        }
        store.markDirty();
        return count;
    }

    private static RivalPlayerRecord mapPlayer(String uuid, JsonObject o) {
        RivalPlayerRecord r = RivalPlayerRecord.create(uuid, str(o, "name", ""), lng(o, "createdAt", System.currentTimeMillis()));
        r.name = str(o, "name", r.name);
        r.uuid = str(o, "uuid", uuid);
        r.nemesisUuid = str(o, "nemesisUuid", "");
        r.createdAt = lng(o, "createdAt", r.createdAt);
        r.lastSeenAt = lng(o, "lastSeenAt", r.lastSeenAt);
        r.lastChallengeEndAt = lng(o, "lastChallengeEndAt", 0L);
        r.lastDeclareAt = lng(o, "lastDeclareAt", 0L);
        r.lastSurpassAt = lng(o, "lastSurpassAt", 0L);
        r.tpMessages = !o.has("tpMessages") || bool(o, "tpMessages", true);
        r.instinctEnabled = !o.has("instinctEnabled") || bool(o, "instinctEnabled", true);

        JsonObject career = o.has("career") && o.get("career").isJsonObject()
                ? o.getAsJsonObject("career") : o;
        r.totalRp = dbl(career, "rivalPointsTotal", dbl(o, "totalRp", 0.0));
        r.officialWins = (int) lng(career, "officialWins", lng(o, "officialWins", 0L));
        r.officialLosses = (int) lng(career, "officialLosses", lng(o, "officialLosses", 0L));
        r.officialDraws = (int) lng(career, "officialDraws", lng(o, "officialDraws", 0L));
        r.knockouts = (int) lng(career, "knockouts", lng(o, "knockouts", 0L));
        r.challengesPlayed = (int) lng(career, "challengesPlayed", lng(o, "challengesPlayed", 0L));
        r.surpassAwards = (int) lng(career, "surpassAwards", lng(o, "surpassAwards", 0L));
        r.presenceMs = lng(career, "presenceMs", lng(o, "presenceMs", 0L));
        r.currentWinStreak = (int) lng(career, "currentStreak", lng(o, "currentWinStreak", 0L));
        r.bestWinStreak = (int) lng(career, "bestStreak", lng(o, "bestWinStreak", 0L));
        r.careerDamageDealt = dbl(career, "damageDealt", dbl(o, "careerDamageDealt", 0.0));
        r.careerHighestCombo = (int) lng(career, "highestCombo", lng(o, "careerHighestCombo", 0L));
        r.careerHits = (int) lng(o, "careerHits", 0L);

        JsonObject totals = o.has("totals") && o.get("totals").isJsonObject()
                ? o.getAsJsonObject("totals") : o;
        r.declarationsSent = (int) lng(totals, "declarationsSent", 0L);
        r.declarationsAccepted = (int) lng(totals, "declarationsAccepted", 0L);
        r.declarationsDeclined = (int) lng(totals, "declarationsDeclined", 0L);
        r.rivalsRemoved = (int) lng(totals, "rivalsRemoved", 0L);

        r.rivals = mapLinks(o.has("rivals") && o.get("rivals").isJsonObject()
                ? o.getAsJsonObject("rivals") : null);
        r.pastRivals = mapLinks(o.has("pastRivals") && o.get("pastRivals").isJsonObject()
                ? o.getAsJsonObject("pastRivals") : null);
        r.recalcTotalRp();
        return r;
    }

    private static Map<String, RivalLink> mapLinks(JsonObject rivals) {
        Map<String, RivalLink> out = new ConcurrentHashMap<>();
        if (rivals == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : rivals.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject o = e.getValue().getAsJsonObject();
            RivalLink link = new RivalLink();
            link.uuid = str(o, "uuid", e.getKey());
            link.name = str(o, "name", "");
            link.mutual = bool(o, "mutual", false);
            link.declaredByMe = bool(o, "declaredByMe", false);
            link.declaredByThem = bool(o, "declaredByThem", false);
            link.inviteSent = bool(o, "inviteSent", false);
            link.inviteReceived = bool(o, "inviteReceived", false);
            link.points = dbl(o, "points", 0.0);
            link.wins = (int) lng(o, "wins", 0L);
            link.losses = (int) lng(o, "losses", 0L);
            link.draws = (int) lng(o, "draws", 0L);
            link.deathLosses = (int) lng(o, "deathLosses", 0L);
            link.deathWins = (int) lng(o, "deathWins", 0L);
            link.isNemesis = bool(o, "isNemesis", false);
            link.presenceMs = lng(o, "presenceMs", 0L);
            link.createdAt = lng(o, "createdAt", 0L);
            link.firstMetAt = lng(o, "firstMetAt", 0L);
            link.mutualSince = lng(o, "mutualSince", 0L);
            link.lastBattleAt = lng(o, "lastBattleAt", 0L);
            link.lastInteractAt = lng(o, "lastInteractAt", 0L);
            link.lastSeenTogetherAt = lng(o, "lastSeenTogetherAt", 0L);
            link.lastSurpassAt = lng(o, "lastSurpassAt", 0L);
            link.pendingExpireAt = lng(o, "pendingExpireAt", 0L);
            link.surpassWasBelow = bool(o, "surpassWasBelow", false);
            if (o.has("provingGrounds") && o.get("provingGrounds").isJsonObject()) {
                try {
                    link.provingGrounds = GSON.fromJson(o.get("provingGrounds"), ProvingGrounds.Grounds.class);
                    link.provingGrounds = ProvingGrounds.normalize(link.provingGrounds);
                } catch (Throwable ignored) {
                }
            }
            out.put(e.getKey(), link);
        }
        return out;
    }

    private static boolean importRivalProgression(String raw) {
        try {
            Path file = ConfigPaths.progressionPath();
            Files.createDirectories(file.getParent());
            // Write CNPC blob as-is (schema already aligned with RivalProgression.Persist).
            Files.writeString(file, raw, StandardCharsets.UTF_8);
            RivalProgression.get().load();
            RivalProgression.get().markDirty();
            return true;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Rival progression import failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return false;
        }
    }

    /* ========================= Spar import ========================= */

    private static int importSparLeaderboard(WorldBlob blob) {
        if (blob == null) {
            return 0;
        }
        Function<String, String> get = key -> {
            if (blob.fileMap != null && blob.fileMap.containsKey(key)) {
                return blob.fileMap.get(key);
            }
            if (blob.liveStored != null) {
                return storedGet(blob.liveStored, key);
            }
            return null;
        };
        List<String> names = new ArrayList<>();
        String namesCsv = blob.sparNames;
        if (notBlank(namesCsv)) {
            for (String part : namesCsv.split(",")) {
                String n = part == null ? "" : part.trim();
                if (!n.isBlank()) {
                    names.add(n);
                }
            }
        }
        if (names.isEmpty()) {
            Iterable<String> keys = blob.fileMap != null
                    ? blob.fileMap.keySet()
                    : storedKeys(blob.liveStored);
            for (String key : keys) {
                if (key != null && key.startsWith("spar.leaderboard.tp.")) {
                    names.add(key.substring("spar.leaderboard.tp.".length()));
                }
            }
        }
        RivalStore rivals = RivalStore.get();
        int imported = 0;
        for (String nameOrSafe : names) {
            String safe = safeName(nameOrSafe);
            double tp = num(get.apply("spar.leaderboard.tp." + safe), 0.0);
            if (!(tp > 0) && get.apply("spar.leaderboard.sessions." + safe) == null) {
                continue;
            }
            String uuid = resolveUuidByName(rivals, nameOrSafe);
            if (uuid == null) {
                uuid = "name:" + safe;
            }
            SparStore.LeaderboardEntry lb = SparStore.get().leaderboard.computeIfAbsent(
                    uuid, k -> new SparStore.LeaderboardEntry());
            lb.name = displayName(nameOrSafe, safe);
            lb.totalTp = Math.max(lb.totalTp, tp);
            lb.longestMs = Math.max(lb.longestMs, (long) num(get.apply("spar.leaderboard.longest." + safe), 0));
            lb.bestPayout = Math.max(lb.bestPayout, num(get.apply("spar.leaderboard.bestPayout." + safe), 0));
            lb.totalTimeMs = Math.max(lb.totalTimeMs, (long) num(get.apply("spar.leaderboard.totalTime." + safe), 0));
            lb.sessions = Math.max(lb.sessions, (int) num(get.apply("spar.leaderboard.sessions." + safe), 0));
            lb.perfectSessions = Math.max(lb.perfectSessions,
                    (int) num(get.apply("spar.leaderboard.perfectPayouts." + safe), 0));
            lb.highestCombo = Math.max(lb.highestCombo,
                    (int) num(get.apply("spar.leaderboard.highestCombo." + safe), 0));
            lb.currentStreak = Math.max(lb.currentStreak,
                    (int) num(get.apply("spar.leaderboard.currentStreak." + safe), 0));
            lb.bestStreak = Math.max(lb.bestStreak,
                    (int) num(get.apply("spar.leaderboard.bestStreak." + safe), 0));
            imported++;
        }
        if (imported > 0) {
            SparStore.get().markDirty();
        }
        return imported;
    }

    private static void migrateSparBond(ServerPlayer player, Object stored) {
        String mentorName = storedGet(stored, "spar.bond.mentorName");
        String apprenticeName = storedGet(stored, "spar.bond.apprenticeName");
        long mentorCd = (long) num(storedGet(stored, "spar.bond.mentorChangeReadyAt"), 0);
        long apprenticeCd = (long) num(storedGet(stored, "spar.bond.apprenticeChangeReadyAt"), 0);
        int streakCur = (int) num(storedGet(stored, "spar.streak.current"), 0);
        int streakBest = (int) num(storedGet(stored, "spar.streak.best"), 0);
        String streakDay = storedGet(stored, "spar.streak.lastDay");

        boolean any = notBlank(mentorName) || notBlank(apprenticeName) || streakCur > 0 || streakBest > 0;
        if (!any) {
            return;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        if (notBlank(mentorName) && (bond.mentorName == null || bond.mentorName.isBlank())) {
            bond.mentorName = mentorName;
            String mu = resolveUuidByName(RivalStore.get(), mentorName);
            if (mu != null) {
                bond.mentorUuid = mu;
            }
        }
        if (notBlank(apprenticeName) && (bond.apprenticeName == null || bond.apprenticeName.isBlank())) {
            bond.apprenticeName = apprenticeName;
            String au = resolveUuidByName(RivalStore.get(), apprenticeName);
            if (au != null) {
                bond.apprenticeUuid = au;
            }
        }
        bond.mentorChangeReadyAt = Math.max(bond.mentorChangeReadyAt, mentorCd);
        bond.apprenticeChangeReadyAt = Math.max(bond.apprenticeChangeReadyAt, apprenticeCd);
        bond.streakCurrent = Math.max(bond.streakCurrent, streakCur);
        bond.streakBest = Math.max(bond.streakBest, streakBest);
        if (notBlank(streakDay)) {
            try {
                bond.streakLastDay = Long.parseLong(streakDay.trim());
            } catch (Exception ignored) {
            }
        }
    }

    private static void migrateProgressionKeys(ServerPlayer player, Object stored) {
        // Copy known training keys if LM NBT is empty for that key.
        for (String key : storedKeys(stored)) {
            if (key == null) {
                continue;
            }
            if (!(key.startsWith("fly_training_")
                    || key.startsWith("potentialunlock_")
                    || key.startsWith("meditation_restore_")
                    || key.startsWith("med2_")
                    || key.startsWith("potential_"))) {
                continue;
            }
            String existing = ProgressionData.storedGet(player, key, null);
            if (existing != null && !existing.isBlank()) {
                continue;
            }
            String v = storedGet(stored, key);
            if (v != null) {
                ProgressionData.storedPut(player, key, v);
            }
        }
        // Kill-TP chat pref shared with End / Rival messaging
        String killTp = storedGet(stored, "dmz_kill_tp_chat");
        if (killTp != null) {
            ProgressionData.storedPut(player, "dmz_kill_tp_chat", killTp);
            // Keep CNPC copy too — End scripts / other packs may still read it.
        }
    }

    /* ========================= Clear ========================= */

    private static void clearWorldCnpcKeys(Object stored) {
        storedRemove(stored, RIVAL_DB);
        storedRemove(stored, RIVAL_DB_BAK);
        storedRemove(stored, RIVAL_PROG);
        storedRemove(stored, RIVAL_PROG_BAK);
        storedRemove(stored, RIVAL_CH);
        storedRemove(stored, RIVAL_CH_BAK);
        storedRemove(stored, SPAR_LB_NAMES);
        List<String> toRemove = new ArrayList<>();
        for (String key : storedKeys(stored)) {
            if (key == null) {
                continue;
            }
            if (key.startsWith("spar.leaderboard.")
                    || key.startsWith("dlr.rivalry.v4.challenge.announce.")
                    || key.startsWith("dlr.rivalry.v4.challenge.end.")) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            storedRemove(stored, key);
        }
    }

    private static void clearPlayerSparKeys(Object stored) {
        storedRemove(stored, "spar.bond.mentorName");
        storedRemove(stored, "spar.bond.apprenticeName");
        storedRemove(stored, "spar.bond.mentorChangeReadyAt");
        storedRemove(stored, "spar.bond.apprenticeChangeReadyAt");
        storedRemove(stored, "spar.bond.inviteFrom");
        storedRemove(stored, "spar.bond.inviteKind");
        storedRemove(stored, "spar.bond.inviteUntil");
        storedRemove(stored, "spar.streak.current");
        storedRemove(stored, "spar.streak.best");
        storedRemove(stored, "spar.streak.lastDay");
    }

    private static void clearPlayerProgressionKeys(Object stored) {
        List<String> toRemove = new ArrayList<>();
        for (String key : storedKeys(stored)) {
            if (key == null) {
                continue;
            }
            if (key.startsWith("fly_training_")
                    || key.startsWith("potentialunlock_")
                    || key.startsWith("meditation_restore_")
                    || key.startsWith("med2_")
                    || key.startsWith("potential_")) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            storedRemove(stored, key);
        }
    }

    /* ========================= CNPC reflection ========================= */

    private static boolean cnpcAvailable() {
        try {
            Class<?> npcApi = Class.forName("noppes.npcs.api.NpcAPI");
            Object available = npcApi.getMethod("IsAvailable").invoke(null);
            return available instanceof Boolean ok && ok;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object overworldStoreddata() {
        try {
            Class<?> npcApi = Class.forName("noppes.npcs.api.NpcAPI");
            Object available = npcApi.getMethod("IsAvailable").invoke(null);
            if (!(available instanceof Boolean ok) || !ok) {
                return null;
            }
            Object api = npcApi.getMethod("Instance").invoke(null);
            Object world = null;
            try {
                world = api.getClass().getMethod("getIWorld", String.class).invoke(api, "minecraft:overworld");
            } catch (Throwable ignored) {
            }
            if (world == null) {
                try {
                    world = api.getClass().getMethod("getIWorld", String.class).invoke(api, "overworld");
                } catch (Throwable ignored) {
                }
            }
            if (world == null) {
                return null;
            }
            return world.getClass().getMethod("getStoreddata").invoke(world);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object playerStoreddata(ServerPlayer player) {
        try {
            Class<?> npcApi = Class.forName("noppes.npcs.api.NpcAPI");
            Object available = npcApi.getMethod("IsAvailable").invoke(null);
            if (!(available instanceof Boolean ok) || !ok) {
                return null;
            }
            Object api = npcApi.getMethod("Instance").invoke(null);
            Object entity = api.getClass()
                    .getMethod("getIEntity", net.minecraft.world.entity.Entity.class)
                    .invoke(api, player);
            if (entity == null) {
                return null;
            }
            return entity.getClass().getMethod("getStoreddata").invoke(entity);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String storedGet(Object stored, String key) {
        if (stored == null || key == null) {
            return null;
        }
        try {
            Object has = stored.getClass().getMethod("has", String.class).invoke(stored, key);
            if (has instanceof Boolean b && !b) {
                return null;
            }
            Object v = stored.getClass().getMethod("get", String.class).invoke(stored, key);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void storedRemove(Object stored, String key) {
        if (stored == null || key == null) {
            return;
        }
        try {
            stored.getClass().getMethod("remove", String.class).invoke(stored, key);
        } catch (Throwable t1) {
            try {
                // Some CNPC builds use put("",…) or clear via put null
                stored.getClass().getMethod("put", String.class, String.class).invoke(stored, key, "");
            } catch (Throwable ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> storedKeys(Object stored) {
        List<String> out = new ArrayList<>();
        if (stored == null) {
            return out;
        }
        try {
            Object keys = stored.getClass().getMethod("getKeys").invoke(stored);
            if (keys instanceof Iterable<?> it) {
                for (Object o : it) {
                    if (o != null) {
                        out.add(String.valueOf(o));
                    }
                }
                return out;
            }
            if (keys instanceof String[] arr) {
                for (String s : arr) {
                    if (s != null) {
                        out.add(s);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /* ========================= helpers ========================= */

    private static void backupRaw(Path dir, String name, String raw) {
        if (!notBlank(raw)) {
            return;
        }
        try {
            Files.writeString(dir.resolve(name), raw, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static void writeMarker(Path marker, String note) throws Exception {
        Files.writeString(marker, note == null ? "done" : note, StandardCharsets.UTF_8);
    }

    private static JsonObject parseObject(String raw) {
        try {
            JsonElement el = JsonParser.parseString(raw);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String resolveUuidByName(RivalStore store, String name) {
        if (name == null || name.isBlank() || store == null) {
            return null;
        }
        String want = name.trim().toLowerCase(Locale.ROOT);
        for (RivalPlayerRecord rec : store.players.values()) {
            if (rec != null && rec.name != null && rec.name.toLowerCase(Locale.ROOT).equals(want)) {
                return rec.uuid;
            }
        }
        return null;
    }

    private static String safeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^A-Za-z0-9_\\-]", "_").toLowerCase(Locale.ROOT);
    }

    private static String displayName(String original, String safe) {
        if (original == null || original.isBlank()) {
            return safe;
        }
        return original;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsString();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static long lng(JsonObject o, String key, long fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsLong();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static double dbl(JsonObject o, String key, double fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsDouble();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsBoolean();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static double num(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
