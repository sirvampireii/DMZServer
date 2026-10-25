package com.dbzlegacy.adaptivedifficulty.config.editor;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.dbzlegacy.adaptivedifficulty.config.ConfigRegistry;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.HotPathConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopHooks;
import com.dbzlegacy.adaptivedifficulty.quest.SagaResetConfig;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Staff config editor. Rows come from public fields on the live config objects,
 * so a new field shows up without a new button.
 * Player record files (rivalry, sparring, rival progression) stay out of this
 * editor. Those maps are live player data, not settings.
 */
public final class ConfigEditor {
    /** Scroll lines stay under the CNPC 64-character trim. */
    private static final int LINE_MAX = 60;
    private static final int LIST_CAP = 200;
    private static final Set<Path> BACKED_THIS_SESSION = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Proposal> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final DateTimeFormatter AUDIT_WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter BACKUP_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    /** Timestamped copies kept per settings file; older ones are pruned on each save. */
    static final int BACKUPS_KEPT = 20;
    /** Last successful editor save per settings file — how other open editors learn of it. */
    private static final Map<Path, FileStamp> FILE_STAMPS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong SAVE_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private ConfigEditor() {}

    public enum Kind {
        BOOLEAN, NUMBER, TEXT, ENUM, GROUP, LIST, MAP, OTHER
    }

    public record Root(
            String id,
            String title,
            String fileName,
            Supplier<Object> target,
            Runnable persist,
            Set<String> rootFields) {}

    /** A change waiting on the confirm page. {@code mode} is set, add, or remove. */
    public record Proposal(String rootId, String path, String raw, String mode, String addKey) {}

    /** Player-facing preview. {@code error} is set when the typed value cannot be saved. */
    public record ChangePreview(String error, String sentence) {
        public static ChangePreview ok(String sentence) {
            return new ChangePreview(null, sentence);
        }

        public static ChangePreview bad(String error) {
            return new ChangePreview(error, null);
        }
    }

    public record Entry(String line, String path, Kind kind) {}

    /** One search hit across modules — breadcrumb uses {@link EditorTab#label()}. */
    public record SearchHit(String moduleId, String moduleLabel, Entry entry) {}

    public enum AddMode {
        NONE, VALUE, KEY_AND_VALUE
    }

    /** One module tab. Rival and sparring stay visible and closed. */
    public record EditorTab(String id, String label, boolean editable, String fileName) {}

    /** One row on the prestige Modules page. Closed modules stay visible. */
    public record ModuleLink(String id, String label, boolean open, int fields) {}

    /** Fields that share a prefix inside one tab. */
    public record FieldGroup(String name, List<Entry> fields) {}

    /** One staff section inside a module. {@code openPath} is a field path or a {@code ~Section} key. */
    public record SectionLink(String title, String openPath, int count) {}

    /** Page paths that start with this are a named section, not a nested field. */
    public static final String SECTION_PREFIX = "~";

    /**
     * One unsaved edit, newest first when listed. {@code changedBy} names the staff member who
     * saved a new value for this same setting after it was staged (blank when nobody did).
     * {@code moduleId} / {@code path} let Review look up who last saved that setting.
     */
    public record PendingChange(
            String name, String before, String after, String changedBy, String moduleId, String path) {}

    public enum DraftOp { SET, ADD, REMOVE }

    /**
     * A change held in memory until Save all. {@code base} is the live value when it was staged,
     * so a later save by another editor to the same setting is caught instead of overwritten.
     */
    public record Draft(String moduleId, String path, DraftOp op, String raw, String addKey, String base) {}

    public enum BannerTone { NONE, OK, INFO, ERROR }

    /**
     * {@code error} is set when a save was refused. {@code message} is the status line.
     * {@code report} lists what was written, what reloaded or failed, and the backup.
     */
    public record SaveResult(String error, int count, String message, List<ReportLine> report) {
        SaveResult(String error, int count, String message) {
            this(error, count, message, List.of());
        }
    }

    /** One report line; {@code failed} lines render as errors. */
    public record ReportLine(String text, boolean failed) {}

    /** The last editor save of one settings file. */
    private record FileStamp(long version, UUID by, String actor, Instant when) {}

    /** Another staff member saved a file this editor has unsaved changes for. */
    public record EditConflict(String actor, String when, String modules) {}

    public record LastChange(String actor, String when) {}

    /** One list or map row, including entries that are only staged. */
    public record ManageRow(
            String title, String path, String key, boolean added, boolean removing, String value) {}

    public static List<Root> roots() {
        List<Root> roots = new ArrayList<>();
        for (ConfigRegistry.Module module : ConfigRegistry.menuModules()) {
            String fileName = module.file() == null ? module.id() : module.file().getFileName().toString();
            roots.add(new Root(
                    module.id(),
                    module.title(),
                    fileName,
                    module.instance(),
                    module.save(),
                    module.rootFields()));
        }
        return roots;
    }

    public static Root root(String id) {
        if (id == null) {
            return null;
        }
        for (Root root : roots()) {
            if (root.id().equals(id)) {
                return root;
            }
        }
        return null;
    }

    public static List<Entry> children(String rootId, String path) {
        Located located = locate(rootId, path == null ? "" : path);
        if (located == null || located.value == null) {
            return List.of();
        }
        Object value = located.value;
        if (value instanceof Map<?, ?> map) {
            return mapEntries(path, map, valueType(located.field, 1));
        }
        if (value instanceof List<?> list) {
            return listEntries(path, list, valueType(located.field, 0));
        }
        if (isGroup(value.getClass())) {
            Set<String> only = null;
            if (path == null || path.isEmpty()) {
                Root root = root(rootId);
                only = root == null ? null : root.rootFields();
            }
            return fieldEntries(path, value, only);
        }
        return List.of();
    }

    public static int hiddenCount(String rootId, String path) {
        Located located = locate(rootId, path == null ? "" : path);
        if (located == null || located.value == null) {
            return 0;
        }
        int size = 0;
        if (located.value instanceof Map<?, ?> map) {
            size = map.size();
        } else if (located.value instanceof List<?> list) {
            size = list.size();
        }
        return Math.max(0, size - LIST_CAP);
    }

    public static Kind kindOf(String rootId, String path) {
        Located located = locate(rootId, path == null ? "" : path);
        if (located == null) {
            return Kind.OTHER;
        }
        if (located.value == null && located.field != null) {
            return kindForType(rawClass(located.field.getGenericType()));
        }
        return kindOfValue(located.value, located.field);
    }

    public static AddMode addMode(String rootId, String path) {
        Kind kind = kindOf(rootId, path);
        if (kind == Kind.MAP) {
            return AddMode.KEY_AND_VALUE;
        }
        if (kind == Kind.LIST) {
            return AddMode.VALUE;
        }
        return AddMode.NONE;
    }

    public static String currentText(String rootId, String path) {
        Located located = locate(rootId, path);
        if (located == null || located.value == null) {
            return "";
        }
        return String.valueOf(located.value);
    }

    public static List<String> editLines(String rootId, String path) {
        Located located = locate(rootId, path);
        List<String> lines = new ArrayList<>();
        if (located == null) {
            lines.add(UltraStyle.LABEL + "That setting is not on this config.");
            return lines;
        }
        lines.add(UltraStyle.LABEL + "Current " + UltraStyle.VALUE + shortText(located.value));
        if (located.field != null && located.field.getType().isEnum()) {
            Object[] constants = located.field.getType().getEnumConstants();
            StringBuilder names = new StringBuilder();
            for (Object constant : constants) {
                if (names.length() > 80) {
                    names.append(" ...");
                    break;
                }
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(constant);
            }
            lines.add(UltraStyle.LABEL + "Use one of " + UltraStyle.VALUE + names);
        } else {
            lines.add(UltraStyle.LABEL + "Saving writes this config file.");
        }
        return lines;
    }

    /** @return a player-facing error, or null when the value was saved */
    public static String apply(String rootId, String path, String raw) {
        return apply(rootId, path, raw, "staff");
    }

    /** @return a player-facing error, or null when the value was saved */
    public static String apply(String rootId, String path, String raw, String actor) {
        Root root = root(rootId);
        if (root == null) {
            return "That config is not editable.";
        }
        Located located = locate(rootId, path);
        String before = located == null || located.value == null ? "empty" : String.valueOf(located.value);
        String error = mutateSet(rootId, path, raw);
        if (error != null) {
            return error;
        }
        String after = raw == null ? "" : raw.trim();
        return persist(root, actor, path, before, after);
    }

    /** @return a player-facing error, or null when the boolean was flipped and saved */
    public static String toggle(String rootId, String path) {
        return toggle(rootId, path, "staff");
    }

    /** @return a player-facing error, or null when the boolean was flipped and saved */
    public static String toggle(String rootId, String path, String actor) {
        Located located = locate(rootId, path);
        if (located == null || located.value == null) {
            return "That setting is not on this config.";
        }
        if (!(located.value instanceof Boolean current)) {
            return "That setting is not a switch.";
        }
        return apply(rootId, path, Boolean.toString(!current), actor);
    }

    /** @return a player-facing error, or null when the entry was removed and saved */
    public static String remove(String rootId, String path) {
        return remove(rootId, path, "staff");
    }

    /** @return a player-facing error, or null when the entry was removed and saved */
    public static String remove(String rootId, String path, String actor) {
        Root root = root(rootId);
        if (root == null) {
            return "That config is not editable.";
        }
        Located located = locate(rootId, path);
        String before = located == null || located.value == null ? "empty" : String.valueOf(located.value);
        String error = mutateRemove(rootId, path);
        if (error != null) {
            return error;
        }
        return persist(root, actor, path, before, "(removed)");
    }

    /** @return a player-facing error, or null when the entry was added and saved */
    public static String add(String rootId, String path, String key, String raw) {
        return add(rootId, path, key, raw, "staff");
    }

    /** @return a player-facing error, or null when the entry was added and saved */
    public static String add(String rootId, String path, String key, String raw, String actor) {
        Root root = root(rootId);
        if (root == null) {
            return "That config is not editable.";
        }
        String error = mutateAdd(rootId, path, key, raw);
        if (error != null) {
            return error;
        }
        String label = key == null || key.isBlank() ? raw : key.trim() + "=" + raw;
        return persist(root, actor, path, "(added)", label);
    }

    public static void stage(UUID player, Proposal proposal) {
        if (player != null && proposal != null) {
            PENDING.put(player, proposal);
        }
    }

    public static ChangePreview preview(UUID player) {
        Proposal proposal = player == null ? null : PENDING.get(player);
        if (proposal == null) {
            return ChangePreview.bad("That change expired. Pick the setting again.");
        }
        if ("remove".equals(proposal.mode())) {
            return ChangePreview.ok("Remove " + displayName(leafName(proposal.path()))
                    + ". Current value: " + currentText(proposal.rootId(), proposal.path()) + ".");
        }
        if ("add".equals(proposal.mode())) {
            String name = proposal.addKey() == null || proposal.addKey().isBlank()
                    ? proposal.raw()
                    : proposal.addKey() + " = " + proposal.raw();
            return ChangePreview.ok("Add " + name + ".");
        }
        return describeSet(proposal.rootId(), proposal.path(), proposal.raw());
    }

    public static String commit(UUID player, String actor) {
        Proposal proposal = player == null ? null : PENDING.remove(player);
        if (proposal == null) {
            return "That change expired. Pick the setting again.";
        }
        String who = actor == null || actor.isBlank() ? "staff" : actor;
        return switch (proposal.mode()) {
            case "add" -> add(proposal.rootId(), proposal.path(), proposal.addKey(), proposal.raw(), who);
            case "remove" -> remove(proposal.rootId(), proposal.path(), who);
            default -> apply(proposal.rootId(), proposal.path(), proposal.raw(), who);
        };
    }

    /** Next number in the same direction as {@code direction} ({@code -1} or {@code 1}), not saved yet. */
    public static String stepNumber(String rootId, String path, int direction) {
        return stepNumber(rootId, path, null, direction);
    }

    /**
     * Steps from {@code currentRaw} when that text is a number, otherwise from the live value.
     * Nothing is written.
     */
    public static String stepNumber(String rootId, String path, String currentRaw, int direction) {
        Located located = locate(rootId, path);
        if (located == null) {
            return null;
        }
        Class<?> type = numberType(located);
        if (type == null) {
            return null;
        }
        String raw = currentRaw == null || currentRaw.isBlank() ? currentText(rootId, path) : currentRaw.trim();
        int dir = direction < 0 ? -1 : 1;
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            double current;
            try {
                current = Double.parseDouble(raw.replace(",", ""));
            } catch (NumberFormatException e) {
                if (!(located.value instanceof Number number)) {
                    return null;
                }
                current = number.doubleValue();
            }
            double step = Math.abs(current) >= 20.0 ? 1.0 : 0.1;
            double next = current + dir * step;
            if (Double.isNaN(next) || Double.isInfinite(next)) {
                return null;
            }
            String text = String.format(Locale.ROOT, "%.4f", next);
            while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
        long current;
        try {
            String number = raw.replace(",", "");
            int dot = number.indexOf('.');
            if (dot >= 0) {
                number = number.substring(0, dot);
            }
            current = Long.parseLong(number);
        } catch (NumberFormatException e) {
            if (!(located.value instanceof Number number)) {
                return null;
            }
            current = number.longValue();
        }
        return Long.toString(current + dir);
    }

    /** Next enum name, not saved yet. */
    public static String nextEnumValue(String rootId, String path) {
        return nextEnumValue(rootId, path, null);
    }

    /** Next enum name after {@code currentRaw}, or after the live value when that text is blank. */
    public static String nextEnumValue(String rootId, String path, String currentRaw) {
        Located located = locate(rootId, path);
        if (located == null || located.field == null) {
            return null;
        }
        Class<?> type = located.key == null
                ? located.field.getType()
                : rawClass(valueType(located.field, located.container instanceof List<?> ? 0 : 1));
        if (type == null || !type.isEnum()) {
            return null;
        }
        Object[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return null;
        }
        String current = currentRaw == null || currentRaw.isBlank()
                ? String.valueOf(located.value)
                : currentRaw.trim();
        int index = 0;
        for (int i = 0; i < constants.length; i++) {
            if (constants[i].toString().equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }
        return constants[(index + 1) % constants.length].toString();
    }

    private static ChangePreview describeSet(String rootId, String path, String raw) {
        Located located = locate(rootId, path);
        if (located == null) {
            return ChangePreview.bad("That setting is not on this config.");
        }
        try {
            Object next;
            if (located.key != null) {
                int typeIndex = located.container instanceof List<?> ? 0 : 1;
                next = coerce(valueType(located.field, typeIndex), raw);
            } else if (located.field != null) {
                if ("*".equals(raw == null ? "" : raw.trim())
                        && located.field.getName().toLowerCase(Locale.ROOT).contains("permission")) {
                    return ChangePreview.bad("A permission node can't be *.");
                }
                next = coerce(located.field.getGenericType(), raw);
            } else {
                return ChangePreview.bad("That setting can't be edited here.");
            }
            String before = located.value == null ? "empty" : String.valueOf(located.value);
            return ChangePreview.ok(displayName(leafName(path)) + ": " + before + " -> " + next);
        } catch (NumberFormatException e) {
            return ChangePreview.bad("That needs to be a number.");
        } catch (IllegalArgumentException e) {
            return ChangePreview.bad(friendlyEditError(e.getMessage()));
        }
    }

    private static String leafName(String path) {
        if (path == null || path.isEmpty()) {
            return "Setting";
        }
        int bracket = path.lastIndexOf('[');
        if (path.endsWith("]") && bracket >= 0) {
            return path.substring(bracket + 1, path.length() - 1);
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    public static String parentPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (path.endsWith("]")) {
            int bracket = path.lastIndexOf('[');
            return bracket <= 0 ? "" : path.substring(0, bracket);
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(0, dot);
    }

    /** Tabs in menu order. Data files stay on the bar and stay closed. */
    public static List<EditorTab> editorTabs() {
        List<EditorTab> tabs = new ArrayList<>();
        addTab(tabs, "difficulty", "Difficulty");
        addTab(tabs, "progression", "Progression");
        addTab(tabs, "character-services", "Character");
        addTab(tabs, "saga", "Saga");
        addTab(tabs, "rival", "Rival");
        addTab(tabs, "sparring", "Sparring");
        addTab(tabs, "currency", "Currency");
        addTab(tabs, "hot-path", "Combat");
        return tabs;
    }

    /** Editor tabs first, then later slices that stay closed. */
    public static List<ModuleLink> moduleLinks() {
        List<ModuleLink> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EditorTab tab : editorTabs()) {
            seen.add(tab.id());
            boolean open = tab.editable();
            int fields = open ? children(tab.id(), "").size() : 0;
            links.add(new ModuleLink(tab.id(), tab.label(), open, fields));
        }
        for (ConfigRegistry.Module module : ConfigRegistry.all()) {
            if (!seen.add(module.id())) {
                continue;
            }
            String label = "tier".equals(module.id()) ? "Difficulty tiers" : module.title();
            boolean open = module.editable() && module.phase() == ConfigRegistry.PHASE_MENU;
            int fields = open ? children(module.id(), "").size() : 0;
            links.add(new ModuleLink(module.id(), label, open, fields));
        }
        return links;
    }

    /** Leaf settings in a module, used to decide when the search prompt appears. */
    public static int fieldCount(String moduleId) {
        List<Entry> all = new ArrayList<>();
        walkFields(moduleId, "", all, 0);
        int count = 0;
        for (Entry entry : all) {
            if (entry.kind() != Kind.GROUP) {
                count++;
            }
        }
        return count;
    }

    /**
     * Groups the fields on this page by a shared prefix.
     * A nested path stays flat. A group of one field joins Other.
     */
    public static List<FieldGroup> groups(String moduleId, String path) {
        List<Entry> fields = children(moduleId, path == null ? "" : path);
        if (path != null && !path.isEmpty()) {
            return List.of(new FieldGroup("", fields));
        }
        Map<String, List<Entry>> buckets = new LinkedHashMap<>();
        for (Entry entry : fields) {
            buckets.computeIfAbsent(groupName(entry.path()), key -> new ArrayList<>()).add(entry);
        }
        List<Entry> other = new ArrayList<>();
        List<String> singles = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> entry : buckets.entrySet()) {
            if (entry.getValue().size() < 2 && !"Other".equals(entry.getKey())) {
                other.addAll(entry.getValue());
                singles.add(entry.getKey());
            }
        }
        for (String key : singles) {
            buckets.remove(key);
        }
        if (!other.isEmpty()) {
            buckets.computeIfAbsent("Other", key -> new ArrayList<>()).addAll(other);
        }
        List<String> order = new ArrayList<>();
        for (String fixed : SECTION_ORDER) {
            if (buckets.containsKey(fixed)) {
                order.add(fixed);
            }
        }
        List<String> rest = new ArrayList<>();
        for (String key : buckets.keySet()) {
            if (!order.contains(key) && !"Other".equals(key)) {
                rest.add(key);
            }
        }
        rest.sort(String.CASE_INSENSITIVE_ORDER);
        order.addAll(rest);
        if (buckets.containsKey("Other")) {
            order.add("Other");
        }
        List<FieldGroup> out = new ArrayList<>();
        for (String name : order) {
            out.add(new FieldGroup(name, buckets.get(name)));
        }
        return out;
    }

    public static EditorTab tab(String id) {
        for (EditorTab tab : editorTabs()) {
            if (tab.id().equals(id)) {
                return tab;
            }
        }
        return null;
    }

    public static void rememberSearch(UUID player, String query) {
        session(player).search = query == null ? "" : query.trim();
    }

    public static String search(UUID player) {
        return session(player).search;
    }

    public static void setBanner(UUID player, String text, BannerTone tone) {
        Session session = session(player);
        session.banner = text == null ? "" : text;
        session.tone = text == null || text.isBlank() ? BannerTone.NONE : tone;
    }

    public static String banner(UUID player) {
        return session(player).banner;
    }

    public static BannerTone bannerTone(UUID player) {
        return session(player).tone;
    }

    public static void clearBanner(UUID player) {
        setBanner(player, "", BannerTone.NONE);
    }

    public static int dirtyCount(UUID player) {
        return session(player).drafts.size();
    }

    public static boolean dirty(UUID player, String moduleId, String path) {
        if (path == null) {
            return false;
        }
        for (Draft draft : session(player).drafts) {
            if (!draft.moduleId().equals(moduleId)) {
                continue;
            }
            if (path.equals(draft.path())) {
                return true;
            }
            if (draft.path().startsWith(path + ".") || draft.path().startsWith(path + "[")) {
                return true;
            }
        }
        return false;
    }

    /** Fields on this page, or every matching name/description/field id in the module when {@code query} is set. */
    public static List<Entry> visibleFields(String moduleId, String path, String query) {
        if (query == null || query.isBlank()) {
            return children(moduleId, path == null ? "" : path);
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> all = new ArrayList<>();
        walkFields(moduleId, "", all, 0);
        List<Entry> matched = new ArrayList<>();
        for (Entry entry : all) {
            if (matchesQuery(moduleId, entry, needle)) {
                matched.add(entry);
            }
        }
        return matched;
    }

    /**
     * Search across every editor tab (Difficulty…Currency). Matches plain name, field id, and
     * one-line description. Used by the Ultra search-first config editor.
     */
    public static List<SearchHit> searchAll(String query) {
        List<SearchHit> out = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return out;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (EditorTab tab : editorTabs()) {
            if (tab == null || tab.id() == null || tab.id().isBlank()) {
                continue;
            }
            List<Entry> all = new ArrayList<>();
            walkFields(tab.id(), "", all, 0);
            for (Entry entry : all) {
                if (entry == null || entry.kind() == Kind.GROUP) {
                    continue;
                }
                if (matchesQuery(tab.id(), entry, needle)) {
                    out.add(new SearchHit(tab.id(), tab.label(), entry));
                }
            }
        }
        return out;
    }

    private static boolean matchesQuery(String moduleId, Entry entry, String needle) {
        if (entry == null || needle == null || needle.isBlank()) {
            return false;
        }
        String leaf = leafName(entry.path());
        String name = displayName(leaf).toLowerCase(Locale.ROOT);
        if (name.contains(needle) || leaf.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        String path = entry.path() == null ? "" : entry.path().toLowerCase(Locale.ROOT);
        if (path.contains(needle)) {
            return true;
        }
        String desc = friendlyDescription(moduleId, entry.path(), currentText(moduleId, entry.path()));
        return desc != null && desc.toLowerCase(Locale.ROOT).contains(needle);
    }

    public static String effectiveRaw(UUID player, String moduleId, String path) {
        Draft draft = findDraft(player, moduleId, path, DraftOp.SET);
        if (draft != null) {
            return draft.raw() == null ? "" : draft.raw();
        }
        return currentText(moduleId, path);
    }

    public static String shownValue(UUID player, String moduleId, String path) {
        Kind kind = kindOf(moduleId, path);
        if (kind == Kind.LIST || kind == Kind.MAP) {
            int count = effectiveCount(player, moduleId, path);
            return count == 1 ? "1 entry" : count + " entries";
        }
        if (kind == Kind.GROUP) {
            int count = children(moduleId, path).size();
            return count == 1 ? "1 setting" : count + " settings";
        }
        String raw = effectiveRaw(player, moduleId, path);
        if (kind == Kind.BOOLEAN) {
            return truth(raw) ? "ON" : "OFF";
        }
        if (raw == null || raw.isBlank()) {
            return "empty";
        }
        return raw;
    }

    public static int effectiveCount(UUID player, String moduleId, String path) {
        int live = 0;
        Located located = locate(moduleId, path);
        if (located != null && located.value instanceof Map<?, ?> map) {
            live = map.size();
        } else if (located != null && located.value instanceof Collection<?> collection) {
            live = collection.size();
        }
        int added = 0;
        int removed = 0;
        for (Draft draft : session(player).drafts) {
            if (!draft.moduleId().equals(moduleId)) {
                continue;
            }
            if (draft.op() == DraftOp.ADD && path.equals(draft.path())) {
                added++;
            }
            if (draft.op() == DraftOp.REMOVE && path.equals(parentPath(draft.path()))) {
                removed++;
            }
        }
        return Math.max(0, live + added - removed);
    }

    /** @return a player-facing error, or null when the draft was stored */
    public static String stageSet(UUID player, String moduleId, String path, String raw) {
        clearBanner(player);
        ChangePreview preview = describeSet(moduleId, path, raw);
        String error = preview.error();
        if (error == null) {
            error = chanceError(moduleId, path, raw);
        }
        if (error != null) {
            markInvalid(player, moduleId, path, error);
            return error;
        }
        clearInvalid(player, moduleId, path);
        Session session = session(player);
        session.drafts.removeIf(draft -> draft.op() == DraftOp.SET
                && moduleId.equals(draft.moduleId())
                && path.equals(draft.path()));
        if (!matchesLive(moduleId, path, raw)) {
            if (session.drafts.size() >= 100) {
                return "Save or discard before changing more settings.";
            }
            noteSeen(session, moduleId);
            session.drafts.add(new Draft(
                    moduleId, path, DraftOp.SET, raw == null ? "" : raw.trim(), "", currentText(moduleId, path)));
            touchRecent(player, moduleId, path);
        }
        return null;
    }

    /** @return a player-facing error, or null when the add was stored */
    public static String stageAdd(UUID player, String moduleId, String path, String key, String raw) {
        clearBanner(player);
        String error = validateAdd(moduleId, path, key, raw);
        if (error != null) {
            return error;
        }
        Kind kind = kindOf(moduleId, path);
        if (kind == Kind.MAP && key != null && !key.isBlank()) {
            String child = joinKey(path, key.trim());
            Located located = locate(moduleId, path);
            if (located != null && located.value instanceof Map<?, ?> map && map.containsKey(key.trim())) {
                return stageSet(player, moduleId, child, raw);
            }
        }
        Session session = session(player);
        String typedKey = key == null ? "" : key.trim();
        if (typedKey.isEmpty() && raw != null) {
            typedKey = raw.trim();
        }
        String trimmedKey = typedKey;
        session.drafts.removeIf(draft -> draft.op() == DraftOp.ADD
                && moduleId.equals(draft.moduleId())
                && path.equals(draft.path())
                && trimmedKey.equals(draft.addKey()));
        if (session.drafts.size() >= 100) {
            return "Save or discard before changing more settings.";
        }
        noteSeen(session, moduleId);
        session.drafts.add(new Draft(
                moduleId, path, DraftOp.ADD, raw == null ? "" : raw.trim(), trimmedKey, ""));
        touchRecent(player, moduleId, path);
        return null;
    }

    public static void stageRemove(UUID player, String moduleId, String path) {
        clearBanner(player);
        Session session = session(player);
        String parent = parentPath(path);
        String key = leafName(path);
        boolean droppedAdd = session.drafts.removeIf(draft -> draft.op() == DraftOp.ADD
                && moduleId.equals(draft.moduleId())
                && parent.equals(draft.path())
                && key.equals(draft.addKey()));
        if (droppedAdd) {
            return;
        }
        session.drafts.removeIf(draft -> draft.op() == DraftOp.SET
                && moduleId.equals(draft.moduleId())
                && path.equals(draft.path()));
        boolean already = session.drafts.removeIf(draft -> draft.op() == DraftOp.REMOVE
                && moduleId.equals(draft.moduleId())
                && path.equals(draft.path()));
        if (already) {
            return;
        }
        noteSeen(session, moduleId);
        session.drafts.add(new Draft(moduleId, path, DraftOp.REMOVE, "", key, currentText(moduleId, path)));
        touchRecent(player, moduleId, path);
    }

    /** Remember the file's save version the first time this editor stages a change to it. */
    private static void noteSeen(Session session, String moduleId) {
        Path file = fileOf(moduleId);
        if (file != null) {
            session.seen.putIfAbsent(file, fileVersion(file));
        }
    }

    private static long fileVersion(Path file) {
        FileStamp stamp = file == null ? null : FILE_STAMPS.get(file);
        return stamp == null ? 0L : stamp.version();
    }

    /** Who is editing: shown to other staff editors when this editor saves or has unsaved changes. */
    public static void noteActor(UUID player, String name) {
        if (name != null && !name.isBlank()) {
            session(player).actor = name.trim();
        }
    }

    /**
     * Files this editor has unsaved changes for that another staff member saved since this
     * editor started changing them. Each conflict names who saved, when, and which tabs.
     */
    public static List<EditConflict> conflicts(UUID player) {
        Session session = session(player);
        List<EditConflict> out = new ArrayList<>();
        for (Map.Entry<Path, Long> seen : session.seen.entrySet()) {
            FileStamp stamp = FILE_STAMPS.get(seen.getKey());
            if (stamp == null || stamp.version() <= seen.getValue() || Objects.equals(stamp.by(), player)) {
                continue;
            }
            if (session.drafts.stream().noneMatch(d -> seen.getKey().equals(fileOf(d.moduleId())))) {
                continue;
            }
            out.add(new EditConflict(stamp.actor(), CLOCK.format(stamp.when()), modulesOf(session, seen.getKey())));
        }
        return out;
    }

    /** Other staff with unsaved changes to a file this editor is also changing: "Name (Tab)". */
    public static List<String> otherEditors(UUID player) {
        Session mine = session(player);
        Set<Path> myFiles = new HashSet<>();
        for (Draft draft : mine.drafts) {
            Path file = fileOf(draft.moduleId());
            if (file != null) {
                myFiles.add(file);
            }
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            Session other = entry.getValue();
            if (entry.getKey().equals(player) || other.drafts.isEmpty()) {
                continue;
            }
            Set<String> shared = new java.util.TreeSet<>();
            for (Draft draft : other.drafts) {
                if (myFiles.contains(fileOf(draft.moduleId()))) {
                    shared.add(tabLabel(draft.moduleId()));
                }
            }
            if (!shared.isEmpty()) {
                String who = other.actor == null || other.actor.isBlank() ? "Another staff member" : other.actor;
                out.add(who + " (" + String.join(", ", shared) + ")");
            }
        }
        return out;
    }

    /**
     * Reload every file this editor has changes for, then put the same changes back on top of
     * what is on disk now. Changes that now match the file drop out; ones that no longer fit
     * stay marked for fixing. Returns a one-line summary.
     */
    public static String reloadAndReapply(UUID player) {
        Session session = session(player);
        List<Draft> mine = new ArrayList<>(session.drafts);
        if (mine.isEmpty()) {
            return "No unsaved changes to reapply.";
        }
        Set<String> reloaded = new java.util.LinkedHashSet<>();
        for (Draft draft : mine) {
            Path file = fileOf(draft.moduleId());
            if (file == null || reloaded.contains(file.toString())) {
                continue;
            }
            String error = reloadModule(player, draft.moduleId());
            if (error != null) {
                session.drafts.clear();
                session.drafts.addAll(mine);
                return "Could not reload the settings file, so your changes were kept as they were. Try again.";
            }
            reloaded.add(file.toString());
        }
        session.seen.clear();
        int failed = 0;
        for (Draft draft : mine) {
            String error = switch (draft.op()) {
                case ADD -> stageAdd(player, draft.moduleId(), draft.path(), draft.addKey(), draft.raw());
                case REMOVE -> {
                    // An entry someone else already removed simply drops out.
                    if (mutateWouldRemove(draft.moduleId(), draft.path()) == null) {
                        stageRemove(player, draft.moduleId(), draft.path());
                    }
                    yield null;
                }
                default -> stageSet(player, draft.moduleId(), draft.path(), draft.raw());
            };
            if (error != null) {
                failed++;
            }
        }
        int kept = session.drafts.size();
        int dropped = Math.max(0, mine.size() - kept - failed);
        StringBuilder msg = new StringBuilder("Reloaded the latest file and reapplied ")
                .append(kept).append(kept == 1 ? " change" : " changes").append('.');
        if (failed > 0) {
            msg.append(' ').append(failed).append(failed == 1 ? " needs" : " need")
                    .append(" fixing - see the red rows.");
        }
        if (dropped > 0) {
            msg.append(' ').append(dropped).append(" already matched the file and dropped out.");
        }
        return msg.toString();
    }

    /** Report lines from this editor's last save. Empty until something is saved. */
    public static List<ReportLine> lastSaveReport(UUID player) {
        return session(player).lastReport;
    }

    /** Full option list for an enum setting, in declared order. Empty for any other kind. */
    public static List<String> enumOptions(String moduleId, String path) {
        Located located = locate(moduleId, path);
        if (located == null || located.field == null) {
            return List.of();
        }
        Class<?> type = located.key == null
                ? located.field.getType()
                : rawClass(valueType(located.field, located.container instanceof List<?> ? 0 : 1));
        if (type == null || !type.isEnum() || type.getEnumConstants() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            names.add(constant.toString());
        }
        return names;
    }

    /** True when {@code path} is one entry of a list or map, so it can be removed. */
    public static boolean isCollectionEntry(String moduleId, String path) {
        if (path == null || path.isBlank() || !path.endsWith("]")) {
            return false;
        }
        Kind parent = kindOf(moduleId, parentPath(path));
        return parent == Kind.LIST || parent == Kind.MAP;
    }

    /** True when this entry is staged for removal. */
    public static boolean removing(UUID player, String moduleId, String path) {
        return findDraft(player, moduleId, path, DraftOp.REMOVE) != null;
    }

    private static String tabLabel(String moduleId) {
        EditorTab t = tab(moduleId);
        return t == null ? displayName(moduleId) : t.label();
    }

    /** Tab names this editor has changes in for one file, e.g. "Difficulty, Currency". */
    private static String modulesOf(Session session, Path file) {
        Set<String> labels = new java.util.TreeSet<>();
        for (Draft draft : session.drafts) {
            if (file.equals(fileOf(draft.moduleId()))) {
                labels.add(tabLabel(draft.moduleId()));
            }
        }
        return String.join(", ", labels);
    }

    public static void undo(UUID player, String moduleId, String path, DraftOp op, String addKey) {
        clearBanner(player);
        String key = addKey == null ? "" : addKey;
        session(player).drafts.removeIf(draft -> draft.op() == op
                && moduleId.equals(draft.moduleId())
                && path.equals(draft.path())
                && key.equals(draft.addKey() == null ? "" : draft.addKey()));
    }

    public static List<ManageRow> manageRows(UUID player, String moduleId, String path) {
        List<ManageRow> rows = new ArrayList<>();
        Set<String> removed = new HashSet<>();
        Set<String> addedKeys = new HashSet<>();
        for (Draft draft : session(player).drafts) {
            if (!moduleId.equals(draft.moduleId())) {
                continue;
            }
            if (draft.op() == DraftOp.REMOVE && path.equals(parentPath(draft.path()))) {
                removed.add(draft.path());
            }
        }
        for (Entry entry : children(moduleId, path)) {
            boolean removing = removed.contains(entry.path());
            String value = removing ? "(removing)" : shownValue(player, moduleId, entry.path());
            rows.add(new ManageRow(displayName(leafName(entry.path())), entry.path(), leafName(entry.path()),
                    false, removing, value));
        }
        for (Draft draft : session(player).drafts) {
            if (draft.op() != DraftOp.ADD || !moduleId.equals(draft.moduleId()) || !path.equals(draft.path())) {
                continue;
            }
            String key = draft.addKey() == null || draft.addKey().isBlank() ? draft.raw() : draft.addKey();
            if (!addedKeys.add(key)) {
                continue;
            }
            String child = joinKey(path, key);
            String title = displayName(key);
            rows.add(new ManageRow(title, child, key, true, false, draft.raw()));
        }
        return rows;
    }

    public static SaveResult saveAll(UUID player, String actor) {
        Session session = session(player);
        if (!session.invalid.isEmpty()) {
            String first = session.invalid.keySet().iterator().next();
            int tab = first.indexOf('\t');
            String firstName = staffLabel(tab < 0 ? first : first.substring(tab + 1));
            int n = session.invalid.size();
            return new SaveResult(
                    (n == 1 ? "1 field needs fixing" : n + " fields need fixing")
                            + " before saving (first: " + firstName + "). Fix the red rows or Undo them, then save again.",
                    0, null);
        }
        if (session.drafts.isEmpty()) {
            return new SaveResult(null, 0, "Nothing to save.");
        }
        List<Draft> drafts = new ArrayList<>(session.drafts);
        for (Draft draft : drafts) {
            String error = validateDraft(draft);
            if (error != null) {
                return new SaveResult(staffLabel(draft.path()) + " was refused: " + error
                        + " Change it or Undo it, then save again.", 0, null);
            }
        }
        // Another editor saved a new value for a setting this editor also changed: saving now
        // would silently overwrite theirs. Different settings in the same file are safe.
        for (Draft draft : drafts) {
            String by = changedBy(draft);
            if (!by.isBlank()) {
                return new SaveResult(by + " changed " + staffLabel(draft.path())
                        + " after you staged it. Use Reload and reapply on this page to build on"
                        + " their save, or Undo that setting.", 0, null);
            }
        }
        drafts.sort(Comparator.comparingInt(draft -> switch (draft.op()) {
            case SET -> 0;
            case REMOVE -> 1;
            case ADD -> 2;
        }));
        List<Change> changes = new ArrayList<>();
        LinkedHashMap<Path, Runnable> saves = new LinkedHashMap<>();
        LinkedHashMap<Path, Runnable> reloads = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            ConfigRegistry.Module module = ConfigRegistry.find(draft.moduleId());
            if (module == null || !module.editable()) {
                return new SaveResult("That file is not a settings file.", 0, null);
            }
            Path file = module.file() == null ? null : module.file().toAbsolutePath().normalize();
            if (file == null) {
                return new SaveResult("That file is not a settings file.", 0, null);
            }
            saves.putIfAbsent(file, module.save());
            reloads.putIfAbsent(file, module.reload());
            changes.add(new Change(draft, beforeText(draft), afterText(draft), file));
        }
        for (Draft draft : drafts) {
            String error = switch (draft.op()) {
                case ADD -> mutateAdd(draft.moduleId(), draft.path(), draft.addKey(), draft.raw());
                case REMOVE -> mutateRemove(draft.moduleId(), draft.path());
                default -> mutateSet(draft.moduleId(), draft.path(), draft.raw());
            };
            if (error != null) {
                restore(reloads.values());
                return new SaveResult(error, 0, null);
            }
        }
        String who = actor == null || actor.isBlank() ? "staff" : actor;
        Set<Path> saved = new HashSet<>();
        List<ReportLine> report = new ArrayList<>();
        Instant now = Instant.now();
        boolean backedUp = false;
        for (Map.Entry<Path, Runnable> entry : saves.entrySet()) {
            Path file = entry.getKey();
            try {
                backupOnce(file);
                backedUp |= backupEverySave(file, now);
                entry.getValue().run();
                saved.add(file);
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] config save failed for {}: {}", AdaptiveDifficultyMod.MOD_ID, file, t.toString());
                restore(reloads.values());
                for (Change change : changes) {
                    if (saved.contains(change.file())) {
                        writeAudit(who, change.draft().moduleId(), change.draft().path(), change.before(), change.after());
                    }
                }
                session.drafts.removeIf(draft -> saved.contains(fileOf(draft.moduleId())));
                String labels = labelsFor(changes, file);
                return new SaveResult(
                        "The " + labels + " settings could not be written to disk, so they were not changed."
                                + (saved.isEmpty() ? "" : " Settings saved before it stay saved.")
                                + " Try again; if it keeps failing, check the server log.",
                        0, null);
            }
        }
        // Each file reloads on its own, so one bad reload is named instead of hiding the rest.
        List<String> reloadedOk = new ArrayList<>();
        for (Map.Entry<Path, Runnable> entry : reloads.entrySet()) {
            String labels = labelsFor(changes, entry.getKey());
            try {
                if (entry.getValue() != null) {
                    entry.getValue().run();
                }
                reloadedOk.add(labels);
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] config reload failed for {}: {}",
                        AdaptiveDifficultyMod.MOD_ID, entry.getKey(), t.toString());
                report.add(new ReportLine(
                        labels + ": saved to disk, but reloading failed, so the server still runs the"
                                + " old values. Press Reload disk or restart to load them.", true));
            }
        }
        for (Change change : changes) {
            if (saved.contains(change.file())) {
                writeAudit(who, change.draft().moduleId(), change.draft().path(), change.before(), change.after());
            }
        }
        for (Path file : saved) {
            FILE_STAMPS.put(file, new FileStamp(SAVE_SEQ.incrementAndGet(), player, who, now));
        }
        int count = changes.size();
        String message = count == 1 ? "Saved 1 change." : "Saved " + count + " changes.";
        report.add(0, new ReportLine(message.substring(0, message.length() - 1) + " at " + CLOCK.format(now)
                + " by " + who + ".", false));
        if (!reloadedOk.isEmpty()) {
            report.add(1, new ReportLine("Reloaded and live now: " + String.join(", ", reloadedOk) + ".", false));
        }
        report.add(new ReportLine(backedUp
                ? "Backup taken before writing (the last " + BACKUPS_KEPT + " are kept per settings file)."
                : "No backup was needed - the settings file did not exist yet.", false));
        session.drafts.clear();
        session.invalid.clear();
        session.recent.clear();
        session.seen.clear();
        session.lastReport = List.copyOf(report);
        return new SaveResult(null, count, message, session.lastReport);
    }

    /** Tab names for the changes going into one file, e.g. "Difficulty and Currency". */
    private static String labelsFor(List<Change> changes, Path file) {
        Set<String> labels = new java.util.TreeSet<>();
        for (Change change : changes) {
            if (file.equals(change.file())) {
                labels.add(tabLabel(change.draft().moduleId()));
            }
        }
        if (labels.isEmpty()) {
            return "settings";
        }
        List<String> list = new ArrayList<>(labels);
        return list.size() == 1
                ? list.get(0)
                : String.join(", ", list.subList(0, list.size() - 1)) + " and " + list.get(list.size() - 1);
    }

    /**
     * Who saved a new value for this setting after it was staged, or blank. Only SET and REMOVE
     * carry a base; an add never overwrites an existing value.
     */
    private static String changedBy(Draft draft) {
        if (draft.op() == DraftOp.ADD || draft.base() == null) {
            return "";
        }
        String live = currentText(draft.moduleId(), draft.path());
        if (Objects.equals(live, draft.base())) {
            return "";
        }
        FileStamp stamp = FILE_STAMPS.get(fileOf(draft.moduleId()));
        return stamp == null ? "Someone" : stamp.actor();
    }

    /** Reloads one settings file and drops unsaved edits for every tab that shares it. */
    public static String reloadModule(UUID player, String moduleId) {
        ConfigRegistry.Module module = ConfigRegistry.find(moduleId);
        if (module == null || !module.editable()) {
            return "Player records stay in their own file.";
        }
        try {
            if (module.reload() != null) {
                module.reload().run();
            }
        } catch (Throwable t) {
            return "The file could not be reloaded.";
        }
        Path file = module.file() == null ? null : module.file().toAbsolutePath().normalize();
        session(player).drafts.removeIf(draft -> sharesFile(draft.moduleId(), file));
        session(player).invalid.keySet().removeIf(key -> sharesFile(moduleOfKey(key), file));
        session(player).recent.removeIf(key -> sharesFile(moduleOfKey(key), file));
        if (file != null) {
            session(player).seen.remove(file);
        }
        return null;
    }

    public static int discardAll(UUID player) {
        Session session = session(player);
        int count = session.drafts.size();
        session.drafts.clear();
        session.invalid.clear();
        session.recent.clear();
        session.seen.clear();
        return count;
    }

    public static void toggleGroup(UUID player, String moduleId, String group) {
        String key = moduleId + "\t" + (group == null ? "" : group);
        Set<String> collapsed = session(player).collapsed;
        if (!collapsed.remove(key)) {
            collapsed.add(key);
        }
    }

    public static boolean groupCollapsed(UUID player, String moduleId, String group) {
        return session(player).collapsed.contains(moduleId + "\t" + (group == null ? "" : group));
    }

    public static String invalidMessage(UUID player, String moduleId, String path) {
        return session(player).invalid.get(moduleId + "\t" + path);
    }

    public static int invalidCount(UUID player) {
        return session(player).invalid.size();
    }

    /** Drops one staged value and its error. Other fields stay. */
    public static void undoField(UUID player, String moduleId, String path) {
        clearBanner(player);
        session(player).drafts.removeIf(draft -> draft.op() == DraftOp.SET
                && moduleId.equals(draft.moduleId())
                && path.equals(draft.path()));
        session(player).invalid.remove(moduleId + "\t" + path);
    }

    /** Paths changed this session for one module, newest first. */
    public static List<String> recentPaths(UUID player, String moduleId) {
        List<String> out = new ArrayList<>();
        for (String key : session(player).recent) {
            int tab = key.indexOf('\t');
            if (tab <= 0 || !moduleId.equals(key.substring(0, tab))) {
                continue;
            }
            out.add(key.substring(tab + 1));
        }
        return out;
    }

    /** Unsaved edits, newest first. Nothing is written until saveAll. */
    public static List<PendingChange> pendingChanges(UUID player) {
        List<Draft> drafts = session(player).drafts;
        List<PendingChange> out = new ArrayList<>();
        for (int i = drafts.size() - 1; i >= 0; i--) {
            Draft draft = drafts.get(i);
            EditorTab moduleTab = tab(draft.moduleId());
            String moduleLabel = moduleTab == null ? draft.moduleId() : moduleTab.label();
            String section = sectionTitle(draft.moduleId(), draft.path());
            String label = staffLabel(draft.path());
            String name = section.isBlank() || section.equalsIgnoreCase(label)
                    ? moduleLabel + " > " + label
                    : moduleLabel + " > " + section + " > " + label;
            out.add(new PendingChange(name, beforeText(draft), afterText(draft), changedBy(draft),
                    draft.moduleId(), draft.path()));
        }
        return out;
    }

    public static String typeName(String moduleId, String path) {
        return switch (kindOf(moduleId, path)) {
            case BOOLEAN -> "boolean";
            case NUMBER -> "number";
            case TEXT -> "string";
            case ENUM -> "enum";
            case LIST -> "list";
            case MAP -> "map";
            case GROUP -> "group";
            default -> "value";
        };
    }

    /** One plain-English line for what this setting does. The current value is shown separately. */
    public static String description(String moduleId, String path) {
        return friendlyDescription(moduleId, path, currentText(moduleId, path));
    }

    public static String friendlyDescription(String moduleId, String path, String current) {
        return ConfigFieldHelp.describe(moduleId, path);
    }

    /** Default value, formatted the same way as {@link #shownValue}. */
    public static String defaultShown(String moduleId, String path) {
        Kind kind = kindOf(moduleId, path);
        if (kind == Kind.BOOLEAN) {
            return truth(defaultText(moduleId, path)) ? "ON" : "OFF";
        }
        if (kind == Kind.GROUP) {
            return "";
        }
        return defaultText(moduleId, path);
    }

    public static void rememberEditorPage(UUID player, String page) {
        session(player).editorPage = page == null ? "" : page;
    }

    public static String editorPage(UUID player) {
        String page = session(player).editorPage;
        return page == null || page.isBlank() ? "edit:difficulty:0:" : page;
    }

    public static String defaultText(String moduleId, String path) {
        Object fresh = freshInstance(moduleId);
        if (fresh == null) {
            return "empty";
        }
        Object value = readValue(fresh, path);
        if (value instanceof Boolean on) {
            return on ? "true" : "false";
        }
        if (value instanceof Map<?, ?> map) {
            return map.size() == 1 ? "1 entry" : map.size() + " entries";
        }
        if (value instanceof Collection<?> collection) {
            return collection.size() == 1 ? "1 entry" : collection.size() + " entries";
        }
        if (value != null && isGroup(value.getClass())) {
            return "group";
        }
        if (value == null) {
            return "empty";
        }
        // Full text. Config menus wrap it; an ellipsis here cut the default mid-word.
        return String.valueOf(value).replace('\n', ' ');
    }

    public static LastChange lastChange(String moduleId, String path) {
        if (moduleId == null || path == null || path.isEmpty()) {
            return null;
        }
        Path log = ConfigPaths.dataDir().resolve("config-audit.log");
        if (!Files.isRegularFile(log)) {
            return null;
        }
        String marker = " " + moduleId + " " + path + " ";
        String hit = null;
        try {
            List<String> lines = Files.readAllLines(log);
            int start = Math.max(0, lines.size() - 400);
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line != null && line.contains(marker)) {
                    hit = line;
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (hit == null) {
            return null;
        }
        int at = hit.indexOf(marker);
        if (at <= 0) {
            return null;
        }
        String head = hit.substring(0, at);
        int space = head.indexOf(' ');
        if (space <= 0) {
            return null;
        }
        String actor = head.substring(space + 1).trim();
        if (actor.isEmpty()) {
            actor = "staff";
        }
        String when = head.substring(0, space);
        try {
            when = AUDIT_WHEN.format(Instant.parse(when));
        } catch (DateTimeParseException ignored) {
            // Keep the raw timestamp when it is not an instant.
        }
        return new LastChange(actor, when);
    }

    public static List<String> enumNames(String moduleId, String path) {
        Located located = locate(moduleId, path);
        if (located == null || located.field == null) {
            return List.of();
        }
        Class<?> type = located.key == null
                ? located.field.getType()
                : rawClass(valueType(located.field, located.container instanceof List<?> ? 0 : 1));
        if (type == null || !type.isEnum()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Object[] constants = type.getEnumConstants();
        if (constants == null) {
            return names;
        }
        for (Object constant : constants) {
            if (names.size() >= 8) {
                names.add("...");
                break;
            }
            names.add(constant.toString());
        }
        return names;
    }

    private static void addTab(List<EditorTab> tabs, String id, String label) {
        ConfigRegistry.Module module = ConfigRegistry.find(id);
        if (module == null) {
            return;
        }
        String fileName = module.file() == null ? "" : module.file().getFileName().toString();
        tabs.add(new EditorTab(id, label, module.editable(), fileName));
    }

    private static void walkFields(String moduleId, String path, List<Entry> out, int depth) {
        if (depth > 5) {
            return;
        }
        for (Entry entry : children(moduleId, path)) {
            out.add(entry);
            if (entry.kind() == Kind.GROUP) {
                walkFields(moduleId, entry.path(), out, depth + 1);
            }
        }
    }

    private static Draft findDraft(UUID player, String moduleId, String path, DraftOp op) {
        for (Draft draft : session(player).drafts) {
            if (draft.op() == op && moduleId.equals(draft.moduleId()) && path.equals(draft.path())) {
                return draft;
            }
        }
        return null;
    }

    private static boolean truth(String raw) {
        return "true".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw) || "ON".equals(raw);
    }

    private static boolean matchesLive(String moduleId, String path, String raw) {
        Located located = locate(moduleId, path);
        if (located == null) {
            return false;
        }
        try {
            Object next = coerceLocated(located, raw);
            return Objects.equals(located.value, next);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String validateDraft(Draft draft) {
        return switch (draft.op()) {
            case ADD -> validateAdd(draft.moduleId(), draft.path(), draft.addKey(), draft.raw());
            case REMOVE -> mutateWouldRemove(draft.moduleId(), draft.path());
            default -> {
                String error = describeSet(draft.moduleId(), draft.path(), draft.raw()).error();
                if (error != null) {
                    yield error;
                }
                yield chanceError(draft.moduleId(), draft.path(), draft.raw());
            }
        };
    }

    private static String validateAdd(String rootId, String path, String key, String raw) {
        Located located = locate(rootId, path);
        if (located == null || located.field == null || located.owner == null) {
            return "That list is not on this config.";
        }
        try {
            if (located.value instanceof Map<?, ?> || kindForType(rawClass(located.field.getGenericType())) == Kind.MAP) {
                if (key == null || key.isBlank()) {
                    return "Type a name for the new entry.";
                }
                coerce(valueType(located.field, 1), raw);
            } else if (located.value instanceof List<?>
                    || kindForType(rawClass(located.field.getGenericType())) == Kind.LIST) {
                coerce(valueType(located.field, 0), raw);
            } else {
                return "Entries can't be added here.";
            }
        } catch (NumberFormatException e) {
            return "That needs to be a number.";
        } catch (IllegalArgumentException e) {
            return friendlyEditError(e.getMessage());
        }
        return null;
    }

    /** Checks a remove without changing memory. */
    private static String mutateWouldRemove(String rootId, String path) {
        Located located = locate(rootId, path);
        if (located == null || located.key == null) {
            return "Select an entry to remove.";
        }
        if (located.container instanceof Map<?, ?> map) {
            return map.containsKey(located.key) ? null : "That entry is already gone.";
        }
        if (located.container instanceof List<?> list) {
            return findListValue(list, located.key) != null ? null : "That entry is already gone.";
        }
        return "That entry can't be removed here.";
    }

    private static final List<String> SECTION_ORDER = List.of(
            "Master switch",
            "Access",
            "Player Scaling",
            "Mob Spawning",
            "Boss Behavior",
            "Elites and Mutations",
            "Unlock Tiers",
            "Ability Tiers",
            "The End",
            "Rivals",
            "Sparring",
            "Meditation",
            "Combat Systems",
            "Prestige and Skills",
            "Tips",
            "Rewards",
            "Ancient Coins",
            "Class Counters",
            "Combat Rating",
            "Mob AI",
            "Telemetry",
            "Saga Costs",
            "Server",
            "One-time Migrations",
            "Other");

    public static boolean isSectionPath(String path) {
        return path != null && path.startsWith(SECTION_PREFIX);
    }

    /** Breadcrumb section for one field, such as {@code The End} or {@code Race Change}. */
    public static String sectionTitle(String moduleId, String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        if (isSectionPath(path)) {
            return path.substring(SECTION_PREFIX.length());
        }
        String parent = parentPath(path);
        if (!parent.isEmpty()) {
            return staffLabel(parent);
        }
        return sectionForLeaf(moduleId, leafName(path));
    }

    /** Plain label. {@code enableEndDimensionStrength} becomes {@code End Dimension Strength}. */
    public static String staffLabel(String path) {
        return ConfigFieldHelp.label(path);
    }

    /**
     * Sections on the module root. Nested configs (character, combat) use each group as a
     * section. Flat configs (difficulty) bucket fields by what they control.
     */
    public static List<SectionLink> sections(String moduleId) {
        List<Entry> kids = children(moduleId, "");
        if (kids.isEmpty()) {
            return List.of();
        }
        int containers = 0;
        for (Entry entry : kids) {
            if (entry.kind() == Kind.GROUP || entry.kind() == Kind.MAP) {
                containers++;
            }
        }
        boolean nested = containers >= 2 && containers * 2 >= kids.size();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> open = new LinkedHashMap<>();
        if (nested) {
            for (Entry entry : kids) {
                String title = staffLabel(entry.path());
                counts.computeIfAbsent(title, key -> new int[1])[0]++;
                open.putIfAbsent(title, entry.path());
            }
        } else {
            for (Entry entry : kids) {
                String title = sectionForLeaf(moduleId, leafName(entry.path()));
                counts.computeIfAbsent(title, key -> new int[1])[0]++;
                open.putIfAbsent(title, SECTION_PREFIX + title);
            }
        }
        List<String> order = new ArrayList<>();
        if (nested) {
            order.addAll(open.keySet());
        } else {
            for (String fixed : SECTION_ORDER) {
                if (counts.containsKey(fixed)) {
                    order.add(fixed);
                }
            }
            for (String key : counts.keySet()) {
                if (!order.contains(key)) {
                    order.add(key);
                }
            }
        }
        List<SectionLink> out = new ArrayList<>();
        for (String title : order) {
            out.add(new SectionLink(title, open.get(title), counts.get(title)[0]));
        }
        return out;
    }

    /** Root fields that belong in one named section. */
    public static List<Entry> fieldsInSection(String moduleId, String section) {
        List<Entry> out = new ArrayList<>();
        if (section == null || section.isBlank()) {
            return out;
        }
        for (Entry entry : children(moduleId, "")) {
            if (section.equals(sectionForLeaf(moduleId, leafName(entry.path())))) {
                out.add(entry);
            }
        }
        return out;
    }

    static String sectionForLeaf(String moduleId, String leaf) {
        String name = leaf == null ? "" : leaf;
        String lower = name.toLowerCase(Locale.ROOT);
        if ("saga".equals(moduleId)) {
            return "enabled".equals(lower) ? "Master switch" : "Saga Costs";
        }
        if (lower.contains("migrated")) {
            return "One-time Migrations";
        }
        if (lower.contains("whitelist")) {
            return "Access";
        }
        if (lower.contains("telemetry")) {
            return "Telemetry";
        }
        if (lower.contains("ancientcoin") || lower.contains("stafffree")) {
            return "Ancient Coins";
        }
        if (lower.startsWith("unlocktier") || lower.startsWith("tiercost")) {
            return "Unlock Tiers";
        }
        if (lower.startsWith("tier")) {
            return "Ability Tiers";
        }
        if (lower.contains("boss")) {
            return "Boss Behavior";
        }
        if (lower.contains("elite") || lower.contains("mutation") || lower.contains("rarity")) {
            return "Elites and Mutations";
        }
        if (lower.contains("end") || lower.contains("dragon")) {
            return "The End";
        }
        if (lower.contains("rival")) {
            return "Rivals";
        }
        if (lower.contains("spar")) {
            return "Sparring";
        }
        if (lower.contains("meditation")) {
            return "Meditation";
        }
        if (lower.contains("lmtip")) {
            return "Tips";
        }
        if (lower.contains("skillcheck")
                || lower.contains("skillunlock")
                || lower.contains("playerstat")
                || "enableprestigesystem".equals(lower)
                || lower.contains("overhaul")) {
            return "Prestige and Skills";
        }
        if (lower.contains("potential")
                || "enableprogression".equals(lower)
                || lower.contains("globaltp")
                || lower.contains("bioandroid")
                || lower.contains("racelock")
                || lower.contains("yardrat")
                || lower.contains("spiritualist")
                || lower.contains("androidconversion")
                || lower.contains("kiweapon")
                || lower.contains("piercing")
                || lower.contains("dotextra")
                || lower.contains("apothic")
                || lower.contains("shadowdummy")) {
            return "Combat Systems";
        }
        if (lower.contains("counter")
                || lower.contains("specialization")
                || lower.contains("defenset")
                || lower.contains("weakstat")
                || lower.contains("tankdamage")
                || lower.contains("strongstat")) {
            return "Class Counters";
        }
        if (lower.contains("combatrating")) {
            return "Combat Rating";
        }
        if (lower.contains("adaptive") || lower.contains("evolution")) {
            return "Mob AI";
        }
        if (lower.contains("nearbyscale")
                || "maxscaledmobsperplayer".equals(lower)
                || "mobscaleradius".equals(lower)) {
            return "Mob Spawning";
        }
        if (lower.contains("reward") || lower.contains("coindrop")) {
            return "Rewards";
        }
        if (lower.contains("scaling")
                || lower.contains("multiplier")
                || lower.contains("curve")
                || lower.contains("health")
                || lower.contains("damage")
                || lower.contains("defense")
                || lower.contains("mobscale")
                || lower.contains("area")
                || lower.contains("hardcap")
                || lower.contains("referencemax")
                || lower.contains("maxmove")
                || lower.contains("maxarmor")
                || lower.contains("maxdamage")
                || lower.contains("maxform")
                || lower.contains("maxlive")
                || lower.contains("transformscale")
                || lower.contains("movement")
                || lower.contains("dmz")
                || lower.contains("contribution")
                || lower.contains("teambonus")
                || lower.contains("levelmultiplier")
                || "scalehostileonly".equals(lower)
                || lower.contains("deathreset")
                || lower.contains("team")) {
            return "Player Scaling";
        }
        if ("enabled".equals(lower)
                || lower.contains("admin")
                || lower.contains("gui")
                || lower.contains("vanilla")
                || lower.contains("dimension")
                || lower.contains("cnpc")) {
            return "Server";
        }
        return "Other";
    }

    private static String groupName(String path) {
        return sectionForLeaf("", leafName(path));
    }

    /**
     * A "percent" number is a 0-100 rate; the editor rejects anything outside it. The config
     * carries no per-field range annotations, so the range is derived from the field name: a
     * {@code *Percent} field such as {@code eliteChancePercent} (default 2.25) is a percentage,
     * not a 0-1 fraction. Without this split, a chance check matched every field containing
     * "chance" and rejected the percent fields' own defaults as out of range.
     */
    private static boolean isPercentField(String moduleId, String path) {
        return path != null
                && leafName(path).toLowerCase(Locale.ROOT).contains("percent")
                && kindOf(moduleId, path) == Kind.NUMBER;
    }

    /**
     * A "chance" number is a 0-1 fraction; the editor rejects anything outside it. A
     * {@code *ChancePercent} field is a percent (see {@link #isPercentField}), not a fraction,
     * so it is excluded here - only a plain chance such as {@code ancientCoinDropChance} (0.05)
     * is a 0-1 fraction.
     */
    private static boolean isChanceField(String moduleId, String path) {
        return path != null
                && leafName(path).toLowerCase(Locale.ROOT).contains("chance")
                && !isPercentField(moduleId, path)
                && kindOf(moduleId, path) == Kind.NUMBER;
    }

    /** Friendly inline line for a number outside its derived range, or null when it fits. */
    private static String chanceError(String moduleId, String path, String raw) {
        boolean percent = isPercentField(moduleId, path);
        if (!percent && !isChanceField(moduleId, path)) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(raw == null ? "" : raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (percent) {
            if (value < 0 || value > 100) {
                return "That percent runs from 0 to 100.";
            }
        } else if (value < 0 || value > 1) {
            return "That chance runs from 0 to 1.";
        }
        return null;
    }

    /**
     * What a field accepts, in plain words: its options when it is a choice, a real range when one
     * can be derived, otherwise the field type. The config carries no per-field range annotations,
     * so ranges are derived from the field name: a {@code *Percent} field is a 0-100 percentage,
     * a plain chance field is a 0-1 fraction, and every other number falls back to its type.
     */
    public static String acceptedValues(String moduleId, String path) {
        return switch (kindOf(moduleId, path)) {
            case BOOLEAN -> "true / false";
            case ENUM -> {
                List<String> options = enumNames(moduleId, path);
                yield options.isEmpty()
                        ? "one of the listed options"
                        : "one of: " + String.join(" / ", options);
            }
            case NUMBER -> {
                if (isPercentField(moduleId, path)) {
                    yield "a number from 0 to 100";
                }
                if (isChanceField(moduleId, path)) {
                    yield "a number from 0 to 1";
                }
                yield "any number";
            }
            case TEXT -> "any text";
            case LIST -> "a list of values";
            case MAP -> "a name and a value";
            case GROUP -> "";
            default -> "any value";
        };
    }

    /**
     * Friendly inline line for a rejected entry: what went wrong and what to enter instead.
     * Raw parser or reflection text never reaches the screen.
     */
    public static String invalidHint(String moduleId, String path, String reason) {
        String what = reasonPhrase(reason);
        Kind kind = kindOf(moduleId, path);
        if (kind == Kind.NUMBER && isPercentField(moduleId, path)) {
            return what + " Enter a percent, from 0 to 100.";
        }
        if (kind == Kind.NUMBER && isChanceField(moduleId, path)) {
            return what + " A chance is a number from 0 to 1.";
        }
        if (kind == Kind.NUMBER) {
            return what + " Enter a number, such as 5.";
        }
        if (kind == Kind.BOOLEAN) {
            return what + " Enter true or false.";
        }
        String accepts = acceptedValues(moduleId, path);
        return accepts.isBlank() ? what + "." : what + " Enter " + accepts + ".";
    }

    /** What went wrong, in plain words. Never echoes a raw parser or reflection message. */
    private static String reasonPhrase(String reason) {
        String lower = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (lower.contains("permission")) {
            return "That one stays in the config file";
        }
        if (lower.contains("between") || lower.contains("from 0 to 1") || lower.contains("range")) {
            return "That is outside the allowed range";
        }
        if (lower.contains("number")) {
            return "That is not a number";
        }
        if (lower.contains("option") || lower.contains("one of")) {
            return "That is not one of the options";
        }
        if (lower.contains("gone") || lower.contains("no longer")) {
            return "That entry is no longer there";
        }
        return "That did not take";
    }

    /**
     * Staff-facing text for a failed edit. Curated sentences pass through; anything that reads
     * like parser or reflection output (class names, "For input string", ...) is replaced, so a
     * raw error never reaches the screen.
     */
    private static String friendlyEditError(String message) {
        if (message == null || message.isBlank()) {
            return "That value didn't work.";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("class ")
                || lower.contains("exception")
                || lower.contains("java.")
                || lower.contains("for input string")
                || lower.contains("can not")
                || lower.contains("cannot")
                || lower.contains(" field ")
                || lower.contains("null")) {
            return "That value didn't work.";
        }
        return message;
    }

    private static void markInvalid(UUID player, String moduleId, String path, String message) {
        String text = message == null || message.isBlank() ? "That value didn't work." : message;
        session(player).invalid.put(moduleId + "\t" + path, text);
    }

    private static void clearInvalid(UUID player, String moduleId, String path) {
        session(player).invalid.remove(moduleId + "\t" + path);
    }

    private static void touchRecent(UUID player, String moduleId, String path) {
        String key = moduleId + "\t" + path;
        List<String> recent = session(player).recent;
        recent.remove(key);
        recent.add(0, key);
        while (recent.size() > 12) {
            recent.remove(recent.size() - 1);
        }
    }

    private static boolean sharesFile(String moduleId, Path file) {
        if (moduleId == null || file == null) {
            return false;
        }
        ConfigRegistry.Module other = ConfigRegistry.find(moduleId);
        if (other == null || other.file() == null) {
            return false;
        }
        return file.equals(other.file().toAbsolutePath().normalize());
    }

    private static String moduleOfKey(String key) {
        if (key == null) {
            return "";
        }
        int tab = key.indexOf('\t');
        return tab < 0 ? key : key.substring(0, tab);
    }

    private static String beforeText(Draft draft) {
        if (draft.op() == DraftOp.ADD) {
            return "(added)";
        }
        Located located = locate(draft.moduleId(), draft.path());
        if (located == null || located.value == null) {
            return "empty";
        }
        return String.valueOf(located.value);
    }

    private static String afterText(Draft draft) {
        if (draft.op() == DraftOp.REMOVE) {
            return "(removed)";
        }
        if (draft.op() == DraftOp.ADD) {
            return draft.addKey() == null || draft.addKey().isBlank()
                    ? draft.raw()
                    : draft.addKey() + "=" + draft.raw();
        }
        return draft.raw() == null ? "" : draft.raw();
    }

    private static Path fileOf(String moduleId) {
        ConfigRegistry.Module module = ConfigRegistry.find(moduleId);
        if (module == null || module.file() == null) {
            return null;
        }
        return module.file().toAbsolutePath().normalize();
    }

    private static void restore(Collection<Runnable> reloads) {
        for (Runnable reload : reloads) {
            if (reload == null) {
                continue;
            }
            try {
                reload.run();
            } catch (Throwable ignored) {
                // The live file is the recovery copy.
            }
        }
    }

    private static String mutateSet(String rootId, String path, String raw) {
        if (root(rootId) == null) {
            return "That config is not editable.";
        }
        Located located = locate(rootId, path);
        if (located == null || located.owner == null) {
            return "That setting is not on this config.";
        }
        if (isBlocked(located.field)) {
            return "The admin permission stays in the config file.";
        }
        try {
            if (located.key != null) {
                int typeIndex = located.container instanceof List<?> ? 0 : 1;
                Object coerced = coerce(valueType(located.field, typeIndex), raw);
                if (located.container instanceof Map<?, ?>) {
                    mutableMap(located.field, located.owner).put(located.key, coerced);
                } else if (located.container instanceof List<?>) {
                    List<Object> list = mutableList(located.field, located.owner);
                    if (!replaceListValue(list, located.field, located.key, coerced)) {
                        return "That entry is no longer in the list.";
                    }
                } else {
                    return "That entry can't be edited here.";
                }
            } else if (located.field != null) {
                if ("*".equals(raw == null ? "" : raw.trim())
                        && located.field.getName().toLowerCase(Locale.ROOT).contains("permission")) {
                    return "A permission node can't be *.";
                }
                located.field.set(located.owner, coerce(located.field.getGenericType(), raw));
            } else {
                return "That setting can't be edited here.";
            }
        } catch (NumberFormatException e) {
            return "That needs to be a number.";
        } catch (IllegalArgumentException e) {
            return friendlyEditError(e.getMessage());
        } catch (ReflectiveOperationException e) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] config edit failed {}.{}: {}",
                    AdaptiveDifficultyMod.MOD_ID, rootId, path, e.toString());
            return "That setting couldn't be changed.";
        }
        return null;
    }

    private static String mutateAdd(String rootId, String path, String key, String raw) {
        if (root(rootId) == null) {
            return "That config is not editable.";
        }
        Located located = locate(rootId, path);
        if (located == null || located.field == null || located.owner == null) {
            return "That list is not on this config.";
        }
        try {
            if (located.value instanceof Map<?, ?> || kindForType(rawClass(located.field.getGenericType())) == Kind.MAP) {
                if (key == null || key.isBlank()) {
                    return "Type a name for the new entry.";
                }
                Map<String, Object> map = mutableMap(located.field, located.owner);
                map.put(key.trim(), coerce(valueType(located.field, 1), raw));
            } else if (located.value instanceof List<?>
                    || kindForType(rawClass(located.field.getGenericType())) == Kind.LIST) {
                List<Object> list = mutableList(located.field, located.owner);
                list.add(coerce(valueType(located.field, 0), raw));
            } else {
                return "Entries can't be added here.";
            }
        } catch (NumberFormatException e) {
            return "That needs to be a number.";
        } catch (IllegalArgumentException e) {
            return friendlyEditError(e.getMessage());
        } catch (ReflectiveOperationException e) {
            return "That entry couldn't be added.";
        }
        return null;
    }

    private static String mutateRemove(String rootId, String path) {
        if (root(rootId) == null) {
            return "That config is not editable.";
        }
        Located located = locate(rootId, path);
        if (located == null || located.key == null || located.field == null || located.owner == null) {
            return "Select an entry to remove.";
        }
        try {
            if (located.container instanceof Map<?, ?>) {
                Map<String, Object> map = mutableMap(located.field, located.owner);
                if (!map.containsKey(located.key)) {
                    return "That entry is already gone.";
                }
                map.remove(located.key);
            } else if (located.container instanceof List<?>) {
                List<Object> list = mutableList(located.field, located.owner);
                if (!removeListValue(list, located.field, located.key)) {
                    return "That entry is already gone.";
                }
            } else {
                return "That entry can't be removed here.";
            }
        } catch (ReflectiveOperationException e) {
            return "That entry couldn't be removed.";
        }
        return null;
    }

    private static Object coerceLocated(Located located, String raw) {
        if (located.key != null) {
            int typeIndex = located.container instanceof List<?> ? 0 : 1;
            return coerce(valueType(located.field, typeIndex), raw);
        }
        if (located.field != null) {
            if ("*".equals(raw == null ? "" : raw.trim())
                    && located.field.getName().toLowerCase(Locale.ROOT).contains("permission")) {
                throw new IllegalArgumentException("A permission node can't be *.");
            }
            return coerce(located.field.getGenericType(), raw);
        }
        throw new IllegalArgumentException("That setting can't be edited here.");
    }

    private static Class<?> numberType(Located located) {
        if (located.field != null && located.key == null && isNumber(located.field.getType())) {
            return located.field.getType();
        }
        if (located.field != null && located.key != null) {
            Class<?> type = rawClass(valueType(located.field, located.container instanceof List<?> ? 0 : 1));
            if (isNumber(type)) {
                return type;
            }
        }
        if (located.value instanceof Number number) {
            return number.getClass();
        }
        return null;
    }

    private static Object freshInstance(String moduleId) {
        Object shopDefault = PrestigeShopHooks.freshInstance(moduleId);
        if (shopDefault != null) {
            return shopDefault;
        }
        return switch (moduleId) {
            case "difficulty", "progression", "currency" -> DifficultyConfig.freshDefaults();
            case "character-services" -> CharacterServicesConfig.freshDefaults();
            case "saga" -> new SagaResetConfig();
            case "hot-path" -> HotPathConfig.freshDefaults();
            default -> null;
        };
    }

    private static Object readValue(Object current, String path) {
        if (current == null || path == null || path.isEmpty()) {
            return current;
        }
        int i = 0;
        while (i < path.length() && current != null) {
            if (path.charAt(i) == '.') {
                i++;
            }
            if (i >= path.length()) {
                break;
            }
            if (path.charAt(i) == '[') {
                StringBuilder parsed = new StringBuilder();
                i++;
                while (i < path.length()) {
                    char c = path.charAt(i);
                    if (c == '\\' && i + 1 < path.length()) {
                        parsed.append(path.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == ']') {
                        i++;
                        break;
                    }
                    parsed.append(c);
                    i++;
                }
                String key = parsed.toString();
                if (current instanceof Map<?, ?> map) {
                    current = map.get(key);
                } else if (current instanceof List<?> list) {
                    current = findListValue(list, key);
                } else {
                    return null;
                }
                continue;
            }
            int start = i;
            while (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[') {
                i++;
            }
            String name = path.substring(start, i);
            Field field = findField(current.getClass(), name);
            if (field == null) {
                return null;
            }
            try {
                current = field.get(current);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return current;
    }

    private static Session session(UUID player) {
        UUID key = player == null ? new UUID(0L, 0L) : player;
        return SESSIONS.computeIfAbsent(key, ignored -> new Session());
    }

    private static final class Session {
        private String search = "";
        private String editorPage = "";
        private String banner = "";
        private BannerTone tone = BannerTone.NONE;
        private final List<Draft> drafts = new ArrayList<>();
        private final Set<String> collapsed = new HashSet<>();
        private final Map<String, String> invalid = new LinkedHashMap<>();
        private final List<String> recent = new ArrayList<>();
        /** Display name of the staff member editing, for other editors' warnings. */
        private String actor = "";
        /** Save version of each file when this editor first staged a change to it. */
        private final Map<Path, Long> seen = new LinkedHashMap<>();
        /** Report from this editor's last save, shown on Review. */
        private List<ReportLine> lastReport = List.of();
    }

    private record Change(Draft draft, String before, String after, Path file) {}

    private static String persist(Root root, String actor, String path, String before, String after) {
        ConfigRegistry.Module module = ConfigRegistry.find(root.id());
        try {
            if (module != null) {
                backupOnce(module.file());
                backupEverySave(module.file(), Instant.now());
            }
            root.persist().run();
            if (module != null && module.reload() != null) {
                module.reload().run();
            }
            writeAudit(actor, root.id(), path, before, after);
            return null;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] config save failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID, root.id(), t.toString());
            return "The file could not be saved.";
        }
    }

    /** Copy the json once per server session before the first write. */
    private static void backupOnce(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        Path normal = file.toAbsolutePath().normalize();
        if (BACKED_THIS_SESSION.contains(normal)) {
            return;
        }
        Path bak = normal.resolveSibling(normal.getFileName().toString() + ".bak");
        Files.copy(normal, bak, StandardCopyOption.REPLACE_EXISTING);
        BACKED_THIS_SESSION.add(normal);
    }

    /**
     * Copy the json before every save into a {@code backups} folder beside it, stamped with the
     * save time, and keep only the newest {@link #BACKUPS_KEPT} copies of that file.
     *
     * @return true when a copy was written (false when the file does not exist yet)
     */
    static boolean backupEverySave(Path file, Instant when) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        Path normal = file.toAbsolutePath().normalize();
        Path dir = normal.resolveSibling("backups");
        Files.createDirectories(dir);
        String name = normal.getFileName().toString();
        String stamp = name + "." + BACKUP_STAMP.format(when);
        // Two saves in the same millisecond must not overwrite each other's copy. "~" sorts
        // after ".", so "stamp~01.bak" stays newer than "stamp.bak" for the pruning sort below.
        // The counter goes one past the highest already used for this stamp - never into a gap
        // left by pruning - so a newer copy can never sort as older.
        int next = -1;
        try (var same = Files.list(dir)) {
            for (Path p : (Iterable<Path>) same::iterator) {
                String n = p.getFileName().toString();
                if (n.equals(stamp + ".bak")) {
                    next = Math.max(next, 0);
                } else if (n.startsWith(stamp + "~") && n.endsWith(".bak")) {
                    try {
                        next = Math.max(next, Integer.parseInt(n.substring(stamp.length() + 1, n.length() - 4)));
                    } catch (NumberFormatException ignored) {
                        // Not one of ours.
                    }
                }
            }
        }
        Path target = next < 0
                ? dir.resolve(stamp + ".bak")
                : dir.resolve(stamp + "~" + String.format(Locale.ROOT, "%02d", next + 1) + ".bak");
        Files.copy(normal, target);
        List<Path> copies = new ArrayList<>();
        try (var listing = Files.list(dir)) {
            listing.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(name + ".") && n.endsWith(".bak");
            }).forEach(copies::add);
        }
        // The stamp sorts by time, so the oldest names come first.
        copies.sort(Comparator.comparing(p -> p.getFileName().toString()));
        for (int i = 0; i < copies.size() - BACKUPS_KEPT; i++) {
            Files.deleteIfExists(copies.get(i));
        }
        return true;
    }

    private static void writeAudit(String actor, String module, String path, String before, String after) {
        try {
            Path log = ConfigPaths.dataDir().resolve("config-audit.log");
            Files.createDirectories(log.getParent());
            String who = actor == null || actor.isBlank() ? "staff" : actor;
            String line = Instant.now() + " " + who + " " + module + " " + path
                    + " " + before + " -> " + after + System.lineSeparator();
            Files.writeString(log, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] config audit log failed: {}", AdaptiveDifficultyMod.MOD_ID, e.toString());
        }
    }

    private static List<Entry> fieldEntries(String path, Object value, Set<String> only) {
        List<Entry> entries = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        for (Field field : value.getClass().getFields()) {
            if (skipField(field)) {
                continue;
            }
            if ((path == null || path.isEmpty()) && only != null && !only.contains(field.getName())) {
                continue;
            }
            fields.add(field);
        }
        fields.sort(Comparator
                .comparingInt((Field field) -> rank(kindForType(field.getType())))
                .thenComparing(Field::getName, String.CASE_INSENSITIVE_ORDER));
        for (Field field : fields) {
            Object child;
            try {
                child = field.get(value);
            } catch (ReflectiveOperationException e) {
                continue;
            }
            String childPath = joinField(path, field.getName());
            Kind kind = kindOfValue(child, field);
            entries.add(new Entry(line(field.getName(), summary(child, kind)), childPath, kind));
        }
        return entries;
    }

    private static List<Entry> mapEntries(String path, Map<?, ?> map, Type valueType) {
        List<String> keys = new ArrayList<>();
        for (Object key : map.keySet()) {
            if (key != null) {
                keys.add(String.valueOf(key));
            }
        }
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        if (keys.size() > LIST_CAP) {
            keys = keys.subList(0, LIST_CAP);
        }
        List<Entry> entries = new ArrayList<>();
        for (String key : keys) {
            Object child = map.get(key);
            Kind kind = child == null ? kindForType(rawClass(valueType)) : kindOfValue(child, null);
            entries.add(new Entry(line(key, summary(child, kind)), joinKey(path, key), kind));
        }
        return entries;
    }

    private static List<Entry> listEntries(String path, List<?> list, Type valueType) {
        int limit = Math.min(list.size(), LIST_CAP);
        List<Entry> entries = new ArrayList<>();
        boolean strings = rawClass(valueType) == String.class;
        for (int i = 0; i < limit; i++) {
            Object child = list.get(i);
            String key = strings ? String.valueOf(child) : Integer.toString(i);
            Kind kind = child == null ? kindForType(rawClass(valueType)) : kindOfValue(child, null);
            String label = strings ? key : (i + 1) + "";
            entries.add(new Entry(line(label, strings ? "entry" : summary(child, kind)), joinKey(path, key), kind));
        }
        return entries;
    }

    /** Turn {@code enableProgression} into {@code Enable Progression}. Names with spaces stay. */
    public static String displayName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (raw.indexOf(' ') >= 0) {
            return raw;
        }
        String spaced = raw.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String line(String name, String summary) {
        String label = displayName(name);
        String detail = summary == null ? "" : summary;
        String text = UltraStyle.VALUE + label + " " + UltraStyle.DIM + "- " + UltraStyle.LABEL + detail;
        if (text.length() <= LINE_MAX) {
            return text;
        }
        int keep = Math.max(8, LINE_MAX - 1);
        return text.substring(0, keep) + "...";
    }

    private static String summary(Object value, Kind kind) {
        if (kind == Kind.GROUP) {
            return "group";
        }
        if (kind == Kind.LIST) {
            int size = value instanceof Collection<?> collection ? collection.size() : 0;
            return size == 1 ? "1 entry" : size + " entries";
        }
        if (kind == Kind.MAP) {
            int size = value instanceof Map<?, ?> map ? map.size() : 0;
            return size == 1 ? "1 entry" : size + " entries";
        }
        if (value instanceof Boolean on) {
            return on ? "ON" : "OFF";
        }
        return shortText(value);
    }

    private static String shortText(Object value) {
        if (value == null) {
            return "empty";
        }
        String text = String.valueOf(value).replace('\n', ' ');
        if (text.length() > 18) {
            return text.substring(0, 17) + "...";
        }
        return text;
    }

    private static int rank(Kind kind) {
        return switch (kind) {
            case GROUP, MAP, LIST -> 0;
            case BOOLEAN -> 1;
            case NUMBER -> 2;
            case TEXT, ENUM -> 3;
            default -> 4;
        };
    }

    private static Kind kindOfValue(Object value, Field field) {
        if (value instanceof Boolean) {
            return Kind.BOOLEAN;
        }
        if (value instanceof Number) {
            return Kind.NUMBER;
        }
        if (value instanceof String) {
            return Kind.TEXT;
        }
        if (value instanceof Enum<?>) {
            return Kind.ENUM;
        }
        if (value instanceof Map<?, ?>) {
            return Kind.MAP;
        }
        if (value instanceof List<?>) {
            return Kind.LIST;
        }
        if (value != null && isGroup(value.getClass())) {
            return Kind.GROUP;
        }
        if (field != null) {
            return kindForType(field.getType());
        }
        return Kind.OTHER;
    }

    private static Kind kindForType(Class<?> type) {
        if (type == null) {
            return Kind.OTHER;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Kind.BOOLEAN;
        }
        if (isNumber(type)) {
            return Kind.NUMBER;
        }
        if (type == String.class) {
            return Kind.TEXT;
        }
        if (type.isEnum()) {
            return Kind.ENUM;
        }
        if (Map.class.isAssignableFrom(type)) {
            return Kind.MAP;
        }
        if (List.class.isAssignableFrom(type)) {
            return Kind.LIST;
        }
        if (isGroup(type)) {
            return Kind.GROUP;
        }
        return Kind.OTHER;
    }

    private static boolean isNumber(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class;
    }

    private static boolean isGroup(Class<?> type) {
        if (type == null || type.isEnum() || type.isPrimitive() || type.isArray()) {
            return false;
        }
        Package pkg = type.getPackage();
        return pkg != null && pkg.getName().startsWith("com.dbzlegacy.adaptivedifficulty");
    }

    private static boolean skipField(Field field) {
        int mod = field.getModifiers();
        if (Modifier.isStatic(mod) || Modifier.isFinal(mod) && Modifier.isStatic(mod)
                || Modifier.isTransient(mod) || field.isSynthetic()) {
            return true;
        }
        String typeName = field.getType().getName();
        if (typeName.endsWith("Logger") || typeName.contains("slf4j") || typeName.contains("log4j")) {
            return true;
        }
        return isBlocked(field);
    }

    private static boolean isBlocked(Field field) {
        if (field == null) {
            return false;
        }
        String name = field.getName();
        return "adminPermission".equals(name) || name.contains("Migrated");
    }

    private static Object coerce(Type type, String raw) {
        Object shopValue = PrestigeShopHooks.coerce(type, raw);
        if (shopValue != null) {
            return shopValue;
        }
        String text = raw == null ? "" : raw.trim();
        Class<?> rawClass = rawClass(type);
        if (rawClass == String.class || rawClass == null || rawClass == Object.class) {
            return raw == null ? "" : raw;
        }
        if (rawClass == boolean.class || rawClass == Boolean.class) {
            if ("true".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text) || "off".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Use on or off.");
        }
        if (rawClass.isEnum()) {
            for (Object constant : rawClass.getEnumConstants()) {
                if (constant.toString().equalsIgnoreCase(text)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("That is not one of the allowed names.");
        }
        if (text.isEmpty()) {
            throw new NumberFormatException("empty");
        }
        String number = text.replace(",", "");
        try {
            if (rawClass == int.class || rawClass == Integer.class) {
                return Integer.valueOf(number);
            }
            if (rawClass == long.class || rawClass == Long.class) {
                return Long.valueOf(number);
            }
            if (rawClass == double.class || rawClass == Double.class) {
                double value = Double.parseDouble(number);
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException("That number doesn't work here.");
                }
                return value;
            }
            if (rawClass == float.class || rawClass == Float.class) {
                float value = Float.parseFloat(number);
                if (Float.isNaN(value) || Float.isInfinite(value)) {
                    throw new IllegalArgumentException("That number doesn't work here.");
                }
                return value;
            }
            if (rawClass == short.class || rawClass == Short.class) {
                return Short.valueOf(number);
            }
            if (rawClass == byte.class || rawClass == Byte.class) {
                return Byte.valueOf(number);
            }
        } catch (NumberFormatException e) {
            throw new NumberFormatException(number);
        }
        throw new IllegalArgumentException("That value can't be edited here.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Field field, Object owner) throws ReflectiveOperationException {
        Object current = field.get(owner);
        if (current instanceof LinkedHashMap<?, ?> || current instanceof java.util.HashMap<?, ?>
                || current instanceof java.util.concurrent.ConcurrentHashMap<?, ?>) {
            return (Map<String, Object>) current;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        if (current instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        field.set(owner, copy);
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> mutableList(Field field, Object owner) throws ReflectiveOperationException {
        Object current = field.get(owner);
        if (current instanceof ArrayList<?>) {
            return (List<Object>) current;
        }
        List<Object> copy = new ArrayList<>();
        if (current instanceof Collection<?> collection) {
            copy.addAll(collection);
        }
        field.set(owner, copy);
        return copy;
    }

    private static boolean replaceListValue(List<Object> list, Field field, String key, Object coerced) {
        if (rawClass(valueType(field, 0)) == String.class) {
            for (int i = 0; i < list.size(); i++) {
                if (key.equals(String.valueOf(list.get(i)))) {
                    list.set(i, coerced);
                    return true;
                }
            }
            return false;
        }
        int index = Integer.parseInt(key);
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.set(index, coerced);
        return true;
    }

    private static boolean removeListValue(List<Object> list, Field field, String key) {
        if (rawClass(valueType(field, 0)) == String.class) {
            for (int i = 0; i < list.size(); i++) {
                if (key.equals(String.valueOf(list.get(i)))) {
                    list.remove(i);
                    return true;
                }
            }
            return false;
        }
        int index = Integer.parseInt(key);
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        return true;
    }

    private static Type valueType(Field field, int index) {
        if (field == null) {
            return String.class;
        }
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length > index) {
            return parameterized.getActualTypeArguments()[index];
        }
        return String.class;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    private static String joinField(String path, String name) {
        if (path == null || path.isEmpty()) {
            return name;
        }
        return path + "." + name;
    }

    private static String joinKey(String path, String key) {
        return path + "[" + escape(key) + "]";
    }

    private static String escape(String key) {
        return key.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }

    private static Located locate(String rootId, String path) {
        Root root = root(rootId);
        if (root == null) {
            return null;
        }
        Object current = root.target().get();
        if (current == null) {
            return null;
        }
        if (path == null || path.isEmpty()) {
            return new Located(null, null, null, null, current);
        }
        Object owner = null;
        Object container = null;
        Field field = null;
        String key = null;
        int i = 0;
        while (i < path.length()) {
            if (path.charAt(i) == '.') {
                i++;
            }
            if (i >= path.length()) {
                break;
            }
            if (path.charAt(i) == '[') {
                StringBuilder parsed = new StringBuilder();
                i++;
                while (i < path.length()) {
                    char c = path.charAt(i);
                    if (c == '\\' && i + 1 < path.length()) {
                        parsed.append(path.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == ']') {
                        i++;
                        break;
                    }
                    parsed.append(c);
                    i++;
                }
                key = parsed.toString();
                container = current;
                if (current instanceof Map<?, ?> map) {
                    current = map.get(key);
                } else if (current instanceof List<?> list) {
                    current = findListValue(list, key);
                } else {
                    return null;
                }
                continue;
            }
            int start = i;
            while (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[') {
                i++;
            }
            String name = path.substring(start, i);
            if (current == null) {
                return null;
            }
            field = findField(current.getClass(), name);
            if (field == null || isBlocked(field)) {
                return null;
            }
            owner = current;
            container = null;
            key = null;
            try {
                current = field.get(current);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return new Located(field, owner, container, key, current);
    }

    private static Object findListValue(List<?> list, String key) {
        for (Object value : list) {
            if (key.equals(String.valueOf(value))) {
                return value;
            }
        }
        try {
            int index = Integer.parseInt(key);
            if (index >= 0 && index < list.size()) {
                return list.get(index);
            }
        } catch (NumberFormatException ignored) {
            // A string entry that is not an index.
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            try {
                Field field = cursor.getField(name);
                if (!skipField(field)) {
                    return field;
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    /** {@code owner} holds {@code field}. {@code container} is the map or list when {@code key} is set. */
    private record Located(Field field, Object owner, Object container, String key, Object value) {}
}
