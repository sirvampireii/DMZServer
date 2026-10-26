package com.dbzlegacy.adaptivedifficulty.progression.combat;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;

/**
 * Caps Viper's Call's right-click venom cloud at {@link #MAX_TICKS} (10 seconds). Simply More
 * spawns it with 360 ticks; on join its duration becomes min(current, 200). Damage and poison
 * are unchanged; reuse stays gated by the item's own cooldown (vipersCallCooldown). Matched by
 * class name, so there is no compile dependency and nothing happens without Simply More.
 */
public final class VipersCallCloudCap {
    public static final int MAX_TICKS = 200;
    static final String CLOUD_CLASS =
            "net.rosemarythyme.simplymore.entity.VipersCallProjectileAreaEffectCloudEntity";

    private VipersCallCloudCap() {}

    /** Call from EntityJoinLevelEvent (server side). */
    public static void onJoin(Entity entity) {
        if (!(entity instanceof AreaEffectCloud cloud) || !CLOUD_CLASS.equals(entity.getClass().getName())) {
            return;
        }
        int duration = cloud.m_19748_(); // getDuration
        if (duration > MAX_TICKS) {
            cloud.m_19734_(MAX_TICKS); // setDuration
        }
    }
}
