package com.dbzlegacy.adaptivedifficulty.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * LM 4.7.61: copied from the Ultra GUI kit's reference ({@code tools/ultra-gui-kit}, same owner) so the LM
 * client draws Preview forms natively. 4.8.2: both halves are used: the menu preview ({@link #begin} /
 * {@link #end}) and the world sync ({@link #applySync}, fed by native message 5 now that the KaiPath
 * bridge is gone). {@link #mergeLook} rebuilds
 * DMZUltra 0.5.7's FormParts records from their own components.
 *
 * <p>Kit notes:
 * Attaches {@code playerModel.parts.bodyParts} to the preview entity.
 *
 * <p>{@link #begin} runs just before the entity is drawn. {@link FormPartsPreviewMixin}
 * asks {@link #meshesFor} while DMZUltra's FormParts layer builds the look, and unions
 * those meshes into the design so {@code part.<id>} bones render on that entity.
 * A missing DMZUltra install, or an id the catalog does not know, leaves the base
 * entity alone.
 *
 * <p>Two states feed {@link #meshesFor}:
 *
 * <ul>
 *   <li><b>Menu preview</b> ({@link #begin}/{@link #end}) — ThreadLocal, overrides the
 *       preview subject while a screen draws it.
 *   <li><b>World sync</b> (message 3 {@code GuiModelSyncPacket}, {@link #applySync}) —
 *       persistent per-player cache. Server-authoritative: each snapshot <b>replaces</b>
 *       the cached parts; an empty {@code bodyParts} array clears all extra pieces.
 *       {@link #clearSync} drops it on disconnect.
 * </ul>
 */
public final class BodyPartPreview {
    private static final ThreadLocal<Active> ACTIVE = new ThreadLocal<>();
    private static final ConcurrentHashMap<String, Set<String>> LOADED = new ConcurrentHashMap<>();

    /**
     * Server-synced world state (message 3) per player UUID: every channel's snapshot for that
     * player combined ({@link #combine}). Renderers read this.
     */
    private static final ConcurrentHashMap<UUID, WorldState> WORLD = new ConcurrentHashMap<>();

    /**
     * The snapshots behind {@link #WORLD}: player UUID → {@code <modid>:gui} channel → that
     * channel's last snapshot. Each mod replaces only its own pieces, so two mods syncing parts
     * for one player never wipe each other. Insertion order is the order channels first synced.
     */
    private static final ConcurrentHashMap<UUID, LinkedHashMap<String, WorldState>> BY_CHANNEL =
            new ConcurrentHashMap<>();

    /** Snapshots addressed {@code subject:"local"}, per channel. */
    private static final LinkedHashMap<String, WorldState> LOCAL_BY_CHANNEL = new LinkedHashMap<>();

    private static volatile WorldState localState = WorldState.EMPTY;
    private static volatile UUID localId;

    private BodyPartPreview() {}

    private record Active(UUID playerId, Set<String> meshes, JsonObject colors, String partsJson, boolean suppressNaturalTail) {}

    /**
     * Cached world parts for one subject: the raw {@code partsJson} (threaded to the merge
     * site via {@link #partsJsonFor} so future per-sync metadata reaches
     * {@link #mergeLook(Object, Set, JsonObject, String)} without another signature wall),
     * the resolved meshes, and the server-sent {@code colors} (per-category model colors;
     * {@code null} when the payload has none). {@code noTail} is {@code meshes} minus the
     * aliased Saiyan tail — served when the entity's race layer already draws one.
     *
     * <p>{@code suppressNaturalTail} is the server's own call on the same question: the
     * player has equipped a chosen tail piece, so the race tail (geo bones
     * {@code tail1}/{@code tail1m}, drawn outside {@code bodyParts} entirely) must be hidden
     * or the two draw side by side. It goes back to {@code false} the moment the piece is
     * unequipped, which is what lets the natural tail render as before.
     */
    record WorldState(String partsJson, Set<String> meshes, Set<String> noTail, boolean aliasTailOnly, boolean suppressNaturalTail, JsonObject colors) {
        static final WorldState EMPTY = new WorldState("", Set.of(), Set.of(), false, false, null);
    }

    /** Mesh collection result shared by the menu preview and the world sync. */
    private record Resolved(LinkedHashSet<String> meshes, Set<String> noTail, boolean aliasTailOnly) {}

    /**
     * Message 3 (world sync): replace the cached parts for the payload's subject.
     * Server-authoritative — never merges; an empty {@code bodyParts} array clears all
     * extra pieces. {@code subject:"local"} (or anything unrecognized) is the receiving
     * player; {@code uuid:<id>} addresses another player — <b>including the receiver
     * itself</b>, since the server broadcasts to the owner and every tracker with a uuid
     * subject. A uuid that resolves to the local player therefore feeds the local state
     * too, or the owner's own parts would vanish from their world render.
     */
    public static void applySync(String partsJson, String reason) {
        applySync("", partsJson, reason);
    }

    /**
     * Message 3 from one {@code <modid>:gui} channel: replace that channel's snapshot for the
     * payload's subject, then recombine every channel's snapshot for that player. A snapshot
     * from another channel is left alone, so any mod can sync its own pieces through its own
     * channel. With a single channel the result is exactly that channel's snapshot.
     */
    public static void applySync(String channelId, String partsJson, String reason) {
        String channel = channelId == null ? "" : channelId;
        try {
            WorldState state = stateFromParts(partsJson, BodyPartPreview::piecesFor);
            String subject = subjectOf(partsJson);
            synchronized (BY_CHANNEL) {
                if (subject.startsWith("uuid:")) {
                    UUID id;
                    try {
                        id = UUID.fromString(subject.substring("uuid:".length()));
                    } catch (Throwable ignored) {
                        return; // unusable subject id — keep the previous state
                    }
                    LinkedHashMap<String, WorldState> channels = BY_CHANNEL.computeIfAbsent(id, k -> new LinkedHashMap<>());
                    channels.put(channel, state);
                    WORLD.put(id, combine(new ArrayList<>(channels.values()), BodyPartPreview::piecesFor));
                    UUID local = localId != null ? localId : resolveLocalId();
                    if (local != null) {
                        localId = local;
                        if (local.equals(id)) {
                            localState = combinedLocal();
                        }
                    }
                    return;
                }
                LOCAL_BY_CHANNEL.put(channel, state);
                localId = resolveLocalId();
                localState = combinedLocal();
            }
        } catch (Throwable ignored) {
            // a bad payload must not crash the network handler
        }
    }

    /** The local player's snapshots: {@code subject:"local"} ones first, then its own uuid's. */
    private static WorldState combinedLocal() {
        List<WorldState> states = new ArrayList<>(LOCAL_BY_CHANNEL.values());
        UUID local = localId;
        LinkedHashMap<String, WorldState> own = local == null ? null : BY_CHANNEL.get(local);
        if (own != null) {
            states.addAll(own.values());
        }
        return combine(states, BodyPartPreview::piecesFor);
    }

    /**
     * Several channels' snapshots for one player as one state. Meshes are unioned in channel
     * order. Single-tail rule across channels: once a channel has supplied a tail-slot piece,
     * later channels' tail meshes are dropped. {@code suppressNaturalTail} holds if any channel
     * asks for it; colors and the raw payload come from the first channel that has them. One
     * snapshot is returned as it is.
     */
    static WorldState combine(List<WorldState> states, Function<String, Set<String>> catalog) {
        List<WorldState> live = new ArrayList<>();
        for (WorldState s : states) {
            if (s != null && s != WorldState.EMPTY) {
                live.add(s);
            }
        }
        if (live.isEmpty()) {
            return WorldState.EMPTY;
        }
        if (live.size() == 1) {
            return live.get(0);
        }
        LinkedHashSet<String> meshes = new LinkedHashSet<>();
        LinkedHashSet<String> noTail = new LinkedHashSet<>();
        boolean tailTaken = false;
        boolean aliasTailOnly = false;
        boolean suppress = false;
        JsonObject colors = null;
        String partsJson = "";
        for (WorldState s : live) {
            Set<String> tails = tailMeshes(s.partsJson(), catalog);
            boolean hasTail = !tails.isEmpty();
            for (String mesh : s.meshes()) {
                if (tailTaken && tails.contains(mesh)) {
                    continue;
                }
                meshes.add(mesh);
                if (s.noTail().contains(mesh)) {
                    noTail.add(mesh);
                }
            }
            if (hasTail && !tailTaken) {
                tailTaken = true;
                aliasTailOnly = s.aliasTailOnly();
            }
            suppress |= s.suppressNaturalTail();
            if (colors == null) {
                colors = s.colors();
            }
            if (partsJson.isEmpty() && s.partsJson() != null) {
                partsJson = s.partsJson();
            }
        }
        return new WorldState(partsJson, Set.copyOf(meshes), Set.copyOf(noTail), aliasTailOnly, suppress, colors);
    }

    /** Meshes of a payload's {@code slot:"tail"} entries. Empty when unreadable. */
    private static Set<String> tailMeshes(String partsJson, Function<String, Set<String>> catalog) {
        try {
            if (partsJson == null || partsJson.isBlank()) {
                return Set.of();
            }
            JsonObject parts = JsonParser.parseString(partsJson).getAsJsonObject();
            if (!parts.has("bodyParts") || !parts.get("bodyParts").isJsonArray()) {
                return Set.of();
            }
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (BodyPartWire.Model model :
                    BodyPartWire.resolve(BodyPartWire.parse(parts.getAsJsonArray("bodyParts")), catalog)) {
                if (model != null && "tail".equals(model.slot()) && model.mesh() != null) {
                    out.add(model.mesh());
                }
            }
            return out;
        } catch (Throwable ignored) {
            return Set.of();
        }
    }

    /**
     * Server-sent {@code colors} for this entity ({@code null} when absent): menu preview
     * subject first, then the world cache (local state for the receiver's own uuid). The
     * consumer prefers these over its reflective {@code FormParts$Look} read — a remote
     * player's reflective read comes back gray.
     */
    public static JsonObject colorsFor(Object player) {
        try {
            Active active = ACTIVE.get();
            UUID id = entityUuid(player);
            if (active != null && active.colors() != null) {
                UUID want = active.playerId();
                if (want == null || id == null || want.equals(id)) {
                    return active.colors();
                }
            }
            if (id == null) {
                return null;
            }
            UUID local = localId;
            if (local == null) {
                local = resolveLocalId();
                if (local != null) {
                    localId = local;
                }
            }
            WorldState state = local != null && local.equals(id) ? localState : WORLD.get(id);
            return state == null ? null : state.colors();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Server-synced (or menu-preview) raw {@code partsJson} for this entity — empty when
     * none. Same precedence as {@link #colorsFor}: the menu preview's subject first, then
     * the local state, then the world cache keyed by the payload's {@code uuid:} subject.
     */
    public static String partsJsonFor(Object player) {
        try {
            Active active = ACTIVE.get();
            UUID id = entityUuid(player);
            if (active != null && active.partsJson() != null && !active.partsJson().isEmpty()) {
                UUID want = active.playerId();
                if (want == null || id == null || want.equals(id)) {
                    return active.partsJson();
                }
            }
            if (id == null) {
                return "";
            }
            UUID local = localId;
            if (local == null) {
                local = resolveLocalId();
                if (local != null) {
                    localId = local;
                }
            }
            WorldState state = local != null && local.equals(id) ? localState : WORLD.get(id);
            return state == null ? "" : state.partsJson();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Drop all server-synced world state (call on disconnect / session reset). */
    public static void clearSync() {
        synchronized (BY_CHANNEL) {
            WORLD.clear();
            BY_CHANNEL.clear();
            LOCAL_BY_CHANNEL.clear();
            localState = WorldState.EMPTY;
            localId = null;
        }
    }

    /**
     * 4.8.2: true when the server's world sync gave {@code player} pieces to draw or asked to hide their race tail.
     * Cheap (no DragonMineZ reflection), so the world-render mixins call it every frame before the full lookup;
     * false at once while nothing is synced.
     */
    public static boolean hasWorldSync(Object player) {
        if (WORLD.isEmpty() && localState == WorldState.EMPTY) {
            return false;
        }
        try {
            UUID id = entityUuid(player);
            if (id == null) {
                return false;
            }
            UUID local = localId;
            if (local == null) {
                local = resolveLocalId();
                if (local != null) {
                    localId = local;
                }
            }
            WorldState state = local != null && local.equals(id) ? localState : WORLD.get(id);
            return state != null && (!state.meshes().isEmpty() || state.suppressNaturalTail());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The local player's cached parts JSON (empty string when none). */
    public static String localPartsJson() {
        WorldState state = localState;
        return state == null ? "" : state.partsJson();
    }

    /**
     * Parse {@code partsJson} into a world state with the same pipeline as the menu
     * preview ({@link BodyPartWire}). Never throws. {@code catalog} maps a source mod id
     * to its piece set (injectable for tests). Defensive mirror of the server's
     * single-tail rule: at most one {@code slot:"tail"} entry — first wins — since a
     * third-party server may not dedupe.
     */
    static WorldState stateFromParts(String partsJson, Function<String, Set<String>> catalog) {
        if (partsJson == null || partsJson.isBlank() || catalog == null) {
            return WorldState.EMPTY;
        }
        try {
            JsonObject parts = JsonParser.parseString(partsJson).getAsJsonObject();
            JsonObject colors = parts.has("colors") && parts.get("colors").isJsonObject()
                    ? parts.getAsJsonObject("colors")
                    : null;
            boolean suppressNaturalTail = suppressNaturalTailOf(parts);
            if (!parts.has("bodyParts") || !parts.get("bodyParts").isJsonArray()) {
                return new WorldState(partsJson, Set.of(), Set.of(), false, suppressNaturalTail, colors);
            }
            List<BodyPartWire.Entry> entries = BodyPartWire.parse(parts.getAsJsonArray("bodyParts"));
            List<BodyPartWire.Entry> singleTail = new ArrayList<>(entries.size());
            boolean tailKept = false;
            for (BodyPartWire.Entry entry : entries) {
                if (entry != null && "tail".equals(entry.slot())) {
                    if (tailKept) {
                        continue;
                    }
                    tailKept = true;
                }
                singleTail.add(entry);
            }
            Resolved resolved = collect(BodyPartWire.resolve(singleTail, catalog));
            return new WorldState(
                    partsJson,
                    Set.copyOf(resolved.meshes()),
                    resolved.noTail(),
                    resolved.aliasTailOnly(),
                    suppressNaturalTail,
                    colors);
        } catch (Throwable ignored) {
            return WorldState.EMPTY;
        }
    }

    private static String subjectOf(String partsJson) {
        try {
            JsonObject o = JsonParser.parseString(partsJson).getAsJsonObject();
            if (o.has("subject") && o.get("subject").isJsonPrimitive()) {
                return o.get("subject").getAsString().trim();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return "local";
    }

    /**
     * {@code parts.suppressNaturalTail}: the server saw a chosen tail piece, so the race tail
     * must stay hidden. Absent in payloads from an older server, so the default is {@code
     * false} — no suppression, the race tail renders exactly as it always has.
     */
    private static boolean suppressNaturalTailOf(JsonObject parts) {
        try {
            return parts != null
                    && parts.has("suppressNaturalTail")
                    && parts.get("suppressNaturalTail").isJsonPrimitive()
                    && parts.get("suppressNaturalTail").getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** {@code parts.colors} object, or {@code null} when absent / not an object. */
    private static JsonObject colorsOf(JsonObject parts) {
        try {
            if (parts != null && parts.has("colors") && parts.get("colors").isJsonObject()) {
                return parts.getAsJsonObject("colors");
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return null;
    }

    /** {@code parts.colors} parsed out of a raw payload — {@code null} when unreadable. */
    private static JsonObject payloadColors(String partsJson) {
        try {
            return colorsOf(JsonParser.parseString(partsJson).getAsJsonObject());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** {@code parts.suppressNaturalTail} from a raw payload — {@code false} when unreadable. */
    private static boolean suppressesFromPayload(String partsJson) {
        try {
            return suppressNaturalTailOf(JsonParser.parseString(partsJson).getAsJsonObject());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The local player's UUID, cached once per session (null while not connected). */
    private static UUID resolveLocalId() {
        try {
            Minecraft mc = Minecraft.m_91087_();
            return mc == null ? null : entityUuid(mc.f_91074_);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Map resolved models to meshes, tracking whether only the aliased tail is present. */
    private static Resolved collect(List<BodyPartWire.Model> models) {
        LinkedHashSet<String> meshes = new LinkedHashSet<>();
        boolean aliasTail = false;
        boolean directTail = false;
        for (BodyPartWire.Model model : models) {
            if (model == null || model.mesh() == null || model.mesh().isBlank()) {
                continue;
            }
            if (BodyPartWire.SAIYAN_TAIL_MESH.equals(model.mesh())) {
                if (model.alias()) {
                    aliasTail = true;
                } else {
                    directTail = true;
                }
            }
            meshes.add(model.mesh());
        }
        boolean aliasTailOnly = aliasTail && !directTail;
        Set<String> noTail;
        if (aliasTailOnly && meshes.contains(BodyPartWire.SAIYAN_TAIL_MESH)) {
            LinkedHashSet<String> copy = new LinkedHashSet<>(meshes);
            copy.remove(BodyPartWire.SAIYAN_TAIL_MESH);
            noTail = Set.copyOf(copy);
        } else {
            noTail = meshes;
        }
        return new Resolved(meshes, noTail, aliasTailOnly);
    }

    /** Remember the pieces for this draw. Safe to call when parts are absent. */
    public static void begin(LivingEntity entity, JsonObject parts) {
        end();
        try {
            if (entity == null || parts == null || !parts.has("bodyParts") || !parts.get("bodyParts").isJsonArray()) {
                return;
            }
            Resolved resolved =
                    collect(BodyPartWire.resolve(
                            BodyPartWire.parse(parts.getAsJsonArray("bodyParts")), BodyPartPreview::piecesFor));
            if (resolved.meshes().isEmpty()) {
                return;
            }
            // The live player's own race layer already draws the Saiyan tail. Don't stack
            // tail_fur on top of it. A preview without that character still gets the mesh.
            boolean suppressNaturalTail = suppressNaturalTailOf(parts);
            boolean hideRaceTail =
                    (resolved.aliasTailOnly() || suppressNaturalTail) && raceTailAlreadyShown(entity);
            Set<String> meshes = resolved.aliasTailOnly() && hideRaceTail
                            ? resolved.noTail()
                            : Set.copyOf(resolved.meshes());
            if (meshes.isEmpty()) {
                return;
            }
            ACTIVE.set(new Active(entityUuid(entity), meshes, colorsOf(parts), parts.toString(), suppressNaturalTail));
        } catch (Throwable ignored) {
            end();
        }
    }

    public static void end() {
        ACTIVE.remove();
    }

    /** LM 4.7.61: true only while an LM native menu draws its model preview (the mixins act then only). */
    public static boolean previewActive() {
        return ACTIVE.get() != null;
    }

    /** The preview's meshes for {@code player}, or empty (never the world-sync state). */
    public static Set<String> previewMeshesFor(Object player) {
        Active active = ACTIVE.get();
        if (active == null || active.meshes() == null) {
            return Set.of();
        }
        UUID want = active.playerId();
        UUID got = entityUuid(player);
        return want == null || got == null || want.equals(got) ? active.meshes() : Set.of();
    }

    /**
     * Meshes to union into DMZUltra's look for this player. A menu preview overrides for
     * its subject; everyone else (world render) gets the last server-synced snapshot
     * (message 3). Empty when neither holds.
     */
    public static Set<String> meshesFor(Object player) {
        Active active = ACTIVE.get();
        UUID got = entityUuid(player);
        if (active != null && active.meshes != null && !active.meshes.isEmpty()) {
            UUID want = active.playerId;
            if (want == null || got == null || want.equals(got)) {
                return active.meshes;
            }
        }
        return worldMeshes(player, got);
    }

    /** Server-synced world state for one entity (never the menu preview). */
    private static Set<String> worldMeshes(Object entity, UUID id) {
        return worldMeshes(entity, id, raceTailAlreadyShown(entity));
    }

    /**
     * World-sync mesh resolution with the race-tail check injectable, so tests can stand in
     * for a character that has one without needing DragonMineZ's capability present.
     *
     * <p>{@code suppressNaturalTail} joins the alias check: the server has seen a chosen tail
     * piece, so the race tail is hidden (see {@link #suppressesRaceTail}) and this entity's
     * only tail is the one the kit serves. The aliased natural tail still yields — mirroring
     * {@code noTail} — so it can never reappear next to a chosen piece. A chosen tail whose
     * own mesh is {@code tail_fur} is a real piece, not the alias, and is left alone.
     */
    static Set<String> worldMeshes(Object entity, UUID id, boolean raceTailShown) {
        if (id == null) {
            return Set.of();
        }
        UUID local = localId;
        if (local == null) {
            local = resolveLocalId();
            if (local != null) {
                localId = local;
            }
        }
        WorldState state = local != null && local.equals(id) ? localState : WORLD.get(id);
        if (state == null || state.meshes().isEmpty()) {
            return Set.of();
        }
        boolean hideRaceTail = raceTailShown
                && (state.aliasTailOnly() || state.suppressNaturalTail());
        return hideRaceTail ? state.noTail() : state.meshes();
    }

    /**
     * True when the race tail must be hidden for this entity — geo bones {@code tail1} and
     * {@code tail1m}, which DragonMineZ draws from {@code isHasSaiyanTail()} on its own and
     * which {@code bodyParts} cannot dedupe against. Two independent draw paths, so a player
     * with a natural tail and a chosen shop tail otherwise shows both.
     *
     * <p>Evaluated per frame: unequipping the piece re-sends {@code suppressNaturalTail:false}
     * and the natural tail renders again, so nothing about the natural tail changes.
     */
    public static boolean suppressesRaceTail(Object player) {
        return suppressesRaceTail(player, raceTailAlreadyShown(player));
    }

    /** See {@link #suppressesRaceTail(Object)}; {@code raceTailShown} is injectable for tests. */
    static boolean suppressesRaceTail(Object player, boolean raceTailShown) {
        if (!raceTailShown) {
            return false;
        }
        try {
            Active active = ACTIVE.get();
            UUID id = entityUuid(player);
            if (active != null && active.suppressNaturalTail()) {
                UUID want = active.playerId();
                if (want == null || id == null || want.equals(id)) {
                    return true;
                }
            }
            if (id == null) {
                return false;
            }
            UUID local = localId;
            if (local == null) {
                local = resolveLocalId();
                if (local != null) {
                    localId = local;
                }
            }
            WorldState state = local != null && local.equals(id) ? localState : WORLD.get(id);
            return state != null && state.suppressNaturalTail();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Copy {@code look} (or build one) so its design includes {@code extra} piece ids.
     * When the payload carried {@code colors}, each present key <b>overrides</b> the
     * reflective value — a remote player's reflective {@code FormParts$Look} read comes
     * back gray (0x808080 matches none of the fallbacks), and the server's values are the
     * authoritative mirror of {@code FormParts.of}. {@code FormParts.of} returns null for a
     * form with no design, set or extras (base form, SSJ); then a fresh Look is built from the
     * pieces and colors ({@link #newLook}), as KaiPath 1.5.6 does, so Body Parts Shop pieces
     * still draw. Returns the original look when DMZUltra's types are missing, or when there
     * is neither a piece nor a color to apply.
     */
    public static Object mergeLook(Object look, Set<String> extra) {
        return mergeLook(look, extra, null, null);
    }

    /** See {@link #mergeLook(Object, Set, JsonObject, String)}. */
    public static Object mergeLook(Object look, Set<String> extra, JsonObject colors) {
        return mergeLook(look, extra, colors, null);
    }

    /**
     * Pieces + colors + the raw payload in one merge. Color precedence: {@code colors}
     * when non-empty, else {@code parts.colors} parsed from {@code partsJson}, else the
     * reflective read. {@code partsJson} is threaded so future per-sync metadata reaches
     * this merge site without another signature wall — the same wall that stranded
     * {@code colors} until it got its own parameter.
     *
     * @param colors {@code parts.colors} ({@code skin}/{@code second}/{@code hair}/
     *     {@code own}/{@code trim}/{@code horn}/{@code light}); {@code null} = derive from
     *     {@code partsJson}, else keep the reflective read
     * @param partsJson the raw message-3 payload (or the menu-preview parts object);
     *     empty or {@code null} = no payload channel
     */
    public static Object mergeLook(Object look, Set<String> extra, JsonObject colors, String partsJson) {
        if ((colors == null || colors.size() == 0) && partsJson != null && !partsJson.isBlank()) {
            colors = payloadColors(partsJson);
        }
        boolean hasPieces = extra != null && !extra.isEmpty();
        boolean hasColors = colors != null && colors.size() > 0;
        if (!hasPieces && !hasColors) {
            return look;
        }
        if (look == null) {
            // No form look (base form, SSJ): build one so the pieces still draw.
            try {
                return newLook(extra, colors);
            } catch (Throwable ignored) {
                return null;
            }
        }
        try {
            // DMZUltra's Look and Design records grew components between versions (0.4.0 and 0.5.7 add
            // hair rules, hidden bones, worn sets). Rebuild each record from its own components and swap
            // only the named ones, so no fixed constructor has to match the installed version.
            Object design = component(look, "design");
            if (design == null) {
                return look;
            }
            LinkedHashSet<String> pieces = new LinkedHashSet<>();
            Object existing = component(design, "parts");
            if (existing instanceof Set<?> set) {
                for (Object piece : set) {
                    if (piece != null) {
                        pieces.add(String.valueOf(piece));
                    }
                }
            }
            if (suppressesFromPayload(partsJson)) {
                // A chosen tail piece is equipped: the race tail mesh leaves the look before the
                // chosen pieces go in, so a chosen piece whose own mesh is the race mesh still renders.
                pieces.remove(BodyPartWire.SAIYAN_TAIL_MESH);
            }
            if (hasPieces) {
                pieces.addAll(extra);
            }
            java.util.Map<String, Object> designSwap = new java.util.HashMap<>();
            designSwap.put("parts", pieces);
            for (String key : new String[] {"own", "trim", "horn", "light"}) {
                putColor(designSwap, colors, key);
            }
            java.util.Map<String, Object> lookSwap = new java.util.HashMap<>();
            lookSwap.put("design", rebuild(design, designSwap));
            for (String key : new String[] {"skin", "second", "hair"}) {
                putColor(lookSwap, colors, key);
            }
            return rebuild(look, lookSwap);
        } catch (Throwable ignored) {
            return look;
        }
    }

    static final String DESIGN_CLASS = "com.dmzultra.client.forms.FormParts$Design";
    static final String LOOK_CLASS = "com.dmzultra.client.forms.FormParts$Look";

    /**
     * A fresh {@code FormParts$Look} wearing {@code extra}, for a form whose {@code FormParts.of}
     * is null. Mirrors KaiPath 1.5.6 (createDesign / createLook): the widest public constructor
     * of each record (DMZUltra 0.4.0 to 0.5.7: Design(Set, int own, int trim, int horn, int light,
     * float front, float back, FormHairRule$Rule, Set hideBase, boolean lightFromBody3) and
     * Look(Design, int skin, int second, int hair, List sets, int third, List more, String model,
     * Set hidden)), server colors where sent, else DMZUltra's own defaults; every later
     * component empty.
     */
    static Object newLook(Set<String> extra, JsonObject colors) throws ReflectiveOperationException {
        ClassLoader loader = BodyPartPreview.class.getClassLoader();
        Class<?> designClass = Class.forName(DESIGN_CLASS, false, loader);
        Class<?> lookClass = Class.forName(LOOK_CLASS, false, loader);
        LinkedHashSet<String> pieces = new LinkedHashSet<>();
        if (extra != null) {
            pieces.addAll(extra);
        }
        Object design = null;
        for (java.lang.reflect.Constructor<?> ctor : widestFirst(designClass)) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length < 7 || types[0] != Set.class) {
                continue;
            }
            Object[] args = defaults(types);
            args[0] = Set.copyOf(pieces);
            args[1] = color(colors, "own", 0xFFFFFF);
            args[2] = color(colors, "trim", 0xE9C46A);
            args[3] = color(colors, "horn", 0xEFE6CF);
            args[4] = color(colors, "light", 0xFFFFFF);
            args[5] = 0f;
            args[6] = 0f;
            design = ctor.newInstance(args);
            break;
        }
        if (design == null) {
            throw new NoSuchMethodException("No supported FormParts$Design constructor");
        }
        for (java.lang.reflect.Constructor<?> ctor : widestFirst(lookClass)) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length < 4 || types[0] != designClass) {
                continue;
            }
            Object[] args = defaults(types);
            args[0] = design;
            args[1] = color(colors, "skin", 0xFFFFFF);
            args[2] = color(colors, "second", 0xFFFFFF);
            args[3] = color(colors, "hair", 0x202424);
            return ctor.newInstance(args);
        }
        throw new NoSuchMethodException("No supported FormParts$Look constructor");
    }

    /** Public constructors of {@code type}, most parameters first. */
    private static java.lang.reflect.Constructor<?>[] widestFirst(Class<?> type) {
        java.lang.reflect.Constructor<?>[] ctors = type.getConstructors();
        java.util.Arrays.sort(ctors, java.util.Comparator.comparingInt(
                (java.lang.reflect.Constructor<?> c) -> c.getParameterCount()).reversed());
        return ctors;
    }

    /** Empty value per parameter type: empty Set / List, "", false, 0; null for anything else (hair rule). */
    private static Object[] defaults(Class<?>[] types) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == Set.class) {
                args[i] = Set.of();
            } else if (t == List.class) {
                args[i] = List.of();
            } else if (t == String.class) {
                args[i] = "";
            } else if (t == boolean.class) {
                args[i] = Boolean.FALSE;
            } else if (t == int.class) {
                args[i] = 0;
            } else if (t == float.class) {
                args[i] = 0f;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    /** Server-sent color for {@code key}, when present. */
    private static void putColor(java.util.Map<String, Object> swap, JsonObject colors, String key) {
        try {
            if (colors != null && colors.has(key) && colors.get(key).isJsonPrimitive()) {
                swap.put(key, colors.get(key).getAsInt());
            }
        } catch (RuntimeException ignored) {
            // keep the record's own value
        }
    }

    /** One record component by name, or null. */
    static Object component(Object record, String name) throws ReflectiveOperationException {
        if (record == null || !record.getClass().isRecord()) {
            return null;
        }
        for (java.lang.reflect.RecordComponent c : record.getClass().getRecordComponents()) {
            if (c.getName().equals(name)) {
                Method accessor = c.getAccessor();
                accessor.setAccessible(true);
                return accessor.invoke(record);
            }
        }
        return null;
    }

    /**
     * Copy of {@code record} with the named components swapped. A swap whose value does not fit the
     * component's type (Look's int {@code hair} vs Design's hair rule) is ignored.
     */
    static Object rebuild(Object record, java.util.Map<String, Object> swap) throws ReflectiveOperationException {
        java.lang.reflect.RecordComponent[] comps = record.getClass().getRecordComponents();
        Class<?>[] types = new Class<?>[comps.length];
        Object[] values = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            Method accessor = comps[i].getAccessor();
            accessor.setAccessible(true);
            Object value = accessor.invoke(record);
            Object next = swap.get(comps[i].getName());
            if (next != null && (types[i].isInstance(next) || (types[i] == int.class && next instanceof Integer))) {
                value = next;
            }
            values[i] = value;
        }
        java.lang.reflect.Constructor<?> ctor = record.getClass().getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(values);
    }

    /** Present numeric color from {@code colors}, else {@code fallback}. */
    private static int color(JsonObject colors, String key, int fallback) {
        try {
            if (colors != null && colors.has(key) && colors.get(key).isJsonPrimitive()) {
                return colors.get(key).getAsInt();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return fallback;
    }

    static Set<String> piecesFor(String source) {
        if (source == null || source.isBlank()) {
            return Set.of();
        }
        if (BodyPartWire.SOURCE_DMZULTRA.equals(source)) {
            if (!dmzUltraInstalled()) {
                return Set.of();
            }
            Set<String> live = readPieces(source);
            return live.isEmpty() ? BodyPartWire.DMZULTRA_PIECES : live;
        }
        return readPieces(source);
    }

    private static boolean dmzUltraInstalled() {
        try {
            Class.forName("com.dmzultra.client.forms.FormParts", false, BodyPartPreview.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Set<String> readPieces(String source) {
        Set<String> cached = LOADED.get(source);
        if (cached != null) {
            return cached;
        }
        Set<String> loaded = tryLoadPieces(source);
        if (!loaded.isEmpty()) {
            LOADED.put(source, loaded);
        }
        return loaded;
    }

    /** {@code assets/<source>/form_parts/forms.json} → {@code pieces} array. Empty on any miss. */
    private static Set<String> tryLoadPieces(String source) {
        InputStream in = null;
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("m_91087_").invoke(null);
            if (mc == null) {
                return Set.of();
            }
            Object manager = null;
            for (String name : new String[] {"m_91098_", "getResourceManager"}) {
                try {
                    manager = mc.getClass().getMethod(name).invoke(mc);
                    if (manager != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                    // next accessor
                }
            }
            if (manager == null) {
                return Set.of();
            }
            Class<?> rl = Class.forName("net.minecraft.resources.ResourceLocation");
            Object location =
                    rl.getMethod("fromNamespaceAndPath", String.class, String.class)
                            .invoke(null, source, "form_parts/forms.json");
            Object resource = null;
            for (Method method : manager.getClass().getMethods()) {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(location)) {
                    Object found = method.invoke(manager, location);
                    if (found instanceof java.util.Optional<?> opt) {
                        resource = opt.orElse(null);
                    } else {
                        resource = found;
                    }
                    if (resource != null) {
                        break;
                    }
                }
            }
            if (resource == null) {
                return Set.of();
            }
            for (String name : new String[] {"m_215507_", "open"}) {
                try {
                    Object opened = resource.getClass().getMethod(name).invoke(resource);
                    if (opened instanceof InputStream stream) {
                        in = stream;
                        break;
                    }
                } catch (Throwable ignored) {
                    // next opener
                }
            }
            if (in == null) {
                return Set.of();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("pieces") || !root.get("pieces").isJsonArray()) {
                    return Set.of();
                }
                LinkedHashSet<String> pieces = new LinkedHashSet<>();
                JsonArray arr = root.getAsJsonArray("pieces");
                for (JsonElement el : arr) {
                    if (el != null && el.isJsonPrimitive()) {
                        String id = el.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
                        if (!id.isEmpty()) {
                            pieces.add(id);
                        }
                    }
                }
                return pieces.isEmpty() ? Set.of() : Set.copyOf(pieces);
            }
        } catch (Throwable ignored) {
            return Set.of();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                    // already consumed or closed by the reader
                }
            }
        }
    }

    private static boolean raceTailAlreadyShown(Object entity) {
        try {
            Class<?> capClass = Class.forName("com.dragonminez.common.stats.StatsCapability");
            Object cap = capClass.getField("INSTANCE").get(null);
            Class<?> provider = Class.forName("com.dragonminez.common.stats.StatsProvider");
            Class<?> capability = Class.forName("net.minecraftforge.common.capabilities.Capability");
            Class<?> entityType = Class.forName("net.minecraft.world.entity.Entity");
            Object lazy = provider.getMethod("get", capability, entityType).invoke(null, cap, entity);
            if (lazy == null) {
                return false;
            }
            Object stats = null;
            try {
                Object resolved = lazy.getClass().getMethod("resolve").invoke(lazy);
                if (resolved instanceof java.util.Optional<?> opt) {
                    stats = opt.orElse(null);
                }
            } catch (Throwable ignored) {
                stats = lazy.getClass().getMethod("orElse", Object.class).invoke(lazy, new Object[] {null});
            }
            if (stats == null) {
                return false;
            }
            Object character = stats.getClass().getMethod("getCharacter").invoke(stats);
            if (character == null) {
                return false;
            }
            Object flag = character.getClass().getMethod("isHasSaiyanTail").invoke(character);
            return flag instanceof Boolean on && on;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static UUID entityUuid(Object entity) {
        if (entity == null) {
            return null;
        }
        for (String name : new String[] {"m_20148_", "getUUID"}) {
            try {
                Object id = entity.getClass().getMethod(name).invoke(entity);
                if (id instanceof UUID uuid) {
                    return uuid;
                }
            } catch (Throwable ignored) {
                // next accessor
            }
        }
        return null;
    }

    private static int integer(Object target, String method, int fallback) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            if (value instanceof Number number) {
                return number.intValue();
            }
        } catch (Throwable ignored) {
            // keep fallback
        }
        return fallback;
    }

    private static float decimal(Object target, String method, float fallback) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            if (value instanceof Number number) {
                return number.floatValue();
            }
        } catch (Throwable ignored) {
            // keep fallback
        }
        return fallback;
    }
}
