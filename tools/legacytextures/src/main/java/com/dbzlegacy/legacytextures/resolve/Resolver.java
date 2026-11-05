package com.dbzlegacy.legacytextures.resolve;

import com.dbzlegacy.legacytextures.TextureMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Picks the file a viewer's game should use for a mod texture or model.
 *
 * <p>For a handled resource the active set for its category is tried first, then each fallback set,
 * and the mod's own file last. The answer for each resource is cached until {@link #configure} or
 * {@link #clear} (mode change or resource reload), which is the only thing that makes it stale.
 */
public final class Resolver {
    private final Predicate<ResourceLocation> rawExists;
    private final Map<ResourceLocation, ResourceLocation> variants = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Boolean> exists = new ConcurrentHashMap<>();
    private volatile SetRegistry sets = SetRegistry.vanillaOnly();
    private volatile TextureSet players;
    private volatile TextureSet armor;
    private volatile TextureSet world;

    /** {@code rawExists} must answer from the loaded packs without going back through the hooks. */
    public Resolver(Predicate<ResourceLocation> rawExists) {
        this.rawExists = rawExists;
        TextureSet v = sets.vanilla();
        players = armor = world = v;
    }

    /**
     * @param mode the viewer's current choice (players and NB Armors apply at once)
     * @param worldHd whether world HD is in the loaded packs (it only changes with a reload)
     * @param worldSet the set id the last reload used for the world
     */
    public synchronized void configure(SetRegistry registry, TextureMode mode, boolean worldHd, String worldSet) {
        sets = registry;
        TextureSet hd = registry.hdOrFallback(mode.set);
        players = mode.players ? hd : registry.vanilla();
        armor = mode.nbArmor ? hd : registry.vanilla();
        world = worldHd ? registry.hdOrFallback(worldSet) : registry.vanilla();
        clear();
    }

    public void clear() {
        variants.clear();
        exists.clear();
    }

    public SetRegistry sets() {
        return sets;
    }

    public TextureSet activeSet(Category c) {
        return switch (c) {
            case PLAYERS -> players;
            case ARMOR -> armor;
            case GUI, WORLD -> world;
        };
    }

    /** The file to use instead of {@code loc}, or {@code loc} itself (same instance) for the mod's own file. */
    public ResourceLocation variant(ResourceLocation loc) {
        Category c = Category.of(loc);
        if (c == null) {
            return loc;
        }
        TextureSet start = activeSet(c);
        if (start.isVanilla()) {
            return loc;
        }
        ResourceLocation hit = variants.get(loc);
        if (hit == null) {
            hit = walk(loc, c, start);
            variants.put(loc, hit);
        }
        return hit;
    }

    private ResourceLocation walk(ResourceLocation loc, Category c, TextureSet start) {
        for (TextureSet s : sets.chain(start)) {
            if (s.isVanilla()) {
                return loc;
            }
            if (!s.covers(c)) {
                continue;
            }
            ResourceLocation candidate = s.map(loc);
            if (candidate != null && exists(candidate)) {
                return candidate;
            }
        }
        return loc;
    }

    public boolean exists(ResourceLocation loc) {
        Boolean known = exists.get(loc);
        if (known == null) {
            known = rawExists.test(loc);
            exists.put(loc, known);
        }
        return known;
    }
}
