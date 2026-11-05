package com.dbzlegacy.legacytextures.server;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.TextureMode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * config/legacytextures/server.json: what a player sees before they pick anything.
 * Written with the defaults on first start; read once per server start.
 */
public final class ServerSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ServerSettings current;

    /** ALL_HD, PLAYERS_HD_ONLY or ALL_VANILLA. */
    public String defaultPreset = TextureMode.Preset.ALL_VANILLA.name();
    /** The HD set the switches use until a player picks another one. */
    public String defaultSet = TextureMode.DEFAULT_SET;

    public TextureMode defaultMode() {
        TextureMode.Preset p = TextureMode.Preset.parse(defaultPreset);
        return TextureMode.of(p != null ? p : TextureMode.Preset.ALL_VANILLA, defaultSet);
    }

    public static synchronized ServerSettings get() {
        if (current == null) {
            current = load(FMLPaths.CONFIGDIR.get().resolve(LegacyTextures.MODID).resolve("server.json"));
        }
        return current;
    }

    public static synchronized void reload() {
        current = null;
        get();
    }

    private static ServerSettings load(Path file) {
        ServerSettings s = null;
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                s = GSON.fromJson(r, ServerSettings.class);
            } catch (Exception e) {
                LegacyTextures.LOG.warn("{} is not valid JSON ({}); using the defaults", file, e.getMessage());
            }
        }
        if (s == null) {
            s = new ServerSettings();
            try {
                Files.createDirectories(file.getParent());
                if (!Files.exists(file)) {
                    try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                        GSON.toJson(s, w);
                    }
                }
            } catch (Exception e) {
                LegacyTextures.LOG.warn("could not write {}: {}", file, e.getMessage());
            }
        }
        if (TextureMode.Preset.parse(s.defaultPreset) == null) {
            LegacyTextures.LOG.warn("server.json defaultPreset '{}' is not ALL_HD, PLAYERS_HD_ONLY or ALL_VANILLA; "
                    + "using ALL_VANILLA", s.defaultPreset);
            s.defaultPreset = TextureMode.Preset.ALL_VANILLA.name();
        }
        s.defaultSet = TextureMode.cleanSetId(s.defaultSet);
        return s;
    }
}
