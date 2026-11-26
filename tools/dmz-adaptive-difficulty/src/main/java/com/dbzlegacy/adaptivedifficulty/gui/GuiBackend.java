package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;

/**
 * Which player UI {@code /lm} and subcommands use.
 * {@link #ULTRA} is the only GUI backend: the Legacy Mechanics client's own menus. LM 4.8.2: it no longer
 * means KaiPath (the KaiPath bridge is gone; the LM client is mandatory). The name stays so existing
 * configs keep loading; {@code cnpc} / {@code chat} config values migrate to {@link #ULTRA} on load.
 */
public enum GuiBackend {
    /** @deprecated Retained for config migration only; {@link #fromConfig()} always yields ULTRA. */
    @Deprecated
    CHAT,
    /** @deprecated Retained for config migration only; {@link #fromConfig()} always yields ULTRA. */
    @Deprecated
    CNPC,
    ULTRA;

    /** Default and only active backend. */
    public static final GuiBackend DEFAULT = ULTRA;

    /**
     * Always {@link #ULTRA}. Legacy {@code cnpc}/{@code chat} values are coerced and logged once
     * at config load; this method never returns a CNPC fallback path.
     */
    public static GuiBackend fromConfig() {
        String raw = DifficultyConfig.get().guiBackend;
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String key = raw.trim().toLowerCase();
        return switch (key) {
            case "ultra", "kaipath", "dmzultra", "native" -> ULTRA;
            case "cnpc", "customnpcs", "customnpc", "noppes", "chat", "cmi", "chest" -> {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] guiBackend \"{}\" is retired — Ultra only; using ultra",
                        AdaptiveDifficultyMod.MOD_ID,
                        raw.trim());
                yield ULTRA;
            }
            default -> {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] unrecognized guiBackend \"{}\"; using {}",
                        AdaptiveDifficultyMod.MOD_ID,
                        raw.trim(),
                        DEFAULT.name().toLowerCase());
                yield DEFAULT;
            }
        };
    }
}
