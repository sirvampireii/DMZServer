package com.dbzlegacy.adaptivedifficulty.progression.bridge;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.DmzResourcePoolClamp;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration;
import com.dmzrevamp.revamp.prestige.PrestigeSystem;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsData;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Keeps dmzrevamp Overhaul prestige count aligned with the LM held wallet.
 * After {@code setCount}, DMZ stats/progression packets are resent so Statistics
 * UI and combat scale pick up the new count.
 */
public final class DmzRevampPrestigeBridge {
    private static final String REVAMP_MOD = "dmzrevamp";
    private static final String PRESTIGE_SYSTEM = "com.dmzrevamp.revamp.prestige.PrestigeSystem";

    private static volatile Boolean revampLoaded;
    private static volatile Method setCount;
    private static volatile Method readCount;

    private DmzRevampPrestigeBridge() {}

    public static boolean revampPresent() {
        if (revampLoaded == null) {
            revampLoaded = ModList.get().isLoaded(REVAMP_MOD);
        }
        return revampLoaded;
    }

    /**
     * {@code dmzstats reset} / {@link StatsData#resetPlayerProgress} clears Overhaul's
     * prestige counter on {@link StatsData}; re-apply after rebuild.
     */
    public static void scheduleSyncAfterStatsReset(ServerPlayer player) {
        if (player == null) {
            return;
        }
        syncFromLegacy(player);
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        UUID id = player.m_20148_();
        server.execute(() -> {
            ServerPlayer p = server.m_6846_().m_11259_(id);
            if (p != null && p.m_6084_()) {
                syncFromLegacy(p);
            }
        });
        for (int delay : new int[] {5, 20, 40, 80, 160, 300, 600}) {
            final int ticks = delay;
            try {
                server.m_6937_(new TickTask(server.m_129921_() + ticks, () -> {
                    ServerPlayer p = server.m_6846_().m_11259_(id);
                    if (p != null && p.m_6084_()) {
                        syncFromLegacy(p);
                    }
                }));
            } catch (Throwable ignored) {
            }
        }
    }

    public static void syncFromLegacy(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enablePrestigeSystem || !revampPresent()) {
            return;
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return;
        }
        int current = PrestigeSystem.count(data);
        com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem
                .ensureHeldRecorded(player, current);
        int want = LmOverhaulPrestigeIntegration.toOverhaulCount(
                com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem
                        .getHeldWallet(player));
        if (current != want) {
            PrestigeSystem.setCount(data, want);
        }
        DmzResourcePoolClamp.clampToOverhaulPool(data);
        OverhaulPrestigeResourceScale.pulse(player);
        DmzSkillUtil.sync(player);
        DmzResourcePoolClamp.syncToClient(player);
        NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
    }

    /**
     * The player's level cap as Overhaul has it ({@code PrestigeSystem.levelCap}). LM's
     * {@code DmzRevampPrestigeCapMixin} answers that call, so this is the cap Overhaul, its
     * Statistics screen, and the prestige gate all show. Without dmzrevamp (or without stats) it
     * is the base cap LM pins as Overhaul's {@code initialLevelCap}.
     */
    public static int levelCap(StatsData data) {
        if (data != null && revampPresent()) {
            try {
                return Math.max(1, PrestigeSystem.levelCap(data));
            } catch (Throwable ignored) {
            }
        }
        return LmOverhaulCapMath.OVERHAUL_LEVEL_CAP;
    }

    public static int overhaulCount(StatsData data) {
        if (data == null || !ensureMethods()) {
            return 0;
        }
        try {
            Object v = readCount.invoke(null, data);
            if (v instanceof Integer i) {
                return Math.max(0, i);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static boolean ensureMethods() {
        if (setCount != null && readCount != null) {
            return true;
        }
        if (!revampPresent()) {
            return false;
        }
        try {
            Class<?> cls = Class.forName(PRESTIGE_SYSTEM);
            setCount = cls.getMethod("setCount", StatsData.class, int.class);
            readCount = cls.getMethod("count", StatsData.class);
            return true;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] dmzrevamp PrestigeSystem reflection unavailable: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
            return false;
        }
    }
}
