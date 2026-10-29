package com.dbzlegacy.adaptivedifficulty.progression.tp;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionConfig;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dragonminez.common.init.MainEffects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Global Mastery Boost: the {@link GlobalTpBoost} window applied to DMZ's
 * {@code MainEffects.MASTERY_GAIN}. DMZ already scales form mastery gain through
 * {@code PotionEffectHelper.applyMasteryGainMultiplier}, so no hook of our own is needed.
 * <p>
 * Active window is persisted to {@code config/legacymechanics/global-mastery-boost.json}
 * so a restart mid-boost keeps the remaining time. Online players lose the effect
 * when the window ends; anyone who joins while it is still active receives it;
 * leftover effect is stripped on login if the boost already ended.
 */
public final class GlobalMasteryBoost {
    private static final int ENCODED_MINUTE_DIVISOR = 10_000;
    private static final int EFFECT_REFRESH_BUFFER_TICKS = 100;
    private static final long TRIGGER_LOCK_MS = 3000L;
    /** Debounce duplicate end, same as the TP boost. */
    private static final long END_LOCK_MS = 5000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicReference<Double> MULTIPLIER = new AtomicReference<>(0.0);
    private static final AtomicInteger AMPLIFIER = new AtomicInteger(-1);
    private static final AtomicLong END_TIME = new AtomicLong(0L);
    private static final AtomicReference<String> PURCHASER = new AtomicReference<>("");
    private static final AtomicLong TRIGGER_LOCK = new AtomicLong(0L);
    private static final AtomicReference<String> TRIGGER_SIGNATURE = new AtomicReference<>("");
    private static final AtomicLong END_LOCK = new AtomicLong(0L);
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private GlobalMasteryBoost() {}

    /** Load persisted window on server start (before players join). */
    public static synchronized void load() {
        LOADED.set(true);
        Path file = ConfigPaths.globalMasteryBoostPath();
        try {
            if (!Files.isRegularFile(file)) {
                clearMemory();
                return;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                Persist blob = GSON.fromJson(reader, Persist.class);
                if (blob == null || !blob.active || blob.endTimeMs <= System.currentTimeMillis()) {
                    clearMemory();
                    deletePersistFile();
                    return;
                }
                int amp = multiplierToAmplifier(blob.multiplier);
                if (amp < 0) {
                    clearMemory();
                    deletePersistFile();
                    return;
                }
                ACTIVE.set(true);
                MULTIPLIER.set(blob.multiplier);
                AMPLIFIER.set(amp);
                END_TIME.set(blob.endTimeMs);
                PURCHASER.set(blob.purchaser == null ? "" : blob.purchaser);
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] Global mastery boost restored: {}x until {} (by {})",
                        AdaptiveDifficultyMod.MOD_ID,
                        formatMult(blob.multiplier),
                        blob.endTimeMs,
                        PURCHASER.get());
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Global mastery boost load failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            clearMemory();
        }
    }

    /** Flush active window (or clear file) on shutdown. */
    public static synchronized void save() {
        if (!LOADED.get()) {
            return;
        }
        persistNow();
    }

    public static void pulse(MinecraftServer server, int tick) {
        if (!ProgressionConfig.globalMasteryBoost() || server == null) {
            return;
        }
        if (tick % 20 != 0) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            if (!ACTIVE.get()) {
                return;
            }
            long end = END_TIME.get();
            if (end <= now) {
                endBoost(true);
                return;
            }
            int amp = AMPLIFIER.get();
            if (amp < 0) {
                return;
            }
            long remainingMs = end - now;
            int durationTicks = Math.max(
                    EFFECT_REFRESH_BUFFER_TICKS,
                    (int) Math.ceil(remainingMs / 50.0) + EFFECT_REFRESH_BUFFER_TICKS
            );
            for (ServerPlayer player : server.m_6846_().m_11314_()) {
                applyEffect(player, amp, durationTicks);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void onLogin(ServerPlayer player) {
        if (!ProgressionConfig.globalMasteryBoost() || player == null) {
            return;
        }
        if (!ACTIVE.get()) {
            // Boost ended while they were offline - strip leftover DMZ MASTERY_GAIN.
            removeEffect(player);
            return;
        }
        long remaining = END_TIME.get() - System.currentTimeMillis();
        if (remaining <= 0L) {
            removeEffect(player);
            return;
        }
        int amp = AMPLIFIER.get();
        if (amp < 0) {
            return;
        }
        int ticks = Math.max(
                EFFECT_REFRESH_BUFFER_TICKS,
                (int) Math.ceil(remaining / 50.0) + EFFECT_REFRESH_BUFFER_TICKS
        );
        applyEffect(player, amp, ticks);
    }

    /**
     * Reply for a {@code start <encoded>} value that does not decode (for example 2, which reads as
     * a 0.0 multiplier): names the value and both valid start forms.
     */
    public static String undecodedReply(int encoded) {
        return undecodedReply(String.valueOf(encoded));
    }

    /** Same reply for a typed value that is not a whole number (for example {@code start 2.5}). */
    public static String undecodedReply(String encoded) {
        return UltraStyle.DANGER + "Boost value " + UltraStyle.WHITE + encoded + UltraStyle.DANGER
                + " was not understood. Use one of these:\n"
                + UltraStyle.YELLOW + "progression masteryboost start <encoded>" + UltraStyle.GRAY
                + " - for example " + UltraStyle.WHITE + "2000030" + UltraStyle.GRAY + " for 2x, 30 minutes\n"
                + UltraStyle.YELLOW + "progression masteryboost start <multiplier> <minutes>" + UltraStyle.GRAY
                + " - for example " + UltraStyle.WHITE + "progression masteryboost start 2 30";
    }

    /** {@code /progression masteryboost start <encoded>}. */
    public static String startBoost(ServerPlayer actor, int encoded, String purchaser) {
        if (!ProgressionConfig.globalMasteryBoost()) {
            return disabledMessage();
        }
        Decoded decoded = decode(encoded);
        if (decoded == null) {
            return undecodedReply(encoded);
        }
        long now = System.currentTimeMillis();
        String sig = (actor == null ? "console" : actor.m_7755_().getString().toLowerCase())
                + "|" + decoded.encoded + "|"
                + (purchaser == null ? "" : purchaser.toLowerCase());
        if (now < TRIGGER_LOCK.get() && sig.equals(TRIGGER_SIGNATURE.get())) {
            return UltraStyle.LABEL + "That mastery boost was just started. Wait a few seconds before sending it again.";
        }
        TRIGGER_LOCK.set(now + TRIGGER_LOCK_MS);
        TRIGGER_SIGNATURE.set(sig);
        String who = purchaser == null || purchaser.isBlank()
                ? (actor == null ? "Server" : actor.m_7755_().getString())
                : purchaser;
        String result = activate(decoded.multiplier, decoded.durationMinutes, who);
        SystemTelemetry.log("progression", "mastery_boost_start", actor, null,
                Map.of("mult", decoded.multiplier, "minutes", decoded.durationMinutes, "purchaser", who));
        return result;
    }

    public static String startBoostMinutes(ServerPlayer actor, double multiplier, int minutes, String purchaser) {
        if (!ProgressionConfig.globalMasteryBoost()) {
            return disabledMessage();
        }
        String who = purchaser == null || purchaser.isBlank()
                ? (actor == null ? "Server" : actor.m_7755_().getString())
                : purchaser;
        String result = activate(multiplier, minutes, who);
        SystemTelemetry.log("progression", "mastery_boost_start", actor, null,
                Map.of("mult", multiplier, "minutes", minutes, "purchaser", who));
        return result;
    }

    /** {@code /progression masteryboost end}. */
    public static String endBoost(boolean announce) {
        long now = System.currentTimeMillis();
        if (now < END_LOCK.get()) {
            return UltraStyle.LABEL + "The mastery boost was just ended. Wait a few seconds before trying again.";
        }
        if (!ACTIVE.get() && AMPLIFIER.get() < 0) {
            return UltraStyle.LABEL + "No global mastery boost is running.";
        }
        END_LOCK.set(now + END_LOCK_MS);
        clearMemory();
        persistNow();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.m_6846_().m_11314_()) {
                removeEffect(player);
            }
            if (announce) {
                broadcast(UltraStyle.GOLD + UltraStyle.BOLD + "GLOBAL MASTERY BOOST " + UltraStyle.RESET
                        + UltraStyle.GRAY + "- " + UltraStyle.RED + "The global mastery boost has ended.");
            }
        }
        SystemTelemetry.log("progression", "mastery_boost_end", null, null, Map.of());
        return UltraStyle.GREEN + "Global mastery boost ended.";
    }

    public static String statusLine() {
        if (!ACTIVE.get()) {
            return UltraStyle.GRAY + "Global mastery boost: " + UltraStyle.RED + "OFF";
        }
        long rem = Math.max(0L, END_TIME.get() - System.currentTimeMillis());
        return UltraStyle.GRAY + "Global mastery boost: " + UltraStyle.GREEN + formatMult(MULTIPLIER.get())
                + "x " + UltraStyle.GRAY + "(" + formatDuration(rem) + " left, by "
                + UltraStyle.WHITE + PURCHASER.get() + UltraStyle.GRAY + ")";
    }

    public static boolean isActive() {
        return ACTIVE.get() && END_TIME.get() > System.currentTimeMillis();
    }

    private static String disabledMessage() {
        return UltraStyle.DANGER + "Global Mastery Boost is disabled. Turn it on in the progression settings first.";
    }

    private static String activate(double multiplier, int durationMinutes, String purchaser) {
        int amplifier = multiplierToAmplifier(multiplier);
        if (amplifier < 0) {
            return UltraStyle.DANGER + "The multiplier must be 1.25 or higher in steps of 0.25, like 1.25, 1.5, or 2.";
        }
        if (durationMinutes <= 0) {
            return UltraStyle.DANGER + "The duration must be at least 1 minute.";
        }
        long now = System.currentTimeMillis();
        long durationMs = durationMinutes * 60_000L;
        long currentEnd = END_TIME.get();
        boolean extending = ACTIVE.get() && currentEnd > now;
        // Stacking: always add duration; never lower an active multiplier.
        double prevMult = extending ? MULTIPLIER.get() : 0.0;
        double keptMult = extending ? Math.max(prevMult, multiplier) : multiplier;
        int keptAmp = multiplierToAmplifier(keptMult);
        if (keptAmp < 0) {
            return UltraStyle.DANGER + "The multiplier must be 1.25 or higher in steps of 0.25, like 1.25, 1.5, or 2.";
        }
        long newEnd = extending ? Math.max(now, currentEnd) + durationMs : now + durationMs;

        ACTIVE.set(true);
        MULTIPLIER.set(keptMult);
        AMPLIFIER.set(keptAmp);
        END_TIME.set(newEnd);
        PURCHASER.set(purchaser == null ? "" : purchaser);
        persistNow();

        applyToAll(keptAmp, newEnd - now);
        broadcast(UltraStyle.GOLD + UltraStyle.BOLD + "GLOBAL MASTERY BOOST ACTIVATED!");
        if (extending) {
            broadcast(UltraStyle.YELLOW + purchaser + UltraStyle.GRAY + " added " + UltraStyle.WHITE
                    + formatDuration(durationMs) + UltraStyle.GRAY + " to the global mastery boost.");
            if (keptMult > prevMult + 0.0001) {
                broadcast(UltraStyle.GRAY + "Boost raised to " + UltraStyle.GREEN + formatMult(keptMult)
                        + "x" + UltraStyle.GRAY + ".");
            } else {
                broadcast(UltraStyle.GRAY + "Boost stays " + UltraStyle.GREEN + formatMult(keptMult)
                        + "x " + UltraStyle.DARK_GRAY + "(highest wins; lower packs only add time).");
            }
            broadcast(UltraStyle.GRAY + "Total remaining: " + UltraStyle.WHITE + formatDuration(newEnd - now)
                    + UltraStyle.GRAY + ".");
            return UltraStyle.GREEN + "Stacked +" + formatDuration(durationMs) + " " + UltraStyle.SEP
                    + " active " + UltraStyle.WHITE + formatMult(keptMult) + "x " + UltraStyle.GRAY
                    + "(" + formatDuration(newEnd - now) + " left).";
        }
        broadcast(UltraStyle.YELLOW + purchaser + UltraStyle.GRAY + " activated a " + UltraStyle.GREEN
                + formatMult(keptMult) + "x Mastery Boost" + UltraStyle.GRAY + "!");
        broadcast(UltraStyle.GRAY + "All online players gain form mastery faster for " + UltraStyle.WHITE
                + formatDuration(newEnd - now) + UltraStyle.GRAY + ".");
        return UltraStyle.GREEN + formatMult(keptMult) + "x mastery activated for "
                + formatDuration(durationMs) + ".";
    }

    private static void applyToAll(int amplifier, long remainingMs) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        int ticks = Math.max(
                EFFECT_REFRESH_BUFFER_TICKS,
                (int) Math.ceil(remainingMs / 50.0) + EFFECT_REFRESH_BUFFER_TICKS
        );
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            applyEffect(player, amplifier, ticks);
        }
    }

    private static void applyEffect(ServerPlayer player, int amplifier, int durationTicks) {
        try {
            if (DeathTpPenalty.active(player)) {
                return;
            }
            MobEffect effect = MainEffects.MASTERY_GAIN.get();
            if (effect == null || player == null) {
                return;
            }
            // Replace any existing MASTERY_GAIN so duration matches the global window.
            player.m_21195_(effect);
            player.m_7292_(new MobEffectInstance(
                    effect,
                    Math.max(1, durationTicks),
                    Math.max(0, amplifier),
                    false,
                    true,
                    true
            ));
        } catch (Throwable ignored) {
        }
    }

    private static void removeEffect(ServerPlayer player) {
        try {
            if (DeathTpPenalty.active(player)) {
                return;
            }
            MobEffect effect = MainEffects.MASTERY_GAIN.get();
            if (effect != null && player != null) {
                player.m_21195_(effect);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clearMemory() {
        ACTIVE.set(false);
        MULTIPLIER.set(0.0);
        AMPLIFIER.set(-1);
        END_TIME.set(0L);
        PURCHASER.set("");
    }

    private static synchronized void persistNow() {
        Path file = ConfigPaths.globalMasteryBoostPath();
        try {
            Files.createDirectories(file.getParent());
            if (!ACTIVE.get() || END_TIME.get() <= System.currentTimeMillis()) {
                deletePersistFile();
                return;
            }
            Persist blob = new Persist();
            blob.active = true;
            blob.multiplier = MULTIPLIER.get();
            blob.endTimeMs = END_TIME.get();
            blob.purchaser = PURCHASER.get();
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(blob, writer);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Global mastery boost save failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    private static void deletePersistFile() {
        try {
            Files.deleteIfExists(ConfigPaths.globalMasteryBoostPath());
        } catch (Throwable ignored) {
        }
    }

    /** True when {@code start <encoded>} would pass decode and the activate checks (self-test, no side effects). */
    public static boolean encodedWouldStart(int encoded) {
        Decoded d = decode(encoded);
        return d != null && minutesWouldStart(d.multiplier, d.durationMinutes);
    }

    /** True when {@code start <multiplier> <minutes>} would pass the activate checks (self-test, no side effects). */
    public static boolean minutesWouldStart(double multiplier, int minutes) {
        return multiplierToAmplifier(multiplier) >= 0 && minutes > 0;
    }

    private static Decoded decode(int encoded) {
        if (encoded <= 0) {
            return null;
        }
        int multiplierCode = encoded / ENCODED_MINUTE_DIVISOR;
        int durationMinutes = encoded % ENCODED_MINUTE_DIVISOR;
        double multiplier = multiplierCode / 100.0;
        if (multiplier <= 1.0 || durationMinutes <= 0) {
            return null;
        }
        return new Decoded(encoded, multiplier, durationMinutes);
    }

    private static int multiplierToAmplifier(double multiplier) {
        if (!(multiplier >= 1.25)) {
            return -1;
        }
        int amplifier = (int) Math.round(((multiplier - 1.0) / 0.25) - 1.0);
        double confirmed = 1.0 + ((amplifier + 1) * 0.25);
        if (Math.abs(confirmed - multiplier) > 0.0001 || amplifier < 0) {
            return -1;
        }
        return amplifier;
    }

    private static void broadcast(String msg) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            DmzRewards.msg(player, msg);
        }
    }

    private static String formatMult(double m) {
        if (Math.abs(m - Math.rint(m)) < 0.001) {
            return Integer.toString((int) Math.rint(m));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", m);
    }

    private static String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long minutes = totalSec / 60L;
        long sec = totalSec % 60L;
        if (minutes < 60) {
            if (minutes <= 0) {
                return sec + "s";
            }
            return minutes + "m" + (sec > 0 ? " " + sec + "s" : "");
        }
        long hours = minutes / 60;
        long rem = minutes % 60;
        return hours + "h" + (rem > 0 ? " " + rem + "m" : "");
    }

    private record Decoded(int encoded, double multiplier, int durationMinutes) {}

    /** Disk blob - wall-clock end so restarts keep remaining time. */
    @SuppressWarnings("unused")
    private static final class Persist {
        boolean active;
        double multiplier;
        long endTimeMs;
        String purchaser;
    }
}
