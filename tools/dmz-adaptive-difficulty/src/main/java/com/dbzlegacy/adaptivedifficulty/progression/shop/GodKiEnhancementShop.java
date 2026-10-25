package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.GuiClickConfirm;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.ScreenNotify;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.skills.Skills;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prestige store: God Ki Enhancement.
 *
 * <p>1 favor buys one investment; each investment grants
 * {@link #MASTERY_PER_POINT} {@code godkienhancement} mastery, up to
 * {@link #MAX_INVESTMENTS} investments (5000 mastery). The investment count
 * is stored per player like the other prestige purchases ({@code pp_*} keys)
 * and survives prestige. Cost, mastery per point, the investment cap and
 * whether it is on sale come from {@link PrestigeShopConfig#godKi}; the
 * constants below are its defaults.
 *
 * <p>On login (and the post-death / pulse reapply window) the character's
 * God Ki mastery is topped up to {@code invested x 1000} — never lowered.
 * The {@code godkienhancement} skill is kept present while invested, so the
 * enhancement exists on the character even though it is not obtainable
 * through normal gameplay.
 *
 * <p>God forms gate on this: custom God forms (group {@code godforms}) need
 * the enhancement present; the true God Ki form (group
 * {@code godkienhancement}) additionally needs the mastery threshold from
 * {@link DifficultyConfig#godKiTrueFormMasteryThreshold}.
 * {@code FormModeHandlerGodKiGateMixin} / {@code StackFormModeHandlerGodKiGateMixin}
 * enforce both gates at transform time.
 */
public final class GodKiEnhancementShop {
    /** DMZ group of the Enhance with God Ki form (Noea God Ki enhancement). */
    public static final String ENHANCEMENT_GROUP = "godkienhancement";
    /** Stock form name inside the enhancement group (Noea overlay). */
    public static final String ENHANCEMENT_FORM = "enhancewithgodki";
    /** Noea custom God forms group (Super Saiyan God and variants). */
    public static final String GOD_FORMS_GROUP = "godforms";
    /** Default mastery granted per invested prestige point. */
    public static final int MASTERY_PER_POINT = 1000;
    /** Default cap: five investments = 5000 mastery. */
    public static final int MAX_INVESTMENTS = 5;
    /** Default favor per investment. */
    public static final int INVESTMENT_COST = 1;
    private static final String KEY_INVESTED = "pp_godki_enh";

    private GodKiEnhancementShop() {}

    /** Prestige-favor investments bought so far (0 to the configured cap). */
    public static int invested(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.max(0, Math.min(maxInvestments(),
                (int) ProgressionData.storedGetLong(player, KEY_INVESTED, 0L)));
    }

    /** Investment cap from the shop config. */
    public static int maxInvestments() {
        return PrestigeShopConfig.get().godKi.maxPoints;
    }

    /** Mastery per investment from the shop config. */
    public static int masteryPerPoint() {
        return PrestigeShopConfig.get().godKi.masteryPerPoint;
    }

    /** Mastery the shop guarantees for an investment count. */
    public static int expectedMastery(int investments) {
        return Math.max(0, Math.min(maxInvestments(), investments)) * masteryPerPoint();
    }

    /** True while the shop is on sale and more investments can still be bought. */
    public static boolean canBuy(ServerPlayer player) {
        return player != null && PrestigeShopConfig.get().godKi.enabled
                && invested(player) < maxInvestments();
    }

    /**
     * Highest {@code godkienhancement} mastery on the character — reads every
     * {@code godkienhancement:*} record across stack and regular masteries, so
     * a renamed overlay form still counts.
     */
    public static double mastery(ServerPlayer player) {
        Character character = character(player);
        if (character == null) {
            return 0.0;
        }
        double best = masteryGroup(character.getStackFormMasteries());
        return Math.max(best, masteryGroup(character.getFormMasteries()));
    }

    /**
     * The enhancement exists on the character: the skill is present or any
     * mastery record is — presence even when unobtainable in normal play.
     */
    public static boolean hasEnhancement(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            Skills skills = DmzSkillUtil.skills(player);
            if (skills != null && skills.hasSkill(ENHANCEMENT_GROUP)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        Character character = character(player);
        if (character == null) {
            return false;
        }
        return masteryRecordPresent(character.getStackFormMasteries())
                || masteryRecordPresent(character.getFormMasteries());
    }

    /** Custom God forms: the enhancement flag alone. */
    public static boolean canUseCustomGodForms(ServerPlayer player) {
        return hasEnhancement(player);
    }

    /** True God Ki form: the enhancement flag plus the mastery threshold. */
    public static boolean canUseTrueGodForm(ServerPlayer player) {
        return hasEnhancement(player) && mastery(player) >= trueFormThreshold();
    }

    /** Mastery needed before the true God Ki form may transform. */
    public static int trueFormThreshold() {
        return Math.max(1, DifficultyConfig.get().godKiTrueFormMasteryThreshold);
    }

    /**
     * Gate reason for activating a form in {@code group}, or {@code null}
     * when allowed. Human copy — no raw ids.
     */
    public static String blockReason(ServerPlayer player, String group) {
        if (player == null || group == null || group.isBlank()) {
            return null;
        }
        String g = group.trim();
        if (GOD_FORMS_GROUP.equalsIgnoreCase(g)) {
            return hasEnhancement(player)
                    ? null
                    : "God forms need God Ki - invest in God Ki Enhancement (Prestige shop, Effects).";
        }
        if (ENHANCEMENT_GROUP.equalsIgnoreCase(g)) {
            if (!hasEnhancement(player)) {
                return "You need God Ki - invest in God Ki Enhancement (Prestige shop, Effects).";
            }
            if (mastery(player) < trueFormThreshold()) {
                return "Your God Ki mastery is too low - invest more in God Ki Enhancement"
                        + " (Prestige shop, Effects).";
            }
        }
        return null;
    }

    /** Transform-gate feedback: red title + reason, anti-spam cooldown. */
    public static void notifyBlocked(ServerPlayer player, String reason) {
        if (player == null || reason == null || reason.isBlank()) {
            return;
        }
        ScreenNotify.blocked(player, "God Ki needed", reason, "lm.godki.gate", 4_000L);
    }

    /** Buy the next investment — double-click confirm, menu-inline feedback. */
    public static String buy(ServerPlayer player) {
        if (player == null) {
            return UltraStyle.dangerLine("Players only.");
        }
        int have = invested(player);
        int max = maxInvestments();
        int cost = PrestigeShopConfig.get().godKi.costPerPoint;
        int perPoint = masteryPerPoint();
        if (have >= max) {
            return UltraStyle.noticeLine(
                    "God Ki Enhancement is fully invested (" + have + "/" + max + ").");
        }
        if (!PrestigeShopConfig.get().godKi.enabled) {
            return UltraStyle.dangerLine("God Ki Enhancement is not for sale right now.");
        }
        int points = PrestigePointsSystem.getPoints(player);
        if (points < cost) {
            return UltraStyle.dangerLine(
                    "Need " + cost + " favor (have " + points + ").");
        }
        if (!GuiClickConfirm.confirmed(player.m_20148_(), "godki-enh:" + have)) {
            return UltraStyle.noticeLine(
                    "Invest " + cost + " favor in God Ki Enhancement (+"
                            + DmzRewards.formatWhole(perPoint) + " mastery, " + (have + 1)
                            + "/" + max + ")? Click again within 10 seconds.");
        }
        int next = have + 1;
        ProgressionData.storedPut(player, KEY_INVESTED, next);
        PrestigePointsSystem.setPoints(player, points - cost);
        reapply(player);
        double now = mastery(player);
        SystemTelemetry.log("prestige_points", "buy_godki_enhancement", player, null, Map.of(
                "invested", next,
                "mastery", (long) now,
                "points", PrestigePointsSystem.getPoints(player)
        ));
        return UltraStyle.color(
                        "a",
                        "God Ki Enhancement invested " + next + "/" + max
                                + " (-" + cost + " favor)")
                + "\n"
                + UltraStyle.dim(
                        "God Ki mastery: " + DmzRewards.formatWhole((long) now)
                                + " (+" + DmzRewards.formatWhole(perPoint) + " per point)")
                + (next == 1 ? "\n" + UltraStyle.color("a", "God forms are usable now.") : "")
                + "\n"
                + UltraStyle.dim("Favor left: " + PrestigePointsSystem.getPoints(player));
    }

    /**
     * Staff {@code /padmin godki} — store an investment count directly (no favor
     * charged) and align the enhancement to it: a raise tops mastery up through
     * {@link #reapply}, a reduce drops the mastery records first so the top-up
     * lands at the new count, and zero fully strips skill, forms, and history
     * through {@link com.dbzlegacy.adaptivedifficulty.noea.GodKiWipeHelper#wipe}.
     */
    static void adminSetInvested(ServerPlayer player, int count) {
        if (player == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(maxInvestments(), count));
        int before = invested(player);
        ProgressionData.storedPut(player, KEY_INVESTED, clamped);
        if (clamped == 0) {
            if (before > 0 || hasEnhancement(player)) {
                com.dbzlegacy.adaptivedifficulty.noea.GodKiWipeHelper.wipe(player, "admin_godki");
            }
            return;
        }
        if (clamped < before) {
            com.dbzlegacy.adaptivedifficulty.noea.GodKiWipeHelper.dropEnhancementMastery(player);
        }
        reapply(player);
    }

    /**
     * Login / reapply top-up: set the enhancement skill and raise mastery to
     * {@code invested x 1000}. Never lowers a higher record. No-op while the
     * character is not attached — the login reapply window retries.
     */
    public static void reapply(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int investments = invested(player);
        if (investments <= 0) {
            return;
        }
        Character character = character(player);
        if (character == null) {
            return;
        }
        double expected = expectedMastery(investments);
        boolean changed = false;
        try {
            Skills skills = DmzSkillUtil.skills(player);
            if (skills != null) {
                DmzSkillUtil.ensureRegistered(skills, ENHANCEMENT_GROUP, 1);
                if (DmzSkillUtil.level(skills, ENHANCEMENT_GROUP) < 1) {
                    DmzSkillUtil.setLevel(skills, ENHANCEMENT_GROUP, 1);
                    changed = true;
                }
            }
        } catch (Throwable ignored) {
        }
        changed |= raiseMastery(character.getStackFormMasteries(), expected);
        changed |= raiseMastery(character.getFormMasteries(), expected);
        if (changed) {
            try {
                DmzSkillUtil.sync(player);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Character character(ServerPlayer player) {
        try {
            return DmzProgression.character(player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Top the group up to {@code expected} — never lower, never below the stock cap. */
    private static boolean raiseMastery(FormMasteries masteries, double expected) {
        if (masteries == null || expected <= 0.0) {
            return false;
        }
        double current = masteryGroup(masteries);
        if (current >= expected) {
            return false;
        }
        // The stock Noea form caps mastery at 100 — the shop's top-up must land in full.
        masteries.setMastery(ENHANCEMENT_GROUP, ENHANCEMENT_FORM, expected, expected);
        return true;
    }

    /** Highest {@code godkienhancement:*} record in one mastery map (0 when none). */
    private static double masteryGroup(FormMasteries masteries) {
        double best = 0.0;
        for (String form : masteryForms(masteries)) {
            best = Math.max(best, masteries.getMastery(ENHANCEMENT_GROUP, form));
        }
        return best;
    }

    private static boolean masteryRecordPresent(FormMasteries masteries) {
        return !masteryForms(masteries).isEmpty();
    }

    /** Form names recorded under the enhancement group (case-insensitive prefix). */
    private static java.util.List<String> masteryForms(FormMasteries masteries) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (masteries == null) {
            return out;
        }
        try {
            CompoundTag tag = masteries.save();
            if (tag == null) {
                return out;
            }
            String prefix = ENHANCEMENT_GROUP + ":";
            for (String key : tag.m_128431_()) {
                if (key == null || key.length() <= prefix.length()
                        || !key.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    continue;
                }
                String form = key.substring(prefix.length()).trim();
                if (!form.isEmpty()) {
                    out.add(form.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
