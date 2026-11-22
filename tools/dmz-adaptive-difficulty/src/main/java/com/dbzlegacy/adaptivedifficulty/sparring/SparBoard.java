package com.dbzlegacy.adaptivedifficulty.sparring;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.7.64 spar board on Sparring Home: players list themselves as looking for a spar for 15 minutes with what
 * they want from it (TP, Potential Unlock, or anything), see who else is looking with what a spar with each would
 * pay them per hit, and ask with one tap. The asked player gets a chat line that opens the board, where "Say yes"
 * tells the asker. A spar still starts the usual way (both players hit each other); starting one takes both off
 * the board. Everything here is session memory, per account; nothing is saved and nothing is paid.
 */
public final class SparBoard {
    private SparBoard() {}

    public static final long LIST_MS = 15L * 60L * 1000L;
    public static final long ASK_MS = 5L * 60L * 1000L;
    /** One ask per pair this often, so nobody can spam a player's chat. */
    public static final long ASK_COOLDOWN_MS = 30_000L;
    /** Board rows shown on Sparring Home (closest match first). */
    public static final int SHOWN = 4;

    public static final String ACT_LIST = "spar_list";
    public static final String ACT_UNLIST = "spar_unlist";
    public static final String ACT_WANT = "spar_want";
    public static final String ACT_ASK = "spar_ask";
    public static final String ACT_YES = "spar_yes";

    /** What a player wants out of a spar. Cycles TP -> Potential Unlock -> Anything. */
    public enum Want {
        TP("TP"), POTENTIAL("Potential Unlock"), ANY("Anything");

        public final String label;

        Want(String label) {
            this.label = label;
        }

        public Want next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    record Listing(String name, Want want, long until) {}

    record Ask(String name, long at) {}

    private static final Map<UUID, Listing> LISTED = new ConcurrentHashMap<>();
    /** The player's chosen want, kept while they are off the board too (session only). */
    private static final Map<UUID, Want> WANT = new ConcurrentHashMap<>();
    /** target -> (asker -> ask). */
    private static final Map<UUID, Map<UUID, Ask>> ASKS = new ConcurrentHashMap<>();

    public static Want want(UUID id) {
        return id == null ? Want.TP : WANT.getOrDefault(id, Want.TP);
    }

    /** Milliseconds left on the board, 0 when not listed. */
    public static long listedLeft(UUID id, long now) {
        Listing l = id == null ? null : LISTED.get(id);
        if (l == null) {
            return 0L;
        }
        long left = l.until() - now;
        if (left <= 0L) {
            LISTED.remove(id);
            return 0L;
        }
        return left;
    }

    public static void list(ServerPlayer p) {
        UUID id = p.m_20148_();
        LISTED.put(id, new Listing(p.m_7755_().getString(), want(id), System.currentTimeMillis() + LIST_MS));
    }

    public static void unlist(UUID id) {
        if (id != null) {
            LISTED.remove(id);
        }
    }

    public static void cycleWant(ServerPlayer p) {
        UUID id = p.m_20148_();
        Want next = want(id).next();
        WANT.put(id, next);
        LISTED.computeIfPresent(id, (k, l) -> new Listing(l.name(), next, l.until()));
    }

    /** Someone on the board, as the viewer sees them. */
    public record Entry(UUID id, String name, Want want, float match, long tpPerHit) {}

    /** Online players on the board except the viewer, closest match first. */
    public static List<Entry> looking(ServerPlayer viewer) {
        List<Entry> out = new ArrayList<>();
        MinecraftServer server = viewer == null ? null : viewer.m_20194_();
        if (server == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(LISTED.keySet())) {
            if (id.equals(viewer.m_20148_()) || listedLeft(id, now) <= 0L) {
                continue;
            }
            ServerPlayer other = server.m_6846_().m_11259_(id);
            if (other == null) {
                continue;
            }
            Listing l = LISTED.get(id);
            if (l == null) {
                continue;
            }
            float[] preview = preview(viewer, other);
            out.add(new Entry(id, other.m_7755_().getString(), l.want(), preview[0], Math.round(preview[1])));
        }
        out.sort(Comparator.comparingDouble((Entry e) -> -e.match()).thenComparing(Entry::name));
        return out;
    }

    /**
     * {closeness multiplier, TP per hit for {@code viewer}} at the start of a spar with {@code partner}: the base
     * hit TP times the same multiplier product a real hit uses (no session, combo or style bonus yet, so the
     * real number grows as the spar goes on), times the global spar TP rate.
     */
    public static float[] preview(ServerPlayer viewer, ServerPlayer partner) {
        try {
            SparCombat.TrainingValues a = SparCombat.liveValues(viewer);
            SparCombat.TrainingValues b = SparCombat.liveValues(partner);
            if (a == null || b == null) {
                return new float[] {1f, 0f};
            }
            float match = SparCombat.rivalMultiplier(a.bp, b.bp);
            float tp = SparCombat.BASE_TP_PER_HIT * SparCombat.buildTotalMultiplier(viewer, partner, null, a, b)
                    * SparCombat.GLOBAL_TP_GAIN_MULT;
            return new float[] {match, Math.min(tp, SparCombat.MAX_TP_PER_ACTION)};
        } catch (Throwable t) {
            return new float[] {1f, 0f};
        }
    }

    /** Asks waiting for {@code target} (newest first), expired ones dropped. */
    public static List<Map.Entry<UUID, String>> asksFor(UUID target) {
        List<Map.Entry<UUID, String>> out = new ArrayList<>();
        Map<UUID, Ask> m = target == null ? null : ASKS.get(target);
        if (m == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        m.entrySet().removeIf(e -> now - e.getValue().at() > ASK_MS);
        m.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, Ask> e) -> -e.getValue().at()))
                .forEach(e -> out.add(Map.entry(e.getKey(), e.getValue().name())));
        return out;
    }

    /** True when {@code asker} already asked {@code target} within the cooldown. */
    public static boolean asked(UUID asker, UUID target) {
        Map<UUID, Ask> m = target == null ? null : ASKS.get(target);
        Ask a = m == null || asker == null ? null : m.get(asker);
        return a != null && System.currentTimeMillis() - a.at() < ASK_COOLDOWN_MS;
    }

    /** Ask {@code targetRaw} (a UUID) to spar. Returns the line for the asker's menu, never throws. */
    public static String ask(ServerPlayer asker, String targetRaw) {
        UUID target = parse(targetRaw);
        ServerPlayer other = target == null || asker.m_20194_() == null ? null
                : asker.m_20194_().m_6846_().m_11259_(target);
        if (other == null || other == asker) {
            return "That player is not online any more.";
        }
        if (asked(asker.m_20148_(), target)) {
            return "Just asked " + other.m_7755_().getString() + ". Give them a moment.";
        }
        ASKS.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                .put(asker.m_20148_(), new Ask(asker.m_7755_().getString(), System.currentTimeMillis()));
        String want = want(asker.m_20148_()).label;
        Component line = Component.m_237113_(LmChat.info("Spar", UltraStyle.VALUE + asker.m_7755_().getString()
                + UltraStyle.LABEL + " wants to spar with you (they want " + want + "). "))
                .m_7220_(Component.m_237113_(UltraStyle.GREEN + "[Open the spar board]").m_130948_(Style.f_131099_
                        .m_131142_(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lm open spar"))
                        .m_131144_(new HoverEvent(HoverEvent.Action.f_130831_,
                                Component.m_237113_("See who is looking and say yes")))));
        other.m_213846_(line);
        return "Asked " + other.m_7755_().getString() + ". 5 minutes to say yes.";
    }

    /** {@code target} says yes to {@code askerRaw}: tell both how to start. */
    public static String yes(ServerPlayer target, String askerRaw) {
        UUID asker = parse(askerRaw);
        Map<UUID, Ask> m = ASKS.get(target.m_20148_());
        Ask a = m == null || asker == null ? null : m.remove(asker);
        ServerPlayer other = asker == null || target.m_20194_() == null ? null
                : target.m_20194_().m_6846_().m_11259_(asker);
        if (a == null || other == null) {
            return "That ask is no longer open.";
        }
        DmzRewards.msg(other, LmChat.ok("Spar", UltraStyle.VALUE + target.m_7755_().getString()
                + UltraStyle.LABEL + " said yes. Meet up and trade hits to start the spar."));
        return "Told " + other.m_7755_().getString() + ". Meet up and trade hits.";
    }

    /** A spar began: both players come off the board and their asks to each other close. */
    public static void onSessionStart(ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null) {
            return;
        }
        LISTED.remove(a.m_20148_());
        LISTED.remove(b.m_20148_());
        Map<UUID, Ask> ma = ASKS.get(a.m_20148_());
        if (ma != null) {
            ma.remove(b.m_20148_());
        }
        Map<UUID, Ask> mb = ASKS.get(b.m_20148_());
        if (mb != null) {
            mb.remove(a.m_20148_());
        }
    }

    public static void onLogout(UUID id) {
        if (id == null) {
            return;
        }
        LISTED.remove(id);
        WANT.remove(id);
        ASKS.remove(id);
        for (Map<UUID, Ask> m : ASKS.values()) {
            m.remove(id);
        }
    }

    /**
     * Spar board button. Returns the menu line to show, or {@code null} when {@code action} is not a spar board
     * action.
     */
    public static String apply(ServerPlayer p, String action, String arg) {
        if (p == null || action == null) {
            return null;
        }
        switch (action) {
            case ACT_LIST -> {
                list(p);
                return "Listed for 15 minutes. Others see you here.";
            }
            case ACT_UNLIST -> {
                unlist(p.m_20148_());
                return "You're off the board.";
            }
            case ACT_WANT -> {
                cycleWant(p);
                return "Looking for: " + want(p.m_20148_()).label + ".";
            }
            case ACT_ASK -> {
                return ask(p, arg);
            }
            case ACT_YES -> {
                return yes(p, arg);
            }
            default -> {
                return null;
            }
        }
    }

    /** {@code 14:59} for milliseconds left. */
    public static String clock(long ms) {
        long s = Math.max(0L, (ms + 999L) / 1000L);
        return (s / 60L) + ":" + String.format(Locale.ROOT, "%02d", s % 60L);
    }

    /** {@code x2.5} for a closeness multiplier. */
    public static String mult(float m) {
        double r = Math.round(m * 10.0) / 10.0;
        return "x" + (r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r));
    }

    private static UUID parse(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (Want.TP.next() != Want.POTENTIAL || Want.POTENTIAL.next() != Want.ANY || Want.ANY.next() != Want.TP) {
            bad.add("spar want cycles TP, Potential Unlock, Anything");
        }
        if (!"15:00".equals(clock(LIST_MS)) || !"0:01".equals(clock(1L)) || !"0:00".equals(clock(0L))) {
            bad.add("board clock");
        }
        if (!"x2.5".equals(mult(2.5f)) || !"x3".equals(mult(3.0f)) || !"x1".equals(mult(1.0f))) {
            bad.add("match multiplier text");
        }
        UUID id = UUID.nameUUIDFromBytes("sparboard:selftest".getBytes());
        LISTED.put(id, new Listing("Test", Want.TP, System.currentTimeMillis() - 1L));
        if (listedLeft(id, System.currentTimeMillis()) != 0L || LISTED.containsKey(id)) {
            bad.add("an expired listing leaves the board");
        }
        if (parse("not-a-uuid") != null || parse(id.toString()) == null) {
            bad.add("ask target parsing");
        }
        return bad;
    }
}
