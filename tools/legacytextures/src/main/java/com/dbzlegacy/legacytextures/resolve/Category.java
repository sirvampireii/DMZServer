package com.dbzlegacy.legacytextures.resolve;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * What a resource belongs to, which decides which switch picks its set.
 * PLAYERS follows the players switch, ARMOR the NB Armors switch, GUI and WORLD the world switch.
 */
public enum Category {
    PLAYERS, ARMOR, GUI, WORLD;

    public final String key = name().toLowerCase(Locale.ROOT);

    public static Category parse(String key) {
        for (Category c : values()) {
            if (c.key.equals(key)) {
                return c;
            }
        }
        return null;
    }

    /** Null for anything a set never replaces (other mods, sounds, models, lang, data). */
    public static Category of(ResourceLocation loc) {
        return of(loc.m_135827_(), loc.m_135815_());
    }

    public static Category of(String ns, String path) {
        switch (ns) {
            case "nbarmors":
                return path.startsWith("textures/") ? ARMOR : null;
            case "dragonminez":
                if (path.startsWith("textures/entity/") || path.startsWith("textures/armor/")
                        || path.startsWith("geo/") || path.startsWith("animations/")) {
                    return PLAYERS;
                }
                return texturesOf(path);
            case "minecraft":
                return texturesOf(path);
            default:
                return null;
        }
    }

    private static Category texturesOf(String path) {
        if (path.startsWith("textures/gui/")) {
            return GUI;
        }
        return path.startsWith("textures/") ? WORLD : null;
    }

    /**
     * Namespaces whose missing textures draw as transparent instead of the purple-black checkerboard.
     * Only the two mods whose selections can name a file one viewer has and another lacks.
     */
    public static boolean blankWhenMissing(ResourceLocation loc) {
        String ns = loc.m_135827_();
        return ("dragonminez".equals(ns) || "nbarmors".equals(ns)) && loc.m_135815_().startsWith("textures/");
    }
}
