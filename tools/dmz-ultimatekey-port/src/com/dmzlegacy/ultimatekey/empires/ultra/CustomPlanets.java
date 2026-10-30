package com.dmzlegacy.ultimatekey.empires.ultra;

import java.util.List;

/**
 * Admin-defined planets. DMZ Ultra has no public registry for them, so the list is empty and the
 * empires features that offered custom planets stay dormant. Replaces the Noea definition registry.
 */
public final class CustomPlanets {
    private static final Snapshot EMPTY = new Snapshot(List.of());

    private CustomPlanets() {
    }

    public static Snapshot current() {
        return EMPTY;
    }

    public record Snapshot(List<PlanetDefinition> planets) {
    }

    public record PlanetDefinition(long numericId, String displayName, int tier, int bodyKind, int palette, long seed,
                                   double x, double y, double z) {
    }
}
