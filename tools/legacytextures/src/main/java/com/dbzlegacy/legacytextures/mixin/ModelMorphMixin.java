package com.dbzlegacy.legacytextures.mixin;

import com.dbzlegacy.legacytextures.client.LtHooks;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Transform blend (0.2.7), checked against DragonMineZ 2.2.1.2 ModelMorph.baked. The blend reads its
 * from and to models straight from GeckoLib's baked map, not through GeoModel.getBakedModel, so it skipped
 * GeoModelMixin: in HD mode it blended the vanilla model shapes under the HD texture (Frost Demon fifth
 * form: 19 cubes with other UVs, 15 more or fewer). Both map lookups now take the key the steady-state
 * model hook would ({@link LtHooks#modelTarget}), so the blend uses the same models as the rest of the
 * transform. Fail-soft: require = 0; a key without an HD model passes through.
 */
@Mixin(targets = "com.dragonminez.client.model.ModelMorph", remap = false)
public abstract class ModelMorphMixin {
    // both GeckoLibCache.getBakedModels().get(...) calls in baked(ResourceLocation): entry.from, then entry.to
    @ModifyArg(method = "baked", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            index = 0, require = 0, remap = false)
    private static Object legacytextures$blendModel(Object key) {
        try {
            return key instanceof ResourceLocation loc ? LtHooks.modelTarget(loc) : key;
        } catch (Throwable t) {
            LtHooks.hookFailed("ModelMorph.baked", t);
            return key;
        }
    }
}
