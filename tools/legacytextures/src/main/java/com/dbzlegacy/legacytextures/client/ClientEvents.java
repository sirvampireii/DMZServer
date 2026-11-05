package com.dbzlegacy.legacytextures.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client wiring: the sets pack, the after-reload refresh and the menu key. */
public final class ClientEvents {
    /** Opens the texture menu. Default ] (unused in vanilla); players can rebind it in Controls. */
    static final KeyMapping OPEN_MENU = new KeyMapping("key.legacytextures.menu", 93, "key.categories.legacytextures");

    private ClientEvents() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientEvents::onPackFinders);
        modBus.addListener(ClientEvents::onReloadListeners);
        modBus.addListener(ClientEvents::onKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
    }

    private static void onKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        while (OPEN_MENU.m_90859_()) {
            if (mc.f_91080_ == null && mc.f_91074_ != null) {
                mc.m_91152_(new TextureScreen(null));
            }
        }
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
