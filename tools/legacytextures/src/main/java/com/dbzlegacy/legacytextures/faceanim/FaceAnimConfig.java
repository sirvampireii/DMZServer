package com.dbzlegacy.legacytextures.faceanim;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * What this viewer sees: the {@code [faceanim]} section of legacytextures-client.toml. Turning one off
 * only changes this game; other players still see the animation. Both default to on.
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

    /** Before the config file is loaded (or if it cannot be read) the animation stays on. */
    private static boolean read(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (Throwable t) {
            return true;
        }
    }
}
