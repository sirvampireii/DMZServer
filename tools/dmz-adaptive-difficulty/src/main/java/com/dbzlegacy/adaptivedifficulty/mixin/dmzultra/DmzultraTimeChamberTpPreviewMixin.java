package com.dbzlegacy.adaptivedifficulty.mixin.dmzultra;

import com.dbzlegacy.adaptivedifficulty.progression.ChamberWaveTp;
import com.dmzultra.divine.GodChamberWaves;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Popo's Time Chamber menu shows TP per foe from {@code GodChamberWaves.tpPerFoe}; with the
 * training-wave nerf (4.7.24) that preview is scaled the same way, so the menu matches the payout.
 * Only Popo's screen ({@code TimeChamber.sendState}); Whis's God Chamber screen is untouched.
 */
@Mixin(targets = "com.dmzultra.divine.TimeChamber", remap = false)
public abstract class DmzultraTimeChamberTpPreviewMixin {

    @Redirect(method = "sendState",
            at = @At(value = "INVOKE",
                    target = "Lcom/dmzultra/divine/GodChamberWaves;tpPerFoe(Lnet/minecraft/server/level/ServerPlayer;)[J"),
            require = 0, remap = false)
    private static long[] lm$scaleTrainingTpPreview(ServerPlayer player) {
        long[] shown = GodChamberWaves.tpPerFoe(player);
        try {
            return ChamberWaveTp.scalePreview(shown);
        } catch (Throwable ignored) {
            return shown;
        }
    }
}
