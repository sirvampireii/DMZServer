package com.dbzlegacy.legacytextures.net;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.server.FaceAnimStore;
import com.dbzlegacy.legacytextures.server.ModeStore;
import java.util.UUID;
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
 * Four small packets. The server owns the stored choice: the client asks for a change
 * ({@link SetMode}) and the server answers with the stored result ({@link SyncMode}), also on login. {@link AskHealth} asks the client for its health report.
 * {@link FaceAnimFlag} (0.2.3) tells clients one player's own face animation switch ({@code /faceanim}).
 */
public final class LtNetwork {
    public static final String PROTOCOL = "3";
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
        CHANNEL.messageBuilder(FaceAnimFlag.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FaceAnimFlag::encode).decoder(FaceAnimFlag::decode).consumerMainThread(FaceAnimFlag::handle).add();
    }

    /** /legacytextures status: the hooks and caches live on the client, so the client reports them. */
    public static void askHealth(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AskHealth());
    }

    /** One player's /faceanim switch, to one viewer. */
    public static void sendFace(ServerPlayer viewer, ServerPlayer subject) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new FaceAnimFlag(subject.m_20148_(), FaceAnimStore.get(subject)));
    }

    /** One player's /faceanim switch, to everyone online (on a change or a login). */
    public static void broadcastFace(ServerPlayer subject) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new FaceAnimFlag(subject.m_20148_(), FaceAnimStore.get(subject)));
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
            ctx.get().setPacketHandled(true);
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
            ctx.get().setPacketHandled(true);
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
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server to client: a player's own face animation switch; a player never sent is on. */
    public record FaceAnimFlag(UUID player, boolean on) {
        static void encode(FaceAnimFlag m, FriendlyByteBuf buf) {
            buf.m_130077_(m.player);
            buf.writeBoolean(m.on);
        }

        static FaceAnimFlag decode(FriendlyByteBuf buf) {
            return new FaceAnimFlag(buf.m_130259_(), buf.readBoolean());
        }

        static void handle(FaceAnimFlag m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.dbzlegacy.legacytextures.faceanim.FaceFlags.set(m.player, m.on));
            ctx.get().setPacketHandled(true);
        }
    }
}
