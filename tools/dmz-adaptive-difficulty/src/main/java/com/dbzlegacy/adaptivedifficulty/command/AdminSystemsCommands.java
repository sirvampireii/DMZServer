package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService;
import com.dbzlegacy.adaptivedifficulty.quest.SagaResetService;
import com.dragonminez.common.quest.Saga;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 4.8.3: staff commands for the thin systems: {@code /lm admin saga}, {@code /lm admin skillcheck} and
 * {@code /lm admin goals}. Every leaf works from the console and from an in-game staff player.
 */
public final class AdminSystemsCommands {
    private AdminSystemsCommands() {}

    private static final SuggestionProvider<CommandSourceStack> SAGAS =
            (ctx, builder) -> SharedSuggestionProvider.m_82970_(SagaResetService.commandKeys(), builder);

    private static final SuggestionProvider<CommandSourceStack> GOALS = (ctx, builder) -> {
        List<String> keys = new ArrayList<>();
        for (WeeklyGoals.Goal g : WeeklyGoals.thisWeek()) {
            keys.add(WeeklyGoals.key(g));
        }
        return SharedSuggestionProvider.m_82970_(keys, builder);
    };

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new AdminSystemsCommands());
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(CommandAccess.underLmAdmin(sagaBranch()));
        event.getDispatcher().register(CommandAccess.underLmAdmin(skillCheckBranch()));
        event.getDispatcher().register(CommandAccess.underLmAdmin(goalsBranch()));
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] registered /lm admin saga, /lm admin skillcheck and /lm admin goals",
                AdaptiveDifficultyMod.MOD_ID);
    }

    // ---- trees ---------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> sagaBranch() {
        return Commands.m_82127_("saga")
                .executes(ctx -> sagaHelp(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> sagaHelp(ctx.getSource())))
                .then(Commands.m_82127_("reset")
                        .executes(ctx -> sagaHelp(ctx.getSource()))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> sagaHelp(ctx.getSource()))
                                .then(LmCommandSuggestions.word("saga", SAGAS)
                                        .executes(ctx -> sagaReset(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "saga"), false))
                                        .then(Commands.m_82127_("confirm")
                                                .executes(ctx -> sagaReset(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "saga"), true))))))
                .then(Commands.m_82127_("unlock")
                        .executes(ctx -> sagaHelp(ctx.getSource()))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> sagaHelp(ctx.getSource()))
                                .then(LmCommandSuggestions.word("saga", SAGAS)
                                        .executes(ctx -> sagaUnlock(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "saga"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> skillCheckBranch() {
        return Commands.m_82127_("skillcheck")
                .executes(ctx -> skillCheckHelp(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> skillCheckHelp(ctx.getSource())))
                .then(Commands.m_82127_("clear")
                        .executes(ctx -> skillCheckHelp(ctx.getSource()))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> skillCheckClear(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> goalsBranch() {
        return Commands.m_82127_("goals")
                .executes(ctx -> goalsHelp(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> goalsHelp(ctx.getSource())))
                .then(Commands.m_82127_("inspect")
                        .executes(ctx -> goalsHelp(ctx.getSource()))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> goalsInspect(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("reset")
                        .executes(ctx -> goalsHelp(ctx.getSource()))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> goalsReset(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"), false))
                                .then(Commands.m_82127_("confirm")
                                        .executes(ctx -> goalsReset(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"), true)))))
                .then(Commands.m_82127_("grant")
                        .executes(ctx -> goalsHelp(ctx.getSource()))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> goalsHelp(ctx.getSource()))
                                .then(LmCommandSuggestions.word("goal", GOALS)
                                        .executes(ctx -> goalsGrant(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "goal"), 0))
                                        .then(Commands.m_82129_("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> goalsGrant(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "goal"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))));
    }

    // ---- help ----------------------------------------------------------------------------------------------------

    /** 4.8.3: the lines {@code /lm admin saga help} prints. */
    public static List<String> sagaHelpLines() {
        return List.of(
                UltraStyle.GOLD + "Saga staff commands",
                line("/lm admin saga reset <player> <saga> confirm",
                        "clear an online player's progress in one saga for free (cannot be undone)"),
                line("/lm admin saga unlock <player> <saga>", "unlock a saga that is locked for an online player"),
                UltraStyle.GRAY + "Saga names: " + UltraStyle.WHITE + String.join(", ", SagaResetService.commandKeys()));
    }

    /** 4.8.3: the lines {@code /lm admin skillcheck help} prints. */
    public static List<String> skillCheckHelpLines() {
        return List.of(
                UltraStyle.GOLD + "Skill Check staff commands",
                line("/lm admin skillcheck clear <player>",
                        "end a stuck Skill Check session so their menus look normal again"));
    }

    /** 4.8.3: the lines {@code /lm admin goals help} prints. */
    public static List<String> goalsHelpLines() {
        return List.of(
                UltraStyle.GOLD + "Weekly goals staff commands",
                line("/lm admin goals inspect <player>", "show a player's progress on this week's three goals"),
                line("/lm admin goals reset <player> confirm",
                        "start their week over: every goal back to zero, same three goals"),
                line("/lm admin goals grant <player> <goal> [amount]",
                        "add progress to one goal (minutes for time goals); no amount completes it"),
                UltraStyle.GRAY + "Works for offline players the server has seen before.");
    }

    private static String line(String cmd, String what) {
        return UltraStyle.WHITE + cmd + UltraStyle.GRAY + " - " + what;
    }

    private static int sagaHelp(CommandSourceStack source) {
        return tellAll(source, sagaHelpLines());
    }

    private static int skillCheckHelp(CommandSourceStack source) {
        return tellAll(source, skillCheckHelpLines());
    }

    private static int goalsHelp(CommandSourceStack source) {
        return tellAll(source, goalsHelpLines());
    }

    // ---- saga ----------------------------------------------------------------------------------------------------

    private static int sagaReset(CommandSourceStack source, String playerName, String sagaRaw, boolean confirm) {
        ServerPlayer target = online(source, playerName);
        if (target == null) {
            return 0;
        }
        Saga saga = saga(source, sagaRaw);
        if (saga == null) {
            return 0;
        }
        String name = target.m_36316_().getName();
        String key = SagaResetService.commandKey(saga);
        if (!confirm) {
            SagaResetService.Offer offer = SagaResetService.findOffer(target, saga.getId());
            if (offer != null && !offer.resettable()) {
                return fail(source, name + ": " + offer.statusLabel() + " in " + title(saga)
                        + ", so there is nothing to reset.");
            }
            tell(source, UltraStyle.YELLOW + "This clears " + name + "'s progress in " + title(saga)
                    + ". It is free for them and cannot be undone.");
            tell(source, UltraStyle.GRAY + "To do it, type " + UltraStyle.WHITE + "/lm admin saga reset " + name + " "
                    + key + " confirm");
            return 1;
        }
        SagaResetService.AdminResult r = SagaResetService.adminReset(target, saga);
        if (!r.ok()) {
            return fail(source, name + ": " + r.line());
        }
        ok(source, UltraStyle.GREEN + r.line() + " (" + name + ")");
        notify(source, target, "Staff reset your " + r.sagaTitle() + " progress, free of charge. You can play it again.");
        return 1;
    }

    private static int sagaUnlock(CommandSourceStack source, String playerName, String sagaRaw) {
        ServerPlayer target = online(source, playerName);
        if (target == null) {
            return 0;
        }
        Saga saga = saga(source, sagaRaw);
        if (saga == null) {
            return 0;
        }
        String name = target.m_36316_().getName();
        SagaResetService.AdminResult r = SagaResetService.adminUnlock(target, saga);
        if (!r.ok()) {
            return fail(source, name + ": " + r.line());
        }
        ok(source, UltraStyle.GREEN + "Unlocked " + r.sagaTitle() + " for " + name + ".");
        notify(source, target, "Staff unlocked " + r.sagaTitle() + " for you.");
        return 1;
    }

    private static Saga saga(CommandSourceStack source, String raw) {
        Saga saga = SagaResetService.findLoose(raw);
        if (saga == null) {
            List<String> keys = SagaResetService.commandKeys();
            fail(source, "No saga called " + raw + ". "
                    + (keys.isEmpty() ? "No sagas are loaded right now." : "Try one of: " + String.join(", ", keys)));
        }
        return saga;
    }

    private static String title(Saga saga) {
        String t = SagaResetService.displayName(saga);
        return t.toLowerCase(Locale.ROOT).endsWith("saga") ? t : t + " Saga";
    }

    // ---- skill check ---------------------------------------------------------------------------------------------

    private static int skillCheckClear(CommandSourceStack source, String playerName) {
        Who who = who(source, playerName);
        if (who == null) {
            return 0;
        }
        boolean had = SkillCheckService.clearSessionIfAny(who.id);
        if (!had) {
            tell(source, UltraStyle.GRAY + who.name + " has no Skill Check session open. Nothing to clear.");
            return 1;
        }
        ok(source, UltraStyle.GREEN + "Cleared " + who.name + "'s Skill Check session.");
        notify(source, who.online, "Staff closed your Skill Check session. Your menus are back to normal.");
        return 1;
    }

    // ---- weekly goals --------------------------------------------------------------------------------------------

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ROOT);

    private static int goalsInspect(CommandSourceStack source, String playerName) {
        Who who = who(source, playerName);
        if (who == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        String start = DAY.format(Instant.ofEpochMilli(WeeklyGoals.week(now) * WeeklyGoals.WEEK_MS).atZone(ZoneOffset.UTC));
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + "Weekly goals for " + who.name + UltraStyle.GRAY + " (week starting " + start
                + ", resets in " + WeeklyGoals.resetsIn(now) + ")");
        for (WeeklyGoals.Goal g : WeeklyGoals.thisWeek()) {
            long have = WeeklyGoals.progress(who.id, g.id());
            boolean done = have >= g.target();
            lines.add((done ? UltraStyle.GREEN + "Done: " : UltraStyle.YELLOW + "Not done: ") + UltraStyle.WHITE
                    + g.title() + UltraStyle.GRAY + " - " + amountText(g, have) + " (goal key "
                    + WeeklyGoals.key(g) + ")");
        }
        lines.add(UltraStyle.GRAY + WeeklyGoals.NO_PAYOUT_NOTE);
        return tellAll(source, lines);
    }

    private static int goalsReset(CommandSourceStack source, String playerName, boolean confirm) {
        Who who = who(source, playerName);
        if (who == null) {
            return 0;
        }
        if (!confirm) {
            tell(source, UltraStyle.YELLOW + "This puts every one of " + who.name
                    + "'s weekly goals back to zero. The three goals stay the same (everyone shares them this week).");
            tell(source, UltraStyle.GRAY + "To do it, type " + UltraStyle.WHITE + "/lm admin goals reset " + who.name
                    + " confirm");
            return 1;
        }
        WeeklyGoals.adminReset(who.id);
        ok(source, UltraStyle.GREEN + "Started " + who.name + "'s week over: every goal is back to zero, same three goals.");
        notify(source, who.online, "Staff reset your weekly goals. Every goal starts again from zero.");
        return 1;
    }

    private static int goalsGrant(CommandSourceStack source, String playerName, String goalRaw, int amount) {
        Who who = who(source, playerName);
        if (who == null) {
            return 0;
        }
        WeeklyGoals.Goal g = WeeklyGoals.thisWeekGoal(goalRaw);
        if (g == null) {
            List<String> keys = new ArrayList<>();
            for (WeeklyGoals.Goal each : WeeklyGoals.thisWeek()) {
                keys.add(WeeklyGoals.key(each));
            }
            String why = WeeklyGoals.isOffWeekGoal(goalRaw) ? "That goal is not one of this week's goals. "
                    : "No goal called " + goalRaw + ". ";
            return fail(source, why + "This week's goals: " + String.join(", ", keys));
        }
        long have = WeeklyGoals.progress(who.id, g.id());
        if (have >= g.target()) {
            tell(source, UltraStyle.GRAY + who.name + " already finished " + g.title() + ". Nothing changed.");
            return 1;
        }
        long add = amount <= 0 ? 0L : (g.minutes() ? amount * 60_000L : amount);
        long now = WeeklyGoals.adminGrant(who.id, g, add);
        boolean done = now >= g.target();
        String added = amount <= 0 ? "Completed" : "Added " + amount + (g.minutes() ? " minutes" : "") + " to";
        ok(source, UltraStyle.GREEN + added + " " + who.name + "'s goal " + UltraStyle.WHITE + g.title()
                + UltraStyle.GREEN + " (now " + amountText(g, now) + ").");
        notify(source, who.online, done ? "Staff completed your weekly goal: " + g.title() + "."
                : "Staff added progress to your weekly goal: " + g.title() + " (" + amountText(g, now) + ").");
        return 1;
    }

    private static String amountText(WeeklyGoals.Goal g, long have) {
        if (have >= g.target()) {
            return g.minutes() ? (g.target() / 60_000L) + " of " + (g.target() / 60_000L) + " minutes, done"
                    : g.target() + " of " + g.target() + ", done";
        }
        return WeeklyGoals.progressText(g, have);
    }

    // ---- shared --------------------------------------------------------------------------------------------------

    /** A player by name: the online player when there is one, else a profile the server has seen. */
    private record Who(UUID id, String name, ServerPlayer online) {}

    private static Who who(CommandSourceStack source, String raw) {
        MinecraftServer server = source.m_81377_();
        if (server == null || raw == null || raw.isBlank()) {
            fail(source, LmCommandMessages.playerNotFound(raw));
            return null;
        }
        ServerPlayer online = server.m_6846_().m_11255_(raw.trim());
        if (online != null) {
            return new Who(online.m_20148_(), online.m_36316_().getName(), online);
        }
        try {
            var cache = server.m_129927_();
            if (cache != null) {
                var profile = cache.m_10996_(raw.trim());
                if (profile.isPresent() && profile.get().getId() != null) {
                    String name = profile.get().getName() == null ? raw.trim() : profile.get().getName();
                    return new Who(profile.get().getId(), name, null);
                }
            }
        } catch (Throwable ignored) {
        }
        fail(source, LmCommandMessages.playerNotFound(raw));
        return null;
    }

    private static ServerPlayer online(CommandSourceStack source, String raw) {
        MinecraftServer server = source.m_81377_();
        ServerPlayer target = server == null || raw == null ? null : server.m_6846_().m_11255_(raw.trim());
        if (target == null) {
            fail(source, LmCommandMessages.playerOffline(raw));
        }
        return target;
    }

    /** One plain line to the player the staff action was about, unless they ran it on themselves. */
    static void notify(CommandSourceStack source, ServerPlayer target, String text) {
        if (target == null) {
            return;
        }
        ServerPlayer self = source.m_230896_();
        if (self != null && self.m_20148_().equals(target.m_20148_())) {
            return;
        }
        target.m_213846_(Component.m_237113_(UltraStyle.GRAY + text));
    }

    private static int tellAll(CommandSourceStack source, List<String> lines) {
        for (String l : lines) {
            tell(source, l);
        }
        return 1;
    }

    private static void tell(CommandSourceStack source, String text) {
        source.m_288197_(() -> Component.m_237113_(text), false);
    }

    private static void ok(CommandSourceStack source, String text) {
        source.m_288197_(() -> Component.m_237113_(text), true);
    }

    private static int fail(CommandSourceStack source, String text) {
        source.m_81352_(Component.m_237113_(text.startsWith(UltraStyle.MARK) ? text : UltraStyle.RED + text));
        return 0;
    }
}
