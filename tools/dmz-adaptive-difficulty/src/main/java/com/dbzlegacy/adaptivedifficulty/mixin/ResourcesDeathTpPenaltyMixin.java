package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dragonminez.common.stats.character.Resources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Registered so the death-penalty hook stays on {@code addTrainingPoints}.
 * The penalty is the TP gain effect. This method does not change the amount.
 * <p>
 * DragonMineZ 2.2: TP is a double, and {@code addTrainingPoints(float)},
 * {@code addTrainingPoints(float, boolean)}, and {@code addBoostedTrainingPoints(double)} all end in
 * {@code addTrainingPoints(double, boolean, boolean)}, which posts the TP gain event. The hook sits
 * there so it sees every amount.
 */
@Mixin(value = Resources.class, remap = false, priority = 6200)
public abstract class ResourcesDeathTpPenaltyMixin {

    @ModifyVariable(
            method = "addTrainingPoints(DZZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private double lm$leaveTrainingPoints(double amount) {
        return amount;
    }
}
