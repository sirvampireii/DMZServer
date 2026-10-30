package com.dmzlegacy.ultimatekey.agent;

import com.dmzlegacy.ultimatekey.DmzUltimateKey;
import com.dmzlegacy.ultimatekey.agent.AgentPlayer;
import com.dmzlegacy.ultimatekey.agent.Agents;
import com.dmzlegacy.ultimatekey.dmz.Dmz;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.player.AttackHand;
import com.dragonminez.common.network.C2S.CombatAttackRequestC2S;
import com.dragonminez.common.network.C2S.DashC2S;
import com.dragonminez.common.network.C2S.EquipTechniqueC2S;
import com.dragonminez.common.network.C2S.ExecuteActionC2S;
import com.dragonminez.common.network.C2S.FlyToggleC2S;
import com.dragonminez.common.network.C2S.IncreaseStatC2S;
import com.dragonminez.common.network.C2S.InstantTransmissionTapC2S;
import com.dragonminez.common.network.C2S.InstantTransmissionTravelToPlayerC2S;
import com.dragonminez.common.network.C2S.KiBlastC2S;
import com.dragonminez.common.network.C2S.SelectFormC2S;
import com.dragonminez.common.network.C2S.SelectTechniqueSlotC2S;
import com.dragonminez.common.network.C2S.StrikeAttackC2S;
import com.dragonminez.common.network.C2S.SwitchActionC2S;
import com.dragonminez.common.network.C2S.TaiyokenCastC2S;
import com.dragonminez.common.network.C2S.TechniqueChargeC2S;
import com.dragonminez.common.network.C2S.UpdateSkillC2S;
import com.dragonminez.common.network.C2S.UpdateStatC2S;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

final class AgentDmz {
    private static Constructor<NetworkEvent.Context> ctor;

    private AgentDmz() {
    }

    static Supplier<NetworkEvent.Context> context(AgentPlayer p) {
        try {
            if (ctor == null) {
                for (Constructor<?> c : NetworkEvent.Context.class.getDeclaredConstructors()) {
                    Class<?>[] t = c.getParameterTypes();
                    if (t.length != 3 || t[0] != Connection.class || t[1] != NetworkDirection.class || t[2] != Integer.TYPE) continue;
                    c.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Constructor<NetworkEvent.Context> found = (Constructor<NetworkEvent.Context>)c;
                    ctor = found;
                }
                if (ctor == null) {
                    throw new IllegalStateException("no usable NetworkEvent.Context constructor");
                }
            }
            NetworkEvent.Context ctx = ctor.newInstance(p.f_8906_.f_9742_, NetworkDirection.PLAY_TO_SERVER, 0);
            return () -> ctx;
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not build a packet context for " + p.m_36316_().getName(), e);
        }
    }

    static void selectForm(AgentPlayer p, String key) {
        boolean stack = key.startsWith("stack:");
        String rest = stack ? key.substring(6) : (key.contains(":") ? key.substring(key.indexOf(58) + 1) : key);
        int dot = rest.lastIndexOf(46);
        if (dot <= 0) {
            throw new IllegalArgumentException("write the form as group.form or stack:group.form");
        }
        new SwitchActionC2S(stack ? ActionMode.STACK : ActionMode.FORM).handle(AgentDmz.context(p));
        new SelectFormC2S(rest.substring(0, dot), rest.substring(dot + 1), stack).handle(AgentDmz.context(p));
    }

    static void transform(AgentPlayer p) {
        AgentDmz.action(p, ExecuteActionC2S.ActionType.INSTANT_TRANSFORM);
    }

    static void descend(AgentPlayer p) {
        AgentDmz.action(p, ExecuteActionC2S.ActionType.FORCE_DESCEND);
    }

    static void action(AgentPlayer p, ExecuteActionC2S.ActionType type) {
        new ExecuteActionC2S(type).handle(AgentDmz.context(p));
    }

    static void hold(AgentPlayer p, UpdateStatC2S.StatAction what, boolean down) {
        UpdateStatC2S.handle((UpdateStatC2S)new UpdateStatC2S(what, down), AgentDmz.context(p));
    }

    static AttackHand nextAttack(AgentPlayer p) {
        float cd = PlayerAttackHelper.getAttackCooldownTicksCapped((Player)p);
        if ((double)(p.m_9236_().m_46467_() - p.control.lastSwing) > Math.ceil(cd) + 30.0) {
            p.control.combo = 0;
        }
        if (PlayerAttackHelper.isChargingTechnique((Player)p)) {
            return null;
        }
        return PlayerAttackHelper.getCurrentAttack((Player)p, (int)p.control.combo);
    }

    static int[] melee(AgentPlayer p, Entity target) {
        AttackHand hand = AgentDmz.nextAttack(p);
        if (hand == null) {
            return null;
        }
        p.control.lastSwing = p.m_9236_().m_46467_();
        int step = p.control.combo++;
        double range = PlayerAttackHelper.getEffectiveAttackRange((Player)p, (double)hand.attributes().attackRange());
        double halfAngle = Math.max(30.0, hand.attack().angle() > 0.0 ? hand.attack().angle() / 2.0 : 0.0);
        String shape = String.valueOf(hand.attack().hitbox());
        if (shape.contains("PLANE")) {
            halfAngle = Math.max(halfAngle, 70.0);
        }
        ArrayList<Integer> ids = new ArrayList<Integer>();
        ids.add(target.m_19879_());
        Vec3 eye = p.m_146892_();
        Vec3 look = p.m_20154_();
        double cos = Math.cos(Math.toRadians(halfAngle));
        for (LivingEntity e2 : p.m_9236_().m_6443_(LivingEntity.class, p.m_20191_().m_82400_(range + 1.0), e -> e != target && e != p && e.m_6084_() && e instanceof Enemy)) {
            Vec3 to = e2.m_20191_().m_82399_().m_82546_(eye);
            if (!(to.m_82553_() <= range + (double)(e2.m_20205_() / 2.0f)) || !(to.m_82541_().m_82526_(look) >= cos) || ids.size() >= 64) continue;
            ids.add(e2.m_19879_());
        }
        new CombatAttackRequestC2S(step, p.m_6144_(), p.m_150109_().f_35977_, ids.stream().mapToInt(Integer::intValue).toArray()).handle(AgentDmz.context(p));
        Agents.log(p, "combo", o -> {
            o.addProperty("step", hand.combo().current() + "/" + hand.combo().total());
            o.addProperty("anim", hand.attack().animation());
            o.addProperty("targets", (Number)ids.size());
        });
        return new int[]{hand.combo().current(), hand.combo().total()};
    }

    static int upswingTicks(AgentPlayer p) {
        AttackHand hand = AgentDmz.nextAttack(p);
        if (hand == null) {
            return 0;
        }
        float speed = Math.max(0.55f, Math.min(1.35f, 12.0f / Math.max(AgentDmz.cooldownTicks(p), 0.001f)));
        int anim = Math.max(8, Math.round(12.0f / speed));
        return Math.max(1, Math.round((float)anim * (float)hand.upswingRate() * 0.4f));
    }

    static float cooldownTicks(AgentPlayer p) {
        return PlayerAttackHelper.getAttackCooldownTicksCapped((Player)p);
    }

    static int itLevel(AgentPlayer p) {
        return Dmz.stats((Entity)p).map(d -> d.getSkills().getSkillLevel("instant_transmission")).orElse(0);
    }

    static boolean itTap(AgentPlayer p, LivingEntity t) {
        Vec3 before = p.m_20182_();
        new InstantTransmissionTapC2S(t.m_20148_()).handle(AgentDmz.context(p));
        return p.m_20182_().m_82557_(before) > 1.0;
    }

    static void itTapAny(AgentPlayer p) {
        new InstantTransmissionTapC2S((UUID)null).handle(AgentDmz.context(p));
    }

    static boolean itTravel(AgentPlayer p, ServerPlayer t) {
        Vec3 before = p.m_20182_();
        ResourceKey dim = p.m_9236_().m_46472_();
        new InstantTransmissionTravelToPlayerC2S(t.m_20148_()).handle(AgentDmz.context(p));
        return p.m_9236_().m_46472_() != dim || p.m_20182_().m_82557_(before) > 1.0;
    }

    static void block(AgentPlayer p, boolean down) {
        AgentDmz.hold(p, UpdateStatC2S.StatAction.BLOCK, down);
    }

    static void dash(AgentPlayer p, float x, float z, boolean doubleDash) {
        new DashC2S(x, z, doubleDash).handle(AgentDmz.context(p));
    }

    static void kiBlast(AgentPlayer p, boolean shooting) {
        KiBlastC2S.handle((KiBlastC2S)new KiBlastC2S(shooting, 0xFFFFFF, 4172287), AgentDmz.context(p));
    }

    static void kiBlastTap(AgentPlayer p) {
        if (p.control.kiBlastRelease > 0L) {
            return;
        }
        AgentDmz.kiBlast(p, true);
        p.control.kiBlastRelease = p.m_9236_().m_46467_() + 10L;
    }

    static void tickKeys(AgentPlayer p) {
        if (p.control.kiBlastRelease > 0L && p.m_9236_().m_46467_() >= p.control.kiBlastRelease) {
            p.control.kiBlastRelease = 0L;
            AgentDmz.safe(p, "ki blast", () -> AgentDmz.kiBlast(p, false));
        }
    }

    static void flyToggle(AgentPlayer p, boolean enable) {
        FlyToggleC2S.handle((FlyToggleC2S)new FlyToggleC2S(enable), AgentDmz.context(p));
    }

    static void instantTransmission(AgentPlayer p, UUID target) {
        new InstantTransmissionTapC2S(target).handle(AgentDmz.context(p));
    }

    static void increaseStat(AgentPlayer p, String stat, int n) {
        IncreaseStatC2S.StatType type = IncreaseStatC2S.StatType.valueOf((String)stat);
        IncreaseStatC2S.handle((IncreaseStatC2S)new IncreaseStatC2S(type, n), AgentDmz.context(p));
    }

    static void learnSkill(AgentPlayer p, String id, boolean owned) {
        UpdateSkillC2S.SkillAction action = owned ? UpdateSkillC2S.SkillAction.UPGRADE : UpdateSkillC2S.SkillAction.PURCHASE;
        new UpdateSkillC2S(action, id, 0).handle(AgentDmz.context(p));
    }

    static void equipTechnique(AgentPlayer p, int slot, String id) {
        new EquipTechniqueC2S(slot, id).handle(AgentDmz.context(p));
    }

    static void techniqueStart(AgentPlayer p, int slot, int targetId) {
        TechniqueChargeC2S.handle((TechniqueChargeC2S)TechniqueChargeC2S.start((int)slot, (int)targetId), AgentDmz.context(p));
    }

    static boolean techniquePress(AgentPlayer p, int slot, int targetId) {
        StatsData d = Dmz.stats((Entity)p).orElse(null);
        if (d == null) {
            return false;
        }
        String[] slots = d.getTechniques().getEquippedSlots();
        if (slot >= slots.length || slots[slot] == null || slots[slot].isEmpty()) {
            return false;
        }
        TechniqueData td = (TechniqueData)d.getTechniques().getUnlockedTechniques().get(slots[slot]);
        if (td instanceof StrikeAttackData) {
            AgentDmz.strike(p, slot, targetId);
            return false;
        }
        if (!(td instanceof KiAttackData)) {
            return false;
        }
        if ("taiyoken".equals(slots[slot])) {
            new SelectTechniqueSlotC2S(slot).handle(AgentDmz.context(p));
            new TaiyokenCastC2S().handle(AgentDmz.context(p));
            return false;
        }
        AgentDmz.techniqueStart(p, slot, targetId);
        return true;
    }

    static void techniqueRelease(AgentPlayer p) {
        TechniqueChargeC2S.handle((TechniqueChargeC2S)TechniqueChargeC2S.setHolding((boolean)false), AgentDmz.context(p));
    }

    static void strike(AgentPlayer p, int slot, int targetId) {
        new SelectTechniqueSlotC2S(slot).handle(AgentDmz.context(p));
        new StrikeAttackC2S(targetId).handle(AgentDmz.context(p));
    }

    static boolean safe(AgentPlayer p, String what, Runnable r) {
        try {
            r.run();
            return true;
        }
        catch (RuntimeException e) {
            DmzUltimateKey.LOG.warn("[DMZ Ultimate Key] agent {}: {} failed", new Object[]{p.m_36316_().getName(), what, e});
            return false;
        }
    }
}

