package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath;
import com.dragonminez.common.stats.StatsData;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LM's limits on Overhaul's {@code PrestigeSystem} (dmzrevamp 2.2.4, 4.7.14).
 * <p>
 * 2.2.4 has one level cap ({@code configuredLevelMaximum()}) and no prestige-count cap: its
 * {@code canPrestigeAuthoritative} only checks the {@code prestige_saga:3} quest. LM keeps the cap of
 * 20 ({@link LmOverhaulCapMath#configuredMaxPrestigeCount()}) here: {@code setCount} clamps to it, and
 * {@code canPrestige} / {@code canPrestigeAuthoritative} (the one {@code tryPrestige} asks) always say no
 * while LM owns prestige (4.7.18: players prestige only through LM's menu, so Overhaul's count never runs
 * ahead of the held wallet). With LM prestige off they say no at 20 held or below the requirement for
 * the held count ({@link LmOverhaulCapMath#blocksOverhaulPrestige}). They
 * never say yes, so 2.2.4's own quest check still applies. {@code maxAssignableTotal} uses the same level cap as the menus. The
 * {@code hexStatReference} hook is gone with the method. Every hook is {@code require = 0}: if a
 * dmzrevamp update moves one, boot continues and the startup self-test warns.
 */
@Mixin(targets = "com.dmzrevamp.revamp.prestige.PrestigeSystem", remap = false, priority = 5000)
public abstract class DmzRevampPrestigeCapMixin {

    @Inject(method = "maxAssignableTotal", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void lm$maxAssignableTotal(StatsData data, CallbackInfoReturnable<Integer> cir) {
        if (data == null || !lmCapsEnforced()) {
            return;
        }
        cir.setReturnValue(LmOverhaulCapMath.maxAssignableTotal(data));
    }

    @Inject(method = "canPrestige", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void lm$blockOverhaulRebirth(StatsData data, CallbackInfoReturnable<Boolean> cir) {
        if (lm$blocked(data)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canPrestigeAuthoritative", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void lm$blockOverhaulRebirthAuthoritative(StatsData data, CallbackInfoReturnable<Boolean> cir) {
        if (lm$blocked(data)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "setCount", at = @At("HEAD"), argsOnly = true, require = 0, remap = false)
    private static int lm$capOverhaulPrestigeCount(int count) {
        return Math.max(0, Math.min(LmOverhaulCapMath.configuredMaxPrestigeCount(), count));
    }

    /** LM owns prestige (LM menu only), at the cap of 20, or below the requirement for the held count. */
    private static boolean lm$blocked(StatsData data) {
        if (lmOwnsPrestige()) {
            return true;
        }
        int count = 0;
        try {
            count = com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .overhaulCount(data);
        } catch (Throwable ignored) {
        }
        if (count >= LmOverhaulCapMath.configuredMaxPrestigeCount()) {
            return true;
        }
        if (data == null) {
            return false;
        }
        try {
            return LmOverhaulCapMath.blocksOverhaulPrestige(count, data.getLevel());
        } catch (Throwable ignored) {
            return false;
        }
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
