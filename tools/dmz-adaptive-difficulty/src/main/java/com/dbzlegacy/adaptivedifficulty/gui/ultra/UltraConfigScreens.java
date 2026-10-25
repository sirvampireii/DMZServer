package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.editor.ConfigEditor;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.UltraClientFeatures;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Staff config editor. The panel request is larger than the GUI so KaiPath 1.3.20, which
 * ignores {@code fillScreen}, clamps the chrome to the window. Controls are laid out in
 * {@link #CONFIG_DESIGN_W}x{@link #CONFIG_DESIGN_H}, the smallest auto GUI (about 426x240),
 * with a wide category rail. Clients that honor {@code fillScreen} scale that box up.
 *
 * <p>Sections such as {@code Difficulty > Mob Spawning} open and collapse from the index.
 * Each field shows a plain label, a one-line description, and the current value next to
 * the default. Edits stay in memory until Review changes, then Confirm save.
 * Rival/spar record files stay closed.
 */
public final class UltraConfigScreens {
    /**
     * Coordinate space that fits the smallest auto GUI (1440p / 4K, about 426x240).
     * 1080p auto scale is about 480x270; the live client clamps the panel request to
     * that window and draws these coordinates unscaled.
     */
    private static final int CONFIG_DESIGN_W = 426;
    private static final int CONFIG_DESIGN_H = 240;
    /**
     * Larger than an auto GUI (and under the 2000px packet cap). KaiPath 1.3.20 clamps
     * this down to {@code screen - margin * 2}. Margin 0 makes the clamp equal the window.
     */
    private static final int CONFIG_PANEL_W = 1920;
    private static final int CONFIG_PANEL_H = 1080;
    private static final int SIDE_X = 8;
    private static final int EDGE = 8;
    /** Wider than the old 108px rail so category names stay on one line. */
    private static final int SIDE_W = 148;
    private static final int MAIN_X = SIDE_X + SIDE_W + EDGE;
    private static final int MAIN_W = CONFIG_DESIGN_W - MAIN_X - EDGE;
    /** Extra gap under a wrapped flow row. */
    private static final int LABEL_CTRL_GAP = 4;
    /** Min clear space between two controls on one row before we stack instead. */
    private static final int CTRL_GAP = 8;
    /**
     * Config-editor button height. Tighter than the shared {@link UltraStyle#BTN_H} (18), so the
     * label sits close to the button edges instead of floating in tall empty space - this is the
     * vertical padding the config editor tightens, and it applies to the sidebar rail, section
     * rows, and the Review bar alike (all emit {@link UltraScreenSpec#buttonHeight}). Row spacing,
     * text-field height, and the footer pin all follow from this one number. The 80px minimum
     * width and the 8px horizontal padding stay put; only the height gives.
     */
    private static final int CONFIG_BTN_H = 13;
    /** One control row plus a 2px gap. Field blocks are multiples of this. */
    private static final int ROW_STEP = CONFIG_BTN_H + 2;
    /** First content row under the title divider. */
    private static final int SEARCH_Y = UltraStyle.CONTENT_ROW;
    /** Live client's close-band inset: the pinned footer is reserved above it. */
    private static final int CLOSE_BAND_PX = 28;
    /**
     * Live {@code pinFooterY} on a 240-tall GUI with margin 0. Reserve is
     * {@code close band + buttonH + margin}, so a {@link #CONFIG_BTN_H} footer button pins at
     * {@code 240 - 41 = 199}. Review and paging use this Y, not the oversized panel height.
     */
    private static final int FOOTER_Y = CONFIG_DESIGN_H - (CLOSE_BAND_PX + CONFIG_BTN_H);
    /** Review / paging row, one step above the pinned footer. */
    private static final int REVIEW_Y = FOOTER_Y - ROW_STEP;
    /**
     * Glyphs that fit a main-column flow button ({@link #MAIN_W} minus pad).
     * Longer descriptions wrap onto the next row. A single token wider than the row
     * continues on the next row instead of being cut off.
     */
    private static final int FLOW_CHARS = 38;

    /** Categories always shown in the left rail (user-facing order). */
    private static final String[][] CATEGORIES = {
        {"difficulty", "Difficulty"},
        {"progression", "Progress"},
        {"character-services", "Character"},
        {"saga", "Saga"},
        {"rival", "Rival"},
        {"sparring", "Sparring"},
        {"currency", "Currency"},
    };

    private UltraConfigScreens() {}

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        if (!StaffAccess.isStaff(player)) {
            UltraHubScreens.open(player, "main");
            return;
        }
        String raw = page == null || page.isBlank() ? "edit:difficulty:0:" : page.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if ("review".equals(lower) || lower.startsWith("review:")) {
            openReview(player, Math.max(0, number(part(raw, 1))));
            return;
        }
        if (lower.startsWith("options:")) {
            // options:<module>:<page>:<path> - the path may itself contain ':'.
            String[] bits = raw.split(":", 4);
            openOptions(player, bits.length > 1 ? bits[1] : "", bits.length > 2 ? number(bits[2]) : 0,
                    bits.length > 3 ? bits[3] : "");
            return;
        }
        // Legacy drill-down pages → search-first editor.
        if ("modules".equals(lower) || "main".equals(lower) || "list".equals(lower)) {
            openEditor(player, "difficulty", 0, "");
            return;
        }
        if (lower.startsWith("module:")) {
            String id = raw.substring("module:".length()).trim();
            openEditor(player, id.isBlank() ? "difficulty" : id, 0, "");
            return;
        }
        if (lower.startsWith("list:")) {
            openEditor(player, part(raw, 1), Math.max(0, number(part(raw, 2))), part(raw, 3));
            return;
        }
        if (lower.startsWith("detail:")) {
            String module = part(raw, 1);
            String fieldPath = part(raw, 3);
            String parent = ConfigEditor.parentPath(fieldPath);
            openEditor(player, module.isBlank() ? "difficulty" : module, 0, parent);
            return;
        }
        if (lower.startsWith("edit:")) {
            openEditor(player, part(raw, 1), Math.max(0, number(part(raw, 2))), part(raw, 3));
            return;
        }
        openEditor(player, "difficulty", 0, "");
    }

    private static void openEditor(ServerPlayer player, String moduleId, int pageNumber, String path) {
        String module = moduleId == null || moduleId.isBlank() ? "difficulty" : moduleId.trim();
        ConfigEditor.EditorTab tab = ConfigEditor.tab(module);
        if (tab == null) {
            module = "difficulty";
            tab = ConfigEditor.tab(module);
        }
        UUID uuid = player.m_20148_();
        String query = ConfigEditor.search(uuid);
        boolean searching = query != null && !query.isBlank();
        String groupPath = path == null ? "" : path;
        String where = categoryLabel(module, tab);

        boolean sectionMode = !searching && ConfigEditor.isSectionPath(groupPath);
        boolean nestedGroup = !searching && !sectionMode && !groupPath.isBlank();
        boolean showIndex = !searching && !sectionMode && !nestedGroup;
        List<ConfigEditor.SectionLink> sections =
                showIndex ? ConfigEditor.sections(module) : List.of();
        if (showIndex && sections.size() == 1) {
            groupPath = sections.get(0).openPath();
            sectionMode = ConfigEditor.isSectionPath(groupPath);
            nestedGroup = !sectionMode && !groupPath.isBlank();
            showIndex = false;
            sections = List.of();
        }

        List<Row> rows = new ArrayList<>();
        if (searching) {
            for (ConfigEditor.SearchHit hit : ConfigEditor.searchAll(query)) {
                if (hit == null || hit.entry() == null) {
                    continue;
                }
                rows.add(new Row(hit.moduleId(), hit.moduleLabel(), hit.entry(), true));
            }
        } else if (sectionMode && tab != null) {
            String section = groupPath.substring(ConfigEditor.SECTION_PREFIX.length());
            for (ConfigEditor.Entry entry : ConfigEditor.fieldsInSection(module, section)) {
                if (entry != null) {
                    rows.add(new Row(module, where, entry, false));
                }
            }
        } else if (nestedGroup && tab != null) {
            for (ConfigEditor.Entry entry : ConfigEditor.visibleFields(module, groupPath, "")) {
                if (entry != null) {
                    rows.add(new Row(module, where, entry, false));
                }
            }
        }

        String crumb = breadcrumb(where, groupPath, searching);
        boolean crumbRow = !searching && groupPath != null && !groupPath.isBlank();
        boolean closedNote = tab != null && !tab.editable() && !searching;
        int contentTop = SEARCH_Y + missingInputRows(player) * ROW_STEP + ROW_STEP;
        if (crumbRow) {
            contentTop += ROW_STEP;
        }
        if (closedNote) {
            contentTop += ROW_STEP;
        }
        int[] heights = showIndex ? sectionHeights(sections) : fieldHeights(uuid, rows);
        List<int[]> slices = paginate(heights, REVIEW_Y - contentTop);
        int current = Math.min(Math.max(0, pageNumber), Math.max(0, slices.size() - 1));
        int from = slices.get(current)[0];
        int to = slices.get(current)[1];
        int dirty = ConfigEditor.dirtyCount(uuid);
        String pageId = editPage(module, current, groupPath);
        ConfigEditor.rememberEditorPage(uuid, pageId);

        UltraScreenSpec spec = frame(crumb, pageId);
        placeCategoryRail(spec, module, searching, SEARCH_Y);
        int y = noteIfTextInputMissing(spec, player, SEARCH_Y);
        y = placeSearchRow(spec, query, y);
        if (crumbRow) {
            String parent = ConfigEditor.isSectionPath(groupPath) ? "" : ConfigEditor.parentPath(groupPath);
            spec.button(
                    "page:" + editPage(module, 0, parent),
                    UltraStyle.dim("v " + crumb),
                    UltraStyle.STYLE_DIM,
                    MAIN_X,
                    y,
                    MAIN_W);
            y += ROW_STEP;
        }
        if (closedNote) {
            spec.button(
                    "do:refresh",
                    UltraStyle.dim("This category is closed - player records are not edited here."),
                    UltraStyle.STYLE_DIM,
                    MAIN_X,
                    y,
                    MAIN_W);
            y += ROW_STEP;
        }

        if (showIndex) {
            if (sections.isEmpty()) {
                spec.setRow(y);
                flowLabel(spec, "No settings in this category.");
            }
            for (int i = from; i < to && i < sections.size(); i++) {
                ConfigEditor.SectionLink section = sections.get(i);
                if (section == null) {
                    continue;
                }
                String count = section.count() == 1 ? "1 setting" : section.count() + " settings";
                spec.button(
                        "page:" + editPage(module, 0, section.openPath()),
                        UltraStyle.LABEL + "> " + section.title() + " (" + count + ")",
                        UltraStyle.STYLE_NORMAL,
                        MAIN_X,
                        y,
                        MAIN_W);
                y += ROW_STEP;
            }
        } else if (from >= to) {
            spec.setRow(y);
            flowLabel(spec, searching ? "No settings match that search." : "No settings in this section.");
        } else {
            for (int i = from; i < to; i++) {
                y = paintField(spec, uuid, rows.get(i), y, true);
            }
        }

        placeReviewRow(spec, current, slices.size(), module, groupPath, dirty);
        spec.footerBackToMain();
        spec.send(player);
    }

    private static void placeCategoryRail(
            UltraScreenSpec spec, String activeModule, boolean searching, int startY) {
        int y = startY;
        for (String[] cat : CATEGORIES) {
            String id = cat[0];
            String label = cat[1];
            boolean active = !searching && id.equalsIgnoreCase(activeModule);
            String style = active ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM;
            String text = active ? UltraStyle.confirm(label) : UltraStyle.dim(label);
            // Chrome: left rail must not set firstButtonY or body text collapses onto controls.
            spec.chromeButton("do:select_module:" + id, text, style, SIDE_X, y, SIDE_W);
            y += ROW_STEP;
        }
    }

    /** Search field, Search, and Clear on one row. Returns the next row Y. */
    private static int placeSearchRow(UltraScreenSpec spec, String query, int y) {
        int btn = UltraStyle.BTN_MIN_W;
        int fieldW = Math.max(40, MAIN_W - btn - CTRL_GAP - btn - CTRL_GAP);
        String inputId = "input:search";
        spec.setRow(y);
        spec.textFieldRow(inputId, query == null ? "" : query, "Search by name", MAIN_X, fieldW);
        int searchX = MAIN_X + fieldW + CTRL_GAP;
        spec.buttonBound(
                "do:search",
                UltraStyle.confirm("Search"),
                UltraStyle.STYLE_CONFIRM,
                searchX,
                y,
                btn,
                inputId);
        spec.button(
                "do:search_clear",
                UltraStyle.LABEL + "Clear",
                UltraStyle.STYLE_NORMAL,
                searchX + btn + CTRL_GAP,
                y,
                btn);
        return y + ROW_STEP;
    }

    /**
     * Review stays on its own row. Prev and Next share that row when a list spans pages.
     *
     * <p>Three {@link UltraStyle#BTN_MIN_W} buttons plus two 8px gaps are wider than the main
     * column, so a middle page used to emit a row 2px over {@link #MAIN_W}; the client's own
     * min-width clamp then pushed Review onto Prev/Next. The gaps - never the buttons, which must
     * keep the 80px minimum - shrink to whatever the column has left, so the row always fits.
     */
    private static void placeReviewRow(
            UltraScreenSpec spec, int page, int pages, String module, String groupPath, int dirty) {
        int btn = UltraStyle.BTN_MIN_W;
        int prevW = page > 0 ? btn : 0;
        int nextW = page + 1 < pages ? btn : 0;
        int gap = CTRL_GAP;
        if (prevW > 0 && nextW > 0) {
            gap = Math.max(2, (MAIN_W - prevW - btn - nextW) / 2);
        }
        int x = MAIN_X;
        if (prevW > 0) {
            spec.button(
                    "page:" + editPage(module, page - 1, groupPath),
                    UltraStyle.dim("Prev"),
                    UltraStyle.STYLE_DIM,
                    x,
                    REVIEW_Y,
                    prevW);
            x += prevW + gap;
        }
        int reviewW = Math.max(btn, MAIN_W - (x - MAIN_X) - (nextW == 0 ? 0 : nextW + gap));
        String review = reviewW >= 160
                ? "Review changes (" + dirty + ")"
                : "Review (" + dirty + ")";
        spec.button(
                "page:review",
                UltraStyle.confirm(review),
                UltraStyle.STYLE_CONFIRM,
                x,
                REVIEW_Y,
                reviewW);
        if (nextW > 0) {
            spec.button(
                    "page:" + editPage(module, page + 1, groupPath),
                    UltraStyle.LABEL + "Next",
                    UltraStyle.STYLE_NORMAL,
                    MAIN_X + MAIN_W - nextW,
                    REVIEW_Y,
                    nextW);
        }
    }

    /**
     * Setting title/description as a main-column flow row (not body {@code line}), so the label
     * sits above its control instead of scrolling in a band that overlaps buttons.
     * Text longer than one row wraps onto more rows. Nothing is dropped.
     */
    private static void flowLabel(UltraScreenSpec spec, String text) {
        flowText(spec, text);
    }

    /** Wrap the full description. Overflow becomes more rows; nothing is dropped. */
    private static void flowText(UltraScreenSpec spec, String text) {
        paintWrappedRows(spec, "do:refresh", text, UltraStyle.STYLE_DIM, LABEL_CTRL_GAP);
    }

    private static void paintWrappedRows(
            UltraScreenSpec spec, String action, String text, String style, int gap) {
        if (spec == null || text == null || text.isBlank()) {
            return;
        }
        String rest = UltraStyle.plain(text).trim();
        while (!rest.isEmpty()) {
            String chunk;
            if (rest.length() <= FLOW_CHARS) {
                chunk = rest;
                rest = "";
            } else {
                int at = flowBreak(rest, FLOW_CHARS);
                chunk = rest.substring(0, at).trim();
                rest = rest.substring(at).trim();
                if (chunk.isEmpty()) {
                    break;
                }
            }
            spec.button(action, UltraStyle.LABEL + chunk, style, MAIN_X, spec.row(), MAIN_W)
                    .rowGap(gap);
        }
    }

    /** Last space in the window, or {@code max} when one token is wider than the row. */
    private static int flowBreak(String text, int max) {
        int limit = Math.min(max, text.length());
        int space = text.lastIndexOf(' ', limit - 1);
        if (space > 0 && space < text.length()) {
            return space;
        }
        return Math.max(1, limit);
    }

    private static int[] sectionHeights(List<ConfigEditor.SectionLink> sections) {
        int n = sections == null ? 0 : sections.size();
        int[] heights = new int[n];
        for (int i = 0; i < n; i++) {
            heights[i] = ROW_STEP;
        }
        return heights;
    }

    private static int[] fieldHeights(UUID uuid, List<Row> rows) {
        int n = rows == null ? 0 : rows.size();
        int[] heights = new int[n];
        for (int i = 0; i < n; i++) {
            heights[i] = paintField(null, uuid, rows.get(i), 0, false);
        }
        return heights;
    }

    /**
     * Pack blocks into pages. A block taller than the budget still gets its own page so a
     * number field is never dropped.
     */
    private static List<int[]> paginate(int[] heights, int budget) {
        List<int[]> pages = new ArrayList<>();
        int n = heights == null ? 0 : heights.length;
        if (n == 0) {
            pages.add(new int[] {0, 0});
            return pages;
        }
        int room = Math.max(ROW_STEP, budget);
        int i = 0;
        while (i < n) {
            int used = 0;
            int j = i;
            while (j < n) {
                if (j > i && used + heights[j] > room) {
                    break;
                }
                used += heights[j];
                j++;
            }
            if (j == i) {
                j = i + 1;
            }
            pages.add(new int[] {i, j});
            i = j;
        }
        return pages;
    }

    /**
     * One setting: human label, now-versus-default, a plain-language line for what it does, the
     * values it accepts, then the control. A rejected entry gets its own friendly line rather than
     * replacing the description, so the player still sees what the setting does.
     * {@code paint} false measures the same rows the painted field will use.
     */
    private static int paintField(UltraScreenSpec spec, UUID uuid, Row row, int y, boolean paint) {
        ConfigEditor.Entry entry = row.entry();
        String module = row.moduleId();
        String path = entry.path();
        boolean dirty = ConfigEditor.dirty(uuid, module, path);
        String name = ConfigEditor.staffLabel(path);
        String raw = ConfigEditor.effectiveRaw(uuid, module, path);
        String value = ConfigEditor.shownValue(uuid, module, path);
        String desc = ConfigEditor.friendlyDescription(module, path, raw == null ? "" : raw);
        String section = ConfigEditor.sectionTitle(module, path);
        String title = row.searchHit()
                ? row.moduleLabel()
                        + " > "
                        + (section.isBlank() || section.equalsIgnoreCase(name)
                                ? name
                                : section + " > " + name)
                : name;
        if (dirty) {
            title = "* " + title;
        }
        String now = value == null ? "" : value;
        String def = ConfigEditor.defaultShown(module, path);
        String indicator = valueIndicator(now, def);
        int indicatorW = 116;
        int nameW = MAIN_W - indicatorW - CTRL_GAP;
        if (nameW < UltraStyle.BTN_MIN_W) {
            nameW = UltraStyle.BTN_MIN_W;
            indicatorW = Math.max(UltraStyle.BTN_MIN_W, MAIN_W - nameW - CTRL_GAP);
        }
        putButton(
                spec,
                paint,
                "do:refresh",
                UltraStyle.dim(title),
                UltraStyle.STYLE_DIM,
                MAIN_X,
                y,
                nameW);
        ConfigEditor.EditorTab moduleTab = ConfigEditor.tab(module);
        boolean editableTab = moduleTab != null && moduleTab.editable();
        if (editableTab && ConfigEditor.isCollectionEntry(module, path)) {
            // One entry of a list or map: "Now x - def y" means little here, so the slot becomes
            // Remove (staged until Save, like every other change) or Keep to take it back.
            boolean removing = ConfigEditor.removing(uuid, module, path);
            putButton(
                    spec,
                    paint,
                    "do:remove:" + module + "|" + encodePath(path),
                    removing ? UltraStyle.confirm("Keep entry") : UltraStyle.danger("Remove"),
                    removing ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DANGER,
                    MAIN_X + nameW + CTRL_GAP,
                    y,
                    indicatorW);
        } else {
            putButton(
                    spec,
                    paint,
                    "do:refresh",
                    dirty ? UltraStyle.confirm(indicator) : UltraStyle.dim(indicator),
                    dirty ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM,
                    MAIN_X + nameW + CTRL_GAP,
                    y,
                    indicatorW);
        }
        y += ROW_STEP;

        if (!editableTab) {
            putButton(
                    spec,
                    paint,
                    "do:refresh",
                    UltraStyle.dim("Closed - " + UltraStyle.plain(now)),
                    UltraStyle.STYLE_DIM,
                    MAIN_X,
                    y,
                    MAIN_W);
            return y + ROW_STEP;
        }
        String invalid = ConfigEditor.invalidMessage(uuid, module, path);
        boolean bad = invalid != null && !invalid.isBlank();
        boolean toggleRow = entry.kind() == ConfigEditor.Kind.BOOLEAN;
        int undoW = dirty ? UltraStyle.BTN_MIN_W : 0;
        int toggleW = toggleRow ? UltraStyle.BTN_MIN_W : 0;
        int extras = (undoW == 0 ? 0 : undoW + CTRL_GAP) + (toggleW == 0 ? 0 : toggleW + CTRL_GAP);
        int descW = Math.max(UltraStyle.BTN_MIN_W, MAIN_W - extras);
        // What the setting does, in plain words. It stays put even when the last entry failed.
        putButton(
                spec,
                paint,
                "do:refresh",
                UltraStyle.dim(oneLine(desc, descW)),
                UltraStyle.STYLE_DIM,
                MAIN_X,
                y,
                descW);
        int x = MAIN_X + descW + CTRL_GAP;
        if (dirty) {
            putButton(
                    spec,
                    paint,
                    "do:undo:" + module + "|" + encodePath(path),
                    UltraStyle.LABEL + "Undo",
                    UltraStyle.STYLE_NORMAL,
                    x,
                    y,
                    undoW);
            x += undoW + CTRL_GAP;
        }
        if (toggleRow) {
            boolean on = "ON".equalsIgnoreCase(value) || "true".equalsIgnoreCase(raw);
            putButton(
                    spec,
                    paint,
                    "do:toggle:" + module + "|" + encodePath(path),
                    on ? UltraStyle.confirm("ON") : UltraStyle.dim("OFF"),
                    on ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_DIM,
                    x,
                    y,
                    toggleW);
        }
        y += ROW_STEP;
        // One guidance row, whichever kind of guidance it is: what the field accepts, or - right
        // after a rejected entry - what went wrong and what to enter instead. The inline hint
        // already names the accepted values, so a field keeps the same height either way and
        // never outgrows the page budget into the review row.
        if (bad) {
            putButton(
                    spec,
                    paint,
                    "do:refresh",
                    UltraStyle.danger(oneLine(ConfigEditor.invalidHint(module, path, invalid), MAIN_W)),
                    UltraStyle.STYLE_DANGER,
                    MAIN_X,
                    y,
                    MAIN_W);
            y += ROW_STEP;
        } else {
            String accepts = ConfigEditor.acceptedValues(module, path);
            if (!accepts.isBlank()) {
                putButton(
                        spec,
                        paint,
                        "do:refresh",
                        UltraStyle.dim("Accepts: " + oneLine(accepts, MAIN_W)),
                        UltraStyle.STYLE_DIM,
                        MAIN_X,
                        y,
                        MAIN_W);
                y += ROW_STEP;
            }
        }
        if (!toggleRow) {
            y = paintControl(spec, paint, module, path, entry.kind(), value, y);
        }
        return y;
    }

    /**
     * Typed controls. Case labels stay so NUMBER / ENUM / LIST keep {@code textFieldRow}
     * and a bound Apply. Side-by-side only when the main column has room (see {@link #fitsRow}).
     */
    private static int paintControl(
            UltraScreenSpec spec,
            boolean paint,
            String module,
            String path,
            ConfigEditor.Kind kind,
            String value,
            int y) {
        String encoded = encodePath(path);
        switch (kind) {
            case NUMBER -> {
                String inputId = "input:" + module + "|" + encoded;
                if (paint && spec != null) {
                    spec.setRow(y);
                    spec.textFieldRow(
                            inputId, value == null ? "" : value, "Type a number...", MAIN_X, MAIN_W);
                }
                y += ROW_STEP;
                // Apply / - / + share one row. Three min-width buttons plus two 8px gaps need
                // 264px and the column is 254px, so the gaps give way - never the buttons. Four
                // stacked rows here is what pushed every number field past the review row.
                int stepW = UltraStyle.BTN_MIN_W;
                int applyW = UltraStyle.BTN_MIN_W;
                int gap = threeUpGap(applyW, stepW, stepW);
                putBound(
                        spec,
                        paint,
                        "do:set:" + module + "|" + encoded,
                        UltraStyle.confirm("Apply"),
                        UltraStyle.STYLE_CONFIRM,
                        MAIN_X,
                        y,
                        applyW,
                        inputId);
                putButton(
                        spec,
                        paint,
                        "do:step:" + module + "|" + encoded + "|-1",
                        UltraStyle.LABEL + "-",
                        UltraStyle.STYLE_NORMAL,
                        MAIN_X + applyW + gap,
                        y,
                        stepW);
                putButton(
                        spec,
                        paint,
                        "do:step:" + module + "|" + encoded + "|1",
                        UltraStyle.LABEL + "+",
                        UltraStyle.STYLE_NORMAL,
                        MAIN_X + applyW + gap + stepW + gap,
                        y,
                        stepW);
                return y + ROW_STEP;
            }
            case ENUM -> {
                String inputId = "input:" + module + "|" + encoded;
                if (paint && spec != null) {
                    spec.setRow(y);
                    spec.textFieldRow(
                            inputId, value == null ? "" : value, "Type an option...", MAIN_X, MAIN_W);
                }
                y += ROW_STEP;
                // Apply / Next / Options share one row like the number steppers: only the gaps
                // give way. Options is the dropdown - a page listing every allowed value.
                int w = UltraStyle.BTN_MIN_W;
                int gap = threeUpGap(w, w, w);
                putBound(
                        spec,
                        paint,
                        "do:set:" + module + "|" + encoded,
                        UltraStyle.confirm("Apply"),
                        UltraStyle.STYLE_CONFIRM,
                        MAIN_X,
                        y,
                        w,
                        inputId);
                putButton(
                        spec,
                        paint,
                        "do:cycle:" + module + "|" + encoded,
                        UltraStyle.confirm("Next"),
                        UltraStyle.STYLE_CONFIRM,
                        MAIN_X + w + gap,
                        y,
                        w);
                putButton(
                        spec,
                        paint,
                        "page:" + optionsPage(module, 0, path),
                        UltraStyle.LABEL + "Options",
                        UltraStyle.STYLE_NORMAL,
                        MAIN_X + 2 * (w + gap),
                        y,
                        w);
                return y + ROW_STEP;
            }
            case LIST, MAP, GROUP -> {
                ConfigEditor.AddMode addMode = ConfigEditor.addMode(module, path);
                if (addMode != ConfigEditor.AddMode.NONE) {
                    String inputId = "input:" + module + "|" + encoded;
                    String placeholder = addMode == ConfigEditor.AddMode.KEY_AND_VALUE
                            ? "Type key=value..."
                            : "Type a new entry...";
                    if (paint && spec != null) {
                        spec.setRow(y);
                        spec.textFieldRow(inputId, "", placeholder, MAIN_X, MAIN_W);
                    }
                    y += ROW_STEP;
                    if (fitsRow(100, 100)) {
                        if (paint && spec != null) {
                            spec.buttonBound(
                                    "do:add:" + module + "|" + encoded,
                                    UltraStyle.confirm("Apply"),
                                    UltraStyle.STYLE_CONFIRM,
                                    MAIN_X,
                                    y,
                                    100,
                                    inputId);
                        }
                        putButton(
                                spec,
                                paint,
                                "page:" + editPage(module, 0, path),
                                UltraStyle.LABEL + "Open " + shortVal(value),
                                UltraStyle.STYLE_NORMAL,
                                MAIN_X + 100 + CTRL_GAP,
                                y,
                                100);
                        return y + ROW_STEP;
                    }
                    putBound(
                            spec,
                            paint,
                            "do:add:" + module + "|" + encoded,
                            UltraStyle.confirm("Apply"),
                            UltraStyle.STYLE_CONFIRM,
                            MAIN_X,
                            y,
                            MAIN_W,
                            inputId);
                    y += ROW_STEP;
                }
                putButton(
                        spec,
                        paint,
                        "page:" + editPage(module, 0, path),
                        UltraStyle.LABEL + "Open " + shortVal(value),
                        UltraStyle.STYLE_NORMAL,
                        MAIN_X,
                        y,
                        MAIN_W);
                return y + ROW_STEP;
            }
            case TEXT -> {
                String inputId = "input:" + module + "|" + encoded;
                if (paint && spec != null) {
                    spec.setRow(y);
                    spec.textFieldRow(
                            inputId, value == null ? "" : value, "Type a value...", MAIN_X, MAIN_W);
                }
                y += ROW_STEP;
                putBound(
                        spec,
                        paint,
                        "do:set:" + module + "|" + encoded,
                        UltraStyle.confirm("Apply"),
                        UltraStyle.STYLE_CONFIRM,
                        MAIN_X,
                        y,
                        MAIN_W,
                        inputId);
                return y + ROW_STEP;
            }
            default -> {
                putButton(
                        spec,
                        paint,
                        "do:refresh",
                        UltraStyle.dim("Can't be edited in the menu yet - ask an admin to change it."),
                        UltraStyle.STYLE_DIM,
                        MAIN_X,
                        y,
                        MAIN_W);
                return y + ROW_STEP;
            }
        }
    }

    private static void putButton(
            UltraScreenSpec spec,
            boolean paint,
            String action,
            String label,
            String style,
            int x,
            int y,
            int w) {
        if (!paint || spec == null) {
            return;
        }
        spec.button(action, label, style, x, y, w);
    }

    private static void putBound(
            UltraScreenSpec spec,
            boolean paint,
            String action,
            String label,
            String style,
            int x,
            int y,
            int w,
            String inputId) {
        if (!paint || spec == null) {
            return;
        }
        spec.buttonBound(action, label, style, x, y, w, inputId);
    }

    /** True when {@code leftW + gap + rightW} fits in the main column. */
    private static boolean fitsRow(int leftW, int rightW) {
        return leftW + CTRL_GAP + rightW <= MAIN_W;
    }

    /**
     * Gap that lets three controls share one row. Only the gaps give way - a button is never
     * narrowed below {@link UltraStyle#BTN_MIN_W} - so three min-width buttons land exactly on
     * the column instead of stacking into extra rows.
     */
    private static int threeUpGap(int leftW, int midW, int rightW) {
        int need = leftW + midW + rightW;
        if (need + 2 * CTRL_GAP <= MAIN_W) {
            return CTRL_GAP;
        }
        return Math.max(2, (MAIN_W - need) / 2);
    }

    private static String optionsPage(String module, int page, String path) {
        return "options:" + module + ":" + page + ":" + (path == null ? "" : path);
    }

    /**
     * Dropdown for an enum setting, as a server-driven page: every allowed value is a button,
     * the staged (or saved) one is gold. Picking stages it exactly like Apply; Back returns to the
     * settings page.
     */
    private static void openOptions(ServerPlayer player, String moduleId, int pageNumber, String path) {
        UUID uuid = player.m_20148_();
        String module = moduleId == null || moduleId.isBlank() ? "difficulty" : moduleId;
        List<String> options = ConfigEditor.enumOptions(module, path);
        String current = ConfigEditor.effectiveRaw(uuid, module, path);
        String name = ConfigEditor.staffLabel(path);
        int contentTop = SEARCH_Y + ROW_STEP;
        int[] heights = new int[options.size()];
        java.util.Arrays.fill(heights, ROW_STEP);
        List<int[]> slices = paginate(heights, REVIEW_Y - contentTop);
        int page = Math.min(Math.max(0, pageNumber), Math.max(0, slices.size() - 1));
        UltraScreenSpec spec = frame(categoryLabel(module, ConfigEditor.tab(module)) + " > " + name,
                optionsPage(module, page, path));
        placeCategoryRail(spec, module, false, SEARCH_Y);
        spec.button(
                "do:refresh",
                UltraStyle.dim(oneLine("Choose a value for " + name + ". Nothing is saved until Review.", MAIN_W)),
                UltraStyle.STYLE_DIM,
                MAIN_X,
                SEARCH_Y,
                MAIN_W);
        int y = contentTop;
        if (options.isEmpty()) {
            spec.button(
                    "do:refresh",
                    UltraStyle.dim("This setting has no fixed list of values. Type one on the settings page."),
                    UltraStyle.STYLE_DIM,
                    MAIN_X,
                    y,
                    MAIN_W);
        }
        int from = slices.get(page)[0];
        int to = Math.min(options.size(), slices.get(page)[1]);
        for (int i = from; i < to; i++) {
            String option = options.get(i);
            boolean chosen = option.equalsIgnoreCase(current == null ? "" : current.trim());
            String label = ConfigEditor.displayName(option);
            spec.button(
                    "do:choose:" + module + "|" + encodePath(path) + "|" + option,
                    chosen ? UltraStyle.confirm(label + " (chosen)") : UltraStyle.LABEL + label,
                    chosen ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL,
                    MAIN_X,
                    y,
                    MAIN_W);
            y += ROW_STEP;
        }
        int pages = slices.size();
        int x = MAIN_X;
        if (page > 0) {
            spec.button("page:" + optionsPage(module, page - 1, path), UltraStyle.dim("Prev"),
                    UltraStyle.STYLE_DIM, x, REVIEW_Y, UltraStyle.BTN_MIN_W);
        }
        if (page + 1 < pages) {
            spec.button("page:" + optionsPage(module, page + 1, path), UltraStyle.LABEL + "Next",
                    UltraStyle.STYLE_NORMAL, MAIN_X + MAIN_W - UltraStyle.BTN_MIN_W, REVIEW_Y,
                    UltraStyle.BTN_MIN_W);
        }
        spec.footerBack("page:" + ConfigEditor.editorPage(uuid), "settings");
        spec.send(player);
    }

    private static void openReview(ServerPlayer player, int pageNumber) {
        UUID uuid = player.m_20148_();
        List<ConfigEditor.PendingChange> pending = ConfigEditor.pendingChanges(uuid);
        int count = pending == null ? 0 : pending.size();
        String editorPage = ConfigEditor.editorPage(uuid);
        String module = moduleFromPage(editorPage);
        // Live-editor awareness: another staff member saved a file this editor is changing, or
        // is changing it right now. Warn above the list instead of letting Save overwrite.
        List<ConfigEditor.EditConflict> conflicts = count == 0 ? List.of() : ConfigEditor.conflicts(uuid);
        List<String> others = count == 0 ? List.of() : ConfigEditor.otherEditors(uuid);
        int warnRows = (conflicts.isEmpty() ? 0 : 2) + (others.isEmpty() ? 0 : 1);
        int contentTop = SEARCH_Y + ROW_STEP + warnRows * ROW_STEP;
        int confirmY = FOOTER_Y - 2 * ROW_STEP;
        int discardY = FOOTER_Y - ROW_STEP;
        int budget = confirmY - contentTop;
        int[] heights = new int[count];
        for (int i = 0; i < count; i++) {
            heights[i] = ROW_STEP * 2;
        }
        List<int[]> slices = paginate(heights, budget);
        int current = Math.min(Math.max(0, pageNumber), Math.max(0, slices.size() - 1));
        int from = slices.get(current)[0];
        int to = slices.get(current)[1];

        UltraScreenSpec spec = frame("Review changes", "review:" + current);
        placeCategoryRail(spec, module, false, SEARCH_Y);
        String summary = count == 0
                ? "No unsaved changes."
                : (count == 1 ? "1 unsaved change." : count + " unsaved changes.");
        int reloadW = 120;
        int summaryW = Math.max(UltraStyle.BTN_MIN_W, MAIN_W - reloadW - CTRL_GAP);
        spec.button(
                "do:refresh",
                UltraStyle.dim(summary),
                UltraStyle.STYLE_DIM,
                MAIN_X,
                SEARCH_Y,
                summaryW);
        spec.button(
                "do:reload_module:" + module,
                UltraStyle.LABEL + "Reload disk",
                UltraStyle.STYLE_NORMAL,
                MAIN_X + summaryW + CTRL_GAP,
                SEARCH_Y,
                reloadW);
        int y = SEARCH_Y + ROW_STEP;
        if (!conflicts.isEmpty()) {
            ConfigEditor.EditConflict first = conflicts.get(0);
            String more = conflicts.size() > 1 ? " (and " + (conflicts.size() - 1) + " more)" : "";
            spec.button(
                    "do:refresh",
                    UltraStyle.danger(oneLine(first.actor() + " saved " + first.modules() + " at " + first.when()
                            + more + " after you started.", MAIN_W)),
                    UltraStyle.STYLE_DANGER,
                    MAIN_X,
                    y,
                    MAIN_W);
            y += ROW_STEP;
            spec.button(
                    "do:reapply",
                    UltraStyle.confirm("Reload and reapply my changes"),
                    UltraStyle.STYLE_CONFIRM,
                    MAIN_X,
                    y,
                    MAIN_W);
            y += ROW_STEP;
        }
        if (!others.isEmpty()) {
            spec.button(
                    "do:refresh",
                    UltraStyle.dim(oneLine("Also editing: " + String.join(", ", others), MAIN_W)),
                    UltraStyle.STYLE_DIM,
                    MAIN_X,
                    y,
                    MAIN_W);
            y += ROW_STEP;
        }
        if (count == 0) {
            // After a save: what was written, what reloaded or failed, and the backup.
            List<ConfigEditor.ReportLine> report = ConfigEditor.lastSaveReport(uuid);
            if (report.isEmpty()) {
                spec.button(
                        "do:refresh",
                        UltraStyle.dim("Nothing is written until you confirm a change."),
                        UltraStyle.STYLE_DIM,
                        MAIN_X,
                        y,
                        MAIN_W);
            }
            for (ConfigEditor.ReportLine line : report) {
                if (y + ROW_STEP > confirmY) {
                    break;
                }
                String text = oneLine(line.text(), MAIN_W);
                spec.button(
                        "do:refresh",
                        line.failed() ? UltraStyle.danger(text) : UltraStyle.dim(text),
                        line.failed() ? UltraStyle.STYLE_DANGER : UltraStyle.STYLE_DIM,
                        MAIN_X,
                        y,
                        MAIN_W);
                y += ROW_STEP;
            }
        } else {
            for (int i = from; i < to; i++) {
                ConfigEditor.PendingChange change = pending.get(i);
                if (change == null) {
                    continue;
                }
                boolean overwritten = change.changedBy() != null && !change.changedBy().isBlank();
                String title = overwritten
                        ? oneLine(change.changedBy() + " changed this since: " + change.name(), MAIN_W)
                        : change.name();
                spec.button(
                        "do:refresh",
                        overwritten ? UltraStyle.danger(title) : UltraStyle.dim(title),
                        overwritten ? UltraStyle.STYLE_DANGER : UltraStyle.STYLE_DIM,
                        MAIN_X,
                        y,
                        MAIN_W);
                y += ROW_STEP;
                spec.button(
                        "do:refresh",
                        UltraStyle.dim("Now " + prettyRaw(change.before()) + " -> " + prettyRaw(change.after())),
                        UltraStyle.STYLE_DIM,
                        MAIN_X,
                        y,
                        MAIN_W);
                y += ROW_STEP;
            }
        }
        placeConfirmRow(spec, current, slices.size(), confirmY);
        spec.button(
                "do:discard_all",
                UltraStyle.danger("Discard all"),
                UltraStyle.STYLE_DANGER,
                MAIN_X,
                discardY,
                MAIN_W);
        String back = editorPage == null || editorPage.isBlank() ? "page:edit:difficulty:0:" : "page:" + editorPage;
        spec.footerBack(back, "Config Editor");
        spec.send(player);
    }

    /** Confirm save stacked above Discard all. Prev / Next share the confirm row when needed. */
    private static void placeConfirmRow(UltraScreenSpec spec, int page, int pages, int y) {
        int btn = UltraStyle.BTN_MIN_W;
        int prevW = page > 0 ? btn : 0;
        int nextW = page + 1 < pages ? btn : 0;
        int x = MAIN_X;
        if (prevW > 0) {
            spec.button(
                    "page:review:" + (page - 1),
                    UltraStyle.dim("Prev"),
                    UltraStyle.STYLE_DIM,
                    x,
                    y,
                    prevW);
            x += prevW + CTRL_GAP;
        }
        int saveW = MAIN_W - (x - MAIN_X) - (nextW == 0 ? 0 : nextW + CTRL_GAP);
        String save = saveW >= 140 ? "Confirm save" : "Save";
        spec.button(
                "do:save_all",
                UltraStyle.confirm(save),
                UltraStyle.STYLE_CONFIRM,
                x,
                y,
                Math.max(btn, saveW));
        if (nextW > 0) {
            spec.button(
                    "page:review:" + (page + 1),
                    UltraStyle.LABEL + "Next",
                    UltraStyle.STYLE_NORMAL,
                    MAIN_X + MAIN_W - nextW,
                    y,
                    nextW);
        }
    }

    private static String prettyRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return "empty";
        }
        if ("true".equalsIgnoreCase(raw)) {
            return "ON";
        }
        if ("false".equalsIgnoreCase(raw)) {
            return "OFF";
        }
        return raw;
    }

    private static String categoryLabel(String module, ConfigEditor.EditorTab tab) {
        for (String[] cat : CATEGORIES) {
            if (cat[0].equalsIgnoreCase(module)) {
                return cat[1];
            }
        }
        return tab == null ? module : tab.label();
    }

    private static String breadcrumb(String category, String groupPath, boolean searching) {
        if (searching) {
            return "Search";
        }
        if (groupPath == null || groupPath.isBlank()) {
            return category;
        }
        if (ConfigEditor.isSectionPath(groupPath)) {
            return category + " > " + groupPath.substring(ConfigEditor.SECTION_PREFIX.length());
        }
        return category + " > " + ConfigEditor.staffLabel(groupPath);
    }

    /**
     * Config do-actions. Returns a status message for chat/toast.
     */
    public static String handleAction(ServerPlayer player, String action, String arg, String page) {
        return handleAction(player, action, arg, page, null);
    }

    public static String handleAction(
            ServerPlayer player,
            String action,
            String arg,
            String page,
            com.google.gson.JsonObject args) {
        if (player == null) {
            return UltraStyle.dangerLine("This only works in-game.");
        }
        if (!StaffAccess.isStaff(player)) {
            return UltraStyle.dangerLine("Staff only.");
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        String a = arg == null ? "" : arg.trim();
        UUID uuid = player.m_20148_();
        ConfigEditor.noteActor(uuid, player.m_7755_().getString());
        return switch (act) {
            case "save_all", "save" -> {
                ConfigEditor.SaveResult result =
                        ConfigEditor.saveAll(uuid, player.m_7755_().getString());
                if (result.error() != null && !result.error().isBlank()) {
                    yield UltraStyle.dangerLine(result.error());
                }
                if (result.count() <= 0) {
                    yield UltraStyle.noticeLine(result.message() == null ? "Nothing to save." : result.message());
                }
                // The full reload report stays on the Review page; the notice sums it up.
                boolean reloadFailed = result.report().stream().anyMatch(ConfigEditor.ReportLine::failed);
                String summary = result.message() + (reloadFailed
                        ? " Some settings did not reload - see the report below."
                        : " Everything reloaded and a backup was taken.");
                yield reloadFailed ? UltraStyle.dangerLine(summary) : UltraStyle.noticeLine(summary);
            }
            case "reapply" -> UltraStyle.noticeLine(ConfigEditor.reloadAndReapply(uuid));
            case "remove" -> {
                String[] parts = splitModulePath(a);
                if (parts == null || !ConfigEditor.isCollectionEntry(parts[0], parts[1])) {
                    yield UltraStyle.dangerLine("That entry can't be removed here. Open its list and try again.");
                }
                boolean wasRemoving = ConfigEditor.removing(uuid, parts[0], parts[1]);
                ConfigEditor.stageRemove(uuid, parts[0], parts[1]);
                yield UltraStyle.noticeLine(wasRemoving
                        ? "Kept the entry."
                        : "Entry marked for removal. It is removed when you confirm the save on Review.");
            }
            case "choose" -> {
                String[] parts = splitModulePathDir(a);
                if (parts == null) {
                    yield UltraStyle.dangerLine("That option is no longer available. Open Options again.");
                }
                String error = ConfigEditor.stageSet(uuid, parts[0], parts[1], parts[2]);
                yield error == null
                        ? UltraStyle.noticeLine("Chose " + ConfigEditor.displayName(parts[2])
                                + ". Review changes to save it.")
                        : UltraStyle.dangerLine(error);
            }
            case "discard_all", "discard" -> {
                int count = ConfigEditor.discardAll(uuid);
                yield count <= 0
                        ? UltraStyle.noticeLine("Nothing to discard.")
                        : UltraStyle.noticeLine("Discarded " + count + " change(s).");
            }
            case "reload_module", "reload" -> {
                String module = !a.isBlank() ? a : moduleFromPage(page);
                String error = ConfigEditor.reloadModule(uuid, module);
                yield error == null
                        ? UltraStyle.noticeLine("Reloaded " + module + " from disk.")
                        : UltraStyle.dangerLine(error);
            }
            case "select_module", "open_module" -> {
                ConfigEditor.rememberSearch(uuid, "");
                yield "";
            }
            case "search" -> {
                String typed = typedValueFromArgs(args, "search");
                if (typed == null) {
                    typed = a;
                }
                ConfigEditor.rememberSearch(uuid, typed == null ? "" : typed);
                yield typed == null || typed.isBlank()
                        ? UltraStyle.noticeLine("Search cleared.")
                        : UltraStyle.noticeLine("Showing matches for \"" + typed + "\".");
            }
            case "search_clear" -> {
                ConfigEditor.rememberSearch(uuid, "");
                yield UltraStyle.noticeLine("Search cleared.");
            }
            case "toggle" -> {
                String[] parts = splitModulePath(a);
                if (parts == null) {
                    yield UltraStyle.dangerLine("Missing field.");
                }
                String raw = ConfigEditor.effectiveRaw(uuid, parts[0], parts[1]);
                boolean on = "true".equalsIgnoreCase(raw) || "ON".equalsIgnoreCase(raw);
                String error = ConfigEditor.stageSet(uuid, parts[0], parts[1], on ? "false" : "true");
                yield error == null ? "" : UltraStyle.dangerLine(error);
            }
            case "step" -> {
                String[] parts = splitModulePathDir(a);
                if (parts == null) {
                    yield UltraStyle.dangerLine("Missing field.");
                }
                int dir = "1".equals(parts[2]) ? 1 : -1;
                String next = ConfigEditor.stepNumber(parts[0], parts[1], dir);
                if (next == null) {
                    yield UltraStyle.dangerLine("Cannot step that field.");
                }
                String error = ConfigEditor.stageSet(uuid, parts[0], parts[1], next);
                yield error == null ? "" : UltraStyle.dangerLine(error);
            }
            case "cycle" -> {
                String[] parts = splitModulePath(a);
                if (parts == null) {
                    yield UltraStyle.dangerLine("Missing field.");
                }
                String next = ConfigEditor.nextEnumValue(parts[0], parts[1]);
                if (next == null) {
                    yield UltraStyle.dangerLine("Cannot cycle that field.");
                }
                String error = ConfigEditor.stageSet(uuid, parts[0], parts[1], next);
                yield error == null ? "" : UltraStyle.dangerLine(error);
            }
            case "set" -> {
                String[] parts = splitModulePath(a);
                if (parts == null) {
                    yield UltraStyle.dangerLine("Missing field.");
                }
                String typed = typedValueFromArgs(args, "set");
                if (typed == null) {
                    yield UltraStyle.dangerLine(
                            "Type a value in the field, then press Apply.");
                }
                String error = ConfigEditor.stageSet(uuid, parts[0], parts[1], typed);
                yield error == null
                        ? UltraStyle.noticeLine("Staged new value.")
                        : UltraStyle.dangerLine(error);
            }
            case "add" -> {
                String[] parts = splitModulePath(a);
                if (parts == null) {
                    yield UltraStyle.dangerLine("Missing field.");
                }
                String typed = typedValueFromArgs(args, "add");
                if (typed == null || typed.isBlank()) {
                    yield UltraStyle.dangerLine(
                            "Type a value in the field, then press Apply.");
                }
                String key = "";
                String raw = typed.trim();
                if (ConfigEditor.addMode(parts[0], parts[1]) == ConfigEditor.AddMode.KEY_AND_VALUE) {
                    int sep = raw.indexOf('=');
                    if (sep < 0) {
                        sep = raw.indexOf(':');
                    }
                    if (sep > 0) {
                        key = raw.substring(0, sep).trim();
                        raw = raw.substring(sep + 1).trim();
                    } else {
                        key = raw;
                        raw = "";
                    }
                }
                String error = ConfigEditor.stageAdd(uuid, parts[0], parts[1], key, raw);
                yield error == null
                        ? UltraStyle.noticeLine("Staged new entry.")
                        : UltraStyle.dangerLine(error);
            }
            case "undo" -> {
                String[] parts = splitModulePath(a);
                if (parts == null) {
                    yield UltraStyle.dangerLine("Missing field.");
                }
                ConfigEditor.undoField(uuid, parts[0], parts[1]);
                yield UltraStyle.noticeLine("Undid field change.");
            }
            case "page", "refresh" -> "";
            default -> UltraStyle.dangerLine("That config action is not available.");
        };
    }

    /** {@code module|path} */
    private static String[] splitModulePath(String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        int bar = arg.indexOf('|');
        if (bar <= 0 || bar >= arg.length() - 1) {
            return null;
        }
        String module = arg.substring(0, bar).trim();
        String path = decodePath(arg.substring(bar + 1).trim());
        if (module.isBlank() || path.isBlank()) {
            return null;
        }
        return new String[] {module, path};
    }

    /** {@code module|path|dir} */
    private static String[] splitModulePathDir(String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        int bar = arg.indexOf('|');
        if (bar <= 0) {
            return null;
        }
        String module = arg.substring(0, bar).trim();
        String rest = arg.substring(bar + 1);
        int bar2 = rest.lastIndexOf('|');
        if (bar2 <= 0 || bar2 >= rest.length() - 1) {
            return null;
        }
        String path = decodePath(rest.substring(0, bar2).trim());
        String dir = rest.substring(bar2 + 1).trim();
        if (module.isBlank() || path.isBlank()) {
            return null;
        }
        return new String[] {module, path, dir};
    }

    private static String encodePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replace("|", "%7C");
    }

    private static String decodePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replace("%7C", "|");
    }

    private static String editPage(String module, int page, String path) {
        return "edit:" + module + ":" + page + ":" + (path == null ? "" : path);
    }

    private static String part(String page, int index) {
        if (page == null) {
            return "";
        }
        String[] parts = page.split(":", -1);
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    private static int number(String raw) {
        try {
            return Integer.parseInt(raw == null ? "0" : raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String shortVal(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String plain = UltraStyle.plain(value);
        return clipWords(plain, 16);
    }

    /** Keep the longest leading words that fit {@code max} glyphs. No ellipsis. */
    private static String clipWords(String plain, int max) {
        if (plain == null || plain.isBlank() || max <= 0) {
            return "";
        }
        String text = plain.trim();
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, max);
        int sp = cut.lastIndexOf(' ');
        if (sp > 0) {
            return cut.substring(0, sp).trim();
        }
        return cut.trim();
    }

    private static String moduleFromPage(String page) {
        if (page == null || page.isBlank()) {
            return "difficulty";
        }
        String p = page.trim();
        if (p.regionMatches(true, 0, "module:", 0, 7)
                || p.regionMatches(true, 0, "list:", 0, 5)
                || p.regionMatches(true, 0, "detail:", 0, 7)
                || p.regionMatches(true, 0, "edit:", 0, 5)) {
            String id = part(p, 1);
            return id.isBlank() ? "difficulty" : id;
        }
        return "difficulty";
    }

    private static String argsString(com.google.gson.JsonObject args, String key) {
        if (args == null || key == null || !args.has(key) || args.get(key).isJsonNull()) {
            return null;
        }
        try {
            return args.get(key).getAsString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * KaiPath 1.3.9 copies the bound EditBox into {@code args.value} when a buttonBound
     * fires. Log every key so a client/key mismatch is obvious in server logs, and fall
     * back to {@code bindInput} / {@code input:*} keys if {@code value} is absent.
     */
    private static String typedValueFromArgs(com.google.gson.JsonObject args, String op) {
        if (args == null) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] config {} args=null (no typed value)",
                    AdaptiveDifficultyMod.MOD_ID,
                    op);
            return null;
        }
        StringBuilder keys = new StringBuilder();
        for (Map.Entry<String, JsonElement> entry : args.entrySet()) {
            if (keys.length() > 0) {
                keys.append(',');
            }
            keys.append(entry.getKey());
        }
        String direct = argsString(args, "value");
        AdaptiveDifficultyMod.LOGGER.debug(
                "[{}] config {} args.keys=[{}] valuePresent={} valueLen={}",
                AdaptiveDifficultyMod.MOD_ID,
                op,
                keys,
                direct != null,
                direct == null ? -1 : direct.length());
        if (direct != null) {
            return direct;
        }
        String bind = argsString(args, "bindInput");
        if (bind != null && !bind.isBlank()) {
            String viaBind = argsString(args, bind);
            if (viaBind == null) {
                String bare = bind.contains(":") ? bind.substring(bind.indexOf(':') + 1) : bind;
                viaBind = argsString(args, bare);
            }
            if (viaBind != null) {
                AdaptiveDifficultyMod.LOGGER.debug(
                        "[{}] config {} recovered typed value from bindInput key={}",
                        AdaptiveDifficultyMod.MOD_ID,
                        op,
                        bind);
                return viaBind;
            }
        }
        for (Map.Entry<String, JsonElement> entry : args.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith("input:") || "text".equals(lower) || "typed".equals(lower)) {
                String alt = argsString(args, key);
                if (alt != null) {
                    AdaptiveDifficultyMod.LOGGER.debug(
                            "[{}] config {} recovered typed value from alt key={}",
                            AdaptiveDifficultyMod.MOD_ID,
                            op,
                            key);
                    return alt;
                }
            }
        }
        return null;
    }

    /** Rows reserved when the client cannot type into a field. */
    private static int missingInputRows(ServerPlayer player) {
        return UltraClientFeatures.hasTextInput(player) ? 0 : 2;
    }

    /** Older KaiPath without field 11 - surface that before a dead Apply button. */
    private static int noteIfTextInputMissing(UltraScreenSpec spec, ServerPlayer player, int y) {
        if (spec == null || missingInputRows(player) == 0) {
            return y;
        }
        spec.button(
                "do:refresh",
                UltraStyle.danger("Typed fields need KaiPath 1.3.9 or newer."),
                UltraStyle.STYLE_DANGER,
                MAIN_X,
                y,
                MAIN_W);
        y += ROW_STEP;
        spec.button(
                "do:refresh",
                UltraStyle.dim("Until then use Toggle, Next, or +/- ."),
                UltraStyle.STYLE_DIM,
                MAIN_X,
                y,
                MAIN_W);
        return y + ROW_STEP;
    }

    /**
     * Panel request stays 1920x1080 so KaiPath 1.3.20 (which ignores {@code fillScreen})
     * clamps the chrome to the window. The {@code fillScreen} design box is
     * {@link #CONFIG_DESIGN_W}x{@link #CONFIG_DESIGN_H} — the grid the widgets are laid out
     * in. KaiPath 1.3.31 computes {@code scale = panel / design}: declaring the 1920x1080
     * panel as the design box made the scale ~1.0 and shrank every 426-grid widget into the
     * top-left corner.
     */
    private static UltraScreenSpec frame(String title, String page) {
        return UltraScreenSpec.of("config")
                .title(title)
                .page(page)
                .panel(CONFIG_PANEL_W, CONFIG_PANEL_H)
                .center(false)
                .margin(0)
                .lockPanel()
                .fillScreen(CONFIG_DESIGN_W, CONFIG_DESIGN_H)
                .buttonHeight(CONFIG_BTN_H)
                .lineGap(2)
                .bodyPad(4);
    }

    /** One line that fits a control of {@code widthPx}, cut on a word boundary. */
    private static String oneLine(String text, int widthPx) {
        String plain = UltraStyle.plain(text == null ? "" : text).trim();
        if (plain.isBlank()) {
            plain = "No description.";
        }
        int labelPx = UltraStyle.labelMaxWidth(Math.max(40, widthPx));
        int maxChars = Math.max(1, labelPx / UltraStyle.CHAR_W_EST);
        if (plain.length() > maxChars) {
            // Trim to the last whole word, then let fitAsciiLabel add the ellipsis, so a
            // description never ends mid-word ("...scaling a mob (percent, e.g...").
            int cut = plain.lastIndexOf(' ', maxChars - 4);
            if (cut > 0) {
                plain = plain.substring(0, cut).trim();
            }
        }
        return UltraStyle.fitAsciiLabel(plain, labelPx);
    }

    /** Current value beside the default. Equal values read as the default. */
    private static String valueIndicator(String now, String def) {
        String n = now == null || now.isBlank() ? "empty" : UltraStyle.plain(now).trim();
        if (def == null || def.isBlank() || "group".equalsIgnoreCase(def)) {
            return clipWords("Now " + n, 16);
        }
        String d = UltraStyle.plain(def).trim();
        if (n.equalsIgnoreCase(d)) {
            return clipWords("Now " + n + " - default", 16);
        }
        return clipWords("Now " + n + " - def " + d, 16);
    }

    /** Chrome geometry for the startup self-test. No player session and no config files. */
    static GuiOpenPacket layoutProbe() {
        UltraScreenSpec spec = frame("Difficulty > Mob Spawning", "edit:difficulty:0:~Mob Spawning");
        placeCategoryRail(spec, "difficulty", false, SEARCH_Y);
        placeSearchRow(spec, "spawn", SEARCH_Y);
        spec.button(
                "page:edit:difficulty:0:",
                UltraStyle.dim("v Difficulty > Mob Spawning"),
                UltraStyle.STYLE_DIM,
                MAIN_X,
                SEARCH_Y + ROW_STEP,
                MAIN_W);
        spec.button(
                "page:review",
                UltraStyle.confirm("Review changes (1)"),
                UltraStyle.STYLE_CONFIRM,
                MAIN_X,
                REVIEW_Y,
                MAIN_W);
        spec.footerBackToMain();
        return spec.toPacket();
    }

    /**
     * Paging-row geometry probe for the startup self-test: {@code placeReviewRow} with
     * {@code page}/{@code pages} picked so the caller can assert Prev / Review / Next never
     * stack and never leave the main column. No player session and no config files.
     */
    static GuiOpenPacket actionRowProbe(int page, int pages) {
        UltraScreenSpec spec = frame("Difficulty > Player Scaling", "edit:difficulty:" + page + ":~Player Scaling");
        placeCategoryRail(spec, "difficulty", false, SEARCH_Y);
        int y = placeSearchRow(spec, "", SEARCH_Y);
        spec.button(
                "page:edit:difficulty:" + page + ":",
                UltraStyle.dim("v Difficulty > Player Scaling"),
                UltraStyle.STYLE_DIM,
                MAIN_X,
                y,
                MAIN_W);
        placeReviewRow(spec, page, pages, "difficulty", "~Player Scaling", 2);
        spec.footerBackToMain();
        return spec.toPacket();
    }

    /** Main column X span, for self-test overlap checks. */
    static int mainColumnX() {
        return MAIN_X;
    }

    /** Main column width, for self-test overlap checks. */
    static int mainColumnW() {
        return MAIN_W;
    }

    /** Review-row top Y, for self-test overlap checks. */
    static int reviewRowY() {
        return REVIEW_Y;
    }

    /** Config-editor button height, for self-test overlap checks. */
    static int configButtonHeight() {
        return CONFIG_BTN_H;
    }

    /** Pinned footer Y, for self-test overlap checks. */
    static int footerRowY() {
        return FOOTER_Y;
    }

    /**
     * Page budget for one inner config screen: content top down to the review row. A field
     * block taller than this paints straight through the Prev / Review / Next row, so every
     * field has to fit inside it - in both the valid and the rejected-entry state.
     */
    static int configFieldBudget() {
        return REVIEW_Y - (SEARCH_Y + ROW_STEP + ROW_STEP);
    }

    /**
     * Height each editable field on a module's inner screens would occupy, keyed by
     * {@code "section > path"}. Measured by the same code that paints, so the numbers are the
     * real ones rather than a hand-count of rows.
     */
    static Map<String, Integer> innerFieldHeights(String module) {
        Map<String, Integer> heights = new LinkedHashMap<>();
        UUID uuid = UUID.randomUUID();
        for (ConfigEditor.SectionLink link : ConfigEditor.sections(module)) {
            String groupPath = link == null ? "" : link.openPath();
            if (!groupPath.startsWith(ConfigEditor.SECTION_PREFIX)) {
                continue;
            }
            String section = groupPath.substring(ConfigEditor.SECTION_PREFIX.length());
            List<Row> rows = new ArrayList<>();
            for (ConfigEditor.Entry entry : ConfigEditor.fieldsInSection(module, section)) {
                rows.add(new Row(module, link.title(), entry, false));
            }
            int[] measured = fieldHeights(uuid, rows);
            for (int i = 0; i < rows.size(); i++) {
                heights.put(section + " > " + rows.get(i).entry().path(), measured[i]);
            }
        }
        return heights;
    }

    private record Row(String moduleId, String moduleLabel, ConfigEditor.Entry entry, boolean searchHit) {}
}
