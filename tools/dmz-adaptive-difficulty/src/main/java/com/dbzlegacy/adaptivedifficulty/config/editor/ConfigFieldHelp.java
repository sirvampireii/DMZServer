package com.dbzlegacy.adaptivedifficulty.config.editor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Staff-facing names and one-line descriptions for config fields.
 * Raw ids such as {@code enableEndDimensionStrength} never appear in the menu.
 */
public final class ConfigFieldHelp {
    private static final Map<String, String> LINES = new HashMap<>();

    private static final String BODY = """
balanceTelemetryEnabled=Logs mob hits on difficulty players to a daily file so staff can tune balance.
staffFreeAncientCoinCosts=When on, staff pay no Ancient Coins for paid LegacyMechanics features.
balanceTelemetryMaxPerSecond=Max telemetry hit lines per player per second (spam guard).
enableSystemTelemetry=Writes a daily log of difficulty, rival, and sparring events.
systemTelemetryMaxPerSecond=Max system telemetry lines per player per second.
teamEliteChanceBonusPercent=Extra elite roll % per weighted online rival using teams (added to #eliteChancePercent).
teamMutationChanceBonusPercent=Extra mutation roll % per weighted online rival.
teamBossThresholdBonusPercent=Boss HP threshold reduction % per weighted rival (more mobs qualify as bosses).
teamBossPromotionChancePercent=Flat boss promotion roll % per weighted rival on T6+ claims (non-natural).
teamThresholdRarityMult=Threshold team mode uses this fraction of team rarity bonuses (Full = 1.0).
combatCurvePivot=Difficulty where the offense curve matches old linear rates.
healthCurveExponent=Health curve exponent - kept low so mob HP does not become unkillable sponges.
healthCurvePivot=Pivot for the health curve (usually same ballpark as combat pivot).
healthPercentPerDifficulty=Flat health % rate (applied through the flat health curve).
dmzExtraHealthPercent=DMZ-style extra health % (kept low; health uses its own flat curve).
areaDifficultyMode=Area difficulty mode (Scaling Health-inspired): weighted (default), average, or max.
areaGroupBonusPercent=Extra area difficulty percent per additional nearby player (Scaling Health group bonus).
areaDifficultyVariancePercent=Random variance applied to area difficulty when scaling a mob (percent, e.g. 5.0 shuffles each mob by up to 5%).
referenceMaxLevel=Reference caps used to document the top tier (Zenith).
tierZenith=Top internal ability ladder threshold (not a player Unlock Tier).
applyDmzExtrasToAllHostiles=Apply DMZ extra health/defense/damage/ki percents to all hostiles (not only dragonminez: mobs).
eliteChancePercent=Percent chance a claimed T4+ hostile becomes elite (rare).
rarityChanceMigrated=One-time: stock 3%/5% chances felt common after the claim-seed +15% elite bug.
rarityChanceMigratedV2=One-time flag that lowered old elite and mutation chances. Leave it alone.
rarityChanceMigratedV3=One-time: stock rarity x3 (0.75%/1.25% to 2.25%/3.75%).
enableRivalSystem=Rival System 4.7.10 port (declare / proximity / challenges / instinct).
enableSparringSystem=Sparring Tp System 3.2.11 port (auto sessions / mentor / TP formula).
enableCnpcDataMigration=One-time CustomNPCs storeddata import to LM JSON/NBT on first boot or first login, then those CNPC keys are cleared.
enableProgression=Master switch for LegacyMechanics progression modules.
enableLivingWorldMeditation=Meditating players gain Meditation while another meditating player or two meditating NPCs are nearby.
meditationDetectionRadius=Blocks from another meditating player or a meditating living NPC.
meditationLevelSeconds=Seconds of nearby time required to reach each Meditation level.
enableEndPortalGuard=Block vanilla End portal / gateway travel and Eye-of-Ender frame lighting.
enableEndPlayerDragonSummon=Difficulty GUI paid End Dragon summon (T4-T7, Ancient Netherite cost).
endPlayerDragonSummonRestoredV1=One-time: 4.5.154 forced the Difficulty summon off.
endDragonSummonNetheriteCost=Ancient Netherite coins charged for a player GUI End Dragon summon.
enableEndMobScaling=Scale Enderman / Shulker / Phantom / Endermite HP/DEF (End Dimension Strength).
endEnforceSingleDragon=Cap living End dragons: at most one player-summoned dragon per summoner, and strip unauthorized vanilla dragons.
endKiCleanupEnabled=Purge / cap leftover DMZ ki_laser / ki_blast in The End (v2.12.0).
endKiPurgeWhenNoDragon=When no dragon is alive, purge all End ki projectiles.
endKiMaxAliveWhileDragon=Hard cap on End ki projectiles while a dragon is alive (script default 32).
enableOverhaulPrestigeIntegration=Use dmzrevamp Statistics prestige UI + native scaling/rebirth.
enableSkillCheck=Donator Skill Check UI (/skillcheck, CNPC interact / trigger 21).
skillCheckPermission=LuckPerms node for donator Skill Check (default false in plugin.yml).
skillCheckNpcNameContains=CNPC display-name needles (case-insensitive substring) that open Skill Check on interact.
enablePlayerStatChecker=Sneak + right-click another player to dump DMZ stats (PlayerStatChecker.js).
enableLmTips=Periodic on-screen Legacy Mechanics tips (/lm hub reminders).
lmTipIntervalSeconds=Seconds between tips for higher ranks (default 10 minutes).
lmTipNewPlayerIntervalSeconds=Seconds between tips for LuckPerms primary groups in #lmTipFrequentGroups (default / student / novice).
lmTipLoginDelaySeconds=Seconds after login before the first tip.
lmTipFrequentGroups=LuckPerms primary groups that get the frequent tip cadence.
lmTipNewPlayerMaxLevel=Fallback when LuckPerms is unavailable: DMZ level below this uses frequent cadence.
bossHealthThreshold=Natural (pre-scale) max-health at/above this marks a boss.
maxHealthMultiplier=Optional ceiling on the health multiplier from the health curve.
maxArmorBonus=Optional armor-point ceiling from defense scaling.
adminPermission=Admin permission node (Forge PermissionAPI / LuckPerms).
enableStaffCnpcTestGui=Staff /lm admin testgui - opens the Ultra hub (experimental flag).
restoreVanillaDifficultyFromPeaceful=If the Minecraft world is on Peaceful (no hostile spawns), restore it on server start.
vanillaDifficulty=Target vanilla difficulty when restoring from Peaceful: easy / normal / hard.
disabledDimensions=Dimensions where adaptive difficulty skips scaling / AI / evolution / gravity.
endScalingEnabledMigrated=One-time: strip legacy default minecraft:the_end from #disabledDimensions so existing configs pick up End scaling.
tierCostLevelDivisor=Legacy excess-above-unlock divisor (pre-2.3.61).
tierCostLevelAnchor=DMZ level where stock T7 hits #tierCostT7TargetCopper.
tierCostT7TargetCopper=Target copper-value for T7 at #tierCostLevelAnchor.
unlockTier1EnemyMult=Nearby-mob scale vs the player's post-transform / limit-release stats.
mobHealthScale=Multiplier on final scaled mob max health (the player's max health, times the tier percent, times this).
maxFormBoost=Ceiling on detected form+stack multipliers (default 100).
maxLiveCombatChannel=Soft ceiling on a single live STR/SKP/VIT/RES channel fed into blend math (default 5.0E7).
transformScaleWeight=How much of a form/transform boost enemies scale to (0-1), after the soft curve.
transformScaleExponent=Diminishing-returns exponent on form surplus ((live/base - 1)^exp x weight).
weakStatCounterMult=Legacy weak-stat dump multiplier (unused in combat as of 3.3.35).
weakDefensePierceMult=Legacy pierce knob (unused in combat as of 3.3.35).
tankDamageDefenseRatio=Live damage floor from player soft RES x tier % - keeps pure RES dumps from facing wet-noodle hits.
tankDamageHealthRatio=Damage floor from a tank's health so stacking vitality still has a cost.
lowTierPressureMigrated=One-time: raise soft T1-T3 percents and tank pierce so early tiers can challenge high-defense, low-level players.
tierPercentLadderMigrated=One-time: raise mid/high tier stock percents T4 80 to 90 - T5 110 to 125 - T6 130 to 165 (T1-T3/T7 already match the ladder).
tierPercentLadderMigratedV2=One-time: stock ladder 28/42/65/90/125/165/200 to 13/28/42/58/76/90/116.
tierPercentLadderMigratedV3=One-time: stock ladder 13/28/42/58/76/90/116 to 10/20/30/40/50/65/90.
tierPercentLadderMigratedV4=One-time (1.0.1): soft stock 10/20/30/40/50/65/90 (+ transform 0.25/0.50) to balanced ladder 21/42/65/90/135/160/200.
crEnemyMultPercentMigratedV1=One-time: remap legacy CR enemy-mult ladder (1.0/1.15/.../3.0) to percent ladder.
legacyMaxHealthCapMigratedV1=One-time: drop legacy absolute HP caps (400 / 1024) to uncapped (0).
referenceMaxLevel150kMigratedV1=One-time: raise the stale live reference level from 100000 to 150000 so breakthroughs to 150k are not clamped by the old cap.
counterSimplifyMigratedV1=One-time: drop the race, weak-stat, and specialization stack and soften class and top-1 counter stock values.
lowTierCounterSoftMigratedV1=One-time: soften stock pierce and counter overlays so early 10-20% tiers are not secretly full defense pierce.
balanceFeelMigratedV1=One-time: balance pass - raise mob HP sponge, soften elite spike, nudge tank HP floor.
challengeFeelMigratedV1=One-time (1.0.12): raise mob sponge and restore live VIT/RES damage floors so even and dump builds feel balanced.
skillChallengeMigratedV1=One-time (1.0.13): raise the stock mob health scale from 0.90 to 1.05 so Ki Infusion and mega forms still have a real fight.
godFormPressureMigratedV1=One-time (1.0.14): god-form pressure - sponge 1.05 to 1.15 + transform weight 0.55 to 0.65.
vitDumpPressureMigratedV1=One-time (1.0.15): raise the stock tank-to-damage health ratio from 0.10 to 0.22 so high-VIT builds track the health floor.
telemetryFeelMigratedV1=One-time (1.0.19): raise the stock tank-to-damage health ratio from 0.22 to 0.28 so T3 to T6 tanks keep climbing on live data.
mobHpTrimMigratedV1=One-time (1.0.29): move the stock mob health scale from 1.15 to 0.92 (mobs stop soaking quite so much damage).
mobHpTrimMigratedV2=One-time (1.0.30): move the stock mob health scale from 0.92 (or a leftover 1.15) to 0.75.
transformSoftMigratedV1=One-time: soften transform to enemy scaling (weight 0.40 to 0.25 + soft curve).
starterCopperCostMigratedV1=One-time: starter economy - T1 at level 1 costs 1x Copper (was 100 = 1x Gold).
tierCostDivisorMigratedV1=One-time (1.0.26): raise the stock tier cost divisor from 1000 to 50000 so T7 at unlock stays near base cost.
tierCostCurveMigratedV2=One-time (2.3.61): absolute-level curve - L1/T1 = 1x Copper, L150k/T7 = 100x Netherite.
coinDropChanceMigratedV1=One-time (1.0.45): stock coin drop 100% to 5%, upgrade duo 2% to 0.5%.
specializationDamageTax=Legacy specialization tax (unused in combat as of 3.3.35).
enableClassCounters=When true, mobs also counter the player's DMZ fighting class (warrior, spiritualist, tank, ...).
enableStrongStatCounters=When true, mobs also counter the player's single highest invested combat stat (STR, SKP, RES, VIT, PWR, ENE).
strongStatCounterMult=Strength of top-stat counter overlays (1.0 = off effect, higher = harder).
classCounterDamageMult=Extra mob damage vs the player's fighting style (class overlay).
raceCounterMult=Legacy race overlay (unused in combat as of 3.3.35).
maxCounterOverlayMult=Cap on multiplicative counter overlays after pierce floors (class x top-stat only).
defenseToArmorFactor=Converts DMZ defense share into vanilla armor points: log1p(def) x factor.
nearbyScaleIntervalTicks=Server ticks between nearby-player mob rescale pulses (per-player stagger).
maxScaledMobsPerPlayer=Max difficulty-adjusted hostiles near one player at a time (closest win).
ancientCoinDropChance=Chance (0-1) on each eligible kill to drop any Ancient Coin(s).
ancientCoinUpgradeChance=Chance (0-1) on each eligible kill to drop the normal coin and one next-higher denomination.
deathResetsActiveDifficulty=V3: reset active tier/level on player death (unlocks stay).
eliteMinUnlockTier=Minimum active unlock-tier for elites / mutations / full AI.
adaptiveAiMinUnlockTier=Soft AI starts at Unlock Tier 1; kit depth is capped by com.dbzlegacy.adaptivedifficulty.tier.UnlockAbilityCaps.
restrictions.blockWhileTransformed=When true, legacy strict mode (unused - services auto-detransform instead).
reskin.lockClassDuringEditor=DMZ recustomize UI includes a class tab - server locks class while reskin session is active.
headBoneShop.nativeRaceBonesFree=Parts listed on the player's current race config do not require a shop unlock.
headBoneShop.persistThroughForms=Re-apply CharacterServicesStore.PlayerRecord#equippedHeadBone after transformations.
perLevelCost=Added to the base once per Dragon Mine Z level (PlayerDifficultyData#getHighestDmzLevel()).
defaultBaseCost=Base used when #baseCosts has no entry for that saga id.
weaponDamage.defaultMultiplier=1.0 leaves the weapon alone. 0.5 is half, 2.0 is double, 0 zeroes the scaling figure.
enabled=Turns the whole difficulty system on or off. Off stops scaling, rewards, AI, and purchases.
whitelistEnabled=When on, only players on the whitelist use difficulty scaling.
whitelist=Player names or UUIDs allowed to use difficulty while the whitelist is on.
prestigeMultiplier=Each prestige rank multiplies that player's difficulty by this amount.
levelMultiplier=How much each DMZ level adds when difficulty is calculated.
teamBonusPercent=Extra difficulty percent added for each weighted teammate.
contributionPercent=Share of a player's stats that count toward area difficulty.
movementPercentPer100Difficulty=Move-speed percent added for every 100 difficulty.
mobScaleRadius=Blocks around a player where mobs pick up that player's difficulty.
referenceMaxPrestige=Prestige rank used when documenting the top ability tier.
scaleHostileOnly=Only hostile mobs receive difficulty health, damage, and defense.
enableRewardScaling=Scales kill XP and reward drops with the player's difficulty.
enableMobScaling=Applies difficulty health, damage, and defense to mobs.
enableElites=Lets claimed high-tier hostiles roll as elites.
eliteStatMultiplier=Stat multiplier applied to a mob that rolled elite.
enableMutations=Lets claimed high-tier hostiles roll a mutation.
enableAdaptiveAi=Lets tough mobs change how they fight as difficulty rises.
enableEnemyEvolution=Lets enemies grow stronger after repeated fights nearby.
enableBossScaling=Applies extra health and damage scaling to boss mobs.
enablePotential=Master switch for the Potential progression module.
enableGlobalTpBoost=Master switch for the global training-points boost.
enableBioAndroid=Master switch for Bio-Android progression.
enableRaceLock=Master switch for race-lock rules.
enableYardrat=Master switch for Yardrat progression.
enableSpiritualistKi=Master switch for Spiritualist ki.
enableAndroidConversion=Master switch for Android conversion.
rivalPresenceTp=Nearby rivals grant a training-points bonus.
rivalInstinct=Lets rivals sense each other.
rivalChallenges=Lets rivals send and accept challenges.
enablePiercingBonus=Adds the piercing bonus on top of weapon damage.
enableDotExtraDamage=Adds extra damage from damage-over-time effects.
enableApothicElemental=Applies Apothic elemental bonuses in combat.
enableEndDimensionStrength=Scales the End dragon and End fights with difficulty.
enableEndNaturalDragonSpawn=Allows the vanilla End dragon to spawn on its own.
enableShadowDummyLimiter=Caps how many shadow training dummies can exist.
enableSkillUnlockService=Master switch for the skill unlock service.
enablePrestigeSystem=Master switch for LegacyMechanics prestige.
bossStatMultiplier=Extra stat multiplier applied on top of boss scaling.
bossIdContains=Mob ids containing these words are treated as bosses.
maxMoveMultiplier=Ceiling on the movement-speed multiplier from scaling.
combatRatingDmzWeight=How much DMZ level counts toward combat rating.
combatRatingPrestigeWeight=How much prestige counts toward combat rating.
combatRatingTransformWeight=How much transformation power counts toward combat rating.
combatRatingDifficultyWeight=How much difficulty counts toward combat rating.
nearbyScaleBudgetPerPlayer=How many nearby mobs one player may scale on each pass.
enableAncientCoinDrops=Mobs can drop Ancient Coins.
ancientCoinDropMult=Multiplier on how many Ancient Coins a mob drops.
ancientCoinRatingDivisor=Combat rating divided by this sets the Ancient Coin drop size.
mutationMinUnlockTier=Lowest unlock tier that can roll mutations.
enemyEvolutionMinUnlockTier=Lowest unlock tier where enemies can evolve.
bossMechanicsMinUnlockTier=Lowest unlock tier where boss mechanics turn on.
rewardScaling=Divisor for kill XP. Higher means XP grows more slowly with difficulty.
combatCurveExponent=How steeply mob damage and defense grow with difficulty.
damagePercentPerDifficulty=Damage percent added for each point of difficulty.
defensePercentPerDifficulty=Armor points added for each point of difficulty.
dmzExtraDamagePercent=Extra damage percent applied the DMZ way, through the offense curve.
dmzExtraDefensePercent=Extra armor points applied the DMZ way, through the offense curve.
dmzExtraKiDamagePercent=Extra ki damage percent applied the DMZ way, through the offense curve.
hardCapDifficulty=Highest difficulty a player can reach. 0 means no hard cap.
mutationChancePercent=Percent chance a claimed high-tier hostile mutates.
enableKiWeapons=Master switch for ki weapon damage in combat.
maxScaledHealth=Highest HP a mob can have after scaling. 0 means no cap.
maxDamageMultiplier=Ceiling on the damage multiplier from scaling. 0 means no cap.
guiBackend=Which menu backend players see for /lm. Ultra is the in-game GUI.
unlockTier1Cost=Ancient Coin cost to buy unlock tier 1, in copper.
classCounterHealthMult=Extra mob HP against glass, caster, and ki classes.
classCounterArmorMult=Extra mob armor against melee and strike classes.
permissions=LuckPerms nodes that gate each character service.
restrictions=Situations where character services refuse to run.
raceChange=Cost, cooldown, and rules for changing race.
classChange=Cost, cooldown, and rules for changing class.
reskin=Cost, cooldown, and rules for a cosmetic reskin.
headBoneShop=Prices and rules for the head-bone cosmetic shop.
weaponDamage=Per-weapon damage overrides and the base damage table.
weaponBonus=Extra damage granted by unique and third-stat weapons.
healing=Caps for lifesteal, overheal, and healing received.
pothala=How often the Pothala earring effect is checked.
spawners=Delay, count, and range used when spawners place mobs.
capsules=How often leftover capsules are purged.
heartstop=Heartstop spell id and how often it is applied.
silkTouch=How often silk-touch breakage is scanned.
silky=How often silky cloth and tool effects run.
raceLocks=How often race-lock data is synced to players.
saga.enabled=Turns paid saga reset on or off.
character-services|enabled=Turns character services (race, class, reskin, head bones) on or off.
saga|enabled=Turns paid saga reset on or off.
""";

    static {
        for (String row : BODY.split("\n")) {
            int eq = row.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            LINES.put(row.substring(0, eq), row.substring(eq + 1).trim());
        }
    }

    private ConfigFieldHelp() {}

    /** One sentence that says what this setting does. Never the raw field id. */
    public static String describe(String moduleId, String path) {
        String full = path == null ? "" : path.trim();
        String module = moduleId == null ? "" : moduleId.trim();
        if (!module.isEmpty()) {
            String keyed = LINES.get(module + "|" + full);
            if (keyed != null && !keyed.isBlank()) {
                return keyed;
            }
        }
        String direct = LINES.get(full);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String leaf = leaf(full);
        if (!full.contains(".") && !full.contains("[")) {
            String byLeaf = LINES.get(leaf);
            if (byLeaf != null && !byLeaf.isBlank()) {
                return byLeaf;
            }
        }
        String patterned = pattern(full);
        if (patterned != null && !patterned.isBlank()) {
            return patterned;
        }
        String label = label(leaf);
        String parent = parentLabel(full);
        if (!parent.isEmpty()) {
            return parent + ": " + label + ".";
        }
        return "Controls " + label + ".";
    }

    private static String pattern(String path) {
        String leaf = leaf(path);
        String lower = leaf.toLowerCase(Locale.ROOT);
        String parent = parentLabel(path);
        String name = label(leaf);
        if (lower.contains("migrated")) {
            return "One-time flag from an old config update. Leave it alone.";
        }
        if (lower.matches("unlocktier[1-7]level")) {
            return "DMZ level required before a player can buy unlock tier " + digit(lower) + ".";
        }
        if (lower.matches("unlocktier[1-7]max")) {
            return "Playable stat cap while the player is on unlock tier " + digit(lower) + ".";
        }
        if (lower.matches("unlocktier[1-7]cost")) {
            return "Ancient Coin cost to buy unlock tier " + digit(lower) + ", in copper.";
        }
        if (lower.matches("unlocktier[1-7]enemymult")) {
            return "Enemy stat multiplier at unlock tier " + digit(lower) + ".";
        }
        String ability = abilityTier(leaf);
        if (ability != null) {
            return "Difficulty where the " + ability + " ability tier starts.";
        }
        if ("enabled".equals(lower) && !parent.isEmpty()) {
            return "Turns " + parent + " on or off.";
        }
        if ("cooldownms".equals(lower)) {
            return "Wait after using " + orLabel(parent, name) + " before it can be used again, in milliseconds.";
        }
        if (lower.endsWith("interval") || lower.endsWith("ticks") || lower.endsWith("seconds")) {
            String unit = lower.endsWith("seconds") ? "seconds" : "ticks (20 ticks is one second)";
            return "How often " + orLabel(parent, name) + " runs, in " + unit + ".";
        }
        if (lower.contains("copper") || lower.endsWith("cost")) {
            return "Ancient Coin price for " + orLabel(parent, name) + ", counted in copper.";
        }
        if (path.startsWith("permissions.")) {
            return "LuckPerms node that grants " + name + ".";
        }
        if (path.startsWith("restrictions.")) {
            return "When on, character services refuse to run in this situation.";
        }
        if (lower.contains("chance")) {
            return "How often " + orLabel(parent, name) + " happens. Higher means more often.";
        }
        if (lower.contains("multiplier") || lower.endsWith("mult") || lower.contains("percent")) {
            return "Number that scales " + orLabel(parent, name) + ".";
        }
        if (lower.startsWith("enable")) {
            return "Turns " + name + " on or off for the server.";
        }
        return null;
    }

    private static String abilityTier(String leaf) {
        return switch (leaf) {
            case "tierAwakened" -> "Awakened";
            case "tierEnhanced" -> "Enhanced";
            case "tierElite" -> "Elite";
            case "tierAdvanced" -> "Advanced";
            case "tierMaster" -> "Master";
            case "tierLegendary" -> "Legendary";
            case "tierGod" -> "God";
            case "tierDivine" -> "Divine";
            case "tierImpossible" -> "Impossible";
            case "tierTranscendent" -> "Transcendent";
            case "tierEternal" -> "Eternal";
            case "tierMythic" -> "Mythic";
            case "tierOmega" -> "Omega";
            case "tierAbsolute" -> "Absolute";
            case "tierApex" -> "Apex";
            case "tierZenith" -> "Zenith";
            default -> null;
        };
    }

    private static String digit(String lower) {
        for (int i = 0; i < lower.length(); i++) {
            if (Character.isDigit(lower.charAt(i))) {
                return String.valueOf(lower.charAt(i));
            }
        }
        return "?";
    }

    private static String orLabel(String parent, String name) {
        return parent == null || parent.isEmpty() ? name : parent;
    }

    /** Human label. Strips a leading Enable and a trailing unit suffix. */
    public static String label(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String leaf = leaf(raw);
        if (leaf.indexOf(' ') >= 0) {
            return polish(leaf);
        }
        String spaced = leaf.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ');
        if (spaced.isEmpty()) {
            return "";
        }
        String titled = Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
        if (titled.regionMatches(true, 0, "Enable ", 0, 7) && titled.length() > 7) {
            titled = titled.substring(7);
        }
        if (titled.endsWith(" Ms")) {
            titled = titled.substring(0, titled.length() - 3);
        }
        if (titled.endsWith(" Copper")) {
            titled = titled.substring(0, titled.length() - 7);
        }
        return polish(titled);
    }

    private static String polish(String name) {
        String text = name;
        if (text.endsWith(" Ai")) {
            text = text.substring(0, text.length() - 3) + " AI";
        }
        text = text.replace(" Ai ", " AI ");
        text = text.replace("Cnpc", "CNPC").replace("Dmz", "DMZ").replace(" Tp", " TP");
        if (text.endsWith(" Tp")) {
            text = text.substring(0, text.length() - 3) + " TP";
        }
        return text;
    }

    static String parentLabel(String path) {
        String parent = parent(path);
        if (parent.isEmpty()) {
            return "";
        }
        return label(parent);
    }

    private static String leaf(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int bracket = path.lastIndexOf('[');
        if (path.endsWith("]") && bracket >= 0) {
            return path.substring(bracket + 1, path.length() - 1);
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    private static String parent(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (path.endsWith("]")) {
            int bracket = path.lastIndexOf('[');
            return bracket <= 0 ? "" : path.substring(0, bracket);
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(0, dot);
    }
}
