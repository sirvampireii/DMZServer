package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.AdminInspectSessions;
import com.dbzlegacy.adaptivedifficulty.gui.CharacterServicesGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.DifficultyTeamGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.MechanicsGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi;
import com.dbzlegacy.adaptivedifficulty.progression.race.AndroidConversion;
import com.dbzlegacy.adaptivedifficulty.quest.SagaResetService;
import com.dbzlegacy.adaptivedifficulty.service.DifficultyActions;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/** Dispatches KaiPath GuiActionPacket presses to existing server menu APIs. */
public final class UltraGuiActions {
    private UltraGuiActions() {}

    public static void dispatch(ServerPlayer player, String screenId, String actionId, String argsJson) {
        if (player == null) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra action dropped: null player screenId={} actionId={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    screenId,
                    actionId);
            return;
        }
        if (actionId == null || actionId.isBlank()) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra action missing actionId screenId={} args={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    screenId,
                    argsJson);
            return;
        }

        JsonObject args = parse(argsJson);
        if (args == null) {
            args = new JsonObject();
        }
        Qualified q = qualify(screenId, actionId, args);
        if (q == null) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra qualify returned null screenId={} actionId={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    screenId,
                    actionId);
            return;
        }
        String screen = q.screen() == null ? "hub" : q.screen();
        String action = q.action() == null ? "" : q.action();

        ServerPlayer subject = AdminInspectSessions.resolveSubject(player);
        if (subject == null) {
            subject = player;
        }
        AdaptiveDifficultyMod.LOGGER.debug(
                "[{}] Ultra action viewer={} subject={} inspecting={} rawActionId={} screen={} resolvedAction={} args={}",
                AdaptiveDifficultyMod.MOD_ID,
                safeName(player),
                safeName(subject),
                AdminInspectSessions.isInspecting(player.m_20148_()),
                actionId,
                screen,
                action,
                args);

        // Trust boundary: staff gate is always on the clicking viewer.
        if (rejectStaffGated(player, screen, action, actionId)) {
            return;
        }

        try {
            dispatchResolved(player, subject, screen, action, args, actionId);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] UltraGuiActions.handle failed rawActionId={} screen={} resolvedAction={} args={} viewer={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    actionId,
                    screen,
                    action,
                    args,
                    safeName(player),
                    t);
            // Unexpected errors: show on hub (menu context) rather than chat.
            deliverFeedback(
                    player,
                    UltraStyle.dangerLine(
                            "Something went wrong. Try again, or ask staff if it keeps happening."));
            UltraGui.openHub(player, "main");
        }
    }

    private static void dispatchResolved(
            ServerPlayer viewer,
            ServerPlayer subject,
            String screen,
            String action,
            JsonObject args,
            String rawActionId) {
        if (viewer == null) {
            return;
        }
        ServerPlayer data = subject == null ? viewer : subject;
        if (action == null || action.isBlank()) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra blank resolved action screen={} rawActionId={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    screen,
                    rawActionId);
            return;
        }
        if (args == null) {
            args = new JsonObject();
        }
        String safeScreen = screen == null || screen.isBlank() ? "hub" : screen;

        // Defense in depth - same gate as dispatch() (covers any future call sites).
        if (rejectStaffGated(viewer, safeScreen, action, rawActionId)) {
            return;
        }

        // Keep viewer bound for do-handlers that open Ultra screens without UltraGui.open
        // (e.g. weight shop confirm) so packets still go to staff during inspect.
        UltraScreenSpec.bindInspectViewer(viewer);
        try {
            dispatchResolvedBound(viewer, data, safeScreen, action, args, rawActionId);
        } finally {
            UltraScreenSpec.clearInspectViewer();
        }
    }

    private static void dispatchResolvedBound(
            ServerPlayer viewer,
            ServerPlayer data,
            String safeScreen,
            String action,
            JsonObject args,
            String rawActionId) {

        // KaiPath sometimes sends bare system names (e.g. "character") from the hub
        // instead of open:character / @hub:open:character.
        if (!hasVerbPrefix(action) && isHubScreen(safeScreen)) {
            String openTarget = bareHubOpenTarget(action);
            if (openTarget != null) {
                AdaptiveDifficultyMod.LOGGER.debug(
                        "[{}] Ultra bare hub action rawActionId={} -> open:{}",
                        AdaptiveDifficultyMod.MOD_ID,
                        rawActionId,
                        openTarget);
                UltraGui.open(viewer, openTarget, "main");
                return;
            }
        }

        if (action.startsWith("tab:")) {
            String tab = action.length() > 4 ? action.substring(4) : "";
            if (tab.isBlank()) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] Ultra empty tab: screen={} rawActionId={}",
                        AdaptiveDifficultyMod.MOD_ID,
                        safeScreen,
                        rawActionId);
                return;
            }
            // Same screenId, updated page - never CNPC, never a different system.
            String page = tabPage(safeScreen, tab);
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] Ultra tab: screen={} tab={} -> page={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeScreen,
                    tab,
                    page);
            UltraGui.open(viewer, safeScreen, page);
            return;
        }
        if (action.startsWith("page:")) {
            String page = action.length() > 5 ? action.substring(5) : "";
            if (page.isBlank()) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] Ultra empty page: screen={} rawActionId={}",
                        AdaptiveDifficultyMod.MOD_ID,
                        safeScreen,
                        rawActionId);
                return;
            }
            UltraGui.open(viewer, safeScreen, page);
            return;
        }
        if ("nav:hub".equals(action)) {
            UltraGui.openHub(viewer, "main");
            return;
        }
        if ("nav:home".equals(action)) {
            UltraGui.open(viewer, safeScreen, homePage(safeScreen));
            return;
        }
        if ("nav:refresh".equals(action)) {
            String page = stringArg(args, "page");
            if (page.isBlank()) {
                page = homePage(safeScreen);
            }
            UltraGui.open(viewer, safeScreen, page);
            return;
        }
        if (action.startsWith("open:")) {
            String target = action.length() > 5 ? action.substring(5) : "";
            if (target.isBlank()) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] Ultra empty open: screen={} rawActionId={}",
                        AdaptiveDifficultyMod.MOD_ID,
                        safeScreen,
                        rawActionId);
                return;
            }
            // Always Ultra GuiOpenPacket for the target system (no CNPC submenu fallback).
            UltraGui.open(viewer, target, "main");
            return;
        }

        // Text field Enter / submit (GuiOpenPacket field 11). Id is input:<payload>.
        if (action.startsWith("input:")) {
            String payload = action.length() > 6 ? action.substring(6) : "";
            String inputPage = stringArg(args, "page");
            if (inputPage.isBlank()) {
                inputPage = homePage(safeScreen);
            }
            if ("config".equals(safeScreen)) {
                if (!args.has("arg") || args.get("arg").isJsonNull()) {
                    args.addProperty("arg", payload);
                }
                // Config drafts are staff-scoped — always the viewer.
                String msg =
                        UltraConfigScreens.handleAction(viewer, "set", payload, inputPage, args);
                deliverFeedback(viewer, msg);
                UltraGui.open(viewer, safeScreen, inputPage);
                return;
            }
            if (("spar".equals(safeScreen) || "sparring".equals(safeScreen) || "difficulty".equals(safeScreen))
                    && ("weight_value".equals(payload) || inputPage.startsWith("weights_input:"))) {
                String msg = UltraWeightShopScreens.handleInputSubmit(data, inputPage, args);
                if (msg != null && !msg.isBlank()) {
                    deliverFeedback(viewer, msg);
                    // Validation failed - stay on the input page (always Sparring now).
                    UltraGui.open(viewer, "spar", inputPage);
                }
                // Success opens confirm inside handleInputSubmit; skip generic reopen.
                return;
            }
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra input: on unsupported screen={} payload={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeScreen,
                    payload);
            deliverFeedback(viewer, UltraStyle.dangerLine("That text field is not available."));
            UltraGui.open(viewer, safeScreen, homePage(safeScreen));
            return;
        }

        // Hub dim tile when the player lacks Skill Check access.
        if ("do:skillcheck_denied".equals(action) || "skillcheck_denied".equals(action)) {
            deliverFeedback(
                    viewer,
                    UltraStyle.noticeLine(
                            "Skill Check is a donator perk - ask staff if you want access."));
            UltraGui.openHub(viewer, "main");
            return;
        }

        // Diagnostic button from /lm admin guitest
        if ("do:guitest_ack".equals(action) || "guitest_ack".equals(action)) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] guitest_ack received viewer={} rawActionId={} screen={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(viewer),
                    rawActionId,
                    safeScreen);
            deliverFeedback(
                    viewer, UltraStyle.noticeLine("Ultra guitest: click reached the server."));
            UltraGui.open(viewer, "admin", "admin");
            return;
        }

        DoParts parts = parseDo(action);
        if (parts == null || parts.action == null || parts.action.isBlank()) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra empty do-action screen={} rawActionId={} resolvedAction={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeScreen,
                    rawActionId,
                    action);
            deliverFeedback(viewer, UltraStyle.dangerLine("That menu action is not available."));
            UltraGui.open(viewer, safeScreen, homePage(safeScreen));
            return;
        }
        // Prefer arg encoded in the action id (do:action:arg...); fall back to argsJson.
        if (parts.arg != null && !parts.arg.isBlank()) {
            args.addProperty("arg", parts.arg);
        } else if (!args.has("arg") || args.get("arg") == null || args.get("arg").isJsonNull()) {
            args.addProperty("arg", "");
        }

        String msg = runDo(viewer, data, safeScreen, parts.action, args, rawActionId);
        // Race full-wipe / free class picker opens the DMZ editor - do not fight it with Ultra.
        if ("character".equals(safeScreen)
                && "race_confirm".equals(parts.action)
                && opensExternalEditor(msg)) {
            return;
        }
        deliverFeedback(viewer, msg);
        // These handlers open the next page themselves when they return blank.
        // Non-blank means they did not open a page — fall through so feedback paints.
        if (("saga_confirm".equals(parts.action)
                        || "weight_quote".equals(parts.action)
                        || "quote".equals(parts.action)
                        || "weight_buy".equals(parts.action)
                        || "buy_weight".equals(parts.action))
                && (msg == null || msg.isBlank())) {
            return;
        }
        // Mutual slots full after Accept/Declare — land on the replace picker (not Requests/Actions).
        if ("rival".equals(safeScreen)
                && ("accept".equals(parts.action) || "declare".equals(parts.action))
                && RivalGuiApi.needsMutualReplacePick(data)) {
            UltraGui.open(viewer, "rival", "pick_replace_mutual");
            return;
        }
        String reopen = reopenPage(safeScreen, parts, args);
        UltraGui.open(
                viewer,
                safeScreen,
                reopen == null || reopen.isBlank() ? homePage(safeScreen) : reopen);
    }

    /** Menu-action outcomes go on the next Ultra page (or chat when guiBackend=chat). */
    private static void deliverFeedback(ServerPlayer viewer, String msg) {
        UltraGuiFeedback.deliver(viewer, msg);
    }

    /** True when race change opened the DMZ creation / class-pick editor. */
    private static boolean opensExternalEditor(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String plain = UltraStyle.plain(msg).toLowerCase(Locale.ROOT);
        return plain.contains("opening character setup")
                || plain.contains("choose a new class");
    }

    /** True when action already uses a protocol verb. */
    static boolean hasVerbPrefix(String action) {
        return UltraScreenIds.hasAllowedVerbPrefix(action);
    }

    static boolean isHubScreen(String screen) {
        if (screen == null || screen.isBlank()) {
            return true;
        }
        String s = screen.toLowerCase(Locale.ROOT);
        return "hub".equals(s) || "lm".equals(s) || "legacymechanics".equals(s) || "main".equals(s);
    }

    /**
     * Staff-only Ultra systems. Non-staff must not open these or run actions on them,
     * even if a forged {@code GuiActionPacket} names them.
     */
    static boolean isStaffOnlySystem(String system) {
        if (system == null || system.isBlank()) {
            return false;
        }
        String id = UltraScreenIds.normalizeLogical(system);
        return "admin".equals(id)
                || "config".equals(id)
                || "progression".equals(id)
                || "skills".equals(id);
    }

    /**
     * Whether this resolved screen/action requires {@link StaffAccess#isStaff}.
     * Covers staff screens, {@code open:} / bare opens to them, {@code do:admin:*},
     * hub reload/syslog/migrate, and guitest ack.
     */
    static boolean requiresStaff(String screen, String action) {
        if (isStaffOnlySystem(screen)) {
            return true;
        }
        if (action == null || action.isBlank()) {
            return false;
        }
        String a = action.trim();
        if (a.startsWith("open:")) {
            return isStaffOnlySystem(a.substring(5));
        }
        if (!hasVerbPrefix(a)) {
            String bare = UltraScreenIds.bareOpenTarget(a);
            return bare != null && isStaffOnlySystem(bare);
        }
        if ("do:guitest_ack".equals(a) || "guitest_ack".equals(a)) {
            return true;
        }
        // Difficulty Combat Rating tab/page — staff only (hidden from player tab row).
        if ("difficulty".equalsIgnoreCase(screen == null ? "" : screen)) {
            String lower = a.toLowerCase(Locale.ROOT);
            if (lower.equals("tab:stats")
                    || lower.equals("page:stats")
                    || lower.endsWith(":tab:stats")
                    || lower.endsWith(":page:stats")
                    || lower.equals("stats")
                    || lower.equals("statistics")
                    || lower.equals("details")
                    || lower.equals("cr")
                    || lower.equals("combat_rating")) {
                return true;
            }
        }
        if (!a.startsWith("do:")) {
            return false;
        }
        DoParts parts = parseDo(a);
        if (parts == null || parts.action == null || parts.action.isBlank()) {
            return false;
        }
        String act = parts.action.toLowerCase(Locale.ROOT);
        return switch (act) {
            case "admin",
                    "reload",
                    "reloadconfig",
                    "syslog",
                    "migrate-cnpc",
                    "migratecnpc",
                    "cnpcmigrate",
                    "guitest_ack",
                    // Staff convert only - android_remove stays player-facing.
                    "android",
                    "android_staff" -> true;
            default -> false;
        };
    }

    /**
     * @return {@code true} when the action was rejected (caller must stop)
     */
    static boolean rejectStaffGated(
            ServerPlayer player, String screen, String action, String rawActionId) {
        if (!requiresStaff(screen, action)) {
            return false;
        }
        if (player != null && StaffAccess.isStaff(player)) {
            return false;
        }
        AdaptiveDifficultyMod.LOGGER.warn(
                "[{}] Ultra staff action denied player={} screen={} action={} rawActionId={}",
                AdaptiveDifficultyMod.MOD_ID,
                safeName(player),
                screen,
                action,
                rawActionId);
        if (player != null) {
            deliverFeedback(player, UltraStyle.dangerLine("That menu action is not available."));
            UltraGui.openHub(player, "main");
        }
        return true;
    }

    /**
     * Map a bare hub tile id to an {@code open:} target, or null if not a known system.
     * Zero-config: every {@link UltraScreenIds#PROTOCOL_IDS} / {@link UltraScreenIds#BUILDER_IDS}
     * name (plus aliases) resolves without a per-system switch here.
     */
    static String bareHubOpenTarget(String action) {
        return UltraScreenIds.bareOpenTarget(action);
    }

    /**
     * @param viewer clicking staff/player (notices + reopen + staff gates)
     * @param subject inspect target for player-scoped edits (may equal {@code viewer})
     */
    private static String runDo(
            ServerPlayer viewer,
            ServerPlayer subject,
            String screen,
            String act,
            JsonObject args,
            String rawActionId) {
        if (viewer == null || act == null || act.isBlank()) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra empty act screen={} rawActionId={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    screen,
                    rawActionId);
            return UltraStyle.dangerLine("That menu action is not available.");
        }
        if (args == null) {
            args = new JsonObject();
        }
        String arg = stringArg(args, "arg");
        String page = stringArg(args, "page");
        if (page.isBlank()) {
            page = homePage(screen);
        }
        String safeScreen = screen == null ? "" : screen;
        // Player-scoped edits apply to the subject; staff-as-self ops stay on the viewer.
        ServerPlayer actor = AdminInspectSessions.resolveActor(viewer, act);
        if (actor == null) {
            actor = viewer;
        }
        ServerPlayer data = subject == null ? viewer : subject;
        String result;
        try {
            result = switch (safeScreen) {
                case "difficulty" -> {
                    if ("mode".equals(act) || "set_mode".equals(act) || "team".equals(act)) {
                        yield nullToEmpty(
                                DifficultyTeamGuiApi.handleDo(data, "mode", arg, page));
                    }
                    // Staff tools on the difficulty admin sub-page.
                    if ("reload".equals(act)
                            || "reloadconfig".equals(act)
                            || "syslog".equals(act)) {
                        yield nullToEmpty(MechanicsGuiApi.handleDo(actor, act, arg, page));
                    }
                    DifficultyActions.Result r =
                            DifficultyActions.handleArgNoReopen(data, act, arg, page);
                    if (r == null) {
                        yield "";
                    }
                    yield r.message() == null ? "" : r.message();
                }
                case "rival" -> nullToEmpty(RivalGuiApi.handleDo(data, act, arg, page));
                case "spar", "sparring" -> {
                    if ("weight_quote".equals(act)
                            || "quote".equals(act)
                            || "weight_buy".equals(act)
                            || "buy_weight".equals(act)) {
                        yield nullToEmpty(
                                UltraWeightShopScreens.handleAction(
                                        data, act, arg, page, args));
                    }
                    yield nullToEmpty(SparGuiApi.handleDo(data, act, arg, page));
                }
                case "prestige" ->
                        nullToEmpty(ProgressionGuiApi.handlePrestigeDo(data, act, arg, page));
                case "character", "saga" -> characterDo(viewer, data, act, arg, page);
                case "progression" -> nullToEmpty(ProgressionGuiApi.handleDo(data, act, arg, page));
                case "config" ->
                        nullToEmpty(UltraConfigScreens.handleAction(viewer, act, arg, page, args));
                case "hub", "admin" -> nullToEmpty(MechanicsGuiApi.handleDo(actor, act, arg, page));
                case "skillcheck", "skills" -> "";
                case "team" -> nullToEmpty(DifficultyTeamGuiApi.handleDo(data, act, arg, page));
                default -> null;
            };
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra runDo threw screen={} action={} arg={} page={} rawActionId={} viewer={} subject={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeScreen,
                    act,
                    arg,
                    page,
                    rawActionId,
                    safeName(viewer),
                    safeName(data),
                    t);
            return UltraStyle.dangerLine(
                    "Something went wrong. Try again, or ask staff if it keeps happening.");
        }
        if (result == null) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra unrecognized screen=\"{}\" rawActionId=\"{}\" action=\"{}\" arg=\"{}\"",
                    AdaptiveDifficultyMod.MOD_ID,
                    screen,
                    rawActionId,
                    act,
                    arg);
            return UltraStyle.dangerLine("That menu action is not available.");
        }
        if (isGenericFailure(result)) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra handler generic failure screen=\"{}\" rawActionId=\"{}\" action=\"{}\" arg=\"{}\" msg={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    screen,
                    rawActionId,
                    act,
                    arg,
                    UltraStyle.plain(result));
        }
        return result;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean isGenericFailure(String msg) {
        if (msg == null) {
            return false;
        }
        String plain = UltraStyle.plain(msg).toLowerCase();
        return plain.contains("something went wrong");
    }

    private static String characterDo(
            ServerPlayer viewer, ServerPlayer subject, String action, String arg, String page) {
        if (viewer == null) {
            return UltraStyle.dangerLine("This only works in-game.");
        }
        ServerPlayer data = subject == null ? viewer : subject;
        if (action == null || action.isBlank()) {
            return UltraStyle.dangerLine("That menu action is not available.");
        }
        String act = action.toLowerCase(Locale.ROOT).trim();
        // Staff picker confirm. The page already named the target, so apply immediately.
        if ("android_staff".equals(act)) {
            if (!StaffAccess.isStaff(viewer)) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] Ultra android staff action denied for non-staff viewer={}",
                        AdaptiveDifficultyMod.MOD_ID,
                        safeName(viewer));
                return UltraStyle.dangerLine("That menu action is not available.");
            }
            return applyAndroidStaff(viewer, arg);
        }
        // Paid self-conversion from the Android tab (player-facing). It charges whoever clicked,
        // so it never runs while staff are inspecting another player's menu.
        if ("android_self".equals(act)) {
            if (subject != null && subject != viewer) {
                return UltraStyle.dangerLine(
                        "That converts you, not the player you are viewing. Use Another player instead.");
            }
            String msg = com.dbzlegacy.adaptivedifficulty.character.CharacterServicesSystem
                    .executeAndroidConversion(viewer);
            return msg == null ? "" : msg;
        }
        // Staff convert only - Removal stays available to players. Staff gate = viewer.
        if ("android".equals(act)) {
            if (!StaffAccess.isStaff(viewer)) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] Ultra android convert denied for non-staff viewer={}",
                        AdaptiveDifficultyMod.MOD_ID,
                        safeName(viewer));
                return UltraStyle.dangerLine("That menu action is not available.");
            }
            String msg = ProgressionGuiApi.handleDo(data, act, arg, page);
            return msg == null ? "" : msg;
        }
        if (UltraStyle.PAGE_ANDROID_REMOVE.equals(act) || "android_remove".equals(act)) {
            String msg = ProgressionGuiApi.handleDo(data, UltraStyle.PAGE_ANDROID_REMOVE, arg, page);
            return msg == null ? "" : msg;
        }
        if ("saga_confirm".equals(act)) {
            String id = arg == null ? "" : arg.trim();
            if (id.isBlank()) {
                return UltraStyle.dangerLine("Choose a saga first.");
            }
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] Ultra saga_confirm viewer={} subject={} sagaId={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(viewer),
                    safeName(data),
                    id);
            UltraGui.open(viewer, "character", "saga_confirm:" + id);
            return "";
        }
        if ("saga_reset".equals(act) || "saga".equals(act) || "reset_saga".equals(act)) {
            String id = arg == null ? "" : arg.trim();
            if (id.isBlank()) {
                return UltraStyle.dangerLine("Choose a saga first.");
            }
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Ultra saga_reset viewer={} subject={} sagaId={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(viewer),
                    safeName(data),
                    id);
            String result = SagaResetService.reset(data, id);
            return result == null ? "" : result;
        }
        String msg = CharacterServicesGuiApi.handleDo(data, action, arg, page);
        return msg == null ? "" : msg;
    }

    /**
     * {@code arg} is {@code convert:uuid:…} or {@code remove:uuid:…} from the named confirm page.
     * Calls conversion directly so the page confirm is the only confirmation.
     */
    private static String applyAndroidStaff(ServerPlayer viewer, String arg) {
        String raw = arg == null ? "" : arg.trim();
        boolean convert;
        String targetArg;
        if (raw.regionMatches(true, 0, "convert:", 0, 8)) {
            convert = true;
            targetArg = raw.substring(8).trim();
        } else if (raw.regionMatches(true, 0, "remove:", 0, 7)) {
            convert = false;
            targetArg = raw.substring(7).trim();
        } else {
            return UltraStyle.dangerLine("Choose Convert Android or Remove Android first.");
        }
        ServerPlayer target = RivalGuiApi.resolveOnline(viewer, targetArg);
        if (target == null) {
            return UltraStyle.dangerLine("That player is not online.");
        }
        if (convert) {
            String msg = AndroidConversion.convert(target);
            return msg == null ? "" : msg;
        }
        String msg = AndroidConversion.removeConfirmed(viewer, target);
        return msg == null ? "" : msg;
    }

    /** After a staff confirm, land on that player's action page so the result is visible. */
    private static String androidStaffReopen(String arg) {
        String raw = arg == null ? "" : arg.trim();
        String target = "";
        if (raw.regionMatches(true, 0, "convert:", 0, 8)) {
            target = raw.substring(8).trim();
        } else if (raw.regionMatches(true, 0, "remove:", 0, 7)) {
            target = raw.substring(7).trim();
        }
        if (target.isBlank()) {
            return "android_staff";
        }
        return "android_target:" + target;
    }

    /**
     * Parses {@code do:action}, {@code do:action:arg}, or {@code do:action:arg:more}.
     * Everything after the first colon following the action name is the arg
     * (so {@code do:boost:1.25:30} -> action {@code boost}, arg {@code 1.25:30}).
     */
    static DoParts parseDo(String action) {
        String raw = stripDo(action);
        if (raw == null || raw.isBlank()) {
            return new DoParts("", "");
        }
        int colon = raw.indexOf(':');
        if (colon < 0) {
            return new DoParts(raw, "");
        }
        return new DoParts(raw.substring(0, colon), raw.substring(colon + 1));
    }

    static String stripDo(String action) {
        if (action != null && action.startsWith("do:")) {
            return action.substring(3);
        }
        return action == null ? "" : action;
    }

    /**
     * Resolve logical screen + action from packet fields.
     *
     * <p>Priority (hub wire shim aware):
     * <ol>
     *   <li>{@code @logical:action} prefix on actionId
     *   <li>{@code args.system} / {@code args.screen} (logical metadata from button args)
     *   <li>packet {@code screenId} (may be {@code hub} when KaiPath shim is active)
     * </ol>
     * Bare hub tile names ({@code character} -> {@code open:character}) still work when the
     * resolved screen is hub.
     */
    static Qualified qualify(String screenId, String actionId, JsonObject args) {
        String action = actionId == null ? "" : actionId.trim();
        String screen = "";
        if (action.startsWith("@")) {
            int colon = action.indexOf(':');
            if (colon > 1) {
                screen = action.substring(1, colon).trim().toLowerCase();
                action = action.substring(colon + 1).trim();
            } else {
                action = action.substring(1).trim();
            }
        }
        // Prefer logical metadata over wire screenId (hub shim tunnels submenus as hub).
        if (screen.isBlank() && args != null) {
            screen = stringArg(args, UltraScreenIds.META_SYSTEM);
        }
        if (screen.isBlank() && args != null) {
            screen = stringArg(args, "screen");
        }
        if (screen.isBlank() && screenId != null && !screenId.isBlank()) {
            // Wire hub with no metadata -> real hub. Otherwise treat packet id as logical.
            screen = screenId.trim();
        }
        if (screen.isBlank()) {
            screen = "hub";
        }
        screen = UltraScreenIds.normalizeLogical(screen);
        return new Qualified(screen, action);
    }

    private static String reopenPage(String screen, DoParts parts, JsonObject args) {
        if (parts == null) {
            return homePage(screen);
        }
        if (("character".equals(screen) || "saga".equals(screen))
                && ("saga_reset".equals(parts.action) || "saga".equals(parts.action))) {
            return "reset";
        }
        if ("character".equals(screen) && "android_staff".equals(parts.action)) {
            return androidStaffReopen(parts.arg);
        }
        String page = stringArg(args, "page");
        if ("rival".equals(screen)) {
            String refreshed = rivalListReopen(parts.action, page);
            if (refreshed != null) {
                return refreshed;
            }
        }
        if (!page.isBlank() && !"config".equals(screen) && !"spar".equals(screen) && !"sparring".equals(screen)) {
            return page;
        }
        // Keep player on the tab they acted from when possible.
        return switch (screen == null ? "" : screen) {
            case "difficulty" -> switch (parts.action == null ? "" : parts.action) {
                case "activate", "lower_tier", "purchase_tier", "buy" -> "tiers";
                case "toggle_personal" ->
                        page != null && (page.startsWith("team") || "team".equals(page))
                                ? "team"
                                : "tiers";
                case "summon_end_dragon", "end_dragon", "summon_dragon" -> "end_dragon";
                case "summon_wither" -> "wither";
                case "summon_warden" -> "warden";
                case "mode", "set_mode", "team" -> "team";
                case "equip_title" ->
                        page != null && page.startsWith("title_detail:") ? page : "titles_p0";
                case "clear_title", "toggle_title_sense" ->
                        page != null && (page.startsWith("titles_p") || page.startsWith("titles:"))
                                ? page
                                : "titles_p0";
                case "toggle_coin_chat" -> "main";
                case "reload", "reloadconfig" -> "admin";
                case "syslog" -> "eventlog";
                default -> page.isBlank() ? homePage(screen) : page;
            };
            case "prestige" -> switch (parts.action == null ? "" : parts.action) {
                case "confirm", "confirm_now", "confirm_gui" -> "turnin";
                case "turnin", "turn_in", "redeem" -> "exchange";
                case "skill", "buy_skill", "skillup" -> "skills";
                case "tier", "buy_tier", "unlock_tier" -> "tiers";
                case "majin", "buy_majin", "mutant", "buy_mutant",
                        "unmajin", "unbuy_majin", "remove_majin",
                        "unmutant", "unbuy_mutant", "remove_mutant",
                        "ultimate", "buy_ultimate", "unultimate", "unbuy_ultimate",
                        "remove_ultimate",
                        "godki", "buy_godki", "godki_enhancement" -> "effects";
                default -> homePage(screen);
            };
            case "progression" -> switch (parts.action == null ? "" : parts.action) {
                // Each boost lands back on its own control view so the confirm click is right there.
                case "boost" -> UltraProgressionScreens.PAGE_TP_BOOST;
                case "masteryboost", "globalmasteryboost" -> UltraProgressionScreens.PAGE_MASTERY_BOOST;
                case "toggle_staff_free_coins", "staff_free_coins" -> "coins";
                default -> homePage(screen);
            };
            case "spar", "sparring" -> switch (parts.action == null ? "" : parts.action) {
                case "dojo_accept", "dojo_war_accept", "dojo_decline", "dojo_war_decline",
                        "dojo_war_cancel", "dojo_war_revoke", "dojo_cancel" -> "dojo_war_pending";
                case "dojo_challenge" -> "dojo_war";
                case "mentor_accept", "mentor_decline", "mentor" -> "pending";
                case "mentor_invite", "apprentice_invite", "mentor_release" -> "mentor";
                case "tpmsg", "mentor_tpmsg", "mentor_tp_msg" -> "settings";
                case "admin" -> "admin";
                case "weight_buy", "buy_weight", "weight_quote", "quote" -> "weights";
                default -> {
                    String pageHint = stringArg(args, "page");
                    yield pageHint.isBlank() ? homePage(screen) : pageHint;
                }
            };
            case "config" -> {
                if (("open_module".equals(parts.action) || "select_module".equals(parts.action))
                        && parts.arg != null
                        && !parts.arg.isBlank()) {
                    yield "edit:" + parts.arg + ":0:";
                }
                if ("search".equals(parts.action) || "search_clear".equals(parts.action)) {
                    String cfgSearchPage = stringArg(args, "page");
                    if (cfgSearchPage != null && cfgSearchPage.startsWith("edit:")) {
                        // Keep category; reset to first page of search/category results.
                        String[] bits = cfgSearchPage.split(":", -1);
                        String mod = bits.length > 1 && !bits[1].isBlank() ? bits[1] : "difficulty";
                        yield "edit:" + mod + ":0:";
                    }
                    yield homePage(screen);
                }
                String cfgPage = stringArg(args, "page");
                yield cfgPage.isBlank() ? homePage(screen) : cfgPage;
            }
            case "rival" -> switch (parts.action == null ? "" : parts.action) {
                case "challenge", "challenge_accept", "challenge_decline", "challenge_cancel",
                        "challenge_send", "spectate", "spectate_stop" -> "challenge";
                case "cancel_mutual_replace", "cancel_replace", "cancelmutualreplace" -> "requests";
                case "tpmsg", "tp_msg", "instinct", "declare", "accept", "decline", "silent",
                        "remove", "accept_replace" -> "actions";
                default -> homePage(screen);
            };
            case "character" -> switch (parts.action == null ? "" : parts.action) {
                case "android_staff" -> androidStaffReopen(parts.arg);
                case "android_remove", "android", "android_self" -> UltraStyle.PAGE_ANDROID_REMOVE;
                case "race_confirm" -> "race:0";
                case "class_confirm" -> "class:0";
                case "reskin_confirm" -> "reskin";
                case "bone_toggle",
                        "bone_unlock",
                        "bone_equip",
                        "bone_unequip",
                        "bone_race_default",
                        "bone_preview",
                        "bone_restore" -> {
                    String bonePage = stringArg(args, "page");
                    yield bonePage != null && bonePage.startsWith("bones")
                            ? bonePage
                            : (page != null && page.startsWith("bones") ? page : "bones:0");
                }
                case "bodypart_color", "bodypart_color_reset" -> {
                    String colorPage = stringArg(args, "page");
                    yield colorPage != null && colorPage.startsWith("bodycolors")
                            ? colorPage
                            : "bodycolors";
                }
                default -> homePage(screen);
            };
            case "admin" -> "admin";
            default -> homePage(screen);
        };
    }

    /**
     * Rival list mutations reopen a page that rebuilds from current data.
     * Staying on {@code list_detail} after remove kept the old Remove button.
     * Settings toggles return null so the current settings page refreshes in place.
     */
    private static String rivalListReopen(String action, String page) {
        String act = action == null ? "" : action;
        String current = page == null ? "" : page;
        return switch (act) {
            case "remove", "declare", "silent", "accept_replace" -> "actions";
            case "accept", "decline" ->
                    current.startsWith("requests") || current.startsWith("pending") ? current : "actions";
            case "cancel_mutual_replace", "cancel_replace", "cancelmutualreplace" -> "requests";
            case "challenge", "challenge_accept", "challenge_decline", "challenge_cancel",
                    "challenge_send", "spectate", "spectate_stop" -> "challenge";
            default -> null;
        };
    }

    private static String homePage(String screen) {
        return switch (screen == null ? "" : screen) {
            case "rival" -> "home";
            case "config" -> "edit:difficulty:0:";
            case "admin" -> "admin";
            case "skillcheck", "skills" -> "main";
            default -> "main";
        };
    }

    private static String tabPage(String screen, String tab) {
        String t = tab == null ? "" : tab.toLowerCase();
        return switch (screen == null ? "" : screen) {
            case "config" -> {
                String id = switch (t) {
                    case "difficulty", "diff" -> "difficulty";
                    case "progression", "prog" -> "progression";
                    case "character", "character-services", "char" -> "character-services";
                    case "saga" -> "saga";
                    case "rival" -> "rival";
                    case "sparring", "spar" -> "sparring";
                    case "currency", "coins" -> "currency";
                    default -> "difficulty";
                };
                yield "edit:" + id + ":0:";
            }
            case "character" -> switch (t) {
                case "saga", "reset" -> "reset:0";
                case "android", "android_remove" -> UltraStyle.PAGE_ANDROID_REMOVE;
                default -> "main";
            };
            case "difficulty" -> switch (t) {
                case "tiers" -> "tiers";
                case "summons" -> "summons";
                case "stats", "cr", "combat_rating" -> "stats";
                default -> "main";
            };
            case "rival" -> switch (t) {
                case "actions", "rival" -> "actions";
                case "challenge" -> "challenge";
                case "info", "information" -> "info";
                default -> "home";
            };
            case "spar", "sparring" -> switch (t) {
                case "dojo" -> "dojo";
                case "wars", "dojo_war" -> "dojo_war";
                default -> "main";
            };
            case "prestige" -> switch (t) {
                case "prestige", "turnin", "points" -> "turnin";
                case "shop" -> "shop";
                default -> "main";
            };
            case "progression" -> switch (t) {
                case "boosts" -> "boosts";
                case "coins" -> "coins";
                default -> "main";
            };
            case "skillcheck", "skills" -> switch (t) {
                case "skills", "core", "natural", "saga", "list" -> "skills";
                case "help", "about" -> "help";
                default -> "main";
            };
            default -> homePage(screen);
        };
    }

    private static JsonObject parse(String argsJson) {
        try {
            if (argsJson == null || argsJson.isBlank()) {
                return new JsonObject();
            }
            JsonElement el = JsonParser.parseString(argsJson);
            if (el != null && el.isJsonObject()) {
                return el.getAsJsonObject();
            }
            return new JsonObject();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra bad argsJson: {}", AdaptiveDifficultyMod.MOD_ID, argsJson);
            return new JsonObject();
        }
    }

    private static String stringArg(JsonObject args, String key) {
        if (args == null || key == null || !args.has(key)) {
            return "";
        }
        JsonElement el = args.get(key);
        if (el == null || el.isJsonNull()) {
            return "";
        }
        try {
            if (el.isJsonPrimitive()) {
                String s = el.getAsString();
                return s == null ? "" : s.trim();
            }
            return el.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
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

    record DoParts(String action, String arg) {}

    record Qualified(String screen, String action) {}
}
