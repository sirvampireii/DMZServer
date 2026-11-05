package com.dbzlegacy.legacytextures.client;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client wiring: the sets pack and the after-reload refresh. */
public final class ClientEvents {
    private ClientEvents() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientEvents::onPackFinders);
        modBus.addListener(ClientEvents::onReloadListeners);
    }

    private static void onPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(SetsPack.source());
        }
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void m_5944_(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void m_5787_(Void unused, ResourceManager manager, ProfilerFiller profiler) {
                ClientState.afterReload();
            }
        });
    }
}
