package com.dbzlegacy.adaptivedifficulty.rival;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Boss bar shown while a player stands in a Proving Grounds claim of one of their own rival pairs.
 * <p>
 * Display only: it never gates, changes, or repeats a grounds bonus. One bar per player, kept in
 * its own map (not the death-penalty bar, not the TP flash bar). Checked once a second from the
 * player's own tick against that player's own rival links only; the bar is created on entering,
 * renamed only when its text changes, and removed on leaving, logout, or a dropped claim.
 */
public final class ProvingGroundsBar {
    private static final ConcurrentHashMap<UUID, ServerBossEvent> BARS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> TEXT = new ConcurrentHashMap<>();

    private ProvingGroundsBar() {}

    /** Player-tick hook (call once a second). */
    public static void tick(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String text = DifficultyConfig.get().enableRivalSystem ? textFor(player) : null;
        if (text == null) {
            hide(player);
        } else {
            show(player, text);
        }
    }

    public static void onLogout(ServerPlayer player) {
        hide(player);
    }

    /** Bar text for the first own-pair claim the player stands in, or null when none. */
    static String textFor(ServerPlayer player) {
        String myUuid = player.m_20148_().toString();
        RivalPlayerRecord me = RivalStore.get().get(myUuid);
        if (me == null || me.rivals == null || me.rivals.isEmpty()) {
            return null;
        }
        for (RivalLink link : me.rivals.values()) {
            if (link == null || link.provingGrounds == null || !link.provingGrounds.active) {
                continue;
            }
            ProvingGrounds.Grounds pg = ProvingGrounds.normalize(link.provingGrounds);
            if (!pg.active || !ProvingGrounds.onGrounds(player, pg)) {
                continue;
            }
            return barText(pg, myUuid);
        }
        return null;
    }

    static String barText(ProvingGrounds.Grounds pg, String myUuid) {
        String tier = pg.tier >= 3 ? "Legendary" : (pg.tier >= 2 ? "Dominant" : "Claimed");
        String champion = pg.championName == null || pg.championName.isBlank() ? "-" : pg.championName;
        String role;
        if (pg.championUuid == null || pg.championUuid.isBlank()) {
            role = "No champion yet";
        } else if (pg.championUuid.equals(myUuid)) {
            role = "You are the champion";
        } else {
            role = "You are the underdog challenger";
        }
        return ProvingGrounds.displayName(pg) + " - " + tier + " - Champion " + champion
                + " - " + pg.battles + (pg.battles == 1 ? " battle" : " battles") + " - " + role;
    }

    private static void show(ServerPlayer player, String text) {
        try {
            UUID id = player.m_20148_();
            ServerBossEvent bar = BARS.get(id);
            if (bar == null) {
                bar = new ServerBossEvent(
                        Component.m_237113_(text),
                        BossEvent.BossBarColor.YELLOW,
                        BossEvent.BossBarOverlay.PROGRESS);
                bar.m_7003_(false);
                bar.m_7005_(false);
                bar.m_7006_(false);
                bar.m_142711_(1.0f);
                BARS.put(id, bar);
                TEXT.put(id, text);
            } else if (!text.equals(TEXT.get(id))) {
                bar.m_6456_(Component.m_237113_(text));
                TEXT.put(id, text);
            }
            // A respawn makes a new player object: move the bar over instead of rebuilding it.
            if (!bar.m_8324_().contains(player)) {
                bar.m_7706_();
                bar.m_6543_(player);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hide(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.m_20148_();
        TEXT.remove(id);
        ServerBossEvent bar = BARS.remove(id);
        if (bar == null) {
            return;
        }
        try {
            bar.m_6539_(player);
            bar.m_7706_();
        } catch (Throwable ignored) {
        }
    }
}
