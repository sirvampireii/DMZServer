package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals;
import com.dbzlegacy.adaptivedifficulty.gui.MechanicsGuiApi;
import com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * Legacy Mechanics hub - system entry points and staff admin.
 *
 * <p>Tiles are collected into a list then packed two-up so adding or gating systems
 * automatically grows or shrinks the panel (no fixed empty body band / choose prompt).
 * Hub tiles use {@link UltraStyle#STYLE_NORMAL} chrome plus per-system <em>label</em> ink
 * via {@link UltraStyle#systemAccent(String)}. Confirm/yellow is reserved for selected
 * actions inside system menus — never on the hub landing. Staff Admin uses accent
 * {@code 4} (dark red) on the label, not {@link UltraStyle#STYLE_DANGER}.
 */
public final class UltraHubScreens {
    private UltraHubScreens() {}

    /** One hub system tile. {@code alwaysOn} skips the placeholder availability check. */
    private record HubTile(String system, String label, String onStyle, boolean alwaysOn) {}

    /** Staff Admin page that opens the prestige shop editor (4.7.31). */
    static final String PAGE_PRESTIGE_SHOP_EDIT = "prestige_shop_edit";

    /** 4.7.62: the menu skin picker (Main menu > Menu skins). */
    public static final String PAGE_SKINS = "skins";
    /** {@code do:menu_skin:<skin id>}: use that skin now (free, no confirm). */
    public static final String ACTION_SKIN = "menu_skin";
    /** {@code do:menu_backgrounds}: themed backgrounds on/off. */
    public static final String ACTION_BACKGROUNDS = "menu_backgrounds";
    /** {@code do:menu_look}: Full / Lite. */
    public static final String ACTION_LOOK = "menu_look";

    /** 4.7.66: the Custom skin's editor (Menu skins &gt; Custom &gt; My colors). */
    public static final String PAGE_CUSTOM = "skin_custom";
    /**
     * {@code do:menu_custom:<op>} on My colors: {@code apply:<token>}, {@code dark}, {@code motif},
     * {@code from:<preset id>}, {@code reset}. Every click carries the five fields ({@code args.bindInputs}).
     */
    public static final String ACTION_CUSTOM = "menu_custom";
    /** 4.7.84 (H6): My colors &gt; From a skin: the five "From" buttons and Reset, off the editor page. */
    public static final String PAGE_CUSTOM_FROM = "skin_custom_from";
    /**
     * {@code do:menu_custom:pick_from} on My colors: keep the typed fields in the draft, then open From a skin (the
     * fields are not on that page, so a plain page link would drop what was typed).
     */
    static final String OP_PICK_FROM = "pick_from";
    /** Players whose next My colors reopen shows From a skin instead (set by {@link #OP_PICK_FROM}). */
    private static final java.util.Set<java.util.UUID> SHOW_FROM = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final String[] TONE_NAMES = {"Panel", "Primary", "Trim", "Text", "Confirm"};
    /** Plain names of the motifs on the Scene button. */
    static final Map<MenuSkins.Motif, String> MOTIF_NAMES = Map.of(
            MenuSkins.Motif.BANNER, "Banner", MenuSkins.Motif.NAMEK, "Namek", MenuSkins.Motif.VEINS, "Gold veins",
            MenuSkins.Motif.WISPS, "Cosmic wisps", MenuSkins.Motif.CLOUDS, "Clouds");
    /** The human error for a field that is not a color (4.7.66; 4.7.67 part colors uses it too). */
    public static final String BAD_HEX = "That doesn't look like a color - use 6 hex digits, like #e23b3b";

    /** What the editor shows between clicks: the fields as typed, the style and scene, and the last result. */
    record Draft(String[] raw, boolean dark, MenuSkins.Motif motif, String message, boolean error) {
        static Draft of(MenuSkins.Custom c, String message) {
            int[] t = c.tones();
            String[] raw = new String[t.length];
            for (int i = 0; i < t.length; i++) {
                raw[i] = MenuSkins.hex(t[i]);
            }
            return new Draft(raw, c.dark(), c.motif(), message, false);
        }
    }

    private static final Map<java.util.UUID, Draft> DRAFTS = new java.util.concurrent.ConcurrentHashMap<>();

    static String toneField(int i) {
        return "input:skin_" + MenuPrefs.TONE_KEYS[i];
    }

    /** 4.7.82: the one wheel on My colors and its size (GUI px). */
    static final String SHARED_WHEEL = "input:skin_wheel";
    static final int WHEEL_SIZE = 80;

    static String toneWheel(int i) {
        return "input:skin_wheel_" + MenuPrefs.TONE_KEYS[i];
    }

    /**
     * 4.7.80 hub tabs: Home (dashboard and systems), Goals (the weekly goals board), More (search, meditation, menus,
     * HUD). The old Search page id (4.7.71-4.7.79) opens More, where search lives now.
     */
    public static final String PAGE_GOALS = "goals";
    public static final String PAGE_SEARCH = "search";
    public static final String PAGE_MORE = "more";
    /** 4.7.68: hub links into Sparring's Spar Board and Dojo tabs (the Spar Board and My dojo tiles). */
    static final String PAGE_BOARD = "board";
    static final String PAGE_MY_DOJO = "mydojo";
    /** Hub link into Sparring's Wars page (kept for old links; wars live in the Dojo tab). */
    static final String PAGE_WARS = "wars";

    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        String p = page == null || page.isBlank() ? "main" : page.toLowerCase(Locale.ROOT);
        if ("admin".equals(p)) {
            openAdmin(player);
            return;
        }
        if (PAGE_SKINS.equals(p)) {
            openSkins(player);
            return;
        }
        if (PAGE_CUSTOM.equals(p)) {
            // Arriving from a link starts from the saved colors; editor clicks keep the draft (openCustomDraft).
            openCustom(player, false);
            return;
        }
        if (PAGE_CUSTOM_FROM.equals(p)) {
            openCustomFrom(player);
            return;
        }
        if (PAGE_GOALS.equals(p)) {
            openGoals(player);
            return;
        }
        if (PAGE_COINS.equals(p)) {
            openCoins(player);
            return;
        }
        // 4.7.80: search lives on More; the old Search page id opens More.
        if (PAGE_SEARCH.equals(p) || PAGE_MORE.equals(p)) {
            openMore(player);
            return;
        }
        if (PAGE_WARS.equals(p)) {
            UltraGui.open(player, "spar", "wars");
            return;
        }
        if (PAGE_BOARD.equals(p)) {
            UltraGui.open(player, "spar", "sparboard");
            return;
        }
        if (PAGE_MY_DOJO.equals(p)) {
            UltraGui.open(player, "spar", "dojo");
            return;
        }
        if (PAGE_PRESTIGE_SHOP_EDIT.equals(p)) {
            if (StaffAccess.isStaff(player)) {
                UltraGui.open(player, "config", "edit:prestige-shop:0:");
            } else {
                openMain(player);
            }
            return;
        }
        openMain(player);
    }

    private static UltraScreenSpec base(String page, String tab) {
        return UltraScreenSpec.of("hub")
                .fitMenu()
                .title("Legacy Mechanics")
                .page(page)
                .activeTab(tab)
                .tabs("home|Home", PAGE_GOALS + "|Goals", PAGE_MORE + "|More");
    }

    /**
     * Hub Home (4.7.64): the dashboard, then the systems grouped by what they are for. Prestige is the spine: the
     * first group is the next prestige (level bar, levels to go) and "Fastest now", the one existing thing that
     * moves the player toward it quickest. Then one snapshot line per system under "Your systems" (4.7.84: one card
     * instead of Train, Compete and Progress). Buttons: Prestige beside "Fastest now", then the system pairs. The LM
     * client draws each header's lines in their own box; KaiPath shows the same lines flat.
     */
    private static void openMain(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Map<String, String> ph = MechanicsGuiApi.placeholders(player);
        if (ph == null) {
            ph = Map.of();
        }
        boolean staff = StaffAccess.isStaff(player);
        UltraScreenSpec spec = base("main", "home");
        spec.line(UltraStyle.header("Your next prestige"));
        for (String l : HubDashboard.prestigeLines(player)) {
            spec.line(l);
        }
        // 4.7.84 (H3): the line and the button that opens what it names.
        String[] fastest = HubDashboard.fastestNowWithGo(player);
        spec.line(UltraStyle.VALUE + fastest[0]);
        // 4.7.84 (X5): two cards, "Your next prestige" and "Your systems" (Train, Compete and Progress were three
        // headers), and no spar streak line (Sparring Home shows it), so Home fits the 300 px budget.
        spec.line(UltraStyle.header("Your systems"));
        spec.line(UltraStyle.VALUE + HubDashboard.difficultyLine(player));
        spec.line(UltraStyle.VALUE + HubDashboard.rivalLine(player));
        spec.line(UltraStyle.VALUE + HubDashboard.dojoLine(player));
        String potential = HubDashboard.potentialLine(player);
        if (potential != null) {
            spec.line(UltraStyle.VALUE + potential);
        }
        spec.buttonsAfterLines();
        // 4.7.78: the search row lives at the top of the More tab now; Home is the dashboard and the systems.

        // Prestige: the payoff, with the "Fastest now" button beside it (4.7.84, H3; was full width).
        // 4.7.85 (X5): three to a row, the system tiles following Prestige and Go, so Home fits the 300 px budget.
        placeTile(spec, ph, new HubTile("prestige", "Prestige", UltraStyle.STYLE_NORMAL, false), true);
        spec.button(fastest[1], fastest[2], UltraStyle.STYLE_NORMAL, tileX(1), spec.row(), TILE_W);
        // Hub tiles: normal (dark) + per-system accent. Nothing is "selected" on the hub.
        List<HubTile> tiles = new ArrayList<>();
        tiles.add(new HubTile("difficulty", "Difficulty", UltraStyle.STYLE_NORMAL, true));
        // 4.7.78: Sparring and Dojo are two systems, one tile each.
        tiles.add(new HubTile("spar", "Sparring", UltraStyle.STYLE_NORMAL, false));
        tiles.add(new HubTile("rival", "Rival", UltraStyle.STYLE_NORMAL, false));
        tiles.add(new HubTile("dojo", "Dojo", UltraStyle.STYLE_NORMAL, false));
        tiles.add(new HubTile("character", "Character Services", UltraStyle.STYLE_NORMAL, true));
        boolean skillCheck = SkillCheckService.canUse(player);
        if (skillCheck) {
            tiles.add(new HubTile("skillcheck", "Skill Check", UltraStyle.STYLE_NORMAL, true));
        } else if (staff) {
            tiles.add(new HubTile("skills", "Skills", UltraStyle.STYLE_NORMAL, true));
        }
        // 4.7.71: no dim Skill Check tile for players who cannot use it: a tile that lands nowhere teaches a dead end.
        placeTiles(spec, ph, tiles, 2);
        // Menu skins and Staff Admin moved to More (4.7.64); Find body parts lives on the Body parts shop.
        spec.send(player);
    }

    // ------------------------------------------------------------------ 4.7.71 search

    /** Search state per player: the last query and the 5 most recent searches (newest first), for the session. */
    private static final Map<java.util.UUID, String> QUERY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<java.util.UUID, java.util.LinkedList<String>> RECENT =
            new java.util.concurrent.ConcurrentHashMap<>();
    public static final int RECENT_MAX = 5;
    static final int RESULTS_MAX = 8;
    static final String SEARCH_INPUT = "input:hub_search";

    /**
     * The search field with Search on its right, on the current row; with a query, Clear search joins the row
     * (4.7.84, H1: one row instead of two).
     */
    private static void placeSearchRow(UltraScreenSpec spec, String query) {
        int btn = UltraStyle.BTN_MIN_W;
        boolean clear = query != null && !query.isBlank();
        int clearW = 92;
        // the field and Search (and Clear search) share one row, across both button columns
        int span = UltraStyle.BTN_W * 2 + UltraStyle.BTN_GAP;
        int fieldW = span - btn - UltraStyle.BTN_GAP - (clear ? clearW + UltraStyle.BTN_GAP : 0);
        int y = spec.row();
        spec.textFieldRow(SEARCH_INPUT, query == null ? "" : query, "Search menus and pages",
                UltraStyle.COL_L, fieldW);
        spec.buttonBound("do:hub_search", UltraStyle.confirm("Search"), UltraStyle.STYLE_CONFIRM,
                UltraStyle.COL_L + fieldW + UltraStyle.BTN_GAP, y, btn, SEARCH_INPUT);
        if (clear) {
            spec.button("do:hub_search_clear", UltraStyle.LABEL + "Clear search", UltraStyle.STYLE_NORMAL,
                    UltraStyle.COL_L + span - clearW, y, clearW);
        }
        spec.rowGap();
    }

    /** Newest first, at most {@link #RECENT_MAX}. */
    public static List<String> recentSearches(ServerPlayer player) {
        java.util.LinkedList<String> list = player == null ? null : RECENT.get(player.m_20148_());
        return list == null ? List.of() : List.copyOf(list);
    }

    static void rememberSearch(ServerPlayer player, String q) {
        java.util.LinkedList<String> list = RECENT.computeIfAbsent(player.m_20148_(), k -> new java.util.LinkedList<>());
        synchronized (list) {
            list.removeIf(x -> x.equalsIgnoreCase(q));
            list.addFirst(q);
            while (list.size() > RECENT_MAX) {
                list.removeLast();
            }
        }
    }

    /** Test rig: run a search as the player would (query, recents, the page). */
    public static void searchFor(ServerPlayer player, String q) {
        String query = q == null ? "" : q.trim();
        QUERY.put(player.m_20148_(), query);
        if (!query.isEmpty()) {
            rememberSearch(player, query);
        }
    }

    /**
     * The search section of More (4.7.80; search was its own tab 4.7.71-4.7.79): type part of a name - a menu, a page,
     * a setting, a system - and the matching pages show as buttons, each with its path ("Sparring > Dojo >
     * Upgrades"). Pages come from the page registry ({@link PageRegistry}) and nothing else; staff also get the
     * matching settings (the config editor's own search). The 5 most recent searches show under the box while it is
     * empty. A client that cannot type gets a line saying so. Lines go in first; {@link #placeSearchButtons} adds the
     * field and the results after {@code buttonsAfterLines}.
     */
    private record SearchState(boolean typing, String query, List<PageRegistry.Hit> hits, int total,
            List<com.dbzlegacy.adaptivedifficulty.config.editor.ConfigEditor.SearchHit> settings) {
        /** A search is showing (results or "nothing matches"): the other sections of More step aside. */
        boolean active() {
            return typing && !query.isBlank();
        }
    }

    /**
     * 4.7.84 (H2): the count line over the results, with the real number of matches before the cut to
     * {@link #RESULTS_MAX}: "3 matches for 'tier'", "10 matches for 'prestige', showing 8. Add a word to narrow it."
     */
    static String resultsLine(String query, int total, int shown, int settings) {
        String q = "'" + query + "'";
        String head = total + (total == 1 ? " match" : " matches") + " for " + q;
        if (total > shown) {
            head += ", showing " + shown + ". Add a word to narrow it.";
        } else {
            head += ".";
        }
        return settings <= 0 ? head : head + " " + settings + (settings == 1 ? " setting" : " settings") + " too.";
    }

    private static SearchState searchLines(UltraScreenSpec spec, ServerPlayer player) {
        boolean typing = com.dbzlegacy.adaptivedifficulty.net.gui.UltraClientFeatures.hasTextInput(player);
        boolean staff = StaffAccess.isStaff(player);
        String query = typing ? QUERY.getOrDefault(player.m_20148_(), "") : "";
        if (!typing) {
            spec.line(UltraStyle.header("Search"));
            spec.line(UltraStyle.LABEL + "Typing a search needs a newer client. Open a system from Home instead.");
            return new SearchState(false, "", List.of(), 0, List.of());
        }
        List<PageRegistry.Hit> all = PageRegistry.searchAll(query, staff);
        List<PageRegistry.Hit> hits = all.size() > RESULTS_MAX ? all.subList(0, RESULTS_MAX) : all;
        List<com.dbzlegacy.adaptivedifficulty.config.editor.ConfigEditor.SearchHit> settings = staff && !query.isBlank()
                ? com.dbzlegacy.adaptivedifficulty.config.editor.ConfigEditor.searchAll(query) : List.of();
        if (query.isBlank()) {
            // Players never get settings (settings search is staff-only), so only staff are told about them.
            spec.line(UltraStyle.LABEL + (staff ? "Type a page, menu or setting, like race or tiers."
                    : "Type what you want, like race, tiers or skin."));
        } else if (hits.isEmpty() && settings.isEmpty()) {
            spec.line(UltraStyle.LABEL + "Nothing matches '" + query + "'. Try a shorter word, like dojo or skin.");
        } else {
            spec.line(UltraStyle.VALUE + resultsLine(query, all.size(), hits.size(), settings.size()));
        }
        return new SearchState(true, query, hits, all.size(), settings);
    }

    /**
     * A result's label (4.7.84): a system Home is just its name ("Dojo"); any other page says where it lives
     * ("Trophy hall - in Dojo > Upgrades"), shortened to its parent and then to the title alone when it does not fit
     * {@code roomPx}.
     */
    static String resultLabel(PageRegistry.Hit h, int roomPx) {
        String title = h.page().title();
        if (PageRegistry.systemHome(h.page().screen(), h.page().pattern())
                || PageRegistry.HUB.equals(h.page().screen()) && PageRegistry.HUB_MAIN.equals(h.page().pattern())) {
            return UltraStyle.VALUE + title;
        }
        String where = h.path().startsWith("Main menu > ") ? h.path().substring("Main menu > ".length()) : h.path();
        if (where.endsWith(" > " + title)) {
            where = where.substring(0, where.length() - (" > " + title).length());
        } else if (where.equals(title)) {
            where = "Main menu";
        }
        String full = title + " - in " + where;
        if (UltraStyle.textWidthPx(full) <= roomPx) {
            return UltraStyle.VALUE + title + UltraStyle.LABEL + " - in " + where;
        }
        String parent = where.contains(" > ") ? where.substring(where.lastIndexOf(" > ") + 3) : where;
        String mid = title + " - in " + parent;
        if (UltraStyle.textWidthPx(mid) <= roomPx) {
            return UltraStyle.VALUE + title + UltraStyle.LABEL + " - in " + parent;
        }
        return UltraStyle.VALUE + title;
    }

    private static void placeSearchButtons(UltraScreenSpec spec, ServerPlayer player, SearchState st) {
        if (!st.typing()) {
            return;
        }
        String query = st.query();
        placeSearchRow(spec, query);
        if (query.isBlank()) {
            // 4.7.85 (X5): three recent searches a row (5 take 2 rows), so More fits the 300 px budget.
            List<String> recent = recentSearches(player);
            int room = UltraStyle.labelMaxWidth(TILE_MENU_W);
            for (int i = 0; i < recent.size(); i++) {
                String r = "Recent: " + recent.get(i);
                String label = UltraStyle.LABEL + (UltraStyle.textWidthPx(r) <= room ? r
                        : UltraStyle.clipPlain(recent.get(i), room));
                spec.button("do:hub_search_recent:" + i, label, UltraStyle.STYLE_NORMAL, tileX(i % 3), spec.row(),
                        TILE_W);
                if (i % 3 == 2) {
                    spec.rowGap();
                }
            }
            if (recent.size() % 3 != 0) {
                spec.rowGap();
            }
            return;
        }
        // 4.7.84 (H1): two results a row, so 8 results take 4 rows and More stays within the height budget.
        int room = UltraStyle.labelMaxWidth(UltraStyle.BTN_W);
        List<PageRegistry.Hit> hits = st.hits();
        for (int i = 0; i < hits.size(); i++) {
            PageRegistry.Hit h = hits.get(i);
            String action = "go:" + h.page().screen() + "|" + h.page().pattern();
            String label = resultLabel(h, room);
            if (i % 2 == 0) {
                spec.buttonLeft(action, label, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, label, UltraStyle.STYLE_NORMAL).rowGap();
            }
        }
        if (hits.size() % 2 == 1) {
            spec.rowGap();
        }
        if (!st.settings().isEmpty()) {
            spec.buttonWide("do:hub_search_settings", UltraStyle.LABEL + "Settings matching '" + query + "' ("
                    + st.settings().size() + ")", UltraStyle.STYLE_NORMAL, null).rowGap();
        }
    }

    /** The search buttons: search, a recent search, clear, the matching settings. False for any other action. */
    public static boolean applySearchAction(ServerPlayer player, String action, String arg, JsonObject args) {
        if (action == null || !action.startsWith("hub_search")) {
            return false;
        }
        switch (action) {
            case "hub_search" -> {
                String typed = args != null && args.has("value") ? args.get("value").getAsString() : "";
                searchFor(player, typed);
            }
            case "hub_search_recent" -> {
                List<String> recent = recentSearches(player);
                int i;
                try {
                    i = Integer.parseInt(arg == null ? "" : arg.trim());
                } catch (NumberFormatException e) {
                    i = -1;
                }
                if (i >= 0 && i < recent.size()) {
                    searchFor(player, recent.get(i));
                }
            }
            case "hub_search_clear" -> QUERY.put(player.m_20148_(), "");
            case "hub_search_settings" -> {
                if (StaffAccess.isStaff(player)) {
                    com.dbzlegacy.adaptivedifficulty.config.editor.ConfigEditor.rememberSearch(player.m_20148_(),
                            QUERY.getOrDefault(player.m_20148_(), ""));
                    UltraGui.open(player, "config", "edit:difficulty:0:");
                    return true;
                }
            }
            default -> {
                return false;
            }
        }
        openMore(player);
        return true;
    }

    /**
     * More (4.7.64; 4.7.80 search): search (field, results or recents), meditation progress, menu skins, the HUD
     * switch, and Staff Admin for staff.
     *
     * <p>4.7.84 (H1): search comes first (its line, then the field as the first row). While a search is showing,
     * its results replace the other sections (Meditation, Your menus, HUD); Clear search brings them back. The page
     * stays within the 300 px height budget either way.
     */
    private static void openMore(ServerPlayer player) {
        boolean staff = StaffAccess.isStaff(player);
        UltraScreenSpec spec = base(PAGE_MORE, PAGE_MORE);
        SearchState search = searchLines(spec, player);
        boolean sections = !search.active();
        boolean hudOn = LmHud.enabled(player);
        if (sections) {
            spec.line(UltraStyle.header("Meditation"));
            String med = HubDashboard.meditationLine(player);
            spec.line(med != null ? med : UltraStyle.LABEL + "Meditation progress is off on this server.");
            spec.line(UltraStyle.LABEL + "Meditate near another player.");
            spec.line(UltraStyle.header("Your menus"));
            // 4.7.72: the native HUD switch; it only changes anything for players with the Legacy Mechanics client.
            spec.labelValue("HUD:", hudOn ? "rival, TP and penalty info on screen" : "boss bars and chat lines");
            // 4.7.86: said to every client again (one spec for both render paths: a client-only line shifts the layout).
            spec.line(UltraStyle.LABEL + "The HUD needs the Legacy Mechanics client.");
        }
        spec.buttonsAfterLines();
        // 4.7.80: search lives here entirely (field, results, recents); there is no Search tab.
        placeSearchButtons(spec, player, search);
        if (sections) {
            // Menu skins, the HUD switch and Staff Admin (staff only) share one row (4.7.85, X5: three columns).
            placeTile(spec, Map.of(), new HubTile("menu_skins", "Menu skins", UltraStyle.STYLE_NORMAL, true), true);
            spec.button("do:" + LmHud.ACTION_TOGGLE, UltraStyle.toggle("HUD", hudOn), UltraStyle.STYLE_NORMAL,
                    tileX(1), spec.row(), TILE_W);
            if (staff) {
                placeTileAt(spec, Map.of(), new HubTile("admin", "Staff Admin", UltraStyle.STYLE_NORMAL, true), 2);
            }
            spec.rowGap();
        }
        spec.send(player);
    }

    /**
     * Goals (4.7.80; the approved 4.7.64 concept, item 8): this week's three goals, each from a different system,
     * with the player's progress and a button to where it is done. Progress only: no rewards in this build.
     */
    private static void openGoals(ServerPlayer player) {
        UltraScreenSpec spec = base(PAGE_GOALS, PAGE_GOALS);
        long now = System.currentTimeMillis();
        List<WeeklyGoals.Goal> goals = WeeklyGoals.goals(WeeklyGoals.week(now));
        spec.line(UltraStyle.header("Weekly goals"));
        spec.labelValue("Resets in", WeeklyGoals.resetsIn(now));
        // 4.7.84 (H5): say plainly that goals pay nothing yet. OWNER: the wording lives in WeeklyGoals.NO_PAYOUT_NOTE.
        spec.line(UltraStyle.LABEL + WeeklyGoals.NO_PAYOUT_NOTE);
        spec.line(UltraStyle.LABEL + "Everyone gets the same three goals. Progress counts from the start of the week.");
        for (WeeklyGoals.Goal g : goals) {
            long have = WeeklyGoals.progress(player.m_20148_(), g.id());
            boolean done = have >= g.target();
            spec.line(UltraStyle.header(g.title()));
            spec.line((done ? UltraStyle.GREEN : UltraStyle.VALUE) + WeeklyGoals.progressText(g, have) + " "
                    + DifficultyGuide.bar(Math.min(have, g.target()), g.target(), 12));
        }
        spec.buttonsAfterLines();
        for (int i = 0; i < goals.size(); i++) {
            WeeklyGoals.Goal g = goals.get(i);
            String label = UltraStyle.plain("Go to " + g.goTitle());
            if (i % 2 == 0) {
                spec.buttonLeft(g.go(), label, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(g.go(), label, UltraStyle.STYLE_NORMAL).rowGap();
            }
        }
        if (goals.size() % 2 == 1) {
            spec.rowGap();
        }
        spec.send(player);
    }

    private static void openAdmin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!StaffAccess.isStaff(player)) {
            openMain(player);
            return;
        }
        JsonArray bookPages = new JsonArray();
        JsonObject page1 = new JsonObject();
        page1.addProperty("title", "Staff Admin");
        JsonArray lines1 = new JsonArray();
        lines1.add("Reload Legacy Mechanics config from disk.");
        lines1.add("Open Progression panel or Config editor.");
        page1.add("lines", lines1);
        bookPages.add(page1);
        JsonObject page2 = new JsonObject();
        page2.addProperty("title", "Commands");
        JsonArray lines2 = new JsonArray();
        lines2.add("Full list: /lm admin help");
        lines2.add("Guitest: /lm admin guitest");
        page2.add("lines", lines2);
        bookPages.add(page2);

        UltraScreenSpec spec = UltraScreenSpec.of("admin")
                .fitMenu()
                .title("Staff Admin")
                .page("admin")
                .activeTab("home")
                .pagedBook(
                        "input:admin_help",
                        UltraStyle.COL_L,
                        UltraStyle.CONTENT_ROW,
                        UltraStyle.BTN_W * 2 + UltraStyle.BTN_GAP,
                        88,
                        bookPages,
                        0)
                .setRow(UltraStyle.CONTENT_ROW + 88 + UltraStyle.BTN_GAP);
        spec.button("do:reload", UltraStyle.confirm("Reload Legacy Mechanics config"), UltraStyle.STYLE_CONFIRM)
                .rowGap();
        spec.buttonLeft("open:progression", "Progression panel", UltraStyle.STYLE_NORMAL)
                .buttonRight("open:config", "Config editor", UltraStyle.STYLE_NORMAL)
                .rowGap();
        // 4.7.31: moved off the Prestige shop hub so that page keeps six buttons.
        spec.button("page:" + PAGE_PRESTIGE_SHOP_EDIT, UltraStyle.LABEL + "Edit prestige shop", UltraStyle.STYLE_NORMAL)
                .rowGap();
        // Sub-page of the hub: one exit back to it (nav:home would reopen this page).
        spec.back();
        spec.send(player);
    }

    /** 4.7.85 (N6): where "Where to get coins" lands from any coin shortfall. */
    static final String PAGE_COINS = "coins";

    /**
     * 4.7.85 (N6): the coin source list, written from {@code AncientCoinEconomy.rollKillLoot} / {@code rollKind}:
     * kills drop coins, the active tier sets which coins, elites and bosses drop more, some titles help.
     */
    private static void openCoins(ServerPlayer player) {
        UltraScreenSpec spec = UltraScreenSpec.of("hub").fitMenu().title("Where to get coins").page(PAGE_COINS)
                .activeTab(PAGE_MORE);
        spec.line(UltraStyle.header("Where to get coins"))
                .line(UltraStyle.VALUE + "Every mob you defeat can drop Ancient Coins.")
                .line(UltraStyle.LABEL + "Your difficulty tier sets which coins drop: Copper with no tier, Iron and Gold"
                        + " from T2-T3, Emerald from T4, Diamond from T5, Netherite from T6.")
                .line(UltraStyle.LABEL + "Elites drop 1 more coin, bosses 2 more. Some titles raise the drop chance.")
                .line(UltraStyle.LABEL + "Coins in your inventory and your wallet both count toward a price.")
                .buttonsAfterLines();
        spec.buttonLeft("go:difficulty|tiers", "Difficulty tiers", UltraStyle.STYLE_NORMAL);
        spec.buttonRight("go:difficulty|titles", "Titles", UltraStyle.STYLE_NORMAL).rowGap();
        spec.back();
        spec.send(player);
    }


    /**
     * Menu skins (4.7.62): the five skins, one row each. The name applies the skin at once (free, no confirm,
     * change back any time); the row's second control is a locked label the LM client draws as a live swatch of
     * that skin (panel, button and tab on its background). Then the Backgrounds and Full/Lite toggles. Every
     * choice is per account and survives relogs and character slot switches.
     */
    private static void openSkins(ServerPlayer player) {
        MenuPrefs.Prefs prefs = MenuPrefs.get(player);
        UltraScreenSpec spec = UltraScreenSpec.of("hub")
                .fitMenu()
                .title("Menu skins")
                .page(PAGE_SKINS)
                .activeTab("home");
        // 4.7.85 (X5): one line (was six), so the page fits the 300 px height budget. The rows name every choice; a
        // player without the Legacy Mechanics client reads what full skins need instead.
        if (com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.hasNativeClient(player)) {
            spec.line(UltraStyle.VALUE + "Tap a name to use it. It's free, and you can switch back.");
        } else {
            spec.line(UltraStyle.LABEL + "Full skins need the Legacy Mechanics client.");
        }
        spec.buttonsAfterLines();
        for (MenuSkins.Skin skin : MenuSkins.ALL) {
            boolean using = skin.id().equals(prefs.skin());
            String action = "do:" + ACTION_SKIN + ":" + skin.id();
            if (using) {
                spec.buttonLeft(action, UltraStyle.confirm(skin.name()), UltraStyle.STYLE_CONFIRM);
                spec.lockedButtonRight("You are using this");
            } else {
                spec.buttonLeft(action, skin.name(), UltraStyle.STYLE_NORMAL);
                spec.lockedButtonRight("Tap the name to use it");
            }
            spec.rowGap();
        }
        // 4.7.66: Custom, after Zeno Omni. The action carries the palette token so the swatch shows the player's
        // own colors; tapping it wears Custom (free) and opens My colors.
        String customAction = "do:" + ACTION_SKIN + ":" + MenuSkins.customToken(prefs.custom());
        boolean edit = prefs.usingCustom();
        if (edit) {
            spec.buttonLeft(customAction, UltraStyle.confirm(MenuSkins.CUSTOM_NAME), UltraStyle.STYLE_CONFIRM);
            spec.lockedButtonRight("You are using this");
            spec.rowGap();
            // 4.7.85 (X5): Edit my colors shares the toggles' row (three columns).
            spec.buttonLeft("page:" + PAGE_CUSTOM, "Edit my colors", UltraStyle.STYLE_NORMAL);
        } else {
            spec.buttonLeft(customAction, MenuSkins.CUSTOM_NAME, UltraStyle.STYLE_NORMAL);
            spec.lockedButtonRight("Tap to make your own");
            spec.rowGap();
        }
        String backgrounds = UltraStyle.toggle("Backgrounds", prefs.backgrounds());
        String look = "Look - " + (prefs.lite() ? "Lite" : "Full");
        if (edit) {
            spec.button("do:" + ACTION_BACKGROUNDS, backgrounds, UltraStyle.STYLE_NORMAL, tileX(1), spec.row(), TILE_W);
            spec.button("do:" + ACTION_LOOK, look, UltraStyle.STYLE_NORMAL, tileX(2), spec.row(), TILE_W);
        } else {
            spec.buttonLeft("do:" + ACTION_BACKGROUNDS, backgrounds, UltraStyle.STYLE_NORMAL);
            spec.buttonRight("do:" + ACTION_LOOK, look, UltraStyle.STYLE_NORMAL);
        }
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /**
     * My colors (4.7.66): the Custom skin's editor. Five hex fields (Panel, Primary, Trim, Text, Confirm) with one
     * shared color wheel (4.7.82) on an LM client that draws one; Style (dark or light) and Scene (the motif); Apply
     * with a live swatch of what is typed. Only Apply saves. A client that cannot send the fields (KaiPath, or an LM
     * client before 4.7.66) gets a plain note instead; it can still wear Custom from the picker.
     *
     * <p>4.7.84 (H6): about 290 px instead of 530. The name/box pairs sit two to a row, the wheel shares its row with
     * Apply, the swatch and "From a skin", and the five "From" buttons and Reset moved to their own page
     * ({@link #openCustomFrom}). Every action id stays the same.
     */
    static void openCustom(ServerPlayer player, boolean keepDraft) {
        if (player == null) {
            return;
        }
        MenuPrefs.Prefs prefs = MenuPrefs.get(player);
        Draft d = keepDraft ? DRAFTS.get(player.m_20148_()) : null;
        if (d == null) {
            d = Draft.of(prefs.custom(), null);
        }
        DRAFTS.put(player.m_20148_(), d);
        boolean editing = canEditColors(player);
        boolean wheels = editing && com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesColorWheel(player);
        UltraScreenSpec spec = UltraScreenSpec.of("hub")
                .fitMenu()
                .title("My colors")
                .page(PAGE_CUSTOM)
                .activeTab("home");
        if (!editing) {
            spec.line(UltraStyle.VALUE + "Editing your colors needs the Legacy Mechanics client, 4.7.66 or newer.")
                    .line(UltraStyle.VALUE + "You can still wear Custom with the colors you saved, and any other skin.")
                    .buttonsAfterLines();
            spec.back();
            spec.send(player);
            return;
        }
        // 4.7.85 (X5): one line - how to edit and that Apply saves (free), or the last result in its place - so the
        // page fits the 300 px height budget.
        boolean message = d.message() != null && !d.message().isBlank();
        if (message) {
            spec.line(d.error() ? UltraStyle.danger(d.message()) : UltraStyle.VALUE + d.message());
        } else {
            spec.line(UltraStyle.VALUE + (wheels ? "Tap a box, pick or type a color, then Apply (free)."
                    : "Type a color like #e23b3b in each box, then Apply (free)."));
        }
        // 4.7.86: the readability note shows after a successful Apply too (the player needs to know their text color
        // was changed); with the result line that is two lines, still within the height budget.
        if (!(message && d.error()) && prefs.usingCustom() && MenuSkins.customTextAdjusted(prefs.custom())) {
            spec.line(UltraStyle.LABEL + "Text color adjusted so it stays readable (now "
                    + MenuSkins.hex(MenuSkins.customText(prefs.custom())) + ").");
        }
        spec.buttonsAfterLines();
        String[] fields = new String[MenuPrefs.TONE_KEYS.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = toneField(i);
        }
        String apply = "do:" + ACTION_CUSTOM + ":apply:" + MenuSkins.customToken(draftCustom(d, prefs.custom()));
        spec.bindInputsToButtons(fields).enterClicks(apply);
        // Two name | box pairs a row (the 350 grid: name 16+70, box 92+70, name 168+70, box 244+80). The fifth
        // pair shares its row with Style and Scene.
        int[] nameX = {16, 168};
        int[] boxX = {92, 244};
        int[] boxW = {70, 80};
        for (int i = 0; i < fields.length; i++) {
            int col = i % 2;
            int row = spec.row();
            spec.button(UltraGuiActions.NO_ACTION, UltraStyle.dim(TONE_NAMES[i] + " color"), UltraStyle.STYLE_DIM,
                    nameX[col], row, 70);
            spec.textField(fields[i], d.raw()[i], "#rrggbb", boxX[col], row, boxW[col], UltraStyle.BTN_H, 9);
            spec.colorPreview(fields[i]);
            if (col == 1) {
                spec.rowGap();
            }
        }
        int styleRow = spec.row();
        spec.button("do:" + ACTION_CUSTOM + ":dark", "Style - " + (d.dark() ? "Dark" : "Light"),
                UltraStyle.STYLE_NORMAL, 168, styleRow, 70);
        spec.button("do:" + ACTION_CUSTOM + ":motif", "Scene - " + MOTIF_SHORT.get(d.motif()),
                UltraStyle.STYLE_NORMAL, 244, styleRow, 80);
        spec.rowGap();
        // The wheel (for the box tapped last) with Apply, the live swatch and From a skin beside it.
        int row = spec.row();
        if (wheels) {
            spec.sharedColorWheel(SHARED_WHEEL, fields, UltraStyle.COL_L, row, WHEEL_SIZE);
        }
        int x0 = wheels ? 100 : UltraStyle.COL_L;
        // Bound to the first field the classic way too (args.value); bindInputs carries all five.
        spec.buttonBound(apply, UltraStyle.confirm("Apply my colors"), UltraStyle.STYLE_CONFIRM, x0, row, 74, fields[0]);
        // The LM client draws this locked label as a live swatch of the fields as typed.
        spec.button(UltraGuiActions.NO_ACTION, UltraStyle.dim("Preview"), UltraStyle.STYLE_DIM, x0 + 79, row, 70);
        spec.button("do:" + ACTION_CUSTOM + ":" + OP_PICK_FROM, "From a skin", UltraStyle.STYLE_NORMAL, x0 + 154, row,
                70);
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /** Scene names short enough for the Scene button ("Scene - Wisps"); {@link #MOTIF_NAMES} stays the long name. */
    static final Map<MenuSkins.Motif, String> MOTIF_SHORT = Map.of(
            MenuSkins.Motif.BANNER, "Banner", MenuSkins.Motif.NAMEK, "Namek", MenuSkins.Motif.VEINS, "Veins",
            MenuSkins.Motif.WISPS, "Wisps", MenuSkins.Motif.CLOUDS, "Clouds");

    /**
     * My colors &gt; From a skin (4.7.84, H6): copy one skin's five colors into the editor's fields, or the default
     * colors. Each fills the fields and goes back to My colors; nothing is saved until Apply there.
     */
    static void openCustomFrom(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UltraScreenSpec spec = UltraScreenSpec.of("hub")
                .fitMenu()
                .title("From a skin")
                .page(PAGE_CUSTOM_FROM)
                .activeTab("home");
        spec.line(UltraStyle.VALUE + "Pick a skin to copy its colors into your boxes.")
                .line(UltraStyle.LABEL + "Nothing is saved until you tap Apply on My colors.")
                .buttonsAfterLines();
        String[] from = {"From Saiyan Royal", "From Namek", "From Obsidian", "From Beerus", "From Zeno Omni"};
        for (int i = 0; i < MenuSkins.ALL.size() && i < from.length; i++) {
            String action = "do:" + ACTION_CUSTOM + ":from:" + MenuSkins.ALL.get(i).id();
            if (i % 2 == 0) {
                spec.buttonLeft(action, from[i], UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, from[i], UltraStyle.STYLE_NORMAL);
                spec.rowGap();
            }
        }
        spec.buttonRight("do:" + ACTION_CUSTOM + ":reset", "Reset to default", UltraStyle.STYLE_NORMAL);
        spec.rowGap();
        spec.back();
        spec.send(player);
    }

    /** Reopen My colors keeping what the player typed (after an editor click). */
    public static void openCustomDraft(ServerPlayer player) {
        if (player != null && SHOW_FROM.remove(player.m_20148_())) {
            openCustomFrom(player);
            return;
        }
        openCustom(player, true);
    }

    /** Forget a player's unsaved editor fields (logout). */
    public static void forgetDraft(ServerPlayer player) {
        if (player != null) {
            DRAFTS.remove(player.m_20148_());
        }
    }

    /** The editor needs a client that types and sends all five fields with a click (the 4.7.66 LM client). */
    public static boolean canEditColors(ServerPlayer player) {
        return com.dbzlegacy.adaptivedifficulty.net.gui.UltraClientFeatures.hasTextInput(player)
                && com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.takesCustomSkin(player);
    }

    /** The draft as a palette for the preview swatch: fields that are not colors yet show the saved tone. */
    static MenuSkins.Custom draftCustom(Draft d, MenuSkins.Custom saved) {
        int[] t = saved.tones();
        for (int i = 0; i < t.length; i++) {
            int v = MenuSkins.parseHex(d.raw()[i]);
            if (v >= 0) {
                t[i] = v;
            }
        }
        return new MenuSkins.Custom(d.dark(), d.motif(), 0, 0, 0, 0, 0).withTones(t);
    }

    /**
     * Apply a My colors action ({@code do:menu_custom:<op>}). Returns true when it was one; the caller reopens the
     * editor with {@link #openCustomDraft}. The five fields come in {@code args} under their own ids (the LM client's
     * {@code bindInputs}); a field that is absent keeps the draft. Only {@code apply} saves: a field that is not
     * 6 hex digits stops the whole apply with {@link #BAD_HEX} and leaves every field as typed; an empty field keeps
     * the current color.
     */
    public static boolean applyCustomAction(ServerPlayer player, String action, String arg, JsonObject args) {
        if (player == null || !ACTION_CUSTOM.equals(action)) {
            return false;
        }
        MenuPrefs.Prefs prefs = MenuPrefs.get(player);
        Draft d = DRAFTS.get(player.m_20148_());
        if (d == null) {
            d = Draft.of(prefs.custom(), null);
        }
        if (!canEditColors(player)) {
            DRAFTS.put(player.m_20148_(), d);
            return true;
        }
        String[] raw = d.raw().clone();
        for (int i = 0; i < raw.length; i++) {
            String key = "@hub:" + toneField(i);
            if (args != null && args.has(key) && args.get(key).isJsonPrimitive()) {
                String v = args.get(key).getAsString().trim();
                raw[i] = v.length() > 16 ? v.substring(0, 16) : v;
            }
        }
        String a = arg == null ? "" : arg.trim();
        int colon = a.indexOf(':');
        String op = (colon < 0 ? a : a.substring(0, colon)).toLowerCase(Locale.ROOT);
        String rest = colon < 0 ? "" : a.substring(colon + 1);
        Draft next;
        switch (op) {
            case "dark" -> next = new Draft(raw, !d.dark(), d.motif(), null, false);
            case "motif" -> {
                MenuSkins.Motif[] all = MenuSkins.Motif.values();
                next = new Draft(raw, d.dark(), all[(d.motif().ordinal() + 1) % all.length], null, false);
            }
            case "from" -> {
                MenuSkins.Skin preset = null;
                for (MenuSkins.Skin s : MenuSkins.ALL) {
                    if (s.id().equalsIgnoreCase(rest)) {
                        preset = s;
                    }
                }
                next = preset == null ? new Draft(raw, d.dark(), d.motif(), null, false)
                        : Draft.of(MenuSkins.Custom.from(preset),
                                "Filled in " + preset.name() + "'s colors. Tap Apply to use them.");
            }
            case "reset" -> next = Draft.of(MenuSkins.Custom.DEFAULT,
                    "Filled in the default colors. Tap Apply to use them.");
            case OP_PICK_FROM -> {
                // 4.7.84 (H6): keep what was typed, then show From a skin.
                next = new Draft(raw, d.dark(), d.motif(), null, false);
                SHOW_FROM.add(player.m_20148_());
            }
            case "apply" -> next = applyDraft(player, prefs, new Draft(raw, d.dark(), d.motif(), null, false));
            default -> next = new Draft(raw, d.dark(), d.motif(), null, false);
        }
        DRAFTS.put(player.m_20148_(), next);
        return true;
    }

    /** Validate every field, then save and push the look; a bad field saves nothing. */
    private static Draft applyDraft(ServerPlayer player, MenuPrefs.Prefs prefs, Draft d) {
        int[] tones = prefs.custom().tones();
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < tones.length; i++) {
            String v = d.raw()[i] == null ? "" : d.raw()[i].trim();
            if (v.isEmpty()) {
                continue;
            }
            int rgb = MenuSkins.parseHex(v);
            if (rgb < 0) {
                bad.add(TONE_NAMES[i]);
            } else {
                tones[i] = rgb;
            }
        }
        if (!bad.isEmpty()) {
            return new Draft(d.raw(), d.dark(), d.motif(), String.join(", ", bad) + ": " + BAD_HEX, true);
        }
        MenuSkins.Custom c = new MenuSkins.Custom(d.dark(), d.motif(), 0, 0, 0, 0, 0).withTones(tones);
        MenuPrefs.setCustom(player, c);
        com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.sendPrefs(player);
        return Draft.of(c, "Saved. Every menu now uses your colors.");
    }

    /** Page to reopen after a Menu skins action: My colors when the player just picked Custom. */
    public static String pageAfterSkinAction(String action, String arg) {
        return ACTION_SKIN.equals(action) && MenuSkins.isCustom(arg) ? PAGE_CUSTOM : PAGE_SKINS;
    }

    /**
     * Apply a Menu skins action. Returns true when {@code action} was one; the caller reopens the picker, which
     * then wears the new choice (the LM client gets the look first, so the page re-skins as it opens).
     */
    public static boolean applySkinAction(ServerPlayer player, String action, String arg) {
        if (player == null || action == null) {
            return false;
        }
        switch (action) {
            case ACTION_SKIN -> MenuPrefs.setSkin(player, arg);
            case ACTION_BACKGROUNDS -> MenuPrefs.toggleBackgrounds(player);
            case ACTION_LOOK -> MenuPrefs.toggleLite(player);
            default -> {
                return false;
            }
        }
        com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork.sendPrefs(player);
        return true;
    }

    /**
     * Pack hub tiles three to a row (4.7.85, X5; were two-up), the first in column {@code startCol} of the current
     * row. Panel height follows {@link UltraScreenSpec} content packing, so more systems just add rows.
     */
    private static void placeTiles(UltraScreenSpec spec, Map<String, String> ph, List<HubTile> tiles, int startCol) {
        if (spec == null || tiles == null || tiles.isEmpty()) {
            return;
        }
        int col = startCol;
        for (HubTile tile : tiles) {
            if (tile == null || tile.system == null || tile.system.isBlank()) {
                continue;
            }
            placeTileAt(spec, ph, tile, col);
            col++;
            if (col == 3) {
                spec.rowGap();
                col = 0;
            }
        }
        if (col != 0) {
            spec.rowGap();
        }
    }

    /**
     * Three-column cell on the legacy grid (4.7.85): 98px at 16 / 122 / 228, which the menu reflow scales to three
     * clean 129px cells on the 426px menu.
     */
    static final int TILE_W = 98;
    /** Menu width of {@link #TILE_W}: the text room of a cell is {@code labelMaxWidth(TILE_MENU_W)}. */
    static final int TILE_MENU_W = 129;

    static int tileX(int col) {
        return UltraStyle.COL_L + col * (TILE_W + UltraStyle.BTN_PAD);
    }

    /** One tile in column {@code col} (0-2) of the current row. */
    private static void placeTileAt(UltraScreenSpec spec, Map<String, String> ph, HubTile tile, int col) {
        String[] t = tileFace(ph, tile);
        spec.button(actionFor(tile.system()), t[0], t[1], tileX(col), spec.row(), TILE_W, null, t[2]);
    }

    /**
     * {@code left} true: the first cell of a three-column row (4.7.85; was the left half); false: the right half;
     * null: full width (Prestige, 4.7.64).
     */
    private static void placeTile(
            UltraScreenSpec spec, Map<String, String> ph, HubTile tile, Boolean left) {
        if (Boolean.TRUE.equals(left)) {
            placeTileAt(spec, ph, tile, 0);
            return;
        }
        String[] t = tileFace(ph, tile);
        if (left == null) {
            spec.buttonWide(actionFor(tile.system()), t[0], t[1], t[2]);
        } else {
            spec.buttonRight(actionFor(tile.system()), t[0], t[1], t[2]);
        }
    }

    /** A tile's {label, style, accent}: lit in its system's ink when the system is on, dim when it is off. */
    private static String[] tileFace(Map<String, String> ph, HubTile tile) {
        String system = tile.system();
        // Spar Board, My dojo and Dojo wars live in Sparring: same availability and accent.
        // 4.7.78: the Dojo tile is available whenever Sparring is (it is the same feature switch).
        String owner = system.startsWith("spar_") || "dojo".equals(system) ? "spar" : system;
        boolean on = tile.alwaysOn()
                || "difficulty".equals(system)
                || (ph != null && "true".equalsIgnoreCase(ph.get(owner)));
        // Per-system identity on the label text (chrome stays default). fitAsciiLabel strips
        // section-sign; UltraScreenSpec re-applies via the accent letter + UltraStyle.color.
        String ink = UltraStyle.systemAccent(owner);
        String base = UltraStyle.plain(tile.label());
        String styled = on ? (ink.isEmpty() ? base : ink + base) : UltraStyle.dim(base);
        String style = on
                ? (tile.onStyle() == null || tile.onStyle().isBlank()
                        ? UltraStyle.STYLE_NORMAL
                        : tile.onStyle())
                : UltraStyle.STYLE_DIM;
        // hubTileAccentCode == systemAccentCode; letter re-applied after fitAsciiLabel.
        String accent = on ? UltraStyle.hubTileAccentCode(owner) : "";
        return new String[] {styled, style, accent};
    }

    private static String actionFor(String system) {
        if (system == null || system.isBlank()) {
            return "open:hub";
        }
        return switch (system) {
            case "spar" -> "open:spar";
            case "dojo" -> "open:dojo";
            case "skillcheck" -> "open:skillcheck";
            case "skills" -> "open:skills";
            case "skillcheck_denied" -> "do:skillcheck_denied";
            case "admin" -> "open:admin";
            case "character" -> "open:character";
            case "menu_skins" -> "page:" + PAGE_SKINS;
            case "spar_wars" -> "page:" + PAGE_WARS;
            case "spar_board" -> "page:" + PAGE_BOARD;
            case "spar_dojo" -> "page:" + PAGE_MY_DOJO;
            default -> "open:" + system;
        };
    }
}
