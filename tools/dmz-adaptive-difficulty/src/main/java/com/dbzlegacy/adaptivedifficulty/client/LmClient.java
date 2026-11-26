package com.dbzlegacy.adaptivedifficulty.client;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeProtocol;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

/**
 * LM 4.7.58 client entry, started by {@code AdaptiveDifficultyMod} on a physical client only.
 *
 * <p>Registers the {@code legacymechanics:native_ui} channel with the menu opener, says hello to a
 * server that has the channel, and keeps name tags off the menu's player model. 4.8.2: the LM client is
 * mandatory on an LM server and the only way menus draw (the KaiPath bridge is gone); it also takes the
 * body-parts world sync (message 5) and draws those pieces on players in the world.
 */
public final class LmClient {
    private LmClient() {}

    private static boolean helloSent;

    public static void init() {
        LmNativeNetwork.register(LmClient::open);
        // 4.7.62: the player's menu look (message 3).
        LmNativeNetwork.onPrefs(MenuLook::apply);
        // 4.7.72: the native HUD (message 4).
        HudOverlay.init();
        // 4.8.2: equipped body parts on players in the world (message 5; was the KaiPath bridge's message 3).
        LmNativeNetwork.onModelSync(p -> BodyPartPreview.applySync(LmNativeNetwork.CHANNEL_ID.toString(),
                p.partsJson(), p.reason()));
        MinecraftForge.EVENT_BUS.addListener(LmClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(LmClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(LmClient::onNameTag);
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] LM client {} ready: menus draw natively on {} protocol {} (required by LM servers; no KaiPath fallback)",
                AdaptiveDifficultyMod.MOD_ID, AdaptiveDifficultyMod.VERSION, LmNativeNetwork.CHANNEL_ID,
                LmNativeProtocol.PROTOCOL_VERSION);
    }

    /** A menu from the server (client main thread). Replaces whatever screen is open. */
    static void open(GuiOpenPacket packet) {
        Minecraft mc = Minecraft.m_91087_();
        if (!helloSent) {
            // The server only sends here when it saw our channel; answer if login missed it.
            sayHello();
        }
        NativeMenuScreen previous = mc.f_91080_ instanceof NativeMenuScreen n ? n : null;
        mc.m_91152_(new NativeMenuScreen(packet, previous));
    }

    private static void sayHello() {
        helloSent = true;
        LmNativeNetwork.sendHello(AdaptiveDifficultyMod.VERSION);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        helloSent = false;
        NativeModelPreview.clear();
        MenuLook.reset();
        HudOverlay.clear();
        BodyPartPreview.clearSync();
        if (LmNativeNetwork.serverHasChannel(event.getConnection())) {
            sayHello();
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        helloSent = false;
        NativeModelPreview.clear();
        MenuLook.reset();
        MenuBackground.clear();
        ColorWheelTex.clear();
        HudOverlay.clear();
        BodyPartPreview.clearSync();
        NativeMenuScreen.clearCaches();
    }

    private static void onNameTag(RenderNameTagEvent event) {
        if (NativeModelPreview.isDrawing(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }
}
