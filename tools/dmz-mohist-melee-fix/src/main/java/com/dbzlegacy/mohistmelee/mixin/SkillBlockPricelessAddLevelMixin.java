package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dbzlegacy.mohistmelee.PricelessPurchaseGuard;
import com.dbzlegacy.mohistmelee.SkillTpCostHelper;
import com.dragonminez.common.stats.skills.Skill;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks {@link Skill#addLevel} during menu PURCHASE/UPGRADE when that tier is priceless.
 * UPGRADE path calls {@code skill.addLevel(1)} directly (not {@code Skills.setSkillLevel}).
 */
@Mixin(value = Skill.class, priority = 2000, remap = false)
public abstract class SkillBlockPricelessAddLevelMixin {

    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);

    @Shadow
    public abstract String getName();

    @Shadow
    public abstract int getLevel();

    @Inject(
            method = "addLevel(I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void dbzlegacy$blockPricelessAddLevel(int amount, CallbackInfo ci) {
        if (!PricelessPurchaseGuard.isActive() || amount <= 0) {
            return;
        }
        try {
            String name = this.getName();
            int current = this.getLevel();
            for (int index = current; index < current + amount; index++) {
                int raw = SkillTpCostHelper.rawConfiguredCost(
                        PricelessPurchaseGuard.currentData(), name, index);
                if (raw < 0) {
                    MeleeDebug.log(
                            "[{}] blocked priceless Skill.addLevel '{}' +{} at index={} (rawCost={})",
                            DmzMohistMeleeFix.MOD_ID,
                            name,
                            amount,
                            index,
                            raw
                    );
                    ci.cancel();
                    return;
                }
            }
        } catch (Throwable ignored) {
            // Never break skill apply.
        }
    }
}
