package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Canonical Ultra {@code screenId} values for {@code GuiOpenPacket}.
 *
 * <p><b>Zero-config rules for new menus:</b>
 * <ol>
 *   <li>Add the logical id to {@link #PROTOCOL_IDS} and {@link #BUILDER_IDS}.
 *   <li>Hub shim tunnels any id not in {@link #KAIPATH_REGISTERED_IDS} automatically.
 *   <li>Bare hub clicks resolve via {@link #bareOpenTarget(String)} for every protocol id
 *       (plus {@link #BARE_ALIASES}) - no per-system {@code switch} required.
 *   <li>All packets go through {@link UltraScreenSpec} -> {@link
 *       com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork#sendOpen}.
 * </ol>
 *
 * <p>KaiPath <b>1.3.1+</b> opens any {@code screenId} via {@code LmDynamicScreen} (no whitelist),
 * so the hub wire shim is off ({@link #KAIPATH_DYNAMIC_ANY_SCREEN_ID}). Stock 1.3.0 only
 * registered {@code hub}; keep {@link #KAIPATH_REGISTERED_IDS} for docs / rollback.
 */
public final class UltraScreenIds {
    /** IDs documented in {@code docs/KAIPATH_GUI_PROTOCOL.md} / LM builders. */
    public static final Set<String> PROTOCOL_IDS = Set.of(
            "hub",
            "difficulty",
            "rival",
            "spar",
            "prestige",
            "character",
            "skillcheck",
            "skills",
            "progression",
            "config",
            "admin");

    /**
     * KaiPath 1.3.1+ dynamic consumer: any screenId renders. When {@code true},
     * {@link #needsHubShim} is always false so wire ids stay logical.
     */
    public static final boolean KAIPATH_DYNAMIC_ANY_SCREEN_ID = true;

    /**
     * Legacy whitelist for stock KaiPath 1.3.0 ({@code hub} only). Unused while
     * {@link #KAIPATH_DYNAMIC_ANY_SCREEN_ID} is true; set that flag false to re-enable shim.
     */
    public static final Set<String> KAIPATH_REGISTERED_IDS = Set.of(
            "hub",
            "difficulty",
            "rival",
            "spar",
            "prestige",
            "character",
            "skillcheck",
            "skills",
            "progression",
            "config",
            "admin");

    /**
     * Every {@code UltraScreenSpec.of("...")} id used by Ultra*Screens builders.
     * Keep in sync with {@code UltraHubScreens}, {@code UltraDifficultyScreens}, etc.
     * New entries are auto-tunneled (shim) and auto-mapped for bare hub actions.
     */
    public static final Set<String> BUILDER_IDS = Set.of(
            "hub",
            "admin",
            "difficulty",
            "rival",
            "spar",
            "prestige",
            "character",
            "skillcheck",
            "skills",
            "progression",
            "config");

    /**
     * Extra bare hub click aliases -> canonical {@link #PROTOCOL_IDS} / {@link #BUILDER_IDS}.
     * Canonical ids map to themselves via {@link #bareOpenTarget(String)}.
     */
    public static final Map<String, String> BARE_ALIASES = Map.ofEntries(
            Map.entry("lm", "hub"),
            Map.entry("legacymechanics", "hub"),
            Map.entry("main", "hub"),
            Map.entry("diff", "difficulty"),
            Map.entry("sparring", "spar"),
            Map.entry("charservices", "character"),
            Map.entry("char", "character"),
            Map.entry("skill_check", "skillcheck"),
            Map.entry("skill", "skills"),
            Map.entry("prog", "progression"),
            Map.entry("cfgedit", "config"),
            Map.entry("configeditor", "config"));

    /** layoutJson / button-args key for the logical system when wire screenId is hub. */
    public static final String META_SYSTEM = "system";

    private UltraScreenIds() {}

    public static boolean isProtocolId(String screenId) {
        return screenId != null && PROTOCOL_IDS.contains(screenId.trim().toLowerCase(Locale.ROOT));
    }

    /** True when KaiPath can open this wire {@code screenId} without MissingScreen. */
    public static boolean kaipathCanOpen(String screenId) {
        if (KAIPATH_DYNAMIC_ANY_SCREEN_ID) {
            return true;
        }
        if (screenId == null || screenId.isBlank()) {
            return true;
        }
        return KAIPATH_REGISTERED_IDS.contains(screenId.trim().toLowerCase(Locale.ROOT));
    }

    /** True when this logical id must be tunneled through wire {@code hub}. */
    public static boolean needsHubShim(String logicalScreenId) {
        if (KAIPATH_DYNAMIC_ANY_SCREEN_ID) {
            return false;
        }
        return !kaipathCanOpen(logicalScreenId);
    }

    /** Builder / protocol ids that currently require the hub wire shim. */
    public static Set<String> hubShimTunnelIds() {
        Set<String> tunneled = new TreeSet<>();
        for (String id : BUILDER_IDS) {
            if (needsHubShim(id)) {
                tunneled.add(id);
            }
        }
        return tunneled;
    }

    /**
     * Map a bare hub tile id (no {@code :}) to a canonical open target, or null.
     * Every {@link #PROTOCOL_IDS} / {@link #BUILDER_IDS} name maps to itself; aliases apply.
     */
    public static String bareOpenTarget(String action) {
        if (action == null || action.isBlank() || action.indexOf(':') >= 0) {
            return null;
        }
        String key = action.trim().toLowerCase(Locale.ROOT);
        String canonical = BARE_ALIASES.getOrDefault(key, key);
        if (PROTOCOL_IDS.contains(canonical) || BUILDER_IDS.contains(canonical)) {
            return canonical;
        }
        return null;
    }

    public static String normalizeLogical(String system) {
        if (system == null || system.isBlank()) {
            return "hub";
        }
        String key = system.trim().toLowerCase(Locale.ROOT);
        String aliased = BARE_ALIASES.get(key);
        if (aliased != null) {
            return aliased;
        }
        return switch (key) {
            case "hub" -> "hub";
            case "difficulty" -> "difficulty";
            case "rival" -> "rival";
            case "spar" -> "spar";
            case "prestige" -> "prestige";
            case "character" -> "character";
            case "saga", "sagareset", "saga_reset" -> "character";
            case "progression" -> "progression";
            case "config" -> "config";
            case "admin" -> "admin";
            case "android_remove", "androidremove" -> "character";
            case "team" -> "difficulty";
            case "skillcheck", "skill_check" -> "skillcheck";
            case "skills", "skill" -> "skills";
            case "logs", "syslog" -> "hub";
            default -> {
                if (PROTOCOL_IDS.contains(key) || BUILDER_IDS.contains(key)) {
                    yield key;
                }
                yield key;
            }
        };
    }

    /**
     * Startup check: every builder id must be in {@link #PROTOCOL_IDS}. Failures are
     * {@code ERROR}. Logs tunneled ids for KaiPath compatibility.
     *
     * @return number of errors (0 = ok)
     */
    public static int validateAtStartup(Set<String> builderScreenIds) {
        int errors = 0;
        Set<String> found = new TreeSet<>();
        if (builderScreenIds != null) {
            for (String id : builderScreenIds) {
                if (id != null && !id.isBlank()) {
                    found.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String id : BUILDER_IDS) {
            found.add(id);
        }
        Set<String> unknown = new LinkedHashSet<>();
        for (String id : found) {
            if (!PROTOCOL_IDS.contains(id)) {
                unknown.add(id);
            }
        }
        if (!unknown.isEmpty()) {
            errors++;
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] Ultra screen builders use ids not in protocol list: {} - "
                            + "add them to UltraScreenIds.PROTOCOL_IDS and docs/KAIPATH_GUI_PROTOCOL.md",
                    AdaptiveDifficultyMod.MOD_ID,
                    unknown);
        }
        for (String id : BUILDER_IDS) {
            if (!PROTOCOL_IDS.contains(id)) {
                errors++;
                AdaptiveDifficultyMod.LOGGER.error(
                        "[{}] BUILDER_IDS entry \"{}\" missing from PROTOCOL_IDS",
                        AdaptiveDifficultyMod.MOD_ID,
                        id);
            }
        }
        Set<String> tunneled = hubShimTunnelIds();
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] Ultra screen ids: builders={} protocol={} kaipathRegistered={} bareAliases={}",
                AdaptiveDifficultyMod.MOD_ID,
                found,
                PROTOCOL_IDS,
                KAIPATH_REGISTERED_IDS,
                BARE_ALIASES.size());
        if (!tunneled.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Ultra submenu shim active: tunneling {} through hub screenId "
                            + "for KaiPath compatibility (auto for every BUILDER_IDS not in "
                            + "KAIPATH_REGISTERED_IDS)",
                    AdaptiveDifficultyMod.MOD_ID,
                    tunneled);
        }
        return errors;
    }

    /** Allowed verb prefixes on button / tab action ids (after optional {@code @screen:} strip). */
    public static boolean hasAllowedVerbPrefix(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        return action.startsWith("do:")
                || action.startsWith("tab:")
                || action.startsWith("nav:")
                || action.startsWith("open:")
                || action.startsWith("page:")
                || action.startsWith("input:")
                || action.startsWith("go:");
    }

    /**
     * Enforce the action-id contract before a button is encoded into {@code buttonsJson}.
     *
     * @throws IllegalArgumentException when the id is null/blank or not bare / verb-prefixed
     */
    public static void requireValidActionId(String screenId, String actionId, String buttonLabel) {
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Ultra button actionId empty on screen="
                            + screenId
                            + " label="
                            + safeLabel(buttonLabel));
        }
        String raw = actionId.trim();
        String semantic = raw;
        if (raw.startsWith("@")) {
            int colon = raw.indexOf(':', 1);
            if (colon <= 1 || colon >= raw.length() - 1) {
                throw new IllegalArgumentException(
                        "Ultra button actionId malformed @screen: prefix on screen="
                                + screenId
                                + " label="
                                + safeLabel(buttonLabel)
                                + " actionId="
                                + raw);
            }
            semantic = raw.substring(colon + 1);
        }
        if (semantic.isBlank()) {
            throw new IllegalArgumentException(
                    "Ultra button actionId empty after @screen: strip on screen="
                            + screenId
                            + " label="
                            + safeLabel(buttonLabel));
        }
        boolean bare = semantic.indexOf(':') < 0;
        if (bare || hasAllowedVerbPrefix(semantic)) {
            return;
        }
        throw new IllegalArgumentException(
                "Ultra button actionId invalid on screen="
                        + screenId
                        + " label="
                        + safeLabel(buttonLabel)
                        + " actionId="
                        + raw
                        + " - use a bare name (e.g. character) or open:/do:/tab:/nav:/page: prefix");
    }

    private static String safeLabel(String label) {
        return label == null || label.isBlank() ? "<no-label>" : label;
    }
}
