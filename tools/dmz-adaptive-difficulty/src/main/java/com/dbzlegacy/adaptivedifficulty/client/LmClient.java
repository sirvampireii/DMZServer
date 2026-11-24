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
 * server that has the channel, and keeps name tags off the menu's player model. The KaiPath channel
 * ({@code legacymechanics:gui}) is never touched here: KaiPath owns it on a client.
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
        MinecraftForge.EVENT_BUS.addListener(LmClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(LmClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(LmClient::onNameTag);
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] LM client {} ready: menus draw natively on {} protocol {} (KaiPath stays the fallback)",
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
    }

    private static void onNameTag(RenderNameTagEvent event) {
        if (NativeModelPreview.isDrawing(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }
}
