package com.dbzlegacy.adaptivedifficulty.title;

import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.mutation.MutationType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Unlock / equip / mastery / Title Score for the secondary title progression layer.
 */
public final class TitleSystem {
    /** Elite Hunter mastery kill thresholds (I..V). */
    private static final long[] ELITE_MASTERY = {25L, 100L, 500L, 2_500L, 10_000L};
    /** Boss Slayer mastery boss kill thresholds (I..V). */
    private static final long[] BOSS_MASTERY = {10L, 40L, 150L, 600L, 2_500L};
    /** Ascendant mastery uses combined T7 elite+boss progress. */
    private static final long[] ASCENDANT_MASTERY = {5L, 20L, 75L, 250L, 1_000L};

    private TitleSystem() {}

    public static List<String> syncTierTitles(ServerPlayer player, boolean announce) {
        List<String> unlocked = new ArrayList<>();
        if (player == null) {
            return unlocked;
        }
        DifficultyCache.refresh(player);
        PlayerDifficultyData data = DifficultyCache.data(player);
        boolean dirty = data.normalizeTitles();
        int level = DmzProgression.tierScalingDmzLevel(player);
        int prestige = DmzProgression.prestige(player);
        for (DifficultyTitle title : DifficultyTitle.values()) {
            if (title.kind != DifficultyTitle.Kind.TIER || title.unlockTier == null) {
                continue;
            }
            // Permanent unlock from DMZ level OR prestige — not active tier (no regress on lower).
            if (level < title.requiredDmzLevel && prestige < title.requiredPrestige) {
                continue;
            }
            if (data.unlockTitle(title.id)) {
                unlocked.add(title.display);
                dirty = true;
            }
        }
        dirty |= syncMastery(player, false);
        dirty |= syncChallengeTitles(player, false);
        dirty |= claimScoreMilestones(player, announce);
        if (dirty) {
            DifficultyCache.save(player);
            data.titleProgress().invalidateScore();
        }
        if (announce) {
            for (String name : unlocked) {
                player.m_213846_(Component.m_237113_(
                        com.dbzlegacy.adaptivedifficulty.util.LmChat.ok(
                                "Difficulty", "Title unlocked: §e" + name)));
            }
        }
        return unlocked;
    }

    public static void maybeUnlockCombatTitle(
            ServerPlayer killer, DifficultySnapshot snap, boolean elite, boolean boss
    ) {
        if (killer == null || snap == null) {
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(killer);
        data.normalizeTitles();
        boolean dirty = false;
        if (boss && snap.activeTier >= 5 && data.unlockTitle(DifficultyTitle.BOSS_SLAYER.id)) {
            dirty = true;
            killer.m_213846_(Component.m_237113_("§6✦ Title unlocked: §eBoss Slayer"));
        }
        if (elite && snap.activeTier >= 6 && data.unlockTitle(DifficultyTitle.ELITE_HUNTER.id)) {
            dirty = true;
            killer.m_213846_(Component.m_237113_("§6✦ Title unlocked: §eElite Hunter"));
        }
        dirty |= tryUnlockAscendant(killer, true);
        dirty |= syncMastery(killer, true);
        dirty |= syncChallengeTitles(killer, true);
        dirty |= claimScoreMilestones(killer, true);
        if (dirty) {
            DifficultyCache.save(killer);
            data.titleProgress().invalidateScore();
        }
        syncTierTitles(killer, true);
    }

    public static boolean tryUnlockAscendant(ServerPlayer player, boolean announce) {
        if (player == null) {
            return false;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        TitleProgress p = data.titleProgress();
        // Ascendant: ever unlocked T7 (Buy ladder), not currently holding it.
        boolean ready = data.hasUnlockedTier(7)
                && p.elitesT7() >= 100L
                && p.bossesT7() >= 25L
                && p.killsNoDeath() >= 100L
                && p.mutationsSeen().size() >= MutationType.values().length;
        if (!ready) {
            return false;
        }
        if (!data.unlockTitle(DifficultyTitle.ASCENDANT.id)) {
            return false;
        }
        if (announce) {
            player.m_213846_(Component.m_237113_(
                    com.dbzlegacy.adaptivedifficulty.util.LmChat.ok(
                            "Difficulty", "Title unlocked: §eAscendant")));
        }
        return true;
    }

    public static boolean syncMastery(ServerPlayer player, boolean announce) {
        if (player == null) {
            return false;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        TitleProgress progress = data.titleProgress();
        boolean dirty = false;
        dirty |= applyMasteryLadder(player, DifficultyTitle.ELITE_HUNTER, progress.elitesKilled(),
                ELITE_MASTERY, announce);
        dirty |= applyMasteryLadder(player, DifficultyTitle.BOSS_SLAYER, progress.bossesKilled(),
                BOSS_MASTERY, announce);
        long ascProgress = Math.min(progress.elitesT7(), progress.bossesT7() * 4L);
        dirty |= applyMasteryLadder(player, DifficultyTitle.ASCENDANT, ascProgress,
                ASCENDANT_MASTERY, announce);
        return dirty;
    }

    private static boolean applyMasteryLadder(
            ServerPlayer player, DifficultyTitle title, long counter,
            long[] thresholds, boolean announce
    ) {
        if (!has(player, title)) {
            return false;
        }
        TitleProgress progress = DifficultyCache.data(player).titleProgress();
        int current = progress.masteryLevel(title.id);
        int target = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (counter >= thresholds[i]) {
                target = i + 1;
            }
        }
        if (target <= current) {
            return false;
        }
        progress.setMasteryLevel(title.id, target);
        if (announce) {
            player.m_213846_(Component.m_237113_(
                    "§6✦ Title mastery: §e" + title.masteryDisplay(target)));
        }
        return true;
    }

    public static boolean syncChallengeTitles(ServerPlayer player, boolean announce) {
        if (player == null) {
            return false;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        TitleProgress p = data.titleProgress();
        boolean dirty = false;
        dirty |= unlockChallenge(player, DifficultyTitle.MUTATION_HUNTER,
                p.mutationsSeen().size() >= MutationType.values().length, announce);
        dirty |= unlockChallenge(player, DifficultyTitle.UNTOUCHABLE,
                p.killsT6NoDeath() >= 100L, announce);
        dirty |= unlockChallenge(player, DifficultyTitle.IMMORTAL,
                p.killsNoDeath() >= 1_000L, announce);
        dirty |= unlockChallenge(player, DifficultyTitle.COIN_LORD,
                p.copperFromKills() >= 1_000_000L, announce);
        dirty |= unlockChallenge(player, DifficultyTitle.WORLD_BREAKER,
                p.killsT7() >= 10_000L, announce);
        dirty |= unlockChallenge(player, DifficultyTitle.SURVIVOR,
                p.playSecondsWithTier() >= 86_400L, announce);
        return dirty;
    }

    private static boolean unlockChallenge(
            ServerPlayer player, DifficultyTitle title, boolean ready, boolean announce
    ) {
        if (!ready) {
            return false;
        }
        if (!DifficultyCache.data(player).unlockTitle(title.id)) {
            return false;
        }
        if (announce) {
            player.m_213846_(Component.m_237113_(
                    "§" + title.rarity.color + "✦ Challenge title: §e" + title.display));
        }
        return true;
    }

    public static int computeTitleScore(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        TitleProgress progress = data.titleProgress();
        if (progress.cachedScoreOrNeg() >= 0) {
            return progress.cachedScoreOrNeg();
        }
        int score = 0;
        for (String id : data.getTitles()) {
            DifficultyTitle title = DifficultyTitle.byId(id);
            if (title == null) {
                continue;
            }
            score += title.scorePoints;
            int mastery = progress.masteryLevel(id);
            if (mastery > 0) {
                score += 10 * mastery; // I=+10 … V=+50
            }
        }
        progress.setCachedScore(score);
        return score;
    }

    public static boolean claimScoreMilestones(ServerPlayer player, boolean announce) {
        if (player == null) {
            return false;
        }
        int score = computeTitleScore(player);
        TitleProgress progress = DifficultyCache.data(player).titleProgress();
        boolean dirty = false;
        for (TitleScoreRewards reward : TitleScoreRewards.ordered()) {
            if (score < reward.scoreRequired || progress.hasClaimedMilestone(reward.scoreRequired)) {
                continue;
            }
            if (progress.claimMilestone(reward.scoreRequired)) {
                dirty = true;
                if (announce) {
                    player.m_213846_(Component.m_237113_(
                            "§6✦ Title Score " + reward.scoreRequired + ": §e" + reward.display));
                    player.m_213846_(Component.m_237113_("§7" + reward.tip));
                }
            }
        }
        return dirty;
    }

    public static boolean has(ServerPlayer player, DifficultyTitle title) {
        if (player == null || title == null) {
            return false;
        }
        return DifficultyCache.data(player).hasTitle(title.id);
    }

    public static String activeId(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        return DifficultyCache.data(player).getActiveTitle();
    }

    public static String activeDisplay(ServerPlayer player) {
        if (player == null) {
            return "None";
        }
        DifficultyTitle title = DifficultyTitle.byId(activeId(player));
        if (title == null) {
            return "None";
        }
        int mastery = DifficultyCache.data(player).titleProgress().masteryLevel(title.id);
        return title.masteryDisplay(mastery);
    }

    public static List<String> unlockedDisplays(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        TitleProgress progress = DifficultyCache.data(player).titleProgress();
        for (String id : DifficultyCache.data(player).getTitles()) {
            DifficultyTitle title = DifficultyTitle.byId(id);
            if (title == null) {
                out.add(id);
            } else {
                out.add(title.masteryDisplay(progress.masteryLevel(id)));
            }
        }
        return out;
    }

    public static boolean equip(ServerPlayer player, String titleId) {
        if (player == null) {
            return false;
        }
        DifficultyTitle title = DifficultyTitle.byId(titleId);
        if (title == null || !has(player, title)) {
            return false;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        data.setActiveTitle(title.id);
        DifficultyCache.save(player);
        return true;
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        data.setActiveTitle("");
        DifficultyCache.save(player);
    }

    /** Record a qualifying Adaptive kill for counters / mastery / challenges. */
    public static void noteKill(
            ServerPlayer killer,
            DifficultySnapshot snap,
            boolean elite,
            boolean boss,
            MutationType mutation,
            boolean personalOn,
            boolean solo,
            long copperValue
    ) {
        if (killer == null || snap == null) {
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(killer);
        if (personalOn) {
            // 4.7.80 weekly goal: kills with difficulty on.
            com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.note(killer.m_20148_(), com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals.TIER_KILLS, 1);
        }
        data.titleProgress().noteKill(
                snap.activeTier,
                elite,
                boss,
                mutation != null,
                mutation == null ? null : mutation.name(),
                personalOn,
                solo,
                copperValue
        );
        boolean dirty = syncMastery(killer, true);
        dirty |= tryUnlockAscendant(killer, true);
        dirty |= syncChallengeTitles(killer, true);
        dirty |= claimScoreMilestones(killer, true);
        if (dirty) {
            DifficultyCache.save(killer);
        } else {
            // Still persist counters periodically via save on kill.
            DifficultyCache.save(killer);
        }
    }

    public static void noteDeath(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        data.titleProgress().noteDeath();
        DifficultyCache.save(player);
    }
}
