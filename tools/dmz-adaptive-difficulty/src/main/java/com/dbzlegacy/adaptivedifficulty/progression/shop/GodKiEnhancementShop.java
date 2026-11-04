package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.ScreenNotify;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.skills.Skills;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * God Ki Enhancement: the level store and mastery rules behind God Ki Mastery.
 *
 * <p>Since 4.7.21 levels are bought in the Mastery Wallet ({@link MasteryWallet}, 10 points a
 * level), not on the Prestige shop Effects page. Each level grants
 * {@link #MASTERY_PER_POINT} {@code godkienhancement} mastery, up to
 * {@link #MAX_INVESTMENTS} levels (10,000 mastery since 4.7.34). The level count is stored per
 * player like the other prestige purchases ({@code pp_godki_enh}) and survives
 * prestige. Mastery per level, the level cap and whether it is on sale come
 * from {@link PrestigeShopConfig#godKi}; the constants below are its defaults.
 *
 * <p>Levels bought on the old Effects page are refunded once per prestige record
 * ({@link #refundEffectsInvestment}): n levels give back n favor, the level goes to 0
 * and the granted God Ki is wiped. {@link #KEY_REFUND_DONE} sits in the same record as
 * the level and the favor and is set before anything changes, and every wallet or staff
 * level write sets it too, so the refund can never run twice or refund a wallet level.
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
    /** Default cap: ten investments = 10,000 mastery (4.7.34; five before). */
    public static final int MAX_INVESTMENTS = 10;
    /** Default favor per investment. */
    public static final int INVESTMENT_COST = 1;
    private static final String KEY_INVESTED = "pp_godki_enh";
    /** One-time Effects-to-wallet refund marker; same {@code pp_} record as {@link #KEY_INVESTED}. */
    static final String KEY_REFUND_DONE = "pp_godki_wallet_refund";

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
     * mastery record is — presence even when unobtainable in normal play. A
     * Mastery wallet God Ki level ({@link KiMasteryTrack#GOD_KI}, 4.7.29) counts
     * too, so God forms stay open for players who master God Ki in the wallet.
     */
    public static boolean hasEnhancement(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (KiMasteryTrack.GOD_KI.level(player) >= 1) {
            return true;
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
                    : "God forms need God Ki - add a God Ki Mastery level in the Mastery wallet (Prestige shop).";
        }
        if (ENHANCEMENT_GROUP.equalsIgnoreCase(g)) {
            if (!hasEnhancement(player)) {
                return "You need God Ki - add a God Ki Mastery level in the Mastery wallet (Prestige shop).";
            }
            if (mastery(player) < trueFormThreshold()) {
                return "Your God Ki mastery is too low - add God Ki Mastery levels in the Mastery wallet"
                        + " (Prestige shop).";
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

    /**
     * The old Effects purchase ({@code do:godki}, {@code /prestige buy godki}). God Ki is bought in the
     * Mastery wallet now, so this only says where to go and changes nothing.
     */
    public static String buy(ServerPlayer player) {
        if (player == null) {
            return UltraStyle.dangerLine("Players only.");
        }
        return UltraStyle.noticeLine("God Ki Mastery moved to the Mastery wallet: open Prestige, then Shop,"
                + " Mastery wallet, Apply points to a form, God Ki Mastery. A level costs "
                + MasteryWallet.GODKI_POINTS_PER_LEVEL + " mastery points.");
    }

    /**
     * Mastery wallet level up: settle the one-time Effects refund first (4.7.22 - storing the level
     * sets the marker, so the refund must run before it), then store the next level and raise the
     * mastery through {@link #reapply}, the same state the Effects purchase left. The caller has
     * checked the cap and the points. Public for the dev probe; only the wallet calls it.
     *
     * @return the new level
     */
    public static int levelUpFromWallet(ServerPlayer player) {
        refundEffectsInvestment(player);
        int next = Math.min(maxInvestments(), invested(player) + 1);
        storeLevel(player, next);
        reapply(player);
        return next;
    }

    /**
     * Login trigger for the Effects refund (4.7.22): try now, then again over the next 15 seconds,
     * because the DMZ character often attaches after the login event. Each try is a cheap no-op once
     * the marker is set or when there is nothing to refund.
     */
    public static void refundOnLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        refundEffectsInvestment(player);
        net.minecraft.server.MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        java.util.UUID id = player.m_20148_();
        for (int delay : new int[] {20, 100, 300}) {
            try {
                server.m_6937_(new net.minecraft.server.TickTask(server.m_129921_() + delay, () -> {
                    ServerPlayer p = server.m_6846_().m_11259_(id);
                    if (p != null) {
                        refundEffectsInvestment(p);
                    }
                }));
            } catch (Throwable ignored) {
            }
        }
    }

    /** Write the level; the refund marker goes with it so a stored level is never refunded later. */
    private static void storeLevel(ServerPlayer player, int level) {
        ProgressionData.storedPutBool(player, KEY_REFUND_DONE, true);
        ProgressionData.storedPut(player, KEY_INVESTED, Math.max(0, level));
    }

    /**
     * One-time refund of levels bought on the old Effects page (4.7.21): n levels add n favor, the
     * level goes to 0 and the granted God Ki (skill, mastery, form records) is wiped, the same as a
     * staff {@code /padmin godki} set to 0. Runs on login ({@link #refundOnLogin}), before any level
     * write (wallet, staff) and from {@link #reapply}; it waits until the character is attached so the
     * wipe can reach it, and writes nothing until then. The marker is written first, in the same record
     * as the level and the favor, and the record is saved straight away; a second call is a no-op.
     */
    static void refundEffectsInvestment(ServerPlayer player) {
        if (player == null || !ProgressionData.storedWritable(player)
                || ProgressionData.storedGetBool(player, KEY_REFUND_DONE)
                || character(player) == null) {
            return;
        }
        int levels = (int) Math.max(0L, ProgressionData.storedGetLong(player, KEY_INVESTED, 0L));
        ProgressionData.storedPutBool(player, KEY_REFUND_DONE, true);
        if (levels > 0) {
            ProgressionData.storedPut(player, KEY_INVESTED, 0);
            PrestigePointsSystem.setPoints(player, PrestigePointsSystem.getPoints(player) + levels);
            com.dbzlegacy.adaptivedifficulty.noea.GodKiWipeHelper.wipe(player, "godki_wallet_refund");
        }
        com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore.flush(player.m_20148_());
        PrestigeCharacter.flush(player);
        if (levels <= 0) {
            return;
        }
        SystemTelemetry.log("prestige_points", "refund_godki_effects", player, null, Map.of(
                "levels", levels,
                "points", PrestigePointsSystem.getPoints(player)
        ));
        try {
            player.m_213846_(net.minecraft.network.chat.Component.m_237113_(UltraStyle.noticeLine(
                    "God Ki Mastery moved to the Mastery wallet. Your " + levels + " God Ki level"
                            + (levels == 1 ? " was" : "s were") + " refunded as " + levels + " favor. Buy them"
                            + " back in Prestige, Shop, Mastery wallet (" + MasteryWallet.GODKI_POINTS_PER_LEVEL
                            + " points a level).")));
        } catch (Throwable ignored) {
        }
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
        // Settle the Effects refund before the level write sets its marker (4.7.22).
        refundEffectsInvestment(player);
        int before = invested(player);
        storeLevel(player, clamped);
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
     * character is not attached — the login reapply window retries. The one-time
     * Effects refund runs first, once the character is attached.
     */
    public static void reapply(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Character character = character(player);
        if (character == null) {
            return;
        }
        refundEffectsInvestment(player);
        int investments = invested(player);
        if (investments <= 0) {
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
