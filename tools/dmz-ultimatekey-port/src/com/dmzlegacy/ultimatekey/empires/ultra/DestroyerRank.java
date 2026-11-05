package com.dmzlegacy.ultimatekey.empires.ultra;

import java.util.Locale;

/**
 * The destroyer ranks the empires layer reads. Same constants and behavior as the Noea rank it
 * replaced; the values come from DMZ Ultra's {@code DestroyerHost.Rank}.
 */
public enum DestroyerRank {
    NONE("Uninitiated"),
    CANDIDATE("Destroyer Candidate"),
    APPRENTICE("God of Destruction Apprentice"),
    DESTROYER("God of Destruction");

    private final String displayName;

    DestroyerRank(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }

    public boolean hasDestructionEnergy() {
        return this == APPRENTICE || this == DESTROYER;
    }

    public static DestroyerRank parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return DestroyerRank.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }

    /** Ultra's rank, by name (the two enums share their constants). */
    static DestroyerRank of(Enum<?> ultraRank) {
        return ultraRank == null ? NONE : parse(ultraRank.name());
    }
}
