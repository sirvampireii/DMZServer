package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.combat.CombatProgression;
import com.dbzlegacy.adaptivedifficulty.progression.dummy.DummyProgression;
import com.dbzlegacy.adaptivedifficulty.progression.end.EndProgression;
import com.dbzlegacy.adaptivedifficulty.progression.race.AndroidConversion;
import com.dbzlegacy.adaptivedifficulty.progression.race.RaceLock;
import com.dbzlegacy.adaptivedifficulty.progression.race.SpiritualistKiControl;
import com.dbzlegacy.adaptivedifficulty.progression.race.YardratProgression;
import com.dbzlegacy.adaptivedifficulty.progression.shop.ShopProgression;
import com.dbzlegacy.adaptivedifficulty.progression.skills.PotentialProgression;
import com.dbzlegacy.adaptivedifficulty.progression.tp.BioAndroidAbsorb;
import com.dbzlegacy.adaptivedifficulty.progression.tp.DeathTpPenalty;
import com.dbzlegacy.adaptivedifficulty.progression.tp.GlobalTpBoost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Facade for natural-progression CNPC ports — skills/tp/race (sibling agents) plus
 * combat / End / shadow-dummy / shop modules from this agent.
 */
public final class ProgressionSystem {
    private ProgressionSystem() {}

    public static void onLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            DeathTpPenalty.onLogin(player);
        } catch (Throwable ignored) {
        }
        if (ProgressionConfig.masterEnabled()) {
            try {
                GlobalTpBoost.onLogin(player);
            } catch (Throwable ignored) {
            }
        }
        try {
            ShopProgression.onLogin(player);
        } catch (Throwable ignored) {
        }
        try {
            LmTips.onLogin(player);
        } catch (Throwable ignored) {
        }
        try {
            PersonalLevelCapMirror.publish(player);
        } catch (Throwable ignored) {
        }
        try {
            com.dragonminez.common.stats.StatsData data =
                    com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
            DmzResourcePoolClamp.clampAndSync(player, data);
            schedulePostLoginPoolClamp(player);
        } catch (Throwable ignored) {
        }
    }

    /** Mohist/DMZ attach order — reclamp after stats finish hydrating (fixes bar overflow). */
    private static void schedulePostLoginPoolClamp(ServerPlayer player) {
        if (player == null) {
            return;
        }
        net.minecraft.server.MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        java.util.UUID id = player.m_20148_();
        Runnable pulse = () -> {
            ServerPlayer p = server.m_6846_().m_11259_(id);
            if (p == null || !p.m_6084_()) {
                return;
            }
            try {
                com.dragonminez.common.stats.StatsData data =
                        com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(p);
                DmzResourcePoolClamp.clampAndSync(p, data);
            } catch (Throwable ignored) {
            }
        };
        server.execute(pulse);
        for (int delay : new int[] {1, 5, 20, 60}) {
            try {
                server.m_6937_(new net.minecraft.server.TickTask(server.m_129921_() + delay, pulse));
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            CombatProgression.onLogout(player);
        } catch (Throwable ignored) {
        }
        try {
            DummyProgression.onLogout(player);
        } catch (Throwable ignored) {
        }
        try {
            ShopProgression.onLogout(player);
        } catch (Throwable ignored) {
        }
        try {
            StaminaRegenGuard.onLogout(player);
        } catch (Throwable ignored) {
        }
        try {
            PersonalLevelCapMirror.forget(player);
        } catch (Throwable ignored) {
        }
        ProgressionData.clearPlayer(player.m_20148_());
    }

    public static void pulse(MinecraftServer server, int tick) {
        if (server == null) {
            return;
        }
        try {
            DeathTpPenalty.pulse(server, tick);
            com.dbzlegacy.adaptivedifficulty.util.TpBossNotify.pulse(server, tick);
        } catch (Throwable ignored) {
        }
        if (ProgressionConfig.masterEnabled()) {
            long now = System.currentTimeMillis();
            try {
                com.dbzlegacy.adaptivedifficulty.progression.skills.LivingWorldNearbyMeditation
                        .pulse(server, tick);
            } catch (Throwable ignored) {
            }
            try {
                GlobalTpBoost.pulse(server, tick);
            } catch (Throwable ignored) {
            }
            try {
                EndProgression.pulse(server, tick);
            } catch (Throwable ignored) {
            }
            try {
                DummyProgression.pulse(server, tick);
            } catch (Throwable ignored) {
            }
            try {
                CombatProgression.pulse(server, tick);
            } catch (Throwable ignored) {
            }
            for (ServerPlayer player : server.m_6846_().m_11314_()) {
                if (player == null) {
                    continue;
                }
                try {
                    BioAndroidAbsorb.pulse(player, now);
                } catch (Throwable ignored) {
                }
                try {
                    RaceLock.pulse(player, now);
                } catch (Throwable ignored) {
                }
                try {
                    YardratProgression.pulse(player, now);
                } catch (Throwable ignored) {
                }
                try {
                    SpiritualistKiControl.pulse(player, now);
                } catch (Throwable ignored) {
                }
                if (tick % 20 == 0) {
                    try {
                        com.dbzlegacy.adaptivedifficulty.progression.bridge.OverhaulPrestigeResourceScale
                                .pulse(player);
                    } catch (Throwable ignored) {
                    }
                    try {
                        com.dragonminez.common.stats.StatsData data =
                                com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player);
                        DmzResourcePoolClamp.clampAndSync(player, data);
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    StaminaRegenGuard.pulse(player);
                } catch (Throwable ignored) {
                }
                if (tick % 200 == 0) {
                    try {
                        PersonalLevelCapMirror.publish(player);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        try {
            ShopProgression.pulse(server, tick);
        } catch (Throwable ignored) {
        }
        try {
            LmTips.pulse(server, tick);
        } catch (Throwable ignored) {
        }
    }

    /** Broad hurt fan-out (End mitigation, ki weapons, DoT, shadow protect, potential). */
    public static void onHurt(LivingHurtEvent event) {
        if (event == null || !ProgressionConfig.masterEnabled()) {
            return;
        }
        try {
            EndProgression.onHurt(event);
        } catch (Throwable ignored) {
        }
        try {
            DummyProgression.onHurt(event);
        } catch (Throwable ignored) {
        }
        LivingEntity victim = event.getEntity();
        var source = event.getSource();
        var causing = source == null ? null : source.m_7639_();
        if (victim instanceof ServerPlayer victimPlayer) {
            try {
                CombatProgression.onPlayerHurt(event, victimPlayer);
            } catch (Throwable ignored) {
            }
            if (causing instanceof ServerPlayer attacker) {
                try {
                    PotentialProgression.onPlayerHurt(victimPlayer, attacker, source);
                } catch (Throwable ignored) {
                }
            }
        }
        if (causing instanceof ServerPlayer attacker && victim != null) {
            try {
                CombatProgression.onHurt(event, attacker, victim);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Legacy PvP-only entry kept for existing DifficultyEvents call sites. */
    public static void onPlayerHurt(ServerPlayer victim, ServerPlayer attacker, DamageSource source) {
        if (!ProgressionConfig.masterEnabled()) {
            return;
        }
        try {
            PotentialProgression.onPlayerHurt(victim, attacker, source);
        } catch (Throwable ignored) {
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event == null || !ProgressionConfig.masterEnabled()) {
            return;
        }
        var killer = event.getSource() == null ? null : event.getSource().m_7639_();
        if (killer instanceof ServerPlayer player) {
            try {
                EndProgression.onKill(event, player);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (event == null || !ProgressionConfig.masterEnabled()) {
            return;
        }
        try {
            DummyProgression.onJoin(event);
        } catch (Throwable ignored) {
        }
    }

    /* ========================= Command API ========================= */

    public static String boostStartEncoded(ServerPlayer actor, int encoded, String purchaser) {
        return GlobalTpBoost.startBoost(actor, encoded, purchaser);
    }

    public static String boostStart(ServerPlayer actor, double mult, int minutes, String purchaser) {
        return GlobalTpBoost.startBoostMinutes(actor, mult, minutes, purchaser);
    }

    public static String boostEnd() {
        return GlobalTpBoost.endBoost(true);
    }

    public static String androidConvert(ServerPlayer target) {
        return AndroidConversion.convert(target);
    }

    public static String androidRemove(ServerPlayer actor, ServerPlayer target) {
        return AndroidConversion.remove(actor, target);
    }

    public static String statusSummary() {
        return ProgressionConfig.statusSummary()
                + "\n" + GlobalTpBoost.statusLine();
    }

    /** Display label for a {@link #setFlag} key ("potential" -> the setting's config editor label). */
    public static String flagLabel(String key) {
        String field = switch (key == null ? "" : key.toLowerCase(java.util.Locale.ROOT)) {
            case "master", "progression", "enableprogression" -> "enableProgression";
            case "potential", "enablepotential" -> "enablePotential";
            case "boost", "globaltpboost", "enableglobaltpboost" -> "enableGlobalTpBoost";
            case "bio", "bioandroid", "enablebioandroid" -> "enableBioAndroid";
            case "racelock", "lock", "enableracelock" -> "enableRaceLock";
            case "yardrat", "enableyardrat" -> "enableYardrat";
            case "spiritualist", "spirit", "enablespiritualistki" -> "enableSpiritualistKi";
            case "android", "enableandroidconversion" -> "enableAndroidConversion";
            case "kiweapons", "enablekiweapons" -> "enableKiWeapons";
            case "piercing", "enablepiercingbonus" -> "enablePiercingBonus";
            case "dot", "enabledotextradamage" -> "enableDotExtraDamage";
            case "apothic", "enableapothicelemental" -> "enableApothicElemental";
            case "end", "endstrength", "enableenddimensionstrength" -> "enableEndDimensionStrength";
            case "endportal", "endportalguard", "enableendportalguard" -> "enableEndPortalGuard";
            case "endnatural", "enableendnaturaldragonspawn" -> "enableEndNaturalDragonSpawn";
            case "endsummon", "enableendplayerdragonsummon", "enddragonsummon" -> "enableEndPlayerDragonSummon";
            case "shadow", "shadowdummy", "enableshadowdummylimiter" -> "enableShadowDummyLimiter";
            case "statchecker", "playerstatchecker", "enableplayerstatchecker" -> "enablePlayerStatChecker";
            case "skills", "skillunlock", "enableskillunlockservice" -> "enableSkillUnlockService";
            case "prestige", "enableprestigesystem" -> "enablePrestigeSystem";
            case "overhaulprestige", "overhaului", "enableoverhaulprestigeintegration" ->
                    "enableOverhaulPrestigeIntegration";
            default -> "";
        };
        return field.isEmpty()
                ? "That setting"
                : com.dbzlegacy.adaptivedifficulty.config.editor.ConfigFieldHelp.label(field);
    }

    public static boolean setFlag(String key, boolean on) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (cfg == null || key == null) {
            return false;
        }
        switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "master", "progression", "enableprogression" -> cfg.enableProgression = on;
            case "potential", "enablepotential" -> cfg.enablePotential = on;
            case "boost", "globaltpboost", "enableglobaltpboost" -> cfg.enableGlobalTpBoost = on;
            case "bio", "bioandroid", "enablebioandroid" -> cfg.enableBioAndroid = on;
            case "racelock", "lock", "enableracelock" -> cfg.enableRaceLock = false;
            case "yardrat", "enableyardrat" -> cfg.enableYardrat = on;
            case "spiritualist", "spirit", "enablespiritualistki" -> cfg.enableSpiritualistKi = on;
            case "android", "enableandroidconversion" -> cfg.enableAndroidConversion = on;
            case "kiweapons", "enablekiweapons" -> cfg.enableKiWeapons = on;
            case "piercing", "enablepiercingbonus" -> cfg.enablePiercingBonus = on;
            case "dot", "enabledotextradamage" -> cfg.enableDotExtraDamage = on;
            case "apothic", "enableapothicelemental" -> cfg.enableApothicElemental = on;
            case "end", "endstrength", "enableenddimensionstrength" -> cfg.enableEndDimensionStrength = on;
            case "endportal", "endportalguard", "enableendportalguard" -> cfg.enableEndPortalGuard = on;
            case "endnatural", "enableendnaturaldragonspawn" -> {
                // Natural spawn is retired — always force off.
                cfg.enableEndNaturalDragonSpawn = false;
            }
            case "endsummon", "enableendplayerdragonsummon", "enddragonsummon" ->
                    cfg.enableEndPlayerDragonSummon = on;
            case "shadow", "shadowdummy", "enableshadowdummylimiter" -> cfg.enableShadowDummyLimiter = on;
            case "statchecker", "playerstatchecker", "enableplayerstatchecker" -> cfg.enablePlayerStatChecker = on;
            case "skills", "skillunlock", "enableskillunlockservice" -> cfg.enableSkillUnlockService = on;
            case "prestige", "enableprestigesystem" -> cfg.enablePrestigeSystem = on;
            case "overhaulprestige", "overhaului", "enableoverhaulprestigeintegration" ->
                    cfg.enableOverhaulPrestigeIntegration = on;
            default -> {
                return false;
            }
        }
        DifficultyConfig.save();
        return true;
    }
}
