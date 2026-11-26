package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * LM 4.7.68 dojo dimension ({@code legacymechanics:dojo}, a void world from the datapack in the LM jar): one plot per
 * dojo on a grid {@value #PLOT_SPACING} blocks apart (plots are never reused, so builds never meet), war rings on
 * their own row, the plot layout every menu map and every build share, visits, the spectator gallery, and putting
 * players back exactly where they stood.
 *
 * <p>A plot is one Japanese hall, made with the mod-toolkit (DojoKit) and placed from structure files in the LM jar
 * ({@link DojoPlacer}): six ring spaces on the hall floor, the spectator balcony on its north wall, the rooms in its
 * south wing, the grounds round it. A new dojo is the empty hall with one sparring mat; each upgrade builds its part
 * in. Every position comes from {@link DojoLayout}. The plot's floor is at {@value #FLOOR_Y} (players stand on
 * {@value #FLOOR_Y} + 1), centred on the plot origin.
 */
public final class DojoWorld {
    private DojoWorld() {}

    public static final ResourceLocation DIMENSION = new ResourceLocation(AdaptiveDifficultyMod.MOD_ID, "dojo");
    public static final int PLOT_SPACING = 4000;
    public static final int PLOTS_PER_ROW = 32;
    public static final int FLOOR_Y = 100;
    /** War rings sit on their own row far from every plot. */
    public static final int RING_ROW_Z = -400_000;
    public static final int RING_SPACING = 1000;
    /** Below this a player in the dojo world is caught (no void deaths). */
    public static final int CATCH_Y = FLOOR_Y - 20;
    /** A board bout is a ring-out once a fighter is this far from the ring's centre on either axis (ring + apron). */
    public static final int RING_OUT_HALF = 12;

    /** Where the plot structure's (0, 0, 0) goes for plot {@code plot}. */
    public static BlockPos plotCorner(int plot) {
        DojoLayout l = DojoLayout.get();
        BlockPos o = plotOrigin(plot);
        return new BlockPos(o.m_123341_() - l.sx / 2, FLOOR_Y + 1 - l.floorY, o.m_123343_() - l.sz / 2);
    }

    /** World x, y, z of a plot position. */
    public static double[] world(int plot, double x, double y, double z) {
        BlockPos c = plotCorner(plot);
        return new double[] {c.m_123341_() + x, c.m_123342_() + y, c.m_123343_() + z};
    }

    /** Ring {@code k} (0..5): its centre in the world (x, z), on the hall floor. */
    public static double[] ringCenter(Dojo d, int k) {
        int[] r = DojoLayout.get().rings.get(Math.max(0, Math.min(DojoCatalog.MAX_ARENAS - 1, k)));
        double[] w = world(d.plot, r[0] + 0.5, 0, r[1] + 0.5);
        return new double[] {w[0], w[2]};
    }

    public static BlockPos plotOrigin(int plot) {
        int p = Math.max(0, plot);
        return new BlockPos((p % PLOTS_PER_ROW) * PLOT_SPACING, FLOOR_Y, (p / PLOTS_PER_ROW) * PLOT_SPACING);
    }

    /**
     * 4.7.88: the plot whose hall footprint (the plot structure, x and z, any height) holds {@code pos}, or -1 outside
     * every plot (between plots, the war ring row, or no layout).
     */
    public static int plotIndexAt(BlockPos pos) {
        DojoLayout l = DojoLayout.get();
        if (pos == null || l == null) {
            return -1;
        }
        int px = Math.floorDiv(pos.m_123341_() + PLOT_SPACING / 2, PLOT_SPACING);
        int pz = Math.floorDiv(pos.m_123343_() + PLOT_SPACING / 2, PLOT_SPACING);
        if (px < 0 || px >= PLOTS_PER_ROW || pz < 0) {
            return -1;
        }
        int plot = pz * PLOTS_PER_ROW + px;
        BlockPos c = plotCorner(plot);
        int dx = pos.m_123341_() - c.m_123341_();
        int dz = pos.m_123343_() - c.m_123343_();
        return dx >= 0 && dx < l.sx && dz >= 0 && dz < l.sz ? plot : -1;
    }

    /** 4.7.88: the dojo that owns plot {@code plot}, or null (no dojo was given that plot). */
    public static Dojo dojoOnPlot(int plot) {
        if (plot < 0) {
            return null;
        }
        for (Dojo d : DojoStore.get().dojos.values()) {
            if (d.plot == plot) {
                return d;
            }
        }
        return null;
    }

    public static BlockPos ringOrigin(int ring) {
        return new BlockPos(Math.max(0, ring) * RING_SPACING, FLOOR_Y, RING_ROW_Z);
    }

    /** The dojo world, or null when the server has not loaded it (the datapack arrives with a restart). */
    public static ServerLevel level(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        for (ServerLevel l : server.m_129785_()) {
            if (l.m_46472_().m_135782_().equals(DIMENSION)) {
                return l;
            }
        }
        return null;
    }

    public static boolean inDojoWorld(ServerPlayer p) {
        return p != null && p.m_9236_().m_46472_().m_135782_().equals(DIMENSION);
    }

    public static final String NOT_LOADED = "The dojo world isn't loaded yet. It appears after the next server restart.";

    /** Give the dojo a plot if it has none. */
    static void ensurePlot(Dojo d) {
        if (d.plot < 0) {
            d.plot = DojoStore.get().nextPlot++;
            DojoStore.get().markDirty();
        }
    }

    /**
     * 4.8.3 (staff {@code /lm admin dojo rebuild}): build {@code d}'s whole plot again even when it is up to date (a
     * damaged or half-built hall). False when the dojo world or its layout is not loaded.
     */
    public static boolean rebuild(MinecraftServer server, Dojo d) {
        if (d == null || level(server) == null || DojoLayout.get() == null) {
            return false;
        }
        PENDING.remove(d.masterUuid);
        d.builtSignature = "";
        DojoStore.get().markDirty();
        ensureBuilt(server, d);
        return true;
    }

    /**
     * 4.8.3 (staff disband): send everyone standing on plot {@code plot} back where they came from (or to the world
     * spawn), then clear the whole plot box to air over a few seconds; when it is done, loose items and other
     * non-player entities in the box are removed. The plot is left empty void with no dojo on it; the allocator still
     * hands every new dojo a new plot ({@link DojoStore#nextPlot}, plots are never reused, 4.7.68). False when the
     * dojo world or its layout is not loaded (nothing is cleared).
     */
    public static boolean clearPlot(MinecraftServer server, String master, int plot) {
        ServerLevel level = level(server);
        DojoLayout l = DojoLayout.get();
        if (level == null || l == null || plot < 0) {
            return false;
        }
        PENDING.remove(master);
        LAST_CHECK.remove(master);
        BlockPos c = plotCorner(plot);
        for (ServerPlayer p : new ArrayList<>(level.m_6907_())) {
            if (plotIndexAt(p.m_20183_()) != plot) {
                continue;
            }
            SPECTATING.remove(p.m_20148_());
            if (!returnPlayer(p)) {
                ServerLevel home = server.m_129783_();
                BlockPos spawn = home.m_220360_();
                teleport(p, home, spawn.m_123341_() + 0.5, spawn.m_123342_(), spawn.m_123343_() + 0.5, 0.0f);
            }
        }
        DojoPlacer.submitClear(master, "clear plot " + plot, level, c, () -> {
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(c.m_123341_(), c.m_123342_(),
                    c.m_123343_(), c.m_123341_() + l.sx, c.m_123342_() + l.sy, c.m_123343_() + l.sz);
            for (net.minecraft.world.entity.Entity e : level.m_6249_(null, box, x -> !(x instanceof ServerPlayer))) {
                e.m_146870_();
            }
            AdaptiveDifficultyMod.LOGGER.info("[{}] dojo plot {} cleared (no dojo on it now)",
                    AdaptiveDifficultyMod.MOD_ID, plot);
        });
        return true;
    }

    // ------------------------------------------------------------------ teleport and return

    public static void teleport(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw) {
        try {
            p.m_8999_(level, x, y, z, yaw, 0.0f);
            p.m_20256_(new Vec3(0.0, 0.0, 0.0));
            p.f_19789_ = 0.0f;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] dojo teleport failed for {}: {}", AdaptiveDifficultyMod.MOD_ID,
                    p.m_7755_().getString(), t.toString());
        }
    }

    /** Remember where {@code p} stands now, unless a spot is already kept (a fight during a visit returns home). */
    public static void rememberSpot(ServerPlayer p, String why) {
        String id = p.m_20148_().toString();
        if (DojoStore.get().returns.containsKey(id)) {
            return;
        }
        DojoStore.ReturnSpot s = new DojoStore.ReturnSpot();
        s.dimension = p.m_9236_().m_46472_().m_135782_().toString();
        s.x = p.m_20185_();
        s.y = p.m_20186_();
        s.z = p.m_20189_();
        s.yaw = p.m_146908_();
        s.pitch = p.m_146909_();
        s.why = why;
        DojoStore.get().returns.put(id, s);
        DojoStore.get().markDirty();
    }

    public static boolean hasSpot(UUID id) {
        return id != null && DojoStore.get().returns.containsKey(id.toString());
    }

    /** Put {@code p} back exactly where they stood before; true when they had a spot. */
    public static boolean returnPlayer(ServerPlayer p) {
        DojoStore.ReturnSpot s = DojoStore.get().returns.remove(p.m_20148_().toString());
        if (s == null) {
            return false;
        }
        DojoStore.get().markDirty();
        SPECTATING.remove(p.m_20148_());
        ServerLevel target = null;
        for (ServerLevel l : p.m_20194_().m_129785_()) {
            if (l.m_46472_().m_135782_().toString().equals(s.dimension)) {
                target = l;
            }
        }
        if (target == null) {
            target = p.m_20194_().m_129783_();
        }
        try {
            p.m_8999_(target, s.x, s.y, s.z, s.yaw, s.pitch);
            p.m_20256_(new Vec3(0.0, 0.0, 0.0));
            p.f_19789_ = 0.0f;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] dojo return failed for {}: {}", AdaptiveDifficultyMod.MOD_ID,
                    p.m_7755_().getString(), t.toString());
        }
        return true;
    }

    // ------------------------------------------------------------------ visits and spectating

    /** Teleport {@code p} into {@code d}'s plot (any player may visit). */
    public static String visit(ServerPlayer p, Dojo d) {
        if (d == null || !d.open) {
            return "That dojo is closed.";
        }
        ServerLevel level = level(p.m_20194_());
        if (level == null) {
            return NOT_LOADED;
        }
        if (BoardSpar.inBout(p.m_20148_()) || DojoWar.fighting(p.m_20148_())) {
            return "Finish your fight first.";
        }
        ensureBuilt(p.m_20194_(), d);
        if (DojoPlacer.busy(d.masterUuid)) {
            return d.name + " is being built. Try again in a few seconds.";
        }
        rememberSpot(p, "visit");
        toSpawn(p, level, d);
        return "Welcome to " + d.name + ". Tap Leave dojo in the menu to go back.";
    }

    public static String leave(ServerPlayer p) {
        if (BoardSpar.inBout(p.m_20148_()) || DojoWar.fighting(p.m_20148_())) {
            return "You can't leave in the middle of a fight. Forfeit from the Spar Board if you must.";
        }
        if (!returnPlayer(p)) {
            return inDojoWorld(p) ? "You have no saved spot to return to." : "You're not visiting a dojo.";
        }
        return "Back where you were.";
    }

    /** Players watching from a gallery: player to the dojo they watch. */
    static final Map<UUID, String> SPECTATING = new ConcurrentHashMap<>();

    public static boolean spectating(UUID id) {
        return id != null && SPECTATING.containsKey(id);
    }

    /** Watch {@code d}'s arenas from its spectator room (only when the dojo built one). */
    public static String spectate(ServerPlayer p, Dojo d) {
        if (d == null || !d.open) {
            return "That dojo is closed.";
        }
        if (DojoService.tier(d, DojoCatalog.SPECTATOR) <= 0) {
            return d.name + " has no spectator room, so its spars can't be watched.";
        }
        ServerLevel level = level(p.m_20194_());
        if (level == null) {
            return NOT_LOADED;
        }
        if (BoardSpar.inBout(p.m_20148_()) || DojoWar.fighting(p.m_20148_())) {
            return "You can't watch while you're fighting.";
        }
        ensureBuilt(p.m_20194_(), d);
        if (DojoPlacer.busy(d.masterUuid)) {
            return d.name + " is being built. Try again in a few seconds.";
        }
        rememberSpot(p, "spectate");
        toBalcony(p, level, d, 0.0f);
        SPECTATING.put(p.m_20148_(), d.masterUuid);
        return "Watching " + d.name + "'s rings from the balcony. You can't be hit or step into a ring. Leave from the menu.";
    }

    static void toSpawn(ServerPlayer p, ServerLevel level, Dojo d) {
        double[] s = DojoLayout.get().spawn;
        double[] w = world(d.plot, s[0], s[1], s[2]);
        teleport(p, level, w[0], w[1], w[2], (float) s[3]);
    }

    static void toBalcony(ServerPlayer p, ServerLevel level, Dojo d, float yaw) {
        double[] s = DojoLayout.get().spectate;
        double[] w = world(d.plot, s[0], s[1], s[2]);
        teleport(p, level, w[0], w[1], w[2], yaw);
    }

    /** The spectator balcony for {@code d}: {minX, minY, minZ, maxX, maxY, maxZ} in the world. */
    static double[] galleryBox(Dojo d) {
        int[] b = DojoLayout.get().balcony;
        double[] lo = world(d.plot, b[0], b[1], b[2]);
        double[] hi = world(d.plot, b[3] + 1, b[4] + 1, b[5] + 1);
        return new double[] {lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]};
    }

    /** Once a second: keep spectators in the gallery, catch anyone falling out of the dojo world. */
    public static void pulse(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        for (ServerPlayer p : new ArrayList<>(level.m_6907_())) {
            UUID id = p.m_20148_();
            String watching = SPECTATING.get(id);
            if (watching != null) {
                Dojo d = DojoService.byMaster(watching);
                if (d == null || !d.open) {
                    returnPlayer(p);
                    continue;
                }
                double[] b = galleryBox(d);
                if (p.m_20185_() < b[0] || p.m_20185_() > b[3] || p.m_20189_() < b[2] || p.m_20189_() > b[5]
                        || p.m_20186_() < b[1] - 0.5 || p.m_20186_() > b[4]) {
                    toBalcony(p, level, d, p.m_146908_());
                }
                continue;
            }
            if (p.m_20186_() < CATCH_Y && !BoardSpar.inBout(id) && !DojoWar.fighting(id)) {
                // A visitor who slipped off the plot: back to the nearest plot's path.
                int px = Math.max(0, Math.round((float) p.m_20185_() / PLOT_SPACING));
                int pz = Math.max(0, Math.round((float) p.m_20189_() / PLOT_SPACING));
                double[] s = DojoLayout.get().spawn;
                BlockPos c = plotCorner(pz * PLOTS_PER_ROW + px);
                teleport(p, level, c.m_123341_() + s[0], c.m_123342_() + s[1], c.m_123343_() + s[2], (float) s[3]);
            }
        }
    }

    // ------------------------------------------------------------------ building
    /** What {@code d}'s plot should be built as (DojoState's signature). */
    public static String signature(Dojo d) {
        return DojoState.of(d).signature();
    }

    /** Signatures being built (queued or running), by dojo. */
    static final Map<String, String> PENDING = new ConcurrentHashMap<>();
    /** The last post-build check per dojo ("OK" or what failed), for the test rig and admins. */
    public static final Map<String, String> LAST_CHECK = new ConcurrentHashMap<>();

    /**
     * Build or update {@code d}'s plot when its state changed since the last build: the whole plot for a new hall
     * tier, a new style or a first build; otherwise only the parts that changed. Placed over a few seconds by
     * {@link DojoPlacer}; anyone standing where a wall went up is put back on the path or the balcony.
     */
    public static void ensureBuilt(MinecraftServer server, Dojo d) {
        if (d == null) {
            return;
        }
        ensurePlot(d);
        ServerLevel level = level(server);
        if (level == null || DojoLayout.get() == null) {
            return;
        }
        DojoState now = DojoState.of(d);
        String sig = now.signature();
        String pending = PENDING.get(d.masterUuid);
        if (sig.equals(pending)) {
            return;
        }
        if (pending == null && sig.equals(DojoState.normalize(d.builtSignature))) {
            if (!sig.equals(d.builtSignature)) {
                d.builtSignature = sig;                 // 4.8.2: a pre-4.8.2 build with no banner pick is already right
                DojoStore.get().markDirty();
            }
            return;
        }
        DojoState was = DojoState.parse(pending != null ? pending : d.builtSignature);
        List<DojoPlacer.Step> steps = DojoPlacer.plan(was, now);
        PENDING.put(d.masterUuid, sig);
        String master = d.masterUuid;
        int plot = d.plot;
        DojoPlacer.Job job = new DojoPlacer.Job(master, d.name + " " + sig, level, plotCorner(plot), steps, () -> {
            Dojo cur = DojoService.byMaster(master);
            if (sig.equals(PENDING.get(master))) {
                PENDING.remove(master);
                if (cur != null) {
                    cur.builtSignature = sig;
                    DojoStore.get().markDirty();
                }
            }
            afterBuild(level, plot, master);
        });
        job.banner = DojoPlacer.design(d);              // 4.8.2: the hall's banner spots fly the picked banner
        DojoPlacer.submit(job);
    }

    /** True when {@code d}'s plot is built as its state says (nothing queued or running). */
    public static boolean ready(Dojo d) {
        return d != null && !DojoPlacer.busy(d.masterUuid) && signature(d).equals(DojoState.normalize(d.builtSignature));
    }

    /** After a build: the checks DMZ's grounds make (the floor is there, the landing points are clear); anyone inside
     *  a wall goes back to the path or the balcony. */
    static void afterBuild(ServerLevel level, int plot, String master) {
        DojoLayout l = DojoLayout.get();
        List<String> bad = new ArrayList<>();
        BlockPos c = plotCorner(plot);
        Dojo d = DojoService.byMaster(master);
        int built = d == null ? 1 : DojoService.arenas(d);
        for (int k = 0; k < l.rings.size(); k++) {
            int[] r = l.rings.get(k);
            if (level.m_8055_(c.m_7918_(r[0], l.floorY - 1, r[1])).m_60795_()) {
                bad.add("no floor at ring " + (k + 1));
            }
            if (k < built && !level.m_8055_(c.m_7918_(r[0], l.floorY, r[1])).m_60795_()) {
                bad.add("ring " + (k + 1) + " is not clear");
            }
        }
        boolean balcony = d != null && DojoService.tier(d, DojoCatalog.SPECTATOR) > 0;
        for (double[] pt : balcony ? new double[][] {l.spawn, l.spectate} : new double[][] {l.spawn}) {
            BlockPos feet = c.m_7918_((int) Math.floor(pt[0]), (int) pt[1], (int) Math.floor(pt[2]));
            if (!level.m_8055_(feet).m_60795_() || !level.m_8055_(feet.m_7494_()).m_60795_()
                    || level.m_8055_(feet.m_7495_()).m_60795_()) {
                bad.add("landing point not safe at " + feet.m_123344_());
            }
        }
        // 4.8.2: every banner spot of the hall flies the picked banner (base and every layer, in order); a banner the
        // master hung somewhere else in their hall is theirs and not checked
        DojoPlacer.BannerDesign pick = d == null || !signature(d).equals(DojoState.normalize(d.builtSignature))
                ? null : DojoPlacer.design(d);
        if (pick != null) {
            for (BlockPos at : bannerSpots(d).keySet()) {
                BannerSpot b = bannerAt(level, at);
                if (b == null || !b.matches(pick)) {
                    bad.add("banner at " + at.m_123344_() + " is not the dojo's banner");
                    break;
                }
            }
        }
        String result = bad.isEmpty() ? "OK" : String.join("; ", bad);
        LAST_CHECK.put(master, result);
        AdaptiveDifficultyMod.LOGGER.info("[{}] dojo plot {} checks: {}", AdaptiveDifficultyMod.MOD_ID, plot, result);
        if (d == null) {
            return;
        }
        for (ServerPlayer p : new ArrayList<>(level.m_6907_())) {
            BlockPos at = p.m_20183_();
            if (at.m_123341_() < c.m_123341_() || at.m_123341_() >= c.m_123341_() + l.sx
                    || at.m_123343_() < c.m_123343_() || at.m_123343_() >= c.m_123343_() + l.sz) {
                continue;
            }
            if (!level.m_8055_(at).m_60795_() || !level.m_8055_(at.m_7494_()).m_60795_()) {
                if (spectating(p.m_20148_())) {
                    toBalcony(p, level, d, p.m_146908_());
                } else {
                    toSpawn(p, level, d);
                }
            }
        }
    }

    // ------------------------------------------------------------------ banners in the hall (4.8.2)
    /**
     * One banner block in a built hall: its world position, wall or standing, its base colour (the block's dye name)
     * and its layers in order as "pattern hashname:dye id" (the form DojoFounding saves a pick in).
     */
    public record BannerSpot(BlockPos pos, boolean wall, String base, List<String> patterns) {
        /** True when this banner is exactly {@code pick}: the same base and the same layers in the same order. */
        public boolean matches(DojoPlacer.BannerDesign pick) {
            return pick != null && pick.base().m_41065_().equals(base) && pick.patternTexts().equals(patterns);
        }
    }

    /**
     * 4.8.2 inspection (test rig and admins): every banner block standing in plot {@code plot} of the dojo world, read
     * back from the placed block and its block entity (not from the dojo's saved fields), sorted by position. Loads
     * the plot's chunks if they are not loaded.
     */
    public static List<BannerSpot> bannersOn(ServerLevel level, int plot) {
        List<BannerSpot> out = new ArrayList<>();
        DojoLayout l = DojoLayout.get();
        if (level == null || l == null || plot < 0) {
            return out;
        }
        BlockPos c = plotCorner(plot);
        int x0 = c.m_123341_(), z0 = c.m_123343_(), x1 = x0 + l.sx - 1, z1 = z0 + l.sz - 1;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk chunk = level.m_6325_(cx, cz);
                for (BlockEntity be : new ArrayList<>(chunk.m_62954_().values())) {
                    BlockPos pos = be.m_58899_();
                    if (!(be instanceof BannerBlockEntity) || pos.m_123341_() < x0 || pos.m_123341_() > x1
                            || pos.m_123343_() < z0 || pos.m_123343_() > z1) {
                        continue;
                    }
                    BannerSpot b = bannerAt(level, pos);
                    if (b != null) {
                        out.add(b);
                    }
                }
            }
        }
        out.sort(java.util.Comparator.comparingLong(b -> b.pos().m_121878_()));
        return out;
    }

    /** 4.8.2 inspection: the banner standing at {@code pos} as placed (block and block entity), or null for none. */
    public static BannerSpot bannerAt(ServerLevel level, BlockPos pos) {
        BlockState st = level.m_8055_(pos);
        if (!(st.m_60734_() instanceof AbstractBannerBlock ab) || !(level.m_7702_(pos) instanceof BannerBlockEntity be)) {
            return null;
        }
        List<String> layers = new ArrayList<>();
        net.minecraft.nbt.ListTag list = be.m_187482_().m_128437_("Patterns", 10);
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag t = list.m_128728_(i);
            layers.add(t.m_128461_("Pattern") + ":" + t.m_128451_("Color"));
        }
        return new BannerSpot(pos.m_7949_(), st.m_60734_() instanceof WallBannerBlock, ab.m_48674_().m_41065_(),
                List.copyOf(layers));
    }

    /**
     * 4.8.2 inspection: where {@code d}'s hall has banner spots as its structures place them today (the whole plot
     * for its tier, style, rings and rooms; a later piece wins a spot), world position to the structure's own base
     * colour (the style's banner). Pure: reads the structure files, not the world.
     */
    public static Map<BlockPos, String> bannerSpots(Dojo d) {
        Map<BlockPos, String> out = new LinkedHashMap<>();
        if (d == null || d.plot < 0 || DojoLayout.get() == null) {
            return out;
        }
        BlockPos c = plotCorner(d.plot);
        for (DojoPlacer.Step step : DojoPlacer.plan(null, DojoState.of(d))) {
            DojoPlacer.Template t = DojoPlacer.template(step.path());
            if (t == null) {
                continue;
            }
            for (int i = 0; i < t.cells.length; i++) {
                int cell = t.cells[i];
                BlockPos pos = c.m_7918_(step.at()[0] + (cell & 0xFF), step.at()[1] + ((cell >> 8) & 0xFF),
                        step.at()[2] + ((cell >> 16) & 0xFF));
                int si = t.state[i];
                if (t.banner[si]) {
                    out.put(pos, ((AbstractBannerBlock) t.palette[si].m_60734_()).m_48674_().m_41065_());
                } else {
                    out.remove(pos);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the hall as text (no pictures)

    /** One cell of the text map: the label and the upgrade (with ring number) it shows. */
    record Cell(String label, String upgrade, int arena) {}

    /** The hall from north to south: the balcony, two rows of rings, the south wing, the grounds. */
    static final List<List<Cell>> MAP = List.of(
            List.of(new Cell("Balcony", DojoCatalog.SPECTATOR, 0)),
            List.of(new Cell("Ring 1", DojoCatalog.ARENA, 1), new Cell("Ring 2", DojoCatalog.ARENA, 2),
                    new Cell("Ring 3", DojoCatalog.ARENA, 3)),
            List.of(new Cell("Ring 4", DojoCatalog.ARENA, 4), new Cell("Ring 5", DojoCatalog.ARENA, 5),
                    new Cell("Ring 6", DojoCatalog.ARENA, 6)),
            List.of(new Cell("Training", DojoCatalog.TRAINING, 0), new Cell("Meditation", DojoCatalog.MEDITATION, 0),
                    new Cell("Trophies", DojoCatalog.TROPHY, 0), new Cell("Quarters", DojoCatalog.QUARTERS, 0)),
            List.of(new Cell("Garden", DojoCatalog.GARDEN, 0)));

    /**
     * The hall as body lines, north at the top: built parts in the value ink, empty spaces dim, and the part
     * {@code highlight} (after its next level when {@code preview}) marked with stars in the confirm ink. LM clients
     * that draw pictures show the floor plan instead.
     */
    public static List<String> mapLines(Dojo d, String highlight, boolean preview) {
        List<String> out = new ArrayList<>();
        int arenas = DojoService.arenas(d);
        for (List<Cell> row : MAP) {
            StringBuilder sb = new StringBuilder();
            for (Cell c : row) {
                boolean built;
                boolean hl;
                if (DojoCatalog.ARENA.equals(c.upgrade())) {
                    built = c.arena() <= arenas;
                    hl = preview && DojoCatalog.ARENA.equals(highlight) && c.arena() == arenas + 1;
                } else {
                    built = DojoService.tier(d, c.upgrade()) > 0;
                    hl = c.upgrade().equals(highlight);
                }
                String cell = hl ? "*" + c.label() + "*" : built ? "[" + c.label() + "]" : "[" + c.label() + " - empty]";
                String ink = hl ? com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.CONFIRM
                        : built ? com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.VALUE
                        : com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.DIM;
                sb.append(ink).append(cell).append(' ');
            }
            out.add(sb.toString().stripTrailing());
        }
        return out;
    }
}
