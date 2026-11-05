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
    };
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private CacheFlush() {
    }

    static void flushAll() {
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
                ((Map<?, ?>) f.get(null)).clear();
            } catch (Throwable t) {
                failed(map[0] + "." + map[1], t);
            }
        }
    }

    private static void failed(String what, Throwable t) {
        if (FAILED.add(what)) {
            LegacyTextures.LOG.warn("could not clear DragonMineZ cache {} ({}); a texture switch may need F3+T "
                    + "until this is updated for the installed DragonMineZ", what, t.toString());
        }
    }
}
