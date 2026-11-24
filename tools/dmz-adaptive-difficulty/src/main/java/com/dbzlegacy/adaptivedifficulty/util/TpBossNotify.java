package com.dbzlegacy.adaptivedifficulty.util;

import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Short per-player boss bar for TP award and penalty notices.
 * Same add/remove pattern as {@code DeathTpPenalty}, but the bar hides itself
 * after a few seconds instead of tracking a long timer.
 */
public final class TpBossNotify {
    /** How long a notice stays up. A newer notice resets this window. */
    public static final long SHOW_MS = 3_000L;

    private static final ConcurrentHashMap<UUID, Held> BARS = new ConcurrentHashMap<>();

    private TpBossNotify() {}

    /**
     * Show {@code text} on the player's boss bar. Blank {@code cooldownKey} or
     * {@code cooldownMs <= 0} skips anti-spam. Existing cooldowns are unchanged.
     */
    public static void show(
            ServerPlayer player,
            String text,
            ChatFormatting color,
            String cooldownKey,
            long cooldownMs) {
        if (player == null || text == null || text.isBlank()) {
            return;
        }
        try {
            if (cooldownKey != null && !cooldownKey.isBlank() && cooldownMs > 0L) {
                long now = System.currentTimeMillis();
                long last = ProgressionData.tempGetLong(player, cooldownKey, 0L);
                if (last > 0L && now - last < cooldownMs) {
                    return;
                }
                ProgressionData.tempPut(player, cooldownKey, now);
            }
            if (player.f_8906_ == null) {
                return;
            }
            String plain = plain(text);
            if (plain.isEmpty()) {
                return;
            }
            UUID id = player.m_20148_();
            ChatFormatting ink = color == null ? ChatFormatting.GREEN : color;
            if (com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(player)) {
                // 4.7.72: the native HUD pill drains over the same 3 seconds on the client; no boss bar.
                hide(id);
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.TP, java.util.List.of(plain), 1f, hudAccent(ink), "",
                        (int) SHOW_MS, true);
                return;
            }
            Component name = Component.m_237113_(plain).m_130940_(ink);
            BossEvent.BossBarColor barColor = barColor(ink);
            Held held = BARS.get(id);
            if (held == null) {
                ServerBossEvent bar = new ServerBossEvent(
                        name, barColor, BossEvent.BossBarOverlay.PROGRESS);
                bar.m_7003_(false);
                bar.m_7005_(false);
                bar.m_7006_(false);
                held = new Held(bar);
                BARS.put(id, held);
            }
            held.event.m_6451_(barColor);
            held.event.m_6456_(name);
            held.event.m_142711_(1.0f);
            held.hideAt = System.currentTimeMillis() + SHOW_MS;
            if (!held.event.m_8324_().contains(player)) {
                held.event.m_6543_(player);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Drop expired bars. Call from the server tick. */
    public static void pulse(MinecraftServer server, int tick) {
        if (tick % 5 != 0 || BARS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID id : BARS.keySet()) {
            Held held = BARS.get(id);
            if (held == null) {
                continue;
            }
            long left = held.hideAt - now;
            if (left <= 0L) {
                hide(id);
                continue;
            }
            try {
                float progress = (float) Math.max(0d, Math.min(1d, left / (double) SHOW_MS));
                held.event.m_142711_(progress);
            } catch (Throwable ignored) {
                hide(id);
            }
        }
    }

    /** Drop the bar when the player leaves so it does not stick on the next join. */
    public static void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        hide(player.m_20148_());
    }

    private static void hide(UUID id) {
        if (id == null) {
            return;
        }
        Held held = BARS.remove(id);
        if (held == null) {
            return;
        }
        try {
            held.event.m_7706_();
        } catch (Throwable ignored) {
        }
    }

    /** The HUD pill's edge: the notice colour as {@code #rrggbb} (the same colour family as the bar). */
    static String hudAccent(ChatFormatting color) {
        Integer rgb = color == null ? null : color.m_126665_();
        return String.format("#%06x", (rgb == null ? 0x55FF55 : rgb) & 0xFFFFFF);
    }

    private static BossEvent.BossBarColor barColor(ChatFormatting color) {
        if (color == null) {
            return BossEvent.BossBarColor.GREEN;
        }
        return switch (color) {
            case RED, DARK_RED -> BossEvent.BossBarColor.RED;
            case YELLOW, GOLD -> BossEvent.BossBarColor.YELLOW;
            case BLUE, AQUA, DARK_AQUA, DARK_BLUE -> BossEvent.BossBarColor.BLUE;
            case LIGHT_PURPLE, DARK_PURPLE -> BossEvent.BossBarColor.PURPLE;
            case WHITE, GRAY, DARK_GRAY -> BossEvent.BossBarColor.WHITE;
            default -> BossEvent.BossBarColor.GREEN;
        };
    }

    private static String plain(String raw) {
        return raw == null ? "" : raw.replaceAll("§.", "").trim();
    }

    private static final class Held {
        final ServerBossEvent event;
        long hideAt;

        Held(ServerBossEvent event) {
            this.event = event;
        }
    }
}
