package com.dbzlegacy.adaptivedifficulty.dojo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LM 4.7.68: the pictures the dojo menus show on LM clients (UltraScreenSpec#pictures), from textures DojoKit
 * rendered with BlockRender (assets/legacymechanics/textures/gui/dojo):
 * <ul>
 *   <li>Where: the dojo's floor plan, roof off, as it is (or will be): the empty hall of its tier and style, with
 *       each built part cut out of the same tier's plan with everything built; the part an upgrade builds marked
 *       green;</li>
 *   <li>Now and After: a close-up of that part of the hall before and after the upgrade (rooms are shown at the
 *       level they would have, with the hall at that tier).</li>
 * </ul>
 */
public final class DojoPictures {
    private DojoPictures() {}

    static final String DIR = "assets/legacymechanics/textures/gui/dojo/";
    static final int GREEN = 0x3CD25A;
    private static final Map<String, int[]> SIZES = new ConcurrentHashMap<>();

    /** Width and height of dojo texture {@code name} (read from the PNG header in the jar), or null. */
    static int[] size(String name) {
        return SIZES.computeIfAbsent(name, n -> {
            try (InputStream in = DojoPictures.class.getClassLoader().getResourceAsStream(DIR + n + ".png")) {
                if (in == null) {
                    return new int[] {0, 0};
                }
                DataInputStream d = new DataInputStream(in);
                d.skipBytes(16);                        // signature (8) + IHDR length and type (8)
                return new int[] {d.readInt(), d.readInt()};
            } catch (Exception e) {
                return new int[] {0, 0};
            }
        });
    }

    static boolean exists(String name) {
        return size(name)[0] > 0;
    }

    static JsonArray layer(String tex, int tw, int th, int u, int v, int uw, int vh, int x, int y, int w, int h) {
        JsonArray a = new JsonArray();
        a.add("dojo/" + tex);
        for (int n : new int[] {tw, th, u, v, uw, vh, x, y, w, h}) {
            a.add(n);
        }
        return a;
    }

    /** One whole texture as a picture with a caption; null when the texture is missing. */
    static JsonObject whole(String tex, String caption) {
        int[] s = size(tex);
        if (s[0] <= 0) {
            return null;
        }
        JsonObject it = new JsonObject();
        it.addProperty("c", caption);
        it.addProperty("a", s[0] * 1000 / Math.max(1, s[1]));
        JsonArray l = new JsonArray();
        l.add(layer(tex, s[0], s[1], 0, 0, s[0], s[1], 0, 0, 1000, 1000));
        it.add("l", l);
        return it;
    }

    /** The plan rectangle of a part ({x0, z0, x1, z1} in plot coordinates). */
    static int[] planRect(DojoLayout l, String part) {
        if ("spectator".equals(part)) {
            return new int[] {l.balcony[0], l.balcony[2], l.balcony[3], l.balcony[5]};
        }
        int[] b = l.boxes.get(part).get(0);
        return new int[] {b[0], b[2], b[3], b[5]};
    }

    /**
     * Where: the floor plan of {@code s}, roof off; {@code target} (a part id, or null) marked green. Null when the
     * plan textures are missing.
     */
    public static JsonObject where(DojoState s, String target, String caption) {
        DojoLayout l = DojoLayout.get();
        String empty = "plan_" + s.key() + "_empty", full = "plan_" + s.key() + "_full";
        if (l == null || !exists(empty) || !exists(full)) {
            return null;
        }
        int tw = l.planSize[0], th = l.planSize[1];
        JsonArray layers = new JsonArray();
        layers.add(layer(empty, tw, th, 0, 0, tw, th, 0, 0, 1000, 1000));
        for (Map.Entry<String, Integer> e : DojoPlacer.parts(s).entrySet()) {
            if (e.getValue() <= 0 || "garden".equals(e.getKey())) {
                continue;
            }
            int[] r = planRect(l, e.getKey());
            int u = Math.max(0, (r[0] - l.planOrigin[0]) * l.planPx), v = Math.max(0, (r[1] - l.planOrigin[1]) * l.planPx);
            int uw = Math.min(tw - u, (r[2] - r[0] + 1) * l.planPx), vh = Math.min(th - v, (r[3] - r[1] + 1) * l.planPx);
            if (uw <= 0 || vh <= 0) {
                continue;
            }
            layers.add(layer(full, tw, th, u, v, uw, vh, u * 1000 / tw, v * 1000 / th, uw * 1000 / tw, vh * 1000 / th));
        }
        JsonObject it = new JsonObject();
        it.addProperty("c", caption);
        it.addProperty("a", tw * 1000 / th);
        it.add("l", layers);
        if (target != null && !"garden".equals(target) && l.boxes.containsKey(target)) {
            int[] r = planRect(l, target);
            JsonArray m = new JsonArray();
            JsonArray mark = new JsonArray();
            mark.add((r[0] - l.planOrigin[0]) * l.planPx * 1000 / tw);
            mark.add((r[1] - l.planOrigin[1]) * l.planPx * 1000 / th);
            mark.add((r[2] - r[0] + 1) * l.planPx * 1000 / tw);
            mark.add((r[3] - r[1] + 1) * l.planPx * 1000 / th);
            mark.add(GREEN);
            m.add(mark);
            it.add("m", m);
        }
        return it;
    }

    /** The close-up texture of a part: {@code level} 0 is the empty space in a hall of {@code tier}. */
    static String closeUp(String part, int level, String style, int tier) {
        String p = part.startsWith("arena_") ? "arena" : part;
        if (level <= 0) {
            return "cu_" + p + "_0_t" + tier;
        }
        return "cu_" + p + "_" + Math.min(3, level) + "_" + style;
    }

    /**
     * Now, After and Where for buying one level of {@code upgrade} from {@code before}; an empty array when the
     * upgrade shows no building (disciple capacity) or the textures are missing.
     */
    public static JsonArray upgrade(DojoState before, String upgrade) {
        JsonArray items = new JsonArray();
        if (DojoCatalog.CAPACITY.equals(upgrade)) {
            return items;
        }
        DojoState after = before.next(upgrade);
        String part = DojoState.part(upgrade, before.arenas);
        boolean ring = DojoCatalog.ARENA.equals(upgrade);
        int lvNow = ring ? 0 : before.level(upgrade);
        int lvAfter = ring ? after.tier : after.level(upgrade);
        JsonObject now = whole(closeUp(part, lvNow, before.style, before.tier), "Now");
        JsonObject then = whole(closeUp(part, lvAfter, after.style, after.tier), "After");
        JsonObject where = where(after, part, "Where");
        for (JsonObject o : new JsonObject[] {now, then, where}) {
            if (o != null) {
                items.add(o);
            }
        }
        return items;
    }

    /**
     * LM 4.8.4 (H): a fully built track's page shows that part alone at the level it has, instead of the whole hall:
     * a room at its top level ({@code cu_<part>_3_<style>}), the rings in the look the hall has now. One item, or an
     * empty array for disciple capacity (no building) or a missing texture.
     */
    public static JsonArray maxed(DojoState s, String upgrade) {
        JsonArray items = new JsonArray();
        if (s == null || DojoCatalog.CAPACITY.equals(upgrade)) {
            return items;
        }
        boolean ring = DojoCatalog.ARENA.equals(upgrade);
        String part = ring ? "arena" : DojoState.part(upgrade, s.arenas);
        int level = ring ? s.tier : s.level(upgrade);
        JsonObject it = whole(closeUp(part, level, s.style, s.tier), "Fully built");
        if (it != null) {
            items.add(it);
        }
        return items;
    }

    /** The plan of the dojo as it is, for the Dojo tab and the upgrade list. */
    public static JsonArray plan(DojoState s) {
        JsonArray items = new JsonArray();
        JsonObject w = where(s, null, "");
        if (w != null) {
            items.add(w);
        }
        return items;
    }

    /** Self-test (mod load): the textures for a new dojo and a first ring are in the jar. */
    public static String selfTest() {
        DojoState start = DojoState.parse("v2|t=1|s=none|a=1|0|0|0|0|0|0");
        JsonArray ring = upgrade(start, DojoCatalog.ARENA);
        JsonArray room = upgrade(start, DojoCatalog.TRAINING);
        if (ring.size() != 3 || room.size() != 3 || plan(start).size() != 1) {
            return "FAIL ring " + ring.size() + ", room " + room.size() + ", plan " + plan(start).size();
        }
        return "OK";
    }
}
