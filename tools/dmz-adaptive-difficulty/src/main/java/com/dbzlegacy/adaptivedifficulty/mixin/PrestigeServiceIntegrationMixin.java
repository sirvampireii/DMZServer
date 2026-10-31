package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overhaul Statistics prestige button: LM refuses it while LM owns prestige (4.7.18) and points to LM's menu. */
@Mixin(targets = "com.dmzrevamp.revamp.prestige.PrestigeService", remap = false)
public abstract class PrestigeServiceIntegrationMixin {

    @Inject(method = "tryPrestige", at = @At("RETURN"), remap = false)
    private static void lm$syncLmAfterOverhaulPrestige(ServerPlayer player, CallbackInfo ci) {
        try {
            LmOverhaulPrestigeIntegration.onOverhaulPrestigeButton(player);
        } catch (Throwable ignored) {
        }
    }
}
