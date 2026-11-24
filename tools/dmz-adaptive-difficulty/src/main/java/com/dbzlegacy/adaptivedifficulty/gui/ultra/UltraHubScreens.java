package com.dbzlegacy.adaptivedifficulty.gui.ultra;

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

    static String toneWheel(int i) {
        return "input:skin_wheel_" + MenuPrefs.TONE_KEYS[i];
    }

    /** 4.7.64 hub tabs: Home (dashboard and systems), Goals (weekly goals, placeholder until 4.7.65), More. */
    public static final String PAGE_GOALS = "goals";
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
        if (PAGE_GOALS.equals(p)) {
            openGoals(player);
            return;
        }
        if (PAGE_MORE.equals(p)) {
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
     * moves the player toward it quickest. Then one snapshot line per system under Train, Compete and Progress.
     * Buttons follow the same order: Prestige full width, then Train, Compete and Progress pairs. The LM client
     * draws each header's lines in their own box; KaiPath shows the same lines flat.
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
        spec.line(UltraStyle.VALUE + HubDashboard.fastestNow(player));
        spec.line(UltraStyle.header("Train"));
        spec.line(UltraStyle.VALUE + HubDashboard.difficultyLine(player));
        String streak = HubDashboard.streakLine(player);
        if (streak != null) {
            spec.line(UltraStyle.VALUE + streak);
        }
        spec.line(UltraStyle.header("Compete"));
        spec.line(UltraStyle.VALUE + HubDashboard.rivalLine(player));
        spec.line(UltraStyle.VALUE + HubDashboard.dojoLine(player));
        String potential = HubDashboard.potentialLine(player);
        if (potential != null) {
            spec.line(UltraStyle.header("Progress"));
            spec.line(UltraStyle.VALUE + potential);
        }
        spec.buttonsAfterLines();

        // Prestige: the payoff, full width.
        placeTile(spec, ph, new HubTile("prestige", "Prestige", UltraStyle.STYLE_NORMAL, false), null);
        spec.rowGap();
        // Hub tiles: normal (dark) + per-system accent. Nothing is "selected" on the hub.
        List<HubTile> tiles = new ArrayList<>();
        tiles.add(new HubTile("difficulty", "Difficulty", UltraStyle.STYLE_NORMAL, true));
        // 4.7.68: one tap to the Spar Board and one to My dojo (wars live in the Dojo tab).
        tiles.add(new HubTile("spar_board", "Spar Board", UltraStyle.STYLE_NORMAL, false));
        tiles.add(new HubTile("rival", "Rival", UltraStyle.STYLE_NORMAL, false));
        tiles.add(new HubTile("spar_dojo", "My dojo", UltraStyle.STYLE_NORMAL, false));
        tiles.add(new HubTile("character", "Character Services", UltraStyle.STYLE_NORMAL, true));
        boolean skillCheck = SkillCheckService.canUse(player);
        if (skillCheck) {
            tiles.add(new HubTile("skillcheck", "Skill Check", UltraStyle.STYLE_NORMAL, true));
        } else if (staff) {
            tiles.add(new HubTile("skills", "Skills", UltraStyle.STYLE_NORMAL, true));
        } else {
            // Dim locked tile - still occupies a slot so layout stays predictable.
            tiles.add(new HubTile("skillcheck_denied", "Skill Check", UltraStyle.STYLE_DIM, true));
        }
        placeTiles(spec, ph, tiles);
        // Menu skins and Staff Admin moved to More (4.7.64); Find body parts lives on the Body parts shop.
        spec.send(player);
    }

    /** Goals (4.7.64): weekly goals arrive with 4.7.65; the tab is here so the hub's shape does not move. */
    private static void openGoals(ServerPlayer player) {
        UltraScreenSpec spec = base(PAGE_GOALS, PAGE_GOALS);
        spec.line(UltraStyle.header("Weekly goals"))
                .line(UltraStyle.VALUE + "Weekly goals arrive in the next update.")
                .line(UltraStyle.LABEL + "Each week: three goals across your systems.")
                .buttonsAfterLines();
        spec.lockedButton("No goals yet").rowGap();
        spec.send(player);
    }

    /** More (4.7.64): meditation progress, menu skins, and Staff Admin for staff. */
    private static void openMore(ServerPlayer player) {
        boolean staff = StaffAccess.isStaff(player);
        UltraScreenSpec spec = base(PAGE_MORE, PAGE_MORE);
        spec.line(UltraStyle.header("Meditation"));
        String med = HubDashboard.meditationLine(player);
        spec.line(med != null ? med : UltraStyle.LABEL + "Meditation progress is off on this server.");
        spec.line(UltraStyle.LABEL + "Meditate near another player.");
        spec.line(UltraStyle.header("Your menus"));
        spec.line(UltraStyle.LABEL + "Pick a skin, backgrounds, and Full or Lite.");
        spec.buttonsAfterLines();
        List<HubTile> tiles = new ArrayList<>();
        tiles.add(new HubTile("menu_skins", "Menu skins", UltraStyle.STYLE_NORMAL, true));
        if (staff) {
            tiles.add(new HubTile("admin", "Staff Admin", UltraStyle.STYLE_NORMAL, true));
        }
        placeTiles(spec, Map.of(), tiles);
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
        spec.footerBackToMain();
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
        spec.line(UltraStyle.VALUE + "Pick how your menus look. Tap a name to use it.")
                .line(UltraStyle.VALUE + "Switching is free and you can switch back.")
                .labelValue("Backgrounds:", "a themed scene behind menus")
                .labelValue("Lite:", "plainer, faster menus for slow PCs");
        // Same text on both paths (one spec): true for everyone, useful for players without the client.
        spec.line(UltraStyle.LABEL + "Full skins need the Legacy Mechanics client.");
        spec.line(UltraStyle.LABEL + "Custom: pick your own colors.");
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
        if (prefs.usingCustom()) {
            spec.buttonLeft(customAction, UltraStyle.confirm(MenuSkins.CUSTOM_NAME), UltraStyle.STYLE_CONFIRM);
            spec.lockedButtonRight("You are using this");
            spec.rowGap();
            spec.buttonLeft("page:" + PAGE_CUSTOM, "Edit my colors", UltraStyle.STYLE_NORMAL);
            spec.rowGap();
        } else {
            spec.buttonLeft(customAction, MenuSkins.CUSTOM_NAME, UltraStyle.STYLE_NORMAL);
            spec.lockedButtonRight("Tap to make your own");
            spec.rowGap();
        }
        spec.buttonLeft("do:" + ACTION_BACKGROUNDS, "Backgrounds - " + (prefs.backgrounds() ? "ON" : "OFF"),
                UltraStyle.STYLE_NORMAL);
        spec.buttonRight("do:" + ACTION_LOOK, "Look - " + (prefs.lite() ? "Lite" : "Full"), UltraStyle.STYLE_NORMAL);
        spec.rowGap();
        spec.footerBackToMain();
        spec.send(player);
    }

    /**
     * My colors (4.7.66): the Custom skin's editor. Five hex fields (Panel, Primary, Trim, Text, Confirm), each with a
     * color wheel beside it on an LM client that draws one; Style (dark or light) and Scene (the motif); Apply with a
     * live swatch of what is typed; "From" buttons that copy a preset's colors into the fields; Reset to the Saiyan
     * Royal defaults. Only Apply saves. A client that cannot send the fields (KaiPath, or an LM client before
     * 4.7.66) gets a plain note instead; it can still wear Custom from the picker.
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
            spec.footerBack("page:" + PAGE_SKINS, "Menu skins");
            spec.send(player);
            return;
        }
        spec.line(UltraStyle.VALUE + (wheels ? "Type a color like #e23b3b, or tap a wheel." : "Type a color like #e23b3b."))
                .line(UltraStyle.VALUE + "Apply saves them and every menu uses them.")
                .line(UltraStyle.LABEL + "From copies a skin's colors into the fields. Reset fills in the defaults.")
                .line(UltraStyle.LABEL + "Nothing changes until you tap Apply. It is free.");
        if (d.message() != null && !d.message().isBlank()) {
            spec.line(d.error() ? UltraStyle.danger(d.message()) : UltraStyle.VALUE + d.message());
        }
        if (prefs.usingCustom() && MenuSkins.customTextAdjusted(prefs.custom())) {
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
        for (int i = 0; i < fields.length; i++) {
            int row = spec.row();
            spec.button(UltraGuiActions.NO_ACTION, UltraStyle.dim(TONE_NAMES[i] + " color"), UltraStyle.STYLE_DIM,
                    UltraStyle.COL_L, row, 96);
            spec.textField(fields[i], d.raw()[i], "#rrggbb", UltraStyle.COL_L + 102, row, 96, UltraStyle.BTN_H, 9);
            spec.colorPreview(fields[i]);
            if (wheels) {
                spec.colorWheel(toneWheel(i), fields[i], UltraStyle.COL_L + 204, row, 44);
            }
            spec.rowGap();
        }
        spec.buttonLeft("do:" + ACTION_CUSTOM + ":dark", "Style - " + (d.dark() ? "Dark" : "Light"),
                UltraStyle.STYLE_NORMAL);
        spec.buttonRight("do:" + ACTION_CUSTOM + ":motif", "Scene - " + MOTIF_NAMES.get(d.motif()),
                UltraStyle.STYLE_NORMAL);
        spec.rowGap();
        // Bound to the first field the classic way too (args.value); bindInputs carries all five.
        spec.buttonBound(apply, UltraStyle.confirm("Apply my colors"), UltraStyle.STYLE_CONFIRM, fields[0]);
        // The LM client draws this locked label as a live swatch of the fields as typed.
        spec.lockedButtonRight("Preview");
        spec.rowGap();
        String[] from = {"From Saiyan Royal", "From Namek", "From Obsidian", "From Beerus", "From Zeno Omni"};
        for (int i = 0; i < MenuSkins.ALL.size(); i++) {
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
        spec.footerBack("page:" + PAGE_SKINS, "Menu skins");
        spec.send(player);
    }

    /** Reopen My colors keeping what the player typed (after an editor click). */
    public static void openCustomDraft(ServerPlayer player) {
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
     * Pack hub tiles two-up. Panel height follows {@link UltraScreenSpec} content packing
     * (compact min height when there is no body copy), so more systems just add rows.
     */
    private static void placeTiles(UltraScreenSpec spec, Map<String, String> ph, List<HubTile> tiles) {
        if (spec == null || tiles == null || tiles.isEmpty()) {
            return;
        }
        int placed = 0;
        for (HubTile tile : tiles) {
            if (tile == null || tile.system == null || tile.system.isBlank()) {
                continue;
            }
            boolean left = placed % 2 == 0;
            placeTile(spec, ph, tile, left);
            placed++;
            if (!left) {
                spec.rowGap();
            }
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
    }

    /** {@code left} true/false: a half-width tile in that column; null: full width (Prestige, 4.7.64). */
    private static void placeTile(
            UltraScreenSpec spec, Map<String, String> ph, HubTile tile, Boolean left) {
        String system = tile.system();
        // Spar Board, My dojo and Dojo wars live in Sparring: same availability and accent.
        String owner = system.startsWith("spar_") ? "spar" : system;
        boolean on = tile.alwaysOn()
                || "difficulty".equals(system)
                || (ph != null && "true".equalsIgnoreCase(ph.get(owner)));
        String action = actionFor(system);
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
        if (left == null) {
            spec.buttonWide(action, styled, style, accent);
        } else if (left) {
            spec.buttonLeft(action, styled, style, accent);
        } else {
            spec.buttonRight(action, styled, style, accent);
        }
    }

    private static String actionFor(String system) {
        if (system == null || system.isBlank()) {
            return "open:hub";
        }
        return switch (system) {
            case "spar" -> "open:spar";
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
