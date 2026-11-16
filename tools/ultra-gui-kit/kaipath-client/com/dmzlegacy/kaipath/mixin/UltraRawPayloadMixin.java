package com.dmzlegacy.kaipath.mixin;

import com.dmzlegacy.kaipath.ultra.consumers.UltraGuiConsumerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads {@code <modid>:gui} packets from a server-only partner the server announced on
 * {@code kaipath:partners} ({@link com.dmzlegacy.kaipath.ultra.consumers.UltraGuiAnnounce}). Such a
 * channel cannot be registered on the client after the game has started, so without this Forge finds
 * no channel and vanilla drops the packet as an unknown custom payload.
 *
 * <p>Only namespaces the connected server announced are touched; every other payload, and every
 * channel that is registered, goes on to Forge unchanged. Runs before Forge's hook, on the network
 * thread: the bytes are copied and handled on the main thread.
 */
@Mixin(ClientPacketListener.class)
public abstract class UltraRawPayloadMixin {
    @Inject(method = "m_7413_", at = @At("HEAD"), cancellable = true, remap = false)
    private void kaipath$rawUltraGui(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ResourceLocation id = packet.m_132042_();
        if (id == null || !UltraGuiConsumerRegistry.isRawPartner(id.m_135827_(), id.m_135815_())) {
            return;
        }
        FriendlyByteBuf data = packet.m_132045_();
        byte[] frame = new byte[data.readableBytes()];
        data.getBytes(data.readerIndex(), frame);
        String ns = id.m_135827_();
        Minecraft.m_91087_().execute(() -> UltraGuiConsumerRegistry.handleRaw(ns, frame));
        ci.cancel();
    }
}
