package com.dmzlegacy.ultimatekey.empires.ultra;

import com.dmzultra.divine.DestroyerDomains;
import com.dmzultra.divine.path.DestroyerRules;
import com.dmzultra.host.DestroyerHost;
import com.dmzultra.host.Hosts;
import net.minecraft.server.level.ServerPlayer;

/** A player's destroyer office, read from DMZ Ultra. Replaces the Noea role service. */
public final class DestroyerOffice {
    private DestroyerOffice() {
    }

    public static DestroyerRoles.Assignment assignment(ServerPlayer player) {
        if (player == null || !UltraLink.present()) {
            return DestroyerRoles.Assignment.none(player == null ? null : player.m_20148_());
        }
        DestroyerHost host = Hosts.destroyer();
        DestroyerRank rank = DestroyerRank.of(host.rank(player));
        if (rank == DestroyerRank.NONE) {
            return DestroyerRoles.Assignment.none(player.m_20148_());
        }
        int universe = host.universeOf(player);
        String pool = universe > 0 ? DestroyerDomains.pool(universe) : "";
        return new DestroyerRoles.Assignment(player.m_20148_(), DestroyerRoles.bounded(player.m_36316_().getName(), 48),
                pool, rank, "dmzultra", 0L);
    }

    /** Ultra keeps a player's destroyer state in step itself; nothing to do. */
    public static void reconcile(ServerPlayer player) {
    }

    public static int energyCap(DestroyerRank rank) {
        if (!UltraLink.present()) {
            return 0;
        }
        return DestroyerRules.energyCap(DestroyerHost.Rank.valueOf((rank == null ? DestroyerRank.NONE : rank).name()));
    }

    public static boolean canResolveHakaiProjectile(ServerPlayer player) {
        return UltraLink.present() && com.dmzultra.space.shim.DestroyerRoleService.canResolveHakaiProjectile(player);
    }
}
