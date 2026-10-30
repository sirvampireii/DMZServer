package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.noea.AbsorptionWipeHelper;
import com.dbzlegacy.adaptivedifficulty.noea.GodKiWipeHelper;
import com.dragonminez.common.network.C2S.NPCActionC2S;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dende action 2 is the stat reset. It calls {@code resetPlayerProgress}
 * itself and never enters {@code StatsCommand.resetStats}. Heal (1) and
 * tail (3) are left alone. {@code require = 0} skips a signature change.
 * <p>
 * DragonMineZ 2.2: {@code handleDende} returns {@code boolean} (did the action happen), so the
 * handler takes {@link CallbackInfoReturnable}; a {@code CallbackInfo} handler no longer matches.
 * Action 2 returns {@code true} right after {@code resetPlayerProgress}.
 */
@Mixin(value = NPCActionC2S.class, remap = false)
public abstract class DendeResetAbsorptionWipeMixin {

    @Inject(
            method = "handleDende(Lnet/minecraft/server/level/ServerPlayer;Lcom/dragonminez/common/stats/StatsData;I)Z",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void lm$wipeOnDendeReset(
            ServerPlayer player,
            StatsData data,
            int actionId,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (player == null || actionId != 2 || !Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        AbsorptionWipeHelper.wipe(player, "NPCActionC2S.handleDende");
        GodKiWipeHelper.wipe(player, "NPCActionC2S.handleDende");
    }
}
