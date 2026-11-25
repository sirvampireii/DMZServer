package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.Dojo;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * LM 4.7.78: founding a dojo is a three-step flow on the Dojo screen, and the dojo's identity (name, style, banner)
 * can change later for Dojo Points.
 *
 * <ol>
 *   <li>Name: the player types it (3 to 24 letters, numbers or spaces; no generated name).</li>
 *   <li>Style: one of the four {@link DojoCatalog.Style}s, the same the master can change later on the style page.</li>
 *   <li>Banner (4.7.84: optional, owner pick d): any crafted banner the player holds or carries. The dojo copies its
 *       design (base colour and patterns); the item stays in the inventory. "Use a default banner for now" founds
 *       with the style's default banner; "Choose your dojo's banner (free)" then stays on Dojo Home until one is
 *       picked.</li>
 * </ol>
 *
 * The founding picks are free and start no cooldown; a player's first founding also pays
 * {@link DojoCatalog#FOUNDING_GRANT} Dojo Points (owner pick c). After founding, the master changes the name (20 Dojo Points),
 * style (40, on the style page) or banner (20) with one shared 12-hour cooldown; an older dojo's first banner pick is
 * free. {@link DojoService} enforces every rule; this class only draws the pages and passes the choices on.
 */
public final class DojoFounding {
    private DojoFounding() {}

    public static final String PAGE_NAME = "found";
    static final String PAGE_STYLE = "found_style";
    static final String PAGE_BANNER = "found_banner";
    static final String PAGE_CONFIRM = "found_confirm";
    static final String PAGE_PICK_BANNER = "dojo_banner";
    static final String PAGE_RENAME = "dojo_rename";
    static final String INPUT_NAME = "input:dojo_found_name";
    static final String INPUT_RENAME = "input:dojo_rename";
    static final String ACT_NAME = "dojo_found_name";
    static final String ACT_STYLE = "dojo_found_style";
    static final String ACT_BANNER = "dojo_found_banner";
    static final String ACT_CONFIRM = "dojo_found_confirm";
    static final String ACT_SET_BANNER = "dojo_banner_set";
    static final String ACT_RENAME = "dojo_rename_set";
    static final int BANNERS_SHOWN = 6;
    /** 4.7.84: the banner step's "Use a default banner for now" choice (the draft's base while no banner is picked). */
    static final String DEFAULT_BANNER = "default";

    /** One player's founding choices so far. */
    record Draft(String name, String style, String base, List<String> patterns) {
        static final Draft EMPTY = new Draft("", "", "", List.of());
    }

    private static final Map<UUID, Draft> DRAFTS = new ConcurrentHashMap<>();

    /** A banner the player can pick: where it is ({@code hand}, {@code off}, {@code slot:N}) and its design. */
    record Banner(String source, String base, List<String> patterns) {}

    static boolean owns(String lower) {
        return lower.equals(PAGE_NAME) || lower.equals(PAGE_STYLE) || lower.equals(PAGE_BANNER)
                || lower.equals(PAGE_CONFIRM) || lower.equals(PAGE_PICK_BANNER) || lower.equals(PAGE_RENAME);
    }

    static Draft draft(ServerPlayer p) {
        return DRAFTS.getOrDefault(p.m_20148_(), Draft.EMPTY);
    }

    /** Forget a player's unfinished founding (logout). */
    public static void forget(UUID id) {
        if (id != null) {
            DRAFTS.remove(id);
        }
    }

    // ------------------------------------------------------------------ pages

    static void open(ServerPlayer p, String raw, String notice) {
        String lower = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        switch (lower) {
            case PAGE_STYLE -> openStyle(p, notice);
            case PAGE_BANNER -> openBanner(p, notice);
            case PAGE_CONFIRM -> openConfirm(p, notice);
            case PAGE_PICK_BANNER -> openPickBanner(p, notice);
            case PAGE_RENAME -> openRename(p, notice);
            default -> openName(p, notice);
        }
    }

    /** A founding step the player cannot take right now (already in a dojo, a closed dojo): say why. */
    private static boolean blocked(ServerPlayer p, UltraScreenSpec spec) {
        String bad = DojoService.foundProblem(p);
        if (bad == null) {
            return false;
        }
        spec.line(UltraStyle.header("Found a dojo")).line(UltraStyle.LABEL + bad).buttonsAfterLines();
        spec.back();
        spec.send(p);
        return true;
    }

    /** Longest dojo name {@code DojoService.nameProblem} accepts. */
    private static final int DOJO_NAME_MAX = 24;

    private static void inputRow(UltraScreenSpec spec, String inputId, String value, String placeholder, String action,
            String label) {
        // 4.7.84: the button is as wide as its label needs (at least 80 px), so "Next: pick a style" is not cut.
        int btn = Math.max(UltraStyle.BTN_MIN_W, UltraStyle.textWidthPx(label) + UltraStyle.BTN_PAD * 2 + 4);
        int fieldW = UltraStyle.BTN_W * 2 + UltraStyle.BTN_GAP - btn - UltraStyle.BTN_GAP;
        int y = spec.row();
        // GuiKit 4.7.81: the field stops at the dojo name limit (DojoService.nameProblem: 3 to 24 characters).
        spec.textField(inputId, value == null ? "" : value, placeholder, UltraStyle.COL_L, y, fieldW, UltraStyle.BTN_H,
                DOJO_NAME_MAX);
        spec.buttonBound("do:" + action, UltraStyle.confirm(label), UltraStyle.STYLE_CONFIRM,
                UltraStyle.COL_L + fieldW + UltraStyle.BTN_GAP, y, btn, inputId);
        spec.rowGap();
    }

    static void openName(ServerPlayer p, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_NAME, notice);
        if (blocked(p, spec)) {
            return;
        }
        spec.line(UltraStyle.header("Found a dojo - step 1 of 3: name"))
                .line(UltraStyle.VALUE + "Name your dojo. Other players see it on the dojo list and in wars.")
                .line(UltraStyle.LABEL + "3 to 24 letters, numbers or spaces. Next you pick a style, then a banner"
                        + " (optional).");
        grantLine(spec, p);
        spec.buttonsAfterLines();
        inputRow(spec, INPUT_NAME, draft(p).name(), "Your dojo's name", ACT_NAME, "Next: pick a style");
        spec.back();
        spec.send(p);
    }

    static void openStyle(ServerPlayer p, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_STYLE, notice);
        if (blocked(p, spec)) {
            return;
        }
        Draft d = draft(p);
        if (d.name().isBlank()) {
            openName(p, notice);
            return;
        }
        spec.line(UltraStyle.header("Found a dojo - step 2 of 3: style"))
                .line(UltraStyle.labelValue("Name", d.name()))
                .line(UltraStyle.VALUE + "Pick the practice your dojo is based on. Members get its bonus when they"
                        + " fight in it.");
        for (DojoCatalog.Style s : DojoCatalog.Style.values()) {
            spec.line(UltraStyle.labelValue(s.label, s.covers));
        }
        spec.line(UltraStyle.LABEL + "You can change it later on the style page (it costs Dojo Points).")
                .buttonsAfterLines();
        int placed = 0;
        for (DojoCatalog.Style s : DojoCatalog.Style.values()) {
            String act = "do:" + ACT_STYLE + ":" + s.name();
            String label = s.name().equals(d.style()) ? UltraStyle.confirm(s.label) : s.label;
            String style = s.name().equals(d.style()) ? UltraStyle.STYLE_CONFIRM : UltraStyle.STYLE_NORMAL;
            if (placed % 2 == 0) {
                spec.buttonLeft(act, label, style);
            } else {
                spec.buttonRight(act, label, style).rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        spec.back();
        spec.send(p);
    }

    static void openBanner(ServerPlayer p, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_BANNER, notice);
        if (blocked(p, spec)) {
            return;
        }
        Draft d = draft(p);
        if (d.name().isBlank() || d.style().isBlank()) {
            open(p, d.name().isBlank() ? PAGE_NAME : PAGE_STYLE, notice);
            return;
        }
        DojoCatalog.Style s = DojoCatalog.Style.of(d.style());
        spec.line(UltraStyle.header("Found a dojo - step 3 of 3: banner (optional)"))
                .line(UltraStyle.labelValue("Name", d.name()) + UltraStyle.LABEL + " - " + UltraStyle.labelValue("Style",
                        s == null ? d.style() : s.label));
        // 4.7.84 (owner pick d): founding works without a banner; the dojo flies its style's default banner until the
        // master picks one on Dojo Home (the first pick stays free).
        List<Banner> found = banners(p);
        String colour = s == null ? "white" : s.bannerColour;
        if (found.isEmpty()) {
            spec.line(UltraStyle.LABEL + "You carry no banner. That's fine: use the default and choose your own later on"
                    + " Dojo Home (free).");
        } else {
            spec.line(UltraStyle.LABEL + "Pick a banner you carry (the dojo copies its design; the banner stays yours),"
                    + " or use the default for now.");
        }
        spec.buttonsAfterLines();
        spec.buttonWide("do:" + ACT_BANNER + ":" + DEFAULT_BANNER, UltraStyle.confirm("Use a default banner for now ("
                + describeColour(colour) + ")"), UltraStyle.STYLE_CONFIRM, null).rowGap();
        for (Banner b : found) {
            spec.buttonWide("do:" + ACT_BANNER + ":" + b.source(), "Use: " + describe(b.base(), b.patterns())
                    + where(b.source()), UltraStyle.STYLE_NORMAL, null).rowGap();
        }
        if (found.isEmpty()) {
            spec.button("page:" + PAGE_BANNER, "Check again", UltraStyle.STYLE_NORMAL).rowGap();
        }
        spec.back();
        spec.send(p);
    }

    /** 4.7.84 (owner pick c): "Your first dojo starts with 15 Dojo Points." while this player's grant is unpaid. */
    static void grantLine(UltraScreenSpec spec, ServerPlayer p) {
        String id = p.m_20148_().toString();
        int grant = DojoService.foundingGrant(com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().players.get(id),
                com.dbzlegacy.adaptivedifficulty.dojo.DojoStore.get().dojos.containsKey(id));
        if (grant > 0) {
            spec.line(UltraStyle.LABEL + "Your first dojo starts with " + grant + " Dojo Points.");
        }
    }

    /** "Red" for {@code red}, "Light blue" for {@code light_blue}. */
    static String describeColour(String base) {
        String b = base == null || base.isBlank() ? "white" : base.replace('_', ' ');
        return Character.toUpperCase(b.charAt(0)) + b.substring(1);
    }

    static void openConfirm(ServerPlayer p, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_CONFIRM, notice);
        if (blocked(p, spec)) {
            return;
        }
        Draft d = draft(p);
        if (d.name().isBlank() || d.style().isBlank() || d.base().isBlank()) {
            open(p, d.name().isBlank() ? PAGE_NAME : d.style().isBlank() ? PAGE_STYLE : PAGE_BANNER, notice);
            return;
        }
        DojoCatalog.Style s = DojoCatalog.Style.of(d.style());
        boolean useDefault = DEFAULT_BANNER.equals(d.base());
        spec.line(UltraStyle.header("Found " + d.name() + "?"))
                .line(UltraStyle.labelValue("Name", d.name()))
                .line(UltraStyle.labelValue("Style", s == null ? d.style() : s.label + " (" + s.covers + ")"))
                .line(UltraStyle.labelValue("Banner", useDefault ? "default (" + describeColour(s == null ? "white"
                        : s.bannerColour) + "), choose your own later for free" : describe(d.base(), d.patterns())))
                .line(UltraStyle.LABEL + (useDefault ? "Founding is free." : "Founding is free. Your banner item stays in"
                        + " your inventory."))
                .line(UltraStyle.LABEL + "You become the master. Your hall starts with one ring.");
        grantLine(spec, p);
        spec.buttonsAfterLines();
        spec.button("do:" + ACT_CONFIRM, UltraStyle.confirm("Found " + d.name()), UltraStyle.STYLE_CONFIRM).rowGap();
        spec.back();
        spec.send(p);
    }

    /** Pick or change the dojo's banner (master): the first pick is free, a change costs points and the cooldown. */
    static void openPickBanner(ServerPlayer p, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_PICK_BANNER, notice);
        Dojo d = DojoService.viewedDojo(p);
        if (d == null || !DojoService.isMaster(p.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only your dojo's master can pick its banner.").buttonsAfterLines();
            spec.back();
            spec.send(p);
            return;
        }
        boolean first = d.bannerBase == null || d.bannerBase.isBlank();
        spec.line(UltraStyle.header(first ? "Choose your dojo's banner" : "Change your dojo's banner"));
        if (!first) {
            spec.line(UltraStyle.labelValue("Now", describe(d.bannerBase, d.bannerPatterns)))
                    .line(UltraStyle.labelValue("A change costs", DojoCatalog.BANNER_CHANGE_COST + " Dojo Points (you have "
                            + d.points + ")"))
                    .line(UltraStyle.LABEL + "After it, the name, style and banner can't change for 12 hours.");
            String bad = DojoService.identityChangeProblem(d, DojoCatalog.BANNER_CHANGE_COST);
            if (bad != null) {
                spec.line(UltraStyle.dangerLine(bad)).buttonsAfterLines();
                if (DojoService.identityCooldownLeft(d) <= 0L) {
                    spec.offerGetMore(UltraStyle.Shortfall.DOJO_POINTS, null);   // 4.7.85 (N6)
                }
                spec.back();
                spec.send(p);
                return;
            }
        } else {
            spec.line(UltraStyle.labelValue("Now", "default (" + describeColour(DojoService.bannerColour(d)) + ")"))
                    .line(UltraStyle.LABEL + "Your first banner is free.");
        }
        bannerPicker(spec, p, "do:" + ACT_SET_BANNER + ":", first ? "Use" : "Use for " + DojoCatalog.BANNER_CHANGE_COST);
        spec.back();
        spec.send(p);
    }

    /** Rename the dojo (master): costs points and the shared cooldown. */
    static void openRename(ServerPlayer p, String notice) {
        UltraScreenSpec spec = UltraDojoScreens.dojoSub(PAGE_RENAME, notice);
        Dojo d = DojoService.viewedDojo(p);
        if (d == null || !DojoService.isMaster(p.m_20148_(), d)) {
            spec.line(UltraStyle.LABEL + "Only your dojo's master can rename it.").buttonsAfterLines();
            spec.back();
            spec.send(p);
            return;
        }
        spec.line(UltraStyle.header("Rename " + d.name))
                .line(UltraStyle.labelValue("A rename costs", DojoCatalog.NAME_CHANGE_COST + " Dojo Points (you have "
                        + d.points + ")"))
                .line(UltraStyle.LABEL + "3 to 24 letters, numbers or spaces.")
                .line(UltraStyle.LABEL + "After it, the name, style and banner can't change for 12 hours.");
        String bad = DojoService.identityChangeProblem(d, DojoCatalog.NAME_CHANGE_COST);
        if (bad != null) {
            spec.line(UltraStyle.dangerLine(bad)).buttonsAfterLines();
            if (DojoService.identityCooldownLeft(d) <= 0L) {
                spec.offerGetMore(UltraStyle.Shortfall.DOJO_POINTS, null);   // 4.7.85 (N6)
            }
        } else {
            spec.buttonsAfterLines();
            inputRow(spec, INPUT_RENAME, "", "New name", ACT_RENAME, "Rename");
        }
        spec.back();
        spec.send(p);
    }

    /**
     * The banners the player holds or carries, as buttons; or, with none, how to get one and a "Check again" button
     * (4.7.84: any crafted banner works, plain is fine).
     */
    private static void bannerPicker(UltraScreenSpec spec, ServerPlayer p, String actionPrefix, String verb) {
        List<Banner> found = banners(p);
        if (found.isEmpty()) {
            spec.line(UltraStyle.dangerLine("You have no banner with you."))
                    .line(UltraStyle.LABEL + "Craft any banner (6 wool and a stick; add patterns at a loom if you like),"
                            + " hold it or carry it, then check again.")
                    .buttonsAfterLines();
            spec.button("page:" + PAGE_PICK_BANNER, "Check again", UltraStyle.STYLE_NORMAL).rowGap();
            return;
        }
        spec.line(UltraStyle.VALUE + "Pick a banner you carry. The dojo copies its design; the banner stays yours.")
                .buttonsAfterLines();
        // Full-width rows: "Use for 20: Light gray, 6 patterns (off hand)" is cut on a half-width button.
        for (Banner b : found) {
            spec.buttonWide(actionPrefix + b.source(), verb + ": " + describe(b.base(), b.patterns()) + where(b.source()),
                    UltraStyle.STYLE_NORMAL, null).rowGap();
        }
    }

    private static String where(String source) {
        return switch (source) {
            case "hand" -> " (in hand)";
            case "off" -> " (off hand)";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ Dojo Home pieces

    /** The banner line on the Dojo Home (name and style are already there). */
    static void bannerLines(UltraScreenSpec spec, Dojo d) {
        spec.line(UltraStyle.labelValue("Banner", bannerText(d)));
    }

    /** "Red, 1 pattern", or (4.7.84) "default (Red)" while the master has not picked one. */
    static String bannerText(Dojo d) {
        return DojoService.defaultBanner(d) ? "default (" + describeColour(DojoService.bannerColour(d)) + ")"
                : describe(d.bannerBase, d.bannerPatterns);
    }

    /**
     * The master's identity buttons (4.7.84: on Dojo settings): rename and change the banner, cost shown; dim with the
     * time left during the cooldown, dim with the cost when unaffordable. With no banner picked yet the banner button
     * is the free first pick.
     */
    static void identityButtons(UltraScreenSpec spec, ServerPlayer p, Dojo d, boolean master) {
        if (!master) {
            return;
        }
        boolean firstBanner = DojoService.defaultBanner(d);
        long cd = DojoService.identityCooldownLeft(d);
        String renameLabel = "Rename - " + DojoCatalog.NAME_CHANGE_COST + " points";
        String bannerLabel = "Banner - " + DojoCatalog.BANNER_CHANGE_COST + " points";
        if (cd > 0L) {
            spec.lockedButton("Rename in " + DojoService.cooldownText(cd));
        } else if (d.points < DojoCatalog.NAME_CHANGE_COST) {
            spec.lockedButton("Rename - needs " + DojoCatalog.NAME_CHANGE_COST);
        } else {
            spec.buttonLeft("page:" + PAGE_RENAME, renameLabel, UltraStyle.STYLE_NORMAL);
        }
        if (firstBanner) {
            spec.buttonRight("page:" + PAGE_PICK_BANNER, UltraStyle.confirm("Banner (free)"), UltraStyle.STYLE_CONFIRM);
        } else if (cd > 0L) {
            spec.lockedButtonRight("Banner in " + DojoService.cooldownText(cd));
        } else if (d.points < DojoCatalog.BANNER_CHANGE_COST) {
            spec.lockedButtonRight("Banner - needs " + DojoCatalog.BANNER_CHANGE_COST);
        } else {
            spec.buttonRight("page:" + PAGE_PICK_BANNER, bannerLabel, UltraStyle.STYLE_NORMAL);
        }
        spec.rowGap();
    }

    // ------------------------------------------------------------------ actions

    /** Founding and identity actions. True when {@code action} was one of them (the next page is opened). */
    public static boolean handle(ServerPlayer p, String action, String arg, JsonObject args) {
        if (p == null || action == null) {
            return false;
        }
        String a = arg == null ? "" : arg.trim();
        String typed = args != null && args.has("value") && !args.get("value").isJsonNull()
                ? args.get("value").getAsString().trim() : "";
        UUID id = p.m_20148_();
        switch (action) {
            case ACT_NAME -> {
                String bad = DojoService.nameProblem(typed);
                if (bad != null) {
                    openName(p, bad);
                    return true;
                }
                Draft d = draft(p);
                DRAFTS.put(id, new Draft(typed, d.style(), d.base(), d.patterns()));
                openStyle(p, null);
                return true;
            }
            case ACT_STYLE -> {
                DojoCatalog.Style s = DojoCatalog.Style.of(a);
                if (s == null) {
                    openStyle(p, "Pick one of the four styles.");
                    return true;
                }
                Draft d = draft(p);
                DRAFTS.put(id, new Draft(d.name(), s.name(), d.base(), d.patterns()));
                openBanner(p, null);
                return true;
            }
            case ACT_BANNER -> {
                if (DEFAULT_BANNER.equals(a)) {
                    Draft d = draft(p);
                    DRAFTS.put(id, new Draft(d.name(), d.style(), DEFAULT_BANNER, List.of()));
                    openConfirm(p, null);
                    return true;
                }
                Banner b = bannerAt(p, a);
                if (b == null) {
                    openBanner(p, "That banner is gone. Pick one you still carry.");
                    return true;
                }
                Draft d = draft(p);
                DRAFTS.put(id, new Draft(d.name(), d.style(), b.base(), b.patterns()));
                openConfirm(p, null);
                return true;
            }
            case ACT_CONFIRM -> {
                Draft d = draft(p);
                boolean useDefault = DEFAULT_BANNER.equals(d.base());
                String msg = DojoService.found(p, d.name(), d.style(), useDefault ? "" : d.base(),
                        useDefault ? List.of() : d.patterns());
                if (DojoService.dojoOf(p) != null) {
                    DRAFTS.remove(id);
                    UltraDojoScreens.openDojoNotice(p, msg);
                } else {
                    openConfirm(p, msg);
                }
                return true;
            }
            case ACT_SET_BANNER -> {
                Banner b = bannerAt(p, a);
                String msg = b == null ? "That banner is gone. Pick one you still carry."
                        : DojoService.setBanner(p, b.base(), b.patterns());
                UltraDojoScreens.openDojoNotice(p, msg);
                return true;
            }
            case ACT_RENAME -> {
                String msg = DojoService.rename(p, typed);
                UltraDojoScreens.openDojoNotice(p, msg);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ banners in the inventory

    /** Banners the player holds (main hand, off hand) or carries, at most {@link #BANNERS_SHOWN}. Never taken. */
    static List<Banner> banners(ServerPlayer p) {
        List<Banner> out = new ArrayList<>();
        addBanner(out, "hand", p.m_21205_());
        addBanner(out, "off", p.m_21206_());
        List<ItemStack> items = p.m_150109_().f_35974_;
        int selected = p.m_150109_().f_35977_;
        for (int i = 0; i < items.size() && out.size() < BANNERS_SHOWN; i++) {
            if (i == selected) {
                continue;                                   // already listed as the banner in hand
            }
            addBanner(out, "slot:" + i, items.get(i));
        }
        return out;
    }

    /** The banner at {@code source} right now, or null (moved, dropped, not a banner). */
    static Banner bannerAt(ServerPlayer p, String source) {
        for (Banner b : banners(p)) {
            if (b.source().equals(source)) {
                return b;
            }
        }
        if (source.startsWith("slot:")) {
            try {
                int i = Integer.parseInt(source.substring(5));
                List<ItemStack> items = p.m_150109_().f_35974_;
                if (i >= 0 && i < items.size()) {
                    List<Banner> one = new ArrayList<>();
                    addBanner(one, source, items.get(i));
                    return one.isEmpty() ? null : one.get(0);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void addBanner(List<Banner> out, String source, ItemStack stack) {
        String base = baseColour(stack);
        if (base == null) {
            return;
        }
        List<String> patterns = new ArrayList<>();
        CompoundTag be = stack.m_41737_("BlockEntityTag");
        if (be != null && be.m_128441_("Patterns")) {
            ListTag list = be.m_128437_("Patterns", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag pt = list.m_128728_(i);
                patterns.add(pt.m_128461_("Pattern") + ":" + pt.m_128451_("Color"));
            }
        }
        out.add(new Banner(source, base, patterns));
    }

    /** {@code red} for a red banner, or null when the stack is not a vanilla banner. */
    static String baseColour(ItemStack stack) {
        if (stack == null || stack.m_41619_()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.m_41720_());
        if (key == null || !"minecraft".equals(key.m_135827_()) || !key.m_135815_().endsWith("_banner")
                || key.m_135815_().endsWith("_wall_banner")) {
            return null;
        }
        return key.m_135815_().substring(0, key.m_135815_().length() - "_banner".length());
    }

    /** "Red, 3 patterns" / "Light blue, plain". */
    static String describe(String base, List<String> patterns) {
        String b = base == null || base.isBlank() ? "Banner" : base.replace('_', ' ');
        b = Character.toUpperCase(b.charAt(0)) + b.substring(1);
        int n = patterns == null ? 0 : patterns.size();
        return b + ", " + (n == 0 ? "plain" : n == 1 ? "1 pattern" : n + " patterns");
    }
}
