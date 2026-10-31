package com.dbzlegacy.adaptivedifficulty.config;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

/** Mirrors the concept doc admin settings. Saved at {@code config/legacymechanics.json}. */
public final class DifficultyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DifficultyConfig INSTANCE = new DifficultyConfig();

    /**
     * Master switch. When {@code false}, scaling / rewards / AI / purchases are inert.
     * Toggle in-game: {@code /difficulty admin off|on|toggle}.
     */
    public boolean enabled = true;

    /**
     * Testing whitelist. When {@code true}, only players in {@link #whitelist} may use AD.
     * Toggle: {@code /difficulty admin whitelist on|off}.
     */
    public boolean whitelistEnabled = false;
    /**
     * Entries are player names (case-insensitive) and/or UUID strings.
     * Managed via {@code /difficulty admin whitelist add|remove}.
     */
    public List<String> whitelist = new ArrayList<>();
    /**
     * When {@code true}, AD mob hits on players using the difficulty system are appended to
     * {@code config/legacymechanics/telemetry/hits-YYYY-MM-DD.jsonl} for balance tuning
     * (all AD players, rate-limited — not whitelist-gated).
     * Toggle: {@code /difficulty admin telemetry on|off}.
     */
    public boolean balanceTelemetryEnabled = false;
    /**
     * When {@code true}, staff ({@link com.dbzlegacy.adaptivedifficulty.util.StaffAccess})
     * pay no Ancient Coins for LM paid features (tier purchases, Character Services, End dragon summon, …).
     * Default {@code false} — ops/admins pay like everyone unless this is on or they hold a bypass permission.
     * Toggle: {@code /difficulty admin stafffree on|off}.
     */
    public boolean staffFreeAncientCoinCosts = false;
    /** Max telemetry hit lines per player per second (spam guard). */
    public int balanceTelemetryMaxPerSecond = 8;
    /**
     * Unified system event log (difficulty/rival/sparring) under
     * {@code config/legacymechanics/telemetry/systems-YYYY-MM-DD.jsonl}.
     * Toggle: {@code /difficulty admin syslog on|off}.
     */
    public boolean enableSystemTelemetry = true;
    /** Max system telemetry lines per player per second. */
    public int systemTelemetryMaxPerSecond = 20;

    public double prestigeMultiplier = 10.0;
    public double levelMultiplier = 1.0;
    public double teamBonusPercent = 10.0;
    public double contributionPercent = 25.0;
    /** Extra elite roll % per weighted online rival using teams (added to {@link #eliteChancePercent}). */
    public double teamEliteChanceBonusPercent = 0.75;
    /** Extra mutation roll % per weighted online rival. */
    public double teamMutationChanceBonusPercent = 1.0;
    /** Boss HP threshold reduction % per weighted rival (more mobs qualify as bosses). */
    public double teamBossThresholdBonusPercent = 8.0;
    /** Flat boss promotion roll % per weighted rival on T6+ claims (non-natural). */
    public double teamBossPromotionChancePercent = 0.20;
    /** Threshold team mode uses this fraction of team rarity bonuses (Full = 1.0). */
    public double teamThresholdRarityMult = 0.65;
    /** Soft divisor for kill XP multiplier. */
    public double rewardScaling = 2_500.0;
    /**
     * Offense (damage/defense) curve exponent. Higher = steeper growth with difficulty.
     * Effective = {@code pow(d, exp) * pow(pivot, 1-exp)}.
     */
    public double combatCurveExponent = 0.96;
    /** Difficulty where the offense curve matches old linear rates. */
    public long combatCurvePivot = 450L;
    /**
     * Health curve exponent — kept low so mob HP does not become unkillable sponges.
     */
    public double healthCurveExponent = 0.40;
    /** Pivot for the health curve (usually same ballpark as combat pivot). */
    public long healthCurvePivot = 250L;
    /** Flat health % rate (applied through the flat health curve). */
    public double healthPercentPerDifficulty = 0.45;
    /**
     * Per-difficulty damage % (offense curve).
     * Tuned so at ~8M (level-100k band) a base-3 melee hit stays under ~800k raw —
     * what most players can tank from HP alone — while still biting through ~200k DEF.
     */
    public double damagePercentPerDifficulty = 0.62;
    /**
     * Per-difficulty armor points (offense curve).
     * Tuned so mob armor ≈ ~200k at 8M, matching typical endgame player DEF.
     */
    public double defensePercentPerDifficulty = 1.85;
    public double movementPercentPer100Difficulty = 0.15;
    /** DMZ-style extra health % (kept low; health uses its own flat curve). */
    public double dmzExtraHealthPercent = 0.45;
    /** DMZ-style extra damage % (offense curve). */
    public double dmzExtraDamagePercent = 0.62;
    /** DMZ-style extra armor points (offense curve). */
    public double dmzExtraDefensePercent = 1.85;
    /** DMZ-style extra ki damage % (offense curve). */
    public double dmzExtraKiDamagePercent = 0.62;
    public double mobScaleRadius = 64.0;
    /**
     * Area difficulty mode (Scaling Health-inspired):
     * {@code weighted} (default), {@code average}, or {@code max}.
     */
    public String areaDifficultyMode = "weighted";
    /** Extra area difficulty percent per additional nearby player (Scaling Health group bonus). */
    public double areaGroupBonusPercent = 5.0;
    /** Random variance applied to area difficulty when scaling a mob (percent, e.g. 5 => 0.95–1.05). */
    public double areaDifficultyVariancePercent = 5.0;
    /**
     * Absolute ceiling on difficulty values. {@code 0} = no hardcap
     * (max is only calculated from DMZ stats + purchased + team bonuses).
     */
    public long hardCapDifficulty = 0L;

    /**
     * Reference caps used to document the top tier (Zenith).
     * Theoretical max ≈ referenceMaxLevel × referenceMaxPrestige × prestigeMultiplier
     * = 150000 × 20 × 10 with defaults. Server DMZ maxValue is 150k; the playable
     * level cap is Overhaul's flat cap (150k; dmzrevamp 2.2.4 has no per-prestige ladder).
     */
    public long referenceMaxLevel = 150_000L;
    public int referenceMaxPrestige = 20;

    // Ability unlock tier thresholds (admin-editable)
    public long tierAwakened = 10L;
    public long tierEnhanced = 50L;
    public long tierElite = 100L;
    public long tierAdvanced = 500L;
    public long tierMaster = 1_000L;
    public long tierLegendary = 5_000L;
    public long tierGod = 10_000L;
    public long tierDivine = 50_000L;
    public long tierImpossible = 100_000L;
    public long tierTranscendent = 250_000L;
    public long tierEternal = 500_000L;
    public long tierMythic = 1_000_000L;
    public long tierOmega = 2_500_000L;
    public long tierAbsolute = 5_000_000L;
    public long tierApex = 7_500_000L;
    /** Top internal ability ladder threshold (not a player Unlock Tier). */
    public long tierZenith = 10_000_000L;
    public boolean scaleHostileOnly = true;
    /**
     * Apply DMZ extra health/defense/damage/ki percents to <b>all</b> hostiles
     * (not only {@code dragonminez:} mobs). Default true so vanilla/modded
     * hostiles scale the same way as DMZ enemies.
     */
    public boolean applyDmzExtrasToAllHostiles = true;
    public boolean enableRewardScaling = true;
    public boolean enableMobScaling = true;

    // Phase 2 systems
    public boolean enableElites = true;
    /** Percent chance a claimed T4+ hostile becomes elite (rare). Nameplate only on true rarity. */
    public double eliteChancePercent = 2.25;
    public double eliteStatMultiplier = 1.50;
    public boolean enableMutations = true;
    /** Percent chance a claimed T5+ hostile mutates. Elites get ×1.35. Nameplate only on true rarity. */
    public double mutationChancePercent = 3.75;
    /**
     * One-time: stock 3%/5% chances felt common after the claim-seed +15% elite bug.
     * Migrates untouched defaults down to 1.25%/2%.
     */
    public Boolean rarityChanceMigrated = Boolean.FALSE;
    /**
     * One-time: after kit nameplates stopped faking "Elite" variants, lower stock rarity
     * defaults again (1.25%/2% → 0.75%/1.25%) when still on the prior stock values.
     */
    public Boolean rarityChanceMigratedV2 = Boolean.FALSE;
    /**
     * One-time: stock rarity ×3 (0.75%/1.25% → 2.25%/3.75%).
     * Only rewrites untouched stock values; custom admin-set chances are kept.
     */
    public Boolean rarityChanceMigratedV3 = Boolean.FALSE;
    public boolean enableAdaptiveAi = true;
    /** Most allies one pack call pulls in, from Master tier up (lower tiers keep 2). */
    public int adaptiveAiPackCallMaxAllies = 4;
    /** From this mob tier up there is no pack call and frenzy buffs only the mob itself. */
    public String adaptiveAiPackCallOffTier = "MYTHIC";
    /** From this mob tier up, melee kit mobs fight as duel kit-fighters (one action per beat). */
    public String adaptiveAiKitFighterTier = "MYTHIC";
    /** High-tier mobs strip Iron's Spells Evasion / Abyssal Shroud from players they hit. */
    public boolean enableDodgeCounter = true;
    /** Lowest mob tier whose hits strip those dodge effects. */
    public String dodgeCounterTier = "MASTER";
    public boolean enableEnemyEvolution = true;
    public boolean enableBossScaling = true;

    /** Rival System 4.7.10 port (declare / proximity / challenges / instinct). */
    public boolean enableRivalSystem = true;
    /** Sparring Tp System 3.2.11 port (auto sessions / mentor / TP formula). */
    public boolean enableSparringSystem = true;
    /** Sparring: ms a landed melee hit keeps a fighter counted as fighting (4.7.17: 8000, was 4500). */
    public long sparMeleeHitWindowMs = 8000L;
    /** Sparring: ms a landed ki / beam hit keeps a fighter counted as fighting. */
    public long sparKiHitWindowMs = 10_000L;
    /** Sparring: ms a fighter counts as moving after real movement. */
    public long sparMovementWindowMs = 10_000L;
    /** Sparring: ms the pair gets to resume fighting before the session ends (4.7.17: 6000, was 4000). */
    public long sparSessionGraceMs = 6000L;
    /** Sparring: ms a reported beam or strike clash holds the session open. */
    public long sparClashHoldMs = 2500L;
    /** Sparring: ms the session stays open after a clash ends (4.7.17; covers the loser's stun). */
    public long sparPostClashHoldMs = 6000L;
    /** Sparring: ms between hits before a combo resets. */
    public long sparComboTimeoutMs = 2500L;
    /** Sparring: ms momentum lasts after the last scored hit. */
    public long sparMomentumDurationMs = 10_000L;
    /**
     * One-time CustomNPCs storeddata → LM JSON/NBT import on first boot / first login,
     * then clears those CNPC keys so scripts no longer own the data.
     */
    public boolean enableCnpcDataMigration = true;

    /** Master switch for LegacyMechanics progression modules. */
    public boolean enableProgression = true;
    public boolean enablePotential = true;
    /**
     * Players in Living World's meditation state gain DragonMineZ Meditation
     * while another meditating player is in range, or while two living NPCs
     * that are meditating are in range. One NPC is not enough. Standing
     * nearby without meditating does not count.
     */
    public boolean enableLivingWorldMeditation = true;
    /** Blocks from another meditating player or a meditating living NPC. */
    public int meditationDetectionRadius = 16;
    /**
     * Seconds of nearby time required to reach each Meditation level.
     * Index 0 is level 1. Defaults total 31 hours 51 minutes to level 10.
     */
    public int[] meditationLevelSeconds = new int[] {
            60, 300, 900, 1800, 3600, 7200, 14400, 21600, 28800, 36000
    };
    public boolean enableGlobalTpBoost = true;
    public boolean enableGlobalMasteryBoost = true;
    public boolean enableBioAndroid = true;
    public boolean enableRaceLock = true;
    public boolean enableYardrat = true;
    public boolean enableSpiritualistKi = true;
    public boolean enableAndroidConversion = true;
    public boolean rivalPresenceTp = true;
    public boolean rivalInstinct = true;
    public boolean rivalChallenges = true;

    /** Combat / End / dummy / shop progression ports. */
    public boolean enableKiWeapons = true;
    public boolean enablePiercingBonus = true;
    public boolean enableDotExtraDamage = true;
    public boolean enableApothicElemental = true;
    public boolean enableEndDimensionStrength = true;
    /** Block vanilla End portal / gateway travel and Eye-of-Ender frame lighting. */
    public boolean enableEndPortalGuard = true;
    public boolean enableEndNaturalDragonSpawn = false;
    /**
     * Difficulty GUI paid End Dragon summon (T4–T7, Ancient Netherite cost).
     * Natural spawn stays off. This is the player path.
     */
    public boolean enableEndPlayerDragonSummon = true;
    /**
     * One-time: 4.5.154 forced the Difficulty summon off. Turn it back on once,
     * then leave later staff toggles alone.
     */
    public Boolean endPlayerDragonSummonRestoredV1 = Boolean.FALSE;
    /** Ancient Netherite coins charged for a player GUI End Dragon summon. */
    public int endDragonSummonNetheriteCost = 3;
    /** Difficulty GUI paid Wither summon (active tier T3-T7, in the Nether). */
    public boolean enableWitherSummon = true;
    /** Ancient Netherite coins charged for a player GUI Wither summon. */
    public int witherSummonNetheriteCost = 1;
    /** Difficulty GUI paid Warden summon (active tier T4-T7, in the Overworld). */
    public boolean enableWardenSummon = true;
    /** Ancient Netherite coins charged for a player GUI Warden summon. */
    public int wardenSummonNetheriteCost = 2;
    /**
     * Paid boss summons (End Dragon, Wither, Warden): chance in percent per tier above the
     * boss's minimum tier that each dropped stack gets one bonus copy (capped at 100).
     */
    public int summonLootBonusPerTierPercent = 25;
    /**
     * Scale Enderman / Shulker / Phantom / Endermite HP/DEF (End Dimension Strength).
     * Default {@code false} matches script v2.11.0 — dragon-only scaling.
     */
    public boolean enableEndMobScaling = false;
    /**
     * Cap living End dragons: at most one player-summoned dragon per summoner,
     * and strip unauthorized vanilla dragons. Never removes another player's fight.
     * (Legacy name — was “one dragon in the whole End”.)
     */
    public boolean endEnforceSingleDragon = true;
    /** Purge / cap leftover DMZ {@code ki_laser} / {@code ki_blast} in The End (v2.12.0). */
    public boolean endKiCleanupEnabled = true;
    /** When no dragon is alive, purge all End ki projectiles. */
    public boolean endKiPurgeWhenNoDragon = true;
    /** Hard cap on End ki projectiles while a dragon is alive (script default 32). */
    public int endKiMaxAliveWhileDragon = 32;
    public boolean enableShadowDummyLimiter = true;
    public boolean enableSkillUnlockService = true;
    public boolean enablePrestigeSystem = true;
    /**
     * God Ki Enhancement (prestige store): mastery needed on the enhancement
     * form before the true God Ki form may transform. Custom God forms only
     * need the enhancement present. Each store investment grants 1000
     * mastery, so 1000 = first investment unlocks the true form.
     */
    public int godKiTrueFormMasteryThreshold = 1000;
    /**
     * Use dmzrevamp Statistics prestige UI + native scaling/rebirth. LM still owns
     * playable max stat total via mixins ({@code enableOverhaulPrestigeIntegration}).
     * Overhaul's flat level cap ({@code configuredLevelMaximum}, 150k as LM pins it, dmzrevamp 2.2.4);
     * the prestige requirement reads it back from Overhaul.
     * Requires {@code LevelingRevamp.json → Prestige.enabled: true} on the server.
     */
    public boolean enableOverhaulPrestigeIntegration = true;
    /**
     * Donator Skill Check UI ({@code /skillcheck}, CNPC interact / trigger 21).
     * Needs {@link #donatorPermission}; staff are not auto-granted.
     */
    public boolean enableSkillCheck = true;
    /** LuckPerms node for donators: Skill Check. */
    public String donatorPermission = "legacymechanics.donator";
    /** LuckPerms node for {@code /lm revive} on yourself (higher donator ranks; staff always may). */
    public String selfRevivePermission = "legacymechanics.donator.selfrevive";
    /** LuckPerms node for {@code /terminal} (higher donator ranks). */
    public String terminalPermission = "legacymechanics.donator.terminal";
    /** LuckPerms node that lets a non-staff player use {@code /lm revive <player>}. */
    public String reviveOthersPermission = "legacymechanics.revive.others";
    /**
     * CNPC display-name needles (case-insensitive substring) that open Skill Check on interact.
     * Also matches scoreboard tag {@code lm_skillcheck}.
     */
    public List<String> skillCheckNpcNameContains = new ArrayList<>(Arrays.asList(
            "Skill Check", "SkillCheck", "Skill Progress"
    ));
    /** Sneak + right-click another player to dump DMZ stats (PlayerStatChecker.js). */
    public boolean enablePlayerStatChecker = true;

    /**
     * Periodic on-screen Legacy Mechanics tips ({@code /lm} hub reminders).
     * Title/subtitle only — no chat. Helps newer ranks discover systems.
     */
    public boolean enableLmTips = true;
    /** Seconds between tips for higher ranks (default 10 minutes). */
    public int lmTipIntervalSeconds = 600;
    /**
     * Seconds between tips for LuckPerms primary groups in {@link #lmTipFrequentGroups}
     * (default / student / novice). Default 4 minutes.
     */
    public int lmTipNewPlayerIntervalSeconds = 240;
    /** Seconds after login before the first tip. */
    public int lmTipLoginDelaySeconds = 75;
    /**
     * LuckPerms <b>primary</b> groups that get the frequent tip cadence.
     * Default: {@code default}, {@code student}, {@code novice}.
     */
    public List<String> lmTipFrequentGroups = new ArrayList<>(Arrays.asList(
            "default", "student", "novice"
    ));
    /**
     * Fallback when LuckPerms is unavailable: DMZ level below this uses frequent cadence.
     */
    public int lmTipNewPlayerMaxLevel = 2500;
    /**
     * Console log lines for single events (a player action, packet, fight, or staff command).
     * Off keeps them in logs/debug.log only; startup lines, warnings, and errors always show.
     */
    public boolean debugLogging = false;

    public double bossStatMultiplier = 1.5;
    /** Natural (pre-scale) max-health at/above this marks a boss. Default keeps wardens/etc. */
    public double bossHealthThreshold = 300.0;
    public List<String> bossIdContains = new ArrayList<>(Arrays.asList(
            "boss", "warden", "wither", "ender_dragon", "raid_boss", "raidboss"
    ));
    /**
     * Optional ceiling on the health multiplier from the health curve.
     * {@code 0} or {@code 1} = uncapped (default). Absolute HP still limited by {@link #maxScaledHealth}.
     */
    public double maxHealthMultiplier = 0.0;
    /**
     * Absolute max HP after scaling.
     * {@code 0} = uncapped (default). Vanilla's 1024 max_health attribute limit is raised at mod boot.
     */
    public double maxScaledHealth = 0.0;
    public double maxMoveMultiplier = 2.0;
    /**
     * Optional armor-point ceiling from defense scaling.
     * {@code 0} = uncapped (default). Only values {@code > 0} apply a ceiling.
     */
    public double maxArmorBonus = 0.0;
    /**
     * Optional damage multiplier ceiling.
     * {@code 0} or {@code 1} = uncapped (default). Only values {@code > 1} apply a ceiling.
     */
    public double maxDamageMultiplier = 0.0;
    /** Admin permission node (Forge PermissionAPI / LuckPerms). Ops always allowed. */
    public String adminPermission = "difficulty.admin";
    /**
     * Player UI backend for {@code /lm} and system menus.
     * {@code ultra} (default) = KaiPath Ultra UI via {@code legacymechanics:gui};
     * {@code cnpc} = CustomNPCs dialogs (explicit fallback);
     * {@code chat} = text-only. Retired {@code cmi}/{@code chest} values migrate to ultra on load.
     */
    public String guiBackend = "ultra";
    /** Staff {@code /lm admin testgui} — opens the Ultra hub (experimental flag). */
    public boolean enableStaffCnpcTestGui = true;
    /**
     * If the Minecraft world is on Peaceful (no hostile spawns), restore it on server start.
     * Peaceful prevents adaptive mob scaling from doing anything.
     * Null in JSON means enabled (default true).
     */
    public Boolean restoreVanillaDifficultyFromPeaceful = Boolean.TRUE;
    /** Target vanilla difficulty when restoring from Peaceful: easy / normal / hard. */
    public String vanillaDifficulty = "hard";
    /**
     * Dimensions where adaptive difficulty skips scaling / AI / evolution / gravity.
     * Empty by default — The End scales like Overworld/Nether; Ender Dragon is
     * hard-exempt (End Strength script). Re-add {@code minecraft:the_end} to deny.
     */
    public List<String> disabledDimensions = new ArrayList<>();
    /**
     * One-time: strip legacy default {@code minecraft:the_end} from
     * {@link #disabledDimensions} so existing configs pick up End scaling.
     */
    public Boolean endScalingEnabledMigrated = Boolean.FALSE;

    // ── V3 Combat Rating weights ───────────────────────────────────────────
    public double combatRatingDmzWeight = 1.0;
    public double combatRatingPrestigeWeight = 1000.0;
    public double combatRatingTransformWeight = 1.0;
    public double combatRatingDifficultyWeight = 1.0;

    // ── V3 Unlock tier requirements / ceilings / activation costs ───────────
    public long unlockTier1Level = 1L;
    public long unlockTier2Level = 500L;
    public long unlockTier3Level = 1_000L;
    public long unlockTier4Level = 5_000L;
    public long unlockTier5Level = 10_000L;
    public long unlockTier6Level = 50_000L;
    public long unlockTier7Level = 100_000L;
    public long unlockTier1Max = 1_000L;
    public long unlockTier2Max = 5_000L;
    public long unlockTier3Max = 10_000L;
    public long unlockTier4Max = 25_000L;
    public long unlockTier5Max = 50_000L;
    public long unlockTier6Max = 100_000L;
    public long unlockTier7Max = 250_000L;
    /** Copper-value at the tier unlock level. T1 default = 1× Copper Ancient. */
    public long unlockTier1Cost = 1L;
    public long unlockTier2Cost = 5L;
    public long unlockTier3Cost = 15L;
    public long unlockTier4Cost = 50L;
    public long unlockTier5Cost = 150L;
    public long unlockTier6Cost = 500L;
    public long unlockTier7Cost = 1_500L;
    /**
     * Legacy excess-above-unlock divisor (pre-2.3.61). Kept for config/admin
     * compat; stock pricing now uses {@link #tierCostLevelAnchor} +
     * {@link #tierCostT7TargetCopper}.
     */
    public double tierCostLevelDivisor = 50_000.0;
    /**
     * DMZ level where stock T7 hits {@link #tierCostT7TargetCopper}.
     * Cost mult is exponential in absolute level from 1 → this anchor
     * (progress clamped), shared across all tiers: {@code base × endMult^progress}.
     * Stock: level 1 → T1 = 1× Copper; level 150000 → T7 = 100× Netherite.
     */
    public double tierCostLevelAnchor = 150_000.0;
    /**
     * Target copper-value for T7 at {@link #tierCostLevelAnchor}.
     * Stock {@code 10_000_000} = 100× Netherite Ancient Coins.
     */
    public long tierCostT7TargetCopper = 10_000_000L;
    /**
     * Nearby-mob scale vs the player's post-transform / limit-release stats.
     * Defaults (1.0.1): T1 21% · T2 42% · T3 65% · T4 90% · T5 135% · T6 160% · T7 200%.
     * Shape: T1 slight early / trivial high → T7 near one-shot pressure.
     */
    public double unlockTier1EnemyMult = 0.21;
    public double unlockTier2EnemyMult = 0.42;
    public double unlockTier3EnemyMult = 0.65;
    public double unlockTier4EnemyMult = 0.90;
    public double unlockTier5EnemyMult = 1.35;
    public double unlockTier6EnemyMult = 1.60;
    public double unlockTier7EnemyMult = 2.00;
    /**
     * Multiplier on final scaled mob max HP ({@code playerMaxHp × tier% × …}).
     * Default {@code 0.75} — ~65% of the old 1.15 sponge so TP-from-HP leveling
     * is less free; damage / defense / other mob attrs are unchanged.
     */
    public double mobHealthScale = 0.75;
    /**
     * Ceiling on detected form⊕stack multipliers (default {@code 100}).
     * Future races with huge {@code maxStatsMultiplier} mastery still soft-curve;
     * this only bounds detection / peel so a typo'd JSON cannot NaN the scaler.
     */
    public double maxFormBoost = 100.0;
    /**
     * Soft ceiling on a single live STR/SKP/VIT/RES channel fed into blend math
     * (default {@code 5.0E7}). Prevents broken race getters from overflowing mob attrs.
     */
    public double maxLiveCombatChannel = 50_000_000.0;
    /**
     * How much of a form/transform boost enemies scale to (0–1), after the soft curve.
     * {@code 0} = form-stripped base stats only · {@code 1} = strongest allowed form slice.
     * Default {@code 0.65}. Combined with {@link #transformScaleExponent} + tier damp /
     * mega-form compress so big forms raise pressure without linearly exploding T6–T7.
     */
    public double transformScaleWeight = 0.65;
    /**
     * Diminishing-returns exponent on form surplus ({@code (live/base - 1)^exp × weight}).
     * {@code 1.0} = linear · {@code 0.75} = stock · lower = softer high forms.
     */
    public double transformScaleExponent = 0.75;
    /**
     * Legacy weak-stat dump multiplier (unused in combat as of 3.3.35).
     * Kept for config/admin compat; default 1.0 = no effect.
     */
    public double weakStatCounterMult = 1.0;
    /**
     * Legacy pierce knob (unused in combat as of 3.3.35).
     * Kept for config/admin compat; default has no combat effect.
     */
    public double weakDefensePierceMult = 1.75;
    /**
     * Live damage floor from player soft RES × tier % — keeps pure RES dumps from
     * facing wet-noodle hits. Still bounded by the VIT hit cap afterward.
     * Example: 0.45 → mob attack at least 45% of (defense × tierPercent).
     */
    public double tankDamageDefenseRatio = 0.45;
    /**
     * Live damage floor from player soft VIT × tier % — presses high-VIT bags so
     * "stack VIT, ignore offense" still feels the tier ladder.
     * Example: 0.28 → mob attack at least 28% of (hitCapHealth × tierPercent).
     */
    public double tankDamageHealthRatio = 0.28;
    /**
     * One-time: raise soft T1–T3 percents + tank pierce so early difficulty
     * contests high-DEF / low-level players (old 15%/30%/55% + 0.40 floor).
     */
    public Boolean lowTierPressureMigrated = Boolean.FALSE;
    /**
     * One-time: raise mid/high tier stock percents
     * T4 80→90 · T5 110→125 · T6 130→165 (T1–T3/T7 already match the ladder).
     */
    public Boolean tierPercentLadderMigrated = Boolean.FALSE;
    /**
     * One-time: stock ladder 28/42/65/90/125/165/200 → 13/28/42/58/76/90/116.
     * Only rewrites untouched stock values; custom admin-set ladders are kept.
     */
    public Boolean tierPercentLadderMigratedV2 = Boolean.FALSE;
    /**
     * One-time: stock ladder 13/28/42/58/76/90/116 → 10/20/30/40/50/65/90.
     * Only rewrites untouched stock values; custom admin-set ladders are kept.
     */
    public Boolean tierPercentLadderMigratedV3 = Boolean.FALSE;
    /**
     * One-time (1.0.1): soft stock 10/20/30/40/50/65/90 (+ transform 0.25/0.50)
     * → balanced ladder 21/42/65/90/135/160/200 (+ transform 0.55/0.75).
     * Only rewrites untouched stock values; custom admin-set ladders are kept.
     */
    public Boolean tierPercentLadderMigratedV4 = Boolean.FALSE;
    /**
     * One-time: remap legacy CR enemy-mult ladder (1.0/1.15/…/3.0) → percent ladder.
     * Without this flag the remap re-ran every sanitize and blocked that ladder as admin intent.
     */
    public Boolean crEnemyMultPercentMigratedV1 = Boolean.FALSE;
    /**
     * One-time: drop legacy absolute HP caps (400 / 1024) → uncapped (0).
     * Without this flag, intentional 1024 caps were wiped every reload.
     */
    public Boolean legacyMaxHealthCapMigratedV1 = Boolean.FALSE;
    /**
     * One-time: raise stale live {@code referenceMaxLevel} 100000 → 150000 to match the
     * DMZ / Overhaul 150k ceiling.
     */
    public Boolean referenceMaxLevel150kMigratedV1 = Boolean.FALSE;
    /** One-time: raise the live {@code referenceMaxPrestige} 10 → 20 with the prestige cap (4.7.12). */
    public Boolean referenceMaxPrestige20MigratedV1 = Boolean.FALSE;
    /**
     * One-time: drop race / weak-stat / specialization stack and soften class + top-1
     * counter stock values so existing configs match the lighter 3.3.35 model.
     */
    public Boolean counterSimplifyMigratedV1 = Boolean.FALSE;
    /**
     * One-time: soften stock pierce / counter overlays so early 10–20% tiers
     * are not secretly ~DEF pierce + full class/top-stat stacks.
     */
    public Boolean lowTierCounterSoftMigratedV1 = Boolean.FALSE;
    /**
     * One-time: balance pass — raise mob HP sponge, soften elite spike, nudge tank HP floor.
     */
    public Boolean balanceFeelMigratedV1 = Boolean.FALSE;
    /**
     * One-time (1.0.12): challenge feel — raise mob sponge, restore live VIT/RES
     * damage floors so even / dump builds feel the tier ladder.
     */
    public Boolean challengeFeelMigratedV1 = Boolean.FALSE;
    /**
     * One-time (1.0.13): skill-aware sponge — raise stock {@code mobHealthScale}
     * 0.90 → 1.05 so Ki Infusion / mega forms still trade hits.
     */
    public Boolean skillChallengeMigratedV1 = Boolean.FALSE;
    /**
     * One-time (1.0.14): god-form pressure — sponge 1.05→1.15 + transform weight 0.55→0.65.
     */
    public Boolean godFormPressureMigratedV1 = Boolean.FALSE;
    /**
     * One-time (1.0.15): VIT-dump / tank pressure — stock {@code tankDamageHealthRatio}
     * 0.10 → 0.22 so high-VIT builds track the same ladder as even builds.
     */
    public Boolean vitDumpPressureMigratedV1 = Boolean.FALSE;
    /**
     * One-time (1.0.19): telemetry feel — stock {@code tankDamageHealthRatio}
     * 0.22 → 0.28 so T3→T6 tanks keep climbing (live data plateaued ~14–17%).
     */
    public Boolean telemetryFeelMigratedV1 = Boolean.FALSE;
    /**
     * One-time (1.0.29): stock {@code mobHealthScale} 1.15 → 0.92 (80% HP sponge).
     * Damage / defense / other mob attrs unchanged. Custom admin scales kept.
     */
    public Boolean mobHpTrimMigratedV1 = Boolean.FALSE;
    /**
     * One-time (1.0.30): stock {@code mobHealthScale} 0.92 (or leftover 1.15) → 0.75.
     * Custom admin scales kept.
     */
    public Boolean mobHpTrimMigratedV2 = Boolean.FALSE;
    /**
     * One-time: soften transform→enemy scaling (weight 0.40→0.25 + soft curve).
     */
    public Boolean transformSoftMigratedV1 = Boolean.FALSE;
    /**
     * One-time: starter economy — T1 at level 1 costs 1× Copper (was 100 = 1× Gold).
     * Only rewrites the stock cost ladder / T1 level gate; custom admin values kept.
     */
    public Boolean starterCopperCostMigratedV1 = Boolean.FALSE;
    /**
     * One-time (1.0.26): stock {@code tierCostLevelDivisor} 1000 → 50000 so T7 at
     * unlock stays near base (excess scaling no longer inverts T6≫T7).
     * Custom admin divisors are kept.
     */
    public Boolean tierCostDivisorMigratedV1 = Boolean.FALSE;
    /**
     * One-time (2.3.61): absolute-level curve — L1/T1 = 1× Copper,
     * L150k/T7 = 100× Netherite. Rewrites stock anchor/target only.
     */
    public Boolean tierCostCurveMigratedV2 = Boolean.FALSE;
    /**
     * One-time (1.0.45): stock coin drop 100%→5%, upgrade duo 2%→0.5%.
     * Custom admin chances are kept.
     */
    public Boolean coinDropChanceMigratedV1 = Boolean.FALSE;
    /**
     * Bumped on every live sanitize/reload so claimed-mob NBT signatures cannot
     * early-return with stale paint after admin retunes.
     */
    private transient long paintEpoch = 0L;
    /**
     * Legacy specialization tax (unused in combat as of 3.3.35).
     * Kept for config/admin compat; default 0.0 = no effect.
     */
    public double specializationDamageTax = 0.0;
    /**
     * When true, mobs also counter the player's DMZ fighting class
     * (warrior, spiritualist, tank, …).
     */
    public boolean enableClassCounters = true;
    /**
     * When true, mobs also counter the player's single highest invested combat stat
     * (STR / SKP / RES / VIT / PWR / ENE — top 2).
     */
    public boolean enableStrongStatCounters = true;
    /** Strength of top-stat counter overlays (1.0 = off effect, higher = harder). */
    public double strongStatCounterMult = 1.08;
    /** Extra mob damage vs the player's fighting style (class overlay). */
    public double classCounterDamageMult = 1.06;
    /** Extra mob HP vs glass / caster / ki classes. */
    public double classCounterHealthMult = 1.05;
    /** Extra mob armor vs melee / strike classes. */
    public double classCounterArmorMult = 1.06;
    /**
     * Legacy race overlay (unused in combat as of 3.3.35).
     * Kept for config/admin compat; default 1.0 = no effect.
     */
    public double raceCounterMult = 1.0;
    /**
     * Cap on multiplicative counter overlays after pierce floors
     * (class × top-stat only). Prevents stacking blow-ups.
     */
    public double maxCounterOverlayMult = 1.25;
    /** Converts DMZ defense share into vanilla armor points: log1p(def) × factor. */
    public double defenseToArmorFactor = 2.5;
    /** Server ticks between nearby-player mob rescale pulses (per-player stagger). */
    public int nearbyScaleIntervalTicks = 40;
    /**
     * Max difficulty-adjusted hostiles near one player at a time (closest win).
     * Also used as the nearby rescale budget.
     */
    public int maxScaledMobsPerPlayer = 5;
    /** @deprecated use {@link #maxScaledMobsPerPlayer}; kept for config compat. */
    @Deprecated
    public int nearbyScaleBudgetPerPlayer = 5;

    // ── V3 Ancient Coin economy ────────────────────────────────────────────
    public boolean enableAncientCoinDrops = true;
    public double ancientCoinDropMult = 1.0;
    /**
     * Chance (0–1) on each eligible kill to drop any Ancient Coin(s). Default 5%.
     * Rolls below {@link #ancientCoinUpgradeChance} also grant the next-higher coin.
     */
    public double ancientCoinDropChance = 0.05;
    /**
     * Chance (0–1) on each eligible kill to drop the normal coin <em>and</em> one
     * next-higher denomination (e.g. Copper + Iron). Must be ≤
     * {@link #ancientCoinDropChance}. Default 0.5%.
     */
    public double ancientCoinUpgradeChance = 0.005;
    public double ancientCoinRatingDivisor = 25_000.0;
    /** V3: reset active tier/level on player death (unlocks stay). */
    public boolean deathResetsActiveDifficulty = true;
    /** Minimum active unlock-tier for elites / mutations / full AI. */
    public int eliteMinUnlockTier = 4;
    public int mutationMinUnlockTier = 5;
    /** Soft AI starts at Unlock Tier 1; kit depth is capped by {@link com.dbzlegacy.adaptivedifficulty.tier.UnlockAbilityCaps}. */
    public int adaptiveAiMinUnlockTier = 1;
    public int enemyEvolutionMinUnlockTier = 1;
    public int bossMechanicsMinUnlockTier = 6;

    public boolean shouldRestoreVanillaFromPeaceful() {
        return restoreVanillaDifficultyFromPeaceful == null || restoreVanillaDifficultyFromPeaceful;
    }

    public long tierRequiredLevel(int tierId) {
        // Floor at 1 — unlockTierNLevel 0 must never mean "everyone unlocked".
        return switch (tierId) {
            case 1 -> Math.max(1L, unlockTier1Level);
            case 2 -> Math.max(1L, unlockTier2Level);
            case 3 -> Math.max(1L, unlockTier3Level);
            case 4 -> Math.max(1L, unlockTier4Level);
            case 5 -> Math.max(1L, unlockTier5Level);
            case 6 -> Math.max(1L, unlockTier6Level);
            case 7 -> Math.max(1L, unlockTier7Level);
            default -> Long.MAX_VALUE / 4L;
        };
    }

    public long tierMaxDifficulty(int tierId) {
        return switch (tierId) {
            case 1 -> Math.max(0L, unlockTier1Max);
            case 2 -> Math.max(0L, unlockTier2Max);
            case 3 -> Math.max(0L, unlockTier3Max);
            case 4 -> Math.max(0L, unlockTier4Max);
            case 5 -> Math.max(0L, unlockTier5Max);
            case 6 -> Math.max(0L, unlockTier6Max);
            case 7 -> Math.max(0L, unlockTier7Max);
            default -> 0L;
        };
    }

    public long tierActivationCost(int tierId) {
        return switch (tierId) {
            case 1 -> Math.max(0L, unlockTier1Cost);
            case 2 -> Math.max(0L, unlockTier2Cost);
            case 3 -> Math.max(0L, unlockTier3Cost);
            case 4 -> Math.max(0L, unlockTier4Cost);
            case 5 -> Math.max(0L, unlockTier5Cost);
            case 6 -> Math.max(0L, unlockTier6Cost);
            case 7 -> Math.max(0L, unlockTier7Cost);
            default -> Long.MAX_VALUE / 4L;
        };
    }

    /**
     * Level-scaled tier purchase cost for a player at {@code dmzLevel}.
     * <p>
     * Stock curve (2.3.61+): exponential in absolute DMZ level from 1 →
     * {@link #tierCostLevelAnchor}, with end multiplier
     * {@code tierCostT7TargetCopper / T7 base} so T1@1 ≈ 1× Copper and
     * T7@150k ≈ 100× Netherite. Progress is clamped at the anchor (no further
     * rise past it). Higher tiers stay ≥ ~25% above the previous tier.
     */
    public long tierActivationCostScaled(int tierId, int dmzLevel) {
        long base = tierActivationCost(tierId);
        if (base <= 0L) {
            return 0L;
        }
        double mult = tierCostLevelMultiplier(Math.max(1, dmzLevel));
        long scaled = Math.round(base * mult);
        long raw = Math.max(base, scaled);
        // Cap at 128 of one coin type, then promote (top rung = 128× Netherite).
        long cost = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy.normalizeCost(raw);

        // Always keep higher tiers at least ~25% above the previous tier's cost.
        if (tierId > 1) {
            long prev = tierActivationCostScaled(tierId - 1, dmzLevel);
            if (cost <= prev) {
                long floor = prev + Math.max(1L, prev / 4L);
                cost = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy
                        .normalizeCost(Math.max(raw, floor));
                if (cost <= prev) {
                    cost = com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy
                            .costStrictlyAbove(prev);
                }
            }
        }
        return cost;
    }

    /**
     * Shared level multiplier for tier buy costs: 1.0 at DMZ 1, rising
     * exponentially to {@code target/T7base} at {@link #tierCostLevelAnchor}.
     */
    public double tierCostLevelMultiplier(int dmzLevel) {
        long t7Base = Math.max(1L, tierActivationCost(7));
        long target = Math.max(t7Base, Math.max(1L, tierCostT7TargetCopper));
        double endMult = Math.max(1.0, (double) target / (double) t7Base);
        double anchor = Math.max(2.0, tierCostLevelAnchor);
        double progress = (Math.max(1, dmzLevel) - 1.0) / (anchor - 1.0);
        if (progress <= 0.0) {
            return 1.0;
        }
        if (progress >= 1.0) {
            return endMult;
        }
        return Math.exp(progress * Math.log(endMult));
    }

    public double tierEnemyMult(int tierId) {
        return tierPlayerStatPercent(tierId);
    }

    /** Fraction of the player's transformed/released stats used for nearby mobs. */
    public double tierPlayerStatPercent(int tierId) {
        return switch (tierId) {
            case 1 -> clampPercent(unlockTier1EnemyMult, 0.21);
            case 2 -> clampPercent(unlockTier2EnemyMult, 0.42);
            case 3 -> clampPercent(unlockTier3EnemyMult, 0.65);
            case 4 -> clampPercent(unlockTier4EnemyMult, 0.90);
            case 5 -> clampPercent(unlockTier5EnemyMult, 1.35);
            case 6 -> clampPercent(unlockTier6EnemyMult, 1.60);
            case 7 -> clampPercent(unlockTier7EnemyMult, 2.00);
            default -> 0.0;
        };
    }

    /** Range-only clamp for live tier percents — never remaps admin ladders. */
    private static double clampPercent(double value, double fallback) {
        if (!(value > 0.0) || Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(0.01, Math.min(10.0, value));
    }

    /** Tier thresholds — editable via {@code /difficulty admin set tierX <n>}. */
    public long tierThreshold(com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier tier) {
        if (tier == null) {
            return 0L;
        }
        return switch (tier) {
            case NONE -> 0L;
            case AWAKENED -> Math.max(0L, tierAwakened);
            case ENHANCED -> Math.max(0L, tierEnhanced);
            case ELITE -> Math.max(0L, tierElite);
            case ADVANCED -> Math.max(0L, tierAdvanced);
            case MASTER -> Math.max(0L, tierMaster);
            case LEGENDARY -> Math.max(0L, tierLegendary);
            case GOD -> Math.max(0L, tierGod);
            case DIVINE -> Math.max(0L, tierDivine);
            case IMPOSSIBLE -> Math.max(0L, tierImpossible);
            case TRANSCENDENT -> Math.max(0L, tierTranscendent);
            case ETERNAL -> Math.max(0L, tierEternal);
            case MYTHIC -> Math.max(0L, tierMythic);
            case OMEGA -> Math.max(0L, tierOmega);
            case ABSOLUTE -> Math.max(0L, tierAbsolute);
            case APEX -> Math.max(0L, tierApex);
            case ZENITH -> Math.max(0L, tierZenith);
        };
    }

    private DifficultyConfig() {}

    public static DifficultyConfig get() {
        return INSTANCE;
    }

    /** Field defaults for the editor. Does not replace the live config. */
    public static DifficultyConfig freshDefaults() {
        return new DifficultyConfig();
    }

    /** Master gate used by events / scaling / rewards / player purchases. */
    public static boolean isEnabled() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    public static void setEnabled(boolean on) {
        if (INSTANCE == null) {
            INSTANCE = new DifficultyConfig();
        }
        INSTANCE.enabled = on;
        save();
    }

    public static boolean isWhitelistEnabled() {
        return INSTANCE != null && INSTANCE.whitelistEnabled;
    }

    public static void setWhitelistEnabled(boolean on) {
        if (INSTANCE == null) {
            INSTANCE = new DifficultyConfig();
        }
        INSTANCE.whitelistEnabled = on;
        save();
    }

    /** When whitelist is off, everyone is allowed (if the master switch is on). */
    public static boolean isPlayerAllowed(ServerPlayer player) {
        if (INSTANCE == null || player == null) {
            return false;
        }
        if (!INSTANCE.whitelistEnabled) {
            return true;
        }
        return isWhitelisted(player);
    }

    public static boolean isWhitelisted(ServerPlayer player) {
        if (INSTANCE == null || player == null) {
            return false;
        }
        return matchesWhitelist(player.m_6302_(), player.m_20148_());
    }

    public static boolean matchesWhitelist(String name, UUID uuid) {
        if (INSTANCE == null || INSTANCE.whitelist == null || INSTANCE.whitelist.isEmpty()) {
            return false;
        }
        String nameKey = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String uuidKey = uuid == null ? "" : uuid.toString().toLowerCase(Locale.ROOT);
        for (String raw : INSTANCE.whitelist) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if ((!nameKey.isEmpty() && entry.equals(nameKey))
                    || (!uuidKey.isEmpty() && entry.equals(uuidKey))) {
                return true;
            }
        }
        return false;
    }

    /** @return true if newly added */
    public static boolean addWhitelistEntry(String raw) {
        if (INSTANCE == null) {
            INSTANCE = new DifficultyConfig();
        }
        if (INSTANCE.whitelist == null) {
            INSTANCE.whitelist = new ArrayList<>();
        }
        String entry = normalizeWhitelistEntry(raw);
        if (entry.isEmpty()) {
            return false;
        }
        for (String existing : INSTANCE.whitelist) {
            if (existing != null && existing.equalsIgnoreCase(entry)) {
                return false;
            }
        }
        INSTANCE.whitelist.add(entry);
        save();
        return true;
    }

    /** @return true if removed */
    public static boolean removeWhitelistEntry(String raw) {
        if (INSTANCE == null || INSTANCE.whitelist == null) {
            return false;
        }
        String entry = normalizeWhitelistEntry(raw);
        if (entry.isEmpty()) {
            return false;
        }
        boolean removed = INSTANCE.whitelist.removeIf(
                e -> e != null && e.equalsIgnoreCase(entry)
        );
        if (removed) {
            save();
        }
        return removed;
    }

    public static List<String> whitelistEntries() {
        if (INSTANCE == null || INSTANCE.whitelist == null) {
            return List.of();
        }
        return new ArrayList<>(INSTANCE.whitelist);
    }

    private static String normalizeWhitelistEntry(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        // Keep UUID casing canonical lowercase; names lowercase for matching.
        try {
            return UUID.fromString(t).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return t.toLowerCase(Locale.ROOT);
        }
    }

    /** Current config filename (LegacyMechanics 1.0.45+). */
    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("legacymechanics.json");
    }

    /** AdaptiveDifficulty 1.0–1.0.44 filename — auto-migrated on first load. */
    public static Path adaptivePath() {
        return FMLPaths.CONFIGDIR.get().resolve("adaptivedifficulty.json");
    }

    /** Pre-1.0 filename — auto-migrated on first load. */
    public static Path legacyPath() {
        return FMLPaths.CONFIGDIR.get().resolve("dmz_adaptive_difficulty.json");
    }

    /** @return false when the JSON exists but could not be parsed. */
    public static boolean load() {
        Path file = path();
        Path adaptive = adaptivePath();
        Path legacy = legacyPath();
        try {
            if (!Files.exists(file) && Files.exists(adaptive)) {
                try {
                    Files.move(adaptive, file);
                    AdaptiveDifficultyMod.LOGGER.info(
                            "[{}] migrated config {} → {}",
                            AdaptiveDifficultyMod.MOD_ID,
                            adaptive.getFileName(),
                            file.getFileName());
                } catch (Exception moveFail) {
                    AdaptiveDifficultyMod.LOGGER.warn(
                            "[{}] could not rename adaptive config; loading {} then re-saving as {}",
                            AdaptiveDifficultyMod.MOD_ID,
                            adaptive.getFileName(),
                            file.getFileName(),
                            moveFail);
                    file = adaptive;
                }
            }
            if (!Files.exists(file) && Files.exists(legacy)) {
                try {
                    Files.move(legacy, file);
                    AdaptiveDifficultyMod.LOGGER.info(
                            "[{}] migrated config {} → {}",
                            AdaptiveDifficultyMod.MOD_ID,
                            legacy.getFileName(),
                            file.getFileName());
                } catch (Exception moveFail) {
                    AdaptiveDifficultyMod.LOGGER.warn(
                            "[{}] could not rename legacy config; loading {} then re-saving as {}",
                            AdaptiveDifficultyMod.MOD_ID,
                            legacy.getFileName(),
                            file.getFileName(),
                            moveFail);
                    file = legacy;
                }
            }
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    DifficultyConfig loaded = GSON.fromJson(reader, DifficultyConfig.class);
                    if (loaded != null) {
                        normalize(loaded);
                        INSTANCE = loaded;
                    }
                }
            }
            save();
            return true;
        } catch (Exception e) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] failed to load config: {}", AdaptiveDifficultyMod.MOD_ID, e.toString());
            if (INSTANCE == null) {
                INSTANCE = new DifficultyConfig();
            }
            return false;
        }
    }

    private static void normalize(DifficultyConfig cfg) {
        if (cfg.bossIdContains == null) {
            cfg.bossIdContains = new ArrayList<>();
        }
        if (cfg.whitelist == null) {
            cfg.whitelist = new ArrayList<>();
        } else {
            LinkedHashSet<String> cleaned = new LinkedHashSet<>();
            for (String raw : cfg.whitelist) {
                String entry = normalizeWhitelistEntry(raw);
                if (!entry.isEmpty()) {
                    cleaned.add(entry);
                }
            }
            cfg.whitelist = new ArrayList<>(cleaned);
        }
        if (cfg.disabledDimensions == null) {
            cfg.disabledDimensions = new ArrayList<>();
        }
        // Legacy installs defaulted The End off; enable End hostiles once (dragon still exempt).
        if (!Boolean.TRUE.equals(cfg.endScalingEnabledMigrated)) {
            cfg.disabledDimensions.removeIf(raw -> {
                if (raw == null || raw.isBlank()) {
                    return true;
                }
                String want = raw.trim().toLowerCase();
                return want.equals("minecraft:the_end") || want.equals("the_end");
            });
            cfg.endScalingEnabledMigrated = Boolean.TRUE;
        }
        // Drop inflated stock rarity chances once (3%/5% → 1.25%/2%).
        if (!Boolean.TRUE.equals(cfg.rarityChanceMigrated)) {
            if (nearly(cfg.eliteChancePercent, 3.0)) {
                cfg.eliteChancePercent = 1.25;
            }
            if (nearly(cfg.mutationChancePercent, 5.0)) {
                cfg.mutationChancePercent = 2.0;
            }
            cfg.rarityChanceMigrated = Boolean.TRUE;
        }
        // Further drop stock rarity after kit cosmetics stopped looking like variants.
        if (!Boolean.TRUE.equals(cfg.rarityChanceMigratedV2)) {
            if (nearly(cfg.eliteChancePercent, 1.25)) {
                cfg.eliteChancePercent = 0.75;
            }
            if (nearly(cfg.mutationChancePercent, 2.0)) {
                cfg.mutationChancePercent = 1.25;
            }
            cfg.rarityChanceMigratedV2 = Boolean.TRUE;
        }
        // Triple stock rarity chances (0.75%/1.25% → 2.25%/3.75%).
        if (!Boolean.TRUE.equals(cfg.rarityChanceMigratedV3)) {
            if (nearly(cfg.eliteChancePercent, 0.75)) {
                cfg.eliteChancePercent = 2.25;
            }
            if (nearly(cfg.mutationChancePercent, 1.25)) {
                cfg.mutationChancePercent = 3.75;
            }
            cfg.rarityChanceMigratedV3 = Boolean.TRUE;
        }
        // Raise soft early-tier pressure + tank pierce (stock values only).
        if (!Boolean.TRUE.equals(cfg.lowTierPressureMigrated)) {
            if (nearly(cfg.unlockTier1EnemyMult, 0.15)) {
                cfg.unlockTier1EnemyMult = 0.28;
            }
            if (nearly(cfg.unlockTier2EnemyMult, 0.30)) {
                cfg.unlockTier2EnemyMult = 0.42;
            }
            if (nearly(cfg.unlockTier3EnemyMult, 0.55)) {
                cfg.unlockTier3EnemyMult = 0.65;
            }
            if (nearly(cfg.tankDamageDefenseRatio, 0.40)) {
                cfg.tankDamageDefenseRatio = 0.90;
            }
            if (nearly(cfg.tankDamageHealthRatio, 0.012)) {
                cfg.tankDamageHealthRatio = 0.025;
            }
            if (nearly(cfg.weakDefensePierceMult, 1.35)) {
                cfg.weakDefensePierceMult = 1.75;
            }
            cfg.lowTierPressureMigrated = Boolean.TRUE;
        }
        // Raise mid/high tier stock percents to the 28/42/65/90/125/165/200 ladder.
        if (!Boolean.TRUE.equals(cfg.tierPercentLadderMigrated)) {
            if (nearly(cfg.unlockTier4EnemyMult, 0.80)) {
                cfg.unlockTier4EnemyMult = 0.90;
            }
            if (nearly(cfg.unlockTier5EnemyMult, 1.10)) {
                cfg.unlockTier5EnemyMult = 1.25;
            }
            if (nearly(cfg.unlockTier6EnemyMult, 1.30)) {
                cfg.unlockTier6EnemyMult = 1.65;
            }
            cfg.tierPercentLadderMigrated = Boolean.TRUE;
        }
        // Soften stock ladder: 28/42/65/90/125/165/200 → 13/28/42/58/76/90/116.
        if (!Boolean.TRUE.equals(cfg.tierPercentLadderMigratedV2)) {
            boolean stockPrior =
                    nearly(cfg.unlockTier1EnemyMult, 0.28)
                            && nearly(cfg.unlockTier2EnemyMult, 0.42)
                            && nearly(cfg.unlockTier3EnemyMult, 0.65)
                            && nearly(cfg.unlockTier4EnemyMult, 0.90)
                            && nearly(cfg.unlockTier5EnemyMult, 1.25)
                            && nearly(cfg.unlockTier6EnemyMult, 1.65)
                            && nearly(cfg.unlockTier7EnemyMult, 2.00);
            if (stockPrior) {
                cfg.unlockTier1EnemyMult = 0.13;
                cfg.unlockTier2EnemyMult = 0.28;
                cfg.unlockTier3EnemyMult = 0.42;
                cfg.unlockTier4EnemyMult = 0.58;
                cfg.unlockTier5EnemyMult = 0.76;
                cfg.unlockTier6EnemyMult = 0.90;
                cfg.unlockTier7EnemyMult = 1.16;
            }
            cfg.tierPercentLadderMigratedV2 = Boolean.TRUE;
        }
        // Soften stock ladder: 13/28/42/58/76/90/116 → 10/20/30/40/50/65/90.
        if (!Boolean.TRUE.equals(cfg.tierPercentLadderMigratedV3)) {
            boolean stockPrior =
                    nearly(cfg.unlockTier1EnemyMult, 0.13)
                            && nearly(cfg.unlockTier2EnemyMult, 0.28)
                            && nearly(cfg.unlockTier3EnemyMult, 0.42)
                            && nearly(cfg.unlockTier4EnemyMult, 0.58)
                            && nearly(cfg.unlockTier5EnemyMult, 0.76)
                            && nearly(cfg.unlockTier6EnemyMult, 0.90)
                            && nearly(cfg.unlockTier7EnemyMult, 1.16);
            if (stockPrior) {
                cfg.unlockTier1EnemyMult = 0.10;
                cfg.unlockTier2EnemyMult = 0.20;
                cfg.unlockTier3EnemyMult = 0.30;
                cfg.unlockTier4EnemyMult = 0.40;
                cfg.unlockTier5EnemyMult = 0.50;
                cfg.unlockTier6EnemyMult = 0.65;
                cfg.unlockTier7EnemyMult = 0.90;
            }
            cfg.tierPercentLadderMigratedV3 = Boolean.TRUE;
        }
        // Restore stock pressure: soft 10–90 (+ transform 0.25/0.50) → balanced live ladder.
        if (!Boolean.TRUE.equals(cfg.tierPercentLadderMigratedV4)) {
            boolean softStock =
                    nearly(cfg.unlockTier1EnemyMult, 0.10)
                            && nearly(cfg.unlockTier2EnemyMult, 0.20)
                            && nearly(cfg.unlockTier3EnemyMult, 0.30)
                            && nearly(cfg.unlockTier4EnemyMult, 0.40)
                            && nearly(cfg.unlockTier5EnemyMult, 0.50)
                            && nearly(cfg.unlockTier6EnemyMult, 0.65)
                            && nearly(cfg.unlockTier7EnemyMult, 0.90);
            boolean midSoftStock =
                    nearly(cfg.unlockTier1EnemyMult, 0.13)
                            && nearly(cfg.unlockTier2EnemyMult, 0.28)
                            && nearly(cfg.unlockTier3EnemyMult, 0.42)
                            && nearly(cfg.unlockTier4EnemyMult, 0.58)
                            && nearly(cfg.unlockTier5EnemyMult, 0.76)
                            && nearly(cfg.unlockTier6EnemyMult, 0.90)
                            && nearly(cfg.unlockTier7EnemyMult, 1.16);
            if (softStock || midSoftStock) {
                cfg.unlockTier1EnemyMult = 0.21;
                cfg.unlockTier2EnemyMult = 0.42;
                cfg.unlockTier3EnemyMult = 0.65;
                cfg.unlockTier4EnemyMult = 0.90;
                cfg.unlockTier5EnemyMult = 1.35;
                cfg.unlockTier6EnemyMult = 1.60;
                cfg.unlockTier7EnemyMult = 2.00;
            }
            if (nearly(cfg.transformScaleWeight, 0.25)) {
                cfg.transformScaleWeight = 0.55;
            }
            if (nearly(cfg.transformScaleExponent, 0.50)) {
                cfg.transformScaleExponent = 0.75;
            }
            cfg.tierPercentLadderMigratedV4 = Boolean.TRUE;
        }
        // Soften counters: class + top-1 only (drop race / weak / specialization stock stack).
        if (!Boolean.TRUE.equals(cfg.counterSimplifyMigratedV1)) {
            if (nearly(cfg.weakStatCounterMult, 1.45)) {
                cfg.weakStatCounterMult = 1.0;
            }
            if (nearly(cfg.specializationDamageTax, 0.60)) {
                cfg.specializationDamageTax = 0.0;
            }
            if (nearly(cfg.raceCounterMult, 1.12)) {
                cfg.raceCounterMult = 1.0;
            }
            if (nearly(cfg.strongStatCounterMult, 1.28)) {
                cfg.strongStatCounterMult = 1.15;
            }
            if (nearly(cfg.classCounterDamageMult, 1.22)) {
                cfg.classCounterDamageMult = 1.12;
            }
            if (nearly(cfg.classCounterHealthMult, 1.18)) {
                cfg.classCounterHealthMult = 1.10;
            }
            if (nearly(cfg.classCounterArmorMult, 1.20)) {
                cfg.classCounterArmorMult = 1.12;
            }
            if (nearly(cfg.maxCounterOverlayMult, 2.25)) {
                cfg.maxCounterOverlayMult = 1.55;
            }
            cfg.counterSimplifyMigratedV1 = Boolean.TRUE;
        }
        // Soften early-tier stock pierce / counter overlays (post 3.3.35 light model).
        if (!Boolean.TRUE.equals(cfg.lowTierCounterSoftMigratedV1)) {
            if (nearly(cfg.tankDamageDefenseRatio, 0.90)) {
                cfg.tankDamageDefenseRatio = 0.55;
            }
            if (nearly(cfg.tankDamageHealthRatio, 0.025)) {
                cfg.tankDamageHealthRatio = 0.015;
            }
            if (nearly(cfg.strongStatCounterMult, 1.15) || nearly(cfg.strongStatCounterMult, 1.28)) {
                cfg.strongStatCounterMult = 1.08;
            }
            if (nearly(cfg.classCounterDamageMult, 1.12) || nearly(cfg.classCounterDamageMult, 1.22)) {
                cfg.classCounterDamageMult = 1.06;
            }
            if (nearly(cfg.classCounterHealthMult, 1.10) || nearly(cfg.classCounterHealthMult, 1.18)) {
                cfg.classCounterHealthMult = 1.05;
            }
            if (nearly(cfg.classCounterArmorMult, 1.12) || nearly(cfg.classCounterArmorMult, 1.20)) {
                cfg.classCounterArmorMult = 1.06;
            }
            if (nearly(cfg.maxCounterOverlayMult, 1.55) || nearly(cfg.maxCounterOverlayMult, 2.25)) {
                cfg.maxCounterOverlayMult = 1.25;
            }
            cfg.lowTierCounterSoftMigratedV1 = Boolean.TRUE;
        }
        // Balance feel: sponge + elite spike + tank HP floor (3.3.38).
        if (!Boolean.TRUE.equals(cfg.balanceFeelMigratedV1)) {
            if (nearly(cfg.mobHealthScale, 0.5)) {
                cfg.mobHealthScale = 0.65;
            }
            if (nearly(cfg.eliteStatMultiplier, 1.75)) {
                cfg.eliteStatMultiplier = 1.50;
            }
            if (nearly(cfg.tankDamageHealthRatio, 0.015) || nearly(cfg.tankDamageHealthRatio, 0.025)) {
                cfg.tankDamageHealthRatio = 0.020;
            }
            cfg.balanceFeelMigratedV1 = Boolean.TRUE;
        }
        // Transform soft curve (3.3.44): linear 0.40 still one-tapped high-tier form swaps.
        if (!Boolean.TRUE.equals(cfg.transformSoftMigratedV1)) {
            if (nearly(cfg.transformScaleWeight, 0.40)) {
                cfg.transformScaleWeight = 0.25;
            }
            // Only fill missing/invalid exponent — do not overwrite intentional linear (1.0).
            if (cfg.transformScaleExponent <= 0.0
                    || Double.isNaN(cfg.transformScaleExponent)
                    || Double.isInfinite(cfg.transformScaleExponent)) {
                cfg.transformScaleExponent = 0.50;
            }
            cfg.transformSoftMigratedV1 = Boolean.TRUE;
        }
        // Starter economy (3.3.50): stock T1 cost was 100 copper-value → shown as 1× Gold.
        if (!Boolean.TRUE.equals(cfg.starterCopperCostMigratedV1)) {
            boolean stockCosts = cfg.unlockTier1Cost == 100L
                    && cfg.unlockTier2Cost == 500L
                    && cfg.unlockTier3Cost == 1_500L
                    && cfg.unlockTier4Cost == 5_000L
                    && cfg.unlockTier5Cost == 15_000L
                    && cfg.unlockTier6Cost == 50_000L
                    && cfg.unlockTier7Cost == 150_000L;
            if (stockCosts) {
                cfg.unlockTier1Cost = 1L;
                cfg.unlockTier2Cost = 5L;
                cfg.unlockTier3Cost = 15L;
                cfg.unlockTier4Cost = 50L;
                cfg.unlockTier5Cost = 150L;
                cfg.unlockTier6Cost = 500L;
                cfg.unlockTier7Cost = 1_500L;
            }
            // Stock T1 gate was level 100 — low-level players could never buy the 1× Copper tier.
            if (cfg.unlockTier1Level == 100L) {
                cfg.unlockTier1Level = 1L;
            }
            cfg.starterCopperCostMigratedV1 = Boolean.TRUE;
        }
        if (cfg.guiBackend == null || cfg.guiBackend.isBlank()) {
            cfg.guiBackend = "ultra";
        } else {
            String before = cfg.guiBackend.trim();
            // Canonicalize aliases so Forge + Bukkit agree.
            cfg.guiBackend = switch (before.toLowerCase()) {
                case "ultra", "kaipath", "dmzultra", "native" -> "ultra";
                // CNPC retired — coerce to ultra (KaiPath required).
                case "cnpc", "customnpcs", "customnpc", "noppes" -> "ultra";
                case "cmi", "cmilib", "cmigui", "deluxemenus", "deluxe", "dm" -> "cmi";
                case "chest", "bukkit", "inventory", "gui" -> "chest";
                case "chat" -> "chat";
                case "auto" -> "auto";
                default -> "ultra";
            };
            // Retired CMI / chest backends → Ultra (KaiPath). Persist via load()'s save().
            if ("cmi".equals(cfg.guiBackend) || "chest".equals(cfg.guiBackend)) {
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] migrated guiBackend \"{}\" → ultra",
                        AdaptiveDifficultyMod.MOD_ID,
                        before);
                cfg.guiBackend = "ultra";
            }
        }
        if (cfg.vanillaDifficulty == null || cfg.vanillaDifficulty.isBlank()) {
            cfg.vanillaDifficulty = "hard";
        }
        if (cfg.adminPermission == null || cfg.adminPermission.isBlank()) {
            cfg.adminPermission = "difficulty.admin";
        }
        if (cfg.combatCurveExponent <= 0.0) {
            cfg.combatCurveExponent = 0.96;
        }
        if (cfg.combatCurvePivot < 1L) {
            cfg.combatCurvePivot = 450L;
        }
        if (cfg.healthCurveExponent <= 0.0) {
            cfg.healthCurveExponent = 0.40;
        }
        if (cfg.healthCurvePivot < 1L) {
            cfg.healthCurvePivot = 250L;
        }
        if (cfg.rewardScaling < 1.0) {
            cfg.rewardScaling = 2_500.0;
        }
        if (!Boolean.TRUE.equals(cfg.legacyMaxHealthCapMigratedV1)) {
            if (nearly(cfg.maxScaledHealth, 400.0) || nearly(cfg.maxScaledHealth, 1024.0)) {
                cfg.maxScaledHealth = 0.0;
            }
            cfg.legacyMaxHealthCapMigratedV1 = Boolean.TRUE;
        }
        if (!Boolean.TRUE.equals(cfg.referenceMaxLevel150kMigratedV1)) {
            if (cfg.referenceMaxLevel == 100_000L) {
                cfg.referenceMaxLevel = 150_000L;
            }
            cfg.referenceMaxLevel150kMigratedV1 = Boolean.TRUE;
        }
        if (!Boolean.TRUE.equals(cfg.referenceMaxPrestige20MigratedV1)) {
            if (cfg.referenceMaxPrestige == 10) {
                cfg.referenceMaxPrestige = 20;
            }
            cfg.referenceMaxPrestige20MigratedV1 = Boolean.TRUE;
        }
        if (cfg.maxDamageMultiplier < 0.0) {
            cfg.maxDamageMultiplier = 0.0;
        }
        if (cfg.maxArmorBonus < 0.0) {
            cfg.maxArmorBonus = 0.0;
        }
        if (cfg.areaDifficultyMode == null || cfg.areaDifficultyMode.isBlank()) {
            cfg.areaDifficultyMode = "weighted";
        }
        if (cfg.hardCapDifficulty < 0L || cfg.hardCapDifficulty == 1_000_000L) {
            cfg.hardCapDifficulty = 0L;
        }
        // 1.0.26: stock divisor 1000 made T6≫T7 at DMZ 100k; ladder floor then
        // inflated God-tier buys far above the documented unlock≈base price.
        if (!Boolean.TRUE.equals(cfg.tierCostDivisorMigratedV1)) {
            if (nearly(cfg.tierCostLevelDivisor, 1_000.0)) {
                cfg.tierCostLevelDivisor = 50_000.0;
            }
            cfg.tierCostDivisorMigratedV1 = Boolean.TRUE;
        }
        if (cfg.tierCostLevelDivisor < 1.0) {
            cfg.tierCostLevelDivisor = 50_000.0;
        }
        // 2.3.61: absolute-level anchors — L1/T1 = 1× Copper, L150k/T7 = 100× Netherite.
        if (!Boolean.TRUE.equals(cfg.tierCostCurveMigratedV2)) {
            boolean stockDivisor = nearly(cfg.tierCostLevelDivisor, 50_000.0)
                    || nearly(cfg.tierCostLevelDivisor, 1_000.0);
            boolean stockTarget = cfg.tierCostT7TargetCopper <= 0L
                    || cfg.tierCostT7TargetCopper == 10_000_000L
                    || cfg.tierCostT7TargetCopper == 1_500L;
            if (stockDivisor && stockTarget) {
                cfg.tierCostLevelAnchor = 150_000.0;
                cfg.tierCostT7TargetCopper = 10_000_000L;
            }
            cfg.tierCostCurveMigratedV2 = Boolean.TRUE;
        }
        if (cfg.tierCostLevelAnchor < 2.0) {
            cfg.tierCostLevelAnchor = 150_000.0;
        }
        if (cfg.tierCostT7TargetCopper < 1L) {
            cfg.tierCostT7TargetCopper = 10_000_000L;
        }
        // 1.0.45: stock kill coins are chance-gated (was always-on + 2% upgrade).
        if (!Boolean.TRUE.equals(cfg.coinDropChanceMigratedV1)) {
            if (cfg.ancientCoinDropChance <= 0.0 || nearly(cfg.ancientCoinDropChance, 1.0)) {
                cfg.ancientCoinDropChance = 0.05;
            }
            if (nearly(cfg.ancientCoinUpgradeChance, 0.02)) {
                cfg.ancientCoinUpgradeChance = 0.005;
            }
            cfg.coinDropChanceMigratedV1 = Boolean.TRUE;
        }
        if (cfg.ancientCoinDropChance < 0.0 || cfg.ancientCoinDropChance > 1.0) {
            cfg.ancientCoinDropChance = 0.05;
        }
        if (cfg.ancientCoinUpgradeChance < 0.0 || cfg.ancientCoinUpgradeChance > 1.0) {
            cfg.ancientCoinUpgradeChance = 0.005;
        }
        if (cfg.ancientCoinUpgradeChance > cfg.ancientCoinDropChance) {
            cfg.ancientCoinUpgradeChance = cfg.ancientCoinDropChance;
        }
        if (cfg.mobHealthScale <= 0.0 || cfg.mobHealthScale > 4.0) {
            cfg.mobHealthScale = 0.75;
        }
        // 1.0.12: raise sponge so offense dumps don't one-punch packs.
        if (!Boolean.TRUE.equals(cfg.challengeFeelMigratedV1)) {
            if (nearly(cfg.mobHealthScale, 0.65) || nearly(cfg.mobHealthScale, 0.50)) {
                cfg.mobHealthScale = 0.90;
            }
            if (nearly(cfg.tankDamageHealthRatio, 0.020) || nearly(cfg.tankDamageHealthRatio, 0.015)
                    || nearly(cfg.tankDamageHealthRatio, 0.025)) {
                cfg.tankDamageHealthRatio = 0.10;
            }
            if (nearly(cfg.tankDamageDefenseRatio, 0.55) || nearly(cfg.tankDamageDefenseRatio, 0.90)
                    || nearly(cfg.tankDamageDefenseRatio, 0.40)) {
                cfg.tankDamageDefenseRatio = 0.45;
            }
            cfg.challengeFeelMigratedV1 = Boolean.TRUE;
        }
        // 1.0.13: skill-aware sponge for infusion / mega forms.
        if (!Boolean.TRUE.equals(cfg.skillChallengeMigratedV1)) {
            if (nearly(cfg.mobHealthScale, 0.90) || nearly(cfg.mobHealthScale, 0.65)
                    || nearly(cfg.mobHealthScale, 0.50)) {
                cfg.mobHealthScale = 1.05;
            }
            cfg.skillChallengeMigratedV1 = Boolean.TRUE;
        }
        // 1.0.14: god-form pressure — higher sponge + more form inheritance.
        if (!Boolean.TRUE.equals(cfg.godFormPressureMigratedV1)) {
            if (nearly(cfg.mobHealthScale, 1.05) || nearly(cfg.mobHealthScale, 0.90)
                    || nearly(cfg.mobHealthScale, 0.65)) {
                cfg.mobHealthScale = 1.15;
            }
            if (nearly(cfg.transformScaleWeight, 0.55) || nearly(cfg.transformScaleWeight, 0.40)
                    || nearly(cfg.transformScaleWeight, 0.25)) {
                cfg.transformScaleWeight = 0.65;
            }
            cfg.godFormPressureMigratedV1 = Boolean.TRUE;
        }
        // 1.0.29: trim stock HP sponge 20% (1.15 → 0.92) — TP/level pace only.
        // Runs after the older sponge upgrades so chained migrations still land here.
        if (!Boolean.TRUE.equals(cfg.mobHpTrimMigratedV1)) {
            if (nearly(cfg.mobHealthScale, 1.15)) {
                cfg.mobHealthScale = 0.92;
            }
            cfg.mobHpTrimMigratedV1 = Boolean.TRUE;
        }
        // 1.0.30: further trim 0.92 → 0.75 (also catches leftover 1.15).
        if (!Boolean.TRUE.equals(cfg.mobHpTrimMigratedV2)) {
            if (nearly(cfg.mobHealthScale, 0.92) || nearly(cfg.mobHealthScale, 1.15)) {
                cfg.mobHealthScale = 0.75;
            }
            cfg.mobHpTrimMigratedV2 = Boolean.TRUE;
        }
        // 1.0.15: VIT dumps / tank class must feel the buy-tier ladder (not shrug packs).
        if (!Boolean.TRUE.equals(cfg.vitDumpPressureMigratedV1)) {
            if (nearly(cfg.tankDamageHealthRatio, 0.10) || nearly(cfg.tankDamageHealthRatio, 0.020)
                    || nearly(cfg.tankDamageHealthRatio, 0.015) || nearly(cfg.tankDamageHealthRatio, 0.025)) {
                cfg.tankDamageHealthRatio = 0.22;
            }
            cfg.vitDumpPressureMigratedV1 = Boolean.TRUE;
        }
        // 1.0.19: telemetry — stretch tank HP floor so T4–T6 buys keep biting.
        if (!Boolean.TRUE.equals(cfg.telemetryFeelMigratedV1)) {
            if (nearly(cfg.tankDamageHealthRatio, 0.22) || nearly(cfg.tankDamageHealthRatio, 0.10)
                    || nearly(cfg.tankDamageHealthRatio, 0.020)) {
                cfg.tankDamageHealthRatio = 0.28;
            }
            cfg.telemetryFeelMigratedV1 = Boolean.TRUE;
        }
        if (!(cfg.maxFormBoost > 1.0) || cfg.maxFormBoost > 500.0 || Double.isNaN(cfg.maxFormBoost)) {
            cfg.maxFormBoost = 100.0;
        }
        if (!(cfg.maxLiveCombatChannel > 1.0) || cfg.maxLiveCombatChannel > 1.0e12
                || Double.isNaN(cfg.maxLiveCombatChannel)) {
            cfg.maxLiveCombatChannel = 50_000_000.0;
        }
        if (cfg.transformScaleWeight < 0.0 || cfg.transformScaleWeight > 1.0
                || Double.isNaN(cfg.transformScaleWeight)) {
            cfg.transformScaleWeight = 0.65;
        }
        if (cfg.transformScaleExponent < 0.20 || cfg.transformScaleExponent > 1.0
                || Double.isNaN(cfg.transformScaleExponent)) {
            cfg.transformScaleExponent = 0.75;
        }
        if (cfg.weakStatCounterMult < 1.0) {
            cfg.weakStatCounterMult = 1.0;
        }
        if (cfg.weakDefensePierceMult < 1.0) {
            cfg.weakDefensePierceMult = 1.75;
        }
        if (cfg.tankDamageDefenseRatio < 0.0 || cfg.tankDamageDefenseRatio > 10.0
                || Double.isNaN(cfg.tankDamageDefenseRatio)) {
            cfg.tankDamageDefenseRatio = 0.45;
        }
        if (cfg.tankDamageHealthRatio < 0.0 || cfg.tankDamageHealthRatio > 1.0
                || Double.isNaN(cfg.tankDamageHealthRatio)) {
            cfg.tankDamageHealthRatio = 0.28;
        }
        if (cfg.specializationDamageTax < 0.0) {
            cfg.specializationDamageTax = 0.0;
        }
        if (cfg.classCounterDamageMult < 1.0) {
            cfg.classCounterDamageMult = 1.0;
        }
        if (cfg.classCounterDamageMult > 3.0) {
            cfg.classCounterDamageMult = 3.0;
        }
        if (cfg.classCounterHealthMult < 1.0) {
            cfg.classCounterHealthMult = 1.0;
        }
        if (cfg.classCounterHealthMult > 3.0) {
            cfg.classCounterHealthMult = 3.0;
        }
        if (cfg.classCounterArmorMult < 1.0) {
            cfg.classCounterArmorMult = 1.0;
        }
        if (cfg.classCounterArmorMult > 3.0) {
            cfg.classCounterArmorMult = 3.0;
        }
        if (cfg.strongStatCounterMult < 1.0) {
            cfg.strongStatCounterMult = 1.08;
        }
        if (cfg.strongStatCounterMult > 3.0) {
            cfg.strongStatCounterMult = 3.0;
        }
        if (cfg.raceCounterMult < 1.0) {
            cfg.raceCounterMult = 1.0;
        }
        if (cfg.raceCounterMult > 2.0) {
            cfg.raceCounterMult = 2.0;
        }
        if (cfg.maxCounterOverlayMult < 1.0) {
            cfg.maxCounterOverlayMult = 1.25;
        }
        if (cfg.maxCounterOverlayMult > 4.0) {
            cfg.maxCounterOverlayMult = 4.0;
        }
        if (cfg.defenseToArmorFactor <= 0.0) {
            cfg.defenseToArmorFactor = 2.5;
        }
        if (cfg.balanceTelemetryMaxPerSecond < 1 || cfg.balanceTelemetryMaxPerSecond > 40) {
            cfg.balanceTelemetryMaxPerSecond = 8;
        }
        if (cfg.nearbyScaleIntervalTicks < 10) {
            cfg.nearbyScaleIntervalTicks = 40;
        }
        if (cfg.mobScaleRadius < 8.0) {
            cfg.mobScaleRadius = 8.0;
        }
        if (cfg.mobScaleRadius > 128.0) {
            cfg.mobScaleRadius = 128.0;
        }
        cfg.eliteChancePercent = Math.max(0.0, Math.min(100.0, cfg.eliteChancePercent));
        cfg.mutationChancePercent = Math.max(0.0, Math.min(100.0, cfg.mutationChancePercent));
        if (cfg.eliteStatMultiplier < 1.0) {
            cfg.eliteStatMultiplier = 1.0;
        }
        if (cfg.eliteStatMultiplier > 10.0) {
            cfg.eliteStatMultiplier = 10.0;
        }
        if (cfg.bossStatMultiplier < 1.0) {
            cfg.bossStatMultiplier = 1.0;
        }
        if (cfg.bossStatMultiplier > 10.0) {
            cfg.bossStatMultiplier = 10.0;
        }
        if (cfg.dmzExtraKiDamagePercent < 0.0) {
            cfg.dmzExtraKiDamagePercent = 0.0;
        }
        if (cfg.dmzExtraKiDamagePercent > 5.0) {
            cfg.dmzExtraKiDamagePercent = 5.0;
        }
        cfg.eliteMinUnlockTier = Math.max(0, Math.min(7, cfg.eliteMinUnlockTier));
        cfg.mutationMinUnlockTier = Math.max(0, Math.min(7, cfg.mutationMinUnlockTier));
        cfg.adaptiveAiMinUnlockTier = Math.max(0, Math.min(7, cfg.adaptiveAiMinUnlockTier));
        cfg.enemyEvolutionMinUnlockTier = Math.max(0, Math.min(7, cfg.enemyEvolutionMinUnlockTier));
        cfg.bossMechanicsMinUnlockTier = Math.max(0, Math.min(7, cfg.bossMechanicsMinUnlockTier));
        if (cfg.adminPermission == null || cfg.adminPermission.isBlank()
                || "*".equals(cfg.adminPermission.trim())) {
            cfg.adminPermission = "difficulty.admin";
        }
        if (cfg.donatorPermission == null || cfg.donatorPermission.isBlank()
                || "*".equals(cfg.donatorPermission.trim())) {
            cfg.donatorPermission = "legacymechanics.donator";
        }
        if (cfg.selfRevivePermission == null || cfg.selfRevivePermission.isBlank()
                || "*".equals(cfg.selfRevivePermission.trim())) {
            cfg.selfRevivePermission = "legacymechanics.donator.selfrevive";
        }
        if (cfg.terminalPermission == null || cfg.terminalPermission.isBlank()
                || "*".equals(cfg.terminalPermission.trim())) {
            cfg.terminalPermission = "legacymechanics.donator.terminal";
        }
        if (cfg.reviveOthersPermission == null || cfg.reviveOthersPermission.isBlank()
                || "*".equals(cfg.reviveOthersPermission.trim())) {
            cfg.reviveOthersPermission = "legacymechanics.revive.others";
        }
        if (cfg.skillCheckNpcNameContains == null) {
            cfg.skillCheckNpcNameContains = new ArrayList<>();
        }
        if (cfg.skillCheckNpcNameContains.isEmpty()) {
            cfg.skillCheckNpcNameContains.addAll(Arrays.asList(
                    "Skill Check", "SkillCheck", "Skill Progress"));
        }
        // Hard product rule: at most 5 difficulty-adjusted mobs near a player.
        if (cfg.maxScaledMobsPerPlayer < 1 || cfg.maxScaledMobsPerPlayer > 5) {
            if (cfg.nearbyScaleBudgetPerPlayer >= 1 && cfg.nearbyScaleBudgetPerPlayer <= 5) {
                cfg.maxScaledMobsPerPlayer = cfg.nearbyScaleBudgetPerPlayer;
            } else {
                cfg.maxScaledMobsPerPlayer = 5;
            }
        }
        cfg.maxScaledMobsPerPlayer = Math.max(1, Math.min(5, cfg.maxScaledMobsPerPlayer));
        cfg.endKiMaxAliveWhileDragon = Math.max(1, Math.min(512, cfg.endKiMaxAliveWhileDragon));
        cfg.endDragonSummonNetheriteCost = Math.max(1, Math.min(64, cfg.endDragonSummonNetheriteCost));
        cfg.witherSummonNetheriteCost = Math.max(1, Math.min(64, cfg.witherSummonNetheriteCost));
        cfg.wardenSummonNetheriteCost = Math.max(1, Math.min(64, cfg.wardenSummonNetheriteCost));
        cfg.summonLootBonusPerTierPercent = Math.max(0, Math.min(100, cfg.summonLootBonusPerTierPercent));
        cfg.adaptiveAiPackCallMaxAllies = Math.max(0, Math.min(10, cfg.adaptiveAiPackCallMaxAllies));
        cfg.adaptiveAiPackCallOffTier = com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier
                .parse(cfg.adaptiveAiPackCallOffTier, com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier.MYTHIC).name();
        cfg.adaptiveAiKitFighterTier = com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier
                .parse(cfg.adaptiveAiKitFighterTier, com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier.MYTHIC).name();
        cfg.dodgeCounterTier = com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier
                .parse(cfg.dodgeCounterTier, com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier.MASTER).name();
        cfg.lmTipIntervalSeconds = Math.max(60, Math.min(7200, cfg.lmTipIntervalSeconds));
        cfg.lmTipNewPlayerIntervalSeconds = Math.max(45, Math.min(3600, cfg.lmTipNewPlayerIntervalSeconds));
        cfg.lmTipLoginDelaySeconds = Math.max(15, Math.min(600, cfg.lmTipLoginDelaySeconds));
        cfg.lmTipNewPlayerMaxLevel = Math.max(1, Math.min(150_000, cfg.lmTipNewPlayerMaxLevel));
        // Natural End dragons stay off. Difficulty summon is restored once, then staff can toggle it.
        cfg.enableEndNaturalDragonSpawn = false;
        if (!Boolean.TRUE.equals(cfg.endPlayerDragonSummonRestoredV1)) {
            cfg.enableEndPlayerDragonSummon = true;
            cfg.endPlayerDragonSummonRestoredV1 = Boolean.TRUE;
        }
        if (cfg.meditationDetectionRadius < 1) {
            cfg.meditationDetectionRadius = 16;
        }
        cfg.meditationDetectionRadius = Math.min(128, cfg.meditationDetectionRadius);
        int[] meditationDefaults = {60, 300, 900, 1800, 3600, 7200, 14400, 21600, 28800, 36000};
        if (cfg.meditationLevelSeconds == null || cfg.meditationLevelSeconds.length == 0) {
            cfg.meditationLevelSeconds = meditationDefaults.clone();
        } else {
            int[] next = new int[meditationDefaults.length];
            for (int i = 0; i < next.length; i++) {
                int value = i < cfg.meditationLevelSeconds.length
                        ? cfg.meditationLevelSeconds[i]
                        : meditationDefaults[i];
                next[i] = Math.max(1, value);
            }
            cfg.meditationLevelSeconds = next;
        }
        cfg.nearbyScaleBudgetPerPlayer = cfg.maxScaledMobsPerPlayer;
        // Never allow free tiers via live admin set / bad JSON.
        cfg.unlockTier1Cost = Math.max(1L, cfg.unlockTier1Cost);
        cfg.unlockTier2Cost = Math.max(1L, cfg.unlockTier2Cost);
        cfg.unlockTier3Cost = Math.max(1L, cfg.unlockTier3Cost);
        cfg.unlockTier4Cost = Math.max(1L, cfg.unlockTier4Cost);
        cfg.unlockTier5Cost = Math.max(1L, cfg.unlockTier5Cost);
        cfg.unlockTier6Cost = Math.max(1L, cfg.unlockTier6Cost);
        cfg.unlockTier7Cost = Math.max(1L, cfg.unlockTier7Cost);
        // Level gates floor at 1 — 0 would unlock every tier for every player.
        cfg.unlockTier1Level = Math.max(1L, cfg.unlockTier1Level);
        cfg.unlockTier2Level = Math.max(1L, cfg.unlockTier2Level);
        cfg.unlockTier3Level = Math.max(1L, cfg.unlockTier3Level);
        cfg.unlockTier4Level = Math.max(1L, cfg.unlockTier4Level);
        cfg.unlockTier5Level = Math.max(1L, cfg.unlockTier5Level);
        cfg.unlockTier6Level = Math.max(1L, cfg.unlockTier6Level);
        cfg.unlockTier7Level = Math.max(1L, cfg.unlockTier7Level);
        if (cfg.adaptiveAiMinUnlockTier < 1) {
            cfg.adaptiveAiMinUnlockTier = 1;
        }
        if (cfg.enemyEvolutionMinUnlockTier < 1) {
            cfg.enemyEvolutionMinUnlockTier = 1;
        }
        // One-time migrate old CR enemy-mult ladder (1.0/1.15/…/3.0) → player-stat percents.
        if (!Boolean.TRUE.equals(cfg.crEnemyMultPercentMigratedV1)) {
            if (nearly(cfg.unlockTier1EnemyMult, 1.0)
                    && nearly(cfg.unlockTier2EnemyMult, 1.15)
                    && nearly(cfg.unlockTier7EnemyMult, 3.0)) {
                cfg.unlockTier1EnemyMult = 0.21;
                cfg.unlockTier2EnemyMult = 0.42;
                cfg.unlockTier3EnemyMult = 0.65;
                cfg.unlockTier4EnemyMult = 0.90;
                cfg.unlockTier5EnemyMult = 1.35;
                cfg.unlockTier6EnemyMult = 1.60;
                cfg.unlockTier7EnemyMult = 2.00;
            }
            cfg.crEnemyMultPercentMigratedV1 = Boolean.TRUE;
        }
        cfg.unlockTier1EnemyMult = clampPercent(cfg.unlockTier1EnemyMult, 0.21);
        cfg.unlockTier2EnemyMult = clampPercent(cfg.unlockTier2EnemyMult, 0.42);
        cfg.unlockTier3EnemyMult = clampPercent(cfg.unlockTier3EnemyMult, 0.65);
        cfg.unlockTier4EnemyMult = clampPercent(cfg.unlockTier4EnemyMult, 0.90);
        cfg.unlockTier5EnemyMult = clampPercent(cfg.unlockTier5EnemyMult, 1.35);
        cfg.unlockTier6EnemyMult = clampPercent(cfg.unlockTier6EnemyMult, 1.60);
        cfg.unlockTier7EnemyMult = clampPercent(cfg.unlockTier7EnemyMult, 2.00);
    }

    public long paintEpoch() {
        return paintEpoch;
    }

    private static boolean nearly(double value, double expected) {
        return Math.abs(value - expected) < 1.0e-9;
    }

    public static void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(INSTANCE, writer);
            }
            com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier.invalidateThresholdCache();
            com.dbzlegacy.adaptivedifficulty.calc.ScalingCurves.invalidateLut();
            com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty.clearCache();
        } catch (IOException e) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] failed to save config: {}", AdaptiveDifficultyMod.MOD_ID, e.toString());
        }
    }

    /** @return false when config JSON failed to parse (previous live values kept). */
    public static boolean reload() {
        boolean ok = load();
        try {
            com.dbzlegacy.adaptivedifficulty.progression.race.RaceLockConfig.reload();
            com.dbzlegacy.adaptivedifficulty.progression.classdef.FightingClassCatalog.reload();
            com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig.load();
            com.dbzlegacy.adaptivedifficulty.quest.SagaResetConfig.load();
            com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneCatalog.invalidate();
        } catch (Throwable ignored) {
        }
        com.dbzlegacy.adaptivedifficulty.tier.DifficultyTier.invalidateThresholdCache();
        com.dbzlegacy.adaptivedifficulty.calc.ScalingCurves.invalidateLut();
        com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty.clearCache();
        invalidateCombatPaintCaches();
        return ok;
    }

    /** Re-run clamps on the live instance (Bukkit admin set / hot edits). */
    public static void sanitizeLive() {
        if (INSTANCE != null) {
            normalize(INSTANCE);
        }
        invalidateCombatPaintCaches();
    }

    /** Drop profile / applied-paint caches so config retunes re-scale claimed mobs. */
    public static void invalidateCombatPaintCaches() {
        if (INSTANCE != null) {
            INSTANCE.paintEpoch++;
        }
        com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile.clearAll();
        // Do NOT clear base-form level samples here — that made transformed players look
        // like level 1 and revoked paid unlock tiers on every admin reload/set.
        com.dbzlegacy.adaptivedifficulty.scaling.MobScaling.clearAppliedProfiles();
    }
}
