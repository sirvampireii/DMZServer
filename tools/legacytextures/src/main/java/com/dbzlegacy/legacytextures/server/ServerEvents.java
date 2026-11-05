package com.dbzlegacy.legacytextures.server;

import com.dbzlegacy.legacytextures.net.LtNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Sends each player their stored choice when they join, and registers /legacytextures. */
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
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        LtCommand.register(event.getDispatcher());
    }
}
