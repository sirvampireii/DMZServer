package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.progression.shop.GodKiEnhancementShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopHooks;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Brigadier tree for {@code /padmin} (staff prestige adjustments). */
public final class PrestigeAdminCommandTree {
    private PrestigeAdminCommandTree() {}

    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> root
    ) {
        // Defense-in-depth: hide the whole tree from non-staff. Every handler also
        // checks denyUnlessStaff at execution; the requires() keeps tab-complete honest.
        LiteralArgumentBuilder<CommandSourceStack> tree = root
                .requires(StaffAccess::isStaffSource)
                .executes(ctx -> ProgressionCommands.prestigeAdminHelp(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> ProgressionCommands.prestigeAdminHelp(ctx.getSource())))
                .then(Commands.m_82127_("info")
                        .executes(ctx -> ProgressionCommands.prestigeAdminInfo(ctx.getSource(), null))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> ProgressionCommands.prestigeAdminInfo(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("skills")
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> ProgressionCommands.prestigeAdminListSkills(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("skill")
                        .then(LmCommandSuggestions.playerWord("player")
                                .then(Commands.m_82129_("skillId", StringArgumentType.word())
                                        .then(LmCommandSuggestions.word("mode", LmCommandSuggestions.PRESTIGE_ADJUST_MODES)
                                                .then(Commands.m_82129_("levels", IntegerArgumentType.integer())
                                                        .executes(ctx -> ProgressionCommands.prestigeAdminSkill(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "skillId"),
                                                                StringArgumentType.getString(ctx, "mode"),
                                                                IntegerArgumentType.getInteger(ctx, "levels"))))))))
                .then(Commands.m_82127_("tier")
                        .then(LmCommandSuggestions.playerWord("player")
                                .then(LmCommandSuggestions.word("mode", LmCommandSuggestions.PRESTIGE_TIER_MODES)
                                        .then(LmCommandSuggestions.word("tier", LmCommandSuggestions.PRESTIGE_TIER_IDS)
                                                .executes(ctx -> ProgressionCommands.prestigeAdminTier(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "mode"),
                                                        StringArgumentType.getString(ctx, "tier")))))))
                .then(adjustField("held"))
                .then(adjustField("completed"))
                .then(adjustField("points"))
                .then(adjustFieldAmount(
                        "godki",
                        IntegerArgumentType.integer(0, GodKiEnhancementShop.MAX_INVESTMENTS)));
        PrestigeShopHooks.attachAdminCommands(tree);
        return tree;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adjustField(String field) {
        return adjustFieldAmount(field, IntegerArgumentType.integer());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adjustFieldAmount(
            String field, IntegerArgumentType amountType
    ) {
        return Commands.m_82127_(field)
                .then(LmCommandSuggestions.playerWord("player")
                        .then(LmCommandSuggestions.word("mode", LmCommandSuggestions.PRESTIGE_ADJUST_MODES)
                                .then(Commands.m_82129_("amount", amountType)
                                        .executes(ctx -> ProgressionCommands.prestigeAdminAdjust(
                                                ctx.getSource(), field,
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "mode"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))));
    }
}
