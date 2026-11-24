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
 * A dojo is built from its STATE, the same one LM keeps (DojoCatalog): the number of rings (a new dojo has one, the
 * free sparring mat), the level of each room (training hall, meditation, spectator balcony, trophy hall, master
 * quarters, garden; 0 = not bought: the space stays empty), and the style (none until the master picks one). The hall's
 * look follows the rings like LM's look tier (1-2 rings basic, 3-4 refined, 5-6 masterwork). Tiers set the materials
 * (BuildKit's style per recipe + the interior palettes below); styles set only the accents (banners, ring inlays,
 * cushions, room dressing).
 * <pre>
 *   DojoKit make &lt;hall_t1.nbt,hall_t2.nbt,hall_t3.nbt&gt; &lt;recipe.json&gt; &lt;outNbtDir&gt; &lt;outPictureDir&gt; [-only name] [-noviews]
 *      dojo_start.nbt (a new dojo) and dojo_&lt;style&gt;_t&lt;tier&gt;.nbt (everything at one tier), their pictures, and the
 *      upgrade previews below for the menus (-only start | style_tN | upgrades)
 *   DojoKit upgrade &lt;halls&gt; &lt;recipe.json&gt; &lt;state&gt; &lt;upgrade id&gt; &lt;out prefix&gt;
 *      what one upgrade adds: &lt;prefix&gt;_now.png and _after.png (a close-up of that part of the dojo before and after,
 *      roof off) and _map.png (the floor plan with the new blocks green). A state is "start" or
 *      "[style:]arenas=4,training_hall=1,..." ("all=1" sets every room).
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

    /** A new dojo has no style yet: plain accents, no banners. */
    static final List<Map<String, String>> NEUTRAL = List.of(Map.of(),
            acc("white", "light_gray_concrete", "smooth_stone", "lantern[hanging=false]", "white_carpet", "hay_block[axis=y]", "target", "oak_leaves"),
            acc("white", "light_gray_concrete", "polished_andesite", "lantern[hanging=false]", "white_carpet", "hay_block[axis=y]", "target", "oak_leaves"),
            acc("white", "gray_concrete", "polished_andesite", "lantern[hanging=false]", "white_carpet", "hay_block[axis=y]", "target", "cherry_leaves"));

    static Map<String, String> acc(String colour, String b, String i, String l, String m, String d, String e, String v) {
        String k = switch (colour) {                    // K: the light set flush into a ring floor, in the style's colour
            case "light_blue", "cyan" -> "sea_lantern";
            case "red", "white" -> "ochre_froglight[axis=y]";
            case "orange" -> "shroomlight";
            default -> "verdant_froglight[axis=y]";
        };
        return Map.of("A", colour + "_wool", "colour", colour, "B", b, "I", i, "L", l, "M", m, "D", d, "E", e,
                "V", v + "[distance=7,persistent=true,waterlogged=false]", "K", k);
    }

    // ------------------------------------------------------------------ state (what LM keeps per dojo)
    static final class State {
        static final List<String> PARTS = List.of("training_hall", "meditation", "spectator", "trophy_hall", "quarters", "garden");
        String style;                                   // null: not picked yet
        int arenas = 1;                                 // a new dojo has one ring, the free sparring mat
        final Map<String, Integer> lv = new LinkedHashMap<>();

        State() { for (String p : PARTS) lv.put(p, 0); }

        static State start() { return new State(); }

        static State showcase(String style, int tier) {
            State s = new State();
            s.style = style;
            s.arenas = 2 * tier;
            for (String p : PARTS) s.lv.put(p, tier);
            return s;
        }

        /** "start", or "[style:]arenas=4,training_hall=1,..." ("all=n" sets every room). */
        static State parse(String spec) {
            State s = new State();
            if (spec.equals("start")) return s;
            int c = spec.indexOf(':');
            if (c > 0) { s.style = spec.substring(0, c); spec = spec.substring(c + 1); }
            for (String kv : spec.split(",")) {
                if (kv.isBlank()) continue;
                String[] p = kv.split("=");
                int n = Integer.parseInt(p[1].trim());
                String k = p[0].trim();
                if (k.equals("arenas")) s.arenas = n;
                else if (k.equals("all")) for (String q : PARTS) s.lv.put(q, n);
                else if (s.lv.containsKey(k)) s.lv.put(k, n);
                else throw new IllegalArgumentException("unknown part " + k + " (arenas, all, " + PARTS + ")");
            }
            return s;
        }

        int level(String id) { return id.equals("arena") ? arenas : lv.getOrDefault(id, 0); }
        /** LM's look tier for the rings, which the whole hall follows. */
        int forceTier;                                  // export: a hall tier with no rings yet (0: from the rings)
        int hallTier() { return forceTier > 0 ? forceTier : 1 + (Math.min(Math.max(arenas, 1), 6) - 1) / 2; }

        State with(String id) {
            State s = new State();
            s.style = style; s.arenas = arenas; s.lv.putAll(lv); s.forceTier = forceTier;
            if (id.equals("arena")) s.arenas = Math.min(6, arenas + 1);
            else s.lv.put(id, Math.min(3, lv.get(id) + 1));
            return s;
        }
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
    final State st;
    final int tier;
    final String style;         // null: not picked yet
    int fx, fz, floorY, ceilY;  // the foundation's north-west corner in plot coordinates; the floor (standing level)

    /** {@code hall}: the BuildKit shell of the state's hall tier. */
    DojoKit(Structure hall, Layout L, State st) {
        this.L = L; this.st = st; this.tier = st.hallTier(); this.style = st.style;
        this.bone = BONES.get(tier);
        this.accent = style == null ? NEUTRAL.get(tier) : ACCENTS.get(style).get(tier);
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
        // The ground comes up to the hall's floor: the grounds, the path and the porch are one level, so nobody has to
        // climb the foundation to get in (dirt under, grass on top, round whatever the hall already fills).
        for (int z = 0; z < s.sz; z++)
            for (int x = 0; x < s.sx; x++)
                for (int y = 0; y < floorY; y++)
                    if (s.isAirOrVoid(x, y, z))
                        s.set(x, y, z, y == floorY - 1 ? "minecraft:grass_block[snowy=false]" : "minecraft:dirt");
    }

    String b(String k) {
        String v = accent.containsKey(k) ? accent.get(k) : bone.get(k);
        if (v == null) throw new IllegalArgumentException("no block for " + k);
        return v.contains(":") ? v : "minecraft:" + v;
    }
    void put(int x, int y, int z, String state) { if (state == null) return; s.set(fx + x, y, fz + z, state.contains(":") ? state : "minecraft:" + state); }
    void fill(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
        s.fill(fx + Math.min(x0, x1), Math.min(y0, y1), fz + Math.min(z0, z1), fx + Math.max(x0, x1), Math.max(y0, y1), fz + Math.max(z0, z1),
                state.contains(":") ? state : "minecraft:" + state);
    }
    String air() { return Structure.AIR; }
    /** The style's banner; null (nothing placed) until a style is picked. */
    String banner(String facing) {
        return style == null ? null : "minecraft:" + accent.get("colour") + "_wall_banner[facing=" + facing + "]";
    }

    // ------------------------------------------------------------------ the build
    void build() {
        interior();
        hallFloor(st.arenas);
        wing();
        if (st.level("spectator") > 0) balcony(st.level("spectator"));
        doors();
        outside();
        grounds(st.level("garden"));
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
            if (k >= rings) continue;                   // not bought: the floor stays empty
            ring(cx, cz);
            // a lantern on a chain over each ring
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

    /** The south wing. A room the dojo has bought is walled off with shoji (a sliding-door gap from the hall floor) and
     *  dressed; a room it hasn't is open, empty floor. */
    void wing() {
        int[] h = L.hall;
        int top = floorY + L.balconyUp - 2;              // rooms are one storey; their ceiling is the balcony level
        for (Map.Entry<String, int[]> e : L.rooms.entrySet()) {
            int level = st.level(e.getKey()), a = e.getValue()[0], b2 = e.getValue()[1];
            if (level <= 0) continue;
            int door = (a + b2) / 2;
            for (int x = a - 1; x <= b2 + 1; x++)
                for (int y = floorY; y <= top; y++)
                    if (!(Math.abs(x - door) <= 1 && y < floorY + 3)) put(x, y, L.wingZ, shoji(x, y));
            for (int xx : new int[]{a - 1, b2 + 1})
                if (xx > h[0] && xx < h[2])
                    for (int z = L.wingZ + 1; z < h[3]; z++) for (int y = floorY; y <= top; y++) put(xx, y, z, shoji(z, y));
            fill(a - 1, top + 1, L.wingZ, b2 + 1, top + 1, h[3] - 1, b("C"));
            room(e.getKey(), a, b2, level);
        }
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
            case "training_hall" -> {
                fill(a, y - 1, z0, b2, y - 1, z1, b("Y"));
                int rows = level >= 2 ? 3 : 2;
                for (int row = 0; row < rows; row++)
                    for (int x = a + 2; x <= b2 - 2; x += 4) dummy(x, y, z0 + 4 + row * 4, level);
                if (level >= 2) for (int x = a + 1; x <= b2 - 1; x += 3) put(x, y, z1, b("E"));
                if (level >= 3) {
                    for (int x = a + 2; x <= b2 - 2; x += 4) put(x, y + 3, z1, banner("north"));
                    fill(a + 1, y, z0 + 1, b2 - 1, y, z0 + 1, b("M"));
                }
            }
            case "meditation" -> {
                // basic: tatami and cushions; refined: a raked gravel garden with stones along the back, lanterns;
                // masterwork: a water basin, bamboo and the style's banner
                fill(a, y - 1, z0, b2, y - 1, z1, b("Y"));
                int back = level >= 2 ? z1 - 4 : z1 - 1;
                for (int x = a + 2; x <= b2 - 2; x += 3) for (int z = z0 + 3; z <= back - 2; z += 4) put(x, y, z, b("M"));
                int cx = (a + b2) / 2;
                if (level >= 2) {
                    fill(a, y - 1, z1 - 3, b2, y - 1, z1, "minecraft:gravel");
                    put(a + 2, y, z1 - 2, "minecraft:mossy_cobblestone");
                    put(cx + 1, y, z1 - 1, "minecraft:cobblestone");
                    put(b2 - 2, y, z1 - 2, "minecraft:mossy_cobblestone");
                    for (int x = a + 1; x <= b2 - 1; x += 4) put(x, y, z1 - 4, b("L"));
                } else put(cx, y, z1 - 1, "minecraft:potted_azalea_bush");
                if (level >= 3) {
                    put(cx - 2, y, z1 - 2, "minecraft:water_cauldron[level=3]");
                    for (int x = a; x <= b2; x += 3) { put(x, y, z1, "minecraft:bamboo[age=0,leaves=large,stage=0]"); put(x, y + 1, z1, "minecraft:bamboo[age=0,leaves=large,stage=0]"); }
                    put(cx, y + 3, z1, banner("north"));
                }
            }
            case "trophy_hall" -> {
                fill(a, y - 1, z0, b2, y - 1, z1, b("F"));
                for (int x = a + 2; x <= b2 - 2; x += 4)
                    for (int z = z0 + 3; z <= z1 - 3; z += 5) {
                        put(x, y, z, "minecraft:polished_andesite");
                        put(x, y + 1, z, level >= 3 ? "minecraft:gold_block" : level >= 2 ? b("I") : "minecraft:smooth_stone_slab[type=bottom]");
                    }
                if (level >= 2) for (int x = a + 1; x <= b2 - 1; x += 3) put(x, y + 3, z1, banner("north"));
                if (level >= 3) fill(a + 1, y, (z0 + z1) / 2, b2 - 1, y, (z0 + z1) / 2, b("M"));
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
                if (level >= 3) {
                    put(cx, y + 3, z1, banner("north"));
                    fill(cx - 2, y, z1 - 4, cx + 2, y, z1 - 3, b("M"));
                    put(a + 1, y, z0 + 1, "minecraft:potted_azalea_bush");
                    put(b2 - 1, y, z0 + 1, "minecraft:potted_azalea_bush");
                }
            }
            default -> { }
        }
    }

    /** One training piece in the style: focus stones (Ki), straw dummies with arms (Melee), anvils (Strike), bamboo
     *  poles (Evasion). */
    void dummy(int x, int y, int z, int level) {
        String d = b("D");
        switch (style == null ? "" : style) {
            case "ki" -> { put(x, y, z, d); put(x, y + 1, z, "minecraft:end_rod[facing=up]"); }
            case "melee" -> {
                put(x, y, z, b("P"));
                put(x, y + 1, z, d);
                put(x - 1, y + 1, z, "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]");
                put(x + 1, y + 1, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=true]");
                put(x, y + 2, z, d);
            }
            case "strike" -> { put(x, y, z, level >= 2 ? b("E") : "minecraft:smooth_stone"); put(x, y + 1, z, d); }
            default -> { for (int k = 0; k < 1 + level; k++) put(x, y + k, z, d); }
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

    /** The grounds: always a path to the porch. Garden 1 adds stone lanterns, 2 trees from SceneKit, 3 cherry trees and
     *  a torii. */
    void grounds(int garden) {
        int px = (L.mainDoor[0] + L.mainDoor[1]) / 2;
        int zEnd = s.sz - fz - 1, zFrom = L.porch[1] + 1;
        String path = b("O");
        int g = floorY - 1;                             // the ground's top layer: level with the hall floor
        for (int z = zFrom; z <= zEnd; z++) {
            fill(px - 2, g, z, px + 2, g, z, path);
            for (int side : new int[]{-3, 3}) if (BuildStyle.hash(px + side, 0, z, 68) < 0.45) put(px + side, g, z, path);
        }
        for (int z = zFrom + 2; z <= zEnd - 1 && garden >= 1; z += 5)
            for (int x : new int[]{px - 4, px + 4}) {
                put(x, g + 1, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]");
                put(x, g + 2, z, "minecraft:lantern[hanging=false,waterlogged=false]");
                put(x, g + 3, z, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]");
            }
        // trees in the four corners of the plot (refined: two kinds; masterwork: cherry)
        if (garden >= 2) {
            String[] kinds = garden >= 3 ? new String[]{"cherry", "cherry", "cherry", "cherry"} : new String[]{"cherry", "oak", "oak", "cherry"};
            int[][] at = {{-fx + 7, -fz + 7}, {s.sx - fx - 8, -fz + 7}, {-fx + 7, s.sz - fz - 9}, {s.sx - fx - 8, s.sz - fz - 9}};
            for (int i = 0; i < 4; i++) {
                Structure t = SceneKit.tree(SceneKit.SPECIES.get(kinds[i]), 1.35, 680 + i + garden).toStructure();
                int[] bb = t.bounds();
                int tcx = (bb[0] + bb[3]) / 2, tcz = (bb[2] + bb[5]) / 2;
                s.paste(t, fx + at[i][0] - tcx, g + 1 - bb[1], fz + at[i][1] - tcz, false);
            }
        }
        if (garden >= 3) {
            int z = zEnd - 4;
            for (int x : new int[]{px - 4, px + 4}) fill(x, g + 1, z, x, g + 7, z, b("G"));
            fill(px - 6, g + 8, z, px + 6, g + 8, z, "minecraft:polished_blackstone");
            fill(px - 4, g + 6, z, px + 4, g + 6, z, b("G"));
            put(px, g + 7, z, b("G"));
        }
    }

    // ------------------------------------------------------------------ pictures
    static BlockLib LIB;

    BufferedImage plan(String title) { return plan(title, 1100, 1000, null); }

    /** The floor plan, roof off. {@code green}: columns (plot x, z packed) tinted green, the Buy button's colour (the
     *  menu's "what this adds"); a picture with no title gets no labels (the menu says what it shows). */
    BufferedImage plan(String title, int w, int h, java.util.Set<Long> green) {
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
        if (green != null) {
            o.highlight = new java.util.HashMap<>();
            for (long col : green) {
                int x = (int) (col >> 32) - (fx - 1), z = (int) col - (fz - 1);
                for (int y = 0; y <= floorY + 1; y++) o.highlight.put(Structure.pack(x, y, z), 0x3CD25A);
            }
        }
        BlockRender r = new BlockRender(crop, LIB, o);
        BlockRender.Camera cam = r.fitOrtho(180, 89.9);
        if (title.isEmpty()) return noSky(r, cam);
        BufferedImage img = r.render(cam);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.util.function.BiFunction<Double, Double, double[]> at = (x, z) -> r.project(new double[]{x + 1, floorY + 2, z + 1, 0, 0, 0, 0, 0});
        for (int k = 0; k < L.rings.size(); k++)
            label(g, at.apply((double) L.rings.get(k)[0] + .5, (double) L.rings.get(k)[1] + .5), "Ring " + (k + 1), k < st.arenas);
        label(g, at.apply((L.hall[0] + L.hall[2]) / 2.0, (L.balconyZ[0] + L.balconyZ[1]) / 2.0 + .5), "Spectator balcony",
                st.level("spectator") > 0);
        String[] names = {"Training hall", "Meditation", "Trophy hall", "Master quarters"};
        int i = 0;
        for (Map.Entry<String, int[]> e : L.rooms.entrySet()) {
            int[] rm = e.getValue();
            label(g, at.apply((rm[0] + rm[1]) / 2.0 + .5, (L.wingZ + L.hall[3]) / 2.0 + .5), names[i++], st.level(e.getKey()) > 0);
        }
        label(g, at.apply((L.corridor[0] + L.corridor[1]) / 2.0 + .5, (L.porch[0] + L.porch[1]) / 2.0 + 1), "Main doors", true);
        title(g, img, title);
        g.dispose();
        return img;
    }

    /** A plan label; a part the dojo hasn't bought reads "... (empty)" in grey. */
    static void label(Graphics2D g, double[] p, String text, boolean built) {
        if (!built) text = text + " (empty)";
        g.setFont(new Font("SansSerif", built ? Font.BOLD : Font.PLAIN, 17));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 14, h = fm.getHeight() + 4;
        int x = (int) p[0] - w / 2, y = (int) p[1] - h / 2;
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(built ? Color.WHITE : new Color(190, 190, 190));
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
        String who = style == null ? "a new dojo (one sparring mat, no style yet)"
                : STYLE_NAMES.get(style) + ", " + TIER_NAMES[tier].toLowerCase(Locale.ROOT);
        out.put("plan", plan("Floor plan - " + who));
        if (style == null) out.put("menu_plan", plan("", 600, 545, null));   // the new dojo on My dojo and Upgrades
        BlockRender.Options o = new BlockRender.Options();
        o.width = 1100; o.height = 640;
        BlockRender r = new BlockRender(s, LIB, o);
        double eyeB = floorY + L.balconyUp + 1.62;
        int midX = (L.hall[0] + L.hall[2]) / 2;
        out.put("arena", view(r, eye(midX + 3.5, eyeB + 1.3, L.balconyZ[1] + 0.4, midX + 3.5, floorY, (L.balconyZ[1] + L.wingZ) / 2.0 + 4, 84),
                "The hall floor from the spectator balcony - " + who));
        int[] tr = L.rooms.get("training_hall");
        double tx = (tr[0] + tr[1]) / 2.0 + .5;
        out.put("training", view(r, eye(tx - 4, floorY + 2.2, L.wingZ + 1.3, tx + 1, floorY + 0.6, L.hall[3] - 2, 84),
                "The training hall from its door - " + who));
        out.put("outside", view(r, r.fitOrtho(150, 28), "Outside - " + who));
        out.put("front", view(r, r.fitPerspective(180, 10, 0), "From the path - " + who));
        o.daylight = 0;
        BlockRender rn = new BlockRender(s, LIB, o);
        out.put("night", view(rn, rn.fitOrtho(150, 28), "At night - " + who));
        return out;
    }

    // ------------------------------------------------------------------ upgrade previews (the menus)
    /** The part of the dojo an upgrade builds, in foundation coordinates {x0, z0, x1, z1, cut, yaw}: cut is how many
     *  layers above the floor stay in the close-up (the roof and room ceilings come off), yaw where the camera looks
     *  from. Null: the grounds (the garden). */
    int[] partBox(String id, State before) {
        int[] h = L.hall;
        switch (id) {
            case "arena" -> {
                int[] c = L.rings.get(Math.min(before.arenas, L.rings.size() - 1));
                int r = L.ringSize / 2 + 2;
                return new int[]{c[0] - r, c[1] - r, c[0] + r, c[1] + r, 4, 150};
            }
            case "spectator" -> { return new int[]{h[0] + 1, h[1] + 1, h[2] - 1, L.balconyZ[1] + L.balconyUp + 2, L.balconyUp + 2, 150}; }
            case "garden" -> { return null; }
            default -> {
                int[] rm = L.rooms.get(id);
                if (rm == null) throw new IllegalArgumentException("no picture for " + id);
                return new int[]{rm[0] - 1, L.wingZ, rm[1] + 1, h[3], 3, 330};   // from the hall side, over the low partition
            }
        }
    }

    /** That part of the dojo, roof off, from above and to the south-east. */
    BufferedImage closeUp(int[] box, String caption, java.util.Set<Long> green) {
        BlockRender.Options o = new BlockRender.Options();
        o.width = 520; o.height = 400;
        BlockRender r;
        if (box == null) {
            if (green != null) {
                o.highlight = new java.util.HashMap<>();
                for (long c : green) {
                    int x = (int) (c >> 32), z = (int) c;
                    for (int y = 0; y < s.sy; y++) if (s.isBlock(x, y, z)) o.highlight.put(Structure.pack(x, y, z), 0x3CD25A);
                }
            }
            r = new BlockRender(s, LIB, o);
            return caption(noSky(r, r.fitOrtho(150, 30)), caption);
        }
        int m = 3, x0 = box[0] - m, z0 = box[1] - m, x1 = box[2] + m, z1 = box[3] + m, top = floorY + box[4];
        Structure c = new Structure(x1 - x0 + 1, top + 1, z1 - z0 + 1);
        for (int y = 0; y <= top; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++)
            c.set(x - x0, y, z - z0, s.get(fx + x, y, fz + z));
        r = new BlockRender(c, LIB, o);
        return caption(noSky(r, r.fitOrtho(box[5], 45)), caption);
    }

    /** Columns (plot x, z packed) inside {@code box} where two builds of the same plot differ. */
    static java.util.Set<Long> changed(DojoKit a, DojoKit b, int[] box) {
        java.util.Set<Long> out = new java.util.HashSet<>();
        int x0 = 0, z0 = 0, x1 = b.s.sx - 1, z1 = b.s.sz - 1;
        if (box != null) { x0 = b.fx + box[0]; z0 = b.fz + box[1]; x1 = b.fx + box[2]; z1 = b.fz + box[3]; }
        for (int z = z0; z <= z1; z++)
            for (int x = x0; x <= x1; x++) {
                if (box == null && x >= b.fx && x < b.fx + b.L.footW && z >= b.fz && z <= b.fz + b.L.porch[1]) continue;
                for (int y = 0; y < b.s.sy; y++)
                    if (!a.s.get(x, y, z).equals(b.s.get(x, y, z))) { out.add(((long) x << 32) | (z & 0xFFFFFFFFL)); break; }
            }
        return out;
    }

    /** Now, after and where: the three pictures an upgrade page shows. */
    static Map<String, BufferedImage> upgradePictures(Structure[] halls, Layout L, State before, String id) {
        State after = before.with(id);
        DojoKit a = new DojoKit(halls[before.hallTier() - 1], L, before);
        a.build();
        DojoKit b = new DojoKit(halls[after.hallTier() - 1], L, after);
        b.build();
        int[] box = b.partBox(id, before);
        java.util.Set<Long> green = changed(a, b, box);
        if (box != null)                                // the whole part the upgrade builds or raises, not only what changed
            for (int z = box[1]; z <= box[3]; z++) for (int x = box[0]; x <= box[2]; x++)
                green.add(((long) (b.fx + x) << 32) | ((b.fz + z) & 0xFFFFFFFFL));
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        out.put("now", a.closeUp(box, "Now", null));
        out.put("after", b.closeUp(box, "After", null));
        out.put("map", box == null ? b.closeUp(null, "Where", green) : caption(b.plan("", 520, 473, green), "Where"));
        return out;
    }

    /** The render with the sky left out (transparent), so the menu's own box shows round the building. */
    static BufferedImage noSky(BlockRender r, BlockRender.Camera cam) {
        BufferedImage img = r.render(cam);
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if (r.zb[y * img.getWidth() + x] != Float.MAX_VALUE) out.setRGB(x, y, img.getRGB(x, y) | 0xFF000000);
        return out;
    }

    // Captions in Minecraft's own font (the toolkit's McFont, read from the installed game), like the menu around them.
    static McFont FONT;
    static boolean fontTried;

    static BufferedImage caption(BufferedImage img, String text) {
        if (text == null) return img;
        if (!fontTried) {
            fontTried = true;
            try {
                FONT = McFont.load(new File(System.getProperty("mc.install", "C:/Users/antho/curseforge/minecraft/Install")));
            } catch (Exception e) {
                System.out.println("no Minecraft font (" + e.getMessage() + "): captions in a system font");
            }
        }
        Graphics2D g = img.createGraphics();
        int k = 3;
        int w = FONT != null ? FONT.width(text) : text.length() * 6;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, (w + 8) * k, (McFont.LINE + 5) * k);
        if (FONT != null) {
            FONT.draw((x0, y0, x1, y1, argb) -> {
                g.setColor(new Color(argb, true));
                g.fillRect((int) Math.round(x0 * k), (int) Math.round(y0 * k), (int) Math.round((x1 - x0) * k), (int) Math.round((y1 - y0) * k));
            }, text, 4, 3, 0xFFFFFFFF, true);
        } else {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 9 * k));
            g.drawString(text, 4 * k, 11 * k);
        }
        g.dispose();
        return img;
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

    // ------------------------------------------------------------------ export (what LM ships)
    static final String[] EXPORT_STYLES = {"none", "ki", "melee", "strike", "evasion"};
    static final String[] ROOM_PARTS = {"training_hall", "meditation", "trophy_hall", "quarters"};
    static final int PLAN_PX = 4;                       // plan textures: pixels per block

    static State bare(String style, int tier) {
        State s = new State();
        s.style = style.equals("none") ? null : style;
        s.arenas = 0;
        s.forceTier = tier;
        return s;
    }

    static DojoKit built(Structure[] halls, Layout L, State st) {
        DojoKit k = new DojoKit(halls[st.hallTier() - 1], L, st);
        k.build();
        return k;
    }

    /** The cells where {@code b} differs from {@code a}, cut to their bounds (structure void elsewhere); {at} gets the
     *  piece's position in the plot. Null when nothing differs. */
    static Structure piece(Structure a, Structure b, int[] at) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = -1, y1 = -1, z1 = -1;
        for (int y = 0; y < b.sy; y++) for (int z = 0; z < b.sz; z++) for (int x = 0; x < b.sx; x++)
            if (!a.get(x, y, z).equals(b.get(x, y, z))) {
                x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z);
                x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z);
            }
        if (x1 < 0) return null;
        Structure p = new Structure(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1);
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            String v = b.get(x, y, z);
            if (!a.get(x, y, z).equals(v)) p.set(x - x0, y - y0, z - z0, v.equals(Structure.VOID) ? Structure.AIR : v);
        }
        at[0] = x0; at[1] = y0; at[2] = z0;
        return p;
    }

    /** The floor plan as a texture: exactly {@link #PLAN_PX} pixels per block over the foundation and the porch, so
     *  LM can cut any part's rectangle out of it (top-left = plot x fx-1, z fz-1). */
    BufferedImage planTexture() {
        Structure c = s.copy();
        c.fill(0, floorY + 2, 0, c.sx - 1, c.sy - 1, c.sz - 1, Structure.AIR);
        int by = floorY + L.balconyUp - 1;
        for (int z = L.balconyZ[0]; z <= L.balconyZ[1]; z++)
            for (int x = L.hall[0] + 1; x < L.hall[2]; x++) if (s.isBlock(fx + x, by, fz + z)) c.set(fx + x, floorY + 1, fz + z, s.get(fx + x, by, fz + z));
        int cw = L.footW + 2, cd = L.porch[1] + 4;
        Structure crop = new Structure(cw, floorY + 3, cd);
        for (int y = 0; y < crop.sy; y++) for (int z = 0; z < cd; z++) for (int x = 0; x < cw; x++)
            crop.set(x, y, z, c.get(fx - 1 + x, y, fz - 1 + z));
        BlockRender.Options o = new BlockRender.Options();
        o.width = cw * PLAN_PX; o.height = cd * PLAN_PX;
        BlockRender r = new BlockRender(crop, LIB, o);
        BlockRender.Camera cam = BlockRender.Camera.ortho(180, 90, cw / 2.0, floorY + 1, cd / 2.0, PLAN_PX);
        return noSky(r, cam);
    }

    static void exportAll(Structure[] halls, Layout L, File structDir, File texDir, File indexFile) throws Exception {
        structDir.mkdirs(); texDir.mkdirs();
        StringBuilder pieces = new StringBuilder();
        DojoKit any = null;
        for (int t = 1; t <= 3; t++)
            for (String style : EXPORT_STYLES) {
                String key = "t" + t + "_" + style;
                File dir = new File(structDir, key);
                dir.mkdirs();
                DojoKit base = built(halls, L, bare(style, t));
                any = base;
                base.s.save(new File(dir, "base.nbt"), true);
                int n = 0;
                State prev = bare(style, t);
                DojoKit prevK = base;
                for (int k = 1; k <= L.rings.size(); k++) {
                    State st = bare(style, t);
                    st.arenas = k;
                    DojoKit now = built(halls, L, st);
                    n += savePiece(prevK.s, now.s, dir, "arena_" + k, key, pieces);
                    prevK = now;
                }
                for (String part : State.PARTS)
                    for (int lv = 1; lv <= 3; lv++) {
                        State st = bare(style, t);
                        st.lv.put(part, lv);
                        n += savePiece(base.s, built(halls, L, st).s, dir, part + "_" + lv, key, pieces);
                    }
                // the plans the menus cut parts out of: the empty hall, and every part built at this tier
                ImageIO.write(base.planTexture(), "png", new File(texDir, "plan_" + key + "_empty.png"));
                State full = bare(style, t);
                full.arenas = L.rings.size();
                for (String part : State.PARTS) full.lv.put(part, t);
                ImageIO.write(built(halls, L, full).planTexture(), "png", new File(texDir, "plan_" + key + "_full.png"));
                System.out.println("export " + key + ": base + " + n + " pieces, plans");
            }
        // close-ups: each part at each level (the hall at that tier), and each part's empty space per tier
        int pics = 0;
        for (String style : EXPORT_STYLES)
            for (int lv = 1; lv <= 3; lv++) {
                State st = bare(style, lv);
                st.arenas = Math.min(L.rings.size(), 2);
                for (String part : State.PARTS) st.lv.put(part, lv);
                DojoKit k = built(halls, L, st);
                State before = bare(style, lv);
                before.arenas = 1;
                ImageIO.write(k.closeUpTex(k.partBox("arena", before)), "png", new File(texDir, "cu_arena_" + lv + "_" + style + ".png"));
                for (String part : State.PARTS)
                    ImageIO.write(k.closeUpTex(k.partBox(part, before)), "png", new File(texDir, "cu_" + part + "_" + lv + "_" + style + ".png"));
                pics += 1 + State.PARTS.size();
            }
        for (int t = 1; t <= 3; t++) {
            State st = bare("none", t);
            st.arenas = 1;
            DojoKit k = built(halls, L, st);
            ImageIO.write(k.closeUpTex(k.partBox("arena", st)), "png", new File(texDir, "cu_arena_0_t" + t + ".png"));
            for (String part : State.PARTS)
                ImageIO.write(k.closeUpTex(k.partBox(part, st)), "png", new File(texDir, "cu_" + part + "_0_t" + t + ".png"));
            pics += 1 + State.PARTS.size();
        }
        Files.writeString(indexFile.toPath(), any.indexJson(pieces.toString()));
        System.out.println("export: " + pics + " close-ups, index " + indexFile);
    }

    static int savePiece(Structure before, Structure after, File dir, String name, String key, StringBuilder index) throws Exception {
        int[] at = new int[3];
        Structure p = piece(before, after, at);
        if (p == null) return 0;
        p.save(new File(dir, name + ".nbt"), true);
        if (index.length() > 0) index.append(",\n");
        index.append(String.format(Locale.ROOT, "    \"%s/%s\": [%d, %d, %d]", key, name, at[0], at[1], at[2]));
        return 1;
    }

    BufferedImage closeUpTex(int[] box) {
        BufferedImage img = closeUp(box, null, null);
        int[] bb = alphaBounds(img);
        return img.getSubimage(bb[0], bb[1], bb[2] - bb[0] + 1, bb[3] - bb[1] + 1);
    }

    static int[] alphaBounds(BufferedImage img) {
        int x0 = img.getWidth(), y0 = img.getHeight(), x1 = -1, y1 = -1;
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++)
            if ((img.getRGB(x, y) >>> 24) != 0) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
        return x1 < 0 ? new int[]{0, 0, img.getWidth() - 1, img.getHeight() - 1} : new int[]{x0, y0, x1, y1};
    }

    /** Everything LM places and positions from, in plot coordinates (x, y, z inside the plot structure). */
    String indexJson(String pieces) {
        int[] h = L.hall;
        StringBuilder rings = new StringBuilder(), boxes = new StringBuilder();
        State none = State.start();
        for (int k = 0; k < L.rings.size(); k++) {
            if (k > 0) rings.append(", ");
            rings.append("[").append(fx + L.rings.get(k)[0]).append(", ").append(fz + L.rings.get(k)[1]).append("]");
            State b = State.start();
            b.arenas = k;
            int[] bx = partBox("arena", b);
            boxes.append(String.format(Locale.ROOT, "    \"arena_%d\": [[%d, %d, %d, %d, %d, %d]],%n", k + 1, fx + bx[0], 0, fz + bx[1],
                    fx + bx[2], ceilY, fz + bx[3]));
        }
        for (String part : State.PARTS) {
            if (part.equals("garden")) continue;
            int[] bx = partBox(part, none);
            int top = part.equals("spectator") ? ceilY - 1 : floorY + L.balconyUp;
            boxes.append(String.format(Locale.ROOT, "    \"%s\": [[%d, %d, %d, %d, %d, %d]],%n", part, fx + bx[0], floorY - 1, fz + bx[1],
                    fx + bx[2], top, fz + bx[3]));
        }
        // the garden: the plot round the foundation and the porch, as four strips
        int fx1 = fx + L.footW - 1, fz1 = fz + L.porch[1] + 2;
        boxes.append(String.format(Locale.ROOT, "    \"garden\": [[0, 0, 0, %d, %d, %d], [0, 0, %d, %d, %d, %d], [0, 0, %d, %d, %d, %d], [%d, 0, %d, %d, %d, %d]]%n",
                s.sx - 1, s.sy - 1, fz - 1,                     // north of the foundation
                fz1 + 1, s.sx - 1, s.sy - 1, s.sz - 1,          // south of the porch
                fz, fx - 1, s.sy - 1, fz1,                      // west
                fx1 + 1, fz, s.sx - 1, s.sy - 1, fz1));         // east
        int balY = floorY + L.balconyUp;
        return String.format(Locale.ROOT, """
                {
                  "_comment": "LM 4.7.68 dojo plot, written by DojoKit export (tools/dmz-adaptive-difficulty/tools/fx). Plot coordinates: x, y, z inside the plot structure; LM puts the plot's floor (floorY) at its dojo floor height.",
                  "size": [%d, %d, %d],
                  "floorY": %d,
                  "ceilY": %d,
                  "foundation": [%d, %d, %d, %d],
                  "ringSize": %d,
                  "rings": [%s],
                  "spawn": [%d.5, %d, %d.5, 180],
                  "spectate": [%d.5, %d, %d.5, 0],
                  "balcony": [%d, %d, %d, %d, %d, %d],
                  "hall": [%d, %d, %d, %d],
                  "planPx": %d,
                  "planOrigin": [%d, %d],
                  "planSize": [%d, %d],
                  "boxes": {
                %s  },
                  "pieces": {
                %s
                  }
                }
                """, s.sx, s.sy, s.sz, floorY, ceilY, fx, fz, fx + L.footW - 1, fz + L.footD - 1, L.ringSize, rings,
                fx + L.spawn[0], floorY, fz + L.spawn[1], fx + L.spectate[0], balY, fz + L.spectate[1],
                fx + L.hall[0] + 1, balY, fz + L.balconyZ[0], fx + L.hall[2] - 1, balY + 4, fz + L.balconyZ[1],
                fx + h[0], fz + h[1], fx + h[2], fz + h[3], PLAN_PX, fx - 1, fz - 1, (L.footW + 2) * PLAN_PX, (L.porch[1] + 4) * PLAN_PX,
                boxes, pieces);
    }

    // ------------------------------------------------------------------ main
    /** The upgrade previews the menus use: from a new dojo, and from one that has grown. */
    static final String[][] PREVIEWS = {
        {"start", "arena", "ring2"}, {"start", "training_hall", "training1"}, {"start", "spectator", "balcony1"},
        {"start", "garden", "garden1"}, {"ki:arenas=4,all=1", "arena", "ring5"}, {"ki:arenas=4,all=1", "meditation", "meditation2"},
    };

    public static void main(String[] a) throws Exception {
        if (a.length < 5 || !(a[0].equals("make") || a[0].equals("upgrade") || a[0].equals("export"))) {
            System.out.println("usage: DojoKit make <hall_t1.nbt,hall_t2.nbt,hall_t3.nbt> <recipe.json> <outNbtDir> <outPictureDir> [-only name] [-noviews]\n"
                    + "       DojoKit upgrade <halls> <recipe.json> <state> <upgrade id> <out prefix>\n"
                    + "       DojoKit export <halls> <recipe.json> <structures dir> <textures dir> <index.json>");
            System.exit(2);
        }
        String[] hallFiles = a[1].split(",");
        Map<String, Object> recipe = ModelPreview.obj(new ModelPreview.J(Files.readString(new File(a[2]).toPath())).read());
        Layout L = Layout.of(recipe);
        BuildStyle.lib();
        LIB = new BlockLib(BlockLib.mods());
        Structure[] halls = new Structure[3];
        for (int t = 1; t <= 3; t++) {
            halls[t - 1] = Structure.load(new File(hallFiles[t - 1]));
            DetailKit.weather(halls[t - 1], 0.25, 68 + t);
        }
        if (a[0].equals("export")) {
            if (a.length < 6) { System.out.println("export needs <structures dir> <textures dir> <index.json>"); System.exit(2); }
            exportAll(halls, L, new File(a[3]), new File(a[4]), new File(a[5]));
            return;
        }
        if (a[0].equals("upgrade")) {
            if (a.length < 6) { System.out.println("upgrade needs <state> <upgrade id> <out prefix>"); System.exit(2); }
            for (Map.Entry<String, BufferedImage> e : upgradePictures(halls, L, State.parse(a[3]), a[4]).entrySet())
                ImageIO.write(e.getValue(), "png", new File(a[5] + "_" + e.getKey() + ".png"));
            return;
        }
        File nbtDir = new File(a[3]), picDir = new File(a[4]);
        nbtDir.mkdirs(); picDir.mkdirs();
        String only = null;
        boolean views = true;
        for (int i = 5; i < a.length; i++) {
            if (a[i].equals("-only")) only = a[++i];
            if (a[i].equals("-noviews")) views = false;
        }
        Map<String, List<BufferedImage>> byKind = new LinkedHashMap<>();
        List<String[]> builds = new ArrayList<>();
        builds.add(new String[]{"start", "start"});
        for (String style : STYLES) for (int t = 1; t <= 3; t++) builds.add(new String[]{style + "_t" + t, style + ":" + t});
        for (String[] bd : builds) {
            String name = bd[0];
            if (only != null && !only.equals(name)) continue;
            State st = bd[1].equals("start") ? State.start()
                    : State.showcase(bd[1].split(":")[0], Integer.parseInt(bd[1].split(":")[1]));
            DojoKit k = new DojoKit(halls[st.hallTier() - 1], L, st);
            k.build();
            File out = new File(nbtDir, "dojo_" + name + ".nbt");
            k.s.save(out, true);
            System.out.printf(Locale.ROOT, "%s: %dx%dx%d, floor y %d, ceiling y %d, foundation at %d %d -> %s%n", name,
                    k.s.sx, k.s.sy, k.s.sz, k.floorY, k.ceilY, k.fx, k.fz, out);
            if (!views) continue;
            for (Map.Entry<String, BufferedImage> e : k.pictures(name).entrySet()) {
                ImageIO.write(e.getValue(), "png", new File(picDir, e.getKey() + "_" + name + ".png"));
                if (!name.equals("start")) byKind.computeIfAbsent(e.getKey(), x -> new ArrayList<>()).add(e.getValue());
            }
        }
        if (views && (only == null || only.equals("upgrades")))
            for (String[] pv : PREVIEWS) {
                for (Map.Entry<String, BufferedImage> e : upgradePictures(halls, L, State.parse(pv[0]), pv[1]).entrySet())
                    ImageIO.write(e.getValue(), "png", new File(picDir, "upgrade_" + pv[2] + "_" + e.getKey() + ".png"));
                System.out.println("upgrade preview " + pv[2] + ": " + pv[1] + " from " + pv[0]);
            }
        if (only == null && views) {
            Map<String, String> titles = Map.of("plan", "Floor plans (roof off) - rows: Ki Masters, Melee, Strike Moves, Evasion; columns: basic, refined, masterwork",
                    "arena", "The hall floor from the spectator balcony - rows: styles; columns: tiers",
                    "training", "The training hall from its door - rows: styles; columns: tiers",
                    "outside", "Outside - rows: styles; columns: tiers",
                    "front", "From the path - rows: styles; columns: tiers",
                    "night", "At night - rows: styles; columns: tiers");
            for (Map.Entry<String, List<BufferedImage>> e : byKind.entrySet())
                if (titles.containsKey(e.getKey())) ImageIO.write(sheet(e.getValue(), null, 3, titles.get(e.getKey()), 0.5), "png", new File(picDir, "sheet_" + e.getKey() + ".png"));
        }
    }
}
