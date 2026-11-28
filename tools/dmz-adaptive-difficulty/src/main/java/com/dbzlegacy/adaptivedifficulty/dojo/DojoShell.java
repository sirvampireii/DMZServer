package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LM 4.8.4 (D): the invisible barrier shell round every dojo plot. {@value #LAYERS} layers of barrier blocks on the
 * four sides of the plot box, the full plot height, and across its top, so nobody walks or flies out of a hall and
 * its garden; teleports (Visit, return, the balcony, commands) pass as before. The shell is the plot boundary, not a
 * wall round the hall: it sits in the outermost cells of the plot box ({@link DojoLayout} sx / sy / sz, never
 * fixed numbers), inside the box {@link DojoWorld#clearPlot} clears, so a disband takes it away with the plot.
 *
 * <p>Placed by code at the end of every build and rebuild of the plot ({@link DojoPlacer.CodeWork}, under the
 * builder's time budget), like the banners: only into air, so it never covers a block of the hall or the garden,
 * and never round a landing point (the Visit spawn and the balcony). The bottom stays open: below the plot the
 * dojo world's fall catch ({@link DojoWorld#pulse}) works as before.
 */
public final class DojoShell {
    private DojoShell() {}

    public static final int LAYERS = 3;

    private static DojoLayout cachedFor;
    private static int[] cached;

    /** Plot positions packed x | y &lt;&lt; 10 | z &lt;&lt; 20 (plots stay under 1024 blocks a side). */
    static int pack(int x, int y, int z) {
        return x | y << 10 | z << 20;
    }

    /** True when plot position (x, y, z) is in the shell: the outer {@value #LAYERS} layers of the sides or the top. */
    public static boolean inShell(DojoLayout l, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= l.sx || y >= l.sy || z >= l.sz) {
            return false;
        }
        return x < LAYERS || x >= l.sx - LAYERS || z < LAYERS || z >= l.sz - LAYERS || y >= l.sy - LAYERS;
    }

    /** True when (x, y, z) is next to a landing point (Visit spawn or balcony): the shell leaves that space free. */
    public static boolean nearLanding(DojoLayout l, int x, int y, int z) {
        for (double[] pt : new double[][] {l.spawn, l.spectate}) {
            int px = (int) Math.floor(pt[0]);
            int py = (int) Math.floor(pt[1]);
            int pz = (int) Math.floor(pt[2]);
            if (Math.abs(x - px) <= 1 && Math.abs(z - pz) <= 1 && y >= py - 1 && y <= py + 2) {
                return true;
            }
        }
        return false;
    }

    /** Every shell cell of the layout (packed), computed once per layout. */
    static synchronized int[] cells(DojoLayout l) {
        if (cachedFor == l && cached != null) {
            return cached;
        }
        int n = 0;
        int[] buf = new int[l.sx * l.sy * l.sz];
        for (int y = 0; y < l.sy; y++) {
            for (int z = 0; z < l.sz; z++) {
                for (int x = 0; x < l.sx; x++) {
                    if (inShell(l, x, y, z) && !nearLanding(l, x, y, z)) {
                        buf[n++] = pack(x, y, z);
                    }
                }
            }
        }
        cached = java.util.Arrays.copyOf(buf, n);
        cachedFor = l;
        return cached;
    }

    /** The shell as code work for a plot build: every shell cell that is air becomes a barrier. */
    static DojoPlacer.CodeWork work() {
        return new DojoPlacer.CodeWork() {
            int index;

            @Override
            public boolean work(DojoPlacer.Job j, long end, BlockPos.MutableBlockPos pos) {
                DojoLayout l = DojoLayout.get();
                if (l == null) {
                    return true;
                }
                if (j.dojo.startsWith(HEAL) && DojoService.byMaster(j.dojo.substring(HEAL.length())) == null) {
                    return true;                        // disbanded while the heal waited: leave the plot empty
                }
                int[] c = cells(l);
                BlockState barrier = Blocks.f_50375_.m_49966_();
                while (index < c.length) {
                    if (System.nanoTime() >= end) {
                        return false;
                    }
                    int stop = Math.min(c.length, index + 512);
                    for (int i = index; i < stop; i++) {
                        int v = c[i];
                        pos.m_122178_(j.origin.m_123341_() + (v & 0x3FF), j.origin.m_123342_() + ((v >> 10) & 0x3FF),
                                j.origin.m_123343_() + ((v >> 20) & 0x3FF));
                        if (j.level.m_8055_(pos).m_60795_()) {
                            j.level.m_7731_(pos, barrier, DojoPlacer.FLAGS);
                            j.placed++;
                        }
                    }
                    index = stop;
                }
                return true;
            }
        };
    }

    /** Job name prefix of a shell-only job (a hall built before 4.8.4 gets its shell once). */
    static final String HEAL = "shell:";
    private static final Set<String> HEALED = ConcurrentHashMap.newKeySet();

    /**
     * A hall that is already up to date (built before the shell existed, or its shell damaged by staff) gets the shell
     * once per server run: a shell-only job that fills any missing barrier. It runs under its own name, so a Visit or
     * a spar never waits for it.
     */
    static void heal(MinecraftServer server, Dojo d) {
        if (d == null || d.plot < 0 || !HEALED.add(d.masterUuid)) {
            return;
        }
        ServerLevel level = DojoWorld.level(server);
        if (level == null || DojoLayout.get() == null) {
            HEALED.remove(d.masterUuid);
            return;
        }
        DojoPlacer.Job job = new DojoPlacer.Job(HEAL + d.masterUuid, d.name + " shell", level,
                DojoWorld.plotCorner(d.plot), java.util.List.of(), null);
        job.code.add(work());
        DojoPlacer.submit(job);
    }

    /**
     * Test rig and admins: what stands in the shell cells of plot {@code plot}: {barriers, other blocks (the hall or
     * garden at the edge), air (gaps)}. A whole shell has no air. Loads the plot's chunks.
     */
    public static int[] inspect(ServerLevel level, int plot) {
        DojoLayout l = DojoLayout.get();
        int[] out = new int[3];
        if (level == null || l == null || plot < 0) {
            return out;
        }
        BlockPos c = DojoWorld.plotCorner(plot);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int v : cells(l)) {
            pos.m_122178_(c.m_123341_() + (v & 0x3FF), c.m_123342_() + ((v >> 10) & 0x3FF), c.m_123343_() + ((v >> 20) & 0x3FF));
            BlockState s = level.m_8055_(pos);
            out[s.m_60713_(Blocks.f_50375_) ? 0 : s.m_60795_() ? 2 : 1]++;
        }
        return out;
    }

    /** Test rig: barrier blocks anywhere in plot {@code plot}'s box (0 once a disband cleared it). */
    public static int barriersIn(ServerLevel level, int plot) {
        DojoLayout l = DojoLayout.get();
        if (level == null || l == null || plot < 0) {
            return 0;
        }
        int n = 0;
        BlockPos c = DojoWorld.plotCorner(plot);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < l.sy; y++) {
            for (int z = 0; z < l.sz; z++) {
                for (int x = 0; x < l.sx; x++) {
                    pos.m_122178_(c.m_123341_() + x, c.m_123342_() + y, c.m_123343_() + z);
                    if (level.m_8055_(pos).m_60713_(Blocks.f_50375_)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Self-test (mod load): the layout's shell is {@value #LAYERS} deep on every side and the top, and leaves the
     *  landing points free. */
    public static String selfTest() {
        DojoLayout l = DojoLayout.get();
        if (l == null) {
            return "FAIL no layout";
        }
        int mx = l.sx / 2;
        int mz = l.sz / 2;
        int my = l.sy / 2;
        for (int k = 0; k < LAYERS; k++) {
            if (!inShell(l, k, my, mz) || !inShell(l, l.sx - 1 - k, my, mz) || !inShell(l, mx, my, k)
                    || !inShell(l, mx, my, l.sz - 1 - k) || !inShell(l, mx, l.sy - 1 - k, mz)) {
                return "FAIL layer " + (k + 1);
            }
        }
        if (inShell(l, LAYERS, my, LAYERS) || inShell(l, mx, 0, mz)) {
            return "FAIL shell inside the plot";
        }
        for (double[] pt : new double[][] {l.spawn, l.spectate}) {
            int x = (int) Math.floor(pt[0]);
            int y = (int) Math.floor(pt[1]);
            int z = (int) Math.floor(pt[2]);
            if (!nearLanding(l, x, y, z) || !nearLanding(l, x, y + 1, z)) {
                return "FAIL landing point";
            }
        }
        return "OK (" + cells(l).length + " cells, " + LAYERS + " layers)";
    }
}
