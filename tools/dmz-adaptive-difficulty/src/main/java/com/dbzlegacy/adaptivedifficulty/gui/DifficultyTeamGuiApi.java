package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.data.TeamMode;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.rival.RivalConstants;
import com.dbzlegacy.adaptivedifficulty.rival.RivalLink;
import com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStatus;
import com.dbzlegacy.adaptivedifficulty.rival.RivalOnlinePlayers;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStore;
import com.dbzlegacy.adaptivedifficulty.team.TeamScaling;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bukkit bridge for the Difficulty Teams page - mutual rivals as teammates.
 * Player-facing colors go through {@link UltraStyle} (Ultra path via UltraGuiActions).
 */
public final class DifficultyTeamGuiApi {
    private DifficultyTeamGuiApi() {}

    public static Map<String, String> placeholders(ServerPlayer player) {
        Map<String, String> out = new HashMap<>();
        out.put("bridge_ok", "false");
        if (player == null) {
            return out;
        }
        out.put("bridge_ok", "true");
        DifficultyConfig cfg = DifficultyConfig.get();
        out.put("rival_enabled", cfg.enableRivalSystem ? "true" : "false");
        PlayerDifficultyData data = DifficultyCache.data(player);
        TeamMode mode = data.getTeamMode();
        out.put("team_mode", mode.name().toLowerCase(Locale.ROOT));
        out.put("team_mode_label", modeLabel(mode));
        DifficultySnapshot snap = DifficultyCache.get(player);
        out.put("team_bonus", String.valueOf(snap.teamThresholdBonus));
        out.put("team_contrib", String.valueOf(snap.teamContribution));
        out.put("personal_max", String.valueOf(snap.personalMax));
        out.put("available_max", String.valueOf(snap.availableMax));
        out.put("mutual_total", String.valueOf(TeamScaling.mutualRivalCount(player)));
        out.put("mutual_online", String.valueOf(TeamScaling.onlineMutualRivalCount(player)));
        out.put("mutual_teaming", String.valueOf(TeamScaling.teammates(player).size()));
        out.put("mutual_max", String.valueOf(RivalConstants.MAX_MUTUAL_RIVALS));
        out.put("team_source", TeamScaling.teamSourceLabel());
        out.put("team_name", TeamScaling.teamName(player));
        out.put("team_size", out.get("mutual_teaming"));
        out.put("bonus_percent", String.valueOf((int) cfg.teamBonusPercent));
        out.put("contrib_percent", String.valueOf((int) cfg.contributionPercent));
        out.put("proximity_blocks", String.valueOf((int) TeamScaling.contributionProximityBlocks()));
        out.put("spawn_elite_bonus", formatBonus(TeamScaling.rarityBonusPercent(
                player, TeamScaling.RarityBonus.ELITE)));
        out.put("spawn_mutant_bonus", formatBonus(TeamScaling.rarityBonusPercent(
                player, TeamScaling.RarityBonus.MUTATION)));
        out.put("spawn_boss_bonus", formatBonus(TeamScaling.rarityBonusPercent(
                player, TeamScaling.RarityBonus.BOSS_PROMOTION)));
        LmOverhaulScaledCombat.putPlaceholders(out, player);
        return out;
    }

    private static String formatBonus(double points) {
        if (points <= 0.0) {
            return "0";
        }
        if (points < 1.0) {
            return String.format(Locale.ROOT, "%.2f", points);
        }
        return String.format(Locale.ROOT, "%.1f", points);
    }

    public static List<String> linesForPage(ServerPlayer player, String page) {
        if (player == null) {
            return List.of(UltraStyle.danger("This page is not available right now. Open the Difficulty menu again."));
        }
        if (!DifficultyConfig.get().enableRivalSystem) {
            return List.of(
                    UltraStyle.LABEL + "Rival system is disabled.",
                    UltraStyle.DIM + "Ask staff to enable the rival system.");
        }
        String p = page == null ? "team" : page.trim().toLowerCase(Locale.ROOT);
        if (!"team".equals(p) && !"teams".equals(p)) {
            return List.of(UltraStyle.LABEL + "Unknown teams page. Open the Difficulty menu again.");
        }
        return teamLines(player);
    }

    /**
     * Tab-separated rival cards for GUI heads:
     * uuid, name, status, online(0/1), optedIn(0/1), near(0/1), spare, team_mode
     */
    public static List<String> mutualRivalCards(ServerPlayer player) {
        List<String> cards = new ArrayList<>();
        if (player == null) {
            return cards;
        }
        RivalPlayerRecord me = RivalStore.get().get(player.m_20148_().toString());
        if (me == null || me.rivals == null) {
            return cards;
        }
        MinecraftServer server = player.m_20194_();
        for (Map.Entry<String, RivalLink> e : me.rivals.entrySet()) {
            RivalLink link = e.getValue();
            if (link == null || !link.mutual) {
                continue;
            }
            RivalStatus st = link.status();
            if (st != RivalStatus.MUTUAL && st != RivalStatus.NEMESIS) {
                continue;
            }
            ServerPlayer other = RivalOnlinePlayers.find(server, e.getKey());
            boolean online = other != null;
            boolean optedIn = online && TeamScaling.isOptedIntoTeams(other);
            boolean near = online && TeamScaling.withinContributionRange(player, other);
            long spare = online ? TeamScaling.spareHeadroom(other) : 0L;
            String teamMode = rivalTeamModeKey(other);
            String name = link.name == null || link.name.isBlank()
                    ? (online ? other.m_7755_().getString() : e.getKey())
                    : link.name;
            cards.add(String.join("\t",
                    e.getKey(),
                    name,
                    st.label(),
                    online ? "1" : "0",
                    optedIn ? "1" : "0",
                    near ? "1" : "0",
                    String.valueOf(spare),
                    teamMode));
        }
        return cards;
    }

    /** personal_only | threshold_bonus_only | full_team_scaling | ad_off | offline */
    private static String rivalTeamModeKey(ServerPlayer other) {
        if (other == null) {
            return "offline";
        }
        PlayerDifficultyData data = DifficultyCache.data(other);
        if (!data.isPersonalEnabled()) {
            return "ad_off";
        }
        return data.getTeamMode().name().toLowerCase(Locale.ROOT);
    }

    public static String handleDo(ServerPlayer player, String action, String arg, String page) {
        if (player == null) {
            return UltraStyle.dangerLine("No player.");
        }
        String act = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if ("set_mode".equals(act) || "mode".equals(act) || "team".equals(act)) {
            return com.dbzlegacy.adaptivedifficulty.service.DifficultyActions
                    .handleArg(player, "team", arg == null ? "" : arg, page == null ? "team" : page)
                    .message();
        }
        return UltraStyle.dangerLine("Unknown teams action.");
    }

    private static List<String> teamLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        PlayerDifficultyData data = DifficultyCache.data(player);
        DifficultySnapshot snap = DifficultyCache.get(player);
        TeamMode mode = data.getTeamMode();
        lines.add(UltraStyle.LABEL + "Mode " + UltraStyle.VALUE + modeLabel(mode));
        // 4.7.56: the Overhaul stat rows live on the staff stats page, not on Rival teams.
        int onlineMutual = TeamScaling.onlineMutualRivalCount(player);
        int teaming = TeamScaling.teammates(player).size();
        lines.add(UltraStyle.LABEL + "Mutual rivals " + UltraStyle.VALUE
                + TeamScaling.mutualRivalCount(player)
                + UltraStyle.DIM + "/" + UltraStyle.VALUE + RivalConstants.MAX_MUTUAL_RIVALS
                + "  " + UltraStyle.LABEL + "online " + UltraStyle.VALUE + onlineMutual);
        if (onlineMutual > 0 && teaming < onlineMutual) {
            lines.add(UltraStyle.DIM + teaming + (teaming == 1 ? " online rival also uses" : " online rivals also use")
                    + " a team mode for bonuses.");
        }
        if (mode == TeamMode.PERSONAL_ONLY) {
            lines.add(UltraStyle.LABEL + "Personal ceiling only - no rival bonus.");
        } else {
            int bonusPct = (int) DifficultyConfig.get().teamBonusPercent;
            int contribPct = (int) DifficultyConfig.get().contributionPercent;
            int prox = (int) TeamScaling.contributionProximityBlocks();
            lines.add(UltraStyle.LABEL + "Extra tier ceiling " + UltraStyle.VALUE + "+" + snap.teamThresholdBonus
                    + UltraStyle.DIM + " (" + bonusPct + "% higher ceiling per online rival on a team mode)");
            if (mode == TeamMode.FULL_TEAM_SCALING) {
                lines.add(UltraStyle.LABEL + "Shared tier room " + UltraStyle.VALUE + "+" + snap.teamContribution
                        + UltraStyle.DIM + " (" + contribPct + "% of nearby rivals' unused room within " + prox + " blocks)");
            } else {
                lines.add(UltraStyle.DIM + "Full mode also shares nearby rivals' unused tier room.");
            }
            lines.add(UltraStyle.LABEL + "Your tier ceiling " + UltraStyle.VALUE + snap.availableMax
                    + UltraStyle.DIM + " (base " + UltraStyle.VALUE + snap.personalMax + UltraStyle.DIM + ")");
            double eliteBonus = TeamScaling.rarityBonusPercent(player, TeamScaling.RarityBonus.ELITE);
            double mutantBonus = TeamScaling.rarityBonusPercent(player, TeamScaling.RarityBonus.MUTATION);
            double bossBonus = TeamScaling.rarityBonusPercent(player, TeamScaling.RarityBonus.BOSS_PROMOTION);
            if (eliteBonus > 0.0 || mutantBonus > 0.0 || bossBonus > 0.0) {
                lines.add(UltraStyle.LABEL + "Spawn boost " + UltraStyle.VALUE + "Elite +"
                        + formatBonus(eliteBonus) + "%"
                        + UltraStyle.DIM + " " + UltraStyle.SEP + " Mutant +" + formatBonus(mutantBonus) + "%"
                        + UltraStyle.DIM + " " + UltraStyle.SEP + " Boss +" + formatBonus(bossBonus) + "%");
            }
        }
        lines.add(UltraStyle.DIM + "Only mutual rivals using Threshold or Full count.");
        lines.add(UltraStyle.DIM + "Declare and accept rivals in the Rival menu (/rival).");
        return lines;
    }

    private static String modeLabel(TeamMode mode) {
        if (mode == null) {
            return "Personal";
        }
        return switch (mode) {
            case PERSONAL_ONLY -> "Personal";
            case THRESHOLD_BONUS_ONLY -> "Threshold";
            case FULL_TEAM_SCALING -> "Full";
        };
    }

}
