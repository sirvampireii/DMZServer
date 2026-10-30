package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dbzlegacy.mohistmelee.SkillTpCostHelper;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.skills.Skills;
import net.minecraft.server.level.ServerPlayer;
import net.shurui.dev.sdu.compat.DmzForms;
import net.shurui.dev.sdu.compat.DmzSkills;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SDU 3.0.5+ buys stack forms via {@code BuyStackSkillC2S} → {@code DmzSkills.buyStackSkill},
 * which does {@code Math.max(0, configuredCost)} and grants for free when cost is {@code -1}.
 * That path never touches DMZ {@code UpdateSkillC2S}, so block it here.
 */
@Mixin(value = DmzSkills.class, priority = 2000, remap = false)
public abstract class SduBuyStackSkillPricelessMixin {

    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final String MSG =
            "\u00A7cThis skill is Priceless and cannot be purchased from the forms menu.";

    @Inject(
            method = "buyStackSkill(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Lnet/shurui/dev/sdu/compat/DmzSkills$BuyResult;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void dbzlegacy$blockPricelessStackBuy(
            ServerPlayer player,
            String skillName,
            CallbackInfoReturnable<DmzSkills.BuyResult> cir
    ) {
        if (player == null || skillName == null || skillName.isBlank()) {
            return;
        }
        try {
            StatsData data = DmzForms.stats(player);
            if (data == null || data.getSkills() == null) {
                return;
            }
            Skills skills = data.getSkills();
            int levelIndex = skills.getSkillLevel(skillName);
            int raw = SkillTpCostHelper.rawConfiguredCost(data, skillName, levelIndex);
            MeleeDebug.log(
                    "[{}] sdu buyStackSkill '{}' levelIndex={} rawCost={}",
                    DmzMohistMeleeFix.MOD_ID,
                    skillName,
                    levelIndex,
                    raw
            );
            if (raw < 0) {
                MeleeDebug.log(
                        "[{}] blocked priceless sdu buyStackSkill '{}' (levelIndex={}, rawCost={})",
                        DmzMohistMeleeFix.MOD_ID,
                        skillName,
                        levelIndex,
                        raw
                );
                cir.setReturnValue(new DmzSkills.BuyResult(false, MSG));
            }
        } catch (Throwable t) {
            LOGGER.warn(
                    "[{}] sdu priceless stack-buy guard failed for '{}': {}",
                    DmzMohistMeleeFix.MOD_ID,
                    skillName,
                    t.toString()
            );
            cir.setReturnValue(new DmzSkills.BuyResult(false, MSG));
        }
    }
}
