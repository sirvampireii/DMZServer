package com.dbzlegacy.adaptivedifficulty.client.mixin;

import com.dbzlegacy.adaptivedifficulty.client.BodyPartPreview;
import com.dragonminez.client.util.BoneVisibilityHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * LM 4.7.61 (client only): in an LM native preview whose parts carry a chosen tail
 * ({@code suppressNaturalTail}), hide DragonMineZ's race tail bones so the figure shows one tail. Acts only
 * while an LM preview draws; the world render is untouched. Optional injection.
 */
@Mixin(targets = "com.dragonminez.client.util.BoneVisibilityHandler", remap = false)
public abstract class RaceTailSuppressMixin {
    @Inject(
            method = "updateVisibility(Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;"
                    + "Lnet/minecraft/client/player/AbstractClientPlayer;"
                    + "Lsoftware/bernie/geckolib/renderer/layer/GeoRenderLayer;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private static void lm$previewTail(
            BakedGeoModel model, AbstractClientPlayer player, GeoRenderLayer<?> layer, CallbackInfo ci) {
        try {
            if (model != null && player != null && BodyPartPreview.previewActive()
                    && BodyPartPreview.suppressesRaceTail(player)) {
                BoneVisibilityHandler.setBonesHidden(model, true, "tail1", "tail1m");
            }
        } catch (Throwable ignored) {
            // visibility stays as DragonMineZ set it
        }
    }
}
