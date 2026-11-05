package com.dbzlegacy.legacytextures.resolve;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * One texture set from config/legacytextures/texture_sets/&lt;id&gt;.json.
 *
 * <p>A file {@code <ns>:<type>/<rest>} (type = textures, geo or animations) lives in this set at
 * {@code <namespace>:<type>/<basePath>/<ns>/<rest>}. The vanilla set (blank namespace) is the mods'
 * own files, so it is always complete; every other set may be partial and falls back.
 */
public final class TextureSet {
    public static final String VANILLA = "vanilla";

    public final String id;
    public final String name;
    public final String namespace;
    public final String basePath;
    public final Set<Category> categories;
    public final String fallback;

    public TextureSet(String id, String name, String namespace, String basePath, Set<Category> categories,
            String fallback) {
        this.id = id;
        this.name = name;
        this.namespace = namespace == null ? "" : namespace;
        this.basePath = trimSlashes(basePath);
        this.categories = categories.isEmpty() ? EnumSet.noneOf(Category.class) : EnumSet.copyOf(categories);
        this.fallback = fallback == null ? "" : fallback;
    }

    public boolean isVanilla() {
        return namespace.isEmpty();
    }

    public boolean covers(Category c) {
        return categories.contains(c);
    }

    /** Where this set keeps {@code loc}, or null when the path cannot be a set file. */
    public ResourceLocation map(ResourceLocation loc) {
        if (isVanilla()) {
            return loc;
        }
        String path = loc.m_135815_();
        int slash = path.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String mapped = path.substring(0, slash) + "/" + (basePath.isEmpty() ? "" : basePath + "/")
                + loc.m_135827_() + path.substring(slash);
        return ResourceLocation.m_214293_(namespace, mapped);
    }

    /** The reverse of {@link #map}: the source file a set file stands in for, or null. */
    public ResourceLocation source(String setPath) {
        if (isVanilla()) {
            return null;
        }
        int slash = setPath.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String type = setPath.substring(0, slash);
        String rest = setPath.substring(slash + 1);
        if (!basePath.isEmpty()) {
            if (!rest.startsWith(basePath + "/")) {
                return null;
            }
            rest = rest.substring(basePath.length() + 1);
        }
        int nsEnd = rest.indexOf('/');
        if (nsEnd <= 0) {
            return null;
        }
        return ResourceLocation.m_214293_(rest.substring(0, nsEnd), type + rest.substring(nsEnd));
    }

    private static String trimSlashes(String s) {
        String t = s == null ? "" : s.trim();
        while (t.startsWith("/")) {
            t = t.substring(1);
        }
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    @Override
    public String toString() {
        return id + " (" + (isVanilla() ? "mod jars" : namespace + ":*/" + basePath) + ", fallback " + fallback + ")";
    }
}
