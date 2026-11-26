package com.dbzlegacy.uiprobe;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.BannerDesign;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoBannerEditor;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoFounding;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraDojoScreens;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.8.2 probe (test only): the banner editor (Part A), banners where other players see a dojo (Part C) and the
 * free rename (Part D). Lines read {@code V482 <AREA> <what>: PASS|FAIL | detail}. Every dojo, war and profile it makes
 * is removed at the end of each method.
 */
public final class V482BannerUi {
    private V482BannerUi() {}

    /** Every 4.8.2 banner and rename check. */
    public static void all(ServerPlayer player, Consumer<String> note) {
        editor(player, note);
        carried(player, note);
        surfaces(player, note);
        rename(player, note);
    }

    // ------------------------------------------------------------------ helpers

    private static ServerPlayer fake(ServerPlayer player, String name) {
        return net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(),
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(("uiprobe482:" + name)
                        .getBytes(StandardCharsets.UTF_8)), name));
    }

    private static DojoStore.Dojo dojo(ServerPlayer master, String name, String style, int clout) {
        DojoService.byClout();                              // roll every dojo into this season first
        DojoStore.Dojo d = new DojoStore.Dojo();
        d.masterUuid = master.m_20148_().toString();
        d.masterName = master.m_7755_().getString();
        d.name = name;
        d.style = style;
        d.open = true;
        d.foundedAt = System.currentTimeMillis();
        d.upgrades.put(DojoCatalog.ARENA, 1);
        DojoStore.get().dojos.put(d.masterUuid, d);
        try {
            java.lang.reflect.Method season = DojoService.class.getDeclaredMethod("currentSeason");
            season.setAccessible(true);
            d.cloutSeason = (Integer) season.invoke(null);
        } catch (ReflectiveOperationException e) {
            d.cloutSeason = 1;
        }
        d.clout = clout;
        return d;
    }

    private static void drop(ServerPlayer... masters) {
        for (ServerPlayer m : masters) {
            DojoStore.get().dojos.remove(m.m_20148_().toString());
            DojoFounding.forget(m.m_20148_());
        }
        DojoStore.get().markDirty();
    }

    private static List<GuiOpenPacket> open(ServerPlayer p, String page) {
        return UltraScreenSpec.captureSends(() -> UltraDojoScreens.openSystem(p, page));
    }

    private static List<GuiOpenPacket> act(ServerPlayer p, String action, String arg) {
        return UltraScreenSpec.captureSends(() -> DojoFounding.handle(p, action, arg, new JsonObject()));
    }

    private static GuiOpenPacket last(List<GuiOpenPacket> l) {
        return l == null || l.isEmpty() ? null : l.get(l.size() - 1);
    }

    private static String text(GuiOpenPacket p) {
        if (p == null) {
            return "";
        }
        StringBuilder lines = new StringBuilder();
        try {
            for (JsonElement el : JsonParser.parseString(p.linesJson()).getAsJsonArray()) {
                lines.append(' ').append(el.isJsonPrimitive() ? el.getAsString() : "");
            }
        } catch (RuntimeException e) {
            lines.append(p.linesJson());
        }
        // Body lines wrap at the panel width: joined with spaces, a wrapped phrase still matches.
        return (p.buttonsJson() + lines + " " + p.layoutJson()).replaceAll("\u00a7.", "").replace("\\u0027", "'");
    }

    private static boolean has(GuiOpenPacket p, String t) {
        return text(p).contains(t);
    }

    /** The first button whose id contains {@code idPart}: {id, label, style, icon}, or null. */
    private static String[] button(GuiOpenPacket p, String idPart) {
        if (p == null) {
            return null;
        }
        for (JsonElement el : JsonParser.parseString(p.buttonsJson()).getAsJsonArray()) {
            JsonObject b = el.getAsJsonObject();
            String id = bare(b.has("id") ? b.get("id").getAsString() : "");
            String label = b.has("label") ? b.get("label").getAsString().replaceAll("\u00a7.", "") : "";
            if (id.contains(idPart) || (idPart.startsWith("label=") && label.contains(idPart.substring(6)))) {
                return new String[] {id, label, b.has("style") ? b.get("style").getAsString() : "",
                        b.has("icon") ? b.get("icon").getAsString() : ""};
            }
        }
        return null;
    }

    /** Ids of the buttons whose id starts with {@code prefix}. */
    private static List<String[]> buttons(GuiOpenPacket p, String prefix) {
        List<String[]> out = new ArrayList<>();
        if (p == null) {
            return out;
        }
        for (JsonElement el : JsonParser.parseString(p.buttonsJson()).getAsJsonArray()) {
            JsonObject b = el.getAsJsonObject();
            String id = bare(b.has("id") ? b.get("id").getAsString() : "");
            if (id.startsWith(prefix)) {
                out.add(new String[] {id, b.has("icon") ? b.get("icon").getAsString() : ""});
            }
        }
        return out;
    }

    /** A button id without the screen qualifier the spec adds ({@code @dojo:do:x} reads {@code do:x}). */
    private static String bare(String id) {
        if (id.startsWith("@")) {
            int c = id.indexOf(':');
            return c < 0 ? id : id.substring(c + 1);
        }
        return id;
    }

    /** The banner pictures item ({@code b}) of a page's layout, or "". */
    private static String preview(GuiOpenPacket p) {
        if (p == null) {
            return "";
        }
        try {
            JsonObject lay = JsonParser.parseString(p.layoutJson()).getAsJsonObject();
            if (!lay.has("pictures")) {
                return "";
            }
            for (JsonElement el : lay.getAsJsonObject("pictures").getAsJsonArray("items")) {
                JsonObject it = el.getAsJsonObject();
                if (it.has("b")) {
                    return it.get("b").getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            return "";
        }
        return "";
    }

    private static String pf(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }

    private static void line(Consumer<String> note, String area, String what, boolean ok, String detail) {
        note.accept("V482 " + area + " " + what + ": " + pf(ok) + (detail == null || detail.isEmpty() ? "" : " | " + detail));
    }

    // ------------------------------------------------------------------ Part A: the editor

    public static void editor(ServerPlayer player, Consumer<String> note) {
        ServerPlayer m = fake(player, "V482Master");
        drop(m);
        DojoStore.Dojo d = dojo(m, "V482 Editor Hall", "KI", 0);
        d.points = 100;
        try {
            GuiOpenPacket ed = last(open(m, "dojo_banner_edit"));
            String[] save = button(ed, "do:dojo_bedit_save");
            line(note, "EDITOR", "opens with the style default as the start and a free Save", ed != null
                    && "dojo_banner_edit".equals(ed.pageId()) && has(ed, "Banner editor") && save != null
                    && save[1].contains("Save this banner (free)") && "lmbanner:light_blue".equals(preview(ed)),
                    ed == null ? "no page" : ed.pageId() + " preview=" + preview(ed));

            GuiOpenPacket base = last(open(m, "dojo_banner_base"));
            List<String[]> colours = buttons(base, "do:dojo_bedit_base:");
            line(note, "EDITOR", "base colour page offers the 16 dyes, each with a banner icon", colours.size() == 16
                    && colours.stream().allMatch(c -> c[1].startsWith("lmbanner:")), colours.size() + " colours");
            act(m, "dojo_bedit_base", "red");
            line(note, "EDITOR", "base pick", "lmbanner:red".equals(DojoBannerEditor.draftIcon(m)),
                    DojoBannerEditor.draftIcon(m));

            // Every pattern a loom offers, from the registry (5 pages of 8), never the plain base.
            List<String> offered = new ArrayList<>();
            boolean iconsOk = true;
            for (int pg = 0; pg < 6; pg++) {
                GuiOpenPacket pp = last(open(m, pg == 0 ? "dojo_banner_pat" : "dojo_banner_pat:" + pg));
                for (String[] b : buttons(pp, "page:dojo_banner_col:")) {
                    String code = b[0].substring("page:dojo_banner_col:".length());
                    if (!offered.contains(code)) {
                        offered.add(code);
                    }
                    iconsOk &= b[1].startsWith("lmbanner:red/" + code + ":");
                }
            }
            List<String> reg = BannerDesign.patterns();
            line(note, "EDITOR", "pattern pages list every registry pattern (no base) with a preview icon",
                    offered.size() == reg.size() && offered.containsAll(reg) && !offered.contains("b") && reg.size() >= 38
                            && iconsOk && reg.contains("flo") && reg.contains("pig"),
                    offered.size() + " offered of " + reg.size() + " in the registry");
            line(note, "EDITOR", "patterns have plain English names", "Cross".equals(BannerDesign.patternName("sc"))
                    && "Border".equals(BannerDesign.patternName("bo")) && "Bottom stripe".equals(BannerDesign.patternName("bs")),
                    BannerDesign.patternName("sc") + "/" + BannerDesign.patternName("bo"));
            GuiOpenPacket col = last(open(m, "dojo_banner_col:sc"));
            List<String[]> cols = buttons(col, "do:dojo_bedit_add:sc:");
            line(note, "EDITOR", "colour page for a new layer offers 16 dyes with the banner it makes", cols.size() == 16
                    && "lmbanner:red/sc:0".equals(button(col, "do:dojo_bedit_add:sc:0")[3]), cols.size() + " colours");

            act(m, "dojo_bedit_add", "sc:0");
            act(m, "dojo_bedit_add", "bo:15");
            act(m, "dojo_bedit_add", "mc:4");
            line(note, "EDITOR", "add 3 layers (pattern + colour, stored as code:dye id)",
                    "lmbanner:red/sc:0,bo:15,mc:4".equals(DojoBannerEditor.draftIcon(m)), DojoBannerEditor.draftIcon(m));
            act(m, "dojo_bedit_remove", "2");
            line(note, "EDITOR", "remove a layer", "lmbanner:red/sc:0,mc:4".equals(DojoBannerEditor.draftIcon(m)),
                    DojoBannerEditor.draftIcon(m));
            act(m, "dojo_bedit_add", "bo:15");
            GuiOpenPacket up = last(act(m, "dojo_bedit_up", "3"));
            act(m, "dojo_bedit_down", "1");
            act(m, "dojo_bedit_recol", "1:11");
            line(note, "EDITOR", "reorder (up, down) and recolour a layer",
                    "lmbanner:red/bo:11,sc:0,mc:4".equals(DojoBannerEditor.draftIcon(m)) && up != null
                            && "dojo_banner_layer:2".equals(up.pageId()), DojoBannerEditor.draftIcon(m));
            for (int i = 0; i < 3; i++) {
                act(m, "dojo_bedit_add", "ts:" + i);
            }
            GuiOpenPacket full = last(open(m, "dojo_banner_edit"));
            String[] add = button(full, "label=Add");
            line(note, "EDITOR", "at 6 layers Add is dim, not hidden", add != null && "dim".equals(add[2]),
                    add == null ? "no Add" : add[1]);
            act(m, "dojo_bedit_add", "ts:5");
            line(note, "EDITOR", "a 7th layer is refused", DojoBannerEditor.draftIcon(m).split(",").length == 6,
                    DojoBannerEditor.draftIcon(m));
            // The 6-layer check added ts:0..2 after the 3 layers: remove layers 6, 5 and 4 to get back to the 3.
            for (int at = 6; at >= 4; at--) {
                act(m, "dojo_bedit_remove", String.valueOf(at));
            }

            GuiOpenPacket ready = last(open(m, "dojo_banner_edit"));
            String[] s1 = button(ready, "do:dojo_bedit_save");
            String shown = preview(ready);
            line(note, "EDITOR", "preview (picture b) and Save icon equal the design Save writes",
                    "lmbanner:red/bo:11,sc:0,mc:4".equals(shown) && s1 != null && shown.equals(s1[3]),
                    shown + " / " + (s1 == null ? "no save" : s1[3]));
            act(m, "dojo_bedit_save", "");
            line(note, "EDITOR", "first save is free, starts no cooldown and writes base + ordered list",
                    "red".equals(d.bannerBase) && List.of("bo:11", "sc:0", "mc:4").equals(d.bannerPatterns) && d.points == 100
                            && d.identityChangedAt == 0L && shown.equals(DojoFounding.bannerIcon(d)),
                    d.bannerBase + " " + d.bannerPatterns + " points=" + d.points);

            act(m, "dojo_bedit_base", "blue");
            GuiOpenPacket paid = last(open(m, "dojo_banner_edit"));
            String[] s2 = button(paid, "do:dojo_bedit_save");
            line(note, "EDITOR", "a later change says it costs 20 Dojo Points and the 12-hour lock", s2 != null
                    && s2[1].contains("20 Dojo Points") && has(paid, "locks the name, style and banner for 12 hours"),
                    s2 == null ? "no save" : s2[1]);
            act(m, "dojo_bedit_save", "");
            line(note, "EDITOR", "second change costs 20 and starts the cooldown", "blue".equals(d.bannerBase)
                    && d.points == 80 && d.identityChangedAt > 0L, "points=" + d.points);

            act(m, "dojo_bedit_base", "green");
            GuiOpenPacket cd = last(open(m, "dojo_banner_edit"));
            String[] s3 = button(cd, "label=Save in");
            line(note, "EDITOR", "during the cooldown Save is dim with the time left and the reason",
                    s3 != null && "dim".equals(s3[2]) && "nav:none".equals(s3[0]) && has(cd, "can change again in"),
                    s3 == null ? "no dim save" : s3[1]);
            act(m, "dojo_bedit_save", "");
            line(note, "EDITOR", "saving during the cooldown changes nothing", "blue".equals(d.bannerBase) && d.points == 80,
                    d.bannerBase);
            d.identityChangedAt = 0L;
            d.points = 5;
            GuiOpenPacket poor = last(open(m, "dojo_banner_edit"));
            String[] s4 = button(poor, "label=Save - needs 20");
            line(note, "EDITOR", "unaffordable: Save is dim with what it needs", s4 != null && "dim".equals(s4[2])
                    && has(poor, "That costs 20 Dojo Points"), s4 == null ? "no dim save" : s4[1]);
            act(m, "dojo_bedit_reset", "");
            d.points = 100;
            GuiOpenPacket same = last(open(m, "dojo_banner_edit"));
            String[] s5 = button(same, "label=nothing changed");
            line(note, "EDITOR", "Start over returns to the saved banner; saving it again is dim", s5 != null
                    && "dim".equals(s5[2]) && DojoBannerEditor.draftIcon(m).equals(DojoFounding.bannerIcon(d)),
                    DojoBannerEditor.draftIcon(m));
            GuiOpenPacket pick = last(open(m, "dojo_banner"));
            line(note, "EDITOR", "the banner page offers Design a banner here", button(pick, "page:dojo_banner_edit") != null, "");
        } finally {
            drop(m);
        }
    }

    // ------------------------------------------------------------------ Part A: the carried banner still works

    public static void carried(ServerPlayer player, Consumer<String> note) {
        ServerPlayer m = fake(player, "V482Carrier");
        drop(m);
        DojoStore.Dojo d = dojo(m, "V482 Carry Hall", "MELEE", 0);
        try {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation("minecraft", "yellow_banner"));
            var stack = new net.minecraft.world.item.ItemStack(item);
            var pats = new net.minecraft.nbt.ListTag();
            var pt = new net.minecraft.nbt.CompoundTag();
            pt.m_128359_("Pattern", "cr");
            pt.m_128405_("Color", 14);
            pats.add(pt);
            stack.m_41698_("BlockEntityTag").m_128365_("Patterns", pats);
            m.m_150109_().m_6211_();
            m.m_150109_().m_36054_(stack);
            GuiOpenPacket pick = last(open(m, "dojo_banner"));
            String[] use = button(pick, "do:dojo_banner_set:");
            line(note, "CARRIED", "banner page lists the carried banner beside Design a banner here", use != null
                    && button(pick, "page:dojo_banner_edit") != null && use[1].contains("Yellow with Diagonal cross (red)"),
                    use == null ? "no carried banner" : use[1]);
            if (use != null) {
                act(m, "dojo_banner_set", use[0].substring("do:dojo_banner_set:".length()));
            }
            line(note, "CARRIED", "copying a carried banner still saves its design", "yellow".equals(d.bannerBase)
                    && List.of("cr:14").equals(d.bannerPatterns), d.bannerBase + " " + d.bannerPatterns);
        } finally {
            m.m_150109_().m_6211_();
            drop(m);
        }
    }

    // ------------------------------------------------------------------ Part C: other players see the banner

    public static void surfaces(ServerPlayer player, Consumer<String> note) {
        ServerPlayer o = fake(player, "V482Other");
        ServerPlayer n = fake(player, "V482Plain");
        drop(o, n);
        DojoStore.Dojo other = dojo(o, "V482 Blue", "KI", 900002);
        DojoStore.Dojo plain = dojo(n, "V482 Plain", "MELEE", 900001);
        other.bannerBase = "blue";
        other.bannerPatterns = new ArrayList<>(List.of("sc:0", "bo:15"));
        String key = other.masterUuid.toLowerCase(Locale.ROOT);
        SparStore.DojoProfile legacy = new SparStore.DojoProfile();
        legacy.bannerMaterial = "LIME_BANNER";
        SparStore.DojoProfile oldProfile = SparStore.get().dojoProfiles.put(key, legacy);
        DojoStore.WarRecord w = new DojoStore.WarRecord();
        w.id = "probe482-war";
        w.fromDojo = other.masterUuid;
        w.fromName = other.name;
        w.toDojo = plain.masterUuid;
        w.toName = plain.name;
        w.status = "DONE";
        w.winnerName = other.name;
        w.fromScore = 1;
        DojoStore.get().wars.add(w);
        String iconO = "lmbanner:blue/sc:0,bo:15";
        String iconN = "lmbanner:red";
        String full = "Blue with Cross (white), Border (black)";
        List<GuiOpenPacket> seen = new ArrayList<>();
        try {
            line(note, "SURFACE", "banner text names the patterns", full.equals(DojoFounding.bannerText(other, 40))
                    && "Blue, 2 layers".equals(DojoFounding.bannerText(other, 20)), DojoFounding.bannerText(other, 40));
            GuiOpenPacket list = last(open(player, "dojo_list"));
            seen.add(list);
            String[] bo = button(list, "page:dojo_view:" + other.masterUuid);
            String[] bn = button(list, "page:dojo_view:" + plain.masterUuid);
            line(note, "SURFACE", "dojo list rows carry the picked banner icon and the style default", bo != null
                    && iconO.equals(bo[3]) && bn != null && iconN.equals(bn[3]),
                    (bo == null ? "-" : bo[3]) + " / " + (bn == null ? "-" : bn[3]));
            line(note, "SURFACE", "dojo list row text ends with the banner when it fits (info)", true,
                    has(list, "Blue with Cross") || has(list, "Blue, 2 layers") ? "shown" : "too long, icon only");
            GuiOpenPacket lb = last(open(player, "dojo_list_wars"));
            seen.add(lb);
            String[] lo = button(lb, "page:dojo_view:" + other.masterUuid);
            line(note, "SURFACE", "dojo leaderboard (by war wins) rows carry the banner icon", lo != null && iconO.equals(lo[3]),
                    lo == null ? "-" : lo[3]);

            GuiOpenPacket view = last(open(player, "dojo_view:" + other.masterUuid));
            seen.add(view);
            line(note, "SURFACE", "dojo view: Banner line after Wars and the banner picture", has(view, "Banner " + full)
                    && text(view).indexOf("Banner " + full) > text(view).indexOf("Wars ") && iconO.equals(preview(view)),
                    preview(view));
            GuiOpenPacket viewN = last(open(player, "dojo_view:" + plain.masterUuid));
            seen.add(viewN);
            line(note, "SURFACE", "dojo view with no banner picked shows the style default", has(viewN, "Banner default (Red)")
                    && iconN.equals(preview(viewN)), preview(viewN));

            GuiOpenPacket wp = last(open(player, "dojo_war_pick"));
            seen.add(wp);
            String[] wo = button(wp, "page:dojo_war_fmt:" + other.masterUuid);
            line(note, "SURFACE", "war pick: banner text and icon", wo != null && iconO.equals(wo[3])
                    && (has(wp, full) || has(wp, "Blue, 2 layers")), wo == null ? "-" : wo[3]);
            GuiOpenPacket wf = last(open(player, "dojo_war_fmt:" + other.masterUuid));
            seen.add(wf);
            line(note, "SURFACE", "war format: their banner in words and as a picture", has(wf, "Their banner")
                    && (has(wf, full) || has(wf, "Blue, 2 layers")) && iconO.equals(preview(wf)), preview(wf));
            GuiOpenPacket wv = last(open(player, "dojo_war_view:" + w.id));
            seen.add(wv);
            String[] so = button(wv, "page:dojo_view:" + other.masterUuid);
            String[] sn = button(wv, "page:dojo_view:" + plain.masterUuid);
            line(note, "SURFACE", "war view: each side's banner text and icon", so != null && iconO.equals(so[3])
                    && sn != null && iconN.equals(sn[3]) && has(wv, "V482 Blue: Blue") && has(wv, "V482 Plain: default (Red)"),
                    (so == null ? "-" : so[3]) + " / " + (sn == null ? "-" : sn[3]));

            boolean lime = false;
            for (GuiOpenPacket p : seen) {
                lime |= text(p).toLowerCase(Locale.ROOT).contains("lime");
            }
            line(note, "SURFACE", "legacy bannerMaterial (set to LIME) is never shown; ladder colour reads dojos.json",
                    !lime && "BLUE_BANNER".equals(DojoRankings.dojoBannerMaterial(other.masterUuid))
                            && "RED_BANNER".equals(DojoRankings.dojoBannerMaterial(plain.masterUuid)),
                    DojoRankings.dojoBannerMaterial(other.masterUuid));
            line(note, "SURFACE", "icon encoding round-trips through UltraStyle.normalizeIcon",
                    iconO.equals(com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle.normalizeIcon(iconO))
                            && !BannerDesign.stack(iconO).m_41619_(), iconO);
        } finally {
            DojoStore.get().wars.remove(w);
            if (oldProfile == null) {
                SparStore.get().dojoProfiles.remove(key);
            } else {
                SparStore.get().dojoProfiles.put(key, oldProfile);
            }
            drop(o, n);
        }
    }

    // ------------------------------------------------------------------ Part D: the free rename

    public static void rename(ServerPlayer player, Consumer<String> note) {
        ServerPlayer r = fake(player, "V482Renamer");
        ServerPlayer c = fake(player, "V482CmdNamer");
        drop(r, c);
        DojoStore.Dojo d = dojo(r, "V482 Old Name", "MELEE", 0);
        DojoStore.Dojo e = dojo(c, "V482 Cmd Hall", "MELEE", 0);
        try {
            long lockedAt = System.currentTimeMillis();
            d.identityChangedAt = lockedAt;
            d.points = 0;
            GuiOpenPacket set = last(open(r, "dojo_settings"));
            String[] rb = button(set, "page:dojo_rename");
            line(note, "RENAME", "settings: free rename open during the cooldown, lock line says so", rb != null
                    && rb[1].contains("Rename (free)") && has(set, "Your free rename works now."),
                    rb == null ? "no rename button" : rb[1]);
            GuiOpenPacket page = last(open(r, "dojo_rename"));
            line(note, "RENAME", "rename page says this rename is free", has(page, "This rename Free"), "");
            JsonObject typed = new JsonObject();
            typed.addProperty("value", "V482 New Name");
            UltraScreenSpec.captureSends(() -> DojoFounding.handle(r, "dojo_rename_set", "", typed));
            line(note, "RENAME", "first rename is free with 0 points during a cooldown and starts none",
                    "V482 New Name".equals(d.name) && d.freeRenameUsed && d.points == 0 && d.identityChangedAt == lockedAt,
                    d.name + " used=" + d.freeRenameUsed);
            line(note, "RENAME", "after a GUI rename the ladder name is the new name",
                    "V482 New Name".equals(DojoRankings.dojoDisplayName(d.masterUuid)), DojoRankings.dojoDisplayName(d.masterUuid));
            JsonObject again = new JsonObject();
            again.addProperty("value", "V482 Third");
            UltraScreenSpec.captureSends(() -> DojoFounding.handle(r, "dojo_rename_set", "", again));
            line(note, "RENAME", "second rename during the cooldown is refused", "V482 New Name".equals(d.name), d.name);
            GuiOpenPacket set2 = last(open(r, "dojo_settings"));
            line(note, "RENAME", "settings lock line without the free rename", has(set2,
                    "The name, style and banner can change again in"), "");
            d.identityChangedAt = 0L;
            d.points = 100;
            GuiOpenPacket set3 = last(open(r, "dojo_settings"));
            line(note, "RENAME", "settings lock line, no cooldown, free rename used", has(set3,
                    "A rename or a new banner locks the name, style and banner for 12 hours."), "");
            UltraScreenSpec.captureSends(() -> DojoFounding.handle(r, "dojo_rename_set", "", again));
            line(note, "RENAME", "second rename costs 20 and starts the 12h cooldown", "V482 Third".equals(d.name)
                    && d.points == 80 && d.identityChangedAt > 0L, d.name + " points=" + d.points);
            String cmd = DojoRankings.setDojoName(r, "V482 Cmd Try");
            line(note, "RENAME", "/lm dojo name follows the cooldown (not unlimited)", "V482 Third".equals(d.name),
                    cmd.replaceAll("\u00a7.", ""));

            GuiOpenPacket setE = last(open(c, "dojo_settings"));
            line(note, "RENAME", "settings lock line with the free rename, no cooldown", has(setE,
                    "Your first rename is free and locks nothing."), "");
            e.points = 0;
            DojoRankings.setDojoName(c, "V482 Cmd Free");
            line(note, "RENAME", "/lm dojo name uses the free rename once", "V482 Cmd Free".equals(e.name) && e.freeRenameUsed
                    && e.identityChangedAt == 0L, e.name);
            DojoRankings.setDojoName(c, "V482 Cmd Paid");
            line(note, "RENAME", "/lm dojo name then needs 20 points", "V482 Cmd Free".equals(e.name), e.name);
        } finally {
            drop(r, c);
        }
    }
}
