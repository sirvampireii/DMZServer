package com.dbzlegacy.adaptivedifficulty.net.gui;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player Ultra GUI optional-feature advertisement (KaiPath → LM).
 *
 * <p>Known optional features LM already sends and expects clients to honor:
 *
 * <ul>
 *   <li>{@link #FEATURE_STYLE} — button {@code style} ({@code normal}/{@code confirm}/
 *       {@code danger}/{@code dim})
 *   <li>{@link #FEATURE_ACTIVE_TAB} — {@link GuiOpenPacket} field 5 {@code activeTab} highlight
 *   <li>{@link #FEATURE_TITLE_ALIGN} — {@code layoutJson.titleAlign} ({@code center}/{@code left})
 *   <li>{@link #FEATURE_PLAYER_MODEL} — {@code layoutJson.playerModel} entity preview
 *   <li>{@link #FEATURE_TEXT_INPUT} — GuiOpenPacket field 11 {@code inputsJson} text fields
 *   <li>{@link #FEATURE_HEADER_BOTTOM} — {@code layoutJson.headerBottom} (title+divider above tabs)
 *   <li>{@link #FEATURE_ACCENT} — {@code layoutJson.accent} (panel/title/activeTab; buttons emit no color keys)
 *   <li>{@link #FEATURE_FIXED_FOOTER} — button {@code footer:true} pinned chrome
 *   <li>{@link #FEATURE_PALETTE} — {@code layoutJson.palette} named theme
 *   <li>{@link #FEATURE_SOUND} — button {@code sound} click id
 *   <li>{@link #FEATURE_BUTTON_SKIN} — button {@code buttonSkin} chrome enable token
 *   <li>{@link #FEATURE_FRAME} — {@code layoutJson.frame} panel border enable token
 *   <li>{@link #FEATURE_PAGED_BOOK} — {@code inputsJson} type {@code paged_book}
 *   <li>{@link #FEATURE_PORTRAIT_FRAME} — {@code playerModel.portraitFrame}
 *   <li>{@link #FEATURE_COMBINED_FORMS} — {@code playerModel.parts.form} on one preview
 *   <li>{@link #FEATURE_ICON} — optional button/tab {@code icon} (item id or texture path)
 * </ul>
 *
 * <p>Clients that never send {@link GuiFeaturesPacket} are treated as the KaiPath <b>1.3.1
 * baseline</b>: dynamic layout/buttons only. KaiPath <b>1.3.17</b> advertises the fifteen
 * chrome ids. KaiPath <b>1.3.22</b> also advertises {@link #FEATURE_ICON}. KaiPath
 * <b>1.3.29</b> (recommended) also advertises {@link #FEATURE_BODY_PARTS}.
 * {@link #PENDING_NEXT} currently holds {@link #FEATURE_BODY_PARTS_SYNC} (LM message 3
 * world sync — pending on JLDK). A client that lacks {@code bodyParts} still draws the
 * base entity and ignores the array. LM {@code footer:true} emission matches 1.3.17.
 */
public final class UltraClientFeatures {
    public static final String FEATURE_STYLE = "style";
    public static final String FEATURE_ACTIVE_TAB = "activetab";
    public static final String FEATURE_TITLE_ALIGN = "titlealign";
    public static final String FEATURE_PLAYER_MODEL = "playermodel";
    public static final String FEATURE_TEXT_INPUT = "textinput";
    public static final String FEATURE_HEADER_BOTTOM = "headerbottom";
    public static final String FEATURE_ACCENT = "accent";
    /** Normalized id for button {@code footer:true} pinned chrome (wire: {@code fixedFooter}). */
    public static final String FEATURE_FIXED_FOOTER = "fixedfooter";

    /** Wire / doc spellings accepted for {@link #FEATURE_ACTIVE_TAB}. */
    public static final String FEATURE_ACTIVE_TAB_DOC = "activeTab";
    /** Wire / doc spelling for {@link #FEATURE_TITLE_ALIGN}. */
    public static final String FEATURE_TITLE_ALIGN_DOC = "titleAlign";
    /** Wire / doc spelling for {@link #FEATURE_PLAYER_MODEL}. */
    public static final String FEATURE_PLAYER_MODEL_DOC = "playerModel";
    /** Wire / doc spelling for {@link #FEATURE_TEXT_INPUT}. */
    public static final String FEATURE_TEXT_INPUT_DOC = "textInput";
    /** Wire / doc spelling for {@link #FEATURE_HEADER_BOTTOM}. */
    public static final String FEATURE_HEADER_BOTTOM_DOC = "headerBottom";
    /** Wire / doc spelling for {@link #FEATURE_ACCENT}. */
    public static final String FEATURE_ACCENT_DOC = "accent";
    /** Wire / doc spelling for {@link #FEATURE_FIXED_FOOTER}. */
    public static final String FEATURE_FIXED_FOOTER_DOC = "fixedFooter";
    /** Normalized id for {@code layoutJson.palette}. */
    public static final String FEATURE_PALETTE = "palette";
    /** Normalized id for button {@code sound}. */
    public static final String FEATURE_SOUND = "sound";
    /** Normalized id for button {@code buttonSkin}. */
    public static final String FEATURE_BUTTON_SKIN = "buttonskin";
    /** Normalized id for {@code layoutJson.frame}. */
    public static final String FEATURE_FRAME = "frame";
    /** Normalized id for advertise {@code pagedBook} ({@code inputsJson} type {@code paged_book}). */
    public static final String FEATURE_PAGED_BOOK = "pagedbook";
    /** Normalized id for {@code playerModel.portraitFrame}. */
    public static final String FEATURE_PORTRAIT_FRAME = "portraitframe";
    /**
     * One preview can combine the base entity with {@code playerModel.parts.form}.
     * Generic — any mod may send form string ids. Not a DMZ-only channel.
     */
    public static final String FEATURE_COMBINED_FORMS = "combined.forms.v1";
    /** Normalized id for optional button/tab {@code icon} (advertised on KaiPath 1.3.22). */
    public static final String FEATURE_ICON = "icon";
    /**
     * Normalized id for {@code playerModel.parts.bodyParts}. Shipped on KaiPath 1.3.29
     * (not on KaiPath 1.3.17–1.3.22). Clients that omit it still draw the entity.
     */
    public static final String FEATURE_BODY_PARTS = "bodyparts";
    /**
     * Normalized id for GuiModelSyncPacket (message 3) — authoritative equipped body-parts
     * state for world rendering. Not shipped on any KaiPath yet (pending on JLDK); LM only
     * sends message 3 to clients that advertise it, so current clients are unaffected.
     */
    public static final String FEATURE_BODY_PARTS_SYNC = "bodypartssync";

    /** Wire / doc spelling for {@link #FEATURE_PALETTE}. */
    public static final String FEATURE_PALETTE_DOC = "palette";
    /** Wire / doc spelling for {@link #FEATURE_SOUND}. */
    public static final String FEATURE_SOUND_DOC = "sound";
    /** Wire / doc spelling for {@link #FEATURE_BUTTON_SKIN}. */
    public static final String FEATURE_BUTTON_SKIN_DOC = "buttonSkin";
    /** Wire / doc spelling for {@link #FEATURE_FRAME}. */
    public static final String FEATURE_FRAME_DOC = "frame";
    /** Wire / doc spelling for {@link #FEATURE_PAGED_BOOK}. */
    public static final String FEATURE_PAGED_BOOK_DOC = "pagedBook";
    /** Wire / doc spelling for {@link #FEATURE_PORTRAIT_FRAME}. */
    public static final String FEATURE_PORTRAIT_FRAME_DOC = "portraitFrame";
    /** Wire / doc spelling for {@link #FEATURE_COMBINED_FORMS}. */
    public static final String FEATURE_COMBINED_FORMS_DOC = "combined.forms.v1";
    /** Wire / doc spelling for {@link #FEATURE_ICON}. */
    public static final String FEATURE_ICON_DOC = "icon";
    /** Wire / doc spelling for {@link #FEATURE_BODY_PARTS}. */
    public static final String FEATURE_BODY_PARTS_DOC = "bodyParts";
    /** Wire / doc spelling for {@link #FEATURE_BODY_PARTS_SYNC}. */
    public static final String FEATURE_BODY_PARTS_SYNC_DOC = "bodyPartsSync";

    /** Optional features the LM bridge expects a fully conforming 1.3.29 client to advertise. */
    public static final Set<String> EXPECTED_OPTIONAL =
            Set.of(
                    FEATURE_STYLE,
                    FEATURE_ACTIVE_TAB,
                    FEATURE_TITLE_ALIGN,
                    FEATURE_PLAYER_MODEL,
                    FEATURE_TEXT_INPUT,
                    FEATURE_HEADER_BOTTOM,
                    FEATURE_ACCENT,
                    FEATURE_FIXED_FOOTER,
                    FEATURE_PALETTE,
                    FEATURE_SOUND,
                    FEATURE_BUTTON_SKIN,
                    FEATURE_FRAME,
                    FEATURE_PAGED_BOOK,
                    FEATURE_PORTRAIT_FRAME,
                    FEATURE_COMBINED_FORMS,
                    FEATURE_ICON,
                    FEATURE_BODY_PARTS);

    /**
     * Features KaiPath <b>1.3.29</b> already advertises/honors (1.3.17 chrome, 1.3.22
     * {@code icon}, 1.3.29 {@code bodyParts}). Missing any of these is a real client gap
     * (wrong jar / old client).
     */
    public static final Set<String> SHIPPED_ON_RECOMMENDED =
            Set.of(
                    FEATURE_STYLE,
                    FEATURE_ACTIVE_TAB,
                    FEATURE_TITLE_ALIGN,
                    FEATURE_PLAYER_MODEL,
                    FEATURE_TEXT_INPUT,
                    FEATURE_HEADER_BOTTOM,
                    FEATURE_ACCENT,
                    FEATURE_FIXED_FOOTER,
                    FEATURE_PALETTE,
                    FEATURE_SOUND,
                    FEATURE_BUTTON_SKIN,
                    FEATURE_FRAME,
                    FEATURE_PAGED_BOOK,
                    FEATURE_PORTRAIT_FRAME,
                    FEATURE_COMBINED_FORMS,
                    FEATURE_ICON,
                    FEATURE_BODY_PARTS);

    /**
     * Optional ids the next KaiPath release might add. {@code bodyPartsSync} (message 3
     * world equipped-parts sync) pending on JLDK since LM 4.6.209. Guitest reports these
     * as {@code pendingNext=} (info), not a shipped-client warning.
     */
    public static final Set<String> PENDING_NEXT = Set.of(FEATURE_BODY_PARTS_SYNC);

    private static final Map<UUID, State> BY_PLAYER = new ConcurrentHashMap<>();

    private UltraClientFeatures() {}

    public record State(boolean advertised, Set<String> features, String rawJson) {
        public State {
            features = features == null
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(features));
            rawJson = rawJson == null ? "[]" : rawJson;
        }

        public boolean has(String feature) {
            if (feature == null || feature.isBlank()) {
                return false;
            }
            String key = normalize(feature);
            return features.contains(key);
        }
    }

    /** Normalize feature ids ({@code activeTab} → {@code activetab}). */
    public static String normalize(String feature) {
        if (feature == null) {
            return "";
        }
        return feature.trim().toLowerCase(Locale.ROOT);
    }

    public static void recordAdvertisement(ServerPlayer player, Set<String> features, String rawJson) {
        if (player == null) {
            return;
        }
        Set<String> normalized = new LinkedHashSet<>();
        if (features != null) {
            for (String f : features) {
                String n = normalize(f);
                if (!n.isEmpty()) {
                    normalized.add(n);
                }
            }
        }
        State state = new State(true, normalized, rawJson == null ? "[]" : rawJson);
        BY_PLAYER.put(player.m_20148_(), state);
        LmLog.event(
                "[{}] Ultra client features ← player={} advertised=true features={} raw={}",
                AdaptiveDifficultyMod.MOD_ID,
                safeName(player),
                normalized,
                state.rawJson());
    }

    /**
     * Current state. If the client never advertised, returns baseline (empty features,
     * {@code advertised=false}).
     */
    public static State get(ServerPlayer player) {
        if (player == null) {
            return new State(false, Set.of(), "[]");
        }
        return BY_PLAYER.getOrDefault(player.m_20148_(), new State(false, Set.of(), "[]"));
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            BY_PLAYER.remove(player.m_20148_());
        }
    }

    public static void clear(UUID id) {
        if (id != null) {
            BY_PLAYER.remove(id);
        }
    }

    /** Optional features from {@link #EXPECTED_OPTIONAL} the client does not report. */
    public static List<String> missingOptional(State state) {
        List<String> missing = new ArrayList<>();
        missing.addAll(missingShipped(state));
        missing.addAll(missingPending(state));
        return missing;
    }

    /** Gaps vs KaiPath 1.3.17 shipped set — warn on login/guitest. */
    public static List<String> missingShipped(State state) {
        List<String> missing = new ArrayList<>();
        if (state == null || !state.has(FEATURE_STYLE)) {
            missing.add(FEATURE_STYLE);
        }
        if (state == null || !state.has(FEATURE_ACTIVE_TAB)) {
            missing.add(FEATURE_ACTIVE_TAB_DOC);
        }
        if (state == null || !state.has(FEATURE_TITLE_ALIGN)) {
            missing.add(FEATURE_TITLE_ALIGN_DOC);
        }
        if (state == null || !state.has(FEATURE_PLAYER_MODEL)) {
            missing.add(FEATURE_PLAYER_MODEL_DOC);
        }
        if (state == null || !state.has(FEATURE_TEXT_INPUT)) {
            missing.add(FEATURE_TEXT_INPUT_DOC);
        }
        if (state == null || !state.has(FEATURE_HEADER_BOTTOM)) {
            missing.add(FEATURE_HEADER_BOTTOM_DOC);
        }
        if (state == null || !state.has(FEATURE_ACCENT)) {
            missing.add(FEATURE_ACCENT_DOC);
        }
        if (state == null || !state.has(FEATURE_FIXED_FOOTER)) {
            missing.add(FEATURE_FIXED_FOOTER_DOC);
        }
        if (state == null || !state.has(FEATURE_PALETTE)) {
            missing.add(FEATURE_PALETTE_DOC);
        }
        if (state == null || !state.has(FEATURE_SOUND)) {
            missing.add(FEATURE_SOUND_DOC);
        }
        if (state == null || !state.has(FEATURE_BUTTON_SKIN)) {
            missing.add(FEATURE_BUTTON_SKIN_DOC);
        }
        if (state == null || !state.has(FEATURE_FRAME)) {
            missing.add(FEATURE_FRAME_DOC);
        }
        if (state == null || !state.has(FEATURE_PAGED_BOOK)) {
            missing.add(FEATURE_PAGED_BOOK_DOC);
        }
        if (state == null || !state.has(FEATURE_PORTRAIT_FRAME)) {
            missing.add(FEATURE_PORTRAIT_FRAME_DOC);
        }
        if (state == null || !state.has(FEATURE_COMBINED_FORMS)) {
            missing.add(FEATURE_COMBINED_FORMS_DOC);
        }
        if (state == null || !state.has(FEATURE_ICON)) {
            missing.add(FEATURE_ICON_DOC);
        }
        if (state == null || !state.has(FEATURE_BODY_PARTS)) {
            missing.add(FEATURE_BODY_PARTS_DOC);
        }
        return missing;
    }

    /** Optional ids not yet advertised by the recommended KaiPath jar ({@code bodyPartsSync}). */
    public static List<String> missingPending(State state) {
        List<String> missing = new ArrayList<>();
        for (String feature : PENDING_NEXT) {
            if (state == null || !state.has(feature)) {
                missing.add(docSpelling(feature));
            }
        }
        return missing;
    }

    private static String docSpelling(String feature) {
        String key = normalize(feature);
        if (FEATURE_ACTIVE_TAB.equals(key)) {
            return FEATURE_ACTIVE_TAB_DOC;
        }
        if (FEATURE_TITLE_ALIGN.equals(key)) {
            return FEATURE_TITLE_ALIGN_DOC;
        }
        if (FEATURE_PLAYER_MODEL.equals(key)) {
            return FEATURE_PLAYER_MODEL_DOC;
        }
        if (FEATURE_TEXT_INPUT.equals(key)) {
            return FEATURE_TEXT_INPUT_DOC;
        }
        if (FEATURE_HEADER_BOTTOM.equals(key)) {
            return FEATURE_HEADER_BOTTOM_DOC;
        }
        if (FEATURE_ACCENT.equals(key)) {
            return FEATURE_ACCENT_DOC;
        }
        if (FEATURE_FIXED_FOOTER.equals(key)) {
            return FEATURE_FIXED_FOOTER_DOC;
        }
        if (FEATURE_PALETTE.equals(key)) {
            return FEATURE_PALETTE_DOC;
        }
        if (FEATURE_SOUND.equals(key)) {
            return FEATURE_SOUND_DOC;
        }
        if (FEATURE_BUTTON_SKIN.equals(key)) {
            return FEATURE_BUTTON_SKIN_DOC;
        }
        if (FEATURE_FRAME.equals(key)) {
            return FEATURE_FRAME_DOC;
        }
        if (FEATURE_PAGED_BOOK.equals(key)) {
            return FEATURE_PAGED_BOOK_DOC;
        }
        if (FEATURE_PORTRAIT_FRAME.equals(key)) {
            return FEATURE_PORTRAIT_FRAME_DOC;
        }
        if (FEATURE_COMBINED_FORMS.equals(key)) {
            return FEATURE_COMBINED_FORMS_DOC;
        }
        if (FEATURE_ICON.equals(key)) {
            return FEATURE_ICON_DOC;
        }
        if (FEATURE_BODY_PARTS.equals(key)) {
            return FEATURE_BODY_PARTS_DOC;
        }
        if (FEATURE_BODY_PARTS_SYNC.equals(key)) {
            return FEATURE_BODY_PARTS_SYNC_DOC;
        }
        return feature == null ? "" : feature;
    }

    public static boolean hasTextInput(ServerPlayer player) {
        return get(player).has(FEATURE_TEXT_INPUT);
    }

    /** Human-readable impact lines for missing features. */
    public static List<String> impactLines(List<String> missing) {
        List<String> lines = new ArrayList<>();
        if (missing == null) {
            return lines;
        }
        for (String m : missing) {
            String key = normalize(m);
            if (FEATURE_STYLE.equals(key)) {
                lines.add(
                        "all buttons render identically, no green/red/dim distinction "
                                + "(style=confirm/danger/dim ignored)");
            } else if (FEATURE_ACTIVE_TAB.equals(key)) {
                lines.add("active tab is not highlighted (GuiOpenPacket.activeTab ignored)");
            } else if (FEATURE_TITLE_ALIGN.equals(key)) {
                lines.add(
                        "titles left-align instead of layoutJson.titleAlign=center");
            } else if (FEATURE_PLAYER_MODEL.equals(key)) {
                lines.add(
                        "Character playerModel preview not drawn (layoutJson.playerModel ignored)");
            } else if (FEATURE_TEXT_INPUT.equals(key)) {
                lines.add(
                        "text fields ignored (GuiOpenPacket.inputsJson / bindInput not rendered)");
            } else if (FEATURE_HEADER_BOTTOM.equals(key)) {
                lines.add(
                        "title/divider may overlap under-title tabs "
                                + "(layoutJson.headerBottom ignored)");
            } else if (FEATURE_ACCENT.equals(key)) {
                lines.add(
                        "system accent colors ignored (layoutJson.accent not painted; "
                                + "activeTab stays shared yellow)");
            } else if (FEATURE_FIXED_FOOTER.equals(key)) {
                lines.add(
                        "footer buttons may scroll with body "
                                + "(button footer:true / fixedFooter not pinned)");
            } else if (FEATURE_PALETTE.equals(key)) {
                lines.add("named palette themes ignored (layoutJson.palette not applied)");
            } else if (FEATURE_SOUND.equals(key)) {
                lines.add("button sound ids ignored (button sound not played)");
            } else if (FEATURE_BUTTON_SKIN.equals(key)) {
                lines.add("buttonSkin enable token ignored (default chrome only)");
            } else if (FEATURE_FRAME.equals(key)) {
                lines.add("panel frame ignored (layoutJson.frame not painted)");
            } else if (FEATURE_PAGED_BOOK.equals(key)) {
                lines.add("paged book widgets not mounted (inputsJson type paged_book ignored)");
            } else if (FEATURE_PORTRAIT_FRAME.equals(key)) {
                lines.add("player preview frame ignored (playerModel.portraitFrame not painted)");
            } else if (FEATURE_COMBINED_FORMS.equals(key)) {
                lines.add(
                        "worn form not drawn on the preview "
                                + "(playerModel.parts.form / combined.forms.v1 ignored)");
            } else if (FEATURE_ICON.equals(key)) {
                lines.add("button and tab icon ids ignored (icon field not drawn)");
            } else if (FEATURE_BODY_PARTS.equals(key)) {
                lines.add(
                        "custom body parts not attached on the preview "
                                + "(playerModel.parts.bodyParts ignored; base entity still draws)");
            }
        }
        return lines;
    }

    /**
     * Log when 1.3.29-shipped features are missing (real gap). {@link #PENDING_NEXT} gaps
     * log at info. {@code icon} and {@code bodyParts} are shipped, so a client that omits
     * them warns with the rest. {@code bodyPartsSync} stays pending until a client ships it.
     */
    public static void warnMissingOptionalFeatures(ServerPlayer player, String context) {
        if (player == null) {
            return;
        }
        State state = get(player);
        List<String> shippedGaps = missingShipped(state);
        List<String> pending = missingPending(state);
        String version = resolveKaiPathVersion(player);
        String verLabel = version == null || version.isBlank() ? "<unknown>" : version;
        String ctx = context == null ? "?" : context;
        if (!shippedGaps.isEmpty()) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Ultra client gap (shipped features through 1.3.29) player={} kaipathVersion={} "
                            + "context={} advertised={} features={} missingShipped={} — {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(player),
                    verLabel,
                    ctx,
                    state.advertised(),
                    state.features().isEmpty() ? "[]" : state.features(),
                    shippedGaps,
                    impactLines(shippedGaps));
        }
        if (!pending.isEmpty()) {
            LmLog.event(
                    "[{}] Ultra pending after KaiPath 1.3.29 player={} kaipathVersion={} "
                            + "context={} pending={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    safeName(player),
                    verLabel,
                    ctx,
                    pending);
        }
    }

    /** One-line summary for guitest / staff chat. */
    public static String summarizeForStaff(ServerPlayer player) {
        State state = get(player);
        List<String> shippedGaps = missingShipped(state);
        List<String> pending = missingPending(state);
        String version = resolveKaiPathVersion(player);
        StringBuilder sb = new StringBuilder();
        sb.append("features=");
        if (!state.advertised()) {
            sb.append("none (1.3.1 baseline — no GuiFeaturesPacket)");
        } else if (state.features().isEmpty()) {
            sb.append("[] (advertised empty)");
        } else {
            sb.append(state.features());
        }
        sb.append(" kaipath=").append(version == null || version.isBlank() ? "?" : version);
        sb.append(" missingShipped=")
                .append(shippedGaps.isEmpty() ? "none" : shippedGaps);
        sb.append(" pendingNext=").append(pending.isEmpty() ? "none" : pending);
        return sb.toString();
    }

    private static String resolveKaiPathVersion(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            // Reuse LM's Forge mod-list probe path via package-private helper when available.
            return LmGuiNetwork.probeKaiPathVersion(player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeName(ServerPlayer player) {
        if (player == null) {
            return "<null>";
        }
        try {
            return player.m_7755_().getString();
        } catch (Throwable ignored) {
            return "<name?>";
        }
    }
}
