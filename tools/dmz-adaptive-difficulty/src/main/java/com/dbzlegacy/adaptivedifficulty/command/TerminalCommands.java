package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.currency.LightmanTerminal;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * {@code /terminal} — Lightman's Currency network terminal.
 * Terminal node holders ({@code legacymechanics.donator.terminal}) or players with the retired CMI node
 * ({@code cmi.customalias.terminal}).
 */
public final class TerminalCommands {
    /** Retired CMI alias node. Still honored so existing grants keep working. */
    public static final String ALIAS_PERMISSION = "cmi.customalias.terminal";

    private TerminalCommands() {}

    /** Terminal node holders, or anyone granted {@link #ALIAS_PERMISSION}. */
    public static boolean allowed(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return StaffAccess.hasTerminalPermission(player)
                || StaffAccess.hasPermission(player, ALIAS_PERMISSION);
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new TerminalCommands());
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                CommandAccess.playerRoot("terminal")
                        .requires(TerminalCommands::mayOpen)
                        .executes(ctx -> open(ctx.getSource())));
        AdaptiveDifficultyMod.LOGGER.info("[{}] registered /terminal", AdaptiveDifficultyMod.MOD_ID);
    }

    /** Terminal node holders or players with {@link #ALIAS_PERMISSION}. Console cannot open the menu. */
    static boolean mayOpen(CommandSourceStack source) {
        if (source == null || !CommandAccess.isPlayerSlashUser(source)) {
            return false;
        }
        return allowed(source.m_230896_());
    }

    private static int open(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.m_81375_();
        } catch (Exception e) {
            source.m_288197_(() -> Component.m_237113_(LmCommandMessages.PLAYERS_ONLY), false);
            return 0;
        }
        String msg = LightmanTerminal.open(player);
        if (msg != null && !msg.isBlank()) {
            source.m_288197_(() -> Component.m_237113_(msg), false);
            return 0;
        }
        return 1;
    }
}
