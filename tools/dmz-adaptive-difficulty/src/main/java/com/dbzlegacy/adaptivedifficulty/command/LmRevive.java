package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lm revive [player]} — revive through DMZ's own code without op or a dmzrevive node.
 *
 * <p>No argument revives the executor and is a donator command (the donator node only, so the
 * Skill Check feature flags never block it; staff are exempt). {@code <player>} needs staff
 * (op 2, Bukkit op, or the configured admin node) or the revive-others node.
 *
 * <p>The revive itself is DMZ's private {@code ReviveCommand.revivePlayers(source, targets)},
 * called reflectively with the executor's own source so DMZ's messages reach them exactly as
 * with {@code /dmzrevive}. Dispatching {@code /dmzrevive} instead would re-run its
 * {@code dmzrevive.others} permission check — the very check staff without op fail. LM never
 * touches death state, stats, or healing; if the DMZ method is missing, the command fails
 * closed with a human message and never falls back to dispatching {@code /dmzrevive}.
 */
final class LmRevive {
    private static final String REVIVE_CLASS = "com.dragonminez.server.commands.ReviveCommand";
    private static final String REVIVE_METHOD = "revivePlayers";

    private static final String SELF_DONATOR =
            UltraStyle.dangerLine("Reviving yourself is a donator perk. Ask staff about donator access.");

    private static final String UNAVAILABLE =
            UltraStyle.dangerLine("Revive is not available right now. Staff should check the server setup.");

    /** Resolved once; {@link #lookupFailed} stays true if DMZ no longer has the method. */
    private static volatile Method revive;
    private static volatile boolean lookupDone;
    private static volatile boolean lookupFailed;

    private LmRevive() {}

    static LiteralArgumentBuilder<CommandSourceStack> literal() {
        return Commands.m_82127_("revive")
                .executes(ctx -> reviveSelf(ctx.getSource()))
                .then(LmCommandSuggestions.playerWord("player")
                        .executes(ctx -> reviveOther(
                                ctx.getSource(), StringArgumentType.getString(ctx, "player"))));
    }

    /** {@code /lm revive}: donators (or staff) revive themselves. */
    private static int reviveSelf(CommandSourceStack source) {
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.PLAYERS_ONLY));
            return 0;
        }
        if (!StaffAccess.hasDonatorPermission(player) && !StaffAccess.isStaff(player)) {
            player.m_213846_(Component.m_237113_(SELF_DONATOR));
            return 0;
        }
        return revive(source, player);
    }

    /** {@code /lm revive <player>}: staff or revive access revive a named online player. */
    private static int reviveOther(CommandSourceStack source, String name) {
        if (StaffAccess.denyUnlessReviveOthers(source) == 0) {
            return 0;
        }
        ServerPlayer target = MechanicsCommands.onlinePlayer(source.m_81377_(), name);
        if (target == null) {
            String who = name == null || name.isBlank() ? "That player" : name.trim();
            LmCommandFeedback.tell(source, UltraStyle.dangerLine(
                    who + " was not found or is not online. Try again when they are on."));
            return 0;
        }
        return revive(source, target);
    }

    /** Hand one target to DMZ's revive with the executor's own source. Never throws. */
    private static int revive(CommandSourceStack source, ServerPlayer target) {
        Method method = method();
        if (method == null) {
            LmCommandFeedback.tell(source, UNAVAILABLE);
            return 0;
        }
        try {
            Collection<ServerPlayer> targets = List.of(target);
            Object result = method.invoke(null, source, targets);
            return result instanceof Integer n ? n : 1;
        } catch (InvocationTargetException e) {
            // DMZ itself refused (for example a source it cannot use): same human message.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] /lm revive: DMZ revive failed ({})",
                    AdaptiveDifficultyMod.MOD_ID, cause.getClass().getSimpleName());
            LmCommandFeedback.tell(source, UNAVAILABLE);
            return 0;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] /lm revive: could not call DMZ revive ({})",
                    AdaptiveDifficultyMod.MOD_ID, t.getClass().getSimpleName());
            LmCommandFeedback.tell(source, UNAVAILABLE);
            return 0;
        }
    }

    /** DMZ's {@code revivePlayers}, looked up once. Null when DMZ changed it (fail closed). */
    private static Method method() {
        if (lookupDone) {
            return lookupFailed ? null : revive;
        }
        synchronized (LmRevive.class) {
            if (!lookupDone) {
                try {
                    Method m = Class.forName(REVIVE_CLASS)
                            .getDeclaredMethod(REVIVE_METHOD, CommandSourceStack.class, Collection.class);
                    m.setAccessible(true);
                    revive = m;
                } catch (Throwable t) {
                    lookupFailed = true;
                    AdaptiveDifficultyMod.LOGGER.warn(
                            "[{}] /lm revive unavailable: DMZ revive method not found ({})",
                            AdaptiveDifficultyMod.MOD_ID, t.getClass().getSimpleName());
                }
                lookupDone = true;
            }
        }
        return lookupFailed ? null : revive;
    }
}
