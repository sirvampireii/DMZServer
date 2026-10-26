package com.dbzlegacy.adaptivedifficulty.rival;

import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Spectate an active rival challenge — live score pulse every 2s.
 */
public final class RivalSpectator {
    private static final long PULSE_MS = 2000L;
    private static final long DEFAULT_DURATION_MS = 10L * 60L * 1000L;
    private static final Map<UUID, SpecState> ACTIVE = new ConcurrentHashMap<>();

    private RivalSpectator() {}

    public static String start(ServerPlayer spectator, ServerPlayer target) {
        if (spectator == null) {
            return "§cInvalid spectator.";
        }
        RivalChallenge ch = null;
        if (target != null) {
            ch = RivalChallengeManager.get().getChallenge(target.m_20148_());
        }
        if (ch == null) {
            // pick any active challenge
            ch = RivalChallengeManager.get().anyActive();
        }
        if (ch == null || ch.status == RivalChallenge.Phase.ENDED) {
            return "§cNo active rival challenge to spectate.";
        }
        SpecState st = new SpecState();
        st.sessionId = ch.id;
        st.until = System.currentTimeMillis() + DEFAULT_DURATION_MS;
        st.lastPulse = 0L;
        ACTIVE.put(spectator.m_20148_(), st);
        // Menu-first: the confirmation is the return value (the menu paints it; /rival chats it).
        return com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.GREEN + "Watching "
                + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.WHITE + ch.nameA
                + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.GRAY + " vs "
                + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.WHITE + ch.nameB
                + "\n" + com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.GRAY
                + "Tap Stop spectate (or /rival spectate stop) to end early.";
    }

    public static String stop(ServerPlayer spectator) {
        if (spectator == null) {
            return "§cInvalid.";
        }
        SpecState removed = ACTIVE.remove(spectator.m_20148_());
        if (removed == null) {
            return "§7Not spectating.";
        }
        return "§7Spectate ended.";
    }

    public static void clear(UUID uuid) {
        if (uuid != null) {
            ACTIVE.remove(uuid);
        }
    }

    public static void pulse(MinecraftServer server, long now) {
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, SpecState> e : ACTIVE.entrySet()) {
            SpecState st = e.getValue();
            if (st == null) {
                continue;
            }
            ServerPlayer spectator = server.m_6846_().m_11259_(e.getKey());
            if (spectator == null) {
                ACTIVE.remove(e.getKey());
                continue;
            }
            if (now > st.until) {
                ACTIVE.remove(e.getKey());
                DmzRewards.msg(spectator, "§7Spectate ended.");
                continue;
            }
            if (now - st.lastPulse < PULSE_MS) {
                continue;
            }
            st.lastPulse = now;
            RivalChallenge ch = RivalChallengeManager.get().byId(st.sessionId);
            if (ch == null || ch.status == RivalChallenge.Phase.ENDED) {
                ACTIVE.remove(e.getKey());
                DmzRewards.msg(spectator, "§eBattle ended.");
                continue;
            }
            long left = 0L;
            if (ch.status == RivalChallenge.Phase.ACTIVE && ch.endsAt > 0L) {
                left = Math.max(0L, (ch.endsAt - now) / 1000L);
            }
            String state = ch.status == RivalChallenge.Phase.ACTIVE
                    ? (" (" + left + "s)")
                    : (" [" + ch.status.name().toLowerCase() + "]");
            DmzRewards.msg(spectator, "§8[Spec] §f" + ch.nameA + " §7" + (int) ch.damageA
                    + " §8vs §f" + ch.nameB + " §7" + (int) ch.damageB + "§7" + state);
        }
    }

    private static final class SpecState {
        String sessionId;
        long until;
        long lastPulse;
    }
}
