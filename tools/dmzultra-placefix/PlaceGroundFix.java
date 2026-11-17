package com.dmzultra.r3.bleachworld.places;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.IPlantable;

/**
 * Added to DMZ Ultra 0.5.7 by {@code tools/dmzultra-placefix} (the "placefix" build). Stock
 * {@code PlaceGround.ground} walks down each column of a candidate spot and calls the column "built"
 * at the first block that is not on its vanilla lists (soft, vegetation, natural). Modded world-gen
 * is on none of them: Pam's gardens and fruit trees, DragonMineZ' might trees and Tinkers' slime
 * islands made every spot read as built (the "on top" block in a refusal is only the surface).
 *
 * <p>{@code PlaceGround.vegetation(BlockState)} and {@code PlaceGround.natural(BlockState)} each start
 * with a call here; when it returns true they return true, otherwise they run unchanged. Both are
 * also what the builder may clear ({@code clearable}), so plants and natural ground found here are
 * cleared for the town the same way vanilla grass and dirt are.
 *
 * <p>Never true for a block with a block entity or a fluid in it, for player-placed (persistent)
 * leaves, or for anything else a player builds with: planks, stone bricks, concrete, glass, and so
 * on still count as built.
 *
 * <p>SRG names (production Forge 1.20.1): m_155947_ hasBlockEntity, m_60819_ getFluidState,
 * m_76178_ isEmpty, m_60734_ getBlock, m_61138_ hasProperty, m_61143_ getValue, f_61447_ PERSISTENT,
 * f_256975_ BLOCK, m_7981_ getKey, m_135827_ getNamespace, m_135815_ getPath.
 */
public final class PlaceGroundFix {
    private PlaceGroundFix() {}

    /** Natural plant cover the column walk steps down through (and the builder may clear). */
    public static boolean plant(BlockState state) {
        if (state == null || state.m_155947_() || !state.m_60819_().m_76178_()) {
            return false;
        }
        Block block = state.m_60734_();
        if (block instanceof LeavesBlock) {
            return !state.m_61138_(BlockStateProperties.f_61447_) || !state.m_61143_(BlockStateProperties.f_61447_);
        }
        if (block instanceof BushBlock // flowers, grass, ferns, saplings, crops, Pam's gardens and crops
                || block instanceof VineBlock
                || block instanceof GrowingPlantBlock // kelp, cave vines, weeping/twisting vines
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof BambooStalkBlock
                || block instanceof IPlantable) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.f_256975_.m_7981_(block);
        if (id == null) {
            return false;
        }
        String ns = id.m_135827_();
        String path = id.m_135815_();
        // Pam's HarvestCraft 2 Trees: fruit hanging from leaves and fruit on trunks (pamapple, pamcinnamon, ...).
        // DragonMineZ' might trees (world-gen giants): leaves, wood, root and fruit are plain blocks, untagged.
        return ns.equals("pamhc2trees") && path.startsWith("pam")
                || ns.equals("dragonminez") && path.startsWith("might_tree_");
    }

    /** Natural ground a column may end on. */
    public static boolean ground(BlockState state) {
        if (state == null || state.m_155947_() || !state.m_60819_().m_76178_()) {
            return false;
        }
        Block block = state.m_60734_();
        if (block instanceof SnowyDirtBlock) { // grass, podzol, mycelium and modded grass (Tinkers' slime grass)
            return true;
        }
        ResourceLocation id = BuiltInRegistries.f_256975_.m_7981_(block);
        if (id == null || !id.m_135827_().equals("tconstruct")) {
            return false;
        }
        // Tinkers' slime islands: earth/sky/ichor/ender slime dirt and congealed slime.
        String path = id.m_135815_();
        return path.endsWith("_slime_dirt") || path.endsWith("_congealed_slime");
    }
}
