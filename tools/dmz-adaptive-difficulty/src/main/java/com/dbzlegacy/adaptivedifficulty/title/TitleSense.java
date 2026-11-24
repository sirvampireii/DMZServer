package com.dbzlegacy.adaptivedifficulty.title;

import com.dbzlegacy.adaptivedifficulty.boss.BossScaling;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.elite.EliteSystem;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dbzlegacy.adaptivedifficulty.util.SystemGate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Optional equipped-title "sense" chat for nearby Elites / Bosses.
 * Throttled per player to avoid spam.
 */
public final class TitleSense {
    private static final Map<UUID, Long> LAST_ELITE_MSG = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_BOSS_MSG = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_GOD_MSG = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 45_000L;
    private static final double RADIUS = 48.0;

    private TitleSense() {}

    public static void pulse(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!SystemGate.participates(player)) {
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ELITE);
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        TitleProgress progress = data.titleProgress();
        if (!progress.titleSenseChat()) {
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ELITE);
            return;
        }
        DifficultyTitle equipped = DifficultyTitle.byId(data.getActiveTitle());
        if (equipped == null) {
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ELITE);
            return;
        }
        long now = System.currentTimeMillis();
        UUID id = player.m_20148_();
        AABB box = player.m_20191_().m_82400_(RADIUS);
        boolean eliteNear = false;
        boolean bossNear = false;
        for (LivingEntity entity : player.m_9236_().m_45976_(LivingEntity.class, box)) {
            if (entity == null || entity == player) {
                continue;
            }
            if (EliteSystem.isElite(entity)) {
                eliteNear = true;
            }
            if (PersistentDataAccess.get(entity).m_128471_(BossScaling.TAG_BOSS)) {
                bossNear = true;
            }
            if (eliteNear && bossNear) {
                break;
            }
        }
        if (com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(player)) {
            // 4.7.75: a chip that stays while the sense holds, instead of a chat line every 45 seconds.
            String chip = null;
            if (equipped == DifficultyTitle.ELITE_HUNTER && eliteNear) {
                chip = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("e", "✦ Elite nearby");
            } else if (equipped == DifficultyTitle.BOSS_SLAYER && bossNear) {
                chip = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("c", "☠ Boss nearby");
            } else if (equipped == DifficultyTitle.T7_GOD && bossNear) {
                chip = com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.color("6", "Godslayer senses a boss");
            }
            if (chip == null) {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.hide(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ELITE);
            } else {
                com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ELITE, java.util.List.of(chip), -1f, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ACCENT_DIFFICULTY, "", 0, false);
            }
            return;
        }
        if (equipped == DifficultyTitle.ELITE_HUNTER && eliteNear
                && now - LAST_ELITE_MSG.getOrDefault(id, 0L) >= COOLDOWN_MS) {
            LAST_ELITE_MSG.put(id, now);
            player.m_213846_(Component.m_237113_(
                    "§e✦ Your Elite Hunter title senses a powerful enemy nearby."));
        }
        if (equipped == DifficultyTitle.BOSS_SLAYER && bossNear
                && now - LAST_BOSS_MSG.getOrDefault(id, 0L) >= COOLDOWN_MS) {
            LAST_BOSS_MSG.put(id, now);
            player.m_213846_(Component.m_237113_(
                    "§c☠ Your Boss Slayer title recognizes a worthy opponent."));
        }
        if (equipped == DifficultyTitle.T7_GOD && bossNear
                && now - LAST_GOD_MSG.getOrDefault(id, 0L) >= COOLDOWN_MS * 2L) {
            LAST_GOD_MSG.put(id, now);
            player.m_213846_(Component.m_237113_(
                    com.dbzlegacy.adaptivedifficulty.util.LmChat.note(
                            "Difficulty", "§6Godslayer senses a boss nearby.")));
        }
    }

    public static int countNearbyElites(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        AABB box = player.m_20191_().m_82400_(RADIUS);
        int n = 0;
        for (LivingEntity entity : player.m_9236_().m_45976_(LivingEntity.class, box)) {
            if (entity != null && entity != player && EliteSystem.isElite(entity)) {
                n++;
            }
        }
        return n;
    }

    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        LAST_ELITE_MSG.remove(playerId);
        LAST_BOSS_MSG.remove(playerId);
        LAST_GOD_MSG.remove(playerId);
    }
}
