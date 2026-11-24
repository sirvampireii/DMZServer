package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.gui.AdminInspectSessions;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds a GuiOpenPacket payload for KaiPath.
 *
 * <p>Panel defaults are the compact Ultra dialog ({@link UltraStyle#PANEL_W}x{@link
 * UltraStyle#PANEL_H}), centered with a screen margin. Button action ids are qualified as
 * {@code @&lt;screenId&gt;:&lt;action&gt;} so KaiPath can omit or mishandle {@code screenId} and
 * the server still routes correctly.
 *
 * <p>Layout JSON includes shared body/button metrics ({@code lineHeight}, {@code lineGap},
 * {@code bodyMaxHeight}, {@code scrollBody}, {@code buttonPad}, ...) so every screen benefits.
 * Optional {@link #playerModel} declares a client-rendered entity preview (JLDK implements
 * rendering; unknown clients ignore the field).
 */
public final class UltraScreenSpec {
    private static final Gson GSON = new Gson();
    /** Largest panel height a menu asks for (KaiPath caps the panel at the window anyway). */
    static final int MENU_MAX_REQUEST_H = 2000;

    /** When set, {@link #send} captures packets instead of transmitting (startup self-test). */
    private static final ThreadLocal<List<GuiOpenPacket>> PROBE = new ThreadLocal<>();
    /**
     * Stack of staff viewers (staff inspect). {@link #send} delivers the GuiOpenPacket to the
     * top viewer even when screen builders were invoked with the inspect subject for data.
     * Nested {@link UltraGui#open} / action-dispatch binds push/pop so an inner clear does not
     * drop the outer viewer.
     */
    private static final ThreadLocal<Deque<ServerPlayer>> INSPECT_VIEWER = new ThreadLocal<>();

    private final String screenId;
    private String title = "";
    private String pageId = "main";
    private String activeTab = "home";
    private final List<Tab> tabs = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final List<Input> inputs = new ArrayList<>();
    /** 4.7.66: fields every button copies (args.bindInputs), the Enter button, fields with a color square. */
    private final List<String> bindAllInputs = new ArrayList<>();
    private String enterActionId;
    private final java.util.Set<String> colorPreviewInputs = new java.util.HashSet<>();
    /** Compact title + tab row; content starts immediately below. */
    private int nextRow = UltraStyle.CONTENT_ROW;
    private int panelWidth = UltraStyle.PANEL_W;
    private int panelHeight = UltraStyle.PANEL_H;
    private boolean centerPanel = true;
    private int panelMargin = UltraStyle.PANEL_MARGIN;
    /**
     * When true, {@link #toPacket()} keeps {@link #panel(int, int)} instead of growing toward
     * {@link UltraStyle#PANEL_MAX_H}.
     */
    private boolean lockPanel;
    /** When true, layoutJson asks the client to scale this design box to the screen. */
    private boolean fillScreen;
    /**
     * Coordinate space for {@link #fillScreen(int, int)}. Zero means the packet panel size,
     * which is also what a client that ignores {@code fillScreen} draws (then clamps).
     */
    private int designWidth;
    private int designHeight;
    /**
     * Menu fit ({@link #fitMenu}; centered and content-sized since 4.7.55): {@link #toPacket}
     * reflows the legacy 350-grid layout into a centered panel as tall as the page.
     */
    private boolean menuFit;
    private PlayerModel playerModel;
    /** 4.7.71: the page asked for the Back footer ({@link #back}); placed per player when it is sent. */
    private boolean backFooter;
    private boolean backPlaced;
    /** 4.7.71: the breadcrumb line for an LM client with the navigation layer, [title, action] pairs; null for none. */
    private JsonArray breadcrumb;
    /** 4.7.68 pictures in the body ({@link #pictures}); null when the page has none. */
    private JsonObject pictures;
    /** Lowest button top Y seen so far; used to size the body scroll region. */
    private int firstButtonY = -1;
    /** Pinned footer buttons ({@code footer:true}) — placed at panel bottom in {@link #toPacket}. */
    private final List<Button> footerButtons = new ArrayList<>();
    /** Controls pinned to a design position: never shifted with the body (see {@link #fixedButton}). */
    private final List<Button> fixedButtons = new ArrayList<>();
    /** Optional tighter body line gap (null = {@link UltraStyle#LINE_GAP}). */
    private Integer lineGapOverride;
    /** Optional tighter body pad (null = {@link UltraStyle#BODY_PAD}). */
    private Integer bodyPadOverride;
    /** Button height this screen emits. Defaults to the shared {@link UltraStyle#BTN_H}. */
    private int buttonHeight = UltraStyle.BTN_H;

    private UltraScreenSpec(String screenId) {
        this.screenId = screenId == null || screenId.isBlank() ? "hub" : screenId.trim();
    }

    public static UltraScreenSpec of(String screenId) {
        return new UltraScreenSpec(screenId);
    }

    /**
     * Run {@code work} while capturing every {@link #send} into a list (no network).
     * Used by {@link UltraScreenSelfTest}.
     */
    public static List<GuiOpenPacket> captureSends(Runnable work) {
        List<GuiOpenPacket> captured = new ArrayList<>();
        PROBE.set(captured);
        try {
            if (work != null) {
                work.run();
            }
        } finally {
            PROBE.remove();
        }
        return captured;
    }

    /**
     * Push the staff viewer for the duration of an Ultra open / action built from an inspect
     * subject. Always pair with {@link #clearInspectViewer()} in {@code finally}.
     */
    public static void bindInspectViewer(ServerPlayer viewer) {
        if (viewer == null) {
            return;
        }
        Deque<ServerPlayer> stack = INSPECT_VIEWER.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            INSPECT_VIEWER.set(stack);
        }
        stack.push(viewer);
    }

    /** Pop one {@link #bindInspectViewer} frame. */
    public static void clearInspectViewer() {
        Deque<ServerPlayer> stack = INSPECT_VIEWER.get();
        if (stack == null || stack.isEmpty()) {
            INSPECT_VIEWER.remove();
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            INSPECT_VIEWER.remove();
        }
    }

    /** Active inspect viewer (top of stack), or null when not in an inspect open. */
    public static ServerPlayer inspectViewer() {
        Deque<ServerPlayer> stack = INSPECT_VIEWER.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    public UltraScreenSpec title(String title) {
        // Plain ASCII title - KaiPath paints the header in its own color; section-sign
        // codes mojibake as stray A/E glyphs on some clients.
        this.title = UltraStyle.wireText(UltraStyle.plain(title == null ? "" : title));
        return this;
    }

    /** Tighter body line spacing for dense lists (e.g. Skill Check). */
    public UltraScreenSpec lineGap(int gap) {
        this.lineGapOverride = Math.max(0, gap);
        return this;
    }

    /** Tighter body padding for dense lists. */
    public UltraScreenSpec bodyPad(int pad) {
        this.bodyPadOverride = Math.max(0, pad);
        return this;
    }

    /**
     * Button height this screen emits, for editors that need denser rows than the shared
     * {@link UltraStyle#BTN_H}. Text fields follow the same height so rows stay aligned.
     */
    public UltraScreenSpec buttonHeight(int height) {
        this.buttonHeight = Math.max(12, height);
        return this;
    }

    public UltraScreenSpec page(String pageId) {
        this.pageId = pageId == null ? "main" : pageId;
        return this;
    }

    public UltraScreenSpec activeTab(String tabId) {
        this.activeTab = tabId == null ? "home" : tabId;
        return this;
    }

    /** Explicit panel size in GUI pixels (DMZUltra-style centered dialog). */
    public UltraScreenSpec panel(int width, int height) {
        this.panelWidth = Math.max(UltraStyle.PANEL_MIN_W, width);
        this.panelHeight = Math.max(UltraStyle.PANEL_MIN_H, height);
        return this;
    }

    public UltraScreenSpec center(boolean center) {
        this.centerPanel = center;
        return this;
    }

    public UltraScreenSpec margin(int margin) {
        this.panelMargin = Math.max(0, margin);
        return this;
    }

    /**
     * Keep the size passed to {@link #panel(int, int)}. Packed menus still grow toward
     * {@link UltraStyle#PANEL_MAX_H}; a locked panel does not.
     */
    public UltraScreenSpec lockPanel() {
        this.lockPanel = true;
        return this;
    }

    /**
     * Scale the packet panel to the screen. Design size matches {@link #panel(int, int)}.
     * A client that ignores {@code fillScreen} draws that panel and clamps it to the GUI.
     */
    public UltraScreenSpec fillScreen() {
        this.fillScreen = true;
        return this;
    }

    /**
     * Scale {@code designWidth} x {@code designHeight} onto the window. The packet panel stays
     * the size passed to {@link #panel(int, int)}. KaiPath 1.3.20 ignores {@code fillScreen} and
     * only clamps that panel down, so a request larger than the GUI fills the window while
     * controls stay inside the design box (the smallest auto GUI).
     */
    public UltraScreenSpec fillScreen(int designWidth, int designHeight) {
        this.fillScreen = true;
        this.designWidth = Math.max(1, designWidth);
        this.designHeight = Math.max(1, designHeight);
        return this;
    }

    /**
     * Player menu (4.7.55): a centered panel sized to its page, in GUI pixels, no fillScreen.
     * The width is {@link UltraStyle#MENU_W} ({@link UltraStyle#MENU_MODEL_W} with a player
     * model), wider only when the tabs, the title or a row need it, never over
     * {@link UltraStyle#MENU_MAX_W}. The height is the header, the whole body text, the rows and
     * the footer row, plus KaiPath's Close band. KaiPath caps the panel at the window and scrolls
     * the whole page (body, rows and footer together) when it is taller, so nothing is cut off and
     * the footer is never pinned off-screen.
     *
     * <p>Builders keep laying out on the legacy 350-wide grid ({@link UltraStyle#COL_L},
     * {@link UltraStyle#COL_R}, {@link #rowGap}); {@link #toPacket} reflows it into the panel:
     * columns are widened to the panel and rows are packed under the body text. Call it before
     * any {@link #line} so body text wraps to the menu width.
     */
    public UltraScreenSpec fitMenu() {
        this.menuFit = true;
        this.fillScreen = false;
        this.designWidth = 0;
        this.designHeight = 0;
        this.lockPanel = true;
        this.centerPanel = true;
        this.panelMargin = UltraStyle.PANEL_MARGIN;
        return this;
    }

    /**
     * Optional client-rendered player model preview. Unknown clients ignore this field;
     * layout and buttons still work. JLDK draws the entity; LM only declares bounds.
     */
    public UltraScreenSpec playerModel(
            int x, int y, int width, int height, float scale, boolean rotate) {
        return playerModel(x, y, width, height, scale, rotate, null);
    }

    /**
     * Player model preview with an optional {@code parts} snapshot (head bones, form identity,
     * extras). See {@link PlayerModelParts} and {@code docs/KAIPATH_GUI_PROTOCOL.md}.
     */
    public UltraScreenSpec playerModel(
            int x,
            int y,
            int width,
            int height,
            float scale,
            boolean rotate,
            JsonObject parts) {
        this.playerModel = new PlayerModel(
                x,
                y,
                Math.max(16, width),
                Math.max(16, height),
                scale <= 0f ? 1f : scale,
                rotate,
                parts);
        return this;
    }

    /**
     * Bounds plus a live {@link PlayerModelParts#snapshot} for {@code player} (Character
     * Services).
     */
    public UltraScreenSpec playerModel(
            ServerPlayer player,
            int x,
            int y,
            int width,
            int height,
            float scale,
            boolean rotate) {
        return playerModel(x, y, width, height, scale, rotate, PlayerModelParts.snapshot(player));
    }

    /**
     * Tab row entries as {@code id|Label}, {@code id|Label|style}, or
     * {@code id|Label|style|icon}. Optional style: {@code danger}/{@code staff} for staff-only
     * tabs. Optional {@code icon} is an item id or texture path. When omitted, LM sends no
     * icon (no per-system gear texture). Tabs are painted as buttons (tabsJson stays empty).
     */
    public UltraScreenSpec tabs(String... idLabelPairs) {
        tabs.clear();
        if (idLabelPairs == null) {
            return this;
        }
        for (String pair : idLabelPairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("\\|", -1);
            String id = parts[0] == null ? "" : parts[0].trim();
            if (id.isBlank()) {
                continue;
            }
            String label = parts.length > 1 && parts[1] != null && !parts[1].isBlank()
                    ? parts[1].trim()
                    : id;
            String styleHint = parts.length > 2 && parts[2] != null ? parts[2].trim() : "";
            String icon = parts.length > 3 && parts[3] != null ? parts[3].trim() : "";
            tabs.add(new Tab(id, label, styleHint, icon));
        }
        // Content starts below title + tab button row (under the header line).
        if (!tabs.isEmpty() && nextRow < UltraStyle.CONTENT_ROW_TABS) {
            nextRow = UltraStyle.CONTENT_ROW_TABS;
        }
        return this;
    }

    public UltraScreenSpec line(String text) {
        if (text != null && !text.isBlank()) {
            // wireText strips mojibake; wrapBody then breaks on spaces so fitLabel
            // never cuts the text box mid-word. Extra lines scroll.
            addWrapped(lines.size(), UltraStyle.wireText(text));
        }
        return this;
    }

    /** Pixel width of the body text box for the panel and player model set so far. */
    private int bodyFitPx() {
        if (menuFit) {
            // Wrap to the body KaiPath draws in the menu panel: left of the menu model box.
            int modelX = playerModel == null ? -1 : menuModelBox().x();
            return UltraStyle.bodyTextFitPx(
                    playerModel == null ? UltraStyle.MENU_W : UltraStyle.MENU_MODEL_W, modelX);
        }
        int modelX = playerModel == null ? -1 : playerModel.x;
        return UltraStyle.bodyTextFitPx(panelWidth, modelX);
    }

    /** Insert word-wrapped body lines at {@code index}. Extra lines scroll; none are dropped. */
    private void addWrapped(int index, String text) {
        List<String> wrapped = UltraStyle.wrapBody(text, bodyFitPx());
        if (wrapped.isEmpty()) {
            return;
        }
        int at = Math.max(0, Math.min(index, lines.size()));
        lines.addAll(at, wrapped);
    }

    public UltraScreenSpec labelValue(String label, String value) {
        return line(UltraStyle.labelValue(label, value));
    }

    public UltraScreenSpec notice(String body) {
        return line(UltraStyle.notice(body));
    }

    /**
     * A button for something the player cannot do yet (4.7.57): dim and inert. It sends
     * {@link UltraGuiActions#NO_ACTION}, which the server ignores, so the page stays as it is.
     * Such a button is shown, never hidden; the page text says what is missing.
     */
    public UltraScreenSpec lockedButton(String label) {
        return button(UltraGuiActions.NO_ACTION, UltraStyle.dim(UltraStyle.plain(label)), UltraStyle.STYLE_DIM);
    }

    /** {@link #lockedButton} in the right column (4.7.59: a locked second action on the same row). */
    public UltraScreenSpec lockedButtonRight(String label) {
        return buttonRight(UltraGuiActions.NO_ACTION, UltraStyle.dim(UltraStyle.plain(label)), UltraStyle.STYLE_DIM);
    }

    public UltraScreenSpec button(String actionId, String label, String style) {
        return button(actionId, label, style, UltraStyle.COL_L, nextRow, UltraStyle.BTN_W);
    }

    public UltraScreenSpec buttonLeft(String actionId, String label, String style) {
        return button(actionId, label, style, UltraStyle.COL_L, nextRow, UltraStyle.BTN_W);
    }

    /** 4.7.64: one button across both columns on the current row (the hub's Prestige tile). */
    public UltraScreenSpec buttonWide(String actionId, String label, String style, String accent) {
        return button(actionId, label, style, UltraStyle.COL_L, nextRow, UltraStyle.BTN_W * 2 + UltraStyle.BTN_GAP,
                null, accent);
    }

    public UltraScreenSpec buttonRight(String actionId, String label, String style) {
        return button(actionId, label, style, UltraStyle.COL_R, nextRow, UltraStyle.BTN_W);
    }

    public UltraScreenSpec button(
            String actionId, String label, String style, int x, int y, int w) {
        return button(actionId, label, style, x, y, w, null);
    }

    /**
     * Button that asks KaiPath to copy a text field's current value into {@code args.value}
     * before sending {@link com.dbzlegacy.adaptivedifficulty.net.gui.GuiActionPacket}.
     * {@code bindInputId} is the same id passed to {@link #textField}.
     */
    public UltraScreenSpec buttonBound(
            String actionId, String label, String style, String bindInputId) {
        return button(
                actionId, label, style, UltraStyle.COL_L, nextRow, UltraStyle.BTN_W, bindInputId);
    }

    /** Bound Apply/search button at an explicit x/width (main-column layouts). */
    public UltraScreenSpec buttonBound(
            String actionId, String label, String style, int x, int y, int w, String bindInputId) {
        return button(actionId, label, style, x, y, w, bindInputId);
    }

    public UltraScreenSpec button(
            String actionId, String label, String style, int x, int y, int w, String bindInputId) {
        return button(actionId, label, style, x, y, w, bindInputId, null);
    }

    /**
     * Same as {@link #button(String, String, String, int, int, int, String)} with an optional
     * single-letter {@code accent} used to color the <em>label text</em> (not button chrome).
     * When {@code accent} is blank, {@link UltraStyle#systemAccentCode(String)} for this screen
     * is applied. Pass a raw label — section-sign is added via {@link UltraStyle#color}.
     */
    public UltraScreenSpec button(
            String actionId,
            String label,
            String style,
            int x,
            int y,
            int w,
            String bindInputId,
            String accent) {
        return addButton(actionId, label, style, x, y, w, bindInputId, accent, true);
    }

    /**
     * Side-rail / chrome control that must not clamp the body text region. Same wire shape as
     * {@link #button(String, String, String, int, int, int)} but ignored by {@link #firstButtonY}
     * packing (hub tab chrome uses the same rule).
     */
    public UltraScreenSpec chromeButton(
            String actionId, String label, String style, int x, int y, int w) {
        return addButton(actionId, label, style, x, y, w, null, null, false);
    }

    /**
     * A control fixed at its design position (4.7.49, the model spin toggle under the preview):
     * like {@link #chromeButton} it does not clamp the body text, and unlike every other
     * non-footer button it is not moved down when the body text needs room, so it stays under
     * the model, which never moves. The panel still grows to hold it.
     */
    public UltraScreenSpec fixedButton(
            String actionId, String label, String style, int x, int y, int w) {
        int before = buttons.size();
        addButton(actionId, label, style, x, y, w, null, null, false);
        fixedButtons.add(buttons.remove(before));
        return this;
    }

    private UltraScreenSpec addButton(
            String actionId,
            String label,
            String style,
            int x,
            int y,
            int w,
            String bindInputId,
            String accent,
            boolean affectsBodyFloor) {
        UltraScreenIds.requireValidActionId(screenId, actionId, label);
        int width = Math.max(UltraStyle.BTN_MIN_W, w);
        String resolvedStyle = normalizeStyle(style);
        if (affectsBodyFloor && (firstButtonY < 0 || y < firstButtonY)) {
            firstButtonY = y;
        }
        String bind = null;
        if (bindInputId != null && !bindInputId.isBlank()) {
            UltraScreenIds.requireValidActionId(screenId, bindInputId, "bindInput");
            bind = qualifyAction(bindInputId);
        }
        String accentCode = normalizeAccent(accent);
        if (UltraStyle.STYLE_DIM.equals(resolvedStyle)) {
            // OFF / unavailable stays dark gray. System accent (spar dark aqua, skill yellow)
            // must not paint a disabled toggle as active.
            accentCode = "8";
        } else if (accentCode.isEmpty()) {
            accentCode = UltraStyle.systemAccentCode(screenId);
        }
        // Purple / gold / yellow ink on the light vanilla button is unreadable.
        accentCode = UltraStyle.readableButtonInk(resolvedStyle, accentCode);
        int labelPx = UltraStyle.labelMaxWidth(width);
        String fitted = UltraStyle.fitAsciiLabel(UltraStyle.clipPlain(label, labelPx), labelPx);
        // Character startup self-test forbids section-sign / C2 in buttonsJson labels; style carries
        // confirm/dim/danger. Other screens may still bake UltraStyle.color ink into labels.
        String wireLabel = plainWireLabel(fitted, accentCode);
        buttons.add(new Button(
                qualifyAction(actionId),
                wireLabel,
                resolvedStyle,
                x,
                y,
                width,
                buttonHeight,
                UltraStyle.BTN_PAD,
                bind,
                accentCode,
                false,
                UltraStyle.SOUND_CONFIRM,
                UltraStyle.BUTTON_SKIN,
                "",
                label));
        return this;
    }

    /**
     * Optional {@code icon} on the button just added. Item id ({@code minecraft:iron_sword})
     * or texture path ({@code modid:textures/item/foo.png}). Blank or invalid values are omitted.
     */
    public UltraScreenSpec icon(String iconId) {
        if (buttons.isEmpty()) {
            return this;
        }
        String norm = UltraStyle.normalizeIcon(iconId);
        if (norm.isEmpty()) {
            return this;
        }
        int last = buttons.size() - 1;
        Button b = buttons.get(last);
        buttons.set(
                last,
                new Button(
                        b.actionId,
                        b.label,
                        b.style,
                        b.x,
                        b.y,
                        b.w,
                        b.h,
                        b.pad,
                        b.bindInput,
                        b.accent,
                        b.footer,
                        b.sound,
                        b.buttonSkin,
                        norm,
                        b.raw));
        return this;
    }

    /** Advance {@link #nextRow} by one control row, plus optional extra gap (px). */
    public UltraScreenSpec rowGap(int extraPx) {
        nextRow += buttonHeight + UltraStyle.BTN_GAP + Math.max(0, extraPx);
        return this;
    }

    /** Full-width button with optional accent letter. */
    public UltraScreenSpec button(String actionId, String label, String style, String accent) {
        return button(actionId, label, style, UltraStyle.COL_L, nextRow, UltraStyle.BTN_W, null, accent);
    }

    public UltraScreenSpec buttonLeft(String actionId, String label, String style, String accent) {
        return button(actionId, label, style, UltraStyle.COL_L, nextRow, UltraStyle.BTN_W, null, accent);
    }

    public UltraScreenSpec buttonRight(String actionId, String label, String style, String accent) {
        return button(actionId, label, style, UltraStyle.COL_R, nextRow, UltraStyle.BTN_W, null, accent);
    }

    public UltraScreenSpec rowGap() {
        nextRow += buttonHeight + UltraStyle.BTN_GAP;
        return this;
    }

    public UltraScreenSpec setRow(int y) {
        nextRow = y;
        return this;
    }

    /**
     * Park the next control row under the body copy.
     *
     * <p>Same estimate Skill Check uses: 24px of pad plus one step per wired line
     * ({@link UltraStyle#LINE_H} + 2), capped at {@link UltraStyle#BODY_MIN_H}, with an
     * 80px minimum band so a short screen still clears the header. Lines past that cap
     * scroll. Tabbed screens start at {@link UltraStyle#CONTENT_ROW_TABS}; others at
     * {@link UltraStyle#CONTENT_ROW}. Call after every {@link #line} and before the first
     * button so the row follows the text that will actually be drawn.
     */
    public UltraScreenSpec buttonsAfterLines() {
        int origin = tabs.isEmpty() ? UltraStyle.CONTENT_ROW : UltraStyle.CONTENT_ROW_TABS;
        int lineCount = lines.size();
        int bodyH = Math.min(UltraStyle.BODY_MIN_H, 24 + lineCount * (UltraStyle.LINE_H + 2));
        return setRow(origin + Math.max(80, bodyH));
    }

    public int row() {
        return nextRow;
    }

    /**
     * 4.7.71: the one Back footer. Where it goes comes from the page registry ({@link PageRegistry}) and, on an LM
     * client with the navigation layer, from the player's page history ({@link NavHistory}):
     * <ul>
     *   <li>a system Home page: one {@code Back to ...} button on the right;</li>
     *   <li>any other page: {@code Back to ...} on the left and {@code Main menu} on the right.</li>
     * </ul>
     * LM client: Back goes to the page the player came from ({@code nav:back}) and is named after it. Bridge
     * clients: Back goes to the page's registry parent and is named after it. The guide footers keep their own
     * ({@link #footerGuide}, {@link #footerBackOnly}).
     */
    public UltraScreenSpec back() {
        backFooter = true;
        return this;
    }

    /** Places the Back footer for {@code player} (null: a bridge view), once. */
    private void placeBack(ServerPlayer player) {
        if (!backFooter || backPlaced) {
            return;
        }
        backPlaced = true;
        // A system's Home (its first tab) keeps one footer button on the right: Back to main menu. Its other tabs are
        // siblings, not a trail: they go back to the system Home (the registry parent, never the tab history), with
        // Main menu beside it.
        boolean firstTab = !tabs.isEmpty() && tabs.get(0).id().equals(activeTab);
        boolean otherTab = !tabs.isEmpty() && !firstTab;
        boolean home = firstTab || (!otherTab && PageRegistry.systemHome(screenId, pageId));
        boolean nativeNav = player != null && !otherTab
                && com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesNav(player);
        String action;
        String label;
        if (nativeNav) {
            String[] prev = NavHistory.previous(player.m_20148_(), screenId, pageId);
            if (prev == null) {
                prev = PageRegistry.parent(screenId, pageId);
            }
            action = "nav:back";
            label = prev == null || (PageRegistry.HUB.equals(prev[0]) && PageRegistry.HUB_MAIN.equals(prev[1]))
                    ? UltraStyle.BACK_TO_MAIN : UltraStyle.backTo(PageRegistry.title(prev[0], prev[1]));
        } else {
            String[] up = home ? null : PageRegistry.parent(screenId, pageId);   // a system Home goes to the main menu
            if (up == null || PageRegistry.HUB.equals(up[0]) && PageRegistry.HUB_MAIN.equals(up[1])) {
                action = "nav:hub";
                label = UltraStyle.BACK_TO_MAIN;
            } else if (!up[0].equals(screenId)) {
                action = PageRegistry.systemHome(up[0], up[1]) ? "open:" + up[0] : "nav:hub";
                label = action.equals("nav:hub") ? UltraStyle.BACK_TO_MAIN : UltraStyle.backTo(PageRegistry.title(up[0], up[1]));
            } else {
                action = PageRegistry.systemHome(up[0], up[1]) ? "nav:home" : "page:" + up[1];
                label = UltraStyle.backTo(PageRegistry.title(up[0], up[1]));
            }
        }
        // Back that already goes to the main menu is the one exit: no second "Main menu" beside it.
        if (home || UltraStyle.BACK_TO_MAIN.equals(label)) {
            placeFooter(null, null, action, label);
        } else {
            placeFooter(action, label, "nav:hub", UltraStyle.MAIN_MENU);
        }
    }

    /**
     * 4.7.71: the breadcrumb line for {@code player}: every page below a system Home with no tab row, on an LM client
     * with the navigation layer. Each crumb but the last opens its page ({@code go:screen|page}).
     */
    private void placeBreadcrumb(ServerPlayer player) {
        breadcrumb = null;
        if (player == null || !tabs.isEmpty() || !com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesNav(player)) {
            return;
        }
        List<PageRegistry.Ref> crumbs = PageRegistry.crumbs(screenId, pageId);
        if (crumbs.size() < 3 || PageRegistry.find(screenId, pageId) == null) {
            return;
        }
        JsonArray arr = new JsonArray();
        for (int i = 0; i < crumbs.size(); i++) {
            PageRegistry.Ref r = crumbs.get(i);
            JsonArray c = new JsonArray();
            c.add(r.title());
            boolean hubMain = PageRegistry.HUB.equals(r.screen()) && PageRegistry.HUB_MAIN.equals(r.page());
            c.add(i == crumbs.size() - 1 ? "" : qualifyAction(hubMain ? "nav:hub" : "go:" + r.screen() + "|" + r.page()));
            arr.add(c);
        }
        breadcrumb = arr;
    }

    /**
     * 4.7.59 guide footer: only the left Back slot ({@code Back to {destination}}), for a first step whose
     * way back is the main menu itself. Keeps Back in the same place as every other step.
     */
    public UltraScreenSpec footerBackOnly(String backActionId, String destination) {
        String action =
                backActionId == null || backActionId.isBlank() ? "nav:hub" : backActionId.trim();
        return placeFooter(action, UltraStyle.backTo(destination), null, null);
    }

    /**
     * 4.7.63 guide footer: {@code Back to {destination}} in the left slot and {@code Skip guide} in the right slot,
     * the same place on every step. Skip finishes the guide like "Got it" does (marks it done, opens Home).
     */
    public UltraScreenSpec footerGuide(String backActionId, String destination, String skipActionId) {
        String action =
                backActionId == null || backActionId.isBlank() ? "nav:hub" : backActionId.trim();
        return placeFooter(action, UltraStyle.backTo(destination), skipActionId, "Skip guide");
    }

    private UltraScreenSpec placeFooter(
            String leftActionId, String leftLabel, String rightActionId, String rightLabel) {
        // Footer Y is resolved in toPacket; placeholder 0. Both faces stay normal.
        // Dim chrome reads as a disabled control.
        if (leftActionId != null && !leftActionId.isBlank()) {
            // 4.7.81 (GuiKit): a fullscreen design box (the config editor) starts Back on its rail edge, like every
            // menu's Back sits on its panel edge.
            addFooterButton(leftActionId, leftLabel, fillScreen && designWidth > 0 ? 8 : UltraStyle.COL_L,
                    UltraStyle.STYLE_NORMAL);
        }
        if (rightActionId != null && !rightActionId.isBlank()) {
            addFooterButton(rightActionId, rightLabel, footerRightX(), UltraStyle.STYLE_NORMAL);
        }
        return this;
    }

    /**
     * Hub menus keep the standard right column. A fullscreen design box right-aligns Main menu
     * inside that box so the button stays on a small GUI.
     */
    private int footerRightX() {
        if (fillScreen && designWidth > 0) {
            int edge = designWidth - panelMargin - UltraStyle.BTN_W;
            if (edge >= UltraStyle.COL_L + UltraStyle.BTN_W + 8) {
                return edge;
            }
        }
        return UltraStyle.COL_R;
    }

    private void addFooterButton(String actionId, String label, int x, String style) {
        UltraScreenIds.requireValidActionId(screenId, actionId, label);
        String face = UltraStyle.STYLE_DIM.equals(style) ? UltraStyle.STYLE_DIM : UltraStyle.STYLE_NORMAL;
        // Same ink as this system's body buttons. Dark gray is the disabled look, so a footer
        // forced to it (Rival's white accent, accent-less screens) read as grayed out.
        String accentCode = UltraStyle.readableButtonInk(face, UltraStyle.systemAccentCode(screenId));
        int labelPx = UltraStyle.labelMaxWidth(UltraStyle.BTN_W);
        // Footer labels name a destination; measure with real glyph widths so a label that fits
        // the button ("Back to Difficulty Home") is not cut by the 6px-per-character estimate.
        String plainLabel = UltraStyle.plain(label);
        String fitted = UltraStyle.textWidthPx(plainLabel) <= labelPx
                ? plainLabel
                : UltraStyle.fitAsciiLabel(UltraStyle.clipPlain(label, labelPx), labelPx);
        String wireLabel = plainWireLabel(fitted, accentCode);
        footerButtons.add(
                new Button(
                        qualifyAction(actionId),
                        wireLabel,
                        face,
                        x,
                        0,
                        UltraStyle.BTN_W,
                        buttonHeight,
                        UltraStyle.BTN_PAD,
                        null,
                        accentCode,
                        true,
                        UltraStyle.SOUND_CONFIRM,
                        UltraStyle.BUTTON_SKIN,
                        "",
                        label));
    }

    /**
     * Editable text field (GuiOpenPacket field 11 {@code inputsJson}). KaiPath <b>1.3.9+</b>
     * advertises {@code textInput} and renders EditBox widgets. Older clients ignore trailing
     * field 11 - pair shop pages with {@link UltraClientFeatures#hasTextInput}.
     *
     * <p>Pair with a button whose {@code args.bindInput} matches {@code inputId} so the client
     * copies the typed value into {@code args.value} on click / Enter.
     */
    public UltraScreenSpec textField(
            String inputId,
            String value,
            String placeholder,
            int x,
            int y,
            int w,
            int h,
            int maxLength) {
        UltraScreenIds.requireValidActionId(screenId, inputId, "text");
        if (firstButtonY < 0 || y < firstButtonY) {
            // Treat inputs like interactive rows for body/button packing.
            firstButtonY = y;
        }
        inputs.add(
                new Input(
                        qualifyAction(inputId),
                        "text",
                        value == null ? "" : value,
                        placeholder == null ? "" : placeholder,
                        x,
                        y,
                        Math.max(40, w),
                        Math.max(14, h),
                        Math.max(1, Math.min(256, maxLength <= 0 ? 64 : maxLength)),
                        null,
                        0,
                        null));
        return this;
    }

    /**
     * 4.7.66 color wheel ({@code inputsJson} {@code type=color_wheel}): a hue ring around a saturation/value square.
     * A tap fills the text field {@code targetInputId} with {@code #rrggbb}. Additive and ignorable: KaiPath and
     * older LM clients skip input types they do not know, and the native analyzer never routes a page on it
     * ({@link com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeProtocol#analyze}). Callers still add it only for a
     * client whose hello lists {@link ColorWheel#FEATURE}, so a bridge page carries the hex field alone.
     */
    public UltraScreenSpec colorWheel(String inputId, String targetInputId, int x, int y, int size) {
        UltraScreenIds.requireValidActionId(screenId, inputId, "color_wheel");
        UltraScreenIds.requireValidActionId(screenId, targetInputId, "color_wheel target");
        if (firstButtonY < 0 || y < firstButtonY) {
            firstButtonY = y;
        }
        int s = Math.max(ColorWheel.MIN_SIZE, size);
        inputs.add(new Input(qualifyAction(inputId), ColorWheel.TYPE, "", "", x, y, s, s, 0, null, 0,
                qualifyAction(targetInputId)));
        return this;
    }

    /**
     * 4.7.68 pictures in the body text (layoutJson {@code pictures}): the rows {@code textLines} take are where an LM
     * client whose hello lists {@code pictures} draws {@code items}, side by side; {@code minRows} keeps the rows tall
     * enough for them. {@code sidePermille} 0: the pictures take the whole width and the LM client hides those rows'
     * text (it is the fallback, e.g. a text map); above 0: they take that share of the width on the right and the
     * rows' text stays on the left (keep those lines short). KaiPath and older LM clients ignore the key and show the
     * text. Each item: {@code c} caption, {@code a} width/height x 1000, {@code l} layers
     * [texture, texW, texH, u, v, uW, vH, x, y, w, h] (texture: a name under legacymechanics:textures/gui/,
     * x..h in thousandths of the item), {@code m} marks [x, y, w, h, rgb] (thousandths).
     */
    public UltraScreenSpec pictures(List<String> textLines, int minRows, int sidePermille, JsonArray items) {
        int start = lines.size();
        for (String l : textLines) {
            line(l);
        }
        while (lines.size() - start < minRows) {
            lines.add("");                                 // an empty row: room for the pictures
        }
        pictures = new JsonObject();
        pictures.addProperty("line", start);
        pictures.addProperty("rows", lines.size() - start);
        pictures.addProperty("side", Math.max(0, Math.min(800, sidePermille)));
        pictures.add("items", items);
        return this;
    }

    /** 4.7.66: the text field {@code inputId} draws a small square of the color typed in it (LM client). */
    public UltraScreenSpec colorPreview(String inputId) {
        colorPreviewInputs.add(qualifyAction(inputId));
        return this;
    }

    /**
     * 4.7.66: every button on this page (footer excepted) carries {@code args.bindInputs}, and the LM client copies
     * each listed field's text into {@code args.<field id>} on click, so a toggle keeps what was typed and one Apply
     * reads several fields. KaiPath ignores the key (it reads {@code bindInput} only).
     */
    public UltraScreenSpec bindInputsToButtons(String... inputIds) {
        for (String id : inputIds) {
            if (id != null && !id.isBlank()) {
                bindAllInputs.add(qualifyAction(id));
            }
        }
        return this;
    }

    /** 4.7.66: Enter in a field clicks this button on the LM client ({@code args.onEnter}). */
    public UltraScreenSpec enterClicks(String actionId) {
        enterActionId = actionId == null ? null : qualifyAction(actionId);
        return this;
    }

    /**
     * Multi-page book widget ({@code inputsJson} {@code type=paged_book}). Advertise id is
     * camelCase {@code pagedBook}; wire type is snake_case {@code paged_book}.
     *
     * <p>Each page is {@code { "title": "...", "lines": ["...", ...] }}.
     */
    public UltraScreenSpec pagedBook(
            String inputId, int x, int y, int w, int h, JsonArray pages, int startPage) {
        UltraScreenIds.requireValidActionId(screenId, inputId, "paged_book");
        if (firstButtonY < 0 || y < firstButtonY) {
            firstButtonY = y;
        }
        JsonArray copy = pages == null ? new JsonArray() : pages.deepCopy();
        inputs.add(
                new Input(
                        qualifyAction(inputId),
                        "paged_book",
                        "",
                        "",
                        x,
                        y,
                        Math.max(80, w),
                        Math.max(40, h),
                        0,
                        copy,
                        Math.max(0, startPage),
                        null));
        return this;
    }

    /** Full-width text field on the current row (left column). */
    public UltraScreenSpec textFieldRow(String inputId, String value, String placeholder) {
        int width = Math.max(80, panelWidth - UltraStyle.COL_L * 2);
        return textField(
                inputId,
                value,
                placeholder,
                UltraStyle.COL_L,
                nextRow,
                width,
                buttonHeight,
                128);
    }

    /** Text field on the current row at an explicit x/width (main column layouts). */
    public UltraScreenSpec textFieldRow(
            String inputId, String value, String placeholder, int x, int width) {
        return textField(
                inputId,
                value,
                placeholder,
                Math.max(0, x),
                nextRow,
                Math.max(40, width),
                buttonHeight,
                128);
    }

    /** Build the wire packet without sending. */
    public GuiOpenPacket toPacket() {
        placeBack(null);                                    // no player (self-tests, probes): the bridge footer
        if (menuFit) {
            return toMenuPacket();
        }
        // Pinned footer band (left/right Back + Main menu) reserved under scrollable body.
        int footerReserve =
                footerButtons.isEmpty()
                        ? 0
                        : buttonHeight + UltraStyle.BTN_GAP + UltraStyle.PANEL_MARGIN;
        int contentBottom =
                nextRow
                        + (buttons.isEmpty() ? 0 : buttonHeight)
                        + UltraStyle.PANEL_MARGIN
                        + footerReserve;
        for (Button b : fixedButtons) {
            contentBottom = Math.max(contentBottom, b.y + b.h + UltraStyle.PANEL_MARGIN + footerReserve);
        }
        int bodyTop = tabs.isEmpty() ? UltraStyle.BODY_TOP : UltraStyle.BODY_TOP_TABS;
        int lineGap = lineGapOverride != null ? lineGapOverride : UltraStyle.LINE_GAP;
        int bodyPad = bodyPadOverride != null ? bodyPadOverride : UltraStyle.BODY_PAD;
        int lineStep = UltraStyle.LINE_H + lineGap;
        int naturalBodyH =
                lines.isEmpty()
                        ? 0
                        : bodyPad * 2 + lines.size() * lineStep;

        // Grow toward PANEL_MAX_H when body text needs more viewport than the default
        // BODY_MIN_H band - shift buttons down so extra height expands the body, not
        // empty space under the footer. When there is no body copy (hub tiles), skip the
        // BODY_MIN_H reserve and shrink the panel to the button stack so menus stay tight
        // as tiles are added or removed.
        int buttonTop = firstButtonY;
        int buttonStackH =
                buttonTop < 0
                        ? UltraStyle.PANEL_MARGIN + footerReserve
                        : Math.max(
                                buttonHeight + UltraStyle.PANEL_MARGIN + footerReserve,
                                contentBottom - buttonTop);
        int maxBodyAtCap =
                Math.max(
                        naturalBodyH <= 0 ? 0 : UltraStyle.BODY_MIN_H,
                        UltraStyle.PANEL_MAX_H - bodyTop - buttonStackH);
        int targetBodyH;
        if (naturalBodyH <= 0) {
            targetBodyH = 0;
        } else {
            targetBodyH =
                    Math.max(
                            UltraStyle.BODY_MIN_H,
                            Math.min(
                                    Math.max(naturalBodyH, UltraStyle.BODY_MIN_H), maxBodyAtCap));
        }
        int height;
        int buttonShift = 0;
        if (lockPanel) {
            // Staff config editor: keep the clamp request. Do not grow it toward PANEL_MAX_H.
            height = panelHeight;
        } else if (naturalBodyH <= 0) {
            // Compact tile menus (hub): pack to content; lower floor than PANEL_MIN_H so
            // few tile rows are not padded with a hollow band under the last row.
            int packed = Math.max(contentBottom, bodyTop + buttonStackH);
            height = Math.max(
                    UltraStyle.PANEL_COMPACT_MIN_H, Math.min(UltraStyle.PANEL_MAX_H, packed));
        } else {
            height = panelHeight;
            if (contentBottom > height) {
                height = Math.min(UltraStyle.PANEL_MAX_H, contentBottom);
            }
        }
        if (!lockPanel) {
            int targetHeight = bodyTop + targetBodyH + buttonStackH;
            if (targetHeight > height) {
                height = Math.min(UltraStyle.PANEL_MAX_H, targetHeight);
            }
            if (buttonTop >= 0 && targetBodyH > 0) {
                int currentBodyRoom = Math.max(0, buttonTop - UltraStyle.BTN_GAP - bodyTop);
                if (targetBodyH > currentBodyRoom) {
                    int need = targetBodyH - currentBodyRoom;
                    int slack = Math.max(0, height - contentBottom);
                    buttonShift = Math.min(need, slack);
                    // If still short of targetBodyH, grow panel once more (capped) then shift.
                    if (buttonShift < need && height < UltraStyle.PANEL_MAX_H) {
                        int grow = Math.min(need - buttonShift, UltraStyle.PANEL_MAX_H - height);
                        height += grow;
                        slack = Math.max(0, height - contentBottom);
                        buttonShift = Math.min(need, slack);
                    }
                }
            }
        }

        // Body region sits under title/tabs and above the first button row (or footer band).
        int effectiveButtonTop = buttonTop < 0 ? -1 : buttonTop + buttonShift;
        int bodyFloor =
                footerReserve > 0
                        ? height - footerReserve
                        : height - UltraStyle.PANEL_MARGIN;
        int bodyBottom =
                effectiveButtonTop < 0
                        ? bodyFloor
                        : Math.min(effectiveButtonTop - UltraStyle.BTN_GAP, bodyFloor);
        // Painted viewport is the room above the first button (after buttonShift).
        // Extra lines set scrollBody instead of painting through that row.
        int bodyMaxH = Math.max(0, bodyBottom - bodyTop);
        boolean scrollBody =
                naturalBodyH > bodyMaxH || contentBottom + buttonShift > UltraStyle.PANEL_MAX_H;

        // Empty tabsJson: stock KaiPath draws its tab strip inside the title chrome.
        // We place tabs as normal buttons under the header line (hub-like) instead.
        JsonArray tabArr = new JsonArray();
        JsonArray lineArr = new JsonArray();
        for (String line : lines) {
            if (line != null) {
                lineArr.add(line);
            }
        }
        JsonArray btnArr = new JsonArray();
        appendTabButtons(btnArr);
        for (Button b : buttons) {
            appendButtonJson(btnArr, b, buttonShift, height);
        }
        for (Button b : fixedButtons) {
            appendButtonJson(btnArr, b, 0, height);
        }
        // Pinned footer last — client honors footer:true at panel bottom.
        // Fullscreen design coordinates stay inside the design box. The live client
        // replaces this Y; a client that also ignores footer:true still draws it on-box.
        int footerBoxH = fillScreen && designHeight > 0 ? designHeight : height;
        int footerInset = fillScreen && designHeight > 0 ? panelMargin : UltraStyle.PANEL_MARGIN;
        int footerY = Math.max(
                UltraStyle.CONTENT_ROW, footerBoxH - footerInset - buttonHeight);
        for (Button b : footerButtons) {
            if (b == null) {
                continue;
            }
            appendButtonJson(
                    btnArr,
                    new Button(
                            b.actionId,
                            b.label,
                            b.style,
                            b.x,
                            footerY,
                            b.w,
                            b.h,
                            b.pad,
                            b.bindInput,
                            b.accent,
                            true,
                            b.sound,
                            b.buttonSkin,
                            b.icon,
                            b.raw),
                    0,
                    height);
        }
        JsonArray inputArr = new JsonArray();
        for (Input in : inputs) {
            if (in == null || in.id == null || in.id.isBlank()) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", in.id);
            o.addProperty("type", in.type);
            o.addProperty("x", in.x);
            o.addProperty("y", in.y + buttonShift);
            o.addProperty("w", in.w);
            o.addProperty("h", in.h);
            if ("paged_book".equalsIgnoreCase(in.type)) {
                o.add("pages", in.pages == null ? new JsonArray() : in.pages);
                o.addProperty("page", Math.max(0, in.page));
            } else if (ColorWheel.TYPE.equalsIgnoreCase(in.type)) {
                o.addProperty("target", in.target == null ? "" : in.target);
            } else {
                if (colorPreviewInputs.contains(in.id)) {
                    o.addProperty("colorPreview", true);
                }
                o.addProperty("value", UltraStyle.wireText(in.value == null ? "" : in.value));
                o.addProperty(
                        "placeholder",
                        UltraStyle.wireText(in.placeholder == null ? "" : in.placeholder));
                o.addProperty("maxLength", in.maxLength);
            }
            JsonObject args = new JsonObject();
            args.addProperty("screen", screenId);
            args.addProperty(UltraScreenIds.META_SYSTEM, screenId);
            args.addProperty("page", pageId);
            o.add("args", args);
            inputArr.add(o);
        }
        JsonObject layout = new JsonObject();
        layout.addProperty("panelWidth", panelWidth);
        layout.addProperty("panelHeight", height);
        layout.addProperty("width", panelWidth);
        layout.addProperty("height", height);
        layout.addProperty("center", centerPanel);
        layout.addProperty("margin", panelMargin);
        if (fillScreen) {
            // Opt-in. Clients that ignore these keys keep panelWidth x panelHeight
            // and clamp that rectangle to the GUI.
            int dw = designWidth > 0 ? designWidth : panelWidth;
            int dh = designHeight > 0 ? designHeight : height;
            layout.addProperty("fillScreen", true);
            layout.addProperty("designWidth", dw);
            layout.addProperty("designHeight", dh);
        }
        // Always present - KaiPath must center titles when value is "center" (not left-default).
        layout.addProperty("titleAlign", UltraStyle.TITLE_ALIGN);
        boolean underTitleTabs = !tabs.isEmpty();
        layout.addProperty("tabsUnderTitle", underTitleTabs);
        // Client chrome (title + yellow divider) must end at/above this Y; tab row sits at/after it.
        layout.addProperty(
                "headerBottom",
                underTitleTabs ? UltraStyle.HEADER_BOTTOM_TABS : UltraStyle.HEADER_BOTTOM);
        String accentCode = UltraStyle.systemAccentCode(screenId);
        if (accentCode != null && !accentCode.isBlank()) {
            // Single-letter color for title chrome + activeTab highlight (plain ASCII titles).
            layout.addProperty("accent", accentCode);
        }
        String paletteName = UltraStyle.systemPaletteName(screenId);
        if (paletteName != null && !paletteName.isBlank()) {
            // Named palette for clients that prefer names over accent letters (KaiPath 1.3.13+).
            layout.addProperty("palette", paletteName);
        }
        // Non-empty valid RL enables framed panel chrome (KaiPath 1.3.13+).
        layout.addProperty("frame", UltraStyle.PANEL_FRAME);
        // Shared body / button metrics - every screen benefits.
        layout.addProperty("lineHeight", UltraStyle.LINE_H);
        layout.addProperty(
                "lineGap", lineGapOverride != null ? lineGapOverride : UltraStyle.LINE_GAP);
        layout.addProperty("bodyTop", bodyTop);
        layout.addProperty(
                "bodyPad", bodyPadOverride != null ? bodyPadOverride : UltraStyle.BODY_PAD);
        layout.addProperty("bodyMaxHeight", bodyMaxH);
        layout.addProperty("scrollBody", scrollBody);
        layout.addProperty("buttonPad", UltraStyle.BTN_PAD);
        layout.addProperty("buttonGap", UltraStyle.BTN_GAP);
        layout.addProperty("buttonMinWidth", UltraStyle.BTN_MIN_W);
        layout.addProperty("buttonHeight", buttonHeight);
        if (pictures != null) {
            layout.add("pictures", pictures);
        }
        if (breadcrumb != null) {
            layout.add("breadcrumb", breadcrumb);
        }
        // Logical system metadata (survives hub wire shim in layoutJson).
        layout.addProperty(UltraScreenIds.META_SYSTEM, screenId);
        layout.addProperty("screen", screenId);
        if (playerModel != null) {
            JsonObject pm = new JsonObject();
            pm.addProperty("x", playerModel.x);
            pm.addProperty("y", playerModel.y);
            pm.addProperty("width", playerModel.width);
            pm.addProperty("height", playerModel.height);
            pm.addProperty("scale", playerModel.scale);
            pm.addProperty("rotate", playerModel.rotate);
            if (playerModel.parts != null && !playerModel.parts.entrySet().isEmpty()) {
                pm.add("parts", playerModel.parts);
                if (playerModel.parts.has("subject")) {
                    // Protocol field. parts.subject is the same token (local, or a player uuid).
                    pm.add("subject", playerModel.parts.get("subject"));
                }
            }
            // Omit portraitFrame so the preview has no border. A non-empty id enables KaiPath chrome.
            layout.add("playerModel", pm);
        }
        return new GuiOpenPacket(
                screenId,
                title,
                pageId,
                GSON.toJson(tabArr),
                activeTab,
                GSON.toJson(lineArr),
                GSON.toJson(btnArr),
                panelWidth,
                height,
                GSON.toJson(layout),
                GSON.toJson(inputArr));
    }

    /** The menu design box the player model is drawn in ({@link ModelPreview#menuBox}). */
    private ModelPreview.Box menuModelBox() {
        return ModelPreview.menuBox(
                new ModelPreview.Box(playerModel.x, playerModel.y, playerModel.width, playerModel.height));
    }

    /** One control of the menu reflow: a button or an input, with its legacy rectangle. */
    private record Cell(Button button, Input input, int x, int y, int w, int h) {
        boolean isButton() {
            return button != null;
        }
    }

    /** One row of the menu reflow: its cells and its height. */
    private record Row(List<Cell> cells, int h) {}

    /** Menu height of a legacy control in a menu row. */
    private static int menuCellH(Cell c) {
        if (c.isButton()) {
            return UltraStyle.MENU_BTN_H;
        }
        if ("paged_book".equalsIgnoreCase(c.input().type)) {
            return Math.max(40, Math.round(c.h() * 0.75f));
        }
        if (ColorWheel.TYPE.equalsIgnoreCase(c.input().type)) {
            return c.h();
        }
        return UltraStyle.MENU_BTN_H + 2;
    }

    /** Rows sharing a legacy Y, top to bottom, cells left to right. */
    private static List<Row> menuRows(List<Cell> cells) {
        java.util.TreeMap<Integer, List<Cell>> byY = new java.util.TreeMap<>();
        for (Cell c : cells) {
            byY.computeIfAbsent(c.y(), k -> new ArrayList<>()).add(c);
        }
        List<Row> rows = new ArrayList<>();
        for (List<Cell> row : byY.values()) {
            row.sort(java.util.Comparator.comparingInt(Cell::x));
            int h = 0;
            for (Cell c : row) {
                h = Math.max(h, menuCellH(c));
            }
            rows.add(new Row(row, h));
        }
        return rows;
    }

    /** Button label fitted to {@code w}, with this button's ink (same rules as {@link #addButton}). */
    private String menuLabel(Button b, int w) {
        String raw = b.raw == null ? UltraStyle.plain(b.label) : b.raw;
        int px = UltraStyle.labelMaxWidth(w);
        String plain = UltraStyle.plain(raw);
        String fitted = b.footer && UltraStyle.textWidthPx(plain) <= px
                ? plain
                : UltraStyle.fitAsciiLabel(UltraStyle.clipPlain(raw, px), px);
        return plainWireLabel(fitted, b.accent);
    }

    private Button menuButton(Button b, int x, int y, int w, int h) {
        return new Button(b.actionId, menuLabel(b, w), b.style, x, y, w, h, b.pad, b.bindInput, b.accent,
                b.footer, b.sound, b.buttonSkin, b.icon, b.raw);
    }

    /**
     * {@link #toPacket} for a {@link #fitMenu} screen (4.7.55): a centered panel sized to the page.
     *
     * <ul>
     *   <li>Title band, then the tabs at {@link UltraStyle#MENU_TAB_Y} across the panel.
     *   <li>Player model at its {@link ModelPreview#menuBox} on the right, its spin toggle under it.
     *       KaiPath pins the model to the panel while the page scrolls, so every row stays left of it,
     *       and on a page taller than {@link UltraStyle#MENU_SMALL_H} the toggle joins the left rows.
     *   <li>Body text from the content top, all of it (the page scrolls, not the body).
     *   <li>Controls in their legacy rows, widened from the 350 grid to the content width and packed
     *       under the body.
     *   <li>Footer row under the content: Back on the left, Main menu on the right. It is a normal
     *       row (no {@code footer:true}), so it scrolls with the page and is never pinned off-screen.
     *   <li>Panel height: the lowest control, {@link UltraStyle#MENU_END_PAD}, then KaiPath's Close band.
     * </ul>
     */
    private GuiOpenPacket toMenuPacket() {
        final int edge = UltraStyle.MENU_EDGE;
        final int gap = UltraStyle.MENU_GAP;
        final int bh = UltraStyle.MENU_BTN_H;
        boolean hasTabs = !tabs.isEmpty();
        int headerBottom = hasTabs ? UltraStyle.MENU_TAB_Y : UltraStyle.MENU_HEADER_BOTTOM;
        int contentTop = hasTabs ? UltraStyle.MENU_TAB_Y + bh + 4 : UltraStyle.MENU_HEADER_BOTTOM;

        List<Cell> cells = new ArrayList<>();
        for (Button b : buttons) {
            cells.add(new Cell(b, null, b.x, b.y, b.w, b.h));
        }
        if (playerModel == null) {
            for (Button b : fixedButtons) {
                cells.add(new Cell(b, null, b.x, b.y, b.w, b.h));
            }
        }
        for (Input in : inputs) {
            if (in != null && in.id != null && !in.id.isBlank()) {
                cells.add(new Cell(null, in, in.x, in.y, in.w, in.h));
            }
        }
        List<Row> rows = menuRows(cells);
        final int boxW = menuWidth(rows);
        ModelPreview.Box model = playerModel == null ? null : menuModelBox(boxW);
        int left = edge;
        int right = model == null ? boxW - edge : model.x() - 8;
        int legacyLeft = UltraStyle.COL_L;
        int legacyRight = playerModel == null
                ? UltraStyle.COL_R + UltraStyle.BTN_W
                : Math.max(legacyLeft + UltraStyle.BTN_W, playerModel.x - 8);

        int lineStep = UltraStyle.MENU_LINE_H + UltraStyle.MENU_LINE_GAP;
        int pad = UltraStyle.MENU_BODY_PAD;
        int bodyH = lines.isEmpty() ? 0 : pad * 2 + lines.size() * lineStep;

        JsonArray btnArr = new JsonArray();
        appendTabButtons(btnArr, edge, boxW - edge * 2, UltraStyle.MENU_TAB_Y, bh, gap);
        JsonArray inputArr = new JsonArray();
        // The model does not scroll with the page, so every row keeps to the left of it.
        double fx = (right - left) / (double) Math.max(1, legacyRight - legacyLeft);
        int y = contentTop + (bodyH > 0 ? bodyH + gap : 0);
        int bottom = contentTop + bodyH;
        for (Row row : rows) {
            int n = row.cells().size();
            int[] xs = new int[n];
            int[] ws = new int[n];
            boolean clean = true;
            for (int i = 0; i < n; i++) {
                Cell c = row.cells().get(i);
                int w = (int) Math.round(c.w() * fx);
                if (c.isButton()) {
                    w = Math.max(UltraStyle.BTN_MIN_W, w);
                }
                w = Math.min(w, right - left);
                int x = left + (int) Math.round((c.x() - legacyLeft) * fx);
                xs[i] = Math.max(left, Math.min(x, right - w));
                ws[i] = w;
                if (i > 0 && xs[i] < xs[i - 1] + ws[i - 1] + gap) {
                    clean = false;
                }
            }
            if (!clean) {
                // Widening pushed two controls together: give the row equal columns instead.
                int w = (right - left - gap * (n - 1)) / n;
                for (int i = 0; i < n; i++) {
                    ws[i] = w;
                    xs[i] = left + i * (w + gap);
                }
            }
            for (int i = 0; i < n; i++) {
                Cell c = row.cells().get(i);
                if (c.isButton()) {
                    appendButtonJson(btnArr, menuButton(c.button(), xs[i], y, ws[i], bh), 0, 0);
                } else {
                    inputArr.add(menuInputJson(c.input(), xs[i], y, ws[i], menuCellH(c)));
                }
            }
            bottom = Math.max(bottom, y + row.h());
            y += row.h() + gap;
        }
        if (model != null) {
            int modelBottom = model.y() + model.h();
            int columnBottom = modelBottom + fixedButtons.size() * (bh + gap);
            int footerH = footerButtons.isEmpty() ? 0 : UltraStyle.MENU_FOOTER_GAP + bh;
            // KaiPath pins the model while the page scrolls. On a page that scrolls at 1080p GUI
            // scale 4 a spin toggle under the model would scroll up behind it, so it joins the
            // rows on the left instead (just above the footer); a shorter page keeps it under the model.
            boolean scrolls = Math.max(bottom, columnBottom) + footerH + UltraStyle.MENU_END_PAD
                    + UltraStyle.MENU_CLOSE_BAND > UltraStyle.MENU_SMALL_H;
            bottom = Math.max(bottom, modelBottom);
            int ty = scrolls ? y : modelBottom + gap;
            for (Button b : fixedButtons) {
                int w;
                int x;
                if (scrolls) {
                    w = Math.max(UltraStyle.BTN_MIN_W, Math.min(b.w, right - left));
                    x = left;
                } else {
                    w = Math.max(UltraStyle.BTN_MIN_W, Math.min(b.w, model.w()));
                    x = Math.min(boxW - edge - w, model.x() + (model.w() - w) / 2);
                }
                appendButtonJson(btnArr, menuButton(b, x, ty, w, bh), 0, 0);
                bottom = Math.max(bottom, ty + bh);
                ty += bh + gap;
            }
        }
        boolean footerRow = false;
        int footerY = bottom + UltraStyle.MENU_FOOTER_GAP;
        for (Button b : footerButtons) {
            if (b == null) {
                continue;
            }
            // Back on the left; Main menu flush with the right content edge. Not pinned: the
            // footer row scrolls with the page, so a capped panel never hides it.
            // 4.7.81 (GuiKit): as wide as a widened two-column row, so Back and Main menu line up with the columns.
            int fw = Math.max(UltraStyle.BTN_MIN_W, Math.min((boxW - edge * 2 - gap) / 2,
                    (int) Math.round(UltraStyle.BTN_W * (boxW - edge - left)
                            / (double) Math.max(1, UltraStyle.COL_R + UltraStyle.BTN_W - legacyLeft))));
            int x = b.x <= UltraStyle.COL_L ? edge : boxW - edge - fw;
            appendButtonJson(btnArr, menuFooterButton(b, x, footerY, fw, bh), 0, 0);
            footerRow = true;
        }
        if (footerRow) {
            bottom = footerY + bh;
        }
        int panelH = Math.min(MENU_MAX_REQUEST_H, bottom + UltraStyle.MENU_END_PAD + UltraStyle.MENU_CLOSE_BAND);

        JsonArray lineArr = new JsonArray();
        for (String line : lines) {
            if (line != null) {
                lineArr.add(line);
            }
        }
        JsonObject layout = new JsonObject();
        layout.addProperty("panelWidth", boxW);
        layout.addProperty("panelHeight", panelH);
        layout.addProperty("width", boxW);
        layout.addProperty("height", panelH);
        layout.addProperty("center", true);
        layout.addProperty("margin", UltraStyle.PANEL_MARGIN);
        layout.addProperty("titleAlign", UltraStyle.TITLE_ALIGN);
        layout.addProperty("tabsUnderTitle", hasTabs);
        layout.addProperty("headerBottom", headerBottom);
        String accentCode = UltraStyle.systemAccentCode(screenId);
        if (accentCode != null && !accentCode.isBlank()) {
            layout.addProperty("accent", accentCode);
        }
        String paletteName = UltraStyle.systemPaletteName(screenId);
        if (paletteName != null && !paletteName.isBlank()) {
            layout.addProperty("palette", paletteName);
        }
        layout.addProperty("frame", UltraStyle.PANEL_FRAME);
        layout.addProperty("lineHeight", UltraStyle.MENU_LINE_H);
        layout.addProperty("lineGap", UltraStyle.MENU_LINE_GAP);
        layout.addProperty("bodyTop", contentTop);
        layout.addProperty("bodyPad", pad);
        layout.addProperty("bodyMaxHeight", bodyH);
        // The whole page scrolls in KaiPath's content band; the body is drawn at its full height.
        layout.addProperty("scrollBody", false);
        layout.addProperty("buttonPad", UltraStyle.BTN_PAD);
        layout.addProperty("buttonGap", gap);
        layout.addProperty("buttonMinWidth", UltraStyle.BTN_MIN_W);
        layout.addProperty("buttonHeight", bh);
        if (pictures != null) {
            layout.add("pictures", pictures);
        }
        if (breadcrumb != null) {
            layout.add("breadcrumb", breadcrumb);
        }
        layout.addProperty(UltraScreenIds.META_SYSTEM, screenId);
        layout.addProperty("screen", screenId);
        if (model != null) {
            JsonObject pm = new JsonObject();
            pm.addProperty("x", model.x());
            pm.addProperty("y", model.y());
            pm.addProperty("width", model.w());
            pm.addProperty("height", model.h());
            pm.addProperty("scale", playerModel.scale);
            pm.addProperty("rotate", playerModel.rotate);
            if (playerModel.parts != null && !playerModel.parts.entrySet().isEmpty()) {
                pm.add("parts", playerModel.parts);
                if (playerModel.parts.has("subject")) {
                    pm.add("subject", playerModel.parts.get("subject"));
                }
            }
            layout.add("playerModel", pm);
        }
        return new GuiOpenPacket(
                screenId,
                title,
                pageId,
                GSON.toJson(new JsonArray()),
                activeTab,
                GSON.toJson(lineArr),
                GSON.toJson(btnArr),
                boxW,
                panelH,
                GSON.toJson(layout),
                GSON.toJson(inputArr));
    }

    /**
     * Menu width (4.7.55): {@link UltraStyle#MENU_W}, or {@link UltraStyle#MENU_MODEL_W} with a
     * player model; wider when the tab row, the title or a row of 80px buttons needs it; never over
     * {@link UltraStyle#MENU_MAX_W} (1080p at GUI scale 4).
     */
    private int menuWidth(List<Row> rows) {
        int edge = UltraStyle.MENU_EDGE;
        int gap = UltraStyle.MENU_GAP;
        int modelCol = playerModel == null ? 0 : menuModelBox().w() + 8;
        int w = playerModel == null ? UltraStyle.MENU_W : UltraStyle.MENU_MODEL_W;
        int tabCount = 0;
        for (Tab tab : tabs) {
            if (tab != null && tab.id != null && !tab.id.isBlank()) {
                tabCount++;
            }
        }
        if (tabCount > 0) {
            w = Math.max(w, edge * 2 + tabCount * UltraStyle.BTN_MIN_W + (tabCount - 1) * gap);
        }
        for (Row r : rows) {
            int n = r.cells().size();
            w = Math.max(w, edge * 2 + n * UltraStyle.BTN_MIN_W + (n - 1) * gap + modelCol);
        }
        if (title != null && !title.isBlank()) {
            w = Math.max(w, UltraStyle.textWidthPx(UltraStyle.plain(title)) + edge * 2 + 16);
        }
        return Math.min(UltraStyle.MENU_MAX_W, w);
    }

    /** The menu model box moved to the right edge of a {@code boxW}-wide panel. */
    private ModelPreview.Box menuModelBox(int boxW) {
        ModelPreview.Box b = menuModelBox();
        int dx = boxW - UltraStyle.MENU_MODEL_W;
        return dx == 0 ? b : new ModelPreview.Box(b.x() + dx, b.y(), b.w(), b.h());
    }

    /** A footer button sent as a normal row (4.7.55): same label and ink, but it scrolls with the page. */
    private Button menuFooterButton(Button b, int x, int y, int w, int h) {
        return new Button(b.actionId, menuLabel(b, w), b.style, x, y, w, h, b.pad, b.bindInput, b.accent,
                false, b.sound, b.buttonSkin, b.icon, b.raw);
    }

    private JsonObject menuInputJson(Input in, int x, int y, int w, int h) {
        JsonObject o = new JsonObject();
        o.addProperty("id", in.id);
        o.addProperty("type", in.type);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("w", w);
        o.addProperty("h", h);
        if ("paged_book".equalsIgnoreCase(in.type)) {
            o.add("pages", in.pages == null ? new JsonArray() : in.pages);
            o.addProperty("page", Math.max(0, in.page));
        } else if (ColorWheel.TYPE.equalsIgnoreCase(in.type)) {
            o.addProperty("target", in.target == null ? "" : in.target);
        } else {
            o.addProperty("value", UltraStyle.wireText(in.value == null ? "" : in.value));
            o.addProperty("placeholder", UltraStyle.wireText(in.placeholder == null ? "" : in.placeholder));
            o.addProperty("maxLength", in.maxLength);
            if (colorPreviewInputs.contains(in.id)) {
                o.addProperty("colorPreview", true);
            }
        }
        JsonObject args = new JsonObject();
        args.addProperty("screen", screenId);
        args.addProperty(UltraScreenIds.META_SYSTEM, screenId);
        args.addProperty("page", pageId);
        o.add("args", args);
        return o;
    }

    public void send(ServerPlayer player) {
        ServerPlayer viewer = inspectViewer();
        ServerPlayer dest = viewer != null ? viewer : player;
        prependInspectBanner(viewer != null ? viewer : player);
        // Pending do: results (purchases, confirms, errors) — viewer-scoped during inspect.
        UltraGuiFeedback.applyPending(this, dest);
        // 4.7.71: Back and the breadcrumb for this player, then the page joins their history.
        placeBack(dest);
        placeBreadcrumb(dest);
        GuiOpenPacket packet = toPacket();
        if (dest != null) {
            // The id as it goes on the wire (long ids are cut to the packet limit): Back compares against what the
            // client sends back.
            NavHistory.record(dest.m_20148_(), screenId, packet.pageId());
            if (PageRegistry.find(screenId, pageId) == null) {
                com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] page {}|{} has no PageRegistry entry (no breadcrumb, Back to Home)",
                        com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod.MOD_ID, screenId, pageId);
            }
        }
        List<GuiOpenPacket> probe = PROBE.get();
        if (probe != null) {
            probe.add(packet);
            return;
        }
        if (dest == null) {
            return;
        }
        LmGuiNetwork.sendOpen(dest, packet);
    }

    /**
     * Insert styled body lines after the inspect banner (if any). Used by
     * {@link UltraGuiFeedback} so action outcomes appear at the top of the reopened page.
     */
    void prependBodyLines(List<String> styledLines) {
        if (styledLines == null || styledLines.isEmpty()) {
            return;
        }
        int at = 0;
        if (!lines.isEmpty()) {
            String first = UltraStyle.plain(lines.get(0)).toLowerCase(Locale.ROOT);
            if (first.startsWith("inspecting")) {
                at = 1;
            }
        }
        for (String line : styledLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            int before = lines.size();
            addWrapped(at, line);
            at += lines.size() - before;
        }
    }

    /** First body line while staff is inspecting another player. */
    private void prependInspectBanner(ServerPlayer viewer) {
        if (viewer == null) {
            return;
        }
        String banner = AdminInspectSessions.inspectBannerLine(viewer);
        if (banner == null || banner.isBlank()) {
            return;
        }
        String plain = UltraStyle.plain(banner).toLowerCase(Locale.ROOT);
        if (!lines.isEmpty()) {
            String first = UltraStyle.plain(lines.get(0)).toLowerCase(Locale.ROOT);
            if (first.startsWith("inspecting")) {
                return;
            }
        }
        if (!plain.startsWith("inspecting")) {
            return;
        }
        addWrapped(0, banner);
    }

    /**
     * Hub-style tab row under the title/header line. Not counted in {@link #firstButtonY}
     * so body packing still sits below content buttons, not the tab chrome.
     */
    private void appendTabButtons(JsonArray btnArr) {
        appendTabButtons(btnArr, UltraStyle.COL_L, Math.max(1, panelWidth - UltraStyle.COL_L * 2),
                UltraStyle.TAB_ROW_Y, UltraStyle.BTN_H, UltraStyle.BTN_GAP);
    }

    /** Tab buttons across {@code inner} px from {@code left} at {@code y}, {@code h} tall. */
    private void appendTabButtons(JsonArray btnArr, int left, int inner, int y, int h, int gap) {
        if (btnArr == null || tabs.isEmpty()) {
            return;
        }
        int count = 0;
        for (Tab tab : tabs) {
            if (tab != null && tab.id != null && !tab.id.isBlank()) {
                count++;
            }
        }
        if (count == 0) {
            return;
        }
        int tabW = Math.max(UltraStyle.BTN_MIN_W, (inner - gap * Math.max(0, count - 1)) / count);
        if (tabW * count + gap * Math.max(0, count - 1) > inner) {
            tabW = Math.max(40, (inner - gap * Math.max(0, count - 1)) / count);
        }
        int x = left;
        for (Tab tab : tabs) {
            if (tab == null || tab.id == null || tab.id.isBlank()) {
                continue;
            }
            UltraScreenIds.requireValidActionId(screenId, "tab:" + tab.id, tab.label);
            boolean active = tab.id.equalsIgnoreCase(activeTab);
            // Plain ASCII only — startup self-test requires exact "Home"/"Saga"/"Android"
            // (no section-sign/accent prefixes). Active state is style=normal vs dim; title tint stays
            // on layoutJson.accent.
            int tabPx = UltraStyle.labelMaxWidth(tabW);
            String fitted = UltraStyle.fitAsciiLabel(UltraStyle.clipPlain(tab.label, tabPx), tabPx);
            String label = UltraStyle.plain(fitted);
            String style = tabStyle(tab, active);
            JsonObject o = new JsonObject();
            o.addProperty("id", qualifyAction("tab:" + tab.id));
            o.addProperty("label", label);
            o.addProperty("style", style);
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("w", tabW);
            o.addProperty("h", h);
            o.addProperty("pad", UltraStyle.BTN_PAD);
            String icon = tab.icon == null || tab.icon.isBlank()
                    ? UltraStyle.systemIcon(screenId)
                    : tab.icon;
            putIcon(o, icon);
            // No sound/buttonSkin — buttonSkin forces KaiPath skinned (flat) chrome.
            JsonObject args = new JsonObject();
            args.addProperty("screen", screenId);
            args.addProperty(UltraScreenIds.META_SYSTEM, screenId);
            args.addProperty("page", pageId);
            o.add("args", args);
            btnArr.add(o);
            x += tabW + gap;
        }
    }

    /** Prefix {@code @screenId:} unless the action is already qualified. */
    String qualifyAction(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return "@" + screenId + ":nav:home";
        }
        String raw = actionId.trim();
        if (raw.startsWith("@")) {
            return raw;
        }
        return "@" + screenId + ":" + raw;
    }

    private static String normalizeStyle(String style) {
        if (style == null || style.isBlank()) {
            return UltraStyle.STYLE_NORMAL;
        }
        String s = style.trim().toLowerCase();
        return switch (s) {
            case "confirm" -> UltraStyle.STYLE_CONFIRM;
            case "danger" -> UltraStyle.STYLE_DANGER;
            case "dim" -> UltraStyle.STYLE_DIM;
            case "normal" -> UltraStyle.STYLE_NORMAL;
            default -> UltraStyle.STYLE_NORMAL;
        };
    }

    /**
     * Inactive tabs stay dim (dark). Active and staff tabs use normal — highlight comes from
     * {@code accent} + {@code activeTab}, not shared confirm yellow.
     */
    private static String tabStyle(Tab tab, boolean active) {
        if (tab != null && tab.staffOnly()) {
            return UltraStyle.STYLE_NORMAL;
        }
        return active ? UltraStyle.STYLE_NORMAL : UltraStyle.STYLE_DIM;
    }

    /**
     * Wire label for buttonsJson. Character forbids section-sign / C2 bytes (startup
     * self-test); other screens may keep UltraStyle.color ink for hub-style tiles.
     */
    private String plainWireLabel(String fittedPlain, String accentCode) {
        String plain = UltraStyle.plain(fittedPlain);
        if ("character".equals(screenId)) {
            return plain;
        }
        if (accentCode == null || accentCode.isBlank()) {
            return plain;
        }
        return UltraStyle.color(accentCode, plain);
    }

    private static String normalizeAccent(String accent) {
        if (accent == null || accent.isBlank()) {
            return "";
        }
        String a = accent.trim();
        if (!a.isEmpty() && a.charAt(0) == '\u00A7' && a.length() >= 2) {
            a = a.substring(1);
        }
        return a.substring(0, 1).toLowerCase();
    }

    /**
     * @param styleHint optional {@code danger}/{@code staff} (or blank). Ids {@code admin}/
     *     {@code staff}/{@code stats} are treated as staff-only even without a hint.
     */
    /**
     * {@code tabsJson} entry. LM does not send this strip (KaiPath would draw a second tab row
     * in the title chrome). Partners that do use the strip may copy this shape, including
     * optional {@code icon}.
     */
    public static String tabEntryJson(String id, String label, String icon) {
        JsonObject o = new JsonObject();
        String tabId = id == null ? "" : id.trim();
        o.addProperty("id", tabId);
        o.addProperty("label", label == null || label.isBlank() ? tabId : label.trim());
        putIcon(o, icon);
        return o.toString();
    }

    /** Omit {@code icon} when the value is blank or not {@code namespace:path}. */
    private static void putIcon(JsonObject o, String icon) {
        if (o == null) {
            return;
        }
        String norm = UltraStyle.normalizeIcon(icon);
        if (!norm.isEmpty()) {
            o.addProperty("icon", norm);
        }
    }

    private record Tab(String id, String label, String styleHint, String icon) {
        boolean staffOnly() {
            String hint = styleHint == null ? "" : styleHint.trim().toLowerCase();
            if ("danger".equals(hint) || "staff".equals(hint)) {
                return true;
            }
            String tid = id == null ? "" : id.trim().toLowerCase();
            return "admin".equals(tid) || "staff".equals(tid) || "stats".equals(tid);
        }
    }

    private void appendButtonJson(JsonArray btnArr, Button b, int buttonShift, int panelH) {
        if (btnArr == null || b == null) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("id", b.actionId);
        o.addProperty("label", b.label);
        o.addProperty("style", b.style);
        // Zero color keys (no accent/palette/tint) — KaiPath default chrome.
        o.addProperty("x", b.x);
        // Footer Y is chosen by toPacket (design box when fillScreen sets one, otherwise
        // the packed panel). Do not replace it with the clamp-request height.
        int y = b.footer ? b.y : b.y + buttonShift;
        o.addProperty("y", y);
        o.addProperty("w", b.w);
        o.addProperty("h", b.h);
        o.addProperty("pad", b.pad);
        putIcon(o, b.icon);
        if (b.footer) {
            o.addProperty("footer", true);
        }
        // No sound/buttonSkin — matches 4.6.148 wire shape; buttonSkin triggers flat skinned paint.
        JsonObject args = new JsonObject();
        // Logical system - echoed by KaiPath into GuiActionPacket even when wire screenId is hub.
        args.addProperty("screen", screenId);
        args.addProperty(UltraScreenIds.META_SYSTEM, screenId);
        args.addProperty("page", pageId);
        if (b.bindInput != null && !b.bindInput.isBlank()) {
            args.addProperty("bindInput", b.bindInput);
        }
        if (!bindAllInputs.isEmpty() && !b.footer) {
            // 4.7.66: the LM client copies every listed field into args under its own id.
            args.addProperty("bindInputs", String.join("|", bindAllInputs));
        }
        if (enterActionId != null && enterActionId.equals(b.actionId)) {
            args.addProperty("onEnter", true);
        }
        o.add("args", args);
        btnArr.add(o);
    }

    private record Button(
            String actionId,
            String label,
            String style,
            int x,
            int y,
            int w,
            int h,
            int pad,
            String bindInput,
            String accent,
            boolean footer,
            String sound,
            String buttonSkin,
            String icon,
            /** Label as the builder passed it, so a menu reflow can fit it to the new width. */
            String raw) {}

    private record Input(
            String id,
            String type,
            String value,
            String placeholder,
            int x,
            int y,
            int w,
            int h,
            int maxLength,
            JsonArray pages,
            int page,
            /** 4.7.66 color_wheel: the text field id a pick fills (qualified). */
            String target) {}

    private record PlayerModel(
            int x, int y, int width, int height, float scale, boolean rotate, JsonObject parts) {}
}
