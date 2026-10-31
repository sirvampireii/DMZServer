package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.progression.ProgressionData;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.events.players.StatsEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

/**
 * Whose prestige record a player is using (4.7.3).
 * <p>
 * DMZ Ultra 0.4.0 gives one account several characters. Each has its own UUID
 * ({@code Slots.characterId}); slot 0's is the account UUID. Prestige held, lifetime prestiges, the
 * staff override, the favor balance, the confirm timer and every prestige-shop purchase
 * ({@link ProgressionData#shop}) are kept per character, in the LM player file named by that UUID.
 * Everything else in {@link LmPlayerStore} stays per account. Without Ultra, or with its slots off, the
 * id is the account UUID and nothing changes from 4.7.2.
 * <p>
 * Migration: slot 0 is the character that already had the account's prestige and purchases, and it
 * keeps using the account file. Every other character starts at 0 held, 0 lifetime, 0 favor and no
 * purchases (marked by {@link #KEY_SEEDED}), so the totals across characters equal the account's
 * values. A character DMZ Ultra creates ({@code CharacterEvent.Born}) also gets fresh DragonMineZ stats.
 * <p>
 * Switching into a character whose stored record is still blank resets it the same way (4.7.4): Ultra
 * skips loading an empty DragonMineZ part, so without it the player would keep the stats of the
 * character they switched from.
 */
public final class PrestigeCharacter {
    /** Character file: its prestige record was started (at zero). */
    static final String KEY_SEEDED = "lm_prestige_seeded";
    /** Account file: births LM has handled, slot number to the slot's creation time. */
    static final String KEY_BIRTHS = "lm_character_births";
    /** Purchase keys in the {@code lm_progression} bag. */
    private static final String SHOP_PREFIX = "pp_";

    /** Account to the character files opened for it this session (flushed and unloaded with the account). */
    private static final Map<UUID, Set<UUID>> OPEN = new ConcurrentHashMap<>();
    private static volatile boolean slots;

    private PrestigeCharacter() {}

    /** Boot: listen for DMZ Ultra's character switches and births when its slots API is installed. */
    public static void register() {
        try {
            Class.forName("com.dmzultra.slots.api.Slots", false, PrestigeCharacter.class.getClassLoader());
            Class.forName("com.dmzultra.slots.api.CharacterEvent$Born", false, PrestigeCharacter.class.getClassLoader());
        } catch (Throwable absent) {
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] prestige is per account (DMZ Ultra character slots not installed)", AdaptiveDifficultyMod.MOD_ID);
            return;
        }
        try {
            PrestigeCharacterUltra.register();
            slots = true;
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] prestige is per character (DMZ Ultra character slots)", AdaptiveDifficultyMod.MOD_ID);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.error(
                    "[{}] DMZ Ultra character slots could not be hooked; prestige stays per account: {}",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    /** The active character's UUID; the account UUID without Ultra slots (or when they are off). */
    public static UUID id(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        UUID account = player.m_20148_();
        if (!slots) {
            return account;
        }
        try {
            UUID character = PrestigeCharacterUltra.characterId(player);
            return character == null ? account : character;
        } catch (Throwable t) {
            return account;
        }
    }

    /** True when the active character's record is the account file (no slots, or slot 0). */
    public static boolean isAccount(ServerPlayer player) {
        return player == null || player.m_20148_().equals(id(player));
    }

    /** The active character's prestige record. Another character's record starts at zero. */
    public static CompoundTag root(ServerPlayer player) {
        if (player == null) {
            return LmPlayerStore.root((UUID) null);
        }
        UUID account = player.m_20148_();
        UUID id = id(player);
        if (account.equals(id)) {
            return LmPlayerStore.root(account);
        }
        OPEN.computeIfAbsent(account, k -> ConcurrentHashMap.newKeySet()).add(id);
        CompoundTag character = LmPlayerStore.root(id);
        if (!character.m_128441_(KEY_SEEDED)) {
            startEmpty(character);
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] prestige record for {}'s character {} started at zero (slot 0 keeps the account's)",
                    AdaptiveDifficultyMod.MOD_ID, player.m_36316_().getName(), id);
        }
        return character;
    }

    /** A numeric record of the active character; {@code fallback} when missing. */
    public static long getLong(ServerPlayer player, String key, long fallback) {
        return LmPlayerStore.getLong(root(player), key, fallback);
    }

    /** Save every character file opened for this account. */
    public static void flush(ServerPlayer player) {
        Set<UUID> open = player == null ? null : OPEN.get(player.m_20148_());
        if (open != null) {
            open.forEach(LmPlayerStore::flush);
        }
    }

    /** Logout: save and drop every character file opened for this account. */
    public static void unload(ServerPlayer player) {
        Set<UUID> open = player == null ? null : OPEN.remove(player.m_20148_());
        if (open != null) {
            open.forEach(LmPlayerStore::unload);
        }
    }

    /**
     * First use of a character other than slot 0: 0 held, 0 lifetime, 0 favor. Nothing is copied from
     * the account (slot 0 keeps it all), and keys the character already has are kept.
     */
    static void startEmpty(CompoundTag character) {
        if (character.m_128441_(KEY_SEEDED)) {
            return;
        }
        if (!character.m_128441_(PrestigeSystem.KEY_HELD)) {
            character.m_128405_(PrestigeSystem.KEY_HELD, 0);
        }
        if (!character.m_128441_(PrestigeSystem.KEY_TOTAL)) {
            character.m_128359_(PrestigeSystem.KEY_TOTAL, "0");
        }
        if (!character.m_128441_(PrestigePointsSystem.ROOT_POINTS)) {
            character.m_128356_(PrestigePointsSystem.ROOT_POINTS, 0L);
        }
        character.m_128379_(KEY_SEEDED, true);
    }

    /** A new character's record: nothing held, no lifetime prestiges, no favor. */
    static void startFresh(CompoundTag character) {
        character.m_128405_(PrestigeSystem.KEY_HELD, 0);
        character.m_128359_(PrestigeSystem.KEY_TOTAL, "0");
        character.m_128473_(PrestigeSystem.KEY_TOTAL_STAFF_OVERRIDE);
        character.m_128356_(PrestigePointsSystem.ROOT_POINTS, 0L);
        character.m_128473_(PrestigeSystem.KEY_CONFIRM_UNTIL);
        character.m_128379_(KEY_SEEDED, true);
    }

    /** Drop every prestige-shop purchase ({@code pp_*}) from a bag; other records stay. */
    static void clearPurchases(CompoundTag bag) {
        for (String key : new ArrayList<>(bag.m_128431_())) {
            if (key != null && key.startsWith(SHOP_PREFIX)) {
                bag.m_128473_(key);
            }
        }
    }

    /**
     * Record a birth once per slot creation. Returns false when this slot's creation was already
     * handled, so a repeated event changes nothing.
     */
    static boolean claimBirth(CompoundTag accountRoot, int slot, long created) {
        CompoundTag births = accountRoot.m_128469_(KEY_BIRTHS);
        String key = Integer.toString(slot);
        if (births.m_128441_(key) && births.m_128454_(key) == created) {
            return false;
        }
        births.m_128356_(key, created);
        accountRoot.m_128365_(KEY_BIRTHS, births);
        return true;
    }

    /**
     * DMZ Ultra moved the player to another character. On the next tick (after a Born event in the
     * same switch): reset the character if its stored record is blank, re-sync the Overhaul count from
     * the new character's held, re-apply only that character's purchases (which also drops the
     * special-perk attributes of the one before), and re-check tiers so another character's purchased
     * tiers are revoked.
     * <p>
     * {@code blank} is read from the incoming slot's stored record when the switch happens. Ultra parks
     * a character's record when the player switches away, so a character that has played is never
     * blank here. At creation this reset runs after the Born one; both leave the same blank character.
     */
    static void switchedIn(ServerPlayer player, UUID fromCharacter, UUID toCharacter, int toSlot, boolean blank) {
        if (player == null) {
            return;
        }
        if (fromCharacter != null) {
            PrestigeSystem.clearPlayer(fromCharacter);
            LmPlayerStore.flush(fromCharacter);
        }
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        UUID account = player.m_20148_();
        server.execute(() -> {
            ServerPlayer p = server.m_6846_().m_11259_(account);
            if (p == null) {
                return;
            }
            UUID active = id(p);
            if (resetOnSwitchIn(blank, toCharacter, active)) {
                resetToFresh(p, account, active);
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] {} switched into blank character slot {}: fresh DragonMineZ stats, no prestige, "
                                + "no purchases", AdaptiveDifficultyMod.MOD_ID, p.m_36316_().getName(), toSlot + 1);
            }
            PrestigeSystem.clearPlayer(active);
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .scheduleSyncAfterStatsReset(p);
            try {
                PrestigePointsSystem.reapplyAllShopPurchases(p);
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] purchases could not be re-applied after {}'s character switch: {}",
                        AdaptiveDifficultyMod.MOD_ID, p.m_36316_().getName(), t.toString());
            }
            try {
                var data = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache.data(p);
                if (data != null) {
                    com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem.syncUnlocks(p, data);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Reset on switch-in only when the incoming slot's stored record is blank and the player is still
     * on that character a tick later (a second switch in between leaves everything alone).
     */
    static boolean resetOnSwitchIn(boolean blank, UUID toCharacter, UUID active) {
        return blank && toCharacter != null && toCharacter.equals(active);
    }

    /**
     * DMZ Ultra created a character and switched the player into it. Only {@code Lifecycle.create}
     * asks for a born switch, right after writing the slot's blank record. LM still checks that the
     * slot's stored record is blank and that the slot's creation time is known and new, so nothing can
     * reset a character that already has data.
     */
    static void born(ServerPlayer player, UUID account, int slot, UUID character, Long created, boolean blank) {
        if (player == null || !player.m_20148_().equals(account) || !character.equals(id(player))) {
            return; // not the player's active character: never touch anyone else's stats
        }
        String name = player.m_36316_().getName();
        if (!blank) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] {}'s slot {} was born from a record that is not blank; stats and prestige left as they are",
                    AdaptiveDifficultyMod.MOD_ID, name, slot + 1);
            return;
        }
        if (created == null) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] {}'s slot {} has no creation time in DMZ Ultra; stats and prestige left as they are",
                    AdaptiveDifficultyMod.MOD_ID, name, slot + 1);
            return;
        }
        CompoundTag accountRoot = LmPlayerStore.root(account);
        if (!claimBirth(accountRoot, slot, created)) {
            return;
        }
        resetToFresh(player, account, character);
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] {} created character slot {}: fresh DragonMineZ stats, no prestige, no purchases",
                AdaptiveDifficultyMod.MOD_ID, name, slot + 1);
    }

    /** The active character's prestige, favor and purchases cleared, with fresh DragonMineZ stats. */
    private static void resetToFresh(ServerPlayer player, UUID account, UUID character) {
        startFresh(root(player));
        PrestigePointsSystem.setPoints(player, 0);
        clearPurchases(ProgressionData.shop(player));
        PrestigeSystem.clearPlayer(character);
        freshStats(player);
        LmPlayerStore.flush(account);
        LmPlayerStore.flush(character);
    }

    /** Fresh DragonMineZ stats, the way DMZ Ultra clears a character: new StatsData, base attributes. */
    private static void freshStats(ServerPlayer player) {
        StatsData stats = DmzProgression.stats(player);
        if (stats == null) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] {}'s new character has no DragonMineZ data to reset",
                    AdaptiveDifficultyMod.MOD_ID, player.m_36316_().getName());
            return;
        }
        try {
            var character = stats.getCharacter();
            if (character.hasActiveStackForm()) {
                character.clearActiveStackForm(player);
            }
            if (character.hasActiveForm()) {
                character.clearActiveForm(player);
            }
        } catch (Throwable ignored) {
        }
        stats.copyFrom(new StatsData((Player) player));
        for (RegistryObject<Attribute> stat : List.of(MainAttributes.STRENGTH, MainAttributes.STRIKE_POWER,
                MainAttributes.RESISTANCE, MainAttributes.VITALITY, MainAttributes.KI_POWER, MainAttributes.ENERGY)) {
            try {
                AttributeInstance live = player.m_21051_(stat.get());
                if (live != null) {
                    live.m_22100_(stat.get().m_22082_());
                }
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.warn(
                        "[{}] {}'s {} could not be reset for a new character: {}",
                        AdaptiveDifficultyMod.MOD_ID, player.m_36316_().getName(), stat.getId(), t.toString());
            }
        }
        player.m_6210_();
        try {
            StatsEvents.applyHealthBonus(player);
        } catch (Throwable ignored) {
        }
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    /**
     * Startup self-test: migration totals, a fresh start, purchase clearing, the birth guard and the
     * switch-in reset guard.
     */
    public static void selfTest(List<String> broken) {
        // An account with 3 held, 5 lifetime, 9 favor and two characters besides slot 0.
        CompoundTag account = new CompoundTag();
        account.m_128405_(PrestigeSystem.KEY_HELD, 3);
        account.m_128359_(PrestigeSystem.KEY_TOTAL, "5");
        account.m_128356_(PrestigePointsSystem.ROOT_POINTS, 9L);
        CompoundTag second = new CompoundTag();
        CompoundTag third = new CompoundTag();
        startEmpty(second);
        startEmpty(third);
        long held = account.m_128451_(PrestigeSystem.KEY_HELD) + second.m_128451_(PrestigeSystem.KEY_HELD)
                + third.m_128451_(PrestigeSystem.KEY_HELD);
        long lifetime = Long.parseLong(account.m_128461_(PrestigeSystem.KEY_TOTAL))
                + Long.parseLong(second.m_128461_(PrestigeSystem.KEY_TOTAL))
                + Long.parseLong(third.m_128461_(PrestigeSystem.KEY_TOTAL));
        long favor = LmPlayerStore.getLong(account, PrestigePointsSystem.ROOT_POINTS, 0L)
                + LmPlayerStore.getLong(second, PrestigePointsSystem.ROOT_POINTS, 0L)
                + LmPlayerStore.getLong(third, PrestigePointsSystem.ROOT_POINTS, 0L);
        if (held != 3 || lifetime != 5 || favor != 9 || second.m_128451_(PrestigeSystem.KEY_HELD) != 0
                || !second.m_128471_(KEY_SEEDED)) {
            broken.add("per-character prestige: slot 0 keeps the account's 3 held / 5 lifetime / 9 favor and other "
                    + "characters start at 0, got totals " + held + " / " + lifetime + " / " + favor);
        }
        CompoundTag own = new CompoundTag();
        own.m_128405_(PrestigeSystem.KEY_HELD, 1);
        startEmpty(own);
        own.m_128405_(PrestigeSystem.KEY_HELD, 2);
        startEmpty(own);
        if (own.m_128451_(PrestigeSystem.KEY_HELD) != 2) {
            broken.add("per-character prestige: starting a record must keep keys it already has, got " + own);
        }
        startFresh(second);
        if (second.m_128451_(PrestigeSystem.KEY_HELD) != 0
                || !"0".equals(second.m_128461_(PrestigeSystem.KEY_TOTAL))
                || LmPlayerStore.getLong(second, PrestigePointsSystem.ROOT_POINTS, -1L) != 0L
                || second.m_128441_(PrestigeSystem.KEY_TOTAL_STAFF_OVERRIDE)) {
            broken.add("per-character prestige: a new character should hold 0, lifetime 0, favor 0, got " + second);
        }
        CompoundTag bag = new CompoundTag();
        bag.m_128359_("pp_perm_ultimate", "true");
        bag.m_128356_("pp_skill_kicontrol", 4L);
        bag.m_128359_("pp_tier_3", "true");
        bag.m_128356_("pp_godki_enh", 2L);
        bag.m_128356_("pp_custom_aura", 1L);
        bag.m_128359_("fly_training_level", "3");
        clearPurchases(bag);
        if (bag.m_128431_().size() != 1 || !bag.m_128441_("fly_training_level")) {
            broken.add("per-character prestige: a new character should own no purchases (pp_*), got " + bag);
        }
        if (!claimBirth(account, 1, 100L) || claimBirth(account, 1, 100L) || !claimBirth(account, 1, 200L)
                || !claimBirth(account, 2, 100L)) {
            broken.add("per-character prestige: a birth should be handled once per slot creation");
        }
        UUID fresh = new UUID(0L, 7L);
        UUID other = new UUID(0L, 8L);
        if (!resetOnSwitchIn(true, fresh, fresh) || !resetOnSwitchIn(true, fresh, fresh)
                || resetOnSwitchIn(false, fresh, fresh) || resetOnSwitchIn(true, fresh, other)
                || resetOnSwitchIn(true, null, fresh)) {
            broken.add("per-character prestige: switching into a blank character should reset it every time, "
                    + "and never a populated one or one the player already left");
        }
    }
}
