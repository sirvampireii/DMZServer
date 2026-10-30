package com.dmzlegacy.ultimatekey.engine.comp;

import com.dmzlegacy.ultimatekey.dmz.Dmz;
import com.dmzlegacy.ultimatekey.engine.Cast;
import com.dmzlegacy.ultimatekey.engine.ChargeCasting;
import com.dmzlegacy.ultimatekey.engine.ComponentRegistry;
import com.dmzlegacy.ultimatekey.engine.Components;
import com.dmzlegacy.ultimatekey.engine.EffectComponent;
import com.dmzlegacy.ultimatekey.engine.LockOnTargets;
import com.dmzlegacy.ultimatekey.engine.Settings;
import com.dmzlegacy.ultimatekey.engine.SkillTarget;
import com.dmzlegacy.ultimatekey.engine.comp.Mechanics;
import com.dmzlegacy.ultimatekey.progression.Restrictions;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import com.dragonminez.server.events.players.combat.StrikeAttackHandler;
import com.dragonminez.server.events.players.combat.TaiyokenHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DmzActions {
    private static final List<Over> OVER_TIME = new ArrayList<Over>();

    private DmzActions() {
    }

    public static void register() {
        DmzActions.mech("dmz technique", Technique::new);
        DmzActions.mech("dmz power release", PowerRelease::new);
        DmzActions.mech("dmz mastery", Mastery::new);
        DmzActions.mech("dmz resource over time", ResourceOverTime::new);
        DmzActions.mech("dmz status", DmzStatus::new);
        DmzActions.mech("dmz interrupt", Interrupt::new);
        ComponentRegistry.register(EffectComponent.Kind.CONDITION, "dmz mastery", MasteryCond::new);
        ComponentRegistry.register(EffectComponent.Kind.CONDITION, "dmz temp form", TempFormCond::new);
        ComponentRegistry.register(EffectComponent.Kind.CONDITION, "dmz lock on", LockOnCond::new);
        ComponentRegistry.register(EffectComponent.Kind.TARGET, "lock on", LockOnTarget::new);
    }

    private static void mech(String key, Supplier<? extends Components.Mechanic> f) {
        ComponentRegistry.register(EffectComponent.Kind.MECHANIC, key, f);
    }

    private static void sync(ServerPlayer p) {
        NetworkHandler.sendToTrackingEntityAndSelf((Object)new StatsSyncS2C(p), (Entity)p);
    }

    public static boolean useTechnique(ServerPlayer p, String techniqueId, int target, boolean ignoreCooldown, boolean free) {
        StatsData d = Dmz.stats((Entity)p).orElse(null);
        String id = techniqueId.trim().toLowerCase(Locale.ROOT);
        if (d == null || id.isEmpty()) {
            return false;
        }
        Techniques t = d.getTechniques();
        TechniqueData tech = (TechniqueData)t.getUnlockedTechniques().get(id);
        if (tech == null) {
            return false;
        }
        if (ignoreCooldown) {
            d.getCooldowns().removeCooldown("TechniqueCooldown_" + id);
        }
        if (id.equals("taiyoken")) {
            TaiyokenHandler.cast((ServerPlayer)p);
            return true;
        }
        if (tech instanceof StrikeAttackData) {
            StrikeAttackData strike = (StrikeAttackData)tech;
            if (free) {
                d.getResources().addEnergy((float)strike.getCalculatedCost(d));
            }
            Object[] before = (String[])t.getEquippedSlots().clone();
            int selected = t.getSelectedSlot();
            int slot = Arrays.asList(before).indexOf(id);
            if (slot < 0) {
                slot = Math.max(0, selected);
                t.equipOrSwapTechnique(slot, id);
            }
            t.selectSlot(slot);
            StrikeAttackHandler.requestStrike((ServerPlayer)p, (int)target);
            if (!Arrays.equals(before, t.getEquippedSlots()) && before[slot] != null) {
                t.equipOrSwapTechnique(slot, (String)before[slot]);
            }
            t.selectSlot(selected);
            NetworkHandler.sendToTrackingEntityAndSelf((Object)new ProgressionSyncS2C(p), (Entity)p);
            return true;
        }
        if (tech instanceof KiAttackData) {
            if (t.getChargingTechniqueId() != null && !t.getChargingTechniqueId().isEmpty()) {
                return false;
            }
            t.startTechniqueCharge(id);
            t.setChargeHolding(false);
            t.setHomingTargetId(target);
            NetworkHandler.sendToTrackingEntityAndSelf((Object)new ProgressionSyncS2C(p), (Entity)p);
            return true;
        }
        return false;
    }

    private static boolean stack(Settings s) {
        return s.getString("form", "").trim().toLowerCase(Locale.ROOT).startsWith("stack:") || s.getBool("stack", false);
    }

    private static String[] form(Settings s, StatsData d) {
        String[] stringArray;
        String f = s.getString("form", "").trim().toLowerCase(Locale.ROOT);
        if (f.isEmpty() || f.equals("any") || f.equals("active")) {
            String[] stringArray2;
            String n;
            Character c = d.getCharacter();
            boolean stack = s.getBool("stack", false);
            String g = stack ? c.getActiveStackFormGroup() : c.getActiveFormGroup();
            String string = n = stack ? c.getActiveStackForm() : c.getActiveForm();
            if (g == null || n == null || n.isEmpty()) {
                stringArray2 = null;
            } else {
                String[] stringArray3 = new String[2];
                stringArray3[0] = g;
                stringArray2 = stringArray3;
                stringArray3[1] = n;
            }
            return stringArray2;
        }
        if (f.startsWith("temp:")) {
            return new String[]{"duktemp", f.substring(5)};
        }
        String rest = f.contains(":") ? f.substring(f.indexOf(58) + 1) : f;
        int dot = rest.lastIndexOf(46);
        if (dot <= 0) {
            stringArray = null;
        } else {
            String[] stringArray4 = new String[2];
            stringArray4[0] = rest.substring(0, dot);
            stringArray = stringArray4;
            stringArray4[1] = rest.substring(dot + 1);
        }
        return stringArray;
    }

    private static double maxMastery(StatsData d, boolean stack, String group, String form) {
        FormConfig.FormData fd = stack ? ConfigManager.getStackForm((String)group, (String)form) : ConfigManager.getForm((String)d.getCharacter().getRaceName(), (String)group, (String)form);
        return fd != null ? fd.getMaxMastery() : 100.0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || OVER_TIME.isEmpty()) {
            return;
        }
        Iterator<Over> it = OVER_TIME.iterator();
        while (it.hasNext()) {
            StatsData d;
            Over o = it.next();
            LivingEntity t = o.target();
            if (!t.m_6084_() || t.m_213877_() || t.m_9236_().m_46467_() > o.end()) {
                it.remove();
                continue;
            }
            if (t.m_9236_().m_46467_() % 20L != 0L || (d = (StatsData)Dmz.stats((Entity)t).orElse(null)) == null) continue;
            Resources r = d.getResources();
            double v = o.percent() ? o.perSecond() / 100.0 * (double)(o.stamina() ? d.getMaxStamina() : d.getMaxEnergy()) : o.perSecond();
            if (o.stamina()) {
                if (v >= 0.0) {
                    r.addStamina((float)v);
                    continue;
                }
                r.removeStamina((float)Math.min(-v, (double)r.getCurrentStamina()));
                continue;
            }
            if (v >= 0.0) {
                r.addEnergy((float)v);
                continue;
            }
            r.removeEnergy((float)Math.min(-v, (double)r.getCurrentEnergy()));
        }
    }

    private record Over(LivingEntity target, boolean stamina, boolean percent, double perSecond, long end) {
    }

    public static final class LockOnTarget
    extends Components.Target {
        @Override
        public String key() {
            return "lock on";
        }

        @Override
        protected List<SkillTarget> getTargets(Cast cast, List<SkillTarget> sources) {
            LivingEntity locked = LockOnTargets.get(cast.caster());
            if (locked == null || (double)locked.m_20270_((Entity)cast.caster()) > this.attr(cast, "range", 64.0)) {
                return List.of();
            }
            return List.of(SkillTarget.of(locked));
        }
    }

    public static final class LockOnCond
    extends Components.Condition {
        @Override
        public String key() {
            return "dmz lock on";
        }

        @Override
        protected boolean test(Cast cast, SkillTarget t) {
            LivingEntity locked = LockOnTargets.get(cast.caster());
            boolean is = locked != null && locked == t.entity();
            return is != this.settings.getBool("inverted", false);
        }
    }

    public static final class TempFormCond
    extends Components.Condition {
        @Override
        public String key() {
            return "dmz temp form";
        }

        @Override
        protected boolean test(Cast cast, SkillTarget t) {
            StatsData d = t.entity() == null ? null : (StatsData)Dmz.stats((Entity)t.entity()).orElse(null);
            boolean in = false;
            if (d != null && "duktemp".equalsIgnoreCase(String.valueOf(d.getCharacter().getActiveFormGroup()))) {
                String form = String.valueOf(d.getCharacter().getActiveForm()).toLowerCase(Locale.ROOT);
                List<String> want = this.settings.getStringList("forms");
                in = want.isEmpty() || want.stream().anyMatch(w -> w.equalsIgnoreCase("any") || w.trim().toLowerCase(Locale.ROOT).endsWith(form));
            }
            return in != this.settings.getBool("inverted", false);
        }
    }

    public static final class MasteryCond
    extends Components.Condition {
        @Override
        public String key() {
            return "dmz mastery";
        }

        @Override
        protected boolean test(Cast cast, SkillTarget t) {
            StatsData d;
            StatsData statsData = d = t.entity() == null ? null : (StatsData)Dmz.stats((Entity)t.entity()).orElse(null);
            if (d == null) {
                return false;
            }
            String[] gf = DmzActions.form(this.settings, d);
            if (gf == null) {
                return false;
            }
            boolean stack = DmzActions.stack(this.settings);
            FormMasteries m = stack ? d.getCharacter().getStackFormMasteries() : d.getCharacter().getFormMasteries();
            double v = m.getMastery(gf[0], gf[1]);
            if (this.settings.getBool("percent", false)) {
                v = v * 100.0 / Math.max(1.0E-6, DmzActions.maxMastery(d, stack, gf[0], gf[1]));
            }
            return v >= this.attr(cast, "min", Double.NEGATIVE_INFINITY) && v <= this.attr(cast, "max", Double.POSITIVE_INFINITY);
        }
    }

    public static final class Interrupt
    extends Mechanics.PerEntity {
        @Override
        public String key() {
            return "dmz interrupt";
        }

        @Override
        protected boolean apply(Cast cast, LivingEntity t) {
            boolean did = false;
            if (t instanceof ServerPlayer) {
                StatsData d;
                ServerPlayer p = (ServerPlayer)t;
                if (ChargeCasting.active(p) != null && p != cast.caster()) {
                    ChargeCasting.interrupt(p);
                    did = true;
                }
                if ((d = (StatsData)Dmz.stats((Entity)p).orElse(null)) != null && d.getTechniques().getChargingTechniqueId() != null && !d.getTechniques().getChargingTechniqueId().isEmpty()) {
                    d.getTechniques().clearTechniqueCharge();
                    NetworkHandler.sendToTrackingEntityAndSelf((Object)new ProgressionSyncS2C(p), (Entity)p);
                    did = true;
                }
            }
            return did;
        }
    }

    public static final class DmzStatus
    extends Mechanics.PerEntity {
        @Override
        public String key() {
            return "dmz status";
        }

        @Override
        protected boolean apply(Cast cast, LivingEntity t) {
            String status;
            int ticks = (int)Math.max(1L, Math.round(this.attr(cast, "seconds", 2.0) * 20.0));
            boolean clear = this.settings.getBool("clear", false);
            switch (status = this.settings.option("status", "stun")) {
                case "stun": {
                    DmzStatus.effect(t, (MobEffect)MainEffects.STUN.get(), ticks, 0, clear);
                    break;
                }
                case "stagger": {
                    DmzStatus.effect(t, (MobEffect)MainEffects.STAGGER.get(), ticks, 1, clear);
                    break;
                }
                case "ki slow": {
                    DmzStatus.effect(t, (MobEffect)MainEffects.KI_SLOW.get(), ticks, 0, clear);
                    break;
                }
                case "knockdown": {
                    StatsData d = Dmz.stats((Entity)t).orElse(null);
                    if (d == null) {
                        return false;
                    }
                    d.getStatus().setKnockedDown(!clear);
                    if (clear) {
                        d.getCooldowns().removeCooldown("KnockdownDuration");
                    } else {
                        d.getCooldowns().setCooldown("KnockdownDuration", ticks);
                    }
                    if (!(t instanceof ServerPlayer)) break;
                    ServerPlayer p = (ServerPlayer)t;
                    DmzActions.sync(p);
                    break;
                }
                default: {
                    if (!(t instanceof ServerPlayer)) {
                        return false;
                    }
                    ServerPlayer p = (ServerPlayer)t;
                    String what = switch (status) {
                        case "no ki attacks" -> "ki_attacks";
                        case "no ki weapons" -> "ki_weapons";
                        case "no transform" -> "transform";
                        case "no magic" -> "iron_spells";
                        default -> null;
                    };
                    if (what == null) {
                        return false;
                    }
                    if (clear) {
                        Restrictions.unlock(p, what);
                        break;
                    }
                    Restrictions.lockFor(p, what, ticks);
                }
            }
            return true;
        }

        private static void effect(LivingEntity t, MobEffect effect, int ticks, int amp, boolean clear) {
            if (clear) {
                t.m_21195_(effect);
            } else {
                t.m_7292_(new MobEffectInstance(effect, ticks, amp, false, false, true));
            }
        }
    }

    public static final class ResourceOverTime
    extends Mechanics.PerEntity {
        @Override
        public String key() {
            return "dmz resource over time";
        }

        @Override
        protected boolean apply(Cast cast, LivingEntity t) {
            if (Dmz.stats((Entity)t).isEmpty()) {
                return false;
            }
            OVER_TIME.add(new Over(t, "stamina".equals(this.settings.option("resource", "ki")), this.settings.getBool("percent", false), this.attr(cast, "value", 5.0), t.m_9236_().m_46467_() + Math.round(this.attr(cast, "seconds", 5.0) * 20.0)));
            return true;
        }
    }

    public static final class Mastery
    extends Mechanics.PerEntity {
        @Override
        public String key() {
            return "dmz mastery";
        }

        @Override
        protected boolean apply(Cast cast, LivingEntity t) {
            StatsData d = Dmz.stats((Entity)t).orElse(null);
            if (d == null) {
                return false;
            }
            String[] gf = DmzActions.form(this.settings, d);
            if (gf == null) {
                return false;
            }
            boolean stack = DmzActions.stack(this.settings);
            FormMasteries m = stack ? d.getCharacter().getStackFormMasteries() : d.getCharacter().getFormMasteries();
            double max = DmzActions.maxMastery(d, stack, gf[0], gf[1]);
            double v = this.attr(cast, "value", 1.0);
            if ("set".equals(this.settings.option("mode", "add"))) {
                m.setMastery(gf[0], gf[1], Math.min(v, max), max);
            } else {
                m.addMastery(gf[0], gf[1], v, max);
            }
            if (t instanceof ServerPlayer) {
                ServerPlayer p = (ServerPlayer)t;
                DmzActions.sync(p);
            }
            return true;
        }
    }

    public static final class PowerRelease
    extends Mechanics.PerEntity {
        @Override
        public String key() {
            return "dmz power release";
        }

        @Override
        protected boolean apply(Cast cast, LivingEntity t) {
            StatsData d = Dmz.stats((Entity)t).orElse(null);
            if (d == null) {
                return false;
            }
            Resources r = d.getResources();
            double v = this.attr(cast, "value", 10.0);
            int next = (int)Math.round("set".equals(this.settings.option("mode", "add")) ? v : (double)r.getPowerRelease() + v);
            if (!this.settings.getBool("ignore-limit", false)) {
                int max = 50 + d.getSkills().getSkillLevel("potentialunlock") * 5;
                if (r.getReleaseLimit() > 0) {
                    max = Math.min(max, r.getReleaseLimit());
                }
                next = Math.min(next, max);
            }
            r.setPowerRelease(Math.max(0, next));
            if (t instanceof ServerPlayer) {
                ServerPlayer p = (ServerPlayer)t;
                DmzActions.sync(p);
            }
            return true;
        }
    }

    public static final class Technique
    extends Components.Mechanic {
        @Override
        public String key() {
            return "dmz technique";
        }

        @Override
        public boolean execute(Cast cast, List<SkillTarget> targets) {
            LivingEntity livingEntity = cast.caster();
            if (!(livingEntity instanceof ServerPlayer)) {
                return false;
            }
            ServerPlayer p = (ServerPlayer)livingEntity;
            int target = -1;
            for (SkillTarget st : targets) {
                if (st.entity() == null || st.entity() == p) continue;
                target = st.entity().m_19879_();
                break;
            }
            return DmzActions.useTechnique(p, this.settings.getString("technique", ""), target, this.settings.getBool("ignore-cooldown", false), this.settings.getBool("free", false));
        }
    }
}

