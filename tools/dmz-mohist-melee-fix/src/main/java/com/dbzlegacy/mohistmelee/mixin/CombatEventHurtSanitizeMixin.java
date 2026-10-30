package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dragonminez.server.events.players.combat.CombatEvent;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After DMZ combat rewrites hurt amount from {@code getMeleeDamage()}, NaN/Inf would deal nothing
 * (and historically got cancelled by FixVanillaEvents). Force a finite amount without setHealth hacks.
 */
@Mixin(value = CombatEvent.class, remap = false)
public abstract class CombatEventHurtSanitizeMixin {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger LOGS = new AtomicInteger();

    @Inject(method = "onLivingHurt", at = @At("RETURN"), remap = false)
    private static void dbzlegacy$finiteMeleeAmount(LivingHurtEvent event, CallbackInfo ci) {
        if (event.isCanceled()) {
            return;
        }
        float amount = event.getAmount();
        if (Float.isFinite(amount) && amount >= 0.0f) {
            return;
        }
        event.setAmount(1.0f);
        int n = LOGS.incrementAndGet();
        if (n <= 40) {
            MeleeDebug.log(
                    "[{}] sanitized non-finite CombatEvent hurt amount -> 1.0",
                    DmzMohistMeleeFix.MOD_ID
            );
        }
    }
}
