package com.dbzlegacy.adaptivedifficulty.progression.tp;

import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.init.MainEffects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Temporary death penalty: the player gets DragonMineZ's TP gain effect at a
 * negative level for 10 minutes.
 * <p>
 * The global TP boost uses the same effect with a positive level. DragonMineZ
 * turns that level into the TP multiplier inside {@code calculateTPGain}.
 * LegacyMechanics does not cut the granted amount. A later death refreshes the
 * window back to 10 minutes. The timer is stored on the player, so a relog or
 * restart keeps the remaining time and puts the effect back on.
 */
public final class DeathTpPenalty {
    public static final double MULTIPLIER = 0.5d;
    public static final long DURATION_MS = 10L * 60L * 1000L;
    /**
     * Same 0.25 steps as the TP boost. Amplifier {@code -3} is a bonus of {@code -0.5},
     * so DragonMineZ's effect multiplier is {@code 0.5}.
     */
    public static final int PENALTY_AMPLIFIER = -3;
    /**
     * What the character-stats tooltip would print for this level: multiplier
     * {@code 0.5}, formatted as {@code Effect: x0.5}. Stock clients hide that line
     * because they treat a negative level as no bonus, so the bar shows it too.
     */
    public static final String EFFECT_LABEL = "Effect: x0.5";
    /** Copied onto the respawned player. Forge does not always keep persistent data across death. */
    public static final String KEY = "lm.death_tp_penalty_until";
    /** Leftover from the old point-bank cut. Cleared so it cannot linger. */
    public static final String FRAC_KEY = "lm.death_tp_penalty_frac";
    private static final int EFFECT_REFRESH_BUFFER_TICKS = 100;
    /** Living-player explanation, so a corpse chat and the respawn chat are not the same slot. */
    private static final ConcurrentHashMap<UUID, Long> LIVING_TOLD = new ConcurrentHashMap<>();
    /**
     * The one penalty bar per player. Before 4.6.247 a second red flash bar carried the
     * announcement next to this one, and this one only said "Effect: x0.5" while draining
     * 1/600 a second, so it looked frozen. Now this bar carries both.
     */
    private static final ConcurrentHashMap<UUID, ServerBossEvent> BARS = new ConcurrentHashMap<>();
    /** Announcement text shown on the bar until the time stored here, then the countdown. */
    private static final ConcurrentHashMap<UUID, Announce> ANNOUNCE = new ConcurrentHashMap<>();
    static final long ANNOUNCE_MS = 3_000L;
    private static final long LIVING_TELL_GAP_MS = 3_000L;

    private DeathTpPenalty() {}

    public static boolean active(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return PersistentDataAccess.getLong(player, KEY, 0L) > System.currentTimeMillis();
    }

    public static void onDeath(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean refreshed = active(player);
        stamp(player);
        applyEffect(player);
        tell(player, refreshed);
    }

    /**
     * The dying entity's chat is gone after respawn. Say the penalty again on the
     * player who is actually on screen, including when the timer was already copied.
     */
    public static void tellLiving(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean refreshed = active(player);
        if (!refreshed) {
            stamp(player);
        }
        applyEffect(player);
        long now = System.currentTimeMillis();
        UUID id = player.m_20148_();
        Long last = LIVING_TOLD.get(id);
        if (last != null && now - last < LIVING_TELL_GAP_MS) {
            return;
        }
        LIVING_TOLD.put(id, now);
        tellSeen(player);
    }

    public static void onLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!active(player)) {
            clearExpired(player, false);
            return;
        }
        applyEffect(player);
        msg(player, LmChat.note(
                "TP",
                "§cDeath penalty §7is still on. §c" + EFFECT_LABEL
                        + " §7for §f" + remainingLabel(player) + "§7."));
        flashBar(player, remainingLabel(player));
    }

    public static void pulse(MinecraftServer server, int tick) {
        if (server == null || tick % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            if (player == null) {
                continue;
            }
            long until = PersistentDataAccess.getLong(player, KEY, 0L);
            if (until <= 0L) {
                // No timer (or unreadable data) but a bar is up: take it down.
                if (BARS.containsKey(player.m_20148_())) {
                    hideBar(player);
                }
                continue;
            }
            if (until <= System.currentTimeMillis()) {
                clearExpired(player, true);
                continue;
            }
            applyEffect(player);
        }
    }

    private static void stamp(ServerPlayer player) {
        PersistentDataAccess.putLong(player, KEY, System.currentTimeMillis() + DURATION_MS);
    }

    private static void applyEffect(ServerPlayer player) {
        if (player == null || !active(player)) {
            return;
        }
        try {
            MobEffect effect = MainEffects.TP_GAIN.get();
            if (effect == null) {
                return;
            }
            showBar(player);
            long remainingMs = PersistentDataAccess.getLong(player, KEY, 0L) - System.currentTimeMillis();
            int ticks = Math.max(
                    EFFECT_REFRESH_BUFFER_TICKS,
                    (int) Math.ceil(remainingMs / 50.0) + EFFECT_REFRESH_BUFFER_TICKS);
            MobEffectInstance current = player.m_21124_(effect);
            if (current != null
                    && current.m_19564_() == PENALTY_AMPLIFIER
                    && current.m_19557_() > 40) {
                return;
            }
            player.m_21195_(effect);
            player.m_7292_(new MobEffectInstance(
                    effect,
                    ticks,
                    PENALTY_AMPLIFIER,
                    false,
                    true,
                    true));
        } catch (Throwable ignored) {
        }
    }

    private static void removePenaltyEffect(ServerPlayer player) {
        try {
            MobEffect effect = MainEffects.TP_GAIN.get();
            if (effect == null || player == null) {
                return;
            }
            MobEffectInstance current = player.m_21124_(effect);
            if (current != null && current.m_19564_() == PENALTY_AMPLIFIER) {
                player.m_21195_(effect);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void tell(ServerPlayer player, boolean refreshed) {
        String body = refreshed
                ? "§cDeath penalty refreshed. §7" + EFFECT_LABEL + " §7stays for §f10 minutes§7."
                : "§cDeath penalty. §c" + EFFECT_LABEL + " §7for §f10 minutes§7.";
        msg(player, LmChat.note("TP", body));
        flashBar(player, "10 minutes");
    }

    /** What the player who is actually on screen sees, including a copied timer. */
    private static void tellSeen(ServerPlayer player) {
        msg(player, LmChat.note(
                "TP",
                "§cDeath penalty. §c" + EFFECT_LABEL + " §7for §f"
                        + remainingLabel(player) + "§7."));
        flashBar(player, remainingLabel(player));
    }

    /** Penalty announcement on the same bar for a few seconds; the countdown follows. */
    private static void flashBar(ServerPlayer player, String detail) {
        if (player == null) {
            return;
        }
        String text = detail == null || detail.isBlank()
                ? EFFECT_LABEL
                : EFFECT_LABEL + " - " + detail;
        ANNOUNCE.put(player.m_20148_(), new Announce(text, System.currentTimeMillis() + ANNOUNCE_MS));
        showBar(player);
    }

    /** Bar text: the announcement while it lasts, else the effect and the time left. */
    private static String barText(ServerPlayer player) {
        Announce a = ANNOUNCE.get(player.m_20148_());
        if (a != null) {
            if (System.currentTimeMillis() < a.until) {
                return a.text;
            }
            ANNOUNCE.remove(player.m_20148_());
        }
        return EFFECT_LABEL + " - " + remainingLabel(player) + " left";
    }

    private record Announce(String text, long until) {}

    public static String remainingLabel(ServerPlayer player) {
        long rem = Math.max(0L, PersistentDataAccess.getLong(player, KEY, 0L) - System.currentTimeMillis());
        long totalSec = rem / 1000L;
        long minutes = totalSec / 60L;
        long sec = totalSec % 60L;
        if (minutes <= 0L) {
            return sec + "s";
        }
        return minutes + "m " + sec + "s";
    }

    private static void clearExpired(ServerPlayer player, boolean announce) {
        CompoundTag tag = PersistentDataAccess.get(player);
        if (!PersistentDataAccess.isWritable(tag) || !tag.m_128441_(KEY)) {
            removePenaltyEffect(player);
            hideBar(player);
            return;
        }
        tag.m_128473_(KEY);
        tag.m_128473_(FRAC_KEY);
        removePenaltyEffect(player);
        hideBar(player);
        if (announce) {
            msg(player, LmChat.ok("TP", "Death penalty ended. TP gain is back to normal."));
        }
    }

    /** Drop the bar when the player leaves so it does not stick on the next join. */
    public static void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        hideBar(player);
    }

    private static void showBar(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            if (!active(player)) {
                return;
            }
            UUID id = player.m_20148_();
            Component name = Component.m_237113_(barText(player)).m_130940_(ChatFormatting.RED);
            ServerBossEvent bar = BARS.get(id);
            if (bar == null) {
                bar = new ServerBossEvent(
                        name,
                        BossEvent.BossBarColor.RED,
                        BossEvent.BossBarOverlay.PROGRESS);
                bar.m_7003_(false);
                bar.m_7005_(false);
                bar.m_7006_(false);
                BARS.put(id, bar);
            }
            if (!bar.m_8324_().contains(player)) {
                bar.m_7706_();
                bar.m_6543_(player);
            }
            bar.m_6456_(name);
            long rem = Math.max(0L, PersistentDataAccess.getLong(player, KEY, 0L) - System.currentTimeMillis());
            float progress = (float) Math.max(0d, Math.min(1d, rem / (double) DURATION_MS));
            bar.m_142711_(progress);
        } catch (Throwable ignored) {
        }
    }

    private static void hideBar(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            ANNOUNCE.remove(player.m_20148_());
            ServerBossEvent bar = BARS.remove(player.m_20148_());
            if (bar == null) {
                return;
            }
            bar.m_6539_(player);
            bar.m_7706_();
        } catch (Throwable ignored) {
        }
    }

    private static void msg(ServerPlayer player, String text) {
        try {
            com.dbzlegacy.adaptivedifficulty.util.DmzRewards.msg(player, text);
        } catch (Throwable ignored) {
        }
    }
}
