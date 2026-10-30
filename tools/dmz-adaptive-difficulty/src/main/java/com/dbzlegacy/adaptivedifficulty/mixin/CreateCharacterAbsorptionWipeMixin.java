package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.noea.AbsorptionWipeHelper;
import com.dbzlegacy.adaptivedifficulty.noea.GodKiWipeHelper;
import com.dragonminez.common.network.C2S.CreateCharacterC2S;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Status;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A new race is applied by {@code CreateCharacterC2S}, and only when the
 * player does not already have a character. That is the dragon-ball and
 * post-reset character screen. The wipe runs before
 * {@code initializeWithRaceAndClass}. An already-created character returns
 * immediately and keeps its absorption. {@code require = 0} skips a lambda rename.
 * <p>
 * DragonMineZ 2.2: the body is {@code lambda$handle$1(packet, player, data)};
 * {@code lambda$handle$0} is now the unknown-race rejection. HEAD runs before the
 * {@code isHasCreatedCharacter} check and before {@code initializeWithRaceAndClass}.
 */
@Mixin(value = CreateCharacterC2S.class, remap = false)
public abstract class CreateCharacterAbsorptionWipeMixin {

    @Inject(
            method = "lambda$handle$1(Lcom/dragonminez/common/network/C2S/CreateCharacterC2S;Lnet/minecraft/server/level/ServerPlayer;Lcom/dragonminez/common/stats/StatsData;)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void lm$wipeBeforeNewCharacter(
            CreateCharacterC2S packet,
            ServerPlayer player,
            StatsData data,
            CallbackInfo ci
    ) {
        if (player == null || data == null) {
            return;
        }
        try {
            Status status = data.getStatus();
            if (status != null && status.isHasCreatedCharacter()) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }
        AbsorptionWipeHelper.wipe(player, "CreateCharacterC2S");
        GodKiWipeHelper.wipe(player, "CreateCharacterC2S");
    }
}
