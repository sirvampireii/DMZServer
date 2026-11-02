package com.dbzlegacy.adaptivedifficulty.mixin.dmzultra;

import com.dbzlegacy.adaptivedifficulty.progression.ChamberWaveTp;
import com.dmzultra.divine.GodChamberWaves;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Popo's Time Chamber training-wave TP nerf (4.7.24, config {@code chamberWaveTpMultiplier}).
 *
 * <p>Bytecode (dmzultra-0.4.0 {@code GodChamberWaves}): {@code step(player, session, now)} adds
 * {@code killed * perFoe(power, powerPct, matched)} to {@code Session.earnedTp}; the HUD shows that
 * total and {@code payOut} pays it through the Ledger at session end. Scaling the {@code perFoe}
 * call inside {@code step} keeps the HUD and the payout in step and leaves foe sizing and wave
 * spawning alone. {@code Session} is package-private, so its trial flag is recorded from the public
 * {@code Config} at {@code start} (which refuses while a run is active, so the record always matches
 * the live session). Whis's God Trial ({@code Config.trial() == true}) is never scaled.
 */
@Mixin(targets = "com.dmzultra.divine.GodChamberWaves", remap = false)
public abstract class DmzultraChamberWaveTpMixin {

    @Inject(method = "start", at = @At("HEAD"), require = 0, remap = false)
    private static void lm$recordChamberRun(ServerPlayer player, GodChamberWaves.Config config, CallbackInfo ci) {
        try {
            if (player != null && config != null && !GodChamberWaves.running(player)) {
                ChamberWaveTp.recordRun(player.m_20148_(), config.trial());
            }
        } catch (Throwable ignored) {
        }
    }

    @Redirect(method = "step",
            at = @At(value = "INVOKE", target = "Lcom/dmzultra/divine/GodChamberWaves;perFoe(JIZ)J"),
            require = 0, remap = false)
    private static long lm$scaleTrainingWaveTp(long power, int powerPct, boolean matched, ServerPlayer player) {
        long base = GodChamberWaves.perFoe(power, powerPct, matched);
        try {
            return ChamberWaveTp.scaleWaveTp(player == null ? null : player.m_20148_(), base);
        } catch (Throwable ignored) {
            return base;
        }
    }
}
