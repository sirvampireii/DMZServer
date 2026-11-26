package com.dbzlegacy.adaptivedifficulty.rival;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.ConfigPaths;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerPlayer;

/**
 * Seasons, weekly quests, achievements, Hall of Fame, journal, titles.
 * Persisted to {@code config/legacymechanics/progression-v4.json}.
 * <p>Mod JSON only — no CustomNPCs (CNPC) script writes. Rival links stay in rivalry-v4.json.
 */
public final class RivalProgression {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final RivalProgression INSTANCE = new RivalProgression();
    private static final long SEASON_MS = 75L * 24L * 60L * 60L * 1000L;

    private Persist data = fresh();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private long lastSaveAt;

    public static RivalProgression get() {
        return INSTANCE;
    }

    private RivalProgression() {}

    public static Path path() {
        return ConfigPaths.progressionPath();
    }

    public synchronized void load() {
        Path file = path();
        try {
            if (!Files.isRegularFile(file)) {
                data = fresh();
                dirty.set(false);
                return;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                Persist loaded = GSON.fromJson(reader, Persist.class);
                data = loaded == null ? fresh() : normalize(loaded);
                dirty.set(false);
                AdaptiveDifficultyMod.LOGGER.info(
                        "[{}] RivalProgression loaded from {}", AdaptiveDifficultyMod.MOD_ID, file);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] RivalProgression load failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            data = fresh();
        }
    }

    public synchronized void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            data.updatedAt = System.currentTimeMillis();
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(data, w);
            }
            dirty.set(false);
            lastSaveAt = System.currentTimeMillis();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] RivalProgression save failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    public void saveIfNeeded(long now) {
        if (dirty.get() && now - lastSaveAt >= 30_000L) {
            save();
        }
    }

    public void markDirty() {
        dirty.set(true);
    }

    /** True when progression file is effectively empty (safe to import CNPC blob). */
    public boolean isImportEmpty() {
        if (data == null) {
            return true;
        }
        boolean noAch = data.achievements == null || data.achievements.isEmpty();
        boolean noJournal = data.journal == null || data.journal.isEmpty();
        boolean noHof = data.hallOfFame == null || data.hallOfFame.isEmpty();
        boolean noLb = data.season == null
                || data.season.leaderboard == null
                || data.season.leaderboard.isEmpty();
        return noAch && noJournal && noHof && noLb;
    }

    /** Used by staff force-migrate overwrite. */
    public synchronized void resetForImport() {
        data = fresh();
        dirty.set(true);
    }

    /** Staff: wipe one player's season / achievements / journal / quests. */
    public synchronized void clearPlayer(String uuid) {
        if (uuid == null || uuid.isBlank() || data == null) {
            return;
        }
        if (data.achievements != null) {
            data.achievements.remove(uuid);
        }
        if (data.quests != null) {
            data.quests.remove(uuid);
        }
        if (data.journal != null) {
            data.journal.remove(uuid);
        }
        if (data.specialTitles != null) {
            data.specialTitles.remove(uuid);
        }
        if (data.season != null && data.season.leaderboard != null) {
            data.season.leaderboard.remove(uuid);
        }
        markDirty();
        save();
    }

    public List<String> seasonLines(ServerPlayer player) {
        ensureSeason();
        if (player == null) {
            return List.of(UltraStyle.RED + "Players only.");
        }
        List<String> lines = new ArrayList<>();
        Season s = data.season;
        lines.add(UltraStyle.GOLD + UltraStyle.BOLD + "Rival Season " + UltraStyle.DARK_GRAY + "— " + UltraStyle.WHITE + s.name + " " + UltraStyle.DARK_GRAY + "(#" + s.id + ")");
        long left = Math.max(0L, s.endsAt - System.currentTimeMillis());
        lines.add(UltraStyle.GRAY + "Ends in " + UltraStyle.WHITE + formatMs(left));
        String id = player.m_20148_().toString();
        double myRp = s.leaderboard.getOrDefault(id, 0.0);
        lines.add(UltraStyle.GRAY + "Your season RP " + UltraStyle.WHITE + (int) myRp);
        List<Map.Entry<String, Double>> top = new ArrayList<>(s.leaderboard.entrySet());
        top.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed());
        int rank = 1;
        for (Map.Entry<String, Double> e : top) {
            if (rank > 5) {
                break;
            }
            RivalPlayerRecord rec = RivalStore.get().get(e.getKey());
            String name = rec == null ? e.getKey().substring(0, 8) : rec.name;
            lines.add(UltraStyle.YELLOW + "#" + rank + " " + UltraStyle.WHITE + name + " " + UltraStyle.GRAY + (int) e.getValue().doubleValue() + " RP");
            rank++;
        }
        return lines;
    }

    public List<String> questLines(ServerPlayer player) {
        if (player == null) {
            return List.of(UltraStyle.RED + "Players only.");
        }
        PlayerQuests q = ensureQuests(player.m_20148_().toString());
        if (q.list == null || q.list.isEmpty()) {
            q.list = defaultQuests();
            markDirty();
        }
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + UltraStyle.BOLD + "Weekly Rival Quests");
        for (QuestItem item : q.list) {
            int prog = Math.min(item.goal, item.progress);
            String done = prog >= item.goal ? UltraStyle.GREEN + "✔" : UltraStyle.GRAY;
            lines.add(done + " " + UltraStyle.WHITE + item.name + " " + UltraStyle.DARK_GRAY + "(" + prog + "/" + item.goal + ") " + UltraStyle.YELLOW + "+" + item.rp + " RP");
        }
        return lines;
    }

    /** 4.7.84: a stored id ("first_blood") as plain words ("First blood") for a player-facing line. */
    static String plainWords(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String w = id.trim().replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    public List<String> achievementLines(ServerPlayer player) {
        String id = player.m_20148_().toString();
        Map<String, Boolean> map = data.achievements.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + UltraStyle.BOLD + "Rival Achievements");
        if (map.isEmpty()) {
            lines.add(UltraStyle.GRAY + "None unlocked yet.");
            return lines;
        }
        for (Map.Entry<String, Boolean> e : map.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                String label = "nemesis".equals(e.getKey()) ? "Vendetta Rank" : plainWords(e.getKey());
                lines.add(UltraStyle.GREEN + "✔ " + UltraStyle.YELLOW + label);
            }
        }
        String title = data.specialTitles.get(id);
        if (title != null && !title.isBlank()) {
            lines.add(UltraStyle.GRAY + "Title " + UltraStyle.YELLOW + plainWords(title));
        }
        return lines;
    }

    public List<String> hofLines() {
        recomputeHof();
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + UltraStyle.BOLD + "Hall of Fame");
        if (data.hallOfFame.isEmpty()) {
            lines.add(UltraStyle.GRAY + "No entries yet.");
            return lines;
        }
        for (Map.Entry<String, String> e : data.hallOfFame.entrySet()) {
            lines.add(UltraStyle.YELLOW + plainWords(e.getKey()) + " " + UltraStyle.DARK_GRAY + "— " + UltraStyle.WHITE + e.getValue());
        }
        return lines;
    }

    public List<String> journalLines(ServerPlayer player) {
        String id = player.m_20148_().toString();
        List<JournalEntry> entries = data.journal.computeIfAbsent(id, k -> new ArrayList<>());
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + UltraStyle.BOLD + "Rival Journal");
        if (entries.isEmpty()) {
            lines.add(UltraStyle.GRAY + "No battles logged yet.");
            return lines;
        }
        int from = Math.max(0, entries.size() - 8);
        for (int i = entries.size() - 1; i >= from; i--) {
            JournalEntry e = entries.get(i);
            lines.add(UltraStyle.DARK_GRAY + e.when + " " + UltraStyle.WHITE + e.summary);
        }
        return lines;
    }

    public List<String> titleLines(ServerPlayer player) {
        String id = player.m_20148_().toString();
        RivalPlayerRecord rec = RivalStore.get().ensurePlayer(player);
        RivalConstants.RpTier tier = RivalConstants.tierFor(rec.totalRp);
        String special = data.specialTitles.getOrDefault(id, "");
        List<String> lines = new ArrayList<>();
        lines.add(UltraStyle.GOLD + UltraStyle.BOLD + "Rival Title");
        lines.add(UltraStyle.GRAY + "Title: " + UltraStyle.MARK + tier.color() + UltraStyle.BOLD + tier.name());
        lines.add(UltraStyle.GRAY + "Perk: " + UltraStyle.YELLOW + (tier.perk() == null || tier.perk().isBlank() ? "None" : tier.perk()));
        lines.add(UltraStyle.GRAY + "TP gain: " + UltraStyle.GREEN + Math.round(tier.tpMult() * 100.0) + "%");
        lines.add(UltraStyle.GRAY + "RP: " + UltraStyle.WHITE + (int) Math.floor(Math.max(0.0, rec.totalRp)));
        if (special != null && !special.isBlank()) {
            lines.add(UltraStyle.GRAY + "Special: " + UltraStyle.YELLOW + plainWords(special));
        } else {
            lines.add(UltraStyle.GRAY + "Special: " + UltraStyle.DARK_GRAY + "none");
        }
        lines.add(UltraStyle.DARK_GRAY + "Rival TP awards use your title multiplier.");
        return lines;
    }

    public void onChallengeEnd(
            ServerPlayer winner,
            ServerPlayer loser,
            RivalChallenge ch,
            boolean draw,
            boolean knockout
    ) {
        ensureSeason();
        long now = System.currentTimeMillis();
        String battleKey = ch == null ? ("x" + now) : ch.id;
        if (data.processedBattles.putIfAbsent(battleKey, true) != null) {
            return;
        }

        if (!draw && winner != null) {
            bumpSeasonRp(winner, 25);
            bumpQuest(winner, "defeat_rival", 1);
            unlock(winner, "first_win");
            unlock(winner, "first_blood");
            if (knockout) {
                unlock(winner, "knockout");
            }
            RivalPlayerRecord winRec = RivalStore.get().ensurePlayer(winner);
            RivalPlayerRecord loseRec = loser == null ? null : RivalStore.get().ensurePlayer(loser);
            if (winRec != null) {
                if (winRec.bestWinStreak >= 5) {
                    unlock(winner, "unbreakable");
                }
                if (winRec.challengesPlayed >= 25) {
                    unlock(winner, "battle_hardened");
                }
                if (winRec.careerHighestCombo >= 20) {
                    unlock(winner, "combo_master");
                }
                if (winRec.totalRp >= 15_000) {
                    unlock(winner, "god_rival");
                }
                if (loseRec != null && winRec.totalRp < loseRec.totalRp) {
                    unlock(winner, "legend_killer");
                }
            }
            if (ch != null && winner != null) {
                RivalChallenge.Combat wc = ch.combatOf(winner.m_20148_());
                RivalChallenge.Combat lc = loser == null ? null : ch.combatOf(loser.m_20148_());
                if (wc != null) {
                    if (wc.longestCombo >= 20) {
                        unlock(winner, "combo_master");
                    }
                    if (wc.ki >= 5000) {
                        unlock(winner, "ki_dominator");
                    }
                    double dealt = Math.max(0.0, wc.damage);
                    double taken = lc == null ? 0.0 : Math.max(0.0, lc.damage);
                    double maxHp = 20.0;
                    try {
                        maxHp = Math.max(1.0, winner.m_21233_());
                    } catch (Throwable ignored) {
                    }
                    double remainingPct = Math.max(0.0, Math.min(100.0,
                            (winner.m_21223_() / maxHp) * 100.0));
                    if (remainingPct >= 90.0) {
                        unlock(winner, "perfect_victory");
                    }
                    if (taken <= 0.5) {
                        unlock(winner, "untouchable");
                    }
                    if (taken > dealt * 1.5 && dealt > 0) {
                        unlock(winner, "comeback_king");
                    }
                }
            }
        }
        if (ch != null) {
            applyCombatQuests(winner, ch);
            applyCombatQuests(loser, ch);
            bumpQuest(winner, "three_battles", 1);
            bumpQuest(loser, "three_battles", 1);
            long planned = ch.durationMs > 0 ? ch.durationMs : RivalConstants.CH_DURATION_MS;
            long elapsed = Math.max(0L, now - ch.startAt);
            if (elapsed >= planned - 1500L) {
                bumpQuest(winner, "long_battle", 1);
                bumpQuest(loser, "long_battle", 1);
            }
            addJournal(winner, summary(ch, draw, winner, true));
            addJournal(loser, summary(ch, draw, loser, false));
        }
        recomputeHof();
        markDirty();
    }

    private void applyCombatQuests(ServerPlayer player, RivalChallenge ch) {
        if (player == null || ch == null) {
            return;
        }
        RivalChallenge.Combat combat = ch.combat.get(player.m_20148_());
        double physical = combat == null ? 0.0 : combat.physical;
        double ki = combat == null ? 0.0 : combat.ki;
        int hits = combat == null ? 0 : combat.hits;
        bumpQuest(player, "melee_hits", Math.max(0, Math.min(50, hits > 0 ? hits : (int) (physical / 50.0))));
        bumpQuest(player, "ki_damage", (int) Math.min(20000, Math.max(0, ki)));
    }

    private String summary(RivalChallenge ch, boolean draw, ServerPlayer me, boolean won) {
        if (ch == null) {
            return "Battle logged";
        }
        String other = me != null && me.m_20148_().equals(ch.a) ? ch.nameB : ch.nameA;
        if (draw) {
            return "Draw vs " + other;
        }
        return (won ? "Win vs " : "Loss vs ") + other
                + (ch.knockout ? " (KO)" : "");
    }

    private void bumpSeasonRp(ServerPlayer player, double amount) {
        if (player == null || !(amount > 0)) {
            return;
        }
        ensureSeason();
        String id = player.m_20148_().toString();
        data.season.leaderboard.merge(id, amount, Double::sum);
        claimQuestRp(player);
    }

    private void claimQuestRp(ServerPlayer player) {
        PlayerQuests q = ensureQuests(player.m_20148_().toString());
        for (QuestItem item : q.list) {
            if (item.progress >= item.goal && !item.claimed) {
                item.claimed = true;
                data.season.leaderboard.merge(player.m_20148_().toString(), (double) item.rp, Double::sum);
                DmzRewards.msg(player, LmChat.ok("Rival", UltraStyle.YELLOW + item.name + " " + UltraStyle.GRAY + "complete " + UltraStyle.GREEN + "+" + item.rp + " season RP"));
            }
        }
    }

    private void bumpQuest(ServerPlayer player, String questId, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        PlayerQuests q = ensureQuests(player.m_20148_().toString());
        for (QuestItem item : q.list) {
            if (questId.equals(item.id)) {
                item.progress += amount;
            }
        }
        claimQuestRp(player);
        markDirty();
    }

    private void unlock(ServerPlayer player, String ach) {
        if (player == null || ach == null) {
            return;
        }
        String id = player.m_20148_().toString();
        Map<String, Boolean> map = data.achievements.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        if (Boolean.TRUE.equals(map.get(ach))) {
            return;
        }
        map.put(ach, true);
        String label = "nemesis".equals(ach) ? "Vendetta Rank" : ach.replace('_', ' ');
        DmzRewards.msg(player, LmChat.note("Rival", UltraStyle.YELLOW + "Achievement: " + label));
        if ("legend_killer".equals(ach)) {
            data.specialTitles.put(id, "legend_killer");
        }
        if ("god_rival".equals(ach)) {
            data.specialTitles.put(id, "god_slayer");
        }
        markDirty();
    }

    public void unlockVendetta(ServerPlayer player) {
        unlock(player, "nemesis");
    }

    private void addJournal(ServerPlayer player, String summary) {
        if (player == null || summary == null) {
            return;
        }
        String id = player.m_20148_().toString();
        List<JournalEntry> list = data.journal.computeIfAbsent(id, k -> new ArrayList<>());
        JournalEntry e = new JournalEntry();
        e.when = java.time.LocalDate.now().toString();
        e.summary = summary;
        list.add(e);
        while (list.size() > 40) {
            list.remove(0);
        }
    }

    public void recomputeHof() {
        RivalStore store = RivalStore.get();
        String bestRp = null;
        double bestRpV = -1;
        String bestBattles = null;
        int bestBattlesV = -1;
        String greatest = null;
        double greatestV = -1;
        String bestStreak = null;
        int bestStreakV = -1;
        String longestRivalry = null;
        long longestRivalryV = -1;
        String seasonChamp = null;
        double seasonChampV = -1;
        for (RivalPlayerRecord rec : store.players.values()) {
            if (rec == null) {
                continue;
            }
            if (rec.totalRp > bestRpV) {
                bestRpV = rec.totalRp;
                bestRp = rec.name + " (" + (int) rec.totalRp + " RP)";
            }
            if (rec.challengesPlayed > bestBattlesV) {
                bestBattlesV = rec.challengesPlayed;
                bestBattles = rec.name + " (" + rec.challengesPlayed + ")";
            }
            if (rec.bestWinStreak > bestStreakV) {
                bestStreakV = rec.bestWinStreak;
                bestStreak = rec.name + " (" + rec.bestWinStreak + ")";
            }
            for (RivalLink link : rec.rivals.values()) {
                if (link == null || !link.mutual) {
                    continue;
                }
                int fights = link.wins + link.losses + link.draws;
                double score = fights * 10 + link.points + (link.isNemesis ? 100 : 0);
                if (score > greatestV) {
                    greatestV = score;
                    greatest = rec.name + " vs " + link.name + " (" + fights + " battles)";
                }
                long age = Math.max(0L, System.currentTimeMillis() - Math.max(1L, link.mutualSince > 0
                        ? link.mutualSince : link.createdAt));
                if (age > longestRivalryV) {
                    longestRivalryV = age;
                    longestRivalry = rec.name + " vs " + link.name
                            + " (" + (age / 86400000L) + "d)";
                }
            }
        }
        if (data.season != null && data.season.leaderboard != null) {
            for (Map.Entry<String, Double> e : data.season.leaderboard.entrySet()) {
                if (e.getValue() == null || e.getValue() <= seasonChampV) {
                    continue;
                }
                seasonChampV = e.getValue();
                RivalPlayerRecord rec = store.get(e.getKey());
                seasonChamp = (rec == null ? e.getKey() : rec.name)
                        + " (" + (int) seasonChampV + " season RP)";
            }
        }
        Map<String, String> hof = new LinkedHashMap<>();
        if (bestRp != null) {
            hof.put("highest_rp", bestRp);
        }
        if (bestBattles != null) {
            hof.put("most_battles", bestBattles);
        }
        if (greatest != null) {
            hof.put("greatest_rivals", greatest);
        }
        if (bestStreak != null) {
            hof.put("longest_streak", bestStreak);
        }
        if (longestRivalry != null) {
            hof.put("longest_rivalry", longestRivalry);
        }
        if (seasonChamp != null) {
            hof.put("season_champion", seasonChamp);
        }
        data.hallOfFame = hof;
    }

    /** 4.8.3: the current rival season number (starts the first season when there is none). */
    public synchronized int currentSeasonId() {
        ensureSeason();
        return data.season.id;
    }

    /**
     * 4.8.3: staff season end: start the next season now (number + 1, a fresh season RP board, the usual length) and
     * save. Returns the number of the season that ended.
     */
    public synchronized int advanceSeasonNow() {
        ensureSeason();
        int ended = data.season.id;
        long now = System.currentTimeMillis();
        data.season.id = ended + 1;
        data.season.name = "Season " + data.season.id;
        data.season.startedAt = now;
        data.season.endsAt = now + SEASON_MS;
        data.season.leaderboard = new ConcurrentHashMap<>();
        markDirty();
        save();
        return ended;
    }

    private void ensureSeason() {
        long now = System.currentTimeMillis();
        if (data.season == null) {
            data.season = new Season();
            data.season.id = 1;
            data.season.name = "Season 1";
            data.season.startedAt = now;
            data.season.endsAt = now + SEASON_MS;
            data.season.leaderboard = new ConcurrentHashMap<>();
        }
        if (now > data.season.endsAt) {
            data.season.id++;
            data.season.name = "Season " + data.season.id;
            data.season.startedAt = now;
            data.season.endsAt = now + SEASON_MS;
            data.season.leaderboard = new ConcurrentHashMap<>();
            markDirty();
        }
    }

    private PlayerQuests ensureQuests(String id) {
        String week = weekKey();
        PlayerQuests q = data.quests.get(id);
        if (q == null || !week.equals(q.week)) {
            q = new PlayerQuests();
            q.week = week;
            q.list = defaultQuests();
            data.quests.put(id, q);
            markDirty();
        }
        return q;
    }

    private static List<QuestItem> defaultQuests() {
        List<QuestItem> list = new ArrayList<>();
        list.add(quest("defeat_rival", "Defeat your rival", 1, 40));
        list.add(quest("melee_hits", "Land 50 melee hits in challenges", 50, 25));
        list.add(quest("ki_damage", "Deal 20,000 Ki damage in challenges", 20000, 25));
        list.add(quest("three_battles", "Fight 3 official battles", 3, 30));
        list.add(quest("long_battle", "Finish a full 60s battle", 1, 20));
        return list;
    }

    private static QuestItem quest(String id, String name, int goal, int rp) {
        QuestItem q = new QuestItem();
        q.id = id;
        q.name = name;
        q.goal = goal;
        q.rp = rp;
        return q;
    }

    private static String weekKey() {
        return String.valueOf(System.currentTimeMillis() / (7L * 86400000L));
    }

    private static Persist fresh() {
        Persist p = new Persist();
        long now = System.currentTimeMillis();
        p.version = 4;
        p.season = new Season();
        p.season.id = 1;
        p.season.name = "Season 1";
        p.season.startedAt = now;
        p.season.endsAt = now + SEASON_MS;
        p.season.leaderboard = new ConcurrentHashMap<>();
        p.achievements = new ConcurrentHashMap<>();
        p.quests = new ConcurrentHashMap<>();
        p.journal = new ConcurrentHashMap<>();
        p.hallOfFame = new LinkedHashMap<>();
        p.specialTitles = new ConcurrentHashMap<>();
        p.processedBattles = new ConcurrentHashMap<>();
        p.updatedAt = now;
        return p;
    }

    private static Persist normalize(Persist p) {
        Persist out = fresh();
        if (p.season != null) {
            out.season = p.season;
            if (out.season.leaderboard == null) {
                out.season.leaderboard = new ConcurrentHashMap<>();
            }
        }
        if (p.achievements != null) {
            out.achievements.putAll(p.achievements);
        }
        if (p.quests != null) {
            out.quests.putAll(p.quests);
        }
        if (p.journal != null) {
            out.journal.putAll(p.journal);
        }
        if (p.hallOfFame != null) {
            out.hallOfFame.putAll(p.hallOfFame);
        }
        if (p.specialTitles != null) {
            out.specialTitles.putAll(p.specialTitles);
        }
        if (p.processedBattles != null) {
            out.processedBattles.putAll(p.processedBattles);
        }
        out.version = 4;
        return out;
    }

    private static String formatMs(long ms) {
        long s = ms / 1000L;
        long d = s / 86400L;
        long h = (s % 86400L) / 3600L;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        return h + "h " + ((s % 3600L) / 60L) + "m";
    }

    public static final class Persist {
        int version;
        Season season;
        Map<String, Map<String, Boolean>> achievements = new ConcurrentHashMap<>();
        Map<String, PlayerQuests> quests = new ConcurrentHashMap<>();
        Map<String, List<JournalEntry>> journal = new ConcurrentHashMap<>();
        Map<String, String> hallOfFame = new LinkedHashMap<>();
        Map<String, String> specialTitles = new ConcurrentHashMap<>();
        Map<String, Boolean> processedBattles = new ConcurrentHashMap<>();
        long updatedAt;
    }

    public static final class Season {
        int id;
        String name;
        long startedAt;
        long endsAt;
        Map<String, Double> leaderboard = new ConcurrentHashMap<>();
    }

    public static final class PlayerQuests {
        String week;
        List<QuestItem> list = new ArrayList<>();
    }

    public static final class QuestItem {
        String id;
        String name;
        int goal;
        int progress;
        int rp;
        boolean claimed;
    }

    public static final class JournalEntry {
        String when;
        String summary;
    }
}
