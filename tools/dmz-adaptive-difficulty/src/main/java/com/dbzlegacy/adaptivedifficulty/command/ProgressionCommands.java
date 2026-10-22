package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.PrestigeMenu;
import com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.ProgressionMenu;
import com.dbzlegacy.adaptivedifficulty.gui.SkillsMenu;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionSystem;
import com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** {@code /progression} — staff progression tools. Meditation is not a slash command. */
public final class ProgressionCommands {
    private ProgressionCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ProgressionCommands());
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(build("progression"));

        // Prestige — player shop with parity for Prestige → Shop → Exchange/skills/tiers
        // Effects (majin/mutant/godki) in the Ultra GUI, plus the staff menu backend.
        // Admin wallet edits stay on /padmin.
        event.getDispatcher().register(Commands.m_82127_("prestige")
                .requires(CommandAccess::isPlayerSlashUser)
                .executes(ctx -> prestigeRoot(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> prestigeHelp(ctx.getSource())))
                .then(Commands.m_82127_("balance")
                        .executes(ctx -> prestigeDo(ctx.getSource(), "balance", "")))
                .then(Commands.m_82127_("effects")
                        .executes(ctx -> prestigeEffects(ctx.getSource())))
                .then(Commands.m_82127_("turnin")
                        .then(LmCommandSuggestions.word("amount", LmCommandSuggestions.PRESTIGE_TURNIN_AMOUNTS)
                                .executes(ctx -> prestigeDo(
                                        ctx.getSource(),
                                        "turnin",
                                        StringArgumentType.getString(ctx, "amount")))))
                .then(Commands.m_82127_("buy")
                        .then(Commands.m_82127_("skill")
                                .then(LmCommandSuggestions.word("skill", LmCommandSuggestions.PRESTIGE_SKILLS)
                                        .executes(ctx -> prestigeDo(
                                                ctx.getSource(),
                                                "skill",
                                                StringArgumentType.getString(ctx, "skill")))))
                        .then(Commands.m_82127_("tier")
                                .then(LmCommandSuggestions.word("tier", LmCommandSuggestions.PRESTIGE_TIERS)
                                        .executes(ctx -> prestigeDo(
                                                ctx.getSource(),
                                                "tier",
                                                StringArgumentType.getString(ctx, "tier")))))
                        .then(Commands.m_82127_("majin").executes(ctx -> prestigeDo(ctx.getSource(), "majin", "")))
                        .then(Commands.m_82127_("mutant").executes(ctx -> prestigeDo(ctx.getSource(), "mutant", "")))
                        .then(Commands.m_82127_("godki").executes(ctx -> prestigeDo(ctx.getSource(), "godki", ""))))
                .then(Commands.m_82127_("remove")
                        .then(Commands.m_82127_("majin").executes(ctx -> prestigeDo(ctx.getSource(), "unmajin", "")))
                        .then(Commands.m_82127_("mutant").executes(ctx -> prestigeDo(ctx.getSource(), "unmutant", ""))))
                .then(CommandAccess.staffLiteral("menu")
                        .executes(ctx -> prestigeGui(ctx.getSource(), "main")))
                .then(CommandAccess.staffLiteral("do")
                        .then(Commands.m_82127_("confirm")
                                .executes(ctx -> prestigeConfirm(ctx.getSource(), "main"))
                                .then(Commands.m_82129_("page", StringArgumentType.word())
                                        .executes(ctx -> prestigeConfirm(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "page")))))
                        .then(Commands.m_82127_("page")
                                .then(Commands.m_82129_("page", StringArgumentType.word())
                                        .executes(ctx -> prestigeGui(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "page")))))));

        event.getDispatcher().register(
                PrestigeAdminCommandTree.attach(
                        Commands.m_82127_("padmin").requires(StaffAccess::isStaffSource)));

        // SkillUnlock admin browser — staff only
        event.getDispatcher().register(Commands.m_82127_("skills")
                .requires(StaffAccess::isStaffSource)
                .executes(ctx -> skillsPage(ctx.getSource(), "core"))
                .then(Commands.m_82127_("help").executes(ctx -> skillsHelp(ctx.getSource())))
                .then(Commands.m_82127_("do")
                        .then(Commands.m_82127_("page")
                                .then(Commands.m_82129_("page", StringArgumentType.word())
                                        .executes(ctx -> skillsPage(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "page")))))));

        // Donator Skill Check
        event.getDispatcher().register(Commands.m_82127_("skillcheck")
                .requires(StaffAccess::hasSkillCheckSource)
                .executes(ctx -> skillCheckPage(ctx.getSource(), "core"))
                .then(Commands.m_82127_("help").executes(ctx -> skillCheckHelp(ctx.getSource())))
                .then(Commands.m_82127_("do")
                        .then(Commands.m_82127_("page")
                                .then(Commands.m_82129_("page", StringArgumentType.word())
                                        .executes(ctx -> skillCheckPage(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "page"))))))
                .then(Commands.m_82129_("page", StringArgumentType.word())
                        .executes(ctx -> skillCheckPage(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "page")))));

        // End Dimension Strength — staff clear/repair only (players summon via Difficulty GUI)
        event.getDispatcher().register(Commands.m_82127_("enddragon")
                .requires(StaffAccess::isStaffSource)
                .executes(ctx -> endDragonHelp(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> endDragonHelp(ctx.getSource())))
                .then(Commands.m_82127_("repair").executes(ctx -> endRepair(ctx.getSource())))
                .then(Commands.m_82127_("clear").executes(ctx -> endClear(ctx.getSource()))));

        event.getDispatcher().register(
                Commands.m_82127_("androidify")
                        .requires(StaffAccess::isStaffSource)
                        .then(Commands.m_82127_("help").executes(ctx -> androidifyHelp(ctx.getSource())))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> androidPlayer(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"))))
                        .executes(ctx -> {
                            if (playerOrNull(ctx.getSource()) == null) {
                                ctx.getSource().m_288197_(
                                        () -> Component.m_237113_(
                                                LmCommandMessages.tryCommand("/androidify <player>")),
                                        false);
                                return 0;
                            }
                            return androidSelf(ctx.getSource());
                        })
        );

        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] registered /progression /prestige /padmin /skills /skillcheck /enddragon /androidify",
                AdaptiveDifficultyMod.MOD_ID
        );
    }

    private static int skillsPage(CommandSourceStack source, String page) {
        ServerPlayer p = playerOrNull(source);
        if (p == null) {
            return 0;
        }
        if (!StaffAccess.isStaff(p)) {
            reply(source, p, LmCommandMessages.STAFF_ONLY);
            return 0;
        }
        SkillsMenu.open(p, page);
        return 1;
    }

    private static int skillCheckPage(CommandSourceStack source, String page) {
        ServerPlayer p = playerOrNull(source);
        if (p == null) {
            return 0;
        }
        if (!SkillCheckService.canUse(p)) {
            reply(source, p, LmCommandMessages.SKILLCHECK_DONATOR);
            return 0;
        }
        SkillCheckService.open(p, page);
        return 1;
    }

    private static int prestigeGui(CommandSourceStack source, String page) {
        ServerPlayer p = playerOrNull(source);
        if (p == null) {
            return 0;
        }
        PrestigeMenu.open(p, page);
        return 1;
    }

    static int prestigeAdminHelp(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        LmCommandFeedback.tellLines(source, LmCommandHelp.prestigeAdmin());
        return 1;
    }

    private static int prestigeRoot(CommandSourceStack source) {
        ServerPlayer p = playerOrNull(source);
        if (p == null) {
            return 0;
        }
        if (StaffAccess.isStaff(p)) {
            return prestigeGui(source, "main");
        }
        return prestigeHelp(source);
    }

    private static int prestigeHelp(CommandSourceStack source) {
        LmCommandFeedback.tellLines(source, LmCommandHelp.prestigePlayer());
        return 1;
    }

    /**
     * View-only shop effects (favor, majin/mutant/ultimate, God Ki) — read from the same
     * placeholders the Prestige → Effects screens use. No purchase path here.
     */
    private static int prestigeEffects(CommandSourceStack source) {
        ServerPlayer p = playerOrNull(source);
        if (p == null) {
            source.m_288197_(() -> Component.m_237113_(LmCommandMessages.NEED_IN_GAME), false);
            return 0;
        }
        Map<String, String> ph = ProgressionGuiApi.prestigePlaceholders(p);
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.LABEL + "Your Prestige shop effects");
        boolean majin = "true".equalsIgnoreCase(ph.get("majin"));
        boolean mutant = "true".equalsIgnoreCase(ph.get("mutant"));
        lines.add(UltraStyle.labelValue("Majin absorption", majin ? "owned" : "not owned"));
        lines.add(UltraStyle.labelValue("Mutant upgrade", mutant ? "owned" : "not owned"));
        boolean ultimate = "true".equalsIgnoreCase(ph.get("ultimate"));
        // Real ownership: the shop flag, or the DMZ ultimate skill earned from Old Kai.
        boolean ultimateEarned = "true".equalsIgnoreCase(ph.get("ultimate_earned"));
        lines.add(UltraStyle.labelValue(
                "Ultimate", (ultimate || ultimateEarned) ? "owned" : "not owned"));
        int godki = 0;
        try {
            godki = Integer.parseInt(ph.getOrDefault("godki_invested", "0").trim());
        } catch (NumberFormatException ignored) {
        }
        lines.add(UltraStyle.labelValue(
                "God Ki Enhancement",
                godki + " invested, mastery " + ph.getOrDefault("godki_mastery", "?")));
        lines.add(UltraStyle.labelValue("Favor", ph.getOrDefault("points", "?")));
        for (String line : lines) {
            LmCommandFeedback.tell(source, line);
        }
        return 1;
    }

    private static int prestigeDo(CommandSourceStack source, String action, String arg) {
        ServerPlayer p = playerOrNull(source);
        if (p == null) {
            source.m_288197_(() -> Component.m_237113_(LmCommandMessages.NEED_IN_GAME), false);
            return 0;
        }
        String msg = ProgressionGuiApi.handlePrestigeDo(p, action, arg, "");
        if (msg != null && !msg.isBlank()) {
            reply(source, p, msg);
        }
        return msg != null && msg.startsWith("§c") ? 0 : 1;
    }

    private static int skillsHelp(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        LmCommandFeedback.tellLines(source, LmCommandHelp.skillsStaff());
        return 1;
    }

    private static int skillCheckHelp(CommandSourceStack source) {
        LmCommandFeedback.tellLines(source, LmCommandHelp.skillCheck());
        return 1;
    }

    private static int androidifyHelp(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        LmCommandFeedback.tellLines(source, LmCommandHelp.androidifyStaff());
        return 1;
    }

    static int prestigeAdminInfo(CommandSourceStack source, String playerName) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolveAdminTarget(source, playerName);
        if (target == null) {
            reply(source, playerOrNull(source),
                    playerName == null || playerName.isBlank()
                            ? LmCommandMessages.playerOffline(null)
                            : LmCommandMessages.playerOffline(playerName));
            return 0;
        }
        reply(source, playerOrNull(source),
                com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin.info(target));
        return 1;
    }

    static int prestigeAdminAdjust(
            CommandSourceStack source, String field, String playerName, String mode, int amount
    ) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolveAdminTarget(source, playerName);
        if (target == null) {
            reply(source, playerOrNull(source), LmCommandMessages.playerOffline(playerName));
            return 0;
        }
        String msg = switch (field == null ? "" : field.toLowerCase()) {
            case "held" -> com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin
                    .adjustHeld(target, mode, amount);
            case "completed" -> com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin
                    .adjustCompleted(target, mode, amount);
            case "points" -> com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin
                    .adjustPoints(target, mode, amount);
            case "breakthroughs" -> com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin
                    .adjustBreakthroughs(target, mode, amount);
            case "godki" -> com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin
                    .adjustGodKi(target, mode, amount);
            default -> "§cUnknown field: " + field;
        };
        reply(source, playerOrNull(source), msg);
        return msg != null && msg.startsWith("§c") ? 0 : 1;
    }

    static int prestigeAdminListSkills(CommandSourceStack source, String playerName) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolveAdminTarget(source, playerName);
        if (target == null) {
            reply(source, playerOrNull(source), LmCommandMessages.playerOffline(playerName));
            return 0;
        }
        reply(source, playerOrNull(source),
                com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin.listSkills(target));
        return 1;
    }

    static int prestigeAdminSkill(
            CommandSourceStack source, String playerName, String skillId, String mode, int levels
    ) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolveAdminTarget(source, playerName);
        if (target == null) {
            reply(source, playerOrNull(source), LmCommandMessages.playerOffline(playerName));
            return 0;
        }
        String msg = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin.adjustSkill(
                target, skillId, mode, levels);
        reply(source, playerOrNull(source), msg);
        return msg != null && msg.startsWith("§c") ? 0 : 1;
    }

    static int prestigeAdminTier(CommandSourceStack source, String playerName, String mode, String tierRaw) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolveAdminTarget(source, playerName);
        if (target == null) {
            reply(source, playerOrNull(source), LmCommandMessages.playerOffline(playerName));
            return 0;
        }
        int tierId;
        if ("all".equalsIgnoreCase(tierRaw)) {
            tierId = 0;
        } else {
            try {
                tierId = Integer.parseInt(tierRaw);
            } catch (NumberFormatException e) {
                reply(source, playerOrNull(source), "§cTier must be a number from 0–7, or §fall§c.");
                return 0;
            }
        }
        String msg = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeAdmin.adjustTier(target, mode, tierId);
        reply(source, playerOrNull(source), msg);
        return msg != null && msg.startsWith("§c") ? 0 : 1;
    }

    private static ServerPlayer resolveAdminTarget(CommandSourceStack source, String playerName) {
        if (playerName == null || playerName.isBlank() || "self".equalsIgnoreCase(playerName)
                || "me".equalsIgnoreCase(playerName)) {
            return playerOrNull(source);
        }
        var server = source.m_81377_();
        if (server == null) {
            return null;
        }
        ServerPlayer byName = server.m_6846_().m_11255_(playerName.trim());
        if (byName != null) {
            return byName;
        }
        String needle = playerName.trim().toLowerCase();
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            if (p != null && p.m_6302_() != null && p.m_6302_().toLowerCase().startsWith(needle)) {
                return p;
            }
        }
        return null;
    }

    private static int prestigeConfirm(CommandSourceStack source, String page) {
        ServerPlayer p = playerOrNull(source);
        if (p == null) {
            return 0;
        }
        String msg = com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeSystem.confirmOrPrompt(p);
        // GUI/lmdo path surfaces this via GuiFeedback; chat-menu backend already printed detail.
        // Direct /prestige do on inventory backends still needs a visible reply.
        if (msg != null && !msg.isBlank()) {
            String backend = DifficultyConfig.get().guiBackend;
            if (backend != null && !"chat".equalsIgnoreCase(backend.trim())) {
                for (String line : msg.split("\n")) {
                    if (line != null && !line.isBlank()) {
                        DmzRewards.msg(p, line);
                    }
                }
            }
        }
        PrestigeMenu.open(p, page == null || page.isBlank() ? "main" : page);
        return 1;
    }

    private static int endDragonHelp(CommandSourceStack source) {
        LmCommandFeedback.tellLines(source, LmCommandHelp.endDragonStaff());
        return 1;
    }

    private static int endRepair(CommandSourceStack source) {
        ServerPlayer p = playerOrNull(source);
        if (p == null) {
            return 0;
        }
        return com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength.cmdRepairPodium(p);
    }

    private static int endClear(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        int removed = com.dbzlegacy.adaptivedifficulty.progression.end.EndDimensionStrength
                .cmdCleanupDragons(source.m_81377_(), player);
        if (player == null) {
            int count = removed;
            source.m_288197_(() -> Component.m_237113_(
                    "§7[The End] Cleared §f" + count + "§7 dragon(s)."), true);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        // Players may run android remove. Every other leaf is staff-only.
        // There is no meditation slash command.
        return Commands.m_82127_(name)
                .executes(ctx -> helpOrGui(ctx.getSource()))
                .then(Commands.m_82127_("gui")
                        .requires(ProgressionCommands::staff)
                        .executes(ctx -> gui(ctx.getSource(), "main")))
                .then(Commands.m_82127_("help").executes(ctx -> helpOrGui(ctx.getSource())))
                .then(Commands.m_82127_("status")
                        .requires(ProgressionCommands::staff)
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.m_82127_("do")
                        .requires(ProgressionCommands::staff)
                        .then(Commands.m_82127_("page")
                                .then(Commands.m_82129_("page", StringArgumentType.word())
                                        .executes(ctx -> gui(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "page")))))
                        .then(Commands.m_82127_("flag")
                                .then(Commands.m_82129_("flag", StringArgumentType.word())
                                        .then(Commands.m_82129_("page", StringArgumentType.word())
                                                .executes(ctx -> flagDo(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "flag"),
                                                        StringArgumentType.getString(ctx, "page"))))))
                        .then(Commands.m_82127_("module_doc")
                                .executes(ctx -> progressionActionDo(ctx.getSource(), "module_doc", "", "main"))
                                .then(Commands.m_82129_("target", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String target = StringArgumentType.getString(ctx, "target");
                                            return progressionActionDo(ctx.getSource(), "module_doc", target, target);
                                        })))
                        .then(Commands.m_82127_("toggle_staff_free_coins")
                                .then(Commands.m_82129_("value", StringArgumentType.word())
                                        .then(Commands.m_82129_("page", StringArgumentType.word())
                                                .executes(ctx -> progressionActionDo(
                                                        ctx.getSource(),
                                                        "toggle_staff_free_coins",
                                                        StringArgumentType.getString(ctx, "value"),
                                                        StringArgumentType.getString(ctx, "page")))))))
                .then(Commands.m_82127_("boost")
                        .requires(ProgressionCommands::staff)
                        .executes(ctx -> boostStatus(ctx.getSource()))
                        .then(Commands.m_82127_("status").executes(ctx -> boostStatus(ctx.getSource())))
                        .then(Commands.m_82127_("start")
                                .requires(ProgressionCommands::staff)
                                .then(Commands.m_82129_("encoded", IntegerArgumentType.integer(1))
                                        .executes(ctx -> boostEncoded(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "encoded"),
                                                null))
                                        .then(Commands.m_82129_("purchaser", StringArgumentType.greedyString())
                                                .executes(ctx -> boostEncoded(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "encoded"),
                                                        StringArgumentType.getString(ctx, "purchaser")))))
                                .then(Commands.m_82129_("multiplier", DoubleArgumentType.doubleArg(1.25))
                                        .then(Commands.m_82129_("minutes", IntegerArgumentType.integer(1))
                                                .executes(ctx -> boostMinutes(
                                                        ctx.getSource(),
                                                        DoubleArgumentType.getDouble(ctx, "multiplier"),
                                                        IntegerArgumentType.getInteger(ctx, "minutes"),
                                                        null))
                                                .then(Commands.m_82129_("purchaser", StringArgumentType.greedyString())
                                                        .executes(ctx -> boostMinutes(
                                                                ctx.getSource(),
                                                                DoubleArgumentType.getDouble(ctx, "multiplier"),
                                                                IntegerArgumentType.getInteger(ctx, "minutes"),
                                                                StringArgumentType.getString(ctx, "purchaser")))))))
                        .then(Commands.m_82127_("end")
                                .requires(ProgressionCommands::staff)
                                .executes(ctx -> boostEnd(ctx.getSource()))))
                .then(Commands.m_82127_("android")
                        .executes(ctx -> androidSelfOrHint(ctx.getSource()))
                        .then(Commands.m_82127_("remove")
                                .executes(ctx -> androidRemoveGuiHint(ctx.getSource()))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .requires(ProgressionCommands::staff)
                                        .executes(ctx -> androidRemovePlayer(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))))
                        .then(LmCommandSuggestions.playerWord("player")
                                .requires(ProgressionCommands::staff)
                                .executes(ctx -> androidPlayer(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("admin")
                        .executes(ctx -> progressionAdmin(ctx.getSource(), "admin"))
                        .then(Commands.m_82129_("flag", StringArgumentType.word())
                                .then(Commands.m_82129_("value", StringArgumentType.word())
                                        .executes(ctx -> adminToggle(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "flag"),
                                                StringArgumentType.getString(ctx, "value"))))));
    }

    private static boolean staff(CommandSourceStack src) {
        return StaffAccess.isStaffSource(src);
    }

    private static int progressionAdmin(CommandSourceStack source, String page) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        return gui(source, page);
    }

    private static int helpOrGui(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player != null && StaffAccess.isStaff(player)) {
            LmCommandFeedback.tellLines(source, LmCommandHelp.progressionStaff());
            return 1;
        }
        LmCommandFeedback.tellLines(source, LmCommandHelp.progressionPlayer());
        return 1;
    }

    private static int gui(CommandSourceStack source, String page) {
        ServerPlayer player = playerOrNull(source);
        if (player == null || !DifficultyConfig.get().enableProgression) {
            return 0;
        }
        if (!StaffAccess.isStaff(player)) {
            reply(source, player, LmCommandMessages.STAFF_ONLY);
            return 0;
        }
        ProgressionMenu.open(player, page);
        return 1;
    }

    private static int flagDo(CommandSourceStack source, String flag, String page) {
        ServerPlayer player = playerOrNull(source);
        if (player == null) {
            return 0;
        }
        if (!staff(source)) {
            reply(source, player, LmCommandMessages.STAFF_ONLY);
            return 0;
        }
        String msg = com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi.handleDo(
                player, "flag", flag, page);
        if (msg != null && !msg.isBlank()) {
            reply(source, player, msg);
        }
        ProgressionMenu.open(player, page == null || page.isBlank() ? "main" : page);
        return 1;
    }

    private static int progressionActionDo(CommandSourceStack source, String action, String arg, String page) {
        ServerPlayer player = playerOrNull(source);
        if (player == null) {
            return 0;
        }
        if (!staff(source)) {
            reply(source, player, LmCommandMessages.STAFF_ONLY);
            return 0;
        }
        String msg = ProgressionGuiApi.handleDo(player, action, arg, page);
        if (msg != null && !msg.isBlank()) {
            LmCommandFeedback.tellLines(source, msg);
        }
        if (!"module_doc".equals(action) && !"moduledoc".equals(action) && !"module".equals(action)) {
            ProgressionMenu.open(player, page == null || page.isBlank() ? "main" : page);
        }
        return 1;
    }

    private static int status(CommandSourceStack source) {
        String summary = ProgressionSystem.statusSummary();
        source.m_288197_(() -> Component.m_237113_("§7" + summary.replace("\n", "\n§7")), true);
        return 1;
    }

    private static int boostEncoded(CommandSourceStack source, int encoded, String purchaser) {
        ServerPlayer actor = playerOrNull(source);
        String msg = ProgressionSystem.boostStartEncoded(actor, encoded, purchaser);
        reply(source, actor, msg);
        return 1;
    }

    private static int boostMinutes(CommandSourceStack source, double mult, int minutes, String purchaser) {
        ServerPlayer actor = playerOrNull(source);
        String msg = ProgressionSystem.boostStart(actor, mult, minutes, purchaser);
        reply(source, actor, msg);
        return 1;
    }

    private static int boostEnd(CommandSourceStack source) {
        String msg = ProgressionSystem.boostEnd();
        reply(source, playerOrNull(source), msg);
        return 1;
    }

    private static int boostStatus(CommandSourceStack source) {
        ServerPlayer actor = playerOrNull(source);
        String msg = com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi.boost(actor, "status");
        for (String line : msg.split("\n")) {
            reply(source, actor, line);
        }
        return 1;
    }

    private static int androidRemoveGuiHint(CommandSourceStack source) {
        return androidRemoveSelf(source);
    }

    private static int androidSelfOrHint(CommandSourceStack source) {
        if (!staff(source)) {
            reply(source, playerOrNull(source), LmCommandMessages.STAFF_ONLY);
            return 0;
        }
        return androidSelf(source);
    }

    private static int androidSelf(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer player = playerOrNull(source);
        if (player == null) {
            source.m_288197_(() -> Component.m_237113_(LmCommandMessages.NEED_IN_GAME), false);
            return 0;
        }
        reply(source, player, ProgressionSystem.androidConvert(player));
        return 1;
    }

    private static int androidPlayer(CommandSourceStack source, String name) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolve(source, name);
        if (target == null) {
            source.m_288197_(() -> Component.m_237113_(LmCommandMessages.playerNotFound(name)), false);
            return 0;
        }
        reply(source, playerOrNull(source), ProgressionSystem.androidConvert(target));
        return 1;
    }

    private static int androidRemoveSelf(CommandSourceStack source) {
        ServerPlayer player = playerOrNull(source);
        if (player == null) {
            source.m_288197_(() -> Component.m_237113_(LmCommandMessages.NEED_IN_GAME), false);
            return 0;
        }
        reply(source, player, ProgressionGuiApi.androidRemove(player, ""));
        return 1;
    }

    private static int androidRemovePlayer(CommandSourceStack source, String name) {
        ServerPlayer actor = playerOrNull(source);
        if (actor == null) {
            ServerPlayer target = resolve(source, name);
            if (target == null) {
                source.m_288197_(() -> Component.m_237113_(LmCommandMessages.playerNotFound(name)), false);
                return 0;
            }
            reply(source, null, ProgressionGuiApi.androidRemove(target, ""));
            return 1;
        }
        reply(source, actor, ProgressionGuiApi.androidRemove(actor, name));
        return 1;
    }

    private static int adminToggle(CommandSourceStack source, String flag, String value) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        boolean on = "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
        boolean off = "off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "0".equals(value);
        if (!on && !off) {
            source.m_288197_(() -> Component.m_237113_("§cUse §fon§c or §foff§c for that flag."), false);
            return 0;
        }
        if (!ProgressionSystem.setFlag(flag, on)) {
            source.m_288197_(() -> Component.m_237113_("§cUnknown flag §f" + flag + "§c—see §f/progression help§c."), false);
            return 0;
        }
        String msg = (on ? LmCommandMessages.turnedOn("Progression flag " + flag)
                : LmCommandMessages.turnedOff("Progression flag " + flag));
        reply(source, playerOrNull(source), msg);
        ServerPlayer p = playerOrNull(source);
        if (p != null) {
            ProgressionMenu.open(p, "admin");
        }
        return 1;
    }

    private static void reply(CommandSourceStack source, ServerPlayer player, String msg) {
        if (player != null) {
            DmzRewards.msg(player, msg);
        } else {
            source.m_288197_(() -> Component.m_237113_(msg), true);
        }
    }

    private static ServerPlayer playerOrNull(CommandSourceStack source) {
        try {
            return source.m_81375_();
        } catch (Exception e) {
            return null;
        }
    }

    private static ServerPlayer resolve(CommandSourceStack source, String name) {
        try {
            MinecraftLookup:
            {
                var server = source.m_81377_();
                if (server == null) {
                    break MinecraftLookup;
                }
                ServerPlayer p = server.m_6846_().m_11255_(name);
                if (p != null) {
                    return p;
                }
                for (ServerPlayer online : server.m_6846_().m_11314_()) {
                    if (online.m_7755_().getString().equalsIgnoreCase(name)) {
                        return online;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
