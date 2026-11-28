package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoCatalog;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoService;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoStore;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWar;
import com.dbzlegacy.adaptivedifficulty.dojo.DojoWorld;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.rival.RivalSystem;
import com.dbzlegacy.adaptivedifficulty.sparring.DojoRankings;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * LM 4.8.3: {@code /lm admin dojo}, the staff tools for dojos (console counts as staff). Every leaf that names a dojo
 * takes the dojo's name (any case, spaces allowed, quotes optional) or its master's name; the rest of the line is
 * read after it. The underlying changes live in {@link DojoService}, {@link DojoWar} and {@link DojoWorld}.
 */
public final class DojoAdminCommands {
    private DojoAdminCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new DojoAdminCommands());
    }

    /** The branch help, one line per leaf (also printed by the bare {@code /lm admin dojo}). */
    public static String help() {
        return LmStaffHelp.build(
                "Dojo staff",
                "/lm admin dojo",
                UltraStyle.GRAY + "<dojo> is the dojo's name or its master's name. Quotes are optional.",
                new LmStaffHelp.Section("Look and data",
                        LmStaffHelp.cmd("/lm admin dojo list", "every dojo with its master, points and state"),
                        LmStaffHelp.cmd("/lm admin dojo status <dojo|player>", "one dojo's full record"),
                        LmStaffHelp.cmd("/lm admin dojo save", "write dojo data to disk now"),
                        LmStaffHelp.cmd("/lm admin dojo reload", "read dojo data from disk (drops unsaved changes)")),
                new LmStaffHelp.Section("Points and clout",
                        LmStaffHelp.cmd("/lm admin dojo points grant <dojo> <amount>", "give Dojo Points (below 0 takes)"),
                        LmStaffHelp.cmd("/lm admin dojo clout grant <dojo> <amount>", "give season clout (below 0 takes)")),
                new LmStaffHelp.Section("Identity (no cost, no cooldown, free rename kept)",
                        LmStaffHelp.cmd("/lm admin dojo rename <dojo> <name>", "rename the dojo"),
                        LmStaffHelp.cmd("/lm admin dojo style <dojo> <ki|melee|strike|evasion>", "set the style"),
                        LmStaffHelp.cmd("/lm admin dojo banner <dojo> <colour> [pattern:colour ...]", "set the banner"),
                        LmStaffHelp.cmd("/lm admin dojo banner <dojo> default", "back to the style's banner"),
                        LmStaffHelp.note("Layers use vanilla pattern codes, like: banner Kaio Dojo red bs:white cr:15")),
                new LmStaffHelp.Section("Members",
                        LmStaffHelp.cmd("/lm admin dojo promote <dojo> <player>", "make a disciple an elite"),
                        LmStaffHelp.cmd("/lm admin dojo demote <dojo> <player>", "an elite steps down"),
                        LmStaffHelp.cmd("/lm admin dojo warrights <dojo> <player>", "turn an elite's war rights on or off"),
                        LmStaffHelp.cmd("/lm admin clear <player> dojo", "take a player out of their dojo")),
                new LmStaffHelp.Section("The dojo itself",
                        LmStaffHelp.cmd("/lm admin dojo found <player> [name]", "found a dojo for an online player"),
                        LmStaffHelp.cmd("/lm admin dojo close <dojo>", "close it (kept for a reopen)"),
                        LmStaffHelp.cmd("/lm admin dojo reopen <dojo>", "reopen it (every upgrade drops one tier)"),
                        LmStaffHelp.cmd("/lm admin dojo rebuild <dojo>", "build the whole hall again"),
                        LmStaffHelp.cmd("/lm admin dojo disband <dojo> confirm", "delete it and clear its plot (asks first)")),
                new LmStaffHelp.Section("Wars and season",
                        LmStaffHelp.cmd("/lm admin dojo war cancel <dojo> <dojo>", "call off their war, no result kept"),
                        LmStaffHelp.cmd("/lm admin dojo season advance confirm", "end the dojo season now (asks first)")));
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> dojo = Commands.m_82127_("dojo")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> help(ctx.getSource())))
                .then(Commands.m_82127_("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.m_82127_("status")
                        .executes(ctx -> list(ctx.getSource()))
                        .then(rest(dojoSuggest(null)).executes(ctx -> status(ctx.getSource(), rest(ctx)))))
                .then(Commands.m_82127_("save").executes(ctx -> save(ctx.getSource())))
                .then(Commands.m_82127_("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.m_82127_("points").executes(ctx -> usage(ctx.getSource(), POINTS))
                        .then(Commands.m_82127_("grant").executes(ctx -> usage(ctx.getSource(), POINTS))
                                .then(rest(dojoSuggest(null)).executes(ctx -> grant(ctx.getSource(), rest(ctx), false)))))
                .then(Commands.m_82127_("clout").executes(ctx -> usage(ctx.getSource(), CLOUT))
                        .then(Commands.m_82127_("grant").executes(ctx -> usage(ctx.getSource(), CLOUT))
                                .then(rest(dojoSuggest(null)).executes(ctx -> grant(ctx.getSource(), rest(ctx), true)))))
                .then(Commands.m_82127_("close").executes(ctx -> usage(ctx.getSource(), "/lm admin dojo close <dojo>"))
                        .then(rest(dojoSuggest(null)).executes(ctx -> close(ctx.getSource(), rest(ctx), false))))
                .then(Commands.m_82127_("reopen").executes(ctx -> usage(ctx.getSource(), "/lm admin dojo reopen <dojo>"))
                        .then(rest(dojoSuggest(null)).executes(ctx -> close(ctx.getSource(), rest(ctx), true))))
                .then(Commands.m_82127_("rebuild").executes(ctx -> usage(ctx.getSource(), "/lm admin dojo rebuild <dojo>"))
                        .then(rest(dojoSuggest(null)).executes(ctx -> rebuild(ctx.getSource(), rest(ctx)))))
                .then(Commands.m_82127_("disband").executes(ctx -> usage(ctx.getSource(), DISBAND))
                        .then(rest(dojoSuggest(d -> List.of("confirm"))).executes(ctx -> disband(ctx.getSource(), rest(ctx)))))
                .then(Commands.m_82127_("rename").executes(ctx -> usage(ctx.getSource(), RENAME))
                        .then(rest(dojoSuggest(null)).executes(ctx -> rename(ctx.getSource(), rest(ctx)))))
                .then(Commands.m_82127_("style").executes(ctx -> usage(ctx.getSource(), STYLE))
                        .then(rest(dojoSuggest(d -> STYLES)).executes(ctx -> style(ctx.getSource(), rest(ctx)))))
                .then(Commands.m_82127_("banner").executes(ctx -> usage(ctx.getSource(), BANNER))
                        .then(rest(dojoSuggest(d -> List.of("default"))).executes(ctx -> banner(ctx.getSource(), rest(ctx)))))
                .then(memberLeaf("promote"))
                .then(memberLeaf("demote"))
                .then(memberLeaf("warrights"))
                .then(Commands.m_82127_("found").executes(ctx -> usage(ctx.getSource(), FOUND))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> found(ctx.getSource(), StringArgumentType.getString(ctx, "player"), ""))
                                .then(Commands.m_82129_("name", StringArgumentType.greedyString())
                                        .executes(ctx -> found(ctx.getSource(), StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.m_82127_("war").executes(ctx -> usage(ctx.getSource(), WAR))
                        .then(Commands.m_82127_("cancel").executes(ctx -> usage(ctx.getSource(), WAR))
                                .then(rest(dojoSuggest(d -> DojoService.dojoNames())).executes(ctx -> warCancel(ctx.getSource(),
                                        rest(ctx))))))
                .then(Commands.m_82127_("season").executes(ctx -> usage(ctx.getSource(), SEASON))
                        .then(Commands.m_82127_("advance")
                                .executes(ctx -> season(ctx.getSource(), false))
                                .then(Commands.m_82127_("confirm").executes(ctx -> season(ctx.getSource(), true)))));
        event.getDispatcher().register(CommandAccess.underLmAdmin(dojo));
        AdaptiveDifficultyMod.LOGGER.info("[{}] registered /lm admin dojo", AdaptiveDifficultyMod.MOD_ID);
    }

    private static final String POINTS = "/lm admin dojo points grant <dojo> <amount>";
    private static final String CLOUT = "/lm admin dojo clout grant <dojo> <amount>";
    private static final String DISBAND = "/lm admin dojo disband <dojo> confirm";
    private static final String RENAME = "/lm admin dojo rename <dojo> <new name>";
    private static final String STYLE = "/lm admin dojo style <dojo> <ki|melee|strike|evasion>";
    private static final String BANNER = "/lm admin dojo banner <dojo> <colour> [pattern:colour ...] (or default)";
    private static final String FOUND = "/lm admin dojo found <player> [dojo name]";
    private static final String WAR = "/lm admin dojo war cancel <dojo> <other dojo>";
    private static final String SEASON = "/lm admin dojo season advance confirm";
    private static final List<String> STYLES = List.of("ki", "melee", "strike", "evasion");

    private static LiteralArgumentBuilder<CommandSourceStack> memberLeaf(String name) {
        String use = "/lm admin dojo " + name + " <dojo> <player>";
        return Commands.m_82127_(name).executes(ctx -> usage(ctx.getSource(), use))
                .then(rest(dojoSuggest(DojoService::discipleNames)).executes(ctx -> member(ctx.getSource(), name,
                        rest(ctx))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> rest(SuggestionProvider<CommandSourceStack> s) {
        return Commands.m_82129_("args", StringArgumentType.greedyString()).suggests(s);
    }

    private static String rest(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "args");
    }

    /**
     * Dojo names that start with what is typed; once a whole dojo name and a space are typed, {@code after}'s words
     * for that dojo (styles, disciples, confirm, the other dojo).
     */
    private static SuggestionProvider<CommandSourceStack> dojoSuggest(Function<DojoStore.Dojo, List<String>> after) {
        return (ctx, builder) -> {
            String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (DojoStore.Dojo d : new ArrayList<>(DojoStore.get().dojos.values())) {
                String n = d.name == null ? "" : d.name;
                if (n.isBlank()) {
                    continue;
                }
                String low = n.toLowerCase(Locale.ROOT);
                if (low.startsWith(typed)) {
                    builder.suggest(n);
                } else if (after != null && typed.startsWith(low + " ")) {
                    String tail = typed.substring(low.length() + 1);
                    for (String o : after.apply(d)) {
                        if (o != null && !o.isBlank() && o.toLowerCase(Locale.ROOT).startsWith(tail)
                                && !o.equalsIgnoreCase(n)) {
                            builder.suggest(n + " " + o);
                        }
                    }
                }
            }
            return builder.buildFuture();
        };
    }

    // ------------------------------------------------------------------ reading a dojo out of the line

    /** A dojo named at the start of a line, and the words after it. */
    private record Named(DojoStore.Dojo dojo, String rest, String problem) {}

    /**
     * The dojo at the start of {@code text}: a quoted name, else the longest run of leading words that names a dojo
     * (its name or its master's name). {@code needRest}: some words must be left after it.
     */
    private static Named named(String text, boolean needRest) {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("\"")) {
            int end = t.indexOf('"', 1);
            if (end > 0) {
                String inside = t.substring(1, end);
                return pick(inside, t.substring(end + 1).trim());
            }
        }
        String[] words = t.isEmpty() ? new String[0] : t.split("\\s+");
        for (int k = words.length - (needRest ? 1 : 0); k >= 1; k--) {
            String head = String.join(" ", Arrays.copyOfRange(words, 0, k));
            if (!DojoService.lookup(head).isEmpty()) {
                return pick(head, String.join(" ", Arrays.copyOfRange(words, k, words.length)));
            }
        }
        return new Named(null, "", noDojo(t));
    }

    private static Named pick(String head, String rest) {
        List<DojoStore.Dojo> hits = DojoService.lookup(head);
        if (hits.isEmpty()) {
            return new Named(null, rest, noDojo(head));
        }
        if (hits.size() > 1) {
            List<String> masters = new ArrayList<>();
            for (DojoStore.Dojo d : hits) {
                masters.add(d.name + " (master " + DojoService.nameOf(d.masterUuid) + ")");
            }
            return new Named(null, rest, hits.size() + " dojos match " + head + ": " + String.join(", ", masters)
                    + ". Name the master instead.");
        }
        return new Named(hits.get(0), rest, null);
    }

    private static String noDojo(String text) {
        return "No dojo matches " + (text == null || text.isBlank() ? "that" : "\"" + text.trim() + "\"")
                + ". See every dojo with /lm admin dojo list.";
    }

    // ------------------------------------------------------------------ replies

    /** Each line to the staff member: in game straight to the player (Mohist can drop feedback), else the console. */
    private static void say(CommandSourceStack source, boolean ok, List<String> lines) {
        ServerPlayer p = source.m_230896_();
        for (String raw : lines) {
            for (String line : raw.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                String text = ok ? line : UltraStyle.DANGER + line;
                if (p != null) {
                    p.m_213846_(Component.m_237113_(text));
                } else if (ok) {
                    source.m_288197_(() -> Component.m_237113_(text), false);
                } else {
                    source.m_81352_(Component.m_237113_(text));
                }
            }
        }
    }

    private static int reply(CommandSourceStack source, boolean ok, String line) {
        say(source, ok, List.of(ok ? UltraStyle.GREEN + line : line));
        return ok ? 1 : 0;
    }

    private static int reply(CommandSourceStack source, String action, DojoService.StaffResult r) {
        if (r.ok()) {
            log(source, action, r.text());
        }
        return reply(source, r.ok(), r.text());
    }

    private static int usage(CommandSourceStack source, String syntax) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        say(source, true, List.of(UltraStyle.LABEL + "Type: " + UltraStyle.WHITE + syntax,
                UltraStyle.DIM + "All dojo staff commands: /lm admin dojo help"));
        return 0;
    }

    private static void log(CommandSourceStack source, String action, String text) {
        ServerPlayer who = source.m_230896_();
        AdaptiveDifficultyMod.LOGGER.info("[{}] staff dojo {} by {}: {}", AdaptiveDifficultyMod.MOD_ID, action,
                who == null ? "console" : who.m_7755_().getString(), text);
    }

    /** One plain line to {@code uuid} when they are online. */
    private static void tell(MinecraftServer server, String uuid, String text) {
        try {
            ServerPlayer p = server == null || uuid == null ? null : server.m_6846_().m_11259_(UUID.fromString(uuid));
            if (p != null) {
                DmzRewards.msg(p, LmChat.info("Dojo", text));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    // ------------------------------------------------------------------ leaves

    private static int help(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        say(source, true, List.of(help()));
        return 1;
    }

    private static int list(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        List<DojoStore.Dojo> all = new ArrayList<>(DojoStore.get().dojos.values());
        all.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name));
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + "Dojos " + UltraStyle.LABEL + "(" + all.size() + ")");
        if (all.isEmpty()) {
            lines.add(UltraStyle.LABEL + "No dojo has been founded yet.");
        }
        for (DojoStore.Dojo d : all) {
            lines.add(UltraStyle.WHITE + d.name + UltraStyle.LABEL + " - master " + UltraStyle.VALUE
                    + DojoService.nameOf(d.masterUuid) + UltraStyle.LABEL + " - " + (d.open ? "open" : "closed") + ", "
                    + UltraStyle.VALUE + DojoService.disciples(d).size() + UltraStyle.LABEL + " disciples, "
                    + UltraStyle.VALUE + d.points + UltraStyle.LABEL + " points");
        }
        lines.add(UltraStyle.DIM + "One dojo in full: /lm admin dojo status <dojo>");
        say(source, true, lines);
        return 1;
    }

    private static int status(CommandSourceStack source, String text) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        Named n = named(text, false);
        if (n.dojo() != null && n.rest().isEmpty()) {
            say(source, true, DojoService.statusLines(n.dojo()));
            return 1;
        }
        // A player name: the dojos they are in.
        UUID player = playerUuid(source.m_81377_(), text.trim());
        if (player != null) {
            List<DojoService.Affiliation> in = DojoService.affiliations(player);
            DojoStore.Dojo closed = DojoStore.get().dojos.get(player.toString());
            if (in.isEmpty() && closed == null) {
                return reply(source, false, text.trim() + " is not in any dojo.");
            }
            List<String> lines = new ArrayList<>();
            for (DojoService.Affiliation a : in) {
                lines.add(UltraStyle.LABEL + text.trim() + " is " + (a.role() == DojoService.Role.MASTER ? "the master"
                        : "a disciple") + " of " + UltraStyle.VALUE + a.dojo().name);
                lines.addAll(DojoService.statusLines(a.dojo()));
            }
            if (closed != null && !closed.open) {
                lines.add(UltraStyle.LABEL + text.trim() + " leads a closed dojo, " + UltraStyle.VALUE + closed.name);
                lines.addAll(DojoService.statusLines(closed));
            }
            say(source, true, lines);
            return 1;
        }
        return reply(source, false, n.problem() != null ? n.problem() : noDojo(text));
    }

    /** A player's UUID by online name, a dojo record or the spar records; null when none is known. */
    private static UUID playerUuid(MinecraftServer server, String name) {
        ServerPlayer p = RivalSystem.findOnline(server, name);
        if (p != null) {
            return p.m_20148_();
        }
        for (java.util.Map.Entry<String, DojoStore.PlayerRecord> e : DojoStore.get().players.entrySet()) {
            if (e.getValue() != null && name.equalsIgnoreCase(e.getValue().name)) {
                try {
                    return UUID.fromString(e.getKey());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (DojoStore.Dojo d : DojoStore.get().dojos.values()) {
            for (String u : DojoService.disciples(d)) {
                if (DojoService.nameOf(u).equalsIgnoreCase(name)) {
                    try {
                        return UUID.fromString(u);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static int save(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        DojoStore.get().markDirty();
        DojoStore.get().save();
        com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().markDirty();
        com.dbzlegacy.adaptivedifficulty.sparring.SparStore.get().save();
        log(source, "save", "saved");
        return reply(source, true, "Saved dojo data (dojos and their member bonds) to disk.");
    }

    private static int reload(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        return reply(source, "reload", DojoService.reloadByStaff());
    }

    private static int grant(CommandSourceStack source, String text, boolean clout) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        String t = text.trim();
        int cut = t.lastIndexOf(' ');
        int amount;
        try {
            amount = cut < 0 ? 0 : Integer.parseInt(t.substring(cut + 1));
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (cut < 0 || amount == 0 || Math.abs((long) amount) > 1_000_000L) {
            return reply(source, false, "End the line with a whole number from -1000000 to 1000000, not 0. Type: "
                    + (clout ? CLOUT : POINTS));
        }
        Named n = named(t.substring(0, cut), false);
        if (n.dojo() == null || !n.rest().isEmpty()) {
            return reply(source, false, n.problem() != null ? n.problem() : noDojo(t.substring(0, cut)));
        }
        DojoStore.Dojo d = n.dojo();
        int change = clout ? DojoService.grantClout(d, amount) : DojoService.grantPoints(d, amount);
        String what = clout ? "clout" : "Dojo Points";
        String now = clout ? d.clout + " this season" : String.valueOf(d.points);
        String line = (change >= 0 ? "Gave " + d.name + " " + change + " " + what
                : "Took " + (-change) + " " + what + " from " + d.name) + " (now " + now + ").";
        log(source, clout ? "clout" : "points", line);
        if (change != 0) {
            tell(source.m_81377_(), d.masterUuid, "Staff " + (change > 0 ? "gave your dojo " + change : "took "
                    + (-change)) + " " + what + (change > 0 ? "." : " from your dojo.") + " It has " + now + " now.");
        }
        return reply(source, true, line);
    }

    private static int close(CommandSourceStack source, String text, boolean reopen) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        Named n = named(text, false);
        if (n.dojo() == null || !n.rest().isEmpty()) {
            return reply(source, false, n.problem() != null ? n.problem() : noDojo(text));
        }
        DojoService.StaffResult r = reopen ? DojoService.reopenByStaff(n.dojo()) : DojoService.closeByStaff(n.dojo());
        if (r.ok()) {
            tell(source.m_81377_(), n.dojo().masterUuid, "Staff " + (reopen ? "reopened" : "closed") + " your dojo, "
                    + n.dojo().name + ".");
        }
        return reply(source, reopen ? "reopen" : "close", r);
    }

    private static int rebuild(CommandSourceStack source, String text) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        Named n = named(text, false);
        if (n.dojo() == null || !n.rest().isEmpty()) {
            return reply(source, false, n.problem() != null ? n.problem() : noDojo(text));
        }
        if (!DojoWorld.rebuild(source.m_81377_(), n.dojo())) {
            return reply(source, false, DojoWorld.NOT_LOADED);
        }
        log(source, "rebuild", n.dojo().name);
        return reply(source, true, "Rebuilding " + n.dojo().name + "'s whole hall. It takes a few seconds; anyone "
                + "inside a wall is moved to the path.");
    }

    private static int disband(CommandSourceStack source, String text) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        String t = text.trim();
        boolean confirm = false;
        Named n = named(t, false);
        if ((n.dojo() == null || !n.rest().isEmpty()) && t.toLowerCase(Locale.ROOT).endsWith(" confirm")) {
            confirm = true;
            n = named(t.substring(0, t.length() - " confirm".length()), false);
        }
        if (n.dojo() == null || !n.rest().isEmpty()) {
            return reply(source, false, n.problem() != null ? n.problem() : noDojo(t));
        }
        DojoStore.Dojo d = n.dojo();
        if (!confirm) {
            int members = DojoService.disciples(d).size();
            say(source, true, List.of(
                    UltraStyle.GOLD + "Disband " + d.name + "?",
                    UltraStyle.YELLOW + "This deletes the dojo for good: its " + d.points + " Dojo Points, upgrades and"
                            + " records go, its " + members + (members == 1 ? " disciple is" : " disciples are")
                            + " released, any war it is in is called off, and its hall is cleared to empty void."
                            + " This cannot be undone.",
                    UltraStyle.LABEL + "To do it, run " + UltraStyle.WHITE + "/lm admin dojo disband " + d.name
                            + " confirm"));
            return 1;
        }
        return reply(source, "disband", DojoService.disband(source.m_81377_(), d));
    }

    private static int rename(CommandSourceStack source, String text) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        Named n = named(text, true);
        if (n.dojo() == null || n.rest().isEmpty()) {
            return reply(source, false, n.problem() != null ? n.problem() : "Give the new name after the dojo. Type: "
                    + RENAME);
        }
        String old = n.dojo().name;
        String name = n.rest().replace("\"", "").trim();
        DojoService.StaffResult r = DojoService.renameByStaff(n.dojo(), name);
        if (r.ok()) {
            tell(source.m_81377_(), n.dojo().masterUuid, "Staff renamed your dojo from " + old + " to " + name + ".");
        }
        return reply(source, "rename", r);
    }

    private static int style(CommandSourceStack source, String text) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        String t = text.trim();
        int cut = t.lastIndexOf(' ');
        if (cut < 0) {
            return reply(source, false, "Give the style after the dojo. Type: " + STYLE);
        }
        Named n = named(t.substring(0, cut), false);
        if (n.dojo() == null || !n.rest().isEmpty()) {
            return reply(source, false, n.problem() != null ? n.problem() : noDojo(t.substring(0, cut)));
        }
        String style = t.substring(cut + 1);
        DojoService.StaffResult r = DojoService.styleByStaff(source.m_81377_(), n.dojo(), style);
        if (r.ok()) {
            DojoCatalog.Style s = DojoCatalog.Style.of(style);
            tell(source.m_81377_(), n.dojo().masterUuid, "Staff set your dojo's style to " + (s == null ? style : s.label)
                    + ".");
        }
        return reply(source, "style", r);
    }

    private static int banner(CommandSourceStack source, String text) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        Named n = named(text, true);
        if (n.dojo() == null || n.rest().isEmpty()) {
            return reply(source, false, n.problem() != null ? n.problem() : "Give the banner after the dojo. Type: "
                    + BANNER);
        }
        DojoService.StaffResult r = DojoService.bannerByStaff(source.m_81377_(), n.dojo(), n.rest());
        if (r.ok()) {
            tell(source.m_81377_(), n.dojo().masterUuid, "Staff changed your dojo's banner.");
        }
        return reply(source, "banner", r);
    }

    private static int member(CommandSourceStack source, String action, String text) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        String t = text.trim();
        int cut = t.lastIndexOf(' ');
        if (cut < 0) {
            return reply(source, false, "Give the player after the dojo. Type: /lm admin dojo " + action
                    + " <dojo> <player>");
        }
        Named n = named(t.substring(0, cut), false);
        if (n.dojo() == null || !n.rest().isEmpty()) {
            return reply(source, false, n.problem() != null ? n.problem() : noDojo(t.substring(0, cut)));
        }
        DojoStore.Dojo d = n.dojo();
        String who = t.substring(cut + 1);
        String uuid = DojoService.discipleNamed(d, who);
        if (uuid == null) {
            return reply(source, false, who + " is not a disciple of " + d.name + ". See its members with "
                    + "/lm admin dojo status " + d.name + ".");
        }
        DojoService.StaffResult r = switch (action) {
            case "promote" -> DojoService.promoteByStaff(d, uuid);
            case "demote" -> DojoService.demoteByStaff(d, uuid);
            default -> DojoService.warRightsByStaff(d, uuid);
        };
        if (r.ok()) {
            String line = switch (action) {
                case "promote" -> "Staff made you an elite of " + d.name + ".";
                case "demote" -> "Staff stepped you down as an elite of " + d.name + ".";
                default -> d.warRights.contains(uuid) ? "Staff gave you standing war rights in " + d.name + "."
                        : "Your wars for " + d.name + " need the master's sign-off again.";
            };
            tell(source.m_81377_(), uuid, line);
        }
        return reply(source, action, r);
    }

    private static int found(CommandSourceStack source, String playerName, String name) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = RivalSystem.findOnline(source.m_81377_(), playerName);
        if (target == null) {
            return reply(source, false, "The player has to be online to found a dojo. " + playerName
                    + " isn't online right now.");
        }
        DojoService.StaffResult r = DojoService.foundByStaff(target, name);
        if (r.ok()) {
            DojoStore.Dojo d = DojoService.dojoOf(target.m_20148_());
            tell(source.m_81377_(), target.m_20148_().toString(), "Staff founded a dojo for you: "
                    + (d == null ? "your dojo" : d.name) + ". Open it with /lm dojo.");
        }
        return reply(source, "found", r);
    }

    private static int warCancel(CommandSourceStack source, String text) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        String t = text.trim();
        DojoStore.Dojo a = null;
        DojoStore.Dojo b = null;
        String problem = null;
        // A quoted first dojo, else every split point until both halves name one dojo each.
        Named first = named(t, true);
        if (first.dojo() != null && t.startsWith("\"")) {
            Named second = named(first.rest(), false);
            a = first.dojo();
            b = second.dojo() != null && second.rest().isEmpty() ? second.dojo() : null;
            problem = b == null ? (second.problem() != null ? second.problem() : noDojo(first.rest())) : null;
        } else {
            String[] words = t.isEmpty() ? new String[0] : t.split("\\s+");
            for (int k = 1; k < words.length && b == null; k++) {
                List<DojoStore.Dojo> left = DojoService.lookup(String.join(" ", Arrays.copyOfRange(words, 0, k)));
                Named right = named(String.join(" ", Arrays.copyOfRange(words, k, words.length)), false);
                if (left.size() == 1 && right.dojo() != null && right.rest().isEmpty()) {
                    a = left.get(0);
                    b = right.dojo();
                }
            }
            if (b == null) {
                problem = "Name two dojos, like: " + WAR + ". Put a name in quotes if it is hard to tell apart. "
                        + "See every dojo with /lm admin dojo list.";
            }
        }
        if (a == null || b == null) {
            return reply(source, false, problem);
        }
        if (a == b) {
            return reply(source, false, "Name two different dojos. Type: " + WAR);
        }
        int n = DojoWar.staffCancel(source.m_81377_(), a.masterUuid, b.masterUuid);
        if (n == 0) {
            return reply(source, false, a.name + " and " + b.name + " have no war running or waiting. See a dojo's wars "
                    + "with /lm admin dojo status " + a.name + ".");
        }
        String line = "Called off " + (n == 1 ? "the war" : n + " wars") + " between " + a.name + " and " + b.name
                + ". Fighters went back where they stood; no result, points or clout were recorded.";
        log(source, "war cancel", line);
        tell(source.m_81377_(), a.masterUuid, "Staff called off your war with " + b.name + ". No result was recorded.");
        tell(source.m_81377_(), b.masterUuid, "Staff called off your war with " + a.name + ". No result was recorded.");
        return reply(source, true, line);
    }

    /** The same season advance as {@code /lm dojo season advance}, under the staff branch. */
    private static int season(CommandSourceStack source, boolean confirm) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        List<String> lines = new ArrayList<>();
        for (String line : confirm ? DojoRankings.advanceSeasonByStaff() : DojoRankings.seasonAdvancePreviewLines()) {
            lines.add(line.replace("/lm dojo season advance confirm", SEASON));
        }
        say(source, true, lines);
        if (confirm) {
            log(source, "season advance", "season advanced");
        }
        return 1;
    }
}
