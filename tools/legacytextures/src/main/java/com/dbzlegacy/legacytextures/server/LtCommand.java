package com.dbzlegacy.legacytextures.server;

import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.net.LtNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * /legacytextures opens the texture menu (a key can be bound to it too). The sub-commands do the same
 * things as the menu buttons, for players who prefer typing:
 * <pre>
 * /legacytextures                         open the menu
 * /legacytextures preset all_hd|players_hd_only|all_vanilla
 * /legacytextures players|nbarmor|world hd|vanilla
 * /legacytextures look &lt;set id&gt;           which HD set the HD switches use
 * /legacytextures status                  your choice, plus your game's hook and cache health
 * </pre>
 * Every player may use it; it only changes what that player's own game draws. All text is lang keys
 * (assets/legacytextures/lang), translated by the player's game.
 */
public final class LtCommand {
    private LtCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.m_82127_("legacytextures")
                .executes(ctx -> open(ctx.getSource().m_81375_()));
        LiteralArgumentBuilder<CommandSourceStack> preset = Commands.m_82127_("preset");
        for (TextureMode.Preset p : TextureMode.Preset.values()) {
            preset.then(Commands.m_82127_(p.name().toLowerCase(Locale.ROOT))
                    .executes(ctx -> change(ctx, ModeStore.get(ctx.getSource().m_81375_()).withPreset(p),
                            Component.m_237110_("legacytextures.cmd.preset", new Object[] {Component.m_237115_(p.key)}))));
        }
        root.then(preset);
        root.then(toggle("players", "legacytextures.switch.players", TextureMode::withPlayers));
        root.then(toggle("nbarmor", "legacytextures.switch.nbarmor", TextureMode::withNbArmor));
        root.then(toggle("world", "legacytextures.switch.world", TextureMode::withWorld));
        root.then(Commands.m_82127_("look").then(Commands.m_82129_("set", StringArgumentType.word())
                .executes(ctx -> {
                    String id = TextureMode.cleanSetId(StringArgumentType.getString(ctx, "set"));
                    return change(ctx, ModeStore.get(ctx.getSource().m_81375_()).withSet(id),
                            Component.m_237110_("legacytextures.cmd.look", new Object[] {id}));
                })));
        root.then(Commands.m_82127_("status").executes(LtCommand::status));
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String word, String what,
            BiFunction<TextureMode, Boolean, TextureMode> with) {
        return Commands.m_82127_(word)
                .then(Commands.m_82127_("hd").executes(ctx -> change(ctx,
                        with.apply(ModeStore.get(ctx.getSource().m_81375_()), true),
                        Component.m_237110_("legacytextures.cmd.hd", new Object[] {Component.m_237115_(what)}))))
                .then(Commands.m_82127_("vanilla").executes(ctx -> change(ctx,
                        with.apply(ModeStore.get(ctx.getSource().m_81375_()), false),
                        Component.m_237110_("legacytextures.cmd.vanilla", new Object[] {Component.m_237115_(what)}))));
    }

    private static int open(ServerPlayer player) {
        LtNetwork.sync(player, ModeStore.get(player), true);
        return 1;
    }

    private static int change(CommandContext<CommandSourceStack> ctx, TextureMode mode, MutableComponent done)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().m_81375_();
        boolean worldChanged = ModeStore.get(player).world != mode.world;
        ModeStore.set(player, mode);
        LtNetwork.sync(player, mode, false);
        if (worldChanged) {
            done.m_130946_(" ").m_7220_(Component.m_237115_("legacytextures.cmd.reload_note"));
        }
        ctx.getSource().m_288197_(() -> done.m_130940_(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().m_81375_();
        TextureMode m = ModeStore.get(player);
        TextureMode.Preset p = m.preset();
        Component text = Component.m_237110_("legacytextures.cmd.status", new Object[] {
                Component.m_237115_(p != null ? p.key : "legacytextures.preset.custom"),
                state(m.players), state(m.nbArmor), state(m.world), m.set});
        ctx.getSource().m_288197_(() -> text.m_6881_().m_130940_(ChatFormatting.YELLOW), false);
        LtNetwork.askHealth(player);
        return 1;
    }

    private static Component state(boolean hd) {
        return Component.m_237115_(hd ? "legacytextures.state.hd" : "legacytextures.state.standard");
    }
}
