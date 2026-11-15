package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.common.stats.character.Resources;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * DMZ stamina/ki regen is skipped while {@link Cooldowns#STAMINA_PAUSE} is active. Bio Android drain cooldowns
 * (Drain, DrainActive) are only sampled so a stalled cooldown clock still ticks; they are never cleared (4.7.50).
 * On Mohist, cooldown decay often stalls until relog.
 */
public final class StaminaRegenGuard {
    /** Dash sets pause to ~20 ticks — clear once dash is over. */
    private static final int STALE_PAUSE_TICKS = 40;
    private static final int STALL_TICKS_BEFORE_NUDGE = 30;
    private static final int NUDGE_INTERVAL_TICKS = 10;

    private static final Map<UUID, Float> LAST_STAMINA = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> STALL_STAMINA = new ConcurrentHashMap<>();
    /** Last pause+drain sample. Unchanged means Mohist stalled DMZ's own tick. */
    private static final Map<UUID, Integer> LAST_COOLDOWN_SAMPLE = new ConcurrentHashMap<>();

    private StaminaRegenGuard() {}

    public static void pulse(ServerPlayer player) {
        if (player == null) {
            return;
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return;
        }
        Cooldowns cds = data.getCooldowns();
        if (cds == null) {
            return;
        }
        // DMZ StatsData.tick and TickHandler already call Cooldowns.tick.
        // Ticking here as well decays dash, drain, and pause twice as fast.
        // Only tick when the sample did not move since the last pulse.
        UUID id = player.m_20148_();
        nudgeCooldownIfStalled(id, cds);

        Resources res = data.getResources();
        if (res == null) {
            return;
        }

        boolean dashing = cds.hasCooldown(Cooldowns.DASH_ACTIVE);
        float maxSPreview = 0f;
        float curSPreview = 0f;
        try {
            maxSPreview = DmzResourcePoolClamp.actualMaxStamina(data);
            curSPreview = res.getCurrentStamina();
        } catch (Throwable ignored) {
        }
        boolean belowMaxStamina = maxSPreview > 1f && curSPreview < maxSPreview - 0.5f;
        // 4.7.50: Drain and DrainActive are left alone. In DragonMineZ 2.2 they are Bio Android's Vital
        // Drain (the 6 s channel and the 180 s cooldown), and no regen code reads them. Clearing them every
        // tick cut every drain before it healed or gave Ki and erased its cooldown, so a Bio could start
        // drain after drain (each paying LM's old absorb TP).
        if (belowMaxStamina && !dashing) {
            cds.removeCooldown(Cooldowns.STAMINA_PAUSE);
        } else if (!dashing && cds.hasCooldown(Cooldowns.STAMINA_PAUSE)) {
            int pause = cds.getCooldown(Cooldowns.STAMINA_PAUSE);
            if (pause > STALE_PAUSE_TICKS || pause > 0) {
                cds.removeCooldown(Cooldowns.STAMINA_PAUSE);
            }
        }

        try {
            float maxS = DmzResourcePoolClamp.actualMaxStamina(data);
            float curS = res.getCurrentStamina();
            if (maxS > 1f && curS >= 0f && curS < maxS && !dashing
                    && !cds.hasCooldown(Cooldowns.STAMINA_PAUSE)) {
                if (curS <= 0.01f) {
                    res.setCurrentStamina(Math.min(maxS, maxS * 0.02f));
                } else {
                    nudgeIfStalled(id, curS, maxS, res, cds, dashing);
                }
            } else {
                clearStall(id);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void nudgeIfStalled(
            UUID id,
            float cur,
            float max,
            Resources res,
            Cooldowns cds,
            boolean dashing
    ) {
        if (dashing || cds.hasCooldown(Cooldowns.STAMINA_PAUSE)) {
            clearStall(id);
            return;
        }
        float prev = LAST_STAMINA.getOrDefault(id, -1f);
        float now = res.getCurrentStamina();
        if (prev >= 0f && Math.abs(now - prev) < 0.08f) {
            int stall = STALL_STAMINA.merge(id, 1, (a, b) -> a + b);
            if (stall >= STALL_TICKS_BEFORE_NUDGE && stall % NUDGE_INTERVAL_TICKS == 0) {
                float step = Math.max(0.5f, max * 0.015f);
                float gap = max - now;
                step = Math.min(step, gap);
                if (step > 0.01f) {
                    res.addStamina(step);
                }
            }
        } else {
            STALL_STAMINA.put(id, 0);
        }
        LAST_STAMINA.put(id, res.getCurrentStamina());
    }

    public static void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        LAST_STAMINA.remove(id);
        STALL_STAMINA.remove(id);
        LAST_COOLDOWN_SAMPLE.remove(id);
    }

    private static void nudgeCooldownIfStalled(UUID id, Cooldowns cds) {
        int sample = cooldownSample(cds);
        Integer previous = LAST_COOLDOWN_SAMPLE.get(id);
        if (sample <= 0) {
            LAST_COOLDOWN_SAMPLE.remove(id);
            return;
        }
        if (previous == null || sample != previous) {
            LAST_COOLDOWN_SAMPLE.put(id, sample);
            return;
        }
        try {
            cds.tick();
        } catch (Throwable ignored) {
        }
        LAST_COOLDOWN_SAMPLE.put(id, cooldownSample(cds));
    }

    private static int cooldownSample(Cooldowns cds) {
        int sample = 0;
        if (cds.hasCooldown(Cooldowns.STAMINA_PAUSE)) {
            sample += cds.getCooldown(Cooldowns.STAMINA_PAUSE);
        }
        if (cds.hasCooldown(Cooldowns.DRAIN)) {
            sample += cds.getCooldown(Cooldowns.DRAIN);
        }
        if (cds.hasCooldown(Cooldowns.DRAIN_ACTIVE)) {
            sample += cds.getCooldown(Cooldowns.DRAIN_ACTIVE);
        }
        return sample;
    }

    private static void clearStall(UUID id) {
        LAST_STAMINA.remove(id);
        STALL_STAMINA.remove(id);
    }
}
