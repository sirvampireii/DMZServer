package com.dbzlegacy.legacytextures.client;

import com.dbzlegacy.legacytextures.LegacyTextures;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.fml.ModList;

/**
 * Empties every DragonMineZ cache that remembers a texture or model answer, so a switch never draws
 * a stale (checkerboard) texture. Run on the main thread, before the next frame.
 *
 * <p>Checked against DragonMineZ 2.2.1.1. Each entry is looked up by name and fails soft: if a DMZ
 * update renames one, that entry logs once and is skipped, and the rest still run.
 * <ul>
 *   <li>TextureCounter.clearCache() - creator option counts; also clears DMZSkinLayer.VALIDATED_TEXTURES_CACHE</li>
 *   <li>DMZSkinLayer.clearValidatedTexturesCache() - getSafeTexture answers</li>
 *   <li>SkinGathererProvider.clearShadedLayers() - shaded body layers built from the image</li>
 *   <li>ArmorTextureResolver.clearCache() - armor (and damaged armor) existence</li>
 *   <li>DMZPlayerModel.MODEL_RESOLUTION_CACHE / FILE_EXISTS_CACHE - model choice per player state</li>
 *   <li>SkinGathererProvider.SHADED_LAYERS (0.2.3) - emptied by reflection after clearShadedLayers() too: the
 *       shaded body composites are keyed by the dragonminez: path, not the resolved file, and their
 *       dragonminez:shaded/ keys bypass the resolver, so a layer left here keeps an HD body after a switch
 *       to vanilla (Majin, Frost Demon, BioAndroid). A layer still here after the call is logged.</li>
 *   <li>DMZRendererCache.clear() - per-player GeckoLib renderers (rebuilt on next frame)</li>
 *   <li>DBSagaModel.clearCache() - saga NPC variant counts</li>
 * </ul>
 * GeckoLib's baked models need no flush: both variants are baked and the model hook picks the key.
 */
final class CacheFlush {
    private static final String DMZ = "com.dragonminez.client.";
    private static final String[][] STATIC_CALLS = {
            {DMZ + "util.TextureCounter", "clearCache"},
            {DMZ + "render.layer.DMZSkinLayer", "clearValidatedTexturesCache"},
            {DMZ + "util.SkinGathererProvider", "clearShadedLayers"},
            {DMZ + "util.ArmorTextureResolver", "clearCache"},
            {DMZ + "render.DMZRendererCache", "clear"},
            {DMZ + "init.entities.model.sagas.DBSagaModel", "clearCache"},
    };
    private static final String[][] STATIC_MAPS = {
            {DMZ + "model.DMZPlayerModel", "MODEL_RESOLUTION_CACHE"},
            {DMZ + "model.DMZPlayerModel", "FILE_EXISTS_CACHE"},
            // after the clearShadedLayers() call above, which also frees the layers' GL textures
            {DMZ + "util.SkinGathererProvider", "SHADED_LAYERS"},
    };
    private static volatile int shadedLeft;
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private CacheFlush() {
    }

    /** @param change the choice being switched to, named in the log if a cache had to be forced empty */
    static void flushAll(Object change) {
        if (!ModList.get().isLoaded("dragonminez")) {
            return;
        }
        for (String[] call : STATIC_CALLS) {
            try {
                Method m = Class.forName(call[0]).getMethod(call[1]);
                m.invoke(null);
            } catch (Throwable t) {
                failed(call[0] + "." + call[1] + "()", t);
            }
        }
        for (String[] map : STATIC_MAPS) {
            try {
                Field f = Class.forName(map[0]).getDeclaredField(map[1]);
                f.setAccessible(true);
                Map<?, ?> cache = (Map<?, ?>) f.get(null);
                int left = cache.size();
                cache.clear();
                if (map[1].equals("SHADED_LAYERS")) {
                    shadedLeft = left;
                    if (left > 0) {
                        LegacyTextures.LOG.warn("DragonMineZ still had {} shaded body layer(s) after clearShadedLayers() "
                                + "on the switch to {}; Legacy Textures dropped them so the body redraws in the new look",
                                left, change);
                    }
                }
            } catch (Throwable t) {
                failed(map[0] + "." + map[1], t);
            }
        }
    }

    /** Shaded body layers DragonMineZ's own clear left behind on the last switch (0 when it worked). */
    static int shadedLeft() {
        return shadedLeft;
    }

    /** DMZ caches that could not be cleared (each listed once); empty when every switch is clean. */
    static Set<String> failed() {
        return FAILED;
    }

    private static void failed(String what, Throwable t) {
        if (FAILED.add(what)) {
            LegacyTextures.LOG.warn("could not clear DragonMineZ cache {} ({}); a texture switch may need F3+T "
                    + "until this is updated for the installed DragonMineZ", what, t.toString());
        }
    }
}
