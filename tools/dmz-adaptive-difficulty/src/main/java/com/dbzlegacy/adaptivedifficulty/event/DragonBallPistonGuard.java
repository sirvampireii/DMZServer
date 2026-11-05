package com.dbzlegacy.adaptivedifficulty.event;

import com.dragonminez.common.init.block.custom.DragonBallBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraftforge.event.level.PistonEvent;

/**
 * Pistons cannot move or break a dragon ball (4.7.36). A ball has push reaction DESTROY, so a piston
 * (or a block, slime or honey it moves) popped it into an item, a way around radar-only breaking.
 * Before any piston extends or retracts, the move's own structure (every block it would push and every
 * block it would destroy, slime and honey links included) is checked; a dragon ball anywhere in it
 * cancels the move, so the piston simply does not fire.
 */
public final class DragonBallPistonGuard {
    private DragonBallPistonGuard() {}

    public static void onPistonPre(PistonEvent.Pre event) {
        try {
            if (!(event.getLevel() instanceof Level level) || level.m_5776_()) {
                return;
            }
            PistonStructureResolver structure = event.getStructureHelper();
            if (structure == null) {
                return;
            }
            structure.m_60422_(); // resolve: fills the push and destroy lists
            if (hasBall(level, structure.m_60436_()) || hasBall(level, structure.m_60437_())) {
                event.setCanceled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasBall(Level level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (level.m_8055_(pos).m_60734_() instanceof DragonBallBlock) {
                return true;
            }
        }
        return false;
    }
}
