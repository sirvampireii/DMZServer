package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dbzlegacy.mohistmelee.PricelessPurchaseGuard;
import com.dbzlegacy.mohistmelee.SkillTpCostHelper;
import com.dragonminez.common.network.C2S.UpdateSkillC2S;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.skills.Skill;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block menu/packet purchase of priceless ({@code -1}) skills and stack forms.
 * <p>
 * SDU double-click / spam-click sends {@code PURCHASE} with {@code Math.max(0, cost)},
 * so {@code -1} arrives as free {@code 0}. DMZ 2.1.3 {@code computeTpCost} also clamped
 * with {@code Math.max(0, …)}; DragonMineZ 2.2 rewrote it to return {@code -1} for any negative
 * or missing price and to skip the buy when the cost is below zero, so the 2.1.3
 * {@code Math.max} redirects are gone.
 * <p>
 * Not registered since 2.12.24. Primary hook: cancel {@code lambda$handle$0} (same pattern as
 * NPCAction disables) before any TP spend / level grant. Also force {@code computeTpCost} to
 * keep negatives so DMZ's own {@code if (cost < 0) skip} checks fire.
 */
@Mixin(value = UpdateSkillC2S.class, priority = 2000, remap = false)
public abstract class UpdateSkillPricelessMixin {

    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);

    @Shadow
    @Final
    private String skillName;

    @Shadow
    @Final
    private UpdateSkillC2S.SkillAction action;

    /**
     * Hard cancel on the actual apply lambda (runs on server thread after enqueueWork).
     * Covers stack-form spam buys whatever {@code computeTpCost} returns.
     */
    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true, remap = false)
    private void dbzlegacy$blockPricelessApply(ServerPlayer player, StatsData data, CallbackInfo ci) {
        if (data == null || !dbzlegacy$isBuyAction()) {
            return;
        }
        if (dbzlegacy$shouldBlockPriceless(data)) {
            ci.cancel();
            return;
        }
        PricelessPurchaseGuard.enter(data);
    }

    /**
     * Always clear the packet-scoped guard after apply (success or early return inside DMZ).
     */
    @Inject(method = "lambda$handle$0", at = @At("RETURN"), remap = false)
    private void dbzlegacy$clearPricelessGuard(ServerPlayer player, StatsData data, CallbackInfo ci) {
        PricelessPurchaseGuard.exit();
    }

    private boolean dbzlegacy$isBuyAction() {
        return this.action == UpdateSkillC2S.SkillAction.PURCHASE
                || this.action == UpdateSkillC2S.SkillAction.UPGRADE;
    }

    private boolean dbzlegacy$shouldBlockPriceless(StatsData data) {
        if (this.action == null || this.skillName == null || this.skillName.isEmpty()) {
            return false;
        }
        if (!dbzlegacy$isBuyAction()) {
            return false;
        }
        try {
            int levelIndex = 0;
            if (this.action == UpdateSkillC2S.SkillAction.UPGRADE) {
                Skill skill = data.getSkills() != null ? data.getSkills().getSkill(this.skillName) : null;
                levelIndex = skill != null ? skill.getLevel() : 0;
            }
            int raw = SkillTpCostHelper.rawConfiguredCost(data, this.skillName, levelIndex);
            MeleeDebug.log(
                    "[{}] skill packet {} '{}' levelIndex={} rawCost={}",
                    DmzMohistMeleeFix.MOD_ID,
                    this.action.name().toLowerCase(),
                    this.skillName,
                    levelIndex,
                    raw
            );
            if (raw < 0) {
                MeleeDebug.log(
                        "[{}] blocked priceless {} of skill '{}' (levelIndex={}, rawCost={})",
                        DmzMohistMeleeFix.MOD_ID,
                        this.action.name().toLowerCase(),
                        this.skillName,
                        levelIndex,
                        raw
                );
                return true;
            }
            return false;
        } catch (Throwable t) {
            LOGGER.warn(
                    "[{}] priceless purchase guard failed for '{}': {}",
                    DmzMohistMeleeFix.MOD_ID,
                    this.skillName,
                    t.toString()
            );
            // Fail closed for menu buys: never let a broken cost lookup become a free unlock.
            return true;
        }
    }

    @Inject(
            method = "computeTpCost(Lcom/dragonminez/common/stats/StatsData;Ljava/lang/String;I)I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void dbzlegacy$preservePricelessCost(
            StatsData data,
            String skillName,
            int level,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (skillName == null || skillName.isEmpty() || level < 0) {
            return;
        }
        try {
            int raw = SkillTpCostHelper.rawConfiguredCost(data, skillName, level);
            if (raw < 0) {
                cir.setReturnValue(-1);
            }
        } catch (Throwable ignored) {
            // Never break skill purchase flow on unexpected config shapes.
        }
    }
}
