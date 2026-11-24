package com.dbzlegacy.adaptivedifficulty.dojo;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * LM 4.7.68: the war ring, built from code on its own row of the dojo world (a plain stone ring with stands). Dojo
 * plots are not built here: they come from the mod-toolkit's structure files ({@link DojoPlacer}).
 */
final class DojoBuilder {
    private DojoBuilder() {}

    private static final Map<String, BlockState> STATES = new HashMap<>();

    static BlockState block(String id) {
        return STATES.computeIfAbsent(id, k -> {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", k));
            return (b == null ? ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", "stone")) : b)
                    .m_49966_();
        });
    }

    static void set(ServerLevel level, int x, int y, int z, String id) {
        level.m_7731_(new BlockPos(x, y, z), block(id), 2);
    }

    static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, String id) {
        BlockState s = block(id);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    level.m_7731_(new BlockPos(x, y, z), s, 2);
                }
            }
        }
    }

    /** A war ring: a wide floor, a boundary, stands for the waiting fighters, posts with lights. */
    static void warRing(ServerLevel level, BlockPos o) {
        int ox = o.m_123341_();
        int oz = o.m_123343_();
        int y = DojoWorld.FLOOR_Y;
        fill(level, ox - 40, y - 1, oz - 40, ox + 40, y - 1, oz + 40, "dirt");
        fill(level, ox - 40, y, oz - 40, ox + 40, y, oz + 40, "grass_block");
        fill(level, ox - 15, y, oz - 15, ox + 15, y, oz + 15, "smooth_stone");
        for (int i = -15; i <= 15; i++) {
            set(level, ox + i, y, oz - 15, "red_concrete");
            set(level, ox + i, y, oz + 15, "red_concrete");
            set(level, ox - 15, y, oz + i, "red_concrete");
            set(level, ox + 15, y, oz + i, "red_concrete");
        }
        // Stands to the south, raised, with a glass front so waiting fighters can't step in.
        fill(level, ox - 20, y + 4, oz + 22, ox + 20, y + 4, oz + 30, "polished_andesite");
        for (int x = -20; x <= 20; x++) {
            fill(level, ox + x, y + 5, oz + 22, ox + x, y + 7, oz + 22, "glass");
        }
        for (int[] c : new int[][] {{-15, -15}, {-15, 15}, {15, -15}, {15, 15}}) {
            fill(level, ox + c[0], y + 1, oz + c[1], ox + c[0], y + 3, oz + c[1], "quartz_pillar");
            set(level, ox + c[0], y + 4, oz + c[1], "sea_lantern");
        }
    }
}
