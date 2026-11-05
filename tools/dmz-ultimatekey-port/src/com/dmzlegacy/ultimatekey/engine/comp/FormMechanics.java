package com.dmzlegacy.ultimatekey.engine.comp;

import com.dmzlegacy.ultimatekey.dmz.Dmz;
import com.dmzlegacy.ultimatekey.engine.Cast;
import com.dmzlegacy.ultimatekey.engine.ComponentRegistry;
import com.dmzlegacy.ultimatekey.engine.Components;
import com.dmzlegacy.ultimatekey.engine.EffectComponent;
import com.dmzlegacy.ultimatekey.engine.SkillTarget;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.util.TransformationsHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class FormMechanics {
    private static final Map<UUID, Timed> TIMED = new HashMap<UUID, Timed>();

    private FormMechanics() {
    }

    public static void register() {
        ComponentRegistry.register(EffectComponent.Kind.MECHANIC, "dmz transform", Transform::new);
        ComponentRegistry.register(EffectComponent.Kind.MECHANIC, "dmz revert", Revert::new);
    }

    public static boolean transformTo(ServerPlayer p, String spec) {
        FormRef ref = FormRef.parse(spec, Dmz.stats((Entity)p).map(Dmz::race).orElse(""));
        return ref != null && FormMechanics.transform(p, ref, false);
    }

    static boolean transform(ServerPlayer p, FormRef ref, boolean requireUnlocked) {
        List<FormConfig.FormData> unlocked;
        FormConfig.FormData fd;
        StatsData d = Dmz.stats((Entity)p).orElse(null);
        if (d == null) {
            return false;
        }
        Character c = d.getCharacter();
        if (!ref.stack() && !ref.race().equals(Dmz.race(d))) {
            return false;
        }
        FormConfig.FormData formData = fd = ref.stack() ? ConfigManager.getStackForm((String)ref.group(), (String)ref.form()) : ConfigManager.getForm((String)ref.race(), (String)ref.group(), (String)ref.form());
        if (fd == null) {
            return false;
        }
        if (requireUnlocked && !ref.stack() && ((unlocked = TransformationsHelper.getUnlockedForms((StatsData)d, (String)ref.race(), (String)ref.group())) == null || unlocked.stream().noneMatch(f -> f.getName().equalsIgnoreCase(fd.getName())))) {
            return false;
        }
        float[] snapshot = d.snapshotMultiplierResources();
        if (ref.stack()) {
            c.recordPreviousStackForm();
            c.setActiveStackForm(ref.group(), fd.getName());
            if (!c.getStackFormsUsedBefore().getFormGroup(ref.group()).contains(fd.getName())) {
                c.getStackFormsUsedBefore().putForm(ref.group(), fd.getName());
            }
            if (!p.m_21023_((MobEffect)MainEffects.STACK_TRANSFORMED.get())) {
                p.m_7292_(new MobEffectInstance((MobEffect)MainEffects.STACK_TRANSFORMED.get(), -1, 0, false, false, true));
            }
        } else {
            c.recordPreviousForm();
            c.setActiveForm(ref.group(), fd.getName());
            if (!c.getFormsUsedBefore().getFormGroup(ref.group()).contains(fd.getName())) {
                c.getFormsUsedBefore().putForm(ref.group(), fd.getName());
            }
            if (!p.m_21023_((MobEffect)MainEffects.TRANSFORMED.get())) {
                p.m_7292_(new MobEffectInstance((MobEffect)MainEffects.TRANSFORMED.get(), -1, 0, false, false, true));
            }
        }
        d.restoreMultiplierGains(p, snapshot);
        FormMechanics.sound(p, (SoundEvent)MainSounds.TRANSFORM_ON.get());
        FormMechanics.sync(p);
        return true;
    }

    static void revert(ServerPlayer p, boolean stack, String to) {
        boolean toBase = "base".equals(to);
        StatsData d = Dmz.stats((Entity)p).orElse(null);
        if (d == null) {
            return;
        }
        Character c = d.getCharacter();
        if (stack) {
            FormConfig.FormData down;
            if (!c.hasActiveStackForm()) {
                return;
            }
            FormConfig.FormData formData = down = "down".equals(to) ? TransformationsHelper.getPreviousStackForm((StatsData)d) : null;
            if ("down".equals(to) && down != null) {
                c.clearPreviousStackFormRecord();
                c.setActiveStackForm(c.getActiveStackFormGroup(), down.getName());
            } else if (!toBase && !"down".equals(to) && c.isHasPreviousStackFormRecord() && c.getPreviousStackForm() != null && !c.getPreviousStackForm().isEmpty()) {
                String g = c.getPreviousStackFormGroup();
                String f = c.getPreviousStackForm();
                c.clearPreviousStackFormRecord();
                c.setActiveStackForm(g, f);
            } else {
                c.clearPreviousStackFormRecord();
                c.clearActiveStackForm((LivingEntity)p);
                p.m_21195_((MobEffect)MainEffects.STACK_TRANSFORMED.get());
            }
        } else {
            FormConfig.FormData down;
            if (!c.hasActiveForm()) {
                return;
            }
            FormConfig.FormData formData = down = "down".equals(to) ? TransformationsHelper.getPreviousForm((StatsData)d) : null;
            if ("down".equals(to) && down != null) {
                c.clearPreviousFormRecord();
                c.setActiveForm(c.getActiveFormGroup(), down.getName());
            } else if (!toBase && !"down".equals(to) && c.isHasPreviousFormRecord() && c.getPreviousForm() != null && !c.getPreviousForm().isEmpty()) {
                String g = c.getPreviousFormGroup();
                String f = c.getPreviousForm();
                c.clearPreviousFormRecord();
                c.setActiveForm(g, f);
            } else {
                c.clearPreviousFormRecord();
                TransformationsHelper.revertToBaseForm((ServerPlayer)p, (StatsData)d, false);
                p.m_21195_((MobEffect)MainEffects.TRANSFORMED.get());
            }
        }
        FormMechanics.sound(p, (SoundEvent)MainSounds.INSTA_FORM_OFF.get());
        FormMechanics.sync(p);
    }

    private static void sound(ServerPlayer p, SoundEvent s) {
        p.m_9236_().m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(), s, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void sync(ServerPlayer p) {
        p.m_6210_();
        NetworkHandler.sendToTrackingEntityAndSelf((Object)new StatsSyncS2C(p), (Entity)p);
    }

    private static boolean inForm(ServerPlayer p, FormRef ref) {
        return Dmz.stats((Entity)p).map(d -> {
            Character c = d.getCharacter();
            return ref.stack() ? ref.group().equalsIgnoreCase(c.getActiveStackFormGroup()) && ref.form().equalsIgnoreCase(c.getActiveStackForm()) : ref.group().equalsIgnoreCase(c.getActiveFormGroup()) && ref.form().equalsIgnoreCase(c.getActiveForm());
        }).orElse(false);
    }

    public static String applyForm(ServerPlayer p, String ref, double seconds, String endMode) {
        String race = Dmz.stats((Entity)p).map(Dmz::race).orElse("");
        FormRef r = FormRef.parse(ref, race);
        if (r == null || !FormMechanics.transform(p, r, false)) {
            return null;
        }
        if (seconds > 0.0 && p.m_20194_() != null) {
            TIMED.put(p.m_20148_(), new Timed(r, (long)p.m_20194_().m_129921_() + Math.round(seconds * 20.0), FormMechanics.revertMode(endMode)));
        }
        return (r.stack() ? "stack:" : "") + r.group() + "." + r.form();
    }

    public static boolean inRaceForm(ServerPlayer p) {
        return Dmz.stats((Entity)p).map(d -> d.getCharacter().hasActiveForm()).orElse(false);
    }

    public static void revertIfIn(ServerPlayer p, String applied, String endMode) {
        int dot;
        boolean stack;
        if (applied == null) {
            return;
        }
        String v = (stack = applied.startsWith("stack:")) ? applied.substring(6) : applied;
        FormRef r = new FormRef(stack, "", v.substring(0, dot = v.lastIndexOf(46)), v.substring(dot + 1));
        if (!FormMechanics.inForm(p, r)) {
            return;
        }
        TIMED.remove(p.m_20148_());
        FormMechanics.revert(p, stack, FormMechanics.revertMode(endMode));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        Player player;
        if (event.phase != TickEvent.Phase.END || !((player = event.player) instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer p = (ServerPlayer)player;
        Timed t = TIMED.get(p.m_20148_());
        if (t == null || p.m_20194_() == null || (long)p.m_20194_().m_129921_() < t.endTick()) {
            return;
        }
        TIMED.remove(p.m_20148_());
        if (FormMechanics.inForm(p, t.ref())) {
            FormMechanics.revert(p, t.ref().stack(), t.to());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer p = (ServerPlayer)player;
        Timed t = TIMED.remove(p.m_20148_());
        if (t != null && FormMechanics.inForm(p, t.ref())) {
            FormMechanics.revert(p, t.ref().stack(), t.to());
        }
    }

    static String revertMode(String option) {
        if (option.contains("down")) {
            return "down";
        }
        return "previous".equals(option) ? "previous" : "base";
    }

    record FormRef(boolean stack, String race, String group, String form) {
        static FormRef parse(String s, String targetRace) {
            String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace('|', '.');
            String owner = targetRace;
            int colon = v.indexOf(58);
            if (colon >= 0) {
                owner = v.substring(0, colon);
                v = v.substring(colon + 1);
            }
            if ("temp".equals(owner)) {
                return new FormRef(false, targetRace, "duktemp", v);
            }
            int dot = v.lastIndexOf(46);
            if (dot <= 0) {
                return null;
            }
            boolean stack = "stack".equals(owner);
            return new FormRef(stack, stack ? "" : owner, v.substring(0, dot), v.substring(dot + 1));
        }
    }

    private record Timed(FormRef ref, long endTick, String to) {
    }

    public static final class Revert
    extends Components.Mechanic {
        @Override
        public String key() {
            return "dmz revert";
        }

        @Override
        public boolean execute(Cast cast, List<SkillTarget> targets) {
            String which = this.settings.option("which", "both");
            String to = FormMechanics.revertMode(this.settings.option("to", "base"));
            boolean worked = false;
            for (SkillTarget t : targets) {
                LivingEntity livingEntity = t.entity();
                if (!(livingEntity instanceof ServerPlayer)) continue;
                ServerPlayer p = (ServerPlayer)livingEntity;
                TIMED.remove(p.m_20148_());
                if (!"race".equals(which)) {
                    FormMechanics.revert(p, true, to);
                }
                if (!"stack".equals(which)) {
                    FormMechanics.revert(p, false, to);
                }
                worked = true;
            }
            return worked;
        }
    }

    public static final class Transform
    extends Components.Mechanic {
        @Override
        public String key() {
            return "dmz transform";
        }

        @Override
        public boolean execute(Cast cast, List<SkillTarget> targets) {
            boolean worked = false;
            for (SkillTarget t : targets) {
                LivingEntity livingEntity = t.entity();
                if (!(livingEntity instanceof ServerPlayer)) continue;
                ServerPlayer p = (ServerPlayer)livingEntity;
                String race = Dmz.stats((Entity)p).map(Dmz::race).orElse("");
                FormRef ref = FormRef.parse(this.settings.getString("form", ""), race);
                if (ref == null || !FormMechanics.transform(p, ref, this.settings.getBool("require-unlocked", false))) continue;
                worked = true;
                double secs = this.attr(cast, "duration", 0.0);
                if (secs > 0.0 && p.m_20194_() != null) {
                    TIMED.put(p.m_20148_(), new Timed(ref, (long)p.m_20194_().m_129921_() + Math.round(secs * 20.0), FormMechanics.revertMode(this.settings.option("on-end", "previous"))));
                    continue;
                }
                TIMED.remove(p.m_20148_());
            }
            return worked;
        }
    }
}

