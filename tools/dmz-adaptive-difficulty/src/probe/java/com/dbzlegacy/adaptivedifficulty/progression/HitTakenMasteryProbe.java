package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.objectives.SparObjective;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.server.events.players.StatsEvents;
import com.dragonminez.server.util.PotionEffectHelper;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Test-only probe (4.7.40, ladder 4.7.42; ships only in the testprobe jar, {@code -Dlm.probe.hitTakenMastery}):
 * real {@code LivingHurtEvent}s through DMZ's own {@code StatsEvents.onEntityHit}, the per-hit form mastery
 * grant. A fake player in a form takes and deals one small hit at a time against another player, a
 * master / Popo spar shadow, a summoned player shadow, a quest SPAR partner, a zombie, and two Ultra
 * God Chamber / Whis trial foes (DMZ saga Saibaman and Vegeta, tagged {@code dmzultra_chamber_enemy} as Ultra
 * spawns them). "DMZ formula" is a direct gainMastery with the same inputs (the pre-4.7.40 rate). Hits
 * taken: player 0.25x; zombie and chamber / trial foes 0.0625x (a quarter of PvP); sparring NPCs 0.03125x.
 * Hits dealt: all unchanged. Repeated with a Mastery Gain effect: the boost still multiplies on top. Logs "hit taken mastery probe check ...: OK|WRONG" and a final OK /
 * FAILED line.
 */
public final class HitTakenMasteryProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d70-726f-6265-2d6e-707373706172");
    private static final UUID OPPONENT_ID = UUID.fromString("6c6d2d70-726f-6265-2d6e-707373706f70");
    private static final float HIT = 1.0f;
    private static final double EPS = 1.0E-9;
    private static int failed;

    private HitTakenMasteryProbe() {}

    public static void run(MinecraftServer server) {
        failed = 0;
        try {
            ServerLevel level = server.m_129783_();
            FakePlayer fp = FakePlayerFactory.get(level, new GameProfile(PROBE_ID, "lm_npcspar_probe"));
            FakePlayer opponent = FakePlayerFactory.get(level, new GameProfile(OPPONENT_ID, "lm_npcspar_rival"));
            StatsData data = DmzProgression.stats(fp);
            check("DMZ stats attached to the probe player", data != null);
            if (data == null) {
                return;
            }
            Character character = data.getCharacter();
            data.getStatus().setHasCreatedCharacter(true);
            data.getResources().setPowerRelease(100);
            String[] form = pickForm(character.getRaceName());
            check("a form with per-hit mastery exists for race " + character.getRaceName(), form != null);
            if (form == null) {
                return;
            }
            character.setActiveForm(form[0], form[1]);
            check("probe player is in form " + form[0] + "/" + form[1], character.getActiveFormData() != null);
            fp.m_21219_();

            LivingEntity popoSpar = shadow(level, fp, "dmz_popo_spar");
            LivingEntity playerShadow = shadow(level, fp, "dmz_player_shadow");
            LivingEntity questPartner = EntityType.f_20501_.m_20615_(level);
            String sparQuest = tagSparPartner(questPartner, fp);
            check("quest SPAR partner recognised by DMZ (quest " + sparQuest + ")",
                    sparQuest != null && QuestService.isSparPartner(questPartner));
            LivingEntity zombie = EntityType.f_20501_.m_20615_(level);
            LivingEntity chamberFoe = chamberFoe(level, "saga_saibaman1");
            LivingEntity trialFoe = chamberFoe(level, "saga_vegeta");
            check("God Chamber / Whis trial foes created (dragonminez:saga_saibaman1, saga_vegeta)",
                    chamberFoe != null && trialFoe != null);
            LivingEntity[] foes = {zombie, chamberFoe, trialFoe};

            pass(level, fp, opponent, popoSpar, playerShadow, questPartner, foes, form, "no boost");
            fp.m_7292_(new MobEffectInstance(MainEffects.MASTERY_GAIN.get(), 20 * 60, 1));
            double boost = PotionEffectHelper.applyMasteryGainMultiplier(fp, 1.0);
            check("Mastery Gain effect active (multiplier " + boost + ")", boost > 1.0 + EPS);
            pass(level, fp, opponent, popoSpar, playerShadow, questPartner, foes, form, "Mastery Gain x" + boost);
            fp.m_21219_();

            check("nothing left marked after the hits", HitTakenMastery.scale(fp, 1.0) == 1.0);
            character.getFormMasteries().setMastery(form[0], form[1], 0.0, 1000.0);
            character.clearActiveForm();
        } catch (Throwable t) {
            failed++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] hit taken mastery probe threw: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] hit taken mastery probe {}", AdaptiveDifficultyMod.MOD_ID,
                failed == 0 ? "OK" : "FAILED: " + failed);
    }

    private static void pass(ServerLevel level, FakePlayer fp, FakePlayer opponent, LivingEntity popoSpar,
            LivingEntity playerShadow, LivingEntity questPartner, LivingEntity[] foes, String[] form, String label) {
        LivingEntity zombie = foes[0];
        Character character = DmzProgression.character(fp);
        FormConfig.FormData data = character.getActiveFormData();

        // DMZ's own formula with no LM factor: what a PvP hit paid before 4.7.40.
        double expectTaken = direct(fp, character, form, data.getMasteryPerHitReceived());
        double expectDealt = direct(fp, character, form, data.getMasteryPerHitDealt());

        double pvpTaken = taken(fp, form, level.m_269111_().m_269075_(opponent));
        double popoTaken = taken(fp, form, level.m_269111_().m_269333_(popoSpar));
        double shadowTaken = taken(fp, form, level.m_269111_().m_269333_(playerShadow));
        double questTaken = taken(fp, form, level.m_269111_().m_269333_(questPartner));
        double zombieTaken = taken(fp, form, level.m_269111_().m_269333_(zombie));
        log(label + ", per hit taken", expectTaken, pvpTaken, popoTaken, shadowTaken, questTaken, zombieTaken);
        double chamberTaken = taken(fp, form, level.m_269111_().m_269333_(foes[1]));
        double trialTaken = taken(fp, form, level.m_269111_().m_269333_(foes[2]));
        AdaptiveDifficultyMod.LOGGER.info("[{}] hit taken mastery probe {}, per hit taken: chamber foe (saibaman) {}, "
                + "trial foe (vegeta) {}", AdaptiveDifficultyMod.MOD_ID, label, chamberTaken, trialTaken);
        check(label + ": hit taken from a player (PvP) = 0.25 x DMZ formula",
                expectTaken > 0 && same(pvpTaken, expectTaken * 0.25));
        check(label + ": hit taken from a zombie (non-PvP) = 0.0625 x DMZ formula (a quarter of PvP)",
                same(zombieTaken, expectTaken * 0.0625) && same(zombieTaken, pvpTaken * 0.25));
        check(label + ": hit taken from a God Chamber wave foe = 0.0625 x DMZ formula", same(chamberTaken, expectTaken * 0.0625));
        check(label + ": hit taken from a Whis trial foe = 0.0625 x DMZ formula", same(trialTaken, expectTaken * 0.0625));
        check(label + ": hit taken from the Popo spar shadow = 0.03125 x DMZ formula (half the non-PvP figure)",
                same(popoTaken, expectTaken * 0.03125) && same(popoTaken, zombieTaken * 0.5));
        check(label + ": hit taken from a summoned player shadow = 0.03125 x DMZ formula", same(shadowTaken, expectTaken * 0.03125));
        check(label + ": hit taken from a quest SPAR partner = 0.03125 x DMZ formula", same(questTaken, expectTaken * 0.03125));

        double pvpDealt = dealt(fp, form, opponent);
        double popoDealt = dealt(fp, form, popoSpar);
        double shadowDealt = dealt(fp, form, playerShadow);
        double questDealt = dealt(fp, form, questPartner);
        double zombieDealt = dealt(fp, form, zombie);
        double chamberDealt = dealt(fp, form, foes[1]);
        double trialDealt = dealt(fp, form, foes[2]);
        log(label + ", per hit dealt", expectDealt, pvpDealt, popoDealt, shadowDealt, questDealt, zombieDealt);
        check(label + ": hits dealt unchanged (player, zombie, chamber and trial foes, Popo spar, player shadow, "
                + "quest spar = DMZ formula)", expectDealt > 0 && same(pvpDealt, expectDealt) && same(zombieDealt, expectDealt)
                && same(chamberDealt, expectDealt) && same(trialDealt, expectDealt)
                && same(popoDealt, expectDealt) && same(shadowDealt, expectDealt) && same(questDealt, expectDealt));
    }

    /** A DMZ saga foe as Ultra's God Chamber waves spawn one (same entity id, same tag); null if unknown. */
    private static LivingEntity chamberFoe(ServerLevel level, String id) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragonminez", id));
        if (type == null || !(type.m_20615_(level) instanceof LivingEntity foe)) {
            return null;
        }
        foe.m_20049_("dmzultra_chamber_enemy");
        return foe;
    }

    /** One {@code gainMastery} with DMZ's inputs for a small hit (damage scale 1), outside any hit event. */
    private static double direct(FakePlayer fp, Character character, String[] form, double perHit) {
        reset(character, form);
        double bonus = 1.0 + com.dragonminez.server.util.GravityLogic.getBonusGravity(fp)
                * ConfigManager.getServerConfig().getGravity().getMasteryBonusPerGravity();
        character.gainMastery(form[0], form[1], PotionEffectHelper.applyMasteryGainMultiplier(fp, perHit * bonus));
        return mastery(character, form);
    }

    private static double taken(FakePlayer fp, String[] form, DamageSource source) {
        Character character = DmzProgression.character(fp);
        reset(character, form);
        StatsEvents.onEntityHit(new LivingHurtEvent(fp, source, HIT));
        return mastery(character, form);
    }

    private static double dealt(FakePlayer fp, String[] form, LivingEntity target) {
        Character character = DmzProgression.character(fp);
        reset(character, form);
        StatsEvents.onEntityHit(new LivingHurtEvent(target, target.m_9236_().m_269111_().m_269075_(fp), HIT));
        return mastery(character, form);
    }

    private static void reset(Character character, String[] form) {
        character.getFormMasteries().setMastery(form[0], form[1], 0.0, 1000.0);
    }

    private static double mastery(Character character, String[] form) {
        return character.getFormMasteries().getMastery(form[0], form[1]);
    }

    private static String[] pickForm(String race) {
        Map<String, FormConfig> groups = ConfigManager.getAllFormsForRace(race);
        if (groups == null) {
            return null;
        }
        for (Map.Entry<String, FormConfig> group : groups.entrySet()) {
            if (group.getValue() == null || group.getValue().getForms() == null) {
                continue;
            }
            for (Map.Entry<String, FormConfig.FormData> f : group.getValue().getForms().entrySet()) {
                FormConfig.FormData d = f.getValue();
                if (d != null && d.getMasteryPerHitReceived() != null && d.getMasteryPerHitReceived() > 0
                        && d.getMasteryPerHitDealt() != null && d.getMasteryPerHitDealt() > 0
                        && d.getMaxMastery() != null && d.getMaxMastery() >= 10.0) {
                    return new String[] {group.getKey(), f.getKey()};
                }
            }
        }
        return null;
    }

    /** A DMZ shadow dummy (looked up by id; the probe classpath has no GeckoLib) tagged like DMZ tags it. */
    private static LivingEntity shadow(ServerLevel level, FakePlayer owner, String tag) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragonminez", "shadow_dummy"));
        LivingEntity dummy = (LivingEntity) type.m_20615_(level);
        CompoundTag nbt = PersistentDataAccess.get(dummy);
        nbt.m_128359_("dmz_quest_owner", owner.m_20149_());
        nbt.m_128379_(tag, true);
        check("shadow dummy " + tag + " is a sparring NPC", HitTakenMastery.isSparNpc(dummy));
        return dummy;
    }

    /** Tags {@code entity} as a partner for the first quest whose objective is a SPAR; the quest key, or null. */
    private static String tagSparPartner(LivingEntity entity, Player owner) {
        for (Map.Entry<String, Quest> e : QuestRegistry.getAllQuests().entrySet()) {
            Quest quest = e.getValue();
            if (quest == null || quest.getObjectives() == null) {
                continue;
            }
            for (int i = 0; i < quest.getObjectives().size(); i++) {
                if (quest.getObjectives().get(i) instanceof SparObjective) {
                    CompoundTag nbt = PersistentDataAccess.get(entity);
                    nbt.m_128359_("dmz_quest_key", e.getKey());
                    nbt.m_128405_("dmz_quest_objective_index", i);
                    nbt.m_128359_("dmz_quest_owner", owner.m_20149_());
                    return e.getKey();
                }
            }
        }
        return null;
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) <= EPS + 1.0E-9 * Math.max(Math.abs(a), Math.abs(b));
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] hit taken mastery probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }

    private static void log(String step, double formula, double pvp, double popo, double shadow, double quest, double zombie) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] hit taken mastery probe {}: DMZ formula {}, player {}, popo spar {}, "
                        + "player shadow {}, quest spar {}, zombie {}", AdaptiveDifficultyMod.MOD_ID, step, formula, pvp, popo,
                shadow, quest, zombie);
    }
}
