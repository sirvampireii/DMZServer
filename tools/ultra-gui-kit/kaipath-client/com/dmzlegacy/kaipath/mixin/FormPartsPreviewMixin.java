package com.dmzlegacy.kaipath.mixin;

import com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.BodyPartPreview;
import java.util.Set;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a bridge preview is drawing, union {@code bodyParts} meshes into DMZUltra's
 * FormParts look. The mixin is not applied when that class is missing
 * ({@link BodyPartMixinPlugin}).
 */
@Mixin(targets = "com.dmzultra.client.forms.FormParts", remap = false)
public abstract class FormPartsPreviewMixin {
    private FormPartsPreviewMixin() {}

    @Inject(
            method =
                    "of(Lnet/minecraft/client/player/AbstractClientPlayer;)Lcom/dmzultra/client/forms/FormParts$Look;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void ultra$bodyParts(AbstractClientPlayer player, CallbackInfoReturnable<Object> cir) {
        try {
            Set<String> extra = BodyPartPreview.meshesFor(player);
            if (extra.isEmpty()) {
                return;
            }
            // Server-sent colors beat the reflective look read — a remote player's
            // reflective read comes back gray (0x808080, matching no fallback). The raw
            // payload rides along so future per-sync metadata reaches this merge site.
            Object merged =
                    BodyPartPreview.mergeLook(
                            cir.getReturnValue(),
                            extra,
                            BodyPartPreview.colorsFor(player),
                            BodyPartPreview.partsJsonFor(player));
            if (merged != null) {
                cir.setReturnValue(merged);
            }
        } catch (Throwable ignored) {
            // base entity still draws
        }
    }
}
