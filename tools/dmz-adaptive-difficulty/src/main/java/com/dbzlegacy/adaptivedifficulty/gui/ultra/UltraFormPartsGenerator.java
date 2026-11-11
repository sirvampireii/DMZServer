package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Java port of {@code sim/gen_dmzultra_form_parts.py}: builds a {@link BodyPartCatalog.Catalog}
 * straight from a DMZ Ultra jar's {@code assets/dmzultra/form_parts/} tables. LM uses it at boot
 * when the installed Ultra is newer than every catalog it ships, so new Ultra content works
 * without an LM rebuild. Keep the rules in step with the Python generator:
 *
 * <ul>
 *   <li>Tables: the sets {@code sets.json} lists, in its order; then every other top-level table
 *       by name; then {@code forms.json}. The first table that defines a key wins.
 *   <li>Only plain {@code race/group/form} keys ({@link #KEY}); variant keys are skipped.
 *   <li>Ultra's {@code formKeyAliases} (first definition wins) apply first, then LM's server
 *       aliases ({@code dmzultra-form-parts-server-aliases.json}); an alias copies its target's
 *       pieces and colors when the alias key is free and the target exists.
 *   <li>{@code pieces} (the shop list) is {@code forms.json}'s piece list only.
 *   <li>Design colors are each form's {@code a/b/c/g} hex keys (own/trim/horn/light).
 * </ul>
 */
public final class UltraFormPartsGenerator {
    static final String BASE = "assets/dmzultra/form_parts/";
    static final Pattern KEY = Pattern.compile("^[a-z0-9_]+/[a-z0-9_]+/[a-z0-9_]+$");
    private static final Pattern HEX = Pattern.compile("^#?[0-9A-Fa-f]{6}$");
    private static final Pattern TOML_VERSION = Pattern.compile("version\\s*=\\s*\"([^\"]+)\"");
    private static final String[][] COLORS = {{"a", "own"}, {"b", "trim"}, {"c", "horn"}, {"g", "light"}};
    /** LM-side aliases, shipped from {@code sim/form-parts-server-aliases.json} by build.sh. */
    static final String SERVER_ALIASES_RESOURCE = "dmzultra-form-parts-server-aliases.json";

    private UltraFormPartsGenerator() {}

    /** Generate from {@code jar}. Throws on a missing or unreadable table. */
    public static BodyPartCatalog.Catalog generate(Path jar, String origin) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return generate(zip, serverAliases(), origin);
        }
    }

    static BodyPartCatalog.Catalog generate(ZipFile jar, Map<String, String> serverAliases, String origin)
            throws Exception {
        JsonObject main = read(jar, BASE + "forms.json");
        List<String> pieces = new ArrayList<>();
        if (main.has("pieces") && main.get("pieces").isJsonArray()) {
            for (JsonElement el : main.getAsJsonArray("pieces")) {
                String id = text(el);
                if (!id.isEmpty()) {
                    pieces.add(id);
                }
            }
        }
        LinkedHashMap<String, List<String>> forms = new LinkedHashMap<>();
        LinkedHashMap<String, BodyPartCatalog.Design> designs = new LinkedHashMap<>();
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        for (String name : tables(jar)) {
            JsonObject table = read(jar, name);
            if (table.has("formKeyAliases") && table.get("formKeyAliases").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : table.getAsJsonObject("formKeyAliases").entrySet()) {
                    aliases.putIfAbsent(e.getKey().trim().toLowerCase(Locale.ROOT), text(e.getValue()));
                }
            }
            if (!table.has("forms") || !table.get("forms").isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : table.getAsJsonObject("forms").entrySet()) {
                String key = e.getKey().trim().toLowerCase(Locale.ROOT);
                if (!KEY.matcher(key).matches() || !e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject entry = e.getValue().getAsJsonObject();
                if (!entry.has("parts") || !entry.get("parts").isJsonArray() || forms.containsKey(key)) {
                    continue;
                }
                List<String> parts = new ArrayList<>();
                for (JsonElement el : entry.getAsJsonArray("parts")) {
                    String id = text(el);
                    if (!id.isEmpty()) {
                        parts.add(id);
                    }
                }
                forms.put(key, List.copyOf(parts));
                BodyPartCatalog.Design design = design(entry);
                if (design != null) {
                    designs.put(key, design);
                }
            }
        }
        applyAliases(aliases, forms, designs);
        applyAliases(serverAliases, forms, designs);
        return new BodyPartCatalog.Catalog(
                version(jar), origin, Map.copyOf(forms), Map.copyOf(designs), List.copyOf(pieces));
    }

    /** sets.json order, then the other top-level tables by name, then forms.json. */
    static List<String> tables(ZipFile jar) throws Exception {
        List<String> names = new ArrayList<>();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            String n = entries.nextElement().getName();
            if (n.startsWith(BASE) && n.endsWith(".json") && n.indexOf('/', BASE.length()) < 0) {
                names.add(n);
            }
        }
        names.sort(null);
        List<String> listed = new ArrayList<>();
        JsonObject sets = read(jar, BASE + "sets.json");
        if (sets.has("sets") && sets.get("sets").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : sets.getAsJsonObject("sets").entrySet()) {
                String full = "assets/dmzultra/" + e.getValue().getAsString();
                if (names.contains(full) && !listed.contains(full)) {
                    listed.add(full);
                }
            }
        }
        List<String> out = new ArrayList<>(listed);
        for (String n : names) {
            if (!listed.contains(n) && !n.equals(BASE + "forms.json") && !n.equals(BASE + "sets.json")) {
                out.add(n);
            }
        }
        out.add(BASE + "forms.json");
        return out;
    }

    private static void applyAliases(
            Map<String, String> aliases,
            Map<String, List<String>> forms,
            Map<String, BodyPartCatalog.Design> designs) {
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            String alias = e.getKey();
            String target = e.getValue();
            if (!forms.containsKey(alias) && forms.containsKey(target) && KEY.matcher(alias).matches()) {
                forms.put(alias, forms.get(target));
                if (designs.containsKey(target)) {
                    designs.put(alias, designs.get(target));
                }
            }
        }
    }

    /** Colors from a/b/c/g; null when the form has none. A missing key reads as white. */
    private static BodyPartCatalog.Design design(JsonObject entry) {
        int[] rgb = new int[4];
        boolean any = false;
        for (int i = 0; i < COLORS.length; i++) {
            rgb[i] = 0xFFFFFF;
            JsonElement v = entry.get(COLORS[i][0]);
            if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()
                    && HEX.matcher(v.getAsString()).matches()) {
                rgb[i] = Integer.parseInt(v.getAsString().replace("#", ""), 16);
                any = true;
            }
        }
        return any ? new BodyPartCatalog.Design(rgb[0], rgb[1], rgb[2], rgb[3]) : null;
    }

    /** {@code version} of the dmzultra block in mods.toml, else "unknown". */
    static String version(ZipFile jar) {
        try {
            ZipEntry e = jar.getEntry("META-INF/mods.toml");
            if (e == null) {
                return "unknown";
            }
            String toml = new String(jar.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
            int at = toml.lastIndexOf("modId=\"dmzultra\"");
            Matcher m = TOML_VERSION.matcher(at < 0 ? toml : toml.substring(at));
            return m.find() ? m.group(1) : "unknown";
        } catch (Exception ex) {
            return "unknown";
        }
    }

    /** LM-side alias -> target pairs from the shipped resource; empty when it is missing. */
    static Map<String, String> serverAliases() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try (InputStream in = UltraFormPartsGenerator.class.getClassLoader().getResourceAsStream(SERVER_ALIASES_RESOURCE)) {
            if (in == null) {
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("aliases") && root.get("aliases").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("aliases").entrySet()) {
                    out.put(e.getKey().trim().toLowerCase(Locale.ROOT), text(e.getValue()));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static JsonObject read(ZipFile jar, String name) throws Exception {
        ZipEntry e = jar.getEntry(name);
        if (e == null) {
            throw new IllegalStateException("missing " + name);
        }
        try (InputStreamReader r = new InputStreamReader(jar.getInputStream(e), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    /** Python's {@code str(p).strip().lower()} for the JSON values the tables use. */
    private static String text(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "none";
        }
        String s = el.isJsonPrimitive() ? el.getAsString() : el.toString();
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
