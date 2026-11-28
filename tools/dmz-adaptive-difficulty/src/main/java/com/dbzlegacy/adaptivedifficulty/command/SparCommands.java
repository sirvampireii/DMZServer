package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.dojo.BoardSpar;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dbzlegacy.adaptivedifficulty.rival.RivalSystem;
import com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings;
import com.dbzlegacy.adaptivedifficulty.sparring.SparStore;
import com.dbzlegacy.adaptivedifficulty.sparring.SparringSystem;
import com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraWeightShopScreens;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** {@code /lm spar} — Sparring Tp System 3.2.11 command surface. */
public final class SparCommands {
    private SparCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new SparCommands());
        DojoAdminCommands.register();                   // 4.8.3: /lm admin dojo (AdaptiveDifficultyMod is audited)
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.m_82127_("spar")
                .executes(ctx -> gui(ctx.getSource(), "main"))
                .then(Commands.m_82127_("gui").executes(ctx -> gui(ctx.getSource(), "main")))
                .then(Commands.m_82127_("help").executes(ctx -> playerHelp(ctx.getSource())))
                .then(Commands.m_82127_("stats")
                        .executes(ctx -> stats(ctx.getSource(), null))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> stats(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("end").executes(ctx -> end(ctx.getSource())))
                .then(Commands.m_82127_("top")
                        .executes(ctx -> top(ctx.getSource(), "tp"))
                        .then(LmCommandSuggestions.word("category", LmCommandSuggestions.SPAR_TOP_CATEGORIES)
                                .executes(ctx -> top(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "category")))));
        // 4.7.88: the old spar dojo branch is /lm dojo. Bare /lm dojo opens the Dojo menu (the picker first for a player in
        // two dojos); the old text summary is /lm dojo summary.
        LiteralArgumentBuilder<CommandSourceStack> dojo = Commands.m_82127_("dojo")
                        .executes(ctx -> dojoMenu(ctx.getSource()))
                        .then(Commands.m_82127_("summary").executes(ctx -> dojoInfo(ctx.getSource())))
                        .then(Commands.m_82127_("help").executes(ctx -> {
                            LmCommandFeedback.tellLines(ctx.getSource(), LmCommandHelp.dojoPlayer());
                            return 1;
                        }))
                        .then(Commands.m_82127_("top")
                                .executes(ctx -> dojoTop(ctx.getSource(), "rp"))
                                .then(LmCommandSuggestions.word("category", LmCommandSuggestions.SPAR_TOP_CATEGORIES)
                                        .executes(ctx -> dojoTop(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "category")))))
                        .then(CommandAccess.staffLiteral("accept").executes(ctx -> dojoAccept(ctx.getSource())))
                        .then(CommandAccess.staffLiteral("decline").executes(ctx -> dojoDecline(ctx.getSource())))
                        .then(Commands.m_82127_("hof").executes(ctx -> dojoHof(ctx.getSource())))
                        .then(Commands.m_82127_("members").executes(ctx -> dojoMembers(ctx.getSource())))
                        .then(Commands.m_82127_("previous-members")
                                .executes(ctx -> dojoPreviousMembers(ctx.getSource())))
                        .then(CommandAccess.staffLiteral("season")
                                .then(Commands.m_82127_("advance")
                                        .executes(ctx -> dojoSeasonAdvance(ctx.getSource(), false))
                                        .then(Commands.m_82127_("confirm")
                                                .executes(ctx -> dojoSeasonAdvance(ctx.getSource(), true)))))
                        .then(Commands.m_82127_("name")
                                .then(Commands.m_82129_("display", StringArgumentType.greedyString())
                                        .executes(ctx -> dojoName(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "display")))))
                        .then(Commands.m_82127_("banner")
                                .then(Commands.m_82129_("material", StringArgumentType.word())
                                        .executes(ctx -> dojoBanner(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "material")))))
                        .then(CommandAccess.staffLiteral("challenge")
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> dojoChallenge(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))));
        root = root
                .then(Commands.m_82127_("do")
                        // Full GUI surface via SparGuiApi.handleDo — greedy rest for uuid: args.
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
                .then(Commands.m_82127_("mentor")
                        .executes(ctx -> mentorStatus(ctx.getSource()))
                        .then(CommandAccess.staffLiteral("accept").executes(ctx -> mentorAccept(ctx.getSource())))
                        .then(CommandAccess.staffLiteral("decline").executes(ctx -> mentorDecline(ctx.getSource())))
                        .then(CommandAccess.staffLiteral("leave").executes(ctx -> mentorRemove(ctx.getSource())))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> mentorInvite(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("apprentice")
                        .executes(ctx -> mentorStatus(ctx.getSource()))
                        .then(Commands.m_82127_("remove")
                                .executes(ctx -> apprenticeRemove(ctx.getSource(), null))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> apprenticeRemove(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> apprenticeInvite(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("weight")
                        .executes(ctx -> weightUsage(ctx.getSource()))
                        .then(CommandAccess.staffLiteral("quote")
                                .then(LmCommandSuggestions.word("type", LmCommandSuggestions.WEIGHT_TYPES)
                                        .then(Commands.m_82129_("value", IntegerArgumentType.integer(1, UltraWeightShopScreens.MAX_WEIGHT_VALUE))
                                                .executes(ctx -> weightQuote(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))
                        .then(CommandAccess.staffLiteral("buy")
                                .then(LmCommandSuggestions.word("type", LmCommandSuggestions.WEIGHT_TYPES)
                                        .then(Commands.m_82129_("value", IntegerArgumentType.integer(1, UltraWeightShopScreens.MAX_WEIGHT_VALUE))
                                                .executes(ctx -> weightBuy(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))));
        // 4.7.88: the old spar admin branch is /lm admin spar.
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.m_82127_("spar")
                        .executes(ctx -> sparAdminHelp(ctx.getSource()))
                        .then(Commands.m_82127_("help").executes(ctx -> sparAdminHelp(ctx.getSource())))
                        .then(Commands.m_82127_("save").executes(ctx -> save(ctx.getSource())))
                        .then(Commands.m_82127_("status").executes(ctx -> sparAdminStatus(ctx.getSource())))
                        .then(Commands.m_82127_("mentor")
                                .then(Commands.m_82127_("resetcd")
                                        .executes(ctx -> resetCd(ctx.getSource(), null))
                                        .then(LmCommandSuggestions.playerWord("player")
                                                .executes(ctx -> resetCd(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        // 4.8.3: staff fixes for one player's spar session, board post, bout and record.
                        .then(Commands.m_82127_("end")
                                .executes(ctx -> sparAdminUsage(ctx.getSource(), "/lm admin spar end <player>"))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> adminEnd(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.m_82127_("board")
                                .executes(ctx -> sparAdminUsage(ctx.getSource(), "/lm admin spar board remove <player>"))
                                .then(Commands.m_82127_("remove")
                                        .executes(ctx -> sparAdminUsage(ctx.getSource(),
                                                "/lm admin spar board remove <player>"))
                                        .then(LmCommandSuggestions.playerWord("player")
                                                .executes(ctx -> adminBoardRemove(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(Commands.m_82127_("bout")
                                .executes(ctx -> sparAdminUsage(ctx.getSource(), "/lm admin spar bout cancel <player>"))
                                .then(Commands.m_82127_("cancel")
                                        .executes(ctx -> sparAdminUsage(ctx.getSource(),
                                                "/lm admin spar bout cancel <player>"))
                                        .then(LmCommandSuggestions.playerWord("player")
                                                .executes(ctx -> adminBoutCancel(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(Commands.m_82127_("reset")
                                .executes(ctx -> sparAdminUsage(ctx.getSource(), "/lm admin spar reset <player>"))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> adminReset(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                false))
                                        .then(Commands.m_82127_("confirm")
                                                .executes(ctx -> adminReset(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        true)))));

        event.getDispatcher().register(CommandAccess.underLm(root));
        event.getDispatcher().register(CommandAccess.underLm(dojo));
        event.getDispatcher().register(CommandAccess.underLmAdmin(admin));
        AdaptiveDifficultyMod.LOGGER.info("[{}] registered /lm spar, /lm dojo and /lm admin spar",
                AdaptiveDifficultyMod.MOD_ID);
    }

    private static int weightUsage(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, "§7Training weights — §f1 ancient copper §7per weight value.");
        DmzRewards.msg(player, "§7Price check: §f/lm spar weight quote <type> <value>");
        DmzRewards.msg(player, "§7Buy: §f/lm spar weight buy <type> <value>");
        DmzRewards.msg(player, "§8Types: turtle_shell, workout_weights, piccolo_cape");
        return 0;
    }

    private static int weightQuote(CommandSourceStack source, String type, int value) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, UltraWeightShopScreens.quoteFromCommand(player, type, value));
        return 1;
    }

    private static int weightBuy(CommandSourceStack source, String type, int value) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        String msg = UltraWeightShopScreens.buyFromCommand(player, type, value);
        if (msg != null && !msg.isBlank()) {
            DmzRewards.msg(player, msg);
        }
        return 1;
    }

    private static int gui(CommandSourceStack source, String page) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        String p = page == null || page.isBlank() ? "main" : page;
        if (p.startsWith("top_")) {
            p = "top_" + p.substring(4);
        }
        com.dbzlegacy.adaptivedifficulty.gui.SparMenu.open(player, p);
        return 1;
    }

    /** Parse {@code /lm spar do <action> [arg…] [returnPage]} for CMI/GUI clicks. */
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
        String act = action == null ? "" : action;
        String a = arg == null ? "" : arg;
        String reopen = page == null || page.isBlank() ? "main" : page;
        if ("page".equalsIgnoreCase(act) || "refresh".equalsIgnoreCase(act)) {
            reopen = a.isBlank() ? "main" : a;
        } else if (!"admin".equalsIgnoreCase(act) && !enabled(player)) {
            return 0;
        }
        String msg = SparGuiApi.handleDo(player, act, a, reopen);
        if (msg != null && !msg.isBlank()) {
            DmzRewards.msg(player, msg);
        }
        com.dbzlegacy.adaptivedifficulty.gui.SparMenu.open(player, reopen);
        return 1;
    }

    private static int top(CommandSourceStack source, String category) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : SparringSystem.topLines(category, 10)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    private static int dojoTop(CommandSourceStack source, String category) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : SparringSystem.dojoTopLines(category, 10)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    /** {@code /lm dojo}: the Dojo menu, the same destination as the hub's Dojo tile and {@code /lm open dojo}. */
    private static int dojoMenu(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        return MechanicsCommands.openSystemMenu(player, "dojo") ? 1 : 0;
    }

    private static int dojoInfo(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : SparringSystem.dojoInfoLines(player)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    /**
     * 4.7.84 (owner pick a): the old war commands declare and answer the same wars as Dojo > Wars
     * ({@code DojoWar}); the old 2x-ranking-point challenge is gone.
     */
    private static int dojoChallenge(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, com.dbzlegacy.adaptivedifficulty.util.LmChat.info("Dojo",
                com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.legacyChallenge(player, name)));
        return 1;
    }

    private static int dojoAccept(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, com.dbzlegacy.adaptivedifficulty.util.LmChat.info("Dojo",
                com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.legacyAnswer(player, true)));
        return 1;
    }

    private static int dojoDecline(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, com.dbzlegacy.adaptivedifficulty.util.LmChat.info("Dojo",
                com.dbzlegacy.adaptivedifficulty.dojo.DojoWar.legacyAnswer(player, false)));
        return 1;
    }

    private static int dojoHof(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : SparringSystem.dojoHallOfFameLines()) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    private static int dojoMembers(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : SparringSystem.dojoMemberLines(player)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    private static int dojoPreviousMembers(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        for (String line : SparringSystem.dojoPreviousMemberLines(player)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    /** Staff, console or in game: the bare form previews; {@code confirm} ends the season now. */
    private static int dojoSeasonAdvance(CommandSourceStack source, boolean confirm) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        List<String> lines = confirm ? DojoRankings.advanceSeasonByStaff() : DojoRankings.seasonAdvancePreviewLines();
        // One message per line, so the console gets readable lines too.
        for (String line : lines) {
            source.m_288197_(() -> Component.m_237113_(line), false);
        }
        if (confirm) {
            ServerPlayer who = source.m_230896_();
            AdaptiveDifficultyMod.LOGGER.info("[{}] dojo season advanced by {}", AdaptiveDifficultyMod.MOD_ID,
                    who == null ? "console" : who.m_7755_().getString());
        }
        return 1;
    }

    private static int dojoName(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, SparringSystem.dojoSetName(player, name));
        return 1;
    }

    private static int dojoBanner(CommandSourceStack source, String material) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, SparringSystem.dojoSetBanner(player, material));
        return 1;
    }

    private static int playerHelp(CommandSourceStack source) {
        LmCommandFeedback.tellLines(source, LmCommandHelp.sparPlayer());
        return 1;
    }

    private static int stats(CommandSourceStack source, String targetName) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        ServerPlayer target = player;
        if (targetName != null && !targetName.isBlank()) {
            target = RivalSystem.findOnline(source.m_81377_(), targetName);
            if (target == null) {
                DmzRewards.msg(player, LmCommandMessages.playerOffline(targetName));
                return 0;
            }
        }
        for (String line : SparringSystem.statsLines(target)) {
            DmzRewards.msg(player, line);
        }
        return 1;
    }

    private static int end(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, SparringSystem.endCommand(player));
        return 1;
    }

    private static int mentorStatus(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, SparringSystem.bondStatus(player));
        return 1;
    }

    private static int mentorInvite(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        ServerPlayer target = RivalSystem.findOnline(source.m_81377_(), name);
        DmzRewards.msg(player, SparringSystem.mentorInvite(player, target));
        return 1;
    }

    private static int apprenticeInvite(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        ServerPlayer target = RivalSystem.findOnline(source.m_81377_(), name);
        DmzRewards.msg(player, SparringSystem.apprenticeInvite(player, target));
        return 1;
    }

    private static int mentorAccept(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, SparringSystem.mentorAccept(player));
        return 1;
    }

    private static int mentorDecline(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, SparringSystem.mentorDecline(player));
        return 1;
    }

    private static int mentorRemove(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, SparringSystem.removeMentor(player));
        return 1;
    }

    private static int apprenticeRemove(CommandSourceStack source, String name) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        DmzRewards.msg(player, SparringSystem.removeApprentice(player, name));
        return 1;
    }

    private static int resetCd(CommandSourceStack source, String name) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer admin = playerOrNull(source);
        ServerPlayer target = name == null ? admin : RivalSystem.findOnline(source.m_81377_(), name);
        if (target == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.playerOffline(name)));
            return 0;
        }
        String msg = SparringSystem.resetMentorCd(admin == null ? target : admin, target);
        source.m_288197_(() -> Component.m_237113_(msg), true);
        return 1;
    }

    private static int save(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        SparStore.get().markDirty();
        SparStore.get().save();
        source.m_288197_(() -> Component.m_237113_(LmCommandMessages.saved("spar data")), true);
        return 1;
    }

    private static int sparAdminHelp(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        LmCommandFeedback.tellLines(source, LmCommandHelp.sparAdmin());
        LmCommandFeedback.tellLines(source, sparAdminFixesHelp());
        return 1;
    }

    // ------------------------------------------------------------------ 4.8.3 staff fixes

    /** 4.8.3: the new staff leaves, listed under the branch help. */
    private static String sparAdminFixesHelp() {
        return UltraStyle.GOLD + "— Player fixes (staff only) —"
                + "\n" + LmStaffHelp.cmd("/lm admin spar end <player>",
                        "end that player's spar now: no result, nothing counts")
                + "\n" + LmStaffHelp.cmd("/lm admin spar board remove <player>", "take down their Spar Board post")
                + "\n" + LmStaffHelp.cmd("/lm admin spar bout cancel <player>",
                        "cancel a stuck board spar: no winner, wagers refunded, both sent back")
                + "\n" + LmStaffHelp.cmd("/lm admin spar reset <player>",
                        "reset their streak, spar TP and season ranking points (asks to confirm)");
    }

    private static int sparAdminUsage(CommandSourceStack source, String usage) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        source.m_81352_(Component.m_237113_(LmCommandMessages.tryCommand(usage)));
        return 0;
    }

    private static void reply(CommandSourceStack source, String line) {
        source.m_288197_(() -> Component.m_237113_(line), true);
    }

    private static int adminEnd(CommandSourceStack source, String name) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = RivalSystem.findOnline(source.m_81377_(), name);
        if (target == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.playerOffline(name)));
            return 0;
        }
        String who = target.m_7755_().getString();
        java.util.UUID partnerId = SparringSystem.runtime(target.m_20148_()).partner;
        String partner = SparringSystem.staffEndSession(target);
        if (partner == null) {
            source.m_81352_(Component.m_237113_(UltraStyle.DANGER + who + " isn't in a spar right now, so there was "
                    + "nothing to end."));
            return 0;
        }
        reply(source, UltraStyle.GREEN + "Ended " + UltraStyle.WHITE + who + UltraStyle.GREEN + "'s spar with "
                + UltraStyle.WHITE + partner + UltraStyle.GREEN + ". No result: nothing counted for streaks, the "
                + "leaderboard or ranking, and any wager went back.");
        DmzRewards.msg(target, LmChat.info("Spar", "Staff ended your spar with " + partner
                + ". It doesn't count for anything, and any wager went back."));
        ServerPlayer other = partnerId == null ? null : source.m_81377_().m_6846_().m_11259_(partnerId);
        if (other != null && other != target) {
            DmzRewards.msg(other, LmChat.info("Spar", "Staff ended your spar with " + who
                    + ". It doesn't count for anything, and any wager went back."));
        }
        return 1;
    }

    private static int adminBoardRemove(CommandSourceStack source, String name) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        Target t = target(source, name);
        if (t == null) {
            return 0;
        }
        if (!BoardSpar.staffRemovePost(t.uuid())) {
            source.m_81352_(Component.m_237113_(UltraStyle.DANGER + t.name() + " has no post on the Spar Board."));
            return 0;
        }
        reply(source, UltraStyle.GREEN + "Took down " + UltraStyle.WHITE + t.name() + UltraStyle.GREEN
                + "'s Spar Board post.");
        if (t.online() != null) {
            DmzRewards.msg(t.online(), LmChat.info("Spar", "Staff took down your Spar Board post. You can post again "
                    + "from the Spar Board."));
        }
        return 1;
    }

    private static int adminBoutCancel(CommandSourceStack source, String name) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        Target t = target(source, name);
        if (t == null) {
            return 0;
        }
        BoardSpar.Bout bout = BoardSpar.boutOf(t.uuid());
        if (bout == null) {
            source.m_81352_(Component.m_237113_(UltraStyle.DANGER + t.name() + " isn't in a board spar."));
            return 0;
        }
        String nameA = bout.nameA;
        String nameB = bout.nameB;
        java.util.UUID idA = bout.a;
        java.util.UUID idB = bout.b;
        String wager = BoardSpar.staffCancelBout(t.uuid());
        reply(source, UltraStyle.GREEN + "Cancelled the board spar " + UltraStyle.WHITE + nameA + UltraStyle.GREEN
                + " vs " + UltraStyle.WHITE + nameB + UltraStyle.GREEN + ". No winner, nothing counted, both sent "
                + "back. Wager: " + UltraStyle.WHITE + wager + UltraStyle.GREEN + ".");
        for (java.util.UUID id : new java.util.UUID[] {idA, idB}) {
            ServerPlayer p = id == null ? null : source.m_81377_().m_6846_().m_11259_(id);
            if (p != null) {
                DmzRewards.msg(p, LmChat.info("Spar", "Staff cancelled your board spar. Nobody won, it doesn't count, "
                        + "and any wager went back."));
            }
        }
        return 1;
    }

    private static int adminReset(CommandSourceStack source, String name, boolean confirm) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        Target t = target(source, name);
        if (t == null) {
            return 0;
        }
        if (!confirm) {
            reply(source, UltraStyle.YELLOW + "This resets " + UltraStyle.WHITE + t.name() + UltraStyle.YELLOW
                    + "'s spar record to 0: daily spar streak (and best), TP from spars (and best spar), leaderboard "
                    + "streaks, and the ranking points they gave their dojo this season. Spar counts, times, combos, "
                    + "history, mentor bonds and the dojo's own total stay. It can't be undone.");
            reply(source, UltraStyle.GRAY + "To do it, type " + UltraStyle.WHITE + "/lm admin spar reset " + t.name()
                    + " confirm");
            return 1;
        }
        List<String> done = SparringSystem.resetSparRecord(t.uuid().toString());
        SparStore.get().save();
        if (done.isEmpty()) {
            reply(source, UltraStyle.GRAY + t.name() + "'s spar record was already at 0. Nothing changed.");
            return 1;
        }
        reply(source, UltraStyle.GREEN + "Reset " + UltraStyle.WHITE + t.name() + UltraStyle.GREEN
                + "'s spar record:");
        for (String line : done) {
            reply(source, UltraStyle.GRAY + "- " + line);
        }
        ServerPlayer who = source.m_230896_();
        AdaptiveDifficultyMod.LOGGER.info("[{}] spar record of {} reset by {}", AdaptiveDifficultyMod.MOD_ID, t.name(),
                who == null ? "console" : who.m_7755_().getString());
        if (t.online() != null) {
            DmzRewards.msg(t.online(), LmChat.info("Spar", "Staff reset your spar record: streak, TP from spars and "
                    + "season ranking points are back to 0."));
        }
        return 1;
    }

    private record Target(java.util.UUID uuid, String name, ServerPlayer online) {}

    /**
     * A player by name: online first, then offline names the spar leaderboard or the server's player cache knows.
     * Tells the source and returns null when nobody matches.
     */
    private static Target target(CommandSourceStack source, String name) {
        ServerPlayer online = RivalSystem.findOnline(source.m_81377_(), name);
        if (online != null) {
            return new Target(online.m_20148_(), online.m_7755_().getString(), online);
        }
        String want = name == null ? "" : name.trim();
        if (!want.isEmpty()) {
            for (java.util.Map.Entry<String, SparStore.LeaderboardEntry> e : SparStore.get().leaderboard.entrySet()) {
                java.util.UUID id = uuidOrNull(e.getKey());
                if (id != null && e.getValue() != null && want.equalsIgnoreCase(e.getValue().name)) {
                    return new Target(id, e.getValue().name, null);
                }
            }
            try {
                var cache = source.m_81377_() == null ? null : source.m_81377_().m_129927_();
                var profile = cache == null ? null : cache.m_10996_(want).orElse(null);
                if (profile != null && profile.getId() != null) {
                    return new Target(profile.getId(), profile.getName(), null);
                }
            } catch (Throwable ignored) {
            }
        }
        source.m_81352_(Component.m_237113_(LmCommandMessages.playerNotFound(name)));
        return null;
    }

    private static java.util.UUID uuidOrNull(String raw) {
        try {
            return raw == null ? null : java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int sparAdminStatus(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        boolean on = DifficultyConfig.get().enableSparringSystem;
        source.m_288197_(() -> Component.m_237113_(
                "§6Spar admin status\n"
                        + "§7enabled §f" + (on ? "ON" : "OFF") + "\n"
                        + "§7bonds §f" + SparStore.get().bondsByPlayer.size() + "\n"
                        + "§8" + SparStore.path()
        ), false);
        return 1;
    }

    private static boolean enabled(ServerPlayer player) {
        if (!DifficultyConfig.get().enableSparringSystem) {
            DmzRewards.msg(player, LmCommandMessages.systemDisabled("Sparring"));
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
