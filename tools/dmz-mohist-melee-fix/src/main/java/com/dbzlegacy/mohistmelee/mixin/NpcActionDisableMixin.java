package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dragonminez.common.network.C2S.NPCActionC2S;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.DialogueResultS2C;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side disables for selected DMZ master NPC actions (DragonMineZ 2.2 jar):
 * <ul>
 *   <li>Master shadow spar: every master UI sends {@code NPCActionC2S("popo", 1)} →
 *       {@code perform} → {@code handlePopo(player, 1)}. Player minigame summons untouched.</li>
 *   <li>Guru potential unlock: {@code NPCActionC2S("guru", 1)} → {@code handleGuru(data, 1)} →
 *       {@code StatsData.grantSkillLevel("potentialunlock", 11)}. Other skill sources untouched.</li>
 *   <li>Dr. Gero android conversion: {@code NPCActionC2S("gero", 1)} → {@code handleGero(1)} →
 *       {@code Status.setAndroidUpgraded(true)} + androidforms. Also blocks CNPC/script reflection
 *       calls into {@code handleGero}.</li>
 * </ul>
 * The packet gate ({@code lambda$handle$0}, the body that runs with the player's stats) cancels
 * before DMZ's range checks, {@code perform()}, and {@code reply()}. DMZ's dialogue screen waits
 * for a {@link DialogueResultS2C} for that action, so the gate sends a failed result itself with
 * the reason; the screen shows the reason as the NPC's line and the option stays usable.
 * The three handlers return {@code false} (DMZ's "did not happen") as a backstop for direct calls.
 */
@Mixin(value = NPCActionC2S.class, remap = false)
public abstract class NpcActionDisableMixin {
    private static final String MSG_SHADOW = "Master shadow dummy sparring is disabled on this server.";
    private static final String MSG_GURU_POTENTIAL = "Guru potential unlock is disabled on this server.";
    private static final String MSG_GERO_ANDROID = "Dr. Gero android conversion is disabled on this server.";

    @Shadow
    @Final
    private String npcName;

    @Shadow
    @Final
    private int actionId;

    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dbzlegacy$blockDisabledNpcActions(
            NPCActionC2S packet,
            ServerPlayer player,
            StatsData stats,
            CallbackInfo ci
    ) {
        if (packet == null) {
            return;
        }
        NpcActionDisableMixin self = (NpcActionDisableMixin) (Object) packet;
        if (self.actionId != 1 || self.npcName == null) {
            return;
        }
        String message = switch (self.npcName) {
            case "popo" -> MSG_SHADOW;
            case "guru" -> MSG_GURU_POTENTIAL;
            case "gero" -> MSG_GERO_ANDROID;
            default -> null;
        };
        if (message == null) {
            return;
        }
        ci.cancel();
        if (player != null) {
            NetworkHandler.sendToPlayer(new DialogueResultS2C(
                    self.npcName,
                    DialogueResultS2C.npcAction(self.actionId),
                    false,
                    Component.m_237113_(message).m_130940_(ChatFormatting.RED)
            ), player);
        }
        dbzlegacy$logDenied(player, self.npcName + "-protocol");
    }

    @Inject(
            method = "handlePopo(Lnet/minecraft/server/level/ServerPlayer;I)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void dbzlegacy$blockMasterShadowDummySpawn(
            ServerPlayer player,
            int actionId,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (actionId != 1) {
            return;
        }
        cir.setReturnValue(false);
        dbzlegacy$actionBar(player, MSG_SHADOW);
        dbzlegacy$logDenied(player, "handlePopo");
    }

    @Inject(
            method = "handleGuru(Lcom/dragonminez/common/stats/StatsData;I)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void dbzlegacy$blockGuruPotentialUnlock(
            StatsData stats,
            int actionId,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (actionId != 1) {
            return;
        }
        // 2.2 passes no player here; DMZ's reply(false) shows its own failure line.
        cir.setReturnValue(false);
        dbzlegacy$logDenied(null, "handleGuru");
    }

    @Inject(
            method = "handleGero(Lnet/minecraft/server/level/ServerPlayer;Lcom/dragonminez/common/stats/StatsData;I)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void dbzlegacy$blockGeroAndroidConversion(
            ServerPlayer player,
            StatsData stats,
            int actionId,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (actionId != 1) {
            return;
        }
        cir.setReturnValue(false);
        dbzlegacy$actionBar(player, MSG_GERO_ANDROID);
        dbzlegacy$logDenied(player, "handleGero");
    }

    private static void dbzlegacy$actionBar(ServerPlayer player, String message) {
        if (player != null) {
            player.m_5661_(Component.m_237113_(message).m_130940_(ChatFormatting.RED), true);
        }
    }

    private static void dbzlegacy$logDenied(ServerPlayer player, String where) {
        MeleeDebug.log(
                "[{}] blocked NPC action ({}) for {}",
                DmzMohistMeleeFix.MOD_ID,
                where,
                player != null ? player.m_36316_().getName() : "?"
        );
    }
}
