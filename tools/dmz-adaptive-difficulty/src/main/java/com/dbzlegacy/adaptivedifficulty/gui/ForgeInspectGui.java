package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Forge staff inspect — Ultra panels with {@link AdminInspectSessions}. */
public final class ForgeInspectGui {
    private ForgeInspectGui() {}

    public static boolean open(ServerPlayer admin, ServerPlayer subject, String system) {
        if (admin == null || subject == null) {
            return false;
        }
        AdminInspectSessions.set(admin.m_20148_(), subject.m_20148_());
        admin.m_213846_(Component.m_237113_(
                UltraStyle.YELLOW
                        + "Inspecting "
                        + UltraStyle.WHITE
                        + subject.m_7755_().getString()
                        + UltraStyle.YELLOW
                        + ". Edits in this menu apply to them. "
                        + UltraStyle.DARK_GRAY
                        + "/lm admin inspect clear "
                        + UltraStyle.LABEL
                        + "to stop."));
        String sys = system == null || system.isBlank() ? "hub" : system.toLowerCase(Locale.ROOT);
        switch (sys) {
            case "hub", "lm", "main" -> UltraGui.openHub(admin, "main");
            case "difficulty", "diff" -> UltraGui.open(admin, "difficulty", "main");
            case "rival" -> UltraGui.open(admin, "rival", "main");
            case "spar", "sparring" -> UltraGui.open(admin, "spar", "main");
            case "dojo" -> {
                // 4.7.88: the subject's dojo record in chat, then their dojo menu as they see it.
                for (String line : dojoRecordLines(subject)) {
                    admin.m_213846_(Component.m_237113_(line));
                }
                UltraGui.open(admin, "dojo", "main");
            }
            case "prestige" -> UltraGui.open(admin, "prestige", "main");
            case "character", "char" -> UltraGui.open(admin, "character", "main");
            case "skillcheck" -> UltraGui.open(admin, "skillcheck", "main");
            case "progression", "prog" -> UltraGui.open(admin, "progression", "main");
            case "skills" -> UltraGui.open(admin, "skills", "core");
            case "config", "cfgedit" -> UltraGui.open(admin, "config", "modules");
            case "admin" -> UltraGui.open(admin, "admin", "admin");
            default -> {
                admin.m_213846_(Component.m_237113_(
                        UltraStyle.dangerLine("That menu cannot be inspected. Try /lm admin inspect hub.")));
                return false;
            }
        }
        return true;
    }

    /** Plain dojo record for staff inspect: name, role, style, points, clout and members, for each dojo. */
    public static java.util.List<String> dojoRecordLines(ServerPlayer subject) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String who = subject.m_7755_().getString();
        java.util.List<com.dbzlegacy.adaptivedifficulty.dojo.DojoService.Affiliation> all =
                com.dbzlegacy.adaptivedifficulty.dojo.DojoService.affiliations(subject.m_20148_());
        if (all.isEmpty()) {
            out.add(UltraStyle.LABEL + who + " is not in a dojo.");
            return out;
        }
        for (com.dbzlegacy.adaptivedifficulty.dojo.DojoService.Affiliation a : all) {
            com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo d = a.dojo();
            com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.Style st =
                    com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog.Style.of(d.style);
            int members = 1 + com.dbzlegacy.adaptivedifficulty.dojo.DojoService.disciples(d).size();
            out.add(UltraStyle.YELLOW + d.name + UltraStyle.LABEL + " (" + who + " is " + a.role().label
                    + ", master " + d.masterName + ")");
            out.add(UltraStyle.LABEL + "Style " + UltraStyle.WHITE + (st == null ? "not picked" : st.label)
                    + UltraStyle.LABEL + ", Dojo Points " + UltraStyle.WHITE + d.points
                    + UltraStyle.LABEL + ", clout " + UltraStyle.WHITE + d.clout
                    + UltraStyle.LABEL + ", members " + UltraStyle.WHITE + members);
        }
        return out;
    }

    public static boolean clear(ServerPlayer admin) {
        if (admin == null) {
            return false;
        }
        AdminInspectSessions.clear(admin.m_20148_());
        admin.m_213846_(Component.m_237113_(UltraStyle.LABEL + "Inspect cleared."));
        MechanicsMenu.open(admin, "main");
        return true;
    }
}
