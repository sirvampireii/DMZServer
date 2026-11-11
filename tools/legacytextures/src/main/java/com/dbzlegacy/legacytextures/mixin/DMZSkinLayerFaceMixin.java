package com.dbzlegacy.legacytextures.mixin;

import com.dbzlegacy.legacytextures.faceanim.FaceAnimator;
import com.dragonminez.common.stats.StatsData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/**
 * Face animation point (DMZ Face Anim 0.2.0, absorbed in 0.2.3), checked against DragonMineZ 2.2.1.1
 * DMZSkinLayer. renderFace marks the face being drawn; each face layer's path goes through
 * {@link FaceAnimator#route} and a swapped frame is drawn by DMZ's own 11-argument renderColoredLayer,
 * so tint, light, HD sets and binding stay DMZ's. Fail-soft: require = 0 on every hook. Since 0.2.5
 * {@link FaceAnimator#route} only swaps a layer this viewer draws from an HD set; a DMZ-file face stays still.
 */
@Mixin(targets = "com.dragonminez.client.render.layer.DMZSkinLayer", remap = false)
public abstract class DMZSkinLayerFaceMixin {
    @Shadow(remap = false)
    private void renderColoredLayer(BakedGeoModel model, PoseStack poseStack, AbstractClientPlayer animatable,
            MultiBufferSource bufferSource, String path, float[] rgb, float partialTick, int packedLight,
            int packedOverlay, float alpha, boolean applyTransformationTint) {
        throw new AssertionError();
    }

    @Inject(method = "renderFace", at = @At("HEAD"), remap = false, require = 0)
    private void legacytextures$faceBegin(PoseStack poseStack, AbstractClientPlayer animatable, BakedGeoModel model,
            MultiBufferSource bufferSource, AbstractClientPlayer player, StatsData stats, float partialTick,
            int packedLight, int packedOverlay, float alpha, CallbackInfo ci) {
        try {
            FaceAnimator.begin(player, stats, partialTick);
        } catch (Throwable t) {
            FaceAnimator.end();
        }
    }

    @Inject(method = "renderFace", at = @At("RETURN"), remap = false, require = 0)
    private void legacytextures$faceEnd(PoseStack poseStack, AbstractClientPlayer animatable, BakedGeoModel model,
            MultiBufferSource bufferSource, AbstractClientPlayer player, StatsData stats, float partialTick,
            int packedLight, int packedOverlay, float alpha, CallbackInfo ci) {
        FaceAnimator.end();
    }

    // the 11-argument overload (the trailing boolean); one literal, so the mixin target audit can read it
    @Inject(method = "renderColoredLayer(Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/lang/String;[FFIIFZ)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void legacytextures$faceRoute(BakedGeoModel model, PoseStack poseStack, AbstractClientPlayer animatable,
            MultiBufferSource bufferSource, String path, float[] rgb, float partialTick, int packedLight,
            int packedOverlay, float alpha, boolean applyTransformationTint, CallbackInfo ci) {
        String routed = FaceAnimator.route(path);
        if (!routed.equals(path)) {
            ci.cancel();
            if (routed != FaceAnimator.SKIP) {
                float[] tint = FaceAnimator.isScreamMouth(routed) ? FaceAnimator.WHITE : rgb;
                this.renderColoredLayer(model, poseStack, animatable, bufferSource, routed, tint, partialTick,
                        packedLight, packedOverlay, alpha, applyTransformationTint);
            }
        }
    }

    @Inject(method = "renderFadingColoredLayer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void legacytextures$faceFading(BakedGeoModel model, PoseStack poseStack, AbstractClientPlayer animatable,
            MultiBufferSource bufferSource, String path, float[] rgb, float partialTick, int packedLight,
            int packedOverlay, float alpha, CallbackInfo ci) {
        if (FaceAnimator.route(path) == FaceAnimator.SKIP) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFeatureShadow", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void legacytextures$faceShadow(BakedGeoModel model, PoseStack poseStack, AbstractClientPlayer animatable,
            MultiBufferSource bufferSource, String featurePath, float[] shadowSource, float pt, int pl, int po,
            float alpha, CallbackInfo ci) {
        if (FaceAnimator.hideShadow(featurePath)) {
            ci.cancel();
        }
    }
}
