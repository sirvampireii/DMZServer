package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.progression.NpcSparMastery;
import com.dragonminez.server.util.PotionEffectHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 4.7.40: scales the base form mastery DMZ passes in, before the Mastery Gain effect and the mutant
 * bonus multiply it. Only changes the value while {@link StatsEventsNpcSparMasteryMixin} has marked a
 * player / sparring-NPC hit (then by {@link NpcSparMastery#NPC_SPAR_FACTOR}); every other call is
 * unchanged.
 */
@Mixin(value = PotionEffectHelper.class, remap = false)
public abstract class PotionEffectHelperNpcSparMasteryMixin {

    @ModifyVariable(method = "applyMasteryGainMultiplier", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static double lm$npcSparMasteryBase(double baseValue) {
        return NpcSparMastery.scaleBase(baseValue);
    }
}
