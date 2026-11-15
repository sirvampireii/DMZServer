package com.dbzlegacy.adaptivedifficulty.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bio Android Vital Drain ("absorb") pays no TP (4.7.50). Checked against DragonMineZ 2.2.1.2
 * {@code BioAndroidEvolution}: a drain-kill (victim at 1 HP or less, base or imperfect form) calls
 * {@code grantDrainTp}, which paid practiceDamageXp x repeat taper x drainTpRatio with no absolute cap,
 * about a tenth of the victim's max HP per kill (a 15B-HP victim paid about 1.5B TP). The repeat taper
 * resets after 30 s idle while the drain cooldown is 180 s, so an AFK victim always paid full rate.
 *
 * <p>The method is cancelled at HEAD, so its only effect, {@code Resources.addTrainingPoints}, never runs.
 * Everything else the drain does stays in {@code onActivate}/the tick: the heal, the Ki gain, the stun on
 * both players and the kill. Majin absorption ({@code MajinAbsorption}) and Cell Jr summoning are other
 * classes and are untouched.
 *
 * <p>{@code require = 1} on purpose: if DragonMineZ renames the method the server stops at boot
 * instead of quietly paying the TP again.
 */
@Mixin(targets = "com.dragonminez.common.racial.impl.BioAndroidEvolution", remap = false)
public abstract class BioAndroidDrainTpMixin {

    @Inject(
            method = "grantDrainTp"
                    + "(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lcom/dragonminez/common/stats/StatsData;"
                    + "Lnet/minecraft/world/entity/LivingEntity;F"
                    + "Lcom/dragonminez/common/config/GeneralServerConfig$BioAndroidRacialConfig;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
    private static void lm$noDrainTp(CallbackInfo ci) {
        ci.cancel();
    }
}
