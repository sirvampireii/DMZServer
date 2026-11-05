package com.dbzlegacy.legacytextures.mixin;

import com.dbzlegacy.legacytextures.client.LtHooks;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lookup point for the client's resource manager (Minecraft 1.20.1, SRG names).
 *
 * <p>DragonMineZ 2.2.1.1 asks {@code Minecraft.getResourceManager().getResource(..)} whether a file
 * exists before it draws or counts it: TextureCounter (character creator option counts),
 * DMZSkinLayer.getSafeTexture, DMZPlayerModel.fileExists, ArmorTextureResolver.textureExists,
 * SkinGathererProvider (shaded body layers read the image here) and DBSagaModel. Answering those
 * from the viewer's active set keeps every one of them in step with what the bind hook draws, so a
 * viewer can only pick, and only sees, options their own game has. This replaces a mixin per DMZ
 * class: nothing here depends on DMZ method names.
 *
 * <p>Texture reloads read through a separate per-reload manager, not this one, so the mod's own
 * texture keys always reload with the mod's own files.
 */
@Mixin(value = ReloadableResourceManager.class, remap = false)
public abstract class ReloadableResourceManagerMixin {
    // getResource(ResourceLocation)
    @Inject(method = "m_213713_", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void legacytextures$setLookup(ResourceLocation loc, CallbackInfoReturnable<Optional<Resource>> cir) {
        try {
            ResourceLocation target = LtHooks.lookupTarget((ResourceManager) (Object) this, loc);
            if (target != null) {
                cir.setReturnValue(((ResourceManager) (Object) this).m_213713_(target));
            }
        } catch (Throwable t) {
            LtHooks.hookFailed("ReloadableResourceManager.getResource", t);
        }
    }
}
