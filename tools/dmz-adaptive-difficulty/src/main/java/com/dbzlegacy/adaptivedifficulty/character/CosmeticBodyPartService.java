package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.BodyPartCatalog;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/**
 * Custom body parts shop — DMZUltra FormParts pieces players pick on the model
 * customization screen.
 *
 * <p>Mirrors the head-bone shop: pieces unlock once for Ancient Coins (config default plus
 * per-part overrides), unlocks persist in {@link CharacterServicesStore}, and the equipped
 * set is sent on the KaiPath preview through {@code playerModel.parts.bodyParts}
 * (KaiPath 1.3.29 attaches them via {@code BodyPartPreview}). Nothing is written to the DMZ
 * character — unlike head bones, these pieces only ride the Ultra preview wire. Piece ids
 * come from {@link BodyPartCatalog#shopPieceIds()} (the resource {@code pieces} array,
 * which mirrors the client's known meshes).
 */
public final class CosmeticBodyPartService {
    /**
     * Two-up cards per shop page. Must equal what the screen renders — the page slice is the
     * render capacity, so a larger stride silently drops the tail of every page and the pieces
     * on it can never be browsed, unlocked, or equipped.
     */
    public static final int CARDS_PER_PAGE = 8;

    /** Part-color channels in display order (the four DMZUltra design keys). */
    public static final List<String> COLOR_CHANNELS = List.of("own", "trim", "horn", "light");

    /**
     * Preset swatches for the channel picker, two-up like the shop's card pages. Names stay
     * short enough for a card label.
     */
    public static final List<Preset> COLOR_PRESETS =
            List.of(
                    new Preset("White", 0xFFFFFF),
                    new Preset("Silver", 0xC0C0C0),
                    new Preset("Gray", 0x7D7D7D),
                    new Preset("Charcoal", 0x36383D),
                    new Preset("Black", 0x111111),
                    new Preset("Crimson", 0xA52A2A),
                    new Preset("Orange", 0xE07B2E),
                    new Preset("Gold", 0xE3B82F),
                    new Preset("Lime", 0x9BC33C),
                    new Preset("Emerald", 0x2E8B57),
                    new Preset("Teal", 0x2F8F97),
                    new Preset("Sky", 0x63B3ED),
                    new Preset("Royal", 0x3B5DC9),
                    new Preset("Purple", 0x8A45BD),
                    new Preset("Pink", 0xE77FB5),
                    new Preset("Brown", 0x7A5230));

    /** One swatch: display name plus packed RGB. */
    public record Preset(String name, int rgb) {}

    private CosmeticBodyPartService() {}

    public static boolean shopEnabled() {
        return CharacterServicesConfig.get().enabled
                && CharacterServicesConfig.get().bodyPartShop.enabled;
    }

    public static boolean canUse(ServerPlayer player) {
        return shopEnabled()
                && CharacterServicesAccess.canBodyPartShop(player)
                && !CosmeticHeadBoneService.isFormModelRace(player);
    }

    /** Config display override, else the id in words ({@code wings_feather} → {@code Wings feather}). */
    public static String displayName(String partId) {
        String id = partId == null ? "" : partId.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return "?";
        }
        String override = CharacterServicesConfig.get().bodyPartShop.displayNames == null
                ? null
                : CharacterServicesConfig.get().bodyPartShop.displayNames.get(id);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        StringBuilder sb = new StringBuilder();
        for (String word : id.replace('_', ' ').split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toTitleCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.length() == 0 ? id : sb.toString();
    }

    /** Shop-listed piece the server may send. Excluded config ids are not offered. */
    public static boolean isPartKnown(String partId) {
        if (partId == null || partId.isBlank()) {
            return false;
        }
        String id = partId.trim().toLowerCase(Locale.ROOT);
        if (CharacterServicesConfig.get().bodyPartShop.isExcluded(id)) {
            return false;
        }
        return BodyPartCatalog.shopPieceIds().contains(id);
    }

    public static long unlockCost(ServerPlayer player, String partId) {
        CharacterServicesConfig.BodyPartShop shop = CharacterServicesConfig.get().bodyPartShop;
        long base = shop.defaultUnlockCostCopper;
        if (shop.partCosts != null && partId != null) {
            Long override = shop.partCosts.get(partId.trim().toLowerCase(Locale.ROOT));
            if (override == null) {
                override = shop.partCosts.get(partId);
            }
            if (override != null && override >= 0L) {
                base = override;
            }
        }
        return CharacterServicesSystem.payableCost(player, base, shop.levelCostMultiplier);
    }

    public static boolean hasUnlock(ServerPlayer player, String partId) {
        if (player == null || partId == null || partId.isBlank()) {
            return false;
        }
        if (CharacterServicesAccess.bypassCost(player)) {
            return true;
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        Set<String> unlocked = rec.unlockedBodyParts;
        if (unlocked == null) {
            return false;
        }
        String id = partId.trim().toLowerCase(Locale.ROOT);
        for (String u : unlocked) {
            if (u != null && id.equals(u.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Worn custom pieces (unknown / excluded ids dropped so a config change takes effect on
     * the next preview without wiping the player's choice).
     */
    public static List<String> equippedParts(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        List<String> stored = rec.equippedBodyParts;
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : stored) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (BodyPartCatalog.shopPieceIds().contains(id)
                    && !CharacterServicesConfig.get().bodyPartShop.isExcluded(id)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    private static void persistEquipped(ServerPlayer player, List<String> parts, String reason) {
        if (player == null) {
            return;
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        rec.equippedBodyParts = new ArrayList<>(parts == null ? List.of() : parts);
        CharacterServicesStore.get().markDirty();
        CharacterServicesStore.get().save();
        // World sync: client re-renders with the authoritative state (bodyPartsSync only).
        com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork.sendModelSync(player, reason);
    }

    /** Persist worn pieces with the default {@code equip}/{@code clear} reason. */
    private static void persistEquipped(ServerPlayer player, List<String> parts) {
        persistEquipped(
                player,
                parts,
                parts == null || parts.isEmpty()
                        ? com.dbzlegacy.adaptivedifficulty.net.gui.GuiModelSyncPacket.REASON_CLEAR
                        : com.dbzlegacy.adaptivedifficulty.net.gui.GuiModelSyncPacket.REASON_EQUIP);
    }

    /** Player-facing label for a channel ({@code own} -> {@code Part color}). */
    public static String colorChannelLabel(String channel) {
        String ch = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        return switch (ch) {
            case "own" -> "Part color";
            case "trim" -> "Trim color";
            case "horn" -> "Horn color";
            case "light" -> "Glow color";
            default -> "?";
        };
    }

    /** True for the four part channels the shop offers. */
    public static boolean isColorChannel(String channel) {
        return channel != null && COLOR_CHANNELS.contains(channel.trim().toLowerCase(Locale.ROOT));
    }

    /** The player's stored override for a channel, or null when unset. */
    public static Integer partColor(ServerPlayer player, String channel) {
        if (player == null || !isColorChannel(channel)) {
            return null;
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        return switch (channel.trim().toLowerCase(Locale.ROOT)) {
            case "own" -> rec.partColorOwn;
            case "trim" -> rec.partColorTrim;
            case "horn" -> rec.partColorHorn;
            case "light" -> rec.partColorLight;
            default -> null;
        };
    }

    /** Row text for a channel: the picked hex, or {@code Default} when unset. */
    public static String partColorValue(ServerPlayer player, String channel) {
        Integer rgb = partColor(player, channel);
        return rgb == null ? "Default" : hex(rgb);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Persist one channel's override (null clears it) and re-sync the model. */
    private static void persistPartColor(ServerPlayer player, String channel, Integer rgb) {
        if (player == null || !isColorChannel(channel)) {
            return;
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        Integer value = rgb == null ? null : rgb & 0xFFFFFF;
        switch (channel.trim().toLowerCase(Locale.ROOT)) {
            case "own" -> rec.partColorOwn = value;
            case "trim" -> rec.partColorTrim = value;
            case "horn" -> rec.partColorHorn = value;
            case "light" -> rec.partColorLight = value;
            default -> {
                return;
            }
        }
        CharacterServicesStore.get().markDirty();
        CharacterServicesStore.get().save();
        // World sync: menu preview + other clients re-render with the new channel value.
        com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork.sendModelSync(
                player, com.dbzlegacy.adaptivedifficulty.net.gui.GuiModelSyncPacket.REASON_COLOR);
    }

    /** Swatch page count (two-up grid, same stride as the piece cards). */
    public static int colorPageCount() {
        return Math.max(1, (COLOR_PRESETS.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE);
    }

    /** Presets on one swatch page, in catalog order. */
    public static List<Preset> colorPresets(int page) {
        int pages = colorPageCount();
        int p = Math.max(0, Math.min(page, pages - 1));
        int from = p * CARDS_PER_PAGE;
        int to = Math.min(COLOR_PRESETS.size(), from + CARDS_PER_PAGE);
        if (from >= COLOR_PRESETS.size()) {
            return List.of();
        }
        return COLOR_PRESETS.subList(from, to);
    }

    /** Pick a swatch: {@code arg} is {@code channel:rgb} (rgb packed 0xRRGGBB, hex). */
    public static String executeColorPick(ServerPlayer player, String arg) {
        if (!canUse(player)) {
            return UltraStyle.DANGER
                    + "Model customization is unavailable right now. Try again later or ask staff.";
        }
        String[] bits = arg == null ? new String[0] : arg.split(":", 2);
        if (bits.length < 2 || !isColorChannel(bits[0])) {
            return UltraStyle.DANGER + "Pick a color from the swatches.";
        }
        int rgb;
        try {
            rgb = (int) Long.parseLong(bits[1].trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return UltraStyle.DANGER + "That swatch is not available. Pick another from the list.";
        }
        String channel = bits[0].trim().toLowerCase(Locale.ROOT);
        persistPartColor(player, channel, rgb);
        return UltraStyle.CONFIRM + colorChannelLabel(channel) + UltraStyle.CONFIRM + " set to "
                + UltraStyle.VALUE + hex(rgb) + UltraStyle.CONFIRM + ".";
    }

    /** Reset a channel to unset - the model follows the form default again. */
    public static String executeColorReset(ServerPlayer player, String channel) {
        if (!canUse(player)) {
            return UltraStyle.DANGER
                    + "Model customization is unavailable right now. Try again later or ask staff.";
        }
        if (!isColorChannel(channel)) {
            return UltraStyle.DANGER + "Pick a color channel first.";
        }
        String ch = channel.trim().toLowerCase(Locale.ROOT);
        if (partColor(player, ch) == null) {
            return UltraStyle.LABEL + colorChannelLabel(ch) + UltraStyle.LABEL
                    + " is already on its default.";
        }
        persistPartColor(player, ch, null);
        return UltraStyle.CONFIRM + colorChannelLabel(ch) + UltraStyle.CONFIRM
                + " reset to default. " + UltraStyle.LABEL + "It follows your form again.";
    }

    /** Header value for the shop page: {@code none} or the worn display names. */
    public static String wornLabel(ServerPlayer player) {
        List<String> worn = equippedParts(player);
        if (worn.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : worn) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(displayName(id));
        }
        return sb.toString();
    }

    public static int pageCount() {
        int size = BodyPartCatalog.shopPieceIds().size();
        return Math.max(1, (size + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE);
    }

    public static String pageTitle(int page) {
        return "Body parts " + (page + 1) + "/" + pageCount();
    }

    /** Shop-list ids on one page, in catalog order. */
    public static List<String> pageIds(int page) {
        List<String> all = BodyPartCatalog.shopPieceIds();
        int pages = pageCount();
        int p = Math.max(0, Math.min(page, pages - 1));
        int from = p * CARDS_PER_PAGE;
        int to = Math.min(all.size(), from + CARDS_PER_PAGE);
        if (from >= all.size()) {
            return List.of();
        }
        return all.subList(from, to);
    }

    /**
     * GUI rows: {@code partId\tdisplay\tstate\tcostText}.
     * State is {@code E} on, {@code U} unlocked, {@code L} locked.
     */
    public static List<String> cards(ServerPlayer player, int page) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        Set<String> worn = new LinkedHashSet<>(equippedParts(player));
        for (String id : pageIds(page)) {
            String state;
            if (worn.contains(id)) {
                state = "E";
            } else if (hasUnlock(player, id)) {
                state = "U";
            } else {
                state = "L";
            }
            String cost = "L".equals(state)
                    ? CharacterServicesSystem.formatCost(unlockCost(player, id))
                    : "";
            out.add(id + "\t" + displayName(id) + "\t" + state + "\t" + cost);
        }
        return out;
    }

    public static String executeUnlock(ServerPlayer player, String partId) {
        if (!canUse(player)) {
            return UltraStyle.DANGER + "Model customization is unavailable right now. Try again later or ask staff.";
        }
        if (partId == null || partId.isBlank()) {
            return UltraStyle.DANGER + "Pick a piece from the list.";
        }
        String id = partId.trim().toLowerCase(Locale.ROOT);
        if (!isPartKnown(id)) {
            return UltraStyle.DANGER + "That piece is not available. Pick another from the list.";
        }
        if (hasUnlock(player, id)) {
            return executeToggle(player, id);
        }
        long cost = CharacterServicesAccess.bypassCost(player) ? 0L : unlockCost(player, id);
        AncientCoinEconomy.migrateWalletToItems(player);
        if (cost > 0L && !AncientCoinEconomy.canAfford(player, cost)) {
            return UltraStyle.DANGER + AncientCoinEconomy.missingText(player, cost);
        }
        if (cost > 0L && !AncientCoinEconomy.charge(player, cost)) {
            return UltraStyle.DANGER + AncientCoinEconomy.missingText(player, cost);
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        if (rec.unlockedBodyParts == null) {
            rec.unlockedBodyParts = new LinkedHashSet<>();
        }
        rec.unlockedBodyParts.add(id);
        CharacterServicesStore.get().markDirty();
        CharacterServicesStore.get().save();
        List<String> worn = new ArrayList<>(equippedParts(player));
        if (!worn.contains(id)) {
            worn.add(id);
        }
        persistEquipped(player, worn);
        return UltraStyle.CONFIRM + "Unlocked and turned on " + UltraStyle.VALUE + displayName(id)
                + UltraStyle.CONFIRM + ".";
    }

    /** Turn one piece on (paying if it is locked) or off. Other worn pieces stay. */
    public static String executeToggle(ServerPlayer player, String partId) {
        if (!canUse(player)) {
            return UltraStyle.DANGER + "Model customization is unavailable right now. Try again later or ask staff.";
        }
        if (partId == null || partId.isBlank()) {
            return UltraStyle.DANGER + "Pick a piece from the list.";
        }
        String id = partId.trim().toLowerCase(Locale.ROOT);
        if (!isPartKnown(id)) {
            return UltraStyle.DANGER + "That piece is not available. Pick another from the list.";
        }
        List<String> worn = new ArrayList<>(equippedParts(player));
        if (worn.contains(id)) {
            worn.remove(id);
            persistEquipped(player, worn, com.dbzlegacy.adaptivedifficulty.net.gui.GuiModelSyncPacket.REASON_UNEQUIP);
            return UltraStyle.CONFIRM + "Turned off " + UltraStyle.VALUE + displayName(id)
                    + UltraStyle.CONFIRM + ".";
        }
        if (!hasUnlock(player, id)) {
            return executeUnlock(player, id);
        }
        worn.add(id);
        persistEquipped(player, worn);
        return UltraStyle.CONFIRM + "Turned on " + UltraStyle.VALUE + displayName(id)
                + UltraStyle.CONFIRM + ".";
    }

    public static String executeClear(ServerPlayer player) {
        if (!canUse(player)) {
            return UltraStyle.DANGER + "Model customization is unavailable right now. Try again later or ask staff.";
        }
        if (equippedParts(player).isEmpty()) {
            return UltraStyle.LABEL + "You are not wearing any custom body parts.";
        }
        persistEquipped(player, List.of());
        return UltraStyle.CONFIRM + "Cleared your custom body parts. " + UltraStyle.LABEL
                + "Unlocked pieces stay in your collection.";
    }
}
