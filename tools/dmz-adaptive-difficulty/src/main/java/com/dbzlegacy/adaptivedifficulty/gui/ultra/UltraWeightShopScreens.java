package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.ItemIds;
import com.dbzlegacy.adaptivedifficulty.net.gui.UltraClientFeatures;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Sparring -> Weights shop. Players pick a DMZ weight type, type a weight value (1 copper per
 * point), confirm, then receive the item with {@code WeightValue} NBT via
 * {@code WeightItem.setWeight(stack, value)} (reflection; falls back to direct NBT).
 *
 * <p>Requires KaiPath {@code textInput} (GuiOpenPacket field 11). Clients without it see a clear
 * blocker message on the input page (KaiPath 1.3.9+ advertises it).
 */
public final class UltraWeightShopScreens {
    /** Hard cap so typed values stay sane (1 copper : 1 weight). */
    public static final int MAX_WEIGHT_VALUE = 10_000_000;

    /** Digits needed to type {@link #MAX_WEIGHT_VALUE} (was hard-capped at 4 / 9999, then 100k). */
    public static final int MAX_WEIGHT_DIGITS = String.valueOf(MAX_WEIGHT_VALUE).length();

    /** Curios slot id for {@code CurioType.WEIGHTS} ({@code data/.../curios/slots/weights.json}). */
    public static final String CURIOS_WEIGHTS_SLOT = "weights";

    public static final String INPUT_ID = "input:weight_value";

    private UltraWeightShopScreens() {}

    enum WeightOffer {
        TURTLE_SHELL("turtle_shell", "Turtle Shell", "dragonminez:weight_turtle_shell"),
        WORKOUT_WEIGHTS("workout_weights", "Workout Weights", "dragonminez:workout_weights"),
        PICCOLO_CAPE("piccolo_cape", "Piccolo Cape", "dragonminez:weight_piccolo_cape");

        final String id;
        final String label;
        final String itemId;

        WeightOffer(String id, String label, String itemId) {
            this.id = id;
            this.label = label;
            this.itemId = itemId;
        }

        static WeightOffer fromId(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String key = raw.trim().toLowerCase(Locale.ROOT);
            for (WeightOffer o : values()) {
                if (o.id.equals(key)) {
                    return o;
                }
            }
            // Accept item-id / enum-style aliases.
            return switch (key) {
                case "weight_turtle_shell", "turtleshell", "turtle" -> TURTLE_SHELL;
                case "workout", "weights", "workoutweights" -> WORKOUT_WEIGHTS;
                case "weight_piccolo_cape", "piccolo", "cape", "piccolocape" -> PICCOLO_CAPE;
                default -> null;
            };
        }
    }

    static void openShop(ServerPlayer player) {
        AncientCoinEconomy.migrateWalletToItems(player);
        UltraScreenSpec spec =
                sub("weights")
                        .line(UltraStyle.LABEL + "Training weights - 1 ancient copper per weight value.")
                        .labelValue(
                                "Copper",
                                String.valueOf(
                                        AncientCoinEconomy.countOf(
                                                player, AncientCoinEconomy.CoinKind.COPPER)))
                        .labelValue("Ancient Coins", AncientCoinEconomy.inventoryBreakdown(player))
                        .labelValue("Max value", String.valueOf(MAX_WEIGHT_VALUE))
                        .line(UltraStyle.LABEL + "Pick a type, type the weight value, then confirm.")
                        .buttonsAfterLines();
        for (WeightOffer offer : WeightOffer.values()) {
            spec.button(
                            "page:weights_input:" + offer.id,
                            UltraStyle.confirm(offer.label),
                            UltraStyle.STYLE_CONFIRM)
                    .rowGap();
        }
        spec.footerBackHome("Spar Home");
        spec.send(player);
    }

    static void openInput(ServerPlayer player, String typeId) {
        WeightOffer offer = WeightOffer.fromId(typeId);
        if (offer == null) {
            openShop(player);
            return;
        }
        AncientCoinEconomy.migrateWalletToItems(player);
        UltraScreenSpec spec =
                sub("weights_input:" + offer.id)
                        .line(UltraStyle.LABEL + offer.label)
                        .labelValue(
                                "Copper",
                                String.valueOf(
                                        AncientCoinEconomy.countOf(
                                                player, AncientCoinEconomy.CoinKind.COPPER)))
                        .labelValue("Ancient Coins", AncientCoinEconomy.inventoryBreakdown(player))
                        .labelValue("Price", "1 copper per weight value")
                        .labelValue("Max value", String.valueOf(MAX_WEIGHT_VALUE));

        if (!UltraClientFeatures.hasTextInput(player)) {
            spec.line(UltraStyle.danger("Typed weight entry needs a newer KaiPath client."))
                    .line(
                            UltraStyle.LABEL
                                    + "Install KaiPath 1.3.9 or newer to type weight values. "
                                    + "Until then this shop cannot take a typed value.");
            spec.buttonsAfterLines();
            spec.footerBack("page:weights", "Weights shop");
            spec.send(player);
            return;
        }

        spec.line(UltraStyle.LABEL + "Type how much weight value you want (1-" + MAX_WEIGHT_VALUE + ").");
        spec.buttonsAfterLines();
        spec.textField(
                        INPUT_ID,
                        "",
                        "e.g. 500000",
                        UltraStyle.COL_L,
                        spec.row(),
                        UltraStyle.PANEL_W - UltraStyle.COL_L * 2,
                        UltraStyle.BTN_H,
                        MAX_WEIGHT_DIGITS)
                .rowGap();
        spec.buttonBound(
                        "do:weight_quote:" + offer.id,
                        UltraStyle.confirm("Continue"),
                        UltraStyle.STYLE_CONFIRM,
                        INPUT_ID)
                .rowGap();
        spec.footerBack("page:weights", "Weights shop");
        spec.send(player);
    }

    static void openConfirm(ServerPlayer player, String typeId, int value) {
        WeightOffer offer = WeightOffer.fromId(typeId);
        if (offer == null || value < 1 || value > MAX_WEIGHT_VALUE) {
            openShop(player);
            return;
        }
        AncientCoinEconomy.migrateWalletToItems(player);
        long cost = value;
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        String costText = free ? "Free for staff" : AncientCoinEconomy.formatExactCost(cost);
        UltraScreenSpec spec =
                sub("weights_confirm:" + offer.id + ":" + value)
                        .line(UltraStyle.LABEL + "Confirm weight purchase")
                        .labelValue("Type", offer.label)
                        .labelValue("Weight value", String.valueOf(value))
                        .labelValue("Cost", costText)
                        .labelValue(
                                "Copper",
                                String.valueOf(
                                        AncientCoinEconomy.countOf(
                                                player, AncientCoinEconomy.CoinKind.COPPER)))
                        .labelValue("Ancient Coins", AncientCoinEconomy.inventoryBreakdown(player));
        if (!free && !AncientCoinEconomy.canAfford(player, cost)) {
            spec.line(UltraStyle.danger(AncientCoinEconomy.missingText(player, cost)));
        }
        spec.buttonsAfterLines();
        if (!free && !AncientCoinEconomy.canAfford(player, cost)) {
            spec.button(
                            "page:weights_input:" + offer.id,
                            UltraStyle.LABEL + "Change value",
                            UltraStyle.STYLE_NORMAL)
                    .rowGap();
        } else {
            spec.button(
                            "do:weight_buy:" + offer.id + ":" + value,
                            UltraStyle.confirm(
                                    free
                                            ? "Confirm"
                                            : UltraStyle.buttonText(
                                                    "Buy "
                                                            + AncientCoinEconomy.compactCost(cost))),
                            UltraStyle.STYLE_CONFIRM)
                    .rowGap();
            spec.buttonLeft(
                            "page:weights_input:" + offer.id,
                            UltraStyle.LABEL + "Change value",
                            UltraStyle.STYLE_NORMAL)
                    .rowGap();
        }
        spec.footerBack("page:weights", "Weights shop");
        spec.send(player);
    }

    /**
     * Handles {@code weight_quote} / {@code weight_buy}. Outcomes are stashed via
     * {@link UltraGuiFeedback} before opening the next page (caller skips generic reopen).
     */
    public static String handleAction(
            ServerPlayer player, String action, String arg, String page, JsonObject args) {
        if (player == null) {
            return UltraStyle.dangerLine("This only works in-game.");
        }
        String act = action == null ? "" : action.toLowerCase(Locale.ROOT).trim();
        return switch (act) {
            case "weight_quote", "quote" -> quote(player, arg, args, page);
            case "weight_buy", "buy_weight" -> buy(player, arg);
            default -> UltraStyle.dangerLine("That weight shop action is not available.");
        };
    }

    /** Enter-key submit from {@code input:weight_value} on a weights_input page. */
    public static String handleInputSubmit(ServerPlayer player, String page, JsonObject args) {
        String typeId = typeFromInputPage(page);
        if (typeId == null) {
            return UltraStyle.dangerLine("Pick a weight type first.");
        }
        return quote(player, typeId, args, page);
    }

    private static String quote(ServerPlayer player, String typeId, JsonObject args, String page) {
        WeightOffer offer = WeightOffer.fromId(typeId);
        if (offer == null) {
            offer = WeightOffer.fromId(typeFromInputPage(page));
        }
        if (offer == null) {
            return UltraStyle.dangerLine("Pick a weight type first.");
        }
        Integer value = parseWeightValue(argsString(args, "value"));
        if (value == null) {
            return UltraStyle.dangerLine(
                    "Type a whole number from 1 to " + MAX_WEIGHT_VALUE + ".");
        }
        openConfirm(player, offer.id, value);
        return "";
    }

    private static String buy(ServerPlayer player, String arg) {
        ParsedBuy parsed = parseBuyArg(arg);
        if (parsed == null) {
            return UltraStyle.dangerLine("Confirm the weight type and value first.");
        }
        return purchase(player, parsed.offer, parsed.value, true);
    }

    /**
     * {@code /spar weight buy <type> <value>} — same purchase path as the GUI confirm
     * button; the outcome is returned as chat feedback instead of being stashed for the
     * next screen.
     */
    public static String buyFromCommand(ServerPlayer player, String typeId, int value) {
        WeightOffer offer = WeightOffer.fromId(typeId);
        String bad = validateCommandOffer(offer, value);
        if (bad != null) {
            return bad;
        }
        return purchase(player, offer, value, false);
    }

    /** {@code /spar weight quote <type> <value>} — price check without opening a screen. */
    public static String quoteFromCommand(ServerPlayer player, String typeId, int value) {
        WeightOffer offer = WeightOffer.fromId(typeId);
        String bad = validateCommandOffer(offer, value);
        if (bad != null) {
            return bad;
        }
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        String costText = free ? "Free (staff)" : AncientCoinEconomy.formatExactCost(value);
        return UltraStyle.noticeLine(
                offer.label
                        + " - weight "
                        + value
                        + " - "
                        + costText
                        + ". Buy: /spar weight buy "
                        + offer.id
                        + " "
                        + value);
    }

    private static String validateCommandOffer(WeightOffer offer, int value) {
        if (offer == null) {
            return UltraStyle.dangerLine(
                    "Unknown weight type - try turtle_shell, workout_weights, or piccolo_cape.");
        }
        if (value < 1 || value > MAX_WEIGHT_VALUE) {
            return UltraStyle.dangerLine("Type a whole number from 1 to " + MAX_WEIGHT_VALUE + ".");
        }
        return null;
    }

    private static String purchase(
            ServerPlayer player, WeightOffer offer, int value, boolean openGui) {
        AncientCoinEconomy.migrateWalletToItems(player);
        long cost = value;
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        long charge = free ? 0L : cost;
        if (charge > 0L && !AncientCoinEconomy.canAfford(player, charge)) {
            String missing = UltraStyle.dangerLine(AncientCoinEconomy.missingText(player, charge));
            if (openGui) {
                // Stash before open so the confirm page shows the missing-funds line.
                UltraGuiFeedback.deliver(player, missing);
                openConfirm(player, offer.id, value);
                return "";
            }
            return missing;
        }
        ItemStack stack = ItemIds.stack(offer.itemId, 1);
        if (stack == null || stack.m_41619_()) {
            return UltraStyle.dangerLine("That weight item is not available on this server.");
        }
        if (charge > 0L && !AncientCoinEconomy.charge(player, charge)) {
            String missing = UltraStyle.dangerLine(AncientCoinEconomy.missingText(player, charge));
            if (openGui) {
                UltraGuiFeedback.deliver(player, missing);
                openConfirm(player, offer.id, value);
                return "";
            }
            return missing;
        }
        applyWeightValue(stack, value);
        String destination = giveWeightItem(player, stack);
        String costText = free ? "Free (staff)" : AncientCoinEconomy.formatExactCost(cost);
        String notice =
                UltraStyle.noticeLine(
                        "Bought "
                                + offer.label
                                + " - weight "
                                + value
                                + " - "
                                + costText
                                + " - "
                                + destination
                                + ".");
        if (openGui) {
            UltraGuiFeedback.deliver(player, notice);
            openShop(player);
            return "";
        }
        return notice;
    }

    /**
     * Prefer an empty Curios {@code weights} slot (CurioType.WEIGHTS), else player inventory, else
     * drop at feet - same fallback ladder as DMZ {@code WeightCommand}, plus curios when free.
     */
    static String giveWeightItem(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.m_41777_();
        if (tryEquipWeightsCurio(player, copy)) {
            return "equipped in weights slot";
        }
        if (player.m_150109_().m_36054_(copy)) {
            return "added to inventory";
        }
        player.m_36176_(copy, false);
        return "dropped (inventory full)";
    }

    /** Reflection into Curios API - LM does not compile against curios. */
    private static boolean tryEquipWeightsCurio(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.m_41619_()) {
            return false;
        }
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = api.getMethod("getCuriosInventory", LivingEntity.class);
            Object raw = getInv.invoke(null, player);
            if (!(raw instanceof LazyOptional<?> lazy)) {
                return false;
            }
            AtomicBoolean equipped = new AtomicBoolean(false);
            lazy.ifPresent(
                    handler -> {
                        try {
                            Method getStacksHandler =
                                    handler.getClass().getMethod("getStacksHandler", String.class);
                            Object stacksOpt =
                                    getStacksHandler.invoke(handler, CURIOS_WEIGHTS_SLOT);
                            if (!(stacksOpt instanceof Optional<?> slotOpt) || slotOpt.isEmpty()) {
                                return;
                            }
                            Object stacksHandler = slotOpt.get();
                            Method getStacks = stacksHandler.getClass().getMethod("getStacks");
                            Object dynamic = getStacks.invoke(stacksHandler);
                            Method getSlots = dynamic.getClass().getMethod("getSlots");
                            Method getStackInSlot =
                                    dynamic.getClass().getMethod("getStackInSlot", int.class);
                            int slots = ((Number) getSlots.invoke(dynamic)).intValue();
                            Method setEquipped =
                                    handler.getClass()
                                            .getMethod(
                                                    "setEquippedCurio",
                                                    String.class,
                                                    int.class,
                                                    ItemStack.class);
                            for (int i = 0; i < slots; i++) {
                                ItemStack existing =
                                        (ItemStack) getStackInSlot.invoke(dynamic, i);
                                if (existing == null || existing.m_41619_()) {
                                    setEquipped.invoke(
                                            handler, CURIOS_WEIGHTS_SLOT, i, stack.m_41777_());
                                    equipped.set(true);
                                    return;
                                }
                            }
                        } catch (Throwable ignored) {
                            // fall through to inventory
                        }
                    });
            return equipped.get();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] Curios weights equip skipped - {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
            return false;
        }
    }

    /**
     * Prefer DMZ {@code WeightItem.setWeight}; fall back to {@code WeightValue} NBT if the class
     * is unavailable at compile/runtime (GeckoLib not on the LM compile classpath).
     */
    static void applyWeightValue(ItemStack stack, int value) {
        if (stack == null || stack.m_41619_()) {
            return;
        }
        try {
            Class<?> cls = Class.forName("com.dragonminez.common.init.item.WeightItem");
            Method set = cls.getMethod("setWeight", ItemStack.class, int.class);
            set.invoke(null, stack, value);
            return;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] WeightItem.setWeight unavailable - writing WeightValue NBT directly",
                    AdaptiveDifficultyMod.MOD_ID,
                    t);
        }
        CompoundTag tag = stack.m_41784_();
        tag.m_128405_("WeightValue", value);
    }

    static Integer parseWeightValue(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // Digits only - reject signs, decimals, separators.
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        if (s.length() > MAX_WEIGHT_DIGITS) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            if (v < 1 || v > MAX_WEIGHT_VALUE) {
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ParsedBuy parseBuyArg(String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        int colon = arg.lastIndexOf(':');
        if (colon <= 0 || colon >= arg.length() - 1) {
            return null;
        }
        WeightOffer offer = WeightOffer.fromId(arg.substring(0, colon));
        Integer value = parseWeightValue(arg.substring(colon + 1));
        if (offer == null || value == null) {
            return null;
        }
        return new ParsedBuy(offer, value);
    }

    private static String typeFromInputPage(String page) {
        if (page == null) {
            return null;
        }
        String p = page.trim();
        String lower = p.toLowerCase(Locale.ROOT);
        if (lower.startsWith("weights_input:")) {
            return p.substring("weights_input:".length()).trim();
        }
        if (lower.startsWith("weights_confirm:")) {
            String rest = p.substring("weights_confirm:".length()).trim();
            int colon = rest.lastIndexOf(':');
            return colon > 0 ? rest.substring(0, colon).trim() : rest;
        }
        return null;
    }

    /** Weights shop / confirm — Sparring subpages without the system tab row. */
    private static UltraScreenSpec sub(String page) {
        return UltraScreenSpec.of("spar")
                .title("Sparring")
                .page(page);
    }


    private static String argsString(JsonObject args, String key) {
        if (args == null || key == null || !args.has(key) || args.get(key).isJsonNull()) {
            return null;
        }
        try {
            return args.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private record ParsedBuy(WeightOffer offer, int value) {}
}
