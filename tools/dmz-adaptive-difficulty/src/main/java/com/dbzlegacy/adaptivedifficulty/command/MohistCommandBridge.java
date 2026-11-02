package com.dbzlegacy.adaptivedifficulty.command;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Mohist routes player chat through Bukkit. Registers Bukkit commands that forward the
 * full line to Forge Brigadier (with Brigadier tab complete). LegacyMechanicsGUI must
 * not be installed — it owns chest menus and disables this bridge when present.
 */
public final class MohistCommandBridge {
    private static volatile boolean registered;

    private MohistCommandBridge() {}

    public static void tryRegister(MinecraftServer server) {
        if (registered || server == null) {
            return;
        }
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            Object legacyGui = pluginManager.getClass().getMethod("getPlugin", String.class)
                    .invoke(pluginManager, "LegacyMechanicsGUI");
            if (legacyGui != null) {
                AdaptiveDifficultyMod.LOGGER.error(
                        "[{}] LegacyMechanicsGUI is loaded — remove plugins/LegacyMechanicsGUI-*.jar "
                                + "and restart. Chest menus and duplicate /lm handlers are active.",
                        AdaptiveDifficultyMod.MOD_ID);
            }
            Object hostPlugin = pluginManager.getClass().getMethod("getPlugin", String.class)
                    .invoke(pluginManager, AdaptiveDifficultyMod.MOD_ID);
            if (hostPlugin == null) {
                hostPlugin = pluginManager.getClass().getMethod("getPlugin", String.class)
                        .invoke(pluginManager, "LegacyMechanics");
            }
            if (hostPlugin == null) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] Mohist command bridge: no Bukkit plugin host for {}",
                        AdaptiveDifficultyMod.MOD_ID,
                        AdaptiveDifficultyMod.MOD_ID);
                return;
            }
            Object commandMap = bukkit.getMethod("getCommandMap").invoke(null);
            Class<?> commandExecutor = Class.forName("org.bukkit.command.CommandExecutor");
            Object executor = Proxy.newProxyInstance(
                    commandExecutor.getClassLoader(),
                    new Class<?>[] {commandExecutor},
                    (proxy, method, args) -> {
                        if (!"onCommand".equals(method.getName()) || args == null || args.length < 4) {
                            return false;
                        }
                        Object sender = args[0];
                        String label = String.valueOf(args[2]);
                        String[] cmdArgs = (String[]) args[3];
                        dispatch(server, sender, label, cmdArgs);
                        return true;
                    });

            Class<?> tabCompleter = Class.forName("org.bukkit.command.TabCompleter");
            Object tabCompleterProxy = Proxy.newProxyInstance(
                    tabCompleter.getClassLoader(),
                    new Class<?>[] {tabCompleter},
                    (proxy, method, args) -> {
                        if (!"onTabComplete".equals(method.getName()) || args == null || args.length < 4) {
                            return Collections.emptyList();
                        }
                        Object sender = args[0];
                        String label = String.valueOf(args[2]);
                        String[] cmdArgs = (String[]) args[3];
                        return tabComplete(server, sender, label, cmdArgs);
                    });

            Class<?> pluginCommandClass = Class.forName("org.bukkit.command.PluginCommand");
            Constructor<?> ctor = pluginCommandClass.getDeclaredConstructor(String.class,
                    Class.forName("org.bukkit.plugin.Plugin"));
            ctor.setAccessible(true);

            String staffPerm = CommandAccess.staffBukkitPermission();
            String skillPerm = CommandAccess.skillCheckBukkitPermission();

            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "lm", List.of(), null);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "difficulty", List.of("diff"), null);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "rival", List.of(), null);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "spar", List.of(), null);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "skillcheck", List.of(), skillPerm);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "progression", List.of(), null);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "prestige", List.of(), null);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "skills", List.of(), staffPerm);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "character", List.of(), null);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "enddragon", List.of(), staffPerm);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "androidify", List.of(), staffPerm);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "padmin", List.of(), staffPerm);
            registerOne(server, commandMap, pluginCommandClass, ctor, hostPlugin, executor, tabCompleterProxy,
                    "terminal", List.of(), null);

            registered = true;
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Mohist Bukkit→Forge command bridge registered (Forge-only mode)",
                    AdaptiveDifficultyMod.MOD_ID);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Mohist command bridge failed (non-Mohist server?): {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
        }
    }

    private static void registerOne(
            MinecraftServer server,
            Object commandMap,
            Class<?> pluginCommandClass,
            Constructor<?> ctor,
            Object hostPlugin,
            Object executor,
            Object tabCompleter,
            String name,
            List<String> aliases,
            String bukkitPermission) throws ReflectiveOperationException {
        Object cmd = ctor.newInstance(name, hostPlugin);
        cmd.getClass().getMethod("setExecutor", Class.forName("org.bukkit.command.CommandExecutor"))
                .invoke(cmd, executor);
        cmd.getClass().getMethod("setTabCompleter", Class.forName("org.bukkit.command.TabCompleter"))
                .invoke(cmd, tabCompleter);
        if (!aliases.isEmpty()) {
            cmd.getClass().getMethod("setAliases", List.class).invoke(cmd, aliases);
        }
        if (bukkitPermission != null && !bukkitPermission.isBlank()) {
            cmd.getClass().getMethod("setPermission", String.class).invoke(cmd, bukkitPermission);
        }
        cmd.getClass().getMethod("setDescription", String.class)
                .invoke(cmd, "LegacyMechanics → Forge (" + name + ")");
        Method register = commandMap.getClass().getMethod("register", String.class,
                Class.forName("org.bukkit.command.Command"));
        register.invoke(commandMap, name, cmd);
    }

    static void dispatch(MinecraftServer server, Object sender, String label, String[] args) {
        CommandSourceStack source = commandSource(server, sender);
        if (source == null) {
            return;
        }
        if (!mayUseBukkitRoot(source, label)) {
            LmCommandFeedback.tell(source, LmCommandMessages.NO_PERMISSION_BRIDGE);
            return;
        }
        String[] coalesced = LmAdminArgCoalesce.forMohist(label, args);
        String line = commandLine(label, coalesced);
        try {
            server.m_129892_().m_230957_(source, line);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] Command dispatch failed for /{}: {}",
                    AdaptiveDifficultyMod.MOD_ID, line, t.toString());
        }
    }

    private static List<String> tabComplete(
            MinecraftServer server, Object sender, String label, String[] args) {
        CommandSourceStack source = commandSource(server, sender);
        if (source == null || !mayUseBukkitRoot(source, label)) {
            return Collections.emptyList();
        }
        String[] coalesced = LmAdminArgCoalesce.forMohist(label, args);
        String line = commandLine(label, coalesced);
        if (LmCommandSuggestions.needsTrailingSpaceForTab(label, coalesced, source) && !line.endsWith(" ")) {
            line = line + " ";
        }
        int cursor = line.length();
        try {
            var dispatcher = server.m_129892_().m_82094_();
            ParseResults<CommandSourceStack> parse = dispatcher.parse(line, source);
            Suggestions suggestions = dispatcher.getCompletionSuggestions(parse, cursor)
                    .get(3, TimeUnit.SECONDS);
            List<String> out = new ArrayList<>();
            String partial = LmCommandSuggestions.playerNamePartial(label, coalesced, source);
            if (LmCommandSuggestions.expectsPlayerName(label, coalesced, source)) {
                List<String> players = LmCommandSuggestions.isOfflineClearSlot(label, coalesced)
                        ? LmCommandSuggestions.filterClearTargets(source, partial)
                        : LmCommandSuggestions.filterPlayerSlot(label, coalesced, source, partial);
                if (!players.isEmpty()) {
                    return players;
                }
            }
            for (Suggestion s : suggestions.getList()) {
                String text = bukkitSuggestionToken(line, s);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                if (!partial.isEmpty() && !text.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                out.add(text);
            }
            return out;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug("[{}] Tab complete failed for /{}: {}",
                    AdaptiveDifficultyMod.MOD_ID, line, t.toString());
            return Collections.emptyList();
        }
    }

    private static String commandLine(String label, String[] args) {
        StringBuilder line = new StringBuilder(label);
        if (args != null) {
            for (String arg : args) {
                line.append(' ');
                if (arg != null) {
                    line.append(arg);
                }
            }
        }
        return line.toString();
    }

    private static String bukkitSuggestionToken(String line, Suggestion s) {
        String text = s.getText();
        return text == null || text.isEmpty() ? null : text;
    }

    private static CommandSourceStack commandSource(MinecraftServer server, Object sender) {
        if (sender == null) {
            return null;
        }
        String senderName = sender.getClass().getName();
        if (senderName.contains("ConsoleCommandSender")
                || senderName.contains("RemoteConsoleCommandSender")) {
            return server.m_129893_();
        }
        try {
            Object handle = sender.getClass().getMethod("getHandle").invoke(sender);
            if (handle instanceof ServerPlayer sp) {
                return forPlayer(sp);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object uuidObj = sender.getClass().getMethod("getUniqueId").invoke(sender);
            if (uuidObj instanceof UUID uuid) {
                ServerPlayer sp = server.m_6846_().m_11259_(uuid);
                if (sp != null) {
                    return forPlayer(sp);
                }
            }
        } catch (Throwable ignored) {
        }
        return server.m_129893_();
    }

    private static boolean mayUseBukkitRoot(CommandSourceStack source, String label) {
        if (CommandAccess.isStaffOnlyBukkitCommand(label)) {
            return StaffAccess.isStaffSource(source);
        }
        if (CommandAccess.isSkillCheckBukkitCommand(label)) {
            return StaffAccess.hasSkillCheckSource(source);
        }
        return true;
    }

    /** Entity-attached stack so feedback and brigadier suggestions reach the player on Mohist. */
    private static CommandSourceStack forPlayer(ServerPlayer sp) {
        MinecraftServer server = sp.m_20194_();
        if (server == null) {
            return null;
        }
        return server.m_129893_().m_81329_(sp);
    }
}
