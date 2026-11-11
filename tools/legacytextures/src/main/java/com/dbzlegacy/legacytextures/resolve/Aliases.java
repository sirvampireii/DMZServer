package com.dbzlegacy.legacytextures.resolve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Cross-race HD aliases from config/legacytextures/aliases.json (0.2.5). A race with no HD files of its
 * own can borrow a donor race's HD file at the same relative path: {@code from} and {@code to} are path
 * prefixes such as {@code dragonminez:textures/entity/races/taimujin/}. Entries are data; adding or
 * removing one needs no code change, only F3+T or a restart.
 *
 * <p>Only player textures alias (category players, under {@code textures/}); faces never do, so eyes
 * stay as the race ships them. The file is written from the jar's default when it is missing.
 */
public final class Aliases {
    public static final String FILE = "aliases.json";
    public static final Aliases NONE = new Aliases(List.of(), List.of());

    private final List<Entry> entries;
    private final List<String> warnings;

    private Aliases(List<Entry> entries, List<String> warnings) {
        this.entries = Collections.unmodifiableList(entries);
        this.warnings = Collections.unmodifiableList(warnings);
    }

    /** One alias: a file under {@code from} borrows the donor's file under {@code to}. */
    public record Entry(String namespace, String from, String to) {
    }

    public static Aliases load(Path file, SetRegistry.DefaultSource defaults) {
        List<String> warnings = new ArrayList<>();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                try (InputStream in = defaults.open(FILE)) {
                    if (in != null) {
                        Files.copy(in, file);
                    }
                }
            }
        } catch (IOException e) {
            warnings.add("could not write the default " + FILE + ": " + e.getMessage());
        }
        if (!Files.isRegularFile(file)) {
            return new Aliases(List.of(), warnings);
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(r).getAsJsonObject(), warnings);
        } catch (Exception e) {
            warnings.add(FILE + ": not valid JSON (" + e.getMessage() + "); no aliases");
            return new Aliases(List.of(), warnings);
        }
    }

    static Aliases parse(JsonObject o, List<String> warnings) {
        List<Entry> out = new ArrayList<>();
        JsonElement arr = o.get("aliases");
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement e : (JsonArray) arr) {
                Entry entry = e.isJsonObject() ? entry(e.getAsJsonObject(), warnings) : null;
                if (entry != null) {
                    out.add(entry);
                }
            }
        }
        // Longest prefix first, so a narrower entry wins over a wider one.
        out.sort((a, b) -> b.from.length() - a.from.length());
        return new Aliases(out, warnings);
    }

    private static Entry entry(JsonObject o, List<String> warnings) {
        String from = str(o, "from");
        String to = str(o, "to");
        ResourceLocation f = prefix(from);
        ResourceLocation t = prefix(to);
        if (f == null || t == null || !f.m_135827_().equals(t.m_135827_()) || f.equals(t)) {
            warnings.add(FILE + ": alias '" + from + "' -> '" + to + "' needs two different prefixes "
                    + "'<namespace>:<path>/' in the same namespace; skipped");
            return null;
        }
        for (ResourceLocation p : List.of(f, t)) {
            if (Category.of(p) != Category.PLAYERS || !p.m_135815_().startsWith("textures/")
                    || isFace(p.m_135815_())) {
                warnings.add(FILE + ": alias '" + from + "' -> '" + to + "': only player textures alias, never "
                        + "faces; skipped");
                return null;
            }
        }
        return new Entry(f.m_135827_(), f.m_135815_(), t.m_135815_());
    }

    private static ResourceLocation prefix(String s) {
        int colon = s.indexOf(':');
        if (colon <= 0 || !s.endsWith("/")) {
            return null;
        }
        return ResourceLocation.m_214293_(s.substring(0, colon), s.substring(colon + 1));
    }

    private static boolean isFace(String path) {
        return path.contains("/faces/");
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString().trim();
    }

    /** The donor file {@code loc} borrows, or null when no alias applies (other files, faces). */
    public ResourceLocation donor(ResourceLocation loc) {
        String path = loc.m_135815_();
        if (entries.isEmpty() || isFace(path)) {
            return null;
        }
        for (Entry e : entries) {
            if (e.namespace.equals(loc.m_135827_()) && path.startsWith(e.from)) {
                return ResourceLocation.m_214293_(e.namespace, e.to + path.substring(e.from.length()));
            }
        }
        return null;
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<String> warnings() {
        return warnings;
    }
}
