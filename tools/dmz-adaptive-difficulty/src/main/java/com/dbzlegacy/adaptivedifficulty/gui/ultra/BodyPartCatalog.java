package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a worn form onto explicit {@code playerModel.parts.bodyParts} entries.
 *
 * <p>The table is the DMZUltra FormParts catalog ({@code race/group/form} → piece ids),
 * shipped as data so a dedicated server can name the pieces without the client mod.
 * Missing or unreadable data omits form pieces. The Saiyan race tail is not a body part:
 * DMZ draws it from the character's tail flag through the client's own race layer, so naming
 * it here as well put a second tail on other players' screens. A tail piece is only sent for a
 * form that names one, or a shop tail the player equipped.
 */
public final class BodyPartCatalog {
    /** Wire {@code source} for pieces that live in DMZUltra's form-parts model. */
    public static final String SOURCE_DMZULTRA = "dmzultra";
    /**
     * Id of the race tail as a piece. Never added automatically (the race layer draws the race
     * tail); kept so {@link #slot} and the chosen-tail check still recognise the id.
     */
    public static final String SAIYAN_TAIL = "saiyan_tail";

    /**
     * Per-form {@code FormParts.Design} colors ({@code own}/{@code trim}/{@code horn}/
     * {@code light}) from the resource's {@code designs} map. Form-constant — identical for
     * every player wearing the form. The client prefers explicit colors over its reflective
     * {@code FormParts$Look} read, which can come back gray for a remote player.
     */
    public record Design(int own, int trim, int horn, int light) {}

    public static final String SLOT_TAIL = "tail";
    public static final String SLOT_WINGS = "wings";
    public static final String SLOT_HORNS = "horns";
    public static final String SLOT_EARS = "ears";
    public static final String SLOT_EXTRA_ARMS = "extra_arms";
    /** Piece that is not one of the standard anchors. Clients still attach it by id. */
    public static final String SLOT_BODY = "body";

    private static final String RESOURCE = "dmzultra-form-parts.json";

    private static volatile Map<String, List<String>> forms = Map.of();
    private static volatile Map<String, Design> designs = Map.of();
    private static volatile List<String> shopPieces = List.of();
    private static volatile boolean loaded;

    private BodyPartCatalog() {}

    public record Part(String id, String source, String slot) {}

    /**
     * Form pieces for the active form and the stack form. The race tail is never added here -
     * the client's race layer already draws it. Never throws. An empty list means "send no
     * bodyParts".
     */
    public static List<Part> resolve(
            String race,
            String group,
            String form,
            String stackGroup,
            String stackForm) {
        LinkedHashMap<String, Part> out = new LinkedHashMap<>();
        try {
            addForm(out, race, group, form);
            addForm(out, race, stackGroup, stackForm);
        } catch (Throwable ignored) {
            return List.of();
        }
        return List.copyOf(out.values());
    }

    /** Standard slot for a piece id. Unknown shapes use {@link #SLOT_BODY}. */
    public static String slot(String id) {
        String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (s.equals(SAIYAN_TAIL) || s.startsWith("tail_")) {
            return SLOT_TAIL;
        }
        if (s.startsWith("wing")) {
            return SLOT_WINGS;
        }
        if (s.startsWith("horn") || s.equals("antlers")) {
            return SLOT_HORNS;
        }
        if (s.contains("ear")) {
            return SLOT_EARS;
        }
        if (s.equals("arm_blade") || s.equals("arm_fins") || s.startsWith("extra_arm")) {
            return SLOT_EXTRA_ARMS;
        }
        return SLOT_BODY;
    }

    /** How many form keys the catalog loaded. Zero when the resource is missing. */
    public static int formCount() {
        ensureLoaded();
        return forms.size();
    }

    /**
     * Piece ids the Body Parts Shop may offer — the DMZUltra FormParts piece list from the
     * resource's top-level {@code pieces} array (mirrors the client's known meshes). Falls
     * back to the sorted union of every form's pieces when the array is absent. Empty when
     * the resource is missing.
     */
    public static List<String> shopPieceIds() {
        ensureLoaded();
        return shopPieces;
    }

    private static void addForm(Map<String, Part> out, String race, String group, String form) {
        String key = formKey(race, group, form);
        if (key == null) {
            return;
        }
        ensureLoaded();
        List<String> pieces = forms.get(key);
        if (pieces == null) {
            return;
        }
        for (String piece : pieces) {
            if (piece == null || piece.isBlank()) {
                continue;
            }
            String id = piece.trim().toLowerCase(Locale.ROOT);
            out.putIfAbsent(id, new Part(id, SOURCE_DMZULTRA, slot(id)));
        }
    }

    static String formKey(String race, String group, String form) {
        String r = norm(race);
        String g = norm(group);
        String f = norm(form);
        if (r.isEmpty() || g.isEmpty() || f.isEmpty()) {
            return null;
        }
        return r + "/" + g + "/" + f;
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (BodyPartCatalog.class) {
            if (loaded) {
                return;
            }
            forms = read();
            loaded = true;
        }
    }

    private static Map<String, List<String>> read() {
        try (InputStream in = BodyPartCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Map.of();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                List<String> pieces = readShopPieces(root);
                shopPieces = List.copyOf(pieces);
                designs = readDesigns(root);
                if (!root.has("forms") || !root.get("forms").isJsonObject()) {
                    return Map.of();
                }
                LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("forms").entrySet()) {
                    if (entry.getKey() == null || !entry.getValue().isJsonArray()) {
                        continue;
                    }
                    JsonArray arr = entry.getValue().getAsJsonArray();
                    List<String> formPieces = new ArrayList<>();
                    for (JsonElement el : arr) {
                        if (el != null && el.isJsonPrimitive()) {
                            formPieces.add(el.getAsString());
                        }
                    }
                    map.put(entry.getKey().trim().toLowerCase(Locale.ROOT), List.copyOf(formPieces));
                }
                return Map.copyOf(map);
            }
        } catch (Throwable ignored) {
            return Map.of();
        }
    }

    /** Top-level {@code pieces} array, else the sorted union of every form's pieces. */
    private static List<String> readShopPieces(JsonObject root) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (root != null && root.has("pieces") && root.get("pieces").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("pieces")) {
                if (el != null && el.isJsonPrimitive()) {
                    String id = el.getAsString().trim().toLowerCase(Locale.ROOT);
                    if (!id.isEmpty()) {
                        out.add(id);
                    }
                }
            }
        }
        if (!out.isEmpty()) {
            return new ArrayList<>(out);
        }
        if (root != null && root.has("forms") && root.get("forms").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("forms").entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonArray()) {
                    continue;
                }
                for (JsonElement el : entry.getValue().getAsJsonArray()) {
                    if (el != null && el.isJsonPrimitive()) {
                        String id = el.getAsString().trim().toLowerCase(Locale.ROOT);
                        if (!id.isEmpty()) {
                            out.add(id);
                        }
                    }
                }
            }
        }
        ArrayList<String> sorted = new ArrayList<>(out);
        java.util.Collections.sort(sorted);
        return sorted;
    }

    /**
     * Top-level {@code designs} map: form key → per-form Design colors. Missing entries or a
     * missing map yield an empty map (the client then falls back to its own DMZUltra table).
     */
    private static Map<String, Design> readDesigns(JsonObject root) {
        if (root == null || !root.has("designs") || !root.get("designs").isJsonObject()) {
            return Map.of();
        }
        try {
            LinkedHashMap<String, Design> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("designs").entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = entry.getValue().getAsJsonObject();
                Design d =
                        new Design(
                                intColor(o, "own"),
                                intColor(o, "trim"),
                                intColor(o, "horn"),
                                intColor(o, "light"));
                map.put(entry.getKey().trim().toLowerCase(Locale.ROOT), d);
            }
            return Map.copyOf(map);
        } catch (Throwable ignored) {
            return Map.of();
        }
    }

    private static int intColor(JsonObject o, String key) {
        try {
            if (o.has(key) && o.get(key).isJsonPrimitive()) {
                return o.get(key).getAsInt();
            }
        } catch (Throwable ignored) {
        }
        return 0xFFFFFF;
    }

    /**
     * Per-form {@link Design} colors for {@code race/group/form} (same normalization as
     * {@link #resolve}), else the stack form, else {@code null} when unknown. Form-constant —
     * identical for every player wearing the form.
     */
    public static Design design(
            String race,
            String group,
            String form,
            String stackGroup,
            String stackForm) {
        try {
            ensureLoaded();
            String key = formKey(race, group, form);
            Design d = key == null ? null : designs.get(key);
            if (d != null) {
                return d;
            }
            key = formKey(race, stackGroup, stackForm);
            return key == null ? null : designs.get(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** How many form color entries the catalog loaded. Zero when the resource is missing. */
    public static int designCount() {
        ensureLoaded();
        return designs.size();
    }
}
