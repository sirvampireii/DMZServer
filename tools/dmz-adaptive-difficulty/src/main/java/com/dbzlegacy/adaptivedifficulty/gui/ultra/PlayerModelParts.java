package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.character.CosmeticBodyPartService;
import com.dbzlegacy.adaptivedifficulty.character.CosmeticHeadBoneService;
import com.dbzlegacy.adaptivedifficulty.character.HeadPartPieces;
import com.dbzlegacy.adaptivedifficulty.gui.AdminInspectSessions;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.character.Character;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the optional {@code layoutJson.playerModel.parts} snapshot for KaiPath / JLDK.
 *
 * <p>Schema {@code 1} - see {@code docs/KAIPATH_GUI_PROTOCOL.md}. {@code subject} is the bridge
 * field any mod may set: {@code local} for the viewing player, or {@code uuid:<id>} for someone
 * else. KaiPath draws that player's model, skin, and parts. This object also carries custom
 * body pieces (DMZ head bones, worn form identity, explicit {@code bodyParts}, and an
 * open {@code extras} array). {@code form} stays even when {@code bodyParts} is sent so
 * older clients that only know the form ids keep working.
 */
public final class PlayerModelParts {
    /** Current documented schema version inside {@code parts.schema}. */
    public static final int SCHEMA = 1;

    private PlayerModelParts() {}

    /**
     * Snapshot equipped cosmetics for the model render target. Returns {@code null} when the
     * target is the viewer and nothing useful can be serialized (caller omits {@code parts}).
     * While staff inspect is active the target is {@link AdminInspectSessions#resolveSubject},
     * and {@code parts} is always sent so the client does not fall back to the local player.
     */
    public static JsonObject snapshot(ServerPlayer player) {
        ServerPlayer target = renderTarget(player);
        if (target == null) {
            return null;
        }
        boolean other = previewsOtherPlayer(target);
        JsonObject parts = build(target, other);
        if (parts == null && !other) {
            return null;
        }
        return parts;
    }

    /**
     * Authoritative snapshot for the world-sync message ({@code GuiModelSyncPacket}) —
     * always non-null, always {@code subject uuid:<owner-uuid>} (so every receiving client
     * binds the parts to the owner, not to itself), no inspect redirect, and always carries
     * {@code bodyParts} so an empty array clears stale client pieces (server-authoritative
     * replace, never merge). The message is broadcast to trackers, so the subject must name
     * the owner: a {@code local} subject would render the owner's parts on each observer.
     * {@link #build} sets that subject — never overwrite it here.
     */
    public static JsonObject syncSnapshot(ServerPlayer player) {
        JsonObject parts = player == null ? null : build(player, true);
        if (parts == null) {
            parts = new JsonObject();
            parts.addProperty("schema", SCHEMA);
        }
        if (!parts.has("bodyParts")) {
            parts.add("bodyParts", new JsonArray());
        }
        if (!parts.has("extras")) {
            parts.add("extras", new JsonArray());
        }
        return parts;
    }

    /** Core parts object for {@code target}. Null when nothing applies and not {@code other}. */
    private static JsonObject build(ServerPlayer target, boolean other) {
        JsonObject parts = new JsonObject();
        parts.addProperty("schema", SCHEMA);
        parts.addProperty("subject", other ? "uuid:" + target.m_20148_() : "local");

        boolean any = false;
        JsonObject head = headBones(target);
        if (head != null) {
            parts.add("headBones", head);
            any = true;
        }
        JsonObject form = formIdentity(target);
        if (form != null) {
            parts.add("form", form);
            any = true;
        }
        JsonArray body = bodyParts(target);
        if (body != null && !body.isEmpty()) {
            parts.add("bodyParts", body);
            any = true;
        }
        // Explicit model colors - the gray-parts fix. Kept out of `any`: colors alone never
        // force a parts object for a bare base player.
        JsonObject colors = colors(target);
        if (colors != null) {
            parts.add("colors", colors);
        }
        // Open extension slot - always present so clients can iterate without null checks.
        parts.add("extras", new JsonArray());
        if (!any && !other) {
            return null;
        }
        return parts;
    }

    /**
     * Explicit model colors under {@code parts.colors} so a client prefers them over its
     * reflective DMZUltra {@code FormParts$Look} read - for a <i>remote</i> player that read
     * resolves another player's capability data and comes back gray (0x808080 matches none
     * of the fallbacks). Mirrors {@code FormParts.of} byte-for-byte: {@code skin}/{@code second}/
     * {@code hair} are per-player (active-form color override beats the character value, and
     * the same fallbacks apply), while {@code own}/{@code trim}/{@code horn}/{@code light} are
     * form-constant design colors from the shipped DMZUltra catalog. Null when the character
     * is unknown - the client then keeps its own fallback path.
     *
     * <p>Key names are the DMZUltra accessor names so the client maps them 1:1 - see
     * {@code docs/KAIPATH_GUI_PROTOCOL.md} (coordinate with JLDK before renaming).
     */
    private static JsonObject colors(ServerPlayer player) {
        Character ch = null;
        try {
            ch = DmzProgression.character(player);
        } catch (Throwable ignored) {
        }
        if (ch == null) {
            return null;
        }
        String race = safe(DmzProgression.race(player));
        String group = "";
        String form = "";
        String stackGroup = "";
        String stackForm = "";
        try {
            group = safe(ch.getActiveFormGroup());
            form = safe(ch.getActiveForm());
        } catch (Throwable ignored) {
        }
        try {
            stackGroup = safe(ch.getActiveStackFormGroup());
            stackForm = safe(ch.getActiveStackForm());
        } catch (Throwable ignored) {
        }

        FormConfig.FormData fd = null;
        try {
            fd = ch.getActiveFormData();
        } catch (Throwable ignored) {
        }

        int skin = 0xFFFFFF;
        int second = 0xFFFFFF;
        int hair = 0x202024;
        try {
            skin =
                    rgb(
                            fd != null && set(fd.getBodyColor1())
                                    ? fd.getRgbBodyColor1()
                                    : ch.getRgbBodyColor(),
                            0xFFFFFF);
            second =
                    rgb(
                            fd != null && set(fd.getBodyColor2())
                                    ? fd.getRgbBodyColor2()
                                    : ch.getRgbBodyColor2(),
                            skin);
            hair =
                    rgb(
                            fd != null && set(fd.getHairColor())
                                    ? fd.getRgbHairColor()
                                    : ch.getRgbHairColor(),
                            0x202024);
        } catch (Throwable ignored) {
        }

        JsonObject o = new JsonObject();
        o.addProperty("skin", skin);
        o.addProperty("second", second);
        o.addProperty("hair", hair);
        BodyPartCatalog.Design design =
                BodyPartCatalog.design(race, group, form, stackGroup, stackForm);
        if (design != null) {
            o.addProperty("own", design.own());
            o.addProperty("trim", design.trim());
            o.addProperty("horn", design.horn());
            o.addProperty("light", design.light());
        }
        return o;
    }

    /** DMZUltra {@code FormParts.set}: a color override applies when the string is non-empty. */
    private static boolean set(String value) {
        return value != null && !value.isEmpty();
    }

    /** DMZUltra {@code FormParts.rgb}: clamp each channel to [0,1], scale, pack RGB. */
    private static int rgb(float[] rgb, int fallback) {
        if (rgb == null || rgb.length < 3) {
            return fallback;
        }
        return channel(rgb[0]) << 16 | channel(rgb[1]) << 8 | channel(rgb[2]);
    }

    private static int channel(float value) {
        return Math.round(Math.max(0f, Math.min(1f, value)) * 255f);
    }

    /**
     * Who the client should draw. Inspect sessions use the subject, not the staff viewer.
     */
    static ServerPlayer renderTarget(ServerPlayer passed) {
        ServerPlayer viewer = UltraScreenSpec.inspectViewer();
        if (viewer != null) {
            ServerPlayer subject = AdminInspectSessions.resolveSubject(viewer);
            if (subject != null) {
                return subject;
            }
        }
        return passed;
    }

    private static boolean previewsOtherPlayer(ServerPlayer target) {
        ServerPlayer viewer = UltraScreenSpec.inspectViewer();
        return viewer != null
                && target != null
                && !viewer.m_20148_().equals(target.m_20148_());
    }

    private static JsonObject headBones(ServerPlayer player) {
        String token = "";
        try {
            token = CosmeticHeadBoneService.activeBone(player);
        } catch (Throwable ignored) {
        }
        if (token == null) {
            token = "";
        }
        token = token.trim().toLowerCase(Locale.ROOT);
        List<String> pieces = HeadPartPieces.fragments(token);
        if (token.isEmpty() && pieces.isEmpty()) {
            return null;
        }
        // Prefer the canonical join when fragments are known atomics.
        String canonical = token;
        try {
            if (!pieces.isEmpty()) {
                String joined = HeadPartPieces.join(pieces);
                if (joined != null && !joined.isBlank()) {
                    canonical = joined;
                }
            }
        } catch (Throwable ignored) {
        }
        JsonObject head = new JsonObject();
        head.addProperty("token", canonical);
        JsonArray arr = new JsonArray();
        for (String piece : pieces.isEmpty() ? HeadPartPieces.fragments(canonical) : pieces) {
            if (piece != null && !piece.isBlank()) {
                arr.add(piece);
            }
        }
        head.add("pieces", arr);
        return head;
    }

    /**
     * Explicit pieces for {@code parts.bodyParts}. Resolves the worn form (and stack form)
     * through {@link BodyPartCatalog}, which mirrors DMZUltra FormParts. Also names
     * {@code saiyan_tail} when the character still has the race tail. Single-tail rule: the
     * array never carries two {@code SLOT_TAIL} entries — a form tail beats a shop tail, and
     * the natural {@code saiyan_tail} is only added when no other tail slot is occupied.
     * Empty when nothing applies. Never throws — a missing catalog still reports the race
     * tail.
     */
    private static JsonArray bodyParts(ServerPlayer player) {
        JsonArray out = new JsonArray();
        try {
            Character ch = null;
            try {
                ch = DmzProgression.character(player);
            } catch (Throwable ignored) {
            }
            String race = safe(DmzProgression.race(player));
            String group = "";
            String form = "";
            String stackGroup = "";
            String stackForm = "";
            boolean tail = false;
            if (ch != null) {
                try {
                    tail = ch.isHasSaiyanTail();
                } catch (Throwable ignored) {
                }
                try {
                    group = safe(ch.getActiveFormGroup());
                    form = safe(ch.getActiveForm());
                } catch (Throwable ignored) {
                }
                try {
                    stackGroup = safe(ch.getActiveStackFormGroup());
                    stackForm = safe(ch.getActiveStackForm());
                } catch (Throwable ignored) {
                }
            }
            // Custom Body Parts Shop pieces the player turned on. Fetched first so the
            // natural race tail can yield to an explicit shop tail (single-tail rule).
            List<String> custom = CosmeticBodyPartService.equippedParts(player);
            boolean customTail = false;
            for (String customId : custom) {
                if (customId != null
                        && !customId.isBlank()
                        && BodyPartCatalog.SLOT_TAIL.equals(BodyPartCatalog.slot(customId))) {
                    customTail = true;
                    break;
                }
            }
            Set<String> seen = new java.util.LinkedHashSet<>();
            boolean tailOccupied = false;
            for (BodyPartCatalog.Part part :
                    BodyPartCatalog.resolve(race, group, form, stackGroup, stackForm, tail && !customTail)) {
                if (part == null || part.id() == null || part.id().isBlank()) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("id", part.id());
                entry.addProperty("source", part.source());
                entry.addProperty("slot", part.slot());
                seen.add(part.id().trim().toLowerCase(Locale.ROOT));
                if (BodyPartCatalog.SLOT_TAIL.equals(part.slot())) {
                    tailOccupied = true;
                }
                out.add(entry);
            }
            // Dedup ids the worn form already named; the KaiPath 1.3.29 preview attaches each
            // known mesh. Single-tail rule: a shop tail yields to a form tail — the array
            // never carries two SLOT_TAIL entries (menu preview and world sync share this path).
            for (String customId : custom) {
                if (customId == null
                        || customId.isBlank()
                        || !seen.add(customId.trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String slot = BodyPartCatalog.slot(customId);
                if (BodyPartCatalog.SLOT_TAIL.equals(slot) && tailOccupied) {
                    continue;
                }
                if (BodyPartCatalog.SLOT_TAIL.equals(slot)) {
                    tailOccupied = true;
                }
                JsonObject customEntry = new JsonObject();
                customEntry.addProperty("id", customId);
                customEntry.addProperty("source", BodyPartCatalog.SOURCE_DMZULTRA);
                customEntry.addProperty("slot", slot);
                out.add(customEntry);
            }
        } catch (Throwable ignored) {
            return new JsonArray();
        }
        return out;
    }

    /**
     * Worn form identity so the client can attach DMZUltra {@code FormParts} (client-only
     * catalog keyed by form). Omitted when the player is fully base / unknown. Kept even
     * when {@link #bodyParts} is present.
     */
    private static JsonObject formIdentity(ServerPlayer player) {
        Character ch = null;
        try {
            ch = DmzProgression.character(player);
        } catch (Throwable ignored) {
        }
        if (ch == null) {
            return null;
        }
        boolean hasForm = false;
        boolean hasStack = false;
        try {
            hasForm = ch.hasActiveForm();
        } catch (Throwable ignored) {
        }
        try {
            hasStack = ch.hasActiveStackForm();
        } catch (Throwable ignored) {
        }
        if (!hasForm && !hasStack) {
            return null;
        }
        String race = safe(DmzProgression.race(player));
        String group = "";
        String form = "";
        String stackGroup = "";
        String stackForm = "";
        try {
            group = safe(ch.getActiveFormGroup());
            form = safe(ch.getActiveForm());
        } catch (Throwable ignored) {
        }
        try {
            stackGroup = safe(ch.getActiveStackFormGroup());
            stackForm = safe(ch.getActiveStackForm());
        } catch (Throwable ignored) {
        }
        if (race.isEmpty() && group.isEmpty() && form.isEmpty() && stackGroup.isEmpty() && stackForm.isEmpty()) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("race", race);
        o.addProperty("group", group);
        o.addProperty("form", form);
        o.addProperty("stackGroup", stackGroup);
        o.addProperty("stackForm", stackForm);
        return o;
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }
}
