package com.dbzlegacy.adaptivedifficulty.util;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dragonminez.common.util.TransformationsHelper;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.fml.ModList;

/**
 * DMZ's English names (its {@code en_us.json}, read once from the DMZ jar) so server menus show
 * the same form and group names the radial does instead of raw config ids.
 */
public final class DmzLang {
    private static volatile Map<String, String> names;

    private DmzLang() {}

    /** The English text for {@code key} with color codes removed, or {@code fallback}. */
    public static String text(String key, String fallback) {
        String v = key == null ? null : load().get(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        String plain = UltraStyle.plain(v).trim();
        return plain.isEmpty() ? fallback : plain;
    }

    /** {@code super_saiyan2} reads as {@code Super Saiyan2} when DMZ has no name for it. */
    public static String titleCase(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String word : raw.trim().replace('_', ' ').replace('-', ' ').split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static Map<String, String> load() {
        Map<String, String> cached = names;
        if (cached != null) {
            return cached;
        }
        Map<String, String> out = new HashMap<>();
        // DMZ's own mod file first (Forge modules can hide resources from Class lookups),
        // then the class resource for runs outside Forge.
        try {
            Path p = ModList.get().getModFileById("dragonminez").getFile().findResource(LANG.substring(1));
            if (Files.isRegularFile(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    read(in, out);
                }
            }
        } catch (Throwable ignored) {
        }
        if (out.isEmpty()) {
            try (InputStream in = TransformationsHelper.class.getResourceAsStream(LANG)) {
                if (in != null) {
                    read(in, out);
                }
            } catch (Throwable ignored) {
                // Fallback names only; never block a menu on the lang file.
            }
        }
        names = out;
        return out;
    }

    private static final String LANG = "/assets/dragonminez/lang/en_us.json";

    /** Flat key/value pairs; non-string values (none today) are skipped. */
    private static void read(InputStream in, Map<String, String> out) throws java.io.IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        reader.setLenient(true);
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (reader.peek() == JsonToken.STRING) {
                out.put(name, reader.nextString());
            } else {
                reader.skipValue();
            }
        }
    }
}
