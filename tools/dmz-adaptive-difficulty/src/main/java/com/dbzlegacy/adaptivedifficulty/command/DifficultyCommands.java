package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.DifficultyMenu;
import com.dbzlegacy.adaptivedifficulty.service.DifficultyActions;
import com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot;
import com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty;
import com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler;
import com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Players: {@code /lm difficulty} opens the GUI (the menu is also {@code /lm open difficulty}).
 * Chat buttons use {@code /lm difficulty do ...} (not for normal player use).
 * Staff: {@code /lm admin difficulty ...} (staff in creative mode, or console).
 * 4.7.88: LM no longer owns a top-level difficulty root, so vanilla {@code /difficulty} (ops, world difficulty)
 * is back; the old LM peaceful/easy/normal/hard and gamedifficulty setters are gone.
 */
public final class DifficultyCommands {
    private DifficultyCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new DifficultyCommands());
        // 4.8.3: saga, skillcheck and goals staff branches (AdaptiveDifficultyMod is byte-audited, so they hook in here).
        AdminSystemsCommands.register();
    }

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(CommandAccess.underLm(playerBranch("difficulty")));
        event.getDispatcher().register(CommandAccess.underLmAdmin(adminBranch("difficulty")));
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] registered /lm difficulty and /lm admin difficulty",
                AdaptiveDifficultyMod.MOD_ID
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playerBranch(String name) {
        return Commands.m_82127_(name)
                .executes(ctx -> openGui(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> playerHelp(ctx.getSource())))
                // Hidden GUI action handler used by clickable chat buttons
                .then(Commands.m_82127_("do")
                        .then(Commands.m_82129_("action", StringArgumentType.word())
                                .executes(ctx -> guiDo(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "action"),
                                        null,
                                        null))
                                .then(Commands.m_82129_("arg", StringArgumentType.word())
                                        .executes(ctx -> guiDo(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "action"),
                                                StringArgumentType.getString(ctx, "arg"),
                                                null))
                                        .then(Commands.m_82129_("page", StringArgumentType.word())
                                                .executes(ctx -> guiDo(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "action"),
                                                        StringArgumentType.getString(ctx, "arg"),
                                                        StringArgumentType.getString(ctx, "page")))))))
                // Player: reset active difficulty to 0 (free; purchased max kept)
                .then(Commands.m_82127_("reset")
                        .executes(ctx -> guiDo(ctx.getSource(), DifficultyActions.ACT_RESET, "0", "main")));
    }

    /** {@code /lm admin difficulty ...} (the old difficulty root's admin branch). */
    private static LiteralArgumentBuilder<CommandSourceStack> adminBranch(String name) {
        return Commands.m_82127_(name)
                        .executes(ctx -> adminHelpOrDeny(ctx.getSource()))
                        .then(Commands.m_82127_("help")
                                .executes(ctx -> adminHelpOrDeny(ctx.getSource())))
                        .then(Commands.m_82127_("reload")
                                .executes(ctx -> reloadOrDeny(ctx.getSource())))
                        .then(Commands.m_82127_("settings")
                                .executes(ctx -> openAdminSettingsOrDeny(ctx.getSource())))
                        // Master system switch — ops / staff only.
                        .then(Commands.m_82127_("off")
                                .executes(ctx -> setSystemEnabled(ctx.getSource(), false)))
                        .then(Commands.m_82127_("on")
                                .executes(ctx -> setSystemEnabled(ctx.getSource(), true)))
                        .then(Commands.m_82127_("toggle")
                                .executes(ctx -> toggleSystem(ctx.getSource())))
                        .then(Commands.m_82127_("status")
                                .executes(ctx -> systemStatus(ctx.getSource())))
                        .then(whitelistRoot("whitelist"))
                        .then(telemetryRoot("telemetry"))
                        .then(staffFreeRoot("stafffree"))
                        .then(syslogRoot("syslog"))
                        .then(Commands.m_82127_("area")
                                .executes(ctx -> showAreaDifficulty(ctx.getSource())))
                        .then(Commands.m_82127_("resetpurchased")
                                .executes(ctx -> adminResetPurchasedOrDeny(ctx.getSource())))
                        .then(Commands.m_82127_("characterreset")
                                .executes(ctx -> adminCharacterResetOrDeny(ctx.getSource())))
                        .then(Commands.m_82127_("resynclevel")
                                .executes(ctx -> adminResyncLevelOrDeny(ctx.getSource(), null))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> adminResyncLevelOrDeny(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.m_82127_("inspect")
                                .executes(ctx -> adminInspectGuiOrDeny(ctx.getSource(), null, "main"))
                                .then(Commands.m_82127_("clear")
                                        .executes(ctx -> adminInspectGuiOrDeny(ctx.getSource(), "clear", "main")))
                                .then(LmCommandSuggestions.playerWord("player")
                                        .executes(ctx -> adminInspectGuiOrDeny(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                "main"))
                                        .then(Commands.m_82129_("page", StringArgumentType.word())
                                                .executes(ctx -> adminInspectGuiOrDeny(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "page"))))))
                        .then(tierRoot("tier"))
                        .then(Commands.m_82127_("set")
                                .then(LmCommandSuggestions.word("key", LmCommandSuggestions.DIFFICULTY_ADMIN_SET_KEYS)
                                        .then(Commands.m_82129_("value", StringArgumentType.greedyString())
                                                .executes(ctx -> adminSetOrDeny(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "value"))))));
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> TIER_NAMES =
            (ctx, builder) -> {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (UnlockTier t : UnlockTier.values()) {
                    names.add(t.display.toLowerCase(java.util.Locale.ROOT));
                }
                return net.minecraft.commands.SharedSuggestionProvider.m_82970_(names, builder);
            };

    /** 4.8.3: {@code /lm admin difficulty tier grant|revoke <player> <tier>}: staff tier grants. */
    private static LiteralArgumentBuilder<CommandSourceStack> tierRoot(String name) {
        return Commands.m_82127_(name)
                .executes(ctx -> tierHelp(ctx.getSource()))
                .then(Commands.m_82127_("help").executes(ctx -> tierHelp(ctx.getSource())))
                .then(Commands.m_82127_("grant")
                        .executes(ctx -> tierHelp(ctx.getSource()))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> tierHelp(ctx.getSource()))
                                .then(LmCommandSuggestions.word("tier", TIER_NAMES)
                                        .executes(ctx -> tierGrant(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "tier"), true)))))
                .then(Commands.m_82127_("revoke")
                        .executes(ctx -> tierHelp(ctx.getSource()))
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> tierHelp(ctx.getSource()))
                                .then(LmCommandSuggestions.word("tier", TIER_NAMES)
                                        .executes(ctx -> tierGrant(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "tier"), false)))));
    }

    /** 4.8.3: the lines {@code /lm admin difficulty tier help} prints. */
    public static java.util.List<String> tierHelpLines() {
        return java.util.List.of(
                UltraStyle.GOLD + "Difficulty tier staff commands",
                UltraStyle.WHITE + "/lm admin difficulty tier grant <player> <tier>" + UltraStyle.GRAY
                        + " - unlock a tier for an online player no matter their level; it stays until revoked",
                UltraStyle.WHITE + "/lm admin difficulty tier revoke <player> <tier>" + UltraStyle.GRAY
                        + " - take back a staff grant (they keep the tier if they still qualify)",
                UltraStyle.GRAY + "Tiers: " + UltraStyle.WHITE + tierNames() + UltraStyle.GRAY + " (or 1 to 7)");
    }

    private static String tierNames() {
        StringBuilder out = new StringBuilder();
        for (UnlockTier t : UnlockTier.values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(t.display.toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }

    private static int tierHelp(CommandSourceStack source) {
        for (String line : tierHelpLines()) {
            source.m_288197_(() -> Component.m_237113_(line), false);
        }
        return 1;
    }

    /** 4.8.3: "elite", "Elite", "3", "t3", "tier3" to a tier; null when nothing matches. */
    public static UnlockTier parseTier(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        if (s.startsWith("tier")) {
            s = s.substring(4);
        } else if (s.startsWith("t") && s.length() > 1 && Character.isDigit(s.charAt(1))) {
            s = s.substring(1);
        }
        for (UnlockTier t : UnlockTier.values()) {
            if (t.display.equalsIgnoreCase(s) || String.valueOf(t.id).equals(s)) {
                return t;
            }
        }
        return null;
    }

    private static int tierGrant(CommandSourceStack source, String playerName, String tierRaw, boolean grant) {
        var server = source.m_81377_();
        ServerPlayer target = server == null || playerName == null ? null : server.m_6846_().m_11255_(playerName.trim());
        if (target == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.playerOffline(playerName)));
            return 0;
        }
        UnlockTier tier = parseTier(tierRaw);
        if (tier == null) {
            source.m_81352_(Component.m_237113_(UltraStyle.RED + "No tier called " + tierRaw + ". Try one of: "
                    + tierNames() + " (or 1 to 7)."));
            return 0;
        }
        String name = target.m_36316_().getName();
        String label = "Tier " + tier.id + " (" + tier.display + ")";
        ServerPlayer self = source.m_230896_();
        boolean tellTarget = self == null || !self.m_20148_().equals(target.m_20148_());
        if (grant) {
            if (!UnlockSystem.grantStaffTier(target, tier.id)) {
                source.m_288197_(() -> Component.m_237113_(UltraStyle.GRAY + name + " already has a staff grant for "
                        + label + ". Nothing changed."), false);
                return 1;
            }
            source.m_288197_(() -> Component.m_237113_(UltraStyle.GREEN + "Granted " + label + " to " + name
                    + ". It stays unlocked until you revoke it."), true);
            if (tellTarget) {
                target.m_213846_(Component.m_237113_(UltraStyle.GRAY + "Staff unlocked difficulty " + label
                        + " for you. Open /lm difficulty to use it."));
            }
            return 1;
        }
        if (!UnlockSystem.revokeStaffTier(target, tier.id)) {
            source.m_81352_(Component.m_237113_(UltraStyle.RED + name + " has no staff grant for " + label
                    + ". Only staff grants can be revoked here."));
            return 0;
        }
        boolean kept = DifficultyCache.data(target).hasUnlockedTier(tier.id);
        source.m_288197_(() -> Component.m_237113_(UltraStyle.GREEN + "Revoked the staff grant of " + label + " from "
                + name + "." + (kept ? UltraStyle.GRAY + " They still qualify, so the tier stays unlocked." : "")), true);
        if (tellTarget) {
            target.m_213846_(Component.m_237113_(UltraStyle.GRAY + "Staff took back the difficulty " + label
                    + " they had unlocked for you." + (kept ? " You still qualify for it, so you keep it." : "")));
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> whitelistRoot(String name) {
        return Commands.m_82127_(name)
                .executes(ctx -> whitelistStatus(ctx.getSource()))
                .then(Commands.m_82127_("on")
                        .executes(ctx -> setWhitelistEnabled(ctx.getSource(), true)))
                .then(Commands.m_82127_("off")
                        .executes(ctx -> setWhitelistEnabled(ctx.getSource(), false)))
                .then(Commands.m_82127_("toggle")
                        .executes(ctx -> setWhitelistEnabled(
                                ctx.getSource(), !DifficultyConfig.isWhitelistEnabled())))
                .then(Commands.m_82127_("status")
                        .executes(ctx -> whitelistStatus(ctx.getSource())))
                .then(Commands.m_82127_("list")
                        .executes(ctx -> whitelistList(ctx.getSource())))
                .then(Commands.m_82127_("add")
                        .then(LmCommandSuggestions.playerWord("player")
                                .executes(ctx -> whitelistAdd(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("remove")
                        .then(Commands.m_82129_("player", StringArgumentType.greedyString())
                                .executes(ctx -> whitelistRemove(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.m_82127_("clear")
                        .executes(ctx -> whitelistClear(ctx.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> telemetryRoot(String name) {
        return Commands.m_82127_(name)
                .executes(ctx -> telemetryStatus(ctx.getSource()))
                .then(Commands.m_82127_("on")
                        .executes(ctx -> setTelemetryEnabled(ctx.getSource(), true)))
                .then(Commands.m_82127_("off")
                        .executes(ctx -> setTelemetryEnabled(ctx.getSource(), false)))
                .then(Commands.m_82127_("toggle")
                        .executes(ctx -> setTelemetryEnabled(
                                ctx.getSource(),
                                !com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry.isEnabled())))
                .then(Commands.m_82127_("status")
                        .executes(ctx -> telemetryStatus(ctx.getSource())))
                .then(Commands.m_82127_("flush")
                        .executes(ctx -> telemetryFlush(ctx.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> staffFreeRoot(String name) {
        return Commands.m_82127_(name)
                .executes(ctx -> staffFreeStatus(ctx.getSource()))
                .then(Commands.m_82127_("on")
                        .executes(ctx -> setStaffFreeAncientCoins(ctx.getSource(), true)))
                .then(Commands.m_82127_("off")
                        .executes(ctx -> setStaffFreeAncientCoins(ctx.getSource(), false)))
                .then(Commands.m_82127_("toggle")
                        .executes(ctx -> setStaffFreeAncientCoins(
                                ctx.getSource(),
                                !DifficultyConfig.get().staffFreeAncientCoinCosts)))
                .then(Commands.m_82127_("status")
                        .executes(ctx -> staffFreeStatus(ctx.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> syslogRoot(String name) {
        return Commands.m_82127_(name)
                .executes(ctx -> syslogStatus(ctx.getSource()))
                .then(Commands.m_82127_("on")
                        .executes(ctx -> setSyslogEnabled(ctx.getSource(), true)))
                .then(Commands.m_82127_("off")
                        .executes(ctx -> setSyslogEnabled(ctx.getSource(), false)))
                .then(Commands.m_82127_("toggle")
                        .executes(ctx -> setSyslogEnabled(
                                ctx.getSource(),
                                !com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry.isEnabled())))
                .then(Commands.m_82127_("status")
                        .executes(ctx -> syslogStatus(ctx.getSource())))
                .then(Commands.m_82127_("flush")
                        .executes(ctx -> syslogFlush(ctx.getSource())));
    }

    private static int guiDo(CommandSourceStack source, String action, String arg, String page) {
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            return 0;
        }
        String act = action == null ? "" : action.toLowerCase();
        if (DifficultyActions.ACT_CHARACTER_RESET.equals(act)
                || "char_reset".equals(act)
                || "characterreset".equals(act)) {
            if (StaffAccess.denyUnlessStaff(source) == 0) {
                return 0;
            }
        }
        if ("page".equals(act)) {
            String targetPage = arg == null || arg.isBlank() ? "main" : arg;
            DifficultyActions.Result result =
                    DifficultyActions.handleArg(player, DifficultyActions.ACT_PAGE, "0", targetPage);
            result.tell(player);
            return 1;
        }
        String returnPage = page;
        if (returnPage == null || returnPage.isBlank()) {
            returnPage = switch (act) {
                case "reset", "zero", "clear", "lower_tier",
                     "buy", "activate", "purchase_tier" -> "tiers";
                case "equip_title", "clear_title", "equip", "unequip_title" -> "titles";
                default -> "main";
            };
        }
        DifficultyActions.Result result = DifficultyActions.handleArg(player, act, arg, returnPage);
        result.tell(player);
        return result.ok() ? 1 : 0;
    }

    private static boolean isStaff(CommandSourceStack src) {
        return StaffAccess.isStaffSource(src);
    }

    private static int openGui(CommandSourceStack source) {
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.PLAYERS_ONLY_CONSOLE));
            return 0;
        }
        DifficultyMenu.open(player, "main");
        return 1;
    }

    /** Ops-only master switch. */
    private static int setSystemEnabled(CommandSourceStack source, boolean on) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        DifficultyConfig.setEnabled(on);
        DifficultyCache.invalidateAll();
        AreaDifficulty.clearCache();
        if (!on) {
            NearbyMobScaler.shutdownAllScaling();
        }
        if (on) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.turnedOn("Adaptive difficulty")
                            + "\n§7Scaling, rewards, AI, and tier purchases are active again."
            ), true);
        } else {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.turnedOff("Adaptive difficulty")
                            + "\n§7No scaling, Ancient Coin drops or tier buys until you turn it back on."
                            + "\n§7Turn it on again: §f/lm admin difficulty on"
            ), true);
        }
        ServerPlayer actor = source.m_230896_();
        String who = actor != null ? actor.m_6302_() : "console";
        LmLog.event(
                "[{}] system {} by {}",
                AdaptiveDifficultyMod.MOD_ID,
                on ? "ENABLED" : "DISABLED",
                who
        );
        return 1;
    }

    private static int toggleSystem(CommandSourceStack source) {
        return setSystemEnabled(source, !DifficultyConfig.isEnabled());
    }

    private static int systemStatus(CommandSourceStack source) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        boolean on = DifficultyConfig.isEnabled();
        boolean wl = DifficultyConfig.isWhitelistEnabled();
        boolean staffFree = DifficultyConfig.get().staffFreeAncientCoinCosts;
        int n = DifficultyConfig.whitelistEntries().size();
        source.m_288197_(() -> Component.m_237113_(
                (on ? "§aAdaptive difficulty is on." : "§eAdaptive difficulty is off.")
                        + " §8· "
                        + (wl ? "§eTesting whitelist on §7(" + n + " players)" : "§7Testing whitelist off")
                        + " §8· "
                        + (staffFree ? "§aStaff skip coin costs" : "§7Staff pay coin costs")
                        + "\n§8Tip: §f/lm admin difficulty help"
        ), false);
        return 1;
    }

    private static int setWhitelistEnabled(CommandSourceStack source, boolean on) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        DifficultyConfig.setWhitelistEnabled(on);
        DifficultyCache.invalidateAll();
        AreaDifficulty.clearCache();
        int n = DifficultyConfig.whitelistEntries().size();
        if (on) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.turnedOn("Testing whitelist")
                            + " §7(" + n + " players)\n"
                            + "§7Only listed players see scaled mobs while you test.\n"
                            + "§7Add someone: §f/lm admin difficulty whitelist add <player>"
            ), true);
        } else {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.turnedOff("Testing whitelist")
                            + "\n§7Everyone can use adaptive difficulty again (when the system is on)."
            ), true);
        }
        return 1;
    }

    private static int whitelistStatus(CommandSourceStack source) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        boolean wl = DifficultyConfig.isWhitelistEnabled();
        int n = DifficultyConfig.whitelistEntries().size();
        source.m_288197_(() -> Component.m_237113_(
                (wl ? "§eWhitelist ON" : "§7Whitelist OFF")
                        + " §8· §f" + n + " §7entries\n"
                        + "§8/lm admin difficulty whitelist add|remove|list|on|off|toggle|clear"
        ), false);
        return 1;
    }

    private static int whitelistList(CommandSourceStack source) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        var entries = DifficultyConfig.whitelistEntries();
        if (entries.isEmpty()) {
            source.m_288197_(() -> Component.m_237113_(
                    "§7Whitelist is empty. §8Add with §f/lm admin difficulty whitelist add <player>"
            ), false);
            return 1;
        }
        String joined = String.join("§8, §f", entries);
        source.m_288197_(() -> Component.m_237113_(
                "§6Whitelist §7(" + entries.size() + ")\n§f" + joined
        ), false);
        return 1;
    }

    private static int whitelistAdd(CommandSourceStack source, String raw) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        if (raw == null || raw.isBlank()) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.tryCommand("/lm admin difficulty whitelist add <player>")));
            return 0;
        }
        ServerPlayer online = resolveOnlinePlayer(source, raw.trim());
        boolean addedName;
        boolean addedUuid = false;
        if (online != null) {
            addedName = DifficultyConfig.addWhitelistEntry(online.m_6302_());
            addedUuid = DifficultyConfig.addWhitelistEntry(online.m_20148_().toString());
        } else {
            addedName = DifficultyConfig.addWhitelistEntry(raw.trim());
        }
        if (!addedName && !addedUuid) {
            source.m_288197_(() -> Component.m_237113_("§eAlready on whitelist: §f" + raw.trim()), false);
            return 1;
        }
        AreaDifficulty.clearCache();
        String label = online != null ? online.m_6302_() : raw.trim();
        source.m_288197_(() -> Component.m_237113_(
                "§aAdded §f" + label + " §ato whitelist"
                        + (DifficultyConfig.isWhitelistEnabled()
                        ? "."
                        : ".\n§eWhitelist is OFF — §f/lm admin difficulty whitelist on")
        ), true);
        return 1;
    }

    private static int whitelistRemove(CommandSourceStack source, String raw) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        if (raw == null || raw.isBlank()) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.tryCommand(
                "/lm admin difficulty whitelist remove <player>")));
            return 0;
        }
        ServerPlayer online = resolveOnlinePlayer(source, raw.trim());
        boolean removed = DifficultyConfig.removeWhitelistEntry(raw.trim());
        if (online != null) {
            removed = DifficultyConfig.removeWhitelistEntry(online.m_6302_()) || removed;
            removed = DifficultyConfig.removeWhitelistEntry(online.m_20148_().toString()) || removed;
        }
        if (!removed) {
            source.m_288197_(() -> Component.m_237113_("§cNot on whitelist: §f" + raw.trim()), false);
            return 0;
        }
        AreaDifficulty.clearCache();
        source.m_288197_(() -> Component.m_237113_("§aRemoved §f" + raw.trim() + " §afrom whitelist."), true);
        return 1;
    }

    private static int setTelemetryEnabled(CommandSourceStack source, boolean on) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry.setEnabled(on);
        source.m_288197_(() -> Component.m_237113_(
                (on ? "§aCombat hit log ON" : "§eCombat hit log OFF")
                        + "\n§7Logs AD hits for §fall players§7 using the difficulty system"
                        + " §8(rate-limited)."
                        + "\n§8" + com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry.telemetryDir()
        ), true);
        return 1;
    }

    private static int telemetryStatus(CommandSourceStack source) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        source.m_288197_(() -> Component.m_237113_(
                "§6Combat hit log\n§7"
                        + com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry.statusLine()
                        + "\n§8Samples all AD players when ON (not whitelist-gated)."
                        + "\n§8/lm admin difficulty telemetry on|off|flush|test"
        ), false);
        return 1;
    }

    private static int telemetryFlush(CommandSourceStack source) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry.flushAndClose();
        source.m_288197_(() -> Component.m_237113_(
                "§aLogs flushed.\n§8"
                        + com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry.telemetryDir()
        ), true);
        return 1;
    }

    private static int setStaffFreeAncientCoins(CommandSourceStack source, boolean on) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        DifficultyConfig cfg = DifficultyConfig.get();
        cfg.staffFreeAncientCoinCosts = on;
        DifficultyConfig.save();
        source.m_288197_(() -> Component.m_237113_(
                (on ? "§aStaff free Ancient Coin costs ON" : "§eStaff free Ancient Coin costs OFF")
                        + "\n§7Tier buys, Character Services, End dragon summon, etc."
                        + (on
                        ? "\n§7Staff/OP pay §fno coins§7 (bypass permission nodes still work when OFF)."
                        : "\n§7Staff/OP pay like everyone unless they have a bypass permission.")
                        + "\n§8/lm admin difficulty stafffree on|off|toggle|status"
        ), true);
        return 1;
    }

    private static int staffFreeStatus(CommandSourceStack source) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        boolean on = DifficultyConfig.get().staffFreeAncientCoinCosts;
        source.m_288197_(() -> Component.m_237113_(
                "§6Staff free Ancient Coin costs: " + (on ? "§aON" : "§eOFF")
                        + "\n§7When ON, ops/staff skip coin charges on LM paid features."
                        + "\n§8/lm admin difficulty stafffree on|off|toggle"
        ), false);
        return 1;
    }

    private static int setSyslogEnabled(CommandSourceStack source, boolean on) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry.setEnabled(on);
        source.m_288197_(() -> Component.m_237113_(
                (on ? "§aEvent log ON" : "§eEvent log OFF")
                        + "\n§7Logs difficulty, rival, and sparring events for all players (rate-limited)."
                        + "\n§8" + com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry.telemetryDir()
        ), true);
        return 1;
    }

    private static int syslogStatus(CommandSourceStack source) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        source.m_288197_(() -> Component.m_237113_(
                "§6Event log\n§7"
                        + com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry.statusLine()
        ), false);
        return 1;
    }

    private static int syslogFlush(CommandSourceStack source) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry.flushAndClose();
        com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry.flushAndClose();
        source.m_288197_(() -> Component.m_237113_("§aFlushed event and hit logs."), true);
        return 1;
    }

    private static int whitelistClear(CommandSourceStack source) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        DifficultyConfig cfg = DifficultyConfig.get();
        int n = cfg.whitelist == null ? 0 : cfg.whitelist.size();
        if (cfg.whitelist != null) {
            cfg.whitelist.clear();
        }
        DifficultyConfig.save();
        AreaDifficulty.clearCache();
        source.m_288197_(() -> Component.m_237113_("§aCleared whitelist (§f" + n + "§a entries)."), true);
        return 1;
    }

    private static ServerPlayer resolveOnlinePlayer(CommandSourceStack source, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        var server = source.m_81377_();
        if (server == null) {
            return null;
        }
        ServerPlayer byName = server.m_6846_().m_11255_(raw);
        if (byName != null) {
            return byName;
        }
        try {
            java.util.UUID uuid = java.util.UUID.fromString(raw.trim());
            return server.m_6846_().m_11259_(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int denyAdmin(CommandSourceStack source) {
        if (!isStaff(source)) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.NEED_STAFF
            ), false);
            return 0;
        }
        return 1;
    }

    private static int adminHelpOrDeny(CommandSourceStack source) {
        return denyAdmin(source) == 0 ? 0 : adminHelp(source);
    }

    private static int reloadOrDeny(CommandSourceStack source) {
        return denyAdmin(source) == 0 ? 0 : reload(source);
    }

    private static int openAdminSettingsOrDeny(CommandSourceStack source) {
        return denyAdmin(source) == 0 ? 0 : openAdminSettings(source);
    }

    private static int adminSetOrDeny(CommandSourceStack source, String key, String value) {
        return denyAdmin(source) == 0 ? 0 : adminSet(source, key, value);
    }

    private static int openAdminSettings(CommandSourceStack source) {
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            return adminHelp(source);
        }
        // 4.7.56: the Ultra config editor, not the retired chat settings page.
        com.dbzlegacy.adaptivedifficulty.gui.DifficultyMenu.open(player, "settings");
        return 1;
    }

    /** Scaling Health-style area difficulty readout (`sh_difficulty get`). */
    private static int showAreaDifficulty(CommandSourceStack source) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.PLAYERS_ONLY));
            return 0;
        }
        ServerLevel level = player.m_284548_(); // serverLevel / getLevel
        if (level == null) {
            level = player.m_9236_() instanceof ServerLevel sl ? sl : null;
        }
        DifficultySnapshot snap = DifficultyCache.refresh(player);
        long area = level == null ? 0L : AreaDifficulty.at(level, player.m_20183_());
        DifficultyConfig cfg = DifficultyConfig.get();
        ServerLevel finalLevel = level;
        source.m_288197_(() -> Component.m_237113_(
                "§6Area Difficulty\n"
                        + "§eActive tier CR proxy: §f" + snap.active + " §7/ max §f" + snap.availableMax + "\n"
                        + "§eArea at you: §f" + area + "\n"
                        + "§eMode: §f" + cfg.areaDifficultyMode
                        + " §8| §eradius §f" + cfg.mobScaleRadius
                        + " §8| §egroupBonus% §f" + cfg.areaGroupBonusPercent
                        + (finalLevel == null ? "\n§cNo server level" : "")
        ), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        boolean ok = DifficultyConfig.reload();
        DifficultyCache.invalidateAll();
        if (ok) {
            source.m_288197_(() -> Component.m_237113_(
                    LmCommandMessages.reloaded("adaptive difficulty config")), true);
            return 1;
        }
        source.m_81352_(Component.m_237113_(
                "§cConfig reload failed — check server log. Previous live values kept."
        ));
        return 0;
    }

    private static int playerHelp(CommandSourceStack source) {
        LmCommandFeedback.tellLines(source, LmCommandHelp.difficultyPlayer());
        return 1;
    }

    private static int adminHelp(CommandSourceStack source) {
        LmCommandFeedback.tellLines(source, LmCommandHelp.difficultyAdmin());
        return 1;
    }

    private static int adminInspectGuiOrDeny(CommandSourceStack source, String playerName, String page) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        ServerPlayer admin = source.m_230896_();
        if (admin == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.PLAYERS_ONLY_INSPECT));
            return 0;
        }
        if (playerName == null || playerName.isBlank()
                || "clear".equalsIgnoreCase(playerName)
                || "self".equalsIgnoreCase(playerName)
                || "me".equalsIgnoreCase(playerName)) {
            com.dbzlegacy.adaptivedifficulty.gui.ForgeInspectGui.clear(admin);
            DifficultyMenu.open(admin, "main");
            source.m_288197_(() -> Component.m_237113_("§7Inspect closed — showing your own GUI."), false);
            return 1;
        }
        ServerPlayer subject = source.m_81377_().m_6846_().m_11255_(playerName);
        if (subject == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.playerOffline(playerName)));
            return 0;
        }
        String targetPage = page == null || page.isBlank() ? "main" : page;
        if ("details".equalsIgnoreCase(targetPage)) {
            targetPage = "stats";
        }
        if (com.dbzlegacy.adaptivedifficulty.gui.ForgeInspectGui.open(admin, subject, "difficulty")) {
            return 1;
        }
        source.m_81352_(Component.m_237113_("§cCould not open inspect GUI."));
        return 0;
    }

    private static int adminResetPurchasedOrDeny(CommandSourceStack source) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.PLAYERS_ONLY));
            return 0;
        }
        var data = DifficultyCache.data(player);
        data.resetTemporary();
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
        ScaledMobTracker.releaseAndRevertPlayer(player);
        NearbyMobScaler.processEvictions();
        AreaDifficulty.clearCache();
        source.m_288197_(() -> Component.m_237113_(
                "§aCleared active tier. Unlocks & Ancient Coins kept."
        ), true);
        return 1;
    }

    private static int adminCharacterResetOrDeny(CommandSourceStack source) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            source.m_81352_(Component.m_237113_(LmCommandMessages.PLAYERS_ONLY
                + " §8Or: §f/lm difficulty do character_reset"));
            return 0;
        }
        DifficultyActions.Result result =
                DifficultyActions.handleArg(player, DifficultyActions.ACT_CHARACTER_RESET, "0", "");
        result.tell(player);
        AreaDifficulty.clearCache();
        source.m_288197_(() -> Component.m_237113_("§aCharacter difficulty reset applied."), true);
        return result.ok() ? 1 : 0;
    }

    /**
     * Clear stuck session DMZ level sample and refresh Buy GUI reading.
     * Public for Bukkit {@code ForgeBridge.resyncLevel} (Mohist owns {@code /lm difficulty}).
     */
    public static String resyncLevel(ServerPlayer target) {
        if (target == null) {
            return "§cNo player.";
        }
        DmzProgression.clearBaseFormLevel(target.m_20148_());
        int sampled = DmzProgression.refreshBaseFormSample(target);
        var data = DifficultyCache.data(target);
        if (!DmzProgression.isTransformed(target)) {
            data.noteDmzLevel(sampled);
        }
        DifficultyCache.refresh(target);
        DifficultyCache.save(target);

        int live = DmzProgression.dmzLevel(target);
        long shown = UnlockSystem.gateLevelForEligibility(target);
        boolean transformed = DmzProgression.isTransformed(target);
        return "§aResynced §f" + target.m_6302_()
                + "§a — sample §f" + sampled
                + "§a · live DMZ §f" + live
                + "§a · GUI gate §f" + shown
                + (transformed ? "§7 (currently transformed — drop to base to raise sample)" : "");
    }

    private static int adminResyncLevelOrDeny(CommandSourceStack source, String playerName) {
        if (denyAdmin(source) == 0) {
            return 0;
        }
        ServerPlayer target;
        if (playerName == null || playerName.isBlank()) {
            target = source.m_230896_();
            if (target == null) {
                source.m_81352_(Component.m_237113_(LmCommandMessages.tryCommand("/lm admin difficulty resynclevel <player>")));
                return 0;
            }
        } else {
            var server = source.m_81377_();
            if (server == null) {
                source.m_81352_(Component.m_237113_("§cNo server."));
                return 0;
            }
            target = server.m_6846_().m_11255_(playerName);
            if (target == null) {
                source.m_81352_(Component.m_237113_(LmCommandMessages.playerOffline(playerName)));
                return 0;
            }
        }

        String msg = resyncLevel(target);
        source.m_288197_(() -> Component.m_237113_(msg), true);
        return 1;
    }

    private static int adminSet(CommandSourceStack source, String key, String value) {
        DifficultyConfig cfg = DifficultyConfig.get();
        try {
            switch (key.toLowerCase()) {
                case "enabled", "system", "systemenabled" -> {
                    cfg.enabled = Boolean.parseBoolean(value);
                    AreaDifficulty.clearCache();
                }
                case "whitelistenabled", "whitelist" -> {
                    // Boolean only — use whitelist add/remove commands for names.
                    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                            || "on".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) {
                        cfg.whitelistEnabled = "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
                        AreaDifficulty.clearCache();
                    } else {
                        source.m_81352_(Component.m_237113_(
                                "Use true/false for whitelistEnabled, or: /lm admin difficulty whitelist add <player>"
                        ));
                        return 0;
                    }
                }
                case "balancetelemetryenabled", "telemetry", "telemetryenabled" -> {
                    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                            || "on".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) {
                        boolean on = "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
                        com.dbzlegacy.adaptivedifficulty.telemetry.BalanceTelemetry.setEnabled(on);
                    } else {
                        source.m_81352_(Component.m_237113_(
                                "Use true/false, or: /lm admin difficulty telemetry on|off"
                        ));
                        return 0;
                    }
                }
                case "stafffreeancientcoincosts", "stafffreecoins", "stafffree" -> {
                    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                            || "on".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) {
                        cfg.staffFreeAncientCoinCosts =
                                "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
                    } else {
                        source.m_81352_(Component.m_237113_(
                                "Use true/false, or: /lm admin difficulty stafffree on|off"
                        ));
                        return 0;
                    }
                }
                case "balancetelemetrymaxpersecond", "telemetryrate" ->
                        cfg.balanceTelemetryMaxPerSecond = Math.max(1, Math.min(40, Integer.parseInt(value)));
                case "prestigemultiplier" -> cfg.prestigeMultiplier = Double.parseDouble(value);
                case "levelmultiplier" -> cfg.levelMultiplier = Double.parseDouble(value);
                case "rewardscaling" -> cfg.rewardScaling = Math.max(1.0, Double.parseDouble(value));
                case "combatcurveexponent", "combatcurve", "offensecurve" ->
                        cfg.combatCurveExponent = Math.max(0.05, Math.min(1.0, Double.parseDouble(value)));
                case "combatcurvepivot", "offensecurvepivot" ->
                        cfg.combatCurvePivot = Math.max(1L, Long.parseLong(value));
                case "healthcurveexponent", "healthcurve" ->
                        cfg.healthCurveExponent = Math.max(0.05, Math.min(1.0, Double.parseDouble(value)));
                case "healthcurvepivot" -> cfg.healthCurvePivot = Math.max(1L, Long.parseLong(value));
                case "health", "healthpercentperdifficulty" ->
                        cfg.healthPercentPerDifficulty = Math.max(0.0, Math.min(2.0, Double.parseDouble(value)));
                case "damage", "damagepercentperdifficulty" ->
                        cfg.damagePercentPerDifficulty = Math.max(0.0, Math.min(2.0, Double.parseDouble(value)));
                case "defense", "defensepercentperdifficulty" ->
                        cfg.defensePercentPerDifficulty = Math.max(0.0, Math.min(2.0, Double.parseDouble(value)));
                case "movement", "movementpercentper100difficulty" ->
                        cfg.movementPercentPer100Difficulty = Math.max(0.0, Math.min(5.0, Double.parseDouble(value)));
                case "dmzextrahealth", "dmzextrahealthpercent" ->
                        cfg.dmzExtraHealthPercent = Math.max(0.0, Math.min(2.0, Double.parseDouble(value)));
                case "dmzextradamage", "dmzextradamagepercent" ->
                        cfg.dmzExtraDamagePercent = Math.max(0.0, Math.min(2.0, Double.parseDouble(value)));
                case "dmzextradefense", "dmzextradefensepercent" ->
                        cfg.dmzExtraDefensePercent = Math.max(0.0, Math.min(2.0, Double.parseDouble(value)));
                case "dmzextrakidamage", "dmzextrakidamagepercent" ->
                        cfg.dmzExtraKiDamagePercent = Math.max(0.0, Math.min(2.0, Double.parseDouble(value)));
                case "tierawakened" -> cfg.tierAwakened = Long.parseLong(value);
                case "tierenhanced" -> cfg.tierEnhanced = Long.parseLong(value);
                case "tierelite" -> cfg.tierElite = Long.parseLong(value);
                case "tieradvanced" -> cfg.tierAdvanced = Long.parseLong(value);
                case "tiermaster" -> cfg.tierMaster = Long.parseLong(value);
                case "tierlegendary" -> cfg.tierLegendary = Long.parseLong(value);
                case "tiergod" -> cfg.tierGod = Long.parseLong(value);
                case "tierdivine" -> cfg.tierDivine = Long.parseLong(value);
                case "tierimpossible" -> cfg.tierImpossible = Long.parseLong(value);
                case "tiertranscendent" -> cfg.tierTranscendent = Long.parseLong(value);
                case "tiereternal" -> cfg.tierEternal = Long.parseLong(value);
                case "tiermythic" -> cfg.tierMythic = Long.parseLong(value);
                case "tieromega" -> cfg.tierOmega = Long.parseLong(value);
                case "tierabsolute" -> cfg.tierAbsolute = Long.parseLong(value);
                case "tierapex" -> cfg.tierApex = Long.parseLong(value);
                case "tierzenith" -> cfg.tierZenith = Long.parseLong(value);
                case "referencemaxlevel" -> cfg.referenceMaxLevel = Math.max(1L, Long.parseLong(value));
                case "referencemaxprestige" -> cfg.referenceMaxPrestige = Math.max(0, Integer.parseInt(value));
                case "hardcap", "hardcapdifficulty" ->
                        cfg.hardCapDifficulty = Math.max(0L, Long.parseLong(value)); // 0 = no hardcap
                case "mobscaleradius" -> cfg.mobScaleRadius = Double.parseDouble(value);
                case "enablemobscaling" -> cfg.enableMobScaling = Boolean.parseBoolean(value);
                case "scalehostileonly" -> cfg.scaleHostileOnly = Boolean.parseBoolean(value);
                case "applydmzextratoallhostiles", "dmzextrasall", "dmzstyleallhostiles" ->
                        cfg.applyDmzExtrasToAllHostiles = Boolean.parseBoolean(value);
                case "enablerewardscaling" -> cfg.enableRewardScaling = Boolean.parseBoolean(value);
                case "enableelites" -> cfg.enableElites = Boolean.parseBoolean(value);
                case "elitechance", "elitechancepercent" ->
                        cfg.eliteChancePercent = Math.max(0.0, Math.min(25.0, Double.parseDouble(value)));
                case "enablemutations" -> cfg.enableMutations = Boolean.parseBoolean(value);
                case "mutationchance", "mutationchancepercent" ->
                        cfg.mutationChancePercent = Math.max(0.0, Math.min(25.0, Double.parseDouble(value)));
                case "enableadaptiveai" -> cfg.enableAdaptiveAi = Boolean.parseBoolean(value);
                case "enableenemyevolution" -> cfg.enableEnemyEvolution = Boolean.parseBoolean(value);
                case "enablebossscaling" -> cfg.enableBossScaling = Boolean.parseBoolean(value);
                case "enablerivalsystem", "rival", "rivals" ->
                        cfg.enableRivalSystem = Boolean.parseBoolean(value);
                case "enablesparringsystem", "sparring", "spar" ->
                        cfg.enableSparringSystem = Boolean.parseBoolean(value);
                case "enableplayerstatchecker", "statchecker", "playerstatchecker" ->
                        cfg.enablePlayerStatChecker = Boolean.parseBoolean(value);
                case "rivalpresencetp", "rivalpresence" ->
                        cfg.rivalPresenceTp = Boolean.parseBoolean(value);
                case "rivalinstinct" -> cfg.rivalInstinct = Boolean.parseBoolean(value);
                case "rivalchallenges" -> cfg.rivalChallenges = Boolean.parseBoolean(value);
                case "bossstatmultiplier" ->
                        cfg.bossStatMultiplier = Math.max(1.0, Math.min(5.0, Double.parseDouble(value)));
                case "bosshealththreshold" ->
                        cfg.bossHealthThreshold = Math.max(0.0, Math.min(100_000.0, Double.parseDouble(value)));
                case "maxhealthmultiplier" -> {
                    double m = Double.parseDouble(value);
                    cfg.maxHealthMultiplier = m <= 0.0 ? 0.0 : Math.max(1.0, Math.min(20.0, m));
                }
                case "maxscaledhealth" ->
                        // 0 = uncapped (vanilla 1024 attribute wall is raised at mod boot)
                        cfg.maxScaledHealth = Math.max(0.0, Math.min(100_000.0, Double.parseDouble(value)));
                case "maxmovemultiplier" -> {
                    double m = Double.parseDouble(value);
                    cfg.maxMoveMultiplier = m <= 0.0 ? 0.0 : Math.max(1.0, Math.min(20.0, m));
                }
                case "maxarmorbonus" ->
                        // 0 = uncapped
                        cfg.maxArmorBonus = Math.max(0.0, Math.min(100_000.0, Double.parseDouble(value)));
                case "maxdamagemultiplier" -> {
                    // 0 / 1 = uncapped; only values > 1 apply a ceiling
                    double m = Double.parseDouble(value);
                    cfg.maxDamageMultiplier = m <= 0.0 ? 0.0 : Math.max(1.0, Math.min(20.0, m));
                }
                case "adminpermission" -> {
                    // Privilege escalation risk — change only in config JSON + reload.
                    source.m_81352_(Component.m_237113_(
                            "The staff permission setting cannot be changed from chat. Ask the server owner to change it"
                                    + " in the config file, then reload."
                    ));
                    return 0;
                }
                case "guibackend" -> {
                    String gui = switch (value.trim().toLowerCase()) {
                        case "ultra", "kaipath", "dmzultra", "native" -> "ultra";
                        // Retired backends → ultra only.
                        case "cnpc", "customnpcs", "customnpc", "noppes",
                                "cmi", "cmilib", "cmigui", "deluxemenus", "deluxe", "dm",
                                "chest", "bukkit", "inventory", "gui", "auto", "chat" -> "ultra";
                        default -> null;
                    };
                    if (gui == null) {
                        source.m_81352_(Component.m_237113_(
                                "Unknown guiBackend. Ultra is the only option."));
                        return 0;
                    }
                    cfg.guiBackend = gui;
                }
                case "vanilladifficulty" -> cfg.vanillaDifficulty = value.trim().toLowerCase();
                case "restorevanilladifficultyfrompeaceful" ->
                        cfg.restoreVanillaDifficultyFromPeaceful = Boolean.valueOf(value);
                case "areadifficultymode" -> cfg.areaDifficultyMode = value.trim().toLowerCase();
                case "areagroupbonuspercent" -> cfg.areaGroupBonusPercent = Double.parseDouble(value);
                case "areadifficultyvariancepercent" ->
                        cfg.areaDifficultyVariancePercent = Double.parseDouble(value);

                // ── V3 Combat Rating ───────────────────────────────────────
                case "combatratingdmzweight", "crdmz", "crdmzweight" ->
                        cfg.combatRatingDmzWeight = Double.parseDouble(value);
                case "combatratingprestigeweight", "crprestige", "crprestigeweight" ->
                        cfg.combatRatingPrestigeWeight = Double.parseDouble(value);
                case "combatratingtransformweight", "crtransform", "crtransformweight" ->
                        cfg.combatRatingTransformWeight = Double.parseDouble(value);
                case "combatratingdifficultyweight", "crdifficulty", "crdiffweight" ->
                        cfg.combatRatingDifficultyWeight = Double.parseDouble(value);

                // ── V3 Unlock tier levels / max / costs / enemy mults ───────
                case "unlocktier1level", "tier1level" -> cfg.unlockTier1Level = Math.max(1L, Long.parseLong(value));
                case "unlocktier2level", "tier2level" -> cfg.unlockTier2Level = Math.max(1L, Long.parseLong(value));
                case "unlocktier3level", "tier3level" -> cfg.unlockTier3Level = Math.max(1L, Long.parseLong(value));
                case "unlocktier4level", "tier4level" -> cfg.unlockTier4Level = Math.max(1L, Long.parseLong(value));
                case "unlocktier5level", "tier5level" -> cfg.unlockTier5Level = Math.max(1L, Long.parseLong(value));
                case "unlocktier6level", "tier6level" -> cfg.unlockTier6Level = Math.max(1L, Long.parseLong(value));
                case "unlocktier7level", "tier7level" -> cfg.unlockTier7Level = Math.max(1L, Long.parseLong(value));
                case "unlocktier1max", "tier1max" -> cfg.unlockTier1Max = Math.max(0L, Long.parseLong(value));
                case "unlocktier2max", "tier2max" -> cfg.unlockTier2Max = Math.max(0L, Long.parseLong(value));
                case "unlocktier3max", "tier3max" -> cfg.unlockTier3Max = Math.max(0L, Long.parseLong(value));
                case "unlocktier4max", "tier4max" -> cfg.unlockTier4Max = Math.max(0L, Long.parseLong(value));
                case "unlocktier5max", "tier5max" -> cfg.unlockTier5Max = Math.max(0L, Long.parseLong(value));
                case "unlocktier6max", "tier6max" -> cfg.unlockTier6Max = Math.max(0L, Long.parseLong(value));
                case "unlocktier7max", "tier7max" -> cfg.unlockTier7Max = Math.max(0L, Long.parseLong(value));
                case "unlocktier1cost", "tier1cost" -> cfg.unlockTier1Cost = Math.max(1L, Long.parseLong(value));
                case "unlocktier2cost", "tier2cost" -> cfg.unlockTier2Cost = Math.max(1L, Long.parseLong(value));
                case "unlocktier3cost", "tier3cost" -> cfg.unlockTier3Cost = Math.max(1L, Long.parseLong(value));
                case "unlocktier4cost", "tier4cost" -> cfg.unlockTier4Cost = Math.max(1L, Long.parseLong(value));
                case "unlocktier5cost", "tier5cost" -> cfg.unlockTier5Cost = Math.max(1L, Long.parseLong(value));
                case "unlocktier6cost", "tier6cost" -> cfg.unlockTier6Cost = Math.max(1L, Long.parseLong(value));
                case "unlocktier7cost", "tier7cost" -> cfg.unlockTier7Cost = Math.max(1L, Long.parseLong(value));
                case "unlocktier1enemymult", "tier1enemymult", "tier1statpercent" ->
                        cfg.unlockTier1EnemyMult = Math.max(0.05, Math.min(4.0, Double.parseDouble(value)));
                case "unlocktier2enemymult", "tier2enemymult", "tier2statpercent" ->
                        cfg.unlockTier2EnemyMult = Math.max(0.05, Math.min(4.0, Double.parseDouble(value)));
                case "unlocktier3enemymult", "tier3enemymult", "tier3statpercent" ->
                        cfg.unlockTier3EnemyMult = Math.max(0.05, Math.min(4.0, Double.parseDouble(value)));
                case "unlocktier4enemymult", "tier4enemymult", "tier4statpercent" ->
                        cfg.unlockTier4EnemyMult = Math.max(0.05, Math.min(4.0, Double.parseDouble(value)));
                case "unlocktier5enemymult", "tier5enemymult", "tier5statpercent" ->
                        cfg.unlockTier5EnemyMult = Math.max(0.05, Math.min(4.0, Double.parseDouble(value)));
                case "unlocktier6enemymult", "tier6enemymult", "tier6statpercent" ->
                        cfg.unlockTier6EnemyMult = Math.max(0.05, Math.min(4.0, Double.parseDouble(value)));
                case "unlocktier7enemymult", "tier7enemymult", "tier7statpercent" ->
                        cfg.unlockTier7EnemyMult = Math.max(0.05, Math.min(4.0, Double.parseDouble(value)));
                case "weakstatcountermult", "weakcounter",
                     "weakdefensepiercemult", "defpierce",
                     "specializationdamagetax", "spectax",
                     "racecountermult", "racemult" -> {
                    source.m_81352_(Component.m_237113_(
                            "§c" + key + " §7no longer changes combat, so it cannot be set here."
                    ));
                    return 0;
                }
                case "tankdamagedefenseratio", "tankdeffloor" ->
                        cfg.tankDamageDefenseRatio = Math.max(0.0, Math.min(10.0, Double.parseDouble(value)));
                case "tankdamagehealthratio", "tankhpfloor" ->
                        cfg.tankDamageHealthRatio = Math.max(0.0, Math.min(1.0, Double.parseDouble(value)));
                case "mobhealthscale", "hpscale" -> {
                    double h = Double.parseDouble(value);
                    cfg.mobHealthScale = (h <= 0.0 || h > 4.0) ? 0.75 : h;
                }
                case "enableclasscounters", "classcounters" ->
                        cfg.enableClassCounters = Boolean.parseBoolean(value)
                                || "on".equalsIgnoreCase(value)
                                || "true".equalsIgnoreCase(value);
                case "enablestrongstatcounters", "strongstatcounters", "topstatcounters" ->
                        cfg.enableStrongStatCounters = Boolean.parseBoolean(value)
                                || "on".equalsIgnoreCase(value)
                                || "true".equalsIgnoreCase(value);
                case "strongstatcountermult", "strongcounter", "topstatmult" ->
                        cfg.strongStatCounterMult = Math.max(1.0, Math.min(3.0, Double.parseDouble(value)));
                case "classcounterdamagemult", "classdmg" ->
                        cfg.classCounterDamageMult = Math.max(1.0, Math.min(3.0, Double.parseDouble(value)));
                case "classcounterhealthmult", "classhp" ->
                        cfg.classCounterHealthMult = Math.max(1.0, Math.min(3.0, Double.parseDouble(value)));
                case "classcounterarmormult", "classarmor" ->
                        cfg.classCounterArmorMult = Math.max(1.0, Math.min(3.0, Double.parseDouble(value)));
                case "maxcounteroverlaymult", "countercap", "overlaycap" ->
                        cfg.maxCounterOverlayMult = Math.max(1.0, Math.min(4.0, Double.parseDouble(value)));
                case "maxformboost", "formboostcap" ->
                        cfg.maxFormBoost = Math.max(8.0, Math.min(500.0, Double.parseDouble(value)));
                case "maxlivecombatchannel", "maxlivechannel" ->
                        cfg.maxLiveCombatChannel = Math.max(10_000.0, Math.min(1.0e12, Double.parseDouble(value)));
                case "transformscaleweight", "transformweight", "formscaleweight" ->
                        cfg.transformScaleWeight = Math.max(0.0, Math.min(1.0, Double.parseDouble(value)));
                case "transformscaleexponent", "transformexponent", "formscaleexponent" ->
                        cfg.transformScaleExponent = Math.max(0.20, Math.min(1.0, Double.parseDouble(value)));
                case "defensetoarmorfactor" ->
                        cfg.defenseToArmorFactor = Math.max(0.1, Double.parseDouble(value));
                case "nearbyscaleintervalticks", "nearbyscaleinterval" ->
                        cfg.nearbyScaleIntervalTicks = Math.max(10, Integer.parseInt(value));
                case "maxscaledmobsperplayer", "nearbyscalebudgetperplayer", "nearbyscalebudget" -> {
                    int n = Math.max(1, Math.min(5, Integer.parseInt(value)));
                    cfg.maxScaledMobsPerPlayer = n;
                    cfg.nearbyScaleBudgetPerPlayer = n;
                }
                case "tiercostleveldivisor", "costleveldivisor", "tiercostdivisor" ->
                        cfg.tierCostLevelDivisor = Math.max(1.0, Double.parseDouble(value));
                case "tiercostlevelanchor", "costlevelanchor", "tiercostanchor" ->
                        cfg.tierCostLevelAnchor = Math.max(2.0, Double.parseDouble(value));
                case "tiercostt7targetcopper", "tiercostt7target", "t7targetcopper", "tiercosttarget" ->
                        cfg.tierCostT7TargetCopper = Math.max(1L, Long.parseLong(value));

                // ── V3 Ancient Coins + feature gates ───────────────────────
                case "enableancientcoindrops", "ancientcoindrops" ->
                        cfg.enableAncientCoinDrops = Boolean.parseBoolean(value);
                case "ancientcoindropmult", "coindropmult" ->
                        cfg.ancientCoinDropMult = Math.max(0.0, Math.min(10.0, Double.parseDouble(value)));
                case "ancientcoindropchance", "coindropchance" ->
                        cfg.ancientCoinDropChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(value)));
                case "ancientcoinratingdivisor", "coinratingdivisor" ->
                        cfg.ancientCoinRatingDivisor = Math.max(1.0, Math.min(1_000_000.0, Double.parseDouble(value)));
                case "ancientcoinupgradechance", "coinupgradechance" ->
                        cfg.ancientCoinUpgradeChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(value)));
                case "elitestatmultiplier", "elitemult" ->
                        cfg.eliteStatMultiplier = Math.max(1.0, Math.min(5.0, Double.parseDouble(value)));
                case "deathresetsactivedifficulty", "deathreset" ->
                        cfg.deathResetsActiveDifficulty = Boolean.parseBoolean(value);
                case "eliteminunlocktier", "elitemintier" ->
                        cfg.eliteMinUnlockTier = Math.max(0, Integer.parseInt(value));
                case "mutationminunlocktier", "mutationmintier" ->
                        cfg.mutationMinUnlockTier = Math.max(0, Integer.parseInt(value));
                case "adaptiveaiminunlocktier", "aimintier" ->
                        cfg.adaptiveAiMinUnlockTier = Math.max(0, Integer.parseInt(value));
                case "enemyevolutionminunlocktier", "evolutionmintier" ->
                        cfg.enemyEvolutionMinUnlockTier = Math.max(0, Integer.parseInt(value));
                case "bossmechanicsminunlocktier", "bossmintier" ->
                        cfg.bossMechanicsMinUnlockTier = Math.max(0, Integer.parseInt(value));

                default -> {
                    source.m_81352_(Component.m_237113_("Unknown setting: " + key + ". Press Tab after"
                            + " /lm admin difficulty set to list the settings, or use the Config editor in Staff Admin."));
                    return 0;
                }
            }
            DifficultyConfig.sanitizeLive();
            DifficultyConfig.save();
            DifficultyCache.invalidateAll();
            source.m_288197_(() -> Component.m_237113_("§aSet §f" + key + " §a= §f" + value), true);
            return 1;
        } catch (Exception e) {
            source.m_81352_(Component.m_237113_("That value doesn't fit " + key
                    + ". Use a number for number settings, or true / false for switches."));
            return 0;
        }
    }
}
