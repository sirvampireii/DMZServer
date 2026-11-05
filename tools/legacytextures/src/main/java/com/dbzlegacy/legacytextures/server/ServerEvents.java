package com.dbzlegacy.legacytextures.server;

import com.dbzlegacy.legacytextures.net.LtNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Sends each player their stored choice when they join, and registers /legacytextures and /faceanim.
 * Face animation switches (0.2.3): a joining player gets everyone's, everyone gets theirs, and a player
 * coming into view is sent again (covers anyone who missed the broadcast).
 */
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ServerSettings.reload();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LtNetwork.sync(player, ModeStore.get(player), false);
            for (ServerPlayer other : player.f_8924_.m_6846_().m_11314_()) {
                if (other != player) {
                    LtNetwork.sendFace(player, other);
                }
            }
            LtNetwork.broadcastFace(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject) {
            LtNetwork.sendFace(viewer, subject);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        LtCommand.register(event.getDispatcher());
    }
}
