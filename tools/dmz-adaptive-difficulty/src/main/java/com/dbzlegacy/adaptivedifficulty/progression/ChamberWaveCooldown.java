package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player cooldown on starting chamber wave runs (4.7.28, config {@code chamberWaveCooldownSeconds},
 * default 120, 0 = off). The timer starts when a run ends ({@code GodChamberWaves.stop}); training
 * (Popo's and Whis's training) and Whis's God Trial keep separate timers. Angel Drills and sanctuary
 * Demand bouts ({@code Config.drill() > 0}) and ops are exempt.
 *
 * <p>End times persist to {@code config/legacymechanics/chamber-wave-cooldown.json}, written on every
 * run end, so a restart (or a crash) never resets a timer. Called from the dmzultra chamber mixins.
 */
public final class ChamberWaveCooldown {
    public static final int TRAINING = 0;
    public static final int TRIAL = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<HashMap<String, Ends>>() {}.getType();

    /** Player UUID string -> last run end per kind (epoch millis; 0 = none). */
    private static Map<String, Ends> ends;

    private ChamberWaveCooldown() {}

    static final class Ends {
        long training;
        long trial;

        long get(int kind) {
            return kind == TRIAL ? trial : training;
        }

        void set(int kind, long at) {
            if (kind == TRIAL) {
                trial = at;
            } else {
                training = at;
            }
        }
    }

    public static long cooldownMillis() {
        return Math.max(0, DifficultyConfig.get().chamberWaveCooldownSeconds) * 1000L;
    }

    /** A run of {@code kind} ended for this player now; persists at once. */
    public static synchronized void recordEnd(UUID player, int kind) {
        if (player == null || (kind != TRAINING && kind != TRIAL)) {
            return;
        }
        Map<String, Ends> map = loaded();
        map.computeIfAbsent(player.toString(), k -> new Ends()).set(kind, System.currentTimeMillis());
        save(map);
    }

    /** Millis left before this player may start a run of {@code kind}; 0 when free. */
    public static synchronized long remaining(UUID player, int kind, long now) {
        long cooldown = cooldownMillis();
        if (player == null || cooldown <= 0L) {
            return 0L;
        }
        Ends e = loaded().get(player.toString());
        long last = e == null ? 0L : e.get(kind);
        return last <= 0L ? 0L : Math.max(0L, last + cooldown - now);
    }

    /**
     * True, after telling the player the time left on the action bar, when this start must be
     * refused. Drills / Demand bouts and ops are never refused.
     */
    public static boolean refuseStart(ServerPlayer player, boolean trial, int drill) {
        if (player == null || drill > 0 || StaffAccess.isOp(player)) {
            return false;
        }
        int kind = trial ? TRIAL : TRAINING;
        long left = remaining(player.m_20148_(), kind, System.currentTimeMillis());
        if (left <= 0L) {
            return false;
        }
        String what = trial ? "The God Trial" : "Training waves";
        player.m_5661_(Component.m_237113_(what + " can start again in " + formatLeft(left) + ".")
                .m_130940_(ChatFormatting.YELLOW), true);
        return true;
    }

    /**
     * Endless waves are off (4.7.29): Ultra's {@code Config.clamp()} turns a wave count of 0 or less
     * into an endless run, so such a start is refused with an action-bar line. Applies to training
     * and trial alike; Angel Drills and sanctuary Demands ({@code drill > 0}) set their own counts and
     * are exempt.
     */
    public static boolean refuseEndless(ServerPlayer player, int waves, int drill) {
        if (player == null || drill > 0 || waves > 0) {
            return false;
        }
        player.m_5661_(Component.m_237113_("Endless waves are disabled. Pick a number of waves.")
                .m_130940_(ChatFormatting.YELLOW), true);
        return true;
    }

    /** "1:05", "0:09" (m:ss), rounded up to the next second. */
    static String formatLeft(long millis) {
        long total = Math.max(1L, (millis + 999L) / 1000L);
        long s = total % 60L;
        return (total / 60L) + ":" + (s < 10L ? "0" : "") + s;
    }

    private static Map<String, Ends> loaded() {
        if (ends != null) {
            return ends;
        }
        ends = new HashMap<>();
        Path file = ConfigPaths.chamberWaveCooldownPath();
        try {
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    Map<String, Ends> read = GSON.fromJson(reader, STORE_TYPE);
                    if (read != null) {
                        read.values().removeIf(v -> v == null);
                        ends.putAll(read);
                    }
                }
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] chamber wave cooldown load failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
        return ends;
    }

    /** Drops timers that already ran out, then writes the file through a temp + move. */
    private static void save(Map<String, Ends> map) {
        long cutoff = System.currentTimeMillis() - cooldownMillis();
        map.values().removeIf(e -> e.training <= cutoff && e.trial <= cutoff);
        Path file = ConfigPaths.chamberWaveCooldownPath();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(map, STORE_TYPE, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] chamber wave cooldown save failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }
}
