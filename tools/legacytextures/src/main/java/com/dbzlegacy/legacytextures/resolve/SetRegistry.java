package com.dbzlegacy.legacytextures.resolve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The texture sets found in config/legacytextures/texture_sets/. Adding a set is one JSON file
 * (plus an optional folder &lt;id&gt;/ laid out like a resource pack, assets/&lt;ns&gt;/...); no code change.
 * The vanilla set always exists, even if its JSON is deleted.
 */
public final class SetRegistry {
    public static final String[] DEFAULT_FILES = {"vanilla.json", "hd.json"};
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Set<String> SOURCE_NAMESPACES = Set.of("dragonminez", "nbarmors", "minecraft");
    private static final int MAX_CHAIN = 8;

    private final Map<String, TextureSet> sets;
    private final Map<String, Path> folders;
    private final List<String> warnings;

    private SetRegistry(Map<String, TextureSet> sets, Map<String, Path> folders, List<String> warnings) {
        this.sets = Collections.unmodifiableMap(sets);
        this.folders = Collections.unmodifiableMap(folders);
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public static TextureSet builtInVanilla() {
        return new TextureSet(TextureSet.VANILLA, "Vanilla", "", "", EnumSet.allOf(Category.class), "");
    }

    /** Only the vanilla set; used before the config folder is read and on the server. */
    public static SetRegistry vanillaOnly() {
        Map<String, TextureSet> m = new LinkedHashMap<>();
        m.put(TextureSet.VANILLA, builtInVanilla());
        return new SetRegistry(m, Map.of(), List.of());
    }

    /**
     * Reads every *.json in {@code dir}. When the folder has no JSON yet, the shipped defaults are
     * written first so the owner has examples to copy.
     */
    public static SetRegistry load(Path dir, DefaultSource defaults) {
        List<String> warnings = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            if (!hasJson(dir)) {
                for (String name : DEFAULT_FILES) {
                    try (InputStream in = defaults.open(name)) {
                        if (in != null) {
                            Files.copy(in, dir.resolve(name));
                        }
                    }
                }
            }
        } catch (IOException e) {
            warnings.add("could not write the default texture sets to " + dir + ": " + e.getMessage());
        }
        Map<String, TextureSet> found = new TreeMap<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
                List<Path> sorted = new ArrayList<>();
                files.forEach(sorted::add);
                Collections.sort(sorted);
                for (Path file : sorted) {
                    try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        TextureSet set = parse(JsonParser.parseReader(r).getAsJsonObject(), file.getFileName().toString(),
                                warnings);
                        if (set == null) {
                            continue;
                        }
                        if (found.containsKey(set.id)) {
                            warnings.add(file.getFileName() + ": set id '" + set.id + "' is already used; skipped");
                            continue;
                        }
                        found.put(set.id, set);
                    } catch (Exception e) {
                        warnings.add(file.getFileName() + ": not a valid set file (" + e.getMessage() + ")");
                    }
                }
            } catch (IOException e) {
                warnings.add("could not list " + dir + ": " + e.getMessage());
            }
        }
        return build(found, dir, warnings);
    }

    static SetRegistry build(Map<String, TextureSet> found, Path dir, List<String> warnings) {
        Map<String, TextureSet> sets = new LinkedHashMap<>();
        TextureSet vanilla = found.remove(TextureSet.VANILLA);
        sets.put(TextureSet.VANILLA, vanilla != null && vanilla.isVanilla() ? vanilla : builtInVanilla());
        for (TextureSet s : found.values()) {
            String fb = s.fallback;
            if (fb.isEmpty() || fb.equals(s.id) || (!fb.equals(TextureSet.VANILLA) && !found.containsKey(fb))) {
                if (!fb.isEmpty() && !fb.equals(TextureSet.VANILLA)) {
                    warnings.add("set '" + s.id + "': fallback '" + fb + "' not found; using vanilla");
                }
                s = new TextureSet(s.id, s.name, s.namespace, s.basePath, s.categories, TextureSet.VANILLA);
            }
            sets.put(s.id, s);
        }
        Map<String, Path> folders = new LinkedHashMap<>();
        if (dir != null) {
            for (String id : sets.keySet()) {
                Path folder = dir.resolve(id).resolve("assets");
                if (Files.isDirectory(folder)) {
                    folders.put(id, folder);
                }
            }
        }
        return new SetRegistry(sets, folders, warnings);
    }

    static TextureSet parse(JsonObject o, String file, List<String> warnings) {
        String id = str(o, "id");
        if (!ID.matcher(id).matches()) {
            warnings.add(file + ": id must be 1-32 of a-z 0-9 _ - (got '" + id + "'); skipped");
            return null;
        }
        String namespace = str(o, "namespace");
        if (id.equals(TextureSet.VANILLA)) {
            namespace = "";
        } else if (namespace.isEmpty() || SOURCE_NAMESPACES.contains(namespace)
                || !ID.matcher(namespace).matches()) {
            warnings.add(file + ": set '" + id + "' needs its own namespace (not blank, dragonminez, nbarmors or "
                    + "minecraft); skipped");
            return null;
        }
        Set<Category> cats = EnumSet.noneOf(Category.class);
        JsonElement arr = o.get("categories");
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement e : (JsonArray) arr) {
                Category c = Category.parse(e.getAsString());
                if (c == null) {
                    warnings.add(file + ": unknown category '" + e.getAsString() + "' ignored");
                } else {
                    cats.add(c);
                }
            }
        } else {
            cats = EnumSet.allOf(Category.class);
        }
        String name = str(o, "name");
        return new TextureSet(id, name.isEmpty() ? id : name, namespace, str(o, "basePath"), cats, str(o, "fallback"));
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString().trim();
    }

    private static boolean hasJson(Path dir) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            return files.iterator().hasNext();
        }
    }

    public TextureSet get(String id) {
        return sets.get(id);
    }

    public TextureSet vanilla() {
        return sets.get(TextureSet.VANILLA);
    }

    /** The set a mode's id means here: that set, else the shipped hd set, else vanilla. */
    public TextureSet hdOrFallback(String id) {
        TextureSet s = sets.get(id);
        if (s == null) {
            s = sets.get("hd");
        }
        return s != null ? s : vanilla();
    }

    /** {@code start} and its fallbacks in order, ending at vanilla; a loop stops the walk. */
    public List<TextureSet> chain(TextureSet start) {
        List<TextureSet> out = new ArrayList<>();
        TextureSet s = start;
        while (s != null && out.size() < MAX_CHAIN && !out.contains(s)) {
            out.add(s);
            if (s.isVanilla()) {
                return out;
            }
            s = sets.get(s.fallback);
        }
        if (out.isEmpty() || !out.get(out.size() - 1).isVanilla()) {
            out.add(vanilla());
        }
        return out;
    }

    /** Every set except vanilla, in id order: the choices the picker offers. */
    public List<TextureSet> hdSets() {
        List<TextureSet> out = new ArrayList<>();
        for (TextureSet s : sets.values()) {
            if (!s.isVanilla()) {
                out.add(s);
            }
        }
        return out;
    }

    public Map<String, TextureSet> all() {
        return sets;
    }

    public Map<String, Path> folders() {
        return folders;
    }

    public List<String> warnings() {
        return warnings;
    }

    @FunctionalInterface
    public interface DefaultSource {
        InputStream open(String fileName) throws IOException;
    }
}
