package com.dbzlegacy.adaptivedifficulty.gui;

import com.dbzlegacy.adaptivedifficulty.calc.LmOverhaulScaledCombat;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings;
import com.dbzlegacy.adaptivedifficulty.sparring.SparCombat;
import com.dbzlegacy.adaptivedifficulty.sparring.SparPlayerRuntime;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;

/**
 * Public static API for Bukkit companion reflection ({@code LegacyMechanicsGUI}).
 * Status maps, lore lines, and {@code /spar do} action dispatch without reopening menus —
 * the companion plugin owns inventory reopen.
 */
public final class SparGuiApi {
    private SparGuiApi() {}

    public static Map<String, String> placeholders(ServerPlayer player) {
        Map<String, String> out = new HashMap<>();
        out.put("bridge_ok", "false");
        out.put("system_enabled", "false");
        if (player == null) {
            return out;
        }
        boolean enabled = DifficultyConfig.get().enableSparringSystem;
        out.put("bridge_ok", "true");
        out.put("system_enabled", enabled ? "true" : "false");
        if (!enabled) {
            return out;
        }
        SparPlayerRuntime rt = SparringSystem.runtime(player.m_20148_());
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        boolean sessionActive = rt != null && rt.active;
        out.put("sessionActive", sessionActive ? "true" : "false");
        out.put("session_active", out.get("sessionActive"));
        String partner = "";
        if (sessionActive && rt.partner != null && player.m_20194_() != null) {
            ServerPlayer p = player.m_20194_().m_6846_().m_11259_(rt.partner);
            if (p != null) {
                partner = p.m_7755_().getString();
            }
        }
        out.put("partner", partner);
        out.put("session_tp", sessionActive ? String.valueOf((int) rt.sessionTp) : "0");
        out.put("perfect", sessionActive && rt.sessionPerfect ? "true" : "false");
        out.put("tpMsg", SparStore.get().tpMessagesOn(player.m_20148_()) ? "true" : "false");
        out.put("tp_msg", out.get("tpMsg"));
        out.put("mentorTpMsg", SparStore.get().mentorTpMessagesOn(player.m_20148_()) ? "true" : "false");
        out.put("mentor_tp_msg", out.get("mentorTpMsg"));

        boolean hasMentor = bond != null
                && bond.mentorUuid != null && !bond.mentorUuid.isBlank();
        int appCount = bond == null ? 0 : bond.apprenticeCount();
        boolean hasApprentice = appCount > 0;
        boolean bonded = hasMentor || hasApprentice;
        out.put("has_mentor", hasMentor ? "true" : "false");
        out.put("has_apprentice", hasApprentice ? "true" : "false");
        out.put("apprentice_count", String.valueOf(appCount));
        out.put("apprentice_max", String.valueOf(SparringSystem.MAX_APPRENTICES));
        out.put("mentor_bonded", bonded ? "true" : "false");
        String mentorDisplay = "";
        if (hasMentor) {
            mentorDisplay = bond.mentorName == null ? "" : bond.mentorName.trim();
            if (mentorDisplay.isBlank() && bond.mentorUuid != null && !bond.mentorUuid.isBlank()
                    && player.m_20194_() != null) {
                try {
                    java.util.UUID mu = java.util.UUID.fromString(bond.mentorUuid.trim());
                    ServerPlayer onlineMentor = player.m_20194_().m_6846_().m_11259_(mu);
                    if (onlineMentor != null) {
                        mentorDisplay = onlineMentor.m_7755_().getString();
                        bond.mentorName = mentorDisplay;
                        SparStore.get().markDirty();
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        out.put("mentor_name", mentorDisplay);
        String appSummary = hasApprentice ? bond.apprenticeNamesSummary() : "";
        out.put("apprentice_name", appSummary);
        out.put("apprentices", appSummary);
        if (hasMentor && hasApprentice) {
            out.put("mentor_role", "both");
            out.put("mentor", blank(mentorDisplay, "?") + " / " + blank(appSummary, "?"));
            out.put("streak", String.valueOf(bond.streakCurrent));
            out.put("streak_best", String.valueOf(bond.streakBest));
        } else if (hasApprentice) {
            out.put("mentor_role", "mentor");
            out.put("mentor", appSummary);
            out.put("streak", String.valueOf(bond.streakCurrent));
            out.put("streak_best", String.valueOf(bond.streakBest));
        } else if (hasMentor) {
            out.put("mentor_role", "apprentice");
            out.put("mentor", mentorDisplay);
            out.put("streak", String.valueOf(bond.streakCurrent));
            out.put("streak_best", String.valueOf(bond.streakBest));
        } else {
            out.put("mentor_role", "");
            out.put("mentor", "");
            out.put("streak", "0");
            out.put("streak_best", "0");
        }
        int pending = SparringSystem.pendingMentorInviteCount(player);
        out.put("pending_invites", String.valueOf(pending));
        out.put("pendingInvites", out.get("pending_invites"));
        DojoRankings.ensureSeason();
        String dojoKey = DojoRankings.homeDojoKey(player);
        out.put("dojo_key", dojoKey == null ? "" : dojoKey);
        out.put("dojo_name", dojoKey == null ? "" : DojoRankings.dojoDisplayName(dojoKey));
        out.put("dojo_master", DojoRankings.isDojoMaster(player) ? "true" : "false");
        int dojoRank = dojoKey == null ? 0 : DojoRankings.dojoRank(dojoKey, "rp");
        out.put("dojo_rank", dojoRank <= 0 ? "-" : String.valueOf(dojoRank));
        SparStore.DojoEntry dojoEntry = dojoKey == null ? null : SparStore.get().dojoSeason == null
                || SparStore.get().dojoSeason.leaderboard == null
                ? null
                : SparStore.get().dojoSeason.leaderboard.get(dojoKey.toLowerCase(java.util.Locale.ROOT));
        out.put("dojo_rp", dojoEntry == null ? "0" : String.valueOf((int) dojoEntry.seasonRp));
        out.put("dojo_wins", dojoEntry == null ? "0" : String.valueOf(dojoEntry.wins));
        out.put("dojo_display_name", dojoKey == null ? "" : DojoRankings.dojoDisplayName(dojoKey));
        out.put("dojo_banner", dojoKey == null ? "WHITE_BANNER" : DojoRankings.dojoBannerMaterial(dojoKey));
        boolean warIncoming = DojoRankings.hasIncomingWarChallenge(player);
        out.put("dojo_war_incoming", warIncoming ? "true" : "false");
        out.put("dojo_war_from", warIncoming ? DojoRankings.incomingWarFromName(player) : "");
        int warPending = DojoRankings.pendingDojoWarCount(player);
        out.put("dojo_war_pending", String.valueOf(warPending));
        out.put("dojo_war_pending_count", out.get("dojo_war_pending"));
        LmOverhaulScaledCombat.putPlaceholders(out, player);
        return out;
    }

    public static List<String> statsLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return SparringSystem.statsLines(player);
    }

    /** Lifetime / bond summary without Combat or full report dump (Ultra stats page). */
    public static List<String> statsOverviewLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return SparringSystem.statsOverviewLines(player);
    }

    /** Last-fight button cards ({@code index\tlabel}). */
    public static List<String> recentFightCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        return SparringSystem.recentFightCards(player);
    }

    /** Full report for one recent fight (1-based index). */
    public static List<String> recentReportLines(ServerPlayer player, int index) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        List<String> lines = SparringSystem.recentReportLines(player, index);
        if (lines.isEmpty()) {
            return List.of(UltraStyle.LABEL + "That fight report is no longer available.");
        }
        return lines;
    }

    public static List<String> topLines(ServerPlayer player, String category) {
        if (!DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return SparringSystem.topLines(category, 10);
    }

    public static List<String> dojoTopLines(ServerPlayer player, String category) {
        if (!DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return SparringSystem.dojoTopLines(category, 10);
    }

    /** Dojo ladder cards for GUI banners ({@code rank\tkey\tname\tbanner\tvalue\troster}). */
    public static List<String> dojoTopCards(ServerPlayer player, String category) {
        if (!DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        String cat = category == null || category.isBlank() ? "rp" : category.trim();
        return SparringSystem.dojoTopCards(cat, 14);
    }

    public static List<String> dojoInfoLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return SparringSystem.dojoInfoLines(player);
    }

    public static List<String> dojoWarLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return DojoRankings.warInfoLines(player);
    }

    public static List<String> rivalDojoCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        return SparringSystem.rivalDojoCards(player);
    }

    public static List<String> dojoHallOfFameLines(ServerPlayer player) {
        if (!DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return SparringSystem.dojoHallOfFameLines();
    }

    public static List<String> dojoMemberLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return SparringSystem.dojoMemberLines(player);
    }

    public static List<String> ownedDojoMemberLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return DojoRankings.ownedDojoMemberLines(player);
    }

    public static List<String> joinedDojoMemberLines(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of(UltraStyle.DANGER + "Sparring system is disabled.");
        }
        return DojoRankings.joinedDojoMemberLines(player);
    }

    public static boolean ownsDojo(ServerPlayer player) {
        return player != null && DojoRankings.isDojoMaster(player);
    }

    public static boolean joinedDojo(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        return bond != null && bond.mentorUuid != null && !bond.mentorUuid.isBlank();
    }

    public static List<String> dojoMemberCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        return SparringSystem.dojoMemberCards(player);
    }

    public static List<String> mentorLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.AQUA + "Training bonds");
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            lines.add(UltraStyle.DANGER + "Sparring system is disabled.");
            return lines;
        }
        SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
        boolean hasMentor = bond != null
                && bond.mentorUuid != null && !bond.mentorUuid.isBlank();
        int appCount = bond == null ? 0 : bond.apprenticeCount();
        boolean hasApprentice = appCount > 0;
        if (hasMentor || hasApprentice) {
            if (hasMentor) {
                lines.add(UltraStyle.LABEL + "Your mentor " + UltraStyle.VALUE + blank(bond.mentorName, "-"));
            }
            if (hasApprentice) {
                lines.add(UltraStyle.LABEL + "Your apprentices " + UltraStyle.VALUE + appCount + UltraStyle.DIM + "/" + UltraStyle.VALUE + SparringSystem.MAX_APPRENTICES);
                lines.add(UltraStyle.DIM + "  " + UltraStyle.VALUE + bond.apprenticeNamesSummary());
                float sharePct = SparCombat.MENTOR_SHARE_PCT * 100.0f / Math.max(1, appCount);
                lines.add(UltraStyle.LABEL + "Mentor share " + UltraStyle.VALUE + String.format(java.util.Locale.ROOT, "%.1f", sharePct)
                        + "% " + UltraStyle.DIM + "each (dojo split)");
                lines.add(UltraStyle.LABEL + "Dojo peers spar " + UltraStyle.GREEN + "+"
                        + Math.round(SparCombat.DOJO_PEER_SPAR_BONUS_PCT * 100) + "% TP");
            }
            if (hasMentor) {
                lines.add(UltraStyle.LABEL + "Spar mentor " + UltraStyle.GREEN + "+"
                        + Math.round(SparCombat.MENTOR_SPAR_BONUS_PCT * 100)
                        + "% " + UltraStyle.DIM + "- peers +"
                        + Math.round(SparCombat.DOJO_PEER_SPAR_BONUS_PCT * 100) + "%");
            }
            lines.add(UltraStyle.LABEL + "Streak " + UltraStyle.VALUE + bond.streakCurrent + " " + UltraStyle.DIM + "best " + UltraStyle.VALUE + bond.streakBest);
        } else {
            lines.add(UltraStyle.LABEL + "Invite apprentices or ask a mentor from Actions.");
            lines.add(UltraStyle.DIM + "Dojo: up to " + UltraStyle.VALUE + SparringSystem.MAX_APPRENTICES + " " + UltraStyle.DIM + "apprentices - one master");
            lines.add(UltraStyle.DIM + "Invite lasts " + UltraStyle.VALUE + "1 hour " + UltraStyle.DIM + "- peers spar +"
                    + Math.round(SparCombat.DOJO_PEER_SPAR_BONUS_PCT * 100) + "% TP");
            lines.add(SparringSystem.bondStatus(player));
        }
        int pending = SparringSystem.pendingMentorInviteCount(player);
        if (pending > 0) {
            lines.add(UltraStyle.YELLOW + "Pending invites " + UltraStyle.VALUE + pending + " " + UltraStyle.DIM + "- open Bond invites (" + UltraStyle.VALUE + "1h" + UltraStyle.DIM + ")");
        } else {
            lines.add(UltraStyle.DIM + "Open Actions to recruit, request a master, or release.");
        }
        return lines;
    }

    /** Encoded pending mentor invites (IN + OUT) for head boards. */
    public static List<String> pendingMentorInviteCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        return SparringSystem.pendingMentorInviteCards(player);
    }

    /** Incoming mentor invite args for Accept/Decline pickers. */
    public static List<String> pendingIncomingMentorArgs(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        return SparringSystem.pendingIncomingMentorArgs(player);
    }

    /** Pending dojo war challenges (incoming + outgoing) for GUI boards. */
    public static List<String> pendingDojoWarCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        return SparringSystem.pendingDojoWarCards(player);
    }

    /** Mentor's dojo roster cards ({@code uuid\tname}) for Release pickers. */
    public static List<String> apprenticeCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        return SparringSystem.apprenticeCards(player);
    }

    /**
     * Membership dojo cards ({@code role\tuuid\tname}) — mentor first, then apprentices.
     * Roles: {@code mentor}, {@code you}, {@code apprentice}.
     */
    public static List<String> membershipDojoCards(ServerPlayer player) {
        if (player == null || !DifficultyConfig.get().enableSparringSystem) {
            return List.of();
        }
        return SparringSystem.membershipDojoCards(player);
    }

    public static List<String> pendingMentorLines(ServerPlayer player) {
        List<String> cards = pendingMentorInviteCards(player);
        List<String> lines = new ArrayList<>();
        if (cards.isEmpty()) {
            lines.add(UltraStyle.LABEL + "No pending mentor invites.");
            lines.add(UltraStyle.DIM + "Outgoing: you invited someone.");
            lines.add(UltraStyle.DIM + "Incoming: they invited you - Accept or Decline.");
            return lines;
        }
        lines.add(PENDING_MENTOR_HEADER);
        for (String card : cards) {
            String line = pendingMentorLine(card);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Heading above the pending mentor invite lines. */
    public static final String PENDING_MENTOR_HEADER =
            UltraStyle.YELLOW + UltraStyle.BOLD + "Pending Mentor Invites";

    /**
     * The text line for one pending invite card, or null when the card has too few fields.
     * The Ultra invites page renders this per card from the same page slice as its buttons.
     */
    public static String pendingMentorLine(String card) {
        String[] p = card == null ? new String[0] : card.split("\t", -1);
        if (p.length < 3) {
            return null;
        }
        String name = p[1];
        String dir = p[2];
        boolean online = p.length > 4 && "1".equals(p[4]);
        String kind = p.length > 5 ? p[5] : "";
        String role = "mentor".equalsIgnoreCase(kind) ? "Mentor" : "Apprentice";
        String onlineWord = online ? "online" : "offline";
        if ("IN".equals(dir)) {
            return UltraStyle.GREEN + "Incoming " + UltraStyle.VALUE + name + " " + UltraStyle.DIM + "(" + onlineWord + ")"
                    + " " + UltraStyle.DIM + "-> you as " + role;
        }
        return UltraStyle.GOLD + "Outgoing " + UltraStyle.VALUE + name + " " + UltraStyle.DIM + "(" + onlineWord + ")"
                + " " + UltraStyle.DIM + "-> them as " + role;
    }

    /** Lore lines for a GUI page. */
    public static List<String> linesForPage(ServerPlayer player, String page) {
        String raw = page == null || page.isBlank() ? "main" : page.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("top_") || lower.startsWith("top ")) {
            String cat = lower.startsWith("top_")
                    ? lower.substring(4).trim()
                    : lower.substring(4).trim();
            if (cat.isBlank()) {
                cat = "tp";
            }
            return topLines(player, cat);
        }
        if (lower.startsWith("dojo_top_") || lower.startsWith("dojo_top ")) {
            String cat = lower.startsWith("dojo_top_")
                    ? lower.substring(9).trim()
                    : lower.substring(9).trim();
            if (cat.isBlank()) {
                cat = "rp";
            }
            return dojoTopLines(player, cat);
        }
        return switch (lower) {
            case "stats", "statistics" -> statsLines(player);
            case "top", "leaderboard" -> topLines(player, "tp");
            case "dojo_top", "dojo_rank", "dojo_rankings", "dojo_leaderboard" -> dojoTopLines(player, "rp");
            case "dojo_info" -> {
                List<String> merged = new ArrayList<>(dojoInfoLines(player));
                merged.add("");
                merged.addAll(dojoTopLines(player, "rp"));
                yield merged;
            }
            case "dojo_war" -> dojoWarLines(player);
            case "dojo_hof", "dojo_hall", "dojo_hall_of_fame" -> dojoHallOfFameLines(player);
            case "dojo_members", "dojo_contributions" -> dojoMemberLines(player);
            case "mentor", "actions", "dojo", "roster", "apprentices" -> mentorLines(player);
            case "pending", "invites", "pendinginvites" -> pendingMentorLines(player);
            case "help" -> List.of(
                    UltraStyle.HEADER + "/spar " + UltraStyle.DIM + "- Sparring TP",
                    UltraStyle.LABEL + "Mentor bonds: GUI -> Training bonds",
                    UltraStyle.YELLOW + "/spar stats|end|top [category]",
                    UltraStyle.DIM + "Commands still work for staff / scripts"
            );
            default -> {
                Map<String, String> ph = placeholders(player);
                List<String> lore = new ArrayList<>();
                if ("true".equalsIgnoreCase(ph.get("sessionActive"))) {
                    lore.add(UltraStyle.GREEN + "Session ACTIVE " + UltraStyle.DIM + "with " + UltraStyle.VALUE + blank(ph.get("partner"), "?")
                            + "  " + UltraStyle.LABEL + "TP " + UltraStyle.VALUE + ph.getOrDefault("session_tp", "0"));
                    if ("true".equalsIgnoreCase(ph.get("perfect"))) {
                        lore.add(UltraStyle.GOLD + "Perfect spar " + UltraStyle.LABEL + "- hits are close enough for the training bonus.");
                    }
                } else {
                    lore.add(UltraStyle.LABEL + "No sparring session right now - hit each other within 30 blocks to start one.");
                }
                if ("true".equalsIgnoreCase(ph.get("mentor_bonded"))) {
                    String role = ph.getOrDefault("mentor_role", "?");
                    if ("both".equalsIgnoreCase(role)) {
                        lore.add(UltraStyle.AQUA + "Mentor " + UltraStyle.VALUE + blank(ph.get("mentor_name"), "?")
                                + " " + UltraStyle.DIM + "- " + UltraStyle.AQUA + "Apprentice " + UltraStyle.VALUE + blank(ph.get("apprentice_name"), "?")
                                + "  " + UltraStyle.LABEL + "streak " + UltraStyle.VALUE + ph.getOrDefault("streak", "0"));
                    } else if ("mentor".equalsIgnoreCase(role)) {
                        lore.add(UltraStyle.AQUA + "Mentoring " + UltraStyle.VALUE + blank(ph.get("apprentice_name"), "?")
                                + "  " + UltraStyle.LABEL + "streak " + UltraStyle.VALUE + ph.getOrDefault("streak", "0"));
                    } else {
                        lore.add(UltraStyle.AQUA + "Apprentice of " + UltraStyle.VALUE + blank(ph.get("mentor_name"), "?")
                                + "  " + UltraStyle.LABEL + "streak " + UltraStyle.VALUE + ph.getOrDefault("streak", "0"));
                    }
                } else {
                    lore.add(UltraStyle.LABEL + "No mentor bond. " + UltraStyle.DIM + "Use Training bonds to invite");
                }
                yield lore;
            }
        };
    }

    /**
     * Dispatch {@code /spar do} actions. Does not reopen GUI — caller reopens.
     *
     * @param action e.g. {@code page}, {@code end}, {@code mentor}
     * @param arg    e.g. page name, {@code accept}/{@code decline}/{@code remove}
     * @param page   return page (unused for page action; used by caller to reopen)
     */
    public static String handleDo(ServerPlayer player, String action, String arg, String page) {
        if (player == null) {
            return UltraStyle.DANGER + "This only works in-game - console can't open this menu.";
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        String a = arg == null ? "" : arg.trim();
        if ("admin".equals(act)) {
            return handleAdmin(player, a);
        }
        if (!DifficultyConfig.get().enableSparringSystem) {
            return UltraStyle.DANGER + "Sparring system is disabled.";
        }
        if ("page".equals(act) || "refresh".equals(act)) {
            return "";
        }
        if ("end".equals(act) || "stop".equals(act)) {
            return SparringSystem.endCommand(player);
        }
        if ("tpmsg".equals(act) || "tp_msg".equals(act) || "tpmessage".equals(act)) {
            if ("toggle".equalsIgnoreCase(a) || a.isBlank()) {
                boolean next = !SparStore.get().tpMessagesOn(player.m_20148_());
                return SparringSystem.setTpMsg(player, next);
            }
            if ("on".equalsIgnoreCase(a) || "true".equalsIgnoreCase(a)) {
                return SparringSystem.setTpMsg(player, true);
            }
            if ("off".equalsIgnoreCase(a) || "false".equalsIgnoreCase(a)) {
                return SparringSystem.setTpMsg(player, false);
            }
            return UltraStyle.DANGER + "Usage: spar do tpmsg toggle|on|off";
        }
        if ("mentor_tpmsg".equals(act) || "mentor_tp_msg".equals(act) || "mentortpmsg".equals(act)) {
            if ("toggle".equalsIgnoreCase(a) || a.isBlank()) {
                boolean next = !SparStore.get().mentorTpMessagesOn(player.m_20148_());
                return SparringSystem.setMentorTpMsg(player, next);
            }
            if ("on".equalsIgnoreCase(a) || "true".equalsIgnoreCase(a)) {
                return SparringSystem.setMentorTpMsg(player, true);
            }
            if ("off".equalsIgnoreCase(a) || "false".equalsIgnoreCase(a)) {
                return SparringSystem.setMentorTpMsg(player, false);
            }
            return UltraStyle.DANGER + "Usage: spar do mentor_tpmsg toggle|on|off";
        }
        if ("mentor".equals(act)) {
            String sub = a.toLowerCase(Locale.ROOT).trim();
            if (sub.equals("accept") || sub.startsWith("accept ") || sub.startsWith("accept:")) {
                String from = "";
                if (sub.startsWith("accept")) {
                    from = a.substring(6).trim();
                    if (from.startsWith(":")) {
                        from = from.substring(1).trim();
                    }
                }
                return SparringSystem.mentorAccept(player, from.isBlank() ? null : from);
            }
            if (sub.equals("decline") || sub.equals("deny")
                    || sub.startsWith("decline ") || sub.startsWith("decline:")
                    || sub.startsWith("deny ") || sub.startsWith("deny:")) {
                String prefix = sub.startsWith("deny") ? "deny" : "decline";
                String from = a.length() > prefix.length() ? a.substring(prefix.length()).trim() : "";
                if (from.startsWith(":")) {
                    from = from.substring(1).trim();
                }
                return SparringSystem.mentorDecline(player, from.isBlank() ? null : from);
            }
            if (sub.equals("cancel") || sub.startsWith("cancel ") || sub.startsWith("cancel:")) {
                String target = a.length() > 6 ? a.substring(6).trim() : "";
                if (target.startsWith(":")) {
                    target = target.substring(1).trim();
                }
                return SparringSystem.mentorCancelInvite(player, target);
            }
            if (sub.equals("remove") || sub.equals("clear")) {
                // Ambiguous — ask which side. Prefer explicit leave/release from GUI.
                SparStore.MentorBond bond = SparStore.get().bond(player.m_20148_());
                boolean hasMentor = bond != null
                        && bond.mentorUuid != null && !bond.mentorUuid.isBlank();
                boolean hasApprentice = bond != null && bond.apprenticeCount() > 0;
                if (hasMentor && hasApprentice) {
                    return UltraStyle.YELLOW + "Leave your mentor, or release one of your apprentices?";
                }
                return SparringSystem.removeBond(player);
            }
            if (sub.equals("leave") || sub.equals("leavementor") || sub.equals("leave_mentor")
                    || sub.equals("remove_mentor") || sub.equals("removementor")) {
                return SparringSystem.removeMentor(player);
            }
            if (sub.equals("release") || sub.equals("releaseapprentice") || sub.equals("release_apprentice")
                    || sub.equals("remove_apprentice") || sub.equals("removeapprentice")
                    || sub.startsWith("release ") || sub.startsWith("release:")) {
                String who = "";
                if (sub.startsWith("release ") || sub.startsWith("release:")) {
                    who = a.length() > 7 ? a.substring(7).trim() : "";
                    if (who.startsWith(":")) {
                        who = who.substring(1).trim();
                    }
                } else if (!a.isBlank() && a.contains(" ")) {
                    // "release uuid:..." already handled above; leftover arg after release
                    who = "";
                }
                // Prefer explicit arg after the subcommand token when present in `a`
                if (who.isBlank() && a.toLowerCase(Locale.ROOT).startsWith("release")) {
                    String rest = a.length() > 7 ? a.substring(7).trim() : "";
                    if (rest.startsWith(":")) {
                        rest = rest.substring(1).trim();
                    }
                    who = rest;
                }
                return SparringSystem.removeApprentice(player, who.isBlank() ? null : who);
            }
            return UltraStyle.DANGER + "Usage: spar do mentor accept|decline|cancel|leave|release [player]";
        }
        if ("mentor_leave".equals(act) || "mentorleave".equals(act) || "leave_mentor".equals(act)) {
            return SparringSystem.removeMentor(player);
        }
        if ("mentor_release".equals(act) || "mentorrelease".equals(act)
                || "release_apprentice".equals(act) || "apprentice_remove".equals(act)) {
            return SparringSystem.removeApprentice(player, a.isBlank() ? null : a);
        }
        if ("mentor_accept".equals(act) || "mentoraccept".equals(act)) {
            return SparringSystem.mentorAccept(player, a.isBlank() ? null : a);
        }
        if ("mentor_decline".equals(act) || "mentordecline".equals(act) || "mentor_deny".equals(act)) {
            return SparringSystem.mentorDecline(player, a.isBlank() ? null : a);
        }
        if ("mentor_cancel".equals(act) || "mentorcancel".equals(act)) {
            return SparringSystem.mentorCancelInvite(player, a);
        }
        if ("mentor_invite".equals(act) || "mentorinvite".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a player below, then invite them as your apprentice.";
            }
            ServerPlayer target = resolveOnline(player, a);
            if (target == null) {
                return UltraStyle.DANGER + "Player not online: " + a;
            }
            return SparringSystem.mentorInvite(player, target);
        }
        if ("apprentice_invite".equals(act) || "apprenticeinvite".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a player below, then ask them to be your mentor.";
            }
            ServerPlayer target = resolveOnline(player, a);
            if (target == null) {
                return UltraStyle.DANGER + "Player not online: " + a;
            }
            return SparringSystem.apprenticeInvite(player, target);
        }
        if ("dojo".equals(act)) {
            String sub = a.toLowerCase(Locale.ROOT).trim();
            if (sub.equals("accept") || sub.equals("accept_war") || sub.equals("war_accept")) {
                return SparringSystem.dojoAcceptWar(player);
            }
            if (sub.equals("decline") || sub.equals("decline_war") || sub.equals("war_decline")) {
                return SparringSystem.dojoDeclineWar(player);
            }
            if (sub.startsWith("challenge ") || sub.startsWith("challenge:")) {
                String who = a.length() > 9 ? a.substring(9).trim() : "";
                if (who.startsWith(":")) {
                    who = who.substring(1).trim();
                }
                if (who.isBlank()) {
                    return UltraStyle.DANGER + "Select a rival dojo master below.";
                }
                ServerPlayer target = resolveOnline(player, who);
                if (target == null) {
                    return UltraStyle.DANGER + "Player not online: " + who;
                }
                return SparringSystem.dojoChallenge(player, target);
            }
            return UltraStyle.DANGER + "Usage: spar do dojo accept|decline|challenge <master>";
        }
        if ("dojo_challenge".equals(act) || "dojo_war".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a rival dojo master below.";
            }
            String who = a.trim();
            if (who.regionMatches(true, 0, "uuid:", 0, 5)) {
                return SparringSystem.dojoChallengeByMasterUuid(player, who.substring(5).trim());
            }
            if (who.matches("[0-9a-fA-F\\-]{32,36}")) {
                return SparringSystem.dojoChallengeByMasterUuid(player, who);
            }
            ServerPlayer target = resolveOnline(player, who);
            if (target != null) {
                return SparringSystem.dojoChallenge(player, target);
            }
            return UltraStyle.DANGER + "Player not online: " + who;
        }
        if ("dojo_accept".equals(act) || "dojo_war_accept".equals(act)) {
            return SparringSystem.dojoAcceptWar(player);
        }
        if ("dojo_decline".equals(act) || "dojo_war_decline".equals(act)) {
            return SparringSystem.dojoDeclineWar(player);
        }
        if ("dojo_war_cancel".equals(act) || "dojo_war_revoke".equals(act) || "dojo_cancel".equals(act)) {
            return SparringSystem.dojoRevokeWar(player, a);
        }
        if ("dojo_name".equals(act) || "dojo_rename".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Usage: /spar dojo name <name>";
            }
            return SparringSystem.dojoSetName(player, a);
        }
        if ("dojo_banner".equals(act) || "dojo_set_banner".equals(act)) {
            if (a.isBlank()) {
                return UltraStyle.DANGER + "Select a banner from the Banner menu.";
            }
            return SparringSystem.dojoSetBanner(player, a);
        }
        return UltraStyle.DANGER + "Something went wrong. Try again, or ask staff if it keeps happening.";
    }

    /** Staff: save / status / mentor resetcd — used by GUI + {@code /spar admin …}. */
    public static String handleAdmin(ServerPlayer player, String arg) {
        if (!StaffAccess.isStaff(player)) {
            return UltraStyle.DANGER + "Staff only.";
        }
        String raw = arg == null ? "" : arg.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("save")) {
            SparStore.get().markDirty();
            SparStore.get().save();
            return UltraStyle.YELLOW + "Spar store saved.";
        }
        if (lower.startsWith("status")) {
            boolean on = DifficultyConfig.get().enableSparringSystem;
            return UltraStyle.GOLD + "Spar admin status\n"
                    + UltraStyle.LABEL + "enabled " + UltraStyle.VALUE + (on ? "ON" : "OFF") + "\n"
                    + UltraStyle.LABEL + "bonds " + UltraStyle.VALUE + SparStore.get().bondsByPlayer.size() + "\n"
                    + UltraStyle.DIM + SparStore.path();
        }
        if (lower.startsWith("resetcd") || lower.startsWith("mentor") || lower.contains("resetcd")) {
            String targetArg = raw;
            if (lower.startsWith("mentor")) {
                targetArg = raw.replaceFirst("(?i)^mentor[_\\s]*resetcd\\s*", "").trim();
            } else if (lower.startsWith("resetcd")) {
                targetArg = raw.replaceFirst("(?i)^resetcd\\s*:?\\s*", "").trim();
            }
            ServerPlayer target = player;
            if (targetArg != null && !targetArg.isBlank()
                    && !targetArg.equalsIgnoreCase("0")
                    && !targetArg.equalsIgnoreCase("self")) {
                ServerPlayer found = resolveOnline(player, targetArg);
                if (found == null) {
                    return UltraStyle.DANGER + "Player not online: " + targetArg;
                }
                target = found;
            }
            return SparringSystem.resetMentorCd(player, target);
        }
        return UltraStyle.HEADER + "/spar admin\n"
                + UltraStyle.YELLOW + "/spar admin save " + UltraStyle.LABEL + "- save sparring data to disk\n"
                + UltraStyle.YELLOW + "/spar admin status " + UltraStyle.LABEL + "- enabled + path\n"
                + UltraStyle.LABEL + "Clear mentor cooldown\n"
                + UltraStyle.DIM + "/spar admin mentor resetcd [player]\n"
                + UltraStyle.DIM + "GUI buttons call these directly (no Forge perm-level gate).";
    }

    /**
     * Resolve an online player by {@code uuid:&lt;uuid&gt;} or by exact / case-insensitive name
     * from the caller's server.
     */
    public static ServerPlayer resolveOnline(ServerPlayer from, String name) {
        if (from == null || name == null || name.isBlank()) {
            return null;
        }
        MinecraftServer server = from.m_20194_();
        if (server == null) {
            return null;
        }
        String raw = name.trim();
        if (raw.regionMatches(true, 0, "uuid:", 0, 5)) {
            String id = raw.substring(5).trim();
            if (id.isBlank()) {
                return null;
            }
            try {
                return server.m_6846_().m_11259_(java.util.UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        ServerPlayer exact = server.m_6846_().m_11255_(raw);
        if (exact != null) {
            return exact;
        }
        String want = raw.toLowerCase(Locale.ROOT);
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            if (p.m_7755_().getString().toLowerCase(Locale.ROOT).equals(want)) {
                return p;
            }
        }
        return null;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
