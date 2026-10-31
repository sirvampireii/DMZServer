package com.dbzlegacy.adaptivedifficulty.progression.shop;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Soft-entry facade for skill unlock GUI + prestige purchase. */
public final class ShopProgression {
    private ShopProgression() {}

    public static void onLogin(ServerPlayer player) {
        PrestigePointsSystem.onLogin(player);
    }

    public static void onLogout(ServerPlayer player) {
        if (player != null) {
            PrestigeSystem.clearPlayer(player.m_20148_());
            PrestigeSystem.clearPlayer(PrestigeCharacter.id(player));
        }
    }

    public static void pulse(MinecraftServer server, int tick) {
        if (server == null || tick % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            if (player != null) {
                PrestigePointsSystem.pulsePlayer(player, now);
            }
        }
    }
}
