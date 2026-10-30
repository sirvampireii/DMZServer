package com.dbzlegacy.mohistmelee;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Per-event log lines (repairs, blocked actions, self-test chatter) go through here.
 * <p>
 * Off by default: they log at DEBUG (logs/debug.log only). Set {@code debugLogging=true} in
 * {@code config/dmz_mohist_melee_fix-debug.properties} (re-read every 30 seconds, no restart), or
 * start the JVM with {@code -Ddmz_mohist_melee_fix.debug=true}, to see them at INFO in the console.
 * Startup lines, warnings, and errors never go through here.
 */
public final class MeleeDebug {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    private static final boolean JVM_FLAG = Boolean.getBoolean(DmzMohistMeleeFix.MOD_ID + ".debug");
    private static final String FILE = DmzMohistMeleeFix.MOD_ID + "-debug.properties";
    private static final String KEY = "debugLogging";
    private static final long RELOAD_MS = 30_000L;

    private static volatile boolean fileFlag;
    private static volatile long checkedAt;

    private MeleeDebug() {}

    /** Called once from the mod constructor: writes the default file if it is missing. */
    public static void register() {
        Path path = path();
        if (path == null || Files.exists(path)) {
            return;
        }
        Properties props = new Properties();
        props.setProperty(KEY, "false");
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "true = log every repair, blocked action, and self-test line to the console."
                    + " Re-read every 30 seconds.");
        } catch (IOException e) {
            LOGGER.warn("[{}] could not write {}: {}", DmzMohistMeleeFix.MOD_ID, path, e.toString());
        }
    }

    public static boolean enabled() {
        if (JVM_FLAG) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - checkedAt >= RELOAD_MS) {
            checkedAt = now;
            fileFlag = readFlag();
        }
        return fileFlag;
    }

    /** INFO when the flag is on, DEBUG otherwise. */
    public static void log(String message, Object... args) {
        if (enabled()) {
            LOGGER.info(message, args);
        } else {
            LOGGER.debug(message, args);
        }
    }

    /** Self-test result: a pass is debug chatter, a failure is a real warning. */
    public static void result(boolean pass, String message, Object... args) {
        if (pass) {
            log(message, args);
        } else {
            LOGGER.warn(message, args);
        }
    }

    private static boolean readFlag() {
        Path path = path();
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            return false;
        }
        return Boolean.parseBoolean(props.getProperty(KEY, "false").trim());
    }

    private static Path path() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(FILE);
        } catch (Throwable t) {
            return null;
        }
    }
}
