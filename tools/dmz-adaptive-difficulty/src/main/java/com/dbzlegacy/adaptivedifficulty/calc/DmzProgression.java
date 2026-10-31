package com.dbzlegacy.adaptivedifficulty.calc;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.skills.Skills;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Reads DMZ progression for V3 unlocks + combat rating + class / race identity.
 */
public final class DmzProgression {
    /**
     * Last DMZ level observed while the player was in base form.
     * Tier buy costs / unlock gates must not follow form-inflated live levels.
     */
    private static final Map<UUID, Integer> BASE_FORM_LEVEL = new ConcurrentHashMap<>();

    private DmzProgression() {}

    public static StatsData stats(Player player) {
        if (player == null) {
            return null;
        }
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Character character(Player player) {
        StatsData data = stats(player);
        if (data == null) {
            return null;
        }
        try {
            return data.getCharacter();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** DMZ race id (e.g. {@code saiyan}, {@code human}). Empty when unavailable. */
    public static String race(Player player) {
        Character ch = character(player);
        if (ch == null) {
            return "";
        }
        try {
            String race = ch.getRace();
            if (race == null || race.isBlank()) {
                race = ch.getRaceName();
            }
            return race == null ? "" : race.trim().toLowerCase();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * DMZ fighting class id (e.g. {@code warrior}, {@code spiritualist}, {@code tank}).
     * Empty when unavailable.
     */
    public static String fightingClass(Player player) {
        Character ch = character(player);
        if (ch == null) {
            return "";
        }
        try {
            String cls = ch.getCharacterClass();
            return cls == null ? "" : cls.trim().toLowerCase();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Stable active form identity for future custom races that skip FormChangeEvent
     * or keep multipliers at 1 while swapping form strings.
     * Empty when fully base. Format: {@code race|formGroup|form|stackGroup|stackForm}.
     */
    public static String activeFormKey(Player player) {
        Character ch = character(player);
        if (ch == null) {
            return "";
        }
        try {
            boolean form = false;
            boolean stack = false;
            try {
                form = ch.hasActiveForm();
            } catch (Throwable ignored) {
            }
            try {
                stack = ch.hasActiveStackForm();
            } catch (Throwable ignored) {
            }
            if (!form && !stack) {
                return "";
            }
            String race = race(player);
            String fg = safeStr(ch.getActiveFormGroup());
            String f = safeStr(ch.getActiveForm());
            String sg = "";
            String s = "";
            try {
                sg = safeStr(ch.getActiveStackFormGroup());
                s = safeStr(ch.getActiveStackForm());
            } catch (Throwable ignored) {
            }
            return race + "|" + fg + "|" + f + "|" + sg + "|" + s;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeStr(String v) {
        return v == null ? "" : v.trim().toLowerCase();
    }

    /**
     * Live DMZ {@code getLevel()} — a <b>stat-progress</b> estimate, not a stored
     * character counter. Clamp to the configured DMZ/AD max so costs / GUI never
     * show values above the server level ceiling (e.g. 100k players reading as
     * ~987k when maxValue / max-stats mode disagrees).
     * <p>
     * When DMZ {@code getLevel()} returns placeholder 1 (config race / maxValue not
     * ready) but base stats or battle power clearly show progression, recompute
     * level from stats so high-CR form players are not stuck at 1 in the Buy GUI.
     */
    public static int dmzLevel(Player player) {
        StatsData data = stats(player);
        if (data == null) {
            return 1;
        }
        try {
            int raw;
            try {
                raw = data.getLevel();
            } catch (Throwable t) {
                raw = 0;
            }
            int clamped = clampDmzLevel(Math.max(0, raw), data, player);
            if (clamped > 1) {
                return clamped;
            }
            int recomputed = recomputeLevelFromStats(player, data);
            if (recomputed > 1) {
                return recomputed;
            }
            return Math.max(1, clamped);
        } catch (Throwable ignored) {
            return 1;
        }
    }

    /**
     * Recompute DMZ level from base stat totals when {@link StatsData#getLevel()}
     * is stuck at placeholder 1 (often while form battle power / CR is already high).
     */
    private static int recomputeLevelFromStats(Player player, StatsData data) {
        if (data == null) {
            return 1;
        }
        try {
            int maxValue = configuredMaxDmzLevel(data, player);
            if (maxValue <= 1) {
                return 1;
            }
            var st = data.getStats();
            if (st == null) {
                return 1;
            }
            int total = Math.max(0, st.getTotalStats());
            if (total <= 6) {
                // Fresh / empty sheet — only trust BP as a last resort signal.
                double bp = 0.0;
                try {
                    bp = data.getBattlePowerExact();
                    if (!(bp > 0.0)) {
                        bp = data.getBattlePower();
                    }
                } catch (Throwable ignored) {
                }
                if (bp < 25_000.0) {
                    return 1;
                }
            }
            int initial = 0;
            try {
                Object v = data.getClass().getMethod("getInitialTotalStats").invoke(data);
                if (v instanceof Number n) {
                    initial = Math.max(0, n.intValue());
                }
            } catch (Throwable ignored) {
            }
            long maxTotal;
            try {
                maxTotal = Math.max(1L, data.getConfiguredMaxTotalStats());
            } catch (Throwable t) {
                maxTotal = Math.max(1L, (long) maxValue * 6L);
            }
            // If DMZ max-total is also broken/tiny while stats are huge, estimate.
            if (maxTotal < Math.max(initial + 1L, (long) total)) {
                maxTotal = Math.max(maxTotal, Math.max((long) maxValue * 6L, (long) total));
            }
            double denom = Math.max(1.0, (double) maxTotal - (double) initial);
            double progress = (Math.max(initial, total) - (double) initial) / denom;
            if (progress < 0.0) {
                progress = 0.0;
            } else if (progress > 1.0) {
                progress = 1.0;
            }
            int level = 1 + (int) Math.floor(progress * (maxValue - 1));
            return clampDmzLevel(level, data, player);
        } catch (Throwable ignored) {
            return 1;
        }
    }

    /**
     * Hard ceiling for AD level reads: min(DMZ gameplay maxValue, AD referenceMaxLevel).
     * Always ≥ 1.
     */
    public static int configuredMaxDmzLevel(StatsData data) {
        return configuredMaxDmzLevel(data, null);
    }

    /** Ceiling for a player: Overhaul's level cap (flat since dmzrevamp 2.2.4), within 150k. */
    public static int configuredMaxDmzLevel(Player player) {
        return configuredMaxDmzLevel(stats(player), player);
    }

    public static int configuredMaxDmzLevel(StatsData data, Player player) {
        int dmzMax = 0;
        try {
            if (data != null) {
                dmzMax = data.getConfiguredMaxValue();
            }
        } catch (Throwable ignored) {
        }
        if (dmzMax <= 1) {
            try {
                var gameplay = com.dragonminez.common.config.ConfigManager.getServerConfig().getGameplay();
                if (gameplay != null && gameplay.getMaxValue() != null) {
                    dmzMax = gameplay.getMaxValue();
                }
            } catch (Throwable ignored) {
            }
        }
        long adMax = 100_000L;
        try {
            adMax = Math.max(1L, DifficultyConfig.get().referenceMaxLevel);
        } catch (Throwable ignored) {
        }
        int ceiling;
        if (dmzMax > 1 && adMax > 0L) {
            ceiling = (int) Math.min(dmzMax, Math.min(Integer.MAX_VALUE, adMax));
        } else if (dmzMax > 1) {
            ceiling = dmzMax;
        } else {
            ceiling = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, adMax));
        }
        // The level cap is Overhaul's. Never clamp below it, and never above ABSOLUTE_LEVEL_CAP
        // just because live referenceMaxLevel is stale (production JSON was still 100000 after
        // Overhaul/DMZ moved to 150k).
        try {
            int personal = com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath.levelCap(data);
            int absolute = (int) Math.min(Integer.MAX_VALUE, Math.max(
                    Math.max(1L, adMax),
                    com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                            .ABSOLUTE_LEVEL_CAP));
            ceiling = Math.min(absolute, Math.max(ceiling, personal));
        } catch (Throwable ignored) {
        }
        return Math.max(1, ceiling);
    }

    public static int clampDmzLevel(int raw) {
        return clampDmzLevel(raw, null, null);
    }

    public static int clampDmzLevel(int raw, StatsData data) {
        return clampDmzLevel(raw, data, null);
    }

    public static int clampDmzLevel(int raw, StatsData data, Player player) {
        int max = configuredMaxDmzLevel(data, player);
        if (raw < 1) {
            return 1;
        }
        return Math.min(raw, max);
    }

    /**
     * Form-stable DMZ level for tier buy costs, unlock gates, and titles.
     * Updates only while the player is in base form so transforming cannot
     * raise (or lower) Ancient Coin prices mid-fight.
     *
     * @param fallbackWhenTransformed preferred level when transformed with no
     *        base-form sample yet (e.g. {@code highestDmzLevel}); {@code <=0} ignored
     */
    public static int dmzLevelForProgression(Player player, long fallbackWhenTransformed) {
        if (player == null) {
            return 1;
        }
        int live = dmzLevel(player);
        UUID id = player.m_20148_();
        if (!isTransformed(player)) {
            rememberBaseFormLevel(id, player, live);
            return live;
        }
        Integer cached = BASE_FORM_LEVEL.get(id);
        if (cached != null) {
            int resolved = clampDmzLevel(cached, stats(player), player);
            StatsData data = stats(player);
            double peak = data != null ? formMultiplierPeak(data) : 1.0;
            // Heal stale/polluted cache (same rules as Buy GUI sample).
            if (peak <= 2.0 && ((resolved <= 1 && live >= 25) || live > resolved + 500)) {
                rememberBaseFormLevel(id, player, live);
                Integer healed = BASE_FORM_LEVEL.get(id);
                return healed != null ? healed : Math.max(1, live);
            }
            return resolved;
        }
        if (fallbackWhenTransformed > 0L) {
            long capped = Math.min(fallbackWhenTransformed, configuredMaxDmzLevel(player));
            return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, capped));
        }
        // Last resort: live level (may be form-sensitive on some race setups).
        return live;
    }

    public static int dmzLevelForProgression(Player player) {
        return dmzLevelForProgression(player, 0L);
    }

    /**
     * DMZ level for unlock gates only.
     * Never uses historical {@code highestDmzLevel} or form-inflated live level —
     * those let post-prestige players keep high-tier access without Prestige N.
     * Base form → live level · transformed → last base-form sample · else 1.
     * Callers that revoke unlocks must check {@link #hasReliableUnlockGateSample} first —
     * a bare {@code 1} here means "unknown", not "player is level 1".
     */
    public static int dmzLevelForUnlockGate(Player player) {
        if (player == null) {
            return 1;
        }
        UUID id = player.m_20148_();
        StatsData data = stats(player);
        if (data == null) {
            Integer cached = BASE_FORM_LEVEL.get(id);
            return cached != null ? Math.max(1, cached) : 1;
        }
        if (!isTransformed(player)) {
            int live = dmzLevel(player);
            rememberBaseFormLevel(id, player, live);
            return live;
        }
        Integer cached = BASE_FORM_LEVEL.get(id);
        if (cached != null) {
            return Math.max(1, cached);
        }
        return 1;
    }

    /**
     * True when unlock-gate level is safe to use for revoke / prestige-up resets.
     * False when:
     * <ul>
     *   <li>DMZ stats are not attached yet (login race — placeholder level 1)</li>
     *   <li>transformed with no base-form sample this session</li>
     *   <li>session cache is a polluted level-1 while live DMZ level is clearly higher</li>
     * </ul>
     */
    public static boolean hasReliableUnlockGateSample(Player player) {
        if (player == null) {
            return false;
        }
        StatsData data = stats(player);
        if (data == null) {
            return false;
        }
        // Character attaches after StatsData on login/respawn. Sampling before that
        // writes placeholder level-1 into BASE_FORM_LEVEL and freezes Buy GUI until death.
        if (character(player) == null) {
            return false;
        }
        if (!isTransformed(player)) {
            int live = dmzLevel(player);
            // Stats/BP loaded but getLevel() still placeholder 1 — keep pulling.
            if (live <= 1 && transformationPower(player) >= 25.0) {
                return false;
            }
            Integer cached = BASE_FORM_LEVEL.get(player.m_20148_());
            if (cached != null && cached <= 1 && live >= 25) {
                BASE_FORM_LEVEL.remove(player.m_20148_());
            }
            return true;
        }
        Integer cached = BASE_FORM_LEVEL.get(player.m_20148_());
        if (cached == null) {
            return false;
        }
        // Reject early-login pollution: BASE_FORM_LEVEL=1 written before StatsData
        // attached, while the authoritative level is far above 1.
        if (cached <= 1) {
            int live = dmzLevel(player);
            if (live >= 25) {
                BASE_FORM_LEVEL.remove(player.m_20148_());
                return false;
            }
        }
        return true;
    }

    public static void clearBaseFormLevel(UUID playerId) {
        if (playerId != null) {
            BASE_FORM_LEVEL.remove(playerId);
        }
    }

    public static void clearAllBaseFormLevels() {
        BASE_FORM_LEVEL.clear();
    }

    /**
     * Refresh the base-form DMZ sample when in base form; otherwise return the cached sample.
     * Never writes a placeholder level-1 while DMZ {@link StatsData} is missing (login race).
     */
    public static int refreshBaseFormSample(Player player) {
        if (player == null) {
            return 1;
        }
        StatsData data = stats(player);
        UUID id = player.m_20148_();
        if (data == null) {
            Integer cached = BASE_FORM_LEVEL.get(id);
            return cached != null ? Math.max(1, cached) : 1;
        }
        if (!isTransformed(player)) {
            int live = dmzLevel(player);
            rememberBaseFormLevel(id, player, live);
            return live;
        }
        Integer cached = BASE_FORM_LEVEL.get(id);
        return cached != null ? Math.max(1, cached) : 1;
    }

    /**
     * GUI-open / command hook: refresh the base-form DMZ sample, then return it.
     * Does not require personal difficulty ON — opening the menu alone is enough.
     * <p>
     * Also heals polluted session samples (cached ≪ live) when transform detection
     * is weak (form peak ≤ 2.0), so Buy GUI cannot stick on an early-login level-1
     * or a stale mid-level sample after stats attach.
     */
    public static int sampleLevelOnGuiOpen(Player player) {
        if (player == null) {
            return 1;
        }
        StatsData data = stats(player);
        UUID id = player.m_20148_();
        if (data == null) {
            Integer cached = BASE_FORM_LEVEL.get(id);
            return cached != null ? Math.max(1, cached) : 1;
        }
        int live = dmzLevel(player);
        // Login/respawn race: StatsData exists but Character is not attached yet.
        // Prefer a meaningful live read for GUI paint; never freeze placeholder 1.
        if (character(player) == null) {
            Integer cached = BASE_FORM_LEVEL.get(id);
            if (live > 1) {
                return live;
            }
            return cached != null ? Math.max(1, cached) : 1;
        }
        if (!isTransformed(player)) {
            rememberBaseFormLevel(id, player, live);
            Integer cached = BASE_FORM_LEVEL.get(id);
            return cached != null ? cached : Math.max(1, live);
        }
        Integer cached = BASE_FORM_LEVEL.get(id);
        double peak = formMultiplierPeak(data);
        // Weak/false transform + stale/polluted cache → prefer live for GUI display.
        if (peak <= 2.0 && (cached == null
                || (cached <= 1 && live >= 25)
                || live > cached + 500)) {
            rememberBaseFormLevel(id, player, live);
            Integer healed = BASE_FORM_LEVEL.get(id);
            return healed != null ? healed : Math.max(1, live);
        }
        return cached != null ? Math.max(1, cached) : 1;
    }

    /**
     * DMZ level painted in Buy GUI / placeholders.
     * <p>
     * Uses live {@link #dmzLevel} (same formula as DMZ's own level readout) whenever
     * it is meaningful. High combat-rating / form players often have battle power
     * loaded while {@code getLevel()} is still placeholder 1 — {@link #dmzLevel}
     * recomputes from stats in that case so the menu cannot stay stuck.
     * Unlock gates / tier costs still use base-form freeze separately.
     */
    public static int guiDisplayDmzLevel(Player player) {
        if (player == null) {
            return 1;
        }
        int sampled = sampleLevelOnGuiOpen(player);
        int live = dmzLevel(player);
        int best = Math.max(1, Math.max(sampled, live));
        // High CR is driven heavily by form battle power. If level still looks like
        // a placeholder while BP is clearly progressed, keep pulling via sample/live
        // recompute — never paint 1 over a known session sample.
        if (best <= 1) {
            double tp = transformationPower(player);
            if (tp >= 25.0) { // BP >= ~25k
                Integer frozen = BASE_FORM_LEVEL.get(player.m_20148_());
                if (frozen != null && frozen > 1) {
                    return frozen;
                }
            }
        }
        return best;
    }

    /**
     * Authoritative DMZ level for tier scaling: {@code paintEase}, buy costs, tier
     * menus, combat rating, and title unlocks.
     * <p>
     * Takes the higher of form-stable {@link #dmzLevelForProgression} and
     * {@link #guiDisplayDmzLevel} so placeholder-1 / stale-cache reads cannot
     * freeze scaling while still refusing to let a transient form spike lower
     * prices mid-fight.
     */
    public static int tierScalingDmzLevel(Player player) {
        return tierScalingDmzLevel(player, 0L);
    }

    /**
     * @param fallbackWhenTransformed passed to {@link #dmzLevelForProgression} when
     *        transformed with no base-form sample (e.g. {@code highestDmzLevel})
     */
    public static int tierScalingDmzLevel(Player player, long fallbackWhenTransformed) {
        if (player == null) {
            return 1;
        }
        int stable = dmzLevelForProgression(player, fallbackWhenTransformed);
        int display = guiDisplayDmzLevel(player);
        return Math.max(1, Math.max(stable, display));
    }

    /**
     * Persist a base-form DMZ sample only when Character is attached.
     * Skips login-race writes and refuses to overwrite a good sample with placeholder 1.
     */
    private static void rememberBaseFormLevel(UUID id, Player player, int live) {
        if (id == null || player == null) {
            return;
        }
        if (character(player) == null) {
            return;
        }
        int clamped = Math.max(1, live);
        Integer cached = BASE_FORM_LEVEL.get(id);
        if (clamped <= 1 && cached != null && cached > 1) {
            return;
        }
        BASE_FORM_LEVEL.put(id, clamped);
    }

    /**
     * True when a real combat form / stack form is active.
     * <p>
     * DMZ often leaves {@code activeForm = "base"} with a non-empty group — that is NOT transformed.
     * Do <b>not</b> treat elevated form-multiplier noise (racial passives / baselines slightly above
     * 1.0) as transformed when the character form name is blank/{@code base}: that froze
     * {@code BASE_FORM_LEVEL} (e.g. stuck at ~7k while live DMZ level is 30k+) and the Buy GUI
     * kept showing the stale sample.
     * <p>
     * When Character is missing (login/respawn race), treat as <b>not</b> transformed —
     * form-multiplier peak alone previously froze the Buy GUI level until death rebuilt Character.
     */
    public static boolean isTransformed(Player player) {
        StatsData data = stats(player);
        if (data == null) {
            return false;
        }
        try {
            Character ch = data.getCharacter();
            if (ch != null) {
                if (ch.hasActiveForm() && isRealFormName(ch.getActiveForm())) {
                    return true;
                }
                if (ch.hasActiveStackForm() && isRealFormName(ch.getActiveStackForm())) {
                    return true;
                }
                // Character attached and only base/blank forms → base form for AD sampling.
                return false;
            }
        } catch (Throwable ignored) {
        }
        // No character yet — never freeze sampling on form-mult noise.
        return false;
    }

    /** False for null/blank/{@code base} — those are not combat forms. */
    private static boolean isRealFormName(String form) {
        if (form == null) {
            return false;
        }
        String t = form.trim();
        return !t.isEmpty() && !"base".equalsIgnoreCase(t);
    }

    private static double formMultiplierPeak(StatsData data) {
        // Delegate to combat profile so unlock-gate transform detection matches
        // addition-mode form⊕stack folding (this server disables product mode).
        return PlayerCombatProfile.liveFormMultiplierPeak(data);
    }

    /**
     * DMZ prestige skill level.
     */
    public static int prestige(Player player) {
        return skillLevel(player, "prestige");
    }

    /** Safe skill level lookup (0 when missing / getter fails). */
    public static int skillLevel(Player player, String skillId) {
        if (player == null || skillId == null || skillId.isBlank()) {
            return 0;
        }
        StatsData data = stats(player);
        if (data == null) {
            return 0;
        }
        try {
            Skills skills = data.getSkills();
            if (skills == null) {
                return 0;
            }
            return Math.max(0, skills.getSkillLevel(skillId));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** True when a toggleable skill is currently active. */
    public static boolean skillActive(Player player, String skillId) {
        if (player == null || skillId == null || skillId.isBlank()) {
            return false;
        }
        StatsData data = stats(player);
        if (data == null) {
            return false;
        }
        try {
            Skills skills = data.getSkills();
            return skills != null && skills.isSkillActive(skillId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Transformation / form power contribution for Combat Rating.
     * <p>
     * Built from <b>released combat statistics</b> (live damage/defense channels ×
     * power release) — same idea as sparring's release-aware rating — not raw DMZ
     * {@code getBattlePowerExact()}. Androids and similar races can report Inf /
     * absurd BP that previously painted {@link Long#MAX_VALUE} in the Buy GUI.
     */
    public static double transformationPower(Player player) {
        return CombatRating.transformFromReleasedStats(player);
    }
}
