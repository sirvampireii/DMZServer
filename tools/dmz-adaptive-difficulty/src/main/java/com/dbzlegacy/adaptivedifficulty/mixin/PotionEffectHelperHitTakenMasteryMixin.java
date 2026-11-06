package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.progression.HitTakenMastery;
import com.dragonminez.server.util.PotionEffectHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 4.7.40: scales the hit-taken form mastery DMZ computes ({@code base x Mastery Gain x mutant}; all
 * multiply, so this equals scaling the base). Only changes the result for the victim of a hit that
 * {@link StatsEventsHitTakenMasteryMixin} has marked; the attacker's grant and every other call are
 * unchanged.
 */
@Mixin(value = PotionEffectHelper.class, remap = false)
public abstract class PotionEffectHelperHitTakenMasteryMixin {

    @Inject(method = "applyMasteryGainMultiplier", at = @At("RETURN"), cancellable = true, remap = false)
    private static void lm$hitTakenMastery(LivingEntity entity, double baseValue, CallbackInfoReturnable<Double> cir) {
        double value = cir.getReturnValueD();
        double scaled = HitTakenMastery.scale(entity, value);
        if (scaled != value) {
            cir.setReturnValue(scaled);
        }
    }
}
