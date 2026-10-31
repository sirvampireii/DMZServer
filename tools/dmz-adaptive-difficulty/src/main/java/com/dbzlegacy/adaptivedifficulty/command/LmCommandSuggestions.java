package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Brigadier tab-complete helpers for LegacyMechanics command trees (Forge / Mohist). */
public final class LmCommandSuggestions {
    private LmCommandSuggestions() {}

    /** Online player names — explicit list (Mohist {@code m_5983_()} can include non-player noise). */
    public static final SuggestionProvider<CommandSourceStack> PLAYERS =
            (ctx, builder) -> SharedSuggestionProvider.m_82970_(onlinePlayerNames(ctx.getSource()), builder);

    /** Online players plus names stored in sparring and rival data. */
    public static final SuggestionProvider<CommandSourceStack> CLEAR_TARGETS =
            (ctx, builder) -> SharedSuggestionProvider.m_82970_(clearTargetNames(ctx.getSource()), builder);

    public static List<String> onlinePlayerNames(CommandSourceStack source) {
        if (source == null) {
            return List.of();
        }
        MinecraftServer server = source.m_81377_();
        if (server == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            if (p == null) {
                continue;
            }
            String name = p.m_6302_();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    public static List<String> filterPlayerNames(CommandSourceStack source, String partial) {
        return filterNames(onlinePlayerNames(source), partial);
    }

    /** Tab list for {@code /lm admin clear} and character cooldown clear. Includes offline names. */
    public static List<String> filterClearTargets(CommandSourceStack source, String partial) {
        return filterNames(clearTargetNames(source), partial);
    }

    public static boolean isOfflineClearSlot(String label, String[] args) {
        if (args == null || label == null || !"lm".equalsIgnoreCase(label)) {
            return false;
        }
        int n = args.length;
        String a0 = n > 0 ? lower(args[0]) : "";
        String a1 = n > 1 ? lower(args[1]) : "";
        String a2 = n > 2 ? lower(args[2]) : "";
        String a3 = n > 3 ? lower(args[3]) : "";
        if ("admin".equals(a0) && "clear".equals(a1) && n >= 3) {
            return true;
        }
        return "admin".equals(a0) && isCharacterAdminSub(a1) && isCooldownSub(a2) && "clear".equals(a3)
                && n >= 5;
    }

    public static List<String> clearTargetNames(CommandSourceStack source) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(onlinePlayerNames(source));
        try {
            for (com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord rec
                    : com.dbzlegacy.adaptivedifficulty.rival.RivalStore.get().players.values()) {
                if (rec != null && rec.name != null && !rec.name.isBlank()) {
                    names.add(rec.name);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.sparring.SparStore store =
                    com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get();
            for (com.dbzlegacy.adaptivedifficulty.sparring.SparStore.LeaderboardEntry ent
                    : store.leaderboard.values()) {
                if (ent != null && ent.name != null && !ent.name.isBlank()) {
                    names.add(ent.name);
                }
            }
            if (store.dojoSeason != null && store.dojoSeason.leaderboard != null) {
                for (com.dbzlegacy.adaptivedifficulty.sparring.SparStore.DojoEntry ent
                        : store.dojoSeason.leaderboard.values()) {
                    if (ent == null) {
                        continue;
                    }
                    if (ent.mentorName != null && !ent.mentorName.isBlank()) {
                        names.add(ent.mentorName);
                    }
                    if (ent.members == null) {
                        continue;
                    }
                    for (com.dbzlegacy.adaptivedifficulty.sparring.SparStore.DojoMemberStats member
                            : ent.members.values()) {
                        if (member != null && member.name != null && !member.name.isBlank()) {
                            names.add(member.name);
                        }
                    }
                }
            }
            for (com.dbzlegacy.adaptivedifficulty.sparring.SparStore.MentorBond bond
                    : store.bondsByPlayer.values()) {
                if (bond == null) {
                    continue;
                }
                bond.normalizeApprentices();
                for (com.dbzlegacy.adaptivedifficulty.sparring.SparStore.ApprenticeRef ref : bond.apprentices) {
                    if (ref != null && ref.name != null && !ref.name.isBlank()) {
                        names.add(ref.name);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(names);
    }

    private static List<String> filterNames(List<String> names, String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (prefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }

    private static final Set<String> PADMIN_SUBCOMMANDS = Set.of(
            "help", "info", "sync", "skills", "skill", "held", "completed", "points",
            "tier");

    private static final Set<String> PRESTIGE_MODE_TOKENS = Set.of(
            "set", "add", "remove", "give", "clear", "take");

    /**
     * Mohist Bukkit tab passes {@code args}; last entry is the partial token being completed.
     */
    public static boolean expectsPlayerName(String label, String[] args) {
        return expectsPlayerName(label, args, null);
    }

    public static boolean expectsPlayerName(String label, String[] args, CommandSourceStack source) {
        if (args == null || args.length == 0) {
            return false;
        }
        String cmd = label == null ? "" : label.toLowerCase(Locale.ROOT);
        int n = args.length;
        String a0 = n > 0 ? lower(args[0]) : "";
        String a1 = n > 1 ? lower(args[1]) : "";
        String a2 = n > 2 ? lower(args[2]) : "";
        String a3 = n > 3 ? lower(args[3]) : "";

        if ("lm".equals(cmd)) {
            if ("fusionreset".equals(a0) && n >= 2) {
                return true;
            }
            if ("revive".equals(a0) && n == 2) {
                return true;
            }
            if ("admin".equals(a0) && "clear".equals(a1) && n >= 3) {
                return true;
            }
            if ("admin".equals(a0) && "inspect".equals(a1) && n >= 3 && !"clear".equals(a2)) {
                return true;
            }
            if ("admin".equals(a0) && isCharacterAdminSub(a1) && isCooldownSub(a2) && "clear".equals(a3)
                    && n >= 5) {
                return true;
            }
            if ("mastery".equals(a0) && isMasteryGrant(a1) && masteryPlayerSlot(args)) {
                return true;
            }
            return false;
        }
        if ("difficulty".equals(cmd) || "diff".equals(cmd)) {
            if ("admin".equals(a0) && ("inspect".equals(a1) || "resynclevel".equals(a1))
                    && n >= 3 && !"clear".equals(a2)) {
                return true;
            }
            if ("admin".equals(a0) && "whitelist".equals(a1) && "add".equals(a2) && n >= 4) {
                return true;
            }
            return false;
        }
        if ("padmin".equals(cmd)) {
            return padminPlayerNameSlot(n, a0, args, source);
        }
        if ("rival".equals(cmd) || "spar".equals(cmd)) {
            if (n == 2 && ("stats".equals(a0) || "spectate".equals(a0) || "declare".equals(a0)
                    || "accept".equals(a0) || "decline".equals(a0) || "remove".equals(a0))) {
                return true;
            }
            if ("rival".equals(cmd) && "challenge".equals(a0) && "send".equals(a1) && n >= 3) {
                return true;
            }
            if ("spar".equals(cmd) && "apprentice".equals(a0) && n >= 2) {
                return true;
            }
            if ("spar".equals(cmd) && "admin".equals(a0) && "mentor".equals(a1) && "resetcd".equals(a2) && n >= 4) {
                return true;
            }
            return false;
        }
        return false;
    }

    /** Partial token for the player-name slot (Bukkit often omits an empty trailing arg). */
    public static String playerNamePartial(String label, String[] args) {
        return playerNamePartial(label, args, null);
    }

    public static String playerNamePartial(String label, String[] args, CommandSourceStack source) {
        if (args == null || args.length == 0 || !expectsPlayerName(label, args, source)) {
            if (args == null || args.length == 0) {
                return "";
            }
            String last = args[args.length - 1];
            return last == null ? "" : last;
        }
        String cmd = label == null ? "" : label.toLowerCase(Locale.ROOT);
        int n = args.length;
        if ("padmin".equals(cmd)) {
            if (n <= 1) {
                return "";
            }
            if (n == 2) {
                String p = args[1];
                return p == null ? "" : p;
            }
            return "";
        }
        if ("lm".equals(cmd)) {
            if (n > 0 && ("fusionreset".equals(lower(args[0])) || "revive".equals(lower(args[0])))) {
                if (n < 2) {
                    return "";
                }
                String p = args[1];
                return p == null ? "" : p;
            }
            if (n > 0 && "mastery".equals(lower(args[0]))) {
                if (n >= 4 && isNumericToken(args[2])) {
                    String p = args[3];
                    return p == null ? "" : p;
                }
                if (n >= 3) {
                    String p = args[2];
                    return p == null ? "" : p;
                }
                return "";
            }
            if (n >= 5) {
                String p = args[4];
                return p == null ? "" : p;
            }
            if (n >= 3) {
                String p = args[2];
                return p == null ? "" : p;
            }
        }
        if ("difficulty".equals(cmd) || "diff".equals(cmd)) {
            if (n >= 4) {
                String p = args[3];
                return p == null ? "" : p;
            }
            if (n >= 3) {
                String p = args[2];
                return p == null ? "" : p;
            }
        }
        if ("rival".equals(cmd) || "spar".equals(cmd)) {
            String p = args[1];
            return p == null ? "" : p;
        }
        String last = args[args.length - 1];
        return last == null ? "" : last;
    }

    /** Append trailing space when the next Brigadier token is a player name. */
    public static boolean needsTrailingSpaceForTab(String label, String[] args) {
        return needsTrailingSpaceForTab(label, args, null);
    }

    public static boolean needsTrailingSpaceForTab(String label, String[] args, CommandSourceStack source) {
        return expectsPlayerName(label, args, source)
                && playerNamePartial(label, args, source).isEmpty();
    }

    /**
     * Player-name slot for {@code /padmin <sub> …}. Do not hijack tab when completing subcommands
     * or adjust modes (set/add/remove) after a resolved player name.
     */
    private static boolean padminPlayerNameSlot(
            int argCount, String subRaw, String[] args, CommandSourceStack source) {
        String sub = lower(subRaw);
        if (!isPrestigeAdminPlayerSub(sub)) {
            return false;
        }
        if (argCount == 1) {
            // Only after a full subcommand literal — partial "h" must fall through to Brigadier.
            return PADMIN_SUBCOMMANDS.contains(sub);
        }
        if (argCount == 2 && args.length >= 2) {
            String token = args[1];
            if (token == null || token.isBlank()) {
                return true;
            }
            if (PRESTIGE_MODE_TOKENS.contains(lower(token))) {
                return false;
            }
            if (isExactOnlinePlayer(source, token)) {
                return false;
            }
            return true;
        }
        return false;
    }

    private static boolean isExactOnlinePlayer(CommandSourceStack source, String name) {
        if (source == null || name == null || name.isBlank()) {
            return false;
        }
        String want = name.trim();
        for (String online : onlinePlayerNames(source)) {
            if (online.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrestigeAdminPlayerSub(String sub) {
        return switch (sub) {
            case "info", "sync", "skills", "skill", "held", "completed", "points",
                    "tier" -> true;
            default -> false;
        };
    }

    private static boolean isCharacterAdminSub(String raw) {
        return "character".equals(lower(raw));
    }

    private static boolean isCooldownSub(String raw) {
        return "cooldown".equals(lower(raw));
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    public static final SuggestionProvider<CommandSourceStack> LM_OPEN_SYSTEMS = literals(
            "hub", "main", "difficulty", "rival", "spar", "prestige", "character", "progression",
            "skills", "skillcheck", "saga", "android_remove", "config"
    );

    public static final SuggestionProvider<CommandSourceStack> LM_INSPECT_SYSTEMS = literals(
            "hub", "clear", "difficulty", "rival", "spar", "skillcheck", "character",
            "prestige", "progression", "skills"
    );

    public static final SuggestionProvider<CommandSourceStack> LM_CLEAR_SCOPES = literals(
            "all", "rival", "spar", "difficulty", "progression"
    );

    public static final SuggestionProvider<CommandSourceStack> LM_CHARACTER_COOLDOWN_KINDS = literals(
            "all", "race", "class", "reskin"
    );

    public static final SuggestionProvider<CommandSourceStack> SYSLOG_MODES = literals(
            "on", "off", "status", "flush", "toggle"
    );

    public static final SuggestionProvider<CommandSourceStack> VANILLA_DIFFICULTY = literals(
            "peaceful", "easy", "normal", "hard"
    );

    public static final SuggestionProvider<CommandSourceStack> RIVAL_TOP_CATEGORIES = literals(
            "rp", "wins", "win", "streak", "beststreak", "damage", "dmg", "combo", "hit", "hits",
            "battles", "battle", "challenges"
    );

    public static final SuggestionProvider<CommandSourceStack> SPAR_TOP_CATEGORIES = literals(
            "tp", "rp", "wins", "streak", "damage", "sessions", "dojo"
    );

    public static final SuggestionProvider<CommandSourceStack> PRESTIGE_ADJUST_MODES = literals(
            "set", "add", "remove"
    );

    public static final SuggestionProvider<CommandSourceStack> PRESTIGE_TIER_MODES = literals(
            "set", "add", "remove", "give", "clear"
    );

    public static final SuggestionProvider<CommandSourceStack> PRESTIGE_TIER_IDS = literals(
            "0", "1", "2", "3", "4", "5", "6", "7", "all"
    );

    public static final SuggestionProvider<CommandSourceStack> DIFFICULTY_ADMIN_SET_KEYS = literals(
            "enabled", "whitelistEnabled", "staffFreeAncientCoinCosts", "balanceTelemetryEnabled",
            "enableSystemTelemetry", "prestigeMultiplier", "levelMultiplier", "rewardScaling",
            "guibackend"
    );

    public static final SuggestionProvider<CommandSourceStack> PRESTIGE_TURNIN_AMOUNTS = literals(
            "1", "2", "3", "6", "9", "12", "15", "18"
    );

    public static final SuggestionProvider<CommandSourceStack> PRESTIGE_TIERS = literals(
            "1", "2", "3", "4", "5", "6", "7"
    );

    public static final SuggestionProvider<CommandSourceStack> PRESTIGE_EFFECTS_BUY = literals(
            "majin", "mutant", "godki"
    );

    public static final SuggestionProvider<CommandSourceStack> PRESTIGE_EFFECTS_REMOVE = literals(
            "majin", "mutant"
    );

    /** Prestige shop skill ids — live from the offer catalog so new skills appear automatically. */
    public static final SuggestionProvider<CommandSourceStack> PRESTIGE_SKILLS =
            (ctx, builder) -> {
                List<String> ids = new ArrayList<>();
                for (PrestigePointsSystem.SkillOffer offer : PrestigePointsSystem.skillOffers()) {
                    ids.add(offer.id());
                }
                return SharedSuggestionProvider.m_82970_(ids, builder);
            };

    public static final SuggestionProvider<CommandSourceStack> WEIGHT_TYPES = literals(
            "turtle_shell", "workout_weights", "piccolo_cape"
    );

    /**
     * Suggested {@code /character do} actions. Race/class/reskin confirm rows are
     * staff-gated in {@link CharacterCommands#STAFF_ONLY_ACTIONS} — hidden here so
     * tab-complete never offers a player an action they cannot run.
     */
    private static final List<String> CHARACTER_ACTION_WORDS = List.of(
            "race_confirm", "class_confirm", "reskin_confirm",
            "bone_unlock", "bone_equip", "bone_toggle", "bone_unequip",
            "bone_preview", "bone_restore", "bone_race_default",
            "bodypart_unlock", "bodypart_toggle", "bodypart_clear"
    );

    public static final SuggestionProvider<CommandSourceStack> CHARACTER_ACTIONS =
            (ctx, builder) -> {
                boolean staff = StaffAccess.isStaffSource(ctx.getSource());
                List<String> visible = new ArrayList<>();
                for (String action : CHARACTER_ACTION_WORDS) {
                    if (staff || !CharacterCommands.STAFF_ONLY_ACTIONS.contains(action)) {
                        visible.add(action);
                    }
                }
                return SharedSuggestionProvider.m_82970_(visible, builder);
            };

    /** Accepted race-change keep percents, live from character-services config. */
    public static final SuggestionProvider<CommandSourceStack> CHARACTER_KEEP_PCT =
            (ctx, builder) -> {
                List<String> steps = new ArrayList<>();
                for (int step : CharacterServicesConfig.preservationSteps()) {
                    steps.add(String.valueOf(step));
                }
                return SharedSuggestionProvider.m_82970_(steps, builder);
            };

    public static SuggestionProvider<CommandSourceStack> literals(String... values) {
        List<String> list = Arrays.asList(values);
        return (ctx, builder) -> SharedSuggestionProvider.m_82970_(list, builder);
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> word(
            String name, SuggestionProvider<CommandSourceStack> suggestions) {
        RequiredArgumentBuilder<CommandSourceStack, String> arg =
                Commands.m_82129_(name, StringArgumentType.word());
        if (suggestions != null) {
            arg.suggests(suggestions);
        }
        return arg;
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> playerWord(String name) {
        return word(name, PLAYERS);
    }

    /**
     * Name-first player slot for mastery grants. Uses vanilla {@code brigadier:string}
     * (word). A custom {@code ArgumentType} is not safe here: LM is server-only, and
     * {@code ClientboundCommandsPacket} must serialize every staff command node with a
     * type the client already knows. Amount-first still wins when the first token is a
     * number, because {@code DoubleArgumentType} is listed first and fails on names.
     */
    public static RequiredArgumentBuilder<CommandSourceStack, String> playerWordUnlessNumber(String name) {
        return Commands.m_82129_(name, StringArgumentType.word()).suggests(PLAYERS);
    }

    /** True when the token is a finite decimal, so it is an amount and not a name. */
    public static boolean isNumericToken(String token) {
        if (token == null) {
            return false;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        try {
            return Double.isFinite(Double.parseDouble(trimmed));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isMasteryGrant(String sub) {
        return "grant-first-superform".equals(sub) || "grant-first-legendary".equals(sub);
    }

    /**
     * Player slot for {@code /lm mastery grant-first-*}.
     * Amount-first: the token after a number. Name-first: the token after the grant name
     * when that token is not a number.
     */
    private static boolean masteryPlayerSlot(String[] args) {
        int n = args.length;
        if (n == 3) {
            return !isNumericToken(args[2]);
        }
        return n == 4 && isNumericToken(args[2]);
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> playerString(String name) {
        return Commands.m_82129_(name, StringArgumentType.string()).suggests(PLAYERS);
    }
}
