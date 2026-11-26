package com.dbzlegacy.adaptivedifficulty.client.mixin;

import com.dbzlegacy.adaptivedifficulty.client.BodyPartPreview;
import java.util.Set;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LM 4.7.61 (client only): while an LM native menu draws its model preview, union the preview's
 * {@code bodyParts} meshes into DMZUltra 0.5.7's FormParts look (Character Services, Preview forms).
 * LM 4.8.2: outside a preview, union the server's body-parts world sync (native message 5, which the
 * KaiPath bridge used to carry) for that player, so equipped pieces draw in the world on the LM client
 * alone. A player with no synced pieces is left as DMZUltra drew them. Applied only when DMZUltra is installed
 * ({@code LegacyMechanicsMixinPlugin}); both injections are optional, so a renamed method degrades to the
 * plain form look instead of a broken client.
 */
@Mixin(targets = "com.dmzultra.client.forms.FormParts", remap = false)
public abstract class FormPartsPreviewMixin {
    @Inject(
            method = "of(Lnet/minecraft/client/player/AbstractClientPlayer;)Lcom/dmzultra/client/forms/FormParts$Look;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void lm$previewParts(AbstractClientPlayer player, CallbackInfoReturnable<Object> cir) {
        lm$merge(player, cir);
    }

    @Inject(
            method = "of(Lnet/minecraft/client/player/AbstractClientPlayer;Ljava/lang/String;)Lcom/dmzultra/client/forms/FormParts$Look;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void lm$previewPartsFor(AbstractClientPlayer player, String key, CallbackInfoReturnable<Object> cir) {
        lm$merge(player, cir);
    }

    private static void lm$merge(AbstractClientPlayer player, CallbackInfoReturnable<Object> cir) {
        try {
            // 4.8.2: the preview's pieces in a menu, the server-synced world pieces everywhere else.
            boolean preview = BodyPartPreview.previewActive();
            if (!preview && !BodyPartPreview.hasWorldSync(player)) {
                return;
            }
            Set<String> extra = preview
                    ? BodyPartPreview.previewMeshesFor(player)
                    : BodyPartPreview.meshesFor(player);
            if (extra.isEmpty()) {
                return;
            }
            Object merged = BodyPartPreview.mergeLook(cir.getReturnValue(), extra, BodyPartPreview.colorsFor(player),
                    BodyPartPreview.partsJsonFor(player));
            if (merged != null) {
                cir.setReturnValue(merged);
            }
        } catch (Throwable ignored) {
            // the form look still draws
        }
    }
}
