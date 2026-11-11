package com.dbzlegacy.legacytextures.resolve;

import com.dbzlegacy.legacytextures.TextureMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Picks the file a viewer's game should use for a mod texture or model.
 *
 * <p>For a handled resource the active set for its category is tried first, then each fallback set,
 * and the mod's own file last. The answer for each resource is cached until {@link #configure} or
 * {@link #clear} (mode change or resource reload), which is the only thing that makes it stale.
 *
 * <p>Cross-race aliases (0.2.5, {@link Aliases}) come after every exact HD lookup misses and before the
 * mod's own file: a player texture the race ships but no set has may draw a donor race's HD file.
 */
public final class Resolver {
    private final Predicate<ResourceLocation> rawExists;
    private final Map<ResourceLocation, ResourceLocation> variants = new ConcurrentHashMap<>();
    /** The viewer's HD set (or its fallback): vanilla mode's last resort for files the mod lacks. */
    private volatile TextureSet hd;
    private final Map<ResourceLocation, Boolean> exists = new ConcurrentHashMap<>();
    private volatile SetRegistry sets = SetRegistry.vanillaOnly();
    private volatile TextureSet players;
    private volatile TextureSet armor;
    private volatile TextureSet world;
    private volatile Aliases aliases = Aliases.NONE;
    /** Alias misses already logged; kept across clears so each is logged once per game. */
    private final Set<ResourceLocation> loggedMisses = ConcurrentHashMap.newKeySet();
    private volatile Consumer<String> log = s -> { };

    /** {@code rawExists} must answer from the loaded packs without going back through the hooks. */
    public Resolver(Predicate<ResourceLocation> rawExists) {
        this.rawExists = rawExists;
        TextureSet v = sets.vanilla();
        players = armor = world = hd = v;
    }

    /**
     * @param mode the viewer's current choice (players and NB Armors apply at once)
     * @param worldHd whether world HD is in the loaded packs (it only changes with a reload)
     * @param worldSet the set id the last reload used for the world
     */
    public synchronized void configure(SetRegistry registry, TextureMode mode, boolean worldHd, String worldSet) {
        sets = registry;
        hd = registry.hdOrFallback(mode.set);
        players = mode.players ? hd : registry.vanilla();
        armor = mode.nbArmor ? hd : registry.vanilla();
        world = worldHd ? registry.hdOrFallback(worldSet) : registry.vanilla();
        clear();
    }

    /** Takes effect at once: cached answers are dropped. */
    public void aliases(Aliases next, Consumer<String> missLog) {
        aliases = next;
        log = missLog;
        clear();
    }

    public int aliasCount() {
        return aliases.entries().size();
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

    /**
     * The file to use instead of {@code loc}, or {@code loc} itself (same instance) for the mod's own file.
     * Vanilla mode prefers the mod's own file; when the mod has no such file (an HD-only body type, 0.2.2)
     * the viewer's HD set is the last resort, so a vanilla viewer sees a body instead of a floating head.
     * A file missing from both stays {@code loc} (the bind hook then draws BLANK, as before).
     */
    public ResourceLocation variant(ResourceLocation loc) {
        Category c = Category.of(loc);
        if (c == null) {
            return loc;
        }
        TextureSet start = activeSet(c);
        if (start.isVanilla()) {
            if (exists(loc)) {
                return loc;
            }
            start = hd;
        }
        ResourceLocation hit = variants.get(loc);
        if (hit == null) {
            hit = walk(loc, c, start);
            variants.put(loc, hit);
        }
        return hit;
    }

    private ResourceLocation walk(ResourceLocation loc, Category c, TextureSet start) {
        List<TextureSet> chain = sets.chain(start);
        for (TextureSet s : chain) {
            if (s.isVanilla()) {
                break;
            }
            if (!s.covers(c)) {
                continue;
            }
            ResourceLocation candidate = s.map(loc);
            if (candidate != null && exists(candidate)) {
                return candidate;
            }
        }
        ResourceLocation borrowed = borrow(loc, c, chain);
        return borrowed != null ? borrowed : loc;
    }

    /**
     * A donor race's HD file for a player texture. Only for a file the race ships itself, so an alias
     * never adds a creator option; vanilla mode never gets here for such a file.
     */
    private ResourceLocation borrow(ResourceLocation loc, Category c, List<TextureSet> chain) {
        if (c != Category.PLAYERS) {
            return null;
        }
        ResourceLocation donor = aliases.donor(loc);
        if (donor == null || !exists(loc)) {
            return null;
        }
        for (TextureSet s : chain) {
            if (s.isVanilla()) {
                break;
            }
            ResourceLocation candidate = s.covers(c) ? s.map(donor) : null;
            if (candidate != null && exists(candidate)) {
                return candidate;
            }
        }
        if (loggedMisses.add(loc)) {
            log.accept("alias: no HD donor " + donor + " for " + loc + "; drawing its own file");
        }
        return null;
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
