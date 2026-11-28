package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.8.2: the in-menu banner editor (Dojo settings > Banner > Design a banner here). The master builds a banner from
 * vanilla parts only: a base colour, then up to {@link BannerDesign#MAX_LAYERS} layers, each a pattern from the vanilla
 * banner pattern registry and a dye colour. Layers can be added, recoloured, moved and removed; nothing is charged
 * until Save.
 *
 * <p>The page shows a live preview (layoutJson pictures item {@code b}: the banner icon, drawn by the LM client with
 * every layer) built from the same base and layer list Save writes. Save goes through
 * {@link DojoService#setBanner}: the dojo's first banner is free and starts no cooldown, a later change costs
 * {@link DojoCatalog#BANNER_CHANGE_COST} Dojo Points and starts the shared 12-hour identity cooldown. Copying a carried
 * banner (the older flow on the same page) still works.
 *
 * <p>4.8.4 (J): the editor is the way to pick a banner, and no banner item is ever needed. It also opens from founding
 * step 3 (no dojo yet): there Save puts the design into the founding draft ({@link DojoFounding#useDesign}) and goes on
 * to the founding confirm, free. Save never reads the inventory; nothing is ever taken.
 */
public final class DojoBannerEditor {
    private DojoBannerEditor() {}

    static final String PAGE_EDIT = "dojo_banner_edit";
    static final String PAGE_BASE = "dojo_banner_base";
    static final String PAGE_PATTERN = "dojo_banner_pat";
    static final String PAGE_COLOUR = "dojo_banner_col:";
    static final String PAGE_LAYER = "dojo_banner_layer:";
    static final String PAGE_RECOLOUR = "dojo_banner_recol:";
    static final String ACT_BASE = "dojo_bedit_base";
    static final String ACT_ADD = "dojo_bedit_add";
    static final String ACT_UP = "dojo_bedit_up";
    static final String ACT_DOWN = "dojo_bedit_down";
    static final String ACT_REMOVE = "dojo_bedit_remove";
    static final String ACT_RECOLOUR = "dojo_bedit_recol";
    static final String ACT_RESET = "dojo_bedit_reset";
    static final String ACT_SAVE = "dojo_bedit_save";
    /** Patterns a pattern page shows (the 8-name picker rule). */
    static final int PATTERNS_SHOWN = 8;

    /** One master's unsaved design for one dojo: base colour and layers in the stored {@code code:<dye id>} form. */
    record Edit(String dojo, String base, List<String> layers) {}

    private static final Map<UUID, Edit> EDITS = new ConcurrentHashMap<>();
    /** 4.8.4 (J): the {@link Edit#dojo} of a design made while founding (there is no dojo yet). */
    static final String FOUNDING = "found";

    static boolean owns(String lower) {
        return lower.equals(PAGE_EDIT) || lower.equals(PAGE_BASE) || lower.equals(PAGE_PATTERN)
                || lower.startsWith(PAGE_PATTERN + ":") || lower.startsWith(PAGE_COLOUR)
                || lower.startsWith(PAGE_LAYER) || lower.startsWith(PAGE_RECOLOUR);
    }

    /** Forget a player's unsaved design (logout). */
    static void forget(UUID id) {
        if (id != null) {
            EDITS.remove(id);
        }
    }

    /** The dojo's design as the editor starts it: the picked banner, or the style default with no layers. */
    static Edit fromDojo(Dojo d) {
        boolean none = DojoService.defaultBanner(d);
        String base = BannerDesign.colourOf(none ? DojoService.bannerColour(d) : d.bannerBase);
        return new Edit(d.masterUuid, base == null ? "white" : base, layersOf(none ? null : d.bannerPatterns));
    }

    /** 4.8.4 (J): a founding design as the editor starts it: the draft's banner, or the style's default colour. */
    static Edit fromDraft(ServerPlayer p) {
        DojoFounding.Draft dr = DojoFounding.draft(p);
        boolean none = !DojoFounding.draftHasBanner(dr);
        DojoCatalog.Style st = DojoCatalog.Style.of(dr.style());
        String base = BannerDesign.colourOf(none ? (st == null ? "white" : st.bannerColour) : dr.base());
        return new Edit(FOUNDING, base == null ? "white" : base, layersOf(none ? null : dr.patterns()));
    }

    /** Stored layers ({@code code:<dye>}) from a saved pattern list; unknown entries are skipped. */
    private static List<String> layersOf(List<String> patterns) {
        List<String> layers = new ArrayList<>();
        if (patterns != null) {
            for (String l : patterns) {
                String code = BannerDesign.layerCode(l);
                String col = BannerDesign.layerColour(l);
                if (!code.isEmpty() && col != null) {
                    layers.add(BannerDesign.layer(code, col));
                }
            }
        }
        return layers;
    }

    /**
     * 4.8.4 (J): true while {@code p} is founding a dojo (no dojo of their own, the draft has a name and a style): the
     * editor then designs the founding banner.
     */
    static boolean founding(ServerPlayer p) {
        return DojoService.foundProblem(p) == null && DojoFounding.draftReady(p);
    }

    /** The founding design in progress (started from the draft). */
    static Edit foundingEdit(ServerPlayer p) {
        Edit e = EDITS.get(p.m_20148_());
        if (e == null || !FOUNDING.equals(e.dojo())) {
            e = fromDraft(p);
            EDITS.put(p.m_20148_(), e);
        }
        return e;
    }

    static Edit edit(ServerPlayer p, Dojo d) {
        Edit e = EDITS.get(p.m_20148_());
        if (e == null || !d.masterUuid.equals(e.dojo())) {
            e = fromDojo(d);
            EDITS.put(p.m_20148_(), e);
        }
        return e;
    }

    /** The preview icon of this player's design (the probe compares it with what Save wrote); "" without a dojo. */
    public static String draftIcon(ServerPlayer p) {
        Dojo d = DojoService.viewedDojo(p);
        if (d == null) {
            return founding(p) ? BannerDesign.icon(foundingEdit(p).base(), foundingEdit(p).layers()) : "";
        }
        Edit e = edit(p, d);
        return BannerDesign.icon(e.base(), e.layers());
    }

    // ------------------------------------------------------------------ pages

    static void open(ServerPlayer p, String raw, String notice) {
        String lower = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.equals(PAGE_BASE)) {
            openBase(p, notice);
        } else if (lower.equals(PAGE_PATTERN) || lower.startsWith(PAGE_PATTERN + ":")) {
            openPatterns(p, UltraPickHelper.pageIndex(lower), notice);
        } else if (lower.startsWith(PAGE_COLOUR)) {
            openColour(p, lower.substring(PAGE_COLOUR.length()), -1, notice);
        } else if (lower.startsWith(PAGE_RECOLOUR)) {
            openColour(p, "", index(lower.substring(PAGE_RECOLOUR.length())), notice);
        } else if (lower.startsWith(PAGE_LAYER)) {
            openLayer(p, index(lower.substring(PAGE_LAYER.length())), notice);
        } else {
            openEdit(p, notice);
        }
    }

    /** The master's dojo, or null (no dojo, or not its master). */
    private static Dojo masterDojo(ServerPlayer p) {
        Dojo d = DojoService.viewedDojo(p);
        return d != null && DojoService.isMaster(p.m_20148_(), d) ? d : null;
    }

    /**
     * The design this page edits: the master's dojo's, or (4.8.4 (J)) the founding one; or null after drawing "only
     * the master" on {@code spec}.
     */
    private static Edit editFor(ServerPlayer p, UltraScreenSpec spec) {
        Dojo d = masterDojo(p);
        if (d != null) {
            return edit(p, d);
        }
        if (founding(p)) {
            return foundingEdit(p);
        }
        spec.line(UltraStyle.LABEL + "Only your dojo's master can design its banner.").buttonsAfterLines();
        spec.back();
        spec.send(p);
        return null;
    }

    /** True when the design is the dojo's saved banner (saving it again would cost points for nothing). */
    static boolean unchanged(Dojo d, Edit e) {
        if (DojoService.defaultBanner(d)) {
            return false;
        }
        Edit now = fromDojo(d);
        return now.base().equals(e.base()) && now.layers().equals(e.layers());
    }

    static void openEdit(ServerPlayer p, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_EDIT, notice);
        Edit e = editFor(p, spec);
        if (e == null) {
            return;
        }
        Dojo d = masterDojo(p);
        boolean founding = d == null;
        boolean first = founding || DojoService.defaultBanner(d);
        int n = e.layers().size();
        String icon = BannerDesign.icon(e.base(), e.layers());
        List<String> card = new ArrayList<>();
        // 4.8.2: four short lines beside the preview, so the page (with a notice and a two-line reason) fits 300 px.
        card.add(UltraStyle.header("Banner editor"));
        card.add(UltraStyle.labelValue("Base", BannerDesign.colourLabel(e.base())) + UltraStyle.LABEL + " - "
                + UltraStyle.labelValue("Layers", n + " of " + BannerDesign.MAX_LAYERS));
        if (founding) {
            // 4.8.4 (J): founding step 3; Save keeps the design for the founding, which is free.
            card.add(UltraStyle.labelValue("Saving", "free, for your new dojo"));
            card.add(UltraStyle.LABEL + "No banner item needed.");
        } else if (first) {
            card.add(UltraStyle.labelValue("Saving", "free, your first banner"));
            card.add(UltraStyle.LABEL + "It starts no wait.");
        } else {
            card.add(UltraStyle.labelValue("Saving costs", DojoCatalog.BANNER_CHANGE_COST + " Dojo Points"));
            card.add(UltraStyle.labelValue("Your dojo has", d.points + " points"));
        }
        spec.pictures(card, card.size(), PICTURE_SIDE, preview(icon));
        String bad = first ? null : DojoService.identityChangeProblem(d, DojoCatalog.BANNER_CHANGE_COST);
        if (bad != null) {
            spec.line(UltraStyle.dangerLine(bad));
        } else if (!first) {
            spec.line(UltraStyle.LABEL + "Saving locks the name, style and banner for 12 hours.");
        }
        spec.buttonsAfterLines();
        UltraDojoScreens.col3(spec, 0, "page:" + PAGE_BASE, "Base colour", UltraStyle.STYLE_NORMAL);
        if (n >= BannerDesign.MAX_LAYERS) {
            UltraDojoScreens.col3(spec, 1, null, UltraDojoScreens.fitCol3("Add - 6 is the most", "Add a layer"), null);
        } else {
            UltraDojoScreens.col3(spec, 1, "page:" + PAGE_PATTERN, "Add a layer", UltraStyle.STYLE_NORMAL);
        }
        UltraDojoScreens.col3(spec, 2, "do:" + ACT_RESET, "Start over", UltraStyle.STYLE_NORMAL);
        spec.rowGap();
        for (int i = 0; i < n; i++) {
            String l = e.layers().get(i);
            String label = UltraStyle.clipPlain((i + 1) + ". " + BannerDesign.patternName(BannerDesign.layerCode(l)),
                    UltraDojoScreens.COL3_LABEL_PX - 12);
            UltraDojoScreens.col3(spec, i % 3, "page:" + PAGE_LAYER + (i + 1), label, UltraStyle.STYLE_NORMAL);
            spec.icon(BannerDesign.icon(e.base(), List.of(l)));
            if (i % 3 == 2 || i == n - 1) {
                spec.rowGap();
            }
        }
        String save;
        if (founding) {
            spec.buttonWide("do:" + ACT_SAVE, UltraStyle.confirm("Use this banner (free)"), UltraStyle.STYLE_CONFIRM,
                    null);
        } else if (first) {
            spec.buttonWide("do:" + ACT_SAVE, UltraStyle.confirm("Save this banner (free)"), UltraStyle.STYLE_CONFIRM,
                    null);
        } else if (unchanged(d, e)) {
            spec.buttonWide(UltraGuiActions.NO_ACTION, UltraStyle.dim("Save - nothing changed yet"),
                    UltraStyle.STYLE_DIM, null);
        } else if (bad != null) {
            long cd = DojoService.identityCooldownLeft(d);
            save = cd > 0L ? "Save in " + DojoService.cooldownText(cd)
                    : "Save - needs " + DojoCatalog.BANNER_CHANGE_COST + " Dojo Points";
            spec.buttonWide(UltraGuiActions.NO_ACTION, UltraStyle.dim(save), UltraStyle.STYLE_DIM, null);
        } else {
            spec.buttonWide("do:" + ACT_SAVE, UltraStyle.confirm("Save this banner - " + DojoCatalog.BANNER_CHANGE_COST
                    + " Dojo Points"), UltraStyle.STYLE_CONFIRM, null);
        }
        spec.icon(icon);
        spec.rowGap();
        spec.back();
        spec.send(p);
    }

    /** Share of the width the preview takes beside its card (thousandths). */
    static final int PICTURE_SIDE = 280;

    /** The live preview: one pictures item that the LM client draws as the banner, every layer in order. */
    static JsonArray preview(String icon) {
        JsonArray items = new JsonArray();
        JsonObject it = new JsonObject();
        it.addProperty("a", 500);                         // a banner is twice as tall as it is wide
        it.addProperty("b", icon);
        items.add(it);
        return items;
    }

    static void openBase(ServerPlayer p, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_BASE, notice);
        Edit e = editFor(p, spec);
        if (e == null) {
            return;
        }
        spec.line(UltraStyle.header("Base colour"))
                .line(UltraStyle.LABEL + "Pick the banner's background. Your layers stay as they are.")
                .buttonsAfterLines();
        colourGrid(spec, e.base(), c -> "do:" + ACT_BASE + ":" + c, c -> BannerDesign.icon(c, e.layers()));
        spec.back();
        spec.send(p);
    }

    static void openPatterns(ServerPlayer p, int page, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(UltraPickHelper.pageArg(PAGE_PATTERN, page), notice);
        Edit e = editFor(p, spec);
        if (e == null) {
            return;
        }
        if (e.layers().size() >= BannerDesign.MAX_LAYERS) {
            openEdit(p, "6 layers is the most. Remove one first.");
            return;
        }
        List<String> all = BannerDesign.patterns();
        int pages = Math.max(1, (all.size() + PATTERNS_SHOWN - 1) / PATTERNS_SHOWN);
        int pg = Math.max(0, Math.min(page, pages - 1));
        spec.line(UltraStyle.header("Add a layer - pick a pattern"))
                .line(UltraStyle.LABEL + "Page " + (pg + 1) + " of " + pages + ". Next you pick its colour.")
                .buttonsAfterLines();
        int contrast = BannerDesign.contrastOn(e.base());
        int placed = 0;
        for (int i = pg * PATTERNS_SHOWN; i < Math.min(all.size(), (pg + 1) * PATTERNS_SHOWN); i++) {
            String code = all.get(i);
            String action = "page:" + PAGE_COLOUR + code;
            String label = BannerDesign.patternName(code);
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonRight(action, label, UltraStyle.STYLE_NORMAL);
            }
            spec.icon(BannerDesign.icon(e.base(), List.of(code + ":" + contrast)));
            if (placed % 2 == 1) {
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        UltraDojoScreens.pager(spec, PAGE_PATTERN, pg, pages);
        spec.back();
        spec.send(p);
    }

    /** Pick a colour for a new layer ({@code at} -1, pattern {@code code}) or for layer {@code at} (1-based). */
    static void openColour(ServerPlayer p, String code, int at, String notice) {
        boolean recolour = at > 0;
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(recolour ? PAGE_RECOLOUR + at : PAGE_COLOUR + code, notice);
        Edit e = editFor(p, spec);
        if (e == null) {
            return;
        }
        if (recolour && at > e.layers().size()) {
            openEdit(p, "That layer is gone. Pick one from the list.");
            return;
        }
        if (!recolour && !BannerDesign.knownPattern(code)) {
            openPatterns(p, 0, "Pick a pattern from the list.");
            return;
        }
        String pat = recolour ? BannerDesign.layerCode(e.layers().get(at - 1)) : code;
        String current = recolour ? BannerDesign.layerColour(e.layers().get(at - 1)) : null;
        spec.line(UltraStyle.header((recolour ? "Layer " + at + ": " : "New layer: ") + BannerDesign.patternName(pat)))
                .line(UltraStyle.LABEL + "Pick its colour. Each button shows your banner with it.")
                .buttonsAfterLines();
        colourGrid(spec, current,
                c -> recolour ? "do:" + ACT_RECOLOUR + ":" + at + ":" + BannerDesign.colourId(c)
                        : "do:" + ACT_ADD + ":" + pat + ":" + BannerDesign.colourId(c),
                c -> {
                    List<String> layers = new ArrayList<>(e.layers());
                    if (recolour) {
                        layers.set(at - 1, BannerDesign.layer(pat, c));
                    } else {
                        layers.add(BannerDesign.layer(pat, c));
                    }
                    return BannerDesign.icon(e.base(), layers);
                });
        spec.back();
        spec.send(p);
    }

    /** The 16 dye colours, three a row, each with the banner it would make; {@code current} is marked. */
    private static void colourGrid(UltraScreenSpec spec, String current,
            java.util.function.Function<String, String> action, java.util.function.Function<String, String> icon) {
        int i = 0;
        for (String c : BannerDesign.COLOURS) {
            boolean now = c.equals(current);
            String label = BannerDesign.colourLabel(c) + (now ? " - now" : "");
            UltraDojoScreens.col3(spec, i % 3, action.apply(c), now ? UltraStyle.confirm(label) : label,
                    now ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL);
            spec.icon(icon.apply(c));
            if (i % 3 == 2) {
                spec.rowGap();
            }
            i++;
        }
        if (i % 3 != 0) {
            spec.rowGap();
        }
    }

    static void openLayer(ServerPlayer p, int at, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_LAYER + at, notice);
        Edit e = editFor(p, spec);
        if (e == null) {
            return;
        }
        int n = e.layers().size();
        if (at < 1 || at > n) {
            openEdit(p, "That layer is gone. Pick one from the list.");
            return;
        }
        String l = e.layers().get(at - 1);
        spec.line(UltraStyle.header("Layer " + at + " of " + n))
                .line(UltraStyle.labelValue("Pattern", BannerDesign.patternName(BannerDesign.layerCode(l))))
                .line(UltraStyle.labelValue("Colour", BannerDesign.colourLabel(BannerDesign.layerColour(l))))
                .line(UltraStyle.LABEL + "Layer 1 is drawn first; later layers cover it.")
                .buttonsAfterLines();
        if (at > 1) {
            spec.buttonLeft("do:" + ACT_UP + ":" + at, "Move up (drawn earlier)", UltraStyle.STYLE_NORMAL);
        } else {
            spec.lockedButton("Move up - already first");
        }
        if (at < n) {
            spec.buttonRight("do:" + ACT_DOWN + ":" + at, "Move down (drawn later)", UltraStyle.STYLE_NORMAL);
        } else {
            spec.lockedButtonRight("Move down - already last");
        }
        spec.rowGap();
        spec.buttonLeft("page:" + PAGE_RECOLOUR + at, "Change its colour", UltraStyle.STYLE_NORMAL);
        spec.icon(BannerDesign.icon(e.base(), List.of(l)));
        spec.buttonRight("do:" + ACT_REMOVE + ":" + at, UltraStyle.danger("Remove this layer"),
                UltraStyle.STYLE_DANGER).rowGap();
        spec.back();
        spec.send(p);
    }

    // ------------------------------------------------------------------ actions

    private static int index(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Editor actions. True when {@code action} was one of them (the next page is opened). */
    static boolean handle(ServerPlayer p, String action, String arg) {
        if (action == null || !action.startsWith("dojo_bedit_")) {
            return false;
        }
        Dojo d = masterDojo(p);
        boolean founding = d == null && founding(p);
        if (d == null && !founding) {
            UltraDojoScreens.openDojoNotice(p, "Only your dojo's master can design its banner.");
            return true;
        }
        Edit e = founding ? foundingEdit(p) : edit(p, d);
        List<String> layers = new ArrayList<>(e.layers());
        String a = arg == null ? "" : arg.trim();
        switch (action) {
            case ACT_BASE -> {
                String c = BannerDesign.colourOf(a);
                if (c == null) {
                    openBase(p, "Pick one of the colours.");
                    return true;
                }
                put(p, e.dojo(), c, layers);
                openEdit(p, null);
            }
            case ACT_ADD -> {
                int colon = a.indexOf(':');
                String code = colon < 0 ? a : a.substring(0, colon);
                String c = colon < 0 ? null : BannerDesign.colourOf(a.substring(colon + 1));
                if (!BannerDesign.knownPattern(code) || c == null) {
                    openPatterns(p, 0, "Pick a pattern, then a colour.");
                    return true;
                }
                if (layers.size() >= BannerDesign.MAX_LAYERS) {
                    openEdit(p, "6 layers is the most. Remove one first.");
                    return true;
                }
                layers.add(BannerDesign.layer(code, c));
                put(p, e.dojo(), e.base(), layers);
                openEdit(p, null);
            }
            case ACT_UP, ACT_DOWN -> {
                int at = index(a);
                int to = ACT_UP.equals(action) ? at - 1 : at + 1;
                if (at < 1 || at > layers.size() || to < 1 || to > layers.size()) {
                    openEdit(p, null);
                    return true;
                }
                String moved = layers.remove(at - 1);
                layers.add(to - 1, moved);
                put(p, e.dojo(), e.base(), layers);
                openLayer(p, to, null);
            }
            case ACT_REMOVE -> {
                int at = index(a);
                if (at >= 1 && at <= layers.size()) {
                    layers.remove(at - 1);
                    put(p, e.dojo(), e.base(), layers);
                }
                openEdit(p, null);
            }
            case ACT_RECOLOUR -> {
                int colon = a.indexOf(':');
                int at = index(colon < 0 ? a : a.substring(0, colon));
                String c = colon < 0 ? null : BannerDesign.colourOf(a.substring(colon + 1));
                if (at < 1 || at > layers.size() || c == null) {
                    openEdit(p, "Pick a layer, then a colour.");
                    return true;
                }
                layers.set(at - 1, BannerDesign.layer(BannerDesign.layerCode(layers.get(at - 1)), c));
                put(p, e.dojo(), e.base(), layers);
                openEdit(p, null);
            }
            case ACT_RESET -> {
                EDITS.put(p.m_20148_(), founding ? fromDraft(p) : fromDojo(d));
                openEdit(p, founding ? "Back to where you started." : "Back to your saved banner.");
            }
            case ACT_SAVE -> {
                if (founding) {
                    // 4.8.4 (J): the founding banner goes into the draft (no item read, nothing taken); founding is
                    // free and the confirm page shows it.
                    DojoFounding.useDesign(p, e.base(), e.layers());
                    EDITS.remove(p.m_20148_());
                    DojoFounding.openConfirm(p, "Your banner is ready. Found your dojo to fly it.");
                    return true;
                }
                if (unchanged(d, e)) {
                    openEdit(p, "That is already your dojo's banner.");
                    return true;
                }
                if (!DojoService.defaultBanner(d)
                        && DojoService.identityChangeProblem(d, DojoCatalog.BANNER_CHANGE_COST) != null) {
                    openEdit(p, null);                  // the page's red line already says why (no repeat notice)
                    return true;
                }
                String msg = DojoService.setBanner(p, e.base(), new ArrayList<>(e.layers()));
                Dojo after = DojoService.viewedDojo(p);
                if (after != null && !DojoService.defaultBanner(after) && fromDojo(after).base().equals(e.base())
                        && fromDojo(after).layers().equals(e.layers())) {
                    EDITS.remove(p.m_20148_());
                    UltraDojoScreens.openDojoNotice(p, msg);
                } else {
                    openEdit(p, msg);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void put(ServerPlayer p, String dojo, String base, List<String> layers) {
        EDITS.put(p.m_20148_(), new Edit(dojo, base, List.copyOf(layers)));
    }
}
