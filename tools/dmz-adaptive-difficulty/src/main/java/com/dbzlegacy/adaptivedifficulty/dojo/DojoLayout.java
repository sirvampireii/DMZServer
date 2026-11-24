package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LM 4.7.68: the dojo plot as DojoKit exported it ({@code data/legacymechanics/dojo/index.json} in the LM jar): the
 * plot size, the floor, ring centres, landing points, the spectator balcony, the box each part of the dojo occupies,
 * and where each structure piece goes. Plot coordinates (x, y, z inside the plot structure).
 *
 * <p>Made by the mod-toolkit (tools/dmz-adaptive-difficulty/tools/fx/DojoKit.java, "export"); LM never edits it.
 */
public final class DojoLayout {
    public static final String INDEX = "data/legacymechanics/dojo/index.json";
    public static final String STRUCTURES = "data/legacymechanics/structures/dojo/";

    public int sx, sy, sz, floorY, ceilY, ringSize, planPx;
    public int[] foundation, balcony, hall, planOrigin, planSize;
    public double[] spawn, spectate;
    public final List<int[]> rings = new ArrayList<>();
    /** Part id (arena_1..6, training_hall, meditation, spectator, trophy_hall, quarters, garden) to its boxes. */
    public final Map<String, List<int[]>> boxes = new LinkedHashMap<>();
    /** "t2_ki/arena_3" to where that piece goes. */
    public final Map<String, int[]> pieces = new LinkedHashMap<>();

    private static volatile DojoLayout loaded;
    private static volatile boolean tried;

    /** The layout, or null when the index is missing from the jar (a broken build). */
    public static DojoLayout get() {
        if (!tried) {
            synchronized (DojoLayout.class) {
                if (!tried) {
                    loaded = load();
                    tried = true;
                }
            }
        }
        return loaded;
    }

    private static DojoLayout load() {
        try (InputStream in = DojoLayout.class.getClassLoader().getResourceAsStream(INDEX)) {
            if (in == null) {
                AdaptiveDifficultyMod.LOGGER.error("[{}] dojo layout missing: {}", AdaptiveDifficultyMod.MOD_ID, INDEX);
                return null;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            DojoLayout l = new DojoLayout();
            int[] size = ints(o.getAsJsonArray("size"));
            l.sx = size[0];
            l.sy = size[1];
            l.sz = size[2];
            l.floorY = o.get("floorY").getAsInt();
            l.ceilY = o.get("ceilY").getAsInt();
            l.ringSize = o.get("ringSize").getAsInt();
            l.planPx = o.get("planPx").getAsInt();
            l.foundation = ints(o.getAsJsonArray("foundation"));
            l.balcony = ints(o.getAsJsonArray("balcony"));
            l.hall = ints(o.getAsJsonArray("hall"));
            l.planOrigin = ints(o.getAsJsonArray("planOrigin"));
            l.planSize = ints(o.getAsJsonArray("planSize"));
            l.spawn = doubles(o.getAsJsonArray("spawn"));
            l.spectate = doubles(o.getAsJsonArray("spectate"));
            for (JsonElement r : o.getAsJsonArray("rings")) {
                l.rings.add(ints(r.getAsJsonArray()));
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("boxes").entrySet()) {
                List<int[]> list = new ArrayList<>();
                for (JsonElement b : e.getValue().getAsJsonArray()) {
                    list.add(ints(b.getAsJsonArray()));
                }
                l.boxes.put(e.getKey(), list);
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("pieces").entrySet()) {
                l.pieces.put(e.getKey(), ints(e.getValue().getAsJsonArray()));
            }
            return l;
        } catch (Exception e) {
            AdaptiveDifficultyMod.LOGGER.error("[{}] dojo layout unreadable: {}", AdaptiveDifficultyMod.MOD_ID, e.toString());
            return null;
        }
    }

    static int[] ints(JsonArray a) {
        int[] r = new int[a.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = a.get(i).getAsInt();
        }
        return r;
    }

    static double[] doubles(JsonArray a) {
        double[] r = new double[a.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = a.get(i).getAsDouble();
        }
        return r;
    }

    /** True when box {@code a} and {@code b} ({x0, y0, z0, x1, y1, z1}) share a block. */
    public static boolean meet(int[] a, int[] b) {
        return a[0] <= b[3] && b[0] <= a[3] && a[1] <= b[4] && b[1] <= a[4] && a[2] <= b[5] && b[2] <= a[5];
    }

    /** Self-test: the layout loads and every part has a box (runs at mod load). */
    public static String selfTest() {
        DojoLayout l = get();
        if (l == null) {
            return "FAIL no layout";
        }
        for (String part : new String[] {"arena_1", "arena_6", "training_hall", "meditation", "spectator", "trophy_hall",
                "quarters", "garden"}) {
            if (!l.boxes.containsKey(part)) {
                return "FAIL no box for " + part;
            }
        }
        if (l.rings.size() != DojoCatalog.MAX_ARENAS) {
            return "FAIL " + l.rings.size() + " rings";
        }
        if (!l.pieces.containsKey("t1_none/arena_1") || !l.pieces.containsKey("t3_evasion/garden_3")) {
            return "FAIL pieces missing";
        }
        return "OK (" + l.rings.size() + " rings, " + l.pieces.size() + " pieces)";
    }
}
