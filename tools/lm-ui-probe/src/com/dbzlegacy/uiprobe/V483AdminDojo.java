package com.dbzlegacy.uiprobe;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoLayout;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWar;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.8.3 Part A probe (test only): every {@code /lm admin dojo} leaf and {@code /lm admin clear <player> dojo}, each
 * run once from the console (output captured) and once as the staff probe player (state checked). Stand-in masters
 * and a disciple are fake players put on the player list (like UiProbe's dojo stand-ins); their two dojos are founded
 * with the new {@code found} leaf and removed with the new {@code disband} leaf, which proves both.
 *
 * <p>Lines: {@code V483 DOJO <what>: PASS|FAIL | detail}.
 */
public final class V483AdminDojo {
    private V483AdminDojo() {}

    private static final String HALL = "Probe Admin Hall";

    /** Collects what a command sends to its source (the console stand-in). */
    private static final class Capture implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override
        public void m_213846_(Component c) {
            lines.add(c.getString());
        }

        @Override
        public boolean m_6999_() {
            return true;
        }

        @Override
        public boolean m_7028_() {
            return true;
        }

        @Override
        public boolean m_6102_() {
            return false;
        }
    }

    private record Out(int result, String text) {
        boolean has(String s) {
            return text.contains(s);
        }
    }

    private static Out console(MinecraftServer server, String cmd) {
        Capture c = new Capture();
        CommandSourceStack src = server.m_129893_().m_165484_(c);
        int r = server.m_129892_().m_230957_(src, cmd);
        return new Out(r, String.join(" / ", c.lines).replaceAll("§.", ""));
    }

    private static int asPlayer(ServerPlayer player, String cmd) {
        return player.m_20194_().m_129892_().m_230957_(player.m_20203_(), cmd);
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> backing(MinecraftServer server) throws ReflectiveOperationException {
        java.lang.reflect.Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11196_");
        f.setAccessible(true);
        return (List<ServerPlayer>) f.get(server.m_6846_());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> byUuid(MinecraftServer server) throws ReflectiveOperationException {
        java.lang.reflect.Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11197_");
        f.setAccessible(true);
        return (Map<UUID, ServerPlayer>) f.get(server.m_6846_());
    }

    private static ServerPlayer standIn(ServerPlayer player, String name, List<ServerPlayer> made) throws Exception {
        com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(
                UUID.nameUUIDFromBytes(("uiprobe483:" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer p = net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(), gp);
        p.m_6034_(player.m_20185_() + 3, player.m_20186_(), player.m_20189_());
        backing(player.m_20194_()).add(p);
        byUuid(player.m_20194_()).put(p.m_20148_(), p);
        made.add(p);
        return p;
    }

    private static void offline(MinecraftServer server, ServerPlayer p) throws Exception {
        backing(server).remove(p);
        byUuid(server).remove(p.m_20148_());
    }

    /** Make {@code pupil} a disciple of {@code master}'s dojo the way a bond does. */
    private static void bond(ServerPlayer master, ServerPlayer pupil) {
        SparStore s = SparStore.get();
        SparStore.MentorBond a = s.bond(pupil.m_20148_());
        a.mentorUuid = master.m_20148_().toString();
        a.mentorName = master.m_7755_().getString();
        s.bond(master.m_20148_()).addApprentice(pupil.m_20148_().toString(), pupil.m_7755_().getString());
        DojoService.onDiscipleJoined(master.m_20148_().toString(), pupil.m_20148_().toString());
        // long-standing, so nothing about the join time gets in the way
        DojoStore.Dojo d = DojoService.byMaster(master.m_20148_().toString());
        if (d != null) {
            d.joinedAt.put(pupil.m_20148_().toString(), 0L);
        }
    }

    private static boolean bonded(ServerPlayer master, ServerPlayer pupil) {
        SparStore.MentorBond a = SparStore.get().bondsByPlayer.get(pupil.m_20148_().toString());
        return a != null && master.m_20148_().toString().equals(a.mentorUuid);
    }

    private static DojoStore.WarRecord war(DojoStore.Dojo a, DojoStore.Dojo b, String status) {
        DojoStore.WarRecord w = new DojoStore.WarRecord();
        w.id = "probe483-" + System.nanoTime();
        w.fromDojo = a.masterUuid;
        w.fromName = a.name;
        w.toDojo = b.masterUuid;
        w.toName = b.name;
        w.format = "BRACKET";
        w.status = status;
        w.createdAt = System.currentTimeMillis();
        w.expiresAt = w.createdAt + 60_000L;
        DojoStore.get().wars.add(w);
        return w;
    }

    public static void run(ServerPlayer player, Consumer<String> note) {
        MinecraftServer server = player.m_20194_();
        ServerLevel lvl = DojoWorld.level(server);
        DojoStore store = DojoStore.get();
        List<ServerPlayer> made = new ArrayList<>();
        List<String> wars = new ArrayList<>();
        try {
            ServerPlayer m1 = standIn(player, "ProbeAdminMaster", made);
            ServerPlayer m2 = standIn(player, "ProbeAdminRival", made);
            ServerPlayer pupil = standIn(player, "ProbeAdminPupil", made);
            String id1 = m1.m_20148_().toString();
            String id2 = m2.m_20148_().toString();
            for (ServerPlayer p : made) {
                store.dojos.remove(p.m_20148_().toString());
                SparStore.get().bondsByPlayer.remove(p.m_20148_().toString());
            }

            // ---- help, list, unknown dojo
            Out help = console(server, "lm admin dojo");
            note.accept("V483 DOJO bare branch prints the branch help (console): " + pf(help.has("/lm admin dojo points grant")
                    && help.has("/lm admin dojo disband")) + " | " + cut(help.text()));
            note.accept("V483 DOJO help (player): " + pf(asPlayer(player, "lm admin dojo help") == 1));

            // ---- found: console with a name, player with the default name
            Out f1 = console(server, "lm admin dojo found ProbeAdminMaster " + HALL);
            DojoStore.Dojo a = DojoService.byMaster(id1);
            note.accept("V483 DOJO found <player> <name> founds the dojo (console): " + pf(f1.result() == 1 && a != null
                    && HALL.equals(a.name) && a.open && a.style.isBlank()) + " | " + cut(f1.text()));
            int f2 = asPlayer(player, "lm admin dojo found ProbeAdminRival");
            DojoStore.Dojo b = DojoService.byMaster(id2);
            note.accept("V483 DOJO found <player> uses the default name (player): " + pf(f2 == 1 && b != null
                    && "ProbeAdminRival's Dojo".equals(b.name)) + " | " + (b == null ? "no dojo" : b.name));
            if (a == null || b == null) {
                note.accept("V483 DOJO stand-in dojos exist: FAIL | the rest needs them");
                return;
            }
            Out again = console(server, "lm admin dojo found ProbeAdminMaster");
            note.accept("V483 DOJO found refuses a player already in a dojo: " + pf(again.result() == 0
                    && again.has("already in")) + " | " + cut(again.text()));
            Out offlineFound = console(server, "lm admin dojo found ProbeNobody483");
            note.accept("V483 DOJO found needs an online player: " + pf(offlineFound.result() == 0
                    && offlineFound.has("online")) + " | " + cut(offlineFound.text()));
            bond(m1, pupil);

            Out list = console(server, "lm admin dojo list");
            note.accept("V483 DOJO list shows every dojo (console): " + pf(list.has(HALL) && list.has("ProbeAdminRival's Dojo"))
                    + " | " + cut(list.text()));
            note.accept("V483 DOJO list (player): " + pf(asPlayer(player, "lm admin dojo list") == 1));
            Out unknown = console(server, "lm admin dojo status No Such Probe Dojo");
            note.accept("V483 DOJO an unknown dojo says none matches and how to list them: " + pf(unknown.result() == 0
                    && unknown.has("No dojo matches") && unknown.has("/lm admin dojo list")) + " | " + cut(unknown.text()));

            // ---- status: by dojo name (any case), by master name, by a disciple's name
            Out st = console(server, "lm admin dojo status " + HALL.toLowerCase());
            note.accept("V483 DOJO status <dojo> shows master, disciples, points, clout, season, wars (console): "
                    + pf(st.result() == 1 && st.has(HALL) && st.has("master ProbeAdminMaster")
                    && st.has("ProbeAdminPupil") && st.has("Dojo Points " + a.points) && st.has("season #")
                    && st.has("Wars ")) + " | " + cut(st.text()));
            Out stPupil = console(server, "lm admin dojo status ProbeAdminPupil");
            note.accept("V483 DOJO status <player> finds the player's dojo: " + pf(stPupil.result() == 1
                    && stPupil.has("a disciple of " + HALL)) + " | " + cut(stPupil.text()));
            note.accept("V483 DOJO status by master name (player): " + pf(asPlayer(player, "lm admin dojo status ProbeAdminMaster")
                    == 1));

            // ---- points and clout (the spot-check: the grant shows on the dojo record)
            int p0 = a.points;
            Out pg = console(server, "lm admin dojo points grant " + HALL + " 50");
            Out stAfter = console(server, "lm admin dojo status " + HALL);
            note.accept("V483 DOJO points grant shows on the dojo record (console): " + pf(pg.result() == 1
                    && a.points == p0 + 50 && DojoService.byMaster(id1).points == p0 + 50
                    && stAfter.has("Dojo Points " + (p0 + 50))) + " | " + cut(pg.text()) + " | " + cut(stAfter.text()));
            int pp = asPlayer(player, "lm admin dojo points grant \"" + HALL + "\" -10");
            note.accept("V483 DOJO points grant below zero takes points (player, quoted name): " + pf(pp == 1
                    && a.points == p0 + 40) + " | " + a.points);
            Out badAmount = console(server, "lm admin dojo points grant " + HALL + " lots");
            note.accept("V483 DOJO points grant with no number says what to type: " + pf(badAmount.result() == 0
                    && a.points == p0 + 40 && badAmount.has("whole number")) + " | " + cut(badAmount.text()));
            int c0 = a.clout;
            Out cg = console(server, "lm admin dojo clout grant " + HALL + " 7");
            int cp = asPlayer(player, "lm admin dojo clout grant ProbeAdminMaster 3");
            note.accept("V483 DOJO clout grant (console, then player by master name): " + pf(cg.result() == 1 && cp == 1
                    && a.clout == c0 + 10) + " | " + cut(cg.text()) + " | clout " + a.clout);

            // ---- rename: no cost, no cooldown, the free rename kept
            int pts = a.points;
            Out rn = console(server, "lm admin dojo rename " + HALL + " Probe Admin Dojo");
            note.accept("V483 DOJO rename (console): free rename kept, no points, no cooldown: " + pf(rn.result() == 1
                    && "Probe Admin Dojo".equals(a.name) && !a.freeRenameUsed && a.points == pts
                    && a.identityChangedAt == 0L) + " | " + cut(rn.text()));
            int rp = asPlayer(player, "lm admin dojo rename Probe Admin Dojo " + HALL);
            note.accept("V483 DOJO rename (player) back: " + pf(rp == 1 && HALL.equals(a.name) && !a.freeRenameUsed));
            Out rnBad = console(server, "lm admin dojo rename " + HALL + " x");
            note.accept("V483 DOJO rename keeps the name rule: " + pf(rnBad.result() == 0 && HALL.equals(a.name))
                    + " | " + cut(rnBad.text()));

            // ---- style
            Out sy = console(server, "lm admin dojo style " + HALL + " ki");
            note.accept("V483 DOJO style (console): " + pf(sy.result() == 1 && "KI".equals(a.style)) + " | " + cut(sy.text()));
            int sp = asPlayer(player, "lm admin dojo style " + HALL + " melee");
            note.accept("V483 DOJO style (player): " + pf(sp == 1 && "MELEE".equals(a.style) && a.identityChangedAt == 0L));
            Out syBad = console(server, "lm admin dojo style " + HALL + " dance");
            note.accept("V483 DOJO style refuses an unknown style: " + pf(syBad.result() == 0 && "MELEE".equals(a.style))
                    + " | " + cut(syBad.text()));

            // ---- banner: base + layers, the hall rebuilds; default clears it
            Out bn = console(server, "lm admin dojo banner " + HALL + " red bs:white cr:15");
            boolean queued = lvl == null || DojoPlacer.busy(id1);
            DojoPlacer.finishNow();
            boolean flies = lvl == null || !DojoWorld.bannersOn(lvl, a.plot).isEmpty()
                    && DojoWorld.bannersOn(lvl, a.plot).stream().allMatch(s -> s.base().equals("red")
                    && s.patterns().equals(List.of("bs:0", "cr:15")));
            note.accept("V483 DOJO banner (console) sets base and layers and rebuilds the hall: " + pf(bn.result() == 1
                    && "red".equals(a.bannerBase) && a.bannerPatterns.equals(List.of("bs:0", "cr:15")) && queued && flies
                    && (lvl == null || DojoWorld.ready(a))) + " | " + cut(bn.text()) + (lvl == null ? " | no dojo world" : ""));
            Out bnBad = console(server, "lm admin dojo banner " + HALL + " notacolour");
            note.accept("V483 DOJO banner refuses a bad colour: " + pf(bnBad.result() == 0 && "red".equals(a.bannerBase))
                    + " | " + cut(bnBad.text()));
            int bp = asPlayer(player, "lm admin dojo banner " + HALL + " default");
            DojoPlacer.finishNow();
            note.accept("V483 DOJO banner default (player) back to the style's banner: " + pf(bp == 1 && a.bannerBase.isEmpty()
                    && a.bannerPatterns.isEmpty() && (lvl == null || DojoWorld.ready(a))));

            // ---- members
            String pu = pupil.m_20148_().toString();
            Out pr = console(server, "lm admin dojo promote " + HALL + " ProbeAdminPupil");
            note.accept("V483 DOJO promote (console): " + pf(pr.result() == 1 && a.elites.contains(pu)) + " | " + cut(pr.text()));
            Out wr = console(server, "lm admin dojo warrights " + HALL + " ProbeAdminPupil");
            note.accept("V483 DOJO warrights on (console): " + pf(wr.result() == 1 && a.warRights.contains(pu)));
            int wp = asPlayer(player, "lm admin dojo warrights " + HALL + " ProbeAdminPupil");
            note.accept("V483 DOJO warrights off (player): " + pf(wp == 1 && !a.warRights.contains(pu)));
            int dp = asPlayer(player, "lm admin dojo demote " + HALL + " ProbeAdminPupil");
            note.accept("V483 DOJO demote (player): " + pf(dp == 1 && !a.elites.contains(pu)));
            int pp2 = asPlayer(player, "lm admin dojo promote " + HALL + " ProbeAdminPupil");
            Out dm = console(server, "lm admin dojo demote " + HALL + " ProbeAdminPupil");
            note.accept("V483 DOJO promote (player) and demote (console): " + pf(pp2 == 1 && dm.result() == 1
                    && !a.elites.contains(pu)) + " | " + cut(dm.text()));
            Out notMember = console(server, "lm admin dojo promote " + HALL + " ProbeAdminRival");
            note.accept("V483 DOJO promote refuses a non-member: " + pf(notMember.result() == 0) + " | " + cut(notMember.text()));

            // ---- close / reopen, both ways
            Out cl = console(server, "lm admin dojo close " + HALL);
            boolean closed = !a.open;
            int ro = asPlayer(player, "lm admin dojo reopen " + HALL);
            note.accept("V483 DOJO close (console) and reopen (player): " + pf(cl.result() == 1 && closed && ro == 1 && a.open)
                    + " | " + cut(cl.text()));
            int cl2 = asPlayer(player, "lm admin dojo close ProbeAdminMaster");
            boolean closed2 = !a.open;
            Out ro2 = console(server, "lm admin dojo reopen ProbeAdminMaster");
            note.accept("V483 DOJO close (player) and reopen (console), by master name: " + pf(cl2 == 1 && closed2
                    && ro2.result() == 1 && a.open) + " | " + cut(ro2.text()));

            // ---- rebuild
            Out rb = console(server, "lm admin dojo rebuild " + HALL);
            boolean rbQueued = DojoPlacer.busy(id1);
            DojoPlacer.finishNow();
            if (lvl == null) {
                note.accept("V483 DOJO rebuild without the dojo world says it isn't loaded: " + pf(rb.result() == 0
                        && rb.has("isn't loaded")));
            } else {
                note.accept("V483 DOJO rebuild (console) builds the whole hall again: " + pf(rb.result() == 1 && rbQueued
                        && DojoWorld.ready(a)) + " | " + cut(rb.text()));
                int rbp = asPlayer(player, "lm admin dojo rebuild " + HALL);
                boolean q2 = DojoPlacer.busy(id1);
                DojoPlacer.finishNow();
                note.accept("V483 DOJO rebuild (player): " + pf(rbp == 1 && q2 && DojoWorld.ready(a)));
            }

            // ---- save and reload (records come back from disk as new objects)
            Out sv = console(server, "lm admin dojo save");
            note.accept("V483 DOJO save (console) writes dojos.json: " + pf(sv.result() == 1
                    && java.nio.file.Files.isRegularFile(DojoStore.path())) + " | " + cut(sv.text()));
            note.accept("V483 DOJO save (player): " + pf(asPlayer(player, "lm admin dojo save") == 1));
            int savedPoints = a.points;
            Out rl = console(server, "lm admin dojo reload");
            a = DojoService.byMaster(id1);
            b = DojoService.byMaster(id2);
            note.accept("V483 DOJO reload (console) reads the saved record back: " + pf(rl.result() == 1 && a != null
                    && b != null && a.points == savedPoints && HALL.equals(a.name)) + " | " + cut(rl.text()));
            int rlp = asPlayer(player, "lm admin dojo reload");
            a = DojoService.byMaster(id1);
            b = DojoService.byMaster(id2);
            note.accept("V483 DOJO reload (player): " + pf(rlp == 1 && a != null && b != null));
            if (a == null || b == null) {
                return;
            }

            // ---- war cancel: a live run (console), a stuck RUNNING record (player), a waiting challenge
            int winsA = a.warWins;
            int lossB = b.warLosses;
            DojoStore.WarRecord live = war(a, b, "CHALLENGED");
            wars.add(live.id);
            String acc = lvl == null ? "no dojo world" : DojoWar.accept(m2, live.id);
            boolean running = DojoWar.runningFor(a.masterUuid) != null;
            Out wc = console(server, "lm admin dojo war cancel " + HALL + " ProbeAdminRival's Dojo");
            note.accept("V483 DOJO war cancel (console) stops a running war with no result: " + pf(wc.result() == 1
                    && (lvl == null || running) && DojoWar.runningFor(a.masterUuid) == null
                    && "CANCELLED".equals(live.status) && live.winnerDojo.isEmpty() && a.warWins == winsA
                    && b.warLosses == lossB && !DojoWar.fighting(m2.m_20148_())) + " | " + acc + " | " + cut(wc.text()));
            DojoStore.WarRecord stuck = war(b, a, "RUNNING");
            DojoStore.WarRecord waiting = war(a, b, "CHALLENGED");
            wars.add(stuck.id);
            wars.add(waiting.id);
            int wcp = asPlayer(player, "lm admin dojo war cancel \"ProbeAdminRival's Dojo\" " + HALL);
            note.accept("V483 DOJO war cancel (player) calls off a stuck war and a waiting challenge: " + pf(wcp == 1
                    && "CANCELLED".equals(stuck.status) && "CANCELLED".equals(waiting.status) && a.warWins == winsA));
            Out wcNone = console(server, "lm admin dojo war cancel " + HALL + " ProbeAdminRival's Dojo");
            note.accept("V483 DOJO war cancel with no war says so: " + pf(wcNone.result() == 0 && wcNone.has("no war"))
                    + " | " + cut(wcNone.text()));

            // ---- season advance: the alias previews, confirms, and the old path still works
            Out prev = console(server, "lm admin dojo season advance");
            note.accept("V483 DOJO season advance previews and names the confirm (console): " + pf(prev.result() == 1
                    && prev.has("/lm admin dojo season advance confirm")) + " | " + cut(prev.text()));
            Out old = console(server, "lm dojo season advance");
            note.accept("V483 DOJO the old /lm dojo season advance still previews: " + pf(old.result() == 1
                    && old.has("/lm dojo season advance confirm")));
            int s0 = SparStore.get().dojoSeason.seasonId;
            Out conf = console(server, "lm admin dojo season advance confirm");
            int s1 = SparStore.get().dojoSeason.seasonId;
            note.accept("V483 DOJO season advance confirm ends the season (console): " + pf(conf.result() == 1 && s1 == s0 + 1)
                    + " | " + s0 + " -> " + s1);
            int pv = asPlayer(player, "lm admin dojo season advance");
            note.accept("V483 DOJO season advance preview changes nothing (player): " + pf(pv == 1
                    && SparStore.get().dojoSeason.seasonId == s1));
            int pc = asPlayer(player, "lm admin dojo season advance confirm");
            note.accept("V483 DOJO season advance confirm (player): " + pf(pc == 1 && SparStore.get().dojoSeason.seasonId == s1 + 1));

            // ---- /lm admin clear <player> dojo
            Out cMaster = console(server, "lm admin clear ProbeAdminMaster dojo");
            note.accept("V483 DOJO clear dojo on a master keeps the dojo and names disband: " + pf(DojoService.byMaster(id1) != null
                    && cMaster.has("still leads " + HALL) && cMaster.has("/lm admin dojo disband")) + " | "
                    + cut(cMaster.text()));
            a = DojoService.byMaster(id1);
            a.elites.add(pu);
            a.warRights.add(pu);
            Out cPupil = console(server, "lm admin clear ProbeAdminPupil dojo");
            note.accept("V483 DOJO clear dojo takes a disciple out (console): " + pf(!bonded(m1, pupil)
                    && !DojoService.disciples(a).contains(pu) && !a.elites.contains(pu) && !a.warRights.contains(pu)
                    && cPupil.has("left " + HALL)) + " | " + cut(cPupil.text()));
            bond(m1, pupil);
            offline(server, pupil);
            int cpo = asPlayer(player, "lm admin clear ProbeAdminPupil dojo");
            note.accept("V483 DOJO clear dojo works for an offline disciple (player): " + pf(cpo == 1 && !bonded(m1, pupil)
                    && !DojoService.disciples(a).contains(pu)));
            backing(server).add(pupil);
            byUuid(server).put(pupil.m_20148_(), pupil);

            // ---- disband: preview, confirm (console, by name; the plot is cleared), player (by master name)
            bond(m1, pupil);
            int plot = a.plot;
            Out dPrev = console(server, "lm admin dojo disband " + HALL);
            note.accept("V483 DOJO disband without confirm only explains: " + pf(dPrev.result() == 1
                    && DojoService.byMaster(id1) != null && dPrev.has("disband " + HALL + " confirm")) + " | "
                    + cut(dPrev.text()));
            Out dc = console(server, "lm admin dojo disband " + HALL + " confirm");
            DojoPlacer.finishNow();
            boolean emptyPlot = true;
            if (lvl != null && plot >= 0 && DojoLayout.get() != null) {
                double[] s = DojoLayout.get().spawn;
                double[] w = DojoWorld.world(plot, s[0], s[1] - 1, s[2]);
                emptyPlot = DojoWorld.bannersOn(lvl, plot).isEmpty()
                        && lvl.m_8055_(BlockPos.m_274561_(w[0], w[1], w[2])).m_60795_();
            }
            note.accept("V483 DOJO disband confirm (console) removes the dojo, frees its disciple, clears the plot: "
                    + pf(dc.result() == 1 && DojoService.byMaster(id1) == null && !bonded(m1, pupil)
                    && DojoService.lookup(HALL).isEmpty() && emptyPlot && DojoWorld.dojoOnPlot(plot) == null)
                    + " | " + cut(dc.text()));
            int dpl = asPlayer(player, "lm admin dojo disband ProbeAdminRival confirm");
            DojoPlacer.finishNow();
            note.accept("V483 DOJO disband confirm (player, by master name): " + pf(dpl == 1 && DojoService.byMaster(id2) == null));
        } catch (Throwable t) {
            note.accept("V483 DOJO ERROR: FAIL | " + t);
        } finally {
            DojoPlacer.finishNow();
            store.wars.removeIf(w -> wars.contains(w.id));
            for (ServerPlayer p : made) {
                String id = p.m_20148_().toString();
                store.dojos.remove(id);
                store.players.remove(id);
                store.returns.remove(id);
                SparStore.get().bondsByPlayer.remove(id);
                try {
                    offline(server, p);
                } catch (Exception ignored) {
                }
            }
            store.markDirty();
            SparStore.get().markDirty();
        }
    }

    private static String cut(String s) {
        return s.length() > 220 ? s.substring(0, 220) + "..." : s;
    }

    private static String pf(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }
}
