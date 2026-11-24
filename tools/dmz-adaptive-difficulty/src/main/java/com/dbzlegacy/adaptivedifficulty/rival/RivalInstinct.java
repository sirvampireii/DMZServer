package com.dbzlegacy.adaptivedifficulty.rival;

import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Status;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rival Instinct alerts for mutual / nemesis rivals (arrive / BP / charging / form / fusion).
 */
public final class RivalInstinct {
    private static final Map<UUID, Long> LAST_PULSE = new ConcurrentHashMap<>();
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> WAS_NEAR = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> LAST_FORM_MULT = new ConcurrentHashMap<>();

    private RivalInstinct() {}

    public static boolean isEnabled(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        RivalPlayerRecord rec = RivalStore.get().ensurePlayer(player);
        return rec == null || rec.instinctEnabled;
    }

    public static boolean toggle(ServerPlayer player) {
        RivalPlayerRecord rec = RivalStore.get().ensurePlayer(player);
        if (rec == null) {
            return false;
        }
        rec.instinctEnabled = !rec.instinctEnabled;
        RivalStore.get().markDirty();
        return rec.instinctEnabled;
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        LAST_PULSE.remove(uuid);
        LAST_FORM_MULT.remove(uuid);
        String prefix = uuid + ">";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
        WAS_NEAR.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void pulse(MinecraftServer server, long now) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableRivalSystem || !cfg.rivalInstinct || server == null) {
            return;
        }
        RivalStore store = RivalStore.get();
        for (ServerPlayer player : server.m_6846_().m_11314_()) {
            if (player == null || !player.m_6084_()) {
                continue;
            }
        UUID id = player.m_20148_();
        if (RivalChallengeManager.get().isInChallenge(id)) {
            continue;
        }
        RivalPlayerRecord instinctRec = store.ensurePlayer(player);
        if (instinctRec != null && !instinctRec.instinctEnabled) {
            continue;
        }
        Long last = LAST_PULSE.get(id);
            if (last != null && now - last < RivalConstants.INSTINCT_PULSE_MS) {
                continue;
            }
            LAST_PULSE.put(id, now);
            process(server, store, player, now);
        }
    }

    private static void process(MinecraftServer server, RivalStore store, ServerPlayer player, long now) {
        RivalPlayerRecord record = store.ensurePlayer(player);
        if (record == null) {
            return;
        }
        UUID id = player.m_20148_();
        double myReleased = DmzRewards.releasedBattlePower(player);

        for (Map.Entry<String, RivalLink> e : record.rivals.entrySet()) {
            RivalLink link = e.getValue();
            if (link == null || !link.mutual) {
                continue;
            }
            RivalStatus st = link.status();
            if (st != RivalStatus.MUTUAL && st != RivalStatus.NEMESIS) {
                continue;
            }
            UUID rivalId;
            try {
                rivalId = UUID.fromString(e.getKey());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String nearKey = id + ">" + rivalId;
            ServerPlayer rival = server.m_6846_().m_11259_(rivalId);
            double range = RivalConstants.rangeForPoints(link.points);
            boolean near = rival != null
                    && rival.m_6084_()
                    && rival.m_9236_() == player.m_9236_()
                    && player.m_20270_(rival) <= range;

            boolean wasNear = Boolean.TRUE.equals(WAS_NEAR.get(nearKey));
            if (!near) {
                WAS_NEAR.put(nearKey, false);
                continue;
            }
            WAS_NEAR.put(nearKey, true);
            double dist = player.m_20270_(rival);
            double theirReleased = DmzRewards.releasedBattlePower(rival);
            String tag = st == RivalStatus.NEMESIS ? "Nemesis" : "Mutual";

            if (!wasNear) {
                String arriveHead = "§e" + link.name + " §7arrived (" + (int) dist + "m)";
                String arriveTail = " §7— feels " + relative(myReleased, theirReleased) + " §8[" + tag + "]";
                String arrive = LmChat.note("Instinct", arriveHead) + arriveTail;
                alert(nearKey + ":arrive", "arrive", arrive, arriveHead + arriveTail, rivalId,
                        player, now, RivalConstants.INSTINCT_ARRIVE_CD_MS);
            } else {
                String bp = "§e" + link.name + " §7still nearby — released BP ~ §f"
                        + DmzRewards.formatWhole(theirReleased);
                alert(nearKey + ":bp", "status", LmChat.info("Instinct", bp), bp, rivalId,
                        player, now, RivalConstants.INSTINCT_STATUS_CD_MS);
            }

            try {
                StatsData data = DmzProgression.stats(rival);
                Status status = data == null ? null : data.getStatus();
                if (status != null) {
                    if (status.isChargingKi() || status.isActionCharging()) {
                        String body = "§e" + link.name + " §cis charging ki!";
                        alert(nearKey + ":charge", "charge", LmChat.fail("Instinct", body), body, rivalId,
                                player, now, RivalConstants.INSTINCT_EVENT_CD_MS);
                    }
                    if (status.isAuraActive()) {
                        String body = "§e" + link.name + " §dtransformation / aura surge!";
                        alert(nearKey + ":aura", "surge", LmChat.note("Instinct", body), body, rivalId,
                                player, now, RivalConstants.INSTINCT_EVENT_CD_MS);
                    }
                    if (status.getFusionPartnerUUID() != null || status.isFusionLeader()) {
                        String fname = status.getFusionName();
                        String body = "§e" + link.name + " §5is fused"
                                + (fname == null || fname.isBlank() ? "!" : " (" + fname + ")!");
                        alert(nearKey + ":fusion", "fusion", LmChat.note("Instinct", body), body, rivalId,
                                player, now, RivalConstants.INSTINCT_EVENT_CD_MS);
                    }
                }
                double formMult = PlayerCombatProfile.liveFormMultiplier(rival);
                Double prev = LAST_FORM_MULT.put(rivalId, formMult);
                // Detect real form spikes via form⊕stack mult (not CR/BP) so Android
                // Inf BP and released-stat CR cannot false-trigger or miss surges.
                if (prev != null && formMult > prev * 1.15 && formMult > 1.12) {
                    String body = "§e" + link.name + " §dpower surged!";
                    alert(nearKey + ":form", "power", LmChat.note("Instinct", body), body, rivalId,
                            player, now, RivalConstants.INSTINCT_EVENT_CD_MS);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * One Instinct alert, on the same cooldowns as always. A native HUD player gets a card (kind, then the chat
     * line's text without the "Instinct" tag; a click opens that rival) instead of the chat line.
     */
    private static void alert(String key, String kind, String message, String card, UUID rivalId,
            ServerPlayer player, long now, long cd) {
        Long last = COOLDOWNS.get(key);
        if (last != null && now - last < cd) {
            return;
        }
        COOLDOWNS.put(key, now);
        deliver(player, kind, message, card, rivalId);
    }

    /** Chat line or HUD card for one alert (the UI probe calls this to compare the two). */
    public static void deliver(ServerPlayer player, String kind, String message, String card, UUID rivalId) {
        if (com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.active(player)) {
            com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.show(player, com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.INSTINCT, java.util.List.of(kind, card), -1f,
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.ACCENT_RIVAL, rivalId == null ? "open:rival" : "go:rival|list_detail:" + rivalId,
                    com.dbzlegacy.adaptivedifficulty.gui.ultra.LmHud.INSTINCT_MS, false);
            return;
        }
        DmzRewards.msg(player, message);
    }

    private static String relative(double mine, double theirs) {
        if (!(mine > 0.0) || !(theirs > 0.0)) {
            return "unclear";
        }
        double ratio = theirs / mine;
        if (ratio >= 1.35) {
            return "much stronger";
        }
        if (ratio >= 1.10) {
            return "stronger";
        }
        if (ratio <= 0.75) {
            return "much weaker";
        }
        if (ratio <= 0.90) {
            return "weaker";
        }
        return "even";
    }
}
