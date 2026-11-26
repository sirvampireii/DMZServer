package com.dbzlegacy.adaptivedifficulty.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Join Bukkit-split tokens for {@code /lm admin …} before Forge Brigadier parse (Mohist). */
public final class LmAdminArgCoalesce {
    private static final Set<String> CLEAR_SCOPES = Set.of(
            "all", "rival", "spar", "dojo", "dojos", "difficulty", "progression", "lm", "*");
    private static final Set<String> COOLDOWN_KINDS = Set.of(
            "all", "race", "racechange", "class", "classchange", "reskin", "skin");

    private LmAdminArgCoalesce() {}

    public static String[] forMohist(String label, String[] args) {
        if (args == null || args.length == 0 || label == null) {
            return args;
        }
        if (!"lm".equalsIgnoreCase(label)) {
            return args;
        }
        if (args.length < 2 || !"admin".equalsIgnoreCase(args[0])) {
            return args;
        }
        String sub = lower(args[1]);
        if ("clear".equals(sub)) {
            return coalesceClearPlayerScope(args);
        }
        if (isCharacterSub(sub) && args.length >= 5
                && isCooldownSub(args[2])
                && "clear".equalsIgnoreCase(args[3])) {
            return coalesceCharacterCooldownClear(args);
        }
        return args;
    }

    private static String[] coalesceClearPlayerScope(String[] args) {
        if (args.length <= 3) {
            return args;
        }
        String playerArg = args[2];
        String scope = args.length > 3 ? args[3] : "all";
        if (args.length > 4) {
            String maybeScope = args[args.length - 1];
            if (CLEAR_SCOPES.contains(lower(maybeScope))) {
                scope = maybeScope;
                playerArg = joinRange(args, 2, args.length - 1);
            } else {
                playerArg = joinRange(args, 2, args.length);
                scope = "all";
            }
        }
        return new String[] {args[0], args[1], playerArg, scope};
    }

    private static String[] coalesceCharacterCooldownClear(String[] args) {
        int playerStart = 4;
        String playerArg = args[playerStart];
        String kind = args.length > playerStart + 1 ? args[playerStart + 1] : "all";
        if (args.length > playerStart + 2) {
            String maybeKind = args[args.length - 1];
            if (COOLDOWN_KINDS.contains(lower(maybeKind))) {
                kind = maybeKind;
                playerArg = joinRange(args, playerStart, args.length - 1);
            } else {
                playerArg = joinRange(args, playerStart, args.length);
                kind = "all";
            }
        } else if (args.length == playerStart + 2
                && COOLDOWN_KINDS.contains(lower(args[playerStart + 1]))) {
            kind = args[playerStart + 1];
        }
        List<String> out = new ArrayList<>();
        out.add(args[0]);
        out.add("character");
        out.add("cooldown");
        out.add("clear");
        out.add(playerArg);
        if (kind != null && !kind.isBlank() && !"all".equalsIgnoreCase(kind)) {
            out.add(kind);
        }
        return out.toArray(new String[0]);
    }

    private static boolean isCharacterSub(String raw) {
        return "character".equals(lower(raw));
    }

    private static boolean isCooldownSub(String raw) {
        return "cooldown".equals(lower(raw));
    }

    private static String joinRange(String[] args, int from, int toExclusive) {
        StringBuilder sb = new StringBuilder(args[from]);
        for (int i = from + 1; i < toExclusive; i++) {
            sb.append(' ').append(args[i]);
        }
        return sb.toString();
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
