package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesSystem;
import com.dbzlegacy.adaptivedifficulty.gui.CharacterServicesGuiApi;
import com.dbzlegacy.adaptivedifficulty.gui.CharacterServicesMenu;
import com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * {@code /lm character} — paid race / class / reskin services with cosmetic unlocks.
 * Player-facing with parity for the Character Services screen; the {@code do} backend
 * mirrors {@code /lm difficulty do} for chat buttons and typed actions. Race, class, and
 * reskin purchases are menu-only for players — their command forms are staff-gated —
 * and staff fix other players through {@code /lm admin character ...}.
 */
public final class CharacterCommands {
    private CharacterCommands() {}

    /**
     * The command forms of the paid race/class/reskin flow. The Character Services
     * screen keeps every one of these as a GUI action; only this command tree gates them.
     */
    public static final Set<String> STAFF_ONLY_ACTIONS = Set.of(
            "race", "race_confirm", "class", "class_confirm", "reskin", "reskin_confirm");

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new CharacterCommands());
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        // 4.7.88: the old character root moved to /lm character, its admin branch to /lm admin character (merged with the
        // existing /lm admin character cooldown).
        event.getDispatcher().register(CommandAccess.underLm(root("character")));
        event.getDispatcher().register(CommandAccess.underLmAdmin(admin("character")));
        AdaptiveDifficultyMod.LOGGER.info("[{}] registered /lm character and /lm admin character",
                AdaptiveDifficultyMod.MOD_ID);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.m_82127_(name)
                .executes(ctx -> open(ctx.getSource(), "main"))
                .then(Commands.m_82127_("help").executes(ctx -> characterHelp(ctx.getSource())))
                .then(Commands.m_82127_("do")
                        .then(LmCommandSuggestions.word("action", LmCommandSuggestions.CHARACTER_ACTIONS)
                                .executes(ctx -> characterDoRest(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "action"),
                                        ""))
                                .then(Commands.m_82129_("arg", StringArgumentType.greedyString())
                                        .executes(ctx -> characterDoRest(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "action"),
                                                StringArgumentType.getString(ctx, "arg"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.m_82127_(name)
                        .executes(ctx -> characterAdminHelp(ctx.getSource()))
                        .then(Commands.m_82127_("help")
                                .executes(ctx -> characterAdminHelp(ctx.getSource())))
                        .then(Commands.m_82127_("set-race")
                                .then(LmCommandSuggestions.playerWord("player")
                                        .then(Commands.m_82129_("race", StringArgumentType.word())
                                                .executes(ctx -> adminSetRace(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "race"),
                                                        100))
                                                .then(Commands.m_82129_(
                                                                "keep%", IntegerArgumentType.integer(0, 100))
                                                        .suggests(LmCommandSuggestions.CHARACTER_KEEP_PCT)
                                                        .executes(ctx -> adminSetRace(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "race"),
                                                                IntegerArgumentType.getInteger(ctx, "keep%")))))))
                        .then(Commands.m_82127_("set-class")
                                .then(LmCommandSuggestions.playerWord("player")
                                        .then(Commands.m_82129_("class", StringArgumentType.word())
                                                .executes(ctx -> adminSetClass(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "class"))))))
                        .then(Commands.m_82127_("reskin")
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> adminReskin(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))));
    }

    private static int characterHelp(CommandSourceStack source) {
        LmCommandFeedback.tellLines(source, LmCommandHelp.characterPlayer());
        return 1;
    }

    private static int characterAdminHelp(CommandSourceStack source) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        LmCommandFeedback.tellLines(source, LmCommandHelp.characterStaff());
        return 1;
    }

    private static int open(CommandSourceStack source, String page) {
        ServerPlayer player = playerOrNull(source);
        if (player == null) {
            return 0;
        }
        CharacterServicesMenu.open(player, page);
        return 1;
    }

    /** {@code /lm admin character set-race <player> <race> [keep%]} — free staff fix. */
    private static int adminSetRace(
            CommandSourceStack source, String playerName, String race, int keepPct
    ) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolveTarget(source, playerName);
        if (target == null) {
            return 0;
        }
        String msg = CharacterServicesSystem.executeRaceChange(target, race, keepPct, true);
        return replyForTarget(source, target, msg);
    }

    /** {@code /lm admin character set-class <player> <class>} — free staff fix. */
    private static int adminSetClass(CommandSourceStack source, String playerName, String classId) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolveTarget(source, playerName);
        if (target == null) {
            return 0;
        }
        String msg = CharacterServicesSystem.executeClassChange(target, classId, true);
        return replyForTarget(source, target, msg);
    }

    /** {@code /lm admin character reskin <player>} — open their appearance editor, free. */
    private static int adminReskin(CommandSourceStack source, String playerName) {
        if (StaffAccess.denyUnlessStaff(source) == 0) {
            return 0;
        }
        ServerPlayer target = resolveTarget(source, playerName);
        if (target == null) {
            return 0;
        }
        String msg = CharacterServicesSystem.executeReskin(target, true);
        return replyForTarget(source, target, msg);
    }

    private static int replyForTarget(CommandSourceStack source, ServerPlayer target, String msg) {
        if (msg == null || msg.isBlank()) {
            return 0;
        }
        LmCommandFeedback.tell(source, msg + " §8(" + target.m_6302_() + ")");
        return msg.startsWith("§c") ? 0 : 1;
    }

    private static ServerPlayer resolveTarget(CommandSourceStack source, String playerName) {
        MinecraftServer server = source.m_81377_();
        ServerPlayer target = server == null ? null : RivalGuiApi.resolveOnline(server, playerName);
        if (target == null) {
            LmCommandFeedback.tell(source, LmCommandMessages.playerOffline(playerName));
        }
        return target;
    }

    /** {@code /lm character do <action> [arg…]} — chat-button parity backend (no screen reopen). */
    private static int characterDoRest(CommandSourceStack source, String action, String rest) {
        return characterDo(source, action, rest == null ? "" : rest.trim());
    }

    private static int characterDo(CommandSourceStack source, String action, String arg) {
        ServerPlayer player = playerOrNull(source);
        if (player == null) {
            source.m_288197_(() -> Component.m_237113_(LmCommandMessages.NEED_IN_GAME), false);
            return 0;
        }
        if (action != null && STAFF_ONLY_ACTIONS.contains(action) && !StaffAccess.isStaff(player)) {
            LmCommandFeedback.tell(source,
                    "§cRace, class, and reskin changes are made in Character Services (§f/lm character§c)."
                            + " §7The command form is staff-only.");
            return 0;
        }
        String msg = CharacterServicesGuiApi.handleDo(player, action, arg, "main");
        if (msg != null && !msg.isBlank()) {
            LmCommandFeedback.tell(source, msg);
        }
        return msg != null && msg.startsWith("§c") ? 0 : 1;
    }

    private static ServerPlayer playerOrNull(CommandSourceStack source) {
        try {
            return source.m_81375_();
        } catch (Exception e) {
            return null;
        }
    }
}
