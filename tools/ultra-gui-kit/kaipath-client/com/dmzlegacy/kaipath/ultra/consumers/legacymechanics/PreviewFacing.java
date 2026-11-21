package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

/**
 * Menu model preview facing ({@code layoutJson.playerModel.rotate}), the contract LM's own client renderer
 * follows since LM 4.7.60:
 *
 * <ul>
 *   <li>{@code rotate true}: the figure turns; a new packet that still says {@code true} keeps turning from
 *       where it was.</li>
 *   <li>{@code rotate false}: the figure is front-facing, the character's front toward the viewer, never the
 *       back and never a stale angle from before the spin stopped. A packet with {@code rotate false} resets
 *       the turntable, so a later spin starts from the front too.</li>
 * </ul>
 *
 * <p>Pure math; {@link LmDynamicScreen} feeds the look offsets to the vanilla follows-mouse entity renderer, where
 * a zero offset is the front.
 */
public final class PreviewFacing {
    private PreviewFacing() {}

    /** Horizontal look swing while turning, in the follows-mouse renderer's units. */
    public static final float SWING = 40f;
    /** Slight downward look while turning. */
    public static final float TILT = -20f;

    /** Turntable angle kept when a menu packet arrives. */
    public static float afterPacket(float degrees, boolean rotate) {
        return rotate ? degrees : 0f;
    }

    /** Horizontal look offset: 0 (the front) when still. */
    public static float lookX(boolean rotate, float degrees) {
        return rotate ? (float) Math.sin(Math.toRadians(degrees)) * SWING : 0f;
    }

    /** Vertical look offset: 0 (level, facing the viewer) when still. */
    public static float lookY(boolean rotate) {
        return rotate ? TILT : 0f;
    }
}
