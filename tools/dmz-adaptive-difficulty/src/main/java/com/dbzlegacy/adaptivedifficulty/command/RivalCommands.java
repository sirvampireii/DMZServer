package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.rival.RivalChallengeManager;
import com.dbzlegacy.adaptivedifficulty.rival.RivalSpectator;
import com.dbzlegacy.adaptivedifficulty.rival.RivalProgression;
import com.dbzlegacy.adaptivedifficulty.rival.RivalStore;
import com.dbzlegacy.adaptivedifficulty.rival.RivalSystem;
import com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.RivalMenu;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.rival.RivalPlayerRecord;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** {@code /lm rival} — Rival System 4.7.10 command surface. */
public final class RivalCommands {
    private RivalCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new RivalCommands());
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.m_82127_("rival")
                .executes(ctx -> gui(ctx.getSource(), "main"))
                .then(Commands.m_82127_("gui").executes(ctx -> gui(ctx.getSource(), "main")))
                .then(Commands.m_82127_("help").executes(ctx -> playerHelp(ctx.getSource())))
                .then(Commands.m_82127_("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.m_82127_("stats")
                        .executes(ctx -> stats(ctx.getSource(), null))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> stats(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("top")
                        .executes(ctx -> top(ctx.getSource(), "rp"))
                        .then(LmCommandSuggestions.word("category", LmCommandSuggestions.RIVAL_TOP_CATEGORIES)
                                .executes(ctx -> top(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "category")))))
                .then(Commands.m_82127_("season").executes(ctx -> gui(ctx.getSource(), "season")))
                .then(Commands.m_82127_("quests").executes(ctx -> gui(ctx.getSource(), "quests")))
                .then(Commands.m_82127_("achievements").executes(ctx -> gui(ctx.getSource(), "achievements")))
                .then(Commands.m_82127_("hof").executes(ctx -> gui(ctx.getSource(), "hof")))
                .then(Commands.m_82127_("journal").executes(ctx -> gui(ctx.getSource(), "journal")))
                .then(Commands.m_82127_("title").executes(ctx -> gui(ctx.getSource(), "title")))
                .then(CommandAccess.staffLiteral("spectate")
                        .executes(ctx -> spectate(ctx.getSource(), null))
                        .then(Commands.m_82127_("stop").executes(ctx -> spectateStop(ctx.getSource())))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> spectate(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("do")
                        // Full GUI surface via RivalGuiApi.handleDo — greedy rest so uuid:/@ pages work.
                        // (Old tree only knew page/tpmsg/instinct/challenge and silently no-oped CMI clicks.)
                        .then(Commands.m_82129_("action", StringArgumentType.word())
                                .executes(ctx -> guiDo(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "action"),
                                        "",
                                        "main"))
                                .then(Commands.m_82129_("rest", StringArgumentType.greedyString())
                                        .executes(ctx -> guiDoRest(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "action"),
                                                StringArgumentType.getString(ctx, "rest"))))))
                .then(CommandAccess.staffLiteral("declare")
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> declare(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(CommandAccess.staffLiteral("accept")
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> accept(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(CommandAccess.staffLiteral("decline")
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> decline(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(CommandAccess.staffLiteral("remove")
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> remove(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("tpmsg")
                        .executes(ctx -> tpmsg(ctx.getSource(), null))
                        .then(Commands.m_82127_("on").executes(ctx -> tpmsg(ctx.getSource(), true)))
                        .then(Commands.m_82127_("off").executes(ctx -> tpmsg(ctx.getSource(), false))))
                .then(CommandAccess.staffLiteral("challenge")
                        .then(Commands.m_82127_("send")
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> challengeSend(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                1))
                                        .then(Commands.m_82129_("minutes", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> challengeSend(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "minutes"))))))
                        .then(Commands.m_82127_("accept").executes(ctx -> challengeAccept(ctx.getSource(), ""))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> challengeAccept(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.m_82127_("decline").executes(ctx -> challengeDecline(ctx.getSource(), ""))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> challengeDecline(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.m_82127_("cancel").executes(ctx -> challengeCancel(ctx.getSource(), ""))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> challengeCancel(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "player"))))))
                .then(LmCommandSuggestions.playerWord("player")
                        .executes(ctx -> silent(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"))));

        // 4.7.88: the old rival root is /lm rival; its admin branch is /lm admin rival.
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.m_82127_("rival")
                .executes(ctx -> adminHelp(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> adminHelp(ctx.getSource())))
                .then(Commands.m_82127_("save").executes(ctx -> save(ctx.getSource())))
                .then(Commands.m_82127_("refresh").executes(ctx -> refresh(ctx.getSource())))
                .then(Commands.m_82127_("status").executes(ctx -> adminStatus(ctx.getSource())))
                // 4.8.3: staff RP changes, a staff end of a challenge, and the rival season end.
                .then(Commands.m_82127_("rp")
                        .executes(ctx -> adminUsage(ctx.getSource(), "/lm admin rival rp set|add <player> <amount>"))
                        .then(Commands.m_82127_("set")
                                .executes(ctx -> adminUsage(ctx.getSource(), "/lm admin rival rp set <player> <amount>"))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> adminUsage(ctx.getSource(),
                                                "/lm admin rival rp set <player> <amount>"))
                                        .then(Commands.m_82129_("amount", IntegerArgumentType.integer(0, 10_000_000))
                                                .executes(ctx -> adminRp(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                                        true)))))
                        .then(Commands.m_82127_("add")
                                .executes(ctx -> adminUsage(ctx.getSource(), "/lm admin rival rp add <player> <amount>"))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> adminUsage(ctx.getSource(),
                                                "/lm admin rival rp add <player> <amount>"))
                                        .then(Commands.m_82129_("amount",
                                                        IntegerArgumentType.integer(-10_000_000, 10_000_000))
                                                .executes(ctx -> adminRp(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                                        false))))))
                .then(Commands.m_82127_("challenge")
                        .executes(ctx -> adminUsage(ctx.getSource(), "/lm admin rival challenge cancel <player>"))
                        .then(Commands.m_82127_("cancel")
                                .executes(ctx -> adminUsage(ctx.getSource(), "/lm admin rival challenge cancel <player>"))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> adminChallengeCancel(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.m_82127_("season")
                        .executes(ctx -> adminUsage(ctx.getSource(), "/lm admin rival season advance"))
                        .then(Commands.m_82127_("advance")
                                .executes(ctx -> adminSeasonAdvance(ctx.getSource(), false))
                                .then(Commands.m_82127_("confirm")
                                        .executes(ctx -> adminSeasonAdvance(ctx.getSource(), true)))));
        event.getDispatcher().register(CommandAccess.underLm(root));
        event.getDispatcher().register(CommandAccess.underLmAdmin(admin));
        AdaptiveDifficultyMod.LOGGER.info("[{}] registered /lm rival and /lm admin rival", AdaptiveDifficultyMod.MOD_ID);
    }

    private static int gui(CommandSourceStack source, String page) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        com.dbzlegacy.adaptivedifficulty.gui.RivalMenu.open(player, page);
        return 1;
    }

    /** Parse {@code /lm rival do <action> [arg…] [returnPage]} for CMI/GUI clicks. */
    private static int guiDoRest(CommandSourceStack source, String action, String rest) {
        String a = action == null ? "" : action.trim();
        String r = rest == null ? "" : rest.trim();
        if ("page".equalsIgnoreCase(a) || "refresh".equalsIgnoreCase(a)) {
            return guiDo(source, a, r, r.isBlank() ? "main" : r);
        }
        int sp = r.lastIndexOf(' ');
        if (sp <= 0) {
            return guiDo(source, a, r, "main");
        }
        return guiDo(source, a, r.substring(0, sp).trim(), r.substring(sp + 1).trim());
    }

    private static int guiDo(CommandSourceStack source, String action, String arg, String page) {
        ServerPlayer player = playerOrNull(source);
        if (player == null) {
            return 0;
        }
        // Admin actions work even when rival system flag is off (RivalGuiApi.handleDo).
        String act = action == null ? "" : action;
        String a = arg == null ? "" : arg;
        String reopen = page == null || page.isBlank() ? "main" : page;
        if ("page".equalsIgnoreCase(act) || "refresh".equalsIgnoreCase(act)) {
            reopen = a.isBlank() ? "main" : a;
        } else if (!"admin".equalsIgnoreCase(act) && !enabled(player)) {
            return 0;
        }
        String msg = RivalGuiApi.handleDo(player, act, a, reopen);
        if (msg != null && !msg.isBlank()) {
            DmzRewards.msg(player, msg);
        }
        com.dbzlegacy.adaptivedifficulty.gui.RivalMenu.open(player, reopen);
        return 1;
    }

    private static int spectate(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        ServerPlayer target = name == null ? null : RivalSystem.findOnline(source.m_81377_(), name);
        DmzRewards.msg(player, RivalSpectator.start(player, target));
        return 1;
    }

    private static int spectateStop(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, RivalSpectator.stop(player));
        return 1;
    }

    private static int playerHelp(CommandSourceStack source) {
        LmCommandFeedback.tellLines(source, LmCommandHelp.rivalPlayer());
        return 1;
    }

    private static int silent(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        ServerPlayer target = RivalSystem.findOnline(source.m_81377_(), name);
        if (target == null) {
            DmzRewards.msg(player, LmCommandMessages.playerOffline(name));
            return 0;
        }
        DmzRewards.msg(player, RivalSystem.silentRival(player, target));
        return 1;
    }

    private static int declare(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        ServerPlayer target = RivalSystem.findOnline(source.m_81377_(), name);
        if (target == null) {
            DmzRewards.msg(player, LmCommandMessages.playerOffline(name));
            return 0;
        }
        DmzRewards.msg(player, RivalSystem.declare(player, target));
        return 1;
    }

    private static int accept(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, RivalSystem.accept(player, name));
        return 1;
    }

    private static int decline(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, RivalSystem.decline(player, name));
        return 1;
    }

    private static int remove(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, RivalSystem.remove(player, name));
        return 1;
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : RivalSystem.listLines(player)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    private static int stats(CommandSourceStack source, String target) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : RivalSystem.statsLines(player, target)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    private static int top(CommandSourceStack source, String category) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : RivalSystem.topLines(category, 10)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    private static int tpmsg(CommandSourceStack source, Boolean on) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, RivalSystem.setTpMsg(player, on));
        return 1;
    }

    private static int challengeSend(CommandSourceStack source, String name, int minutes) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        if (!DifficultyConfig.get().rivalChallenges) {
            DmzRewards.msg(player, LmCommandMessages.systemDisabled("Rival challenges"));
            return 0;
        }
        ServerPlayer target = RivalSystem.findOnline(source.m_81377_(), name);
        if (target == null) {
            DmzRewards.msg(player, LmCommandMessages.playerOffline(name));
            return 0;
        }
        DmzRewards.msg(player, RivalChallengeManager.get().sendChallenge(player, target, minutes));
        return 1;
    }

    private static int challengeAccept(CommandSourceStack source, String who) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, RivalChallengeManager.get().acceptChallenge(player, who));
        return 1;
    }

    private static int challengeDecline(CommandSourceStack source, String who) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, RivalChallengeManager.get().declineChallenge(player, who));
        return 1;
    }

    private static int challengeCancel(CommandSourceStack source, String who) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, RivalChallengeManager.get().cancelChallenge(player, who));
        return 1;
    }

    private static int refresh(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        RivalStore.get().load();
        RivalProgression.get().load();
        source.m_288197_(() -> Component.m_237113_("§aReloaded rival data from disk."), true);
        return 1;
    }

    private static int save(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        RivalStore.get().markDirty();
        RivalStore.get().save();
        RivalProgression.get().save();
        source.m_288197_(() -> Component.m_237113_(LmCommandMessages.saved("rival data")), true);
        return 1;
    }

    private static int adminHelp(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        LmCommandFeedback.tellLines(source, LmCommandHelp.rivalAdmin());
        LmCommandFeedback.tellLines(source, adminFixesHelp());
        return 1;
    }

    // ------------------------------------------------------------------ 4.8.3 staff fixes

    /** 4.8.3: the new staff leaves, listed under the branch help. */
    private static String adminFixesHelp() {
        return UltraStyle.GOLD + "— Player fixes and season (staff only) —"
                + "\n" + LmStaffHelp.cmd("/lm admin rival rp set <player> <amount>", "set a player's total RP")
                + "\n" + LmStaffHelp.cmd("/lm admin rival rp add <player> <amount>",
                        "give RP, or take it with a negative amount (never below 0)")
                + "\n" + LmStaffHelp.cmd("/lm admin rival challenge cancel <player>",
                        "end their duel or duel requests now: no result, both players told")
                + "\n" + LmStaffHelp.cmd("/lm admin rival season advance",
                        "end the rival season: save the standings, set everyone's RP to 0 (asks to confirm)");
    }

    private static int adminUsage(CommandSourceStack source, String usage) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        source.m_81352_(Component.m_237113_(LmCommandMessages.tryCommand(usage)));
        return 0;
    }

    private static void reply(CommandSourceStack source, String line) {
        source.m_288197_(() -> Component.m_237113_(line), true);
    }

    private static int adminRp(CommandSourceStack source, String name, int amount, boolean set) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        RivalPlayerRecord rec = rivalRecord(source, name);
        if (rec == null) {
            return 0;
        }
        String who = rec.name == null || rec.name.isBlank() ? name : rec.name;
        rec.recalcTotalRp();
        int before = (int) Math.round(rec.totalRp);
        int delta = set ? amount - before : amount;
        if (delta == 0) {
            reply(source, UltraStyle.GRAY + who + " already has " + UltraStyle.WHITE + before + " RP" + UltraStyle.GRAY
                    + ". Nothing changed.");
            return 1;
        }
        if (rec.rivals.isEmpty()) {
            source.m_81352_(Component.m_237113_(UltraStyle.DANGER + who + " has no rivals, and RP is kept on each "
                    + "rivalry, so there is nowhere to put it. They need a rival first (/lm rival declare)."));
            return 0;
        }
        int changed = RivalStore.get().adjustTotalRp(rec, delta, "staff");
        RivalStore.get().save();
        int after = (int) Math.round(rec.totalRp);
        if (changed == 0) {
            reply(source, UltraStyle.GRAY + who + " has " + UltraStyle.WHITE + after + " RP" + UltraStyle.GRAY
                    + " and RP can't go below 0. Nothing changed.");
            return 1;
        }
        reply(source, UltraStyle.GREEN + (changed > 0 ? "Gave " : "Took ") + UltraStyle.WHITE + who + " "
                + Math.abs(changed) + " RP" + UltraStyle.GREEN + (changed > 0 ? "" : " away") + " (was " + before
                + ", now " + UltraStyle.WHITE + after + UltraStyle.GREEN + ")."
                + (changed != delta ? UltraStyle.GRAY + " RP stops at 0." : ""));
        ServerPlayer online = RivalSystem.findOnline(source.m_81377_(), who);
        if (online != null) {
            DmzRewards.msg(online, LmChat.info("Rival", "Staff " + (changed > 0 ? "gave you " : "took ")
                    + Math.abs(changed) + " RP" + (changed > 0 ? "" : " from you") + ". You now have " + after + " RP."));
        }
        return 1;
    }

    private static int adminChallengeCancel(CommandSourceStack source, String name) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        java.util.UUID id = null;
        String who = name;
        ServerPlayer online = RivalSystem.findOnline(source.m_81377_(), name);
        if (online != null) {
            id = online.m_20148_();
            who = online.m_7755_().getString();
        } else {
            RivalPlayerRecord rec = findRecordByName(name);
            if (rec != null) {
                try {
                    id = java.util.UUID.fromString(rec.uuid);
                    who = rec.name;
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (id == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.playerNotFound(name)));
            return 0;
        }
        java.util.List<RivalChallengeManager.StaffCancel> ended = RivalChallengeManager.get()
                .staffCancel(source.m_81377_(), id);
        if (ended.isEmpty()) {
            source.m_81352_(Component.m_237113_(UltraStyle.DANGER + who + " isn't in a rival challenge and has no "
                    + "challenge request, so there was nothing to cancel."));
            return 0;
        }
        for (RivalChallengeManager.StaffCancel c : ended) {
            String what = c.live() ? "the duel" : "the challenge request";
            reply(source, UltraStyle.GREEN + "Cancelled " + what + " " + UltraStyle.WHITE + c.nameA()
                    + UltraStyle.GREEN + " vs " + UltraStyle.WHITE + c.nameB() + UltraStyle.GREEN
                    + (c.live() ? ". No result: no TP, RP, win or loss." : "."));
            for (java.util.UUID u : new java.util.UUID[] {c.a(), c.b()}) {
                ServerPlayer p = u == null ? null : source.m_81377_().m_6846_().m_11259_(u);
                if (p != null) {
                    String otherName = u.equals(c.a()) ? c.nameB() : c.nameA();
                    DmzRewards.msg(p, LmChat.info("Challenge", "Staff cancelled " + (c.live() ? "your duel" : "the "
                            + "challenge request") + " with " + otherName + (c.live() ? ". It doesn't count: no TP, "
                            + "RP, win or loss." : ".")));
                }
            }
        }
        return 1;
    }

    private static int adminSeasonAdvance(CommandSourceStack source, boolean confirm) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        RivalStore store = RivalStore.get();
        int season = RivalProgression.get().currentSeasonId();
        java.util.List<RivalStore.SeasonStanding> standings = store.currentStandings();
        if (!confirm) {
            reply(source, UltraStyle.YELLOW + "This ends rival Season " + season + " now:");
            reply(source, UltraStyle.GRAY + "- Saves the final standings (" + standings.size() + " player"
                    + (standings.size() == 1 ? "" : "s") + " with RP) with the season number and today's date.");
            reply(source, UltraStyle.GRAY + "- Sets every player's RP to 0 on every rivalry. Rivals, wins, losses "
                    + "and titles stay.");
            reply(source, UltraStyle.GRAY + "- Starts Season " + (season + 1) + " with an empty season RP board.");
            topLines(source, standings);
            reply(source, UltraStyle.DANGER + "It can't be undone. " + UltraStyle.GRAY + "To do it, type "
                    + UltraStyle.WHITE + "/lm admin rival season advance confirm");
            return 1;
        }
        RivalStore.SeasonArchive archive = store.advanceSeason(season);
        int next = RivalProgression.get().advanceSeasonNow() + 1;
        reply(source, UltraStyle.GREEN + "Rival Season " + archive.season + " ended. Saved the standings of "
                + archive.standings.size() + " player" + (archive.standings.size() == 1 ? "" : "s")
                + " (" + archive.endedDate + "); everyone's RP is now 0. Season " + next + " has started.");
        topLines(source, archive.standings);
        ServerPlayer who = source.m_230896_();
        AdaptiveDifficultyMod.LOGGER.info("[{}] rival season {} advanced by {} ({} standings archived)",
                AdaptiveDifficultyMod.MOD_ID, archive.season, who == null ? "console" : who.m_7755_().getString(),
                archive.standings.size());
        for (ServerPlayer p : source.m_81377_().m_6846_().m_11314_()) {
            DmzRewards.msg(p, LmChat.info("Rival", "Rival Season " + archive.season + " is over. Everyone's RP is "
                    + "back to 0 for Season " + next + "."));
        }
        return 1;
    }

    private static void topLines(CommandSourceStack source, java.util.List<RivalStore.SeasonStanding> standings) {
        if (standings.isEmpty()) {
            reply(source, UltraStyle.GRAY + "Nobody has RP yet, so the standings are empty.");
            return;
        }
        reply(source, UltraStyle.GOLD + "Top of the standings:");
        for (int i = 0; i < Math.min(5, standings.size()); i++) {
            RivalStore.SeasonStanding st = standings.get(i);
            reply(source, UltraStyle.GRAY + "#" + st.rank + " " + UltraStyle.WHITE + st.name + UltraStyle.GRAY + " - "
                    + UltraStyle.WHITE + st.rp + " RP");
        }
    }

    /** A rival record by name: online first (made if missing), then an offline name the rival data knows. */
    private static RivalPlayerRecord rivalRecord(CommandSourceStack source, String name) {
        ServerPlayer online = RivalSystem.findOnline(source.m_81377_(), name);
        RivalPlayerRecord rec = online != null ? RivalStore.get().ensurePlayer(online) : findRecordByName(name);
        if (rec == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.playerNotFound(name)));
        }
        return rec;
    }

    private static RivalPlayerRecord findRecordByName(String name) {
        String want = name == null ? "" : name.trim();
        if (want.isEmpty()) {
            return null;
        }
        for (RivalPlayerRecord r : RivalStore.get().players.values()) {
            if (r != null && want.equalsIgnoreCase(r.name)) {
                return r;
            }
        }
        return null;
    }

    private static int adminStatus(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        boolean on = DifficultyConfig.get().enableRivalSystem;
        int players = RivalStore.get().players.size();
        source.m_288197_(() -> Component.m_237113_(
                "§6Rival admin status\n"
                        + "§7enabled §f" + (on ? "ON" : "OFF") + "\n"
                        + "§7players §f" + players
        ), false);
        return 1;
    }

    private static boolean enabled(ServerPlayer player) {
        if (!DifficultyConfig.get().enableRivalSystem) {
            DmzRewards.msg(player, LmCommandMessages.systemDisabled("The rival system"));
            return false;
        }
        return true;
    }

    private static ServerPlayer playerOrNull(CommandSourceStack source) {
        try {
            return source.m_81375_();
        } catch (Exception e) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.PLAYERS_ONLY));
            return null;
        }
    }
}
