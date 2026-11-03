package com.dbzlegacy.adaptivedifficulty.mixin.dmzultra;

import com.dbzlegacy.adaptivedifficulty.progression.ChamberWaveTp;
import com.dmzultra.divine.GodChamberWaves;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Whis's God Chamber menu shows TP per foe from {@code GodChamberWaves.tpPerFoe}; since the God
 * Trial waves are scaled too (4.7.26), that preview is scaled the same way, so the menu matches the
 * payout. Only Whis's screen ({@code GodChamber.sendState}); Popo's is
 * {@link DmzultraTimeChamberTpPreviewMixin}.
 */
@Mixin(targets = "com.dmzultra.divine.GodChamber", remap = false)
public abstract class DmzultraGodChamberTpPreviewMixin {

    @Redirect(method = "sendState",
            at = @At(value = "INVOKE",
                    target = "Lcom/dmzultra/divine/GodChamberWaves;tpPerFoe(Lnet/minecraft/server/level/ServerPlayer;)[J"),
            require = 0, remap = false)
    private static long[] lm$scaleTrialTpPreview(ServerPlayer player) {
        long[] shown = GodChamberWaves.tpPerFoe(player);
        try {
            return ChamberWaveTp.scalePreview(shown);
        } catch (Throwable ignored) {
            return shown;
        }
    }
}
