package com.dbzlegacy.legacytextures.faceanim;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * What this viewer sees: the {@code [faceanim]} section of legacytextures-client.toml, also switched from
 * the texture menu. Turning one off only changes this game; other players still see the animation. Both
 * default to on.
 */
public final class FaceAnimConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue BLINK;
    private static final ForgeConfigSpec.BooleanValue SCREAM;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Face animation on DragonMineZ faces, for what you see. Other players are not affected.")
                .push("faceanim");
        BLINK = b.comment("Eyes blink every few seconds.").define("enableBlink", true);
        SCREAM = b.comment("The mouth opens into a shout while a player charges ki or charges an action.")
                .define("enableScream", true);
        b.pop();
        SPEC = b.build();
    }

    private FaceAnimConfig() {
    }

    public static boolean blink() {
        return read(BLINK);
    }

    public static boolean scream() {
        return read(SCREAM);
    }

    /** The menu's "See blinking" switch (0.2.4): used from the next frame and saved to the file. */
    public static void setBlink(boolean on) {
        write(BLINK, on);
    }

    /** The menu's "See shouting" switch (0.2.4): used from the next frame and saved to the file. */
    public static void setScream(boolean on) {
        write(SCREAM, on);
    }

    /** Before the config file is loaded there is nothing to write to; the click then does nothing. */
    private static void write(ForgeConfigSpec.BooleanValue value, boolean on) {
        try {
            value.set(on);
            value.save();
        } catch (Throwable ignored) {
            // keep the menu working; the switch shows the value actually in use
        }
    }

    /** Before the config file is loaded (or if it cannot be read) the animation stays on. */
    private static boolean read(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (Throwable t) {
            return true;
        }
    }
}
