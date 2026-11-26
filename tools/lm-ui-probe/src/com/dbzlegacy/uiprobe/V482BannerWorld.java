package com.dbzlegacy.uiprobe;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoState;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.8.2 Part B (test only): the dojo in the world follows the picked banner. Stand-in masters found dojos in the
 * dojo world (like UiProbe.v88DojoRules), every build is finished at once with {@link DojoPlacer#finishNow()} (the
 * probe runs inside one server tick), and the banners are read back from the placed blocks and block entities
 * ({@link DojoWorld#bannersOn}), never from the dojo's saved fields.
 *
 * <p>Lines: {@code V482 WORLD <what>: PASS|FAIL | detail}.
 */
public final class V482BannerWorld {
    private V482BannerWorld() {}

    public static void run(ServerPlayer player, Consumer<String> note) {
        var server = player.m_20194_();
        ServerLevel lvl = DojoWorld.level(server);
        if (lvl == null || com.dbzlegacy.adaptivedifficulty.dojo.DojoLayout.get() == null) {
            note.accept("V482 WORLD the dojo world and its layout are loaded: FAIL | missing");
            return;
        }
        var store = DojoStore.get();
        java.util.function.Function<String, ServerPlayer> fake = n -> net.minecraftforge.common.util.FakePlayerFactory.get(lvl,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(("uiprobe482:" + n)
                        .getBytes(StandardCharsets.UTF_8)), n));
        ServerPlayer picker = fake.apply("ProbeBannerMaster");
        ServerPlayer plain = fake.apply("ProbePlainMaster");
        List<DojoStore.Dojo> made = new ArrayList<>();
        try {
            DojoStore.Dojo dPick = found(store, picker, "Probe Banner Hall", made);
            DojoStore.Dojo dPlain = found(store, plain, "Probe Plain Hall", made);
            DojoWorld.ensureBuilt(server, dPick);
            DojoWorld.ensureBuilt(server, dPlain);
            DojoPlacer.finishNow();

            // ---- no pick: the style's own banners, exactly as the structures place them (plain, style colour)
            Map<BlockPos, String> spots = DojoWorld.bannerSpots(dPlain);
            List<DojoWorld.BannerSpot> placed = DojoWorld.bannersOn(lvl, dPlain.plot);
            note.accept("V482 WORLD a KI hall has banner spots: " + pf(!spots.isEmpty()) + " | " + spots.size() + " spots");
            note.accept("V482 WORLD no pick: every spot keeps the style's plain banner: " + pf(DojoWorld.ready(dPlain)
                    && styleBanners(spots, placed)) + " | " + summary(placed));

            // ---- first pick: base + 3 layers, the hall rebuilds and every spot flies it
            Map<BlockPos, Map<?, ?>> shapes = shapes(lvl, DojoWorld.bannerSpots(dPick));
            List<String> first = List.of("bs:15", "cr:0", "mc:14");
            String msg = DojoService.setBanner(picker, "blue", first);
            boolean queued = DojoPlacer.busy(dPick.masterUuid);
            DojoPlacer.finishNow();
            DojoPlacer.BannerDesign pick = DojoPlacer.design(dPick);
            note.accept("V482 WORLD setBanner rebuilds the hall (the signature has a banner part): " + pf(queued
                    && pick != null && dPick.builtSignature.endsWith("|b=" + DojoState.bannerKey(dPick))
                    && !dPick.builtSignature.endsWith("|b=" + DojoState.NO_BANNER) && DojoWorld.ready(dPick))
                    + " | " + msg + " | " + dPick.builtSignature);
            placed = DojoWorld.bannersOn(lvl, dPick.plot);
            note.accept("V482 WORLD the placed banners match the pick exactly (base, 3 layers in order): "
                    + pf(matchesAll(DojoWorld.bannerSpots(dPick), placed, "blue", first)) + " | " + summary(placed));
            note.accept("V482 WORLD banners keep their shape (wall, facing): " + pf(sameShapes(lvl, shapes)));
            note.accept("V482 WORLD the build check passes with the banner: "
                    + pf("OK".equals(DojoWorld.LAST_CHECK.get(dPick.masterUuid))) + " | "
                    + DojoWorld.LAST_CHECK.get(dPick.masterUuid));
            note.accept("V482 WORLD the other hall is untouched by this pick: " + pf(styleBanners(spots,
                    DojoWorld.bannersOn(lvl, dPlain.plot))));

            // ---- a change: the blocks follow again
            dPick.identityChangedAt = 0L;
            dPick.points = Math.max(dPick.points, DojoCatalog.BANNER_CHANGE_COST + 5);
            List<String> second = List.of("ts:1", "gra:4");
            String msg2 = DojoService.setBanner(picker, "black", second);
            DojoPlacer.finishNow();
            placed = DojoWorld.bannersOn(lvl, dPick.plot);
            note.accept("V482 WORLD changing the banner updates every spot: " + pf(DojoWorld.ready(dPick)
                    && matchesAll(DojoWorld.bannerSpots(dPick), placed, "black", second)) + " | " + msg2 + " | "
                    + summary(placed));

            // ---- an upgrade after a pick: the new piece's banners fly the pick too
            dPick.upgrades.put(DojoCatalog.TROPHY, 2);
            DojoWorld.ensureBuilt(server, dPick);
            DojoPlacer.finishNow();
            placed = DojoWorld.bannersOn(lvl, dPick.plot);
            note.accept("V482 WORLD a room built after the pick flies the pick: " + pf(DojoWorld.ready(dPick)
                    && matchesAll(DojoWorld.bannerSpots(dPick), placed, "black", second)) + " | " + summary(placed));

            // ---- upgrade from a pre-4.8.2 build signature (no banner part)
            String legacyPlain = DojoState.of(dPlain).signature().replace("|b=" + DojoState.NO_BANNER, "");
            dPlain.builtSignature = legacyPlain;
            DojoWorld.ensureBuilt(server, dPlain);
            boolean plainBusy = DojoPlacer.busy(dPlain.masterUuid);
            note.accept("V482 WORLD an old build with no pick is not rebuilt (signature upgraded in place): " + pf(!plainBusy
                    && DojoWorld.ready(dPlain) && dPlain.builtSignature.endsWith("|b=" + DojoState.NO_BANNER))
                    + " | " + legacyPlain + " -> " + dPlain.builtSignature);
            String sig = DojoState.of(dPick).signature();
            dPick.builtSignature = sig.substring(0, sig.lastIndexOf("|b="));
            DojoWorld.ensureBuilt(server, dPick);
            boolean pickBusy = DojoPlacer.busy(dPick.masterUuid);
            DojoPlacer.finishNow();
            note.accept("V482 WORLD an old build with a pick rebuilds once so its banner goes up: " + pf(pickBusy
                    && DojoWorld.ready(dPick) && matchesAll(DojoWorld.bannerSpots(dPick),
                    DojoWorld.bannersOn(lvl, dPick.plot), "black", second)));
        } catch (Throwable t) {
            note.accept("V482 WORLD ERROR: FAIL | " + t);
        } finally {
            DojoPlacer.finishNow();
            for (DojoStore.Dojo d : made) {
                store.dojos.remove(d.masterUuid);
            }
            store.markDirty();
        }
    }

    private static DojoStore.Dojo found(DojoStore store, ServerPlayer m, String name, List<DojoStore.Dojo> made) {
        var d = new DojoStore.Dojo();
        d.masterUuid = m.m_20148_().toString();
        d.masterName = m.m_7755_().getString();
        d.name = name;
        d.open = true;
        d.style = "KI";
        d.foundedAt = System.currentTimeMillis();
        d.upgrades.put(DojoCatalog.ARENA, 1);
        store.dojos.put(d.masterUuid, d);
        made.add(d);
        return d;
    }

    /** Every expected spot holds a banner in the structure's colour with no layers, and no other banner stands there. */
    private static boolean styleBanners(Map<BlockPos, String> spots, List<DojoWorld.BannerSpot> placed) {
        Map<BlockPos, DojoWorld.BannerSpot> at = byPos(placed);
        if (spots.isEmpty() || !at.keySet().equals(spots.keySet())) {
            return false;
        }
        for (Map.Entry<BlockPos, String> e : spots.entrySet()) {
            DojoWorld.BannerSpot b = at.get(e.getKey());
            if (!b.base().equals(e.getValue()) || !b.patterns().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Every expected spot (and nothing else) holds {@code base} with exactly {@code layers}, in order. */
    private static boolean matchesAll(Map<BlockPos, String> spots, List<DojoWorld.BannerSpot> placed, String base,
            List<String> layers) {
        Map<BlockPos, DojoWorld.BannerSpot> at = byPos(placed);
        if (spots.isEmpty() || !at.keySet().equals(spots.keySet())) {
            return false;
        }
        for (DojoWorld.BannerSpot b : placed) {
            if (!b.base().equals(base) || !b.patterns().equals(layers)) {
                return false;
            }
        }
        return true;
    }

    private static Map<BlockPos, DojoWorld.BannerSpot> byPos(List<DojoWorld.BannerSpot> placed) {
        Map<BlockPos, DojoWorld.BannerSpot> m = new HashMap<>();
        for (DojoWorld.BannerSpot b : placed) {
            m.put(b.pos(), b);
        }
        return m;
    }

    /** Each spot's block properties (facing / rotation), to compare after a recolour. */
    private static Map<BlockPos, Map<?, ?>> shapes(ServerLevel lvl, Map<BlockPos, String> spots) {
        Map<BlockPos, Map<?, ?>> m = new HashMap<>();
        for (BlockPos p : spots.keySet()) {
            m.put(p, lvl.m_8055_(p).m_61148_());
        }
        return m;
    }

    private static boolean sameShapes(ServerLevel lvl, Map<BlockPos, Map<?, ?>> before) {
        if (before.isEmpty()) {
            return false;
        }
        for (Map.Entry<BlockPos, Map<?, ?>> e : before.entrySet()) {
            if (!lvl.m_8055_(e.getKey()).m_61148_().equals(e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static String summary(List<DojoWorld.BannerSpot> placed) {
        if (placed.isEmpty()) {
            return "no banners";
        }
        DojoWorld.BannerSpot b = placed.get(0);
        return placed.size() + " banners, first " + b.pos().m_123344_() + " " + (b.wall() ? "wall " : "standing ")
                + b.base() + " " + b.patterns();
    }

    private static String pf(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }
}
