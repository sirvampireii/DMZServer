package com.dmzlegacy.ultimatekey.empires.ultra;

import com.dmzultra.space.SpacePolicy;

/** Server space rules, read from DMZ Ultra's SpacePolicy. Replaces the Noea policy snapshot. */
public final class SpaceRules {
    private static final SpaceRules INSTANCE = new SpaceRules();

    private SpaceRules() {
    }

    public static SpaceRules current() {
        return INSTANCE;
    }

    public boolean deepSpaceEnabled() {
        return UltraLink.present() && SpacePolicy.current().deepSpaceEnabled();
    }

    /** Ultra has no PvP-hakai switch for bounties; the bounty hakai hooks are dormant. */
    public boolean destroyerPvpHakai() {
        return false;
    }
}
