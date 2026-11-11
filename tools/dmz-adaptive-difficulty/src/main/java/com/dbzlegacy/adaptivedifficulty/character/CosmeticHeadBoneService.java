package com.dbzlegacy.adaptivedifficulty.character;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dragonminez.common.stats.character.Character;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/** Global head-bone unlock shop and equip (cross-race cosmetics). */
public final class CosmeticHeadBoneService {
    public static final int CARDS_PER_PAGE = 14;

    /** Prior active bone while a shop card is being previewed. Not written to player json. */
    private static final Map<UUID, String> PREVIEW_STASH = new ConcurrentHashMap<>();

    private CosmeticHeadBoneService() {}

    public static boolean isPreviewing(ServerPlayer player) {
        return player != null && PREVIEW_STASH.containsKey(player.m_20148_());
    }

    /** Bone currently shown, when a preview is active. */
    public static String previewBoneId(ServerPlayer player) {
        return isPreviewing(player) ? activeBone(player) : "";
    }

    public static String previewLabel(ServerPlayer player) {
        String id = previewBoneId(player);
        if (id.isBlank()) {
            return "";
        }
        CosmeticHeadBoneCatalog.Entry entry = CosmeticHeadBoneCatalog.get(id);
        return entry == null ? CosmeticHeadBoneCatalog.prettyId(id) : entry.displayName();
    }

    /**
     * Show {@code boneId} on the player without charging, unlocking, or saving
     * {@code equippedHeadBone}. The bone they had on is kept until restore, a new preview,
     * or a real equip.
     */
    public static String previewBone(ServerPlayer player, String boneId) {
        if (!canUse(player)) {
            return "§cModel customization is unavailable right now. Try again later or ask staff.";
        }
        if (boneId == null || boneId.isBlank()) {
            return "§cPick a look from the list.";
        }
        String bone = boneId.trim().toLowerCase(Locale.ROOT);
        if (!CosmeticHeadBoneCatalog.isKnown(bone)) {
            return "§cThat look is not available. Pick another from the list.";
        }
        Character ch = DmzProgression.character(player);
        if (ch == null) {
            return "§cYour character is still loading. Wait a moment and try again.";
        }
        String base = PREVIEW_STASH.putIfAbsent(player.m_20148_(), activeBone(player));
        if (base == null) {
            base = PREVIEW_STASH.get(player.m_20148_());
        }
        try {
            List<String> shown = new ArrayList<>(HeadPartPieces.fragments(base));
            shown.add(bone);
            String token = HeadPartPieces.join(shown);
            if (!token.equalsIgnoreCase(activeBone(player))) {
                ch.setActiveHeadBone(token);
                RaceHeadBoneSync.syncClient(player);
            }
            return "";
        } catch (Throwable t) {
            return "§cCould not preview that look. Try again or pick another.";
        }
    }

    /** Put back the bone from before the preview. */
    public static String restorePreview(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        String prior = PREVIEW_STASH.remove(player.m_20148_());
        if (prior == null) {
            return "";
        }
        return applyActiveOnly(player, prior);
    }

    /** Drop the stash without restoring. A purchase or real equip replaces the preview. */
    public static void discardPreview(ServerPlayer player) {
        if (player != null) {
            PREVIEW_STASH.remove(player.m_20148_());
        }
    }

    /**
     * After a restart, an unpaid preview must not stay on the character. A saved
     * equipped bone wins. A bone the player does not own is cleared.
     */
    public static void settleAfterLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PREVIEW_STASH.remove(player.m_20148_());
        expandLegacyUnlocks(player);
        String equipped = equippedBone(player);
        String keptEquipped = ownedPiecesToken(player, equipped);
        if (!equipped.isEmpty() && !keptEquipped.equalsIgnoreCase(equipped)) {
            persistEquippedBone(player, keptEquipped);
            equipped = keptEquipped;
        }
        equipped = equipped.toLowerCase(Locale.ROOT);
        String active = activeBone(player).toLowerCase(Locale.ROOT);
        if (!equipped.isEmpty() && isBoneAllowed(player, equipped) && !equipped.equals(active)) {
            applyActiveOnly(player, equipped);
            return;
        }
        if (!active.isEmpty() && !isBoneAllowed(player, active)) {
            String fallback = ownedPiecesToken(player, active);
            if (fallback.isEmpty()) {
                fallback = unequipHeadBoneId(player);
                if (!fallback.isEmpty() && !isBoneAllowed(player, fallback)) {
                    fallback = "";
                }
            }
            applyActiveOnly(player, fallback);
        }
    }

    public static boolean shopEnabled() {
        return CharacterServicesConfig.get().enabled
                && CharacterServicesConfig.get().headBoneShop.enabled;
    }

    public static boolean canUse(ServerPlayer player) {
        return shopEnabled()
                && CharacterServicesAccess.canUseServices(player)
                && CharacterServicesAccess.canHeadBoneShop(player)
                && !isFormModelRace(player);
    }

    /**
     * Races whose look is owned by a form model (not DMZ headBones). Head-part shop does not
     * apply correctly — hide/grey Model customization for these players.
     */
    public static boolean isFormModelRace(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        String race = DmzProgression.race(player);
        return "monkey".equalsIgnoreCase(race);
    }

    /**
     * True when {@code boneId} is listed on the race's DMZ headBones (disk {@code character.json}
     * and/or ConfigManager). Disk is authoritative when ConfigManager has not loaded the race
     * (e.g. custom {@code demon} packs that only exist under {@code config/dragonminez/races/}).
     */
    public static boolean isNativeForRace(String raceId, String boneId) {
        if (raceId == null || boneId == null) {
            return false;
        }
        if (!CharacterServicesConfig.get().headBoneShop.nativeRaceBonesFree) {
            return false;
        }
        String race = raceId.trim().toLowerCase(Locale.ROOT);
        String bone = boneId.trim().toLowerCase(Locale.ROOT);
        for (String listed : DmzContentDiscovery.headBonesForRace(race)) {
            if (listed == null || listed.isBlank()) {
                continue;
            }
            String id = listed.trim().toLowerCase(Locale.ROOT);
            if (bone.equals(id)) {
                return true;
            }
            if (id.contains("+")) {
                for (String piece : HeadPartPieces.fragments(id)) {
                    if (bone.equals(piece)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasUnlock(ServerPlayer player, String boneId) {
        if (player == null || boneId == null || boneId.isBlank()) {
            return false;
        }
        if (CharacterServicesAccess.bypassCost(player)) {
            return true;
        }
        expandLegacyUnlocks(player);
        String bone = boneId.trim().toLowerCase(Locale.ROOT);
        String race = DmzProgression.race(player);
        if (isNativeForRace(race, bone)) {
            return true;
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        Set<String> unlocked = rec.unlockedHeadBones;
        if (unlocked == null) {
            return false;
        }
        for (String u : unlocked) {
            if (u != null && bone.equals(u.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBoneAllowed(ServerPlayer player, String boneId) {
        if (player == null || boneId == null || boneId.isBlank()) {
            return false;
        }
        String raw = boneId.trim().toLowerCase(Locale.ROOT);
        if (HeadPartPieces.isPureAtomic(raw)) {
            for (String piece : HeadPartPieces.fragments(raw)) {
                if (!CosmeticHeadBoneCatalog.isKnown(piece) || !hasUnlock(player, piece)) {
                    return false;
                }
            }
            return true;
        }
        if (raw.contains("+")) {
            return false;
        }
        return CosmeticHeadBoneCatalog.isKnown(raw) && hasUnlock(player, raw);
    }

    /**
     * Pieces of {@code token} this player may wear, in canonical order.
     * Unknown fragments (including a truncated combo piece) are dropped.
     */
    public static String ownedPiecesToken(ServerPlayer player, String token) {
        if (player == null || token == null || token.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String piece : HeadPartPieces.fragments(token)) {
            if (piece.contains("+")) {
                continue;
            }
            if (CosmeticHeadBoneCatalog.isKnown(piece) && hasUnlock(player, piece)) {
                kept.add(piece);
            }
        }
        return HeadPartPieces.join(HeadPartPieces.onePerGroup(kept));
    }

    /**
     * Pieces to restore after a race change. A worn {@code hair} fragment is kept even when the
     * new race does not list it, and is recorded as unlocked so a later sync does not delete it.
     * Race-specific hair rules apply when the player equips a style, not by wiping the old one.
     */
    public static String piecesKeptAcrossRaceChange(ServerPlayer player, String token) {
        String owned = ownedPiecesToken(player, token);
        if (player == null || token == null || token.isBlank()) {
            return owned;
        }
        boolean hadHair = false;
        for (String piece : HeadPartPieces.fragments(token)) {
            if ("hair".equals(piece)) {
                hadHair = true;
                break;
            }
        }
        if (!hadHair) {
            return owned;
        }
        rememberHairUnlock(player);
        List<String> parts = new ArrayList<>(HeadPartPieces.fragments(owned));
        if (!parts.contains("hair")) {
            parts.add("hair");
        }
        return HeadPartPieces.join(parts);
    }

    private static void rememberHairUnlock(ServerPlayer player) {
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        if (rec.unlockedHeadBones == null) {
            rec.unlockedHeadBones = new LinkedHashSet<>();
        }
        if (rec.unlockedHeadBones.add("hair")) {
            CharacterServicesStore.get().markDirty();
            CharacterServicesStore.get().save();
        }
    }

    public static String wornLabel(String token) {
        List<String> parts = HeadPartPieces.fragments(HeadPartPieces.join(HeadPartPieces.fragments(token)));
        if (parts.isEmpty()) {
            return token == null || token.isBlank() ? "none" : CosmeticHeadBoneCatalog.prettyId(token);
        }
        StringBuilder sb = new StringBuilder();
        for (String piece : parts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            CosmeticHeadBoneCatalog.Entry entry = CosmeticHeadBoneCatalog.get(piece);
            sb.append(entry == null ? CosmeticHeadBoneCatalog.prettyId(piece) : entry.displayName());
        }
        return sb.toString();
    }

    public static Set<String> allowedBoneIds(ServerPlayer player) {
        Set<String> out = new HashSet<>();
        for (CosmeticHeadBoneCatalog.Entry e : CosmeticHeadBoneCatalog.all()) {
            if (hasUnlock(player, e.id())) {
                out.add(e.id());
            }
        }
        return out;
    }

    public static String activeBone(ServerPlayer player) {
        Character ch = DmzProgression.character(player);
        if (ch == null) {
            return "";
        }
        try {
            String raw = ch.getActiveHeadBone();
            return raw == null ? "" : raw.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Persisted cosmetic choice (survives race change unlock list; re-applied after forms). */
    public static String equippedBone(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        String stored = rec.equippedHeadBone;
        return stored == null ? "" : stored.trim();
    }

    public static void persistEquippedBone(ServerPlayer player, String boneId) {
        if (player == null) {
            return;
        }
        String bone = boneId == null ? "" : boneId.trim().toLowerCase(Locale.ROOT);
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        rec.equippedHeadBone = bone;
        CharacterServicesStore.get().markDirty();
        // World sync: headBones rides the same parts payload (bodyPartsSync clients only).
        com.dbzlegacy.adaptivedifficulty.net.gui.LmGuiNetwork.sendModelSync(
                player, com.dbzlegacy.adaptivedifficulty.net.gui.GuiModelSyncPacket.REASON_EQUIP);
    }

    /**
     * DMZ often clears or resets {@code activeHeadBone} when transforming. Restore the player's
     * chosen part on the next server tick so it runs after DMZ applies the new form mesh.
     */
    public static void scheduleReapplyAfterFormChange(ServerPlayer player) {
        if (player == null || !persistThroughFormsEnabled()) {
            return;
        }
        var server = player.m_20194_();
        Runnable task = () -> reapplyAfterFormChange(player);
        if (server != null) {
            server.execute(task);
        } else {
            task.run();
        }
    }

    /**
     * @return true when character data was updated and clients were synced
     */
    public static boolean reapplyAfterFormChange(ServerPlayer player) {
        if (player == null || !persistThroughFormsEnabled()) {
            return false;
        }
        String want = resolveEquippedBone(player);
        if (want.isEmpty() || !isBoneAllowed(player, want)) {
            return false;
        }
        String current = activeBone(player);
        if (want.equalsIgnoreCase(current)) {
            return false;
        }
        Character ch = DmzProgression.character(player);
        if (ch == null) {
            return false;
        }
        try {
            ch.setActiveHeadBone(want);
            RaceHeadBoneSync.syncClient(player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean persistThroughFormsEnabled() {
        return CharacterServicesConfig.get().headBoneShop.persistThroughForms;
    }

    private static String resolveEquippedBone(ServerPlayer player) {
        if (isPreviewing(player)) {
            String stored = equippedBone(player);
            return stored.isEmpty() ? "" : ownedPiecesToken(player, stored);
        }
        String stored = equippedBone(player);
        if (!stored.isEmpty()) {
            String kept = ownedPiecesToken(player, stored);
            if (!kept.equalsIgnoreCase(stored)) {
                persistEquippedBone(player, kept);
            }
            return kept;
        }
        String active = activeBone(player);
        if (active.isEmpty()) {
            return "";
        }
        String kept = ownedPiecesToken(player, active);
        if (kept.isEmpty()) {
            return "";
        }
        persistEquippedBone(player, kept);
        return kept;
    }

    public static long unlockCost(ServerPlayer player, String boneId) {
        CharacterServicesConfig.HeadBoneShop shop = CharacterServicesConfig.get().headBoneShop;
        if (hasUnlock(player, boneId)) {
            return 0L;
        }
        long base = shop.defaultUnlockCostCopper;
        if (shop.boneCosts != null && boneId != null) {
            Long override = shop.boneCosts.get(boneId.trim().toLowerCase(Locale.ROOT));
            if (override == null) {
                override = shop.boneCosts.get(boneId);
            }
            if (override != null && override >= 0L) {
                base = override;
            }
        }
        return CharacterServicesSystem.payableCost(player, base, shop.levelCostMultiplier);
    }

    public static int pageCount() {
        return HeadPartPieces.PAGES;
    }

    /**
     * GUI rows: {@code boneId\\tdisplay\\tstate\\tcostText\\tgroup\\treplacesName}.
     * State is {@code E} on, {@code U} unlocked, {@code N} included with the race, {@code L} locked.
     * {@code replacesName} is the worn sibling when this group already has a part and does not stack.
     */
    public static List<String> cards(ServerPlayer player, int page) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        int p = Math.max(0, Math.min(page, HeadPartPieces.PAGES - 1));
        Set<String> worn = new HashSet<>(HeadPartPieces.fragments(activeBone(player)));
        String race = DmzProgression.race(player);
        for (String id : HeadPartPieces.pageIds(p)) {
            CosmeticHeadBoneCatalog.Entry e = CosmeticHeadBoneCatalog.get(id);
            if (e == null) {
                continue;
            }
            String state;
            if (worn.contains(id)) {
                state = "E";
            } else if (hasUnlock(player, id)) {
                state = isNativeForRace(race, id) ? "N" : "U";
            } else {
                state = "L";
            }
            String cost = "L".equals(state)
                    ? CharacterServicesSystem.formatCost(unlockCost(player, id))
                    : "";
            String group = HeadPartPieces.raceGroup(id);
            String replaces = "";
            if (!worn.contains(id) && !HeadPartPieces.allowsMultipleInGroup(group)) {
                for (String other : worn) {
                    if (!group.equals(HeadPartPieces.raceGroup(other))) {
                        continue;
                    }
                    CosmeticHeadBoneCatalog.Entry wornEntry = CosmeticHeadBoneCatalog.get(other);
                    replaces = wornEntry == null
                            ? CosmeticHeadBoneCatalog.prettyId(other)
                            : wornEntry.displayName();
                    break;
                }
            }
            out.add(e.id() + "\t" + e.displayName() + "\t" + state + "\t" + cost
                    + "\t" + group + "\t" + replaces);
        }
        return out;
    }

    public static String executeUnlock(ServerPlayer player, String boneId) {
        if (!canUse(player)) {
            return "§cModel customization is unavailable right now. Try again later or ask staff.";
        }
        if (boneId == null || boneId.isBlank()) {
            return "§cPick a look from the list.";
        }
        String bone = boneId.trim().toLowerCase(Locale.ROOT);
        if (!CosmeticHeadBoneCatalog.isKnown(bone)) {
            return "§cThat look is not available. Pick another from the list.";
        }
        if (hasUnlock(player, bone)) {
            return executeEquip(player, bone);
        }
        long cost = CharacterServicesAccess.bypassCost(player) ? 0L : unlockCost(player, bone);
        AncientCoinEconomy.migrateWalletToItems(player);
        if (cost > 0L && !AncientCoinEconomy.canAfford(player, cost)) {
            return "§c" + AncientCoinEconomy.missingText(player, cost);
        }
        if (cost > 0L && !AncientCoinEconomy.charge(player, cost)) {
            return "§c" + AncientCoinEconomy.missingText(player, cost);
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        if (rec.unlockedHeadBones == null) {
            rec.unlockedHeadBones = new LinkedHashSet<>();
        }
        rec.unlockedHeadBones.add(bone);
        CharacterServicesStore.get().markDirty();
        CharacterServicesStore.get().save();
        String equipped = executeEquip(player, bone);
        if (equipped.startsWith("§c")) {
            return equipped;
        }
        CosmeticHeadBoneCatalog.Entry entry = CosmeticHeadBoneCatalog.get(bone);
        String label = entry == null ? CosmeticHeadBoneCatalog.prettyId(bone) : entry.displayName();
        return "§aUnlocked and turned on §f" + label + "§a.";
    }

    /** Shop unlocks live in {@link CharacterServicesStore} and are never removed by race change. */
    public static boolean hasPersistedUnlock(ServerPlayer player, String boneId) {
        if (player == null || boneId == null || boneId.isBlank()) {
            return false;
        }
        expandLegacyUnlocks(player);
        String bone = boneId.trim().toLowerCase(Locale.ROOT);
        if (storedUnlock(player, bone)) {
            return true;
        }
        if (!HeadPartPieces.isPureAtomic(bone)) {
            return false;
        }
        for (String piece : HeadPartPieces.fragments(bone)) {
            if (!storedUnlock(player, piece)) {
                return false;
            }
        }
        return true;
    }

    private static boolean storedUnlock(ServerPlayer player, String bone) {
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        if (rec.unlockedHeadBones == null) {
            return false;
        }
        for (String u : rec.unlockedHeadBones) {
            if (u != null && bone.equals(u.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * After race change: keep purchased unlocks and re-apply the previous part when still valid
     * for the new race (cross-race cosmetics stay usable).
     */
    public static void reapplyHeadBoneAfterRaceChange(ServerPlayer player, String priorActiveBone) {
        if (player == null) {
            return;
        }
        discardPreview(player);
        String prior = priorActiveBone == null ? "" : priorActiveBone.trim();
        String bone = piecesKeptAcrossRaceChange(player, prior);
        if (!bone.isEmpty() && isBoneAllowed(player, bone)) {
            Character ch = DmzProgression.character(player);
            if (ch != null) {
                try {
                    ch.setActiveHeadBone(bone);
                    persistEquippedBone(player, bone);
                    RaceHeadBoneSync.syncClient(player);
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
        if (RaceHeadBoneSync.syncCharacter(player)) {
            RaceHeadBoneSync.syncClient(player);
        }
    }

    /** First head bone in this race's config (DMZ default for that race). */
    public static String raceDefaultHeadBoneId(ServerPlayer player) {
        String race = DmzProgression.race(player);
        List<String> bones = DmzContentDiscovery.headBonesForRaceOrdered(race);
        for (String bone : bones) {
            if (bone == null || bone.isBlank()) {
                continue;
            }
            String id = bone.trim().toLowerCase(Locale.ROOT);
            if (id.contains("+")) {
                String joined = HeadPartPieces.join(HeadPartPieces.fragments(id));
                if (!joined.isEmpty()) {
                    return joined;
                }
                continue;
            }
            return id;
        }
        return "hair";
    }

    /**
     * Minimal look: {@code hair} when the race supports it, otherwise clear extra head bone
     * (empty — DMZ uses base head without add-on parts).
     */
    public static String unequipHeadBoneId(ServerPlayer player) {
        String race = DmzProgression.race(player);
        List<String> bones = DmzContentDiscovery.headBonesForRaceOrdered(race);
        for (String bone : bones) {
            if ("hair".equals(bone)) {
                return "hair";
            }
        }
        return "";
    }

    public static String executeRaceDefaultHeadBone(ServerPlayer player) {
        if (!canUse(player)) {
            return "§cModel customization is unavailable right now. Try again later or ask staff.";
        }
        String bone = raceDefaultHeadBoneId(player);
        return applyHeadBoneDirect(player, bone, "§aSet race default look: §f");
    }

    public static String executeUnequipHeadBone(ServerPlayer player) {
        if (!canUse(player)) {
            return "§cModel customization is unavailable right now. Try again later or ask staff.";
        }
        String bone = unequipHeadBoneId(player);
        if (bone.isEmpty()) {
            return applyHeadBoneDirect(player, "", "§aCleared extra head parts.");
        }
        return applyHeadBoneDirect(player, bone, "§aCleared extras - now §f");
    }

    private static String applyHeadBoneDirect(ServerPlayer player, String bone, String prefix) {
        Character ch = DmzProgression.character(player);
        if (ch == null) {
            return "§cYour character is still loading. Wait a moment and try again.";
        }
        discardPreview(player);
        try {
            String normalized = bone == null ? "" : bone.trim().toLowerCase(Locale.ROOT);
            ch.setActiveHeadBone(normalized);
            persistEquippedBone(player, normalized);
            RaceHeadBoneSync.syncClient(player);
            if (prefix.contains("Unequipped extra")) {
                return prefix;
            }
            String label = wornLabel(normalized);
            if (label.isBlank()) {
                label = normalized.isBlank() ? "none" : normalized;
            }
            return prefix + label + "§a.";
        } catch (Throwable t) {
            return "§cCould not update your look. Try again or ask staff.";
        }
    }

    public static String executeEquip(ServerPlayer player, String boneId) {
        if (!canUse(player)) {
            return "§cModel customization is unavailable right now. Try again later or ask staff.";
        }
        if (boneId == null || boneId.isBlank()) {
            return "§cPick a look from the list.";
        }
        String bone = boneId.trim().toLowerCase(Locale.ROOT);
        if (!isBoneAllowed(player, bone)) {
            long cost = unlockCost(player, bone);
            return "§cUnlock §f" + CosmeticHeadBoneCatalog.prettyId(bone)
                    + " §cfirst for §f" + CharacterServicesSystem.formatCost(cost)
                    + " §cAncient Coins, then turn it on.";
        }
        restorePreview(player);
        List<String> next = new ArrayList<>(HeadPartPieces.fragments(activeBone(player)));
        String group = HeadPartPieces.raceGroup(bone);
        if (!HeadPartPieces.allowsMultipleInGroup(group)) {
            next.removeIf(part -> group.equals(HeadPartPieces.raceGroup(part)));
        }
        if (!next.contains(bone)) {
            next.add(bone);
        }
        CosmeticHeadBoneCatalog.Entry entry = CosmeticHeadBoneCatalog.get(bone);
        String label = entry == null ? CosmeticHeadBoneCatalog.prettyId(bone) : entry.displayName();
        return applyJoined(player, HeadPartPieces.join(next), "§aTurned on §f" + label + "§a.");
    }

    /**
     * Head-bone token the player would wear after turning {@code boneId} on: the same group rule
     * as {@link #executeEquip}. Used for the confirm-page preview (4.7.46); stores nothing.
     */
    public static String tokenWith(ServerPlayer player, String boneId) {
        String bone = boneId == null ? "" : boneId.trim().toLowerCase(Locale.ROOT);
        List<String> next = new ArrayList<>(HeadPartPieces.fragments(activeBone(player)));
        if (bone.isEmpty()) {
            return HeadPartPieces.join(next);
        }
        String group = HeadPartPieces.raceGroup(bone);
        if (!HeadPartPieces.allowsMultipleInGroup(group)) {
            next.removeIf(part -> group.equals(HeadPartPieces.raceGroup(part)));
        }
        if (!next.contains(bone)) {
            next.add(bone);
        }
        return HeadPartPieces.join(next);
    }

    /** Turn one atomic part on (paying if it is locked) or off. Other worn parts stay. */
    public static String executeToggle(ServerPlayer player, String boneId) {
        if (!canUse(player)) {
            return "§cModel customization is unavailable right now. Try again later or ask staff.";
        }
        if (boneId == null || boneId.isBlank()) {
            return "§cPick a look from the list.";
        }
        String bone = boneId.trim().toLowerCase(Locale.ROOT);
        if (!HeadPartPieces.isAtomic(bone) || CosmeticHeadBoneCatalog.get(bone) == null) {
            return "§cThat look is not available. Pick another from the list.";
        }
        restorePreview(player);
        boolean on = HeadPartPieces.fragments(activeBone(player)).contains(bone);
        if (on) {
            List<String> next = new ArrayList<>(HeadPartPieces.fragments(activeBone(player)));
            next.remove(bone);
            CosmeticHeadBoneCatalog.Entry entry = CosmeticHeadBoneCatalog.get(bone);
            String label = entry == null ? CosmeticHeadBoneCatalog.prettyId(bone) : entry.displayName();
            return applyJoined(player, HeadPartPieces.join(next), "§aTurned off §f" + label + "§a.");
        }
        if (!hasUnlock(player, bone)) {
            return executeUnlock(player, bone);
        }
        return executeEquip(player, bone);
    }

    /**
     * A purchased combo such as {@code horns1+antennas2+ma} becomes the atomic pieces inside it.
     * The truncated fragment is dropped so the purchase is not lost.
     */
    private static void expandLegacyUnlocks(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CharacterServicesStore.PlayerRecord rec =
                CharacterServicesStore.get().record(player.m_20148_().toString());
        if (rec.unlockedHeadBones == null || rec.unlockedHeadBones.isEmpty()) {
            return;
        }
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (String stored : rec.unlockedHeadBones) {
            if (stored == null || stored.isBlank()) {
                continue;
            }
            String id = stored.trim().toLowerCase(Locale.ROOT);
            if (id.contains("+")) {
                for (String piece : HeadPartPieces.fragments(id)) {
                    if (HeadPartPieces.isAtomic(piece)) {
                        next.add(piece);
                    }
                }
            } else {
                next.add(id);
            }
        }
        if (next.equals(rec.unlockedHeadBones)) {
            return;
        }
        rec.unlockedHeadBones = next;
        CharacterServicesStore.get().markDirty();
        CharacterServicesStore.get().save();
    }

    private static String applyJoined(ServerPlayer player, String token, String message) {
        Character ch = DmzProgression.character(player);
        if (ch == null) {
            return "§cYour character is still loading. Wait a moment and try again.";
        }
        discardPreview(player);
        try {
            String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            ch.setActiveHeadBone(normalized);
            persistEquippedBone(player, normalized);
            RaceHeadBoneSync.syncClient(player);
            return message;
        } catch (Throwable t) {
            return "§cCould not update your look. Try again or ask staff.";
        }
    }

    /** Visual only. Does not write {@code equippedHeadBone}. */
    private static String applyActiveOnly(ServerPlayer player, String bone) {
        Character ch = DmzProgression.character(player);
        if (ch == null) {
            return "§cYour character is still loading. Wait a moment and try again.";
        }
        try {
            String normalized = bone == null ? "" : bone.trim().toLowerCase(Locale.ROOT);
            ch.setActiveHeadBone(normalized);
            RaceHeadBoneSync.syncClient(player);
            return "§7Restored your previous look.";
        } catch (Throwable t) {
            return "§cCould not restore your previous look. Try again or ask staff.";
        }
    }
}
