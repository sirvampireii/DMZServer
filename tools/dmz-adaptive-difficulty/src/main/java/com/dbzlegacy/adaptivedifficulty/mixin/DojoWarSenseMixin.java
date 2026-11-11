package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.sparring.DojoWarSense;
import com.dmzultra.sense.SenseOptions;
import com.dmzultra.sense.SensePackets;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a dojo war is active, DMZ Ultra's ki sense scan also lists the other dojo's online
 * members in the same world, at any distance (4.7.47; this used to hook Noea's scan). Ultra's own
 * hiding rules ({@code sensed}: spectators, cloaks, Glind divinity) still apply. Skipped when
 * DMZ Ultra is not installed. Same members in Ultra 0.4.0 and 0.5.3.
 */
@Mixin(targets = "com.dmzultra.sense.SenseService", remap = false, priority = 700)
public abstract class DojoWarSenseMixin {
    @Shadow
    private static SensePackets.Sig sig(LivingEntity entity, boolean godKi) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean godKi(ServerPlayer viewer) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean sensed(LivingEntity entity, ServerPlayer viewer, boolean godKi) {
        throw new AssertionError();
    }

    @Inject(method = "gather", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void lm$addDojoWarFoes(
            ServerPlayer viewer, int radius, CallbackInfoReturnable<List<SensePackets.Sig>> cir) {
        boolean god = godKi(viewer);
        cir.setReturnValue(DojoWarSense.withWarFoes(
                viewer,
                cir.getReturnValue(),
                SensePackets.Sig::key,
                foe -> sig(foe, god),
                foe -> sensed(foe, viewer, god),
                SenseOptions.maxSignatures()));
    }
}
