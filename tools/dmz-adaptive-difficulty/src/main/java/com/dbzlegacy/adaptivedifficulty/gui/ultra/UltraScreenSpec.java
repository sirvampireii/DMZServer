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
    private PlayerModel playerModel;
    /** Lowest button top Y seen so far; used to size the body scroll region. */
    private int firstButtonY = -1;
    /** Pinned footer buttons ({@code footer:true}) — placed at panel bottom in {@link #toPacket}. */
    private final List<Button> footerButtons = new ArrayList<>();
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

    /** Clear any previously set player model (default: off). */
    public UltraScreenSpec clearPlayerModel() {
        this.playerModel = null;
        return this;
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

    public UltraScreenSpec button(String actionId, String label, String style) {
        return button(actionId, label, style, UltraStyle.COL_L, nextRow, UltraStyle.BTN_W);
    }

    public UltraScreenSpec buttonLeft(String actionId, String label, String style) {
        return button(actionId, label, style, UltraStyle.COL_L, nextRow, UltraStyle.BTN_W);
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
                ""));
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
                        norm));
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
     * Tabbed system-page footer: right {@code Main menu} only ({@code nav:hub}). Tabs are the
     * in-system navigation — no left {@code Back to …}. Pinned ({@code footer:true}).
     */
    public UltraScreenSpec footerBackToMain() {
        return placeFooter(null, null, "nav:hub", UltraStyle.MAIN_MENU);
    }

    /**
     * Sub-page footer (no tab row): left {@code Back to {destination}} ({@code nav:home}) + right
     * {@code Main menu} ({@code nav:hub}). Both pinned ({@code footer:true}).
     *
     * @param destination plain words naming where left goes (e.g. {@code Rival Home}, {@code Shop})
     */
    public UltraScreenSpec footerBackHome(String destination) {
        return placeFooter(
                "nav:home",
                UltraStyle.backTo(destination),
                "nav:hub",
                UltraStyle.MAIN_MENU);
    }

    /**
     * Nested sub-page footer (no tab row): left {@code Back to {destination}} + right
     * {@code Main menu}. Use when the parent is not the system home tab.
     */
    public UltraScreenSpec footerBack(String backActionId, String destination) {
        String action =
                backActionId == null || backActionId.isBlank() ? "nav:home" : backActionId.trim();
        return placeFooter(action, UltraStyle.backTo(destination), "nav:hub", UltraStyle.MAIN_MENU);
    }

    /**
     * @deprecated use {@link #footerBackHome(String)} so the label names the destination.
     */
    @Deprecated
    public UltraScreenSpec footerBackHome() {
        return footerBackHome("Home");
    }

    private UltraScreenSpec placeFooter(
            String leftActionId, String leftLabel, String rightActionId, String rightLabel) {
        // Footer Y is resolved in toPacket; placeholder 0. Both faces stay normal.
        // Dim chrome reads as a disabled control.
        if (leftActionId != null && !leftActionId.isBlank()) {
            addFooterButton(leftActionId, leftLabel, UltraStyle.COL_L, UltraStyle.STYLE_NORMAL);
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
        String accentCode = UltraStyle.readableButtonInk(face, UltraStyle.systemAccentCode(screenId));
        // White or unset ink disappears on the light normal face. Dark gray stays readable.
        if (UltraStyle.STYLE_NORMAL.equals(face) && (accentCode.isEmpty() || "f".equals(accentCode))) {
            accentCode = "8";
        }
        int labelPx = UltraStyle.labelMaxWidth(UltraStyle.BTN_W);
        String fitted = UltraStyle.fitAsciiLabel(UltraStyle.clipPlain(label, labelPx), labelPx);
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
                        ""));
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
                        0));
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
                        Math.max(0, startPage)));
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
                            b.icon),
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
            } else {
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

    public void send(ServerPlayer player) {
        ServerPlayer viewer = inspectViewer();
        ServerPlayer dest = viewer != null ? viewer : player;
        prependInspectBanner(viewer != null ? viewer : player);
        // Pending do: results (purchases, confirms, errors) — viewer-scoped during inspect.
        UltraGuiFeedback.applyPending(this, dest);
        GuiOpenPacket packet = toPacket();
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
        int gap = UltraStyle.BTN_GAP;
        int inner = Math.max(1, panelWidth - UltraStyle.COL_L * 2);
        int tabW = Math.max(UltraStyle.BTN_MIN_W, (inner - gap * Math.max(0, count - 1)) / count);
        if (tabW * count + gap * Math.max(0, count - 1) > inner) {
            tabW = Math.max(40, (inner - gap * Math.max(0, count - 1)) / count);
        }
        int x = UltraStyle.COL_L;
        int y = UltraStyle.TAB_ROW_Y;
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
            o.addProperty("h", UltraStyle.BTN_H);
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
            String icon) {}

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
            int page) {}

    private record PlayerModel(
            int x, int y, int width, int height, float scale, boolean rotate, JsonObject parts) {}
}
