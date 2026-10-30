package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.progression.DmzResourcePoolClamp;
import com.dbzlegacy.adaptivedifficulty.progression.StatsDataLoadContext;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Resources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resources writes: ignore a non-positive energy drain, clamp current pools
 * on every write, and reclamp after a copy that happens outside stats NBT load.
 */
@Mixin(value = Resources.class, remap = false)
public abstract class ResourcesEnergyDrainGuardMixin {

    /**
     * DragonMineZ 2.2: {@code removeEnergy(float)} is a stub that calls
     * {@code removeEnergy(float, boolean drainsSurge)}. Pinned to the full overload so the guard
     * runs once per drain; a name-only target would hook both.
     */
    @Inject(method = "removeEnergy(FZ)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void lm$ignoreNonPositiveEnergyDrain(float amount, boolean drainsSurge, CallbackInfo ci) {
        if (amount <= 0f) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "setCurrentEnergy", at = @At("HEAD"), argsOnly = true, remap = false)
    private float lm$clampSetEnergy(float value) {
        return clampEnergy(value);
    }

    @ModifyVariable(method = "setCurrentStamina", at = @At("HEAD"), argsOnly = true, remap = false)
    private float lm$clampSetStamina(float value) {
        return clampStamina(value);
    }

    @Inject(method = "addEnergy", at = @At("RETURN"), remap = false)
    private void lm$clampAfterAddEnergy(CallbackInfo ci) {
        clampBoth();
    }

    @Inject(method = "addStamina", at = @At("RETURN"), remap = false)
    private void lm$clampAfterAddStamina(CallbackInfo ci) {
        clampBoth();
    }

    @Inject(method = "copyFrom", at = @At("RETURN"), remap = false)
    private void lm$clampAfterCopy(Resources other, CallbackInfo ci) {
        if (StatsDataLoadContext.inLoad()) {
            return;
        }
        Resources self = (Resources) (Object) this;
        StatsData data = self.getStatsData();
        if (data == null) {
            return;
        }
        try {
            DmzResourcePoolClamp.clamp(data);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ki/stamina clamp skipped during Resources sync: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
        }
    }

    private void clampBoth() {
        Resources self = (Resources) (Object) this;
        StatsData data = self.getStatsData();
        if (data != null) {
            DmzResourcePoolClamp.clamp(data);
        }
    }

    private float clampEnergy(float value) {
        Resources self = (Resources) (Object) this;
        StatsData data = self.getStatsData();
        if (data == null) {
            return value;
        }
        float max = DmzResourcePoolClamp.actualMaxEnergy(data);
        if (DmzResourcePoolClamp.shouldClampCurrent(value, max)) {
            return max;
        }
        return value;
    }

    private float clampStamina(float value) {
        Resources self = (Resources) (Object) this;
        StatsData data = self.getStatsData();
        if (data == null) {
            return value;
        }
        float max = DmzResourcePoolClamp.actualMaxStamina(data);
        if (DmzResourcePoolClamp.shouldClampCurrent(value, max)) {
            return max;
        }
        return value;
    }
}
