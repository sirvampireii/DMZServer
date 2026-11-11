package com.dbzlegacy.mohistmelee;

import com.dbzlegacy.mohistmelee.mixin.QuestServiceInvoker;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestParty;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.objectives.KillObjective;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Caps DragonMineZ saga kill-quest enemies near a player and clears stale ones (3.0.2).
 *
 * <p>DMZ has no spawn cap: accepting a kill quest spawns its whole remaining count at once, so
 * 50 accepted one-kill quests put 50 enemies on one player. Once a second, server-wide (one pass
 * over each level's entities, nothing per player tick):
 * <ul>
 *   <li><b>Cap:</b> a party keeps at most {@link #CAP} kill-quest enemies within 64 blocks of
 *       their owners; the farthest extras are discarded.</li>
 *   <li><b>Lifetime:</b> an enemy older than 60 s that no player has hit in the last 10 s is
 *       discarded. Being hit by it does not count, only hitting it. Bosses are not exempt.</li>
 *   <li><b>Top-up:</b> each kill the party scored since the last spawn may bring one replacement,
 *       through DMZ's own spawn. No kills, no refill: an idle pit stays empty until the player
 *       uses the quest's re-summon.</li>
 * </ul>
 * Discarding posts no death event: no drops, no quest credit, no TP or mastery. Wave mobs
 * ({@code dmz_quest_session}), escorts ({@code dmz_quest_escort}) and non-kill objectives (spar
 * partners) are never touched.
 */
public final class SagaEnemyCap {
    private static final Logger LOGGER = LogManager.getLogger(DmzMohistMeleeFix.MOD_ID);
    static final int CAP = 20;
    /** DMZ's own wave leash: QuestFieldSessions.WAVE_LEASH_DISTANCE_SQR (64 blocks). */
    static final double RANGE_SQR = 4096.0;
    static final int LIFETIME_TICKS = 1200;
    static final long FOUGHT_TICKS = 200L;
    /** Vanilla keeps lastHurtByMob for 100 ticks; the 20-tick sweep reads it well inside that. */
    private static final int HURT_MEMORY_TICKS = 100;
    private static final String KEY_TAG = "dmz_quest_key";
    private static final String INDEX_TAG = "dmz_quest_objective_index";
    private static final String OWNER_TAG = "dmz_quest_owner";
    private static final String SESSION_TAG = "dmz_quest_session";
    private static final String ESCORT_TAG = "dmz_quest_escort";

    private static int tickCounter;
    private static boolean failureLogged;
    /** Game time a player last hit each quest enemy (vanilla forgets after 5 s; the rule needs 10 s). */
    private static final Map<Entity, Long> LAST_PLAYER_HIT = new WeakHashMap<>();
    /** Kill progress per party and quest at the last sweep: "group|quest" -> kills. */
    private static Map<String, Integer> killSnapshot = new HashMap<>();

    private SagaEnemyCap() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(SagaEnemyCap.class);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        try {
            sweep(server);
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.error("[{}] saga enemy sweep failed (logged once)", DmzMohistMeleeFix.MOD_ID, t);
            }
        }
    }

    /** One quest enemy in range of its owner. */
    private record Tracked(LivingEntity entity, double distSqr) {}

    private static void sweep(MinecraftServer server) {
        PlayerList players = server.m_6846_();
        Map<UUID, String> groupOf = new HashMap<>();
        Map<String, List<Tracked>> inRange = new HashMap<>();
        // Alive enemies per "group|quest|objective", any distance: what DMZ's spawn counts.
        Map<String, Integer> alive = new HashMap<>();
        int capped = 0;
        int expired = 0;

        for (ServerLevel level : server.m_129785_()) {
            long now = level.m_46467_();
            for (Entity e : level.m_8583_()) {
                if (!(e instanceof LivingEntity living) || !living.m_6084_()) {
                    continue;
                }
                CompoundTag tags = PersistentDataAccess.get(e);
                if (!tags.m_128441_(KEY_TAG) || tags.m_128441_(SESSION_TAG) || tags.m_128441_(ESCORT_TAG)) {
                    continue;
                }
                String questKey = tags.m_128461_(KEY_TAG);
                if (!(objective(questKey, tags.m_128451_(INDEX_TAG)) instanceof KillObjective)) {
                    continue;
                }
                ServerPlayer owner = onlineOwner(players, tags.m_128461_(OWNER_TAG));
                if (owner == null || owner.m_9236_() != level) {
                    continue;
                }
                String group = groupOf.computeIfAbsent(owner.m_20148_(), id -> groupKey(owner));
                alive.merge(group + "|" + questKey + "|" + tags.m_128451_(INDEX_TAG), 1, Integer::sum);
                rememberPlayerHit(living, now);
                double d = living.m_20280_(owner);
                if (d <= RANGE_SQR) {
                    inRange.computeIfAbsent(group, g -> new ArrayList<>()).add(new Tracked(living, d));
                }
            }
        }

        Map<String, Integer> room = new HashMap<>();
        for (Map.Entry<String, List<Tracked>> entry : inRange.entrySet()) {
            List<Tracked> mobs = entry.getValue();
            mobs.sort(Comparator.comparingDouble(Tracked::distSqr).reversed());
            int over = mobs.size() - CAP;
            int kept = 0;
            for (Tracked t : mobs) {
                LivingEntity mob = t.entity();
                boolean discard;
                if (over > 0) {
                    over--;
                    discard = true;
                    capped++;
                } else if (mob.f_19797_ > LIFETIME_TICKS && !fought(mob, mob.m_9236_().m_46467_())) {
                    discard = true;
                    expired++;
                } else {
                    discard = false;
                }
                if (discard) {
                    CompoundTag tags = PersistentDataAccess.get(mob);
                    alive.merge(entry.getKey() + "|" + tags.m_128461_(KEY_TAG) + "|" + tags.m_128451_(INDEX_TAG), -1, Integer::sum);
                    mob.m_146870_();
                } else {
                    kept++;
                }
            }
            room.put(entry.getKey(), CAP - kept);
        }

        int toppedUp = topUp(players, groupOf, alive, room);
        if (capped + expired + toppedUp > 0) {
            MeleeDebug.log("[{}] saga sweep: capped={} expired={} toppedUp={}", DmzMohistMeleeFix.MOD_ID, capped, expired, toppedUp);
        }
    }

    /**
     * Replacements for kills: per party, the kill progress gained since the last sweep is a budget
     * of enemies, spent on accepted quests that still need more alive, at most one DMZ spawn per
     * quest per sweep and never past the cap's room. A kill that completes its quest counts too:
     * that quest has left the accepted list, so its progress is read from the quest data, which
     * keeps it after completion (an abandoned quest resets to 0 and adds nothing).
     */
    private static int topUp(PlayerList players, Map<UUID, String> groupOf, Map<String, Integer> alive,
                             Map<String, Integer> room) {
        // group -> quest -> the member to spawn for (first seen)
        Map<String, Map<String, ServerPlayer>> requesters = new HashMap<>();
        Map<String, Integer> progressNow = new HashMap<>();
        Map<String, List<ServerPlayer>> members = new HashMap<>();
        for (ServerPlayer p : players.m_11314_()) {
            PlayerQuestData pqd = QuestParty.questData(p);
            if (pqd == null) {
                continue;
            }
            String group = groupOf.computeIfAbsent(p.m_20148_(), id -> groupKey(p));
            members.computeIfAbsent(group, g -> new ArrayList<>()).add(p);
            for (String questKey : new ArrayList<>(pqd.getAcceptedQuestIds())) {
                Quest quest = QuestRegistry.getQuest(questKey);
                if (quest == null || !hasQuestSpawnedKills(quest)) {
                    continue;
                }
                progressNow.merge(group + "|" + questKey, killed(pqd, questKey, quest), Math::max);
                requesters.computeIfAbsent(group, g -> new HashMap<>()).putIfAbsent(questKey, p);
            }
        }

        Map<String, Integer> budgets = new HashMap<>();
        for (Map.Entry<String, Integer> snap : killSnapshot.entrySet()) {
            String key = snap.getKey();
            int bar = key.indexOf('|');
            String group = key.substring(0, bar);
            Integer now = progressNow.get(key);
            if (now == null) {
                now = progressOutsideAccepted(members.get(group), key.substring(bar + 1));
            }
            budgets.merge(group, Math.max(0, now - snap.getValue()), Integer::sum);
        }

        int spawned = 0;
        for (Map.Entry<String, Map<String, ServerPlayer>> g : requesters.entrySet()) {
            String group = g.getKey();
            int budget = budgets.getOrDefault(group, 0);
            int free = room.getOrDefault(group, CAP);
            for (Map.Entry<String, ServerPlayer> q : g.getValue().entrySet()) {
                if (budget <= 0 || free <= 0) {
                    break;
                }
                String questKey = q.getKey();
                ServerPlayer requester = q.getValue();
                int missing = missing(requester, questKey, group, alive);
                if (missing <= 0) {
                    continue;
                }
                QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
                PlayerQuestData pqd = QuestParty.questData(requester);
                if (resolved == null || pqd == null) {
                    continue;
                }
                int partySize = Math.max(1, QuestParty.participants(requester, questKey).size());
                int allowed = Math.min(missing, Math.min(budget, free));
                // DMZ's spawn adds the quest's whole shortfall (remaining - alive). When that is more
                // than the kills earned or the cap's room (a multi-count quest), the extras it adds
                // are discarded in the same tick, farthest first, before any client tracks them
                // (3.0.3). A one-kill quest is never over, so it takes the plain path.
                Set<Entity> before = missing > allowed ? questMobs(requester.m_284548_(), questKey) : null;
                QuestServiceInvoker.callSpawnKillObjectives(requester, resolved, partySize, pqd.getQuestDifficulty(questKey));
                int n = before == null ? missing : keepNewest(requester, questKey, before, allowed);
                MeleeDebug.log("[{}] saga top-up {}: budget={} room={} missing={} allowed={} kept={}",
                        DmzMohistMeleeFix.MOD_ID, questKey, budget, free, missing, allowed, n);
                budget -= n;
                free -= n;
                spawned += n;
            }
        }
        // Every spawn and top-up resets the baseline: only later kills earn replacements.
        killSnapshot = progressNow;
        return spawned;
    }

    /** Living mobs in {@code level} tagged with {@code questKey}. */
    private static Set<Entity> questMobs(ServerLevel level, String questKey) {
        Set<Entity> out = new HashSet<>();
        for (Entity e : level.m_8583_()) {
            if (e instanceof LivingEntity living && living.m_6084_()
                    && questKey.equals(PersistentDataAccess.get(e).m_128461_(KEY_TAG))) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Keep {@code allowed} of the mobs DMZ's spawn just added for {@code questKey} (nearest to the
     * requester) and discard the rest. Returns how many were kept.
     */
    private static int keepNewest(ServerPlayer requester, String questKey, Set<Entity> before, int allowed) {
        List<Entity> added = new ArrayList<>(questMobs(requester.m_284548_(), questKey));
        added.removeAll(before);
        added.sort(Comparator.comparingDouble(e -> e.m_20280_(requester)));
        for (int i = allowed; i < added.size(); i++) {
            added.get(i).m_146870_();
        }
        return Math.min(allowed, added.size());
    }

    /** Kill progress summed over the quest's kill objectives. */
    private static int killed(PlayerQuestData pqd, String questKey, Quest quest) {
        int killed = 0;
        for (int i = 0; i < quest.getObjectives().size(); i++) {
            if (quest.getObjectives().get(i) instanceof KillObjective) {
                killed += pqd.getObjectiveProgress(questKey, i);
            }
        }
        return killed;
    }

    /** Progress of a quest no member has accepted any more (completed, failed or abandoned). */
    private static int progressOutsideAccepted(List<ServerPlayer> group, String questKey) {
        Quest quest = QuestRegistry.getQuest(questKey);
        int best = 0;
        if (group == null || quest == null) {
            return 0;
        }
        for (ServerPlayer p : group) {
            PlayerQuestData pqd = QuestParty.questData(p);
            if (pqd != null) {
                best = Math.max(best, killed(pqd, questKey, quest));
            }
        }
        return best;
    }

    /** Enemies DMZ's spawn would add for this quest now: remaining minus alive, per kill objective. */
    private static int missing(ServerPlayer requester, String questKey, String group, Map<String, Integer> alive) {
        Quest quest = QuestRegistry.getQuest(questKey);
        PlayerQuestData pqd = QuestParty.questData(requester);
        if (quest == null || pqd == null) {
            return 0;
        }
        int missing = 0;
        for (int i = 0; i < quest.getObjectives().size(); i++) {
            if (!(quest.getObjectives().get(i) instanceof KillObjective kill) || kill.getSpawnMode() != KillObjective.SpawnMode.QUEST) {
                continue;
            }
            int remaining = quest.getObjectiveRequired(pqd, questKey, i) - pqd.getObjectiveProgress(questKey, i);
            missing += Math.max(0, remaining - alive.getOrDefault(group + "|" + questKey + "|" + i, 0));
        }
        return missing;
    }

    private static boolean hasQuestSpawnedKills(Quest quest) {
        for (QuestObjective o : quest.getObjectives()) {
            if (o instanceof KillObjective kill && kill.getSpawnMode() == KillObjective.SpawnMode.QUEST) {
                return true;
            }
        }
        return false;
    }

    private static QuestObjective objective(String questKey, int index) {
        Quest quest = QuestRegistry.getQuest(questKey);
        if (quest == null || index < 0 || index >= quest.getObjectives().size()) {
            return null;
        }
        return quest.getObjectives().get(index);
    }

    private static ServerPlayer onlineOwner(PlayerList players, String owner) {
        try {
            return players.m_11259_(UUID.fromString(owner));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A party shares one key (its smallest member id); a solo player is their own group. */
    private static String groupKey(ServerPlayer player) {
        Set<String> ids = new HashSet<>();
        ids.add(player.m_20148_().toString());
        for (UUID id : PartyManager.getPartyMemberIds(player)) {
            ids.add(id.toString());
        }
        return ids.stream().min(String::compareTo).orElse(player.m_20148_().toString());
    }

    /** Record a player's hit while vanilla still remembers it (lastHurtByMob, 100 ticks). */
    private static void rememberPlayerHit(LivingEntity mob, long now) {
        if (!(mob.m_21188_() instanceof Player)) {
            return;
        }
        int ago = mob.f_19797_ - mob.m_21213_();
        if (ago >= 0 && ago <= HURT_MEMORY_TICKS) {
            LAST_PLAYER_HIT.put(mob, now - ago);
        }
    }

    /** True when a player hit this enemy within the last 10 s. */
    private static boolean fought(LivingEntity mob, long now) {
        Long hit = LAST_PLAYER_HIT.get(mob);
        return hit != null && now - hit <= FOUGHT_TICKS;
    }
}
