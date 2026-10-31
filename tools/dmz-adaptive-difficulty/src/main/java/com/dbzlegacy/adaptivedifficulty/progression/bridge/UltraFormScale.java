package com.dbzlegacy.adaptivedifficulty.progression.bridge;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraftforge.fml.ModList;

/**
 * Turns off DMZ Ultra's Overhaul form scaling (4.7.4).
 * <p>
 * DMZ Ultra 0.4.0 multiplies its own forms' multipliers by a per-race factor whenever dmzrevamp is
 * loaded ({@code OverhaulScale.scaled}, through its {@code FormDataScaleMixin}), so SSJ5's configured
 * 30x shows as about 112x mastered. Ultra has no setting for it. With
 * {@code disableUltraFormScaling} on (the default), LM calls {@code OverhaulScale.simulate(false)}:
 * {@code active()} then reads false and {@code scaled()} returns the configured value for every form
 * and race.
 * <p>
 * It runs once the server has started, after Ultra's boot self-test ({@code FormsChecks}, which checks
 * the scaling and sets {@code simulate(null)} when it finishes). A pulse re-applies it if the scale
 * reads active again, and gives the scale back to Ultra if the flag is turned off and the config
 * reloaded. Reflection only, with no compile dependency: if Ultra renames or removes the class, LM logs
 * a warning, keeps booting, and the scale stays on.
 */
public final class UltraFormScale {
    static final String CLASS = "com.dmzultra.form.OverhaulScale";
    /** Pulse period: every 10 seconds. */
    private static final int CHECK_TICKS = 200;

    private static volatile Method active;
    private static volatile Method simulate;
    /** LM set the scale off and keeps it off. */
    private static volatile boolean holding;
    /** A failed lookup was already logged; the pulse stops trying. */
    private static volatile boolean unavailable;

    private UltraFormScale() {}

    /** Server started: apply the config flag and log what happened. */
    public static void onServerStarted() {
        holding = false;
        unavailable = false;
        if (!DifficultyConfig.get().disableUltraFormScaling) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] DMZ Ultra form scaling left on: disableUltraFormScaling is false",
                    AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        if (!lookup()) {
            return;
        }
        try {
            boolean wasActive = (Boolean) active.invoke(null);
            simulate.invoke(null, Boolean.FALSE);
            holding = true;
            if ((Boolean) active.invoke(null)) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] DMZ Ultra form scaling still reads on after disabling it; forms may stay inflated",
                        AdaptiveDifficultyMod.MOD_ID);
            } else {
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] DMZ Ultra form scaling disabled{}: forms use their configured multipliers "
                                + "(set disableUltraFormScaling false to turn it back on)",
                        AdaptiveDifficultyMod.MOD_ID, wasActive ? "" : " (it was not active)");
            }
        } catch (Throwable t) {
            unavailable = true;
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] DMZ Ultra form scaling could not be disabled, so it stays on: {}",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    /** Server tick: keep the scale off while the flag is on; hand it back when the flag is turned off. */
    public static void pulse(int tick) {
        if (tick % CHECK_TICKS != 0 || unavailable) {
            return;
        }
        boolean wanted = DifficultyConfig.get().disableUltraFormScaling;
        if (!wanted && !holding) {
            return;
        }
        if (!lookup()) {
            return;
        }
        try {
            if (!wanted) {
                simulate.invoke(null, (Object) null);
                holding = false;
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] DMZ Ultra form scaling handed back to Ultra: disableUltraFormScaling is false",
                        AdaptiveDifficultyMod.MOD_ID);
                return;
            }
            if (!holding) {
                simulate.invoke(null, Boolean.FALSE);
                holding = true;
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] DMZ Ultra form scaling disabled: disableUltraFormScaling is true",
                        AdaptiveDifficultyMod.MOD_ID);
            } else if ((Boolean) active.invoke(null)) {
                simulate.invoke(null, Boolean.FALSE);
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] DMZ Ultra form scaling read on again (an Ultra check or reload); disabled it again",
                        AdaptiveDifficultyMod.MOD_ID);
            }
        } catch (Throwable t) {
            unavailable = true;
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] DMZ Ultra form scaling check failed and stops: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    /** Find {@code OverhaulScale.active()} and {@code simulate(Boolean)}; log once when they are missing. */
    private static boolean lookup() {
        if (active != null && simulate != null) {
            return true;
        }
        if (unavailable) {
            return false;
        }
        Method[] found = find(CLASS, UltraFormScale.class.getClassLoader());
        if (found == null) {
            unavailable = true;
            boolean ultra = false;
            try {
                ultra = ModList.get().isLoaded("dmzultra");
            } catch (Throwable ignored) {
            }
            if (ultra) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] DMZ Ultra form scaling left on: {}.active()/simulate(Boolean) not found "
                                + "(Ultra changed it); forms may show inflated multipliers",
                        AdaptiveDifficultyMod.MOD_ID, CLASS);
            } else {
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] DMZ Ultra form scaling: Ultra is not installed, nothing to disable",
                        AdaptiveDifficultyMod.MOD_ID);
            }
            return false;
        }
        active = found[0];
        simulate = found[1];
        return true;
    }

    /** {@code [active, simulate]} on the named class, or null when either is missing. */
    static Method[] find(String className, ClassLoader loader) {
        try {
            Class<?> scale = Class.forName(className, true, loader);
            Method a = scale.getMethod("active");
            Method s = scale.getMethod("simulate", Boolean.class);
            if ((a.getReturnType() != boolean.class && a.getReturnType() != Boolean.class)
                    || !java.lang.reflect.Modifier.isStatic(a.getModifiers())
                    || !java.lang.reflect.Modifier.isStatic(s.getModifiers())) {
                return null;
            }
            return new Method[] {a, s};
        } catch (Throwable absent) {
            return null;
        }
    }

    /** Startup self-test: a missing class is reported as missing, never thrown. */
    public static void selfTest(List<String> broken) {
        if (find("com.dbzlegacy.adaptivedifficulty.missing.OverhaulScale", UltraFormScale.class.getClassLoader())
                != null) {
            broken.add("Ultra form scaling: a missing OverhaulScale class should be found as missing");
        }
    }
}
