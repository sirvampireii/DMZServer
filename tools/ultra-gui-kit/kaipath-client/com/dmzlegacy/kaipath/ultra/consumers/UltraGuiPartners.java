package com.dmzlegacy.kaipath.ultra.consumers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Partner <em>server</em> mod ids that own an Ultra GUI channel ({@code <modid>:gui}).
 *
 * <p>Wire codecs and {@code LmDynamicScreen} are <b>shared</b> — they do not embed a mod id.
 * KaiPath registers one client consumer per partner so clicks return on the same channel
 * that opened the screen.
 *
 * <p>Partners are discovered. A new mod does not need a KaiPath release:
 *
 * <ol>
 *   <li>Ship {@code META-INF/kaipath-ultra.txt} in the mod jar. The first non-comment line
 *       is the Forge mod id. An empty marker falls back to {@code modId} in {@code mods.toml}.
 *   <li>Or list extra ids in {@code config/kaipath-ultra-partners.txt} (one id per line).
 * </ol>
 *
 * {@link #DEFAULT_PARTNERS} is only the built-in seed ({@code legacymechanics}).
 *
 * <p>Server-only partners (no jar on the client) need neither: the server announces them on
 * {@code kaipath:partners} at login ({@link UltraGuiAnnounce}).
 *
 * <p>A partner that is <b>also loaded in this game</b> (installed on both sides, or played in
 * singleplayer) is not registered here eagerly: its own code may own {@code <modid>:gui} in this JVM.
 * It links ({@code UltraGuiLink} in the server kit), or {@link #registerLocalPartners()} registers
 * it at client setup if nothing else did.
 */
public final class UltraGuiPartners {
    /** Marker inside a partner jar. First non-comment line is the mod id. */
    public static final String MARKER_ENTRY = "META-INF/kaipath-ultra.txt";
    /** Optional client config: one Forge mod id per line. {@code #} starts a comment. */
    public static final String CONFIG_FILE_NAME = "kaipath-ultra-partners.txt";

    /**
     * Built-in seed. Discovery always includes these, then jars and config.
     * Keep lowercase Forge mod ids (channel namespace).
     */
    public static final String[] DEFAULT_PARTNERS = {
        "legacymechanics",
    };

    private UltraGuiPartners() {}

    /** Register every {@link #DEFAULT_PARTNERS} id (idempotent). */
    public static void registerDefaults() {
        register(DEFAULT_PARTNERS);
    }

    /**
     * Register {@link #DEFAULT_PARTNERS}, ids from {@code config/kaipath-ultra-partners.txt},
     * and every mod jar under {@code mods/} that contains {@link #MARKER_ENTRY}.
     */
    public static List<String> discoverAndRegister() {
        Path game = gameDir();
        LinkedHashSet<String> found = new LinkedHashSet<>(discoverIds(
                game.resolve("config").resolve(CONFIG_FILE_NAME),
                game.resolve("mods")));
        scanLoadedMods(found);
        List<String> ids = List.copyOf(found);
        for (String id : ids) {
            if (isLoadedLocally(id)) {
                LOCAL.add(id);
            } else {
                register(id);
            }
        }
        UltraGuiAnnounce.register();
        return ids;
    }

    /** Partners loaded in this game that did not link: registered once mods have registered theirs. */
    private static final Set<String> LOCAL = new LinkedHashSet<>();

    /**
     * Call from {@code FMLClientSetupEvent}: registers {@code <modid>:gui} for discovered partners
     * that are loaded in this game and did not link. Runs after every mod constructor and common
     * setup, so a partner that registered its own channel keeps it (KaiPath logs and skips) instead
     * of crashing the game.
     */
    public static void registerLocalPartners() {
        for (String id : List.copyOf(LOCAL)) {
            if (!UltraGuiConsumerRegistry.isRegistered(id + ":" + UltraGuiConsumerRegistry.CHANNEL_PATH)) {
                register(id);
            }
        }
    }

    /** Whether Forge loaded a mod with this id in this game (reflection: no Forge compile dependency). */
    static boolean isLoadedLocally(String modId) {
        try {
            Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
            Object list = modList.getMethod("get").invoke(null);
            return list != null && (Boolean) modList.getMethod("isLoaded", String.class).invoke(list, modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Markers in every mod file Forge loaded, including jar-in-jar mods and dev workspaces that a
     * scan of {@code mods/} misses. Reflection: no Forge compile dependency.
     */
    static void scanLoadedMods(Set<String> ids) {
        try {
            Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
            Object list = modList.getMethod("get").invoke(null);
            if (list == null) {
                return;
            }
            for (Object info : (List<?>) modList.getMethod("getModFiles").invoke(list)) {
                Object file = info.getClass().getMethod("getFile").invoke(info);
                Object res = file.getClass().getMethod("findResource", String[].class)
                        .invoke(file, (Object) new String[] {"META-INF", "kaipath-ultra.txt"});
                if (!(res instanceof Path marker) || !Files.isRegularFile(marker)) {
                    continue;
                }
                String id = firstModIdLine(Files.readString(marker, StandardCharsets.UTF_8));
                if (id.isEmpty()) {
                    List<?> mods = (List<?>) info.getClass().getMethod("getMods").invoke(info);
                    if (!mods.isEmpty()) {
                        id = sanitizeModId(String.valueOf(mods.get(0).getClass().getMethod("getModId").invoke(mods.get(0))));
                    }
                }
                addId(ids, id);
            }
        } catch (Throwable ignored) {
            // Forge absent or API changed: the mods/ scan above still ran.
        }
    }

    /**
     * Pure discovery (no Forge channel registration). Always includes {@link #DEFAULT_PARTNERS}.
     * Missing files are skipped.
     */
    public static List<String> discoverIds(Path configFile, Path modsDir) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String seed : DEFAULT_PARTNERS) {
            addId(ids, seed);
        }
        readListFile(configFile, ids);
        scanModsDir(modsDir, ids);
        return List.copyOf(ids);
    }

    static void addId(Set<String> ids, String raw) {
        String id = sanitizeModId(raw);
        if (!id.isEmpty()) {
            ids.add(id);
        }
    }

    /** Lowercase Forge mod id, or empty when the token is not a mod id. */
    public static String sanitizeModId(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.length() > 64) {
            return "";
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return "";
            }
        }
        return s;
    }

    /** First non-comment line of a marker or config file. */
    public static String firstModIdLine(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int hash = t.indexOf('#');
            if (hash >= 0) {
                t = t.substring(0, hash).trim();
            }
            return sanitizeModId(t);
        }
        return "";
    }

    static String modIdFromModsToml(String toml) {
        if (toml == null || toml.isEmpty()) {
            return "";
        }
        for (String line : toml.split("\\R")) {
            String t = line.trim();
            if (!t.startsWith("modId")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String value = t.substring(eq + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            String id = sanitizeModId(value);
            if (!id.isEmpty()) {
                return id;
            }
        }
        return "";
    }

    private static void readListFile(Path file, Set<String> ids) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (String line : text.split("\\R")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                int hash = t.indexOf('#');
                if (hash >= 0) {
                    t = t.substring(0, hash).trim();
                }
                addId(ids, t);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void scanModsDir(Path modsDir, Set<String> ids) {
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return;
        }
        try (var stream = Files.list(modsDir)) {
            stream.filter(p -> {
                String name = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
                return Files.isRegularFile(p) && name.endsWith(".jar");
            }).forEach(jar -> readPartnerJar(jar, ids));
        } catch (Throwable ignored) {
        }
    }

    private static void readPartnerJar(Path jar, Set<String> ids) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry marker = zip.getEntry(MARKER_ENTRY);
            if (marker == null) {
                return;
            }
            String markerText = readZip(zip, marker);
            String id = firstModIdLine(markerText);
            if (id.isEmpty()) {
                ZipEntry toml = zip.getEntry("META-INF/mods.toml");
                if (toml != null) {
                    id = modIdFromModsToml(readZip(zip, toml));
                }
            }
            addId(ids, id);
        } catch (Throwable ignored) {
        }
    }

    private static String readZip(ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Path gameDir() {
        try {
            Class<?> paths = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object gamedir = paths.getField("GAMEDIR").get(null);
            Object value = gamedir.getClass().getMethod("get").invoke(gamedir);
            if (value instanceof Path path) {
                return path;
            }
        } catch (Throwable ignored) {
        }
        return Path.of(System.getProperty("user.dir", "."));
    }

    /** Register one or more partner mod ids. Blank entries are skipped. */
    public static void register(String... modIds) {
        if (modIds == null) {
            return;
        }
        for (String modId : modIds) {
            if (modId == null || modId.isBlank()) {
                continue;
            }
            UltraGuiConsumerRegistry.registerClientChannel(modId.trim().toLowerCase());
        }
    }
}
