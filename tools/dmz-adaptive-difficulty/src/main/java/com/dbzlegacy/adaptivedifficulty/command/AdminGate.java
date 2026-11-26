package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 4.7.88: staff admin commands need creative mode ({@link StaffAccess#isAdminSource}). The gate itself lives in
 * each admin node's {@code requires()}, which hides those nodes from tab-complete. Two Forge events finish it:
 * <ul>
 *   <li>A staff player in survival who types an admin command would only see vanilla's "Unknown command". When a
 *   line fails to parse but parses fully once that player counts as creative, the command is refused with
 *   {@link #NEED_CREATIVE} instead. Nothing runs.</li>
 *   <li>A staff player's command list is resent after a gamemode change, so tab-complete shows the admin nodes in
 *   creative and hides them again in survival.</li>
 * </ul>
 */
public final class AdminGate {
    /** Plain words: what happened and what to do. */
    public static final String NEED_CREATIVE = UltraStyle.YELLOW + "Staff admin commands need creative mode."
            + UltraStyle.GRAY + " Switch to creative and try again.";

    private AdminGate() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new AdminGate());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        if (parse == null || complete(parse)) {
            return;
        }
        CommandSourceStack source = parse.getContext().getSource();
        if (!needsCreativeOnly(source, parse.getReader().getString())) {
            return;
        }
        event.setCanceled(true);
        LmCommandFeedback.tell(source, NEED_CREATIVE);
    }

    /**
     * True when {@code line} is refused for this source only because a staff player is not in creative: it fails
     * to parse now and parses fully as if they were. Public for the probe.
     */
    public static boolean needsCreativeOnly(CommandSourceStack source, String line) {
        if (source == null || line == null) {
            return false;
        }
        ServerPlayer player = source.m_230896_();
        if (player == null || player.m_7500_() || !StaffAccess.isStaffSource(source)) {
            return false;
        }
        MinecraftServer server = source.m_81377_();
        if (server == null) {
            return false;
        }
        CommandDispatcher<CommandSourceStack> dispatcher = server.m_129892_().m_82094_();
        String input = line.startsWith("/") ? line.substring(1) : line;
        if (complete(dispatcher.parse(input, source))) {
            return false;
        }
        return StaffAccess.withAssumedCreative(() -> complete(dispatcher.parse(input, source)));
    }

    private static boolean complete(ParseResults<CommandSourceStack> parse) {
        return !parse.getReader().canRead()
                && parse.getExceptions().isEmpty()
                && parse.getContext().getCommand() != null;
    }

    @SubscribeEvent
    public void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !StaffAccess.isStaff(player)) {
            return;
        }
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        // The event fires before the change; resend on the next tick, when isCreative() reads the new mode.
        server.execute(() -> {
            if (!player.m_213877_()) {
                server.m_129892_().m_82095_(player);
            }
        });
    }
}
