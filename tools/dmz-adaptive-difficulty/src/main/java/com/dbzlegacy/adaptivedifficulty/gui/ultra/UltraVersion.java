package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Installed DMZ Ultra version, read once from Forge's mod list ({@code dmzultra} mod info
 * version), so the same LM jar can serve Ultra 0.4.0 and 0.5.3 and gate the content each can
 * draw. Ultra absent, an unparseable version, or a failed read all count as {@link #BASELINE}
 * with a warning: LM never assumes newer content than it can prove is installed.
 *
 * <p>Server side only. Ultra's mods.toml declares MATCH_VERSION, so a client that joined runs the
 * same Ultra version as the server and the server's answer stands for every client too. The
 * noscale-patched jar keeps the stock mods.toml, so it reports the stock version (0.5.3).
 */
public final class UltraVersion {
    public static final String MOD_ID = "dmzultra";
    /** What LM assumes when it cannot read a version: the oldest Ultra it supports. */
    public static final String BASELINE = "0.4.0";

    private static final Logger LOG = LogManager.getLogger("legacymechanics");
    private static final Pattern NUMERIC = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)");

    /** Detected version and how it was found. {@code version} is always a dotted number. */
    public record Detection(String version, String raw, boolean installed, String note) {}

    private static volatile Detection detection;
    private static volatile String simulated;

    private UltraVersion() {}

    /** Dotted-numeric installed Ultra version, or {@link #BASELINE} when unknown. */
    public static String detectedVersion() {
        return detection().version();
    }

    /** True when the installed Ultra is {@code version} or newer (dotted-numeric compare). */
    public static boolean atLeast(String version) {
        return compare(detectedVersion(), version) >= 0;
    }

    public static Detection detection() {
        String sim = simulated;
        if (sim != null) {
            String v = normalize(sim);
            return v == null
                    ? new Detection(BASELINE, sim, true, "simulated version '" + sim + "' is not a version; using " + BASELINE)
                    : new Detection(v, sim, true, "simulated");
        }
        Detection d = detection;
        if (d == null) {
            synchronized (UltraVersion.class) {
                if (detection == null) {
                    detection = detect();
                }
                d = detection;
            }
        }
        return d;
    }

    /**
     * Test hook: answer {@code version} instead of the mod list ({@code null} restores the real
     * detection). Used by {@link BodyPartCatalogSelfTest}, which runs without Forge.
     */
    static void simulate(String version) {
        simulated = version;
    }

    /**
     * Dotted-numeric compare: {@code 0.5.3 < 0.5.4 < 0.6.0}, {@code 0.5.10 > 0.5.9}, missing
     * parts count as 0. A pre-release suffix ({@code 0.5.3-beta}) compares as its base version.
     * An unparseable side compares as {@link #BASELINE}.
     */
    public static int compare(String a, String b) {
        int[] x = parts(a);
        int[] y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    /** Leading dotted number of {@code raw} ({@code 0.5.3-beta.1} → {@code 0.5.3}), else null. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = NUMERIC.matcher(raw.trim().toLowerCase(Locale.ROOT));
        return m.find() ? m.group(1) : null;
    }

    private static int[] parts(String version) {
        String v = normalize(version);
        if (v == null) {
            v = BASELINE;
        }
        String[] bits = v.split("\\.");
        int[] out = new int[bits.length];
        for (int i = 0; i < bits.length; i++) {
            try {
                out[i] = Integer.parseInt(bits[i]);
            } catch (NumberFormatException e) {
                out[i] = Integer.MAX_VALUE;
            }
        }
        return out;
    }

    private static Detection detect() {
        try {
            var container = net.minecraftforge.fml.ModList.get().getModContainerById(MOD_ID);
            if (container.isEmpty()) {
                return new Detection(BASELINE, "", false, "DMZ Ultra is not installed; using " + BASELINE + " content");
            }
            // getVersion() returns Maven's ArtifactVersion, which LM does not compile against.
            Object info = container.get().getModInfo();
            String raw = String.valueOf(info.getClass().getMethod("getVersion").invoke(info));
            String v = normalize(raw);
            if (v == null) {
                return new Detection(BASELINE, raw, true,
                        "DMZ Ultra version '" + raw + "' is not a dotted number; using " + BASELINE + " content");
            }
            return new Detection(v, raw, true, "");
        } catch (Throwable t) {
            return new Detection(BASELINE, "", false,
                    "could not read the DMZ Ultra version (" + t.getClass().getSimpleName() + "); using "
                            + BASELINE + " content");
        }
    }

    /** The installed Ultra jar on disk, or null when it is absent or not a single file. */
    static Path installedJar() {
        try {
            var file = net.minecraftforge.fml.ModList.get().getModFileById(MOD_ID);
            return file == null ? null : file.getFile().getFilePath();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * One boot line: detected Ultra version and the content set LM uses for it. A warning when
     * the version could not be read (LM then serves {@link #BASELINE} content).
     */
    public static void logAtBoot() {
        Detection d = detection();
        String catalog = BodyPartCatalog.activeDescription();
        String content = atLeast("0.5.3") ? "0.5.3 content (new races, 115-piece shop)" : "0.4.0 content (109-piece shop)";
        if (d.note().isEmpty()) {
            LOG.info("[legacymechanics] DMZ Ultra {} detected (mod version '{}'): body parts catalog {}; {}",
                    d.version(), d.raw(), catalog, content);
        } else {
            LOG.warn("[legacymechanics] DMZ Ultra version: {}. Body parts catalog {}; {}",
                    d.note(), catalog, content);
        }
    }
}
