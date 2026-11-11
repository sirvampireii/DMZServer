package com.dbzlegacy.adaptivedifficulty.sparring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/**
 * Adds the opposing dojo's online members to DMZ Ultra's ki sense list while a dojo war is
 * active (4.7.47; it used to feed Noea's tracking scan). Same world only, any distance. Kept
 * free of Ultra types: the mixin passes Ultra's own signature builder and visibility check.
 */
public final class DojoWarSense {
    private DojoWarSense() {}

    /**
     * {@code gathered} plus a signature for every war opponent Ultra would let {@code viewer}
     * sense, opponents first. Signatures already present for an opponent are replaced. The list
     * stays within {@code cap} by dropping the farthest non-opponent signatures (Ultra sorts
     * nearest first). Returns {@code gathered} unchanged outside a war or on any error.
     */
    public static <T> List<T> withWarFoes(
            ServerPlayer viewer,
            List<T> gathered,
            Function<T, UUID> key,
            Function<ServerPlayer, T> signature,
            Predicate<ServerPlayer> visible,
            int cap) {
        if (viewer == null || gathered == null) {
            return gathered;
        }
        try {
            List<ServerPlayer> foes = DojoRankings.onlineWarOpponents(viewer);
            List<T> out = new ArrayList<>();
            Set<UUID> foeIds = new HashSet<>();
            for (ServerPlayer foe : foes) {
                if (foe == null || foe == viewer || foe.m_9236_() != viewer.m_9236_() || !visible.test(foe)) {
                    continue;
                }
                T sig = signature.apply(foe);
                if (sig != null && foeIds.add(foe.m_20148_())) {
                    out.add(sig);
                }
            }
            if (out.isEmpty()) {
                return gathered;
            }
            int limit = Math.max(cap, out.size());
            for (T sig : gathered) {
                if (out.size() >= limit) {
                    break;
                }
                if (!foeIds.contains(key.apply(sig))) {
                    out.add(sig);
                }
            }
            return List.copyOf(out);
        } catch (Throwable t) {
            return gathered;
        }
    }
}
