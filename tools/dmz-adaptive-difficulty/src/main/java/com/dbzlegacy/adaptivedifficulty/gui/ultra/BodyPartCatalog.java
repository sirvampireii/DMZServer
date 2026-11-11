package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Maps a worn form onto explicit {@code playerModel.parts.bodyParts} entries.
 *
 * <p>The table is the DMZUltra FormParts catalog ({@code race/group/form} → piece ids),
 * shipped as data so a dedicated server can name the pieces without the client mod.
 * Missing or unreadable data omits form pieces. The Saiyan race tail is not a body part:
 * DMZ draws it from the character's tail flag through the client's own race layer, so naming
 * it here as well put a second tail on other players' screens. A tail piece is only sent for a
 * form that names one, or a shop tail the player equipped.
 *
 * <p>4.7.46: one catalog per Ultra version ({@link #SHIPPED}). {@link #active()} picks the newest
 * one that is not newer than the installed Ultra ({@link UltraVersion}), so the same LM jar sends
 * only ids the installed Ultra can draw. Every part path (shop list, equip checks, form resolve,
 * design colors, model sync) reads the active catalog through the static methods here.
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

    /** Shipped catalogs, oldest first: {@code dmzultra-form-parts-<version>.json}. */
    static final List<String> SHIPPED = List.of("0.4.0", "0.5.3");
    /** Pre-4.7.46 single resource name: an alias of the 0.4.0 catalog, read only as a fallback. */
    static final String LEGACY_RESOURCE = "dmzultra-form-parts.json";
    /**
     * DMZ Ultra 0.5.3's Cerealian "Tail" hairstyle names its pieces {@code cereal_tail_*}. They sit
     * on the head; the name is the hairstyle, not a tail, so they never take the tail slot (the
     * single-tail rule would otherwise drop a shop tail and hide the race tail).
     */
    private static final String HAIR_TAIL_PREFIX = "cereal_tail_";

    private static final Logger LOG = LogManager.getLogger("legacymechanics");
    private static final Map<String, Catalog> SHIPPED_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Catalog active;
    /** Test hook: the jar a newer-than-shipped Ultra is read from (null: the installed mod file). */
    static volatile Path installedJarOverride;

    private BodyPartCatalog() {}

    public record Part(String id, String source, String slot) {}

    /**
     * One loaded catalog: form key → piece ids, per-form colors, and the shop piece list.
     * {@code version} is the Ultra version it was generated from; {@code origin} says where it
     * came from ({@code shipped}, {@code generated}, {@code missing}).
     */
    public record Catalog(
            String version,
            String origin,
            Map<String, List<String>> forms,
            Map<String, Design> designs,
            List<String> shopPieces) {
        /** Form pieces for the active form and the stack form. Never throws. */
        public List<Part> resolve(String race, String group, String form, String stackGroup, String stackForm) {
            LinkedHashMap<String, Part> out = new LinkedHashMap<>();
            try {
                addForm(out, race, group, form);
                addForm(out, race, stackGroup, stackForm);
            } catch (Throwable ignored) {
                return List.of();
            }
            return List.copyOf(out.values());
        }

        /** Design colors for the form, else the stack form, else null. Never throws. */
        public Design design(String race, String group, String form, String stackGroup, String stackForm) {
            try {
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

        private void addForm(Map<String, Part> out, String race, String group, String form) {
            String key = formKey(race, group, form);
            List<String> pieces = key == null ? null : forms.get(key);
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

        public String describe() {
            return version + " (" + origin + ", " + shopPieces.size() + " shop pieces, " + forms.size() + " forms)";
        }
    }

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
        return active().resolve(race, group, form, stackGroup, stackForm);
    }

    /**
     * Standard slot for a piece id. Unknown shapes use {@link #SLOT_BODY}. A DMZUltra set piece
     * carries its set's prefix ({@code ystr_tail}, {@code msek_tail_feral}), so a tail is also a
     * {@code _tail} suffix or a {@code _tail_} infix; plural {@code _tails} ids are cloth (sash tails).
     */
    public static String slot(String id) {
        String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith(HAIR_TAIL_PREFIX)) {
            return SLOT_BODY;
        }
        if (s.equals(SAIYAN_TAIL) || s.startsWith("tail_") || s.endsWith("_tail") || s.contains("_tail_")) {
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

    /** How many form keys the active catalog loaded. Zero when the resource is missing. */
    public static int formCount() {
        return active().forms().size();
    }

    /**
     * Piece ids the Body Parts Shop may offer — the DMZUltra FormParts piece list from the
     * active catalog's top-level {@code pieces} array (mirrors the client's known meshes). Falls
     * back to the sorted union of every form's pieces when the array is absent. Empty when
     * the resource is missing.
     */
    public static List<String> shopPieceIds() {
        return active().shopPieces();
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
        return active().design(race, group, form, stackGroup, stackForm);
    }

    /** How many form color entries the active catalog loaded. Zero when the resource is missing. */
    public static int designCount() {
        return active().designs().size();
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

    /** The catalog every part path uses: chosen once from the detected Ultra version. */
    public static Catalog active() {
        Catalog c = active;
        if (c == null) {
            synchronized (BodyPartCatalog.class) {
                if (active == null) {
                    active = select(UltraVersion.detectedVersion());
                }
                c = active;
            }
        }
        return c;
    }

    /** Version, origin and size of the active catalog, for the boot line. */
    public static String activeDescription() {
        return active().describe();
    }

    /** Test hook: forget the active catalog so the next call selects again. */
    static void reselect() {
        synchronized (BodyPartCatalog.class) {
            active = null;
        }
    }

    /**
     * Highest shipped catalog version that is {@code <=} {@code detected}; the 0.4.0 catalog when
     * none is. Never a catalog newer than the installed Ultra: a 0.4.0 client cannot draw 0.5.3
     * piece ids.
     */
    static String shippedVersionFor(String detected) {
        String best = SHIPPED.get(0);
        for (String v : SHIPPED) {
            if (UltraVersion.compare(v, detected) <= 0 && UltraVersion.compare(v, best) > 0) {
                best = v;
            }
        }
        return best;
    }

    /**
     * Catalog for an installed Ultra {@code detected}. Newer than every shipped catalog: generate
     * one from the installed jar and adopt it only when it passes every
     * {@link BodyPartCatalogSelfTest} assertion; otherwise (or on any error) log an error and use
     * the newest shipped catalog, which is still older than the installed Ultra.
     */
    static Catalog select(String detected) {
        String newest = SHIPPED.get(SHIPPED.size() - 1);
        Catalog fallback = shipped(shippedVersionFor(detected));
        if (UltraVersion.compare(detected, newest) <= 0) {
            return fallback;
        }
        Path jar = installedJarOverride != null ? installedJarOverride : UltraVersion.installedJar();
        try {
            if (jar == null) {
                throw new IllegalStateException("the installed DMZ Ultra jar could not be found");
            }
            Catalog generated = UltraFormPartsGenerator.generate(jar, "generated");
            List<String> failures =
                    BodyPartCatalogSelfTest.check(generated, BodyPartCatalogSelfTest.Expect.newerThan(fallback));
            if (!failures.isEmpty()) {
                throw new IllegalStateException(failures.size() + " self-test assertion(s) failed: " + failures);
            }
            LOG.info("[legacymechanics] DMZ Ultra {} is newer than LM's newest catalog ({}); generated the body parts "
                    + "catalog from {}: {}", detected, newest, jar.getFileName(), generated.describe());
            return generated;
        } catch (Throwable t) {
            LOG.error("[legacymechanics] DMZ Ultra {} is newer than LM's newest catalog ({}), and generating one from "
                    + "the installed jar FAILED: {}. Using the shipped {} catalog: new Ultra pieces will not show until "
                    + "LM ships a catalog for this version.", detected, newest, t.getMessage(), fallback.version());
            return fallback;
        }
    }

    /** A shipped catalog by version (cached). The 0.4.0 catalog also answers to the old resource name. */
    static Catalog shipped(String version) {
        return SHIPPED_CACHE.computeIfAbsent(version, v -> {
            Catalog c = readResource("dmzultra-form-parts-" + v + ".json", v);
            if (c == null && SHIPPED.get(0).equals(v)) {
                c = readResource(LEGACY_RESOURCE, v);
            }
            return c != null ? c : new Catalog(v, "missing", Map.of(), Map.of(), List.of());
        });
    }

    private static Catalog readResource(String resource, String version) {
        try (InputStream in = BodyPartCatalog.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                return new Catalog(
                        version, "shipped", readForms(root), readDesigns(root), List.copyOf(readShopPieces(root)));
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Map<String, List<String>> readForms(JsonObject root) {
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
}
