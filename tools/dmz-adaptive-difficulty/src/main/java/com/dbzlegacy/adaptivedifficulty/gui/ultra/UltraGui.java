package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.AdminInspectSessions;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/**
 * Menu entry. Every system opens via {@code Ultra*Screens} -> {@code GuiOpenPacket}, drawn by the
 * Legacy Mechanics client. LM 4.8.2: the client is mandatory and the KaiPath bridge is gone; a player
 * without the LM client gets the install message - there is no CNPC or KaiPath fallback.
 *
 * <p>The client gate never blocks building: while {@link UltraScreenSpec#captureSends} runs (boot
 * self-tests, the UI probe's crawls and its Forge fake-player stand-ins, which never have a client), the
 * page is built and captured whatever the viewer's client. Real sends are gated again at the network
 * ({@code LmGuiNetwork.sendOpen}).
 *
 * <p>Staff inspect ({@link AdminInspectSessions}): menu data is built from the subject; the
 * GuiOpenPacket is always delivered to the clicking viewer.
 */
public final class UltraGui {
    private UltraGui() {}

    /**
     * 4.7.84 (N2): set while a click made inside a menu is being handled ({@link UltraGuiActions#dispatch} with the
     * page it came from). Any other {@link #open} (a hub tile from a command, {@code /lm}, a key, an NPC, a timer such
     * as the spar result) starts a fresh Back trail.
     */
    private static final ThreadLocal<int[]> MENU_CLICK = ThreadLocal.withInitial(() -> new int[1]);

    /** Runs {@code r} as the handling of a click inside a menu: pages it opens keep the player's Back trail. */
    public static void runAsMenuClick(Runnable r) {
        int[] depth = MENU_CLICK.get();
        depth[0]++;
        try {
            r.run();
        } finally {
            depth[0]--;
        }
    }

    /** True while a click made inside a menu is being handled on this thread. */
    public static boolean inMenuClick() {
        return MENU_CLICK.get()[0] > 0;
    }

    /** 4.8.2: true when this player can see LM menus: their client has the Legacy Mechanics client mod. */
    public static boolean preferUltra(ServerPlayer player) {
        return player != null && LmNativeNetwork.hasNativeClient(player);
    }

    /**
     * Open a system page ({@code GuiOpenPacket}). If the viewer has no Legacy Mechanics client, tell them
     * to install it and return without opening anything (unless a capture is running: then the page builds).
     */
    public static void open(ServerPlayer player, String system, String page) {
        if (player == null) {
            return;
        }
        String logical = UltraScreenIds.normalizeLogical(system);
        String pg = normalizePage(page);

        // The LM client must be on the viewer (staff), not the inspect subject. A capture builds regardless.
        if (!UltraScreenSpec.capturing() && !preferUltra(player)) {
            LmLog.event(
                    "[{}] menu refused (no Legacy Mechanics client) player={} system={} page={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(player),
                    logical,
                    pg);
            DmzRewards.msg(player, UltraStyle.dangerLine(LmNativeNetwork.requiredClientMessage()));
            return;
        }

        // N2: opened from outside a menu: a new trail, so Back never names a page from an earlier visit.
        if (!inMenuClick()) {
            NavHistory.fresh(player.m_20148_());
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
                case "admin" -> UltraHubScreens.open(viewer,
                        UltraHubScreens.PAGE_PRESTIGE_SHOP_EDIT.equalsIgnoreCase(pg) ? pg : "admin");
                case "difficulty" -> UltraDifficultyScreens.open(data, pg);
                case "rival" -> UltraRivalScreens.open(data, pg);
                case "spar" -> UltraSparScreens.open(data, pg);
                case "dojo" -> UltraDojoScreens.openSystem(data, pg);
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
