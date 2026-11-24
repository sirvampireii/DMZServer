package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * LM 4.7.68: places dojo structures (vanilla structure files from DojoKit, in the LM jar) into the dojo world, the
 * way DragonMineZ places its tournament grounds: the plot's chunks are held with a ticket while it builds, blocks go
 * in under a per-tick time budget (one structure file after another, in order), and the build is checked when it
 * finishes. One job at a time; a newer request for the same dojo replaces a queued one.
 *
 * <p>A step places one structure at a plot position, optionally only inside some plot boxes (that is how a part is
 * cleared back to the empty hall: its boxes from the base structure).
 */
public final class DojoPlacer {
    private DojoPlacer() {}

    /** Time per tick the dojo builder may use. */
    static final long TICK_BUDGET_NANOS = 6_000_000L;
    static final TicketType<ChunkPos> TICKET = TicketType.m_9462_("legacymechanics_dojo_build",
            Comparator.comparingLong(c -> ChunkPos.m_45589_(c.f_45578_, c.f_45579_)));
    /** Block update flags: send to clients (2), shapes are already right in the structure (16). */
    static final int FLAGS = 2 | 16;

    // ------------------------------------------------------------------ structures (parsed once)
    /** A parsed structure: each block's position packed (x | y << 8 | z << 16) and its palette index. */
    static final class Template {
        int sx, sy, sz;
        int[] cells;
        short[] state;
        BlockState[] palette;
    }

    /** The last few structures read (a base hall is ~670k cells: keep only what builds are using). */
    private static final Map<String, Template> TEMPLATES = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Template> e) {
            return size() > 12;
        }
    };

    /** The structure {@code path} under data/legacymechanics/structures/dojo/ (without .nbt), or null. */
    static synchronized Template template(String path) {
        Template t = TEMPLATES.get(path);
        if (t == null) {
            t = read(path);
            if (t != null) {
                TEMPLATES.put(path, t);
            }
        }
        return t;
    }

    private static Template read(String path) {
        String res = DojoLayout.STRUCTURES + path + ".nbt";
        try (InputStream in = DojoPlacer.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) {
                AdaptiveDifficultyMod.LOGGER.error("[{}] dojo structure missing: {}", AdaptiveDifficultyMod.MOD_ID, res);
                return null;
            }
            CompoundTag nbt = NbtIo.m_128939_(in);
            Template t = new Template();
            ListTag size = nbt.m_128437_("size", 3);
            t.sx = size.m_128763_(0);
            t.sy = size.m_128763_(1);
            t.sz = size.m_128763_(2);
            ListTag pal = nbt.m_128437_("palette", 10);
            t.palette = new BlockState[pal.size()];
            for (int i = 0; i < t.palette.length; i++) {
                t.palette[i] = state(pal.m_128728_(i));
            }
            ListTag blocks = nbt.m_128437_("blocks", 10);
            int n = blocks.size();
            t.cells = new int[n];
            t.state = new short[n];
            for (int i = 0; i < n; i++) {
                CompoundTag b = blocks.m_128728_(i);
                ListTag pos = b.m_128437_("pos", 3);
                t.cells[i] = pos.m_128763_(0) | pos.m_128763_(1) << 8 | pos.m_128763_(2) << 16;
                t.state[i] = (short) b.m_128451_("state");
            }
            return t;
        } catch (Exception e) {
            AdaptiveDifficultyMod.LOGGER.error("[{}] dojo structure unreadable {}: {}", AdaptiveDifficultyMod.MOD_ID, res,
                    e.toString());
            return null;
        }
    }

    private static final Map<String, BlockState> PARSED = new ConcurrentHashMap<>();

    /** A palette entry ({Name, Properties}) as a block state: the block from the registry, each property set from
     *  its name (an unknown block becomes air, an unknown property keeps the block's default). */
    static BlockState state(CompoundTag entry) {
        String name = entry.m_128461_("Name");
        TreeMap<String, String> props = new TreeMap<>();
        if (entry.m_128441_("Properties")) {
            CompoundTag p = entry.m_128469_("Properties");
            for (String k : p.m_128431_()) {
                props.put(k, p.m_128461_(k));
            }
        }
        String key = name + props;
        return PARSED.computeIfAbsent(key, k -> {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name));
            if (block == null) {
                AdaptiveDifficultyMod.LOGGER.warn("[{}] dojo block {} unknown, using air", AdaptiveDifficultyMod.MOD_ID, name);
                return Blocks.f_50016_.m_49966_();
            }
            BlockState st = block.m_49966_();
            for (Map.Entry<String, String> e : props.entrySet()) {
                Property<?> prop = block.m_49965_().m_61081_(e.getKey());
                if (prop != null) {
                    st = with(st, prop, e.getValue());
                }
            }
            return st;
        });
    }

    private static <T extends Comparable<T>> BlockState with(BlockState st, Property<T> prop, String value) {
        java.util.Optional<T> v = prop.m_6215_(value);
        return v.isPresent() ? st.m_61124_(prop, v.get()) : st;
    }

    // ------------------------------------------------------------------ jobs
    /** One structure at plot position {at}, only inside {@code within} (plot boxes) when that is not null. */
    record Step(String path, int[] at, List<int[]> within) {}

    static final class Job {
        final String dojo;
        final String label;
        final ServerLevel level;
        final BlockPos origin;                          // the plot structure's (0, 0, 0) in the world
        final Deque<Step> steps = new ArrayDeque<>();
        final Runnable done;
        Template cur;
        Step step;
        int index;
        boolean ticketed;
        final Set<ChunkPos> chunks = new LinkedHashSet<>();
        long placed;
        long startedAt;

        Job(String dojo, String label, ServerLevel level, BlockPos origin, List<Step> steps, Runnable done) {
            this.dojo = dojo;
            this.label = label;
            this.level = level;
            this.origin = origin;
            this.steps.addAll(steps);
            this.done = done;
        }
    }

    private static final Deque<Job> QUEUE = new ArrayDeque<>();
    private static Job active;

    /** Queue a build of {@code dojo}'s plot; a queued (not yet started) job for the same dojo is replaced. */
    static synchronized void submit(Job job) {
        QUEUE.removeIf(j -> j.dojo.equals(job.dojo));
        QUEUE.addLast(job);
    }

    /** True while a build of {@code dojo} is queued or running. */
    public static synchronized boolean busy(String dojo) {
        if (active != null && active.dojo.equals(dojo)) {
            return true;
        }
        for (Job j : QUEUE) {
            if (j.dojo.equals(dojo)) {
                return true;
            }
        }
        return false;
    }

    /** Every server tick: place blocks for the running job until the time budget is used. */
    public static void tick() {
        Job j = next();
        if (j == null) {
            return;
        }
        if (!j.ticketed) {
            hold(j);
            j.ticketed = true;
            return;                                     // chunks load before the next tick
        }
        ServerChunkCache cache = j.level.m_7726_();
        for (ChunkPos c : j.chunks) {
            if (cache.m_7131_(c.f_45578_, c.f_45579_) == null) {
                return;                                 // still loading
            }
        }
        work(j, System.nanoTime() + TICK_BUDGET_NANOS);
    }

    /**
     * Test rig (the UI probe runs its checks on the server thread, inside one tick): finish every queued build now,
     * loading the plot's chunks directly. Never called by gameplay code.
     */
    public static void finishNow() {
        for (int guard = 0; guard < 64; guard++) {
            Job j = next();
            if (j == null) {
                return;
            }
            if (!j.ticketed) {
                hold(j);
                j.ticketed = true;
            }
            for (ChunkPos c : j.chunks) {
                j.level.m_6325_(c.f_45578_, c.f_45579_);
            }
            work(j, Long.MAX_VALUE);
        }
    }

    private static synchronized Job next() {
        if (active == null) {
            active = QUEUE.pollFirst();
            if (active != null) {
                active.startedAt = System.currentTimeMillis();
            }
        }
        return active;
    }

    /** Place {@code j}'s blocks until {@code end} (System.nanoTime) or until it is done. */
    private static void work(Job j, long end) {
        try {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (System.nanoTime() < end) {
                if (j.cur == null) {
                    j.step = j.steps.pollFirst();
                    if (j.step == null) {
                        finish(j);
                        return;
                    }
                    j.cur = template(j.step.path());
                    j.index = 0;
                    if (j.cur == null) {
                        continue;
                    }
                }
                Template t = j.cur;
                int stop = Math.min(t.cells.length, j.index + 2048);
                for (int i = j.index; i < stop; i++) {
                    int c = t.cells[i];
                    int px = j.step.at()[0] + (c & 0xFF), py = j.step.at()[1] + ((c >> 8) & 0xFF),
                            pz = j.step.at()[2] + ((c >> 16) & 0xFF);
                    if (j.step.within() != null && !inside(j.step.within(), px, py, pz)) {
                        continue;
                    }
                    pos.m_122178_(j.origin.m_123341_() + px, j.origin.m_123342_() + py, j.origin.m_123343_() + pz);
                    j.level.m_7731_(pos, t.palette[t.state[i]], FLAGS);
                    j.placed++;
                }
                j.index = stop;
                if (j.index >= t.cells.length) {
                    j.cur = null;
                }
            }
        } catch (Throwable e) {
            AdaptiveDifficultyMod.LOGGER.error("[{}] dojo build {} failed: {}", AdaptiveDifficultyMod.MOD_ID, j.label,
                    e.toString());
            release(j);
            synchronized (DojoPlacer.class) {
                active = null;
            }
        }
    }

    static boolean inside(List<int[]> boxes, int x, int y, int z) {
        for (int[] b : boxes) {
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
                return true;
            }
        }
        return false;
    }

    private static void hold(Job j) {
        DojoLayout l = DojoLayout.get();
        int x0 = j.origin.m_123341_() >> 4, z0 = j.origin.m_123343_() >> 4;
        int x1 = (j.origin.m_123341_() + l.sx - 1) >> 4, z1 = (j.origin.m_123343_() + l.sz - 1) >> 4;
        ServerChunkCache cache = j.level.m_7726_();
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                ChunkPos c = new ChunkPos(cx, cz);
                j.chunks.add(c);
                cache.m_8387_(TICKET, c, 0, c);
            }
        }
    }

    private static void release(Job j) {
        ServerChunkCache cache = j.level.m_7726_();
        for (ChunkPos c : j.chunks) {
            cache.m_8438_(TICKET, c, 0, c);
        }
    }

    private static void finish(Job j) {
        release(j);
        AdaptiveDifficultyMod.LOGGER.info("[{}] dojo build {} done: {} blocks in {} ms", AdaptiveDifficultyMod.MOD_ID,
                j.label, j.placed, System.currentTimeMillis() - j.startedAt);
        synchronized (DojoPlacer.class) {
            active = null;
        }
        if (j.done != null) {
            try {
                j.done.run();
            } catch (Throwable e) {
                AdaptiveDifficultyMod.LOGGER.error("[{}] dojo build {} check failed: {}", AdaptiveDifficultyMod.MOD_ID,
                        j.label, e.toString());
            }
        }
    }

    // ------------------------------------------------------------------ what to place
    /** The parts a dojo state is made of, in placing order, with their level (0 = not built). */
    static Map<String, Integer> parts(DojoState s) {
        Map<String, Integer> m = new java.util.LinkedHashMap<>();
        for (int k = 1; k <= DojoCatalog.MAX_ARENAS; k++) {
            m.put("arena_" + k, s.arenas >= k ? 1 : 0);
        }
        m.put("training_hall", s.training);
        m.put("meditation", s.meditation);
        m.put("trophy_hall", s.trophy);
        m.put("quarters", s.quarters);
        m.put("spectator", s.spectator);
        m.put("garden", s.garden);
        return m;
    }

    static String pieceName(String part, int level) {
        return part.startsWith("arena_") ? part : part + "_" + level;
    }

    /**
     * The steps from {@code was} (null: nothing there) to {@code now}. A new hall tier or style, or no build yet,
     * places the whole plot; otherwise each changed part's boxes go back to the empty hall and every built part that
     * touches them is placed again.
     */
    static List<Step> plan(DojoState was, DojoState now) {
        DojoLayout l = DojoLayout.get();
        String key = now.key();
        List<Step> steps = new ArrayList<>();
        int[] zero = {0, 0, 0};
        Map<String, Integer> want = parts(now);
        if (was == null || !was.key().equals(key)) {
            steps.add(new Step(key + "/base", zero, null));
            for (Map.Entry<String, Integer> e : want.entrySet()) {
                addPiece(steps, l, key, e.getKey(), e.getValue());
            }
            return steps;
        }
        Map<String, Integer> had = parts(was);
        List<int[]> cleared = new ArrayList<>();
        for (Map.Entry<String, Integer> e : want.entrySet()) {
            if (!e.getValue().equals(had.get(e.getKey()))) {
                List<int[]> boxes = l.boxes.get(e.getKey());
                steps.add(new Step(key + "/base", zero, boxes));
                cleared.addAll(boxes);
            }
        }
        if (cleared.isEmpty()) {
            return steps;
        }
        for (Map.Entry<String, Integer> e : want.entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            boolean touches = false;
            for (int[] b : l.boxes.get(e.getKey())) {
                for (int[] c : cleared) {
                    touches |= DojoLayout.meet(b, c);
                }
            }
            if (touches) {
                addPiece(steps, l, key, e.getKey(), e.getValue());
            }
        }
        return steps;
    }

    private static void addPiece(List<Step> steps, DojoLayout l, String key, String part, int level) {
        if (level <= 0) {
            return;
        }
        String name = key + "/" + pieceName(part, Math.min(3, level));
        int[] at = l.pieces.get(name);
        if (at != null) {
            steps.add(new Step(name, at, null));
        }
    }

    /** Self-test (mod load): a full plan and a one-room change plan the right steps. */
    public static String selfTest() {
        DojoState start = DojoState.parse("v2|t=1|s=none|a=1|0|0|0|0|0|0");
        DojoState more = DojoState.parse("v2|t=1|s=none|a=1|1|0|0|0|0|0");
        List<Step> full = plan(null, start);
        List<Step> one = plan(start, more);
        if (full.size() != 2 || !full.get(0).path().equals("t1_none/base") || !full.get(1).path().equals("t1_none/arena_1")) {
            return "FAIL full " + full;
        }
        // the training hall's box back to the empty hall, then the training hall (it touches nothing built)
        if (one.size() != 2 || one.get(0).within() == null || !one.get(1).path().equals("t1_none/training_hall_1")) {
            return "FAIL one room " + one;
        }
        return "OK (" + full.size() + " + " + one.size() + " steps)";
    }

}
