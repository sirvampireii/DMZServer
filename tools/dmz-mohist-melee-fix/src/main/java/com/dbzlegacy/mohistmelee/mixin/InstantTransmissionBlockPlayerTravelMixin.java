package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dragonminez.common.network.C2S.InstantTransmissionTravelToPlayerC2S;
import com.dragonminez.common.stats.StatsData;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks {@code InstantTransmissionTravelToPlayerC2S} (IT to party/external players).
 * Master IT via {@code InstantTransmissionTravelC2S} is unchanged.
 */
@Mixin(value = InstantTransmissionTravelToPlayerC2S.class, remap = false)
public abstract class InstantTransmissionBlockPlayerTravelMixin {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger LOGS = new AtomicInteger();
    private static final String MSG =
            "\u00A7cInstant Transmission to players is disabled on this server.";

    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true, remap = false)
    private void dbzlegacy$blockPlayerIt(
            ServerPlayer player,
            StatsData data,
            CallbackInfo ci
    ) {
        if (player != null) {
            player.m_5661_(Component.m_237113_(MSG), true);
        }
        int n = LOGS.incrementAndGet();
        if (n <= 20) {
            MeleeDebug.log(
                    "[{}] blocked IT-to-player for {}",
                    DmzMohistMeleeFix.MOD_ID,
                    player != null ? player.m_36316_().getName() : "?"
            );
        }
        ci.cancel();
    }
}
