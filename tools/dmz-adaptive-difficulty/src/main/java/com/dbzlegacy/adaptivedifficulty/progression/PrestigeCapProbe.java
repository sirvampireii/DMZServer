package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem;
import com.dragonminez.common.stats.StatsData;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Dev-only probe (JVM flag {@code -Dlm.probe.prestigeCap=true}; never runs otherwise): prestige a fake
 * player to 3 held, turn all 3 in through the real exchange, and log the held wallet, Overhaul's count,
 * the level cap and the menu need after each step and again 2 s later (4.7.18 repro / regression).
 */
public final class PrestigeCapProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d70-726f-6265-2d63-61702d343731");

    private PrestigeCapProbe() {}

    public static void runIfRequested(MinecraftServer server) {
        if (server == null || !Boolean.getBoolean("lm.probe.prestigeCap")) {
            return;
        }
        try {
            ServerLevel level = server.m_129783_();
            FakePlayer fp = FakePlayerFactory.get(level, new GameProfile(PROBE_ID, "lm_cap_probe"));
            log("start", fp);
            PrestigeSystem.setHeldPublic(fp, 3);
            log("held set to 3", fp);
            String reply = PrestigePointsSystem.turnIn(fp, 3);
            log("turned in 3 (" + reply.replaceAll("§.", "").replace('\n', ' ') + ")", fp);
            PrestigeSystem.setHeldPublic(fp, 2);
            log("prestiged back to 2", fp);
            reply = PrestigePointsSystem.turnIn(fp, 1);
            log("turned in 1", fp);
            StatsData data = DmzProgression.stats(fp);
            com.dmzrevamp.revamp.prestige.PrestigeSystem.setCount(data, 5);
            log("Overhaul count forced to 5", fp);
            DmzRevampPrestigeBridge.reconcile(fp);
            log("after reconcile", fp);
            AdaptiveDifficultyMod.LOGGER.info("[{}] cap probe Overhaul's prestige button allowed: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    com.dmzrevamp.revamp.prestige.PrestigeSystem.canPrestigeAuthoritative(data));
            server.m_6937_(new TickTask(server.m_129921_() + 40, () -> log("2 s later", fp)));
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] cap probe failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
    }

    private static void log(String step, FakePlayer fp) {
        StatsData data = DmzProgression.stats(fp);
        AdaptiveDifficultyMod.LOGGER.info("[{}] cap probe {}: held {}, Overhaul count {}, level cap {}, menu need {}",
                AdaptiveDifficultyMod.MOD_ID, step, PrestigeSystem.getHeldWallet(fp),
                DmzRevampPrestigeBridge.overhaulCount(data), LmOverhaulCapMath.levelCap(data),
                PrestigeSystem.requiredLevel(fp));
    }
}
