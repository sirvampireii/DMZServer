package com.dbzlegacy.legacytextures.client;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.resolve.Category;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** What the three mixins call. Every method returns the input unchanged when it has nothing to do. */
public final class LtHooks {
    public static final ResourceLocation BLANK = ResourceLocation.fromNamespaceAndPath(LegacyTextures.MODID, "textures/blank.png");
    /** Above zero while a lookup must see the loaded packs as they are (a key loading its own file, a raw probe). */
    private static final ThreadLocal<int[]> RAW = ThreadLocal.withInitial(() -> new int[1]);
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    // How often each hook ran since start (racy counters; /legacytextures status shows them as health).
    static long binds;
    static long lookups;
    static long models;

    private LtHooks() {
    }

    /**
     * TextureManager: the key to bind. An HD variant when the viewer's set has one; the mod's own
     * file otherwise; and a transparent texture instead of the purple-black checkerboard when a
     * DragonMineZ or NB Armors texture is missing from every set the viewer has (for example a
     * player wearing a body type only the HD set ships, seen by a viewer on vanilla).
     */
    public static ResourceLocation bindTarget(Map<ResourceLocation, AbstractTexture> loaded, ResourceLocation loc) {
        if (loc == null) {
            return null;
        }
        binds++;
        ResourceLocation v = ClientState.RESOLVER.variant(loc);
        if (v != loc) {
            return v;
        }
        if (Category.blankWhenMissing(loc) && !ClientState.RESOLVER.exists(loc)) {
            AbstractTexture tex = loaded.get(loc);
            // A texture some code registered itself (not a file) is left alone.
            if (tex == null || tex == MissingTextureAtlasSprite.m_118080_()) {
                return BLANK;
            }
        }
        return loc;
    }

    /** Client resource manager: the file to read instead of {@code loc}, or null to read {@code loc}. */
    public static ResourceLocation lookupTarget(ResourceManager self, ResourceLocation loc) {
        if (loc == null || RAW.get()[0] > 0) {
            return null;
        }
        lookups++;
        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || self != mc.m_91098_()) {
            return null;
        }
        ResourceLocation v = ClientState.RESOLVER.variant(loc);
        return v == loc ? null : v;
    }

    /** GeckoLib: the baked model to use; an HD model only when GeckoLib actually baked it. */
    public static ResourceLocation modelTarget(ResourceLocation loc) {
        if (loc == null) {
            return null;
        }
        models++;
        ResourceLocation v = ClientState.RESOLVER.variant(loc);
        return v != loc && GeckoBridge.isBaked(v) ? v : loc;
    }

    /** Whether the loaded packs have {@code loc}, answered without any set redirect. */
    static boolean rawExists(ResourceLocation loc) {
        Minecraft mc = Minecraft.m_91087_();
        ResourceManager rm = mc == null ? null : mc.m_91098_();
        if (rm == null) {
            return true;
        }
        RAW.get()[0]++;
        try {
            return rm.m_213713_(loc).isPresent();
        } finally {
            RAW.get()[0]--;
        }
    }

    /**
     * TextureManager.loadTexture start/end: a texture key always loads its own file. Without this a
     * key like dragonminez:...png loaded while HD is on would hold the HD image, and switching back
     * would draw it (the stale-cache bug). The HD file has its own key.
     */
    public static void keyLoad(boolean start) {
        int[] depth = RAW.get();
        depth[0] = start ? depth[0] + 1 : Math.max(0, depth[0] - 1);
    }

    /** Hooks that threw at least once (each then keeps drawing the mods' own textures for that call). */
    static Set<String> failedHooks() {
        return FAILED;
    }

    public static void hookFailed(String hook, Throwable t) {
        if (FAILED.add(hook)) {
            LegacyTextures.LOG.error("{} hook failed; drawing the mods' own textures from here on for that call", hook, t);
        }
    }
}
