import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * DojoKit - the LM dojo's interior and grounds on top of a BuildKit hall (LegacyMechanics 4.7.68).
 * <p>
 * The toolkit builds the hall's shell from a recipe (tools/dojo/hall_t1..3.json: BuildKit, japanese style). This fills
 * the gap BuildKit has no part for: the rooms inside one hall. Every position comes from the recipe's "dojo" section
 * (foundation coordinates: x / z from the north-west corner of parts[0], the foundation terrace):
 * <pre>
 *   hall      the hall walls [x0, z0, x1, z1]            rings / ringSize   ring centres on the hall floor (up to 6)
 *   wingZ     the partition between the hall floor and the south wing; rooms {name: [xFrom, xTo]}; corridor [x0, x1]
 *   balcony   {"z": [z0, z1], "up": 5}: the spectator mezzanine on the north wall, "up" layers above the floor
 *   doors     main [x0, x1] in the south wall, porch [z0, z1] (the entrance porch BuildKit builds)
 *   spawn / spectate   [x, z] landing points (outside the porch / on the balcony)
 * </pre>
 * Tiers set the materials (BuildKit's style per recipe + the interior palettes below); styles set only the accents
 * (banners, ring inlays, cushions, room dressing). Output per style and tier: the whole plot as a structure file, and
 * the pictures (floor plan, arena from the balcony, training area, outside by day and night).
 * <pre>
 *   DojoKit make &lt;hall_t1.nbt,hall_t2.nbt,hall_t3.nbt&gt; &lt;recipe.json&gt; &lt;outNbtDir&gt; &lt;outPictureDir&gt; [-only style_tier] [-noviews]
 * </pre>
 */
public final class DojoKit {
    static final String[] STYLES = {"ki", "melee", "strike", "evasion"};
    static final Map<String, String> STYLE_NAMES = Map.of("ki", "Ki Masters", "melee", "Melee", "strike", "Strike Moves",
            "evasion", "Evasion");
    static final String[] TIER_NAMES = {"", "Basic", "Refined", "Masterwork"};

    /** Interior palettes per tier (the shell's own materials come from the BuildKit recipe). */
    static final List<Map<String, String>> BONES = List.of(Map.of(),
            Map.ofEntries(Map.entry("F", "oak_planks"), Map.entry("Y", "oak_planks"), Map.entry("P", "spruce_log[axis=y]"),
                    Map.entry("J", "spruce_planks"), Map.entry("W", "birch_planks"), Map.entry("Q", "spruce_planks"),
                    Map.entry("S", "spruce_stairs"), Map.entry("H", "spruce_fence"), Map.entry("O", "dirt_path"),
                    Map.entry("C", "spruce_planks"), Map.entry("G", "red_concrete")),
            Map.ofEntries(Map.entry("F", "spruce_planks"), Map.entry("Y", "stripped_bamboo_block[axis=x]"),
                    Map.entry("P", "dark_oak_log[axis=y]"), Map.entry("J", "dark_oak_planks"), Map.entry("W", "white_concrete"),
                    Map.entry("Q", "dark_oak_planks"), Map.entry("S", "dark_oak_stairs"), Map.entry("H", "dark_oak_fence"),
                    Map.entry("O", "gravel"), Map.entry("C", "spruce_planks"), Map.entry("G", "red_concrete")),
            Map.ofEntries(Map.entry("F", "dark_oak_planks"), Map.entry("Y", "stripped_bamboo_block[axis=x]"),
                    Map.entry("P", "stripped_dark_oak_log[axis=y]"), Map.entry("J", "dark_oak_planks"),
                    Map.entry("W", "white_concrete"), Map.entry("Q", "stripped_dark_oak_wood[axis=y]"),
                    Map.entry("S", "dark_oak_stairs"), Map.entry("H", "dark_oak_fence"), Map.entry("O", "polished_andesite"),
                    Map.entry("C", "dark_oak_planks"), Map.entry("G", "red_concrete")));

    /** Style accents per tier: A banner / cloth colour, B ring border, I inlay, L light, M cushions, D and E the
     *  training area's dummies and drill pieces, V leaves. */
    static final Map<String, List<Map<String, String>>> ACCENTS = new LinkedHashMap<>();
    static {
        ACCENTS.put("ki", List.of(Map.of(),
                acc("light_blue", "light_blue_concrete", "calcite", "lantern[hanging=false]", "light_blue_carpet", "white_wool", "light_blue_wool", "azalea_leaves"),
                acc("light_blue", "light_blue_concrete", "light_blue_glazed_terracotta", "soul_lantern[hanging=false]", "light_blue_carpet", "calcite", "light_blue_stained_glass", "azalea_leaves"),
                acc("cyan", "cyan_concrete", "diamond_block", "soul_lantern[hanging=false]", "cyan_carpet", "beacon", "sea_lantern", "flowering_azalea_leaves")));
        ACCENTS.put("melee", List.of(Map.of(),
                acc("red", "red_wool", "hay_block[axis=y]", "lantern[hanging=false]", "red_carpet", "hay_block[axis=y]", "target", "oak_leaves"),
                acc("red", "red_concrete", "hay_block[axis=y]", "lantern[hanging=false]", "red_carpet", "hay_block[axis=y]", "target", "oak_leaves"),
                acc("red", "red_concrete", "gold_block", "lantern[hanging=false]", "red_carpet", "hay_block[axis=y]", "target", "cherry_leaves")));
        ACCENTS.put("strike", List.of(Map.of(),
                acc("orange", "andesite", "smooth_stone", "lantern[hanging=false]", "gray_carpet", "anvil[facing=north]", "smooth_stone", "oak_leaves"),
                acc("orange", "orange_terracotta", "polished_andesite", "lantern[hanging=false]", "orange_carpet", "anvil[facing=north]", "iron_block", "oak_leaves"),
                acc("orange", "orange_concrete", "copper_block", "lantern[hanging=false]", "orange_carpet", "anvil[facing=north]", "iron_block", "oak_leaves")));
        ACCENTS.put("evasion", List.of(Map.of(),
                acc("green", "bamboo_block[axis=y]", "moss_block", "lantern[hanging=false]", "green_carpet", "bamboo_block[axis=y]", "moss_block", "oak_leaves"),
                acc("green", "stripped_bamboo_block[axis=y]", "moss_block", "lantern[hanging=false]", "lime_carpet", "bamboo_block[axis=y]", "azalea", "azalea_leaves"),
                acc("lime", "emerald_block", "moss_block", "lantern[hanging=false]", "lime_carpet", "bamboo_block[axis=y]", "flowering_azalea", "cherry_leaves")));
    }

    static Map<String, String> acc(String colour, String b, String i, String l, String m, String d, String e, String v) {
        String k = switch (colour) {                    // K: the light set flush into a ring floor, in the style's colour
            case "light_blue", "cyan" -> "sea_lantern";
            case "red" -> "ochre_froglight[axis=y]";
            case "orange" -> "shroomlight";
            default -> "verdant_froglight[axis=y]";
        };
        return Map.of("A", colour + "_wool", "colour", colour, "B", b, "I", i, "L", l, "M", m, "D", d, "E", e,
                "V", v + "[distance=7,persistent=true,waterlogged=false]", "K", k);
    }

    // ------------------------------------------------------------------ layout (the recipe's "dojo" section)
    static final class Layout {
        int[] hall, corridor, mainDoor, porch, spawn, spectate, balconyZ;
        int ringSize, wingZ, balconyUp, margin;
        List<int[]> rings = new ArrayList<>();
        Map<String, int[]> rooms = new LinkedHashMap<>();
        int footW, footD, footX0, footZ0;   // parts[0] in recipe coordinates
        int hallH;                          // parts[1]: floors x floorH, the hall body's height in layers

        static int[] ints(Object o) {
            List<?> l = (List<?>) o;
            int[] r = new int[l.size()];
            for (int i = 0; i < r.length; i++) r[i] = ((Number) l.get(i)).intValue();
            return r;
        }

        static Layout of(Map<String, Object> recipe) {
            Layout L = new Layout();
            Map<String, Object> d = ModelPreview.obj(recipe.get("dojo"));
            if (d == null) throw new IllegalArgumentException("the recipe has no \"dojo\" section");
            L.hall = ints(d.get("hall"));
            L.ringSize = ((Number) d.get("ringSize")).intValue();
            for (Object o : (List<?>) d.get("rings")) L.rings.add(ints(o));
            L.wingZ = ((Number) d.get("wingZ")).intValue();
            L.corridor = ints(d.get("corridor"));
            for (Map.Entry<String, Object> e : ModelPreview.obj(d.get("rooms")).entrySet()) L.rooms.put(e.getKey(), ints(e.getValue()));
            Map<String, Object> b = ModelPreview.obj(d.get("balcony"));
            L.balconyZ = ints(b.get("z"));
            L.balconyUp = ((Number) b.get("up")).intValue();
            Map<String, Object> doors = ModelPreview.obj(d.get("doors"));
            L.mainDoor = ints(doors.get("main"));
            L.porch = ints(doors.get("porch"));
            L.spawn = ints(d.get("spawn"));
            L.spectate = ints(d.get("spectate"));
            L.margin = ((Number) d.getOrDefault("margin", 14)).intValue();
            Map<String, Object> p0 = ModelPreview.obj(((List<?>) recipe.get("parts")).get(0));
            int[] size = ints(p0.get("size")), at = ints(p0.get("at"));
            L.footW = size[0]; L.footD = size[1];
            L.footX0 = at[0] - size[0] / 2; L.footZ0 = at[1] - size[1] / 2;
            Map<String, Object> p1 = ModelPreview.obj(((List<?>) recipe.get("parts")).get(1));
            L.hallH = ((Number) p1.get("floors")).intValue() * ((Number) p1.get("floorH")).intValue();
            return L;
        }
    }

    // ------------------------------------------------------------------ one dojo
    final Structure s;          // the whole plot
    final Layout L;
    final Map<String, String> bone, accent;
    final int tier;
    final String style;
    int fx, fz, floorY, ceilY;  // the foundation's north-west corner in plot coordinates; the floor (standing level)

    DojoKit(Structure hall, Layout L, int tier, String style) {
        this.L = L; this.tier = tier; this.style = style;
        this.bone = BONES.get(tier);
        this.accent = ACCENTS.get(style).get(tier);
        int m = L.margin;
        s = new Structure(hall.sx + 2 * m, hall.sy + 14, hall.sz + 2 * m);
        // where the foundation sits inside the hall structure: the extent of its lowest layer
        int bx = Integer.MAX_VALUE, bz = Integer.MAX_VALUE, by = -1;
        for (int y = 0; y < hall.sy && by < 0; y++)
            for (int z = 0; z < hall.sz; z++) for (int x = 0; x < hall.sx; x++)
                if (hall.isBlock(x, y, z)) { by = y; bx = Math.min(bx, x); bz = Math.min(bz, z); }
        fx = m + bx; fz = m + bz;
        s.fill(0, 0, 0, s.sx - 1, 0, s.sz - 1, "minecraft:grass_block[snowy=false]");
        s.paste(hall, m, 1 - by, m, true);
        // the floor: the first air above a block in the middle of the hall floor
        int cx = fx + (L.hall[0] + L.hall[2]) / 2, cz = fz + (L.hall[1] + L.wingZ) / 2;
        floorY = -1;
        for (int y = 1; y < s.sy && floorY < 0; y++) if (s.isAirOrVoid(cx, y, cz) && s.isBlock(cx, y - 1, cz)) floorY = y;
        if (floorY < 0) throw new IllegalStateException("no hall floor found");
        ceilY = floorY - 1 + L.hallH;                   // the top of the hall's walls: DojoKit lays the ceiling there
    }

    String b(String k) {
        String v = accent.containsKey(k) ? accent.get(k) : bone.get(k);
        if (v == null) throw new IllegalArgumentException("no block for " + k);
        return v.contains(":") ? v : "minecraft:" + v;
    }
    void put(int x, int y, int z, String state) { s.set(fx + x, y, fz + z, state.contains(":") ? state : "minecraft:" + state); }
    void fill(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
        s.fill(fx + Math.min(x0, x1), Math.min(y0, y1), fz + Math.min(z0, z1), fx + Math.max(x0, x1), Math.max(y0, y1), fz + Math.max(z0, z1),
                state.contains(":") ? state : "minecraft:" + state);
    }
    String air() { return Structure.AIR; }
    String banner(String facing) { return "minecraft:" + accent.get("colour") + "_wall_banner[facing=" + facing + "]"; }

    // ------------------------------------------------------------------ the build
    void build(int rings, int rooms, int balcony) {
        interior();
        hallFloor(rings);
        wing(rooms);
        if (balcony > 0) balcony(balcony);
        doors();
        outside();
        grounds();
        roofHollow();
    }

    /** The space between the hall's ceiling and its roof is no room: structure void, so placing the dojo leaves it
     *  as it is (air in the dojo world) and nothing counts it as a sealed room. */
    void roofHollow() {
        int[] h = L.hall;
        for (int x = h[0] + 1; x < h[2]; x++)
            for (int z = h[1] + 1; z < h[3]; z++)
                for (int y = ceilY + 1; y < s.sy; y++) {
                    if (!s.isAirOrVoid(fx + x, y, fz + z)) break;
                    s.set(fx + x, y, fz + z, Structure.VOID);
                }
    }

    /** Empty the hall below its ceiling (BuildKit made two storeys; the hall floor runs full height). */
    void interior() {
        int[] h = L.hall;
        fill(h[0] + 1, floorY, h[1] + 1, h[2] - 1, ceilY - 1, h[3] - 1, air());
        fill(h[0] + 1, floorY - 1, h[1] + 1, h[2] - 1, floorY - 1, h[3] - 1, b("F"));
        fill(h[0] + 1, ceilY, h[1] + 1, h[2] - 1, ceilY, h[3] - 1, b("C"));
        // ceiling beams across the hall, a lantern on a chain between each pair
        for (int z = h[1] + 4; z < L.wingZ; z += 8) {
            fill(h[0] + 1, ceilY - 1, z, h[2] - 1, ceilY - 1, z, "minecraft:dark_oak_log[axis=x]");
        }
        // paper-lantern panels set into the ceiling between the beams
        for (int z = h[1] + 8; z < L.wingZ - 1; z += 8)
            for (int x = h[0] + 4; x < h[2] - 2; x += 6) put(x, ceilY, z, "minecraft:ochre_froglight[axis=y]");
    }

    void hallFloor(int rings) {
        int r = L.ringSize / 2, up = floorY;
        for (int k = 0; k < L.rings.size(); k++) {
            int cx = L.rings.get(k)[0], cz = L.rings.get(k)[1];
            if (k < rings) ring(cx, cz);
            else fill(cx - r, up - 1, cz - r, cx + r, up - 1, cz + r, b("Y"));
            // a lantern on a chain over each ring space
            for (int dx : new int[]{-5, 5}) {
                fill(cx + dx, up + 6, cz, cx + dx, ceilY - 1, cz, "minecraft:chain[axis=y,waterlogged=false]");
                put(cx + dx, up + 5, cz, b("L").replace("hanging=false", "hanging=true"));
            }
        }
        // two rows of columns between the rings, a lantern on each at head height
        int[] cols = {(L.rings.get(0)[0] + L.rings.get(1)[0]) / 2, (L.rings.get(1)[0] + L.rings.get(2)[0]) / 2};
        for (int x : cols)
            for (int z = L.balconyZ[1] + 2; z < L.wingZ; z += 7) {
                fill(x, up, z, x, ceilY - 1, z, b("P"));
                for (String f : new String[]{"east", "west"}) {
                    int ox = f.equals("east") ? 1 : -1;
                    put(x + ox, up + 2, z, "minecraft:" + (b("L").contains("soul") ? "soul_" : "") + "wall_torch[facing=" + f + "]");
                }
            }
        // andon floor lanterns down the aisle between the two rows of rings
        int aisle = (L.rings.get(0)[1] + L.rings.get(3)[1] + 1) / 2;
        for (int x = L.hall[0] + 4; x < L.hall[2] - 2; x += 6) {
            put(x, up, aisle, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]");
            put(x, up + 1, aisle, b("L"));
        }
        // style banners on the side walls of the hall floor
        for (int z = L.balconyZ[1] + 4; z < L.wingZ - 2; z += 8) {
            put(L.hall[0] + 1, up + 4, z, banner("east"));
            put(L.hall[2] - 1, up + 4, z, banner("west"));
        }
    }

    void ring(int cx, int cz) {
        int r = L.ringSize / 2, y = floorY - 1;
        fill(cx - r, y, cz - r, cx + r, y, cz + r, tier < 3 ? b("F") : b("Y"));
        for (int i = -r; i <= r; i++) {
            put(cx + i, y, cz - r, b("B")); put(cx + i, y, cz + r, b("B"));
            put(cx - r, y, cz + i, b("B")); put(cx + r, y, cz + i, b("B"));
        }
        put(cx, y, cz, b("I"));
        if (tier >= 2) {
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++)
                    if (Math.abs(Math.hypot(x - cx, z - cz) - 4.5) < 0.5) put(x, y, z, b("I"));
            for (int dz = -1; dz <= 1; dz++) { put(cx - 4, y, cz + dz, b("A")); put(cx + 4, y, cz + dz, b("A")); }
        }
        if (tier >= 3)
            for (int k = 2; k < r; k++)
                for (int[] sg : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) put(cx + sg[0] * k, y, cz + sg[1] * k, b("I"));
        // lights set flush into the floor: the centre and four points round it
        put(cx, y, cz, b("K"));
        for (int[] sg : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) put(cx + sg[0] * 4, y, cz + sg[1] * 4, b("K"));
        // corner posts with a light
        for (int[] c : new int[][]{{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
            put(cx + c[0], floorY, cz + c[1], b("P"));
            put(cx + c[0], floorY + 1, cz + c[1], b("L"));
        }
    }

    /** The south wing: a shoji partition with a sliding-door gap into each room, the corridor from the main doors. */
    void wing(int level) {
        int[] h = L.hall;
        int top = floorY + L.balconyUp - 2;              // rooms are one storey; their ceiling is the balcony level
        for (int x = h[0] + 1; x < h[2]; x++)
            for (int y = floorY; y <= top; y++) {
                boolean corridor = x >= L.corridor[0] && x <= L.corridor[1];
                boolean door = false;
                for (int[] rm : L.rooms.values()) door |= Math.abs(x - (rm[0] + rm[1]) / 2) <= 1 && y < floorY + 3;
                if (corridor && y < floorY + 4) continue;
                if (door) continue;
                put(x, y, L.wingZ, shoji(x, y));
            }
        fill(h[0] + 1, top + 1, L.wingZ, h[2] - 1, top + 1, h[3] - 1, b("C"));
        for (int[] rm : L.rooms.values())
            for (int xx : new int[]{rm[0] - 1, rm[1] + 1})
                if (xx > h[0] && xx < h[2] && (xx < L.corridor[0] || xx > L.corridor[1]))
                    for (int z = L.wingZ + 1; z < h[3]; z++) for (int y = floorY; y <= top; y++) put(xx, y, z, shoji(z, y));
        // corridor: walls each side, lanterns
        for (int z = L.wingZ + 2; z < h[3]; z += 4) {
            put(L.corridor[0], floorY + 3, z, "minecraft:" + (b("L").contains("soul") ? "soul_" : "") + "wall_torch[facing=east]");
            put(L.corridor[1], floorY + 3, z, "minecraft:" + (b("L").contains("soul") ? "soul_" : "") + "wall_torch[facing=west]");
        }
        for (Map.Entry<String, int[]> e : L.rooms.entrySet()) room(e.getKey(), e.getValue()[0], e.getValue()[1], level);
    }

    String shoji(int along, int y) {
        int k = y - floorY;
        if (along % 4 == 0) return b("P");
        return k == 0 || k == 3 ? b("J") : b("W");
    }

    void room(String name, int a, int b2, int level) {
        int z0 = L.wingZ + 1, z1 = L.hall[3] - 1, y = floorY;
        if (level <= 0) return;
        // a hanging lantern every 6 blocks under the room ceiling
        for (int x = a + 3; x <= b2 - 2; x += 6)
            for (int z = z0 + 3; z <= z1 - 2; z += 6) put(x, floorY + L.balconyUp - 2, z, b("L").replace("hanging=false", "hanging=true"));
        switch (name) {
            case "training" -> {
                fill(a, y - 1, z0, b2, y - 1, z1, b("Y"));
                int rows = level >= 2 ? 3 : 2;
                for (int row = 0; row < rows; row++)
                    for (int x = a + 2; x <= b2 - 2; x += 4) dummy(x, y, z0 + 4 + row * 4);
                for (int x = a + 1; x <= b2 - 1; x += 3) put(x, y, z1, b("E"));
                if (level >= 3) for (int x = a + 2; x <= b2 - 2; x += 4) put(x, y + 3, z1, banner("north"));
            }
            case "meditation" -> {
                fill(a, y - 1, z0, b2, y - 1, z1, b("Y"));
                for (int x = a + 2; x <= b2 - 2; x += 3) for (int z = z0 + 3; z <= z1 - 3; z += 4) put(x, y, z, b("M"));
                int cx = (a + b2) / 2;
                put(cx, y, z1 - 1, "minecraft:potted_azalea_bush");
                if (level >= 2) { put(cx - 2, y, z1 - 1, b("L")); put(cx + 2, y, z1 - 1, b("L")); }
                if (level >= 3) put(cx, y + 2, z1, banner("north"));
            }
            case "trophy" -> {
                fill(a, y - 1, z0, b2, y - 1, z1, b("F"));
                for (int x = a + 2; x <= b2 - 2; x += 4)
                    for (int z = z0 + 3; z <= z1 - 3; z += 5) {
                        put(x, y, z, "minecraft:polished_andesite");
                        put(x, y + 1, z, level >= 3 ? "minecraft:gold_block" : level >= 2 ? b("I") : "minecraft:smooth_stone_slab[type=bottom]");
                    }
                for (int x = a + 1; x <= b2 - 1; x += 3) put(x, y + 3, z1, banner("north"));
            }
            case "quarters" -> {
                fill(a, y - 1, z0, b2, y - 1, z1, b("Y"));
                int cx = (a + b2) / 2;
                put(cx, y, z1 - 1, "minecraft:" + accent.get("colour") + "_bed[facing=south,occupied=false,part=head]");
                put(cx, y, z1 - 2, "minecraft:" + accent.get("colour") + "_bed[facing=south,occupied=false,part=foot]");
                fill(a + 1, y, z1, a + 4, y + (level >= 2 ? 2 : 1), z1, "minecraft:bookshelf");
                put(b2 - 2, y, z1 - 1, "minecraft:dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
                put(b2 - 2, y, z1 - 2, "minecraft:dark_oak_slab[type=top,waterlogged=false]");
                put(b2 - 2, y + 1, z1 - 2, b("L"));
                if (level >= 3) put(cx, y + 3, z1, banner("north"));
            }
            default -> { }
        }
    }

    /** One training piece in the style: focus stones (Ki), straw dummies with arms (Melee), anvils (Strike), bamboo
     *  poles (Evasion). */
    void dummy(int x, int y, int z) {
        String d = b("D");
        switch (style) {
            case "ki" -> { put(x, y, z, d); put(x, y + 1, z, "minecraft:end_rod[facing=up]"); }
            case "melee" -> {
                put(x, y, z, b("P"));
                put(x, y + 1, z, d);
                put(x - 1, y + 1, z, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]");
                put(x + 1, y + 1, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]");
                put(x, y + 2, z, d);
            }
            case "strike" -> { put(x, y, z, tier >= 2 ? b("E") : "minecraft:smooth_stone"); put(x, y + 1, z, d); }
            default -> { for (int k = 0; k < 2 + tier; k++) put(x, y + k, z, d); }
        }
    }

    /** The spectator balcony along the north wall: a floor, a rail, stairs down at the west end. */
    void balcony(int level) {
        int[] h = L.hall;
        int by = floorY + L.balconyUp - 1;                 // the balcony's floor block
        fill(h[0] + 1, by, L.balconyZ[0], h[2] - 1, by, L.balconyZ[1], b("Q"));
        for (int x = h[0] + 4; x < h[2]; x++) put(x, by + 1, L.balconyZ[1], b("H"));
        for (int x = h[0] + 4; x < h[2]; x += 8) fill(x, floorY, L.balconyZ[1], x, by - 1, L.balconyZ[1], b("P"));
        // stairs at the west end, climbing north onto the balcony
        int steps = by - floorY + 1;
        for (int i = 0; i < steps; i++) {
            int z = L.balconyZ[1] + steps - i, y = floorY + i;
            fill(h[0] + 1, y, z, h[0] + 3, y, z, b("S") + "[facing=north,half=bottom,shape=straight,waterlogged=false]");
            if (i > 0) fill(h[0] + 1, floorY, z, h[0] + 3, y - 1, z, b("J"));
        }
        if (level >= 2) for (int x = h[0] + 8; x < h[2]; x += 8) put(x, by + 2, L.balconyZ[1], b("L"));
        if (level >= 3) for (int x = h[0] + 6; x < h[2]; x += 8) put(x, by + 3, L.balconyZ[0], banner("south"));
        // benches
        for (int x = h[0] + 6; x < h[2] - 1; x += 2) put(x, by + 1, L.balconyZ[0] + 1, b("S") + "[facing=north,half=bottom,shape=straight,waterlogged=false]");
    }

    /** The main sliding doors: through the hall's south wall into the entrance porch. */
    void doors() {
        int z = L.hall[3], x0 = L.mainDoor[0], x1 = L.mainDoor[1];
        fill(x0, floorY, z, x1, floorY + 3, z, air());
        // the porch: open its north wall and close the slot between the two buildings
        for (int zz = z + 1; zz < L.porch[0] + 1; zz++) {
            fill(x0 - 1, floorY - 1, zz, x1 + 1, floorY - 1, zz, b("F"));
            fill(x0 - 1, floorY, zz, x0 - 1, floorY + 4, zz, b("W"));
            fill(x1 + 1, floorY, zz, x1 + 1, floorY + 4, zz, b("W"));
            fill(x0, floorY + 4, zz, x1, floorY + 4, zz, b("J"));
        }
        fill(x0, floorY, L.porch[0], x1, floorY + 3, L.porch[0], air());
        // the porch floor and lights; noren over its south door
        for (int x = x0 - 4; x <= x1 + 4; x++) for (int zz = L.porch[0] + 1; zz < L.porch[1]; zz++)
            if (s.isAirOrVoid(fx + x, floorY, fz + zz) && s.isBlock(fx + x, floorY - 1, fz + zz)) put(x, floorY - 1, zz, b("F"));
        for (int zz = L.porch[0]; zz <= L.porch[1] + 1; zz++)
            for (int x = x0 - 6; x <= x1 + 6; x++)
                if (s.get(fx + x, floorY, fz + zz).contains("_door")) {
                    for (int dx = -2; dx <= 2; dx++) put(x + dx, floorY + 2, zz + 1, dx % 2 == 0 ? banner("south") : air());
                    return;
                }
    }

    /** Outside identity: the style's banners between the posts under the main eaves, on all four sides. */
    void outside() {
        int[] h = L.hall;
        int y = floorY + L.balconyUp + 3;
        for (int x = h[0] + 2; x < h[2]; x += 6) {
            if (s.isAirOrVoid(fx + x, y, fz + h[1] - 1)) put(x, y, h[1] - 1, banner("north"));
            if (x < L.porch[0] - 100 || Math.abs(x - (L.mainDoor[0] + L.mainDoor[1]) / 2) > 12)
                if (s.isAirOrVoid(fx + x, y, fz + h[3] + 1)) put(x, y, h[3] + 1, banner("south"));
        }
        for (int z = h[1] + 2; z < h[3]; z += 6) {
            if (s.isAirOrVoid(fx + h[0] - 1, y, fz + z)) put(h[0] - 1, y, z, banner("west"));
            if (s.isAirOrVoid(fx + h[2] + 1, y, fz + z)) put(h[2] + 1, y, z, banner("east"));
        }
    }

    /** The grounds: a path to the porch with stone lanterns, trees from SceneKit (refined up), a torii (masterwork). */
    void grounds() {
        int px = (L.mainDoor[0] + L.mainDoor[1]) / 2;
        int zEnd = s.sz - fz - 1, zFrom = L.porch[1] + 1;
        String path = b("O");
        for (int z = zFrom; z <= zEnd; z++) {
            fill(px - 2, 0, z, px + 2, 0, z, path);
            for (int side : new int[]{-3, 3}) if (BuildStyle.hash(px + side, 0, z, 68) < 0.45) put(px + side, 0, z, path);
        }
        for (int z = zFrom + 2; z <= zEnd - 1; z += 5)
            for (int x : new int[]{px - 4, px + 4}) {
                put(x, 1, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]");
                put(x, 2, z, "minecraft:lantern[hanging=false,waterlogged=false]");
                put(x, 3, z, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]");
            }
        // trees in the four corners of the plot (refined: two kinds; masterwork: cherry)
        if (tier >= 2) {
            String[] kinds = tier >= 3 ? new String[]{"cherry", "cherry", "cherry", "cherry"} : new String[]{"cherry", "oak", "oak", "cherry"};
            int[][] at = {{-fx + 7, -fz + 7}, {s.sx - fx - 8, -fz + 7}, {-fx + 7, s.sz - fz - 9}, {s.sx - fx - 8, s.sz - fz - 9}};
            for (int i = 0; i < 4; i++) {
                Structure t = SceneKit.tree(SceneKit.SPECIES.get(kinds[i]), 1.35, 680 + i + tier).toStructure();
                int[] bb = t.bounds();
                int tcx = (bb[0] + bb[3]) / 2, tcz = (bb[2] + bb[5]) / 2;
                s.paste(t, fx + at[i][0] - tcx, 1 - bb[1], fz + at[i][1] - tcz, false);
            }
        }
        if (tier >= 3) {
            int z = zEnd - 4;
            for (int x : new int[]{px - 4, px + 4}) fill(x, 1, z, x, 7, z, b("G"));
            fill(px - 6, 8, z, px + 6, 8, z, "minecraft:polished_blackstone");
            fill(px - 4, 6, z, px + 4, 6, z, b("G"));
            put(px, 7, z, b("G"));
        }
    }

    // ------------------------------------------------------------------ pictures
    static BlockLib LIB;

    BufferedImage plan(String title) { return plan(title, 1100, 1000, -1); }

    /** The floor plan, roof off. {@code ring} >= 0 tints that ring space gold (the menu's "what this builds"); a
     *  picture under 400 px wide gets no labels (the menu draws its own). */
    BufferedImage plan(String title, int w, int h, int ring) {
        Structure c = s.copy();
        c.fill(0, floorY + 2, 0, c.sx - 1, c.sy - 1, c.sz - 1, Structure.AIR);
        // keep the balcony floor (one layer above the cut) by drawing it into the cut
        int by = floorY + L.balconyUp - 1;
        for (int z = L.balconyZ[0]; z <= L.balconyZ[1]; z++)
            for (int x = L.hall[0] + 1; x < L.hall[2]; x++) if (s.isBlock(fx + x, by, fz + z)) c.set(fx + x, floorY + 1, fz + z, s.get(fx + x, by, fz + z));
        // crop to the foundation and the porch
        Structure crop = new Structure(L.footW + 2, c.sy, L.porch[1] + 4);
        for (int y = 0; y < c.sy; y++) for (int z = 0; z < crop.sz; z++) for (int x = 0; x < crop.sx; x++)
            crop.set(x, y, z, c.get(fx - 1 + x, y, fz - 1 + z));
        BlockRender.Options o = new BlockRender.Options();
        o.width = w; o.height = h; o.ao = true;
        if (ring >= 0) {
            o.highlight = new java.util.HashMap<>();
            int rr = L.ringSize / 2, rcx = L.rings.get(ring)[0] + 1, rcz = L.rings.get(ring)[1] + 1;
            for (int x = rcx - rr; x <= rcx + rr; x++) for (int z = rcz - rr; z <= rcz + rr; z++)
                for (int y = floorY - 1; y <= floorY + 1; y++) o.highlight.put(Structure.pack(x, y, z), 0xFFC83C);
        }
        BlockRender r = new BlockRender(crop, LIB, o);
        BlockRender.Camera cam = r.fitOrtho(180, 89.9);
        BufferedImage img = r.render(cam);
        if (w < 400) return img;
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.util.function.BiFunction<Double, Double, double[]> at = (x, z) -> r.project(new double[]{x + 1, floorY + 2, z + 1, 0, 0, 0, 0, 0});
        for (int k = 0; k < L.rings.size(); k++) label(g, at.apply((double) L.rings.get(k)[0] + .5, (double) L.rings.get(k)[1] + .5), "Ring " + (k + 1));
        label(g, at.apply((L.hall[0] + L.hall[2]) / 2.0, (L.balconyZ[0] + L.balconyZ[1]) / 2.0 + .5), "Spectator balcony");
        String[] names = {"Training area", "Meditation", "Trophy hall", "Master quarters"};
        int i = 0;
        for (int[] rm : L.rooms.values()) label(g, at.apply((rm[0] + rm[1]) / 2.0 + .5, (L.wingZ + L.hall[3]) / 2.0 + .5), names[i++]);
        label(g, at.apply((L.corridor[0] + L.corridor[1]) / 2.0 + .5, (L.porch[0] + L.porch[1]) / 2.0 + 1), "Main doors");
        title(g, img, title);
        g.dispose();
        return img;
    }

    static void label(Graphics2D g, double[] p, String text) {
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 14, h = fm.getHeight() + 4;
        int x = (int) p[0] - w / 2, y = (int) p[1] - h / 2;
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 7, y + fm.getAscent() + 2);
    }

    static void title(Graphics2D g, BufferedImage img, String text) {
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, 0, img.getWidth(), fm.getHeight() + 10);
        g.setColor(Color.WHITE);
        g.drawString(text, 10, fm.getAscent() + 5);
    }

    BufferedImage view(BlockRender r, BlockRender.Camera c, String title) {
        BufferedImage img = r.render(c);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        title(g, img, title);
        g.dispose();
        return img;
    }

    BlockRender.Camera eye(double x, double y, double z, double tx, double ty, double tz, double fov) {
        BlockRender.Camera c = BlockRender.Camera.look(fx + x, y, fz + z, fx + tx, ty, fz + tz);
        c.fov = fov;
        return c;
    }

    Map<String, BufferedImage> pictures(String name) {
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        String who = STYLE_NAMES.get(style) + ", " + TIER_NAMES[tier].toLowerCase(Locale.ROOT);
        out.put("plan", plan("Floor plan - " + who));
        out.put("menu_plan", plan("", 182, 132, 4));
        BlockRender.Options o = new BlockRender.Options();
        o.width = 1100; o.height = 640;
        BlockRender r = new BlockRender(s, LIB, o);
        double eyeB = floorY + L.balconyUp + 1.62;
        int midX = (L.hall[0] + L.hall[2]) / 2;
        out.put("arena", view(r, eye(midX + 3.5, eyeB + 1.3, L.balconyZ[1] + 0.4, midX + 3.5, floorY, (L.balconyZ[1] + L.wingZ) / 2.0 + 4, 84),
                "The hall floor from the spectator balcony - " + who));
        int[] tr = L.rooms.get("training");
        double tx = (tr[0] + tr[1]) / 2.0 + .5;
        out.put("training", view(r, eye(tx - 4, floorY + 2.2, L.wingZ + 1.3, tx + 1, floorY + 0.6, L.hall[3] - 2, 84),
                "The training area from its door - " + who));
        out.put("outside", view(r, r.fitOrtho(150, 28), "Outside - " + who));
        out.put("front", view(r, r.fitPerspective(180, 10, 0), "From the path - " + who));
        o.daylight = 0;
        BlockRender rn = new BlockRender(s, LIB, o);
        out.put("night", view(rn, rn.fitOrtho(150, 28), "At night - " + who));
        return out;
    }

    static BufferedImage sheet(List<BufferedImage> imgs, List<String> rowNames, int cols, String title, double scale) {
        int w = (int) (imgs.get(0).getWidth() * scale), h = (int) (imgs.get(0).getHeight() * scale);
        int rows = (imgs.size() + cols - 1) / cols, head = 40;
        BufferedImage s = new BufferedImage(cols * w + (cols + 1) * 6, head + rows * (h + 6) + 6, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = s.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(28, 30, 36));
        g.fillRect(0, 0, s.getWidth(), s.getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString(title, 10, 28);
        for (int i = 0; i < imgs.size(); i++) {
            int x = 6 + (i % cols) * (w + 6), y = head + (i / cols) * (h + 6);
            g.drawImage(imgs.get(i), x, y, w, h, null);
        }
        g.dispose();
        return s;
    }

    // ------------------------------------------------------------------ main
    public static void main(String[] a) throws Exception {
        if (a.length < 5 || !a[0].equals("make")) {
            System.out.println("usage: DojoKit make <hall_t1.nbt,hall_t2.nbt,hall_t3.nbt> <recipe.json> <outNbtDir> <outPictureDir> [-only style_tN] [-noviews]");
            System.exit(2);
        }
        String[] halls = a[1].split(",");
        Map<String, Object> recipe = ModelPreview.obj(new ModelPreview.J(Files.readString(new File(a[2]).toPath())).read());
        Layout L = Layout.of(recipe);
        File nbtDir = new File(a[3]), picDir = new File(a[4]);
        nbtDir.mkdirs(); picDir.mkdirs();
        String only = null;
        boolean views = true;
        for (int i = 5; i < a.length; i++) {
            if (a[i].equals("-only")) only = a[++i];
            if (a[i].equals("-noviews")) views = false;
        }
        BuildStyle.lib();
        LIB = new BlockLib(BlockLib.mods());
        Map<String, List<BufferedImage>> byKind = new LinkedHashMap<>();
        for (String style : STYLES)
            for (int tier = 1; tier <= 3; tier++) {
                String name = style + "_t" + tier;
                if (only != null && !only.equals(name)) continue;
                Structure hall = Structure.load(new File(halls[tier - 1]));
                DetailKit.weather(hall, 0.25, 68 + tier);
                DojoKit k = new DojoKit(hall, L, tier, style);
                k.build(2 * tier, tier, tier);
                File out = new File(nbtDir, "dojo_" + name + ".nbt");
                k.s.save(out, true);
                System.out.printf(Locale.ROOT, "%s: %dx%dx%d, floor y %d, ceiling y %d, foundation at %d %d -> %s%n", name,
                        k.s.sx, k.s.sy, k.s.sz, k.floorY, k.ceilY, k.fx, k.fz, out);
                if (!views) continue;
                for (Map.Entry<String, BufferedImage> e : k.pictures(name).entrySet()) {
                    ImageIO.write(e.getValue(), "png", new File(picDir, e.getKey() + "_" + name + ".png"));
                    byKind.computeIfAbsent(e.getKey(), x -> new ArrayList<>()).add(e.getValue());
                }
            }
        if (only == null && views) {
            Map<String, String> titles = Map.of("plan", "Floor plans (roof off) - rows: Ki Masters, Melee, Strike Moves, Evasion; columns: basic, refined, masterwork",
                    "arena", "The hall floor from the spectator balcony - rows: styles; columns: tiers",
                    "training", "The training area from its door - rows: styles; columns: tiers",
                    "outside", "Outside - rows: styles; columns: tiers",
                    "front", "From the path - rows: styles; columns: tiers",
                    "night", "At night - rows: styles; columns: tiers");
            for (Map.Entry<String, List<BufferedImage>> e : byKind.entrySet())
                if (titles.containsKey(e.getKey())) ImageIO.write(sheet(e.getValue(), null, 3, titles.get(e.getKey()), 0.5), "png", new File(picDir, "sheet_" + e.getKey() + ".png"));
        }
    }
}
