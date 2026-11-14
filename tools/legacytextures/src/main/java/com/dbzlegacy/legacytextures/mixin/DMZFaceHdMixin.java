package com.dbzlegacy.legacytextures.mixin;

import com.dbzlegacy.legacytextures.client.HdFace;
import com.dbzlegacy.legacytextures.client.LtHooks;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * HD face for an HD-only body (0.2.7), checked against DragonMineZ 2.2.1.2 ModRenderTypes. Every face
 * layer DMZSkinLayer.renderFace draws (eyes, nose, mouth, brows and their shadows) gets its render type
 * from skinOverlayCutout or skinOverlayTranslucent. While {@link HdFace} marks the player, the texture key
 * becomes the HD file's own key, so the face is drawn from the same set as the body. Fail-soft: require = 0,
 * and outside such a face the key passes through unchanged.
 */
@Mixin(targets = "com.dragonminez.client.render.util.ModRenderTypes", remap = false)
public abstract class DMZFaceHdMixin {
    @ModifyVariable(method = {"skinOverlayCutout", "skinOverlayTranslucent"}, at = @At("HEAD"), argsOnly = true,
            ordinal = 0, require = 0, remap = false)
    private static ResourceLocation legacytextures$hdFaceKey(ResourceLocation loc) {
        try {
            return HdFace.key(loc);
        } catch (Throwable t) {
            LtHooks.hookFailed("ModRenderTypes face key", t);
            return loc;
        }
    }
}
