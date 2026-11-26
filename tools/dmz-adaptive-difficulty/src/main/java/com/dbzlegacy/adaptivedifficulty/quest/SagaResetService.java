package com.dbzlegacy.adaptivedifficulty.quest;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.stats.StatsData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Paid saga replay. The menu is whatever {@link QuestRegistry#getAllSagas()} currently loaded
 * (vanilla story plus datapack sagas). Cost is {@link SagaResetConfig#costFor(ServerPlayer, String)}.
 * Progress clear is {@link PlayerQuestData#resetSaga}, which also drops a tracked quest in that saga.
 */
public final class SagaResetService {
    public static final String NOT_ENOUGH = "Not enough Ancient Coins. Earn more, then try this reset again.";
    public static final String RESET_OFF =
            "Saga reset is turned off. Ask staff to turn it on if you need to replay a saga.";
    public static final String SAGA_LOCKED =
            "That saga is locked. Finish the earlier story quests that unlock it, then you can reset it.";
    public static final String NOTHING_TO_RESET =
            "Nothing to reset. Start this saga first, then come back when you have progress to clear.";
    public static final String NOT_LOADED =
            "That saga is not loaded. Ask staff to load it, then open this page again.";

    private SagaResetService() {}

    public enum Status {
        COMPLETED,
        IN_PROGRESS,
        LOCKED,
        NOT_STARTED
    }

    public record Offer(String id, String name, long cost, Status status) {
        public boolean resettable() {
            return status == Status.COMPLETED || status == Status.IN_PROGRESS;
        }

        public String statusLabel() {
            return switch (status) {
                case COMPLETED -> "Completed";
                case IN_PROGRESS -> "In progress";
                case LOCKED -> "Locked until earlier quests";
                case NOT_STARTED -> "Not started";
            };
        }

        /** Row detail: this player's cost, then the status. Unstarted rows say there is nothing to reset. */
        public String rowDetail() {
            String price = SagaResetService.costText(cost);
            if (status == Status.NOT_STARTED) {
                return price + " - Not started - Nothing to reset until you play it";
            }
            if (status == Status.LOCKED) {
                return price + " - Locked until earlier story quests unlock it";
            }
            return price + " - " + statusLabel();
        }
    }

    public static List<Offer> offers(ServerPlayer player) {
        List<Offer> offers = new ArrayList<>();
        StatsData stats = DmzProgression.stats(player);
        PlayerQuestData data = stats == null ? null : stats.getPlayerQuestData();
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        for (Saga saga : loadedSagas()) {
            String id = saga.getId();
            long cost = free ? 0L : SagaResetConfig.get().costFor(player, id);
            offers.add(new Offer(id, displayName(saga), cost, status(data, saga)));
        }
        return offers;
    }

    public static Offer findOffer(ServerPlayer player, String sagaId) {
        if (sagaId == null || sagaId.isBlank()) {
            return null;
        }
        for (Offer offer : offers(player)) {
            if (offer.id().equalsIgnoreCase(sagaId.trim())) {
                return offer;
            }
        }
        return null;
    }

    public static List<String> confirmLines(ServerPlayer player, String sagaId) {
        Offer offer = findOffer(player, sagaId);
        List<String> lines = new ArrayList<>();
        if (offer == null) {
            lines.add(NOT_LOADED);
            return lines;
        }
        lines.add(confirmSentence(offer));
        StatsData stats = DmzProgression.stats(player);
        PlayerQuestData data = stats == null ? null : stats.getPlayerQuestData();
        if (data != null && data.isInParty()) {
            lines.add("Leave your quest party before resetting.");
        }
        return lines;
    }

    /** "Reset Dragon Balls Saga progress? This costs 850 Ancient Coins and cannot be undone." */
    public static String confirmSentence(Offer offer) {
        if (offer == null) {
            return NOT_LOADED;
        }
        String price = offer.cost() <= 0L
                ? "This is free"
                : "This costs " + costText(offer.cost());
        return "Reset " + sagaTitle(offer.name()) + " progress? " + price + " and cannot be undone.";
    }

    /**
     * @return a notice line, or null when the reset completed with nothing to show
     */
    public static String reset(ServerPlayer player, String sagaId) {
        if (player == null) {
            return "Could not reset that saga. Go back and choose a loaded saga.";
        }
        if (!SagaResetConfig.get().enabled) {
            return RESET_OFF;
        }
        Saga saga = find(sagaId);
        if (saga == null || saga.getId() == null) {
            return NOT_LOADED;
        }
        StatsData stats = DmzProgression.stats(player);
        if (stats == null || stats.getPlayerQuestData() == null) {
            return "Your quest data is not loaded yet. Try again in a moment.";
        }
        PlayerQuestData data = stats.getPlayerQuestData();
        String id = saga.getId();
        if (data.isSagaLocked(id)) {
            return SAGA_LOCKED;
        }
        if (status(data, saga) == Status.NOT_STARTED) {
            return NOTHING_TO_RESET;
        }
        if (data.isInParty()) {
            return "Leave your quest party before resetting a saga.";
        }
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        long cost = free ? 0L : SagaResetConfig.get().costFor(player, id);
        if (!free && !AncientCoinEconomy.canAfford(player, cost)) {
            return NOT_ENOUGH;
        }
        if (!free && !AncientCoinEconomy.charge(player, cost)) {
            return NOT_ENOUGH;
        }
        data.resetSaga(id);
        clearTrackedIfSaga(data, id);
        discardOwnedSagaEntities(player, id);
        try {
            QuestService.syncQuestState(player);
        } catch (Throwable ignored) {
        }
        String paid = free || cost <= 0L ? "" : " Paid " + costText(cost) + ".";
        return "Reset " + sagaTitle(displayName(saga)) + "." + paid;
    }

    /** 4.8.3: result of a staff saga action: whether it changed anything, and the line to show staff. */
    public record AdminResult(boolean ok, String line, String sagaTitle) {}

    /** 4.8.3: the command key for a saga, its name in lower case with hyphens ("saiyan-saga"). */
    public static String commandKey(Saga saga) {
        String title = sagaTitle(displayName(saga)).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (char c : title.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    /** 4.8.3: command keys of every loaded saga, sorted by name. */
    public static List<String> commandKeys() {
        List<String> keys = new ArrayList<>();
        for (Saga saga : loadedSagas()) {
            keys.add(commandKey(saga));
        }
        return keys;
    }

    /** 4.8.3: readable titles of every loaded saga ("Saiyan Saga"), sorted. */
    public static List<String> sagaTitles() {
        List<String> titles = new ArrayList<>();
        for (Saga saga : loadedSagas()) {
            titles.add(sagaTitle(displayName(saga)));
        }
        return titles;
    }

    /**
     * 4.8.3: a loaded saga by what staff typed: its command key, name, or id, ignoring case, spaces, hyphens and
     * underscores, with or without the word "saga". Null when nothing matches.
     */
    public static Saga findLoose(String raw) {
        String want = squash(raw);
        if (want.isEmpty()) {
            return null;
        }
        String wantBare = want.endsWith("saga") && want.length() > 4 ? want.substring(0, want.length() - 4) : want;
        for (Saga saga : loadedSagas()) {
            String id = saga.getId();
            String tail = id;
            int cut = Math.max(id.lastIndexOf('.'), id.lastIndexOf(':'));
            if (cut >= 0 && cut < id.length() - 1) {
                tail = id.substring(cut + 1);
            }
            for (String c : new String[] {id, tail, displayName(saga), sagaTitle(displayName(saga))}) {
                String sq = squash(c);
                String bare = sq.endsWith("saga") && sq.length() > 4 ? sq.substring(0, sq.length() - 4) : sq;
                if (sq.equals(want) || bare.equals(wantBare)) {
                    return saga;
                }
            }
        }
        return null;
    }

    /**
     * 4.8.3: staff reset of a player's saga progress. Free for everyone, works while player resets are turned off,
     * and skips nothing else: a locked saga, an unstarted saga and a quest party still refuse.
     */
    public static AdminResult adminReset(ServerPlayer player, Saga saga) {
        if (player == null || saga == null || saga.getId() == null) {
            return new AdminResult(false, NOT_LOADED, "Saga");
        }
        String title = sagaTitle(displayName(saga));
        StatsData stats = DmzProgression.stats(player);
        if (stats == null || stats.getPlayerQuestData() == null) {
            return new AdminResult(false, "Their quest data is not loaded yet. Try again in a moment.", title);
        }
        PlayerQuestData data = stats.getPlayerQuestData();
        String id = saga.getId();
        if (data.isSagaLocked(id)) {
            return new AdminResult(false, title + " is locked for them. Unlock it first with /lm admin saga unlock.",
                    title);
        }
        if (status(data, saga) == Status.NOT_STARTED) {
            return new AdminResult(false, "They have not started " + title + ", so there is nothing to reset.", title);
        }
        if (data.isInParty()) {
            return new AdminResult(false, "They are in a quest party. Ask them to leave it, then reset again.", title);
        }
        data.resetSaga(id);
        clearTrackedIfSaga(data, id);
        discardOwnedSagaEntities(player, id);
        try {
            QuestService.syncQuestState(player);
        } catch (Throwable ignored) {
        }
        return new AdminResult(true, "Reset " + title + " progress. No coins were charged.", title);
    }

    /**
     * 4.8.3: staff unlock of a saga that is locked for the player ({@link PlayerQuestData#isSagaLocked}). DMZ stores
     * the lock flag under {@code setSagaUnlocked}; writing false clears it, so the check reads it back.
     */
    public static AdminResult adminUnlock(ServerPlayer player, Saga saga) {
        if (player == null || saga == null || saga.getId() == null) {
            return new AdminResult(false, NOT_LOADED, "Saga");
        }
        String title = sagaTitle(displayName(saga));
        StatsData stats = DmzProgression.stats(player);
        if (stats == null || stats.getPlayerQuestData() == null) {
            return new AdminResult(false, "Their quest data is not loaded yet. Try again in a moment.", title);
        }
        PlayerQuestData data = stats.getPlayerQuestData();
        String id = saga.getId();
        if (!data.isSagaLocked(id)) {
            return new AdminResult(false, title + " is already unlocked for them. Nothing changed.", title);
        }
        data.setSagaUnlocked(id, false);
        if (data.isSagaLocked(id)) {
            return new AdminResult(false, "Could not unlock " + title + ". Check the server log.", title);
        }
        try {
            QuestService.syncQuestState(player);
        } catch (Throwable ignored) {
        }
        return new AdminResult(true, "Unlocked " + title + ".", title);
    }

    private static String squash(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String costText(long coins) {
        if (coins <= 0L) {
            return "free";
        }
        return coins + " Ancient Coin" + (coins == 1L ? "" : "s");
    }

    public static String displayName(Saga saga) {
        if (saga == null) {
            return "Saga";
        }
        String name = saga.getName();
        String id = saga.getId();
        if (name == null || name.isBlank() || looksLikeId(name, id)) {
            return prettify(id == null || id.isBlank() ? name : id);
        }
        return name.trim();
    }

    public static String prettify(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Saga";
        }
        String name = raw.trim();
        if (name.startsWith("dmz.")) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                name = name.substring(dot + 1);
            }
        }
        name = name.replace('_', ' ').replace('-', ' ').trim();
        if (name.isEmpty()) {
            return "Saga";
        }
        StringBuilder out = new StringBuilder();
        for (String part : name.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? "Saga" : out.toString();
    }

    private static boolean looksLikeId(String name, String id) {
        if (id != null && name.equalsIgnoreCase(id)) {
            return true;
        }
        String trimmed = name.trim();
        return trimmed.startsWith("dmz.") || trimmed.indexOf('_') >= 0 || trimmed.indexOf('-') >= 0;
    }

    private static String sagaTitle(String name) {
        if (name == null || name.isBlank()) {
            return "Saga";
        }
        String trimmed = name.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith("saga")) {
            return trimmed;
        }
        return trimmed + " Saga";
    }

    private static List<Saga> loadedSagas() {
        List<Saga> sagas = new ArrayList<>();
        try {
            Map<String, Saga> all = QuestRegistry.getAllSagas();
            if (all != null) {
                sagas.addAll(all.values());
            }
        } catch (Throwable ignored) {
        }
        sagas.removeIf(saga -> saga == null || saga.getId() == null || saga.getId().isBlank());
        sagas.sort(Comparator.comparing(SagaResetService::displayName, String.CASE_INSENSITIVE_ORDER));
        return sagas;
    }

    private static Saga find(String sagaId) {
        if (sagaId == null || sagaId.isBlank()) {
            return null;
        }
        for (Saga saga : loadedSagas()) {
            if (saga.getId().equalsIgnoreCase(sagaId.trim())) {
                return saga;
            }
        }
        return null;
    }

    /**
     * {@link PlayerQuestData#isSagaLocked} reads {@code sagaUnlocks} and defaults to false,
     * so a saga with no flag is offered. A true flag is locked and is not reset.
     */
    private static Status status(PlayerQuestData data, Saga saga) {
        String id = saga.getId();
        if (data != null && id != null && data.isSagaLocked(id)) {
            return Status.LOCKED;
        }
        int started = data == null ? 0 : startedCount(data, id);
        if (started <= 0) {
            return Status.NOT_STARTED;
        }
        int total = saga.getQuests() == null ? 0 : saga.getQuests().size();
        int completed = data == null ? 0 : completedCount(data, id);
        if (total > 0 && completed >= total) {
            return Status.COMPLETED;
        }
        return Status.IN_PROGRESS;
    }

    private static int startedCount(PlayerQuestData data, String sagaId) {
        String prefix = sagaId + ":";
        Set<String> keys = new LinkedHashSet<>();
        addMatching(keys, data.getAcceptedQuestIds(), prefix);
        addMatching(keys, data.getCompletedQuestIds(), prefix);
        addMatching(keys, data.getFailedQuestIds(), prefix);
        return keys.size();
    }

    private static int completedCount(PlayerQuestData data, String sagaId) {
        Set<String> keys = new LinkedHashSet<>();
        addMatching(keys, data.getCompletedQuestIds(), sagaId + ":");
        return keys.size();
    }

    private static void addMatching(Set<String> keys, Set<String> source, String prefix) {
        if (source == null) {
            return;
        }
        for (String key : source) {
            if (key != null && key.startsWith(prefix)) {
                keys.add(key);
            }
        }
    }

    /**
     * {@code resetSaga} already nulls {@code trackedQuestId} when it starts with {@code sagaId:}.
     * This catches a tracked id that is the bare saga id.
     */
    private static void clearTrackedIfSaga(PlayerQuestData data, String sagaId) {
        String tracked = data.getTrackedQuestId();
        if (tracked == null || tracked.isBlank()) {
            return;
        }
        if (tracked.equalsIgnoreCase(sagaId) || tracked.startsWith(sagaId + ":")) {
            data.setTrackedQuestId(null);
        }
    }

    /** Remove this player's live quest mobs for the saga. Other players' mobs stay. */
    private static void discardOwnedSagaEntities(ServerPlayer player, String sagaId) {
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return;
        }
        String owner = player.m_20148_().toString();
        String prefix = sagaId + ":";
        for (ServerLevel level : server.m_129785_()) {
            List<Entity> doomed = new ArrayList<>();
            for (Entity entity : level.m_8583_()) {
                if (entity == null || entity instanceof ServerPlayer) {
                    continue;
                }
                CompoundTag tag = PersistentDataAccess.get(entity);
                if (!PersistentDataAccess.isWritable(tag)) {
                    continue;
                }
                if (!owner.equals(tag.m_128461_("dmz_quest_owner"))) {
                    continue;
                }
                String saga = tag.m_128461_("dmz_saga_id");
                String questKey = tag.m_128461_("dmz_quest_key");
                if (sagaId.equals(saga) || (questKey != null && questKey.startsWith(prefix))) {
                    doomed.add(entity);
                }
            }
            for (Entity entity : doomed) {
                entity.m_146870_();
            }
        }
    }
}
