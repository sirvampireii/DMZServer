package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * LM 4.8.2: a vanilla banner design (a base dye colour and pattern layers) as the dojo stores it and as menus show it.
 *
 * <p>Storage ({@code DojoStore.Dojo.bannerBase} / {@code bannerPatterns}): the base is a dye name ({@code red}); each
 * layer is {@code <pattern hashname>:<dye id>} ({@code cr:0}), the same form a carried banner's NBT copies into. A
 * layer whose colour is a dye name ({@code cr:white}) is read too.
 *
 * <p>Icon ({@link #icon}): {@code lmbanner:<base>} or {@code lmbanner:<base>/<code>:<dye id>,<code>:<dye id>...} - the
 * stored list joined as it is, so the preview a menu draws is built from exactly what a save writes. The LM client
 * draws it as a banner with every layer ({@code client/BannerIcons}). Pattern choices come from the vanilla banner
 * pattern registry: every pattern a loom offers (no custom patterns), without the plain base.
 */
public final class BannerDesign {
    private BannerDesign() {}

    /** Icon namespace the LM client draws as a patterned banner. */
    public static final String ICON_NS = "lmbanner";
    /** Layers the editor builds (the loom's limit). */
    public static final int MAX_LAYERS = 6;
    /** Layers an icon carries at most (a carried banner made by command can have more than a loom allows). */
    public static final int ICON_MAX_LAYERS = 16;

    /** The 16 dye colours by dye id (DyeColor order). */
    public static final List<String> COLOURS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** Plain English names for the vanilla patterns, by hashname. A pattern missing here reads its registry path. */
    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        name("bl", "Bottom left square");
        name("br", "Bottom right square");
        name("tl", "Top left square");
        name("tr", "Top right square");
        name("bs", "Bottom stripe");
        name("ts", "Top stripe");
        name("ls", "Left stripe");
        name("rs", "Right stripe");
        name("cs", "Middle stripe");
        name("ms", "Cross stripe");
        name("drs", "Diagonal stripe");
        name("dls", "Back diagonal stripe");
        name("ss", "Small stripes");
        name("cr", "Diagonal cross");
        name("sc", "Cross");
        name("bt", "Bottom triangle");
        name("tt", "Top triangle");
        name("bts", "Bottom teeth");
        name("tts", "Top teeth");
        name("ld", "Top left half");
        name("rud", "Top right half");
        name("lud", "Bottom left half");
        name("rd", "Bottom right half");
        name("vh", "Left half");
        name("vhr", "Right half");
        name("hh", "Top half");
        name("hhb", "Bottom half");
        name("mc", "Circle");
        name("mr", "Diamond");
        name("bo", "Border");
        name("cbo", "Wavy border");
        name("gra", "Fade from top");
        name("gru", "Fade from bottom");
        name("bri", "Bricks");
        name("glb", "Globe");
        name("cre", "Creeper");
        name("sku", "Skull");
        name("flo", "Flower");
        name("moj", "Mojang logo");
        name("pig", "Piglin snout");
    }

    private static void name(String code, String label) {
        NAMES.put(code, label);
    }

    // ------------------------------------------------------------------ colours

    /** {@code red} for 14 or {@code "red"}; null when it is neither a dye id nor a dye name. */
    public static String colourOf(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (COLOURS.contains(t)) {
            return t;
        }
        try {
            int id = Integer.parseInt(t);
            return id >= 0 && id < COLOURS.size() ? COLOURS.get(id) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The dye id of a colour name or id, or -1. */
    public static int colourId(String raw) {
        String c = colourOf(raw);
        return c == null ? -1 : COLOURS.indexOf(c);
    }

    /** "Light blue" for {@code light_blue}. */
    public static String colourLabel(String colour) {
        String c = colourOf(colour);
        String b = (c == null ? "white" : c).replace('_', ' ');
        return Character.toUpperCase(b.charAt(0)) + b.substring(1);
    }

    /** A colour a pattern stands out in on {@code base}: black on light bases, white on dark ones. */
    public static int contrastOn(String base) {
        String c = colourOf(base);
        boolean light = c == null || List.of("white", "yellow", "lime", "light_gray", "light_blue", "pink", "orange",
                "magenta", "cyan").contains(c);
        return light ? COLOURS.indexOf("black") : COLOURS.indexOf("white");
    }

    // ------------------------------------------------------------------ patterns

    private static volatile List<String> codes = List.of();
    private static final Map<String, String> PATHS = new LinkedHashMap<>();

    /**
     * Every pattern a loom offers, in registry order, as hashnames: read from the vanilla banner pattern registry
     * ({@code BuiltInRegistries.BANNER_PATTERN}), without the plain base ({@code b}).
     */
    public static List<String> patterns() {
        List<String> now = codes;
        if (!now.isEmpty()) {
            return now;
        }
        List<String> out = new ArrayList<>();
        synchronized (PATHS) {
            // The registry is an Iterable (IdMap); seen through Object so the compile needs no DataFixerUpper types.
            Object registry = BuiltInRegistries.f_256878_;
            for (Object o : (Iterable<?>) registry) {
                if (!(o instanceof BannerPattern bp)) {
                    continue;
                }
                String hash = bp.m_58579_();
                if (hash == null || hash.isBlank() || "b".equals(hash)) {
                    continue;
                }
                PATHS.put(hash, registryPath(hash));
                out.add(hash);
            }
        }
        codes = Collections.unmodifiableList(out);
        return codes;
    }

    /** The registry path of a pattern ({@code stripe_bottom} for {@code bs}), or the hashname. */
    private static String registryPath(String hash) {
        try {
            net.minecraft.core.Holder<BannerPattern> h = BannerPattern.m_222700_(hash);
            if (h != null) {
                java.util.Optional<?> key = h.m_203543_();
                if (key.isPresent() && key.get() instanceof net.minecraft.resources.ResourceKey<?> rk) {
                    return rk.m_135782_().m_135815_();
                }
            }
        } catch (RuntimeException ignored) {
            // the hashname stands in
        }
        return hash;
    }

    /** True when {@code code} is a pattern from the registry the editor offers. */
    public static boolean knownPattern(String code) {
        return code != null && patterns().contains(code);
    }

    /** "Cross" for {@code sc}; a pattern missing from the table reads its registry path ("Stripe bottom"). */
    public static String patternName(String code) {
        String c = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        String n = NAMES.get(c);
        if (n != null) {
            return n;
        }
        patterns();
        String path = PATHS.getOrDefault(c, c);
        int slash = path.lastIndexOf('/');
        String tidy = (slash >= 0 ? path.substring(slash + 1) : path).replace('_', ' ').trim();
        if (tidy.isEmpty()) {
            return "Pattern";
        }
        return Character.toUpperCase(tidy.charAt(0)) + tidy.substring(1);
    }

    // ------------------------------------------------------------------ layers

    /** {@code cr:0} for a pattern and a colour (name or id): the stored form. */
    public static String layer(String code, String colour) {
        return code + ":" + Math.max(0, colourId(colour));
    }

    /** The pattern hashname of a stored layer. */
    public static String layerCode(String layer) {
        if (layer == null) {
            return "";
        }
        int c = layer.indexOf(':');
        return (c < 0 ? layer : layer.substring(0, c)).trim().toLowerCase(Locale.ROOT);
    }

    /** The colour name of a stored layer ({@code cr:0} or {@code cr:white}), or null. */
    public static String layerColour(String layer) {
        if (layer == null) {
            return null;
        }
        int c = layer.indexOf(':');
        return c < 0 ? null : colourOf(layer.substring(c + 1));
    }

    /** "Cross (white)". */
    public static String layerText(String layer) {
        String col = layerColour(layer);
        return patternName(layerCode(layer)) + " (" + (col == null ? "white" : col.replace('_', ' ')) + ")";
    }

    // ------------------------------------------------------------------ text

    /**
     * "Red with Cross (white), Border (black)" when it fits {@code maxChars}; else "Red, 4 layers"; "Red, plain" for
     * a banner without layers.
     */
    public static String describe(String base, List<String> layers, int maxChars) {
        String b = colourLabel(base == null || base.isBlank() ? "white" : base);
        int n = layers == null ? 0 : layers.size();
        if (n == 0) {
            return b + ", plain";
        }
        StringBuilder full = new StringBuilder(b).append(" with ");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                full.append(", ");
            }
            full.append(layerText(layers.get(i)));
        }
        if (full.length() <= maxChars) {
            return full.toString();
        }
        return b + ", " + n + (n == 1 ? " layer" : " layers");
    }

    // ------------------------------------------------------------------ icon

    /** The icon for a base and layers (the stored list, joined as it is). */
    public static String icon(String base, List<String> layers) {
        String b = colourOf(base);
        StringBuilder s = new StringBuilder(ICON_NS).append(':').append(b == null ? "white" : b);
        int shown = 0;
        if (layers != null) {
            for (String l : layers) {
                String code = layerCode(l);
                int col = colourId(l == null || l.indexOf(':') < 0 ? "" : l.substring(l.indexOf(':') + 1));
                if (!validCode(code) || col < 0 || shown >= ICON_MAX_LAYERS) {
                    continue;
                }
                s.append(shown == 0 ? '/' : ',').append(code).append(':').append(col);
                shown++;
            }
        }
        return s.toString();
    }

    /** True for {@code lmbanner:...}. */
    public static boolean isIcon(String raw) {
        return raw != null && raw.trim().toLowerCase(Locale.ROOT).startsWith(ICON_NS + ":");
    }

    /** The icon string tidied, or "" when it is not a valid banner icon (UltraStyle.normalizeIcon). */
    public static String normalizeIcon(String raw) {
        Parsed p = parse(raw);
        return p == null ? "" : icon(p.base(), p.layers());
    }

    /** A parsed icon: the base colour name and the layers as {@code code:id}. */
    public record Parsed(String base, List<String> layers) {}

    /** Parse {@code lmbanner:<base>[/<code>:<id>,...]}; null when malformed. */
    public static Parsed parse(String raw) {
        if (!isIcon(raw)) {
            return null;
        }
        String body = raw.trim().toLowerCase(Locale.ROOT).substring(ICON_NS.length() + 1);
        if (body.length() > 400) {
            return null;
        }
        int slash = body.indexOf('/');
        String base = colourOf(slash < 0 ? body : body.substring(0, slash));
        if (base == null) {
            return null;
        }
        List<String> layers = new ArrayList<>();
        if (slash >= 0 && slash < body.length() - 1) {
            for (String part : body.substring(slash + 1).split(",")) {
                int c = part.indexOf(':');
                if (c <= 0) {
                    return null;
                }
                String code = part.substring(0, c);
                int col = colourId(part.substring(c + 1));
                if (!validCode(code) || col < 0 || layers.size() >= ICON_MAX_LAYERS) {
                    return null;
                }
                layers.add(code + ":" + col);
            }
        }
        return new Parsed(base, layers);
    }

    private static boolean validCode(String code) {
        if (code == null || code.isEmpty() || code.length() > 16) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /**
     * The banner item for an icon: {@code minecraft:<base>_banner} with {@code BlockEntityTag.Patterns} (each
     * {@code Pattern} hashname and {@code Color} dye id), which the item renderer draws with every layer. Empty when
     * the icon is not a banner icon.
     */
    public static ItemStack stack(String icon) {
        Parsed p = parse(icon);
        if (p == null) {
            return ItemStack.f_41583_;
        }
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("minecraft", p.base() + "_banner"));
        if (item == null || item == Items.f_41852_) {
            return ItemStack.f_41583_;
        }
        ItemStack stack = new ItemStack(item);
        if (!p.layers().isEmpty()) {
            stack.m_41698_("BlockEntityTag").m_128365_("Patterns", patternsTag(p.layers()));
        }
        return stack;
    }

    /** The NBT pattern list ({@code Pattern} hashname, {@code Color} dye id) for stored layers. */
    public static ListTag patternsTag(List<String> layers) {
        ListTag list = new ListTag();
        for (String l : layers) {
            int c = l.indexOf(':');
            CompoundTag t = new CompoundTag();
            t.m_128359_("Pattern", l.substring(0, c));
            t.m_128405_("Color", Math.max(0, colourId(l.substring(c + 1))));
            list.add(t);
        }
        return list;
    }
}
