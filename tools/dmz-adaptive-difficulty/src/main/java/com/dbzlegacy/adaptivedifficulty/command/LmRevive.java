package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.dragonminez.common.stats.StatsData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lm revive [player|all]} — revive through DMZ's own code without op or a dmzrevive node.
 *
 * <p>No argument revives the executor: staff, or the self-revive node alone (a higher donator
 * rank; the base donator node is not enough, and Skill Check flags never matter). {@code <player>} needs staff
 * (op 2, Bukkit op, or the configured admin node) or the revive-others node. {@code all} (4.7.23) has
 * the same gate as {@code <player>} and revives every online player DMZ shows as dead
 * ({@code Status.isAlive()} false, the Otherworld state DMZ's revive clears).
 *
 * <p>Cooldown (4.7.23): after a successful revive of any form, an executor who is not an op waits
 * {@code reviveCooldownSeconds} (default 300) before the next one. Tracked in memory by the
 * executor's UUID, so a restart clears it; ops ({@link StaffAccess#isOp}) and the console are
 * exempt. Denied, not-found, unavailable, or nobody-dead attempts never start it.
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
            UltraStyle.dangerLine("Reviving yourself is a perk of higher donator ranks. Ask staff about upgrading your rank.");

    private static final String UNAVAILABLE =
            UltraStyle.dangerLine("Revive is not available right now. Staff should check the server setup.");

    private static final String NOBODY_DEAD = UltraStyle.noticeLine("No one is dead right now.");

    /** Executor UUID -> epoch millis when their next revive is allowed. In memory only. */
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    /** Resolved once; {@link #lookupFailed} stays true if DMZ no longer has the method. */
    private static volatile Method revive;
    private static volatile boolean lookupDone;
    private static volatile boolean lookupFailed;

    private LmRevive() {}

    static LiteralArgumentBuilder<CommandSourceStack> literal() {
        return Commands.m_82127_("revive")
                .executes(ctx -> reviveSelf(ctx.getSource()))
                // Literal before the player word, so "all" is never read as a player named "all".
                .then(Commands.m_82127_("all")
                        .executes(ctx -> reviveAll(ctx.getSource())))
                .then(LmCommandSuggestions.playerWord("player")
                        .executes(ctx -> reviveOther(
                                ctx.getSource(), StringArgumentType.getString(ctx, "player"))));
    }

    /** {@code /lm revive}: self-revive node holders (or staff) revive themselves. */
    private static int reviveSelf(CommandSourceStack source) {
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.PLAYERS_ONLY));
            return 0;
        }
        if (!StaffAccess.hasSelfRevive(player) && !StaffAccess.isStaff(player)) {
            player.m_213846_(Component.m_237113_(SELF_DONATOR));
            return 0;
        }
        if (onCooldown(source)) {
            return 0;
        }
        return revive(source, List.of(player));
    }

    /** {@code /lm revive <player>}: staff or revive access revive a named online player. */
    private static int reviveOther(CommandSourceStack source, String name) {
        if (StaffAccess.denyUnlessReviveOthers(source) == 0) {
            return 0;
        }
        if (onCooldown(source)) {
            return 0;
        }
        ServerPlayer target = MechanicsCommands.onlinePlayer(source.m_81377_(), name);
        if (target == null) {
            String who = name == null || name.isBlank() ? "That player" : name.trim();
            LmCommandFeedback.tell(source, UltraStyle.dangerLine(
                    who + " was not found or is not online. Try again when they are on."));
            return 0;
        }
        return revive(source, List.of(target));
    }

    /** {@code /lm revive all}: staff or revive access revive every dead online player at once. */
    private static int reviveAll(CommandSourceStack source) {
        if (StaffAccess.denyUnlessReviveOthers(source) == 0) {
            return 0;
        }
        if (onCooldown(source)) {
            return 0;
        }
        List<ServerPlayer> dead = deadOnlinePlayers(source.m_81377_());
        if (dead.isEmpty()) {
            LmCommandFeedback.tell(source, NOBODY_DEAD);
            return 0;
        }
        return revive(source, dead);
    }

    /** Online players whose DMZ status is not alive (in the Otherworld). Living players never pass. */
    private static List<ServerPlayer> deadOnlinePlayers(MinecraftServer server) {
        List<ServerPlayer> dead = new ArrayList<>();
        if (server == null) {
            return dead;
        }
        for (ServerPlayer p : server.m_6846_().m_11314_()) {
            try {
                StatsData stats = DmzProgression.stats(p);
                if (stats != null && stats.getStatus() != null && !stats.getStatus().isAlive()) {
                    dead.add(p);
                }
            } catch (Throwable ignored) {
            }
        }
        return dead;
    }

    /** True, after telling the executor the time left, while a non-op executor is on cooldown. */
    private static boolean onCooldown(CommandSourceStack source) {
        ServerPlayer executor = source.m_230896_();
        if (executor == null || StaffAccess.isOp(executor)) {
            return false;
        }
        Long until = COOLDOWN_UNTIL.get(executor.m_20148_());
        long left = until == null ? 0L : until - System.currentTimeMillis();
        if (left <= 0L) {
            return false;
        }
        LmCommandFeedback.tell(source, UltraStyle.dangerLine(
                "Revive is on cooldown — try again in " + formatLeft(left) + "."));
        return true;
    }

    /** After a successful revive: a non-op executor waits {@code reviveCooldownSeconds}. */
    private static void startCooldown(CommandSourceStack source) {
        ServerPlayer executor = source.m_230896_();
        int seconds = DifficultyConfig.get().reviveCooldownSeconds;
        if (executor == null || seconds <= 0 || StaffAccess.isOp(executor)) {
            return;
        }
        COOLDOWN_UNTIL.put(executor.m_20148_(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** "3m 12s", "5m" or "45s", rounded up to the next second. */
    static String formatLeft(long millis) {
        long total = Math.max(1L, (millis + 999L) / 1000L);
        long m = total / 60L;
        long s = total % 60L;
        if (m == 0L) {
            return s + "s";
        }
        return s == 0L ? m + "m" : m + "m " + s + "s";
    }

    /**
     * Hand the targets to DMZ's revive with the executor's own source; a count above 0 starts the
     * executor's cooldown. Never throws.
     */
    private static int revive(CommandSourceStack source, Collection<ServerPlayer> targets) {
        Method method = method();
        if (method == null) {
            LmCommandFeedback.tell(source, UNAVAILABLE);
            return 0;
        }
        try {
            Object result = method.invoke(null, source, targets);
            int revived = result instanceof Integer n ? n : 1;
            if (revived > 0) {
                startCooldown(source);
            }
            return revived;
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
