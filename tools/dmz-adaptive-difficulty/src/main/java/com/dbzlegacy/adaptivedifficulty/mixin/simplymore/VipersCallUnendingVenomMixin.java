package com.dbzlegacy.adaptivedifficulty.mixin.simplymore;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Disables Viper's Call "Unending Venom": while held in the main hand, Simply More's
 * {@code inventoryTick} re-adds every active effect under 25 ticks at 25 ticks, so any effect
 * (Abyssal Shroud, Evasion, ...) could be held forever. The re-add is skipped; effects run out
 * naturally. String target, no compile dependency; the mixin plugin only applies it when Simply
 * More is installed. The right-click venom cloud and the effect blacklist are untouched.
 */
@Mixin(targets = "net.rosemarythyme.simplymore.item.uniques.VipersCallItem", remap = false)
public abstract class VipersCallUnendingVenomMixin {

    @Redirect(
            method = "m_6883_", // inventoryTick
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;m_7292_(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            remap = false,
            // Optional: if a Simply More update changes this method, log and leave it rather than
            // failing server startup when the item class loads.
            require = 0)
    private boolean lm$noUnendingVenom(Player player, MobEffectInstance refreshed) {
        return false;
    }
}
