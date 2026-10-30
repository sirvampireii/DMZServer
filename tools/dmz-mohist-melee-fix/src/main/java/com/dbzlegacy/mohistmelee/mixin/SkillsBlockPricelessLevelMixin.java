package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dbzlegacy.mohistmelee.PricelessPurchaseGuard;
import com.dbzlegacy.mohistmelee.SkillTpCostHelper;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.common.stats.skills.Skills;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Last-line defense: while a skill PURCHASE/UPGRADE packet is applying, refuse level grants
 * whose configured TP cost is priceless. Does not affect {@code /dmzskill set} or quest/NPC
 * unlocks (guard inactive).
 */
@Mixin(value = Skills.class, priority = 2000, remap = false)
public abstract class SkillsBlockPricelessLevelMixin {

    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);

    @Shadow
    public abstract int getSkillLevel(String name);

    @Shadow
    public abstract Skill getSkill(String name);

    @Inject(
            method = "setSkillLevel(Ljava/lang/String;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void dbzlegacy$blockPricelessSetLevel(String skillName, int level, CallbackInfo ci) {
        if (!PricelessPurchaseGuard.isActive() || skillName == null || skillName.isEmpty()) {
            return;
        }
        if (ConfigManager.getSkillsConfig() == null) {
            return;
        }
        try {
            int current = this.getSkillLevel(skillName);
            if (level <= current) {
                return;
            }
            // Paying for tiers current..level-1
            for (int index = current; index < level; index++) {
                int raw = SkillTpCostHelper.rawConfiguredCost(
                        PricelessPurchaseGuard.currentData(), skillName, index);
                if (raw < 0) {
                    MeleeDebug.log(
                            "[{}] blocked priceless setSkillLevel '{}' {} -> {} (index={}, rawCost={})",
                            DmzMohistMeleeFix.MOD_ID,
                            skillName,
                            current,
                            level,
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

    @Inject(
            method = "addSkillLevel(Ljava/lang/String;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void dbzlegacy$blockPricelessAddSkillLevel(String skillName, int amount, CallbackInfo ci) {
        if (!PricelessPurchaseGuard.isActive() || skillName == null || amount <= 0) {
            return;
        }
        try {
            int current = this.getSkillLevel(skillName);
            for (int index = current; index < current + amount; index++) {
                int raw = SkillTpCostHelper.rawConfiguredCost(
                        PricelessPurchaseGuard.currentData(), skillName, index);
                if (raw < 0) {
                    MeleeDebug.log(
                            "[{}] blocked priceless addSkillLevel '{}' +{} at index={} (rawCost={})",
                            DmzMohistMeleeFix.MOD_ID,
                            skillName,
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
