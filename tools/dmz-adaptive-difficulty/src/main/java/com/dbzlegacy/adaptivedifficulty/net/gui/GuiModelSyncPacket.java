package com.dbzlegacy.adaptivedifficulty.net.gui;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.PlayerModelParts;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: authoritative equipped body-parts state for the receiving player
 * ({@code <modid>:gui} message {@link UltraGuiProtocol#MSG_MODEL_SYNC}).
 *
 * <p><b>Feature-gated — do not send to clients that did not advertise
 * {@code bodyPartsSync}</b> via {@link GuiFeaturesPacket}. KaiPath 1.3.29 and older
 * registered only messages 0–2 on this channel; an unregistered message id aborts the
 * connection. {@link LmGuiNetwork#sendModelSync} enforces the gate.
 *
 * <p>Wire format (protocol {@link UltraGuiProtocol#PROTOCOL_VERSION}, append-compatible):
 *
 * <ol>
 *   <li>{@code partsJson} — the playerModel parts object, <b>same schema</b> as
 *       {@code GuiOpenPacket layoutJson.playerModel.parts} ({@code schema}, {@code subject},
 *       {@code headBones}, {@code form}, {@code bodyParts}, {@code colors}, {@code extras}).
 *       Always includes {@code bodyParts} — an empty array clears the client's extra pieces.
 *       {@code subject} is always {@code uuid:<owner-uuid>}: the message is broadcast to
 *       every tracker, so each receiver binds the snapshot to the owner's entity — a
 *       {@code local} subject would draw the owner's parts on each observer.
 *   <li>{@code reason} — trigger token ({@code login}/{@code equip}/{@code unequip}/
 *       {@code clear}/{@code track}); diagnostics only, client rendering must not depend on it
 * </ol>
 *
 * <p>Client contract (JLDK): replace — never merge — the cached parts for the player named by
 * {@code subject} with the object received ({@code uuid:<id>} keys the cache; {@code local}
 * means the receiving player). Prefer {@code colors} over a reflective {@code FormParts$Look}
 * read — a remote player's reflective read comes back gray. The server already deduplicates
 * tail-slot pieces (at most one {@code tail}-slot piece is sent), and never sends the race
 * tail as a piece — the client's race layer draws it. Unknown trailing fields are ignored.
 */
public final class GuiModelSyncPacket {
    /**
     * Max {@code partsJson} chars on the wire. KaiPath 1.5.1 rejects a longer snapshot with a
     * {@code DecoderException} (which drops the connection), so this cannot be raised without a
     * KaiPath release. Oversized payloads degrade, never truncate. Every piece equipped (109 shop
     * pieces) plus the largest form is about 7,000 chars, so pieces fit.
     */
    public static final int PARTS_JSON_MAX = 8192;

    /** {@code reason} tokens (diagnostics only — not part of the render contract). */
    public static final String REASON_LOGIN = "login";

    public static final String REASON_EQUIP = "equip";
    public static final String REASON_UNEQUIP = "unequip";
    public static final String REASON_CLEAR = "clear";
    /** Sent to a tracker that just started seeing the subject ({@code PlayerEvent.StartTracking}). */
    public static final String REASON_TRACK = "track";
    /** A body-parts part-channel color was picked or reset in the shop. */
    public static final String REASON_COLOR = "color";

    private final String partsJson;
    private final String reason;

    public GuiModelSyncPacket(String partsJson, String reason) {
        this.partsJson = sanitizePartsJson(partsJson);
        this.reason = GuiOpenPacket.trim(reason == null ? "" : reason, 32);
    }

    /** PlayerModel parts object JSON ({@code PlayerModelParts.SCHEMA} layout). */
    public String partsJson() {
        return partsJson;
    }

    /** Trigger token — diagnostics only. */
    public String reason() {
        return reason;
    }

    /** Serialize 2 fields — no channel / mod-id bytes. */
    public static void encode(GuiModelSyncPacket msg, FriendlyByteBuf buf) {
        GuiOpenPacket.writeUtf8(buf, msg.partsJson);
        GuiOpenPacket.writeUtf8(buf, msg.reason);
    }

    /** Decode fields 1–2; missing trailing fields default. Leftover bytes are ignored. */
    public static GuiModelSyncPacket decode(FriendlyByteBuf buf) {
        String parts = GuiOpenPacket.readUtf8(buf);
        String reason = buf.isReadable() ? GuiOpenPacket.readUtf8(buf) : "";
        return new GuiModelSyncPacket(parts, reason);
    }

    /** Server never renders — KaiPath owns the client handler. */
    public static void handle(GuiModelSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // No player/world access on the network thread — mark handled only.
        context.setPacketHandled(true);
    }

    /**
     * Guarantees valid JSON within {@link #PARTS_JSON_MAX} that <b>keeps the equipped pieces</b>
     * (4.7.10; before, a payload still too big after two drops was sent with an empty
     * {@code bodyParts}, which wiped the player's pieces on every client). An oversized payload
     * drops, in order: {@code headBones}, {@code form}, {@code extras}, {@code colors}, then any
     * other field but {@code schema}/{@code subject}/{@code bodyParts}/{@code suppressNaturalTail}.
     * Only if the pieces alone still do not fit (never with the 0.4.0 catalog) are entries
     * dropped from the front — form pieces, which Ultra draws natively — keeping the player's
     * shop pieces at the end. A hard cut mid-JSON would break the whole message. {@code subject}
     * is always preserved: the sync is broadcast, and a {@code local} subject would clear each
     * observer's own parts instead of the owner's.
     */
    static String sanitizePartsJson(String json) {
        if (json == null || json.isBlank()) {
            return fallback(null);
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String subject = o.has("subject") && o.get("subject").isJsonPrimitive()
                    ? o.get("subject").getAsString()
                    : null;
            String out = o.toString();
            if (out.length() <= PARTS_JSON_MAX) {
                return out;
            }
            int before = out.length();
            for (String optional : new String[] {"headBones", "form", "extras", "colors"}) {
                o.remove(optional);
                out = o.toString();
                if (out.length() <= PARTS_JSON_MAX) {
                    LmLog.event(
                            "[{}] GuiModelSync partsJson {} chars over the cap; dropped optional fields up to {} "
                                    + "(pieces kept) len={}",
                            AdaptiveDifficultyMod.MOD_ID, before, optional, out.length());
                    return out;
                }
            }
            JsonObject core = new JsonObject();
            core.addProperty("schema", PlayerModelParts.SCHEMA);
            core.addProperty("subject", subject == null || subject.isBlank() ? "local" : subject);
            JsonArray pieces = o.has("bodyParts") && o.get("bodyParts").isJsonArray()
                    ? o.getAsJsonArray("bodyParts")
                    : new JsonArray();
            core.add("bodyParts", pieces);
            if (o.has("suppressNaturalTail")) {
                core.add("suppressNaturalTail", o.get("suppressNaturalTail"));
            }
            core.add("extras", new JsonArray());
            out = core.toString();
            int total = pieces.size();
            while (out.length() > PARTS_JSON_MAX && pieces.size() > 1) {
                pieces.remove(0);
                out = core.toString();
            }
            if (pieces.size() < total) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] GuiModelSync partsJson for {} too big even with pieces only: sent the last {} of {} pieces",
                        AdaptiveDifficultyMod.MOD_ID, subject, pieces.size(), total);
            }
            return out.length() <= PARTS_JSON_MAX ? out : fallback(subject);
        } catch (Throwable ignored) {
            return fallback(subjectOf(json));
        }
    }

    /** Minimal valid parts object that clears client state for {@code subject}. */
    private static String fallback(String subject) {
        String s = subject == null || subject.isBlank() ? "local" : subject;
        return "{\"schema\":" + PlayerModelParts.SCHEMA
                + ",\"subject\":\"" + s + "\",\"bodyParts\":[],\"extras\":[]}";
    }

    /** Best-effort {@code subject} from a string that did not parse — diagnostics only. */
    private static String subjectOf(String json) {
        try {
            int i = json.indexOf("\"subject\"");
            if (i < 0) {
                return null;
            }
            int colon = json.indexOf(':', i);
            int open = json.indexOf('"', colon + 1);
            int close = json.indexOf('"', open + 1);
            if (colon < 0 || open < 0 || close < 0) {
                return null;
            }
            return json.substring(open + 1, close);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
