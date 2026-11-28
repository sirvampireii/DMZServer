package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * LM 4.7.68: the war ring, built from code on its own row of the dojo world. Dojo plots are not built here: they come
 * from the mod-toolkit's structure files ({@link DojoPlacer}).
 *
 * <p>4.8.4 (L): the war ring is a colosseum. The fighting floor is exactly the pre-4.8.4 one (81 x 81, with the
 * 31 x 31 stone ring and its red border in the middle): a fighter who falls off it drops into the pit, whose floor is
 * below the ring-out depth, so a bout still ends by KO or ring-out exactly as before. Everything built round it starts
 * {@value #PODIUM_IN} blocks from the centre, past the {@value DojoCatalog#RING_OUT_BLOCKS}-block ring-out, so no seat,
 * column or wall stands where a fighter is still in the bout: a podium wall with a glass front (the old stands'
 * pattern, so waiting fighters can't step down), {@value #TIERS} tiers of seating on all four sides, four arched
 * entrances with iron gates, a colonnade and cornice on top, and the two dojos' war banners. It is placed under the
 * dojo builder's time budget ({@link DojoPlacer}), ahead of any waiting hall build, long before the one-minute sign-up
 * ends.
 */
public final class DojoBuilder {
    private DojoBuilder() {}

    private static final Map<String, BlockState> STATES = new HashMap<>();

    static BlockState block(String id) {
        return STATES.computeIfAbsent(id, k -> {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", k));
            return (b == null ? ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", "stone")) : b)
                    .m_49966_();
        });
    }

    /** A block with properties ("iron_bars", "east=true,west=true"), read the way structure palettes are. */
    static BlockState block(String id, String props) {
        return STATES.computeIfAbsent(id + "[" + props + "]", k -> {
            CompoundTag e = new CompoundTag();
            e.m_128359_("Name", "minecraft:" + id);
            CompoundTag p = new CompoundTag();
            for (String kv : props.split(",")) {
                String[] t = kv.split("=", 2);
                p.m_128359_(t[0], t[1]);
            }
            e.m_128365_("Properties", p);
            return DojoPlacer.state(e);
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

    // ------------------------------------------------------------------ the war colosseum (4.8.4)
    /** Half the fighting floor (81 x 81, as before 4.8.4). */
    public static final int PLATFORM_HALF = 40;
    /** Half the stone ring in its middle (31 x 31 with a red border). */
    public static final int RING_HALF = 15;
    /** The pit floor is this far below the fighting floor: a fighter on it is below FLOOR_Y - 10, a ring-out. */
    public static final int PIT_DEPTH = 12;
    /** The first block of the colosseum (podium wall, gate columns, banners) is past this distance from the centre. */
    public static final double PODIUM_IN = 62.5;
    static final double PODIUM_OUT = 64.5;
    public static final int TIERS = 8;
    static final int TIER_DEPTH = 2;
    static final double SEATS_OUT = PODIUM_OUT + TIERS * TIER_DEPTH;
    static final double WALL_OUT = SEATS_OUT + 2.0;
    /** The terrace round the outer wall ends here. */
    public static final double OUTER = 88.5;
    static final int LOW = -PIT_DEPTH;
    static final int HIGH = 20;
    /** Waiting fighters sit on this seating tier on the south side (glass front between them and the pit). */
    public static final int STAND_TIER = 2;
    /** The y waiting fighters stand at (on {@link #STAND_TIER}). */
    public static final int STAND_Y = DojoWorld.FLOOR_Y + 5 + STAND_TIER + 1;

    /** The builder job for the ring at {@code o}. */
    static String jobId(BlockPos o) {
        return "war-ring:" + o.m_123341_() + "," + o.m_123343_();
    }

    /** True once the ring at {@code o} is built (nothing queued or running for it). */
    public static boolean ringReady(BlockPos o) {
        return !DojoPlacer.busy(jobId(o));
    }

    /**
     * A war ring for {@code from} against {@code to} (either may be null): the colosseum at {@code o}, queued ahead of
     * every waiting hall build. The banners fly each dojo's picked banner, or its style's colour.
     */
    static void warRing(ServerLevel level, BlockPos o, Dojo from, Dojo to) {
        int ox = o.m_123341_();
        int oz = o.m_123343_();
        int reach = (int) Math.ceil(OUTER);
        DojoPlacer.Job job = new DojoPlacer.Job(jobId(o), "war ring " + ox, level,
                new BlockPos(ox, DojoWorld.FLOOR_Y, oz), List.of(), null);
        job.area = new int[] {ox - reach, oz - reach, ox + reach, oz + reach};
        job.code.add(colosseum(reach));
        job.code.add(banners(from, to));
        DojoPlacer.submitNext(job);
    }

    /** The colosseum, column by column (x, z), each column bottom to top; air is left alone (the row is void). */
    static DojoPlacer.CodeWork colosseum(int reach) {
        return new DojoPlacer.CodeWork() {
            int index;

            @Override
            public boolean work(DojoPlacer.Job j, long end, BlockPos.MutableBlockPos pos) {
                int side = reach * 2 + 1;
                int total = side * side;
                while (index < total) {
                    if ((index & 15) == 0 && System.nanoTime() >= end) {
                        return false;
                    }
                    int dx = index % side - reach;
                    int dz = index / side - reach;
                    index++;
                    double r = Math.sqrt((double) dx * dx + (double) dz * dz);
                    if (r > OUTER && !onPlatform(dx, dz)) {
                        continue;
                    }
                    for (int dy = LOW; dy <= HIGH; dy++) {
                        BlockState s = at(dx, dy, dz, r);
                        if (s != null) {
                            pos.m_122178_(j.origin.m_123341_() + dx, j.origin.m_123342_() + dy, j.origin.m_123343_() + dz);
                            j.level.m_7731_(pos, s, DojoPlacer.FLAGS);
                            j.placed++;
                        }
                    }
                }
                return true;
            }
        };
    }

    static boolean onPlatform(int dx, int dz) {
        return Math.abs(dx) <= PLATFORM_HALF && Math.abs(dz) <= PLATFORM_HALF;
    }

    /** Distance from the nearest gate's centre line (the gates face north, south, east and west). */
    static int lat(int dx, int dz) {
        return Math.min(Math.abs(dx), Math.abs(dz));
    }

    /** The arched opening of a gate: 5 wide and 3 high, 3 wide at the top of the arch. */
    static boolean gateOpen(int lat, int dy) {
        return (lat <= 2 && dy >= 1 && dy <= 3) || (lat <= 1 && dy == 4);
    }

    /** True when (dx, dz) at radius {@code r} lies within {@code half} blocks (along the arc) of a ray every
     *  {@code step} degrees from {@code offset}. */
    static boolean onRay(int dx, int dz, double r, double step, double offset, double half) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        double near = offset + Math.round((a - offset) / step) * step;
        return Math.abs(Math.toRadians(a - near)) * r <= half;
    }

    /** The seating tier at radius {@code r} (0 = front row), or -1 outside the seats. */
    static int tierAt(double r) {
        return r < PODIUM_OUT || r >= SEATS_OUT ? -1 : (int) ((r - PODIUM_OUT) / TIER_DEPTH);
    }

    /**
     * The colosseum block at (dx, dy, dz) from the ring centre on the fighting floor (dy 0 = {@link DojoWorld#FLOOR_Y}),
     * {@code r} the horizontal distance; null for air.
     */
    static BlockState at(int dx, int dy, int dz, double r) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (onPlatform(dx, dz)) {                       // the fighting floor, as before 4.8.4
            int m = Math.max(ax, az);
            if (dy == 0) {
                if (m < RING_HALF) {
                    return block("smooth_stone");
                }
                if (m == RING_HALF) {
                    return block(ax == RING_HALF && az == RING_HALF ? "sea_lantern" : "red_concrete");
                }
                return block("smooth_sandstone");
            }
            if (dy == -1) {
                return block("sandstone");
            }
            return m == PLATFORM_HALF && dy > LOW && dy < -1 ? block("stone_bricks") : null;
        }
        int lat = lat(dx, dz);
        boolean northSouth = ax <= az;
        if (r < PODIUM_IN) {                            // the pit, and a column either side of each gate
            if (dy == LOW) {
                return block("coarse_dirt");
            }
            if (r >= PODIUM_IN - 1.0 && lat == 3 && dy > LOW) {
                return dy <= 6 ? block("quartz_pillar") : dy == 7 ? block("sea_lantern") : null;
            }
            return null;
        }
        if (r < PODIUM_OUT) {                           // the podium wall with its glass front and iron gates
            boolean front = r < PODIUM_IN + 1.0;
            if (gateOpen(lat, dy)) {
                return front ? block("iron_bars", northSouth ? "east=true,west=true" : "north=true,south=true") : null;
            }
            if (dy >= LOW && dy <= 3) {
                return block(dy == LOW ? "coarse_dirt" : "stone_bricks");
            }
            if (dy == 4) {
                return block("chiseled_stone_bricks");
            }
            return front && dy >= 5 && dy <= 7 ? block("glass") : null;
        }
        if (r < SEATS_OUT) {                            // the seats: two blocks deep, one higher per tier
            int k = tierAt(r);
            int top = 5 + k;
            if (gateOpen(lat, dy) || dy < -1 || dy > top) {
                return null;
            }
            if (dy == top) {
                return block(k % 2 == 0 ? "smooth_sandstone" : "cut_sandstone");
            }
            return block(dy == -1 ? "stone_bricks" : "sandstone");
        }
        if (r < WALL_OUT) {                             // the outer wall: colonnade inside, arched windows outside
            boolean inner = r < SEATS_OUT + 1.0;
            if (gateOpen(lat, dy) || dy < -1) {
                return null;
            }
            if (dy == -1) {
                return block("stone_bricks");
            }
            if (dy <= 12) {
                return block(lat == 3 && dy <= 6 ? "quartz_pillar" : "cut_sandstone");
            }
            if (inner) {
                if (dy <= 17) {
                    return onRay(dx, dz, r, 10.0, 0.0, 0.75) ? block("quartz_pillar") : null;
                }
                return dy == 18 ? block("smooth_quartz") : null;
            }
            if (dy >= 14 && dy <= 16 && onRay(dx, dz, r, 10.0, 5.0, 1.6)) {
                return null;                            // a window between two columns
            }
            if (dy == 17 && onRay(dx, dz, r, 10.0, 5.0, 0.7)) {
                return null;                            // the window's arch
            }
            if (dy <= 18) {
                return block(dy == 18 ? "smooth_quartz" : "cut_sandstone");
            }
            return dy == 19 && onRay(dx, dz, r, 5.0, 0.0, 1.2) ? block("chiseled_sandstone") : null;
        }
        if (r <= OUTER) {                               // the terrace, with lit columns either side of each gate
            if (dy == -1) {
                return block("stone_bricks");
            }
            if (dy == 0) {
                return block(lat <= 2 ? "polished_andesite" : "smooth_stone");
            }
            if (lat == 3 && r < WALL_OUT + 1.0 && dy >= 1) {
                return dy <= 6 ? block("quartz_pillar") : dy == 7 ? block("sea_lantern") : null;
            }
        }
        return null;
    }

    /** Where a war banner goes, from the ring centre: which dojo (0 challenger, 1 challenged), a wall banner's facing
     *  (null for a standing banner) and a standing banner's rotation. */
    record BannerPlace(int dx, int dy, int dz, int dojo, String facing, int rot) {}

    /**
     * The war banners: on the podium wall's inner face, four per side either side of the gate (the two dojos in turn),
     * and eight standing on the cornice between the gates, facing the floor. All past the ring-out.
     */
    static List<BannerPlace> bannerPlaces() {
        List<BannerPlace> out = new ArrayList<>();
        int[] lats = {-24, -12, 12, 24};
        for (String side : new String[] {"north", "south", "west", "east"}) {
            for (int i = 0; i < lats.length; i++) {
                int a = lats[i];
                int d = 0;
                while (Math.hypot(a, d + 1) < PODIUM_IN) {
                    d++;
                }
                // (a, d): the last cell before the wall on this side's axis; the wall block behind holds the banner
                int dx;
                int dz;
                String facing;
                switch (side) {
                    case "north" -> { dx = a; dz = -d; facing = "south"; }
                    case "south" -> { dx = a; dz = d; facing = "north"; }
                    case "west" -> { dx = -d; dz = a; facing = "east"; }
                    default -> { dx = d; dz = a; facing = "west"; }
                }
                out.add(new BannerPlace(dx, 3, dz, i % 2, facing, 0));
            }
        }
        int n = 0;
        for (int deg = 30; deg < 360; deg += 30) {
            if (deg % 90 == 0) {
                continue;
            }
            double t = Math.toRadians(deg);
            int dx = 0;
            int dz = 0;
            for (double rr = SEATS_OUT + 0.5; rr < SEATS_OUT + 1.5; rr += 0.1) {
                dx = (int) Math.round(rr * Math.cos(t));
                dz = (int) Math.round(rr * Math.sin(t));
                double h = Math.hypot(dx, dz);
                if (h >= SEATS_OUT && h < SEATS_OUT + 1.0) {
                    break;                              // on the cornice of the colonnade
                }
            }
            // face the floor: rotation 0 = south (+z), 4 = west, 8 = north, 12 = east
            int rot = Math.floorMod((int) Math.round(Math.atan2(dx, -dz) / (2 * Math.PI) * 16.0), 16);
            out.add(new BannerPlace(dx, 19, dz, n++ % 2, null, rot));
        }
        return out;
    }

    /** Hang the war banners once the stone is up (one step: a couple of dozen blocks). */
    static DojoPlacer.CodeWork banners(Dojo from, Dojo to) {
        DojoPlacer.BannerDesign[] design = {DojoPlacer.design(from), DojoPlacer.design(to)};
        String[] colour = {styleColour(from, "red"), styleColour(to, "blue")};
        return (j, end, pos) -> {
            for (BannerPlace b : bannerPlaces()) {
                String base = design[b.dojo()] != null ? "white" : colour[b.dojo()];
                BlockState baked = b.facing() != null ? block(base + "_wall_banner", "facing=" + b.facing())
                        : block(base + "_banner", "rotation=" + b.rot());
                DojoPlacer.putBanner(j.level, j.origin.m_7918_(b.dx(), b.dy(), b.dz()), baked, design[b.dojo()]);
                j.placed++;
            }
            return true;
        };
    }

    /** The dye name a dojo with no picked banner flies (its style's colour), or {@code fallback}. */
    static String styleColour(Dojo d, String fallback) {
        DojoCatalog.Style s = d == null ? null : DojoCatalog.Style.of(d.style);
        return s == null || s.bannerColour == null || s.bannerColour.isBlank() ? fallback : s.bannerColour;
    }

    /** South seats for waiting fighters: the dz on {@link #STAND_TIER} for column {@code dx} (|dx| 4..15). */
    static int standDz(int dx) {
        double mid = PODIUM_OUT + STAND_TIER * TIER_DEPTH + TIER_DEPTH / 2.0;
        int best = (int) Math.round(mid);
        double err = Double.MAX_VALUE;
        for (int dz = (int) PODIUM_OUT; dz <= (int) SEATS_OUT; dz++) {
            double r = Math.hypot(dx, dz);
            if (tierAt(r) == STAND_TIER && Math.abs(r - mid) < err) {
                err = Math.abs(r - mid);
                best = dz;
            }
        }
        return best;
    }

    /**
     * Test rig: what stands at the ring at {@code o}: the stone ring floor, seats rising on all four sides, the four
     * gates, the colonnade, the banners, and nothing above the floor inside the ring-out. "OK ..." or what is wrong.
     */
    public static String inspect(ServerLevel level, BlockPos o) {
        List<String> bad = new ArrayList<>();
        java.util.function.BiPredicate<int[], String> is = (c, id) -> level.m_8055_(o.m_7918_(c[0], c[1], c[2]))
                .m_60713_(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", id)));
        java.util.function.Predicate<int[]> air = c -> level.m_8055_(o.m_7918_(c[0], c[1], c[2])).m_60795_();
        if (!is.test(new int[] {0, 0, 0}, "smooth_stone") || !is.test(new int[] {RING_HALF, 0, 0}, "red_concrete")
                || !is.test(new int[] {PLATFORM_HALF, 0, 0}, "smooth_sandstone")) {
            bad.add("no stone ring floor");
        }
        if (!is.test(new int[] {PLATFORM_HALF + 5, LOW, 0}, "coarse_dirt") || !air.test(new int[] {PLATFORM_HALF + 5, 0, 0})) {
            bad.add("no pit round the floor");
        }
        int seats = 0;
        int gates = 0;
        for (int[] dir : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            boolean rising = true;                      // a seat on every tier of this side, off the gate line
            for (int k = 0; k < TIERS; k++) {
                int along = (int) Math.round(PODIUM_OUT + k * TIER_DEPTH + 1.0);
                int[] c = {dir[0] * along + (dir[1] != 0 ? 8 : 0), 5 + k, dir[1] * along + (dir[0] != 0 ? 8 : 0)};
                rising &= tierAt(Math.hypot(c[0], c[2])) == k && !air.test(c) && air.test(new int[] {c[0], c[1] + 1, c[2]});
            }
            seats += rising ? 1 : 0;
            int in = (int) Math.ceil(PODIUM_IN);
            int g = (int) Math.round(PODIUM_OUT + 6);
            gates += is.test(new int[] {dir[0] * in, 2, dir[1] * in}, "iron_bars")
                    && air.test(new int[] {dir[0] * g, 2, dir[1] * g}) && !air.test(new int[] {dir[0] * g, 5, dir[1] * g})
                    ? 1 : 0;
        }
        if (seats != 4) {
            bad.add("tiered seats on " + seats + " of 4 sides");
        }
        if (gates != 4) {
            bad.add(gates + " of 4 arched gates");
        }
        int columns = 0;
        for (int deg = 0; deg < 360; deg += 10) {
            double t = Math.toRadians(deg);
            int cx = (int) Math.round((SEATS_OUT + 0.5) * Math.cos(t));
            int cz = (int) Math.round((SEATS_OUT + 0.5) * Math.sin(t));
            boolean found = false;
            for (int ex = -1; ex <= 1 && !found; ex++) {
                for (int ez = -1; ez <= 1 && !found; ez++) {
                    found = is.test(new int[] {cx + ex, 15, cz + ez}, "quartz_pillar");
                }
            }
            columns += found ? 1 : 0;
        }
        if (columns < 36) {
            bad.add(columns + " of 36 colonnade columns");
        }
        int banners = 0;
        List<BannerPlace> places = bannerPlaces();
        for (BannerPlace b : places) {
            BlockState s = level.m_8055_(o.m_7918_(b.dx(), b.dy(), b.dz()));
            banners += s.m_60734_() instanceof net.minecraft.world.level.block.AbstractBannerBlock ? 1 : 0;
        }
        if (banners != places.size()) {
            bad.add(banners + " of " + places.size() + " banners");
        }
        int blocked = 0;                                // nothing above the floor inside the ring-out
        int reach = (int) DojoCatalog.RING_OUT_BLOCKS;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (Math.hypot(dx, dz) > DojoCatalog.RING_OUT_BLOCKS) {
                    continue;
                }
                for (int dy = 1; dy <= HIGH; dy++) {
                    blocked += air.test(new int[] {dx, dy, dz}) ? 0 : 1;
                }
            }
        }
        if (blocked > 0) {
            bad.add(blocked + " blocks above the floor inside the ring-out");
        }
        return bad.isEmpty() ? "OK (stone ring floor, pit, seats on 4 sides x " + TIERS + " tiers from " + PODIUM_IN
                + " blocks, 4 gates, " + columns + " columns, " + banners + " banners)" : String.join("; ", bad);
    }
}
