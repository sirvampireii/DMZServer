package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LM 4.7.71: the pages each player opened this session, newest on top, so Back goes to the page they actually came
 * from (LM native client; bridge clients keep the registry parent). Every menu page goes through
 * {@link UltraScreenSpec#send}, which records it here:
 * <ul>
 *   <li>the same page again (a reload after an action) changes nothing;</li>
 *   <li>a page already lower in the stack trims the stack back to it (going back up a path never piles up);</li>
 *   <li>a new page goes on top (at most {@value #MAX} deep).</li>
 * </ul>
 * Kept for the session: page reloads keep it, logout clears it.
 */
public final class NavHistory {
    private NavHistory() {}

    static final int MAX = 32;
    private static final Map<UUID, Deque<String>> STACKS = new ConcurrentHashMap<>();

    static String key(String screen, String page) {
        return (screen == null ? "" : screen) + "|" + (page == null ? "" : page);
    }

    /** {@code screen} page {@code page} was just shown to {@code player}. */
    public static void record(UUID player, String screen, String page) {
        if (player == null || screen == null) {
            return;
        }
        String k = key(screen, page);
        Deque<String> d = STACKS.computeIfAbsent(player, u -> new ArrayDeque<>());
        synchronized (d) {
            if (k.equals(d.peekFirst())) {
                return;
            }
            // Paging within one page family (Previous / Next, page 2 of a list) replaces the page: Back leaves the
            // family instead of stepping back through its pages.
            String top = d.peekFirst();
            if (top != null && siblings(top, k)) {
                d.pollFirst();
            }
            if (d.contains(k)) {
                while (!d.isEmpty() && !k.equals(d.peekFirst())) {
                    d.pollFirst();
                }
                return;
            }
            d.addFirst(k);
            while (d.size() > MAX) {
                d.pollLast();
            }
        }
    }

    /** Same screen, same first part of the page id, same registry parent: pages of one family. */
    static boolean siblings(String a, String b) {
        String[] x = split(a);
        String[] y = split(b);
        if (!x[0].equals(y[0]) || x[1].equals(y[1])) {
            return false;
        }
        String hx = x[1].contains(":") ? x[1].substring(0, x[1].indexOf(':')) : x[1];
        String hy = y[1].contains(":") ? y[1].substring(0, y[1].indexOf(':')) : y[1];
        if (!hx.equals(hy) || !x[1].contains(":") || !y[1].contains(":")) {
            return false;
        }
        String[] px = PageRegistry.parent(x[0], x[1]);
        String[] py = PageRegistry.parent(y[0], y[1]);
        return px != null && py != null && px[0].equals(py[0]) && px[1].equals(py[1]);
    }

    /** The page under {@code screen|page} (where Back from it goes), as {screen, page}, or null when none. */
    public static String[] previous(UUID player, String screen, String page) {
        Deque<String> d = player == null ? null : STACKS.get(player);
        if (d == null) {
            return null;
        }
        String k = key(screen, page);
        synchronized (d) {
            Iterator<String> it = d.iterator();
            boolean below = !k.equals(d.peekFirst());          // the page is not on top yet: the top is "previous"
            boolean first = true;
            while (it.hasNext()) {
                String e = it.next();
                // a page of the same family on top is replaced when this page is recorded (paging), so skip it
                boolean pagedOver = first && below && siblings(e, k);
                first = false;
                if (below && !e.equals(k) && !pagedOver) {
                    return split(e);
                }
                if (e.equals(k)) {
                    below = true;
                }
            }
        }
        return null;
    }

    /** Back from {@code screen|page}: drops it and returns the page under it ({screen, page}), or null. */
    public static String[] pop(UUID player, String screen, String page) {
        String[] prev = previous(player, screen, page);
        Deque<String> d = player == null ? null : STACKS.get(player);
        if (d != null && prev != null) {
            String target = key(prev[0], prev[1]);
            synchronized (d) {
                while (!d.isEmpty() && !target.equals(d.peekFirst())) {
                    d.pollFirst();
                }
            }
        }
        return prev;
    }

    /** Logout: the history ends with the session. */
    public static void clear(UUID player) {
        if (player != null) {
            STACKS.remove(player);
        }
    }

    /** Test rig and self-test: the stack, newest first. */
    public static java.util.List<String> snapshot(UUID player) {
        Deque<String> d = player == null ? null : STACKS.get(player);
        if (d == null) {
            return java.util.List.of();
        }
        synchronized (d) {
            return java.util.List.copyOf(d);
        }
    }

    private static String[] split(String k) {
        int bar = k.indexOf('|');
        return new String[] {k.substring(0, bar), k.substring(bar + 1)};
    }

    /** Self-test: push, reload, trim on revisit, Back. */
    public static String selfTest() {
        UUID u = UUID.nameUUIDFromBytes("lm-nav-selftest".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        clear(u);
        record(u, "hub", "main");
        record(u, "spar", "main");
        record(u, "spar", "dojo");
        record(u, "spar", "dojo");
        record(u, "spar", "dojo_upgrades");
        String[] back = previous(u, "spar", "dojo_upgrades");
        boolean ok = back != null && back[1].equals("dojo") && snapshot(u).size() == 4;
        record(u, "character", "form_preview:0");
        String[] before = previous(u, "character", "form_preview:1");   // Back label, worked out before the record
        ok &= before != null && before[1].equals("dojo_upgrades");
        record(u, "character", "form_preview:1");              // paging: replaces, Back leaves the family
        String[] fam = previous(u, "character", "form_preview:1");
        ok &= fam != null && fam[1].equals("dojo_upgrades");
        record(u, "spar", "main");                              // revisit: trims to it
        ok &= snapshot(u).size() == 2;
        String[] popped = pop(u, "spar", "main");
        ok &= popped != null && popped[0].equals("hub") && snapshot(u).size() == 1;
        clear(u);
        return ok ? "OK" : "FAIL " + snapshot(u);
    }
}
