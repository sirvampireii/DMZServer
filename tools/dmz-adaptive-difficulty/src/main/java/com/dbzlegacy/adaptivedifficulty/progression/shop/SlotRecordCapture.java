package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dmzultra.slots.SlotIndex;
import com.dmzultra.slots.SlotService;
import com.dmzultra.slots.SlotStore;
import com.dmzultra.slots.StandAside;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayer;

/**
 * 4.7.43: saves the active character's DMZ Ultra slot record when its player logs out or the server stops.
 * Called only from {@code DmzultraSlotCaptureMixin}, which the mixin plugin applies only when Ultra's
 * {@code SlotService} is installed, so LM runs without Ultra.
 * <p>
 * Ultra 0.4.0 writes {@code slots/<uuid>/slot_N.dat} only on a switch ({@code SwitchEngine.request}).
 * {@code SlotService.logout} rewrites only the index summary and {@code stopped} only clears its maps, so
 * every switch back to a character re-applied the record from its last switch-out and lost everything
 * gained since (Kokopeli0007's main: live STR 168,495 / PWR 142,484, record STR 145,133 / PWR 122,727).
 * <p>
 * The record is built with Ultra's own {@code SlotService.record(player, slot, gen, false)} and written with
 * {@code SlotStore.writeRecord}, exactly as the switch path parks the outgoing character, with the index's
 * current gen. Unlike the switch path there is no {@code DmzPart.toBase} first: nobody switches in, and
 * ending the form would change the live session and the vanilla player save. Live state is captured as-is.
 * <p>
 * Open question, not handled here: Koko's live STR and PWR are both exactly x1.161 of the record's, a uniform
 * multiplier rather than training gains. Cause unknown.
 */
public final class SlotRecordCapture {
    /** The logging-out player's index, read at the head of {@code logout} while Ultra still caches it. */
    private static final ThreadLocal<SlotIndex> LOGOUT_INDEX = new ThreadLocal<>();
    private static Method record;
    private static boolean recordMissing;

    private SlotRecordCapture() {}

    /**
     * Head of {@code SlotService.logout}: remember the index. Ultra's {@code index} reads the cache (or the disk
     * and caches it, since the player is still listed); either way {@code logout} removes the cache entry.
     */
    public static void beforeLogout(Entity entity) {
        LOGOUT_INDEX.remove();
        try {
            if (!(entity instanceof ServerPlayer player) || player instanceof FakePlayer || StandAside.active()) {
                return;
            }
            SlotIndex index = SlotService.index(player.m_20194_(), player.m_20148_());
            LOGOUT_INDEX.set(index);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] could not read {}'s character slots at logout: {}",
                    AdaptiveDifficultyMod.MOD_ID, name(entity), t.toString());
        }
    }

    /** End of {@code SlotService.logout}, after Ultra's index write: save the active character's record. */
    public static void afterLogout(Entity entity) {
        SlotIndex index = LOGOUT_INDEX.get();
        LOGOUT_INDEX.remove();
        if (index != null && entity instanceof ServerPlayer player) {
            capture(player, index, "logout");
        }
    }

    /**
     * Head of {@code SlotService.stopped}, before it clears Ultra's caches: save every player still online.
     * On a normal stop Forge has already logged everyone out (each saved by {@link #afterLogout}), so this
     * covers only players a stop leaves behind.
     */
    public static void onStopped(MinecraftServer server) {
        try {
            if (server == null || StandAside.active()) {
                return;
            }
            for (ServerPlayer player : new ArrayList<>(server.m_6846_().m_11314_())) {
                if (player instanceof FakePlayer) {
                    continue;
                }
                try {
                    capture(player, SlotService.index(server, player.m_20148_()), "server stop");
                } catch (Throwable t) {
                    AdaptiveDifficultyMod.LOGGER.warn("[{}] could not save {}'s character slot at server stop: {}",
                            AdaptiveDifficultyMod.MOD_ID, name(player), t.toString());
                }
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] could not save character slots at server stop: {}",
                    AdaptiveDifficultyMod.MOD_ID, t.toString());
        }
    }

    /**
     * The switch path's capture, without its toBase, at the index's current gen. Like Ultra's own logout summary,
     * it needs an index with an entry for the active slot. Never throws.
     */
    static void capture(ServerPlayer player, SlotIndex index, String when) {
        int slot = -1;
        try {
            MinecraftServer server = player.m_20194_();
            UUID account = player.m_20148_();
            slot = SlotService.activeSlot(player);
            if (server == null || index == null || index.entry(slot) == null) {
                return;
            }
            long gen = index.gen;
            Method m = recordMethod();
            if (m == null) {
                return;
            }
            CompoundTag out = (CompoundTag) m.invoke(null, player, slot, gen, false);
            SlotStore.writeRecord(server, account, slot, out);
            SlotStore.log(server, "lm-capture " + account + " (" + name(player) + ") slot " + slot + " gen " + gen
                    + " at " + when);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] could not save {}'s character slot {} at {}: {}",
                    AdaptiveDifficultyMod.MOD_ID, name(player), slot + 1, when, t.toString());
        }
    }

    /** Ultra's package-private {@code SlotService.record(ServerPlayer, int, long, boolean)}, or null if missing. */
    private static synchronized Method recordMethod() {
        if (record == null && !recordMissing) {
            try {
                Method m = SlotService.class.getDeclaredMethod("record", ServerPlayer.class, int.class, long.class,
                        boolean.class);
                m.setAccessible(true);
                record = m;
            } catch (Throwable t) {
                recordMissing = true;
                AdaptiveDifficultyMod.LOGGER.warn("[{}] DMZ Ultra has no SlotService.record - character slots are "
                        + "not saved at logout: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            }
        }
        return record;
    }

    private static String name(Entity entity) {
        try {
            return entity instanceof ServerPlayer p ? p.m_36316_().getName() : String.valueOf(entity);
        } catch (Throwable t) {
            return "?";
        }
    }
}
