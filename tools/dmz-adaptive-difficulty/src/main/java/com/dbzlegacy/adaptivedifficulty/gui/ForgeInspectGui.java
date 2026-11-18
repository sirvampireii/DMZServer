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
