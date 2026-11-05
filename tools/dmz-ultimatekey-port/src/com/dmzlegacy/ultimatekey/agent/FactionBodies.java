package com.dmzlegacy.ultimatekey.agent;

import com.dmzlegacy.ultimatekey.DmzUltimateKey;
import com.dmzlegacy.ultimatekey.agent.AgentBrain;
import com.dmzlegacy.ultimatekey.agent.AgentClones;
import com.dmzlegacy.ultimatekey.agent.AgentFactions;
import com.dmzlegacy.ultimatekey.agent.AgentLand;
import com.dmzlegacy.ultimatekey.agent.AgentLooks;
import com.dmzlegacy.ultimatekey.agent.AgentPlayer;
import com.dmzlegacy.ultimatekey.agent.Agents;
import com.dmzlegacy.ultimatekey.dmz.Dmz;
import com.dmzlegacy.ultimatekey.engine.DynamicSkill;
import com.dmzlegacy.ultimatekey.engine.DynamicSkills;
import com.dmzlegacy.ultimatekey.engine.Scheduler;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairCodec;
import com.dragonminez.common.hair.HairStyleSlot;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Stats;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

public final class FactionBodies {
    public static final int MAX_SLOTS = 64;

    private FactionBodies() {
    }

    public static LivingEntity spawnSoldier(ServerLevel level, Vec3 at, Spec s) {
        AgentPlayer c;
        MinecraftServer server = level.m_7654_();
        GameProfile profile = FactionBodies.freeProfile(server, s.name);
        if (profile == null) {
            return null;
        }
        try {
            c = Agents.spawnClone(server, profile, s.faction == null ? new UUID(0L, 1L) : s.faction, level, at);
        }
        catch (RuntimeException e) {
            DmzUltimateKey.LOG.debug("[DMZ Ultimate Key] NPC faction soldier {} could not spawn: {}", (Object)s.name, (Object)e.toString());
            return null;
        }
        AgentClones.adopt(c);
        try {
            FactionBodies.character(c, s);
            c.m_150109_().m_6211_();
            for (EquipmentSlot slot2 : EquipmentSlot.values()) {
                c.m_8061_(slot2, ItemStack.f_41583_);
            }
            s.gear.forEach((slot, stack) -> c.m_8061_((EquipmentSlot)slot, stack.m_41777_()));
            DynamicSkills.assign(c, s.skills);
            c.brain.profile = FactionBodies.profile(s);
            c.brain.resume();
            FactionBodies.tag(c, s);
            c.m_21153_(c.m_21233_());
            c.m_36324_().m_38705_(20);
            Scheduler.later(c.m_20148_(), "duk_clone", 3, false, () -> c.m_21153_(c.m_21233_()));
            level.m_8767_((ParticleOptions)ParticleTypes.f_123796_, at.f_82479_, at.f_82480_ + 1.0, at.f_82481_, 12, 0.3, 0.6, 0.3, 0.02);
        }
        catch (RuntimeException e) {
            DmzUltimateKey.LOG.warn("[DMZ Ultimate Key] NPC faction soldier {} failed to set up", (Object)s.name, (Object)e);
            AgentClones.remove((LivingEntity)c);
            return null;
        }
        return c;
    }

    private static void character(AgentPlayer c, Spec s) {
        Dmz.stats((Entity)c).ifPresent(d -> {
            d.getStatus().setHasCreatedCharacter(true);
            d.getStatus().setAlive(true);
            if (!s.race.isBlank()) {
                AgentLooks.randomize(c, s.race);
            }
            Character ch = d.getCharacter();
            if (!s.characterClass.isBlank()) {
                ch.setCharacterClass(s.characterClass.trim().toLowerCase(Locale.ROOT));
            }
            if (s.stats != null && s.stats.length >= 6) {
                Stats st = d.getStats();
                st.setStrength(Math.max(1, s.stats[0]));
                st.setStrikePower(Math.max(1, s.stats[1]));
                st.setResistance(Math.max(1, s.stats[2]));
                st.setVitality(Math.max(1, s.stats[3]));
                st.setKiPower(Math.max(1, s.stats[4]));
                st.setEnergy(Math.max(1, s.stats[5]));
            }
            if (!s.hairCode.isBlank()) {
                FactionBodies.applyHair(ch, s.hairCode.trim());
            }
            NetworkHandler.sendToTrackingEntityAndSelf((Object)new StatsSyncS2C((ServerPlayer)c), (Entity)c);
        });
    }

    static void applyHair(Character ch, String code) {
        // DMZ 2.2 keeps one style per HairStyleSlot; HairCodec still reads the 2.1 (DMZ1/DMZF1) codes.
        try {
            if (HairCodec.isFullSetCode((String)code)) {
                EnumMap<HairStyleSlot, CustomHair> set = HairCodec.fromFullSetCode((String)code);
                if (set == null || set.get(HairStyleSlot.BASE) == null) {
                    return;
                }
                for (Map.Entry<HairStyleSlot, CustomHair> e : set.entrySet()) {
                    if (e.getValue() == null) continue;
                    ch.setHairStyle(e.getKey(), e.getValue());
                }
            } else {
                CustomHair h = HairCodec.fromCode((String)code);
                if (h == null) {
                    return;
                }
                ch.setHairStyle(HairStyleSlot.BASE, h);
            }
            ch.setHairId(0);
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    public static String hairCodeOf(ServerPlayer p) {
        // The whole set, so a copy keeps the player's transformation hair too.
        return Dmz.stats((Entity)p).map(d -> {
            try {
                EnumMap<HairStyleSlot, CustomHair> styles = d.getCharacter().getHairStyles();
                return styles == null || styles.get(HairStyleSlot.BASE) == null ? "" : HairCodec.toFullSetCode(styles);
            }
            catch (RuntimeException e) {
                return "";
            }
        }).orElse("");
    }

    private static JsonObject profile(Spec s) {
        JsonObject o = new JsonObject();
        JsonObject def = new JsonObject();
        if (s.post != null) {
            def.addProperty("job", "guard");
            def.add("pos", (JsonElement)AgentBrain.arr(s.post));
        } else {
            def.addProperty("job", "idle");
        }
        o.add("default", (JsonElement)def);
        o.add("schedule", (JsonElement)new JsonArray());
        o.addProperty("defend", Boolean.valueOf(true));
        JsonObject combat = new JsonObject();
        combat.addProperty("fly", Boolean.valueOf(true));
        combat.addProperty("block", (Number)0.3);
        combat.addProperty("dodge", (Number)0.2);
        combat.addProperty("recharge_ki", (Number)25);
        JsonArray skills = new JsonArray();
        for (String id : s.skills.keySet()) {
            DynamicSkill sk = DynamicSkills.get(id);
            if (sk == null || !sk.canCast()) continue;
            skills.add(id.toLowerCase(Locale.ROOT));
        }
        if (!skills.isEmpty()) {
            combat.add("skills", (JsonElement)skills);
        }
        o.add("combat", (JsonElement)combat);
        return o;
    }

    private static void tag(AgentPlayer c, Spec s) {
        ServerScoreboard board = c.m_20194_().m_129896_();
        PlayerTeam team = board.m_83489_(s.teamId);
        if (team == null) {
            team = board.m_83492_(s.teamId);
        }
        team.m_83351_(s.color);
        team.m_83360_((Component)Component.m_237113_((String)s.prefix).m_130940_(s.color));
        board.m_6546_(c.m_6302_(), team);
    }

    private static GameProfile freeProfile(MinecraftServer server, String wanted) {
        String base;
        String string = base = wanted == null ? "Soldier" : wanted.replaceAll("[^A-Za-z0-9_]", "");
        if (base.length() < 3) {
            base = "Soldier";
        }
        if (base.length() > 13) {
            base = base.substring(0, 13);
        }
        Object name = base;
        int n = 2;
        while (!FactionBodies.nameFree(server, (String)name)) {
            if (n > 99) {
                return null;
            }
            name = base + n;
            ++n;
        }
        for (int slot = 1; slot <= 64; ++slot) {
            UUID id = UUID.nameUUIDFromBytes(("dmzultimatekey:npcfaction:slot:" + slot).getBytes(StandardCharsets.UTF_8));
            if (server.m_6846_().m_11259_(id) != null) continue;
            return new GameProfile(id, (String)name);
        }
        return null;
    }

    public static boolean nameFree(MinecraftServer server, String name) {
        if (server.m_6846_().m_11255_(name) != null || Agents.bodyOf(name) != null) {
            return false;
        }
        for (String s : Agents.savedNames()) {
            if (!s.equalsIgnoreCase(name)) continue;
            return false;
        }
        return true;
    }

    public static boolean alive(LivingEntity e) {
        return e != null && AgentClones.live((Entity)e) && e.m_6084_() && !e.m_213877_();
    }

    public static void remove(LivingEntity e) {
        AgentClones.remove(e);
    }

    public static void attack(LivingEntity body, LivingEntity target) {
        AgentClones.attack(body, target);
    }

    public static Entity target(LivingEntity body) {
        return AgentClones.target(body);
    }

    public static boolean realPlayer(Entity e) {
        return e instanceof ServerPlayer && !(e instanceof AgentPlayer);
    }

    public static List<String> templateIds() {
        return new ArrayList<String>(AgentLand.templates().keySet());
    }

    public static Build prepare(ServerLevel level, String templateId, BlockPos near, int radius, List<AABB> avoid) {
        AgentLand.Template t = AgentLand.templates().get(templateId == null ? "" : templateId.toLowerCase(Locale.ROOT));
        if (t == null) {
            return null;
        }
        int step = 4;
        int checked = 0;
        for (int r = 8; r <= radius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    if (++checked > 200) {
                        return null;
                    }
                    int x = near.m_123341_() + dx;
                    int z = near.m_123343_() + dz;
                    Rotation rot = (Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90) : (dz > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180)).m_55952_(Rotation.CLOCKWISE_180);
                    BlockPos origin = FactionBodies.ground(level, t, x, z, rot);
                    if (origin == null) continue;
                    AABB box = AgentLand.box(t, origin, rot);
                    boolean clash = false;
                    if (avoid != null) {
                        for (AABB a : avoid) {
                            if (!a.m_82400_(3.0).m_82381_(box)) continue;
                            clash = true;
                        }
                    }
                    if (clash || AgentLand.checkSite(level, t, origin, rot) != null) continue;
                    List<AgentLand.Step> plan = AgentLand.plan(level, t, origin, rot);
                    Vec3 home = null;
                    for (AgentLand.Step s : plan) {
                        if (!"home".equals(s.marker()) && (!"trader".equals(s.marker()) || home != null)) continue;
                        home = Vec3.m_82539_((Vec3i)s.pos());
                    }
                    if (home == null) {
                        home = new Vec3(box.m_82399_().f_82479_, (double)(origin.m_123342_() + 1), box.m_82399_().f_82481_);
                    }
                    return new Build(level, t.id(), origin, home, box, plan);
                }
            }
        }
        return null;
    }

    private static BlockPos ground(ServerLevel level, AgentLand.Template t, int x, int z, Rotation rot) {
        AABB foot = AgentLand.box(t, new BlockPos(x, 0, z), rot);
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        ArrayList<Integer> hs = new ArrayList<Integer>();
        int bx = (int)foot.f_82288_;
        while ((double)bx < foot.f_82291_) {
            int bz = (int)foot.f_82290_;
            while ((double)bz < foot.f_82293_) {
                if (!level.m_7232_(bx >> 4, bz >> 4)) {
                    return null;
                }
                int h = level.m_6924_(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                BlockState g = level.m_8055_(new BlockPos(bx, h - 1, bz));
                if (!g.m_60819_().m_76178_() || g.m_60795_()) {
                    return null;
                }
                minY = Math.min(minY, h);
                maxY = Math.max(maxY, h);
                hs.add(h);
                bz += 2;
            }
            bx += 2;
        }
        if (hs.isEmpty() || maxY - minY > (t.foundation() != null ? 6 : 3) || minY <= level.m_141937_() + 2) {
            return null;
        }
        hs.sort(Integer::compare);
        return new BlockPos(x, (Integer)hs.get(hs.size() / 2) - 1, z);
    }

    public static String spawnResident(MinecraftServer server, String name, ServerLevel level, Vec3 home, String role, String race, String agentFaction) {
        AgentPlayer p;
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            return "bad name";
        }
        if (!FactionBodies.nameFree(server, name)) {
            return "name taken";
        }
        try {
            p = Agents.spawn(server, name, level, home);
        }
        catch (RuntimeException e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
        if (race != null && !race.isBlank()) {
            AgentLooks.randomize(p, race);
        }
        JsonObject prof = p.brain.profile;
        if (role != null && !role.isBlank()) {
            prof.addProperty("role", role);
        }
        prof.add("home", (JsonElement)AgentBrain.arr(home));
        JsonObject base = prof.has("base") && prof.get("base").isJsonObject() ? prof.getAsJsonObject("base") : new JsonObject();
        base.addProperty("dim", level.m_46472_().m_135782_().toString());
        prof.add("base", (JsonElement)base);
        prof.addProperty("roam_radius", (Number)48);
        p.brain.roleKey = "";
        p.brain.roleJob = null;
        p.brain.save(name);
        if (agentFaction != null && !agentFaction.isBlank() && AgentFactions.get(agentFaction) != null) {
            AgentFactions.enlist(agentFaction, name);
        }
        Agents.log(p, "npc_faction_resident", o -> o.addProperty("role", role == null ? "" : role));
        return null;
    }

    public static boolean residentLive(String name) {
        return Agents.bodyOf(name) != null;
    }

    public static boolean residentKnown(String name) {
        for (String s : Agents.savedNames()) {
            if (!s.equalsIgnoreCase(name)) continue;
            return true;
        }
        return false;
    }

    public static double livePower(String name) {
        ServerPlayer p = Agents.bodyOf(name);
        return p == null ? 0.0 : Dmz.stats((Entity)p).map(d -> (double)d.getBattlePower()).orElse(0.0);
    }

    public static List<String> agentFactionIds() {
        return new ArrayList<String>(AgentFactions.all().keySet());
    }

    public static String agentFactionName(String id) {
        return AgentBrain.str(AgentFactions.get(id), "name", id);
    }

    public static String agentFactionLeader(String id) {
        return AgentFactions.leaderOf(id);
    }

    public static List<String> agentFactionMembers(String id) {
        return AgentFactions.membersOf(id);
    }

    public static boolean agentFactionExists(String id) {
        return id != null && AgentFactions.get(id) != null;
    }

    public static final class Spec {
        public String name = "Soldier";
        public UUID faction;
        public String teamId = "dukf_npc";
        public String prefix = "";
        public ChatFormatting color = ChatFormatting.RED;
        public String race = "";
        public String characterClass = "";
        public String hairCode = "";
        public int[] stats;
        public final Map<EquipmentSlot, ItemStack> gear = new LinkedHashMap<EquipmentSlot, ItemStack>();
        public final Map<String, Integer> skills = new LinkedHashMap<String, Integer>();
        public Vec3 post;
    }

    public static final class Build {
        public final String template;
        public final BlockPos origin;
        public final Vec3 home;
        public final AABB box;
        private final ServerLevel level;
        private final Deque<AgentLand.Step> steps;

        private Build(ServerLevel level, String template, BlockPos origin, Vec3 home, AABB box, List<AgentLand.Step> steps) {
            this.level = level;
            this.template = template;
            this.origin = origin;
            this.home = home;
            this.box = box;
            this.steps = new ArrayDeque<AgentLand.Step>(steps);
        }

        public boolean placeSome(int budget) {
            int n = 0;
            while (!this.steps.isEmpty() && n < budget) {
                BlockEntity be;
                BlockState now;
                AgentLand.Step s = this.steps.poll();
                if (s.marker() != null || s.state() == null || !this.level.m_46749_(s.pos()) || (now = this.level.m_8055_(s.pos())).equals(s.state())) continue;
                this.level.m_7731_(s.pos(), s.state(), 2);
                if (s.nbt() != null && (be = this.level.m_7702_(s.pos())) != null) {
                    CompoundTag t = s.nbt().m_6426_();
                    t.m_128405_("x", s.pos().m_123341_());
                    t.m_128405_("y", s.pos().m_123342_());
                    t.m_128405_("z", s.pos().m_123343_());
                    try {
                        be.m_142466_(t);
                        be.m_6596_();
                    }
                    catch (RuntimeException runtimeException) {
                        // empty catch block
                    }
                }
                ++n;
            }
            return this.steps.isEmpty();
        }
    }
}

