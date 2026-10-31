package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.EvasionAttackData;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import java.util.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Quest SKILL rewards ({@code SkillReward.giveReward} → {@code StatsData.grantSkillLevel}) set the skill
 * level but never unlock the matching technique; only the TP buy path ({@code UpdateSkillC2S}) does,
 * through {@code unlockTechniqueIfPresent} when a skill goes from 0 to 1 (4.7.19).
 * <p>
 * This mirrors that: when {@code grantSkillLevel} takes a skill from 0 to 1 or more, the lowercased id
 * is looked up in {@code PredefinedTechniques.REGISTRY} (ki), then {@code STRIKE_REGISTRY}, then
 * {@code EVASION_REGISTRY}, and a copy of the template ({@code load(template.save())}) is unlocked in
 * the base techniques. Pure skills (no registry entry) are left alone. An already unlocked technique
 * is skipped: DMZ's {@code unlockTechnique} would overwrite it and lose its experience. Any failure is
 * logged and never breaks the skill grant. Fused-data skill sync is untouched.
 */
@Mixin(value = StatsData.class, remap = false)
public abstract class StatsDataQuestTechniqueMixin {

    /** Base skill level before this grant; -1 when unknown. Read and cleared at RETURN. */
    @Unique
    private int lm$levelBeforeGrant = -1;

    @Inject(method = "grantSkillLevel", at = @At("HEAD"), require = 0, remap = false)
    private void lm$rememberLevelBeforeGrant(String skill, int level, CallbackInfo ci) {
        lm$levelBeforeGrant = -1;
        try {
            lm$levelBeforeGrant = ((StatsData) (Object) this).getBaseSkills().getSkillLevel(skill);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "grantSkillLevel", at = @At("RETURN"), require = 0, remap = false)
    private void lm$unlockGrantedTechnique(String skill, int level, CallbackInfo ci) {
        int before = lm$levelBeforeGrant;
        lm$levelBeforeGrant = -1;
        if (skill == null || before != 0 || level < 1) {
            return;
        }
        try {
            String id = skill.toLowerCase(Locale.ROOT);
            TechniqueData copy = null;
            if (PredefinedTechniques.REGISTRY.containsKey(id)) {
                KiAttackData c = new KiAttackData();
                c.load(PredefinedTechniques.REGISTRY.get(id).save());
                copy = c;
            } else if (PredefinedTechniques.STRIKE_REGISTRY.containsKey(id)) {
                StrikeAttackData c = new StrikeAttackData();
                c.load(PredefinedTechniques.STRIKE_REGISTRY.get(id).save());
                copy = c;
            } else if (PredefinedTechniques.EVASION_REGISTRY.containsKey(id)) {
                EvasionAttackData c = new EvasionAttackData();
                c.load(PredefinedTechniques.EVASION_REGISTRY.get(id).save());
                copy = c;
            }
            if (copy == null) {
                return;
            }
            Techniques techniques = ((StatsData) (Object) this).getBaseTechniques();
            if (techniques == null || techniques.getUnlockedTechniques().containsKey(copy.getId())) {
                return;
            }
            techniques.unlockTechnique(copy);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] technique unlock for granted skill {} failed: {}",
                    AdaptiveDifficultyMod.MOD_ID, skill, t.toString());
        }
    }
}
