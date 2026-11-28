package com.dbzlegacy.uiprobe;

import com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoPlacer;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoState;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoBannerEditor;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.DojoFounding;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraDojoScreens;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSpec;
import com.dbzlegacy.adaptivedifficulty.net.gui.GuiOpenPacket;
import com.dbzlegacy.adaptivedifficulty.net.gui.LmNativeNetwork;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * LM 4.8.4 probe (test only), parts H, I and J: the maxed upgrade page shows the room alone (picture clients), a
 * disciple's name on Members asks to make them an elite (View opens their page), and a banner needs no item (the
 * editor from the pick page and from founding step 3). Lines read {@code V484 <AREA> <what>: PASS|FAIL | detail}.
 * The probe player's own pages are kept for screenshots; every dojo, bond and draft it makes is removed at the end.
 */
public final class V484Menus {
    private V484Menus() {}

    /** Every H, I and J check. {@code keep} gets the probe player's pages (screenshots, packet file). */
    public static void run(ServerPlayer player, Consumer<String> note, BiConsumer<String, List<GuiOpenPacket>> keep) {
        String pid = player.m_20148_().toString();
        DojoStore store = DojoStore.get();
        DojoStore.Dojo hadDojo = store.dojos.remove(pid);
        SparStore.MentorBond hadBond = SparStore.get().bondsByPlayer.remove(pid);
        List<ServerPlayer> fakes = new ArrayList<>();
        try {
            if (DojoService.dojoOf(player) != null) {
                note.accept("V484 MENUS the probe player is free to run a test dojo: FAIL | in another dojo");
                return;
            }
            founding(player, note, keep, fakes);
            maxedPage(player, note, keep, fakes);
            promote(player, note, keep, fakes);
            pickPage(player, note, keep, fakes);
        } catch (Throwable t) {
            note.accept("V484 MENUS ERROR: FAIL | " + t);
        } finally {
            DojoPlacer.finishNow();
            store.dojos.remove(pid);
            DojoFounding.forget(player.m_20148_());
            SparStore.get().bondsByPlayer.remove(pid);
            for (ServerPlayer f : fakes) {
                store.dojos.remove(f.m_20148_().toString());
                SparStore.get().bondsByPlayer.remove(f.m_20148_().toString());
                DojoFounding.forget(f.m_20148_());
            }
            if (hadDojo != null) {
                store.dojos.put(pid, hadDojo);
            }
            if (hadBond != null) {
                SparStore.get().bondsByPlayer.put(pid, hadBond);
            }
            store.markDirty();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static ServerPlayer fake(ServerPlayer player, String name, List<ServerPlayer> fakes) {
        ServerPlayer f = net.minecraftforge.common.util.FakePlayerFactory.get(player.m_284548_(),
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(("uiprobe484:" + name)
                        .getBytes(StandardCharsets.UTF_8)), name));
        DojoStore.get().dojos.remove(f.m_20148_().toString());
        SparStore.get().bondsByPlayer.remove(f.m_20148_().toString());
        DojoFounding.forget(f.m_20148_());
        fakes.add(f);
        return f;
    }

    private static DojoStore.Dojo dojo(ServerPlayer master, String name, String style) {
        DojoStore.Dojo d = new DojoStore.Dojo();
        d.masterUuid = master.m_20148_().toString();
        d.masterName = master.m_7755_().getString();
        d.name = name;
        d.style = style;
        d.open = true;
        d.foundedAt = System.currentTimeMillis();
        d.upgrades.put(DojoCatalog.ARENA, 1);
        DojoStore.get().dojos.put(d.masterUuid, d);
        return d;
    }

    /** Make {@code pupil} a disciple of {@code master}'s dojo the way a bond does, joined {@code joinedAt}. */
    private static void bond(ServerPlayer master, ServerPlayer pupil, long joinedAt) {
        SparStore s = SparStore.get();
        SparStore.MentorBond a = s.bond(pupil.m_20148_());
        a.mentorUuid = master.m_20148_().toString();
        a.mentorName = master.m_7755_().getString();
        s.bond(master.m_20148_()).addApprentice(pupil.m_20148_().toString(), pupil.m_7755_().getString());
        DojoService.onDiscipleJoined(master.m_20148_().toString(), pupil.m_20148_().toString());
        DojoStore.Dojo d = DojoService.byMaster(master.m_20148_().toString());
        if (d != null) {
            d.joinedAt.put(pupil.m_20148_().toString(), joinedAt);
        }
    }

    private static List<GuiOpenPacket> open(ServerPlayer p, String page) {
        return UltraScreenSpec.captureSends(() -> UltraDojoScreens.openSystem(p, page));
    }

    /** A dojo / founding / editor action as the button sends it. */
    private static List<GuiOpenPacket> act(ServerPlayer p, String action, String arg) {
        return UltraScreenSpec.captureSends(() -> {
            if (!DojoFounding.handle(p, action, arg, new JsonObject())) {
                UltraDojoScreens.handle(p, action, arg);
            }
        });
    }

    private static List<GuiOpenPacket> typed(ServerPlayer p, String action, String value) {
        JsonObject args = new JsonObject();
        args.addProperty("value", value);
        return UltraScreenSpec.captureSends(() -> DojoFounding.handle(p, action, "", args));
    }

    private static GuiOpenPacket last(List<GuiOpenPacket> l) {
        return l == null || l.isEmpty() ? null : l.get(l.size() - 1);
    }

    /** The last packet of {@code s}, kept for a screenshot when {@code p} is the probe player. */
    private static GuiOpenPacket shot(ServerPlayer p, ServerPlayer player, BiConsumer<String, List<GuiOpenPacket>> keep,
            String tag, Supplier<List<GuiOpenPacket>> s) {
        List<GuiOpenPacket> got = s.get();
        if (p == player && got != null && !got.isEmpty()) {
            keep.accept("v484menus " + tag, List.of(got.get(got.size() - 1)));
        }
        return last(got);
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
        return (p.buttonsJson() + lines).replaceAll("§.", "").replace("\\u0027", "'");
    }

    private static boolean has(GuiOpenPacket p, String t) {
        return text(p).contains(t);
    }

    /** The first button whose id contains {@code idPart} (or label, with {@code label=}): {id, label, style}, or null. */
    private static String[] button(GuiOpenPacket p, String idPart) {
        if (p == null) {
            return null;
        }
        for (JsonElement el : JsonParser.parseString(p.buttonsJson()).getAsJsonArray()) {
            JsonObject b = el.getAsJsonObject();
            String id = bare(b.has("id") ? b.get("id").getAsString() : "");
            String label = b.has("label") ? b.get("label").getAsString().replaceAll("§.", "") : "";
            if ((!idPart.startsWith("label=") && id.contains(idPart))
                    || (idPart.startsWith("label=") && label.contains(idPart.substring(6)))) {
                return new String[] {id, label, b.has("style") ? b.get("style").getAsString() : ""};
            }
        }
        return null;
    }

    /** Every button as "id=label". */
    private static String buttons(GuiOpenPacket p) {
        StringBuilder sb = new StringBuilder();
        if (p == null) {
            return "";
        }
        for (JsonElement el : JsonParser.parseString(p.buttonsJson()).getAsJsonArray()) {
            JsonObject b = el.getAsJsonObject();
            sb.append('[').append(bare(b.has("id") ? b.get("id").getAsString() : "")).append('=')
                    .append(b.has("label") ? b.get("label").getAsString().replaceAll("§.", "") : "").append(']');
        }
        return sb.toString();
    }

    private static String bare(String id) {
        if (id.startsWith("@")) {
            int c = id.indexOf(':');
            return c < 0 ? id : id.substring(c + 1);
        }
        return id;
    }

    /** The pictures of a page: per item, its first layer's texture ("dojo/cu_..."); empty without pictures. */
    private static List<String> pictures(GuiOpenPacket p) {
        List<String> out = new ArrayList<>();
        if (p == null) {
            return out;
        }
        try {
            JsonObject lay = JsonParser.parseString(p.layoutJson()).getAsJsonObject();
            if (!lay.has("pictures")) {
                return out;
            }
            for (JsonElement el : lay.getAsJsonObject("pictures").getAsJsonArray("items")) {
                JsonObject it = el.getAsJsonObject();
                JsonArray l = it.has("l") ? it.getAsJsonArray("l") : new JsonArray();
                out.add(l.size() == 0 ? (it.has("b") ? "banner" : "?") : l.get(0).getAsJsonArray().get(0).getAsString());
            }
        } catch (RuntimeException ignored) {
            return out;
        }
        return out;
    }

    private static boolean emptyInventory(ServerPlayer p) {
        for (int i = 0; i < p.m_150109_().m_6643_(); i++) {
            ItemStack s = p.m_150109_().m_8020_(i);
            if (s != null && !s.m_41619_()) {
                return false;
            }
        }
        return true;
    }

    private static String pf(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }

    private static void line(Consumer<String> note, String area, String what, boolean ok, String detail) {
        note.accept("V484 " + area + " " + what + ": " + pf(ok) + (detail == null || detail.isEmpty() ? "" : " | " + detail));
    }

    /** Every placed banner in the hall flies {@code base} with exactly {@code layers}; at least one spot. */
    private static boolean hallFlies(ServerPlayer p, DojoStore.Dojo d, String base, List<String> layers) {
        ServerLevel lvl = DojoWorld.level(p.m_20194_());
        if (lvl == null) {
            return false;
        }
        DojoPlacer.finishNow();
        List<DojoWorld.BannerSpot> placed = DojoWorld.bannersOn(lvl, d.plot);
        if (placed.isEmpty() || placed.size() != DojoWorld.bannerSpots(d).size()) {
            return false;
        }
        for (DojoWorld.BannerSpot b : placed) {
            if (!b.base().equals(base) || !b.patterns().equals(layers)) {
                return false;
            }
        }
        return true;
    }

    private static int hallBanners(ServerPlayer p, DojoStore.Dojo d) {
        ServerLevel lvl = DojoWorld.level(p.m_20194_());
        return lvl == null ? -1 : DojoWorld.bannersOn(lvl, d.plot).size();
    }

    /** Six layers, every one a different pattern and colour. */
    static final List<String> SIX = List.of("bs:15", "cr:0", "mc:14", "bo:11", "ts:4", "flo:1");

    /** Base {@code base} and the {@link #SIX} layers through the editor's own buttons. */
    private static void design(ServerPlayer p, String base) {
        act(p, "dojo_bedit_base", base);
        for (String l : SIX) {
            act(p, "dojo_bedit_add", l);
        }
    }

    // ------------------------------------------------------------------ J: founding step 3, no banner item

    static void founding(ServerPlayer player, Consumer<String> note, BiConsumer<String, List<GuiOpenPacket>> keep,
            List<ServerPlayer> fakes) {
        // The probe player walks it for the screenshots (step 3 and the editor while founding), then forgets it.
        typed(player, "dojo_found_name", "Probe Shot Hall");
        act(player, "dojo_found_style", "KI");
        shot(player, player, keep, "founding step 3", () -> open(player, "found_banner"));
        act(player, "dojo_bedit_base", "red");
        act(player, "dojo_bedit_add", "cr:4");
        act(player, "dojo_bedit_add", "bo:15");
        shot(player, player, keep, "founding editor", () -> open(player, "dojo_banner_edit"));
        DojoFounding.forget(player.m_20148_());

        ServerPlayer f = fake(player, "V484Founder", fakes);
        f.m_150109_().m_6211_();
        typed(f, "dojo_found_name", "V484 Free Banner");
        GuiOpenPacket step3 = last(act(f, "dojo_found_style", "KI"));
        String[] ed = button(step3, "page:dojo_banner_edit");
        String[] def = button(step3, "do:dojo_found_banner:default");
        line(note, "BANNER", "founding step 3 with an empty inventory offers Design a banner here and the default",
                step3 != null && "found_banner".equals(step3.pageId()) && ed != null && ed[1].contains("Design a banner here")
                        && def != null && def[1].contains("Use a default banner for now") && has(step3, "No banner item needed"),
                step3 == null ? "no page" : step3.pageId() + " " + buttons(step3));
        line(note, "BANNER", "founding step 3 has no carry-a-banner copy and no Check again",
                step3 != null && !has(step3, "You carry no banner") && !has(step3, "Check again") && !has(step3, "Craft any banner")
                        && !has(step3, "6 wool"), "");
        GuiOpenPacket edit = last(open(f, "dojo_banner_edit"));
        String[] save = button(edit, "do:dojo_bedit_save");
        line(note, "BANNER", "the editor opens while founding (no dojo yet) with a free Use this banner",
                edit != null && "dojo_banner_edit".equals(edit.pageId()) && has(edit, "Banner editor") && save != null
                        && save[1].contains("Use this banner (free)"), edit == null ? "no page" : buttons(edit));
        design(f, "purple");
        line(note, "BANNER", "founding design: base + 6 layers, a 7th refused",
                "lmbanner:purple/bs:15,cr:0,mc:14,bo:11,ts:4,flo:1".equals(DojoBannerEditor.draftIcon(f)),
                DojoBannerEditor.draftIcon(f));
        act(f, "dojo_bedit_add", "gra:2");
        boolean seventh = DojoBannerEditor.draftIcon(f).split(",").length == 6;
        GuiOpenPacket conf = last(act(f, "dojo_bedit_save", ""));
        line(note, "BANNER", "Use this banner goes to the founding confirm with the design (no item read)",
                seventh && conf != null && "found_confirm".equals(conf.pageId()) && has(conf, "Purple")
                        && !has(conf, "banner item stays"), conf == null ? "no page" : conf.pageId());
        act(f, "dojo_found_confirm", "");
        DojoStore.Dojo d = DojoService.dojoOf(f);
        line(note, "BANNER", "founded dojo record carries the designed banner; nothing was ever held or taken",
                d != null && "purple".equals(d.bannerBase) && SIX.equals(d.bannerPatterns) && emptyInventory(f)
                        && d.identityChangedAt == 0L, d == null ? "no dojo" : d.bannerBase + " " + d.bannerPatterns);
        if (d != null) {
            line(note, "BANNER", "the founded hall flies the designed banner on every spot",
                    hallFlies(f, d, "purple", SIX), hallBanners(f, d) + " banners placed");
            GuiOpenPacket home = last(open(f, "home"));
            line(note, "BANNER", "Dojo Home shows the designed banner", has(home, "Purple"),
                    home == null ? "no page" : home.pageId());
        }
    }

    // ------------------------------------------------------------------ J: the pick page, no banner item

    static void pickPage(ServerPlayer player, Consumer<String> note, BiConsumer<String, List<GuiOpenPacket>> keep,
            List<ServerPlayer> fakes) {
        // The probe player's banner page (default banner) for the screenshot.
        DojoStore.Dojo mine = DojoService.dojoOf(player);
        if (mine != null) {
            mine.bannerBase = "";
            mine.bannerPatterns = new ArrayList<>();
            shot(player, player, keep, "banner pick page", () -> open(player, "dojo_banner"));
        }

        ServerPlayer f = fake(player, "V484Picker", fakes);
        f.m_150109_().m_6211_();
        typed(f, "dojo_found_name", "V484 Pick Hall");
        act(f, "dojo_found_style", "MELEE");
        act(f, "dojo_found_banner", "default");
        act(f, "dojo_found_confirm", "");
        DojoStore.Dojo d = DojoService.dojoOf(f);
        if (d == null) {
            line(note, "BANNER", "a default-banner dojo is founded for the pick page", false, "no dojo");
            return;
        }
        d.points = 50;
        GuiOpenPacket pick = last(open(f, "dojo_banner"));
        String[] ed = button(pick, "page:dojo_banner_edit");
        line(note, "BANNER", "pick page with an empty inventory: Design a banner here, no carry copy, no Check again",
                pick != null && "dojo_banner".equals(pick.pageId()) && ed != null && has(pick, "No banner item needed")
                        && !has(pick, "Check again") && !has(pick, "Craft any banner") && !has(pick, "6 wool")
                        && button(pick, "do:dojo_banner_set:") == null, pick == null ? "no page" : buttons(pick));
        act(f, "dojo_bedit_reset", "");
        design(f, "cyan");
        GuiOpenPacket after = last(act(f, "dojo_bedit_save", ""));
        line(note, "BANNER", "editor Save from the pick page writes base + 6 layers, first banner free",
                "cyan".equals(d.bannerBase) && SIX.equals(d.bannerPatterns) && d.points == 50 && d.identityChangedAt == 0L
                        && emptyInventory(f), d.bannerBase + " " + d.bannerPatterns + " points=" + d.points
                        + (after == null ? "" : " -> " + after.pageId()));
        line(note, "BANNER", "the hall flies the saved banner on every spot", hallFlies(f, d, "cyan", SIX),
                hallBanners(f, d) + " banners placed");
        line(note, "BANNER", "the dojo record's banner icon is the editor design",
                "lmbanner:cyan/bs:15,cr:0,mc:14,bo:11,ts:4,flo:1".equals(DojoFounding.bannerIcon(d)), DojoFounding.bannerIcon(d));
        // The copy list stays a convenience: with a carried banner it is listed under the editor, item kept.
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("minecraft", "lime_banner"));
        f.m_150109_().m_36054_(new ItemStack(item));
        GuiOpenPacket carry = last(open(f, "dojo_banner"));
        line(note, "BANNER", "a carried banner is still offered as a copy below the editor",
                button(carry, "do:dojo_banner_set:") != null && button(carry, "page:dojo_banner_edit") != null
                        && has(carry, "You can also copy a banner you carry"), buttons(carry));
        f.m_150109_().m_6211_();
    }

    // ------------------------------------------------------------------ H: the maxed upgrade page

    static void maxedPage(ServerPlayer player, Consumer<String> note, BiConsumer<String, List<GuiOpenPacket>> keep,
            List<ServerPlayer> fakes) {
        DojoStore.Dojo d = dojo(player, "Probe Maxed Hall", "KI");
        d.upgrades.put(DojoCatalog.ARENA, DojoCatalog.MAX_ARENAS);
        d.upgrades.put(DojoCatalog.TROPHY, DojoCatalog.MAX_ROOM_TIER);
        d.upgrades.put(DojoCatalog.TRAINING, DojoCatalog.MAX_ROOM_TIER);
        d.upgrades.put(DojoCatalog.MEDITATION, 1);
        d.upgrades.put(DojoCatalog.CAPACITY, DojoCatalog.MAX_CAPACITY_LEVEL);
        d.points = 0;
        boolean pics = LmNativeNetwork.takesPictures(player);
        line(note, "MAXED", "the probe player's client takes pictures", pics, "");
        DojoState s = DojoState.of(d);

        GuiOpenPacket trophy = shot(player, player, keep, "maxed trophy hall",
                () -> open(player, "dojo_up:" + DojoCatalog.TROPHY));
        List<String> tp = pictures(trophy);
        line(note, "MAXED", "a maxed room shows its max-tier close-up alone, not the hall map",
                tp.equals(List.of("dojo/cu_trophy_hall_3_ki")) && !has(trophy, "Map: the starred")
                        && !has(trophy, "[Ring 1]") && has(trophy, "Fully built"), tp + " " + (trophy == null ? "no page" : trophy.pageId()));
        GuiOpenPacket arena = shot(player, player, keep, "maxed rings", () -> open(player, "dojo_up:" + DojoCatalog.ARENA));
        List<String> ap = pictures(arena);
        line(note, "MAXED", "six rings: the ring close-up in the hall's look, alone",
                ap.equals(List.of("dojo/cu_arena_" + s.tier + "_ki")) && !has(arena, "Map: the starred"), ap.toString());
        GuiOpenPacket med = shot(player, player, keep, "meditation tier 1 of 3",
                () -> open(player, "dojo_up:" + DojoCatalog.MEDITATION));
        List<String> mp = pictures(med);
        line(note, "MAXED", "a track with tiers left keeps Now / After / Where", mp.size() == 3
                && mp.get(0).equals("dojo/cu_meditation_1_ki") && mp.get(1).equals("dojo/cu_meditation_2_ki"), mp.toString());
        GuiOpenPacket cap = shot(player, player, keep, "maxed capacity", () -> open(player, "dojo_up:" + DojoCatalog.CAPACITY));
        line(note, "MAXED", "maxed capacity is unchanged: no picture, no map, Fully built",
                pictures(cap).isEmpty() && !has(cap, "Map: the starred") && has(cap, "Fully built. Nothing more to buy here.")
                        && has(cap, "Fully upgraded"), cap == null ? "no page" : buttons(cap));

        // A text client (no pictures) keeps the map on a maxed page.
        ServerPlayer t = fake(player, "V484TextMaster", fakes);
        DojoStore.Dojo dt = dojo(t, "V484 Text Hall", "KI");
        dt.upgrades.put(DojoCatalog.TROPHY, DojoCatalog.MAX_ROOM_TIER);
        GuiOpenPacket text = last(open(t, "dojo_up:" + DojoCatalog.TROPHY));
        line(note, "MAXED", "a text client keeps the map on the maxed page", !LmNativeNetwork.takesPictures(t)
                && pictures(text).isEmpty() && has(text, "Map: the starred") && has(text, "Fully built"),
                text == null ? "no page" : text.pageId());
    }

    // ------------------------------------------------------------------ I: tap a name to promote

    static void promote(ServerPlayer player, Consumer<String> note, BiConsumer<String, List<GuiOpenPacket>> keep,
            List<ServerPlayer> fakes) {
        DojoStore.Dojo d = DojoService.dojoOf(player);
        if (d == null) {
            d = dojo(player, "Probe Maxed Hall", "KI");
        }
        d.upgrades.put(DojoCatalog.CAPACITY, DojoCatalog.MAX_CAPACITY_LEVEL);
        ServerPlayer a = fake(player, "V484Alder", fakes);
        ServerPlayer b = fake(player, "V484Birch", fakes);
        ServerPlayer c = fake(player, "V484Cedar", fakes);
        ServerPlayer e = fake(player, "V484Elm", fakes);
        bond(player, a, 0L);
        bond(player, b, System.currentTimeMillis());          // joined now: the 3-day rule blocks
        bond(player, c, 0L);
        bond(player, e, 0L);
        String au = a.m_20148_().toString(), bu = b.m_20148_().toString(), cu = c.m_20148_().toString(),
                eu = e.m_20148_().toString();
        d.elites.clear();

        GuiOpenPacket members = shot(player, player, keep, "members", () -> open(player, "dojo_members"));
        String[] nameA = button(members, "page:dojo_promote_ask:" + au);
        String[] viewA = button(members, "page:dojo_member:" + au);
        line(note, "PROMOTE", "a disciple's name asks to make them an elite; View opens their page",
                nameA != null && nameA[1].contains("V484Alder") && viewA != null && "View".equals(viewA[1]),
                buttons(members));
        line(note, "PROMOTE", "no Make elite side button and no promote action on the list",
                button(members, "do:dojo_promote") == null && button(members, "label=Make elite") == null
                        && has(members, "Tap a disciple's name"), "");

        GuiOpenPacket blocked = shot(player, player, keep, "promote ask 3-day rule",
                () -> open(player, "dojo_promote_ask:" + bu));
        String[] lockB = button(blocked, "label=Make elite - not yet");
        line(note, "PROMOTE", "the 3-day rule shows on the page before the click, confirm dim",
                blocked != null && has(blocked, "can be an elite in") && lockB != null && "dim".equals(lockB[2])
                        && button(blocked, "do:dojo_promote_list") == null
                        && button(blocked, "page:dojo_member:" + bu) != null, blocked == null ? "no page" : buttons(blocked));
        GuiOpenPacket refused = last(act(player, "dojo_promote_list", bu));
        line(note, "PROMOTE", "a refused promote stays on the page with the reason inline", !d.elites.contains(bu)
                && refused != null && ("dojo_promote_ask:" + bu).equals(refused.pageId()) && has(refused, "can be an elite in"),
                refused == null ? "no page" : refused.pageId());

        GuiOpenPacket ask = shot(player, player, keep, "promote ask", () -> open(player, "dojo_promote_ask:" + au));
        String[] yes = button(ask, "do:dojo_promote_list:" + au);
        line(note, "PROMOTE", "the confirm page says what an elite can do and asks first",
                ask != null && has(ask, "Make V484Alder an elite?") && has(ask, "declare wars") && yes != null
                        && yes[1].contains("Yes, make elite") && !d.elites.contains(au), ask == null ? "no page" : buttons(ask));
        GuiOpenPacket done = last(act(player, "dojo_promote_list", au));
        line(note, "PROMOTE", "confirming makes them an elite and returns to Members with the result",
                d.elites.contains(au) && done != null && "dojo_members".equals(done.pageId()) && has(done, "is now an elite"),
                done == null ? "no page" : done.pageId());
        act(player, "dojo_promote_list", cu);
        GuiOpenPacket two = shot(player, player, keep, "members with two elites", () -> open(player, "dojo_members"));
        String[] stepA = button(two, "do:dojo_demote:" + au);
        line(note, "PROMOTE", "an elite's row: name opens their page, Step down stays",
                stepA != null && stepA[1].contains("Step down") && button(two, "page:dojo_member:" + au) != null
                        && button(two, "page:dojo_promote_ask:" + au) == null, buttons(two));
        GuiOpenPacket max = shot(player, player, keep, "promote ask two elites", () -> open(player, "dojo_promote_ask:" + eu));
        String[] lockE = button(max, "label=Make elite - 2 already");
        line(note, "PROMOTE", "with two elites the page says so and the confirm is dim", d.elites.size() == 2
                && has(max, "already has two elites") && lockE != null && "dim".equals(lockE[2]), max == null ? "" : buttons(max));
        act(player, "dojo_promote_list", eu);
        line(note, "PROMOTE", "a third elite is refused", !d.elites.contains(eu), d.elites.toString());

        // Non-master: the mentor-only gate.
        GuiOpenPacket outsider = last(open(a, "dojo_promote_ask:" + eu));
        String why = DojoService.promote(a, eu);
        line(note, "PROMOTE", "only the master promotes (page and action)", outsider != null
                && has(outsider, "Only the master manages members") && "Only the master names elites.".equals(why)
                && !d.elites.contains(eu), why);

        // View: the member page, with its own Make elite and war rights.
        GuiOpenPacket view = last(open(player, "dojo_member:" + eu));
        line(note, "PROMOTE", "View opens the member page; its own Make elite stays", view != null
                && ("dojo_member:" + eu).equals(view.pageId()) && button(view, "label=Make elite") != null
                && has(view, "War rights"), view == null ? "no page" : buttons(view));
        GuiOpenPacket elitePage = last(open(player, "dojo_member:" + au));
        line(note, "PROMOTE", "an elite's member page keeps war rights", button(elitePage, "do:dojo_warrights:" + au) != null,
                "");

        // Step down still works.
        act(player, "dojo_demote", au);
        line(note, "PROMOTE", "Step down still works", !d.elites.contains(au), d.elites.toString());
    }
}
