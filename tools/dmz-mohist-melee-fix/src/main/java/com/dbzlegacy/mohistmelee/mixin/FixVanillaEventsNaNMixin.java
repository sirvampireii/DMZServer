package com.dbzlegacy.mohistmelee.mixin;

import com.dbzlegacy.mohistmelee.DmzMohistMeleeFix;
import com.dbzlegacy.mohistmelee.MeleeDebug;
import com.dragonminez.server.events.FixVanillaEvents;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DMZ {@code FixVanillaEvents} cancels LivingAttack/Hurt/Damage when the amount is NaN.
 * On Mohist, vanilla attack damage or mid-pipeline amounts can be NaN, so the swing animation
 * (sent earlier in {@code CombatAttackRequestC2S}) plays but the hit is cancelled — zero damage.
 * <p>
 * Skip that cancel and leave a finite placeholder so later DMZ combat handlers can apply real damage.
 * Does not redirect damage / setHealth.
 */
@Mixin(value = FixVanillaEvents.class, remap = false)
public abstract class FixVanillaEventsNaNMixin {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final AtomicInteger LOGS = new AtomicInteger();

    @Inject(method = "onAttackEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void dbzlegacy$allowNaNAttack(LivingAttackEvent event, CallbackInfo ci) {
        float amount = event.getAmount();
        if (Float.isNaN(amount) || Float.isInfinite(amount)) {
            log("LivingAttack", event.getEntity());
            // Do not cancel the attack — CombatEvent still needs to run for melee.
            ci.cancel();
        }
    }

    @Inject(method = "onLivingHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private void dbzlegacy$sanitizeHurt(LivingHurtEvent event, CallbackInfo ci) {
        float amount = event.getAmount();
        if (Float.isNaN(amount) || Float.isInfinite(amount)) {
            log("LivingHurt", event.getEntity());
            event.setAmount(1.0f);
            ci.cancel();
        }
    }

    @Inject(method = "onLivingDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private void dbzlegacy$sanitizeDamage(LivingDamageEvent event, CallbackInfo ci) {
        float amount = event.getAmount();
        if (Float.isNaN(amount) || Float.isInfinite(amount)) {
            log("LivingDamage", event.getEntity());
            event.setAmount(1.0f);
            ci.cancel();
        }
    }

    private static void log(String where, LivingEntity entity) {
        int n = LOGS.incrementAndGet();
        if (n <= 40) {
            MeleeDebug.log(
                    "[{}] prevented NaN cancel where={}",
                    DmzMohistMeleeFix.MOD_ID,
                    where
            );
        }
    }
}
