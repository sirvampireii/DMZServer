package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.noea.AbsorptionWipeHelper;
import com.dbzlegacy.adaptivedifficulty.noea.GodKiWipeHelper;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.progression.race.RaceLock;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.progression.classdef.FightingClassCatalog;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.OpenRecustomizeS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Stats;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.skills.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/** Paid race / class / reskin services integrated with DragonMineZ + Ancient Coins. */
public final class CharacterServicesSystem {
    private CharacterServicesSystem() {}

    public static record TransferableStats(
            int strength,
            int strike,
            int resistance,
            int vitality,
            int kiPower,
            int energy
    ) {
        TransferableStats scaled(int preservationPercent) {
            int pct = Math.max(0, Math.min(100, preservationPercent));
            return new TransferableStats(
                    scale(strength, pct),
                    scale(strike, pct),
                    scale(resistance, pct),
                    scale(vitality, pct),
                    scale(kiPower, pct),
                    scale(energy, pct)
            );
        }

        private static int scale(int v, int pct) {
            if (v <= 0 || pct <= 0) {
                return 0;
            }
            return Math.max(0, (int) Math.round(v * (pct / 100.0)));
        }
    }

    public static String blockReason(ServerPlayer player) {
        return blockReason(player, false);
    }

    /** @param cosmetic when true (reskin), skip transform lock — appearance-only */
    public static String blockReason(ServerPlayer player, boolean cosmetic) {
        CharacterServicesConfig cfg = CharacterServicesConfig.get();
        if (!cfg.enabled) {
            return "§cCharacter Services are turned off on this server.";
        }
        if (!CharacterServicesAccess.canUseServices(player)) {
            return "§cYou do not have permission for Character Services. Ask staff if you need access.";
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return "§cYour character is still loading. Wait a moment and try again.";
        }
        Status status = data.getStatus();
        if (cfg.restrictions.blockWhileDead && status != null && !status.isAlive()) {
            return "§cYou can't change your character while dead. Respawn first.";
        }
        if (cfg.restrictions.blockWhileSparring && inActiveSpar(player)) {
            return "§cYou can't change your character during a spar. Finish the fight first.";
        }
        return "";
    }

    private static boolean inActiveSpar(ServerPlayer player) {
        try {
            Class<?> spar = Class.forName("com.dbzlegacy.adaptivedifficulty.sparring.SparCombat");
            var m = spar.getMethod("isInActiveFight", ServerPlayer.class);
            Object r = m.invoke(null, player);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static List<String> raceCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        String current = DmzProgression.race(player);
        Set<String> blocked = CharacterServicesConfig.get().blockedRaceSet();
        try {
            for (String race : DmzContentDiscovery.discoverRaceIds()) {
                if (race == null || race.isBlank()) {
                    continue;
                }
                String id = race.trim().toLowerCase(Locale.ROOT);
                if (blocked.contains(id)) {
                    continue;
                }
                if (!CharacterServicesAccess.bypassRaceLock(player)) {
                    if (RaceLock.selectBlockReason(player, id) != null) {
                        continue;
                    }
                }
                String name = titleCase(id);
                boolean selected = id.equalsIgnoreCase(current);
                out.add(id + "\t" + name + "\t" + (selected ? "1" : "0"));
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] race list failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
        return out;
    }

    public static List<String> classCards(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        String race = DmzProgression.race(player);
        String current = DmzProgression.fightingClass(player);
        List<String> raceClasses = FightingClassCatalog.classIdsForRace(race);
        if (raceClasses.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] no DMZ classes for race {} in character services GUI",
                    AdaptiveDifficultyMod.MOD_ID,
                    race);
            return out;
        }
        for (String id : raceClasses) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String name = FightingClassLabels.display(id);
            if (name == null || name.isBlank() || "Not set".equals(name)) {
                name = FightingClassCatalog.skillNameFor(id);
            }
            if (name == null || name.isBlank()) {
                name = titleCase(id);
            }
            out.add(id + "\t" + name + "\t" + (id.equalsIgnoreCase(current) ? "1" : "0"));
        }
        return out;
    }

    public static List<String> statPreviewLines(ServerPlayer player, int preservationPercent) {
        List<String> lines = new ArrayList<>();
        TransferableStats now = captureStats(player);
        if (now == null) {
            lines.add("§7We could not read your stats yet - try again in a moment.");
            return lines;
        }
        TransferableStats kept = now.scaled(preservationPercent);
        lines.add("§8After change (at " + preservationPercent + "%):");
        lines.add(statLine("Strength", now.strength, kept.strength));
        lines.add(statLine("Strike", now.strike, kept.strike));
        lines.add(statLine("Defense", now.resistance, kept.resistance));
        lines.add(statLine("Vitality", now.vitality, kept.vitality));
        lines.add(statLine("Ki Power", now.kiPower, kept.kiPower));
        lines.add(statLine("Energy", now.energy, kept.energy));
        return lines;
    }

    public static long scaledCost(ServerPlayer player, long baseCopper, boolean useLevelMult) {
        if (baseCopper <= 0L) {
            return 0L;
        }
        if (!useLevelMult) {
            return baseCopper;
        }
        double mult = DifficultyConfig.get().tierCostLevelMultiplier(DmzProgression.tierScalingDmzLevel(player));
        return Math.max(1L, Math.round(baseCopper * mult));
    }

    public static long raceCost(ServerPlayer player, int preservationPercent) {
        long base = CharacterServicesConfig.get().raceCostCopper(preservationPercent);
        if (base <= 0L) {
            return 0L;
        }
        long base100 = Math.max(1L, CharacterServicesConfig.get().raceCostCopper(100));
        boolean useLevel = CharacterServicesConfig.get().raceChange.levelCostMultiplier;
        if (!useLevel) {
            // A steep configured ladder would otherwise flatten every high keep-% to the
            // 128x Netherite coin cap. Compress against the configured 100% price so the
            // charge scales with the selected percentage exactly as configured.
            return AncientCoinEconomy.normalizeCost(compressRaceScaledUnderCap(base, base100));
        }
        long scaled = scaledCost(player, base, true);
        // Shared tier level mult can be thousands× at high DMZ. normalizeCost then hard-caps
        // at 128× Netherite, so every keep-% collapses to the same charge. Compress against
        // the scaled 100% price so the preservation ladder stays distinct under the cap.
        long scaled100 = scaledCost(player, base100, true);
        scaled = compressRaceScaledUnderCap(scaled, scaled100);
        return AncientCoinEconomy.normalizeCost(scaled);
    }

    /**
     * When {@code scaled100} exceeds the payable Ancient Coin ceiling, scale {@code scaled}
     * by {@code cap / scaled100} so keep-% prices stay proportional. Package-visible for sim.
     */
    static long compressRaceScaledUnderCap(long scaled, long scaled100) {
        if (scaled <= 0L) {
            return 0L;
        }
        long cap = AncientCoinEconomy.maxNormalizedCost();
        if (scaled100 > cap && scaled100 > 0L) {
            return Math.max(1L, Math.round(scaled * ((double) cap / (double) scaled100)));
        }
        return scaled;
    }

    /**
     * Copper actually taken for a race change. The confirm scroll and {@link #executeRaceChange}
     * both use this for the same percent the button shows, including level scaling and the staff waiver.
     */
    public static long raceAmountDue(ServerPlayer player, int preservationPercent) {
        return amountDue(player, raceCost(player, preservationPercent));
    }

    public static long classAmountDue(ServerPlayer player) {
        return amountDue(player, classCost(player));
    }

    public static long reskinAmountDue(ServerPlayer player) {
        return amountDue(player, reskinCost(player));
    }

    /** Listed copper after the staff/bypass waiver. Zero means the charge call takes nothing. */
    public static long amountDue(ServerPlayer player, long listedCopper) {
        if (player != null && CharacterServicesAccess.bypassCost(player)) {
            return 0L;
        }
        return Math.max(0L, listedCopper);
    }

    public static long classCost(ServerPlayer player) {
        CharacterServicesConfig.ClassChange cc = CharacterServicesConfig.get().classChange;
        return payableCost(player, cc.baseCostCopper, cc.levelCostMultiplier);
    }

    public static long reskinCost(ServerPlayer player) {
        CharacterServicesConfig.Reskin rs = CharacterServicesConfig.get().reskin;
        return payableCost(player, rs.baseCostCopper, rs.levelCostMultiplier);
    }

    /** Explains level scaling + coin normalization so UI matches {@link #executeReskin} charges. */
    public static String reskinCostExplanation(ServerPlayer player) {
        CharacterServicesConfig.Reskin rs = CharacterServicesConfig.get().reskin;
        if (CharacterServicesAccess.bypassCost(player)) {
            return "§8No charge for your account";
        }
        if (!rs.levelCostMultiplier) {
            return "§8Fixed price for everyone";
        }
        long base = rs.baseCostCopper;
        long pay = reskinCost(player);
        int lvl = DmzProgression.tierScalingDmzLevel(player);
        if (pay <= base) {
            return "§8Price scales with level (§f" + lvl + "§8) - you are at the base price";
        }
        return "§8Price scales with level (§f" + lvl + "§8) - base §f"
                + formatCost(base) + " §8-> you pay §f" + formatCost(pay);
    }

    /** Level-scaled copper, snapped to payable Ancient Coin denominations (same as AD tier buys). */
    public static long payableCost(ServerPlayer player, long baseCopper, boolean useLevelMult) {
        return AncientCoinEconomy.normalizeCost(scaledCost(player, baseCopper, useLevelMult));
    }

    public static String formatCost(long copperCost) {
        return AncientCoinEconomy.formatExactCost(copperCost);
    }

    private static boolean chargeAc(ServerPlayer player, long cost) {
        if (cost <= 0L) {
            return true;
        }
        AncientCoinEconomy.migrateWalletToItems(player);
        return AncientCoinEconomy.charge(player, cost);
    }

    private static String insufficientFunds(ServerPlayer player, long cost) {
        return "§c" + AncientCoinEconomy.missingText(player, cost);
    }

    public static String cooldownLine(ServerPlayer player, String kind) {
        if (CharacterServicesAccess.bypassCooldown(player)) {
            return "§aReady whenever you are §8(no cooldown for your account)";
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        long now = System.currentTimeMillis();
        long last;
        long cd;
        switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
            case "race" -> {
                last = rec.lastRaceChangeAt;
                cd = CharacterServicesConfig.get().raceChange.cooldownMs;
            }
            case "class" -> {
                last = rec.lastClassChangeAt;
                cd = CharacterServicesConfig.get().classChange.cooldownMs;
            }
            case "reskin" -> {
                last = rec.lastReskinAt;
                cd = CharacterServicesConfig.get().reskin.cooldownMs;
            }
            default -> {
                return "§7-";
            }
        }
        long left = last + cd - now;
        if (left <= 0L) {
            return "§aReady to use";
        }
        return "§7On cooldown - ready in §f" + formatDuration(left);
    }

    private static String statLine(String label, int before, int after) {
        return "§7" + label + " §f" + format(before) + " §8-> §f" + format(after);
    }

    public static String executeRaceChange(ServerPlayer player, String targetRace, int preservationPercent) {
        return executeRaceChange(player, targetRace, preservationPercent, false);
    }

    /**
     * @param staffGrant staff fix: skip the player's race permission, race-lock,
     *                   cooldown, and Ancient Coin charge, and start no cooldown.
     *                   Feature switches and safety checks still apply.
     */
    public static String executeRaceChange(
            ServerPlayer player, String targetRace, int preservationPercent, boolean staffGrant
    ) {
        String block = blockReason(player);
        if (!block.isEmpty()) {
            return block;
        }
        if (!staffGrant && !CharacterServicesAccess.canRaceChange(player)) {
            return "§cYou do not have permission to change race. Ask staff if you need access.";
        }
        CharacterServicesConfig cfg = CharacterServicesConfig.get();
        if (!cfg.raceChange.enabled) {
            return "§cRace change is turned off on this server.";
        }
        if (!isValidPreservation(preservationPercent)) {
            return "§cPick how much of your stats to keep, then confirm again.";
        }
        String raceId = normalizeId(targetRace);
        if (raceId.isEmpty() || !isRaceAvailable(raceId)) {
            return "§cThat race is not available right now. Pick another from the list.";
        }
        String currentRace = DmzProgression.race(player);
        if (raceId.equalsIgnoreCase(currentRace)) {
            return "§cYou are already that race. Pick a different one.";
        }
        if (!staffGrant && !CharacterServicesAccess.bypassRaceLock(player)) {
            String lock = RaceLock.selectBlockReason(player, raceId);
            if (lock != null && !lock.isBlank()) {
                return lock;
            }
        }
        if (!staffGrant && !CharacterServicesAccess.bypassCooldown(player)) {
            long left = cooldownRemaining(player, "race");
            if (left > 0L) {
                return "§cRace change is on cooldown. Try again in §f" + formatDuration(left) + "§c.";
            }
        }
        releaseTransformation(player);
        long cost = staffGrant ? 0L : raceAmountDue(player, preservationPercent);
        AncientCoinEconomy.migrateWalletToItems(player);
        if (cost > 0L && !AncientCoinEconomy.canAfford(player, cost)) {
            return insufficientFunds(player, cost);
        }

        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return "§cYour character is still loading. Wait a moment and try again.";
        }
        boolean fullWipe = preservationPercent <= 0;
        TransferableStats before = null;
        TransferableStats target = null;
        if (!fullWipe) {
            before = captureStats(player);
            if (before == null) {
                return "§cCould not read your stats. Wait a moment and try again.";
            }
            target = before.scaled(preservationPercent);
        }

        if (cost > 0L && !chargeAc(player, cost)) {
            return insufficientFunds(player, cost);
        }

        try {
            Character ch = data.getCharacter();
            if (ch == null) {
                refund(player, cost);
                return "§cYour character is still loading. Wait a moment and try again.";
            }
            AbsorptionWipeHelper.wipe(player, "CharacterServicesSystem.executeRaceChange");
            GodKiWipeHelper.wipe(player, "CharacterServicesSystem.executeRaceChange");
            clearForms(ch, player);
            // Strip Zenkai_/Assimilation_/Absorption_ BonusStats splits earned on the old race.
            RacialBonusWipeHelper.wipeLeavingRace(
                    player,
                    data,
                    currentRace,
                    raceId,
                    "CharacterServicesSystem.executeRaceChange");
            String priorHeadBone = CosmeticHeadBoneService.activeBone(player);
            String priorClassBeforeRaceChange = "";
            boolean openedFreeClassPicker = false;
            if (fullWipe) {
                applyFullProgressWipe(player, data);
                RaceChangeCreationFlow.begin(player, raceId, priorHeadBone);
                RaceChangeCreationFlow.prepareCharacterData(player, data, raceId);
                RaceChangeCreationFlow.openEditor(player);
            } else {
                String priorClass = DmzProgression.fightingClass(player);
                if (priorClass == null || priorClass.isBlank()) {
                    try {
                        priorClass = ch.getCharacterClass();
                    } catch (Throwable ignored) {
                    }
                }
                priorClassBeforeRaceChange = priorClass == null ? "" : priorClass;
                boolean keepSkills = cfg.raceChange.keepSkillsOnRaceChange;
                List<RaceChangeSkillPreserve.Entry> skillSnapshot =
                        keepSkills ? RaceChangeSkillPreserve.capture(data.getSkills()) : List.of();
                if (RaceChangeClassMapper.requiresClassPicker(priorClassBeforeRaceChange, raceId)) {
                    float[] pickerResourceSnapshot = data.snapshotMultiplierResources();
                    RaceChangeClassMapper.commitFightingClassForRace(
                            data, raceId, "", priorClassBeforeRaceChange);
                    com.dbzlegacy.adaptivedifficulty.progression.race.AndroidConversion.stripIfRaceIneligible(
                            player, raceId);
                    try {
                        data.updateTransformationSkillLimits(raceId);
                    } catch (Throwable ignored) {
                    }
                    DmzClassCommandApply.pushStatsSync(player);
                    CosmeticHeadBoneService.reapplyHeadBoneAfterRaceChange(player, priorHeadBone);
                    if (target != null) {
                        applyStats(data.getStats(), target);
                    }
                    // Full class sync runs when the player finishes recustomize (packet mixin hook).
                    if (keepSkills) {
                        RaceChangeSkillPreserve.restore(data, currentRace, raceId, skillSnapshot);
                    }
                    RaceChangeClassPickFlow.begin(
                            player, raceId, priorClassBeforeRaceChange, pickerResourceSnapshot);
                    RaceChangeClassPickFlow.openRecustomizeEditor(player);
                    openedFreeClassPicker = true;
                } else {
                    float[] resourceSnapshot = data.snapshotMultiplierResources();
                    String mappedClass =
                            RaceChangeClassMapper.applyRaceAndFightingClass(
                                    player, data, raceId, priorClass);
                    if (mappedClass != null
                            && !mappedClass.isBlank()
                            && !priorClassBeforeRaceChange.isBlank()
                            && !mappedClass.equalsIgnoreCase(priorClassBeforeRaceChange)) {
                        AdaptiveDifficultyMod.LOGGER.info(
                                "[{}] race change remapped fighting class {} -> {} for race {}",
                                AdaptiveDifficultyMod.MOD_ID,
                                priorClassBeforeRaceChange,
                                mappedClass,
                                raceId);
                    }
                    CosmeticHeadBoneService.reapplyHeadBoneAfterRaceChange(player, priorHeadBone);
                    if (target != null) {
                        applyStats(data.getStats(), target);
                    }
                    DmzCharacterClassChangeHooks.onServicesRaceChangeApplied(
                            player, data, raceId, mappedClass, resourceSnapshot, target != null);
                    if (keepSkills) {
                        RaceChangeSkillPreserve.restore(data, currentRace, raceId, skillSnapshot);
                    }
                }
            }
            // DMZ's race change never clears the tail flag. Every branch above has now
            // committed the new race, so clear a stuck one the way the bonus wipe strips
            // old-race splits - family races are never touched.
            SaiyanTailWipeHelper.clearIfRaceCannotHaveTail(
                    player, "CharacterServicesSystem.executeRaceChange");
            // The Evil Eye is a Noea overlay whose exit path requires the demon race the
            // player just left, so the final form's giant hitbox/reach would strand.
            // Clear-only, and never for demon-race players.
            EvilEyeWipeHelper.clearIfNotDemon(player);
            // Recompute the entity hitbox from the new race/form instead of keeping the
            // previous form's cached dimensions (reach derives from the same box).
            player.m_6210_(); // refreshDimensions
            if (!staffGrant) {
                CharacterServicesStore.get().record(player.m_20148_().toString()).lastRaceChangeAt =
                        System.currentTimeMillis();
            }
            CharacterServicesStore.get().markDirty();
            audit(player, "Race Change", currentRace, raceId, preservationPercent, cost, true);
            String paid = cost > 0L ? " §7Paid §f" + formatCost(cost) + "§7." : "";
            if (fullWipe) {
                return "§aOpening character setup for §f" + titleCase(raceId)
                        + "§a. §7Pick your class and appearance - free full wipe."
                        + " §7Purchased looks stay unlocked."
                        + paid;
            }
            if (openedFreeClassPicker) {
                return "§aRace changed to §f" + titleCase(raceId)
                        + "§a. Eligible stats preserved at §f" + preservationPercent + "%§a."
                        + " §7Your old class is not available on this race - §fchoose a new class§7"
                        + " in the editor (§fno extra cost§7)."
                        + (cfg.raceChange.keepSkillsOnRaceChange
                                ? " §7Ki skills and shared form progress kept."
                                : "")
                        + " §7Purchased looks stay unlocked."
                        + paid;
            }
            boolean keepSkills = cfg.raceChange.keepSkillsOnRaceChange;
            String kept = keepSkills
                    ? " §7Ki skills, learned techniques, and shared form progress kept."
                    : "";
            String classNote = "";
            if (!fullWipe) {
                String cls = DmzProgression.fightingClass(player);
                if (cls != null && !cls.isBlank()) {
                    String label = FightingClassLabels.display(cls);
                    if (label == null || label.isBlank() || "Not set".equals(label)) {
                        label = titleCase(cls);
                    }
                    classNote = " §7Fighting class §f" + label + " §7(" + titleCase(raceId)
                            + " stats).";
                }
            }
            return "§aRace changed to §f" + titleCase(raceId)
                    + "§a. Eligible stats preserved at §f" + preservationPercent + "%§a."
                    + classNote
                    + kept
                    + " §7Purchased looks stay unlocked."
                    + paid;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] race change failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    player.m_6302_(),
                    t.toString());
            refund(player, cost);
            audit(player, "Race Change", currentRace, raceId, preservationPercent, cost, false);
            return "§cRace change failed. Your coins were returned when possible. Try again or ask staff.";
        }
    }

    public static String executeClassChange(ServerPlayer player, String targetClass) {
        return executeClassChange(player, targetClass, false);
    }

    /**
     * @param staffGrant staff fix: skip the player's class permission, cooldown, and
     *                   Ancient Coin charge, and start no cooldown. Feature switches
     *                   and safety checks still apply.
     */
    public static String executeClassChange(ServerPlayer player, String targetClass, boolean staffGrant) {
        String block = blockReason(player);
        if (!block.isEmpty()) {
            return block;
        }
        if (!staffGrant && !CharacterServicesAccess.canClassChange(player)) {
            return "§cYou do not have permission to change class. Ask staff if you need access.";
        }
        CharacterServicesConfig.ClassChange cc = CharacterServicesConfig.get().classChange;
        if (!cc.enabled) {
            return "§cClass change is turned off on this server.";
        }
        String classId = normalizeId(targetClass);
        String race = DmzProgression.race(player);
        if (classId.isEmpty()) {
            return "§cThat class is not available for your race. Pick another from the list.";
        }
        String current = DmzProgression.fightingClass(player);
        if (classId.equalsIgnoreCase(current)) {
            return "§cYou are already that class. Pick a different one.";
        }
        if (!staffGrant && !CharacterServicesAccess.bypassCooldown(player)) {
            long left = cooldownRemaining(player, "class");
            if (left > 0L) {
                return "§cClass change is on cooldown. Try again in §f" + formatDuration(left) + "§c.";
            }
        }
        releaseTransformation(player);
        long cost = staffGrant ? 0L : classAmountDue(player);
        AncientCoinEconomy.migrateWalletToItems(player);
        if (cost > 0L && !AncientCoinEconomy.canAfford(player, cost)) {
            return insufficientFunds(player, cost);
        }

        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return "§cYour character is still loading. Wait a moment and try again.";
        }
        if (!DmzClassCommandApply.isValidClass(data, classId)) {
            return "§cThat class is not available for your race. Pick another from the list.";
        }
        TransferableStats before = cc.preserveBaseStats ? captureStats(player) : null;
        float[] resourceSnapshot = data.snapshotMultiplierResources();

        if (cost > 0L && !chargeAc(player, cost)) {
            return insufficientFunds(player, cost);
        }

        try {
            Character ch = data.getCharacter();
            if (ch == null) {
                refund(player, cost);
                return "§cYour character is still loading. Wait a moment and try again.";
            }
            String committed =
                    RaceChangeClassMapper.commitFightingClassForRace(data, race, classId, current);
            if (committed != null && !committed.isBlank()) {
                classId = committed;
            } else {
                ch.setCharacterClass(classId);
            }
            boolean preservePrimaries = before != null;
            if (preservePrimaries) {
                applyStats(data.getStats(), before);
            }
            DmzCharacterClassChangeHooks.onPaidClassChange(
                    player, data, classId, resourceSnapshot, preservePrimaries);
            if (!staffGrant) {
                CharacterServicesStore.get().record(player.m_20148_().toString()).lastClassChangeAt =
                        System.currentTimeMillis();
            }
            CharacterServicesStore.get().markDirty();
            audit(player, "Class Change", current, classId, 100, cost, true);
            String paid = cost > 0L ? " §7Paid §f" + formatCost(cost) + "§7." : "";
            String label = FightingClassLabels.display(classId);
            if (label == null || label.isBlank() || "Not set".equals(label)) {
                label = titleCase(classId);
            }
            return "§aClass changed to §f" + label + "§a." + paid;
        } catch (Throwable t) {
            refund(player, cost);
            audit(player, "Class Change", current, classId, 100, cost, false);
            return "§cClass change failed. Your coins were returned when possible. Try again or ask staff.";
        }
    }

    /**
     * Validates reskin eligibility and cost without charging, opening the editor, or starting cooldown.
     * Empty string means OK to proceed.
     */
    public static String reskinPrecheck(ServerPlayer player) {
        return reskinPrecheck(player, false);
    }

    /**
     * @param staffGrant staff fix: skip the player's reskin permission, cooldown, and
     *                   Ancient Coin charge. Feature switch and safety checks still apply.
     */
    public static String reskinPrecheck(ServerPlayer player, boolean staffGrant) {
        String block = blockReason(player, true);
        if (!block.isEmpty()) {
            return block;
        }
        if (!staffGrant && !CharacterServicesAccess.canReskin(player)) {
            return "§cYou do not have permission to reskin. Ask staff if you need access.";
        }
        CharacterServicesConfig.Reskin rs = CharacterServicesConfig.get().reskin;
        if (!rs.enabled) {
            return "§cReskin is turned off on this server.";
        }
        if (!staffGrant && !CharacterServicesAccess.bypassCooldown(player)) {
            long left = cooldownRemaining(player, "reskin");
            if (left > 0L) {
                return "§cReskin is on cooldown. Try again in §f" + formatDuration(left) + "§c.";
            }
        }
        long cost = staffGrant ? 0L : reskinAmountDue(player);
        AncientCoinEconomy.migrateWalletToItems(player);
        if (cost > 0L && !AncientCoinEconomy.canAfford(player, cost)) {
            return insufficientFunds(player, cost);
        }
        return "";
    }

    /**
     * Charges Ancient Coins (level-scaled), then opens DMZ recustomize UI only — no full mod menu.
     */
    public static String executeReskin(ServerPlayer player) {
        return executeReskin(player, false);
    }

    /**
     * @param staffGrant staff fix: open the editor without charging or starting a
     *                   reskin cooldown (permission and cooldown are pre-skipped too).
     */
    public static String executeReskin(ServerPlayer player, boolean staffGrant) {
        String pre = reskinPrecheck(player, staffGrant);
        if (pre != null && !pre.isBlank()) {
            return pre;
        }
        long cost = staffGrant ? 0L : reskinAmountDue(player);
        AncientCoinEconomy.migrateWalletToItems(player);
        if (cost > 0L && !chargeAc(player, cost)) {
            return insufficientFunds(player, cost);
        }
        try {
            ReskinSessionGuard.begin(player);
            if (RaceHeadBoneSync.syncCharacter(player)) {
                RaceHeadBoneSync.syncClient(player);
            }
            var server = player.m_20194_();
            String uuid = player.m_20148_().toString();
            Runnable openEditor = () -> {
                try {
                    NetworkHandler.sendToPlayer(new OpenRecustomizeS2C(), player);
                    if (!staffGrant) {
                        CharacterServicesStore.get().record(uuid).lastReskinAt = System.currentTimeMillis();
                    }
                    CharacterServicesStore.get().markDirty();
                } catch (Throwable openErr) {
                    ReskinSessionGuard.clear(player);
                    refund(player, cost);
                    AdaptiveDifficultyMod.LOGGER.warn("[{}] Reskin editor open failed for {}: {}",
                            AdaptiveDifficultyMod.MOD_ID, player.m_6302_(), openErr.toString());
                }
            };
            // Defer so CNPC/chest inventory can close on the client before DMZ UI opens.
            if (server != null) {
                server.execute(() -> server.execute(() -> server.execute(openEditor)));
            } else {
                openEditor.run();
            }
            audit(player, "Reskin", "", "", 0, cost, true);
            String paid = cost > 0L ? "§7Paid §f" + formatCost(cost) + "§7. " : "";
            return "§a" + paid + "Opening the appearance editor. §7Your stats and progression are unchanged.";
        } catch (Throwable t) {
            ReskinSessionGuard.clear(player);
            refund(player, cost);
            audit(player, "Reskin", "", "", 0, cost, false);
            return "§cCould not open the appearance editor. Your coins were returned when possible. Try again or ask staff.";
        }
    }

    private static void refund(ServerPlayer player, long cost) {
        if (cost > 0L) {
            AncientCoinEconomy.grantCopperExact(player, cost);
        }
    }

    private static void syncClient(ServerPlayer player) {
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Same core wipe as {@code dmzstats reset <player> 0 false} — keeps the race already set on
     * {@link Character}, clears skills, techniques, resources, quest progress, etc.
     */
    private static void applyFullProgressWipe(ServerPlayer player, StatsData data) {
        if (player == null || data == null) {
            return;
        }
        try {
            data.resetPlayerProgress(player, 0, false, false);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] full race-change wipe failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    player.m_6302_(),
                    t.toString());
            Skills skills = data.getSkills();
            if (skills != null) {
                try {
                    skills.removeAllSkills();
                } catch (Throwable ignored) {
                }
            }
            Stats stats = data.getStats();
            if (stats != null) {
                applyStats(stats, new TransferableStats(0, 0, 0, 0, 0, 0));
            }
        }
    }

    /** Drop active form / stack form so race & class edits apply safely while transformed. */
    private static void releaseTransformation(ServerPlayer player) {
        if (player == null) {
            return;
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return;
        }
        Character ch = data.getCharacter();
        if (ch == null) {
            return;
        }
        boolean hadForm = false;
        try {
            hadForm = ch.hasActiveForm() || ch.hasActiveStackForm();
        } catch (Throwable ignored) {
        }
        if (!hadForm) {
            return;
        }
        clearForms(ch, player);
        syncClient(player);
    }

    private static void clearForms(Character ch, ServerPlayer player) {
        try {
            ch.clearActiveForm(player, true);
        } catch (Throwable ignored) {
            try {
                ch.clearActiveForm();
            } catch (Throwable ignored2) {
            }
        }
        try {
            ch.clearActiveStackForm(player, true);
        } catch (Throwable ignored) {
            try {
                ch.clearActiveStackForm();
            } catch (Throwable ignored2) {
            }
        }
    }

    private static TransferableStats captureStats(ServerPlayer player) {
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return null;
        }
        Stats s = data.getStats();
        if (s == null) {
            return null;
        }
        return new TransferableStats(
                s.getStrength(),
                s.getStrikePower(),
                s.getResistance(),
                s.getVitality(),
                s.getKiPower(),
                s.getEnergy()
        );
    }

    private static void applyStats(Stats stats, TransferableStats t) {
        if (stats == null || t == null) {
            return;
        }
        stats.setStrength(t.strength);
        stats.setStrikePower(t.strike);
        stats.setResistance(t.resistance);
        stats.setVitality(t.vitality);
        stats.setKiPower(t.kiPower);
        stats.setEnergy(t.energy);
    }

    private static boolean isValidPreservation(int pct) {
        for (int step : CharacterServicesConfig.preservationSteps()) {
            if (step == pct) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRaceAvailable(String raceId) {
        return DmzContentDiscovery.isKnownRace(raceId);
    }

    private static long cooldownRemaining(ServerPlayer player, String kind) {
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        long now = System.currentTimeMillis();
        long last;
        long cd;
        switch (kind) {
            case "race" -> {
                last = rec.lastRaceChangeAt;
                cd = CharacterServicesConfig.get().raceChange.cooldownMs;
            }
            case "class" -> {
                last = rec.lastClassChangeAt;
                cd = CharacterServicesConfig.get().classChange.cooldownMs;
            }
            case "reskin" -> {
                last = rec.lastReskinAt;
                cd = CharacterServicesConfig.get().reskin.cooldownMs;
            }
            default -> {
                return 0L;
            }
        }
        return Math.max(0L, last + cd - now);
    }

    private static void audit(
            ServerPlayer player,
            String action,
            String from,
            String to,
            int preservation,
            long cost,
            boolean success
    ) {
        AdaptiveDifficultyMod.LOGGER.info(
                "[CharacterServices] player={} action={} from={} to={} preservation={} cost={} success={}",
                player == null ? "?" : player.m_7755_().getString(),
                action,
                from,
                to,
                preservation,
                cost,
                success);
    }

    private static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("race:")) {
            s = s.substring(5).trim();
        }
        if (s.startsWith("class:")) {
            s = s.substring(6).trim();
        }
        return s;
    }

    /** Player-facing race name, the same one the race cards show ("frost_demon" -> "Frost Demon"). */
    public static String raceDisplayName(String id) {
        return titleCase(id);
    }

    private static String titleCase(String id) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        String[] parts = id.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(java.lang.Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1));
            }
        }
        return sb.toString();
    }

    private static String format(int v) {
        return DmzRewards.formatWhole(v);
    }

    private static String formatDuration(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        long days = sec / 86400L;
        sec %= 86400L;
        long hours = sec / 3600L;
        sec %= 3600L;
        long minutes = sec / 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
