package com.dmzlegacy.ultimatekey.empires.ultra;

import com.dmzultra.host.Hosts;
import net.minecraft.server.level.ServerPlayer;

/** Space travel state from DMZ Ultra. Replaces the Noea atmosphere and sonic flight services. */
public final class UltraTravel {
    private UltraTravel() {
    }

    /** True while the player is crossing between a planet and space. */
    public static boolean active(ServerPlayer player) {
        return player != null && UltraLink.present() && Hosts.space().crossing(player);
    }

    /** Ultra has no sonic flight to stop. */
    public static void stop(ServerPlayer player) {
    }

    /** Ultra has no Tournament of Power match; bounties never wait for one. */
    public static boolean isActive(ServerPlayer player) {
        return false;
    }
}
