package com.dbzlegacy.adaptivedifficulty.service;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.progression.Momentum;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeCharacter;
import com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler;
import com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * "Try one fight free" (LM 4.7.59): a tier the player meets the requirement for, for 5 minutes, at no cost;
 * then the previous tier comes back by itself.
 *
 * <ul>
 *   <li>One trial per tier per character (the used tiers live in the character's LM record).</li>
 *   <li>The running trial lives on the account, like the active tier it borrows: {@code tier}, the tier it
 *       replaced ({@code prev}, 0 = none) and when it ends.</li>
 *   <li>Any tier change the player makes during the trial (buy, switch down, clear) ends it without a
 *       revert: the player chose. A trial whose tier is no longer active when it ends reverts nothing.</li>
 *   <li>Momentum banks normally during a trial.</li>
 * </ul>
 */
public final class DifficultyTrial {
    private DifficultyTrial() {}

    public static final long TRIAL_MILLIS = 5L * 60L * 1000L;
    /** Running trial, on the account record. */
    static final String KEY_ACTIVE = "lm_difficulty_trial";
    /** Tiers already tried, on the character record. */
    static final String KEY_USED = "lm_difficulty_trials_used";

    /** A running trial. */
    public record Active(int tier, int prev, long endsAt) {}

    // ------------------------------------------------------------------ pure rules (self-tested)

    /** The tier to put back when a trial of {@code trialTier} (which replaced {@code prev}) ends. */
    public static int revertTo(int currentActive, int trialTier, int prev) {
        return currentActive == trialTier ? Math.max(0, prev) : currentActive;
    }

    /** Why a trial cannot start, or null when it can. */
    public static String blockReason(int tier, int active, boolean personalOn, boolean eligible, boolean used,
            boolean running) {
        if (!personalOn) {
            return "Turn on personal difficulty first.";
        }
        if (running) {
            return "A free trial is already running.";
        }
        if (tier == active) {
            return "T" + tier + " is already your tier.";
        }
        if (!eligible) {
            return "Free trials open once you meet this tier's requirement.";
        }
        if (used) {
            return "You already tried T" + tier + " on this character.";
        }
        return null;
    }

    // ------------------------------------------------------------------ live game

    /** The running trial on this account, or null. */
    public static Active active(ServerPlayer player) {
        CompoundTag root = LmPlayerStore.root(player);
        if (player == null || !root.m_128441_(KEY_ACTIVE)) {
            return null;
        }
        CompoundTag t = root.m_128469_(KEY_ACTIVE);
        return new Active(t.m_128451_("tier"), t.m_128451_("prev"), t.m_128454_("endsAt"));
    }

    /** Millis left on the running trial (0 when none). */
    public static long remainingMillis(ServerPlayer player) {
        Active a = active(player);
        return a == null ? 0L : Math.max(0L, a.endsAt() - System.currentTimeMillis());
    }

    public static boolean used(ServerPlayer player, int tier) {
        CompoundTag root = LmPlayerStore.root(PrestigeCharacter.id(player));
        for (int t : root.m_128465_(KEY_USED)) {
            if (t == tier) {
                return true;
            }
        }
        return false;
    }

    private static void markUsed(ServerPlayer player, int tier) {
        java.util.UUID character = PrestigeCharacter.id(player);
        CompoundTag root = LmPlayerStore.root(character);
        List<Integer> list = new ArrayList<>();
        for (int t : root.m_128465_(KEY_USED)) {
            list.add(t);
        }
        if (!list.contains(tier)) {
            list.add(tier);
        }
        root.m_128385_(KEY_USED, list.stream().mapToInt(Integer::intValue).toArray());
        LmPlayerStore.flush(character);
    }

    /** Why this player cannot start a trial of {@code tier} right now, or null. */
    public static String blockReason(ServerPlayer player, int tier) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        UnlockTier ut = UnlockTier.byId(tier);
        if (data == null || ut == null) {
            return "Unknown tier.";
        }
        return blockReason(tier, data.getActiveTier(), data.isPersonalEnabled(), UnlockSystem.isEligible(player, ut),
                used(player, tier), active(player) != null);
    }

    /** {@code do:trial:<t>}: start a 5-minute free trial. */
    static DifficultyActions.Result start(ServerPlayer player, int tier, String page) {
        String returnPage = page == null || page.isBlank() ? "tiers" : page;
        String block = blockReason(player, tier);
        UnlockTier ut = UnlockTier.byId(tier);
        if (block != null || ut == null) {
            DifficultyActions.openGui(player, returnPage);
            return DifficultyActions.Result.fail(block == null ? "Unknown tier." : block);
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        int prev = data.getActiveTier();
        DifficultyActions.applyTier(data, player, ut);
        if (data.getActiveTier() != tier) {
            DifficultyActions.openGui(player, returnPage);
            return DifficultyActions.Result.fail("T" + tier + " could not start. Try again in a moment.");
        }
        CompoundTag t = new CompoundTag();
        t.m_128405_("tier", tier);
        t.m_128405_("prev", prev);
        t.m_128356_("endsAt", System.currentTimeMillis() + TRIAL_MILLIS);
        LmPlayerStore.root(player).m_128365_(KEY_ACTIVE, t);
        markUsed(player, tier);
        com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.note(player.m_20148_(), com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.TRY_TIER, 1);
        LmPlayerStore.flush(player.m_20148_());
        DifficultyActions.openGui(player, "your_tier");
        return DifficultyActions.Result.ok("Free trial: T" + tier + " " + ut.display + " for 5 minutes, no cost. "
                + (prev > 0 ? "You go back to T" + prev + " after." : "Difficulty turns back off after."));
    }

    /** The player changed tier by choice during a trial: keep their choice, drop the trial. */
    public static void endByChoice(ServerPlayer player) {
        if (player != null && LmPlayerStore.root(player).m_128441_(KEY_ACTIVE)) {
            LmPlayerStore.root(player).m_128473_(KEY_ACTIVE);
            LmPlayerStore.flush(player.m_20148_());
        }
    }

    /** Once a second: put back the previous tier for every online player whose trial ran out. */
    public static void pulse(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            Active a = active(player);
            if (a != null && now >= a.endsAt()) {
                finish(player, a);
            }
        }
    }

    private static void finish(ServerPlayer player, Active a) {
        LmPlayerStore.root(player).m_128473_(KEY_ACTIVE);
        LmPlayerStore.flush(player.m_20148_());
        PlayerDifficultyData data = DifficultyCache.data(player);
        int current = data.getActiveTier();
        int back = revertTo(current, a.tier(), a.prev());
        if (back == current) {
            return;
        }
        UnlockTier prev = UnlockTier.byId(back);
        if (back <= 0 || prev == null) {
            data.resetTemporary();
            DifficultyCache.save(player);
            DifficultyCache.refresh(player);
            ScaledMobTracker.releaseAndRevertPlayer(player);
            NearbyMobScaler.processEvictions();
        } else {
            DifficultyActions.applyTier(data, player, prev);
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] free trial of T{} ended for {}; back to {}", AdaptiveDifficultyMod.MOD_ID,
                a.tier(), player.m_7755_().getString(), back <= 0 ? "no tier" : "T" + back);
        DmzRewards.msg(player, UltraStyle.noticeLine("Your free T" + a.tier() + " trial ended. "
                + (back <= 0 ? "Difficulty is back to no tier." : "You are back on T" + back + ".")
                + " Buy it on the Tiers page to keep it."));
    }

    /** Boot self-test of the revert rule and the start checks. Returns problems (empty when fine). */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        if (revertTo(5, 5, 2) != 2 || revertTo(5, 5, 0) != 0) {
            bad.add("an ended trial puts the previous tier back");
        }
        if (revertTo(3, 5, 2) != 3) {
            bad.add("a tier the player picked during the trial stays");
        }
        if (blockReason(5, 2, true, true, false, false) != null) {
            bad.add("an eligible, untried tier can be tried");
        }
        if (blockReason(5, 2, true, true, true, false) == null || blockReason(5, 2, true, false, false, false) == null
                || blockReason(5, 5, true, true, false, false) == null || blockReason(5, 2, false, true, false, false) == null
                || blockReason(5, 2, true, true, false, true) == null) {
            bad.add("used, ineligible, current, difficulty-off and running trials are refused");
        }
        if (TRIAL_MILLIS != 300_000L) {
            bad.add("a trial lasts 5 minutes");
        }
        // Momentum keeps banking during a trial: nothing here touches it.
        if (Momentum.remaining(0L, 0L) != 0L) {
            bad.add("momentum baseline");
        }
        return bad;
    }
}
