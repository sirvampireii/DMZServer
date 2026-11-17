package com.dmzultra.multiverse;

import com.dmzultra.DMZUltra;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Compile-only stand-in for DMZ Ultra 0.5.7's {@code PodVoyage}. Only {@link #podfixLeftPod} and the
 * {@link #PODFIX_HELD} field are taken from the compiled class (PodFixPatch moves them into the real
 * PodVoyage); everything else here exists so they compile with the real names and descriptors, and is
 * never shipped. verify.py checks that every member they reference exists in the real class.
 *
 * <p>SRG names: m_6084_ isAlive, m_9236_ level, m_46472_ dimension, m_284548_ serverLevel, m_8791_
 * getEntity(UUID), m_20256_ setDeltaMovement, f_82478_ Vec3.ZERO, m_20202_ getVehicle, m_146870_ discard,
 * m_213877_ isRemoved, m_20148_ getUUID, m_36316_ getGameProfile.
 */
public final class PodVoyage {
    /** podfix: per player, the start tick of the port voyage last logged as going on without its rider. */
    private static Map<UUID, Long> PODFIX_HELD;

    private PodVoyage() {}

    public static int launchTicks(int mode) {
        throw new AssertionError();
    }

    public static int flightTicks(int mode, boolean far) {
        throw new AssertionError();
    }

    private static void trip(ServerPlayer player, Voyage voyage, long now) {
        throw new AssertionError();
    }

    private static void cancel(ServerPlayer player, String why) {
        throw new AssertionError();
    }

    /**
     * podfix: what {@code advance} does, before the trip, when the player is not alive in their own pod in
     * the world they left from (stock: {@code cancel(player, "they left the pod")} every time).
     *
     * <p>A voyage to one of Ultra's ports (Kanassa, Arlia and the other canon worlds; {@code voyage.port})
     * keeps going while the player is alive and still in the world they left from: their pod is held still,
     * the flight runs out (or a skip ends it) and the landing takes them, mounted or not, exactly as the
     * stock trip does. When they are set down on the new world the pod they stepped out of is removed, as
     * the stock landing removes the pod they ride. Re-boarding it before then resumes the stock path.
     *
     * <p>Everything else is the stock cancel, word for word: a voyage that is not to a port (DragonMineZ'
     * destinations and Ultra's ground landings), a dead player, or one who is no longer in the world they
     * left from.
     */
    private static void podfixLeftPod(ServerPlayer player, Voyage voyage, long now) {
        if (voyage.port == null || !player.m_6084_() || !player.m_9236_().m_46472_().equals(voyage.from)) {
            cancel(player, "they left the pod");
            return;
        }
        ServerLevel origin = player.m_284548_();
        Entity source = origin.m_8791_(voyage.sourcePod);
        if (source != null) {
            source.m_20256_(Vec3.f_82478_);
        }
        if (PODFIX_HELD == null) {
            PODFIX_HELD = new HashMap<>();
        }
        UUID id = player.m_20148_();
        Long logged = PODFIX_HELD.get(id);
        if (logged == null || logged != voyage.start) {
            PODFIX_HELD.put(id, voyage.start);
            DMZUltra.LOGGER.info("[PodVoyage] {} left the pod on the way to '{}'; the voyage goes on and lands them there.",
                    player.m_36316_().getName(), voyage.destination);
        }
        if (now - voyage.start >= (long) (launchTicks(voyage.mode) + flightTicks(voyage.mode, voyage.far)) || voyage.skippedFlight) {
            trip(player, voyage, now);
            if (player.m_9236_().m_46472_().equals(voyage.to)) {
                PODFIX_HELD.remove(id);
                if (source != null && !source.m_213877_() && player.m_20202_() != source) {
                    source.m_146870_();
                }
            }
        }
    }

    public interface Port {}

    /** Same field names and types as the real Voyage; not final here so javac reads them (no constants). */
    private static final class Voyage {
        String destination;
        ResourceKey<Level> from;
        ResourceKey<Level> to;
        UUID sourcePod;
        long start;
        int mode;
        boolean far;
        long tripped;
        boolean skippedFlight;
        Port port;
    }
}
