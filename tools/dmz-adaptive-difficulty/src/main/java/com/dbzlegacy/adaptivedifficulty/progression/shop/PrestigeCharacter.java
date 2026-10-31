package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.events.players.StatsEvents;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
 * staff override, the favor balance and the confirm timer are kept per character, in the LM player file
 * named by that UUID. Everything else in {@link LmPlayerStore} stays per account. Without Ultra, or
 * with its slots off, the id is the account UUID and nothing changes from 4.7.2.
 * <p>
 * A character's first read copies the account's prestige keys into its own file (once, marked by
 * {@link #KEY_SEEDED}); the account file is left as it was. A character DMZ Ultra creates
 * ({@code CharacterEvent.Born}) starts with no prestige and fresh DragonMineZ stats instead.
 */
public final class PrestigeCharacter {
    /** Character file: the account's prestige keys were copied in (or the character was born fresh). */
    static final String KEY_SEEDED = "lm_prestige_seeded";
    /** Account file: births LM has handled, slot number to the slot's creation time. */
    static final String KEY_BIRTHS = "lm_character_births";
    /** Account file: the account's prestige keys from before slot 0 was created anew. */
    static final String KEY_ACCOUNT_SEED = "lm_prestige_account_seed";
    /** The favor mirror inside the account's {@code lm_progression} bag. */
    private static final String BAG_POINTS = "prestige_points";

    /** Every prestige key that lives per character. */
    static final List<String> KEYS = List.of(
            PrestigeSystem.KEY_HELD,
            PrestigeSystem.KEY_TOTAL,
            PrestigeSystem.KEY_TOTAL_STAFF_OVERRIDE,
            PrestigePointsSystem.ROOT_POINTS);

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

    /** The active character's prestige record. Seeds it from the account on first use. */
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
            CompoundTag accountRoot = LmPlayerStore.root(account);
            seed(accountSeed(accountRoot, bagPoints(accountRoot)), character);
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] prestige record for {}'s character {} started from the account's",
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
     * The account's prestige keys a character starts from: the snapshot kept when slot 0 was created
     * anew, else the account file's own keys. Favor is the larger of the record and the bag mirror,
     * the same rule {@link PrestigePointsSystem#getPoints} uses for the account.
     */
    static CompoundTag accountSeed(CompoundTag accountRoot, long bagPoints) {
        if (accountRoot.m_128425_(KEY_ACCOUNT_SEED, 10)) { // TAG_COMPOUND
            return accountRoot.m_128469_(KEY_ACCOUNT_SEED);
        }
        CompoundTag out = new CompoundTag();
        for (String key : KEYS) {
            Tag value = accountRoot.m_128423_(key);
            if (value != null) {
                out.m_128365_(key, value.m_6426_());
            }
        }
        long points = Math.max(Math.max(0L, bagPoints),
                LmPlayerStore.getLong(accountRoot, PrestigePointsSystem.ROOT_POINTS, 0L));
        if (points > 0L || out.m_128441_(PrestigePointsSystem.ROOT_POINTS)) {
            out.m_128356_(PrestigePointsSystem.ROOT_POINTS, points);
        }
        return out;
    }

    /** Copy the seed into a character record once. Keys the character already has are kept. */
    static void seed(CompoundTag source, CompoundTag character) {
        if (character.m_128441_(KEY_SEEDED)) {
            return;
        }
        for (String key : KEYS) {
            Tag value = source.m_128423_(key);
            if (value != null && !character.m_128441_(key)) {
                character.m_128365_(key, value.m_6426_());
            }
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

    /** DMZ Ultra moved the player to another character. */
    static void switchedIn(ServerPlayer player, UUID fromCharacter) {
        if (player == null) {
            return;
        }
        if (fromCharacter != null) {
            PrestigeSystem.clearPlayer(fromCharacter);
            LmPlayerStore.flush(fromCharacter);
        }
        // A Born event follows in the same call for a new character: resolve on the next tick so the
        // new record is started fresh before anything reads it.
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        UUID account = player.m_20148_();
        server.execute(() -> {
            ServerPlayer p = server.m_6846_().m_11259_(account);
            if (p != null) {
                PrestigeSystem.clearPlayer(id(p));
                com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                        .scheduleSyncAfterStatsReset(p);
            }
        });
    }

    /**
     * DMZ Ultra created a character and switched the player into it. Only {@code Lifecycle.create}
     * asks for a born switch, right after writing the slot's blank record, so this is a new character.
     */
    static void born(ServerPlayer player, UUID account, int slot, UUID character, long created) {
        if (player == null || !player.m_20148_().equals(account) || !character.equals(id(player))) {
            return; // not the player's active character: never touch anyone else's stats
        }
        CompoundTag accountRoot = LmPlayerStore.root(account);
        if (!claimBirth(accountRoot, slot, created)) {
            return;
        }
        if (account.equals(character) && !accountRoot.m_128441_(KEY_ACCOUNT_SEED)) {
            // Slot 0 shares the account file: keep what other characters still start from.
            accountRoot.m_128365_(KEY_ACCOUNT_SEED, accountSeed(accountRoot, bagPoints(accountRoot)));
        }
        startFresh(root(player));
        PrestigePointsSystem.setPoints(player, 0);
        PrestigeSystem.clearPlayer(character);
        freshStats(player);
        LmPlayerStore.flush(account);
        LmPlayerStore.flush(character);
        AdaptiveDifficultyMod.LOGGER.info(
                "[{}] {} created character slot {}: fresh DragonMineZ stats and no prestige",
                AdaptiveDifficultyMod.MOD_ID, player.m_36316_().getName(), slot + 1);
        com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                .scheduleSyncAfterStatsReset(player);
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

    /** Startup self-test: seeding, a fresh start, and the birth guard, on detached records. */
    public static void selfTest(List<String> broken) {
        CompoundTag account = new CompoundTag();
        account.m_128405_(PrestigeSystem.KEY_HELD, 3);
        account.m_128359_(PrestigeSystem.KEY_TOTAL, "5");
        account.m_128356_(PrestigePointsSystem.ROOT_POINTS, 7L);
        account.m_128359_("lm_mastery_points", "40");
        CompoundTag character = new CompoundTag();
        seed(accountSeed(account, 9L), character);
        if (character.m_128451_(PrestigeSystem.KEY_HELD) != 3
                || !"5".equals(character.m_128461_(PrestigeSystem.KEY_TOTAL))
                || LmPlayerStore.getLong(character, PrestigePointsSystem.ROOT_POINTS, -1L) != 9L
                || !character.m_128471_(KEY_SEEDED) || character.m_128441_("lm_mastery_points")) {
            broken.add("per-character prestige: a character should start from the account's held 3, lifetime 5, "
                    + "favor 9 (the larger of record and bag) and nothing else, got " + character);
        }
        account.m_128405_(PrestigeSystem.KEY_HELD, 8);
        character.m_128405_(PrestigeSystem.KEY_HELD, 4);
        seed(accountSeed(account, 0L), character);
        if (character.m_128451_(PrestigeSystem.KEY_HELD) != 4) {
            broken.add("per-character prestige: seeding twice must not change a character's record");
        }
        CompoundTag own = new CompoundTag();
        own.m_128405_(PrestigeSystem.KEY_HELD, 1);
        seed(accountSeed(account, 0L), own);
        if (own.m_128451_(PrestigeSystem.KEY_HELD) != 1 || !"5".equals(own.m_128461_(PrestigeSystem.KEY_TOTAL))) {
            broken.add("per-character prestige: seeding must keep keys the character already has, got " + own);
        }
        startFresh(character);
        if (character.m_128451_(PrestigeSystem.KEY_HELD) != 0
                || !"0".equals(character.m_128461_(PrestigeSystem.KEY_TOTAL))
                || LmPlayerStore.getLong(character, PrestigePointsSystem.ROOT_POINTS, -1L) != 0L
                || character.m_128441_(PrestigeSystem.KEY_TOTAL_STAFF_OVERRIDE) || !character.m_128471_(KEY_SEEDED)) {
            broken.add("per-character prestige: a new character should hold 0, lifetime 0, favor 0, got " + character);
        }
        if (!claimBirth(account, 1, 100L) || claimBirth(account, 1, 100L) || !claimBirth(account, 1, 200L)
                || !claimBirth(account, 2, 100L)) {
            broken.add("per-character prestige: a birth should be handled once per slot creation");
        }
        CompoundTag snapshot = accountSeed(account, 0L);
        account.m_128365_(KEY_ACCOUNT_SEED, snapshot);
        startFresh(account);
        CompoundTag later = new CompoundTag();
        seed(accountSeed(account, 0L), later);
        if (later.m_128451_(PrestigeSystem.KEY_HELD) != 8) {
            broken.add("per-character prestige: after slot 0 starts fresh, other characters should still start "
                    + "from the account's old prestige, got " + later);
        }
        CompoundTag empty = new CompoundTag();
        seed(accountSeed(new CompoundTag(), 0L), empty);
        if (empty.m_128441_(PrestigeSystem.KEY_HELD) || !empty.m_128471_(KEY_SEEDED)) {
            broken.add("per-character prestige: an account with no prestige should seed nothing, got " + empty);
        }
    }

    private static long bagPoints(CompoundTag accountRoot) {
        CompoundTag bag = accountRoot.m_128469_("lm_progression");
        return LmPlayerStore.getLong(bag, BAG_POINTS, 0L);
    }
}
