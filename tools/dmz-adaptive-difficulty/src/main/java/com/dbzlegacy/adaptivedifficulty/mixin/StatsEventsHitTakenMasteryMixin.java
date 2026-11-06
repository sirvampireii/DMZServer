package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.progression.HitTakenMastery;
import com.dragonminez.server.events.players.StatsEvents;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 4.7.40: marks the victim of the hit DMZ's per-hit form mastery grant is handling, so
 * {@link PotionEffectHelperHitTakenMasteryMixin} scales only the hit-taken grant. See {@link HitTakenMastery}.
 */
@Mixin(value = StatsEvents.class, remap = false)
public abstract class StatsEventsHitTakenMasteryMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"), remap = false)
    private static void lm$hitTakenMasteryBegin(LivingHurtEvent event, CallbackInfo ci) {
        HitTakenMastery.begin(event);
    }

    @Inject(method = "onEntityHit", at = @At("RETURN"), remap = false)
    private static void lm$hitTakenMasteryEnd(LivingHurtEvent event, CallbackInfo ci) {
        HitTakenMastery.end();
    }
}
