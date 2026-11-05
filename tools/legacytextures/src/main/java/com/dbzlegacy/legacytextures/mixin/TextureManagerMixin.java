package com.dbzlegacy.legacytextures.mixin;

import com.dbzlegacy.legacytextures.client.LtHooks;
import java.util.Map;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bind point for every entity, armor and GUI texture (Minecraft 1.20.1, SRG names).
 * {@code RenderType} texture shards and {@code RenderSystem.setShaderTexture} both reach
 * {@code getTexture(ResourceLocation)}; the overload with a default and {@code bindForSetup}
 * are covered too. The HD file and the mod's own file are different keys here, so both stay
 * loaded and a switch is just a different key on the next frame: no reload, no stale texture.
 *
 * <p>Priority 900 (applied before GeckoLib's default 1000) puts this redirect ahead of GeckoLib's own
 * HEAD hook on {@code getTexture} ({@code wrapAnimatableTexture}) in the merged method, so GeckoLib
 * wraps the key that is really drawn. Behind it, GeckoLib would re-load (and, for a missing file,
 * warn about) the undrawn key on every bind. Checked by exporting the merged TextureManager.
 */
@Mixin(value = TextureManager.class, priority = 900, remap = false)
public abstract class TextureManagerMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, AbstractTexture> f_118468_;

    // getTexture(ResourceLocation), getTexture(ResourceLocation, AbstractTexture), bindForSetup(ResourceLocation)
    @ModifyVariable(method = {"m_118506_", "m_174786_", "m_174784_"}, at = @At("HEAD"), argsOnly = true,
            ordinal = 0, require = 0, remap = false)
    private ResourceLocation legacytextures$bindTarget(ResourceLocation loc) {
        try {
            return LtHooks.bindTarget(this.f_118468_, loc);
        } catch (Throwable t) {
            LtHooks.hookFailed("TextureManager.getTexture", t);
            return loc;
        }
    }

    // loadTexture(ResourceLocation, AbstractTexture): a key always loads its own file, never a set's.
    @Inject(method = "m_118515_", at = @At("HEAD"), require = 0, remap = false)
    private void legacytextures$keyLoadStart(ResourceLocation loc, AbstractTexture tex,
            CallbackInfoReturnable<AbstractTexture> cir) {
        LtHooks.keyLoad(true);
    }

    @Inject(method = "m_118515_", at = @At("RETURN"), require = 0, remap = false)
    private void legacytextures$keyLoadEnd(ResourceLocation loc, AbstractTexture tex,
            CallbackInfoReturnable<AbstractTexture> cir) {
        LtHooks.keyLoad(false);
    }
}
