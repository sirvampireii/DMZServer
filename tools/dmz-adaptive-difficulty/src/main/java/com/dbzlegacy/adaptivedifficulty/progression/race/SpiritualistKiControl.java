package com.dbzlegacy.adaptivedifficulty.progression.race;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.progression.DmzSkillUtil;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionConfig;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.skills.Skills;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Port of Spirtualist Ki Control.js — grant/remove {@code kicontrol} for Spiritualist class.
 *
 * <p>4.7.53: the "LM granted it" mark is kept per character ({@link PrestigeCharacter#id}, the account
 * UUID for slot 0 or without DMZ Ultra slots). It was one flag for the whole account, so a grant on one
 * slot let {@link #remove} strip Ki Control from the next non-Spiritualist slot the player switched to,
 * even when that slot had Ki Control in its own record. Now only the character LM granted it to loses it,
 * when its class stops being Spiritualist. The old account flag ({@link #KEY_GRANTED}) never strips: a
 * confirmed Spiritualist that has Ki Control takes it over as its own mark, and it is dropped.
 */
public final class SpiritualistKiControl {
    private static final String TARGET_CLASS = "spiritualist";
    private static final String KI_CONTROL = "kicontrol";
    private static final long CLASS_CONFIRM_MS = 10_000L;
    /** 4.7.52 and older: one mark for the whole account. Read only to move it onto a Spiritualist character. */
    private static final String KEY_GRANTED = "spiritualist_granted_kicontrol";
    /** Per character: {@code spiritualist_granted_kicontrol:<character UUID>}. */
    private static final String KEY_GRANTED_PREFIX = KEY_GRANTED + ":";

    private SpiritualistKiControl() {}

    public static void pulse(ServerPlayer player, long nowMs) {
        if (!ProgressionConfig.spiritualistKi() || player == null) {
            return;
        }
        if (nowMs < ProgressionData.tempGetLong(player, "spiritualist_kicontrol_next_check", 0L)) {
            return;
        }
        ProgressionData.tempPut(player, "spiritualist_kicontrol_next_check", nowMs + 1000L);
        try {
            StatsData data = DmzProgression.stats(player);
            Character ch = data == null ? null : data.getCharacter();
            Skills skills = data == null ? null : data.getSkills();
            if (ch == null || skills == null) {
                return;
            }
            String cls = normalize(ch.getCharacterClass());
            String last = ProgressionData.tempGet(player, "spiritualist_last_detected_class", "");
            if (!cls.equals(last)) {
                ProgressionData.tempPut(player, "spiritualist_last_detected_class", cls);
                ProgressionData.tempRemove(player, "spiritualist_class_detected_at");
            }
            if (TARGET_CLASS.equals(cls)) {
                if (!isConfirmed(player, nowMs)) {
                    return;
                }
                adoptAccountMark(player, skills);
                if (grant(player, skills)) {
                    SystemTelemetry.log("progression", "spiritualist_ki_grant", player, null, Map.of());
                }
            } else if (remove(player, skills)) {
                SystemTelemetry.log("progression", "spiritualist_ki_remove", player, null, Map.of());
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isConfirmed(ServerPlayer player, long now) {
        long detectedAt = ProgressionData.tempGetLong(player, "spiritualist_class_detected_at", 0L);
        if (detectedAt <= 0L) {
            ProgressionData.tempPut(player, "spiritualist_class_detected_at", now);
            return false;
        }
        return now - detectedAt >= CLASS_CONFIRM_MS;
    }

    private static boolean grant(ServerPlayer player, Skills skills) {
        int current = DmzSkillUtil.level(skills, KI_CONTROL);
        if (current >= 1) {
            return false;
        }
        DmzSkillUtil.setLevel(skills, KI_CONTROL, 1);
        if (DmzSkillUtil.level(skills, KI_CONTROL) != 1) {
            DmzRewards.msg(player, LmChat.fail("Spirit", "DMZ rejected the Ki Control unlock."));
            return false;
        }
        ProgressionData.storedPutBool(player, grantedKey(player), true);
        DmzSkillUtil.sync(player);
        DmzRewards.msg(player, LmChat.card("Spiritualist Ability Unlocked", null, null,
                "§7Your natural connection to Ki has unlocked §bKi Control§7."));
        return true;
    }

    private static boolean remove(ServerPlayer player, Skills skills) {
        String key = grantedKey(player);
        if (!ProgressionData.storedGetBool(player, key)) {
            return false;
        }
        if (DmzSkillUtil.level(skills, KI_CONTROL) > 0) {
            DmzSkillUtil.setLevel(skills, KI_CONTROL, 0);
        }
        if (DmzSkillUtil.level(skills, KI_CONTROL) > 0) {
            return false;
        }
        ProgressionData.storedRemove(player, key);
        DmzSkillUtil.sync(player);
        DmzRewards.msg(player, LmChat.info("Spirit",
                "Ki Control was removed because your chosen class is not Spiritualist."));
        return true;
    }

    /** The mark for the character being played: only that character can lose what LM granted it. */
    static String grantedKey(ServerPlayer player) {
        return KEY_GRANTED_PREFIX + PrestigeCharacter.id(player);
    }

    /**
     * A 4.7.52 account mark says LM granted Ki Control to some character of the account, not which. A
     * confirmed Spiritualist that has Ki Control is that character as far as anyone can tell: it takes the
     * mark over. The account mark is then dropped either way and never strips anything.
     */
    private static void adoptAccountMark(ServerPlayer player, Skills skills) {
        if (!ProgressionData.storedGetBool(player, KEY_GRANTED)) {
            return;
        }
        if (DmzSkillUtil.level(skills, KI_CONTROL) >= 1) {
            ProgressionData.storedPutBool(player, grantedKey(player), true);
        }
        ProgressionData.storedRemove(player, KEY_GRANTED);
    }

    private static String normalize(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim()
                .replaceAll("\\s+", "")
                .replace("_", "")
                .replace("-", "");
    }
}
