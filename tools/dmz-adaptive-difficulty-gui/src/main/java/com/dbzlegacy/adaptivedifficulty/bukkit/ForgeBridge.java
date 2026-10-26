package com.dbzlegacy.adaptivedifficulty.bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Reflects into the Forge mod for live values / actions (Mohist shared JVM).
 * Surface: hub status, Tiers (UnlockTier 1–7 buy/lower/reset), Rival Teams, character_reset.
 */
public final class ForgeBridge {
    private static final long PLACEHOLDER_TTL_MS = 200L;
    private static final Map<UUID, CachedPlaceholders> PLACEHOLDER_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean resolved;
    private static volatile String resolveError;
    private static Class<?> serverPlayerCls;
    private static Class<?> cacheCls;
    private static Class<?> actionsCls;
    private static Method getHandle;
    private static Method cacheGet;
    private static Method cacheRefresh;
    private static Method cacheData;
    private static Method economyBalanceText;
    private static Method economyFormat;
    private static Method economyFormatExactCost;
    private static Method economyActivationCostPlayer;
    private static Method paidFeatureBypassCost;
    private static Method economyCountOf;
    private static Method economyBalance;
    private static Class<?> coinKindCls;
    private static Method unlockTierValues;
    private static Method unlockTierById;
    private static Method unlockTierActivationCost;
    private static Method unlockTierActivationCostForLevel;
    private static Method unlockTierMaxDifficulty;
    private static Method unlockTierRequiredDmzLevel;
    private static Method unlockTierRequiredPrestige;
    private static Method unlockTierRequirementTip;
    private static Method titleActiveDisplay;
    private static Method titleActiveId;
    private static Method titleHas;
    private static Method titleValues;
    private static Method titleRequirementTip;
    private static Method snapshotState;
    private static Method snapshotStateColor;
    private static Method actionsHandle;
    private static Method actionsHandleArg;
    private static Method actionsHandleArgNoReopen;
    private static Method resultMessage;
    private static Method resultOk;
    private static Method chatMenuOpen;
    private static Method rivalChatMenuOpen;
    private static Method sparChatMenuOpen;
    private static Method rivalPlaceholdersMethod;
    private static Method rivalLinesMethod;
    private static Method rivalHandleDoMethod;
    private static Method rivalPendingDeclareArgsMethod;
    private static Method rivalAcceptCandidateArgsMethod;
    private static Method rivalCurrentCardsMethod;
    private static Method rivalPastCardsMethod;
    private static Method rivalPendingInviteCardsMethod;
    private static Method rivalPendingChallengeCardsMethod;
    private static Method rivalCurrentArgsMethod;
    private static Method sparPlaceholdersMethod;
    private static Method sparLinesMethod;
    private static Method sparHandleDoMethod;
    private static Method sparPendingMentorInviteCardsMethod;
    private static Method sparPendingIncomingMentorArgsMethod;
    private static Method sparPendingDojoWarCardsMethod;
    private static Method sparApprenticeCardsMethod;
    private static Method sparMembershipDojoCardsMethod;
    private static Method sparRivalDojoCardsMethod;
    private static Method sparDojoMemberCardsMethod;
    private static Method sparDojoTopCardsMethod;
    private static Method diffTeamLinesMethod;
    private static Method diffTeamMutualCardsMethod;
    private static Method endDragonMenuLinesMethod;
    private static Method endDragonCanSummonMethod;
    private static Method hubChatMenuOpen;
    private static Method hubPlaceholdersMethod;
    private static Method hubLinesMethod;
    private static Method hubHandleDoMethod;
    private static Method progressionChatMenuOpen;
    private static Method progressionPlaceholdersMethod;
    private static Method progressionLinesMethod;
    private static Method progressionHandleDoMethod;
    private static Method prestigePlaceholdersMethod;
    private static Method prestigeLinesMethod;
    private static Method prestigeHandleDoMethod;
    private static Method prestigeAdminMethod;
    private static Method charPlaceholdersMethod;
    private static Method charLinesMethod;
    private static Method charHandleDoMethod;
    private static Method charRaceCardsMethod;
    private static Method charHeadBoneCardsMethod;
    private static Method charClassCardsMethod;
    private static Method skillsPlaceholdersMethod;
    private static Method skillsLinesMethod;
    private static Method skillsHandleDoMethod;
    private static Method androidConvertMethod;
    private static Method androidConvertConsoleMethod;
    private static Method androidRemoveMethod;
    private static Method endDragonMethod;
    private static Method boostMethod;
    private static Method progressionHelpMethod;
    private static Method progressionHelpForStaffMethod;
    private static Method progressionStatusMethod;
    private static Method progressionAdminFlagMethod;
    private static volatile Field RESULT_MESSAGE_FIELD;

    private ForgeBridge() {}

    public static Object nmsPlayer(Player player) {
        if (player == null) {
            return null;
        }
        try {
            if (getHandle == null) {
                getHandle = player.getClass().getMethod("getHandle");
            }
            Object handle = getHandle.invoke(player);
            ensureResolved(handle == null ? null : handle.getClass().getClassLoader());
            return handle;
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return null;
        }
    }

    public static Map<String, String> placeholders(Player player) {
        if (player == null) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        CachedPlaceholders cached = PLACEHOLDER_CACHE.get(player.getUniqueId());
        if (cached != null && now - cached.atMs <= PLACEHOLDER_TTL_MS) {
            return cached.map;
        }
        Map<String, String> built = buildPlaceholders(player);
        PLACEHOLDER_CACHE.put(player.getUniqueId(), new CachedPlaceholders(built, now));
        return built;
    }

    public static String placeholder(Player player, String id) {
        if (id == null) {
            return "";
        }
        String key = id.toLowerCase(Locale.ROOT);
        return placeholders(player).getOrDefault(key, "");
    }

    private static Map<String, String> buildPlaceholders(Player player) {
        Map<String, String> out = new HashMap<>();
        // Fail-closed defaults — GUI must not look healthy when Forge is unreachable.
        out.put("bridge_ok", "false");
        out.put("system_enabled", "false");
        out.put("player_allowed", "false");
        out.put("personal_enabled", "false");
        Object nms = nmsPlayer(player);
        if (nms == null || !forgeAvailable()) {
            return out;
        }
        try {
            ensureResolved();
            Object snap = cacheRefresh.invoke(null, nms);
            if (snap == null) {
                return out;
            }

            long active = longField(snap, "active");
            long available = longField(snap, "availableMax");
            long calculated = longField(snap, "calculated");
            long purchased = longField(snap, "purchased");
            long personalMax = longField(snap, "personalMax");
            long teamBonus = longField(snap, "teamThresholdBonus");
            long teamContrib = longField(snap, "teamContribution");
            long combatRating = longField(snap, "combatRating");
            long ancientCopper = longField(snap, "ancientCopper");
            int level = intField(snap, "dmzLevel");
            // Prefer Forge tierScalingDmzLevel — snap can still carry a polluted gate
            // of 1 from highestDmzLevel until the next reliable sample.
            try {
                ClassLoader cl = nms.getClass().getClassLoader();
                Class<?> dmz = Class.forName(
                        "com.dbzlegacy.adaptivedifficulty.calc.DmzProgression", true, cl);
                Class<?> playerCls = Class.forName(
                        "net.minecraft.world.entity.player.Player", true, cl);
                Object shown = dmz.getMethod("tierScalingDmzLevel", playerCls).invoke(null, nms);
                if (shown instanceof Number n && n.intValue() > 1) {
                    level = n.intValue();
                }
            } catch (Throwable ignored) {
            }
            int prestige = intField(snap, "prestige");
            int activeTier = intField(snap, "activeTier");
            int highestUnlocked = intField(snap, "highestUnlockedTier");
            Object teamMode = field(snap, "teamMode");
            Object activeTierName = field(snap, "activeTierName");

            // Internal numbers kept for PAPI compatibility; GUIs no longer show active/available points.
            out.put("active", String.valueOf(active));
            out.put("available", String.valueOf(available));
            out.put("available_max", String.valueOf(available));
            out.put("calculated", String.valueOf(calculated));
            out.put("purchased", String.valueOf(purchased));
            out.put("personal_max", String.valueOf(personalMax));
            out.put("team_bonus", String.valueOf(teamBonus));
            out.put("team_contrib", String.valueOf(teamContrib));
            out.put("team_contribution", String.valueOf(teamContrib));
            out.put("level", String.valueOf(level));
            out.put("dmz_level", String.valueOf(level));
            out.put("prestige", String.valueOf(prestige));
            if (teamMode != null) {
                String modeKey = String.valueOf(teamMode).toLowerCase(Locale.ROOT);
                out.put("team_mode", modeKey);
                out.put("team_mode_label", teamModeLabel(modeKey));
            } else {
                out.put("team_mode", "personal_only");
                out.put("team_mode_label", "Personal");
            }
            out.put("combat_rating", String.valueOf(combatRating > 0 ? combatRating : calculated));
            out.put("active_tier", String.valueOf(activeTier));
            out.put("highest_unlocked", String.valueOf(highestUnlocked));
            out.put("active_tier_name", activeTierName == null ? "None" : String.valueOf(activeTierName));
            out.put("tier", activeTierName == null ? "None" : String.valueOf(activeTierName));

            putTeamScalingPlaceholders(out, nms);

            long liveBalance = ancientCopper > 0 ? ancientCopper : purchased;
            if (economyBalance != null) {
                try {
                    Object bal = economyBalance.invoke(null, nms);
                    if (bal instanceof Number n) {
                        liveBalance = n.longValue();
                    }
                } catch (Throwable ignored) {
                }
            }
            out.put("ancient_coins", String.valueOf(liveBalance));

            String balance = "?";
            if (economyBalanceText != null) {
                balance = String.valueOf(economyBalanceText.invoke(null, nms));
            } else if (economyFormat != null) {
                balance = String.valueOf(economyFormat.invoke(null, liveBalance));
            }
            out.put("balance", balance);
            out.put("currency", "Ancient Coins");

            putCoinCounts(out, nms);

            boolean systemOn = systemEnabled();
            out.put("system_enabled", systemOn ? "true" : "false");
            out.put("system_status", systemOn ? "ENABLED" : "DISABLED");
            boolean wlOn = whitelistEnabled();
            boolean allowed = playerAllowed(player);
            out.put("whitelist_enabled", wlOn ? "true" : "false");
            out.put("whitelist_status", wlOn ? "ON" : "OFF");
            out.put("player_allowed", allowed ? "true" : "false");

            boolean personalOn = true;
            boolean coinChatOn = false;
            if (cacheData != null) {
                try {
                    Object data = cacheData.invoke(null, nms);
                    if (data != null) {
                        Object pe = data.getClass().getMethod("isPersonalEnabled").invoke(data);
                        if (pe instanceof Boolean b) {
                            personalOn = b;
                        }
                        Object cc = data.getClass().getMethod("isCoinDropChat").invoke(data);
                        if (cc instanceof Boolean b) {
                            coinChatOn = b;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            out.put("personal_enabled", personalOn ? "true" : "false");
            out.put("personal_status", personalOn ? "ON" : "OFF");
            out.put("coin_drop_chat", coinChatOn ? "true" : "false");
            boolean bypassAncient = bypassAncientCoinCost(nms);
            out.put("bypass_ancient_cost", bypassAncient ? "true" : "false");
            out.put("staff_free_ancient_coin_costs", staffFreeAncientCoinCosts() ? "true" : "false");
            putCounterPlaceholders(out, nms);

            // Prestige points shop — for NPC scripts / PlaceholderAPI.
            try {
                Map<String, String> pp = prestigePlaceholders(player);
                if (pp != null) {
                    out.put("prestige_points", pp.getOrDefault("points", "0"));
                    out.put("prestige_held", pp.getOrDefault("held", "0"));
                    out.put("prestige_level_cap", pp.getOrDefault("level_cap", "100000"));
                    out.put("prestige_breakthroughs", pp.getOrDefault("breakthroughs", "0"));
                    out.put("prestige_majin", pp.getOrDefault("majin", "false"));
                    out.put("prestige_mutant", pp.getOrDefault("mutant", "false"));
                    out.put("prestige_spend_ok", pp.getOrDefault("last_spend_ok", "false"));
                }
            } catch (Throwable ignored) {
            }

            // UnlockTier 1–7 — level-scaled activation costs + live unlock flags.
            Object playerData = null;
            if (cacheData != null) {
                try {
                    playerData = cacheData.invoke(null, nms);
                } catch (Throwable ignored) {
                }
            }
            if (unlockTierValues != null) {
                try {
                    for (Object ut : (Object[]) unlockTierValues.invoke(null)) {
                        int id = ((Number) field(ut, "id")).intValue();
                        Object display = field(ut, "display");
                        out.put("tier_" + id + "_name", "T" + id + " " + (display == null ? "" : display));
                        long cost = resolveTierCost(ut, nms, level);
                        // Never show resolve failures as "free" (formatExactCost(0)).
                        String costText;
                        if (bypassAncientCoinCost(nms)) {
                            costText = "free";
                        } else if (cost <= 0L) {
                            costText = "?";
                        } else if (economyFormatExactCost != null) {
                            costText = String.valueOf(economyFormatExactCost.invoke(null, cost));
                        } else {
                            costText = String.valueOf(cost);
                        }
                        out.put("unlock_tier_" + id + "_cost", costText);
                        out.put("tier_" + id + "_cost", costText);
                        out.put("tier_" + id + "_cost_raw", String.valueOf(Math.max(0L, cost)));
                        // Fail closed — never invent unlocks from highest_unlocked.
                        boolean unlocked = false;
                        if (playerData != null) {
                            try {
                                Object u = playerData.getClass()
                                        .getMethod("hasUnlockedTier", int.class)
                                        .invoke(playerData, id);
                                unlocked = u instanceof Boolean b && b;
                            } catch (Throwable ignored) {
                                unlocked = false;
                            }
                        }
                        out.put("tier_" + id + "_unlocked", unlocked ? "true" : "false");
                        long reqLevel = resolveTierRequiredLevel(ut, id);
                        int reqPrestige = resolveTierRequiredPrestige(ut, id);
                        String reqTip = resolveTierRequirementTip(ut, reqLevel, reqPrestige);
                        out.put("tier_" + id + "_req_level", String.valueOf(reqLevel));
                        out.put("tier_" + id + "_req_prestige", String.valueOf(reqPrestige));
                        out.put("tier_" + id + "_req", reqTip);
                        out.put("unlock_tier_" + id + "_req_level", String.valueOf(reqLevel));
                        out.put("unlock_tier_" + id + "_req_prestige", String.valueOf(reqPrestige));
                        out.put("unlock_tier_" + id + "_req", reqTip);
                        Object defaultMax = field(ut, "defaultMaxDifficulty");
                        out.put("unlock_tier_" + id + "_max", String.valueOf(defaultMax));
                        if (unlockTierById != null && unlockTierMaxDifficulty != null) {
                            Object live = unlockTierById.invoke(null, id);
                            if (live != null) {
                                try {
                                    long max = ((Number) unlockTierMaxDifficulty.invoke(live)).longValue();
                                    out.put("unlock_tier_" + id + "_max", String.valueOf(max));
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            putTitlePlaceholders(out, nms);

            if (snapshotState != null) {
                Object state = snapshotState.invoke(snap);
                out.put("state", state == null ? "?" : String.valueOf(state));
            }
            if (snapshotStateColor != null) {
                Object stateColor = snapshotStateColor.invoke(snap);
                out.put("state_color", stateColor == null ? "f" : String.valueOf(stateColor));
            }
            // Personal OFF always wins for status display (GUI / PAPI).
            if (!personalOn) {
                out.put("state", "Off");
                out.put("state_color", "c");
            }
            out.put("bridge_ok", "true");
        } catch (Throwable ignored) {
            // Forge mod not loaded / partial API — keep fail-closed defaults.
            out.put("bridge_ok", "false");
            out.put("system_enabled", "false");
            out.put("player_allowed", "false");
        }
        return out;
    }

    private static long resolveTierRequiredLevel(Object unlockTier, int tierId) {
        try {
            if (unlockTierRequiredDmzLevel != null && unlockTier != null) {
                Object v = unlockTierRequiredDmzLevel.invoke(unlockTier);
                if (v instanceof Number n) {
                    return Math.max(0L, n.longValue());
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object fallback = field(unlockTier, "defaultRequiredLevel");
            if (fallback instanceof Number n) {
                return Math.max(0L, n.longValue());
            }
        } catch (Throwable ignored) {
        }
        // Stock gates when reflection fails.
        return switch (tierId) {
            case 1 -> 1L;
            case 2 -> 500L;
            case 3 -> 1_000L;
            case 4 -> 5_000L;
            case 5 -> 10_000L;
            case 6 -> 50_000L;
            case 7 -> 100_000L;
            default -> 0L;
        };
    }

    private static int resolveTierRequiredPrestige(Object unlockTier, int tierId) {
        try {
            if (unlockTierRequiredPrestige != null && unlockTier != null) {
                Object v = unlockTierRequiredPrestige.invoke(unlockTier);
                if (v instanceof Number n) {
                    return Math.max(0, n.intValue());
                }
            }
        } catch (Throwable ignored) {
        }
        return Math.max(0, tierId);
    }

    private static String resolveTierRequirementTip(Object unlockTier, long reqLevel, int reqPrestige) {
        try {
            if (unlockTierRequirementTip != null && unlockTier != null) {
                Object tip = unlockTierRequirementTip.invoke(unlockTier);
                if (tip != null) {
                    String text = String.valueOf(tip).trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "DMZ " + reqLevel + " or Prestige " + reqPrestige;
    }

    private static long resolveTierCost(Object unlockTier, Object nmsPlayer, int dmzLevel) {
        try {
            if (economyActivationCostPlayer != null) {
                Object cost = economyActivationCostPlayer.invoke(null, unlockTier, nmsPlayer);
                if (cost instanceof Number n) {
                    return n.longValue();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (unlockTierActivationCostForLevel != null) {
                Object cost = unlockTierActivationCostForLevel.invoke(unlockTier, dmzLevel);
                if (cost instanceof Number n) {
                    return n.longValue();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (unlockTierActivationCost != null) {
                Object cost = unlockTierActivationCost.invoke(unlockTier);
                if (cost instanceof Number n) {
                    return n.longValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    /** Class / top-stat counter identity for staff GUI + PAPI. */
    private static void putCounterPlaceholders(Map<String, String> out, Object nms) {
        out.putIfAbsent("fighting_class", "");
        out.putIfAbsent("race", "");
        out.putIfAbsent("fighting_style", "HYBRID");
        out.putIfAbsent("top_stats", "—");
        out.putIfAbsent("weak_stat", "NONE");
        if (nms == null) {
            return;
        }
        try {
            ensureResolved(nms.getClass().getClassLoader());
            Class<?> profileCls = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile",
                    nms.getClass().getClassLoader());
            Object profile = profileCls.getMethod("of", serverPlayerCls).invoke(null, nms);
            if (profile == null) {
                return;
            }
            Object cls = profileCls.getField("fightingClass").get(profile);
            Object race = profileCls.getField("race").get(profile);
            Object style = profileCls.getField("style").get(profile);
            Object weak = profileCls.getField("weakest").get(profile);
            Object topLabel = profileCls.getMethod("topStatsLabel").invoke(profile);
            out.put("fighting_class", cls == null ? "" : String.valueOf(cls));
            out.put("race", race == null ? "" : String.valueOf(race));
            out.put("fighting_style", style == null ? "HYBRID" : String.valueOf(style));
            out.put("weak_stat", weak == null ? "NONE" : String.valueOf(weak));
            out.put("top_stats", topLabel == null ? "—" : String.valueOf(topLabel));
            Class<?> scaled = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat",
                    nms.getClass().getClassLoader());
            scaled.getMethod("putPlaceholders", Map.class, serverPlayerCls).invoke(null, out, nms);
        } catch (Throwable ignored) {
        }
    }

    private static void putCoinCounts(Map<String, String> out, Object nms) {
        // Ladder: Copper → Iron → Gold → Emerald → Diamond → Netherite
        // (Lapis / Ender Pearl ancients are unused by this mod.)
        String[] keys = {
                "coins_copper", "coins_iron", "coins_gold",
                "coins_emerald", "coins_diamond", "coins_netherite"
        };
        String[] kindNames = {
                "COPPER", "IRON", "GOLD", "EMERALD", "DIAMOND", "NETHERITE"
        };
        for (String key : keys) {
            out.putIfAbsent(key, "0");
        }
        if (economyCountOf == null || coinKindCls == null || nms == null) {
            return;
        }
        try {
            Object[] kinds = (Object[]) coinKindCls.getMethod("values").invoke(null);
            for (Object kind : kinds) {
                String name = String.valueOf(kind);
                Object countObj = economyCountOf.invoke(null, nms, kind);
                long count = countObj instanceof Number n ? n.longValue() : 0L;
                for (int i = 0; i < kindNames.length; i++) {
                    if (kindNames[i].equalsIgnoreCase(name)) {
                        out.put(keys[i], String.valueOf(count));
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void putTitlePlaceholders(Map<String, String> out, Object nms) {
        out.put("active_title", "None");
        out.put("active_title_id", "");
        out.put("title_score", "0");
        out.put("titles_unlocked", "0");
        out.put("titles_total", "0");
        out.put("nearby_elites", "0");
        out.put("bosses_killed", "0");
        out.put("elites_killed", "0");
        out.put("title_sense", "true");
        out.put("title_perk", "");
        if (nms == null) {
            return;
        }
        try {
            if (titleActiveDisplay != null) {
                Object display = titleActiveDisplay.invoke(null, nms);
                out.put("active_title", display == null || String.valueOf(display).isBlank()
                        ? "None" : String.valueOf(display));
            }
            if (titleActiveId != null) {
                Object id = titleActiveId.invoke(null, nms);
                out.put("active_title_id", id == null ? "" : String.valueOf(id));
            }
            ClassLoader cl = nms.getClass().getClassLoader();
            Class<?> titleSystem = loadClass("com.dbzlegacy.adaptivedifficulty.title.TitleSystem", cl);
            Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", cl);
            try {
                Object score = titleSystem.getMethod("computeTitleScore", sp).invoke(null, nms);
                if (score instanceof Number n) {
                    out.put("title_score", String.valueOf(n.intValue()));
                }
            } catch (Throwable ignored) {
            }
            Object data = cacheData == null ? null : cacheData.invoke(null, nms);
            Object progress = data == null ? null
                    : data.getClass().getMethod("titleProgress").invoke(data);
            int masteryActive = 0;
            if (progress != null) {
                Object elites = progress.getClass().getMethod("elitesKilled").invoke(progress);
                Object bosses = progress.getClass().getMethod("bossesKilled").invoke(progress);
                Object sense = progress.getClass().getMethod("titleSenseChat").invoke(progress);
                out.put("elites_killed", elites == null ? "0" : String.valueOf(elites));
                out.put("bosses_killed", bosses == null ? "0" : String.valueOf(bosses));
                out.put("title_sense", Boolean.TRUE.equals(sense) ? "true" : "false");
                String activeId = out.getOrDefault("active_title_id", "");
                if (!activeId.isBlank()) {
                    Object m = progress.getClass().getMethod("masteryLevel", String.class)
                            .invoke(progress, activeId);
                    if (m instanceof Number n) {
                        masteryActive = n.intValue();
                    }
                }
            }
            try {
                Class<?> senseCls = loadClass("com.dbzlegacy.adaptivedifficulty.title.TitleSense", cl);
                Object near = senseCls.getMethod("countNearbyElites", sp).invoke(null, nms);
                if (near instanceof Number n) {
                    out.put("nearby_elites", String.valueOf(n.intValue()));
                }
            } catch (Throwable ignored) {
            }
            int unlocked = 0;
            int total = 0;
            if (titleValues != null) {
                for (Object title : (Object[]) titleValues.invoke(null)) {
                    total++;
                    String id = String.valueOf(field(title, "id"));
                    String display = String.valueOf(field(title, "display"));
                    Object rarity = field(title, "rarity");
                    String rarityName = rarity == null ? "" : String.valueOf(field(rarity, "display"));
                    String rarityColor = rarity == null ? "7" : String.valueOf(field(rarity, "color"));
                    int mastery = 0;
                    if (progress != null) {
                        Object m = progress.getClass().getMethod("masteryLevel", String.class)
                                .invoke(progress, id);
                        if (m instanceof Number n) {
                            mastery = n.intValue();
                        }
                    }
                    try {
                        Object masteredName = title.getClass()
                                .getMethod("masteryDisplay", int.class)
                                .invoke(title, mastery);
                        if (masteredName != null) {
                            display = String.valueOf(masteredName);
                        }
                    } catch (Throwable ignored) {
                    }
                    out.put("title_" + id + "_name", display);
                    out.put("title_" + id + "_rarity", rarityName);
                    out.put("title_" + id + "_rarity_color", rarityColor);
                    out.put("title_" + id + "_mastery", String.valueOf(mastery));
                    boolean earned = false;
                    if (titleHas != null) {
                        earned = Boolean.TRUE.equals(titleHas.invoke(null, nms, title));
                    }
                    if (earned) {
                        unlocked++;
                    }
                    out.put("title_" + id + "_earned", earned ? "true" : "false");
                    String req = "";
                    if (titleRequirementTip != null) {
                        Object tip = titleRequirementTip.invoke(title);
                        req = tip == null ? "" : String.valueOf(tip);
                    }
                    out.put("title_" + id + "_req", req);
                    try {
                        Object perk = title.getClass().getMethod("perkTip", int.class)
                                .invoke(title, mastery);
                        out.put("title_" + id + "_perk", perk == null ? "" : String.valueOf(perk));
                        if (id.equalsIgnoreCase(out.getOrDefault("active_title_id", ""))) {
                            out.put("title_perk", perk == null ? "" : String.valueOf(perk));
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            out.put("titles_unlocked", String.valueOf(unlocked));
            out.put("titles_total", String.valueOf(total));
            if (out.getOrDefault("title_perk", "").isBlank() && masteryActive >= 0) {
                // leave empty when none equipped
            }
        } catch (Throwable ignored) {
        }
    }

    /** Runs a GUI action through the Forge mod and returns the result message. */
    public static String handleAction(Player player, String action, String arg) {
        return handleActionResult(player, action, arg, null).message;
    }

    /**
     * @param returnPage GUI page to reopen after the action (e.g. {@code adjust}, {@code buy}).
     */
    public static String handleAction(Player player, String action, String arg, String returnPage) {
        return handleActionResult(player, action, arg, returnPage).message;
    }

    /**
     * V3 actions: activate/purchase_tier/buy, down, team, reset, character_reset, page, refresh;
     * optional set (free lower only). Unknown actions fail.
     */
    public static ActionResult handleActionResult(Player player, String action, String arg, String returnPage) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            String detail = resolveError == null || resolveError.isBlank()
                    ? "is LegacyMechanics-*.jar loaded in mods/?"
                    : resolveError;
            return ActionResult.fail("Could not reach LegacyMechanics mod (" + detail + ").");
        }
        try {
            ensureResolved(nms.getClass().getClassLoader());
            String act = action == null ? "" : action.toLowerCase(Locale.ROOT);
            if (!isSupportedAction(act)) {
                return ActionResult.fail("Unknown action.");
            }
            String page = resolveReturnPage(act, arg, returnPage);
            if ("page".equals(act)) {
                page = arg == null || arg.isBlank() ? "main" : arg;
            }
            Object result;
            // Prefer no-reopen: Bukkit already owns inventory reopen after /difficulty do.
            // Avoids Forge DifficultyMenu dumping chat when reflection player resolve flickers.
            if (actionsHandleArgNoReopen != null) {
                result = actionsHandleArgNoReopen.invoke(null, nms, act, arg == null ? "" : arg, page);
            } else if (actionsHandleArg != null) {
                result = actionsHandleArg.invoke(null, nms, act, arg == null ? "" : arg, page);
            } else {
                long amount = 0L;
                if (!("page".equals(act) || "team".equals(act) || "refresh".equals(act)
                        || "reset".equals(act) || "zero".equals(act) || "clear".equals(act)
                        || "character_reset".equals(act) || "char_reset".equals(act)
                        || "characterreset".equals(act))) {
                    if (arg != null && !arg.isBlank()) {
                        try {
                            amount = Long.parseLong(arg);
                        } catch (NumberFormatException ignored) {
                            return ActionResult.fail("Invalid amount: " + arg);
                        }
                    }
                }
                result = actionsHandle.invoke(null, nms, act, amount, page);
            }
            PLACEHOLDER_CACHE.remove(player.getUniqueId());
            if (result == null) {
                return ActionResult.ok("");
            }
            Object msg = readResultMessage(result);
            boolean ok = resultOk == null || Boolean.TRUE.equals(resultOk.invoke(result));
            String text = msg == null ? "" : String.valueOf(msg);
            return new ActionResult(ok, text);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return ActionResult.fail("Action failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage()));
        }
    }

    private static boolean isSupportedAction(String act) {
        return switch (act) {
            case "activate", "purchase_tier", "buy", "lower_tier",
                 "down", "team", "reset", "zero", "clear",
                 "character_reset", "char_reset", "characterreset",
                 "equip_title", "clear_title", "equip", "unequip_title",
                 "toggle_personal", "personal", "toggle_difficulty", "difficulty_toggle",
                 "toggle_coin_chat", "coin_chat", "toggle_chat", "chat_drops",
                 "toggle_title_sense", "title_sense", "toggle_sense", "sense_chat",
                 "summon_end_dragon", "end_dragon", "summon_dragon", "dragon_summon",
                 "page", "refresh", "set" -> true;
            default -> false;
        };
    }

    /**
     * Canonical Forge {@code guiBackend}. CNPC / chat / CMI / chest are retired — always
     * normalize to {@code ultra} so Bukkit never tries to load deleted {@code gui.cnpc}.
     */
    public static String guiBackend() {
        try {
            ensureResolved();
            // Prefer Forge GuiBackend.fromConfig() (always ULTRA; coerces retired values).
            try {
                Object backend = loadClass(
                                "com.dbzlegacy.adaptivedifficulty.gui.GuiBackend", preferredLoader())
                        .getMethod("fromConfig")
                        .invoke(null);
                if (backend != null) {
                    String name = String.valueOf(backend).trim().toLowerCase(Locale.ROOT);
                    if ("ultra".equals(name) || "cnpc".equals(name) || "chat".equals(name)) {
                        return "ultra";
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to raw field / defaults.
            }
            Object cfg = loadClass("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig", preferredLoader())
                    .getMethod("get").invoke(null);
            Object raw = cfg.getClass().getField("guiBackend").get(cfg);
            if (raw == null) {
                return "ultra";
            }
            String v = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
            if (v.isEmpty()) {
                return "ultra";
            }
            return switch (v) {
                case "ultra", "kaipath", "dmzultra", "native" -> "ultra";
                // Retired backends — Ultra only (gui.cnpc package deleted in 4.6.115+).
                case "cnpc", "customnpcs", "customnpc", "noppes",
                        "cmi", "cmilib", "cmigui", "deluxemenus", "deluxe", "dm",
                        "chest", "bukkit", "inventory", "gui",
                        "chat", "auto" -> "ultra";
                default -> "ultra";
            };
        } catch (Throwable t) {
            return "ultra";
        }
    }

    /**
     * @deprecated CNPC menus were removed; redirects to {@link #openUltraLm}.
     */
    @Deprecated
    public static boolean openCnpcLm(Player player, String system, String page) {
        return openUltraLm(player, system, page);
    }

    /** Open Legacy Mechanics Ultra / KaiPath UI (Forge {@code UltraGui}). */
    public static boolean openUltraLm(Player player, String system, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            resolveError = "Could not reach LegacyMechanics Forge player.";
            return false;
        }
        try {
            Class<?> gui = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraGui",
                    nms.getClass().getClassLoader());
            String sys = system == null || system.isBlank() ? "hub" : system.trim();
            String pg = page == null || page.isBlank() ? "main" : page;
            // Legacy CNPC system id for Android Removal → Character Services android page.
            if ("android_remove".equalsIgnoreCase(sys)
                    || "androidremove".equalsIgnoreCase(sys)
                    || "android".equalsIgnoreCase(sys)) {
                sys = "character";
                if (page == null || page.isBlank() || "main".equalsIgnoreCase(page)) {
                    pg = "android_remove";
                }
            }
            gui.getMethod("open", nms.getClass(), String.class, String.class).invoke(null, nms, sys, pg);
            return true;
        } catch (Throwable t) {
            Throwable root = t.getCause() != null ? t.getCause() : t;
            resolveError = root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : ": " + root.getMessage());
            return false;
        }
    }

    /** Forge mod version string, or null if unreachable. */
    public static String modVersion() {
        try {
            ensureResolved();
            Object v = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod", preferredLoader())
                    .getField("VERSION").get(null);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private static ClassLoader preferredLoader() {
        try {
            if (serverPlayerCls != null) {
                return serverPlayerCls.getClassLoader();
            }
        } catch (Throwable ignored) {
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        return ctx != null ? ctx : ForgeBridge.class.getClassLoader();
    }

    public static String adminPermission() {
        try {
            Object cfg = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig")
                    .getMethod("get").invoke(null);
            Object raw = cfg.getClass().getField("adminPermission").get(cfg);
            if (raw == null) {
                return "difficulty.admin";
            }
            String v = String.valueOf(raw).trim();
            return v.isEmpty() ? "difficulty.admin" : v;
        } catch (Throwable t) {
            return "difficulty.admin";
        }
    }

    /** Master Adaptive Difficulty switch ({@code DifficultyConfig.enabled}). */
    public static boolean systemEnabled() {
        try {
            Object on = loadClass("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig", preferredLoader())
                    .getMethod("isEnabled").invoke(null);
            return on instanceof Boolean b && b;
        } catch (Throwable t) {
            try {
                Object cfg = loadClass("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig", preferredLoader())
                        .getMethod("get").invoke(null);
                Object raw = cfg.getClass().getField("enabled").get(cfg);
                return raw instanceof Boolean b && b;
            } catch (Throwable ignored) {
                // Fail closed — UI must not claim the system is on when Forge is unreachable.
                return false;
            }
        }
    }

    public static boolean whitelistEnabled() {
        try {
            Object on = loadClass("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig", preferredLoader())
                    .getMethod("isWhitelistEnabled").invoke(null);
            return on instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether this Bukkit player may use AD right now (master + whitelist). */
    public static boolean playerAllowed(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            // Fail closed when whitelist is on or Forge player resolve failed.
            return systemEnabled() && !whitelistEnabled();
        }
        try {
            ClassLoader cl = nms.getClass().getClassLoader();
            Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", cl);
            Object allowed = loadClass("com.dbzlegacy.adaptivedifficulty.util.SystemGate", cl)
                    .getMethod("allows", sp)
                    .invoke(null, nms);
            return allowed instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Opens Forge clickable chat menu (settings / chat backend). */
    public static boolean openChatMenu(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return false;
        }
        String target = page == null || page.isBlank() ? "main" : page;
        // Staff-only pages must not open for players who only have dmzdiff.gui.
        if (("settings".equalsIgnoreCase(target) || "stats".equalsIgnoreCase(target)
                || "statistics".equalsIgnoreCase(target) || "details".equalsIgnoreCase(target))
                && !isStaff(player)) {
            target = "main";
        }
        try {
            ensureResolved();
            if (chatMenuOpen == null) {
                return false;
            }
            chatMenuOpen.invoke(null, nms, target);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean openRivalChatMenu(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return false;
        }
        try {
            ensureRivalResolved(nms.getClass().getClassLoader());
            if (rivalChatMenuOpen == null) {
                return false;
            }
            rivalChatMenuOpen.invoke(null, nms, page == null || page.isBlank() ? "main" : page);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean openSparChatMenu(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return false;
        }
        try {
            ensureSparResolved(nms.getClass().getClassLoader());
            if (sparChatMenuOpen == null) {
                return false;
            }
            sparChatMenuOpen.invoke(null, nms, page == null || page.isBlank() ? "main" : page);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> rivalPlaceholders(Player player) {
        Map<String, String> fail = new HashMap<>();
        fail.put("bridge_ok", "false");
        fail.put("system_enabled", "false");
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return fail;
        }
        try {
            ensureRivalResolved(nms.getClass().getClassLoader());
            if (rivalPlaceholdersMethod == null) {
                return fail;
            }
            Object raw = rivalPlaceholdersMethod.invoke(null, nms);
            if (raw instanceof Map<?, ?> map) {
                Map<String, String> out = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        out.put(String.valueOf(e.getKey()),
                                e.getValue() == null ? "" : String.valueOf(e.getValue()));
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return fail;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> sparPlaceholders(Player player) {
        Map<String, String> fail = new HashMap<>();
        fail.put("bridge_ok", "false");
        fail.put("system_enabled", "false");
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return fail;
        }
        try {
            ensureSparResolved(nms.getClass().getClassLoader());
            if (sparPlaceholdersMethod == null) {
                return fail;
            }
            Object raw = sparPlaceholdersMethod.invoke(null, nms);
            if (raw instanceof Map<?, ?> map) {
                Map<String, String> out = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        out.put(String.valueOf(e.getKey()),
                                e.getValue() == null ? "" : String.valueOf(e.getValue()));
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return fail;
    }

    @SuppressWarnings("unchecked")
    public static List<String> rivalLines(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of("§cLegacyMechanics mod unreachable.");
        }
        try {
            ensureRivalResolved(nms.getClass().getClassLoader());
            if (rivalLinesMethod == null) {
                return List.of("§cRival GUI API missing — update LegacyMechanics jar.");
            }
            Object raw = rivalLinesMethod.invoke(null, nms, page == null ? "main" : page);
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    out.add(o == null ? "" : String.valueOf(o));
                }
                return out;
            }
        } catch (Throwable t) {
            return List.of("§cRival lines failed: " + t.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<String> sparLines(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of("§cLegacyMechanics mod unreachable.");
        }
        try {
            ensureSparResolved(nms.getClass().getClassLoader());
            if (sparLinesMethod == null) {
                return List.of("§cSpar GUI API missing — update LegacyMechanics jar.");
            }
            Object raw = sparLinesMethod.invoke(null, nms, page == null ? "main" : page);
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    out.add(o == null ? "" : String.valueOf(o));
                }
                return out;
            }
        } catch (Throwable t) {
            return List.of("§cSpar lines failed: " + t.getMessage());
        }
        return List.of();
    }

    public static String rivalHandleDo(Player player, String action, String arg, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureRivalResolved(nms.getClass().getClassLoader());
            if (rivalHandleDoMethod == null) {
                return "§cRival GUI API missing — update LegacyMechanics jar.";
            }
            Object msg = rivalHandleDoMethod.invoke(
                    null, nms, action == null ? "" : action, arg == null ? "" : arg,
                    page == null ? "main" : page);
            return msg == null ? "" : String.valueOf(msg);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cRival action failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /**
     * Pending incoming declare picker args ({@code uuid:&lt;uuid&gt;} when online, else name).
     * Invite-only (Decline). Online declarers first. Empty when none / API missing.
     */
    public static List<String> rivalPendingIncomingDeclareArgs(Player player) {
        return invokeRivalStringList(player, "pendingIncomingDeclareArgs");
    }

    /**
     * Accept picker args: pending invite or Declared (both Silent).
     * {@code uuid:&lt;uuid&gt;} when online, else name. Empty when none / API missing.
     */
    public static List<String> rivalAcceptCandidateArgs(Player player) {
        return invokeRivalStringList(player, "acceptCandidateArgs");
    }

    /** Encoded current-rival cards for head boards (tab-separated fields). */
    public static List<String> rivalCurrentCards(Player player) {
        return invokeRivalStringList(player, "currentRivalCards");
    }

    /** Encoded past-rival cards for history board. */
    public static List<String> rivalPastCards(Player player) {
        return invokeRivalStringList(player, "pastRivalCards");
    }

    /** Encoded pending declare invites (incoming + outgoing). */
    public static List<String> rivalPendingInviteCards(Player player) {
        return invokeRivalStringList(player, "pendingInviteCards");
    }

    /** Encoded pending duel requests (incoming + outgoing). */
    public static List<String> rivalPendingChallengeCards(Player player) {
        return invokeRivalStringList(player, "pendingChallengeCards");
    }

    /** True when Accept is waiting for a Mutual slot replace pick. */
    public static boolean rivalNeedsMutualReplace(Player player) {
        Map<String, String> ph = rivalPlaceholders(player);
        return "true".equalsIgnoreCase(ph.getOrDefault("needs_mutual_replace", "false"));
    }

    /** Current-rival picker args for remove (uuid: preferred). */
    public static List<String> rivalCurrentArgs(Player player) {
        return invokeRivalStringList(player, "currentRivalArgs");
    }

    private static List<String> invokeRivalStringList(Player player, String methodName) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of();
        }
        try {
            ensureRivalResolved(nms.getClass().getClassLoader());
            Method m = switch (methodName) {
                case "pendingIncomingDeclareArgs" -> rivalPendingDeclareArgsMethod;
                case "acceptCandidateArgs" -> rivalAcceptCandidateArgsMethod;
                case "currentRivalCards" -> rivalCurrentCardsMethod;
                case "pastRivalCards" -> rivalPastCardsMethod;
                case "pendingInviteCards" -> rivalPendingInviteCardsMethod;
                case "pendingChallengeCards" -> rivalPendingChallengeCardsMethod;
                case "currentRivalArgs" -> rivalCurrentArgsMethod;
                default -> null;
            };
            if (m == null) {
                return List.of();
            }
            Object raw = m.invoke(null, nms);
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) {
                        String s = String.valueOf(o);
                        if (!s.isBlank()) {
                            out.add(s);
                        }
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
            // Optional API — empty when missing.
        }
        return List.of();
    }

    /** Encoded mutual-rival team cards (tab-separated). */
    public static List<String> diffTeamMutualCards(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of();
        }
        try {
            ensureDiffTeamResolved(nms.getClass().getClassLoader());
            if (diffTeamMutualCardsMethod == null) {
                return List.of();
            }
            Object raw = diffTeamMutualCardsMethod.invoke(null, nms);
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) {
                        String s = String.valueOf(o);
                        if (!s.isBlank()) {
                            out.add(s);
                        }
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    public static List<String> diffTeamLines(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of("§cLegacyMechanics mod unreachable.");
        }
        try {
            ensureDiffTeamResolved(nms.getClass().getClassLoader());
            if (diffTeamLinesMethod == null) {
                return List.of("§cTeams API missing — update LegacyMechanics jar.");
            }
            Object raw = diffTeamLinesMethod.invoke(null, nms, "team");
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    out.add(o == null ? "" : String.valueOf(o));
                }
                return out;
            }
        } catch (Throwable t) {
            return List.of("§cTeams lines failed: " + t.getMessage());
        }
        return List.of();
    }

    public static List<String> endDragonMenuLines(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of("§cLegacyMechanics mod unreachable.");
        }
        try {
            ensureEndDragonResolved(nms.getClass().getClassLoader());
            if (endDragonMenuLinesMethod == null) {
                return List.of("§cEnd Dragon API missing — update LegacyMechanics jar.");
            }
            Object raw = endDragonMenuLinesMethod.invoke(null, nms);
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    out.add(o == null ? "" : String.valueOf(o));
                }
                return out;
            }
        } catch (Throwable t) {
            return List.of("§cEnd Dragon menu failed: " + t.getMessage());
        }
        return List.of();
    }

    public static boolean endDragonCanSummon(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return false;
        }
        try {
            ensureEndDragonResolved(nms.getClass().getClassLoader());
            if (endDragonCanSummonMethod == null) {
                return false;
            }
            Object raw = endDragonCanSummonMethod.invoke(null, nms);
            return Boolean.TRUE.equals(raw);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Encoded pending mentor invites (incoming + outgoing). */
    public static List<String> sparPendingMentorInviteCards(Player player) {
        return invokeSparStringList(player, "pendingMentorInviteCards");
    }

    /** Incoming mentor invite args for Accept/Decline pickers. */
    public static List<String> sparPendingIncomingMentorArgs(Player player) {
        return invokeSparStringList(player, "pendingIncomingMentorArgs");
    }

    public static List<String> sparPendingDojoWarCards(Player player) {
        return invokeSparStringList(player, "pendingDojoWarCards");
    }

    /** Mentor dojo roster cards ({@code uuid\tname}) for Release pickers. */
    public static List<String> sparApprenticeCards(Player player) {
        return invokeSparStringList(player, "apprenticeCards");
    }

    /** Membership dojo cards ({@code role\tuuid\tname}) — mentor + peers. */
    public static List<String> sparMembershipDojoCards(Player player) {
        return invokeSparStringList(player, "membershipDojoCards");
    }

    /** Rival dojo cards for war challenge picker ({@code uuid\tname\trp}). */
    public static List<String> sparRivalDojoCards(Player player) {
        return invokeSparStringList(player, "rivalDojoCards");
    }

    public static List<String> sparDojoMemberCards(Player player) {
        return invokeSparStringList(player, "dojoMemberCards");
    }

    /** Dojo ladder banner cards ({@code rank\tkey\tname\tbanner\tvalue\troster}). */
    public static List<String> sparDojoTopCards(Player player, String category) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of();
        }
        try {
            ensureSparResolved(nms.getClass().getClassLoader());
            if (sparDojoTopCardsMethod == null) {
                return List.of();
            }
            Object raw = sparDojoTopCardsMethod.invoke(
                    null, nms, category == null || category.isBlank() ? "rp" : category);
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) {
                        String s = String.valueOf(o);
                        if (!s.isBlank()) {
                            out.add(s);
                        }
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
            // Optional API — empty when missing.
        }
        return List.of();
    }

    private static List<String> invokeSparStringList(Player player, String methodName) {
        return invokeSparStringList(player, methodName, false);
    }

    private static List<String> invokeSparStringList(Player player, String methodName, boolean retried) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of();
        }
        try {
            ensureSparResolved(nms.getClass().getClassLoader());
            Method m = switch (methodName) {
                case "pendingMentorInviteCards" -> sparPendingMentorInviteCardsMethod;
                case "pendingIncomingMentorArgs" -> sparPendingIncomingMentorArgsMethod;
                case "pendingDojoWarCards" -> sparPendingDojoWarCardsMethod;
                case "apprenticeCards" -> sparApprenticeCardsMethod;
                case "membershipDojoCards" -> sparMembershipDojoCardsMethod;
                case "rivalDojoCards" -> sparRivalDojoCardsMethod;
                case "dojoMemberCards" -> sparDojoMemberCardsMethod;
                default -> null;
            };
            if (m == null) {
                if (!retried && "pendingDojoWarCards".equals(methodName)) {
                    sparPendingDojoWarCardsMethod = null;
                    return invokeSparStringList(player, methodName, true);
                }
                return List.of();
            }
            Object raw = m.invoke(null, nms);
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) {
                        String s = String.valueOf(o);
                        if (!s.isBlank()) {
                            out.add(s);
                        }
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
            if (!retried && "pendingDojoWarCards".equals(methodName)) {
                sparPendingDojoWarCardsMethod = null;
                return invokeSparStringList(player, methodName, true);
            }
        }
        return List.of();
    }

    public static String sparHandleDo(Player player, String action, String arg, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureSparResolved(nms.getClass().getClassLoader());
            if (sparHandleDoMethod == null) {
                return "§cSpar GUI API missing — update LegacyMechanics jar.";
            }
            Object msg = sparHandleDoMethod.invoke(
                    null, nms, action == null ? "" : action, arg == null ? "" : arg,
                    page == null ? "main" : page);
            return msg == null ? "" : String.valueOf(msg);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cSpar action failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    public static boolean openHubChatMenu(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return false;
        }
        try {
            ensureHubResolved(nms.getClass().getClassLoader());
            if (hubChatMenuOpen == null) {
                return false;
            }
            hubChatMenuOpen.invoke(null, nms, page == null || page.isBlank() ? "main" : page);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean openProgressionChatMenu(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return false;
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (progressionChatMenuOpen == null) {
                return false;
            }
            progressionChatMenuOpen.invoke(null, nms, page == null || page.isBlank() ? "main" : page);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> hubPlaceholders(Player player) {
        Map<String, String> fail = new HashMap<>();
        fail.put("bridge_ok", "false");
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return fail;
        }
        try {
            ensureHubResolved(nms.getClass().getClassLoader());
            if (hubPlaceholdersMethod == null) {
                return fail;
            }
            Object raw = hubPlaceholdersMethod.invoke(null, nms);
            return mapStringValues(raw, fail);
        } catch (Throwable ignored) {
        }
        return fail;
    }

    @SuppressWarnings("unchecked")
    public static List<String> hubLines(Player player, String page) {
        return invokeLines(player, page, true, () -> {
            ensureHubResolved(nmsPlayer(player).getClass().getClassLoader());
            return hubLinesMethod;
        }, "Hub");
    }

    public static String hubHandleDo(Player player, String action, String arg, String page) {
        return invokeHandleDo(player, action, arg, page, true, () -> {
            ensureHubResolved(nmsPlayer(player).getClass().getClassLoader());
            return hubHandleDoMethod;
        }, "Hub");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> progressionPlaceholders(Player player) {
        Map<String, String> fail = new HashMap<>();
        fail.put("bridge_ok", "false");
        fail.put("system_enabled", "false");
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return fail;
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (progressionPlaceholdersMethod == null) {
                return fail;
            }
            Object raw = progressionPlaceholdersMethod.invoke(null, nms);
            return mapStringValues(raw, fail);
        } catch (Throwable ignored) {
        }
        return fail;
    }

    @SuppressWarnings("unchecked")
    public static List<String> progressionLines(Player player, String page) {
        return invokeLines(player, page, false, () -> {
            ensureProgressionResolved(nmsPlayer(player).getClass().getClassLoader());
            return progressionLinesMethod;
        }, "Progression");
    }

    public static String progressionHandleDo(Player player, String action, String arg, String page) {
        return invokeHandleDo(player, action, arg, page, false, () -> {
            ensureProgressionResolved(nmsPlayer(player).getClass().getClassLoader());
            return progressionHandleDoMethod;
        }, "Progression");
    }

    /**
     * Staff: Dr. Gero android conversion for self (blank/null target) or an online player name.
     * Prefer this over {@link #forwardCommand} — Mohist often shadows Forge {@code /progression}.
     */
    public static String androidConvert(Player actor, String targetName) {
        Object nms = nmsPlayer(actor);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (androidConvertMethod == null) {
                return "§cAndroid API missing — update LegacyMechanics jar.";
            }
            Object raw = androidConvertMethod.invoke(null, nms, targetName == null ? "" : targetName);
            return raw == null ? "" : String.valueOf(raw);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cAndroid convert failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /**
     * Console / Saga / Fabled — no Bukkit player actor.
     * {@code androidify <player>} or {@code progression android <player>}.
     */
    public static String androidConvertConsole(String targetName) {
        try {
            ensureProgressionResolved(preferredProgressionClassLoader());
            if (androidConvertConsoleMethod != null) {
                Object raw = androidConvertConsoleMethod.invoke(
                        null, targetName == null ? "" : targetName);
                return raw == null ? "" : String.valueOf(raw);
            }
            // Older jars: androidConvert(null, name) after 2.3.64 API.
            if (androidConvertMethod != null) {
                Object raw = androidConvertMethod.invoke(
                        null, null, targetName == null ? "" : targetName);
                return raw == null ? "" : String.valueOf(raw);
            }
            return "§cAndroid console API missing — update LegacyMechanics jar.";
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cAndroid convert failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /** Staff: remove Android upgrade (two-click confirm) for self or online name. */
    public static String androidRemove(Player actor, String targetName) {
        Object nms = nmsPlayer(actor);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (androidRemoveMethod == null) {
                return "§cAndroid remove API missing — update LegacyMechanics jar.";
            }
            Object raw = androidRemoveMethod.invoke(null, nms, targetName == null ? "" : targetName);
            return raw == null ? "" : String.valueOf(raw);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cAndroid remove failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /** Player {@code /terminal} — Lightman's Currency network terminal. */
    public static String openLightmanTerminal(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ClassLoader loader = nms.getClass().getClassLoader();
            Class<?> gate = Class.forName(
                    "com.dbzlegacy.adaptivedifficulty.command.TerminalCommands",
                    true,
                    loader);
            Method allowed = null;
            for (Method cand : gate.getMethods()) {
                if ("allowed".equals(cand.getName()) && cand.getParameterCount() == 1) {
                    allowed = cand;
                    break;
                }
            }
            if (allowed == null) {
                return "§cTerminal API missing — update LegacyMechanics jar.";
            }
            Object ok = allowed.invoke(null, nms);
            if (!(ok instanceof Boolean openOk) || !openOk) {
                return "§cThe terminal is a perk of higher donator ranks. Ask staff about upgrading your rank.";
            }
            Class<?> api = Class.forName(
                    "com.dbzlegacy.adaptivedifficulty.currency.LightmanTerminal",
                    true,
                    loader);
            Method open = null;
            for (Method cand : api.getMethods()) {
                if ("open".equals(cand.getName()) && cand.getParameterCount() == 1) {
                    open = cand;
                    break;
                }
            }
            if (open == null) {
                return "§cTerminal API missing — update LegacyMechanics jar.";
            }
            Object raw = open.invoke(null, nms);
            return raw == null ? "" : String.valueOf(raw);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cCould not open the currency terminal: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /** Staff: End dragon clear/repair via {@code /lmdo enddragon clear|repair}. */
    public static String endDragon(Player actor, String action) {
        Object nms = nmsPlayer(actor);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (endDragonMethod == null) {
                return "§cEnd dragon API missing — update LegacyMechanics jar.";
            }
            Object raw = endDragonMethod.invoke(null, nms, action == null ? "spawn" : action);
            return raw == null ? "" : String.valueOf(raw);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cEnd dragon failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /**
     * Global TP boost — status / start / end.
     * {@code argsJoined} is everything after {@code /progression boost}.
     * {@code actor} may be {@code null} for console / store (Tebex) commands.
     */
    public static String boost(Player actor, String argsJoined) {
        Object nms = actor == null ? null : nmsPlayer(actor);
        if (actor != null && nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ClassLoader cl = nms != null
                    ? nms.getClass().getClassLoader()
                    : preferredProgressionClassLoader();
            ensureProgressionResolved(cl);
            if (boostMethod == null) {
                return "§cBoost API missing — update LegacyMechanics jar.";
            }
            Object raw = boostMethod.invoke(null, nms, argsJoined == null ? "" : argsJoined);
            return raw == null ? "" : String.valueOf(raw);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cBoost failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /** Chat help for the Bukkit {@code /progression} command tree. */
    public static String progressionHelp() {
        return progressionHelp(null);
    }

    /** Chat help; staff/op see the full tree, others are pointed at /lm. */
    public static String progressionHelp(Player player) {
        try {
            Object nms = player == null ? null : nmsPlayer(player);
            ClassLoader cl = nms != null
                    ? nms.getClass().getClassLoader()
                    : preferredProgressionClassLoader();
            ensureProgressionResolved(cl);
            if (progressionHelpForStaffMethod != null) {
                boolean staff = player != null && isStaff(player);
                Object raw = progressionHelpForStaffMethod.invoke(null, staff);
                return raw == null ? "" : String.valueOf(raw);
            }
            if (progressionHelpMethod == null) {
                return "§cProgression help API missing — update LegacyMechanics jar.";
            }
            Object raw = progressionHelpMethod.invoke(null);
            return raw == null ? "" : String.valueOf(raw);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cProgression help failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /** Text flag and boost status summary. */
    public static String progressionStatus() {
        try {
            ClassLoader cl = preferredProgressionClassLoader();
            ensureProgressionResolved(cl);
            if (progressionStatusMethod == null) {
                return "§cProgression status API missing — update LegacyMechanics jar.";
            }
            Object raw = progressionStatusMethod.invoke(null);
            return raw == null ? "" : String.valueOf(raw);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cProgression status failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /** Staff: toggle a progression module flag. */
    public static String progressionAdminFlag(Player actor, String flag, String value) {
        Object nms = nmsPlayer(actor);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (progressionAdminFlagMethod == null) {
                return "§cProgression admin API missing — update LegacyMechanics jar.";
            }
            Object raw = progressionAdminFlagMethod.invoke(null, nms,
                    flag == null ? "" : flag, value == null ? "" : value);
            return raw == null ? "" : String.valueOf(raw);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cProgression admin failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    private static ClassLoader preferredProgressionClassLoader() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            Object nms = nmsPlayer(p);
            if (nms != null) {
                return nms.getClass().getClassLoader();
            }
        }
        return AdaptiveDifficultyGuiPlugin.class.getClassLoader();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> prestigePlaceholders(Player player) {
        Map<String, String> fail = new HashMap<>();
        fail.put("bridge_ok", "false");
        fail.put("system_enabled", "false");
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return fail;
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (prestigePlaceholdersMethod == null) {
                return fail;
            }
            Object raw = prestigePlaceholdersMethod.invoke(null, nms);
            return mapStringValues(raw, fail);
        } catch (Throwable ignored) {
        }
        return fail;
    }

    @SuppressWarnings("unchecked")
    public static List<String> prestigeLines(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of("§cLegacyMechanics mod unreachable.");
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (prestigeLinesMethod == null) {
                return List.of("§cPrestige GUI API missing — update LegacyMechanics jar.");
            }
            Object raw = prestigeLinesMethod.invoke(null, nms, page == null ? "main" : page);
            return listStringValues(raw);
        } catch (Throwable t) {
            return List.of("§cPrestige lines failed: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> charPlaceholders(Player player) {
        Map<String, String> fail = new HashMap<>();
        fail.put("bridge_ok", "false");
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return fail;
        }
        try {
            ensureCharacterServicesResolved(nms.getClass().getClassLoader());
            if (charPlaceholdersMethod == null) {
                return fail;
            }
            Object raw = charPlaceholdersMethod.invoke(null, nms);
            return mapStringValues(raw, fail);
        } catch (Throwable ignored) {
        }
        return fail;
    }

    public static List<String> charLines(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of("§cLegacyMechanics mod unreachable.");
        }
        try {
            ensureCharacterServicesResolved(nms.getClass().getClassLoader());
            if (charLinesMethod == null) {
                return List.of("§cCharacter Services API missing — update LegacyMechanics jar.");
            }
            Object raw = charLinesMethod.invoke(null, nms, page == null ? "main" : page);
            return listStringValues(raw);
        } catch (Throwable t) {
            return List.of("§cCharacter lines failed: " + t.getMessage());
        }
    }

    public static List<String> charRaceCards(Player player) {
        return invokeCharStringList(player, "raceCards");
    }

    public static List<String> charClassCards(Player player) {
        return invokeCharStringList(player, "classCards");
    }

    public static List<String> charHeadBoneCards(Player player, int page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of();
        }
        try {
            ensureCharacterServicesResolved(nms.getClass().getClassLoader());
            if (charHeadBoneCardsMethod == null) {
                return List.of();
            }
            Object raw = charHeadBoneCardsMethod.invoke(null, nms, page);
            return listStringValues(raw);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static String charHandleDo(Player player, String action, String arg, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureCharacterServicesResolved(nms.getClass().getClassLoader());
            if (charHandleDoMethod == null) {
                return "§cCharacter Services API missing — update LegacyMechanics jar.";
            }
            Object msg = charHandleDoMethod.invoke(
                    null, nms, action == null ? "" : action, arg == null ? "" : arg,
                    page == null ? "main" : page);
            return msg == null ? "" : String.valueOf(msg);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cCharacter action failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    private static List<String> invokeCharStringList(Player player, String methodName) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of();
        }
        try {
            ensureCharacterServicesResolved(nms.getClass().getClassLoader());
            Method m = "raceCards".equals(methodName) ? charRaceCardsMethod : charClassCardsMethod;
            if (m == null) {
                return List.of();
            }
            Object raw = m.invoke(null, nms);
            return listStringValues(raw);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static synchronized void ensureCharacterServicesResolved(ClassLoader preferred) throws Exception {
        if (charPlaceholdersMethod != null && charLinesMethod != null && charHandleDoMethod != null
                && charHeadBoneCardsMethod != null) {
            return;
        }
        Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.CharacterServicesGuiApi", preferred);
        Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
        charPlaceholdersMethod = api.getMethod("placeholders", sp);
        charLinesMethod = api.getMethod("linesForPage", sp, String.class);
        charHandleDoMethod = api.getMethod("handleDo", sp, String.class, String.class, String.class);
        charRaceCardsMethod = api.getMethod("raceCards", sp);
        charClassCardsMethod = api.getMethod("classCards", sp);
        charHeadBoneCardsMethod = api.getMethod("headBoneCards", sp, int.class);
    }

    public static String prestigeHandleDo(Player player, String action, String arg, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (prestigeHandleDoMethod == null) {
                return "§cPrestige GUI API missing — update LegacyMechanics jar.";
            }
            Object msg = prestigeHandleDoMethod.invoke(
                    null, nms, action == null ? "" : action, arg == null ? "" : arg,
                    page == null ? "main" : page);
            return msg == null ? "" : String.valueOf(msg);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cPrestige action failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /**
     * Staff {@code /prestige admin …} via reflection — Mohist often cannot forward
     * this to Forge brigadier ({@code forwardCommand} fails).
     *
     * @param rawArgs text after {@code admin} (blank = help)
     */
    public static String prestigeAdmin(Player actor, String rawArgs) {
        try {
            Object nms = actor == null ? null : nmsPlayer(actor);
            if (actor != null && nms == null) {
                return "§cCould not reach LegacyMechanics mod.";
            }
            ClassLoader preferred = nms != null
                    ? nms.getClass().getClassLoader()
                    : preferredProgressionClassLoader();
            ensureProgressionResolved(preferred);
            if (prestigeAdminMethod == null) {
                resolvePrestigeAdminMethod(preferred);
            }
            if (prestigeAdminMethod == null) {
                return "§cPrestige admin API missing — update LegacyMechanics jar to 2.3.100+"
                        + (resolveError == null || resolveError.isBlank() ? "" : "\n§8" + resolveError);
            }
            Object msg = prestigeAdminMethod.invoke(null, nms, rawArgs == null ? "" : rawArgs);
            return msg == null ? "" : String.valueOf(msg);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cPrestige admin failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    private static void resolvePrestigeAdminMethod(ClassLoader preferred) {
        try {
            Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi", preferred);
            Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
            try {
                prestigeAdminMethod = api.getMethod("handlePrestigeAdmin", sp, String.class);
                return;
            } catch (NoSuchMethodException ignored) {
            }
            // Fallback: scan in case classloader ServerPlayer identity differs.
            for (Method m : api.getMethods()) {
                if (!"handlePrestigeAdmin".equals(m.getName()) || m.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] pts = m.getParameterTypes();
                if (pts[1] == String.class
                        && pts[0].getName().endsWith("ServerPlayer")) {
                    prestigeAdminMethod = m;
                    return;
                }
            }
            resolveError = "handlePrestigeAdmin not found on ProgressionGuiApi";
            prestigeAdminMethod = null;
        } catch (Throwable t) {
            resolveError = "resolve prestigeAdmin: " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " — " + t.getMessage());
            prestigeAdminMethod = null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> skillsPlaceholders(Player player) {
        Map<String, String> fail = new HashMap<>();
        fail.put("bridge_ok", "false");
        fail.put("system_enabled", "false");
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return fail;
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (skillsPlaceholdersMethod == null) {
                return fail;
            }
            Object raw = skillsPlaceholdersMethod.invoke(null, nms);
            return mapStringValues(raw, fail);
        } catch (Throwable ignored) {
        }
        return fail;
    }

    @SuppressWarnings("unchecked")
    public static List<String> skillsLines(Player player, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of("§cLegacyMechanics mod unreachable.");
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (skillsLinesMethod == null) {
                return List.of("§cSkills GUI API missing — update LegacyMechanics jar.");
            }
            Object raw = skillsLinesMethod.invoke(null, nms, page == null ? "core" : page);
            return listStringValues(raw);
        } catch (Throwable t) {
            return List.of("§cSkills lines failed: " + t.getMessage());
        }
    }

    public static String skillsHandleDo(Player player, String action, String arg, String page) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            ensureProgressionResolved(nms.getClass().getClassLoader());
            if (skillsHandleDoMethod == null) {
                return "§cSkills GUI API missing — update LegacyMechanics jar.";
            }
            Object msg = skillsHandleDoMethod.invoke(
                    null, nms, action == null ? "" : action, arg == null ? "" : arg,
                    page == null ? "core" : page);
            return msg == null ? "" : String.valueOf(msg);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§cSkills action failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    @FunctionalInterface
    private interface MethodSupplier {
        Method get() throws Exception;
    }

    private static Map<String, String> mapStringValues(Object raw, Map<String, String> fail) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> out = new HashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()),
                            e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }
            return out;
        }
        return fail;
    }

    private static List<String> listStringValues(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                out.add(o == null ? "" : String.valueOf(o));
            }
            return out;
        }
        return List.of();
    }

    private static List<String> invokeLines(
            Player player, String page, boolean hubDefault, MethodSupplier methodGet, String label) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return List.of("§cLegacyMechanics mod unreachable.");
        }
        try {
            Method m = methodGet.get();
            if (m == null) {
                return List.of("§c" + label + " GUI API missing — update LegacyMechanics jar.");
            }
            Object raw = m.invoke(null, nms, page == null ? (hubDefault ? "main" : "main") : page);
            return listStringValues(raw);
        } catch (Throwable t) {
            return List.of("§c" + label + " lines failed: " + t.getMessage());
        }
    }

    private static String invokeHandleDo(
            Player player, String action, String arg, String page,
            boolean hubDefault, MethodSupplier methodGet, String label) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach LegacyMechanics mod.";
        }
        try {
            Method m = methodGet.get();
            if (m == null) {
                return "§c" + label + " GUI API missing — update LegacyMechanics jar.";
            }
            Object msg = m.invoke(
                    null, nms, action == null ? "" : action, arg == null ? "" : arg,
                    page == null ? "main" : page);
            return msg == null ? "" : String.valueOf(msg);
        } catch (Throwable t) {
            Throwable root = t.getCause() == null ? t : t.getCause();
            return "§c" + label + " action failed: " + root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : " — " + root.getMessage());
        }
    }

    /**
     * Execute a command line through Forge brigadier (bypasses Bukkit command ownership).
     * Used when this plugin owns {@code /rival}/{@code /spar} but needs declare/challenge/etc.
     */
    public static boolean forwardCommand(Player player, String commandLine) {
        if (player == null || commandLine == null || commandLine.isBlank()) {
            return false;
        }
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return false;
        }
        try {
            Object source = null;
            for (Method m : nms.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().endsWith("CommandSourceStack")) {
                    source = m.invoke(nms);
                    break;
                }
            }
            if (source == null) {
                return false;
            }
            Object server = null;
            for (Method m : nms.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().endsWith("MinecraftServer")) {
                    server = m.invoke(nms);
                    break;
                }
            }
            if (server == null) {
                return false;
            }
            Object commands = null;
            for (Method m : server.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().endsWith("Commands")) {
                    commands = m.invoke(server);
                    break;
                }
            }
            if (commands == null) {
                return false;
            }
            Object dispatcher = null;
            for (Method m : commands.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().contains("CommandDispatcher")) {
                    dispatcher = m.invoke(commands);
                    break;
                }
            }
            if (dispatcher == null) {
                return false;
            }
            Method execute = null;
            for (Method m : dispatcher.getClass().getMethods()) {
                if (!"execute".equals(m.getName()) || m.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p[0] == String.class) {
                    execute = m;
                    break;
                }
            }
            if (execute == null) {
                return false;
            }
            execute.invoke(dispatcher, line, source);
            return true;
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    private static synchronized void ensureDiffTeamResolved(ClassLoader preferred) throws Exception {
        if (diffTeamLinesMethod != null && diffTeamMutualCardsMethod != null) {
            return;
        }
        Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.DifficultyTeamGuiApi", preferred);
        Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
        diffTeamLinesMethod = api.getMethod("linesForPage", sp, String.class);
        diffTeamMutualCardsMethod = api.getMethod("mutualRivalCards", sp);
    }

    private static synchronized void ensureEndDragonResolved(ClassLoader preferred) throws Exception {
        if (endDragonMenuLinesMethod != null && endDragonCanSummonMethod != null) {
            return;
        }
        Class<?> end = loadClass("com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength", preferred);
        Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
        endDragonMenuLinesMethod = end.getMethod("summonMenuLines", sp);
        endDragonCanSummonMethod = end.getMethod("canOpenSummonMenu", sp);
    }

    private static void putTeamScalingPlaceholders(Map<String, String> out, Object nms) {
        out.put("team_name", "none");
        out.put("team_source", "Rival Mutual");
        out.put("team_size", "0");
        out.put("mutual_total", "0");
        out.put("mutual_online", "0");
        if (nms == null) {
            return;
        }
        try {
            ClassLoader cl = nms.getClass().getClassLoader();
            Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", cl);
            Class<?> ts = loadClass("com.dbzlegacy.adaptivedifficulty.team.TeamScaling", cl);
            Object name = ts.getMethod("teamName", sp).invoke(null, nms);
            Object source = ts.getMethod("teamSourceLabel").invoke(null);
            Object mates = ts.getMethod("teammates", sp).invoke(null, nms);
            Object onlineMutual = ts.getMethod("onlineMutualRivalCount", sp).invoke(null, nms);
            Object mutual = ts.getMethod("mutualRivalCount", sp).invoke(null, nms);
            Object prox = ts.getMethod("contributionProximityBlocks").invoke(null);
            int teaming = mates instanceof List<?> list ? list.size() : 0;
            int online = onlineMutual instanceof Number n ? n.intValue() : 0;
            out.put("team_name", name == null ? "none" : String.valueOf(name));
            out.put("team_source", source == null ? "Rival Mutual" : String.valueOf(source));
            out.put("team_size", String.valueOf(teaming));
            out.put("mutual_total", mutual == null ? "0" : String.valueOf(mutual));
            out.put("mutual_online", String.valueOf(online));
            out.put("mutual_teaming", String.valueOf(teaming));
            out.put("proximity_blocks", prox == null ? "48" : String.valueOf((int) Math.round(((Number) prox).doubleValue())));
            try {
                Class<?> cfg = loadClass("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig", cl);
                Object cfgInst = cfg.getMethod("get").invoke(null);
                Object bonus = cfg.getField("teamBonusPercent").get(cfgInst);
                Object contrib = cfg.getField("contributionPercent").get(cfgInst);
                out.put("bonus_percent", bonus == null ? "10" : String.valueOf((int) Math.round(((Number) bonus).doubleValue())));
                out.put("contrib_percent", contrib == null ? "25" : String.valueOf((int) Math.round(((Number) contrib).doubleValue())));
            } catch (Throwable ignoredCfg) {
                out.put("bonus_percent", "10");
                out.put("contrib_percent", "25");
            }
        } catch (Throwable ignored) {
        }
    }

    private static String teamModeLabel(String modeKey) {
        if (modeKey == null) {
            return "Personal";
        }
        return switch (modeKey.toLowerCase(Locale.ROOT)) {
            case "threshold_bonus_only" -> "Threshold";
            case "full_team_scaling" -> "Full";
            default -> "Personal";
        };
    }

    private static synchronized void ensureRivalResolved(ClassLoader preferred) throws Exception {
        if (rivalPlaceholdersMethod != null && rivalLinesMethod != null && rivalHandleDoMethod != null) {
            resolveOptionalRivalMethods(preferred);
            return;
        }
        Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi", preferred);
        Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
        rivalPlaceholdersMethod = api.getMethod("placeholders", sp);
        rivalLinesMethod = api.getMethod("linesForPage", sp, String.class);
        rivalHandleDoMethod = api.getMethod("handleDo", sp, String.class, String.class, String.class);
        resolveOptionalRivalMethods(preferred);
        try {
            rivalChatMenuOpen = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.gui.RivalChatMenu", preferred)
                    .getMethod("open", sp, String.class);
        } catch (Throwable missing) {
            rivalChatMenuOpen = null;
        }
    }

    private static void resolveOptionalRivalMethods(ClassLoader preferred) {
        try {
            Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi", preferred);
            Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
            if (rivalPendingDeclareArgsMethod == null) {
                try {
                    rivalPendingDeclareArgsMethod = api.getMethod("pendingIncomingDeclareArgs", sp);
                } catch (Throwable ignored) {
                    rivalPendingDeclareArgsMethod = null;
                }
            }
            if (rivalAcceptCandidateArgsMethod == null) {
                try {
                    rivalAcceptCandidateArgsMethod = api.getMethod("acceptCandidateArgs", sp);
                } catch (Throwable ignored) {
                    rivalAcceptCandidateArgsMethod = null;
                }
            }
            if (rivalCurrentCardsMethod == null) {
                try {
                    rivalCurrentCardsMethod = api.getMethod("currentRivalCards", sp);
                } catch (Throwable ignored) {
                    rivalCurrentCardsMethod = null;
                }
            }
            if (rivalPastCardsMethod == null) {
                try {
                    rivalPastCardsMethod = api.getMethod("pastRivalCards", sp);
                } catch (Throwable ignored) {
                    rivalPastCardsMethod = null;
                }
            }
            if (rivalPendingInviteCardsMethod == null) {
                try {
                    rivalPendingInviteCardsMethod = api.getMethod("pendingInviteCards", sp);
                } catch (Throwable ignored) {
                    rivalPendingInviteCardsMethod = null;
                }
            }
            if (rivalPendingChallengeCardsMethod == null) {
                try {
                    rivalPendingChallengeCardsMethod = api.getMethod("pendingChallengeCards", sp);
                } catch (Throwable ignored) {
                    rivalPendingChallengeCardsMethod = null;
                }
            }
            if (rivalCurrentArgsMethod == null) {
                try {
                    rivalCurrentArgsMethod = api.getMethod("currentRivalArgs", sp);
                } catch (Throwable ignored) {
                    rivalCurrentArgsMethod = null;
                }
            }
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    private static synchronized void ensureSparResolved(ClassLoader preferred) throws Exception {
        if (sparPlaceholdersMethod != null && sparLinesMethod != null && sparHandleDoMethod != null) {
            resolveOptionalSparMethods(preferred);
            return;
        }
        Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi", preferred);
        Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
        sparPlaceholdersMethod = api.getMethod("placeholders", sp);
        sparLinesMethod = api.getMethod("linesForPage", sp, String.class);
        sparHandleDoMethod = api.getMethod("handleDo", sp, String.class, String.class, String.class);
        try {
            sparChatMenuOpen = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.gui.SparChatMenu", preferred)
                    .getMethod("open", sp, String.class);
        } catch (Throwable missing) {
            sparChatMenuOpen = null;
        }
        resolveOptionalSparMethods(preferred);
    }

    private static void resolveOptionalSparMethods(ClassLoader preferred) {
        try {
            Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi", preferred);
            Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
            if (sparPendingMentorInviteCardsMethod == null) {
                try {
                    sparPendingMentorInviteCardsMethod = api.getMethod("pendingMentorInviteCards", sp);
                } catch (Throwable ignored) {
                    sparPendingMentorInviteCardsMethod = null;
                }
            }
            if (sparPendingIncomingMentorArgsMethod == null) {
                try {
                    sparPendingIncomingMentorArgsMethod = api.getMethod("pendingIncomingMentorArgs", sp);
                } catch (Throwable ignored) {
                    sparPendingIncomingMentorArgsMethod = null;
                }
            }
            if (sparPendingDojoWarCardsMethod == null) {
                try {
                    sparPendingDojoWarCardsMethod = api.getMethod("pendingDojoWarCards", sp);
                } catch (Throwable ignored) {
                    sparPendingDojoWarCardsMethod = null;
                }
            }
            if (sparApprenticeCardsMethod == null) {
                try {
                    sparApprenticeCardsMethod = api.getMethod("apprenticeCards", sp);
                } catch (Throwable ignored) {
                    sparApprenticeCardsMethod = null;
                }
            }
            if (sparMembershipDojoCardsMethod == null) {
                try {
                    sparMembershipDojoCardsMethod = api.getMethod("membershipDojoCards", sp);
                } catch (Throwable ignored) {
                    sparMembershipDojoCardsMethod = null;
                }
            }
            if (sparRivalDojoCardsMethod == null) {
                try {
                    sparRivalDojoCardsMethod = api.getMethod("rivalDojoCards", sp);
                } catch (Throwable ignored) {
                    sparRivalDojoCardsMethod = null;
                }
            }
            if (sparDojoMemberCardsMethod == null) {
                try {
                    sparDojoMemberCardsMethod = api.getMethod("dojoMemberCards", sp);
                } catch (Throwable ignored) {
                    sparDojoMemberCardsMethod = null;
                }
            }
            if (sparDojoTopCardsMethod == null) {
                try {
                    sparDojoTopCardsMethod = api.getMethod("dojoTopCards", sp, String.class);
                } catch (Throwable ignored) {
                    sparDojoTopCardsMethod = null;
                }
            }
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    private static synchronized void ensureHubResolved(ClassLoader preferred) throws Exception {
        if (hubPlaceholdersMethod != null && hubLinesMethod != null && hubHandleDoMethod != null) {
            return;
        }
        Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.MechanicsGuiApi", preferred);
        Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
        hubPlaceholdersMethod = api.getMethod("placeholders", sp);
        hubLinesMethod = api.getMethod("linesForPage", sp, String.class);
        hubHandleDoMethod = api.getMethod("handleDo", sp, String.class, String.class, String.class);
        try {
            hubChatMenuOpen = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.gui.MechanicsChatMenu", preferred)
                    .getMethod("open", sp, String.class);
        } catch (Throwable missing) {
            hubChatMenuOpen = null;
        }
    }

    private static synchronized void ensureProgressionResolved(ClassLoader preferred) throws Exception {
        Class<?> api = loadClass("com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi", preferred);
        Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
        if (progressionPlaceholdersMethod == null || progressionLinesMethod == null
                || progressionHandleDoMethod == null
                || prestigePlaceholdersMethod == null || prestigeLinesMethod == null
                || prestigeHandleDoMethod == null
                || skillsPlaceholdersMethod == null || skillsLinesMethod == null
                || skillsHandleDoMethod == null) {
            progressionPlaceholdersMethod = api.getMethod("placeholders", sp);
            progressionLinesMethod = api.getMethod("linesForPage", sp, String.class);
            progressionHandleDoMethod = api.getMethod("handleDo", sp, String.class, String.class, String.class);
            prestigePlaceholdersMethod = api.getMethod("prestigePlaceholders", sp);
            prestigeLinesMethod = api.getMethod("prestigeLines", sp, String.class);
            prestigeHandleDoMethod = api.getMethod("handlePrestigeDo", sp, String.class, String.class, String.class);
            skillsPlaceholdersMethod = api.getMethod("skillsPlaceholders", sp);
            skillsLinesMethod = api.getMethod("skillsLines", sp, String.class);
            skillsHandleDoMethod = api.getMethod("handleSkillsDo", sp, String.class, String.class, String.class);
            try {
                progressionChatMenuOpen = loadClass(
                        "com.dbzlegacy.adaptivedifficulty.gui.ProgressionChatMenu", preferred)
                        .getMethod("open", sp, String.class);
            } catch (Throwable missing) {
                progressionChatMenuOpen = null;
            }
        }
        // Resolve admin API independently so older jars still load the rest.
        if (prestigeAdminMethod == null) {
            resolvePrestigeAdminMethod(preferred);
        }
        if (androidConvertMethod == null) {
            try {
                androidConvertMethod = api.getMethod("androidConvert", sp, String.class);
            } catch (Throwable missing) {
                androidConvertMethod = null;
            }
        }
        if (androidConvertConsoleMethod == null) {
            try {
                androidConvertConsoleMethod = api.getMethod("androidConvertConsole", String.class);
            } catch (Throwable missing) {
                androidConvertConsoleMethod = null;
            }
        }
        if (androidRemoveMethod == null) {
            try {
                androidRemoveMethod = api.getMethod("androidRemove", sp, String.class);
            } catch (Throwable missing) {
                androidRemoveMethod = null;
            }
        }
        if (endDragonMethod == null) {
            try {
                endDragonMethod = api.getMethod("endDragon", sp, String.class);
            } catch (Throwable missing) {
                endDragonMethod = null;
            }
        }
        if (boostMethod == null) {
            try {
                boostMethod = api.getMethod("boost", sp, String.class);
            } catch (Throwable missing) {
                boostMethod = null;
            }
        }
        if (progressionHelpMethod == null) {
            try {
                progressionHelpMethod = api.getMethod("commandHelp");
            } catch (Throwable missing) {
                progressionHelpMethod = null;
            }
        }
        if (progressionHelpForStaffMethod == null) {
            try {
                progressionHelpForStaffMethod = api.getMethod("commandHelp", boolean.class);
            } catch (Throwable missing) {
                progressionHelpForStaffMethod = null;
            }
        }
        if (progressionStatusMethod == null) {
            try {
                progressionStatusMethod = api.getMethod("statusText");
            } catch (Throwable missing) {
                progressionStatusMethod = null;
            }
        }
        if (progressionAdminFlagMethod == null) {
            try {
                progressionAdminFlagMethod = api.getMethod("adminFlag", sp, String.class, String.class);
            } catch (Throwable missing) {
                progressionAdminFlagMethod = null;
            }
        }
    }

    public record ActionResult(boolean ok, String message) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, message == null ? "" : message);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message == null ? "" : message);
        }
    }

    /** Default page to reopen for an action when none is supplied. */
    public static String resolveReturnPage(String action, String arg, String explicitPage) {
        if (explicitPage != null && !explicitPage.isBlank()) {
            return explicitPage.toLowerCase(Locale.ROOT);
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT);
        return switch (act) {
            case "page" -> arg == null || arg.isBlank() ? "main" : arg.toLowerCase(Locale.ROOT);
            case "down", "reset", "zero", "clear", "set", "lower_tier",
                 "buy", "activate", "purchase_tier" -> "tiers";
            case "equip_title", "clear_title", "equip", "unequip_title" -> "titles";
            case "team" -> "team";
            case "toggle_personal", "personal", "toggle_difficulty", "difficulty_toggle",
                 "toggle_coin_chat", "coin_chat", "toggle_chat", "chat_drops" -> "main";
            default -> "main";
        };
    }

    public static boolean forgeAvailable() {
        try {
            loadClass("com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod", null);
            return true;
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    /** Last reflection/classloader failure detail (for logs / player messages). */
    public static String lastError() {
        return resolveError == null ? "" : resolveError;
    }

    public static boolean isStaff(Player player) {
        if (player == null) {
            return false;
        }
        // Align with Forge StaffAccess: op or configured adminPermission only.
        // Do not treat permission("*") as staff — that widens settings/admin set to anyone
        // whose permission plugin grants a wildcard node by default.
        return player.isOp() || player.hasPermission(adminPermission());
    }

    private static boolean bypassAncientCoinCost(Object nmsPlayer) {
        if (nmsPlayer == null) {
            return false;
        }
        try {
            ensureResolved();
            if (paidFeatureBypassCost != null) {
                Object ok = paidFeatureBypassCost.invoke(null, nmsPlayer);
                return ok instanceof Boolean b && b;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean staffFreeAncientCoinCosts() {
        try {
            Object cfg = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig")
                    .getMethod("get").invoke(null);
            Object raw = cfg.getClass().getField("staffFreeAncientCoinCosts").get(cfg);
            return raw instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Donator Skill Check: {@code legacymechanics.donator} (config {@code donatorPermission}). Staff are not
     * auto-granted — without the node they use {@code /skills} and do not see Skill Check in hub.
     */
    public static boolean hasSkillCheck(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Object cfg = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig")
                    .getMethod("get").invoke(null);
            Object enabled = cfg.getClass().getField("enableSkillCheck").get(cfg);
            if (enabled instanceof Boolean b && !b) {
                return false;
            }
            Object skillsOn = cfg.getClass().getField("enableSkillUnlockService").get(cfg);
            if (skillsOn instanceof Boolean b && !b) {
                return false;
            }
            Object raw = cfg.getClass().getField("donatorPermission").get(cfg);
            String node = raw == null ? "legacymechanics.donator" : String.valueOf(raw).trim();
            if (node.isEmpty()) {
                node = "legacymechanics.donator";
            }
            return player.hasPermission(node);
        } catch (Throwable t) {
            return player.hasPermission("legacymechanics.donator");
        }
    }

    /** True while Forge marked a Skill Check UI session for this player. */
    public static boolean inSkillCheckSession(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Class<?> cls = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService",
                    preferredLoader());
            Object ok = cls.getMethod("inSession", java.util.UUID.class)
                    .invoke(null, player.getUniqueId());
            return ok instanceof Boolean b && b;
        } catch (Throwable t) {
            Map<String, String> ph = hubPlaceholders(player);
            return "true".equalsIgnoreCase(ph.getOrDefault("skillcheck_session", "false"));
        }
    }

    public static void markSkillCheckSession(Player player) {
        if (player == null) {
            return;
        }
        try {
            Object nms = nmsPlayer(player);
            if (nms == null) {
                return;
            }
            Class<?> sp = loadClass("net.minecraft.server.level.ServerPlayer", preferredLoader());
            Class<?> cls = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService",
                    preferredLoader());
            cls.getMethod("markSession", sp).invoke(null, nms);
        } catch (Throwable ignored) {
        }
    }

    public static String adminSet(String key, String value) {
        try {
            String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
            // Prefer dedicated setters so caches clear correctly.
            if ("enabled".equals(k) || "system".equals(k) || "systemenabled".equals(k)) {
                boolean on = Boolean.parseBoolean(value)
                        || "on".equalsIgnoreCase(value)
                        || "true".equalsIgnoreCase(value);
                return setSystemEnabled(on);
            }
            if ("whitelistenabled".equals(k) || "whitelist".equals(k)) {
                if (!("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                        || "on".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value))) {
                    return "Use true/false, or: /difficulty admin whitelist add <player>";
                }
                boolean on = "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
                return setWhitelistEnabled(on);
            }
            if ("balancetelemetryenabled".equals(k) || "telemetry".equals(k) || "telemetryenabled".equals(k)) {
                if (!("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                        || "on".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value))) {
                    return "Use true/false, or: /difficulty admin telemetry on|off";
                }
                boolean on = "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
                return setTelemetryEnabled(on);
            }
            if ("stafffreeancientcoincosts".equals(k) || "stafffreecoins".equals(k) || "stafffree".equals(k)) {
                if (!("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                        || "on".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value))) {
                    return "Use true/false, or: /difficulty admin stafffree on|off";
                }
                boolean on = "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
                return setStaffFreeAncientCoinCosts(on);
            }
            Class<?> cfgCls = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig");
            Object cfg = cfgCls.getMethod("get").invoke(null);
            Field field = findConfigField(cfgCls, key);
            if (field == null) {
                return "Unknown key: " + key;
            }
            // Block mutating collections / migration flags via raw reflection.
            if (isLegacyUnusedCounterField(field.getName())) {
                return key + " is unused as of 3.3.35 (class + top-stat counters only)";
            }
            if (!isSafeAdminField(field.getName())) {
                return "Key not settable here: " + key + " (use a dedicated admin command)";
            }
            Object parsed = coerce(field.getType(), value);
            // Pre-clamp obvious foot-guns before assign.
            parsed = clampAdminValue(field.getName(), parsed);
            field.setAccessible(true);
            field.set(cfg, parsed);
            // Re-run config normalize so maxScaledMobs / intervals / costs stay valid.
            try {
                cfgCls.getMethod("sanitizeLive").invoke(null);
            } catch (NoSuchMethodException ignored) {
            }
            cfgCls.getMethod("save").invoke(null);
            Class.forName("com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache")
                    .getMethod("invalidateAll").invoke(null);
            clearAreaCache();
            PLACEHOLDER_CACHE.clear();
            Object live = field.get(cfg);
            return "Set " + key + " = " + live;
        } catch (Throwable t) {
            return "Failed: " + t.getMessage();
        }
    }

    public static String setSystemEnabled(boolean on) {
        try {
            Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig")
                    .getMethod("setEnabled", boolean.class)
                    .invoke(null, on);
            Class.forName("com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache")
                    .getMethod("invalidateAll").invoke(null);
            if (!on) {
                try {
                    Class.forName("com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler")
                            .getMethod("shutdownAllScaling")
                            .invoke(null);
                } catch (Throwable ignored) {
                }
            }
            clearAreaCache();
            PLACEHOLDER_CACHE.clear();
            return on
                    ? "§aAdaptive Difficulty ENABLED.\n§7Scaling, rewards, AI, and tier purchases are active again."
                    : "§cAdaptive Difficulty DISABLED.\n§7No scaling, kill coins, AI, or tier purchases until re-enabled.\n§eRe-enable: §f/difficulty admin on";
        } catch (Throwable t) {
            return "§cFailed to toggle system: " + t.getMessage();
        }
    }

    public static String setWhitelistEnabled(boolean on) {
        try {
            Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig")
                    .getMethod("setWhitelistEnabled", boolean.class)
                    .invoke(null, on);
            Class.forName("com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache")
                    .getMethod("invalidateAll").invoke(null);
            clearAreaCache();
            PLACEHOLDER_CACHE.clear();
            int n = whitelistEntries().size();
            return on
                    ? "§eWhitelist ENABLED §7(" + n + " entries).\n§7Only listed players use Adaptive Difficulty.\n§eAdd: §f/difficulty admin whitelist add <player>"
                    : "§aWhitelist DISABLED.\n§7All players may use Adaptive Difficulty again (if system is on).";
        } catch (Throwable t) {
            return "§cFailed to toggle whitelist: " + t.getMessage();
        }
    }

    public static String systemStatusText() {
        boolean on = systemEnabled();
        boolean wl = whitelistEnabled();
        boolean staffFree = staffFreeAncientCoinCosts();
        int n = whitelistEntries().size();
        return (on ? "§aSystem ENABLED" : "§cSystem DISABLED")
                + " §8· "
                + (wl ? "§eWhitelist ON §7(" + n + " entries)" : "§7Whitelist OFF")
                + " §8· "
                + (staffFree ? "§aStaff coin bypass ON" : "§7Staff coin bypass OFF")
                + "\n§8/difficulty admin whitelist on|off|add|remove|list"
                + "\n§8/difficulty admin stafffree on|off";
    }

    public static boolean staffFreeAncientCoinCostsEnabled() {
        return staffFreeAncientCoinCosts();
    }

    public static String setStaffFreeAncientCoinCosts(boolean on) {
        try {
            Class<?> cfgCls = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig");
            Object cfg = cfgCls.getMethod("get").invoke(null);
            cfg.getClass().getField("staffFreeAncientCoinCosts").set(cfg, on);
            cfgCls.getMethod("save").invoke(null);
            PLACEHOLDER_CACHE.clear();
            return (on ? "§aStaff free Ancient Coin costs ON" : "§eStaff free Ancient Coin costs OFF")
                    + "\n§7Tier buys, Character Services, End dragon summon, etc."
                    + (on
                    ? "\n§7Staff/OP pay §fno coins§7 when this is on."
                    : "\n§7Staff/OP pay §fnormal prices§7 unless they hold a non-op bypass permission.")
                    + "\n§8GUI: /lm → Progression → Ancient Coins · /difficulty admin stafffree on|off";
        } catch (Throwable t) {
            return "§cFailed to toggle staff free coins: " + t.getMessage();
        }
    }

    public static String staffFreeAncientCoinCostsStatusText() {
        boolean on = staffFreeAncientCoinCosts();
        return "§6Staff free Ancient Coin costs: " + (on ? "§aON" : "§eOFF")
                + "\n§7When ON, ops/staff skip coin charges on LM paid features."
                + "\n§8/difficulty admin stafffree on|off|toggle"
                + "\n§8/lm → Progression → Ancient Coins — click the dye toggle";
    }

    public static String whitelistStatusText() {
        boolean wl = whitelistEnabled();
        int n = whitelistEntries().size();
        return (wl ? "§eWhitelist ON" : "§7Whitelist OFF")
                + " §8· §f" + n + " §7entries\n"
                + "§8/difficulty admin whitelist add|remove|list|on|off|toggle|clear";
    }

    @SuppressWarnings("unchecked")
    public static List<String> whitelistEntries() {
        try {
            Object list = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig")
                    .getMethod("whitelistEntries").invoke(null);
            if (list instanceof List<?> raw) {
                List<String> out = new ArrayList<>();
                for (Object o : raw) {
                    if (o != null) {
                        out.add(String.valueOf(o));
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    public static String whitelistListText() {
        List<String> entries = whitelistEntries();
        if (entries.isEmpty()) {
            return "§7Whitelist is empty. §8Add with §f/difficulty admin whitelist add <player>";
        }
        return "§6Whitelist §7(" + entries.size() + ")\n§f" + String.join("§8, §f", entries);
    }

    public static String whitelistAdd(String raw) {
        if (raw == null || raw.isBlank()) {
            return "§cUsage: /difficulty admin whitelist add <player>";
        }
        try {
            Class<?> cfg = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig");
            Player online = org.bukkit.Bukkit.getPlayerExact(raw.trim());
            boolean added = false;
            if (online != null) {
                added = Boolean.TRUE.equals(cfg.getMethod("addWhitelistEntry", String.class)
                        .invoke(null, online.getName())) || added;
                added = Boolean.TRUE.equals(cfg.getMethod("addWhitelistEntry", String.class)
                        .invoke(null, online.getUniqueId().toString())) || added;
            } else {
                added = Boolean.TRUE.equals(cfg.getMethod("addWhitelistEntry", String.class)
                        .invoke(null, raw.trim()));
            }
            clearAreaCache();
            PLACEHOLDER_CACHE.clear();
            if (!added) {
                return "§eAlready on whitelist: §f" + raw.trim();
            }
            String label = online != null ? online.getName() : raw.trim();
            return "§aAdded §f" + label + " §ato whitelist"
                    + (whitelistEnabled() ? "." : ".\n§eWhitelist is OFF — §f/difficulty admin whitelist on");
        } catch (Throwable t) {
            return "§cWhitelist add failed: " + t.getMessage();
        }
    }

    public static String whitelistRemove(String raw) {
        if (raw == null || raw.isBlank()) {
            return "§cUsage: /difficulty admin whitelist remove <player|uuid>";
        }
        try {
            Class<?> cfg = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig");
            Player online = org.bukkit.Bukkit.getPlayerExact(raw.trim());
            boolean removed = Boolean.TRUE.equals(cfg.getMethod("removeWhitelistEntry", String.class)
                    .invoke(null, raw.trim()));
            if (online != null) {
                removed = Boolean.TRUE.equals(cfg.getMethod("removeWhitelistEntry", String.class)
                        .invoke(null, online.getName())) || removed;
                removed = Boolean.TRUE.equals(cfg.getMethod("removeWhitelistEntry", String.class)
                        .invoke(null, online.getUniqueId().toString())) || removed;
            }
            clearAreaCache();
            PLACEHOLDER_CACHE.clear();
            if (!removed) {
                return "§cNot on whitelist: §f" + raw.trim();
            }
            return "§aRemoved §f" + raw.trim() + " §afrom whitelist.";
        } catch (Throwable t) {
            return "§cWhitelist remove failed: " + t.getMessage();
        }
    }

    public static boolean isPlayerWhitelisted(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (String entry : whitelistEntries()) {
            if (entry != null && entry.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean telemetryEnabled() {
        try {
            Object on = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry", preferredLoader())
                    .getMethod("isEnabled").invoke(null);
            return on instanceof Boolean b && b;
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    public static String setTelemetryEnabled(boolean on) {
        try {
            loadClass("com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry", preferredLoader())
                    .getMethod("setEnabled", boolean.class).invoke(null, on);
            Path dir = telemetryDir();
            return (on ? "§aCombat hit log ON" : "§eCombat hit log OFF")
                    + "\n§7Logs AD hits for §fall players§7 using the difficulty system"
                    + " §8(rate-limited)."
                    + "\n§8" + dir;
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cFailed to toggle hit log: " + t.getMessage()
                    + "\n§8Install matching LegacyMechanics jar.";
        }
    }

    public static String telemetryStatusText() {
        try {
            Object line = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry", preferredLoader())
                    .getMethod("statusLine").invoke(null);
            return "§6Combat hit log\n§7" + line
                    + "\n§8Samples all AD players when ON (not whitelist-gated)."
                    + "\n§8/difficulty admin telemetry on|off|flush|test";
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cHit log unavailable: " + t.getMessage()
                    + "\n§8Need LegacyMechanics with BalanceTelemetry.";
        }
    }

    public static String telemetryFlush() {
        try {
            loadClass("com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry", preferredLoader())
                    .getMethod("flushAndClose").invoke(null);
            return "§aLogs flushed.\n§8" + telemetryDir();
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cHit log flush failed: " + t.getMessage();
        }
    }

    /**
     * Staff: unified system telemetry ({@code /difficulty admin syslog} / {@code /lm admin syslog}).
     * Works from console — no player NMS required.
     */
    public static String syslogCommand(String mode) {
        String m = mode == null || mode.isBlank() ? "status" : mode.toLowerCase(Locale.ROOT);
        try {
            Class<?> tel = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry", preferredLoader());
            return switch (m) {
                case "on", "true", "enable" -> {
                    tel.getMethod("setEnabled", boolean.class).invoke(null, true);
                    yield "§aEvent log ON\n§8" + tel.getMethod("telemetryDir").invoke(null);
                }
                case "off", "false", "disable" -> {
                    tel.getMethod("setEnabled", boolean.class).invoke(null, false);
                    yield "§eEvent log OFF";
                }
                case "flush" -> {
                    tel.getMethod("flushAndClose").invoke(null);
                    yield "§aLogs flushed.\n§8" + tel.getMethod("telemetryDir").invoke(null);
                }
                case "status", "0", "" -> "§7" + tel.getMethod("statusLine").invoke(null);
                default -> "§cUsage: syslog on|off|status|flush";
            };
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cEvent log unavailable: " + t.getMessage()
                    + "\n§8Need LegacyMechanics with SystemTelemetry.";
        }
    }

    /**
     * Staff: clear stuck DMZ base-form level sample and refresh Buy GUI gate level.
     */
    public static String resyncLevel(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cPlayer NMS unavailable.";
        }
        try {
            ClassLoader cl = preferredLoader();
            ensureResolved(cl);
            Class<?> sp = Class.forName("net.minecraft.server.level.ServerPlayer", true, cl);
            Object msg = Class.forName(
                            "com.dbzlegacy.adaptivedifficulty.command.DifficultyCommands", true, cl)
                    .getMethod("resyncLevel", sp)
                    .invoke(null, nms);
            return msg == null ? "§eResync returned empty." : String.valueOf(msg);
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cResync failed: " + resolveError;
        }
    }

    public static String telemetryTest(String playerName) {
        try {
            if (!telemetryEnabled()) {
                setTelemetryEnabled(true);
            }
            Object path = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry", preferredLoader())
                    .getMethod("writeTestProbe", String.class)
                    .invoke(null, playerName == null ? "console" : playerName);
            String p = path == null ? "" : String.valueOf(path);
            if (p.isBlank()) {
                return "§cTelemetry probe failed — check server log / jar version.";
            }
            return "§aTelemetry probe written.\n§7File: §f" + p
                    + "\n§7Live hits only log for §fwhitelisted§7 players with an active buy tier.";
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cTelemetry test failed: " + t.getMessage()
                    + "\n§8Install AdaptiveDifficulty 1.0.18+ (BalanceTelemetry).";
        }
    }

    public static Path telemetryDir() {
        try {
            Object path = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry", preferredLoader())
                    .getMethod("telemetryDir").invoke(null);
            if (path instanceof Path p) {
                return p;
            }
            if (path != null) {
                return Path.of(String.valueOf(path));
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return Path.of("config", "adaptivedifficulty", "telemetry");
    }

    public static String whitelistClear() {
        try {
            Class<?> cfgCls = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig");
            Object cfg = cfgCls.getMethod("get").invoke(null);
            Field listField = cfgCls.getField("whitelist");
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) listField.get(cfg);
            int n = list == null ? 0 : list.size();
            if (list != null) {
                list.clear();
            }
            cfgCls.getMethod("save").invoke(null);
            clearAreaCache();
            PLACEHOLDER_CACHE.clear();
            return "§aCleared whitelist (§f" + n + "§a entries).";
        } catch (Throwable t) {
            return "§cWhitelist clear failed: " + t.getMessage();
        }
    }

    private static void clearAreaCache() {
        try {
            Class.forName("com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty")
                    .getMethod("clearCache").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    /** @return true when Forge reload succeeded (false if mod missing / load failed). */
    /** Staff CNPC test hub — Forge {@code CnpcStaffTestGui.open} (Mohist routes {@code /lm} on Bukkit). */
    public static boolean openStaffCnpcTestGui(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            resolveError = "Could not reach LegacyMechanics Forge player.";
            return false;
        }
        try {
            ClassLoader cl = nms.getClass().getClassLoader();
            Class<?> gui = Class.forName(
                    "com.dbzlegacy.adaptivedifficulty.gui.CnpcStaffTestGui", true, cl);
            java.lang.reflect.Method open = gui.getMethod("open", nms.getClass());
            Object ok = open.invoke(null, nms);
            if (ok instanceof Boolean b) {
                return b;
            }
            return true;
        } catch (NoSuchMethodException missing) {
            resolveError = "CnpcStaffTestGui missing — update LegacyMechanics Forge jar to 4.5.1+.";
            return false;
        } catch (Throwable t) {
            Throwable root = t.getCause() != null ? t.getCause() : t;
            resolveError = root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : ": " + root.getMessage());
            return false;
        }
    }

    public static boolean reloadConfig() {
        try {
            Object ok = Class.forName("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig")
                    .getMethod("reload").invoke(null);
            Class.forName("com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache")
                    .getMethod("invalidateAll").invoke(null);
            PLACEHOLDER_CACHE.clear();
            if (ok instanceof Boolean b) {
                return b;
            }
            return true;
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    /**
     * Staff: import CNPC Rival/Spar world storeddata into LegacyMechanics JSON stores.
     * Works from console or player — Mohist Bukkit owns {@code /lm}, so this must live here.
     */
    public static String migrateCnpc(boolean forceOverwrite) {
        try {
            Class<?> migrator = Class.forName(
                    "com.dbzlegacy.adaptivedifficulty.data.CnpcDataMigrator");
            Object server = null;
            try {
                Class<?> serverLifecycle = Class.forName(
                        "net.minecraftforge.server.ServerLifecycleHooks");
                server = serverLifecycle.getMethod("getCurrentServer").invoke(null);
            } catch (Throwable ignored) {
            }
            if (server == null) {
                try {
                    Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                    Object bServer = bukkit.getMethod("getServer").invoke(null);
                    if (bServer != null) {
                        try {
                            server = bServer.getClass().getMethod("getServer").invoke(bServer);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            if (server == null) {
                return "§cNo MinecraftServer — is the world loaded?";
            }
            Object msg = migrator.getMethod("forceMigrateWorld",
                            Class.forName("net.minecraft.server.MinecraftServer"),
                            boolean.class)
                    .invoke(null, server, forceOverwrite);
            return msg == null ? "§eMigration returned empty." : String.valueOf(msg);
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cCNPC migrate failed: " + resolveError;
        }
    }

    /**
     * Staff wipe of one player's LegacyMechanics data
     * ({@code /lm admin clear <player> [all|rival|spar|difficulty|progression]}).
     * Works from console or player — Mohist Bukkit owns {@code /lm}.
     */
    public static String clearPlayerData(String playerArg, String scope) {
        try {
            Class<?> clearer = Class.forName(
                    "com.dbzlegacy.adaptivedifficulty.data.PlayerDataClear");
            Object server = null;
            try {
                Class<?> serverLifecycle = Class.forName(
                        "net.minecraftforge.server.ServerLifecycleHooks");
                server = serverLifecycle.getMethod("getCurrentServer").invoke(null);
            } catch (Throwable ignored) {
            }
            if (server == null) {
                try {
                    Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                    Object bServer = bukkit.getMethod("getServer").invoke(null);
                    if (bServer != null) {
                        try {
                            server = bServer.getClass().getMethod("getServer").invoke(bServer);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            if (server == null) {
                return "§cNo MinecraftServer — is the world loaded?";
            }
            Object msg = clearer.getMethod(
                            "clear",
                            Class.forName("net.minecraft.server.MinecraftServer"),
                            String.class,
                            String.class)
                    .invoke(null, server, playerArg, scope == null ? "all" : scope);
            return msg == null ? "§eClear returned empty." : String.valueOf(msg);
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cClear failed: " + resolveError;
        }
    }

    /**
     * Staff mastery grant for one online player
     * ({@code /lm mastery grant-first-superform} or {@code grant-first-legendary}).
     * Works from console or a player — Mohist Bukkit owns {@code /lm}.
     */
    public static String grantFirstFormMastery(
            String playerName, double amount, String kindToken, boolean notifyTarget) {
        try {
            ClassLoader cl = preferredLoader();
            Class<?> mech = loadClass(
                    "com.dbzlegacy.adaptivedifficulty.command.MechanicsCommands", cl);
            Class<?> serverCls = loadClass("net.minecraft.server.MinecraftServer", cl);
            Object server = minecraftServerOrNull();
            if (server == null) {
                return "§cNo MinecraftServer — is the world loaded?";
            }
            Object msg = mech.getMethod(
                            "grantNamed",
                            serverCls,
                            String.class,
                            double.class,
                            String.class,
                            boolean.class)
                    .invoke(null, server, playerName, amount, kindToken, notifyTarget);
            return msg == null ? "§eMastery grant returned empty." : String.valueOf(msg);
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cMastery grant failed: " + resolveError;
        }
    }

    /**
     * Staff reset of character service cooldowns
     * ({@code /lm admin character cooldown clear <player> [race|class|reskin|all]}).
     */
    public static String clearCharacterServiceCooldowns(String playerArg, String kind) {
        try {
            Class<?> clearer = Class.forName(
                    "com.dbzlegacy.adaptivedifficulty.data.PlayerDataClear");
            Object server = minecraftServerOrNull();
            if (server == null) {
                return "§cNo MinecraftServer — is the world loaded?";
            }
            Object msg = clearer.getMethod(
                            "clearCharacterCooldowns",
                            Class.forName("net.minecraft.server.MinecraftServer"),
                            String.class,
                            String.class)
                    .invoke(null, server, playerArg, kind == null ? "all" : kind);
            return msg == null ? "§eCooldown clear returned empty." : String.valueOf(msg);
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return "§cCharacter cooldown clear failed: " + resolveError;
        }
    }

    private static Object minecraftServerOrNull() {
        try {
            Class<?> serverLifecycle = Class.forName(
                    "net.minecraftforge.server.ServerLifecycleHooks");
            Object server = serverLifecycle.getMethod("getCurrentServer").invoke(null);
            if (server != null) {
                return server;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object bServer = bukkit.getMethod("getServer").invoke(null);
            if (bServer != null) {
                try {
                    return bServer.getClass().getMethod("getServer").invoke(bServer);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Pull live DMZ level + unlock data before opening Buy / Difficulty GUI.
     * Mohist {@code /difficulty} owns the inventory open and previously skipped
     * Forge {@code DifficultyActions.openGui}, so placeholders could show a stuck
     * session sample (level 1 / stale mid-level) until {@code resynclevel}.
     */
    public static boolean prepareDifficultyGui(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return false;
        }
        try {
            ensureResolved(nms.getClass().getClassLoader());
            try {
                Method prep = actionsCls.getMethod("prepareGui", serverPlayerCls);
                prep.invoke(null, nms);
            } catch (NoSuchMethodException legacy) {
                // Older Forge jars: sample + refresh only.
                ClassLoader cl = nms.getClass().getClassLoader();
                Class<?> dmz = loadClass(
                        "com.dbzlegacy.adaptivedifficulty.calc.DmzProgression", cl);
                Class<?> playerCls = loadClass("net.minecraft.world.entity.player.Player", cl);
                dmz.getMethod("sampleLevelOnGuiOpen", playerCls).invoke(null, nms);
                if (cacheRefresh != null) {
                    cacheRefresh.invoke(null, nms);
                }
                syncPlayerProgress(player);
            }
            PLACEHOLDER_CACHE.remove(player.getUniqueId());
            return true;
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    /**
     * Refresh unlock-tier grants and difficulty titles from current DMZ / prestige
     * (same sync chat menu runs when opening Buy / Titles).
     */
    public static void syncPlayerProgress(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return;
        }
        try {
            ensureResolved(nms.getClass().getClassLoader());
            if (cacheData == null) {
                return;
            }
            Object data = cacheData.invoke(null, nms);
            ClassLoader cl = nms.getClass().getClassLoader();
            try {
                Class<?> dmz = loadClass(
                        "com.dbzlegacy.adaptivedifficulty.calc.DmzProgression", cl);
                Class<?> playerCls = loadClass("net.minecraft.world.entity.player.Player", cl);
                dmz.getMethod("sampleLevelOnGuiOpen", playerCls).invoke(null, nms);
            } catch (Throwable ignored) {
            }
            Class.forName("com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem", true, cl)
                    .getMethod("syncUnlocks",
                            Class.forName("net.minecraft.server.level.ServerPlayer", true, cl),
                            Class.forName("com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData", true, cl))
                    .invoke(null, nms, data);
            Class.forName("com.dbzlegacy.adaptivedifficulty.title.TitleSystem", true, cl)
                    .getMethod("syncTierTitles",
                            Class.forName("net.minecraft.server.level.ServerPlayer", true, cl),
                            boolean.class)
                    .invoke(null, nms, false);
            if (cacheRefresh != null) {
                cacheRefresh.invoke(null, nms);
            }
            PLACEHOLDER_CACHE.remove(player.getUniqueId());
        } catch (Throwable ignored) {
        }
    }

    /** Staff: clear V3 temporary difficulty (active tier/level). Unlocks & coins kept. */
    public static String resetPurchased(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach adaptive difficulty mod"
                    + (resolveError == null || resolveError.isBlank() ? "." : " (" + resolveError + ").");
        }
        try {
            ensureResolved(nms.getClass().getClassLoader());
            Object data = cacheData.invoke(null, nms);
            try {
                data.getClass().getMethod("resetTemporary").invoke(data);
            } catch (NoSuchMethodException legacy) {
                data.getClass().getMethod("setActiveDifficultyLevel", long.class).invoke(data, 0L);
                data.getClass().getMethod("setActiveTier", int.class).invoke(data, 0);
            }
            cacheCls.getMethod("save", serverPlayerCls).invoke(null, nms);
            cacheRefresh.invoke(null, nms);
            try {
                loadClass("com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker",
                        nms.getClass().getClassLoader())
                        .getMethod("releaseAndRevertPlayer", serverPlayerCls)
                        .invoke(null, nms);
                loadClass("com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler",
                        nms.getClass().getClassLoader())
                        .getMethod("processEvictions")
                        .invoke(null);
            } catch (Throwable ignored) {
            }
            loadClass("com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty", nms.getClass().getClassLoader())
                    .getMethod("clearCache").invoke(null);
            PLACEHOLDER_CACHE.remove(player.getUniqueId());
            return "§aCleared active tier/level. Unlocks & Ancient Coins kept.";
        } catch (Throwable t) {
            return "§cReset failed: " + t.getMessage();
        }
    }

    public static String areaDifficultyText(Player player) {
        Object nms = nmsPlayer(player);
        if (nms == null) {
            return "§cCould not reach adaptive difficulty mod"
                    + (resolveError == null || resolveError.isBlank() ? "." : " (" + resolveError + ").");
        }
        try {
            ClassLoader cl = nms.getClass().getClassLoader();
            ensureResolved(cl);
            Object snap = cacheRefresh.invoke(null, nms);
            long active = ((Number) snap.getClass().getField("active").get(snap)).longValue();
            long available = ((Number) snap.getClass().getField("availableMax").get(snap)).longValue();
            Object level = serverPlayerCls.getMethod("m_284548_").invoke(nms);
            Object pos = nms.getClass().getMethod("m_20183_").invoke(nms);
            long area = 0L;
            if (level != null && pos != null) {
                area = ((Number) loadClass("com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty", cl)
                        .getMethod("at", loadClass("net.minecraft.server.level.ServerLevel", cl),
                                loadClass("net.minecraft.core.BlockPos", cl))
                        .invoke(null, level, pos)).longValue();
            }
            Object cfg = loadClass("com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig", cl)
                    .getMethod("get").invoke(null);
            String mode = String.valueOf(cfg.getClass().getField("areaDifficultyMode").get(cfg));
            double radius = ((Number) cfg.getClass().getField("mobScaleRadius").get(cfg)).doubleValue();
            double group = ((Number) cfg.getClass().getField("areaGroupBonusPercent").get(cfg)).doubleValue();
            return "§6Area Difficulty\n"
                    + "§eActive tier CR proxy: §f" + active + " §7/ max §f" + available + "\n"
                    + "§eArea at you: §f" + area + "\n"
                    + "§eMode: §f" + mode + " §8| §eradius §f" + radius
                    + " §8| §egroupBonus% §f" + group;
        } catch (Throwable t) {
            return "§cArea difficulty failed: " + t.getMessage();
        }
    }

    public static boolean setVanillaDifficulty(String level) {
        try {
            Class<?> difficulty = Class.forName("net.minecraft.world.Difficulty");
            Object parsed = Class.forName("com.dbzlegacy.adaptivedifficulty.world.VanillaDifficultyGuard")
                    .getMethod("parse", String.class)
                    .invoke(null, level);
            if (parsed == null) {
                return false;
            }
            Object server = Class.forName("org.bukkit.Bukkit")
                    .getMethod("getServer").invoke(null);
            Object nmsServer = server.getClass().getMethod("getServer").invoke(server);
            return Boolean.TRUE.equals(
                    Class.forName("com.dbzlegacy.adaptivedifficulty.world.VanillaDifficultyGuard")
                            .getMethod("set", Class.forName("net.minecraft.server.MinecraftServer"), difficulty)
                            .invoke(null, nmsServer, parsed)
            );
        } catch (Throwable t) {
            return false;
        }
    }

    private static void ensureResolved() throws Exception {
        ensureResolved(null);
    }

    private static void ensureResolved(ClassLoader preferred) throws Exception {
        if (resolved) {
            return;
        }
        synchronized (ForgeBridge.class) {
            if (resolved) {
                return;
            }
            try {
                serverPlayerCls = loadClass("net.minecraft.server.level.ServerPlayer", preferred);
                cacheCls = loadClass("com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache", preferred);
                actionsCls = loadClass("com.dbzlegacy.adaptivedifficulty.service.DifficultyActions", preferred);

                cacheGet = cacheCls.getMethod("get", serverPlayerCls);
                cacheRefresh = cacheCls.getMethod("refresh", serverPlayerCls);
                cacheData = cacheCls.getMethod("data", serverPlayerCls);

                // Optional: Ancient Coin economy + UnlockTier costs.
                try {
                    Class<?> economyCls = loadClass(
                            "com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy", preferred);
                    economyBalanceText = economyCls.getMethod("balanceText", serverPlayerCls);
                    try {
                        economyFormat = economyCls.getMethod("format", long.class);
                    } catch (NoSuchMethodException ignored) {
                        economyFormat = null;
                    }
                    economyFormatExactCost = economyCls.getMethod("formatExactCost", long.class);
                    try {
                        economyBalance = economyCls.getMethod("balance", serverPlayerCls);
                    } catch (NoSuchMethodException ignored) {
                        economyBalance = null;
                    }
                    try {
                        coinKindCls = loadClass(
                                "com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy$CoinKind",
                                preferred);
                        economyCountOf = economyCls.getMethod("countOf", serverPlayerCls, coinKindCls);
                    } catch (Throwable ignored) {
                        coinKindCls = null;
                        economyCountOf = null;
                    }
                    try {
                        Class<?> unlockTierCls = loadClass(
                                "com.dbzlegacy.adaptivedifficulty.tier.UnlockTier", preferred);
                        economyActivationCostPlayer = economyCls.getMethod(
                                "activationCost", unlockTierCls, serverPlayerCls);
                    } catch (Throwable ignored) {
                        economyActivationCostPlayer = null;
                    }
                    try {
                        Class<?> paidCls = loadClass(
                                "com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess", preferred);
                        paidFeatureBypassCost = paidCls.getMethod(
                                "bypassAncientCoinCost", serverPlayerCls);
                    } catch (Throwable ignored) {
                        paidFeatureBypassCost = null;
                    }
                } catch (Throwable missing) {
                    economyBalanceText = null;
                    economyFormat = null;
                    economyFormatExactCost = null;
                    economyBalance = null;
                    economyCountOf = null;
                    economyActivationCostPlayer = null;
                    paidFeatureBypassCost = null;
                    coinKindCls = null;
                }
                try {
                    Class<?> unlockTierCls = loadClass(
                            "com.dbzlegacy.adaptivedifficulty.tier.UnlockTier", preferred);
                    unlockTierValues = unlockTierCls.getMethod("values");
                    unlockTierById = unlockTierCls.getMethod("byId", int.class);
                    unlockTierActivationCost = unlockTierCls.getMethod("activationCost");
                    unlockTierMaxDifficulty = unlockTierCls.getMethod("maxDifficulty");
                    try {
                        unlockTierActivationCostForLevel =
                                unlockTierCls.getMethod("activationCostForLevel", int.class);
                    } catch (NoSuchMethodException ignored) {
                        unlockTierActivationCostForLevel = null;
                    }
                    try {
                        unlockTierRequiredDmzLevel = unlockTierCls.getMethod("requiredDmzLevel");
                    } catch (NoSuchMethodException ignored) {
                        unlockTierRequiredDmzLevel = null;
                    }
                    try {
                        unlockTierRequiredPrestige = unlockTierCls.getMethod("requiredPrestige");
                    } catch (NoSuchMethodException ignored) {
                        unlockTierRequiredPrestige = null;
                    }
                    try {
                        unlockTierRequirementTip = unlockTierCls.getMethod("requirementTip");
                    } catch (NoSuchMethodException ignored) {
                        unlockTierRequirementTip = null;
                    }
                } catch (Throwable missing) {
                    unlockTierValues = null;
                    unlockTierById = null;
                    unlockTierActivationCost = null;
                    unlockTierActivationCostForLevel = null;
                    unlockTierMaxDifficulty = null;
                    unlockTierRequiredDmzLevel = null;
                    unlockTierRequiredPrestige = null;
                    unlockTierRequirementTip = null;
                }
                try {
                    Class<?> titleSys = loadClass(
                            "com.dbzlegacy.adaptivedifficulty.title.TitleSystem", preferred);
                    Class<?> titleCls = loadClass(
                            "com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle", preferred);
                    titleActiveDisplay = titleSys.getMethod("activeDisplay", serverPlayerCls);
                    titleActiveId = titleSys.getMethod("activeId", serverPlayerCls);
                    titleHas = titleSys.getMethod("has", serverPlayerCls, titleCls);
                    titleValues = titleCls.getMethod("values");
                    titleRequirementTip = titleCls.getMethod("requirementTip");
                } catch (Throwable missing) {
                    titleActiveDisplay = null;
                    titleActiveId = null;
                    titleHas = null;
                    titleValues = null;
                    titleRequirementTip = null;
                }

                try {
                    Class<?> snapCls = loadClass(
                            "com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot", preferred);
                    snapshotState = snapCls.getMethod("state");
                    snapshotStateColor = snapCls.getMethod("stateColorCode");
                } catch (Throwable missing) {
                    snapshotState = null;
                    snapshotStateColor = null;
                }

                actionsHandle = actionsCls.getMethod(
                        "handle", serverPlayerCls, String.class, long.class, String.class);
                try {
                    actionsHandleArg = actionsCls.getMethod(
                            "handleArg", serverPlayerCls, String.class, String.class, String.class);
                } catch (NoSuchMethodException missing) {
                    actionsHandleArg = null;
                }
                try {
                    actionsHandleArgNoReopen = actionsCls.getMethod(
                            "handleArgNoReopen", serverPlayerCls, String.class, String.class, String.class);
                } catch (NoSuchMethodException missing) {
                    actionsHandleArgNoReopen = null;
                }
                Class<?> resultCls = loadClass(
                        "com.dbzlegacy.adaptivedifficulty.service.DifficultyActions$Result", preferred);
                try {
                    resultMessage = resultCls.getMethod("message");
                    RESULT_MESSAGE_FIELD = null;
                } catch (NoSuchMethodException missing) {
                    resultMessage = null;
                    RESULT_MESSAGE_FIELD = resultCls.getField("message");
                }
                resultOk = resultCls.getMethod("ok");
                try {
                    chatMenuOpen = loadClass(
                            "com.dbzlegacy.adaptivedifficulty.gui.DifficultyChatMenu", preferred)
                            .getMethod("open", serverPlayerCls, String.class);
                } catch (Throwable missing) {
                    chatMenuOpen = null;
                }
                resolveError = null;
                resolved = true;
            } catch (Throwable t) {
                resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
                throw t instanceof Exception e ? e : new Exception(t);
            }
        }
    }

    private static Object readResultMessage(Object result) throws Exception {
        if (resultMessage != null) {
            return resultMessage.invoke(result);
        }
        if (RESULT_MESSAGE_FIELD != null) {
            return RESULT_MESSAGE_FIELD.get(result);
        }
        return "";
    }

    /** Load a class trying Mohist/Forge-friendly classloaders first. */
    private static Class<?> loadClass(String name, ClassLoader preferred) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        List<ClassLoader> loaders = new ArrayList<>();
        if (preferred != null) {
            loaders.add(preferred);
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null) {
            loaders.add(ctx);
        }
        loaders.add(ForgeBridge.class.getClassLoader());
        try {
            Object server = Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);
            if (server != null) {
                loaders.add(server.getClass().getClassLoader());
            }
        } catch (Throwable ignored) {
        }
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(name, true, loader);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            if (last != null) {
                throw last;
            }
            throw e;
        }
    }

    private static Field findConfigField(Class<?> cfgCls, String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("guibackend", "guiBackend"),
                Map.entry("vanilladifficulty", "vanillaDifficulty"),
                Map.entry("enablemobscaling", "enableMobScaling"),
                Map.entry("adminpermission", "adminPermission"),
                Map.entry("enabled", "enabled"),
                Map.entry("system", "enabled"),
                Map.entry("systemenabled", "enabled"),
                Map.entry("whitelistenabled", "whitelistEnabled"),
                Map.entry("rewardscaling", "rewardScaling"),
                Map.entry("prestigemultiplier", "prestigeMultiplier"),
                Map.entry("levelmultiplier", "levelMultiplier"),
                Map.entry("teambonus", "teamBonusPercent"),
                Map.entry("teambonuspercent", "teamBonusPercent"),
                Map.entry("maxhealthmultiplier", "maxHealthMultiplier"),
                Map.entry("maxscaledhealth", "maxScaledHealth"),
                Map.entry("mobscaleradius", "mobScaleRadius"),
                Map.entry("mobhealthscale", "mobHealthScale"),
                Map.entry("ancientcoinupgradechance", "ancientCoinUpgradeChance"),
                Map.entry("nearbyscaleintervalticks", "nearbyScaleIntervalTicks"),
                Map.entry("nearbyscaleinterval", "nearbyScaleIntervalTicks"),
                Map.entry("maxscaledmobsperplayer", "maxScaledMobsPerPlayer"),
                Map.entry("nearbyscalebudgetperplayer", "maxScaledMobsPerPlayer"),
                Map.entry("tiercostleveldivisor", "tierCostLevelDivisor"),
                Map.entry("enableclasscounters", "enableClassCounters"),
                Map.entry("classcounters", "enableClassCounters"),
                Map.entry("enablestrongstatcounters", "enableStrongStatCounters"),
                Map.entry("strongstatcounters", "enableStrongStatCounters"),
                Map.entry("topstatcounters", "enableStrongStatCounters"),
                Map.entry("strongstatcountermult", "strongStatCounterMult"),
                Map.entry("strongcounter", "strongStatCounterMult"),
                Map.entry("topstatmult", "strongStatCounterMult"),
                Map.entry("classcounterdamagemult", "classCounterDamageMult"),
                Map.entry("classdmg", "classCounterDamageMult"),
                Map.entry("classcounterhealthmult", "classCounterHealthMult"),
                Map.entry("classhp", "classCounterHealthMult"),
                Map.entry("classcounterarmormult", "classCounterArmorMult"),
                Map.entry("classarmor", "classCounterArmorMult"),
                Map.entry("maxcounteroverlaymult", "maxCounterOverlayMult"),
                Map.entry("countercap", "maxCounterOverlayMult"),
                Map.entry("overlaycap", "maxCounterOverlayMult"),
                Map.entry("transformscaleweight", "transformScaleWeight"),
                Map.entry("transformweight", "transformScaleWeight"),
                Map.entry("formscaleweight", "transformScaleWeight"),
                Map.entry("transformscaleexponent", "transformScaleExponent"),
                Map.entry("transformexponent", "transformScaleExponent"),
                Map.entry("formscaleexponent", "transformScaleExponent")
        );
        String fieldName = aliases.getOrDefault(k, key);
        try {
            return cfgCls.getField(fieldName);
        } catch (NoSuchFieldException e) {
            for (Field f : cfgCls.getFields()) {
                if (f.getName().equalsIgnoreCase(fieldName) || f.getName().equalsIgnoreCase(k)) {
                    return f;
                }
            }
            return null;
        }
    }

    /** Legacy counter knobs kept in JSON for migrate/compat; no combat effect. */
    private static boolean isLegacyUnusedCounterField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return switch (fieldName) {
            case "weakStatCounterMult", "weakDefensePierceMult",
                 "specializationDamageTax", "raceCounterMult" -> true;
            default -> false;
        };
    }

    /**
     * Allowlist of scalar config fields settable via Bukkit {@code admin set}.
     * Deny-by-default: collections, migration flags, and {@code adminPermission}
     * (privilege escalation if set to a common/default node) are never writable here.
     */
    private static boolean isSafeAdminField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        return switch (fieldName) {
            case "enabled", "whitelistEnabled", "staffFreeAncientCoinCosts",
                 "balanceTelemetryEnabled",
                 "balanceTelemetryMaxPerSecond",
                 "prestigeMultiplier", "levelMultiplier", "teamBonusPercent",
                 "contributionPercent", "rewardScaling",
                 "combatCurveExponent", "combatCurvePivot",
                 "healthCurveExponent", "healthCurvePivot",
                 "healthPercentPerDifficulty", "damagePercentPerDifficulty",
                 "defensePercentPerDifficulty", "movementPercentPer100Difficulty",
                 "dmzExtraHealthPercent", "dmzExtraDamagePercent",
                 "dmzExtraDefensePercent", "dmzExtraKiDamagePercent",
                 "tierAwakened", "tierEnhanced", "tierElite", "tierAdvanced",
                 "tierMaster", "tierLegendary", "tierGod", "tierDivine",
                 "tierImpossible", "tierTranscendent", "tierEternal", "tierMythic",
                 "tierOmega", "tierAbsolute", "tierApex", "tierZenith",
                 "referenceMaxLevel", "referenceMaxPrestige", "hardCapDifficulty",
                 "mobScaleRadius", "enableMobScaling", "scaleHostileOnly",
                 "applyDmzExtrasToAllHostiles", "enableRewardScaling",
                 "enableElites", "eliteChancePercent", "eliteStatMultiplier",
                 "enableMutations", "mutationChancePercent",
                 "enableAdaptiveAi", "enableEnemyEvolution", "enableBossScaling",
                 "bossStatMultiplier", "bossHealthThreshold",
                 "maxHealthMultiplier", "maxScaledHealth", "maxMoveMultiplier",
                 "maxArmorBonus", "maxDamageMultiplier",
                 "guiBackend", "vanillaDifficulty", "areaDifficultyMode",
                 "areaGroupBonusPercent", "areaDifficultyVariancePercent",
                 "combatRatingDmzWeight", "combatRatingPrestigeWeight",
                 "combatRatingTransformWeight", "combatRatingDifficultyWeight",
                 "unlockTier1Level", "unlockTier2Level", "unlockTier3Level",
                 "unlockTier4Level", "unlockTier5Level", "unlockTier6Level",
                 "unlockTier7Level",
                 "unlockTier1Max", "unlockTier2Max", "unlockTier3Max",
                 "unlockTier4Max", "unlockTier5Max", "unlockTier6Max",
                 "unlockTier7Max",
                 "unlockTier1Cost", "unlockTier2Cost", "unlockTier3Cost",
                 "unlockTier4Cost", "unlockTier5Cost", "unlockTier6Cost",
                 "unlockTier7Cost",
                 "unlockTier1EnemyMult", "unlockTier2EnemyMult", "unlockTier3EnemyMult",
                 "unlockTier4EnemyMult", "unlockTier5EnemyMult", "unlockTier6EnemyMult",
                 "unlockTier7EnemyMult",
                 "enableClassCounters", "enableStrongStatCounters",
                 "strongStatCounterMult", "classCounterDamageMult",
                 "classCounterHealthMult", "classCounterArmorMult",
                 "maxCounterOverlayMult", "transformScaleWeight", "transformScaleExponent",
                 "defenseToArmorFactor",
                 "nearbyScaleIntervalTicks", "maxScaledMobsPerPlayer",
                 "nearbyScaleBudgetPerPlayer", "tierCostLevelDivisor",
                 "enableAncientCoinDrops", "ancientCoinDropMult",
                 "ancientCoinRatingDivisor", "ancientCoinUpgradeChance",
                 "deathResetsActiveDifficulty", "mobHealthScale",
                 "tankDamageDefenseRatio", "tankDamageHealthRatio",
                 "maxFormBoost", "maxLiveCombatChannel",
                 "eliteMinUnlockTier", "mutationMinUnlockTier",
                 "adaptiveAiMinUnlockTier", "enemyEvolutionMinUnlockTier",
                 "bossMechanicsMinUnlockTier" -> true;
            // Legacy unused: weakStatCounterMult, weakDefensePierceMult,
            // specializationDamageTax, raceCounterMult — not live-settable.
            default -> false;
        };
    }

    private static Object clampAdminValue(String fieldName, Object parsed) {
        if (parsed == null || fieldName == null) {
            return parsed;
        }
        try {
            return switch (fieldName) {
                case "maxScaledMobsPerPlayer", "nearbyScaleBudgetPerPlayer" -> {
                    int n = ((Number) parsed).intValue();
                    yield Math.max(1, Math.min(5, n));
                }
                case "nearbyScaleIntervalTicks" -> Math.max(10, ((Number) parsed).intValue());
                case "mobScaleRadius" -> {
                    double r = ((Number) parsed).doubleValue();
                    yield Math.max(8.0, Math.min(128.0, r));
                }
                case "mobHealthScale" -> {
                    double h = ((Number) parsed).doubleValue();
                    // Match DifficultyConfig.sanitizeLive stock default.
                    yield h <= 0.0 || h > 4.0 ? 1.15 : h;
                }
                case "tankDamageDefenseRatio" -> {
                    double r = ((Number) parsed).doubleValue();
                    yield r < 0.0 || r > 10.0 || Double.isNaN(r) ? 0.45 : r;
                }
                case "tankDamageHealthRatio" -> {
                    double r = ((Number) parsed).doubleValue();
                    yield r < 0.0 || r > 1.0 || Double.isNaN(r) ? 0.28 : r;
                }
                case "maxFormBoost" -> {
                    double m = ((Number) parsed).doubleValue();
                    // Matches DifficultyConfig.sanitizeLive bounds.
                    if (!(m > 1.0) || m > 500.0 || Double.isNaN(m)) {
                        yield 100.0;
                    }
                    yield m;
                }
                case "maxLiveCombatChannel" -> {
                    double m = ((Number) parsed).doubleValue();
                    if (!(m > 1.0) || m > 1.0e12 || Double.isNaN(m)) {
                        yield 50_000_000.0;
                    }
                    yield m;
                }
                case "ancientCoinUpgradeChance" -> {
                    double c = ((Number) parsed).doubleValue();
                    yield Math.max(0.0, Math.min(0.25, c));
                }
                case "ancientCoinDropMult" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(0.0, Math.min(10.0, m));
                }
                case "ancientCoinRatingDivisor" -> {
                    double d = ((Number) parsed).doubleValue();
                    yield Math.max(1.0, Math.min(1_000_000.0, d));
                }
                case "prestigeMultiplier", "levelMultiplier", "rewardScaling" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(0.0, Math.min(50.0, m));
                }
                case "teamBonusPercent", "contributionPercent",
                     "areaGroupBonusPercent", "areaDifficultyVariancePercent",
                     "movementPercentPer100Difficulty" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(0.0, Math.min(5.0, m));
                }
                case "eliteChancePercent", "mutationChancePercent" -> {
                    double c = ((Number) parsed).doubleValue();
                    // Live admin set: keep rarities from becoming a free farm.
                    yield Math.max(0.0, Math.min(25.0, c));
                }
                case "eliteStatMultiplier", "bossStatMultiplier" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(1.0, Math.min(5.0, m));
                }
                case "maxDamageMultiplier", "maxHealthMultiplier", "maxMoveMultiplier" -> {
                    double m = ((Number) parsed).doubleValue();
                    // 0 = uncapped (documented); otherwise keep a sane ceiling.
                    if (m <= 0.0) {
                        yield 0.0;
                    }
                    yield Math.max(1.0, Math.min(20.0, m));
                }
                case "maxScaledHealth", "maxArmorBonus", "bossHealthThreshold" -> {
                    double m = ((Number) parsed).doubleValue();
                    if (m <= 0.0) {
                        yield 0.0; // uncapped / disabled
                    }
                    yield Math.max(1.0, Math.min(100_000.0, m));
                }
                case "dmzExtraKiDamagePercent", "dmzExtraDamagePercent",
                     "dmzExtraHealthPercent", "dmzExtraDefensePercent",
                     "damagePercentPerDifficulty", "healthPercentPerDifficulty",
                     "defensePercentPerDifficulty" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(0.0, Math.min(2.0, m));
                }
                case "unlockTier1EnemyMult", "unlockTier2EnemyMult", "unlockTier3EnemyMult",
                     "unlockTier4EnemyMult", "unlockTier5EnemyMult", "unlockTier6EnemyMult",
                     "unlockTier7EnemyMult" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(0.05, Math.min(4.0, m));
                }
                case "strongStatCounterMult", "classCounterDamageMult",
                     "classCounterHealthMult", "classCounterArmorMult" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(1.0, Math.min(3.0, m));
                }
                case "maxCounterOverlayMult" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(1.0, Math.min(4.0, m));
                }
                case "transformScaleWeight" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(0.0, Math.min(1.0, m));
                }
                case "transformScaleExponent" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(0.20, Math.min(1.0, m));
                }
                case "defenseToArmorFactor" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(0.1, Math.min(50.0, m));
                }
                case "tierCostLevelDivisor" -> {
                    double m = ((Number) parsed).doubleValue();
                    yield Math.max(1.0, Math.min(10_000.0, m));
                }
                case "eliteMinUnlockTier", "mutationMinUnlockTier",
                     "adaptiveAiMinUnlockTier", "enemyEvolutionMinUnlockTier",
                     "bossMechanicsMinUnlockTier" -> {
                    int n = ((Number) parsed).intValue();
                    yield Math.max(0, Math.min(7, n));
                }
                case "unlockTier1Cost", "unlockTier2Cost", "unlockTier3Cost",
                     "unlockTier4Cost", "unlockTier5Cost", "unlockTier6Cost",
                     "unlockTier7Cost" -> Math.max(1L, ((Number) parsed).longValue());
                case "guiBackend" -> {
                    String s = String.valueOf(parsed).trim().toLowerCase(Locale.ROOT);
                    yield switch (s) {
                        case "cmi", "cmilib", "cmigui", "deluxemenus", "deluxe", "dm" -> "cmi";
                        case "chest", "bukkit", "inventory", "gui" -> "chest";
                        case "chat" -> "chat";
                        case "auto" -> "auto";
                        default -> "auto";
                    };
                }
                case "vanillaDifficulty" -> {
                    String s = String.valueOf(parsed).trim().toLowerCase(Locale.ROOT);
                    yield switch (s) {
                        case "peaceful", "easy", "normal", "hard" -> s;
                        default -> "hard";
                    };
                }
                default -> parsed;
            };
        } catch (Throwable t) {
            return parsed;
        }
    }

    private static Object coerce(Class<?> type, String value) {
        if (type == String.class) {
            return value;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value)
                    || "on".equalsIgnoreCase(value)
                    || "yes".equalsIgnoreCase(value);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        }
        // Refuse List/Map/etc. mutation through this path.
        throw new IllegalArgumentException("Unsupported config type: " + type.getSimpleName());
    }

    private static Object field(Object obj, String name) throws Exception {
        Field f = obj.getClass().getField(name);
        return f.get(obj);
    }

    private static long longField(Object obj, String name) throws Exception {
        return ((Number) field(obj, name)).longValue();
    }

    private static int intField(Object obj, String name) throws Exception {
        return ((Number) field(obj, name)).intValue();
    }

    private record CachedPlaceholders(Map<String, String> map, long atMs) {}
}
