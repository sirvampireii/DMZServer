package com.dbzlegacy.adaptivedifficulty.progression.race;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionConfig;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.common.util.TransformationsHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Port of AndrioidConversion.js / DragonMineZ {@code NPCActionC2S.handleGero}
 * and the CNPC Android Upgrade Removal script — Dr. Gero convert + remove.
 * <p>
 * Android is an <b>upgrade flag</b> on supported races (not a race swap). Eligible
 * when that race has {@code androidforms} TP costs configured (e.g. Human, Saiyan,
 * Frost Demon, Viltrumite). Bio-Android is blocked (already an android lineage).
 * Combat scaling stays race-agnostic via live DMZ stats + form mults; the upgrade
 * only swaps form skills / {@code isAndroidUpgraded}.
 */
public final class AndroidConversion {
    /** Native android lineage — cannot take the Gero upgrade on top. */
    private static final Set<String> BLOCKED = Set.of("bioandroid");
    private static final String ANDROID_FORM_GROUP = "androidforms";
    private static final String ANDROID_BASE_FORM = "androidbase";
    private static final long CONFIRM_MS = 10_000L;

    /** Staff/self remove confirm: actor → pending target + expiry. */
    private static final Map<UUID, PendingRemove> PENDING_REMOVE = new ConcurrentHashMap<>();

    private AndroidConversion() {}

    /**
     * Clears the Gero Android upgrade when the player's race no longer has {@code androidforms}
     * configured (e.g. paid race change to Namekian). No-op when not upgraded or still eligible.
     */
    public static void stripIfRaceIneligible(ServerPlayer player, String raceId) {
        if (player == null) {
            return;
        }
        if (!isAndroidUpgraded(player)) {
            return;
        }
        String race = raceId == null || raceId.isBlank() ? "" : raceId.trim();
        if (race.isBlank()) {
            try {
                Character ch = DmzProgression.character(player);
                race = ch == null ? "" : raceName(ch);
            } catch (Throwable ignored) {
                race = "";
            }
        }
        if (race.isBlank() || raceAllowsAndroidForms(race)) {
            return;
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return;
        }
        doRemove(player, data, true);
    }

    /** Which Android tab state applies to a player. Read-only: never converts or charges. */
    public enum Eligibility {
        /** Already has the Gero upgrade - only removal applies. */
        UPGRADED,
        /** Race has androidforms configured and is not an android lineage - may convert. */
        ELIGIBLE,
        /** Race has no androidforms configured. */
        INELIGIBLE,
        /** Bio-Android: already an android lineage, cannot take the Gero upgrade. */
        ANDROID_RACE,
        /** Character data not loaded yet. */
        UNKNOWN
    }

    /**
     * The same checks {@link #convert} makes, in the same order, without changing anything —
     * so menus show only the button that applies.
     */
    public static Eligibility eligibility(ServerPlayer player) {
        if (player == null) {
            return Eligibility.UNKNOWN;
        }
        try {
            StatsData data = DmzProgression.stats(player);
            Character character = data == null ? null : data.getCharacter();
            Status status = data == null ? null : data.getStatus();
            if (character == null || status == null) {
                return Eligibility.UNKNOWN;
            }
            if (status.isAndroidUpgraded()) {
                return Eligibility.UPGRADED;
            }
            String raceName = raceName(character);
            if (raceName.isBlank()) {
                return Eligibility.UNKNOWN;
            }
            if (BLOCKED.contains(raceName.toLowerCase(Locale.ROOT))) {
                return Eligibility.ANDROID_RACE;
            }
            return raceAllowsAndroidForms(player, character, raceName)
                    ? Eligibility.ELIGIBLE
                    : Eligibility.INELIGIBLE;
        } catch (Throwable ignored) {
            return Eligibility.UNKNOWN;
        }
    }

    /** True when the player has the Gero Android upgrade flag. */
    public static boolean isAndroidUpgraded(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            StatsData data = DmzProgression.stats(player);
            Status status = data == null ? null : data.getStatus();
            return status != null && status.isAndroidUpgraded();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Race ids on disk that currently expose {@code androidforms} TP costs
     * (Bio-Android excluded). Empty if configs are not loaded yet.
     */
    public static List<String> configuredAndroidRaceIds() {
        List<String> out = new ArrayList<>();
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("dragonminez").resolve("races");
            if (!Files.isDirectory(dir)) {
                return out;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(id -> {
                            if (id == null || id.isBlank()) {
                                return;
                            }
                            String lower = id.toLowerCase(Locale.ROOT);
                            if (BLOCKED.contains(lower)) {
                                return;
                            }
                            if (raceAllowsAndroidForms(id)) {
                                out.add(id);
                            }
                        });
            }
        } catch (Throwable ignored) {
            // Config not ready — callers fall back to generic copy.
        }
        return out;
    }

    /** Player-facing eligible-race hint for GUI / deny messages. */
    public static String eligibleRaceHint() {
        List<String> ids = configuredAndroidRaceIds();
        if (ids.isEmpty()) {
            return "Human, Saiyan, Frost Demon, Viltrumite (races with androidforms configured)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(prettyRace(ids.get(i)));
        }
        return sb.toString();
    }

    public static String convert(ServerPlayer player) {
        if (!ProgressionConfig.androidConversion()) {
            return "§cAndroid conversion is disabled. §7Staff: enable the android flag in /progression.";
        }
        if (player == null) {
            return "§cPlayer required.";
        }
        try {
            StatsData data = DmzProgression.stats(player);
            if (data == null) {
                return "§c[Android] DragonMineZ data could not be loaded.";
            }
            Character character = data.getCharacter();
            Status status = data.getStatus();
            Skills skills = data.getSkills();
            if (character == null || status == null || skills == null) {
                return "§c[Android] Missing character/status/skills data.";
            }
            if (status.isAndroidUpgraded()) {
                return "§c[Android] §f" + player.m_7755_().getString()
                        + " §7is already an upgraded Android.";
            }
            String raceName = raceName(character);
            if (raceName.isBlank()) {
                return "§c[Android] Could not read player race.";
            }
            String lower = raceName.toLowerCase(Locale.ROOT);
            if (BLOCKED.contains(lower)) {
                return "§c[Android] §f" + prettyRace(raceName)
                        + " §7is already an android lineage and cannot take the Gero upgrade.";
            }
            // Match Gero: race must have androidforms TP costs configured (resolve DMZ race id aliases).
            if (!raceAllowsAndroidForms(player, character, raceName)) {
                return "§c[Android] §f" + prettyRace(raceName)
                        + " §7has no android forms. §8Eligible: §7" + eligibleRaceHint() + "§8.";
            }

            status.setAndroidUpgraded(true);
            DmzSkillUtil.setLevel(skills, ANDROID_FORM_GROUP, 1);
            try {
                skills.removeSkill("superforms");
            } catch (Throwable ignored) {
            }
            try {
                skills.removeSkill("legendaryforms");
            } catch (Throwable ignored) {
            }
            try {
                data.updateTransformationSkillLimits(raceName);
            } catch (Throwable ignored) {
            }
            character.setSelectedFormGroup(ANDROID_FORM_GROUP);
            character.setSelectedForm(ANDROID_BASE_FORM);
            character.setActiveForm(ANDROID_FORM_GROUP, ANDROID_BASE_FORM);
            try {
                character.clearActiveStackForm();
            } catch (Throwable ignored) {
            }
            try {
                player.m_6210_(); // refreshDimensions
            } catch (Throwable ignored) {
            }
            DmzSkillUtil.sync(player);
            DmzRewards.msg(player, LmChat.ok("Android", "Conversion complete. §7Android forms unlocked for §f"
                    + prettyRace(raceName) + "§7."));
            SystemTelemetry.log("progression", "android_conversion", player, null,
                    Map.of("race", raceName, "android", "true"));
            return "§a[Android] Conversion complete for §f" + player.m_7755_().getString()
                    + " §7(" + prettyRace(raceName) + ")§a.";
        } catch (Throwable t) {
            return "§c[Android Trigger Error] §f" + t;
        }
    }

    /**
     * Staff/self: remove Android upgrade (two-click confirm within 10s, same target).
     * Restores normal form skills deleted by Gero conversion.
     */
    public static String remove(ServerPlayer actor, ServerPlayer target) {
        if (!ProgressionConfig.androidConversion()) {
            return "§cAndroid tools are disabled. §7Staff: enable the android flag in /progression.";
        }
        if (actor == null || target == null) {
            return "§cPlayer required.";
        }
        try {
            StatsData data = DmzProgression.stats(target);
            if (data == null) {
                return "§c[Android] DragonMineZ data could not be loaded for §f"
                        + target.m_7755_().getString() + "§c.";
            }
            Status status = data.getStatus();
            if (status == null) {
                return "§c[Android] Status could not be loaded.";
            }
            if (!status.isAndroidUpgraded()) {
                clearPending(actor.m_20148_());
                return "§e[Android] §f" + target.m_7755_().getString()
                        + " §7is not an upgraded Android.";
            }

            long now = System.currentTimeMillis();
            PendingRemove pending = PENDING_REMOVE.get(actor.m_20148_());
            if (pending != null
                    && pending.until > now
                    && target.m_20148_().equals(pending.target)) {
                clearPending(actor.m_20148_());
                return doRemove(target, data, false);
            }

            PENDING_REMOVE.put(
                    actor.m_20148_(), new PendingRemove(target.m_20148_(), now + CONFIRM_MS));
            String who = target.m_20148_().equals(actor.m_20148_())
                    ? "yourself"
                    : "§f" + target.m_7755_().getString();
            return "§6[Android] §cWarning: §7This will remove the Android upgrade from " + who + "§7.\n"
                    + "§7Race, stats, skills, and progression stay — form skills are restored.\n"
                    + "§eConfirm again within 10 seconds.";
        } catch (Throwable t) {
            return "§c[Android Remove Error] §f" + t;
        }
    }

    /**
     * Staff menu confirm already named the target. Apply removal now — do not arm the
     * 10-second second click in {@link #remove}.
     */
    public static String removeConfirmed(ServerPlayer actor, ServerPlayer target) {
        if (!StaffAccess.isStaff(actor)) {
            return UltraStyle.DANGER + "Staff only.";
        }
        if (!ProgressionConfig.androidConversion()) {
            return UltraStyle.DANGER + "Android tools are disabled. "
                    + UltraStyle.GRAY + "Staff: enable the android flag in /progression.";
        }
        if (target == null) {
            return UltraStyle.DANGER + "Player required.";
        }
        try {
            StatsData data = DmzProgression.stats(target);
            if (data == null) {
                return UltraStyle.DANGER + "[Android] DragonMineZ data could not be loaded for "
                        + UltraStyle.WHITE + target.m_7755_().getString() + UltraStyle.DANGER + ".";
            }
            Status status = data.getStatus();
            if (status == null) {
                return UltraStyle.DANGER + "[Android] Status could not be loaded.";
            }
            if (!status.isAndroidUpgraded()) {
                clearPending(actor.m_20148_());
                return UltraStyle.YELLOW + "[Android] " + UltraStyle.WHITE + target.m_7755_().getString()
                        + " " + UltraStyle.GRAY + "is not an upgraded Android.";
            }
            clearPending(actor.m_20148_());
            return doRemove(target, data, false);
        } catch (Throwable t) {
            return UltraStyle.DANGER + "[Android Remove Error] " + UltraStyle.WHITE + t;
        }
    }

    private static String doRemove(ServerPlayer player, StatsData data, boolean automaticRaceChange) {
        Character character = data.getCharacter();
        Status status = data.getStatus();
        Skills skills = data.getSkills();
        if (character == null || status == null || skills == null) {
            return automaticRaceChange
                    ? ""
                    : "§c[Android] Missing character/status/skills data.";
        }

        status.setAndroidUpgraded(false);

        try {
            TransformationsHelper.revertToBaseForm(player, data);
        } catch (Throwable ignored) {
            try {
                character.clearActiveForm(player);
            } catch (Throwable ignored2) {
                try {
                    character.clearActiveForm();
                } catch (Throwable ignored3) {
                }
            }
        }

        // Gero deletes these; re-add at 0 so trainers/quests can unlock again.
        ensureSkillAtZero(skills, "superforms");
        ensureSkillAtZero(skills, "legendaryforms");
        try {
            skills.removeSkill(ANDROID_FORM_GROUP);
        } catch (Throwable ignored) {
        }
        try {
            character.setSelectedFormGroup("");
            character.setSelectedForm("");
        } catch (Throwable ignored) {
        }

        String race = raceName(character);
        try {
            data.updateTransformationSkillLimits(race);
        } catch (Throwable ignored) {
            try {
                data.updateTransformationSkillLimits(character.getRace());
            } catch (Throwable ignored2) {
            }
        }
        try {
            TransformationsHelper.ensureSelectedFormDefault(data);
        } catch (Throwable ignored) {
        }
        try {
            TransformationsHelper.ensureSelectedStackFormDefault(data);
        } catch (Throwable ignored) {
        }
        try {
            player.m_6210_();
        } catch (Throwable ignored) {
        }
        DmzSkillUtil.sync(player);

        if (automaticRaceChange) {
            DmzRewards.msg(
                    player,
                    LmChat.info(
                            "Android",
                            "Upgrade removed — §f"
                                    + prettyRace(race)
                                    + " §7does not support Android forms."));
        } else {
            DmzRewards.msg(player, LmChat.ok("Android", "Upgrade removed. §7You are no longer an upgraded Android."));
            DmzRewards.msg(player, LmChat.info("Android", "Race, stats, skills, and progression were preserved."));
        }
        SystemTelemetry.log(
                "progression",
                "android_remove",
                player,
                null,
                Map.of(
                        "race",
                        race == null ? "" : race,
                        "automatic",
                        automaticRaceChange ? "race_change" : "manual"));
        return automaticRaceChange
                ? ""
                : "§a[Android] Upgrade removed for §f" + player.m_7755_().getString() + "§a.";
    }

    private static void ensureSkillAtZero(Skills skills, String id) {
        try {
            if (!skills.hasSkill(id)) {
                skills.setSkillLevel(id, 0);
            }
        } catch (Throwable ignored) {
            try {
                skills.setSkillLevel(id, 0);
            } catch (Throwable ignored2) {
            }
        }
    }

    public static void clearPending(UUID actor) {
        if (actor != null) {
            PENDING_REMOVE.remove(actor);
        }
    }

    private static String raceName(Character character) {
        // Gero uses getRaceName() only.
        try {
            String n = character.getRaceName();
            if (n != null && !n.isBlank()) {
                return n;
            }
        } catch (Throwable ignored) {
        }
        try {
            String n = character.getRace();
            return n == null ? "" : n;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Match Gero: {@code getFormSkillTpCosts("androidforms").length > 0}. */
    public static boolean raceAllowsAndroidForms(String raceName) {
        return raceAllowsAndroidForms(null, null, raceName);
    }

    /** Resolves DMZ race folder ids (display name vs config key) before checking androidforms. */
    public static boolean raceAllowsAndroidForms(ServerPlayer player, Character character, String raceName) {
        for (String key : raceConfigKeys(character, raceName)) {
            if (raceConfigHasAndroidForms(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean raceConfigHasAndroidForms(String raceKey) {
        if (raceKey == null || raceKey.isBlank()) {
            return false;
        }
        try {
            RaceCharacterConfig cfg = ConfigManager.getRaceCharacter(raceKey);
            if (cfg != null) {
                Integer[] costs = cfg.getFormSkillTpCosts(ANDROID_FORM_GROUP);
                if (costs != null && costs.length > 0) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        Path forms = FMLPaths.CONFIGDIR.get()
                .resolve("dragonminez")
                .resolve("races")
                .resolve(raceKey.toLowerCase(Locale.ROOT))
                .resolve("forms")
                .resolve("androidforms.json");
        return Files.isRegularFile(forms);
    }

    private static List<String> raceConfigKeys(Character character, String primaryRace) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addRaceKey(keys, primaryRace);
        if (character != null) {
            try {
                addRaceKey(keys, character.getRace());
            } catch (Throwable ignored) {
            }
            try {
                addRaceKey(keys, character.getRaceName());
            } catch (Throwable ignored) {
            }
        }
        return new ArrayList<>(keys);
    }

    private static void addRaceKey(LinkedHashSet<String> keys, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String s = raw.trim();
        keys.add(s);
        String lower = s.toLowerCase(Locale.ROOT);
        keys.add(lower);
        keys.add(lower.replace(' ', '_').replace('-', '_'));
        if (lower.endsWith(" saiyan") || lower.equals("half saiyan") || lower.equals("half-saiyan")) {
            keys.add("saiyan");
        }
    }

    private static String prettyRace(String raceId) {
        if (raceId == null || raceId.isBlank()) {
            return "Unknown";
        }
        String id = raceId.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "frostdemon" -> "Frost Demon";
            case "bioandroid" -> "Bio-Android";
            case "sento_saiyan" -> "Sento Saiyan";
            case "ancient_saiyan" -> "Ancient Saiyan";
            case "namekian" -> "Namekian";
            case "viltrumite" -> "Viltrumite";
            case "saiyan" -> "Saiyan";
            case "human" -> "Human";
            case "majin" -> "Majin";
            case "monkey" -> "Monkey";
            default -> {
                String[] parts = id.split("[_\\-]+");
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
                yield sb.length() == 0 ? raceId : sb.toString();
            }
        };
    }

    private record PendingRemove(UUID target, long until) {}
}
