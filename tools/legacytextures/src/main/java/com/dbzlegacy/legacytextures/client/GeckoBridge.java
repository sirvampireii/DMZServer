package com.dbzlegacy.legacytextures.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;

/** Kept apart so nothing loads a GeckoLib class unless the GeckoLib hook is live. */
final class GeckoBridge {
    private GeckoBridge() {
    }

    static boolean isBaked(ResourceLocation loc) {
        return GeckoLibCache.getBakedModels().containsKey(loc);
    }
}
