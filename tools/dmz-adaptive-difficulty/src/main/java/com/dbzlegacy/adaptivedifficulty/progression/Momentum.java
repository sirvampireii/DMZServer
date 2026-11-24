package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter;
import com.dbzlegacy.adaptivedifficulty.scaling.HostileMobs;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Difficulty Momentum (LM 4.7.59). Beating a difficulty mob banks Mastery Boost time; while time is banked,
 * form mastery from hitting difficulty mobs is multiplied.
 *
 * <ul>
 *   <li>A difficulty mob is a hostile the difficulty system scaled ({@link MobScaling#isAdPainted}, the same
 *       population the balance telemetry measures). Chamber wave foes never count: they already grant form
 *       mastery per kill ({@link ChamberKillMastery}, 4.7.45).</li>
 *   <li>Each kill by a player banks {@code momentumSecondsPerKill} more seconds for that player, up to
 *       {@code momentumMaxBankSeconds}. The timer runs in real time. Each player banks only from their own
 *       kills (party members included); a kill credits the player behind it, never the party.</li>
 *   <li>DragonMineZ grants form mastery per landed hit. While a player has time banked, the hit mastery DMZ
 *       grants them for hitting a difficulty mob is multiplied by {@code momentumMasteryMultiplier}. Training,
 *       sparring, hits on anything else and the chamber kill grant are unchanged, so banked time cannot be
 *       spent elsewhere.</li>
 *   <li>Session-only and per character: cleared on logout and on a DMZ Ultra character switch; a bank made by
 *       another character never counts.</li>
 * </ul>
 * The hit side runs through the existing hit-mastery mixins: {@link #begin} / {@link #end} bracket DMZ's
 * {@code StatsEvents.onEntityHit}, and {@link #scale} adjusts {@code applyMasteryGainMultiplier}'s result for
 * the marked attacker only.
 */
public final class Momentum {
    private Momentum() {}

    /** One player's bank: the character it belongs to and when it runs out (epoch millis). */
    record Bank(UUID character, long expiresAt) {}

    private record Mark(Entity attacker, double factor) {}

    private static final Map<UUID, Bank> BANKS = new ConcurrentHashMap<>();
    /** 4.7.72: when each player last hit or was hit by a difficulty mob (the HUD meter shows for 10 s after). */
    private static final Map<UUID, Long> LAST_FIGHT = new ConcurrentHashMap<>();
    private static final ThreadLocal<Mark> ACTIVE = new ThreadLocal<>();

    // ------------------------------------------------------------------ pure rules (self-tested)

    /** Millis left on a bank that runs out at {@code expiresAt}; never negative. */
    public static long remaining(long expiresAt, long now) {
        return Math.max(0L, expiresAt - now);
    }

    /** New expiry after one kill: what is left plus {@code perKill} seconds, capped at {@code maxBank} seconds. */
    public static long banked(long expiresAt, long now, int perKill, int maxBank) {
        long left = remaining(expiresAt, now);
        long next = Math.min(Math.max(0L, maxBank) * 1000L, left + Math.max(0L, perKill) * 1000L);
        return now + Math.max(left, next);
    }

    /** A kill or hit on this mob counts for Momentum. */
    public static boolean qualifies(boolean hostile, boolean difficultyScaled, boolean chamberWaveFoe) {
        return hostile && difficultyScaled && !chamberWaveFoe;
    }

    /** {@code value} with the Momentum factor when {@code entity} is the marked attacker; else unchanged. */
    static double scale(Object marked, Object entity, double factor, double value) {
        return marked != null && entity == marked && factor > 1.0 ? value * factor : value;
    }

    /** {@code m:ss}, for the menu. */
    public static String clock(long millis) {
        long s = Math.max(0L, (millis + 999L) / 1000L);
        return (s / 60L) + ":" + (s % 60L < 10L ? "0" : "") + (s % 60L);
    }

    // ------------------------------------------------------------------ live game

    private static boolean enabled() {
        DifficultyConfig cfg = DifficultyConfig.get();
        return cfg != null && cfg.momentumEnabled;
    }

    static boolean qualifies(LivingEntity mob) {
        if (mob == null) {
            return false;
        }
        return qualifies(HostileMobs.isHostile(mob), MobScaling.isAdPainted(mob),
                PersistentDataAccess.get(mob).m_128403_(ChamberKillMastery.WAVE_FOE));
    }

    /** Millis this player's current character has banked (0 when none, expired, or another character's). */
    public static long remainingMillis(ServerPlayer player) {
        if (player == null) {
            return 0L;
        }
        Bank bank = BANKS.get(player.m_20148_());
        if (bank == null || !Objects.equals(bank.character(), PrestigeCharacter.id(player))) {
            return 0L;
        }
        return remaining(bank.expiresAt(), System.currentTimeMillis());
    }

    /** Lowest-priority {@code LivingDeathEvent}: the killer banks time for a difficulty mob. */
    public static void onDeath(LivingDeathEvent event) {
        try {
            if (!enabled() || event == null || event.isCanceled()) {
                return;
            }
            LivingEntity dead = event.getEntity();
            if (dead == null || dead.m_9236_().f_46443_ || !qualifies(dead)) {
                return;
            }
            ServerPlayer killer = ChamberKillMastery.killer(event.getSource());
            if (killer != null) {
                bank(killer);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] momentum kill failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    /** Bank one kill's worth of time for {@code player}. Returns the new millis left. */
    public static long bank(ServerPlayer player) {
        DifficultyConfig cfg = DifficultyConfig.get();
        long now = System.currentTimeMillis();
        UUID character = PrestigeCharacter.id(player);
        Bank old = BANKS.get(player.m_20148_());
        long expires = old == null || !Objects.equals(old.character(), character) ? now : old.expiresAt();
        long next = banked(expires, now, cfg.momentumSecondsPerKill, cfg.momentumMaxBankSeconds);
        BANKS.put(player.m_20148_(), new Bank(character, next));
        return remaining(next, now);
    }

    /** Forget a player's bank (logout, character switch). */
    public static void clear(ServerPlayer player) {
        if (player != null) {
            BANKS.remove(player.m_20148_());
            LAST_FIGHT.remove(player.m_20148_());
        }
    }

    /** The HUD meter shows: time banked and a fight within {@code fightMs}. */
    public static boolean meterShows(long left, long lastFight, long now, long fightMs) {
        return left > 0L && lastFight > 0L && now - lastFight <= fightMs;
    }

    /** HUD meter text (the menu's own clock). */
    public static String meterText(long left) {
        return "Momentum " + clock(left);
    }

    /**
     * 4.7.75: the server boost chip (global TP and mastery boosts with time left) for HUD players, once a second
     * (sent only when its text changes). The purchase and end broadcasts stay in chat.
     */
    public static void boostHudPulse(net.minecraft.server.MinecraftServer server, int tick) {
        if (server == null || tick % 20 != 10) {
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        String tp = com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost.hudLine();
        String mastery = com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalMasteryBoost.hudLine();
        if (tp != null) {
            lines.add(tp);
        }
        if (mastery != null) {
            lines.add(mastery);
        }
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            if (!com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(player)) {
                continue;
            }
            if (lines.isEmpty()) {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.BOOST);
            } else {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.BOOST, lines, -1f, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.accentOf("prestige"), "open:prestige", 0, false);
            }
        }
    }

    /** Mark that {@code player} is fighting a difficulty mob now (tests use it too). */
    public static void markFight(ServerPlayer player) {
        if (player != null) {
            LAST_FIGHT.put(player.m_20148_(), System.currentTimeMillis());
        }
    }

    /**
     * 4.7.72: once a second, the native HUD's Momentum meter for players who take it: "Momentum m:ss" and the
     * bank against its cap (the Difficulty menu's numbers), while banked and in a fight in the last 10 s.
     */
    public static void hudPulse(net.minecraft.server.MinecraftServer server, int tick) {
        if (server == null || tick % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            if (player == null) {
                continue;
            }
            boolean hud = com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(player);
            long left = hud && enabled() ? remainingMillis(player) : 0L;
            Long fight = LAST_FIGHT.get(player.m_20148_());
            if (hud && meterShows(left, fight == null ? 0L : fight, now, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.MOMENTUM_FIGHT_MS)) {
                long max = Math.max(1L, DifficultyConfig.get().momentumMaxBankSeconds * 1000L);
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.MOMENTUM, java.util.List.of(meterText(left)),
                        (float) Math.min(1.0, left / (double) max), com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ACCENT_DIFFICULTY, "open:difficulty", 0,
                        false);
            } else {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.MOMENTUM);
            }
        }
    }

    /** HEAD of DMZ's hit handler: mark the attacker when they hit a difficulty mob with time banked. */
    public static void begin(LivingHurtEvent event) {
        ACTIVE.remove();
        try {
            if (!enabled() || event == null) {
                return;
            }
            // 4.7.72: a difficulty mob hitting a player is a fight too (the HUD meter only).
            if (event.getEntity() instanceof ServerPlayer victim && event.getSource() != null
                    && event.getSource().m_7639_() instanceof LivingEntity src && qualifies(src)) {
                LAST_FIGHT.put(victim.m_20148_(), System.currentTimeMillis());
                return;
            }
            if (!qualifies(event.getEntity())) {
                return;
            }
            ServerPlayer attacker = ChamberKillMastery.killer(event.getSource());
            if (attacker != null) {
                LAST_FIGHT.put(attacker.m_20148_(), System.currentTimeMillis());
            }
            if (attacker != null && remainingMillis(attacker) > 0L) {
                ACTIVE.set(new Mark(attacker, DifficultyConfig.get().momentumMasteryMultiplier));
            }
        } catch (Throwable ignored) {
            ACTIVE.remove();
        }
    }

    /** RETURN of DMZ's hit handler. */
    public static void end() {
        ACTIVE.remove();
    }

    /** Scales the marked attacker's hit mastery; everyone else's is unchanged. */
    public static double scale(Entity entity, double value) {
        Mark mark = ACTIVE.get();
        return mark == null ? value : scale(mark.attacker(), entity, mark.factor(), value);
    }

    /** Boot self-test of the timer, the kill rule and the hit scaling. Returns problems (empty when fine). */
    public static java.util.List<String> selfTest() {
        java.util.List<String> bad = new java.util.ArrayList<>();
        long now = 1_000_000L;
        long one = banked(0L, now, 10, 300);
        if (remaining(one, now) != 10_000L) {
            bad.add("first kill banks 10s");
        }
        long two = banked(one, now + 4_000L, 10, 300);
        if (remaining(two, now + 4_000L) != 16_000L) {
            bad.add("second kill inside the window adds 10s to what is left");
        }
        long capped = banked(now + 295_000L, now, 10, 300);
        if (remaining(capped, now) != 300_000L) {
            bad.add("bank caps at the max");
        }
        if (remaining(one, now + 11_000L) != 0L) {
            bad.add("bank runs out in real time");
        }
        if (remaining(banked(now + 600_000L, now, 10, 300), now) != 600_000L) {
            bad.add("a lowered cap never takes banked time away");
        }
        if (!qualifies(true, true, false) || qualifies(true, true, true) || qualifies(true, false, false)
                || qualifies(false, true, false)) {
            bad.add("only scaled hostiles that are not chamber wave foes count");
        }
        Object attacker = new Object();
        Object other = new Object();
        if (scale(attacker, attacker, 1.5, 2.0) != 3.0 || scale(attacker, other, 1.5, 2.0) != 2.0
                || scale(null, attacker, 1.5, 2.0) != 2.0) {
            bad.add("only the marked attacker's hit mastery is multiplied");
        }
        if (!meterShows(5_000L, now - 9_000L, now, 10_000L) || meterShows(0L, now, now, 10_000L)
                || meterShows(5_000L, now - 11_000L, now, 10_000L) || meterShows(5_000L, 0L, now, 10_000L)) {
            bad.add("HUD meter shows only while banked and fighting in the last 10 s");
        }
        if (!"Momentum 0:42".equals(meterText(41_200L))) {
            bad.add("HUD meter text");
        }
        if (!"5:00".equals(clock(300_000L)) || !"0:09".equals(clock(8_200L)) || !"0:00".equals(clock(0L))) {
            bad.add("clock text");
        }
        return bad;
    }
}
