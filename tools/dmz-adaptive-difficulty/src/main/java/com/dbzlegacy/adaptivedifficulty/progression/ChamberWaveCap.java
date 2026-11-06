package com.dbzlegacy.adaptivedifficulty.progression;

import com.dmzultra.divine.GodChamberWaves;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * God Chamber training cap (4.7.37, owner task 2026-11-05 "cap God Chamber training waves at 10 waves
 * and 5 foes per wave"). Ultra lets a training run pick up to 30 waves of 8 foes ({@code MAX_WAVES},
 * {@code MAX_PER_WAVE}); LM clamps a start to {@link #MAX_WAVES} x {@link #MAX_PER_WAVE} and the run
 * starts with the capped values (no refusal). Trial requests are capped too: Ultra turns a trial asked for
 * in the Time Chamber, or by a player who already owns God Ki, into training with the requested numbers,
 * while a real God Trial is rebuilt as a fixed 5 x 5 whatever was sent, so it is unaffected. Drills are left
 * exactly as Ultra built them. When a cap applied and a training run really started, the player is told
 * once on the action bar. Endless runs (0 waves) are still refused by
 * {@link ChamberWaveCooldown#refuseEndless}.
 */
public final class ChamberWaveCap {
    /** Most waves a training run may have (Ultra allows 30). */
    public static final int MAX_WAVES = 10;
    /** Most foes per wave in a training run (Ultra allows 8). */
    public static final int MAX_PER_WAVE = 5;
    /** Cap notice waiting for {@code start} to return, by player; only kept when no run was going. */
    private static final Map<UUID, String> PENDING = new ConcurrentHashMap<>();

    private ChamberWaveCap() {}

    /** The config with the caps applied (not drills); the same object when nothing changes. */
    public static GodChamberWaves.Config cap(GodChamberWaves.Config config) {
        if (config == null || config.drill() > 0) {
            return config;
        }
        int waves = Math.min(MAX_WAVES, config.waves());
        int perWave = Math.min(MAX_PER_WAVE, config.perWave());
        if (waves == config.waves() && perWave == config.perWave()) {
            return config;
        }
        return new GodChamberWaves.Config(config.trial(), perWave, waves, config.enemy(), config.powerPct(), config.drill());
    }

    /** Plain words for what {@link #cap} changed, or {@code null} when nothing was capped. */
    public static String notice(GodChamberWaves.Config before, GodChamberWaves.Config after) {
        if (before == null || after == null || before == after) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (after.waves() != before.waves()) {
            text.append("Wave count capped at ").append(MAX_WAVES).append('.');
        }
        if (after.perWave() != before.perWave()) {
            text.append(text.length() > 0 ? " " : "").append("Enemies per wave capped at ").append(MAX_PER_WAVE).append('.');
        }
        return text.length() == 0 ? null : text.toString();
    }

    /** {@code GodChamberWaves.start} HEAD: cap the config and remember the notice for {@link #afterStart}. */
    public static GodChamberWaves.Config onStart(ServerPlayer player, GodChamberWaves.Config config) {
        GodChamberWaves.Config capped = cap(config);
        if (player == null) {
            return capped;
        }
        PENDING.remove(player.m_20148_());
        String notice = notice(config, capped);
        if (notice != null && !GodChamberWaves.running(player)) {
            PENDING.put(player.m_20148_(), notice);
        }
        return capped;
    }

    /** {@code GodChamberWaves.start} return: tell the player once, only if a training run now runs. */
    public static void afterStart(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String notice = PENDING.remove(player.m_20148_());
        if (notice == null) {
            return;
        }
        GodChamberWaves.Status status = GodChamberWaves.status(player);
        if (status != null && status.mode() == GodChamberWaves.Status.TRAINING) {
            player.m_5661_(Component.m_237113_(notice).m_130940_(ChatFormatting.YELLOW), true);
        }
    }
}
