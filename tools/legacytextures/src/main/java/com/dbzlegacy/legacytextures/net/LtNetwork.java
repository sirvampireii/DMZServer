package com.dbzlegacy.legacytextures.net;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.server.ModeStore;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Three small packets. The server owns the stored choice: the client asks for a change
 * ({@link SetMode}) and the server answers with the stored result ({@link SyncMode}), also on login. {@link AskHealth} asks the client for its health report.
 */
public final class LtNetwork {
    public static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(LegacyTextures.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private LtNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SyncMode.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncMode::encode).decoder(SyncMode::decode).consumerMainThread(SyncMode::handle).add();
        CHANNEL.messageBuilder(SetMode.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetMode::encode).decoder(SetMode::decode).consumerMainThread(SetMode::handle).add();
        CHANNEL.messageBuilder(AskHealth.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AskHealth::encode).decoder(AskHealth::decode).consumerMainThread(AskHealth::handle).add();
    }

    /** /legacytextures status: the hooks and caches live on the client, so the client reports them. */
    public static void askHealth(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AskHealth());
    }

    public static void sync(ServerPlayer player, TextureMode mode, boolean openMenu) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncMode(mode, openMenu));
    }

    /** Server to client: the stored choice; {@code openMenu} also opens the settings screen. */
    public record SyncMode(TextureMode mode, boolean openMenu) {
        static void encode(SyncMode m, FriendlyByteBuf buf) {
            m.mode.write(buf);
            buf.writeBoolean(m.openMenu);
        }

        static SyncMode decode(FriendlyByteBuf buf) {
            return new SyncMode(TextureMode.read(buf), buf.readBoolean());
        }

        static void handle(SyncMode m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.dbzlegacy.legacytextures.client.ClientState.onServerSync(m.mode, m.openMenu));
        }
    }

    /** Server to client: print this game's hook and cache health in chat. */
    public record AskHealth() {
        static void encode(AskHealth m, FriendlyByteBuf buf) {
        }

        static AskHealth decode(FriendlyByteBuf buf) {
            return new AskHealth();
        }

        static void handle(AskHealth m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.dbzlegacy.legacytextures.client.ClientStatus.report());
        }
    }

    /** Client to server: the player picked a new choice in the menu. */
    public record SetMode(TextureMode mode) {
        static void encode(SetMode m, FriendlyByteBuf buf) {
            m.mode.write(buf);
        }

        static SetMode decode(FriendlyByteBuf buf) {
            return new SetMode(TextureMode.read(buf));
        }

        static void handle(SetMode m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ModeStore.set(player, m.mode);
                sync(player, ModeStore.get(player), false);
            }
        }
    }
}
