package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration;
import com.dragonminez.common.stats.StatsData;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overhaul's {@code levelCap} runs natively (no LM override since 4.7.2): it rises with the prestige
 * count, from the {@code initialLevelCap} LM pins (50k) to {@code maxLevel} (150k). LM reads it
 * through {@code DmzRevampPrestigeBridge.levelCap} for the prestige requirement, the soft-locks, and
 * the menus, and {@code hexStatReference}, {@code maxAssignableTotal} and {@code canPrestige} here
 * use the same native value ({@code LmOverhaulCapMath.overhaulLevelCap(data)}). Playable totals stay
 * {@link com.dbzlegacy.adaptivedifficulty.mixin.StatsDataMixin} + this
 * {@code maxAssignableTotal} + TP/stat soft-locks. The most prestiges a player can hold is
 * Overhaul's own {@code maxPrestigeCount}.
 */
@Mixin(targets = "com.dmzrevamp.revamp.prestige.PrestigeSystem", remap = false, priority = 5000)
public abstract class DmzRevampPrestigeCapMixin {

    @Inject(method = "maxAssignableTotal", at = @At("HEAD"), cancellable = true, remap = false)
    private static void lm$maxAssignableTotal(StatsData data, CallbackInfoReturnable<Integer> cir) {
        if (data == null || !lmCapsEnforced()) {
            return;
        }
        cir.setReturnValue(LmOverhaulCapMath.maxAssignableTotal(data));
    }

    @Inject(method = "hexStatReference", at = @At("HEAD"), cancellable = true, remap = false)
    private static void lm$hexReferenceUsesPersonalCap(StatsData data, CallbackInfoReturnable<Double> cir) {
        if (data == null || !lmCapsEnforced()) {
            return;
        }
        int cap = LmOverhaulCapMath.overhaulLevelCap(data);
        cir.setReturnValue((double) Math.max(1, cap));
    }

    @Inject(method = "canPrestige", at = @At("HEAD"), cancellable = true, remap = false)
    private static void lm$blockOverhaulRebirth(StatsData data, CallbackInfoReturnable<Boolean> cir) {
        int count = 0;
        try {
            count = com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .overhaulCount(data);
        } catch (Throwable ignored) {
        }
        if (count >= LmOverhaulCapMath.configuredMaxPrestigeCount()) {
            cir.setReturnValue(false);
            return;
        }
        if (lmOwnsPrestige() && !LmOverhaulPrestigeIntegration.integrationActive()) {
            cir.setReturnValue(false);
            return;
        }
        // Gate on Overhaul's native cap for this player (the prestige requirement).
        if (data == null) {
            return;
        }
        try {
            int cap = LmOverhaulCapMath.overhaulLevelCap(data);
            cir.setReturnValue(data.getLevel() >= cap);
        } catch (Throwable ignored) {
        }
    }

    @ModifyVariable(method = "setCount", at = @At("HEAD"), argsOnly = true, remap = false)
    private static int lm$capOverhaulPrestigeCount(int count) {
        return Math.max(0, Math.min(LmOverhaulCapMath.configuredMaxPrestigeCount(), count));
    }

    private static boolean lmOwnsPrestige() {
        try {
            return DifficultyConfig.get().enablePrestigeSystem;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean lmCapsEnforced() {
        try {
            return ModList.get().isLoaded("dmzrevamp");
        } catch (Throwable t) {
            return true;
        }
    }
}
