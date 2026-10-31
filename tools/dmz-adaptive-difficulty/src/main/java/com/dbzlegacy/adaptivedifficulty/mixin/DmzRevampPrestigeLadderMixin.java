package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath;
import com.dmzrevamp.config.LevelingRevampConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps Overhaul's level-cap ladder at 10 steps while the prestige cap is 20 (4.7.12).
 * <p>
 * Overhaul's {@code PrestigeSystem.levelCap} reads {@code maxPrestigeCount} four times: twice as
 * the count limit ({@code min(count, max)}, then {@code count >= max → maxLevel}) and twice as the
 * divisor of the step ({@code initial / max + (maxLevel - initial) / max}). Raising
 * {@code maxPrestigeCount} to 20 would halve the step and lower every early requirement. Only the
 * two divisor reads (ordinals 2 and 3) are redirected to {@link LmOverhaulCapMath#LADDER_STEPS}:
 * the ladder stays 50k, 65k … 140k, then 150k from 7 held, and the count limit follows the real
 * {@code maxPrestigeCount} (20). LM only raises the cap to 20 when both redirects are present
 * ({@link LmOverhaulCapMath#ladderPinned}), so a dmzrevamp update that moves these reads cannot
 * ship a stretched ladder.
 */
@Mixin(targets = "com.dmzrevamp.revamp.prestige.PrestigeSystem", remap = false, priority = 5000)
public abstract class DmzRevampPrestigeLadderMixin {

    @Redirect(
            method = "levelCap",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/dmzrevamp/config/LevelingRevampConfig$Prestige;maxPrestigeCount:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 2),
            require = 0,
            remap = false)
    private static int lm$ladderStepsInitial(LevelingRevampConfig.Prestige prestige) {
        return LmOverhaulCapMath.LADDER_STEPS;
    }

    @Redirect(
            method = "levelCap",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/dmzrevamp/config/LevelingRevampConfig$Prestige;maxPrestigeCount:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 3),
            require = 0,
            remap = false)
    private static int lm$ladderStepsRange(LevelingRevampConfig.Prestige prestige) {
        return LmOverhaulCapMath.LADDER_STEPS;
    }
}
