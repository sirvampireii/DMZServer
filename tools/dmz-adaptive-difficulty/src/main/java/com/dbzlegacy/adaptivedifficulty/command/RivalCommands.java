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

/** {@code /rival} — Rival System 4.7.10 command surface. */
public final class RivalCommands {
    private RivalCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new RivalCommands());
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.m_82127_("rival")
                .requires(CommandAccess::isPlayerSlashUser)
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
                .then(CommandAccess.staffLiteral("admin")
                        .executes(ctx -> adminHelp(ctx.getSource()))
                        .then(Commands.m_82127_("help").executes(ctx -> adminHelp(ctx.getSource())))
                        .then(Commands.m_82127_("save").executes(ctx -> save(ctx.getSource())))
                        .then(Commands.m_82127_("refresh").executes(ctx -> refresh(ctx.getSource())))
                        .then(Commands.m_82127_("status").executes(ctx -> adminStatus(ctx.getSource()))))
                .then(LmCommandSuggestions.playerWord("player")
                        .executes(ctx -> silent(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"))));

        event.getDispatcher().register(root);
        AdaptiveDifficultyMod.LOGGER.info("[{}] registered /rival", AdaptiveDifficultyMod.MOD_ID);
    }

    private static int gui(CommandSourceStack source, String page) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !enabled(player)) {
            return 0;
        }
        com.dbzlegacy.adaptivedifficulty.gui.RivalMenu.open(player, page);
        return 1;
    }

    /** Parse {@code /rival do <action> [arg…] [returnPage]} for CMI/GUI clicks. */
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
        return 1;
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
                        + "§7players §f" + players + "\n"
                        + "§8" + RivalStore.path() + "\n"
                        + "§8" + RivalProgression.path()
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
