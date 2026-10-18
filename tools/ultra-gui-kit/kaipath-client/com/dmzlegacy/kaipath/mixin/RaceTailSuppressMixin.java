package com.dmzlegacy.kaipath.mixin;

import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.BodyPartPreview;
import com.dragonminez.client.util.BoneVisibilityHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Hides the natural race tail while a chosen tail piece is equipped.
 *
 * <p>The two tails come from two systems that never see each other. A shop tail is a
 * {@code part.<mesh>} bone inside DMZUltra's FormParts look, which
 * {@link FormPartsPreviewMixin} unions {@code bodyParts} into. The natural Saiyan tail is the
 * base geo bones {@code tail1}/{@code tail1m}, shown by DragonMineZ from
 * {@code isHasSaiyanTail()} alone. The single-tail rule in the sync payload can only dedupe
 * inside {@code bodyParts}, so a player with both otherwise draws two tails.
 *
 * <p>After DragonMineZ's visibility pass has said what it will say, re-hide the race bones for
 * as long as the server reports a chosen piece. Re-evaluated every frame, so unequipping the
 * piece simply stops the hide and the natural tail comes back. Skipped entirely when DragonMineZ
 * is missing ({@link BodyPartMixinPlugin}), and the injection is optional, so a version that
 * renames the pass degrades to no suppression rather than a broken client.
 */
@Mixin(targets = "com.dragonminez.client.util.BoneVisibilityHandler", remap = false)
public abstract class RaceTailSuppressMixin {
    private RaceTailSuppressMixin() {}

    @Inject(
            method =
                    "updateVisibility(Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;"
                            + "Lnet/minecraft/client/player/AbstractClientPlayer;"
                            + "Lsoftware/bernie/geckolib/renderer/layer/GeoRenderLayer;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private static void ultra$suppressRaceTail(
            BakedGeoModel model, AbstractClientPlayer player, GeoRenderLayer<?> layer, CallbackInfo ci) {
        try {
            if (model != null
                    && player != null
                    && BodyPartPreview.suppressesRaceTail(player)) {
                BoneVisibilityHandler.setBonesHidden(model, true, "tail1", "tail1m");
            }
        } catch (Throwable ignored) {
            // the base render continues with whatever visibility is already set
        }
    }
}
