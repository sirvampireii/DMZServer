package com.dbzlegacy.adaptivedifficulty.mixin.dmzultra;

import com.dbzlegacy.adaptivedifficulty.rival.NemesisSpawnMode;
import com.dmzultra.battlepower.FoeScale;
import com.dmzultra.journal.Journal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-player nemesis spawn gate for dmzultra's rival journal
 * ({@code NemesisSpawnMode}). dmzultra is not a compile-shipped dependency of
 * the server runtime, so the target uses the string form with {@code require 0}
 * and the mixin plugin gates application on the class being present.
 *
 * <p>Bytecode call graph (dmzultra-0.2.9 {@code com.dmzultra.journal.Rival}):
 * the manual journal challenge ({@code JournalActionC2SPacket} menu) calls
 * {@code meet(...)} with trigger {@code "appear"}; the automatic {@code onTick}
 * visits call it with {@code "tired"} / {@code "found"}; both materialize through
 * {@code spawn(...)} (one call site each). Writ proving actions reach {@code spawn}
 * through {@code champion(...)} directly. {@code spawn} carries no trigger, so the
 * two entries record their origin in a flow marker that {@code spawn} reads:
 *
 * <ul>
 *   <li>{@code "appear"} (nemesis menu) — passes in every mode, including OFF.</li>
 *   <li>{@code champion} (writ proving) — honors {@link NemesisSpawnMode#allowManual}.</li>
 *   <li>automatic / unattributed — honors {@link NemesisSpawnMode#allowTickSpawn}.</li>
 * </ul>
 *
 * <p>Returning {@code false} from {@code meet} and {@code null} from {@code spawn}
 * are both safe: the mod treats them exactly like its own "no spawn" rolls and
 * shows its stock fallback text.
 */
@Mixin(targets = "com.dmzultra.journal.Rival", remap = false)
public abstract class DmzultraNemesisSpawnGateMixin {

    /** Flow origin recorded by the entry points — {@code spawn} itself has no trigger. */
    @Unique
    private static final ThreadLocal<Integer> lm$flow = new ThreadLocal<>();

    @Unique private static final int lm$FLOW_AUTO = 0;
    @Unique private static final int lm$FLOW_APPEAR = 1;
    @Unique private static final int lm$FLOW_CHAMPION = 2;

    /**
     * The deliberate nemesis-menu challenge ({@code "appear"}) passes through in
     * every mode — OFF must still let a player summon their own nemesis. Automatic
     * visits ({@code "tired"} / {@code "found"}) spawn only on AUTO (MANUAL and
     * OFF both suppress them).
     */
    @Inject(method = "meet", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void lm$gateNemesisMeet(
            ServerPlayer player,
            Journal.State state,
            CompoundTag identity,
            String trigger,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (player == null || cir == null) {
                return;
            }
            boolean deliberate = "appear".equalsIgnoreCase(trigger);
            lm$flow.set(deliberate ? lm$FLOW_APPEAR : lm$FLOW_AUTO);
            if (deliberate) {
                return;
            }
            if (!NemesisSpawnMode.allowTickSpawn(player)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Writ proving summons are player-started — record the origin for {@code spawn}. */
    // Rival has champion(int) and champion(ServerPlayer, int); this handler is for the second.
    @Inject(method = "champion(Lnet/minecraft/server/level/ServerPlayer;I)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"), require = 0, remap = false)
    private static void lm$markNemesisChampion(
            ServerPlayer player, int faction, CallbackInfoReturnable<Component> cir) {
        try {
            lm$flow.set(lm$FLOW_CHAMPION);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Final gate — reason-aware: the nemesis-menu challenge passes in every mode;
     * writ champion summons honor {@link NemesisSpawnMode#allowManual} (blocked only
     * on OFF); automatic or unattributed origins honor
     * {@link NemesisSpawnMode#allowTickSpawn} (AUTO only), which also covers the
     * degraded case where the {@code meet} marker never ran.
     */
    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void lm$gateNemesisSpawn(
            ServerPlayer player,
            EntityType<?> type,
            long power,
            Component name,
            FoeScale.Spec spec,
            CallbackInfoReturnable<Mob> cir) {
        try {
            if (player == null || cir == null) {
                return;
            }
            Integer origin = lm$flow.get();
            lm$flow.remove();
            if (origin != null && origin == lm$FLOW_APPEAR) {
                return;
            }
            boolean allowed =
                    origin != null && origin == lm$FLOW_CHAMPION
                            ? NemesisSpawnMode.allowManual(player)
                            : NemesisSpawnMode.allowTickSpawn(player);
            if (!allowed) {
                cir.setReturnValue(null);
            }
        } catch (Throwable ignored) {
        }
    }
}
