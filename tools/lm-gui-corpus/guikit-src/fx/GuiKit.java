import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * GuiKit - HudKit grown to whole screens (mod-toolkit, 2026-11-22; TOOLS_TO_BUILD 13): a layout file becomes mockups in
 * Minecraft's own font at real window sizes and GUI scales 2, 3 and Auto (what Minecraft starts with), checked:
 * <pre>
 *   ERROR  off the screen, out of its parent, two things on top of each other, text that doesn't fit its box, rows
 *          that don't fit their list, icons that don't fit their wheel, a missing text key / texture / glyph
 *   warn   text hard to read on what is behind it, click targets under 12 px, pictures at an uneven scale
 *
 *   GuiKit make &lt;project&gt; [-spec file] [-only regex] [-out dir]
 *   GuiKit demo &lt;out dir&gt;
 *   GuiKit measure &lt;text&gt;
 *   exit 0 fine, 1 a screen fails, 2 not done / not proven
 *
 * tools\guikit.json   (GUI pixels)
 *   {"sizes": ["1920x1080", "1280x720"], "scales": [2, 3, "auto"],
 *    "screens": [{"id": "journal", "dim": true, "backdrop": "shot.png", "elements": [
 *      {"type": "panel", "anchor": "center", "w": 320, "h": 200, "style": "parchment", "children": [
 *        {"type": "text", "keys": "quest.mymod.*.name", "anchor": "top_left", "x": 8, "y": 8, "w": 150}]}]}]}
 *
 *  position  "anchor": one of top_left top_center top_right center_left center center_right bottom_left bottom_center
 *            bottom_right, on the parent (the screen at the top); "pivot": the point of itself put there (default the same);
 *            "x", "y": from there; "w", "h": a number, "fill" (the parent less x / y on both sides) or "50%"
 *  type      panel  style vanilla dark parchment tooltip none; "scroll": true lets its children run past its bottom
 *            text   "text" | "key" | "keys" (a pattern: the LONGEST of them, the worst case), "args", "colour", "shadow",
 *                   "wrap", "maxLines", "align" left center right
 *            button (text as above, "state" hover disabled), tabs ("items", "selected"), list ("items" | "keys", "rowH",
 *            "scroll", "selected"), grid ("cols", "rows", "cell", "gap", "icon"), slot / item ("icon"), image ("texture",
 *            "uv": [x,y,w,h]), bar ("value", "colour"), figure (a player stands there), box ("colour"), line,
 *            wheel ("rings": [{"inner", "outer", "count", "icons", "selected", "colour"}], "turn")
 *  "over": true      may lie on its neighbours (overlays, decoration)
 *
 * LegacyMechanics skins (2026-11-24; LmLook.java ports LM's MenuSkins / NativeMenuScreen number for number)
 *  top       "include": ["specs/a.json", ...] more files whose "screens" join (relative to this file)
 *            "skin": saiyan_royal (default) namek_serenity obsidian_gold beerus_destruction zeno_omni; "look": full | lite
 *            "keep": ["1920x1080@2"] write pictures only for these (every size is still checked); "format": png | jpg;
 *            "sheets": false (no contact sheet per screen)
 *  screen    "skin", "look" (override the top), "background": world (the sky) | skin (the skin's themed scene) | scrim
 *  panel     "style": "lm" - rounded 14 px, trim border, gradient, two soft shadows (one in Lite), divider, accent frame.
 *            "title" (bold, trim), "crumbs": [["Main menu","go"], ..., ["This page"]], "headerBottom" (42; 28 with tabs),
 *            "accent": a system (difficulty rival spar dojo prestige character skillcheck admin) or #rrggbb, "frame": false,
 *            "fit": NativeMenuFit - native size, shrunk to the window less "margin" (10) but never under "minScale" (0.5),
 *            then the whole menu scrolls if "scroll": true; "fit": "fill" scales a design box to the window (config editor);
 *            "pad": the content edge (10). Everything inside draws in the skin:
 *  button    raised 8 px rounded face, accent edge; "kind" normal confirm danger dim locked, "active": true (the tab),
 *            "state" hover pressed, "pad" (8), "accent" (a hub tile's own system)
 *  text      body ink, colour letters through the skin (c crimson, 6 trim, f text, others vanilla, all made readable);
 *            "colour" may be text muted trim accent confirm danger
 *  inset     the info box / card (inner shadow); field ("text" or "placeholder"); bar (glossy trim in a recessed track);
 *            swatch ("skin": the Menu skins preview); colorwheel; book ("title", "text" lines, "value" = pages)
 *  LM rules  ERROR a label past its 8 px padding, "Hub", a bare "Back", "Invalid input", a raw id (a_b, ns:path, key=v),
 *            a control in the right border margin, a footer that is not "Back to X" bottom left + "Main menu" bottom right
 *            (a lone "Back to main menu" bottom right), anything under the footer, a menu too tall without "scroll"
 *            warn  a button under 80 px, padding under 8, a click target under 12 px AFTER the fit shrink, a breadcrumb
 *            that folds into "...", a field hint that is cut, skin text under 3:1 (4.5:1 on a light skin)
 * </pre>
 */
public final class GuiKit {
    private static final List<String> ANCHORS = List.of("top_left", "top_center", "top_right", "center_left", "center",
            "center_right", "bottom_left", "bottom_center", "bottom_right");
    private static final List<String> TYPES = List.of("panel", "text", "button", "tabs", "list", "grid", "slot", "item", "image",
            "bar", "figure", "box", "line", "wheel", "inset", "field", "swatch", "colorwheel", "book");
    private static final List<String> STYLES = List.of("vanilla", "dark", "parchment", "tooltip", "none", "lm");
    private static final Set<String> TOP = Set.of("sizes", "scales", "screens", "include", "skin", "look", "keep", "format", "sheets", "_comment");
    private static final Set<String> SCREEN = Set.of("id", "dim", "backdrop", "elements", "skin", "look", "background", "_comment");
    private static final Set<String> KEYS = Set.of("id", "type", "anchor", "pivot", "x", "y", "w", "h", "children", "style",
            "scroll", "text", "key", "keys", "args", "colour", "shadow", "wrap", "maxLines", "align", "state", "items",
            "selected", "rowH", "cols", "rows", "cell", "gap", "icon", "texture", "uv", "value", "rings", "turn", "over", "_comment",
            "accent", "title", "titleAlign", "headerBottom", "crumbs", "frame", "fit", "margin", "kind", "active", "pad",
            "placeholder", "skin", "action", "radius", "minScale");
    private static final Set<String> RING = Set.of("inner", "outer", "count", "icons", "selected", "colour", "_comment");
    private static final List<String> SIZES = List.of("1920x1080", "2560x1440", "1366x768", "1280x720", "854x480");
    private static final List<Object> SCALES = List.of(2.0, 3.0, "auto");
    private static final Pattern HOLE = Pattern.compile("%%|%(?:(\\d)\\$)?[sd]");

    private final File project, out, assets, install;
    private final McFont font;
    private final Map<String, String> lang = new HashMap<>();
    private final Map<String, BufferedImage> pictures = new HashMap<>();
    private final List<String> broken = new ArrayList<>();
    private final Map<String, Set<String>> errors = new LinkedHashMap<>(), warnings = new LinkedHashMap<>();
    private final BufferedImage widgets;
    private String where = "";
    /** LM skin of the screen being made (top-level / screen "skin"), Full or Lite ("look"). */
    private LmLook.Skin skin = LmLook.SAIYAN_ROYAL;
    private boolean lite;
    private final Map<String, int[]> baked = new HashMap<>();
    private static final List<String> KINDS = List.of("normal", "confirm", "danger", "dim", "locked");

    private static final class El {
        final Map<String, Object> raw;
        final String name, type;
        final List<El> children = new ArrayList<>();
        double x, y, w, h;
        List<String> lines = List.of();
        /** LM: inside an "lm" panel; that panel's accent; the design transform to GUI px (GUI = ox + x * k). */
        boolean lm;
        int accent;
        double ox, oy, k = 1;
        boolean tooTall, inScroll;

        El(Map<String, Object> raw, String name, String type) {
            this.raw = raw;
            this.name = name;
            this.type = type;
        }
    }

    private GuiKit(File project, File out, File assets, File install) throws Exception {
        this.project = project;
        this.out = out;
        this.assets = assets;
        this.install = install;
        font = McFont.load(install);
        widgets = vanilla("textures/gui/widgets.png");
        File langs = assets;
        if (langs.isDirectory()) {
            File[] spaces = langs.listFiles(File::isDirectory);
            for (File ns : spaces == null ? new File[0] : spaces) {
                File f = new File(ns, "lang/en_us.json");
                if (!f.isFile()) continue;
                DataKit.map(new AssetCheck.SJ(DataKit.read(f)).parse()).forEach((k, v) -> lang.put(k, DataKit.str(v)));
            }
        }
    }

    private BufferedImage vanilla(String path) throws Exception {
        try (ZipFile z = new ZipFile(new File(install, "versions/1.20.1/1.20.1.jar"))) {
            ZipEntry e = z.getEntry("assets/minecraft/" + path);
            return e == null ? null : ImageIO.read(new ByteArrayInputStream(z.getInputStream(e).readAllBytes()));
        }
    }

    private void error(String what) { errors.computeIfAbsent(what, k -> new TreeSet<>()).add(where); }

    private void warn(String what) { warnings.computeIfAbsent(what, k -> new TreeSet<>()).add(where); }

    // ================================================================== reading a layout
    private static String str(Map<String, Object> m, String key, String otherwise) {
        return m.get(key) == null ? otherwise : DataKit.str(m.get(key));
    }

    private static double num(Map<String, Object> m, String key, double otherwise) {
        return m.get(key) instanceof Number n ? n.doubleValue() : otherwise;
    }

    private static int colour(Map<String, Object> m, String key, int otherwise) {
        String s = str(m, key, "");
        if (s.matches("#[0-9a-fA-F]{6}")) return 0xFF000000 | Integer.parseInt(s.substring(1), 16);
        if (s.matches("#[0-9a-fA-F]{8}")) return (int) Long.parseLong(s.substring(1), 16);
        return otherwise;
    }

    private El read(Map<String, Object> m, String at, int n) {
        String type = str(m, "type", "box");
        El e = new El(m, at + "/" + str(m, "id", type + n), type);
        for (String k : m.keySet()) if (!KEYS.contains(k)) broken.add(e.name + ": unknown key \"" + k + "\"" + DataKit.nearest(k, KEYS));
        if (!TYPES.contains(type)) broken.add(e.name + ": no type \"" + type + "\"" + DataKit.nearest(type, TYPES));
        for (String k : List.of("anchor", "pivot"))
            if (m.get(k) != null && !ANCHORS.contains(str(m, k, ""))) broken.add(e.name + ": no " + k + " \"" + str(m, k, "") + "\"" + DataKit.nearest(str(m, k, ""), ANCHORS));
        for (String k : List.of("x", "y", "rowH", "cols", "rows", "cell", "gap", "maxLines", "value", "turn", "selected", "pad", "margin", "headerBottom", "radius", "minScale"))
            if (m.get(k) != null && !(m.get(k) instanceof Number)) broken.add(e.name + ": \"" + k + "\" is a number");
        for (String k : List.of("w", "h")) {
            Object v = m.get(k);
            if (v != null && !(v instanceof Number n2 && n2.doubleValue() > 0) && !DataKit.str(v).equals("fill") && !DataKit.str(v).matches("\\d{1,3}(\\.\\d+)?%"))
                broken.add(e.name + ": \"" + k + "\" is a number above 0, \"fill\" or \"50%\"");
        }
        if (m.get("style") != null && !STYLES.contains(str(m, "style", ""))) broken.add(e.name + ": no style \"" + str(m, "style", "") + "\"" + DataKit.nearest(str(m, "style", ""), STYLES));
        if (m.get("colour") != null && !str(m, "colour", "").matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})|text|muted|trim|accent|confirm|danger"))
            broken.add(e.name + ": \"colour\" is #rrggbb, #aarrggbb or (lm) text muted trim accent confirm danger");
        if (m.get("kind") != null && !KINDS.contains(str(m, "kind", ""))) broken.add(e.name + ": no kind \"" + str(m, "kind", "") + "\"" + DataKit.nearest(str(m, "kind", ""), KINDS));
        if (m.get("skin") != null && LmLook.byId(str(m, "skin", "")) == null) broken.add(e.name + ": no skin \"" + str(m, "skin", "") + "\"" + DataKit.nearest(str(m, "skin", ""), LmLook.IDS));
        if (m.get("accent") != null && LmLook.accent(str(m, "accent", ""), LmLook.SAIYAN_ROYAL) < 0) broken.add(e.name + ": no accent \"" + str(m, "accent", "") + "\" (a system name or #rrggbb)");
        if (type.equals("swatch") && m.get("skin") == null) broken.add(e.name + ": needs \"skin\"");
        for (Object c : DataKit.list(m.get("crumbs"))) if (DataKit.list(c).isEmpty() || DataKit.list(c).size() > 2) broken.add(e.name + ": a crumb is [\"Title\"] or [\"Title\", \"link\"]");
        if (Set.of("text", "button").contains(type) && m.get("text") == null && m.get("key") == null && m.get("keys") == null)
            broken.add(e.name + ": needs \"text\", \"key\" or \"keys\"");
        if (Set.of("tabs", "list").contains(type) && m.get("items") == null && m.get("keys") == null) broken.add(e.name + ": needs \"items\" or \"keys\"");
        if (type.equals("image") && m.get("texture") == null) broken.add(e.name + ": needs \"texture\"");
        if (type.equals("wheel")) {
            if (DataKit.list(m.get("rings")).isEmpty()) broken.add(e.name + ": needs \"rings\"");
            for (Object o : DataKit.list(m.get("rings"))) {
                Map<String, Object> r = DataKit.map(o);
                for (String k : r.keySet()) if (!RING.contains(k)) broken.add(e.name + ": unknown ring key \"" + k + "\"" + DataKit.nearest(k, RING));
                if (num(r, "outer", 0) <= num(r, "inner", 0) || num(r, "inner", 0) < 0) broken.add(e.name + ": a ring needs \"inner\" < \"outer\"");
            }
        }
        int i = 0;
        for (Object c : DataKit.list(m.get("children"))) e.children.add(read(DataKit.map(c), e.name, ++i));
        return e;
    }

    private String textOf(El e) {
        Map<String, Object> m = e.raw;
        String s;
        if (m.get("key") != null) {
            String k = str(m, "key", "");
            s = lang.get(k);
            if (s == null) {
                error(e.name + ": no text for the key " + k);
                s = k;
            }
        } else if (m.get("keys") != null) {
            s = null;
            for (String one : keysOf(e)) if (s == null || font.width(one) > font.width(s)) s = one;
            if (s == null) s = "";
        } else s = str(m, "text", "");
        List<Object> args = DataKit.list(m.get("args"));
        Matcher holes = HOLE.matcher(s);
        StringBuilder out = new StringBuilder();
        int next = 0;
        while (holes.find()) {
            if (holes.group().equals("%%")) {
                holes.appendReplacement(out, "%");
                continue;
            }
            int which = holes.group(1) != null ? Integer.parseInt(holes.group(1)) - 1 : next++;
            String put;
            if (which >= 0 && which < args.size()) put = DataKit.str(args.get(which));
            else {
                warn(e.name + ": give \"args\" (a worst case) for the text's " + holes.group());
                put = "999";
            }
            holes.appendReplacement(out, Matcher.quoteReplacement(put));
        }
        holes.appendTail(out);
        return out.toString();
    }

    private List<String> keysOf(El e) {
        String pattern = str(e.raw, "keys", "");
        Pattern rx = DataKit.glob(pattern);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> l : new TreeMap<>(lang).entrySet()) if (rx.matcher(l.getKey()).matches()) out.add(l.getValue());
        if (out.isEmpty()) error(e.name + ": no text key matches " + pattern);
        return out;
    }

    private List<String> itemsOf(El e) {
        if (e.raw.get("items") == null) return keysOf(e);
        List<String> out = new ArrayList<>();
        for (Object o : DataKit.list(e.raw.get("items"))) out.add(DataKit.str(o));
        return out;
    }

    // ================================================================== placing
    private static double part(String anchor, boolean across) {
        if (anchor.equals("center")) return 0.5;
        if (across) return anchor.endsWith("left") ? 0 : anchor.endsWith("right") ? 1 : 0.5;
        return anchor.startsWith("top") ? 0 : anchor.startsWith("bottom") ? 1 : 0.5;
    }

    private static int shownLines(El e) {
        return (int) Math.min(e.lines.size(), Math.max(1, num(e.raw, "maxLines", 999)));
    }

    private static double length(Object v, double parent, double offset, double otherwise) {
        if (v == null) return otherwise;
        if (v instanceof Number n) return n.doubleValue();
        String s = DataKit.str(v);
        if (s.equals("fill")) return parent - 2 * Math.abs(offset);
        return Math.floor(parent * Double.parseDouble(s.substring(0, s.length() - 1)) / 100);
    }

    private static boolean lmPanel(El e) { return e.type.equals("panel") && "lm".equals(str(e.raw, "style", "")); }

    private static double pad(El e) { return num(e.raw, "pad", 8); }

    private void place(El e, El parent, double px, double py, double pw, double ph) {
        Map<String, Object> m = e.raw;
        double ox = num(m, "x", 0), oy = num(m, "y", 0), w = 16, h = 16;
        e.lm = lmPanel(e) || parent != null && parent.lm;
        e.accent = m.get("accent") != null ? LmLook.accent(str(m, "accent", ""), skin) : parent != null && parent.lm ? parent.accent : skin.trim();
        if (parent != null) {
            e.ox = parent.ox;
            e.oy = parent.oy;
            e.k = parent.k;
            e.inScroll = parent.inScroll;
        }
        if (lmPanel(e) && parent == null && !Boolean.FALSE.equals(m.get("fit"))) {
            // LM's NativeMenuFit: native size when it fits the window less the margin, else scaled down (never under 0.5x),
            // centred; only a panel still too tall at 0.5x scrolls (the whole menu).
            e.w = num(m, "w", 426);
            e.h = num(m, "h", 240);
            double margin = num(m, "margin", 10), aw = Math.max(1, pw - 2 * margin), ah = Math.max(1, ph - 2 * margin);
            // "fit": "fill" is a fillScreen design box (the config editor): scaled to the window, up or down
            boolean fill = "fill".equals(str(m, "fit", ""));
            double floor = num(m, "minScale", 0.5);                        // LM's NativeMenuFit.MIN_SCALE
            double k = fill ? Math.min(aw / e.w, ah / e.h) : Math.max(floor, Math.min(1, Math.min(aw / e.w, ah / e.h)));
            e.k = k;
            e.ox = (pw - e.w * k) / 2;
            e.tooTall = e.h * k > ah + 0.01;
            e.oy = e.tooTall ? margin : (ph - e.h * k) / 2;
            e.inScroll = e.tooTall && Boolean.TRUE.equals(m.get("scroll"));   // the whole menu scrolls (the top is shown)
            e.x = 0;
            e.y = 0;
            for (El c : e.children) place(c, e, 0, 0, e.w, e.h);
            return;
        }
        switch (e.type) {
            case "text" -> {
                String s = textOf(e);
                boolean wrap = Boolean.TRUE.equals(m.get("wrap"));
                double room = length(m.get("w"), pw, ox, pw);
                e.lines = wrap ? font.wrap(s, (int) room) : List.of(s.split("\n", -1));
                w = 0;
                for (String l : e.lines) w = Math.max(w, font.width(l));
                h = shownLines(e) * McFont.LINE;
            }
            case "button" -> {
                e.lines = List.of(textOf(e));
                w = font.width(e.lines.get(0)) + (e.lm ? 2 * pad(e) : 16);
                h = e.lm ? 16 : 20;
            }
            case "field" -> {
                e.lines = List.of(m.get("text") == null && m.get("key") == null && m.get("keys") == null ? "" : textOf(e));
                w = pw - 2 * Math.abs(ox);
                h = 16;
            }
            case "tabs" -> {
                e.lines = itemsOf(e);
                w = 0;
                for (String s : e.lines) w += font.width(s) + 12;
                h = 16;
            }
            case "list" -> {
                e.lines = itemsOf(e);
                w = 0;
                for (String s : e.lines) w = Math.max(w, font.width(s) + 12);
                h = e.lines.size() * num(m, "rowH", 12) + 2;
            }
            case "grid" -> {
                double cell = num(m, "cell", 18), gap = num(m, "gap", 0), cols = num(m, "cols", 9), rows = num(m, "rows", 1);
                w = cols * cell + (cols - 1) * gap;
                h = rows * cell + (rows - 1) * gap;
            }
            case "slot" -> {
                w = 18;
                h = 18;
            }
            case "bar" -> {
                w = 100;
                h = e.lm ? 8 : 5;
            }
            case "figure" -> {
                w = 49;
                h = 70;
            }
            case "line" -> {
                w = pw - 2 * Math.abs(ox);
                h = 1;
            }
            case "panel", "box", "inset", "swatch", "colorwheel", "book" -> {
                w = pw;
                h = ph;
            }
            case "image" -> {
                BufferedImage t = picture(e, m.get("texture"));
                List<Object> uv = DataKit.list(m.get("uv"));
                if (uv.size() == 4 && uv.get(2) instanceof Number a && uv.get(3) instanceof Number b) {
                    w = a.doubleValue();
                    h = b.doubleValue();
                } else if (t != null) {
                    w = t.getWidth();
                    h = t.getHeight();
                }
            }
            case "wheel" -> {
                for (Object o : DataKit.list(m.get("rings"))) w = Math.max(w, 2 * num(DataKit.map(o), "outer", 0));
                h = w;
            }
            default -> { }
        }
        e.w = length(m.get("w"), pw, ox, w);
        e.h = length(m.get("h"), ph, oy, h);
        String anchor = str(m, "anchor", "top_left"), pivot = str(m, "pivot", anchor);
        e.x = Math.floor(px + pw * part(anchor, true) + ox - e.w * part(pivot, true));       // the game draws on whole GUI pixels
        e.y = Math.floor(py + ph * part(anchor, false) + oy - e.h * part(pivot, false));
        for (El c : e.children) place(c, e, e.x, e.y, e.w, e.h);
    }

    // ================================================================== checking
    private void check(El e, El parent, int sw, int sh) {
        boolean scrolled = parent != null && Boolean.TRUE.equals(parent.raw.get("scroll")) || e.inScroll;
        double gx = e.ox + e.x * e.k, gy = e.oy + e.y * e.k, gw = e.w * e.k, gh = e.h * e.k;     // where it is in GUI px
        if (e.tooTall && !e.inScroll)
            error(e.name + ": " + (int) e.h + " px tall: taller than the window even at " + num(e.raw, "minScale", 0.5) + "x (give it \"scroll\": true if the whole menu may scroll)");
        else if (gx < -0.01 || gx + gw > sw + 0.01 || !scrolled && (gy < -0.01 || gy + gh > sh + 0.01)) error(e.name + ": off the screen");
        else if (parent != null && (e.x < parent.x - 0.01 || e.x + e.w > parent.x + parent.w + 0.01
                || !scrolled && (e.y < parent.y - 0.01 || e.y + e.h > parent.y + parent.h + 0.01)))
            error(e.name + ": reaches out of " + parent.name);
        switch (e.type) {
            case "text" -> {
                if (e.lm) {                                                   // LM player text: no raw ids (CLAUDE.md)
                    for (String l : e.lines) {
                        Matcher id = RAW_ID.matcher(plain(l).replaceAll("/[a-z]+( [a-z]+)*", ""));
                        if (id.find()) error(e.name + ": \"" + plain(l) + "\" shows a raw id (" + id.group() + ")");
                    }
                }
                for (String l : e.lines) {
                    if (font.width(l) > e.w + 0.01) {
                        error(e.name + ": \"" + plain(l) + "\" is wider than its box (" + font.width(l) + " / " + (int) e.w + ")");
                        break;
                    }
                }
                if (e.lines.size() > num(e.raw, "maxLines", 999)) error(e.name + ": " + e.lines.size() + " lines, room for " + (int) num(e.raw, "maxLines", 999));
                else if (e.lines.size() * McFont.LINE - 1 > e.h) error(e.name + ": " + e.lines.size() + " lines don't fit its height");
            }
            case "button" -> {
                if (e.lm) lmButton(e);
                else if (font.width(e.lines.get(0)) > e.w - 4) error(e.name + ": \"" + plain(e.lines.get(0)) + "\" doesn't fit the button");
                if (gw < 12 || gh < 12) warn(e.name + ": under 12 px to click" + (e.k < 1 ? String.format(Locale.ROOT, " (%.1f px: the menu is shrunk to %.2fx)", Math.min(gw, gh), e.k) : ""));
            }
            case "field" -> {
                double room = e.w - 8;
                if (font.width(e.lines.get(0)) > room) error(e.name + ": \"" + plain(e.lines.get(0)) + "\" doesn't fit the field");
                String hint = str(e.raw, "placeholder", "");
                if (font.width(hint) > room) warn(e.name + ": the hint \"" + plain(hint) + "\" is cut to the field (" + font.width(hint) + " / " + (int) room + ")");
                if (gh < 12) warn(e.name + ": under 12 px to click");
            }
            case "panel" -> {
                if (lmPanel(e)) lmPanelCheck(e);
            }
            case "tabs" -> {
                double room = e.w / Math.max(1, e.lines.size()) - 4;
                for (String s : e.lines) if (font.width(s) > room) error(e.name + ": the tab \"" + plain(s) + "\" doesn't fit");
                if (e.h < 12 || room < 12) warn(e.name + ": under 12 px to click");
            }
            case "list" -> {
                double row = num(e.raw, "rowH", 12);
                int seen = (int) Math.floor((e.h - 1) / row);
                if (e.lines.size() > seen && !Boolean.TRUE.equals(e.raw.get("scroll"))) error(e.name + ": " + e.lines.size() + " rows, room for " + seen + " (no \"scroll\")");
                if (row < 9) error(e.name + ": rows lower than the font");
                for (String s : e.lines) {
                    if (font.width(s) > e.w - 6) {
                        error(e.name + ": the row \"" + plain(s) + "\" is too wide");
                        break;
                    }
                }
            }
            case "grid" -> {
                if (num(e.raw, "cell", 18) * e.k < 12) warn(e.name + ": under 12 px to click");
            }
            case "slot" -> {
                if (gw < 12 || gh < 12) warn(e.name + ": under 12 px to click");
            }
            case "wheel" -> {
                int n = 0;
                for (Object o : DataKit.list(e.raw.get("rings"))) {
                    Map<String, Object> r = DataKit.map(o);
                    n++;
                    double in = num(r, "inner", 0), out = num(r, "outer", 0), count = num(r, "count", 0);
                    if (count < 1) continue;
                    if (out - in < 16) error(e.name + ": ring " + n + " is thinner than an icon");
                    else if (count > 1 && 2 * (in + out) / 2 * Math.sin(Math.PI / count) < 18) error(e.name + ": " + (int) count + " icons don't fit on ring " + n);
                }
            }
            case "image" -> {
                BufferedImage t = picture(e, e.raw.get("texture"));
                if (t != null) {
                    List<Object> uv = DataKit.list(e.raw.get("uv"));
                    double sw2 = uv.size() == 4 && uv.get(2) instanceof Number n ? n.doubleValue() : t.getWidth();
                    double kx = e.w / sw2;
                    if (Math.abs(kx - Math.rint(kx)) > 0.01) warn(e.name + ": drawn at " + String.format(Locale.ROOT, "%.2f", kx) + "x (uneven pixels)");
                }
            }
            default -> { }
        }
        siblings(e.children);
        for (El c : e.children) check(c, e, sw, sh);
    }

    // ================================================================== LM rules (LegacyMechanics CLAUDE.md, CNPC_GUI_MENU_RULES)
    private static final Pattern RAW_ID = Pattern.compile("\\b[a-z0-9]+_[a-z0-9_]+\\b|\\b[a-z]+:[a-z_]+\\b|\\b[a-z]+=[A-Za-z0-9]");

    /** An LM button: the label fits inside its 8 px padding (the client cuts it otherwise), 80 px wide, friendly words. */
    private void lmButton(El e) {
        String label = plain(e.lines.get(0));
        double room = e.w - 2 * pad(e) - (e.raw.get("icon") != null ? 12 : 0);
        int tw = font.width(e.lines.get(0));
        if (tw > room) error(e.name + ": \"" + label + "\" doesn't fit the button inside its " + (int) pad(e) + " px padding (" + tw + " / " + (int) room + ")");
        if (e.w < 80) warn(e.name + ": " + (int) e.w + " px wide, under LM's 80 px button minimum");
        if (pad(e) < 8) warn(e.name + ": " + (int) pad(e) + " px padding, under LM's 8 px");
        lmWords(e, label);
    }

    private void lmWords(El e, String label) {
        if (label.matches("(?i).*\\bhub\\b.*")) error(e.name + ": \"" + label + "\" says Hub (LM says \"Main menu\" / \"Back to X\")");
        if (label.equalsIgnoreCase("back") || label.matches("(?i)back\\b(?! to ).*")) error(e.name + ": \"" + label + "\" is a bare Back (say \"Back to X\")");
        if (label.matches("(?i).*invalid input.*")) error(e.name + ": \"" + label + "\" is a bare \"Invalid input\"");
        Matcher id = RAW_ID.matcher(label);
        if (id.find()) error(e.name + ": \"" + label + "\" shows a raw id (" + id.group() + ")");
    }

    /**
     * An LM panel: the title and breadcrumb fit, and the footer follows UltraScreenSpec.back(): "Back to X" bottom left
     * with "Main menu" bottom right on one row, a lone "Back to main menu" bottom right, nothing under the footer.
     */
    private void lmPanelCheck(El p) {
        String title = str(p.raw, "title", "");
        int room = (int) p.w - 2 * LmLook.EDGE;
        if (font.width("§l" + title) > room)
            error(p.name + ": the title \"" + plain(title) + "\" is wider than the panel (" + font.width("§l" + title) + " / " + room + ")");
        lmWords(p, plain(title));
        List<Object> crumbs = DataKit.list(p.raw.get("crumbs"));
        if (!crumbs.isEmpty()) {
            int w = 0;
            for (int i = 0; i < crumbs.size(); i++) w += font.width(DataKit.str(DataKit.list(crumbs.get(i)).get(0))) + (i > 0 ? font.width(" > ") : 0);
            if (w > room) warn(p.name + ": the breadcrumb folds into \"...\" (" + w + " / " + room + ")");
        }
        for (El c : p.children)                                           // controls keep EDGE clear of the right border
            if ((c.type.equals("button") || c.type.equals("field")) && c.x + c.w > p.w - num(p.raw, "pad", LmLook.EDGE) + 0.01)
                error(p.name + ": \"" + (c.lines.isEmpty() ? c.name : plain(c.lines.get(0))) + "\" ends at " + (int) (c.x + c.w)
                        + ", inside the " + (int) num(p.raw, "pad", LmLook.EDGE) + " px margin of the panel's right border ("
                        + (int) (p.w - num(p.raw, "pad", LmLook.EDGE)) + ")");
        El back = null;
        List<El> buttons = new ArrayList<>();
        for (El c : p.children) {
            if (!c.type.equals("button") || plain(c.lines.get(0)).equals("Close")) continue;   // Close: the client's own, in the bottom band
            buttons.add(c);
            if (plain(c.lines.get(0)).startsWith("Back to ")) back = c;
        }
        double right = p.w - LmLook.EDGE;
        El partner = null;
        for (El c : buttons) if (back != null && c != back && c.y == back.y && Math.abs(c.x + c.w - right) < 0.5) partner = c;
        for (El c : buttons)
            if (plain(c.lines.get(0)).equals("Main menu") && (back == null || c != partner))
                error(p.name + ": \"Main menu\" is not bottom right beside a \"Back to X\"");
        if (back != null) {
            String l = plain(back.lines.get(0));
            if (partner == null) {
                if (!l.equals("Back to main menu")) error(p.name + ": \"" + l + "\" alone, with nothing on its right (only a system Home has one footer button)");
                else if (Math.abs(back.x + back.w - right) > 0.5) error(p.name + ": the lone \"" + l + "\" is not at the bottom right like every system Home's");
            } else if (Math.abs(back.x - LmLook.EDGE) > 0.5) error(p.name + ": \"" + l + "\" is not at the bottom left");
            for (El c : buttons)
                if (c != back && c != partner && c.y >= back.y) error(p.name + ": \"" + plain(c.lines.get(0)) + "\" sits on or under the Back footer row");
        }
    }

    private void siblings(List<El> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                El a = list.get(i), b = list.get(j);
                if (a.type.equals("line") || b.type.equals("line") || Boolean.TRUE.equals(a.raw.get("over")) || Boolean.TRUE.equals(b.raw.get("over"))) continue;
                if (a.x < b.x + b.w - 0.01 && b.x < a.x + a.w - 0.01 && a.y < b.y + b.h - 0.01 && b.y < a.y + a.h - 0.01)
                    error(a.name + " and " + b.name.substring(b.name.lastIndexOf('/') + 1) + " are on top of each other");
            }
        }
    }

    private static String plain(String s) {
        String p = s.replaceAll("§.", "");
        return p.length() > 48 ? p.substring(0, 45) + "..." : p;
    }

    // ================================================================== drawing
    private BufferedImage picture(El e, Object name) {
        if (name == null) return null;
        String id = DataKit.str(name);
        if (pictures.containsKey(id)) {
            BufferedImage had = pictures.get(id);
            if (had == null) error(e.name + ": no texture " + id);
            return had;
        }
        BufferedImage img = null;
        try {
            int c = id.indexOf(':');
            if (c > 1 && !id.contains("..")) {                             // ns:path
                File f = new File(assets, id.substring(0, c) + "/" + id.substring(c + 1));
                if (f.isFile()) img = ImageIO.read(f);
                else if (id.startsWith("minecraft:")) img = vanilla(id.substring(10));
            } else {
                File f = new File(id).isAbsolute() ? new File(id) : new File(project, id);
                if (f.isFile()) img = ImageIO.read(f);
            }
            if (img != null && img.getHeight() > img.getWidth() && img.getHeight() % img.getWidth() == 0 && e.type.equals("item"))
                img = img.getSubimage(0, 0, img.getWidth(), img.getWidth());
        } catch (Exception bad) {
            img = null;
        }
        pictures.put(id, img);
        if (img == null) error(e.name + ": no texture " + id);
        return img;
    }

    private void bevel(GuiPaint g, double x, double y, double w, double h, int fill, int light, int dark) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w - 1, y + 1, light);
        g.fill(x, y, x + 1, y + h - 1, light);
        g.fill(x + 1, y + h - 1, x + w, y + h, dark);
        g.fill(x + w - 1, y + 1, x + w, y + h, dark);
    }

    private void icon(GuiPaint g, El e, Object name, double x, double y) {
        BufferedImage t = picture(e, name);
        if (t != null) g.image(t, 0, 0, t.getWidth(), t.getHeight(), x, y, 16, 16, 0xFFFFFFFF);
    }

    // ================================================================== LM drawing (client/NativeMenuScreen.java, number for number)
    private static int A(int rgb) { return 0xFF000000 | rgb; }

    private int tone(El e, String name, int otherwise) {
        if (name == null) return otherwise;
        return switch (name) {
            case "text" -> A(skin.text());
            case "muted" -> A(skin.muted());
            case "trim" -> A(skin.trim());
            case "accent" -> A(e.accent);
            case "confirm" -> A(skin.confirm());
            case "danger" -> A(LmLook.DANGER);
            default -> colour(e.raw, "colour", otherwise);
        };
    }

    private void shadowRings(GuiPaint g, double x, double y, double w, double h, double r, int rings, int drop, int alpha) {
        for (int i = rings; i >= 1; i--) g.round(x - i, y - i + drop, w + 2 * i, h + 2 * i, r + i, LmLook.ringAlpha(alpha, rings, i) << 24);
    }

    private void glowRings(GuiPaint g, double x, double y, double w, double h, double r, int rgb, int rings, int alpha) {
        for (int i = rings; i >= 1; i--) g.round(x - i, y - i, w + 2 * i, h + 2 * i, r + i, LmLook.ringAlpha(alpha, rings, i) << 24 | rgb & 0xFFFFFF);
    }

    private void insetBox(GuiPaint g, double x, double y, double w, double h, double r, int edge, int fill) {
        g.round(x, y, w, h, r, A(edge));
        g.round(x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), A(fill));
        double rows = Math.min(LmLook.INSET_SHADOW_PX, Math.max(1, Math.floor((h - 2) / 2)));
        g.round(x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), skin.insetShadowAlpha() << 24, 0, rows);
    }

    private void glossy(GuiPaint g, double x, double y, double w, double h, double r, int rgb) {
        g.round(x, y, w, h, r, A(LmLook.blend(rgb, LmLook.WHITE, 0.35)), A(LmLook.blend(rgb, LmLook.BLACK, 0.25)), h);
        double rr = LmLook.radiusFor(w, h, r), gy = y + Math.max(1, Math.floor(h / 3) - 1);
        if (h >= 4 && w > 2 * rr + 2) g.fill(x + rr, gy, x + w - rr, gy + 1, LmLook.GLOSS_LINE);
    }

    /** Text with its colour letters mapped through the skin (MenuSkins.ink): c crimson, 6 trim, f text, others vanilla, made readable. */
    private double lmText(GuiPaint g, El e, String s, double x, double y, int base, boolean shade, boolean muted, boolean check) {
        int bg = g.behind(x, y, x + Math.max(1, font.width(s)), y + 8);
        if (!muted) base = A(LmLook.readable(base & 0xFFFFFF, bg, skin.dark() ? 3.0 : 4.5));   // NativeText.parse: the base ink too
        int col = base;
        boolean bold = false;
        StringBuilder seg = new StringBuilder();
        List<Object[]> parts = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                char f = Character.toLowerCase(s.charAt(++i));
                if ("0123456789abcdefr".indexOf(f) >= 0) {
                    if (seg.length() > 0) parts.add(new Object[]{col, (bold ? "§l" : "") + seg});
                    seg.setLength(0);
                    col = f == 'r' || muted ? base : A(LmLook.ink(skin, f, bg));
                    bold = false;
                } else if (f == 'l') {
                    if (seg.length() > 0) parts.add(new Object[]{col, (bold ? "§l" : "") + seg});
                    seg.setLength(0);
                    bold = true;
                }
                continue;
            }
            seg.append(c);
        }
        if (seg.length() > 0) parts.add(new Object[]{col, (bold ? "§l" : "") + seg});
        for (Object[] p : parts) {
            String t = (String) p[1];
            int c = (int) p[0];
            font.draw(g, t, x, y, c, shade);
            double need = skin.dark() ? 3.0 : 4.5;
            if (check && !plain(t).isBlank() && LmLook.contrast(c & 0xFFFFFF, bg) < need)
                warn(e.name + ": \"" + plain(t) + "\" hard to read on what is behind it (" + String.format(Locale.ROOT, "%.1f", LmLook.contrast(c & 0xFFFFFF, bg)) + ":1)");
            x += font.width(t);
        }
        return x;
    }

    private void drawLmPanel(GuiPaint g, El e) {
        Map<String, Object> m = e.raw;
        double w = e.w, h = e.h, r = LmLook.PANEL_RADIUS, x = e.x, y = e.y;
        if (!lite) shadowRings(g, x, y, w, h, r, LmLook.AMBIENT_RINGS, LmLook.AMBIENT_DROP, skin.shadowAlpha(LmLook.AMBIENT_ALPHA));
        shadowRings(g, x, y, w, h, r, LmLook.KEY_RINGS, LmLook.KEY_DROP, skin.shadowAlpha(LmLook.KEY_ALPHA));
        g.round(x, y, w, h, r, A(skin.trim()));
        g.round(x + 1, y + 1, w - 2, h - 2, r - 1, A(skin.panelTop()), A(skin.panelBottom()), h - 2);
        if (!Boolean.FALSE.equals(m.get("frame"))) {
            g.round(x + 2, y + 2, w - 4, h - 4, r - 2, A(skin.frameRing(e.accent)));
            g.round(x + 3, y + 3, w - 6, h - 6, r - 3, A(LmLook.blend(skin.panelTop(), skin.panelBottom(), 2.0 / h)),
                    A(LmLook.blend(skin.panelTop(), skin.panelBottom(), 1 - 2.0 / h)), h - 6);
        }
        double headerBottom = num(m, "headerBottom", 42);
        double dividerY = Math.max(10, headerBottom - 3);
        g.fill(x + LmLook.EDGE, y + dividerY, x + w - LmLook.EDGE, y + dividerY + 1, LmLook.DIVIDER_ALPHA << 24 | skin.trim());
        String title = str(m, "title", "");
        if (!title.isEmpty()) {
            double band = Math.max(9, headerBottom - 3), ty = Math.max(3, Math.floor((band - 9) / 2));
            title = "§l" + title;                                          // drawTitle: fit(..., bold = true)
            int tw = font.width(title);
            double tx = "left".equals(str(m, "titleAlign", "center")) ? LmLook.EDGE : Math.floor((w - tw) / 2);
            lmText(g, e, title, x + tx, y + ty, A(skin.trim()), skin.textShadow(), false, true);
        }
        List<Object> crumbs = DataKit.list(m.get("crumbs"));
        if (!crumbs.isEmpty()) {
            String sep = " > ";
            int sepW = font.width(sep), max = (int) w - 2 * LmLook.EDGE, n = crumbs.size(), first = 0;
            while (first < n - 1 && crumbsWidth(crumbs, first, sepW) > max) first++;
            double total = crumbsWidth(crumbs, first, sepW) + (first > 0 ? font.width("...") + sepW : 0);
            double cx = x + Math.max(LmLook.EDGE, Math.floor((w - total) / 2)), cy = y + Math.max(12, headerBottom - 3) - 11;
            if (first > 0) {
                font.draw(g, "..." + sep, cx, cy, A(skin.fieldEdge()), false);
                cx += font.width("...") + sepW;
            }
            for (int i = first; i < n; i++) {
                String t = DataKit.str(DataKit.list(crumbs.get(i)).get(0));
                boolean link = i < n - 1;
                font.draw(g, t, cx, cy, A(link ? skin.trim() : skin.text()), false);
                cx += font.width(t);
                if (link) {
                    font.draw(g, sep, cx, cy, A(skin.fieldEdge()), false);
                    cx += sepW;
                }
            }
        }
    }

    private int crumbsWidth(List<Object> crumbs, int from, int sepW) {
        int w = 0;
        for (int i = from; i < crumbs.size(); i++) w += font.width(DataKit.str(DataKit.list(crumbs.get(i)).get(0))) + (i > from ? sepW : 0);
        return w;
    }

    /** NativeMenuScreen.drawSkinButton: rounded 8 px face, accent edge, raised with shadow, sheen and shade; sinks pressed. */
    private void drawLmButton(GuiPaint g, El e) {
        Map<String, Object> m = e.raw;
        String kind = str(m, "kind", "normal"), state = str(m, "state", "");
        boolean locked = kind.equals("locked"), flat = kind.equals("dim") || locked, hover = state.equals("hover") && !locked;
        boolean pressed = state.equals("pressed") && !locked, active = Boolean.TRUE.equals(m.get("active"));
        int tint = e.accent, face, edge;
        switch (kind) {
            case "confirm" -> { face = hover ? skin.confirmFaceHover() : skin.confirmFace(); edge = skin.confirm(); }
            case "danger" -> { face = hover ? skin.dangerFaceHover() : skin.dangerFace(); edge = LmLook.DANGER; }
            case "dim", "locked" -> { face = hover ? skin.dimFaceHover() : skin.dimFace(); edge = skin.dimEdge(); }
            default -> { face = hover ? skin.faceHover() : skin.face(); edge = hover ? tint : skin.edgeRest(tint); }
        }
        if (active) edge = tint;
        double off = flat ? 0 : pressed ? LmLook.PRESS_SINK : active ? -LmLook.TAB_LIFT : hover ? -1 : 0;
        double x = e.x, y = e.y + off, w = e.w, h = e.h, r = LmLook.BUTTON_RADIUS;
        if (active && !lite) glowRings(g, x, y, w, h, r, skin.glow(), LmLook.GLOW_RINGS, LmLook.GLOW_ALPHA);
        if (kind.equals("confirm") && !lite) glowRings(g, x, y, w, h, r, skin.glow(), 2, LmLook.GLOW_ALPHA / 2);
        boolean sunk = off > 0.5;
        if (!flat && !sunk) shadowRings(g, x, y, w, h, r, lite ? 1 : LmLook.BUTTON_SHADOW_RINGS, LmLook.BUTTON_SHADOW_DROP, skin.shadowAlpha(LmLook.BUTTON_SHADOW_ALPHA));
        g.round(x, y, w, h, r, A(edge));
        int top = A(LmLook.blend(face, LmLook.WHITE, flat ? 0 : LmLook.FACE_TOP_LIFT));
        int bottom = A(LmLook.blend(face, LmLook.BLACK, flat ? 0 : LmLook.FACE_BOTTOM_DROP));
        g.round(x + 1, y + 1, w - 2, h - 2, r - 1, sunk ? bottom : top, sunk ? top : bottom, h - 2);
        if (!flat && !sunk && !lite) {
            g.fill(x + r / 2, y + 1, x + w - r / 2, y + 2, skin.edgeHighlight());
            g.fill(x + r / 2, y + h - 2, x + w - r / 2, y + h - 1, skin.edgeShade());
        }
        double left = x + pad(e), maxW = w - 2 * pad(e);
        if (m.get("icon") != null) {
            icon(g, e, m.get("icon"), left, y + (h - 10) / 2);
            left += 12;
            maxW -= 12;
        }
        String s = e.lines.get(0);
        if (s.length() >= 2 && s.charAt(0) == '§' && LmLook.letterAccent(s.charAt(1)) == tint) s = s.substring(2);
        int tw = font.width(s);
        lmText(g, e, s, Math.floor(left + Math.max(0, (maxW - tw) / 2)), Math.floor(y + (h - 8) / 2), A(flat ? skin.muted() : skin.text()),
                !flat && skin.textShadow(), flat, true);
        if (active) g.round(x + r, y + h - 3, w - 2 * r, 2, 1, A(tint));
    }

    /** The Menu skins picker's live swatch (NativeMenuScreen.drawSwatch). */
    private void drawSwatch(GuiPaint g, El e) {
        LmLook.Skin s = LmLook.byId(str(e.raw, "skin", ""));
        double x = e.x, y = e.y, w = e.w, h = e.h;
        g.round(x, y, w, h, LmLook.BUTTON_RADIUS, A(s.bgTop()), A(s.bgBottom()), h);
        g.round(x + 3, y + 2, w - 6, h - 4, 4, A(s.trim()));
        g.round(x + 4, y + 3, w - 8, h - 6, 3, A(s.panel()));
        g.round(x + 7, y + 5, 12, 3, 1, A(s.primary()));
        g.round(x + 21, y + 5, 10, 3, 1, A(s.face()));
        g.round(x + 7, y + h - 7, 24, 3, 1, A(s.confirmFace()));
        String t = str(e.raw, "text", s == skin ? "You are using this" : "Sample text");
        font.draw(g, t, x + 36, y + Math.floor((h - 8) / 2), A(LmLook.readable(s.text(), s.panel(), s.dark() ? 3.0 : 4.5)), s.textShadow());
        if (font.width(t) > w - 42) error(e.name + ": \"" + plain(t) + "\" doesn't fit the swatch");
    }

    private void drawLm(GuiPaint g, El e) {
        Map<String, Object> m = e.raw;
        double x = e.x, y = e.y, x1 = e.x + e.w, y1 = e.y + e.h;
        switch (e.type) {
            case "panel" -> {
                if (lmPanel(e)) drawLmPanel(g, e);
                else if (!"none".equals(str(m, "style", "none"))) g.round(x, y, e.w, e.h, LmLook.BOX_RADIUS, A(skin.panel()));
            }
            case "inset" -> insetBox(g, x, y, e.w, e.h, num(m, "radius", LmLook.BOX_RADIUS), skin.insetEdge(), skin.infoFill());
            case "field" -> {
                insetBox(g, x, y, e.w, e.h, 4, Boolean.TRUE.equals(m.get("active")) ? skin.trim() : skin.fieldEdge(), skin.fieldFill());
                String v = e.lines.get(0), hint = str(m, "placeholder", "");
                double ty = Math.floor(y + (e.h - 8) / 2);
                if (!v.isEmpty()) lmText(g, e, v, x + 4, ty, A(skin.text()), true, false, true);
                else if (!hint.isEmpty()) {
                    String shown = hint;
                    if (font.width(hint) > e.w - 8) {
                        while (!shown.isEmpty() && font.width(shown + "...") > e.w - 8) shown = shown.substring(0, shown.length() - 1);
                        shown = shown.stripTrailing() + "...";
                    }
                    font.draw(g, shown, x + 4, ty, A(skin.muted()), false);
                }
            }
            case "bar" -> {
                insetBox(g, x, y, e.w, e.h, LmLook.BAR_RADIUS + 1, skin.trackRim(), skin.trackFill());
                double share = Math.max(0, Math.min(1, num(m, "value", 0.6)));
                double fw = share <= 0 ? 0 : Math.max(e.h - 2, Math.round((e.w - 2) * share));
                int fill = m.get("colour") == null ? skin.trim() : tone(e, str(m, "colour", null), A(skin.trim())) & 0xFFFFFF;
                if (fw > 0) glossy(g, x + 1, y + 1, fw, e.h - 2, LmLook.BAR_RADIUS, fill);
            }
            case "swatch" -> drawSwatch(g, e);
            case "colorwheel" -> {                              // ColorWheelTex: a hue ring, and inside it saturation x value
                double side = Math.min(e.w, e.h), c = side / 2, cx = x + (e.w - side) / 2 + c, cy = y + c, inner = c * 0.74;
                for (int i = 0; i < 72; i++)
                    g.ring(cx, cy, inner, c, Math.PI * 2 * i / 72, Math.PI * 2 * (i + 1) / 72 + 0.01, A(java.awt.Color.HSBtoRGB(i / 72f, 1f, 1f) & 0xFFFFFF));
                double sq = Math.floor(inner * Math.sqrt(2)) - 2, sx = cx - sq / 2, sy = cy - sq / 2, hue = num(m, "value", 0);
                for (int a = 0; a < 8; a++)
                    for (int b = 0; b < 8; b++)
                        g.fill(sx + sq * a / 8, sy + sq * b / 8, sx + sq * (a + 1) / 8, sy + sq * (b + 1) / 8,
                                A(java.awt.Color.HSBtoRGB((float) hue, (a + 0.5f) / 8, 1 - (b + 0.5f) / 8) & 0xFFFFFF));
            }
            case "book" -> {                                    // NativeMenuScreen.drawBook: inset page, "Title  (1/2)" in trim
                insetBox(g, x, y, e.w, e.h, LmLook.BUTTON_RADIUS, skin.fieldEdge(), skin.bookFill());
                String t = "§l" + str(m, "title", "Page 1") + "  (1/" + (int) num(m, "value", 1) + ")";
                lmText(g, e, t, x + 6, y + 5, A(skin.trim()), false, false, true);
                if (font.width(t) > e.w - 12) error(e.name + ": the book title \"" + plain(t) + "\" doesn't fit");
                double ly = y + 18;
                for (String l : str(m, "text", "").split("\n")) {
                    if (ly + 9 > y + e.h - 22) {
                        error(e.name + ": the book page has more lines than room");
                        break;
                    }
                    if (font.width(l) > e.w - 12) error(e.name + ": the book line \"" + plain(l) + "\" is wider than the page");
                    lmText(g, e, l, x + 6, ly, A(skin.text()), false, false, true);
                    ly += 13;
                }
            }
            case "button" -> drawLmButton(g, e);
            case "text" -> {
                int c = tone(e, str(m, "colour", null), A(skin.text()));
                boolean shade = m.get("shadow") instanceof Boolean b && b;          // LM body lines draw without a shadow
                String align = str(m, "align", part(str(m, "pivot", str(m, "anchor", "top_left")), true) == 0.5 ? "center" : "left");
                for (int i = 0; i < shownLines(e); i++) {
                    int w = font.width(e.lines.get(i));
                    double tx = align.equals("center") ? Math.floor(x + (e.w - w) / 2) : align.equals("right") ? x1 - w : x;
                    lmText(g, e, e.lines.get(i), tx, y + i * McFont.LINE, c, shade, false, true);
                }
            }
            case "line" -> g.fill(x, y, x1, y1, tone(e, str(m, "colour", null), LmLook.DIVIDER_ALPHA << 24 | skin.trim()));
            case "box" -> g.round(x, y, e.w, e.h, num(m, "radius", 0), tone(e, str(m, "colour", null), 0x90000000));
            case "figure" -> {
                g.round(x, y, e.w, e.h, LmLook.BOX_RADIUS, 0x33000000);
                double u = Math.floor(Math.min(e.w / 16, (e.h - 4) / 34)), ox = Math.floor(x + (e.w - 16 * u) / 2), oy = Math.floor(y1 - 2 - 32 * u);
                g.fill(ox + 4 * u, oy, ox + 12 * u, oy + 8 * u, 0xFFC8A486);
                g.fill(ox + 4 * u, oy - u, ox + 12 * u, oy + 2 * u, 0xFF201810);
                g.fill(ox + 4 * u, oy + 8 * u, ox + 12 * u, oy + 20 * u, 0xFF3B4A8C);
                g.fill(ox, oy + 8 * u, ox + 4 * u, oy + 20 * u, 0xFFC8A486);
                g.fill(ox + 12 * u, oy + 8 * u, ox + 16 * u, oy + 20 * u, 0xFFC8A486);
                g.fill(ox + 4 * u, oy + 20 * u, ox + 12 * u, oy + 32 * u, 0xFFE07020);
            }
            default -> {
                draw(g, e, A(skin.text()), false, true);
                return;
            }
        }
        boolean clip = Boolean.TRUE.equals(m.get("scroll"));
        if (clip) g.clip(x, y, x1, y1);
        for (El c : e.children) draw(g, c, A(skin.text()), false);
        if (clip) g.unclip();
    }


    private void draw(GuiPaint g, El e, int ink, boolean shadow) {
        if (e.lm && lmPanel(e) && (e.k != 1 || e.ox != 0 || e.oy != 0)) {
            g.transform(e.ox, e.oy, e.k);
            drawLm(g, e);
            g.identity();
            if (e.inScroll) {                                  // NativeMenuScreen.drawScrollBar, at scroll 0
                double margin = num(e.raw, "margin", 10), track = g.height / (double) g.scale - 2 * margin;
                double total = e.h * e.k, view = track, bar = Math.max(12, Math.round(track * view / total));
                double x = Math.min(g.width / (double) g.scale - 4, Math.round(e.ox + e.w * e.k) + 3);
                insetBox(g, x - 1, margin, 5, track, 2, skin.insetEdge(), skin.trackFill());
                glossy(g, x, margin, 3, bar, 1, skin.trim());
            }
            return;
        }
        if (e.lm) {
            drawLm(g, e);
            return;
        }
        draw(g, e, ink, shadow, false);
    }

    private void draw(GuiPaint g, El e, int ink, boolean shadow, boolean plainOnly) {
        Map<String, Object> m = e.raw;
        double x = e.x, y = e.y, x1 = e.x + e.w, y1 = e.y + e.h;
        int textInk = ink;
        boolean textShadow = shadow;
        switch (e.type) {
            case "panel" -> {
                String style = str(m, "style", "dark");
                switch (style) {
                    case "vanilla" -> {
                        g.fill(x + 1, y, x1 - 1, y1, 0xFF000000);
                        g.fill(x, y + 1, x1, y1 - 1, 0xFF000000);
                        bevel(g, x + 1, y + 1, e.w - 2, e.h - 2, colour(m, "colour", 0xFFC6C6C6), 0xFFFFFFFF, 0xFF555555);
                        textInk = 0xFF404040;
                        textShadow = false;
                    }
                    case "parchment" -> {
                        g.fill(x, y, x1, y1, 0xFF5A3C26);
                        bevel(g, x + 2, y + 2, e.w - 4, e.h - 4, colour(m, "colour", 0xFFE4D2A8), 0xFFF2E6C6, 0xFFBFA77C);
                        textInk = 0xFF3A281A;
                        textShadow = false;
                    }
                    case "tooltip" -> {
                        g.fill(x, y, x1, y1, 0xF0100010);
                        g.fill(x + 1, y + 1, x1 - 1, y + 2, 0x905000FF);
                        g.fill(x + 1, y1 - 2, x1 - 1, y1 - 1, 0x9028007F);
                        g.fill(x + 1, y + 2, x + 2, y1 - 2, 0x705000FF);
                        g.fill(x1 - 2, y + 2, x1 - 1, y1 - 2, 0x705000FF);
                        textInk = 0xFFFFFFFF;
                        textShadow = true;
                    }
                    case "none" -> { }
                    default -> {
                        g.fill(x, y, x1, y1, 0xFF3C3550);
                        g.fill(x + 1, y + 1, x1 - 1, y1 - 1, colour(m, "colour", 0xEC12101C));
                        textInk = 0xFFE8E4F0;
                        textShadow = true;
                    }
                }
            }
            case "box" -> g.fill(x, y, x1, y1, colour(m, "colour", 0x90000000));
            case "line" -> g.fill(x, y, x1, y1, colour(m, "colour", ink & 0xFFFFFF | 0x80000000));
            case "text" -> {
                int c = colour(m, "colour", ink);
                boolean shade = m.get("shadow") instanceof Boolean b ? b : shadow;
                int back = g.behind(x, y, x1, Math.min(y1, y + e.lines.size() * McFont.LINE));
                String align = str(m, "align", part(str(m, "pivot", str(m, "anchor", "top_left")), true) == 0.5 ? "center" : "left");
                for (int i = 0; i < shownLines(e); i++) {                   // past maxLines the game draws nothing
                    int w = font.width(e.lines.get(i));
                    double tx = align.equals("center") ? Math.floor(x + (e.w - w) / 2) : align.equals("right") ? x1 - w : x;
                    font.draw(g, e.lines.get(i), tx, y + i * McFont.LINE, c, shade);
                }
                // a shadow carries text over the world; without one it needs the contrast itself
                if (!e.lines.get(0).contains("§") && ratio(c & 0xFFFFFF, back) < (shade ? 1.3 : 2.5))
                    warn(e.name + ": hard to read on what is behind it");
            }
            case "button" -> {
                String state = str(m, "state", "");
                int v = state.equals("hover") ? 86 : state.equals("disabled") ? 46 : 66;
                if (widgets != null) g.slices(widgets, 0, v, 200, 20, x, y, e.w, e.h, 3);
                else bevel(g, x, y, e.w, e.h, 0xFF707070, 0xFFA0A0A0, 0xFF303030);
                String s = e.lines.get(0);
                font.draw(g, s, Math.floor(x + (e.w - font.width(s)) / 2), Math.floor(y + (e.h - 8) / 2), state.equals("disabled") ? 0xFFA0A0A0 : 0xFFFFFFFF, true);
            }
            case "tabs" -> {
                int n = Math.max(1, e.lines.size()), chosen = (int) num(m, "selected", 0);
                for (int i = 0; i < e.lines.size(); i++) {
                    double a = x + Math.floor(e.w * i / n), b = x + Math.floor(e.w * (i + 1) / n);
                    bevel(g, a, y, b - a, e.h, i == chosen ? 0xFF4A4264 : 0xFF241F34, 0xFF6C6288, 0xFF14101E);
                    String s = e.lines.get(i);
                    font.draw(g, s, Math.floor((a + b - font.width(s)) / 2), Math.floor(y + (e.h - 8) / 2), i == chosen ? 0xFFFFE08A : 0xFFD0CCD8, true);
                }
            }
            case "list" -> {
                double row = num(m, "rowH", 12);
                int chosen = (int) num(m, "selected", -1), seen = (int) Math.floor((e.h - 1) / row);
                g.fill(x, y, x1, y1, 0x30000000);
                for (int i = 0; i < Math.min(seen, e.lines.size()); i++) {
                    if (i == chosen) g.fill(x + 1, y + 1 + i * row, x1 - 1, y + 1 + (i + 1) * row, 0x50A890FF);
                    font.draw(g, e.lines.get(i), x + 3, Math.floor(y + 1 + i * row + (row - 8) / 2), ink, shadow);
                }
                if (e.lines.size() > seen && seen > 0) {
                    g.fill(x1 - 3, y + 1, x1 - 1, y1 - 1, 0x60000000);
                    g.fill(x1 - 3, y + 1, x1 - 1, y + 1 + (e.h - 2) * seen / e.lines.size(), 0xFF9088A8);
                }
            }
            case "grid" -> {
                double cell = num(m, "cell", 18), gap = num(m, "gap", 0);
                for (int r = 0; r < num(m, "rows", 1); r++) {
                    for (int c = 0; c < num(m, "cols", 9); c++) {
                        double a = x + c * (cell + gap), b = y + r * (cell + gap);
                        bevel(g, a, b, cell, cell, 0xFF8B8B8B, 0xFF373737, 0xFFFFFFFF);
                        if (m.get("icon") != null && cell >= 18) icon(g, e, m.get("icon"), a + (cell - 16) / 2, b + (cell - 16) / 2);
                    }
                }
            }
            case "slot" -> {
                bevel(g, x, y, e.w, e.h, 0xFF8B8B8B, 0xFF373737, 0xFFFFFFFF);
                icon(g, e, m.get("icon"), x + (e.w - 16) / 2, y + (e.h - 16) / 2);
            }
            case "item" -> {
                if (m.get("icon") == null) g.fill(x + 2, y + 2, x1 - 2, y1 - 2, 0xFFB09060);
                else icon(g, e, m.get("icon"), x + (e.w - 16) / 2, y + (e.h - 16) / 2);
            }
            case "image" -> {
                BufferedImage t = picture(e, m.get("texture"));
                if (t != null) {
                    double[] uv = {0, 0, t.getWidth(), t.getHeight()};
                    List<Object> given = DataKit.list(m.get("uv"));
                    if (given.size() == 4) for (int i = 0; i < 4; i++) if (given.get(i) instanceof Number n) uv[i] = n.doubleValue();
                    g.image(t, uv[0], uv[1], uv[2], uv[3], x, y, e.w, e.h, colour(m, "colour", 0xFFFFFFFF));
                }
            }
            case "bar" -> {
                g.fill(x, y, x1, y1, 0xFF000000);
                g.fill(x + 1, y + 1, x1 - 1, y1 - 1, 0xFF262630);
                g.fill(x + 1, y + 1, x + 1 + (e.w - 2) * Math.max(0, Math.min(1, num(m, "value", 0.6))), y1 - 1, colour(m, "colour", 0xFF58A0E8));
            }
            case "figure" -> {
                double u = Math.floor(Math.min(e.w / 16, e.h / 32)), ox = Math.floor(x + (e.w - 16 * u) / 2), oy = Math.floor(y1 - 32 * u);
                g.fill(x, y, x1, y1, 0x70000000);
                g.fill(ox + 4 * u, oy, ox + 12 * u, oy + 8 * u, 0xFFC8A486);
                g.fill(ox + 4 * u, oy + 8 * u, ox + 12 * u, oy + 20 * u, 0xFF6B7280);
                g.fill(ox, oy + 8 * u, ox + 4 * u, oy + 20 * u, 0xFFC8A486);
                g.fill(ox + 12 * u, oy + 8 * u, ox + 16 * u, oy + 20 * u, 0xFFC8A486);
                g.fill(ox + 4 * u, oy + 20 * u, ox + 12 * u, oy + 32 * u, 0xFF41464F);
            }
            case "wheel" -> {
                double cx = x + e.w / 2, cy = y + e.h / 2, turn = Math.toRadians(num(m, "turn", 0));
                for (Object o : DataKit.list(m.get("rings"))) {
                    Map<String, Object> r = DataKit.map(o);
                    double in = num(r, "inner", 0), out = num(r, "outer", 0);
                    int n = (int) num(r, "count", 0), chosen = (int) num(r, "selected", -1);
                    g.ring(cx, cy, in, out, 0, Math.PI * 2, colour(r, "colour", 0xA0141010));
                    if (n > 0 && chosen >= 0) g.ring(cx, cy, in, out, turn + Math.PI * 2 * (chosen - 0.5) / n, turn + Math.PI * 2 * (chosen + 0.5) / n, 0x80E8B070);
                    g.ring(cx, cy, Math.max(0, out - 1), out, 0, Math.PI * 2, 0xE0F0C0A0);
                    if (in > 1) g.ring(cx, cy, in, in + 1, 0, Math.PI * 2, 0xE0F0C0A0);
                    List<Object> icons = DataKit.list(r.get("icons"));
                    for (int i = 0; i < n; i++) {
                        double a = turn + Math.PI * 2 * (i + 0.5) / n;
                        if (n > 1) g.ring(cx, cy, in + 1, out - 1, a - 0.6 / Math.max(8, out), a + 0.6 / Math.max(8, out), 0xC0F0C0A0);
                        double mid = (in + out) / 2, at = turn + Math.PI * 2 * i / n;
                        double ix = Math.round(cx + Math.sin(at) * mid) - 8, iy = Math.round(cy - Math.cos(at) * mid) - 8;
                        if (!icons.isEmpty()) icon(g, e, icons.get(i % icons.size()), ix, iy);
                        else g.fill(ix + 2, iy + 2, ix + 14, iy + 14, 0xFF6C6080);
                    }
                }
            }
            default -> { }
        }
        if (e.children.isEmpty() || plainOnly) return;
        boolean clip = Boolean.TRUE.equals(m.get("scroll"));
        if (clip) g.clip(x, y, x1, y1);
        for (El c : e.children) draw(g, c, textInk, textShadow);
        if (clip) g.unclip();
    }

    private static double ratio(int a, int b) {
        double la = shine(a), lb = shine(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double shine(int rgb) {
        double[] v = {Px.r(rgb) / 255.0, Px.g(rgb) / 255.0, Px.b(rgb) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.04045 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    // ================================================================== all of it
    /** Minecraft's Window.calculateScale. wanted 0 = Auto. */
    static int scale(int w, int h, int wanted) {
        int i = 1;
        while (i != wanted && i < w && i < h && w / (i + 1) >= 320 && h / (i + 1) >= 240) i++;
        return i;
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void jpg(BufferedImage img, File to) throws Exception {
        javax.imageio.ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        javax.imageio.ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.88f);
        File tmp = new File(to.getPath() + ".tmp");
        try (javax.imageio.stream.ImageOutputStream o = ImageIO.createImageOutputStream(tmp)) {
            w.setOutput(o);
            w.write(null, new javax.imageio.IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        Files.move(tmp.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** "include": more layout files (relative to this one) whose "screens" join this file's; they hold nothing else. */
    static void include(Map<String, Object> spec, File specFile, List<String> broken) throws Exception {
        List<Object> all = new ArrayList<>(DataKit.list(spec.get("screens")));
        for (Object o : DataKit.list(spec.get("include"))) {
            File f = new File(specFile.getParentFile(), DataKit.str(o));
            if (!f.isFile()) {
                broken.add("no include " + f);
                continue;
            }
            Map<String, Object> more = DataKit.map(new AssetCheck.SJ(DataKit.read(f)).parse());
            for (String k : more.keySet()) if (!Set.of("screens", "_comment").contains(k)) broken.add(f.getName() + ": an included file holds only \"screens\", not \"" + k + "\"");
            all.addAll(DataKit.list(more.get("screens")));
        }
        spec.put("screens", all);
    }

    int run(Map<String, Object> spec, Pattern only, String title) throws Exception {
        for (String k : spec.keySet()) if (!TOP.contains(k)) broken.add("unknown key \"" + k + "\"" + DataKit.nearest(k, TOP));
        List<int[]> sizes = new ArrayList<>();
        for (Object o : spec.get("sizes") == null ? new ArrayList<Object>(SIZES) : DataKit.list(spec.get("sizes"))) {
            Matcher m = Pattern.compile("(\\d{3,5})x(\\d{3,5})").matcher(DataKit.str(o));
            if (m.matches() && Integer.parseInt(m.group(1)) <= 7680 && Integer.parseInt(m.group(2)) <= 4320)
                sizes.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
            else broken.add("a size is like \"1920x1080\": " + DataKit.str(o));
        }
        List<Integer> scales = new ArrayList<>();
        for (Object o : spec.get("scales") == null ? SCALES : DataKit.list(spec.get("scales"))) {
            if (o instanceof Number n && n.intValue() == n.doubleValue() && n.intValue() >= 1 && n.intValue() <= 12) scales.add(n.intValue());
            else if ("auto".equals(o)) scales.add(0);
            else broken.add("a scale is 1..12 or \"auto\": " + DataKit.str(o));
        }
        record Screen(String id, Map<String, Object> raw, List<El> top, BufferedImage shot, LmLook.Skin skin, boolean lite) {}
        String topSkin = str(spec, "skin", "saiyan_royal"), topLook = str(spec, "look", "full");
        if (LmLook.byId(topSkin) == null) broken.add("no skin \"" + topSkin + "\"" + DataKit.nearest(topSkin, LmLook.IDS));
        if (!List.of("full", "lite").contains(topLook)) broken.add("\"look\" is full or lite");
        Set<String> keep = new HashSet<>();
        for (Object o : DataKit.list(spec.get("keep"))) {
            if (DataKit.str(o).matches("\\d{3,5}x\\d{3,5}@\\d{1,2}")) keep.add(DataKit.str(o));
            else broken.add("a \"keep\" entry is like \"1920x1080@2\": " + DataKit.str(o));
        }
        String format = str(spec, "format", "png");
        if (!List.of("png", "jpg").contains(format)) broken.add("\"format\" is png or jpg");
        boolean sheets = !Boolean.FALSE.equals(spec.get("sheets"));
        List<Screen> screens = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Object o : DataKit.list(spec.get("screens"))) {
            Map<String, Object> s = DataKit.map(o);
            String id = str(s, "id", "");
            if (!id.matches("[a-z0-9_]+")) broken.add("\"" + id + "\" isn't a screen id (lower case, _)");
            else if (!ids.add(id)) broken.add(id + " is there twice");
            for (String k : s.keySet()) if (!SCREEN.contains(k)) broken.add(id + ": unknown key \"" + k + "\"" + DataKit.nearest(k, SCREEN));
            List<El> top = new ArrayList<>();
            int n = 0;
            for (Object e : DataKit.list(s.get("elements"))) top.add(read(DataKit.map(e), id, ++n));
            if (top.isEmpty()) broken.add(id + ": no \"elements\"");
            BufferedImage shot = null;
            String sk = str(s, "skin", topSkin), lk = str(s, "look", topLook), bgk = str(s, "background", "world");
            if (LmLook.byId(sk) == null) broken.add(id + ": no skin \"" + sk + "\"" + DataKit.nearest(sk, LmLook.IDS));
            if (!List.of("full", "lite").contains(lk)) broken.add(id + ": \"look\" is full or lite");
            if (!List.of("world", "skin", "scrim").contains(bgk)) broken.add(id + ": \"background\" is world, skin or scrim");
            if (s.get("backdrop") != null) {
                File f = new File(DataKit.str(s.get("backdrop")));
                if (!f.isAbsolute()) f = new File(project, DataKit.str(s.get("backdrop")));
                if (f.isFile()) shot = ImageIO.read(f);
                else broken.add(id + ": no backdrop " + f);
            }
            screens.add(new Screen(id, s, top, shot, LmLook.byId(sk) == null ? LmLook.SAIYAN_ROYAL : LmLook.byId(sk), lk.equals("lite")));
        }
        if (only != null) screens.removeIf(s -> !only.matcher(s.id()).find());
        if (screens.isEmpty() && broken.isEmpty()) broken.add("no \"screens\"" + (only == null ? "" : " match"));
        if (!broken.isEmpty()) {
            System.out.println("GuiKit: NOT DONE");
            for (String b : broken) System.out.println("  " + b);
            return 2;
        }
        Files.createDirectories(out.toPath());
        StringBuilder page = new StringBuilder("<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>")
                .append(html(title)).append("</title><style>")
                .append("body{margin:0;background:#0d0d0d;color:#f0efec;font:14px/1.4 system-ui,'Segoe UI',sans-serif}main{max-width:1300px;margin:auto;padding:24px 16px}")
                .append("p,small{color:#c3c2b7}h1{font-size:22px}h2{font-size:17px;margin-top:28px}.all{display:grid;grid-template-columns:repeat(auto-fill,minmax(380px,1fr));gap:8px}")
                .append("a{color:inherit;text-decoration:none}img{width:100%;height:auto;display:block}small{display:block}.bad{color:#ff9c90}.tag{padding:4px 0}</style></head><body><main><h1>")
                .append(html(title)).append("</h1><p>Mockups in Minecraft's font, at the GUI pixels the game would use. Click one for full size.</p>");
        int pictures = 0;
        for (Screen s : screens) {
            List<BufferedImage> tiles = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            Set<String> done = new HashSet<>();
            page.append("<h2>").append(html(s.id())).append("</h2>");
            StringBuilder grid = new StringBuilder("<div class='all'>");
            for (int[] size : sizes) {
                for (int wanted : scales) {
                    int scale = scale(size[0], size[1], wanted);
                    if (!done.add(size[0] + "x" + size[1] + "@" + scale)) continue;
                    int sw = (int) Math.ceil(size[0] / (double) scale), sh = (int) Math.ceil(size[1] / (double) scale);
                    where = size[0] + "x" + size[1] + " scale " + scale;
                    GuiPaint g = new GuiPaint(size[0], size[1], scale);
                    skin = s.skin();
                    lite = s.lite();
                    String bg = str(s.raw(), "background", "world");
                    if (bg.equals("skin")) {
                        String key = skin.id() + lite + size[0] + "x" + size[1];
                        g.skinBackdrop(baked.computeIfAbsent(key, k -> GuiPaint.bakeSkin(skin, lite, size[0], size[1])));
                    } else {
                        g.backdrop(s.shot());
                        if (bg.equals("scrim")) g.fill(0, 0, sw, sh, skin.scrim());
                    }
                    if (Boolean.TRUE.equals(s.raw().get("dim"))) g.fill(0, 0, sw, sh, 0xC8101010);
                    for (El e : s.top()) place(e, null, 0, 0, sw, sh);
                    for (El e : s.top()) check(e, null, sw, sh);
                    siblings(s.top());
                    for (El e : s.top()) draw(g, e, 0xFFFFFFFF, true);
                    String file = s.id() + "_" + size[0] + "x" + size[1] + "_s" + scale + "." + format;
                    BufferedImage img = g.image();
                    if (!keep.isEmpty() && !keep.contains(size[0] + "x" + size[1] + "@" + scale)) continue;
                    if (format.equals("jpg")) jpg(img, new File(out, file));
                    else Frames.png(img, new File(out, file));
                    pictures++;
                    BufferedImage small = new BufferedImage(480, (int) Math.round(480.0 * size[1] / size[0]), BufferedImage.TYPE_INT_RGB);
                    Graphics2D d = small.createGraphics();
                    d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    d.drawImage(img, 0, 0, small.getWidth(), small.getHeight(), null);
                    d.dispose();
                    tiles.add(small);
                    String label = where + (wanted == 0 ? " (auto)" : "") + " = " + sw + "x" + sh;
                    labels.add(label);
                    grid.append("<a href='").append(file).append("'><img loading='lazy' src='").append(file).append("' alt='")
                            .append(html(label)).append("'><small class='tag'>").append(html(label)).append("</small></a>");
                }
            }
            if (sheets && !tiles.isEmpty()) Frames.png(Frames.sheet(tiles, 3, labels, s.id()), new File(out, s.id() + "_sheet.png"));
            errors.forEach((what, at) -> {
                if (what.startsWith(s.id() + "/")) page.append("<small class='bad'>").append(html(what)).append(" (").append(html(String.join(", ", at))).append(")</small>");
            });
            warnings.forEach((what, at) -> {
                if (what.startsWith(s.id() + "/")) page.append("<small>").append(html(what)).append("</small>");
            });
            page.append(grid).append("</div>");
        }
        for (String g : font.missing) {
            where = "every size";
            error("the font has no glyph " + g);
        }
        List<String> report = new ArrayList<>();
        errors.forEach((what, at) -> report.add("FAIL " + what + " (" + String.join(", ", at) + ")"));
        warnings.forEach((what, at) -> report.add("warn " + what + " (" + (at.size() > 3 ? at.size() + " sizes" : String.join(", ", at)) + ")"));
        Files.write(out.toPath().resolve("report.txt"), report, StandardCharsets.UTF_8);
        Files.writeString(out.toPath().resolve("index.html"), page.append("</main></body></html>\n").toString());
        System.out.printf(Locale.ROOT, "GuiKit: %s - %d screens, %d mockups: %d errors, %d warnings%n", errors.isEmpty() ? "OK" : "FAILED",
                screens.size(), pictures, errors.size(), warnings.size());
        for (int i = 0; i < Math.min(40, report.size()); i++) System.out.println("  " + report.get(i));
        System.out.println("see: " + new File(out, "index.html"));
        return errors.isEmpty() ? 0 : 1;
    }

    // ================================================================== the demo: Heaven and Abyss's five screens, as examples
    private static final String DEMO = """
        {"screens": [
         {"id": "journal", "dim": true, "elements": [
           {"id": "book", "type": "panel", "style": "parchment", "anchor": "center", "w": 360, "h": 216, "children": [
             {"id": "tabs", "type": "tabs", "anchor": "top_center", "y": 6, "w": 344, "items": ["Guide", "Quests", "Bestiary", "Items", "Lore"], "selected": 1},
             {"id": "quests", "type": "list", "x": 8, "y": 28, "w": 120, "h": 180, "rowH": 13, "scroll": true, "selected": 1,
              "items": ["The Awakening", "A Calling of Frost", "Licence: Rank E", "Echoes in the Crypt", "The First Egg", "Into the Abyss"]},
             {"id": "title", "type": "text", "text": "§lA Calling of Frost", "x": 138, "y": 30, "w": 208},
             {"id": "rule", "type": "line", "x": 138, "y": 42, "w": 212},
             {"id": "body", "type": "text", "x": 138, "y": 48, "w": 210, "wrap": true, "maxLines": 8,
              "text": "The old shrine on the mountain still keeps its cold. Reach it, light the brazier with frost magic and wait for the spirit to answer. Nothing here locks you out of other work."},
             {"id": "steps", "type": "text", "x": 138, "y": 130, "w": 210, "text": "Reach the shrine  (1/1)\\nLight the brazier  (0/1)"},
             {"id": "reward", "type": "slot", "x": 138, "y": 156, "icon": "minecraft:textures/item/prismarine_crystals.png"},
             {"id": "track", "type": "button", "text": "Track", "anchor": "bottom_right", "x": -10, "y": -10, "w": 70}]}]},
         {"id": "spell_wheel", "elements": [
           {"id": "wheel", "type": "wheel", "anchor": "center", "rings": [
              {"inner": 0, "outer": 22, "colour": "#d0141010"}, {"inner": 22, "outer": 46, "count": 6}, {"inner": 47, "outer": 71, "count": 8}, {"inner": 72, "outer": 96, "count": 12, "selected": 2}],
            "children": [{"id": "mana", "type": "text", "text": "240", "anchor": "center", "colour": "#8cc8ff"}]},
           {"id": "name", "type": "text", "text": "§nFrost Nova", "anchor": "center", "pivot": "bottom_center", "y": -99},
           {"id": "hint", "type": "text", "text": "Release to select", "anchor": "center", "pivot": "top_center", "y": 100, "colour": "#e0e0e0"}]},
         {"id": "character", "dim": true, "elements": [
           {"id": "window", "type": "panel", "anchor": "center", "w": 340, "h": 216, "children": [
             {"id": "tabs", "type": "tabs", "anchor": "top_center", "y": 4, "w": 332, "items": ["Attributes", "Feats", "Path", "Class", "Spellbook"]},
             {"id": "player", "type": "figure", "x": 10, "y": 28, "w": 84, "h": 130},
             {"id": "name", "type": "text", "text": "Paragon, tier 3", "x": 10, "y": 164, "w": 84, "align": "center", "colour": "#ffe08a"},
             {"id": "level", "type": "bar", "x": 10, "y": 178, "w": 84, "h": 6, "value": 0.4},
             {"id": "names", "type": "text", "x": 108, "y": 30, "w": 90, "text": "Strength\\nAgility\\nEndurance\\nIntellect\\nSpirit"},
             {"id": "values", "type": "text", "x": 206, "y": 30, "w": 30, "align": "right", "text": "24\\n18\\n31\\n40\\n22"},
             {"id": "feats", "type": "grid", "x": 108, "y": 90, "cols": 6, "rows": 2, "gap": 2},
             {"id": "save", "type": "button", "text": "Confirm", "anchor": "bottom_right", "x": -8, "y": -8, "w": 80}]}]},
         {"id": "dialogue", "elements": [
           {"id": "speech", "type": "panel", "anchor": "bottom_center", "y": -6, "w": 360, "h": 106, "children": [
             {"id": "face", "type": "figure", "x": 6, "y": 6, "w": 56, "h": 94},
             {"id": "speaker", "type": "text", "text": "Serah of the Guild", "x": 70, "y": 8, "w": 280, "colour": "#ffd070"},
             {"id": "line", "type": "text", "x": 70, "y": 20, "w": 282, "wrap": true, "maxLines": 3,
              "text": "You came back from the crypt in one piece. Good. I have work that needs someone who can keep a secret, if you will hear it."},
             {"id": "yes", "type": "button", "text": "I'm listening", "x": 70, "y": 54, "w": 138},
             {"id": "ask", "type": "button", "text": "What is the pay?", "x": 214, "y": 54, "w": 138},
             {"id": "bye", "type": "button", "text": "Not now", "x": 70, "y": 79, "w": 282}]}]},
         {"id": "map", "dim": true, "elements": [
           {"id": "frame", "type": "panel", "style": "vanilla", "anchor": "center", "w": 400, "h": 220, "children": [
             {"id": "title", "type": "text", "text": "The Verdant Reach", "x": 8, "y": 7, "w": 200},
             {"id": "land", "type": "box", "x": 8, "y": 19, "w": 278, "h": 192, "colour": "#ff3c5a3a", "children": [
               {"id": "here", "type": "text", "text": "▲", "anchor": "center", "colour": "#ffffff", "shadow": true}]},
             {"id": "pins", "type": "list", "x": 292, "y": 19, "w": 100, "h": 168, "scroll": true, "items": ["Guild Hall", "Frost Shrine", "Sunken Crypt", "Sky Temple"]},
             {"id": "pin", "type": "button", "text": "New pin", "x": 292, "y": 191, "w": 100}]}]}
        ]}
        """;

    static int run(String[] a) throws Exception {
        String what = a.length > 0 ? a[0] : "";
        File install = new File(System.getProperty("user.home"), "curseforge/minecraft/Install");
        if (what.equals("measure") && a.length > 1) {
            McFont f = McFont.load(install);
            System.out.println(f.width(a[1]) + " GUI px: " + a[1]);
            return 0;
        }
        if (a.length < 2 || !Set.of("make", "demo").contains(what)) {
            System.out.println("usage: GuiKit make <project> [-spec file] [-only regex] [-out dir] | demo <out dir> | measure <text>");
            return 2;
        }
        boolean demo = what.equals("demo");
        File where = new File(a[1]).getAbsoluteFile();
        File specFile = null, out = null;
        Pattern only = null;
        for (int i = 2; i < a.length; i++) {
            switch (a[i]) {
                case "-spec" -> specFile = new File(a[++i]).getAbsoluteFile();
                case "-out" -> out = new File(a[++i]).getAbsoluteFile();
                case "-install" -> install = new File(a[++i]).getAbsoluteFile();
                case "-only" -> only = Pattern.compile(a[++i]);
                default -> throw new IllegalArgumentException("unknown option " + a[i]);
            }
        }
        if (!demo && !where.isDirectory()) {
            System.out.println("GuiKit: no project " + where);
            return 2;
        }
        Map<String, Object> settings = Map.of();
        File config = new File(where, "tools/toolkit.json");
        if (!demo && config.isFile()) settings = DataKit.map(new AssetCheck.SJ(DataKit.read(config)).parse());
        File assets = new File(where, str(settings, "assets", "src/main/resources/assets").replace('\\', '/'));
        if (out == null) out = demo ? where : new File(new File(where, str(settings, "preview", "build/preview").replace('\\', '/')), "guikit");
        if ((out.toPath().normalize().toString().replace('\\', '/') + "/").toLowerCase(Locale.ROOT).contains("/src/main/")) {
            System.out.println("GuiKit: writes mockups, never into src");
            return 2;
        }
        Map<String, Object> spec;
        if (demo) spec = DataKit.map(new AssetCheck.SJ(DEMO).parse());
        else {
            if (specFile == null) specFile = new File(where, "tools/guikit.json");
            if (!specFile.isFile()) {
                System.out.println("GuiKit: no " + specFile + " (its format is at the top of GuiKit.java)");
                return 2;
            }
            spec = new LinkedHashMap<>(DataKit.map(new AssetCheck.SJ(DataKit.read(specFile)).parse()));
        }
        List<String> early = new ArrayList<>();
        if (!demo) include(spec, specFile, early);
        GuiKit kit;
        try {
            kit = new GuiKit(where, out, assets, install);
        } catch (IllegalStateException e) {
            System.out.println("GuiKit: NOT PROVEN - " + e.getMessage() + " (the font comes from the game)");
            return 2;
        }
        kit.broken.addAll(early);
        return kit.run(spec, only, demo ? "GuiKit: five example screens" : str(settings, "name", where.getName()) + " screens");
    }

    public static void main(String[] a) {
        System.setProperty("java.awt.headless", "true");
        int code;
        try {
            code = run(a);
        } catch (IllegalArgumentException e) {
            System.out.println("GuiKit: " + e.getMessage());
            code = 2;
        } catch (Exception e) {
            System.out.println("GuiKit could not run: " + e);
            e.printStackTrace(System.out);
            code = 2;
        }
        System.exit(code);
    }
}
