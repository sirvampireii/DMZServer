package com.dbzlegacy.mohistmelee;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mohist + DMZ M1 fix without damage redirects:
 * <ul>
 *   <li>Repair collapsed Forge ENTITY_REACH</li>
 *   <li>Clear stale strikeLocked that eats CombatAttackRequest packets</li>
 *   <li>Leave vanilla ServerPlayer.attack intact (NPCs die normally)</li>
 * </ul>
 */
@Mod(DmzMohistMeleeFix.MOD_ID)
public final class DmzMohistMeleeFix {
    public static final String MOD_ID = "dmz_mohist_melee_fix";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public DmzMohistMeleeFix() {
        LOGGER.info(
                "[{}] v3.0.1 for DragonMineZ 2.2: party guard on the 2.2 party API, melee keeps DMZ windup, no Shurui/SDU mixins;"
                        + " per-event logs need debugLogging=true in config/dmz_mohist_melee_fix-debug.properties",
                MOD_ID
        );
        MeleeDebug.register();
        ReachRepairEvents.register();
        PersonalSagaEvents.register();
        StatsResetCommands.register();
        MeleeFixSelfTest.registerIfEnabled();
    }
}
