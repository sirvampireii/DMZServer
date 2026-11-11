package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.character.RacialStrikeWipe;
import com.dbzlegacy.adaptivedifficulty.progression.FusionBonusOwner;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.BonusStats;
import com.dragonminez.common.stats.techniques.Techniques;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Links each stats object to its bonus map and technique bag (fusion bonus owner, racial strike wipe).
 *
 * <p>4.7.49 (M-P1-7): the six stat-cap handlers on {@code getConfiguredMaxValue},
 * {@code getConfiguredMaxTotalStats} and {@code getMaxAllowedIncreaseForStat} are gone. dmzrevamp's
 * {@code StatsDataLevelingRevampMixin} (priority 1000) cancels all three at HEAD whenever its levels
 * are on, and a lower-priority mixin's HEAD code runs first, so LM's never ran (checked in the
 * exported class: revamp's 150,000 level cap came back, not LM's). Caps are revamp's; LM's menus
 * read them through {@code RevampEffectiveCaps}. Making LM's caps win would be a separate change
 * with a priority below 1000.
 */
@Mixin(value = StatsData.class, remap = false, priority = 5000)
public abstract class StatsDataMixin {
    @Shadow(remap = false)
    public abstract Player getPlayer();

    /** Bonus map already linked. Later reads skip the owner map. */
    @Unique
    private BonusStats lm$fusionBonuses;

    /** Technique bag already linked. Later reads skip the owner map. */
    @Unique
    private Techniques lm$strikeTechniques;

    /**
     * Link this stats object to the map the getter just returned.
     * One link per map instance. Do not read the getter again from here.
     */
    @Inject(method = "getBonusStats", at = @At("RETURN"), remap = false)
    private void lm$rememberFusionOwnerOnRead(CallbackInfoReturnable<BonusStats> cir) {
        BonusStats bonuses = cir.getReturnValue();
        if (bonuses == null || bonuses == lm$fusionBonuses) {
            return;
        }
        FusionBonusOwner.remember((StatsData) (Object) this, bonuses);
        lm$fusionBonuses = bonuses;
    }

    /**
     * Link this stats object to the technique bag the getter just returned.
     * Do not call getTechniques again from here.
     */
    @Inject(method = "getTechniques", at = @At("RETURN"), remap = false)
    private void lm$rememberStrikeOwnerOnRead(CallbackInfoReturnable<Techniques> cir) {
        Techniques techniques = cir.getReturnValue();
        if (techniques == null || techniques == lm$strikeTechniques) {
            return;
        }
        RacialStrikeWipe.remember((StatsData) (Object) this, techniques);
        lm$strikeTechniques = techniques;
    }
}
