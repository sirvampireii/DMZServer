package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.ServerMeleeFallback;
import com.dragonminez.common.network.C2S.CombatAttackRequestC2S;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Melee packet path on DragonMineZ 2.2.
 * <p>
 * 2.1.3: this cancelled {@code handle} and called {@code processAttackRequest} itself. 2.2's
 * {@code handle} does work that must not be skipped: it records the action for minigames, stores
 * the arrival pitch/yaw that {@code processAttackRequest} aims with, and hands the hit to
 * {@code MeleeWindupTracker}, which strikes only after the windup. So {@code handle} now runs as
 * DMZ wrote it:
 * <ul>
 *   <li>{@code handle} HEAD queues the Mohist repairs (stale strike lock, reach, primaries) on the
 *       same work queue, ahead of DMZ's own runnable.</li>
 *   <li>{@code processAttackRequest} HEAD/RETURN queue the rescue hit for stale or empty entity
 *       ids behind DMZ's hit, only when DMZ actually strikes.</li>
 * </ul>
 */
@Mixin(value = CombatAttackRequestC2S.class, remap = false)
public abstract class CombatAttackRequestHandleMixin {

    private static final String PROCESS =
            "processAttackRequest(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lcom/dragonminez/common/network/C2S/CombatAttackRequestC2S;)V";

    @Inject(method = "handle(Ljava/util/function/Supplier;)V", at = @At("HEAD"), remap = false)
    private void dbzlegacy$repairBeforeHandle(Supplier<NetworkEvent.Context> ctx, CallbackInfo ci) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerMeleeFallback.preHitRepairs(player, "melee-packet");
            }
        });
    }

    @Inject(method = PROCESS, at = @At("HEAD"), remap = false)
    private static void dbzlegacy$readHitTime(ServerPlayer player, CombatAttackRequestC2S request, CallbackInfo ci) {
        ServerMeleeFallback.beginAttackRequest(player);
    }

    @Inject(method = PROCESS, at = @At("RETURN"), remap = false)
    private static void dbzlegacy$queueRescue(ServerPlayer player, CombatAttackRequestC2S request, CallbackInfo ci) {
        ServerMeleeFallback.queueRescue(player, request);
    }
}
