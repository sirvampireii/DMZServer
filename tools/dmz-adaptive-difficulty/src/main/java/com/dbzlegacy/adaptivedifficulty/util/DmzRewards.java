package com.dbzlegacy.adaptivedifficulty.util;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dragonminez.common.combat.clash.BeamClashManager;
import com.dragonminez.common.combat.clash.StrikeClash;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Resources;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Shared DMZ reward / combat helpers for Rival + Sparring systems.
 */
public final class DmzRewards {
    private DmzRewards() {}

    public static boolean awardTp(
            ServerPlayer player,
            float amount,
            String reason,
            boolean showMessage,
            String prefix
    ) {
        if (player == null || !(amount > 0.0f)) {
            return false;
        }
        try {
            StatsData data = DmzProgression.stats(player);
            if (data == null) {
                return false;
            }
            Resources resources = data.getResources();
            if (resources == null) {
                return false;
            }
            float grant = amount;
            try {
                double tpBonus = com.dbzlegacy.adaptivedifficulty.title.TitleEffects.tpGainBonus(player);
                if (tpBonus > 0.0) {
                    grant = (float) (grant * (1.0 + tpBonus));
                }
            } catch (Throwable ignored) {
            }
            resources.addTrainingPoints(grant);
            try {
                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
            } catch (Throwable syncErr) {
                AdaptiveDifficultyMod.LOGGER.debug(
                        "[{}] TP sync failed for {}: {}",
                        AdaptiveDifficultyMod.MOD_ID,
                        player.m_7755_().getString(),
                        syncErr.toString()
                );
            }
            if (showMessage) {
                // Boss bar — chat TP lines clutter the fight feed, titles cover the screen.
                String why = reason == null || reason.isBlank() ? "" : " (" + reason + ")";
                String system = resolveTpSystem(prefix);
                String line = system + " +" + formatWhole(grant) + " TP" + why;
                TpBossNotify.show(
                        player, line, ChatFormatting.GREEN, "lm.tp.grant.subtitle", 1_500L);
            }
            return true;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] awardTp failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return false;
        }
    }

    public static double battlePower(ServerPlayer player) {
        try {
            // Prefer sane DMZ BP; Androids / overflowed forms fall back to
            // released-stat power (same basis as Combat Rating).
            return Math.max(0.0,
                    com.dbzlegacy.adaptivedifficulty.calc.CombatRating.safeBattlePower(player));
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    public static double releasedBattlePower(ServerPlayer player) {
        try {
            // When DMZ BP is sane, apply power-release like Rival instinct.
            // When we already fell back to released-stat power, do not double-apply.
            StatsData data = DmzProgression.stats(player);
            if (data != null
                    && com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat.scaled(data)) {
                return com.dbzlegacy.adaptivedifficulty.calc.CombatRating.releasedStatPower(player);
            }
            if (data != null) {
                double exact = data.getBattlePowerExact();
                float raw = data.getBattlePower();
                boolean dmzSane = (Double.isFinite(exact) && exact > 0.0
                        && exact <= com.dbzlegacy.adaptivedifficulty.calc.CombatRating.SAFE_DMZ_BATTLE_POWER_MAX)
                        || (Double.isFinite(raw) && raw > 0.0f
                        && raw <= com.dbzlegacy.adaptivedifficulty.calc.CombatRating.SAFE_DMZ_BATTLE_POWER_MAX);
                if (dmzSane) {
                    double bp = Double.isFinite(exact) && exact > 0.0
                            && exact <= com.dbzlegacy.adaptivedifficulty.calc.CombatRating.SAFE_DMZ_BATTLE_POWER_MAX
                            ? exact : raw;
                    double release = powerReleasePercent(player) / 100.0;
                    if (!(release > 0.0) || !Double.isFinite(release)) {
                        release = 1.0;
                    }
                    double out = bp * Math.max(0.5, Math.min(2.0, release));
                    return Double.isFinite(out) && out > 0.0 ? out : bp;
                }
            }
            return com.dbzlegacy.adaptivedifficulty.calc.CombatRating.releasedStatPower(player);
        } catch (Throwable ignored) {
            return battlePower(player);
        }
    }

    public static double powerReleasePercent(ServerPlayer player) {
        try {
            StatsData data = DmzProgression.stats(player);
            if (data == null) {
                return 100.0;
            }
            Resources resources = data.getResources();
            if (resources == null) {
                return 100.0;
            }
            int release = resources.getPowerRelease();
            if (release <= 0) {
                release = resources.getRelease();
            }
            return release > 0 ? release : 100.0;
        } catch (Throwable ignored) {
            return 100.0;
        }
    }

    public static boolean isKiDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        try {
            return MainDamageTypes.isKiblastDamage(source);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Beam clash only (DMZ's {@code BeamClashManager}); strike clashes are {@link #isStrikeClashing}. */
    public static boolean isClashing(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            return BeamClashManager.isClashing(uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** DMZ 2.2.1 strike (melee) clash: up to 12s, both fighters locked, no damage. */
    public static boolean isStrikeClashing(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            return StrikeClash.isClashing(uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void msg(ServerPlayer player, String text) {
        LmChat.send(player, text);
    }

    /** Legacy {@code §6[Tag]} prefix or short system name → canonical TP tag label. */
    static String resolveTpSystem(String systemOrPrefix) {
        if (systemOrPrefix == null || systemOrPrefix.isBlank()) {
            return "TP";
        }
        String s = systemOrPrefix.trim();
        if (s.startsWith("§6[") && s.contains("]")) {
            int open = s.indexOf('[');
            int close = s.indexOf(']', open + 1);
            if (close > open) {
                s = s.substring(open + 1, close).trim();
            }
        }
        return LmChat.systemName(s);
    }

    public static String formatWhole(double value) {
        long n = Math.max(0L, Math.round(value));
        String raw = Long.toString(n);
        StringBuilder out = new StringBuilder();
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                out.append(',');
            }
            out.append(raw.charAt(i));
        }
        return out.toString();
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
