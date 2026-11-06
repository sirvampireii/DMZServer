package com.dbzlegacy.adaptivedifficulty.mixin;

import com.dbzlegacy.adaptivedifficulty.progression.NpcSparMastery;
import com.dragonminez.server.events.players.StatsEvents;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 4.7.40: marks DMZ's per-hit form mastery grant while it handles a hit between a player and a
 * sparring NPC, so {@link PotionEffectHelperNpcSparMasteryMixin} halves the base. See
 * {@link NpcSparMastery}.
 */
@Mixin(value = StatsEvents.class, remap = false)
public abstract class StatsEventsNpcSparMasteryMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"), remap = false)
    private static void lm$npcSparMasteryBegin(LivingHurtEvent event, CallbackInfo ci) {
        NpcSparMastery.begin(event);
    }

    @Inject(method = "onEntityHit", at = @At("RETURN"), remap = false)
    private static void lm$npcSparMasteryEnd(LivingHurtEvent event, CallbackInfo ci) {
        NpcSparMastery.end();
    }
}
