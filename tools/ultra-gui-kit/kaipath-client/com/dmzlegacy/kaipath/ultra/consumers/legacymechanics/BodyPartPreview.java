package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
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

    /** Server-synced world state (message 3), keyed by player UUID. */
    private static final ConcurrentHashMap<UUID, WorldState> WORLD = new ConcurrentHashMap<>();

    private static volatile WorldState localState = WorldState.EMPTY;
    private static volatile UUID localId;

    private BodyPartPreview() {}

    private record Active(UUID playerId, Set<String> meshes, JsonObject colors, String partsJson) {}

    /**
     * Cached world parts for one subject: the raw {@code partsJson} (threaded to the merge
     * site via {@link #partsJsonFor} so future per-sync metadata reaches
     * {@link #mergeLook(Object, Set, JsonObject, String)} without another signature wall),
     * the resolved meshes, and the server-sent {@code colors} (per-category model colors;
     * {@code null} when the payload has none). {@code noTail} is {@code meshes} minus the
     * aliased Saiyan tail — served when the entity's race layer already draws one.
     */
    record WorldState(String partsJson, Set<String> meshes, Set<String> noTail, boolean aliasTailOnly, JsonObject colors) {
        static final WorldState EMPTY = new WorldState("", Set.of(), Set.of(), false, null);
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
        try {
            WorldState state = stateFromParts(partsJson, BodyPartPreview::piecesFor);
            String subject = subjectOf(partsJson);
            if (subject.startsWith("uuid:")) {
                try {
                    UUID id = UUID.fromString(subject.substring("uuid:".length()));
                    WORLD.put(id, state);
                    UUID local = localId != null ? localId : resolveLocalId();
                    if (local != null) {
                        localId = local;
                        if (local.equals(id)) {
                            localState = state;
                        }
                    }
                } catch (Throwable ignored) {
                    // unusable subject id — keep the previous state
                }
                return;
            }
            localState = state;
            localId = resolveLocalId();
        } catch (Throwable ignored) {
            // a bad payload must not crash the network handler
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
        WORLD.clear();
        localState = WorldState.EMPTY;
        localId = null;
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
            if (!parts.has("bodyParts") || !parts.get("bodyParts").isJsonArray()) {
                return new WorldState(partsJson, Set.of(), Set.of(), false, colors);
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
            Set<String> meshes =
                    resolved.aliasTailOnly() && raceTailAlreadyShown(entity)
                            ? resolved.noTail()
                            : Set.copyOf(resolved.meshes());
            if (meshes.isEmpty()) {
                return;
            }
            ACTIVE.set(new Active(entityUuid(entity), meshes, colorsOf(parts), parts.toString()));
        } catch (Throwable ignored) {
            end();
        }
    }

    public static void end() {
        ACTIVE.remove();
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
        // The entity's race layer may already draw the Saiyan tail (aliased mesh).
        return state.aliasTailOnly() && raceTailAlreadyShown(entity)
                ? state.noTail()
                : state.meshes();
    }

    /**
     * Copy {@code look} (or build one) so its design includes {@code extra} piece ids.
     * When the payload carried {@code colors}, each present key <b>overrides</b> the
     * reflective value — a remote player's reflective {@code FormParts$Look} read comes
     * back gray (0x808080 matches none of the fallbacks), and the server's values are the
     * authoritative mirror of {@code FormParts.of}. Returns the original look when
     * DMZUltra's types are missing, or when there is neither a piece nor a color to apply.
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
        try {
            Class<?> designC = Class.forName("com.dmzultra.client.forms.FormParts$Design");
            Class<?> lookC = Class.forName("com.dmzultra.client.forms.FormParts$Look");
            LinkedHashSet<String> pieces = new LinkedHashSet<>();
            int own = 0xFFFFFF;
            int trim = 0xE9C46A;
            int horn = 0xEFE6CF;
            int light = 0xFFFFFF;
            float front = 0f;
            float back = 0f;
            int skin = 0xFFFFFF;
            int second = 0xFFFFFF;
            int hair = 0x202424;
            if (look != null && lookC.isInstance(look)) {
                Object design = lookC.getMethod("design").invoke(look);
                if (design != null) {
                    Object existing = designC.getMethod("parts").invoke(design);
                    if (existing instanceof Set<?> set) {
                        for (Object piece : set) {
                            if (piece != null) {
                                pieces.add(String.valueOf(piece));
                            }
                        }
                    }
                    own = integer(design, "own", own);
                    trim = integer(design, "trim", trim);
                    horn = integer(design, "horn", horn);
                    light = integer(design, "light", light);
                    front = decimal(design, "front", front);
                    back = decimal(design, "back", back);
                }
                skin = integer(look, "skin", skin);
                second = integer(look, "second", second);
                hair = integer(look, "hair", hair);
            }
            // Server-sent colors win over the reflective read (the gray-parts fix).
            own = color(colors, "own", own);
            trim = color(colors, "trim", trim);
            horn = color(colors, "horn", horn);
            light = color(colors, "light", light);
            skin = color(colors, "skin", skin);
            second = color(colors, "second", second);
            hair = color(colors, "hair", hair);
            if (hasPieces) {
                pieces.addAll(extra);
            }
            Object design =
                    designC.getConstructor(
                                    Set.class, int.class, int.class, int.class, int.class, float.class, float.class)
                            .newInstance(pieces, own, trim, horn, light, front, back);
            return lookC.getConstructor(designC, int.class, int.class, int.class)
                    .newInstance(design, skin, second, hair);
        } catch (Throwable ignored) {
            return look;
        }
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
