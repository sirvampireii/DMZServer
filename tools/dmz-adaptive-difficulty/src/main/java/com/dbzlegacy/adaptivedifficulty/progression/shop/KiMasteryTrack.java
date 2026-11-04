package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mastery Wallet level tracks for the {@code godki} stack forms (4.7.29): God Ki Mastery feeds
 * {@code godki:godki}, Divine Ki Mastery feeds {@code godki:divineki}. Both use the God Ki shop
 * scale ({@link PrestigeShopConfig#godKi}: 10 levels of 1,000 mastery by default since 4.7.34) and cost
 * {@link MasteryWallet#GODKI_POINTS_PER_LEVEL} points a level.
 *
 * <p>The level is stored per player in the prestige record ({@link #key}), like {@code pp_godki_enh}
 * before it. Old {@code pp_godki_enh} levels are not read, converted or deleted here: every player's
 * God Ki track starts at 0. {@link #reapply} raises the form's stack mastery to {@code level x 1,000}
 * and never lowers it, so mastery earned by fighting is kept and a DMZ reset is topped back up on
 * login. The track is a mastery accelerator only: it grants no skill and does not unlock the forms.
 *
 * <p>Divine Ki Mastery is offered once God Ki is mastered: God Ki level at its cap, or
 * {@code godki:godki} mastery at the full scale (10,000 by default).
 */
public enum KiMasteryTrack {
    GOD_KI("pp_godki_form", "godki", "God Ki"),
    DIVINE_KI("pp_divineki", "divineki", "Divine Ki");

    /** DMZ stack form group both tracks feed ({@code config/dragonminez/forms/godki.json}). */
    public static final String GROUP = "godki";

    /** Prestige-record key holding this track's level. */
    public final String key;
    /** Stack form inside {@link #GROUP} this track feeds. */
    public final String form;
    /** Player-facing name ("God Ki", "Divine Ki"). */
    public final String name;

    KiMasteryTrack(String key, String form, String name) {
        this.key = key;
        this.form = form;
        this.name = name;
    }

    /** Wallet line title, e.g. "God Ki Mastery". */
    public String label() {
        return name + " Mastery";
    }

    public static int maxLevel() {
        return Math.max(0, GodKiEnhancementShop.maxInvestments());
    }

    public static int masteryPerLevel() {
        return Math.max(1, GodKiEnhancementShop.masteryPerPoint());
    }

    /** Stored level, 0 to {@link #maxLevel()}. */
    public int level(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(maxLevel(), ProgressionData.storedGetLong(player, key, 0L)));
    }

    /**
     * Store the next level and raise the form's mastery to it. The caller has checked the cap, the
     * points and that the record is writable.
     *
     * @return the new level
     */
    public int levelUp(ServerPlayer player) {
        int next = Math.min(maxLevel(), level(player) + 1);
        ProgressionData.storedPut(player, key, next);
        reapply(player);
        return next;
    }

    /** Live {@code godki:<form>} stack mastery (0 when the character is not attached). */
    public double formMastery(ServerPlayer player) {
        FormMasteries masteries = stackMasteries(player);
        try {
            return masteries == null ? 0.0 : masteries.getMastery(GROUP, form);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /** Divine Ki Mastery is offered once God Ki is mastered (level cap or full God Ki mastery). */
    public static boolean divineKiOpen(ServerPlayer player) {
        int max = maxLevel();
        return max > 0 && (GOD_KI.level(player) >= max
                || GOD_KI.formMastery(player) >= (double) max * masteryPerLevel() - 1.0E-9);
    }

    /** Login / respawn top-up for every track with a level. */
    public static void reapplyAll(ServerPlayer player) {
        for (KiMasteryTrack track : values()) {
            track.reapply(player);
        }
    }

    /** Raise {@code godki:<form>} to level x mastery per level; never lowers a higher record. */
    public void reapply(ServerPlayer player) {
        int lv = level(player);
        if (lv <= 0) {
            return;
        }
        FormMasteries masteries = stackMasteries(player);
        if (masteries == null) {
            return;
        }
        double expected = (double) lv * masteryPerLevel();
        try {
            if (masteries.getMastery(GROUP, form) >= expected) {
                return;
            }
            masteries.setMastery(GROUP, form, expected, Math.max(expected, configCap()));
            DmzSkillUtil.sync(player);
        } catch (Throwable ignored) {
        }
    }

    /** {@code maxMastery} of this form in godki.json (0 when unreadable). */
    private double configCap() {
        try {
            FormConfig group = ConfigManager.getStackFormGroup(GROUP);
            FormConfig.FormData data = group == null ? null : group.getForm(form);
            Double max = data == null ? null : data.getMaxMastery();
            return max == null ? 0.0 : max;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static FormMasteries stackMasteries(ServerPlayer player) {
        try {
            Character character = player == null ? null : DmzProgression.character(player);
            return character == null ? null : character.getStackFormMasteries();
        } catch (Throwable t) {
            return null;
        }
    }
}
