package com.dbzlegacy.legacytextures.mixin;

import com.dbzlegacy.legacytextures.client.LtHooks;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Model point for GeckoLib 4.x ({@code GeoModel.getBakedModel(ResourceLocation)}, checked against
 * geckolib-forge-1.20.1-4.8.4). DMZPlayerModel, DBSagaModel and the master NPC models all pass
 * their {@code dragonminez:geo/...} file through here. GeckoLib bakes every geo file at reload,
 * HD variants included, so switching is a different key: instant, nothing to rebuild.
 * Applied only when GeckoLib is installed (see LtMixinPlugin); a missing HD model keeps the mod's own.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.model.GeoModel", remap = false)
public abstract class GeoModelMixin {
    // getBakedModel(ResourceLocation): one overload in 4.8.4 (the mixin audit fails if that changes)
    @ModifyVariable(method = "getBakedModel", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0,
            remap = false)
    private ResourceLocation legacytextures$modelTarget(ResourceLocation loc) {
        try {
            return LtHooks.modelTarget(loc);
        } catch (Throwable t) {
            LtHooks.hookFailed("GeoModel.getBakedModel", t);
            return loc;
        }
    }
}
