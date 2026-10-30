package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.character.DmzClassChangeCapture;
import com.dbzlegacy.adaptivedifficulty.character.RaceChangeCreationFlow;
import com.dbzlegacy.adaptivedifficulty.character.RaceChangeCreationPacketGuard;
import com.dragonminez.common.network.C2S.CreateCharacterC2S;
import com.dragonminez.common.stats.StatsData;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CreateCharacterC2S.class, remap = false)
public abstract class CreateCharacterC2SMixin {

    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private static void lm$guardRaceChangeCreatePacket(
            CreateCharacterC2S packet,
            Supplier<NetworkEvent.Context> ctxSupplier,
            CallbackInfo ci
    ) {
        try {
            NetworkEvent.Context ctx = ctxSupplier == null ? null : ctxSupplier.get();
            ServerPlayer player = ctx == null ? null : ctx.getSender();
            if (player == null) {
                return;
            }
            RaceChangeCreationPacketGuard.applyCreateCharacterPacket(packet, player);
            if (RaceChangeCreationFlow.isActive(player)) {
                StatsData data = DmzProgression.stats(player);
                if (data != null) {
                    DmzClassChangeCapture.store(player, data.snapshotMultiplierResources());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * After DMZ applies {@code CreateCharacterC2S} (enqueueWork), not at {@code handle} return.
     * DragonMineZ has no named apply method; the body is a lambda. In 2.2 it is
     * {@code lambda$handle$1(packet, player, data)}: {@code lambda$handle$0} is now the
     * unknown-race rejection, which only re-syncs stats. Pinned by descriptor; the build's mixin
     * target audit fails if a renumber moves it. {@code require = 0} keeps a boot from refusing.
     */
    @Inject(
            method = "lambda$handle$1(Lcom/dragonminez/common/network/C2S/CreateCharacterC2S;Lnet/minecraft/server/level/ServerPlayer;Lcom/dragonminez/common/stats/StatsData;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void lm$afterRaceChangeCreationApplied(
            CreateCharacterC2S packet,
            ServerPlayer player,
            StatsData data,
            CallbackInfo ci
    ) {
        try {
            if (player == null || !RaceChangeCreationFlow.isActive(player)) {
                return;
            }
            RaceChangeCreationFlow.onCharacterCreated(player);
        } catch (Throwable ignored) {
        }
    }
}
