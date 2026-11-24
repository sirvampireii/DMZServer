package com.dbzlegacy.adaptivedifficulty.dojo;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * LM 4.7.68 dojo dimension ({@code legacymechanics:dojo}, a void world from the datapack in the LM jar): one plot per
 * dojo on a grid {@value #PLOT_SPACING} blocks apart (plots are never reused, so builds never meet), war rings on
 * their own row, the plot layout every menu map and every build share, visits, the spectator gallery, and putting
 * players back exactly where they stood.
 *
 * <p>Plot layout (offsets from the plot origin, floor at {@value #FLOOR_Y}): six arena slots in two rows of three to
 * the north, the spectator gallery above them, the courtyard and garden in the middle, the training hall, meditation
 * room and trophy hall to the south, master quarters and the disciple bunks behind those.
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
    public static final int ARENA_RADIUS = 12;
    public static final int ROOM_RADIUS = 9;
    public static final int ISLAND_X = 80;
    public static final int ISLAND_Z_MIN = -110;
    public static final int ISLAND_Z_MAX = 100;
    public static final int GALLERY_Y = FLOOR_Y + 16;
    /** Below this a player in the dojo world is caught (no void deaths). */
    public static final int CATCH_Y = FLOOR_Y - 20;

    /** Where a room sits on the plot (x, z offsets). Arenas use {@link #arenaCenter}. */
    public static int[] roomOffset(String room) {
        return switch (room) {
            case DojoCatalog.TRAINING -> new int[] {-48, 40};
            case DojoCatalog.MEDITATION -> new int[] {0, 40};
            case DojoCatalog.TROPHY -> new int[] {48, 40};
            case DojoCatalog.QUARTERS -> new int[] {-30, 78};
            case DojoCatalog.CAPACITY -> new int[] {30, 78};
            case DojoCatalog.GARDEN -> new int[] {0, 0};
            default -> new int[] {0, 0};
        };
    }

    /** Arena slot {@code k} (0..5) center offset. */
    public static int[] arenaCenter(int k) {
        return new int[] {-48 + (k % 3) * 48, -50 - (k / 3) * 40};
    }

    public static BlockPos plotOrigin(int plot) {
        int p = Math.max(0, plot);
        return new BlockPos((p % PLOTS_PER_ROW) * PLOT_SPACING, FLOOR_Y, (p / PLOTS_PER_ROW) * PLOT_SPACING);
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

    // ------------------------------------------------------------------ teleport and return

    static void teleport(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw) {
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
        rememberSpot(p, "visit");
        BlockPos o = plotOrigin(d.plot);
        teleport(p, level, o.m_123341_() + 0.5, FLOOR_Y + 1, o.m_123343_() + 20.5, 180.0f);
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
        rememberSpot(p, "spectate");
        BlockPos o = plotOrigin(d.plot);
        teleport(p, level, o.m_123341_() + 0.5, GALLERY_Y + 1, o.m_123343_() - 30.5, 180.0f);
        SPECTATING.put(p.m_20148_(), d.masterUuid);
        return "Watching " + d.name + "'s arenas. You can't be hit or step into a ring. Leave from the menu.";
    }

    /** Gallery bounds for {@code d}: {minX, minY, minZ, maxX, maxY, maxZ}. */
    static double[] galleryBox(Dojo d) {
        BlockPos o = plotOrigin(d.plot);
        return new double[] {o.m_123341_() - 65, GALLERY_Y, o.m_123343_() - 109, o.m_123341_() + 66, GALLERY_Y + 6,
                o.m_123343_() - 28};
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
                        || p.m_20186_() < b[1] || p.m_20186_() > b[4]) {
                    BlockPos o = plotOrigin(d.plot);
                    teleport(p, level, o.m_123341_() + 0.5, GALLERY_Y + 1, o.m_123343_() - 30.5, p.m_146908_());
                }
                continue;
            }
            if (p.m_20186_() < CATCH_Y && !BoardSpar.inBout(id) && !DojoWar.fighting(id)) {
                // A visitor who slipped off the island: back onto the nearest plot's courtyard.
                int px = Math.max(0, Math.round((float) p.m_20185_() / PLOT_SPACING));
                int pz = Math.max(0, Math.round((float) p.m_20189_() / PLOT_SPACING));
                teleport(p, level, px * PLOT_SPACING + 0.5, FLOOR_Y + 1, pz * PLOT_SPACING + 20.5, 180.0f);
            }
        }
    }

    // ------------------------------------------------------------------ building

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

    /** What a plot is built at: every upgrade tier, so a purchase or a reopen rebuilds it. */
    public static String signature(Dojo d) {
        StringBuilder sb = new StringBuilder("v1");
        for (String up : DojoCatalog.UPGRADES) {
            sb.append('|').append(up).append('=').append(DojoService.tier(d, up));
        }
        sb.append("|trophies=").append(Math.min(6, d.warWins));
        return sb.toString();
    }

    /** Build (or rebuild) {@code d}'s plot when its upgrades changed since the last build. */
    public static void ensureBuilt(MinecraftServer server, Dojo d) {
        if (d == null) {
            return;
        }
        ensurePlot(d);
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        String sig = signature(d);
        if (sig.equals(d.builtSignature)) {
            return;
        }
        long t0 = System.nanoTime();
        try {
            DojoBuilder.build(level, plotOrigin(d.plot), d, d.builtSignature.isBlank());
            d.builtSignature = sig;
            DojoStore.get().markDirty();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error("[{}] dojo build failed for {}: {}", AdaptiveDifficultyMod.MOD_ID,
                    d.name, t.toString());
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] built dojo {} at plot {} in {} ms", AdaptiveDifficultyMod.MOD_ID,
                d.name, d.plot, (System.nanoTime() - t0) / 1_000_000L);
    }

    // ------------------------------------------------------------------ layout map (the upgrade preview)

    /** One cell of the top-down map: the label and the upgrade (with arena index) it shows. */
    record Cell(String label, String upgrade, int arena) {}

    static final List<List<Cell>> MAP = List.of(
            List.of(new Cell("Arena 4", DojoCatalog.ARENA, 4), new Cell("Arena 5", DojoCatalog.ARENA, 5),
                    new Cell("Arena 6", DojoCatalog.ARENA, 6)),
            List.of(new Cell("Arena 1", DojoCatalog.ARENA, 1), new Cell("Arena 2", DojoCatalog.ARENA, 2),
                    new Cell("Arena 3", DojoCatalog.ARENA, 3)),
            List.of(new Cell("Gallery", DojoCatalog.SPECTATOR, 0), new Cell("Garden", DojoCatalog.GARDEN, 0),
                    new Cell("Gate", "", 0)),
            List.of(new Cell("Training", DojoCatalog.TRAINING, 0), new Cell("Meditate", DojoCatalog.MEDITATION, 0),
                    new Cell("Trophies", DojoCatalog.TROPHY, 0)),
            List.of(new Cell("Quarters", DojoCatalog.QUARTERS, 0), new Cell("Bunks", DojoCatalog.CAPACITY, 0),
                    new Cell("", "", 0)));

    /**
     * The top-down layout map as body lines: built rooms in the value ink, empty slots dim, and the room
     * {@code highlight} (after its next level when {@code preview}) marked with stars in the confirm ink.
     */
    public static List<String> mapLines(Dojo d, String highlight, boolean preview) {
        List<String> out = new ArrayList<>();
        int arenas = DojoService.arenas(d) + (preview && DojoCatalog.ARENA.equals(highlight) ? 1 : 0);
        for (List<Cell> row : MAP) {
            StringBuilder sb = new StringBuilder();
            for (Cell c : row) {
                if (c.label().isBlank()) {
                    continue;
                }
                boolean built;
                boolean hl;
                if (DojoCatalog.ARENA.equals(c.upgrade())) {
                    built = c.arena() <= arenas;
                    hl = DojoCatalog.ARENA.equals(highlight) && c.arena() == arenas && preview;
                } else if (c.upgrade().isBlank()) {
                    built = true;
                    hl = false;
                } else {
                    int t = DojoService.tier(d, c.upgrade()) + (preview && c.upgrade().equals(highlight) ? 1 : 0);
                    built = t > 0;
                    hl = c.upgrade().equals(highlight);
                }
                String cell = hl ? "*" + c.label() + "*" : "[" + c.label() + "]";
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
