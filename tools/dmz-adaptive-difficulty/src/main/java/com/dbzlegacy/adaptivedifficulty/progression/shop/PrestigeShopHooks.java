package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.command.LmCommandFeedback;
import com.dbzlegacy.adaptivedifficulty.command.LmCommandSuggestions;
import com.dbzlegacy.adaptivedifficulty.config.ConfigRegistry;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCustomShop;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopConfig;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PrestigeShopHooks {
    public static final String MODULE_ID = "prestige-shop";
    private static final Map<String, String> HELP;

    private PrestigeShopHooks() {
    }

    public static void decoratePlaceholders(Map<String, String> map, ServerPlayer serverPlayer) {
        if (map == null || serverPlayer == null || !"true".equalsIgnoreCase(map.get("system_enabled"))) {
            return;
        }
        try {
            PrestigeShopConfig prestigeShopConfig = PrestigeShopConfig.get();
            map.put("form_cost", String.valueOf(prestigeShopConfig.forms.majin.cost));
            map.put("majin_cost", String.valueOf(prestigeShopConfig.forms.majin.cost));
            map.put("mutant_cost", String.valueOf(prestigeShopConfig.forms.mutant.cost));
            map.put("ultimate_cost", String.valueOf(prestigeShopConfig.forms.ultimate.cost));
            map.put("godki_cost", String.valueOf(prestigeShopConfig.godKi.costPerPoint));
            map.put("godki_per_point", DmzRewards.formatWhole((double)prestigeShopConfig.godKi.masteryPerPoint));
            map.put("godki_max", String.valueOf(prestigeShopConfig.godKi.maxPoints));
            String string = map.getOrDefault("shop_skill_ids", "");
            for (String string2 : string.split(",")) {
                if (string2.isBlank()) continue;
                map.put("skill_" + string2 + "_cost", String.valueOf(prestigeShopConfig.skillShop.costFor(string2)));
                map.put("skill_" + string2 + "_levels", String.valueOf(prestigeShopConfig.skillShop.levelsFor(string2)));
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (int n : prestigeShopConfig.exchange.amounts) {
                map.put("turnin_" + n + "_points", String.valueOf(prestigeShopConfig.exchange.payout(n)));
                stringBuilder.append(stringBuilder.length() == 0 ? "" : ",").append(n);
            }
            map.put("turnin_amounts", stringBuilder.toString());
            map.put("custom_count", String.valueOf(PrestigeCustomShop.forSale().size()));
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static String handleDo(ServerPlayer serverPlayer, String string, String string2, String string3) {
        if (serverPlayer == null || string == null || !DifficultyConfig.get().enablePrestigeSystem) {
            return null;
        }
        return switch (string.toLowerCase(Locale.ROOT).trim()) {
            case "custom_buy", "buy_custom", "perk_buy" -> PrestigeCustomShop.buy(serverPlayer, string2);
            case "custom_remove", "remove_custom", "perk_remove" -> PrestigeCustomShop.remove(serverPlayer, string2);
            default -> null;
        };
    }

    public static void attachAdminCommands(LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder) {
        literalArgumentBuilder.then(Commands.m_82127_((String)"custom").then(((RequiredArgumentBuilder)LmCommandSuggestions.playerWord((String)"player").executes(commandContext -> PrestigeShopHooks.reply((CommandContext<CommandSourceStack>)commandContext, PrestigeShopHooks.listOwned((CommandSourceStack)commandContext.getSource(), StringArgumentType.getString((CommandContext)commandContext, (String)"player"))))).then(Commands.m_82129_((String)"entry", (ArgumentType)StringArgumentType.word()).suggests((commandContext, suggestionsBuilder) -> SharedSuggestionProvider.m_82970_(PrestigeShopConfig.get().customEntries.keySet(), (SuggestionsBuilder)suggestionsBuilder)).then(((RequiredArgumentBuilder)LmCommandSuggestions.word((String)"mode", (SuggestionProvider)LmCommandSuggestions.literals((String[])new String[]{"give", "take", "set"})).executes(commandContext -> PrestigeShopHooks.reply((CommandContext<CommandSourceStack>)commandContext, PrestigeShopHooks.adjust((CommandContext<CommandSourceStack>)commandContext, 1)))).then(Commands.m_82129_((String)"amount", (ArgumentType)IntegerArgumentType.integer((int)0)).executes(commandContext -> PrestigeShopHooks.reply((CommandContext<CommandSourceStack>)commandContext, PrestigeShopHooks.adjust((CommandContext<CommandSourceStack>)commandContext, IntegerArgumentType.getInteger((CommandContext)commandContext, (String)"amount")))))))));
        literalArgumentBuilder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_((String)"shop").executes(commandContext -> PrestigeShopHooks.reply((CommandContext<CommandSourceStack>)commandContext, PrestigeShopHooks.shopHelp()))).then(Commands.m_82127_((String)"list").executes(commandContext -> PrestigeShopHooks.reply((CommandContext<CommandSourceStack>)commandContext, PrestigeShopHooks.shopList())))).then(Commands.m_82127_((String)"check").executes(commandContext -> PrestigeShopHooks.reply((CommandContext<CommandSourceStack>)commandContext, PrestigeShopHooks.shopCheck())))).then(Commands.m_82127_((String)"reload").executes(commandContext -> {
            PrestigeShopConfig.load();
            return PrestigeShopHooks.reply((CommandContext<CommandSourceStack>)commandContext, "\u00a7aReloaded prestige-shop.json \u00a77(" + PrestigeShopConfig.get().customEntries.size() + " custom entries). " + PrestigeShopHooks.shopCheckSummary());
        })));
    }

    private static int reply(CommandContext<CommandSourceStack> commandContext, String string) {
        LmCommandFeedback.tellLines((CommandSourceStack)((CommandSourceStack)commandContext.getSource()), (String)string);
        return string != null && string.startsWith("\u00a7c") ? 0 : 1;
    }

    private static ServerPlayer online(CommandSourceStack commandSourceStack, String string) {
        MinecraftServer minecraftServer = commandSourceStack.m_81377_();
        return minecraftServer == null || string == null ? null : minecraftServer.m_6846_().m_11255_(string);
    }

    private static String listOwned(CommandSourceStack commandSourceStack, String string) {
        if (!StaffAccess.isStaffSource((CommandSourceStack)commandSourceStack)) {
            return "\u00a7cStaff only.";
        }
        ServerPlayer serverPlayer = PrestigeShopHooks.online(commandSourceStack, string);
        if (serverPlayer == null) {
            return "\u00a7cPlayer not online: \u00a7f" + string;
        }
        List<String> list = PrestigeCustomShop.ownedSummary(serverPlayer);
        return list.isEmpty() ? "\u00a77" + serverPlayer.m_6302_() + " owns no custom prestige perks." : "\u00a76Custom perks - " + serverPlayer.m_6302_() + "\n\u00a7f" + String.join("\n\u00a7f", list);
    }

    private static String adjust(CommandContext<CommandSourceStack> commandContext, int n) {
        if (!StaffAccess.isStaffSource((CommandSourceStack)((CommandSourceStack)commandContext.getSource()))) {
            return "\u00a7cStaff only.";
        }
        String string = StringArgumentType.getString(commandContext, (String)"player");
        ServerPlayer serverPlayer = PrestigeShopHooks.online((CommandSourceStack)commandContext.getSource(), string);
        if (serverPlayer == null) {
            return "\u00a7cPlayer not online: \u00a7f" + string;
        }
        return PrestigeCustomShop.adminAdjust(serverPlayer, StringArgumentType.getString(commandContext, (String)"entry"), StringArgumentType.getString(commandContext, (String)"mode"), n);
    }

    private static String shopHelp() {
        return "\u00a76Prestige shop - staff\n\u00a7f/padmin shop list \u00a77- custom entries and prices\n\u00a7f/padmin shop check \u00a77- find typos in reward lines\n\u00a7f/padmin shop reload \u00a77- reload config/legacymechanics/prestige-shop.json\n\u00a7f/padmin custom <player> \u00a77- perks a player owns\n\u00a7f/padmin custom <player> <entry> <give|take|set> [n] \u00a77- no favor charged or refunded\n\u00a77Edit everything in \u00a7f/lm \u00a77> Config > Prestige shop.";
    }

    private static String shopList() {
        PrestigeShopConfig prestigeShopConfig = PrestigeShopConfig.get();
        if (prestigeShopConfig.customEntries.isEmpty()) {
            return "\u00a77No custom entries. Add one in /lm > Config > Prestige shop > Custom Entries.";
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("\u00a76Custom prestige entries" + (prestigeShopConfig.customShop.enabled ? "" : " \u00a7c(custom shop is off)"));
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> entry : prestigeShopConfig.customEntries.entrySet()) {
            PrestigeShopConfig.CustomEntry customEntry = entry.getValue();
            arrayList.add((customEntry.enabled ? "\u00a7a" : "\u00a78") + entry.getKey() + " \u00a77- \u00a7f" + customEntry.name + " \u00a77- \u00a7e" + customEntry.cost + " favor" + (String)(customEntry.maxPurchases == 1 ? "" : " \u00a77(max " + String.valueOf(customEntry.maxPurchases == 0 ? "unlimited" : Integer.valueOf(customEntry.maxPurchases)) + ")") + (customEntry.enabled ? "" : " \u00a78[off]"));
        }
        return String.join("\n", arrayList);
    }

    private static String shopCheck() {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> entry : PrestigeShopConfig.get().customEntries.entrySet()) {
            for (String string : PrestigeCustomShop.problems(entry.getKey(), entry.getValue())) {
                arrayList.add("\u00a7c" + entry.getKey() + "\u00a77: " + string);
            }
        }
        return arrayList.isEmpty() ? "\u00a7aAll custom entries look valid." : String.join("\n", arrayList);
    }

    private static String shopCheckSummary() {
        int n = 0;
        for (Map.Entry<String, PrestigeShopConfig.CustomEntry> entry : PrestigeShopConfig.get().customEntries.entrySet()) {
            n += PrestigeCustomShop.problems(entry.getKey(), entry.getValue()).size();
        }
        return n == 0 ? "\u00a7aNo problems found." : "\u00a7c" + n + " problem(s) - run /padmin shop check.";
    }

    public static void addModules(List<ConfigRegistry.Module> list) {
        ConfigRegistry.Module module = new ConfigRegistry.Module(MODULE_ID, "Prestige shop", PrestigeShopConfig.class, PrestigeShopConfig.path(), PrestigeShopConfig::get, () -> {
            if (!PrestigeShopConfig.save()) {
                throw new IllegalStateException("prestige-shop save failed");
            }
        }, PrestigeShopConfig::load, true, 1, null);
        int n = list.size();
        for (int i = 0; i < list.size(); ++i) {
            if (!"progression".equals(list.get(i).id())) continue;
            n = i + 1;
        }
        list.add(n, module);
    }

    public static Object coerce(Type type, String string) {
        Class<?> clazz = null;
        if (type instanceof Class) {
            clazz = (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class) {
                clazz = (Class<?>) rawType;
            }
        }
        if (clazz == PrestigeShopConfig.CustomEntry.class) {
            PrestigeShopConfig.CustomEntry customEntry = new PrestigeShopConfig.CustomEntry();
            customEntry.name = string == null ? "" : string.trim();
            customEntry.enabled = false;
            return customEntry;
        }
        return null;
    }

    public static Object freshInstance(String string) {
        return MODULE_ID.equals(string) ? PrestigeShopConfig.freshDefaults() : null;
    }

    public static String describe(String string, String string2) {
        if (!MODULE_ID.equals(string) || string2 == null) {
            return null;
        }
        return HELP.get(string2.replaceAll("\\[(?:\\\\.|[^\\]])*\\]", "[]"));
    }

    private static void help(String string, String string2) {
        HELP.put(string, string2);
    }

    static {
        Object object;
        HELP = new HashMap<String, String>();
        PrestigeShopHooks.help("exchange", "Turning current prestiges into Prestige favor (Shop > Exchange).");
        PrestigeShopHooks.help("exchange.enabled", "Shows the Exchange page and allows turn-ins.");
        PrestigeShopHooks.help("exchange.amounts", "How many prestiges can be turned in at once. One button per amount.");
        PrestigeShopHooks.help("exchange.amounts[]", "An allowed turn-in amount.");
        PrestigeShopHooks.help("exchange.bonusGroupSize", "Every this-many prestiges in one turn-in adds a growing bonus (3 gives 3->4, 6->9, 9->15). 0 = no bonus.");
        PrestigeShopHooks.help("exchange.payoutOverrides", "Exact favor for a turn-in amount, replacing the formula. Key = amount, value = favor.");
        PrestigeShopHooks.help("exchange.payoutOverrides[]", "Favor paid for turning in this many prestiges.");
        PrestigeShopHooks.help("skillShop", "Buying permanent DMZ skill levels with favor (Shop > Skill shop).");
        PrestigeShopHooks.help("skillShop.enabled", "Shows the Skill shop and allows skill purchases.");
        PrestigeShopHooks.help("skillShop.costPerPurchase", "Favor per purchase, unless a skill has its own price in Cost Overrides.");
        PrestigeShopHooks.help("skillShop.levelsPerPurchase", "Skill levels gained per purchase, unless overridden per skill.");
        PrestigeShopHooks.help("skillShop.levelsPerPurchaseOverrides", "Levels per purchase for one skill. Key = skill id (potentialunlock gives 2 by default).");
        PrestigeShopHooks.help("skillShop.levelsPerPurchaseOverrides[]", "Levels gained per purchase of this skill.");
        PrestigeShopHooks.help("skillShop.costOverrides", "Favor per purchase for one skill. Key = skill id.");
        PrestigeShopHooks.help("skillShop.costOverrides[]", "Favor per purchase of this skill.");
        PrestigeShopHooks.help("skillShop.maxLevelOverrides", "Highest kept level the shop sells for one skill. Key = skill id.");
        PrestigeShopHooks.help("skillShop.maxLevelOverrides[]", "Highest level of this skill the shop sells.");
        PrestigeShopHooks.help("skillShop.hiddenSkills", "Skill ids taken out of the Skill shop. Levels players already bought are kept.");
        PrestigeShopHooks.help("skillShop.hiddenSkills[]", "A skill id that is not sold.");
        PrestigeShopHooks.help("skillShop.extraSkills", "Extra DMZ skill ids to sell besides the Natural + Saga skills (form skills are never sold).");
        PrestigeShopHooks.help("skillShop.extraSkills[]", "An extra skill id sold in the Skill shop.");
        PrestigeShopHooks.help("tierShop", "Permanent difficulty tier unlocks bought with favor (Shop > Difficulty tiers).");
        PrestigeShopHooks.help("tierShop.enabled", "Shows the tier page and allows tier purchases.");
        PrestigeShopHooks.help("tierShop.requireInOrder", "When on, a player must own T1..T(n-1) before buying Tn.");
        PrestigeShopHooks.help("tierShop.costs", "Favor price per tier. Key = tier number 1-7.");
        PrestigeShopHooks.help("tierShop.costs[]", "Favor price of this tier.");
        PrestigeShopHooks.help("tierShop.notForSale", "Tier numbers that can't be bought (staff can still grant them).");
        PrestigeShopHooks.help("tierShop.notForSale[]", "A tier that is not for sale.");
        PrestigeShopHooks.help("forms", "Permanent Majin, Mutant and Ultimate (Shop > Effects).");
        PrestigeShopHooks.help("forms.majinMutantExclusive", "When on, a player can own only one of Majin and Mutant at a time.");
        for (String string2 : List.of("majin", "mutant", "ultimate")) {
            object = Character.toUpperCase(string2.charAt(0)) + string2.substring(1);
            PrestigeShopHooks.help("forms." + string2, "Permanent " + (String)object + " price and refund.");
            PrestigeShopHooks.help("forms." + string2 + ".enabled", "Sells Permanent " + (String)object + ". Players who own it keep it when this is off.");
            PrestigeShopHooks.help("forms." + string2 + ".cost", "Favor price of Permanent " + (String)object + ".");
            PrestigeShopHooks.help("forms." + string2 + ".refundPercent", "Percent of the price given back when a player removes Permanent " + (String)object + ".");
        }
        PrestigeShopHooks.help("godKi", "God Ki Enhancement investments (Shop > Effects).");
        PrestigeShopHooks.help("godKi.enabled", "Allows new God Ki investments. Existing investments keep working when off.");
        PrestigeShopHooks.help("godKi.costPerPoint", "Favor per God Ki investment.");
        PrestigeShopHooks.help("godKi.masteryPerPoint", "God Ki mastery each investment guarantees.");
        PrestigeShopHooks.help("godKi.maxPoints", "Most God Ki investments a player can make (staff /padmin godki still caps at 5).");
        PrestigeShopHooks.help("customShop", "The Special perks page with your own entries.");
        PrestigeShopHooks.help("customShop.enabled", "Shows the custom entries page. Owners keep their rewards when this is off.");
        PrestigeShopHooks.help("customShop.menuLabel", "Button and page title for the custom entries.");
        PrestigeShopHooks.help("customShop.reapplySeconds", "How often owned perks are checked and re-applied (after milk, death, prestige), in seconds.");
        PrestigeShopHooks.help("customShop.hidePotionParticles", "Hide the particles of permanent potion effects (a line ending in 'show' overrides).");
        PrestigeShopHooks.help("customEntries", "Your own shop entries. Add one with a short id like vip_aura (the value is its name), then open it to set price and rewards. New entries start off.");
        PrestigeShopHooks.help("customEntries[]", "One custom entry. Open it to edit price, limits and rewards. Delete it to take its rewards away from everyone.");
        PrestigeShopHooks.help("customEntries[].enabled", "Sells this entry. Players who already own it keep it when off.");
        PrestigeShopHooks.help("customEntries[].name", "Name shown in the shop.");
        PrestigeShopHooks.help("customEntries[].description", "Short text shown under the name.");
        PrestigeShopHooks.help("customEntries[].cost", "Favor price of the first purchase.");
        PrestigeShopHooks.help("customEntries[].costIncreasePerPurchase", "Added to the price for every level already owned (2nd buy = cost + this, ...).");
        PrestigeShopHooks.help("customEntries[].maxPurchases", "How many times it can be bought. 1 = one-time unlock, 0 = unlimited. Rewards scale with the count.");
        PrestigeShopHooks.help("customEntries[].removable", "Players can remove it themselves (one level at a time).");
        PrestigeShopHooks.help("customEntries[].refundPercent", "Percent of that level's price refunded when removed.");
        PrestigeShopHooks.help("customEntries[].requiredLifetimePrestiges", "Lifetime prestiges needed before it can be bought.");
        PrestigeShopHooks.help("customEntries[].requiredLevel", "DMZ level needed before it can be bought.");
        PrestigeShopHooks.help("customEntries[].requiredEntries", "Other entry ids the player must own first.");
        PrestigeShopHooks.help("customEntries[].requiredEntries[]", "An entry id that must be owned first.");
        PrestigeShopHooks.help("customEntries[].blockedByEntries", "Entry ids that can't be owned at the same time (like Majin vs Mutant).");
        PrestigeShopHooks.help("customEntries[].blockedByEntries[]", "An entry id that blocks this one.");
        PrestigeShopHooks.help("customEntries[].revokeSkillsOnRemove", "On removal, lower skills this entry raised (unless the player has gone past that level).");
        String string3 = "Permanent potion effect: <effect id> [amplifier] [show]. Amplifier 0 = level I. Example: minecraft:speed 1";
        PrestigeShopHooks.help("customEntries[].potionEffects", string3);
        PrestigeShopHooks.help("customEntries[].potionEffects[]", string3);
        String string2 = "Attribute bonus: <attribute id> <amount> [add|multiply_base|multiply_total]. Any mod's attribute. Example: minecraft:generic.max_health 4 add";
        PrestigeShopHooks.help("customEntries[].attributes", string2);
        PrestigeShopHooks.help("customEntries[].attributes[]", string2);
        object = "DMZ stat bonus: <STR|SKP|RES|DEF|STM|VIT|PWR|ENE> <value> [flat|mult] [raw]. Examples: STR 500 flat, PWR 1.15 mult";
        PrestigeShopHooks.help("customEntries[].dmzStats", (String)object);
        PrestigeShopHooks.help("customEntries[].dmzStats[]", (String)object);
        String string4 = "Permanent DMZ skill level: <skill id> <level>. Kept through prestige. Example: kiboost 2";
        PrestigeShopHooks.help("customEntries[].dmzSkills", string4);
        PrestigeShopHooks.help("customEntries[].dmzSkills[]", string4);
        String string5 = "Permanent DMZ effect: <effect id> [power]. Example: majin 1.0";
        PrestigeShopHooks.help("customEntries[].dmzEffects", string5);
        PrestigeShopHooks.help("customEntries[].dmzEffects[]", string5);
        String string6 = "Console command. Placeholders: %player% %uuid% %entry% %name% %count%.";
        PrestigeShopHooks.help("customEntries[].commandsOnPurchase", "Run once each time it is bought. " + string6);
        PrestigeShopHooks.help("customEntries[].commandsOnPurchase[]", "Run when bought. " + string6);
        PrestigeShopHooks.help("customEntries[].commandsOnRemove", "Run when a level is removed. " + string6);
        PrestigeShopHooks.help("customEntries[].commandsOnRemove[]", "Run when removed. " + string6);
        PrestigeShopHooks.help("customEntries[].commandsOnReapply", "Run on login, respawn and after prestige while owned (for things that reset). " + string6);
        PrestigeShopHooks.help("customEntries[].commandsOnReapply[]", "Run on login/respawn/prestige. " + string6);
    }
}
