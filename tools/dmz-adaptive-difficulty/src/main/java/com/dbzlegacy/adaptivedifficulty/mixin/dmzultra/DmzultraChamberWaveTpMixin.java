package com.dbzlegacy.adaptivedifficulty.mixin.dmzultra;

import com.dbzlegacy.adaptivedifficulty.progression.ChamberWaveTp;
import com.dmzultra.divine.GodChamberWaves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Chamber-wave TP nerf (config {@code chamberWaveTpMultiplier}): Popo's training waves (4.7.24) and
 * Whis's God Trial (4.7.26).
 *
 * <p>Bytecode (dmzultra-0.4.0 {@code GodChamberWaves}): {@code step(player, session, now)} adds
 * {@code killed * perFoe(power, powerPct, matched)} to {@code Session.earnedTp}; the HUD shows that
 * total and {@code payOut} pays it through the Ledger at session end. Scaling the {@code perFoe}
 * call inside {@code step} keeps the HUD and the payout in step and leaves foe sizing, wave spawning
 * and the trial's god-ki award alone. Every run is scaled the same, so no per-run state is kept.
 */
@Mixin(targets = "com.dmzultra.divine.GodChamberWaves", remap = false)
public abstract class DmzultraChamberWaveTpMixin {

    @Redirect(method = "step",
            at = @At(value = "INVOKE", target = "Lcom/dmzultra/divine/GodChamberWaves;perFoe(JIZ)J"),
            require = 0, remap = false)
    private static long lm$scaleChamberWaveTp(long power, int powerPct, boolean matched) {
        long base = GodChamberWaves.perFoe(power, powerPct, matched);
        try {
            return ChamberWaveTp.scaleWaveTp(base);
        } catch (Throwable ignored) {
            return base;
        }
    }
}
