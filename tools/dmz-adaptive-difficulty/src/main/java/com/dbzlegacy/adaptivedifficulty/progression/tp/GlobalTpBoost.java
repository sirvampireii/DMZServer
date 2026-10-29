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
 * Port of Global TP Boost.js + TP boost end.js (triggers 30/31).
 * <p>
 * Active window is persisted to {@code config/legacymechanics/global-tp-boost.json}
 * so a restart mid-boost keeps the remaining time. Online players lose the effect
 * when the window ends; anyone who joins while it is still active receives it;
 * leftover effect is stripped on login if the boost already ended.
 */
public final class GlobalTpBoost {
    private static final int ENCODED_MINUTE_DIVISOR = 10_000;
    private static final int EFFECT_REFRESH_BUFFER_TICKS = 100;
    private static final long TRIGGER_LOCK_MS = 3000L;
    /** Script TP boost end.js END_LOCK_MS — debounce duplicate end. */
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

    private GlobalTpBoost() {}

    /** Load persisted window on server start (before players join). */
    public static synchronized void load() {
        LOADED.set(true);
        Path file = ConfigPaths.globalTpBoostPath();
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
                        "[{}] Global TP boost restored: {}x until {} (by {})",
                        AdaptiveDifficultyMod.MOD_ID,
                        formatMult(blob.multiplier),
                        blob.endTimeMs,
                        PURCHASER.get());
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Global TP boost load failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
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
        if (!ProgressionConfig.globalTpBoost() || server == null) {
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
        if (!ProgressionConfig.globalTpBoost() || player == null) {
            return;
        }
        if (!ACTIVE.get()) {
            // Boost ended while they were offline — strip leftover DMZ TP_GAIN.
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

    /** Trigger 30 / {@code /progression boost start}. */
    public static String startBoost(ServerPlayer actor, int encoded, String purchaser) {
        if (!ProgressionConfig.globalTpBoost()) {
            return "§cGlobal TP Boost is disabled.";
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
            return "§7Boost trigger locked (duplicate).";
        }
        TRIGGER_LOCK.set(now + TRIGGER_LOCK_MS);
        TRIGGER_SIGNATURE.set(sig);
        String who = purchaser == null || purchaser.isBlank()
                ? (actor == null ? "Server" : actor.m_7755_().getString())
                : purchaser;
        String result = activate(decoded.multiplier, decoded.durationMinutes, who);
        SystemTelemetry.log("progression", "tp_boost_start", actor, null,
                Map.of("mult", decoded.multiplier, "minutes", decoded.durationMinutes, "purchaser", who));
        return result;
    }

    /**
     * Reply for a {@code start <encoded>} value that does not decode (for example 2, which reads as
     * a 0.0 multiplier): names the value and both valid start forms.
     */
    public static String undecodedReply(int encoded) {
        return UltraStyle.DANGER + "Boost value " + UltraStyle.WHITE + encoded + UltraStyle.DANGER
                + " was not understood. Use one of these:\n"
                + UltraStyle.YELLOW + "progression boost start <encoded>" + UltraStyle.GRAY
                + " - for example " + UltraStyle.WHITE + "2000030" + UltraStyle.GRAY + " for 2x, 30 minutes\n"
                + UltraStyle.YELLOW + "progression boost start <multiplier> <minutes>" + UltraStyle.GRAY
                + " - for example " + UltraStyle.WHITE + "progression boost start 2 30";
    }

    public static String startBoostMinutes(ServerPlayer actor, double multiplier, int minutes, String purchaser) {
        if (!ProgressionConfig.globalTpBoost()) {
            return "§cGlobal TP Boost is disabled.";
        }
        String who = purchaser == null || purchaser.isBlank()
                ? (actor == null ? "Server" : actor.m_7755_().getString())
                : purchaser;
        String result = activate(multiplier, minutes, who);
        SystemTelemetry.log("progression", "tp_boost_start", actor, null,
                Map.of("mult", multiplier, "minutes", minutes, "purchaser", who));
        return result;
    }

    /** Trigger 31 / {@code /progression boost end}. */
    public static String endBoost(boolean announce) {
        long now = System.currentTimeMillis();
        if (now < END_LOCK.get()) {
            return "§7Global TP boost end is on cooldown.";
        }
        if (!ACTIVE.get() && AMPLIFIER.get() < 0) {
            return "§7No global TP boost is active.";
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
                broadcast("§6§lGLOBAL TP BOOST §r§7- §cThe global TP boost has ended.");
            }
        }
        SystemTelemetry.log("progression", "tp_boost_end", null, null, Map.of());
        return "§aGlobal TP boost ended.";
    }

    public static String statusLine() {
        if (!ACTIVE.get()) {
            return "§7Global TP boost: §cOFF";
        }
        long rem = Math.max(0L, END_TIME.get() - System.currentTimeMillis());
        return "§7Global TP boost: §a" + formatMult(MULTIPLIER.get())
                + "x §7(" + formatDuration(rem) + " left, by §f" + PURCHASER.get() + "§7)";
    }

    public static boolean isActive() {
        return ACTIVE.get() && END_TIME.get() > System.currentTimeMillis();
    }

    private static String activate(double multiplier, int durationMinutes, String purchaser) {
        int amplifier = multiplierToAmplifier(multiplier);
        if (amplifier < 0) {
            return "§cMultiplier must be 1.25+ in 0.25 steps.";
        }
        if (durationMinutes <= 0) {
            return "§cDuration must be > 0 minutes.";
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
            return "§cMultiplier must be 1.25+ in 0.25 steps.";
        }
        long newEnd = extending ? Math.max(now, currentEnd) + durationMs : now + durationMs;

        ACTIVE.set(true);
        MULTIPLIER.set(keptMult);
        AMPLIFIER.set(keptAmp);
        END_TIME.set(newEnd);
        PURCHASER.set(purchaser == null ? "" : purchaser);
        persistNow();

        applyToAll(keptAmp, newEnd - now);
        broadcast("§6§lGLOBAL TP BOOST ACTIVATED!");
        if (extending) {
            broadcast("§e" + purchaser + " §7added §f" + formatDuration(durationMs)
                    + " §7to the global TP boost.");
            if (keptMult > prevMult + 0.0001) {
                broadcast("§7Boost raised to §a" + formatMult(keptMult) + "x§7.");
            } else {
                broadcast("§7Boost stays §a" + formatMult(keptMult)
                        + "x §8(highest wins; lower packs only add time).");
            }
            broadcast("§7Total remaining: §f" + formatDuration(newEnd - now) + "§7.");
            return "§aStacked +" + formatDuration(durationMs) + " · active §f"
                    + formatMult(keptMult) + "x §7(" + formatDuration(newEnd - now) + " left).";
        }
        broadcast("§e" + purchaser + " §7activated a §a" + formatMult(keptMult)
                + "x TP Boost§7!");
        broadcast("§7All online players receive boosted TP for §f"
                + formatDuration(newEnd - now) + "§7.");
        return "§a" + formatMult(keptMult) + "x TP activated for "
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
            MobEffect effect = MainEffects.TP_GAIN.get();
            if (effect == null || player == null) {
                return;
            }
            // Replace any existing TP_GAIN so duration matches the global window.
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
            MobEffect effect = MainEffects.TP_GAIN.get();
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
        Path file = ConfigPaths.globalTpBoostPath();
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
                    "[{}] Global TP boost save failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    private static void deletePersistFile() {
        try {
            Files.deleteIfExists(ConfigPaths.globalTpBoostPath());
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

    /** Disk blob — wall-clock end so restarts keep remaining time. */
    @SuppressWarnings("unused")
    private static final class Persist {
        boolean active;
        double multiplier;
        long endTimeMs;
        String purchaser;
    }
}
