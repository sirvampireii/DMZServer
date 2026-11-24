package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * LM 4.7.68 dojo builds, from code (no structure files): the floating island, the arenas, the rooms and the
 * spectator gallery, each drawn in its look tier. Basic is stone bricks and oak, refined is polished andesite,
 * spruce and lanterns, masterwork is polished blackstone, dark oak, quartz and gold. A room that is not built is
 * cleared to bare courtyard, so a reopened dojo (one tier down) visibly loses a level. Blocks are placed without
 * neighbor updates; a full plot is a few tens of thousands of blocks, built once and again only on a change.
 */
final class DojoBuilder {
    private DojoBuilder() {}

    /** Palette by look tier 1..3: floor, wall, pillar, light, accent, roof, glass. */
    static String[] palette(int look) {
        return switch (Math.max(1, Math.min(3, look))) {
            case 1 -> new String[] {"stone_bricks", "oak_planks", "oak_log", "torch", "cobblestone", "oak_slab",
                    "glass"};
            case 2 -> new String[] {"polished_andesite", "spruce_planks", "stripped_spruce_log", "lantern",
                    "chiseled_stone_bricks", "spruce_slab", "glass"};
            default -> new String[] {"polished_blackstone_bricks", "dark_oak_planks", "quartz_pillar",
                    "sea_lantern", "gold_block", "dark_oak_slab", "white_stained_glass"};
        };
    }

    static void build(ServerLevel level, BlockPos o, Dojo d, boolean first) {
        int ox = o.m_123341_();
        int oz = o.m_123343_();
        int y = DojoWorld.FLOOR_Y;
        if (first) {
            island(level, ox, y, oz);
        }
        for (int k = 0; k < DojoCatalog.MAX_ARENAS; k++) {
            int[] c = DojoWorld.arenaCenter(k);
            arena(level, ox + c[0], y, oz + c[1], k < DojoService.arenas(d),
                    DojoCatalog.lookTier(DojoCatalog.ARENA, DojoService.arenas(d)));
        }
        gallery(level, ox, y, oz, DojoService.tier(d, DojoCatalog.SPECTATOR));
        garden(level, ox, y, oz, DojoService.tier(d, DojoCatalog.GARDEN));
        for (String room : new String[] {DojoCatalog.TRAINING, DojoCatalog.MEDITATION, DojoCatalog.TROPHY,
                DojoCatalog.QUARTERS}) {
            int[] off = DojoWorld.roomOffset(room);
            room(level, ox + off[0], y, oz + off[1], room, DojoService.tier(d, room), d.warWins);
        }
        int[] bunks = DojoWorld.roomOffset(DojoCatalog.CAPACITY);
        bunks(level, ox + bunks[0], y, oz + bunks[1], DojoService.tier(d, DojoCatalog.CAPACITY));
        // Gate and a signpost path to the courtyard.
        DojoWorld.fill(level, ox - 2, y, oz + 14, ox + 2, y, oz + 24, palette(1)[0]);
    }

    static void island(ServerLevel level, int ox, int y, int oz) {
        DojoWorld.fill(level, ox - DojoWorld.ISLAND_X, y - 1, oz + DojoWorld.ISLAND_Z_MIN, ox + DojoWorld.ISLAND_X,
                y - 1, oz + DojoWorld.ISLAND_Z_MAX, "dirt");
        DojoWorld.fill(level, ox - DojoWorld.ISLAND_X, y, oz + DojoWorld.ISLAND_Z_MIN, ox + DojoWorld.ISLAND_X, y,
                oz + DojoWorld.ISLAND_Z_MAX, "grass_block");
        for (int x = -DojoWorld.ISLAND_X; x <= DojoWorld.ISLAND_X; x++) {
            DojoWorld.set(level, ox + x, y + 1, oz + DojoWorld.ISLAND_Z_MIN, "stone_brick_wall");
            DojoWorld.set(level, ox + x, y + 1, oz + DojoWorld.ISLAND_Z_MAX, "stone_brick_wall");
        }
        for (int z = DojoWorld.ISLAND_Z_MIN; z <= DojoWorld.ISLAND_Z_MAX; z++) {
            DojoWorld.set(level, ox - DojoWorld.ISLAND_X, y + 1, oz + z, "stone_brick_wall");
            DojoWorld.set(level, ox + DojoWorld.ISLAND_X, y + 1, oz + z, "stone_brick_wall");
        }
    }

    /** One arena slot: a ring when built, bare grass when not. */
    static void arena(ServerLevel level, int cx, int y, int cz, boolean built, int look) {
        int r = DojoWorld.ARENA_RADIUS;
        DojoWorld.fill(level, cx - r - 1, y + 1, cz - r - 1, cx + r + 1, y + 5, cz + r + 1, "air");
        if (!built) {
            DojoWorld.fill(level, cx - r - 1, y, cz - r - 1, cx + r + 1, y, cz + r + 1, "grass_block");
            return;
        }
        String[] p = palette(look);
        DojoWorld.fill(level, cx - r, y, cz - r, cx + r, y, cz + r, p[0]);
        for (int i = -r; i <= r; i++) {
            DojoWorld.set(level, cx + i, y, cz - r, p[4]);
            DojoWorld.set(level, cx + i, y, cz + r, p[4]);
            DojoWorld.set(level, cx - r, y, cz + i, p[4]);
            DojoWorld.set(level, cx + r, y, cz + i, p[4]);
        }
        // Center mark and corner posts; refined and masterwork add tall posts with lights.
        DojoWorld.set(level, cx, y, cz, p[4]);
        int postH = look >= 2 ? 3 : 1;
        for (int[] c : new int[][] {{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
            DojoWorld.fill(level, cx + c[0], y + 1, cz + c[1], cx + c[0], y + postH, cz + c[1], p[2]);
            DojoWorld.set(level, cx + c[0], y + postH + 1, cz + c[1], p[3]);
        }
    }

    /** The glass gallery above the arenas (spectator room); cleared when not built. */
    static void gallery(ServerLevel level, int ox, int y, int oz, int tier) {
        int gy = DojoWorld.GALLERY_Y;
        int x1 = ox - 66;
        int x2 = ox + 66;
        int z1 = oz - 110;
        int z2 = oz - 28;
        DojoWorld.fill(level, x1, gy, z1, x2, gy + 4, z2, "air");
        if (tier <= 0) {
            return;
        }
        String[] p = palette(tier);
        DojoWorld.fill(level, x1, gy, z1, x2, gy, z2, p[6]);
        for (int x = x1; x <= x2; x++) {
            DojoWorld.fill(level, x, gy + 1, z1, x, gy + 3, z1, p[6]);
            DojoWorld.fill(level, x, gy + 1, z2, x, gy + 3, z2, p[6]);
        }
        for (int z = z1; z <= z2; z++) {
            DojoWorld.fill(level, x1, gy + 1, z, x1, gy + 3, z, p[6]);
            DojoWorld.fill(level, x2, gy + 1, z, x2, gy + 3, z, p[6]);
        }
        // A walkway of solid floor to stand on along the middle, with rails in the look's accent.
        DojoWorld.fill(level, ox - 2, gy, z1 + 1, ox + 2, gy, z2 - 1, p[0]);
        if (tier >= 2) {
            for (int z = z1 + 4; z < z2; z += 8) {
                DojoWorld.set(level, ox - 3, gy + 1, z, p[3]);
                DojoWorld.set(level, ox + 3, gy + 1, z, p[3]);
            }
        }
    }

    /** Courtyard garden around the center: flowers at basic, bushes at refined, trees and lights at masterwork. */
    static void garden(ServerLevel level, int ox, int y, int oz, int tier) {
        DojoWorld.fill(level, ox - 16, y + 1, oz - 12, ox + 16, y + 6, oz + 12, "air");
        DojoWorld.fill(level, ox - 16, y, oz - 12, ox + 16, y, oz + 12, "grass_block");
        DojoWorld.fill(level, ox - 2, y, oz - 12, ox + 2, y, oz + 12, tier >= 2 ? "polished_andesite" : "gravel");
        if (tier <= 0) {
            return;
        }
        String[] flowers = {"poppy", "dandelion", "blue_orchid", "allium", "oxeye_daisy"};
        int i = 0;
        for (int x = -14; x <= 14; x += 3) {
            for (int z = -10; z <= 10; z += 4) {
                if (Math.abs(x) <= 3) {
                    continue;
                }
                DojoWorld.set(level, ox + x, y + 1, oz + z, flowers[i++ % flowers.length]);
            }
        }
        if (tier >= 2) {
            for (int x : new int[] {-12, 12}) {
                for (int z : new int[] {-8, 8}) {
                    DojoWorld.fill(level, ox + x - 1, y + 1, oz + z - 1, ox + x + 1, y + 2, oz + z + 1, "oak_leaves");
                }
            }
        }
        if (tier >= 3) {
            for (int x : new int[] {-8, 8}) {
                DojoWorld.fill(level, ox + x, y + 1, oz, ox + x, y + 4, oz, "cherry_log");
                DojoWorld.fill(level, ox + x - 2, y + 5, oz - 2, ox + x + 2, y + 6, oz + 2, "cherry_leaves");
                DojoWorld.set(level, ox + x, y + 1, oz + 3, "lantern");
            }
        }
    }

    /** A room: walls with a door facing north, a roof, and the props that say what it is. */
    static void room(ServerLevel level, int cx, int y, int cz, String room, int tier, int trophies) {
        int r = DojoWorld.ROOM_RADIUS;
        DojoWorld.fill(level, cx - r, y + 1, cz - r, cx + r, y + 7, cz + r, "air");
        DojoWorld.fill(level, cx - r, y, cz - r, cx + r, y, cz + r, "grass_block");
        if (tier <= 0) {
            return;
        }
        String[] p = palette(tier);
        DojoWorld.fill(level, cx - r, y, cz - r, cx + r, y, cz + r, p[0]);
        for (int i = -r; i <= r; i++) {
            for (int h = 1; h <= 4; h++) {
                boolean door = Math.abs(i) <= 1 && h <= 3;
                if (!door) {
                    DojoWorld.set(level, cx + i, y + h, cz - r, p[1]);
                }
                DojoWorld.set(level, cx + i, y + h, cz + r, p[1]);
                DojoWorld.set(level, cx - r, y + h, cz + i, p[1]);
                DojoWorld.set(level, cx + r, y + h, cz + i, p[1]);
            }
        }
        for (int[] c : new int[][] {{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
            DojoWorld.fill(level, cx + c[0], y + 1, cz + c[1], cx + c[0], y + 4, cz + c[1], p[2]);
        }
        DojoWorld.fill(level, cx - r, y + 5, cz - r, cx + r, y + 5, cz + r, p[5]);
        DojoWorld.set(level, cx - r + 2, y + 1, cz - r + 2, p[3]);
        DojoWorld.set(level, cx + r - 2, y + 1, cz - r + 2, p[3]);
        DojoWorld.set(level, cx - r + 2, y + 1, cz + r - 2, p[3]);
        DojoWorld.set(level, cx + r - 2, y + 1, cz + r - 2, p[3]);
        switch (room) {
            case DojoCatalog.TRAINING -> {
                for (int x = -6; x <= 6; x += 3) {
                    DojoWorld.fill(level, cx + x, y + 1, cz + 5, cx + x, y + 2, cz + 5, "hay_block");
                    DojoWorld.set(level, cx + x, y + 1, cz - 3, "target");
                }
            }
            case DojoCatalog.MEDITATION -> {
                String mat = tier >= 3 ? "purple_carpet" : tier == 2 ? "cyan_carpet" : "white_carpet";
                for (int x = -6; x <= 6; x += 3) {
                    for (int z = -4; z <= 6; z += 3) {
                        DojoWorld.set(level, cx + x, y + 1, cz + z, mat);
                    }
                }
            }
            case DojoCatalog.TROPHY -> {
                for (int i = 0; i < 6; i++) {
                    int x = -6 + (i % 3) * 6;
                    int z = i < 3 ? 2 : 6;
                    DojoWorld.set(level, cx + x, y + 1, cz + z, p[4]);
                    if (i < trophies) {
                        DojoWorld.set(level, cx + x, y + 2, cz + z, tier >= 3 ? "diamond_block" : "gold_block");
                    }
                }
            }
            case DojoCatalog.QUARTERS -> {
                DojoWorld.fill(level, cx - 6, y + 1, cz + 5, cx - 4, y + 1, cz + 7, "red_wool");
                DojoWorld.fill(level, cx + 4, y + 1, cz + 7, cx + 7, y + 3, cz + 7, "bookshelf");
                DojoWorld.set(level, cx, y + 1, cz + 6, "crafting_table");
            }
            default -> {
            }
        }
    }

    /** Disciple bunks: a longer bunkhouse for each capacity level. */
    static void bunks(ServerLevel level, int cx, int y, int cz, int levelUp) {
        DojoWorld.fill(level, cx - 14, y + 1, cz - 6, cx + 14, y + 6, cz + 6, "air");
        DojoWorld.fill(level, cx - 14, y, cz - 6, cx + 14, y, cz + 6, "grass_block");
        int len = 4 + 2 * Math.max(0, levelUp);
        String[] p = palette(1 + Math.min(2, levelUp / 2));
        DojoWorld.fill(level, cx - len, y, cz - 4, cx + len, y, cz + 4, p[0]);
        for (int x = -len; x <= len; x++) {
            for (int h = 1; h <= 3; h++) {
                if (!(Math.abs(x) <= 1 && h <= 2)) {
                    DojoWorld.set(level, cx + x, y + h, cz - 4, p[1]);
                }
                DojoWorld.set(level, cx + x, y + h, cz + 4, p[1]);
            }
        }
        for (int z = -4; z <= 4; z++) {
            DojoWorld.fill(level, cx - len, y + 1, cz + z, cx - len, y + 3, cz + z, p[1]);
            DojoWorld.fill(level, cx + len, y + 1, cz + z, cx + len, y + 3, cz + z, p[1]);
        }
        DojoWorld.fill(level, cx - len, y + 4, cz - 4, cx + len, y + 4, cz + 4, p[5]);
        for (int x = -len + 2; x <= len - 2; x += 3) {
            DojoWorld.set(level, cx + x, y + 1, cz + 3, "white_wool");
        }
    }

    /** A war ring: a wide floor, a boundary, stands for the waiting fighters, posts with lights. */
    static void warRing(ServerLevel level, BlockPos o) {
        int ox = o.m_123341_();
        int oz = o.m_123343_();
        int y = DojoWorld.FLOOR_Y;
        DojoWorld.fill(level, ox - 40, y - 1, oz - 40, ox + 40, y - 1, oz + 40, "dirt");
        DojoWorld.fill(level, ox - 40, y, oz - 40, ox + 40, y, oz + 40, "grass_block");
        DojoWorld.fill(level, ox - 15, y, oz - 15, ox + 15, y, oz + 15, "smooth_stone");
        for (int i = -15; i <= 15; i++) {
            DojoWorld.set(level, ox + i, y, oz - 15, "red_concrete");
            DojoWorld.set(level, ox + i, y, oz + 15, "red_concrete");
            DojoWorld.set(level, ox - 15, y, oz + i, "red_concrete");
            DojoWorld.set(level, ox + 15, y, oz + i, "red_concrete");
        }
        // Stands to the south, raised, with a glass front so waiting fighters can't step in.
        DojoWorld.fill(level, ox - 20, y + 4, oz + 22, ox + 20, y + 4, oz + 30, "polished_andesite");
        for (int x = -20; x <= 20; x++) {
            DojoWorld.fill(level, ox + x, y + 5, oz + 22, ox + x, y + 7, oz + 22, "glass");
        }
        for (int[] c : new int[][] {{-15, -15}, {-15, 15}, {15, -15}, {15, 15}}) {
            DojoWorld.fill(level, ox + c[0], y + 1, oz + c[1], ox + c[0], y + 3, oz + c[1], "quartz_pillar");
            DojoWorld.set(level, ox + c[0], y + 4, oz + c[1], "sea_lantern");
        }
    }
}
