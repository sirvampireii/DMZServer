package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dragonminez.common.network.C2S.CombatAttackRequestC2S;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Production log: {@code DecoderException: CombatAttackRequestC2S: invalid entity id count 66}.
 * DMZ hard-caps at 64 and <b>drops the whole M1 packet</b> (swing can still look local, no server hit).
 * Crowded raids/spawn can collect more than 64 targets client-side.
 * <p>
 * Allow decode up to a higher cap, then keep only the first 64 IDs for processing.
 */
@Mixin(value = CombatAttackRequestC2S.class, remap = false)
public abstract class CombatAttackRequestDecodeMixin {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger LOGS = new AtomicInteger();
    private static final int DMZ_MAX = 64;
    private static final int DECODE_MAX = 256;

    @Shadow
    @Final
    @Mutable
    private int[] entityIds;

    @ModifyConstant(
            method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            constant = @Constant(intValue = 64),
            remap = false
    )
    private int dbzlegacy$raiseDecodeCap(int original) {
        return DECODE_MAX;
    }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"), remap = false)
    private void dbzlegacy$truncateEntityIds(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (this.entityIds == null || this.entityIds.length <= DMZ_MAX) {
            return;
        }
        int before = this.entityIds.length;
        this.entityIds = Arrays.copyOf(this.entityIds, DMZ_MAX);
        int n = LOGS.incrementAndGet();
        if (n <= 40) {
            MeleeDebug.log(
                    "[{}] clamped CombatAttackRequest entity ids {} -> {} (packet would have been dropped)",
                    DmzMohistMeleeFix.MOD_ID,
                    before,
                    DMZ_MAX
            );
        }
    }
}
