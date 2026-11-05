package com.dbzlegacy.legacytextures.server;

import com.dbzlegacy.legacytextures.TextureMode;
import com.dbzlegacy.legacytextures.net.LtNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /legacytextures opens the texture menu. The sub-commands do the same things as the menu buttons,
 * for players who prefer typing:
 * <pre>
 * /legacytextures                         open the menu
 * /legacytextures preset all_hd|players_hd_only|all_vanilla
 * /legacytextures players|nbarmor|world hd|vanilla
 * /legacytextures look &lt;set id&gt;           which HD set the HD switches use
 * /legacytextures status                  show your current choice
 * </pre>
 * Every player may use it; it only changes what that player's own game draws.
 */
public final class LtCommand {
    private LtCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.m_82127_("legacytextures")
                .executes(ctx -> open(ctx.getSource().m_81375_()));
        LiteralArgumentBuilder<CommandSourceStack> preset = Commands.m_82127_("preset");
        for (TextureMode.Preset p : TextureMode.Preset.values()) {
            preset.then(Commands.m_82127_(p.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(ctx -> change(ctx, ModeStore.get(ctx.getSource().m_81375_()).withPreset(p),
                            "Preset set to " + p.label + ".")));
        }
        root.then(preset);
        root.then(toggle("players", "Players and DragonMineZ armor", TextureMode::withPlayers));
        root.then(toggle("nbarmor", "NB Armors outfits", TextureMode::withNbArmor));
        root.then(toggle("world", "World blocks, items and menus", TextureMode::withWorld));
        root.then(Commands.m_82127_("look").then(Commands.m_82129_("set", StringArgumentType.word())
                .executes(ctx -> {
                    String id = TextureMode.cleanSetId(StringArgumentType.getString(ctx, "set"));
                    return change(ctx, ModeStore.get(ctx.getSource().m_81375_()).withSet(id),
                            "Your HD switches now use the '" + id + "' texture set. If your game does not have that "
                                    + "set, it keeps using the standard HD set.");
                })));
        root.then(Commands.m_82127_("status").executes(LtCommand::status));
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String word, String label,
            BiFunction<TextureMode, Boolean, TextureMode> with) {
        return Commands.m_82127_(word)
                .then(Commands.m_82127_("hd").executes(ctx -> change(ctx,
                        with.apply(ModeStore.get(ctx.getSource().m_81375_()), true), label + " now use HD textures.")))
                .then(Commands.m_82127_("vanilla").executes(ctx -> change(ctx,
                        with.apply(ModeStore.get(ctx.getSource().m_81375_()), false),
                        label + " now use the standard textures.")));
    }

    private static int open(ServerPlayer player) {
        LtNetwork.sync(player, ModeStore.get(player), true);
        return 1;
    }

    private static int change(CommandContext<CommandSourceStack> ctx, TextureMode mode, String done)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().m_81375_();
        boolean worldChanged = ModeStore.get(player).world != mode.world;
        ModeStore.set(player, mode);
        LtNetwork.sync(player, mode, false);
        String note = worldChanged ? " Your game reloads its textures once to switch the world look." : "";
        ctx.getSource().m_288197_(() -> Component.m_237113_(done + note).m_130940_(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        TextureMode m = ModeStore.get(ctx.getSource().m_81375_());
        TextureMode.Preset p = m.preset();
        String text = "Legacy Textures: " + (p != null ? p.label : "custom mix")
                + ". Players: " + word(m.players) + ", NB Armors: " + word(m.nbArmor) + ", world: " + word(m.world)
                + ". HD set: " + m.set + ". Type /legacytextures to change it.";
        ctx.getSource().m_288197_(() -> Component.m_237113_(text).m_130940_(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static String word(boolean hd) {
        return hd ? "HD" : "standard";
    }
}
