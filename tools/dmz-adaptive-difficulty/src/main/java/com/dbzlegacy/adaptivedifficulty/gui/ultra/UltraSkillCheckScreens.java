package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/**
 * Skill Check (donator) and Skills (staff) - Ultra port of {@code CnpcLmSkillCheckGui}.
 *
 * <p>Single screen (no tab bar). Human labels only.
 */
public final class UltraSkillCheckScreens {
    private UltraSkillCheckScreens() {}

    /** Donator Skill Check. */
    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        if (!SkillCheckService.canUse(player) && !SkillCheckService.inSession(player)) {
            // Menu feedback stays in the menu: the hub opens with this notice on it.
            UltraGuiFeedback.deliver(
                    player,
                    UltraStyle.noticeLine(
                            "Skill Check isn't available to you. Ask staff if you think that's wrong."));
            UltraHubScreens.open(player, "main");
            return;
        }
        show(player, false);
    }

    /** Staff skill-unlock browser (not the donator Skill Check). */
    public static void openSkillsAdmin(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        if (!StaffAccess.isStaff(player)) {
            UltraGuiFeedback.deliver(
                    player, UltraStyle.noticeLine("Skills admin is for staff only. Ask staff if you need a skill changed."));
            UltraHubScreens.open(player, "main");
            return;
        }
        show(player, true);
    }

    private static void show(ServerPlayer player, boolean staffAdmin) {
        String screenId = staffAdmin ? "skills" : "skillcheck";
        String title = staffAdmin ? "Skills" : "Skill Check";
        // Compact skills-only list (no DMZ level / Ki / Energy header).
        List<String> lines = ProgressionGuiApi.skillsLines(player, "skillcheck");
        UltraScreenSpec spec = UltraScreenSpec.of(screenId)
                .title(title)
                .page("main")
                .activeTab("home")
                .lineGap(2)
                .bodyPad(4)
                .line(UltraStyle.LABEL + "Your skills");
        if (skillListMissing(lines)) {
            spec.line(
                    UltraStyle.dim(
                            "No skills listed. Ask staff to turn skill unlock on."));
        } else {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    spec.line(line);
                }
            }
        }
        if (staffAdmin) {
            spec.line(UltraStyle.LABEL + "Staff: /skills - Donators: /skillcheck");
        }
        // Dense list - band follows wired line count; extra skills scroll.
        spec.buttonsAfterLines();
        spec.footerBackToMain();
        spec.send(player);
    }

    /** A one-line service error is not a skill list. */
    private static boolean skillListMissing(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return true;
        }
        if (lines.size() != 1) {
            return false;
        }
        String plain = UltraStyle.plain(lines.get(0)).toLowerCase(Locale.ROOT);
        return plain.contains("error") || plain.contains("disabled");
    }
}
