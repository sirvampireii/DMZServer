package com.dmzlegacy.ultimatekey.agent;

import com.dmzlegacy.ultimatekey.agent.AgentBandits;
import com.dmzlegacy.ultimatekey.agent.AgentBrain;
import com.dmzlegacy.ultimatekey.agent.AgentControl;
import com.dmzlegacy.ultimatekey.agent.AgentDmz;
import com.dmzlegacy.ultimatekey.agent.AgentFactions;
import com.dmzlegacy.ultimatekey.agent.AgentLand;
import com.dmzlegacy.ultimatekey.agent.AgentMorality;
import com.dmzlegacy.ultimatekey.agent.AgentPersonality;
import com.dmzlegacy.ultimatekey.agent.AgentPhrases;
import com.dmzlegacy.ultimatekey.agent.AgentPlayer;
import com.dmzlegacy.ultimatekey.agent.AgentPower;
import com.dmzlegacy.ultimatekey.agent.AgentRoles;
import com.dmzlegacy.ultimatekey.agent.AgentSocial;
import com.dmzlegacy.ultimatekey.agent.AgentVillain;
import com.dmzlegacy.ultimatekey.agent.Agents;
import com.dmzlegacy.ultimatekey.agent.NpcEditor;
import com.dmzlegacy.ultimatekey.agent.VillainBoons;
import com.dmzlegacy.ultimatekey.agent.VillainMinions;
import com.dmzlegacy.ultimatekey.agent.VillainPowers;
import com.dmzlegacy.ultimatekey.agent.VillainRecruit;
import com.dmzlegacy.ultimatekey.dmz.Dmz;
import com.dragonminez.common.dragonball.DragonDefinition;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.entities.dragon.DragonWishEntity;
import com.dragonminez.common.network.C2S.GrantWishC2S;
import com.dragonminez.common.network.C2S.TravelToPlanetC2S;
import com.dragonminez.server.world.data.DragonBallSavedData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class VillainSchemes {
    private VillainSchemes() {
    }

    static JsonArray acts(AgentPlayer p) {
        JsonObject d = AgentVillain.def(p);
        return d.has("scheme") && d.get("scheme").isJsonArray() ? d.getAsJsonArray("scheme") : new JsonArray();
    }

    static JsonObject currentAct(AgentPlayer p) {
        JsonArray a;
        int i = (int)AgentBrain.num(AgentVillain.state(p), "act", 0.0);
        return i < (a = VillainSchemes.acts(p)).size() && a.get(i).isJsonObject() ? a.get(i).getAsJsonObject() : null;
    }

    static String goal(JsonObject act) {
        return AgentBrain.str(act, "goal", "say");
    }

    private static String fill(AgentPlayer p, JsonObject act, String s) {
        return s.replace("%npc%", AgentVillain.name(p)).replace("%minutes%", String.valueOf((int)AgentBrain.num(act, "minutes", 10.0))).replace("%days%", String.valueOf((int)AgentBrain.num(act, "days", 3.0)));
    }

    private static int since(AgentPlayer p, String counter) {
        JsonObject st = AgentVillain.state(p);
        JsonObject start = st.has("act_start") ? st.getAsJsonObject("act_start") : new JsonObject();
        return AgentVillain.count(p, counter) - (start.has(counter) ? start.get(counter).getAsInt() : 0);
    }

    private static void beginAct(AgentPlayer p, JsonObject act) {
        JsonObject st = AgentVillain.state(p);
        int idx = (int)AgentBrain.num(st, "act", 0.0);
        st.addProperty("act_begun", (Number)idx);
        JsonObject counts = st.has("counts") ? st.getAsJsonObject("counts").deepCopy() : new JsonObject();
        st.add("act_start", (JsonElement)counts);
        long now = p.m_9236_().m_46467_();
        st.addProperty("act_started", (Number)now);
        st.addProperty("act_bp", (Number)Dmz.stats((Entity)p).map(d -> d.getBattlePowerExact()).orElse(1.0));
        st.remove("conquer_target");
        String g = VillainSchemes.goal(act);
        if (g.equals("doom")) {
            st.addProperty("doom_end", (Number)(now + (long)(AgentBrain.num(act, "minutes", 10.0) * 1200.0)));
        }
        if (g.equals("tournament")) {
            st.addProperty("games_day", (Number)(p.m_9236_().m_46468_() / 24000L));
        }
        if (act.has("say_start")) {
            AgentSocial.say(p, VillainSchemes.fill(p, act, AgentBrain.str(act, "say_start", "")));
        }
        if (act.has("broadcast_start")) {
            AgentVillain.broadcast(p, VillainSchemes.fill(p, act, AgentBrain.str(act, "broadcast_start", "")));
        }
        if ((g.equals("doom") || g.equals("tournament")) && act.has("broadcast")) {
            AgentVillain.broadcast(p, VillainSchemes.fill(p, act, AgentBrain.str(act, "broadcast", "")));
        }
        Agents.log(p, "villain_act", o -> {
            o.addProperty("act", (Number)(idx + 1));
            o.addProperty("goal", g);
        });
        p.brain.roleKey = "";
        p.brain.save(AgentVillain.name(p));
    }

    static void checkAct(AgentPlayer p) {
        JsonObject act = VillainSchemes.currentAct(p);
        if (act == null) {
            return;
        }
        JsonObject st = AgentVillain.state(p);
        int idx = (int)AgentBrain.num(st, "act", 0.0);
        if ((int)AgentBrain.num(st, "act_begun", -1.0) != idx) {
            VillainSchemes.beginAct(p, act);
            return;
        }
        if (VillainSchemes.goal(act).equals("doom")) {
            VillainSchemes.doomTick(p, act);
        }
        if (VillainSchemes.progress(p, act) >= 1.0) {
            VillainSchemes.complete(p, act);
        }
    }

    private static void complete(AgentPlayer p, JsonObject act) {
        JsonArray boons;
        JsonObject st = AgentVillain.state(p);
        int idx = (int)AgentBrain.num(st, "act", 0.0);
        String g = VillainSchemes.goal(act);
        if (g.equals("awaken")) {
            VillainSchemes.awaken(p, act);
        }
        if (g.equals("doom")) {
            VillainSchemes.doomFall(p, act);
        }
        if (act.has("say_done")) {
            AgentSocial.say(p, VillainSchemes.fill(p, act, AgentBrain.str(act, "say_done", "")));
        }
        if (!g.equals("doom") && !g.equals("tournament") && act.has("broadcast")) {
            AgentVillain.broadcast(p, VillainSchemes.fill(p, act, AgentBrain.str(act, "broadcast", "")));
        }
        JsonArray jsonArray = boons = act.has("boons") && act.get("boons").isJsonArray() ? act.getAsJsonArray("boons").deepCopy() : new JsonArray();
        if (act.has("model") && act.get("model").isJsonObject() && AgentVillain.hasLooks(act.getAsJsonObject("model"))) {
            JsonObject look = act.getAsJsonObject("model").deepCopy();
            look.addProperty("type", "looks");
            boons.add((JsonElement)look);
        }
        VillainBoons.apply(p, boons, "act" + (idx + 1));
        st.addProperty("act", (Number)(idx + 1));
        st.addProperty("act_begun", (Number)Integer.valueOf(-1));
        Agents.log(p, "villain_act_done", o -> o.addProperty("act", (Number)(idx + 1)));
        AgentVillain.add(p, "acts", 1);
        if (VillainSchemes.currentAct(p) == null) {
            JsonObject d = AgentVillain.def(p);
            if (d.has("loop_from")) {
                st.addProperty("act", (Number)((int)AgentBrain.num(d, "loop_from", 0.0)));
            } else {
                AgentPhrases.say(p, "v_plan_done", new String[0]);
            }
        }
        p.brain.roleKey = "";
        p.brain.save(AgentVillain.name(p));
    }

    static double progress(AgentPlayer p, JsonObject act) {
        JsonObject st = AgentVillain.state(p);
        long now = p.m_9236_().m_46467_();
        return switch (VillainSchemes.goal(act)) {
            case "lair" -> {
                if (VillainSchemes.nextLairTemplate(p) == null && (VillainSchemes.lairDone(p) || !VillainSchemes.lair(p).has("templates"))) {
                    yield 1.0;
                }
                yield 0.0;
            }
            case "power" -> Dmz.stats((Entity)p).map(d -> d.getBattlePowerExact()).orElse(0.0) / Math.max(1.0, AgentBrain.num(st, "act_bp", 1.0) * AgentBrain.num(act, "at", 2.0));
            case "minions" -> (double)VillainMinions.of(p).size() / Math.max(1.0, AgentBrain.num(act, "count", 3.0));
            case "absorb" -> (double)VillainSchemes.since(p, "absorbed") / Math.max(1.0, AgentBrain.num(act, "count", 1.0));
            case "candy" -> (double)VillainSchemes.since(p, "candy") / Math.max(1.0, AgentBrain.num(act, "count", 5.0));
            case "drain" -> (double)VillainSchemes.since(p, "drained") / Math.max(1.0, AgentBrain.num(act, "amount", 10000.0));
            case "conquer" -> (double)VillainSchemes.since(p, "towns") / Math.max(1.0, AgentBrain.num(act, "count", 1.0));
            case "dragon_balls" -> {
                if (VillainSchemes.since(p, "wishes") >= 1) {
                    yield 1.0;
                }
                yield (double)VillainSchemes.since(p, "balls") / 8.0;
            }
            case "tournament" -> ((double)(p.m_9236_().m_46468_() / 24000L) - AgentBrain.num(st, "games_day", 0.0)) / Math.max(1.0, AgentBrain.num(act, "days", 3.0));
            case "awaken" -> (double)VillainSchemes.since(p, "energy") / Math.max(1.0, AgentBrain.num(act, "energy", 3000.0));
            case "doom" -> {
                if ((double)now >= AgentBrain.num(st, "doom_end", Double.MAX_VALUE)) {
                    yield 1.0;
                }
                yield 0.0;
            }
            case "travel" -> {
                if (p.m_9236_().m_46472_().m_135782_().m_135815_().equals(AgentBrain.str(act, "to", "namek"))) {
                    yield 1.0;
                }
                yield 0.0;
            }
            default -> 1.0;
        };
    }

    static String describeAct(AgentPlayer p, JsonObject act) {
        String g;
        JsonObject st = AgentVillain.state(p);
        return switch (g = VillainSchemes.goal(act)) {
            case "lair" -> "building its lair" + (String)(VillainSchemes.nextLairTemplate(p) == null ? "" : " (" + VillainSchemes.nextLairTemplate(p) + ")");
            case "minions" -> "gathering servants " + VillainMinions.of(p).size() + "/" + (int)AgentBrain.num(act, "count", 3.0);
            case "absorb" -> "absorbing the strong " + VillainSchemes.since(p, "absorbed") + "/" + (int)AgentBrain.num(act, "count", 1.0);
            case "candy" -> "turning people into candy " + VillainSchemes.since(p, "candy") + "/" + (int)AgentBrain.num(act, "count", 5.0);
            case "drain" -> "draining energy " + VillainSchemes.since(p, "drained") + "/" + (int)AgentBrain.num(act, "amount", 10000.0);
            case "conquer" -> "conquering towns " + VillainSchemes.since(p, "towns") + "/" + (int)AgentBrain.num(act, "count", 1.0);
            case "dragon_balls" -> "hunting the dragon balls (" + VillainSchemes.heldBalls(p, VillainSchemes.ballSet(p)).size() + "/7 held)";
            case "tournament" -> "holding its tournament";
            case "awaken" -> "gathering energy " + VillainSchemes.since(p, "energy") + "/" + (int)AgentBrain.num(act, "energy", 3000.0);
            case "doom" -> "destroying the planet in " + Math.max(0L, (long)((AgentBrain.num(st, "doom_end", 0.0) - (double)p.m_9236_().m_46467_()) / 1200.0)) + " min";
            case "power" -> "training for power";
            case "travel" -> "travelling to " + AgentBrain.str(act, "to", "namek");
            default -> g;
        };
    }

    static void onDefeated(AgentPlayer p) {
        JsonObject act = VillainSchemes.currentAct(p);
        if (act == null) {
            return;
        }
        String g = VillainSchemes.goal(act);
        if (g.equals("doom") || g.equals("tournament")) {
            AgentVillain.state(p).addProperty("act_begun", (Number)Integer.valueOf(-1));
        }
    }

    static void step(AgentPlayer p) {
        if (VillainRecruit.tick(p)) {
            return;
        }
        JsonObject act = VillainSchemes.currentAct(p);
        if (act == null) {
            VillainSchemes.rest(p);
            return;
        }
        switch (VillainSchemes.goal(act)) {
            case "lair": {
                VillainSchemes.buildLair(p);
                break;
            }
            case "power": 
            case "awaken": {
                VillainSchemes.train(p);
                break;
            }
            case "minions": {
                VillainSchemes.minions(p);
                break;
            }
            case "absorb": {
                VillainSchemes.hunt(p, VillainSchemes.absorbTarget(p, act), "looking for someone worth absorbing");
                break;
            }
            case "candy": {
                VillainSchemes.hunt(p, VillainSchemes.candyTarget(p), "looking for something sweet");
                break;
            }
            case "drain": {
                VillainSchemes.drain(p);
                break;
            }
            case "conquer": {
                VillainSchemes.conquer(p, act);
                break;
            }
            case "dragon_balls": {
                VillainSchemes.dragonBalls(p, act);
                break;
            }
            case "tournament": {
                VillainSchemes.tournament(p);
                break;
            }
            case "doom": {
                VillainSchemes.rest(p);
                p.brain.state = "preparing to destroy the planet";
                break;
            }
            case "travel": {
                VillainSchemes.travel(p, AgentBrain.str(act, "to", "namek"));
                break;
            }
            default: {
                VillainSchemes.rest(p);
            }
        }
    }

    static void rest(AgentPlayer p) {
        String homeDim;
        Vec3 home = AgentBrain.vec(p.brain.profile, "home");
        String string = homeDim = p.brain.profile.has("base") ? AgentBrain.str(p.brain.profile.getAsJsonObject("base"), "dim", "") : "";
        if (home == null || !homeDim.isEmpty() && !homeDim.equals(p.m_9236_().m_46472_().m_135782_().toString())) {
            p.control.stopMoving();
            p.brain.state = "brooding";
            return;
        }
        if (home.m_82554_(p.m_20182_()) > 3.0) {
            AgentRoles.walk(p.control, home);
        }
        p.brain.state = "in its lair";
    }

    private static void hunt(AgentPlayer p, LivingEntity t, String idle) {
        if (t == null) {
            VillainSchemes.wanderFar(p);
            p.brain.state = idle;
            return;
        }
        if (t.m_20270_((Entity)p) < 24.0f && p.m_142582_((Entity)t)) {
            p.brain.attack(p, t, 600);
            String taunt = AgentVillain.line(p, "taunt");
            if (taunt != null && ThreadLocalRandom.current().nextInt(4) == 0) {
                AgentSocial.say(p, taunt);
            }
        } else {
            AgentRoles.walk(p.control, t.m_20182_());
        }
        p.brain.state = "hunting " + t.m_7755_().getString();
    }

    private static void wanderFar(AgentPlayer p) {
        if (p.control.nav != AgentControl.Nav.NONE) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble() * Math.PI * 2.0;
        double d = 24 + r.nextInt(40);
        int x = (int)(p.m_20185_() + Math.cos(a) * d);
        int z = (int)(p.m_20189_() + Math.sin(a) * d);
        int y = p.m_9236_().m_6924_(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        AgentRoles.walk(p.control, new Vec3((double)x + 0.5, (double)y, (double)z + 0.5));
    }

    private static void train(AgentPlayer p) {
        LivingEntity mob = null;
        for (LivingEntity e2 : p.m_9236_().m_6443_(LivingEntity.class, p.m_20191_().m_82400_(32.0), e -> e instanceof Enemy && e.m_6084_())) {
            if (mob != null && !(e2.m_20270_((Entity)p) < mob.m_20270_((Entity)p))) continue;
            mob = e2;
        }
        VillainSchemes.hunt(p, mob, "training");
    }

    private static boolean isPrey(AgentPlayer p, LivingEntity e) {
        ServerPlayer sp;
        if (e == p || !e.m_6084_() || !AgentVillain.hostileTo(p, e)) {
            return false;
        }
        if (e instanceof AgentPlayer) {
            AgentPlayer a = (AgentPlayer)e;
            return !AgentVillain.is(a) && VillainMinions.masterOf(a) == null && AgentPersonality.of(a) != null;
        }
        return e instanceof ServerPlayer && !(sp = (ServerPlayer)e).m_7500_() && !sp.m_5833_();
    }

    private static LivingEntity absorbTarget(AgentPlayer p, JsonObject act) {
        double need = AgentBrain.num(act, "min_power", 0.5) * AgentPower.full(p);
        Player best = null;
        double bestScore = 0.0;
        for (Player o : p.m_9236_().m_6907_()) {
            if (!VillainSchemes.isPrey(p, (LivingEntity)o) || o.m_20270_((Entity)p) > 160.0f) continue;
            double pw = AgentBandits.perceived(p, (LivingEntity)o);
            boolean android = o instanceof AgentPlayer && ((AgentPlayer)o).brain.profile.has("android");
            if (pw < need && !android) continue;
            double score = pw * (double)(android ? 10 : 1);
            if (!(score > bestScore)) continue;
            bestScore = score;
            best = o;
        }
        return best;
    }

    private static LivingEntity candyTarget(AgentPlayer p) {
        LivingEntity best = null;
        for (Player o : p.m_9236_().m_6907_()) {
            if (!VillainSchemes.isPrey(p, (LivingEntity)o) || !(o.m_20270_((Entity)p) < 64.0f) || best != null && !(o.m_20270_((Entity)p) < best.m_20270_((Entity)p))) continue;
            best = o;
        }
        if (best != null) {
            return best;
        }
        for (LivingEntity e2 : p.m_9236_().m_6443_(LivingEntity.class, p.m_20191_().m_82400_(32.0), e -> e instanceof AbstractVillager || e instanceof Animal)) {
            if (best != null && !(e2.m_20270_((Entity)p) < best.m_20270_((Entity)p))) continue;
            best = e2;
        }
        return best;
    }

    private static void minions(AgentPlayer p) {
        if (VillainPowers.has(p, "android") && VillainSchemes.nextLairTemplate(p) == null) {
            Vec3 home = AgentBrain.vec(p.brain.profile, "home");
            if (home != null && home.m_82554_(p.m_20182_()) > 4.0) {
                AgentRoles.walk(p.control, home);
                p.brain.state = "going to its lab";
                return;
            }
            if (VillainPowers.buildAndroid(p)) {
                return;
            }
            if (!VillainPowers.has(p, "mind_control") && !AgentVillain.focus(p).contains("martial")) {
                p.brain.state = "building an android";
                return;
            }
        }
        boolean magic = VillainPowers.has(p, "mind_control");
        AgentPlayer best = null;
        for (AgentPlayer o : Agents.bodies()) {
            if (o == p || o.m_9236_() != p.m_9236_() || o.m_20270_((Entity)p) > 128.0f || AgentPersonality.of(o) == null || AgentVillain.is(o) || VillainMinions.masterOf(o) != null || "pure".equals(AgentMorality.heart(o))) continue;
            double score = (float)(magic && AgentMorality.evil(o) ? 2 : 1) / (1.0f + o.m_20270_((Entity)p));
            if (best != null) {
                int n = magic && AgentMorality.evil(best) ? 2 : 1;
                if (!(score > (double)((float)n / (1.0f + best.m_20270_((Entity)p))))) continue;
            }
            best = o;
        }
        VillainSchemes.hunt(p, best, "looking for servants");
    }

    private static void drain(AgentPlayer p) {
        Vec3 crowd = null;
        for (Player o : p.m_9236_().m_6907_()) {
            if (!VillainSchemes.isPrey(p, (LivingEntity)o) || !(o.m_20270_((Entity)p) < 256.0f) || crowd != null && !(o.m_20182_().m_82554_(p.m_20182_()) < crowd.m_82554_(p.m_20182_()))) continue;
            crowd = o.m_20182_();
        }
        if (crowd != null && crowd.m_82554_(p.m_20182_()) > 12.0) {
            AgentRoles.walk(p.control, crowd);
            p.brain.state = "heading where the energy is";
            return;
        }
        int got = VillainPowers.drainArea(p);
        String string = p.brain.state = got > 0 ? "draining everything around it" : "searching for energy";
        if (crowd == null) {
            VillainSchemes.wanderFar(p);
        }
    }

    private static void conquer(AgentPlayer p, JsonObject act) {
        AgentPlayer foe;
        Vec3 home;
        JsonObject st = AgentVillain.state(p);
        String army = AgentFactions.foundNamed(p, AgentBrain.str(AgentVillain.def(p), "army", "%npc%'s Army").replace("%npc%", AgentVillain.name(p)));
        String dim = p.m_9236_().m_46472_().m_135782_().toString();
        String target = AgentBrain.str(st, "conquer_target", "");
        JsonObject tf = AgentFactions.get(target);
        if (tf == null || army.equals(AgentBrain.str(tf, "overlord", "")) || target.equals(army)) {
            target = "";
            double bestD = Double.MAX_VALUE;
            for (Map.Entry<String, JsonElement> e : AgentFactions.all().entrySet()) {
                JsonObject f = ((JsonElement)e.getValue()).getAsJsonObject();
                if (((String)e.getKey()).equals(army) || f.has("villain") || army.equals(AgentBrain.str(f, "overlord", "")) || !dim.equals(AgentBrain.str(f, "dim", ""))) continue;
                Vec3 h = AgentFactions.home((String)e.getKey());
                double d = h == null ? Double.MAX_VALUE : h.m_82554_(p.m_20182_());
                double d2 = d;
                if (!(d2 < bestD)) continue;
                bestD = d2;
                target = (String)e.getKey();
            }
            if (target.isEmpty()) {
                if (!VillainSchemes.planetDone(p, dim)) {
                    VillainSchemes.markPlanet(p, dim);
                    AgentVillain.broadcast(p, "%npc% now rules " + VillainSchemes.planetName(dim) + "!");
                }
                if (act.has("travel") && act.get("travel").getAsBoolean()) {
                    VillainSchemes.travel(p, VillainSchemes.nextPlanet(p));
                } else {
                    VillainSchemes.rest(p);
                    p.brain.state = "ruling its conquered world";
                }
                return;
            }
            st.addProperty("conquer_target", target);
            st.addProperty("siege_since", (Number)Long.valueOf(-1L));
            AgentPhrases.say(p, "v_next_target", "faction", AgentBrain.str(AgentFactions.get(target), "name", target));
        }
        if ((home = AgentFactions.home(target)) == null) {
            st.remove("conquer_target");
            return;
        }
        if (home.m_82554_(p.m_20182_()) > 16.0) {
            AgentRoles.walk(p.control, home);
            p.brain.state = "marching on " + AgentBrain.str(AgentFactions.get(target), "name", target);
            return;
        }
        AgentPlayer leader = (AgentPlayer)Agents.bodyOf(AgentFactions.leaderOf(target));
        AgentPlayer agentPlayer = foe = leader != null && leader.m_9236_() == p.m_9236_() && leader.m_20270_((Entity)p) < 64.0f ? leader : null;
        if (foe == null) {
            for (String m : AgentFactions.membersOf(target)) {
                AgentPlayer mb = (AgentPlayer)Agents.bodyOf(m);
                if (mb == null || mb.m_9236_() != p.m_9236_() || !(mb.m_20270_((Entity)p) < 48.0f)) continue;
                foe = mb;
                break;
            }
        }
        if (foe != null) {
            VillainSchemes.hunt(p, foe, "");
            return;
        }
        long now = p.m_9236_().m_46467_();
        if (AgentBrain.num(st, "siege_since", -1.0) < 0.0) {
            st.addProperty("siege_since", (Number)now);
        } else if ((double)now - AgentBrain.num(st, "siege_since", now) > 2400.0) {
            VillainSchemes.takeTown(p, target, army);
        }
        p.brain.state = "besieging " + AgentBrain.str(AgentFactions.get(target), "name", target);
    }

    private static void takeTown(AgentPlayer p, String town, String army) {
        AgentFactions.subjugate(town, army);
        AgentVillain.add(p, "towns", 1);
        AgentVillain.broadcast(p, "%npc% has conquered " + AgentBrain.str(AgentFactions.get(town), "name", town) + "!");
        AgentPhrases.say(p, "v_town_taken", new String[0]);
        AgentVillain.state(p).remove("conquer_target");
        VillainRecruit.payServants(p, (int)AgentBrain.num(AgentVillain.def(p), "servant_pay", 500.0));
    }

    private static boolean planetDone(AgentPlayer p, String dim) {
        JsonObject st = AgentVillain.state(p);
        if (!st.has("planets_done")) {
            return false;
        }
        for (JsonElement e : st.getAsJsonArray("planets_done")) {
            if (!e.getAsString().equals(dim)) continue;
            return true;
        }
        return false;
    }

    private static void markPlanet(AgentPlayer p, String dim) {
        JsonObject st = AgentVillain.state(p);
        if (!st.has("planets_done")) {
            st.add("planets_done", (JsonElement)new JsonArray());
        }
        st.getAsJsonArray("planets_done").add(dim);
        AgentVillain.add(p, "planets", 1);
    }

    static String planetName(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "Earth";
            case "dragonminez:namek" -> "Namek";
            case "dragonminez:sacredkaiplanet" -> "the Sacred World of the Kai";
            case "dragonminez:otherworld" -> "the Otherworld";
            default -> dim;
        };
    }

    private static String nextPlanet(AgentPlayer p) {
        String here = p.m_9236_().m_46472_().m_135782_().m_135815_();
        for (String d : List.of("overworld", "namek", "sacredkaiplanet")) {
            Object full;
            Object object = full = d.equals("overworld") ? "minecraft:overworld" : "dragonminez:" + d;
            if (d.equals(here) || VillainSchemes.planetDone(p, (String)full)) continue;
            return d;
        }
        return here.equals("overworld") ? "namek" : "overworld";
    }

    static void travel(AgentPlayer p, String dest) {
        if (p.m_9236_().m_46472_().m_135782_().m_135815_().equals(dest)) {
            return;
        }
        JsonObject st = AgentVillain.state(p);
        long now = p.m_9236_().m_46467_();
        if ((double)now - AgentBrain.num(st, "last_travel", -10000.0) < 1200.0) {
            return;
        }
        st.addProperty("last_travel", (Number)now);
        AgentPhrases.say(p, "v_travel", "planet", VillainSchemes.planetName((String)(dest.equals("overworld") ? "minecraft:overworld" : "dragonminez:" + dest)));
        boolean ok = AgentDmz.safe(p, "travel", () -> new TravelToPlanetC2S(dest).handle(AgentDmz.context(p)));
        p.m_20194_().execute(() -> {
            if (p.m_20159_()) {
                Entity pod = p.m_20202_();
                p.m_8127_();
                if (pod != null) {
                    pod.m_146870_();
                }
            }
        });
        Agents.log(p, "villain_travel", o -> {
            o.addProperty("to", dest);
            o.addProperty("ok", Boolean.valueOf(ok));
        });
    }

    private static void tournament(AgentPlayer p) {
        JsonObject st = AgentVillain.state(p);
        if (!st.has("arena")) {
            if (AgentLand.project(p) != null) {
                p.brain.state = "building its tournament ring";
                return;
            }
            JsonObject params = new JsonObject();
            params.addProperty("template", "lair_arena");
            params.addProperty("radius", (Number)64);
            params.addProperty("claim", Boolean.valueOf(false));
            params.addProperty("as_base", Boolean.valueOf(false));
            params.addProperty("materials", "free");
            params.addProperty("tag", "villain_arena");
            if (AgentLand.templates().containsKey("lair_arena")) {
                AgentLand.startEstablish(p, params);
            } else {
                st.add("arena", (JsonElement)AgentBrain.arr(p.m_20182_()));
            }
            return;
        }
        Vec3 ring = AgentBrain.vec(st, "arena");
        Player challenger = null;
        for (Player o : p.m_9236_().m_6907_()) {
            if (!VillainSchemes.isPrey(p, (LivingEntity)o) || !(o.m_20182_().m_82554_(ring) < 14.0) || challenger != null && !(o.m_20270_((Entity)p) < challenger.m_20270_((Entity)p))) continue;
            challenger = o;
        }
        if (challenger != null) {
            p.brain.attack(p, (LivingEntity)challenger, 600);
            p.brain.state = "fighting challenger " + challenger.m_7755_().getString();
            return;
        }
        if (ring.m_82554_(p.m_20182_()) > 3.0) {
            AgentRoles.walk(p.control, ring);
        }
        p.brain.state = "waiting in its ring for challengers";
        long now = p.m_9236_().m_46467_();
        if ((double)now - AgentBrain.num(st, "last_call", -100000.0) > 12000.0) {
            st.addProperty("last_call", (Number)now);
            AgentVillain.broadcast(p, "%npc% is waiting in its ring at " + (int)ring.f_82479_ + ", " + (int)ring.f_82481_ + ". Who will fight for this world?");
        }
    }

    private static void doomTick(AgentPlayer p, JsonObject act) {
        JsonObject st = AgentVillain.state(p);
        long left = (long)(AgentBrain.num(st, "doom_end", 0.0) - (double)p.m_9236_().m_46467_());
        long minute = left / 1200L;
        if (left > 0L && minute != (long)AgentBrain.num(st, "doom_said", -1.0)) {
            st.addProperty("doom_said", (Number)minute);
            if (minute <= 5L) {
                AgentVillain.broadcast(p, "%npc% will destroy " + VillainSchemes.planetName(p.m_9236_().m_46472_().m_135782_().toString()) + " in " + (minute + 1L) + " minute(s)!");
            }
        }
    }

    private static void doomFall(AgentPlayer p, JsonObject act) {
        String planet = VillainSchemes.planetName(p.m_9236_().m_46472_().m_135782_().toString());
        AgentVillain.broadcast(p, planet + " has fallen to %npc%!");
        JsonArray cmds = act.has("commands") && act.get("commands").isJsonArray() ? act.getAsJsonArray("commands") : null;
        ArrayList<String> run = new ArrayList<String>();
        if (cmds == null) {
            run.add("weather thunder 6000");
        } else {
            for (JsonElement e : cmds) {
                run.add(e.getAsString());
            }
        }
        CommandSourceStack src = p.m_20194_().m_129893_().m_81324_().m_81325_(4);
        for (String c : run) {
            p.m_20194_().m_129892_().m_230957_(src, c.replace("%npc%", AgentVillain.name(p)).replaceFirst("^/", ""));
        }
        VillainSchemes.markPlanet(p, p.m_9236_().m_46472_().m_135782_().toString());
        if (act.has("travel") && act.get("travel").getAsBoolean()) {
            VillainSchemes.travel(p, VillainSchemes.nextPlanet(p));
        }
    }

    private static void awaken(AgentPlayer p, JsonObject act) {
        String name = AgentBrain.str(act, "name", "Majin_Buu");
        if (Agents.bodyOf(name) != null) {
            return;
        }
        try {
            AgentPlayer born = Agents.spawn(p.m_20194_(), name, p.m_284548_(), p.m_20182_().m_82520_(2.0, 0.0, 0.0));
            NpcEditor.shape(born, new JsonObject());
            AgentVillain.make(born, AgentBrain.str(act, "preset", "devourer"));
            VillainMinions.enslave(p, born, "awakened");
        }
        catch (RuntimeException e) {
            Agents.log(p, "awaken_failed", o -> o.addProperty("why", String.valueOf(e.getMessage())));
        }
    }

    private static JsonObject lair(AgentPlayer p) {
        JsonObject d = AgentVillain.def(p);
        return d.has("lair") && d.get("lair").isJsonObject() ? d.getAsJsonObject("lair") : new JsonObject();
    }

    static String nextLairTemplate(AgentPlayer p) {
        JsonObject lair = VillainSchemes.lair(p);
        JsonObject st = AgentVillain.state(p);
        if (!lair.has("templates") || !lair.get("templates").isJsonArray()) {
            return null;
        }
        JsonArray built = st.has("lair_built") ? st.getAsJsonArray("lair_built") : new JsonArray();
        JsonObject fails = st.has("lair_fails") ? st.getAsJsonObject("lair_fails") : new JsonObject();
        for (JsonElement e : lair.getAsJsonArray("templates")) {
            String id = e.getAsString();
            if (built.contains(e) || fails.has(id) && fails.get(id).getAsInt() >= 3 || !AgentLand.templates().containsKey(id)) continue;
            return id;
        }
        return null;
    }

    static boolean lairDone(AgentPlayer p) {
        JsonObject st = AgentVillain.state(p);
        return st.has("lair_built") && !st.getAsJsonArray("lair_built").isEmpty() && VillainSchemes.nextLairTemplate(p) == null;
    }

    static boolean lairBlocking(AgentPlayer p) {
        JsonObject act = VillainSchemes.currentAct(p);
        return act != null && VillainSchemes.goal(act).equals("lair");
    }

    static void buildLair(AgentPlayer p) {
        if (AgentLand.project(p) != null) {
            p.brain.state = "building its lair";
            return;
        }
        String next = VillainSchemes.nextLairTemplate(p);
        JsonObject lair = VillainSchemes.lair(p);
        JsonObject st = AgentVillain.state(p);
        if (next == null) {
            if (VillainSchemes.lairDone(p)) {
                return;
            }
            long now = p.m_9236_().m_46467_();
            if (!st.has("lair_retry")) {
                st.addProperty("lair_retry", (Number)(now + 3600L));
            }
            if ((double)now >= AgentBrain.num(st, "lair_retry", now)) {
                st.remove("lair_fails");
                st.remove("lair_retry");
            }
            VillainSchemes.wanderFar(p);
            p.brain.state = "searching far and wide for a place for its lair";
            return;
        }
        JsonObject params = new JsonObject();
        params.addProperty("template", next);
        JsonObject fails = st.has("lair_fails") ? st.getAsJsonObject("lair_fails") : new JsonObject();
        int failed = fails.has(next) ? fails.get(next).getAsInt() : 0;
        params.addProperty("radius", (Number)(AgentBrain.num(lair, "radius", 128.0) * (double)(1 + failed)));
        params.addProperty("claim", Boolean.valueOf(true));
        boolean first = !st.has("lair_built") || st.getAsJsonArray("lair_built").isEmpty();
        params.addProperty("as_base", Boolean.valueOf(first));
        params.addProperty("materials", AgentBrain.str(lair, "materials", "free"));
        params.addProperty("tag", "villain_lair");
        if (st.has("lair_origin")) {
            params.add("near", st.get("lair_origin"));
        }
        String why = AgentLand.startEstablish(p, params);
        p.brain.state = why == null ? "choosing a site for its lair" : why;
    }

    static void lairBuilt(AgentPlayer p, String template, JsonObject pr, boolean ok) {
        JsonObject st = AgentVillain.state(p);
        if (ok) {
            if (!st.has("lair_built")) {
                st.add("lair_built", (JsonElement)new JsonArray());
            }
            st.getAsJsonArray("lair_built").add(template);
            if (!st.has("lair_origin") && pr.has("origin")) {
                JsonArray o = pr.getAsJsonArray("origin");
                JsonArray v = new JsonArray();
                for (JsonElement e : o) {
                    v.add((Number)e.getAsDouble());
                }
                st.add("lair_origin", (JsonElement)v);
            }
            AgentPhrases.say(p, "v_lair", new String[0]);
        } else {
            if (!st.has("lair_fails")) {
                st.add("lair_fails", (JsonElement)new JsonObject());
            }
            JsonObject f = st.getAsJsonObject("lair_fails");
            f.addProperty(template, (Number)((f.has(template) ? f.get(template).getAsInt() : 0) + 1));
        }
        p.brain.save(AgentVillain.name(p));
    }

    static void arenaBuilt(AgentPlayer p, JsonObject pr) {
        if (pr.has("home")) {
            AgentVillain.state(p).add("arena", pr.get("home"));
        } else if (pr.has("origin")) {
            AgentVillain.state(p).add("arena", pr.get("origin"));
        }
        p.brain.save(AgentVillain.name(p));
    }

    static boolean finish(AgentPlayer p, LivingEntity t) {
        JsonObject act = VillainSchemes.currentAct(p);
        String g = act == null ? "" : VillainSchemes.goal(act);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (g) {
            case "absorb": {
                if (!VillainPowers.absorb(p, t)) break;
                return true;
            }
            case "candy": {
                if (!VillainPowers.candy(p, t)) break;
                return true;
            }
            case "minions": {
                if (!(t instanceof AgentPlayer)) break;
                AgentPlayer a = (AgentPlayer)t;
                if (VillainPowers.mindControl(p, a)) {
                    return true;
                }
                if (!AgentVillain.focus(p).contains("martial") || !VillainPowers.recruit(p, a)) break;
                return true;
            }
            case "conquer": {
                String target = AgentBrain.str(AgentVillain.state(p), "conquer_target", "");
                if (!(t instanceof AgentPlayer)) break;
                AgentPlayer a = (AgentPlayer)t;
                if (target.isEmpty() || !AgentFactions.leaderOf(target).equalsIgnoreCase(AgentVillain.name(a))) break;
                String army = AgentFactions.factionOf(p);
                AgentPhrases.say(a, "surrender", new String[0]);
                if (army != null) {
                    VillainSchemes.takeTown(p, target, army);
                }
                return true;
            }
            case "tournament": {
                if (!(t instanceof Player)) break;
                AgentPhrases.say(p, "v_next_challenger", new String[0]);
                return true;
            }
        }
        if (t instanceof Player && VillainPowers.has(p, "candy_beam") && r.nextDouble() < 0.35) {
            return VillainPowers.candy(p, t);
        }
        if (t instanceof Player && VillainPowers.has(p, "absorb") && AgentBandits.perceived(p, t) > AgentPower.full(p) * 0.7 && r.nextDouble() < 0.3) {
            return VillainPowers.absorb(p, t);
        }
        if (t instanceof AgentPlayer) {
            AgentPlayer a = (AgentPlayer)t;
            if (VillainPowers.has(p, "mind_control") && AgentMorality.evil(a) && r.nextDouble() < 0.4) {
                return VillainPowers.mindControl(p, a);
            }
        }
        return false;
    }

    static String ballSet(AgentPlayer p) {
        String dim = p.m_9236_().m_46472_().m_135782_().toString();
        return dim.equals("minecraft:overworld") ? "earth" : (dim.equals("dragonminez:namek") ? "namek" : null);
    }

    private static Item ballItem(String set, int star) {
        try {
            return (Item)MainItems.getDragonBallBlockItemOrThrow((String)set, (int)star).get();
        }
        catch (RuntimeException e) {
            return null;
        }
    }

    static List<Integer> heldBalls(AgentPlayer p, String set) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        if (set == null) {
            return out;
        }
        for (int s = 1; s <= 7; ++s) {
            Item it = VillainSchemes.ballItem(set, s);
            if (it == null || !p.m_150109_().m_36063_(new ItemStack((ItemLike)it))) continue;
            out.add(s);
        }
        return out;
    }

    private static void dragonBalls(AgentPlayer p, JsonObject act) {
        String set = VillainSchemes.ballSet(p);
        if (set == null) {
            VillainSchemes.travel(p, "overworld");
            return;
        }
        ServerLevel level = p.m_284548_();
        JsonObject st = AgentVillain.state(p);
        if (AgentBrain.num(st, "summoned_at", -1.0) > 0.0) {
            VillainSchemes.wish(p, act);
            return;
        }
        List<Integer> held = VillainSchemes.heldBalls(p, set);
        if (held.size() >= 7) {
            VillainSchemes.summon(p, set);
            return;
        }
        DragonBallSavedData data = DragonBallSavedData.get((ServerLevel)level);
        BlockPos best = null;
        for (Map.Entry e : data.getActiveBalls(set).entrySet()) {
            if (held.contains(e.getKey())) continue;
            for (BlockPos pos : (List<BlockPos>)e.getValue()) {
                if (best != null && !(pos.m_123331_((Vec3i)p.m_20183_()) < best.m_123331_((Vec3i)p.m_20183_()))) continue;
                best = pos;
            }
        }
        if (best != null) {
            if (Vec3.m_82512_(best).m_82554_(p.m_146892_()) > 4.0) {
                AgentRoles.walk(p.control, Vec3.m_82539_(best));
                p.brain.state = "following its radar to a dragon ball (" + held.size() + "/7)";
                return;
            }
            p.f_8941_.m_9280_(best);
            for (ItemEntity ie : level.m_45976_(ItemEntity.class, new AABB(best).m_82400_(3.0))) {
                if (!p.m_150109_().m_36054_(ie.m_32055_().m_41777_())) continue;
                ie.m_146870_();
            }
            AgentVillain.add(p, "balls", 1);
            AgentPhrases.say(p, "v_dragon_ball", "count", String.valueOf(held.size() + 1));
            return;
        }
        for (Map.Entry e : data.getPendingBalls(set).entrySet()) {
            if (held.contains(e.getKey())) continue;
            for (BlockPos pos : (List<BlockPos>)e.getValue()) {
                if (best != null && !(pos.m_123331_((Vec3i)p.m_20183_()) < best.m_123331_((Vec3i)p.m_20183_()))) continue;
                best = pos;
            }
        }
        if (best != null) {
            int y = level.m_6924_(Heightmap.Types.MOTION_BLOCKING, best.m_123341_(), best.m_123343_());
            AgentRoles.walk(p.control, new Vec3((double)best.m_123341_() + 0.5, (double)y, (double)best.m_123343_() + 0.5));
            p.brain.state = "searching for a dragon ball";
        } else {
            VillainSchemes.wanderFar(p);
            p.brain.state = "searching for the dragon balls";
        }
    }

    private static void summon(AgentPlayer p, String set) {
        ServerLevel level = p.m_284548_();
        BlockPos feet = p.m_20183_();
        int[][] ring = new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}};
        BlockPos first = null;
        for (int s = 1; s <= 7; ++s) {
            Item it = VillainSchemes.ballItem(set, s);
            int slot = -1;
            for (int i = 0; i < p.m_150109_().f_35974_.size(); ++i) {
                if (!((ItemStack)p.m_150109_().f_35974_.get(i)).m_150930_(it)) continue;
                slot = i;
            }
            if (slot < 0) continue;
            BlockPos spot = feet.m_7918_(ring[s - 1][0], 0, ring[s - 1][1]);
            BlockPos ground = spot.m_7495_();
            if (!level.m_8055_(spot).m_247087_()) continue;
            ItemStack stack = (ItemStack)p.m_150109_().f_35974_.get(slot);
            ItemStack keep = p.m_21205_().m_41777_();
            p.m_21008_(InteractionHand.MAIN_HAND, stack);
            p.m_150109_().f_35974_.set(slot, keep);
            p.f_8941_.m_7179_((ServerPlayer)p, (Level)level, p.m_21205_(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.m_82512_((Vec3i)ground).m_82520_(0.0, 0.5, 0.0), Direction.UP, ground, false));
            if (first != null) continue;
            first = spot;
        }
        if (first == null) {
            return;
        }
        BlockPos at = first;
        AgentPhrases.say(p, "v_summon", new String[0]);
        p.m_20194_().execute(() -> {
            BlockState state = level.m_8055_(at);
            state.m_60664_((Level)level, (Player)p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.m_82512_((Vec3i)at), Direction.UP, at, false));
            AgentVillain.state(p).addProperty("summoned_at", (Number)level.m_46467_());
        });
    }

    private static void wish(AgentPlayer p, JsonObject act) {
        JsonObject st = AgentVillain.state(p);
        List dragons = p.m_9236_().m_6443_(DragonWishEntity.class, p.m_20191_().m_82400_(50.0), d -> AgentVillain.name(p).equals(d.getOwnerName()) && !d.hasGrantedWish());
        if (dragons.isEmpty()) {
            if ((double)p.m_9236_().m_46467_() - AgentBrain.num(st, "summoned_at", 0.0) > 400.0) {
                st.remove("summoned_at");
            }
            return;
        }
        ArrayList<Integer> picks = new ArrayList<Integer>();
        if (act.has("wish") && act.get("wish").isJsonArray()) {
            for (JsonElement e : act.getAsJsonArray("wish")) {
                picks.add(e.getAsInt());
            }
        }
        if (picks.isEmpty()) {
            picks.add(0);
        }
        AgentSocial.say(p, act.has("wish_line") ? AgentBrain.str(act, "wish_line", "") : AgentPhrases.line(p, "v_wish", new String[0]));
        // DMZ 2.2: the wish names the dragon it is made on and the wish screen that dragon shows.
        DragonWishEntity dragon = (DragonWishEntity)dragons.get(0);
        DragonDefinition definition = dragon.getDragonDefinition();
        String screen = definition == null ? "" : definition.getWishScreenId();
        AgentDmz.safe(p, "wish", () -> new GrantWishC2S(dragon.m_19879_(), screen, picks, List.of()).handle(AgentDmz.context(p)));
        st.remove("summoned_at");
        AgentVillain.add(p, "wishes", 1);
        AgentVillain.broadcast(p, "%npc% has made a wish on the dragon balls!");
        if (act.has("wish_boons") && act.get("wish_boons").isJsonArray()) {
            VillainBoons.apply(p, act.getAsJsonArray("wish_boons"), "wish" + AgentVillain.count(p, "wishes"));
        }
    }
}

