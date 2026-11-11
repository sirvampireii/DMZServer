package com.dbzlegacy.legacytextures.faceanim;

import com.dbzlegacy.legacytextures.LegacyTextures;
import com.dbzlegacy.legacytextures.client.ClientState;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.extras.ActionMode;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Picks the face frame for the player DMZSkinLayer is drawing (DMZ Face Anim 0.2.0, absorbed). Between
 * {@link #begin} and {@link #end} (DMZSkinLayer.renderFace) {@link #route} swaps an eye or mouth layer
 * path for its blink or scream frame; outside a face, or when nothing is animating, paths pass through.
 *
 * <p>Only HD faces animate (0.2.5). DragonMineZ 2.2.1.2 has no blinking of its own, so a face layer this
 * viewer draws from the mod's own file stays still, as DMZ draws it. A layer whose file resolves to an
 * HD set (in HD mode, or an HD-only file in vanilla mode) blinks and shouts as before.
 */
public final class FaceAnimator {
    /** Draw nothing for this layer (an overlay that has no closed-eye frame). */
    public static final String SKIP = "";
    public static final float[] WHITE = new float[] {1.0F, 1.0F, 1.0F};
    private static final Pattern FACE = Pattern.compile(
            "(textures/entity/races/(?:humansaiyan|namekian|frostdemon|majin|bioandroid|angel|glind)/faces/)([^/]+)\\.png");
    private static final Pattern EYE = Pattern.compile("[a-z0-9_]+_eye_\\d+_\\d+|[a-z]+_eye_layer\\d+|janemba_eye_\\d+");
    private static final Pattern MOUTH = Pattern.compile("(?!frostdemon_fifth|janemba)[a-z0-9_]+_mouth(?:_\\d+)?");
    private static final String SCREAM = "anim/mouth_scream_";
    private static final int BLINK_CYCLE_TICKS = 90;
    private static final int BLINK_TICKS = 4;
    private static final int SCREAM_FULL_LEVEL = 3;
    private static final int SCREAM_MAX_LEVEL = 4;
    private static final int EYES_OPEN = 0;
    private static final int EYES_HALF = 1;
    private static final int EYES_CLOSED = 2;
    private static final Map<AbstractClientPlayer, ScreamState> SCREAMS = new WeakHashMap<>();
    private static boolean active;
    private static int eyes;
    private static int mouthFrame;
    private static boolean loggedHook;
    private static boolean loggedGate;

    private FaceAnimator() {
    }

    public static void begin(AbstractClientPlayer player, StatsData stats, float partialTick) {
        if (!loggedHook) {
            loggedHook = true;
            LegacyTextures.LOG.info("face animation hook active in DMZSkinLayer");
        }
        // The player's own switch (/faceanim, server-stored) and this viewer's [faceanim] settings.
        boolean subject = FaceFlags.enabled(player.m_20148_());
        boolean blink = subject && FaceAnimConfig.blink();
        boolean scream = subject && FaceAnimConfig.scream();
        int level = updateScream(player, scream && wantsScream(stats));
        mouthFrame = !scream || level == 0 ? 0 : (level < SCREAM_FULL_LEVEL ? 1 : 2 + player.f_19797_ / 2 % 2);
        eyes = level > 0 || !blink ? EYES_OPEN : blinkFrame(player, (float) player.f_19797_ + partialTick);
        active = eyes != EYES_OPEN || mouthFrame != 0;
    }

    public static void end() {
        active = false;
    }

    private static boolean wantsScream(StatsData stats) {
        Status status = stats.getStatus();
        if (status == null) {
            return false;
        }
        boolean transforming = status.isActionCharging() && status.getSelectedAction() != ActionMode.FUSION;
        return status.isChargingKi() || transforming;
    }

    private static int updateScream(AbstractClientPlayer player, boolean screaming) {
        ScreamState state = SCREAMS.computeIfAbsent(player, p -> new ScreamState());
        int elapsed = player.f_19797_ - state.lastTick;
        if (elapsed != 0) {
            state.lastTick = player.f_19797_;
            int step = screaming ? Math.abs(elapsed) : -Math.abs(elapsed);
            state.level = Math.max(0, Math.min(SCREAM_MAX_LEVEL, state.level + step));
        }
        return state.level;
    }

    /** Open most of the 90-tick cycle; half, closed, closed, half for 4 ticks at a per-player offset. */
    private static int blinkFrame(AbstractClientPlayer player, float time) {
        int cycle = (int) (time / BLINK_CYCLE_TICKS);
        int seed = player.m_20148_().hashCode() * 31 + cycle * -1640531527;
        int offset = Math.floorMod(seed ^ seed >>> 15, BLINK_CYCLE_TICKS - BLINK_TICKS);
        float progress = time - (float) (cycle * BLINK_CYCLE_TICKS) - (float) offset;
        if (progress < 0.0F || progress >= BLINK_TICKS) {
            return EYES_OPEN;
        }
        return progress >= 1.0F && progress < 3.0F ? EYES_CLOSED : EYES_HALF;
    }

    /** The layer path to draw instead of {@code path}, {@code path} itself, or {@link #SKIP}. */
    public static String route(String path) {
        if (!active) {
            return path;
        }
        Matcher face = FACE.matcher(path);
        if (!face.matches()) {
            return path;
        }
        String routed = frame(path, face.group(1), face.group(2));
        return routed == path || isHd(path) ? routed : path;
    }

    private static String frame(String path, String folder, String stem) {
        if (eyes != EYES_OPEN) {
            if (stem.startsWith("ssj4_eyes_") || stem.startsWith("shiyoken_eye")) {
                return SKIP;
            }
            if (EYE.matcher(stem).matches()) {
                return folder + "anim/" + stem + (eyes == EYES_HALF ? "_half.png" : "_closed.png");
            }
        }
        return mouthFrame != 0 && MOUTH.matcher(stem).matches() ? folder + SCREAM + mouthFrame + ".png" : path;
    }

    /** Whether this viewer draws {@code dragonminez:<path>} from an HD set; a failure leaves the face still. */
    private static boolean isHd(String path) {
        try {
            ResourceLocation loc = ResourceLocation.m_214293_("dragonminez", path);
            return !ClientState.RESOLVER.variant(loc).equals(loc);
        } catch (Throwable t) {
            if (!loggedGate) {
                loggedGate = true;
                LegacyTextures.LOG.warn("face animation HD check failed; faces stay still: {}", t.toString());
            }
            return false;
        }
    }

    public static boolean hideShadow(String featurePath) {
        return route(featurePath) != featurePath;
    }

    public static boolean isScreamMouth(String routed) {
        return routed.contains(SCREAM);
    }

    private static final class ScreamState {
        int lastTick;
        int level;
    }
}
