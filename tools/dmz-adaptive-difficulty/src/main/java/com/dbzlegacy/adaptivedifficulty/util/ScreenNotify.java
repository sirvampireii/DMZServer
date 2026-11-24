package com.dbzlegacy.adaptivedifficulty.util;

import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Screen feedback for in-world notices (title, subtitle, or action bar).
 * Prefer this over chat for hints players need while fighting / training.
 */
public final class ScreenNotify {
    private static final String DEFAULT_CD_KEY = "lm.screen.notify";
    private static final long DEFAULT_COOLDOWN_MS = 10_000L;

    private ScreenNotify() {}

    /** Title + subtitle with default anti-spam cooldown. No chat. */
    public static void blocked(ServerPlayer player, String title, String subtitle) {
        blocked(player, title, subtitle, DEFAULT_CD_KEY, DEFAULT_COOLDOWN_MS);
    }

    /**
     * @param cooldownKey per-player temp key (empty = no cooldown)
     * @param cooldownMs  min ms between shows for this key
     */
    public static void blocked(
            ServerPlayer player, String title, String subtitle, String cooldownKey, long cooldownMs
    ) {
        showTitle(player, title, subtitle, ChatFormatting.RED, ChatFormatting.GRAY, cooldownKey, cooldownMs);
    }

    /** Soft training / progress hint (gold title). No chat. */
    public static void hint(
            ServerPlayer player, String title, String subtitle, String cooldownKey, long cooldownMs
    ) {
        showTitle(player, title, subtitle, ChatFormatting.GOLD, ChatFormatting.YELLOW, cooldownKey, cooldownMs);
    }

    /**
     * Subtitle only (empty title packet) — for frequent TP / Potential notices.
     * Yellow subtitle, default anti-spam when {@code cooldownKey} is non-blank.
     */
    public static void subtitleOnly(
            ServerPlayer player, String subtitle, String cooldownKey, long cooldownMs
    ) {
        subtitleOnly(player, subtitle, ChatFormatting.YELLOW, cooldownKey, cooldownMs);
    }

    /**
     * Subtitle only with an explicit color. Sends clear + timings + empty title + subtitle.
     * No chat. Blank {@code cooldownKey} or {@code cooldownMs <= 0} skips anti-spam.
     */
    public static void subtitleOnly(
            ServerPlayer player,
            String subtitle,
            ChatFormatting color,
            String cooldownKey,
            long cooldownMs
    ) {
        if (player == null || subtitle == null || subtitle.isBlank()) {
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
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.vanilla(player, "subtitle", subtitle);
            ChatFormatting ink = color == null ? ChatFormatting.YELLOW : color;
            player.f_8906_.m_9829_(new ClientboundClearTitlesPacket(false));
            // Slightly shorter hold than full title+subtitle pairs — TP bursts fire often.
            player.f_8906_.m_9829_(new ClientboundSetTitlesAnimationPacket(5, 35, 8));
            player.f_8906_.m_9829_(new ClientboundSetTitleTextPacket(Component.m_237113_("")));
            player.f_8906_.m_9829_(new ClientboundSetSubtitleTextPacket(styled(subtitle, ink)));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Soft tip on the action bar (above hotbar) — less intrusive than a full-screen title.
     * Combines {@code title} · {@code detail} into one line.
     */
    public static void actionBar(
            ServerPlayer player, String title, String detail, String cooldownKey, long cooldownMs
    ) {
        if (player == null) {
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
            String head = plain(title);
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.vanilla(player, "actionbar", head);
            String rest = plain(detail);
            MutableComponent line;
            if (!head.isEmpty() && !rest.isEmpty()) {
                line = Component.m_237113_(head).m_130940_(ChatFormatting.GOLD)
                        .m_7220_(Component.m_237113_(" · ").m_130940_(ChatFormatting.DARK_GRAY))
                        .m_7220_(Component.m_237113_(rest).m_130940_(ChatFormatting.YELLOW));
            } else if (!head.isEmpty()) {
                line = Component.m_237113_(head).m_130940_(ChatFormatting.GOLD);
            } else if (!rest.isEmpty()) {
                line = Component.m_237113_(rest).m_130940_(ChatFormatting.YELLOW);
            } else {
                return;
            }
            // true = action bar (hotbar overlay), not chat.
            player.m_5661_(line, true);
        } catch (Throwable ignored) {
        }
    }

    private static void showTitle(
            ServerPlayer player,
            String title,
            String subtitle,
            ChatFormatting titleColor,
            ChatFormatting subtitleColor,
            String cooldownKey,
            long cooldownMs
    ) {
        if (player == null || title == null || title.isBlank()) {
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
            player.f_8906_.m_9829_(new ClientboundClearTitlesPacket(false));
            player.f_8906_.m_9829_(new ClientboundSetTitlesAnimationPacket(5, 45, 10));
            player.f_8906_.m_9829_(new ClientboundSetTitleTextPacket(styled(title, titleColor)));
            if (subtitle != null && !subtitle.isBlank()) {
                player.f_8906_.m_9829_(
                        new ClientboundSetSubtitleTextPacket(styled(subtitle, subtitleColor)));
            }
        } catch (Throwable ignored) {
        }
    }

    /** Strip legacy § codes and apply a solid formatting (titles render § poorly on some clients). */
    private static Component styled(String raw, ChatFormatting color) {
        return Component.m_237113_(plain(raw)).m_130940_(color);
    }

    private static String plain(String raw) {
        return raw == null ? "" : raw.replaceAll("§.", "").trim();
    }
}
