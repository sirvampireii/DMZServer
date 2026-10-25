package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared Ultra name/arg pickers — two-column button grids with human labels (no raw IDs on
 * buttons when a display name is provided).
 *
 * <p>Pickers page instead of truncating: a list longer than one page gets Prev / Next, and
 * every item lands on exactly one page in its original order ({@link #pageSlice}).
 */
public final class UltraPickHelper {
    private UltraPickHelper() {}

    /** Pages needed to show {@code total} items at {@code perPage} per page (at least 1). */
    public static int pageCount(int total, int perPage) {
        int size = Math.max(1, perPage);
        return Math.max(1, (Math.max(0, total) + size - 1) / size);
    }

    /** Clamp a requested page index into {@code [0, pages)}. */
    public static int clampPage(int page, int pages) {
        return Math.min(Math.max(1, pages) - 1, Math.max(0, page));
    }

    /**
     * Items on page {@code page} (0-based), same order as {@code items}. Never null. A page past
     * the end is empty (not the last page again), so lists sharing one page index never repeat.
     */
    public static <T> List<T> pageSlice(List<T> items, int perPage, int page) {
        int size = Math.max(1, perPage);
        if (items == null || page < 0 || (long) page * size >= items.size()) {
            return new ArrayList<>();
        }
        int from = page * size;
        int to = Math.min(items.size(), from + size);
        return new ArrayList<>(items.subList(from, to));
    }

    /** {@code base} for page 0, {@code base:N} after — the page arg Prev / Next reopen. */
    public static String pageArg(String base, int page) {
        return page > 0 ? base + ":" + page : base;
    }

    /** {@code pick_release:2} → {@code pick_release}; a page without a trailing index is unchanged. */
    public static String pageBase(String page) {
        if (page == null) {
            return "";
        }
        int colon = page.lastIndexOf(':');
        return colon > 0 && isDigits(page.substring(colon + 1)) ? page.substring(0, colon) : page;
    }

    /** {@code pick_release:2} → 2; no trailing index → 0. */
    public static int pageIndex(String page) {
        if (page == null) {
            return 0;
        }
        int colon = page.lastIndexOf(':');
        if (colon <= 0 || !isDigits(page.substring(colon + 1))) {
            return 0;
        }
        try {
            return Integer.parseInt(page.substring(colon + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty() || s.length() > 6) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** "Page X of Y" body line, only when the list spans pages. Call before buttonsAfterLines(). */
    public static void pageLabel(UltraScreenSpec spec, int page, int pages) {
        if (spec != null && pages > 1) {
            spec.line(UltraStyle.LABEL + "Page " + (page + 1) + " of " + pages);
        }
    }

    /**
     * Prev / Next row, laid out like the Character Services pages: Prev (dim) from page 2 on,
     * Next only while more items remain. Nothing is placed for a single page.
     */
    public static void placePrevNext(UltraScreenSpec spec, String pageBase, int page, int pages) {
        if (spec == null || pageBase == null || pageBase.isBlank()) {
            return;
        }
        if (page > 0) {
            spec.buttonLeft(
                    "page:" + pageArg(pageBase, page - 1), UltraStyle.dim("Prev"), UltraStyle.STYLE_DIM);
        }
        if (page + 1 < pages) {
            String next = "page:" + pageArg(pageBase, page + 1);
            if (page > 0) {
                spec.buttonRight(next, UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            } else {
                spec.buttonLeft(next, UltraStyle.LABEL + "Next", UltraStyle.STYLE_NORMAL);
            }
            spec.rowGap();
        } else if (page > 0) {
            spec.rowGap();
        }
    }

    /**
     * Place one page of choices ({@code perPage} each) as left/right buttons, then Prev / Next
     * reopening {@code pageBase}. Each choice is either a bare name (used as both label and
     * {@code do:} arg) or {@code arg|Label}. Add {@link #pageLabel} before buttonsAfterLines().
     */
    public static void placeNameButtons(
            UltraScreenSpec spec,
            String doAction,
            List<String> choices,
            int perPage,
            String pageBase,
            int page) {
        if (spec == null || doAction == null || doAction.isBlank() || choices == null) {
            return;
        }
        List<String> valid = new ArrayList<>();
        for (String raw : choices) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            int bar = raw.indexOf('|');
            if ((bar > 0 ? raw.substring(0, bar) : raw).isBlank()) {
                continue;
            }
            valid.add(raw);
        }
        int pages = pageCount(valid.size(), perPage);
        int pg = clampPage(page, pages);
        int placed = 0;
        for (String raw : pageSlice(valid, perPage, pg)) {
            String arg;
            String label;
            int bar = raw.indexOf('|');
            if (bar > 0) {
                arg = raw.substring(0, bar).trim();
                label = raw.substring(bar + 1).trim();
            } else {
                arg = raw.trim();
                label = arg;
            }
            if (arg.isBlank()) {
                continue;
            }
            if (label.isBlank()) {
                label = arg;
            }
            String action = "do:" + doAction + ":" + arg;
            String styled = UltraStyle.confirm(label);
            if (placed % 2 == 0) {
                spec.buttonLeft(action, styled, "confirm");
            } else {
                spec.buttonRight(action, styled, "confirm");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
        if (placed == 0) {
            spec.line(UltraStyle.dim("Nobody is available right now."));
        }
        placePrevNext(spec, pageBase, pg, pages);
    }

    /** Navigation buttons that reopen a page on the current system. */
    public static void placePageButtons(UltraScreenSpec spec, String... pageLabelPairs) {
        if (spec == null || pageLabelPairs == null) {
            return;
        }
        int placed = 0;
        for (String pair : pageLabelPairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int bar = pair.indexOf('|');
            String page = bar > 0 ? pair.substring(0, bar).trim() : pair.trim();
            String label = bar > 0 ? pair.substring(bar + 1).trim() : page;
            if (page.isBlank()) {
                continue;
            }
            String action = "page:" + page;
            if (placed % 2 == 0) {
                spec.buttonLeft(action, label, "normal");
            } else {
                spec.buttonRight(action, label, "normal");
                spec.rowGap();
            }
            placed++;
        }
        if (placed % 2 == 1) {
            spec.rowGap();
        }
    }
}
