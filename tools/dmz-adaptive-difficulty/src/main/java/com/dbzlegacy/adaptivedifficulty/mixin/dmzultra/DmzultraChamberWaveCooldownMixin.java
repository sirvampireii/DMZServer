package com.dbzlegacy.adaptivedifficulty.mixin.dmzultra;

import com.dbzlegacy.adaptivedifficulty.progression.ChamberWaveCooldown;
import com.dmzultra.divine.GodChamberWaves;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chamber wave start cooldown (4.7.28, config {@code chamberWaveCooldownSeconds}).
 *
 * <p>Bytecode (dmzultra-0.4.0 {@code GodChamberWaves}): Popo's and Whis's menus, Angel Drills and
 * sanctuary Demands all start runs through {@code start(player, config)}, and every run end (manual
 * stop, logout, death, the low-health stop, trial completion, leaving the realm) goes through
 * {@code stop(player, announce)}, which removes the session first. So {@code stop}'s HEAD still sees
 * the session through the public {@code status(player)} ({@code mode}: training, trial or drill) and
 * records the end; {@code start}'s HEAD refuses a run still on that kind's cooldown. Drill runs are
 * neither recorded nor refused. The same HEAD hook refuses endless runs (wave count 0) first (4.7.29).
 */
@Mixin(targets = "com.dmzultra.divine.GodChamberWaves", remap = false)
public abstract class DmzultraChamberWaveCooldownMixin {

    @Inject(method = "start", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void lm$chamberWaveCooldown(ServerPlayer player, GodChamberWaves.Config config, CallbackInfo ci) {
        try {
            if (config != null && (ChamberWaveCooldown.refuseEndless(player, config.waves(), config.drill())
                    || ChamberWaveCooldown.refuseStart(player, config.trial(), config.drill()))) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "stop", at = @At("HEAD"), require = 0, remap = false)
    private static void lm$recordChamberWaveEnd(ServerPlayer player, boolean announce, CallbackInfo ci) {
        try {
            GodChamberWaves.Status status = player == null ? null : GodChamberWaves.status(player);
            if (status == null) {
                return;
            }
            if (status.mode() == GodChamberWaves.Status.TRIAL) {
                ChamberWaveCooldown.recordEnd(player.m_20148_(), ChamberWaveCooldown.TRIAL);
            } else if (status.mode() == GodChamberWaves.Status.TRAINING) {
                ChamberWaveCooldown.recordEnd(player.m_20148_(), ChamberWaveCooldown.TRAINING);
            }
        } catch (Throwable ignored) {
        }
    }
}
