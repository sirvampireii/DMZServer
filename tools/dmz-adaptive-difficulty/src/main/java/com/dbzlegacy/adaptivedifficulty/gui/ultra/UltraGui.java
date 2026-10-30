package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.AdminInspectSessions;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ultra / KaiPath entry. Every system opens via {@code Ultra*Screens} -> {@code GuiOpenPacket}.
 * Players without KaiPath get an install message - there is no CNPC fallback.
 *
 * <p>Staff inspect ({@link AdminInspectSessions}): menu data is built from the subject; the
 * GuiOpenPacket is always delivered to the clicking viewer.
 */
public final class UltraGui {
    private UltraGui() {}

    public static boolean preferUltra(ServerPlayer player) {
        return player != null && LmGuiNetwork.clientHasKaiPath(player);
    }

    /**
     * Open a system page on Ultra ({@code GuiOpenPacket}). If KaiPath is missing, tell the
     * player to install it and return without opening another GUI backend.
     */
    public static void open(ServerPlayer player, String system, String page) {
        if (player == null) {
            return;
        }
        String logical = UltraScreenIds.normalizeLogical(system);
        String pg = normalizePage(page);

        // KaiPath must be on the viewer (staff), not the inspect subject.
        if (!preferUltra(player)) {
            LmLog.event(
                    "[{}] Ultra refused (KaiPath missing) player={} system={} page={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(player),
                    logical,
                    pg);
            DmzRewards.msg(
                    player,
                    UltraStyle.dangerLine(
                            "Install KaiPath (DMZ Legacy client mod) to open Legacy Mechanics menus."));
            return;
        }

        ServerPlayer effective = AdminInspectSessions.resolveSubject(player);
        if (effective == null) {
            effective = player;
        }
        openUltra(player, effective, logical, pg);
    }

    /**
     * Ultra primary path: always an {@code Ultra*Screens} builder -> {@code GuiOpenPacket}.
     *
     * @param viewer staff (or player) who receives the packet
     * @param subject player whose stats/settings populate the menu ({@code viewer} when not inspecting)
     */
    static void openUltra(
            ServerPlayer viewer, ServerPlayer subject, String logical, String page) {
        if (viewer == null) {
            return;
        }
        ServerPlayer data = subject == null ? viewer : subject;
        String id = logical == null || logical.isBlank() ? "hub" : logical;
        String pg = normalizePage(page);
        AdaptiveDifficultyMod.LOGGER.debug(
                "[{}] Ultra open screenId={} page={} viewer={} subject={} inspecting={}",
                AdaptiveDifficultyMod.MOD_ID,
                id,
                pg,
                safeName(viewer),
                safeName(data),
                AdminInspectSessions.isInspecting(viewer.m_20148_()));
        UltraScreenSpec.bindInspectViewer(viewer);
        try {
            switch (id) {
                case "hub" -> UltraHubScreens.open(data, pg);
                // Staff-global screens stay on the viewer (config drafts / admin tools).
                case "admin" -> UltraHubScreens.open(viewer, "admin");
                case "difficulty" -> UltraDifficultyScreens.open(data, pg);
                case "rival" -> UltraRivalScreens.open(data, pg);
                case "spar" -> UltraSparScreens.open(data, pg);
                case "prestige" -> UltraPrestigeScreens.open(data, pg);
                case "character" -> UltraCharacterScreens.open(data, pg);
                case "skillcheck" -> UltraSkillCheckScreens.open(data, pg);
                case "skills" -> UltraSkillCheckScreens.openSkillsAdmin(data, pg);
                case "progression" -> UltraProgressionScreens.open(data, pg);
                case "config" -> UltraConfigScreens.open(viewer, pg);
                default -> {
                    AdaptiveDifficultyMod.LOGGER.warn(
                            "[{}] Ultra unknown logical screenId=\"{}\" page=\"{}\" - opening hub",
                            AdaptiveDifficultyMod.MOD_ID,
                            id,
                            pg);
                    UltraHubScreens.open(data, "main");
                }
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra openUltra failed screenId={} page={} viewer={} subject={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    id,
                    pg,
                    safeName(viewer),
                    safeName(data),
                    t);
        } finally {
            UltraScreenSpec.clearInspectViewer();
        }
    }

    public static void openHub(ServerPlayer player, String page) {
        open(player, "hub", page);
    }

    private static String normalizePage(String page) {
        if (page == null || page.isBlank()) {
            return "main";
        }
        // Keep colon pages (e.g. saga_confirm:id, module:foo) case-sensitive trim.
        if (page.indexOf(':') >= 0) {
            return page.trim();
        }
        return page.toLowerCase(Locale.ROOT);
    }

    private static String safeName(ServerPlayer player) {
        if (player == null) {
            return "?";
        }
        try {
            return player.m_7755_().getString();
        } catch (Throwable ignored) {
            return "?";
        }
    }
}
