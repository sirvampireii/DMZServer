package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.LmChat;
import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dragonminez.common.stats.StatsData;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * Port of {@code Prestige NPC.js} purchase logic as {@code /prestige} chat GUI.
 * <p>
 * The requirement is the player's level cap as dmzrevamp's Overhaul has it
 * ({@link #requiredLevel(ServerPlayer)}): reaching your current cap earns your next prestige.
 * Overhaul's native cap rises with the prestige count LM sets from held prestige (50k, 65k, 80k …
 * 150k with the pinned 50k / 150k / 10). The most you can hold is Overhaul's {@code maxPrestigeCount}
 * ({@link #maxHeld()}).
 *
 * <p><b>Lifetime completed</b> never drops when held prestige is turned in.
 */
public final class PrestigeSystem {
    private static final long CONFIRM_MS = 10_000L;
    /** Live Prestige NPC.js — held tokens live on CNPC faction 4. */
    private static final int FACTION_HELD_ID = 4;

    private static final String KEY_TOTAL = "prestige_total_completed";
    /**
     * When set, {@link #getCompleted} returns stored {@link #KEY_TOTAL} only — staff
     * {@code /padmin completed set 0} is not bumped by DMZ skill or shop inference.
     */
    private static final String KEY_TOTAL_STAFF_OVERRIDE = "prestige_total_staff_override";
    private static final String KEY_HELD = "lm_prestige_held";
    private static final String KEY_CONFIRM_UNTIL = "lm_prestige_confirm_until";

    private static final Map<UUID, Long> CONFIRM_UNTIL = new ConcurrentHashMap<>();

    private PrestigeSystem() {}

    public static void open(ServerPlayer player) {
        if (!DifficultyConfig.get().enablePrestigeSystem || player == null) {
            return;
        }
        showStatus(player);
    }

    public static String confirmOrPrompt(ServerPlayer player) {
        if (!DifficultyConfig.get().enablePrestigeSystem || player == null) {
            return "§cPrestige system is disabled.";
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return reply(player, "§cCould not read your Dragon Mine Z stats.");
        }
        int level;
        try {
            level = Math.max(0, data.getLevel());
        } catch (Throwable t) {
            return reply(player, "§cCould not determine your Dragon Mine Z level.");
        }

        int completed = getCompleted(player);
        int held = getHeld(player);
        int required = requiredLevel(player);
        int next = completed + 1;

        if (held >= maxHeld()) {
            return replyCap(player, held);
        }
        if (!meetsRequirement(level, required)) {
            return replyRequirement(player, completed, next, required, level);
        }

        long now = System.currentTimeMillis();
        Long until = CONFIRM_UNTIL.get(player.m_20148_());
        CompoundTag tag = LmPlayerStore.root(player);
        if (until == null && PersistentDataAccess.isWritable(tag) && tag.m_128441_(KEY_CONFIRM_UNTIL)) {
            until = tag.m_128454_(KEY_CONFIRM_UNTIL);
        }

        if (until != null && until > now) {
            return purchase(player, data, completed, required, level);
        }

        long confirmUntil = now + CONFIRM_MS;
        CONFIRM_UNTIL.put(player.m_20148_(), confirmUntil);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128356_(KEY_CONFIRM_UNTIL, confirmUntil);
        }
        return replyConfirm(player, completed, next, required, level, held);
    }

    /**
     * Ultra GUI confirm subpage - run the prestige purchase when eligible.
     * Skips the chat double-click prompt (confirmation already happened in the GUI).
     */
    public static String confirmFromGui(ServerPlayer player) {
        if (!DifficultyConfig.get().enablePrestigeSystem || player == null) {
            return "§cPrestige system is disabled.";
        }
        StatsData data = DmzProgression.stats(player);
        if (data == null) {
            return "§cCould not read your Dragon Mine Z stats.";
        }
        int level;
        try {
            level = Math.max(0, data.getLevel());
        } catch (Throwable t) {
            return "§cCould not determine your Dragon Mine Z level.";
        }
        int completed = getCompleted(player);
        int held = getHeld(player);
        int required = requiredLevel(player);
        if (held >= maxHeld()) {
            return replyCap(player, held);
        }
        if (!meetsRequirement(level, required)) {
            return replyRequirement(player, completed, completed + 1, required, level);
        }
        return purchase(player, data, completed, required, level);
    }

    private static String purchase(
            ServerPlayer player, StatsData data, int completed, int required, int level
    ) {
        CONFIRM_UNTIL.remove(player.m_20148_());
        CompoundTag tag = LmPlayerStore.root(player);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128473_(KEY_CONFIRM_UNTIL);
        }

        int held = getHeld(player);
        if (held >= maxHeld()) {
            return replyCap(player, held);
        }
        if (!meetsRequirement(level, required)) {
            return replyRequirement(player, completed, completed + 1, required, level);
        }

        int newHeld = held + 1;
        int newCompleted = completed + 1;
        setHeld(player, newHeld);
        setCompleted(player, newCompleted);
        resetPrestigeProgress(player);

        // Players cannot run /dmzstats reset. Prestige wipes the same way that command does.
        try {
            data.resetPlayerProgress(player, 0, false, false);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] prestige stat reset failed for {}: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    player.m_7755_().getString(),
                    t.toString());
        }
        // Prestige-point skill floors + Permanent Majin/Mutant must survive dmzstats reset.
        PrestigePointsSystem.scheduleReapplyAfterPrestige(player);
        try {
            com.dbzlegacy.adaptivedifficulty.progression.PrestigeResourceRecovery.afterDmzStatsReset(player);
        } catch (Throwable ignored) {
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .scheduleSyncAfterStatsReset(player);
        } catch (Throwable ignored) {
        }

        int nextRequired = requiredLevel(player);
        String summary = "§aLifetime prestiges §f" + newCompleted + "§a.\n"
                + "§7Current prestige amount §6" + newHeld + "§7/§f" + maxHeld() + ".\n"
                + "§7Reach your cap of §e" + DmzRewards.formatWhole(nextRequired) + " §7again for the next one.";
        if (!preferGuiFeedback()) {
            send(player, "");
            send(player, LmChat.DIVIDER);
            send(player, "§aLifetime prestiges §f" + newCompleted + "§a.");
            send(player, "§7Current prestige amount §6" + newHeld + "§7/§f" + maxHeld() + ".");
            send(player, "§7Reach your cap of §e" + DmzRewards.formatWhole(nextRequired) + " §7again for the next one.");
            send(player, LmChat.DIVIDER);
        }
        SystemTelemetry.log("prestige", "purchase", player, null, Map.of(
                "completed", newCompleted,
                "held", newHeld,
                "required", required
        ));
        return summary;
    }

    private static void showStatus(ServerPlayer player) {
        StatsData data = DmzProgression.stats(player);
        int level = 0;
        if (data != null) {
            try {
                level = data.getLevel();
            } catch (Throwable ignored) {
            }
        }
        int completed = getCompleted(player);
        int held = getHeld(player);
        int required = requiredLevel(player);
        send(player, "");
        send(player, "§8── §6Prestige §8──");
        send(player, "§7Lifetime prestiges: §f" + completed);
        send(player, "§7Current prestige amount §6" + held + "§7/§f" + maxHeld()
                + " §7· level §f" + DmzRewards.formatWhole(level));
        send(player, "§7Your cap: §e" + DmzRewards.formatWhole(required)
                + " §7power. Reaching your current cap earns your next prestige.");
        MutableComponent row = Component.m_237113_("§7")
                .m_7220_(btn("§a[Prestige]", "/prestige do confirm", "Confirm prestige purchase"))
                .m_7220_(Component.m_237113_("  "))
                .m_7220_(btn("§7[Refresh]", "/prestige", "Refresh status"));
        send(player, row);
    }

    private static String replyConfirm(
            ServerPlayer player, int completed, int next, int required, int level, int held
    ) {
        boolean goD = PrestigeUniverseRoleWarn.isGodOfDestruction(player);
        boolean kai = PrestigeUniverseRoleWarn.isSupremeKai(player);
        StringBuilder summary = new StringBuilder();
        summary.append("§ePrestige now?\n")
                .append("§7This resets your stats. Click again within 10 seconds.\n")
                .append("§7Current prestige amount will be §6")
                .append(held + 1)
                .append("§7/§f")
                .append(maxHeld())
                .append(".");
        if (goD) {
            summary.append('\n').append(UltraStyle.DANGER).append(PrestigeUniverseRoleWarn.GOD_OF_DESTRUCTION);
        }
        if (kai) {
            summary.append('\n').append(UltraStyle.DANGER).append(PrestigeUniverseRoleWarn.SUPREME_KAI);
        }
        if (!preferGuiFeedback()) {
            send(player, "");
            send(player, LmChat.DIVIDER);
            send(player, "§ePrestige now?");
            send(player, "§7This resets your stats and adds one to your current prestige amount.");
            send(player, "§7Your level §f" + DmzRewards.formatWhole(level)
                    + " §7is enough (needs §e" + DmzRewards.formatWhole(required) + "§7).");
            send(player, "§7Current prestige amount will be §6" + (held + 1) + "§7/§f" + maxHeld() + ".");
            if (goD) {
                send(player, UltraStyle.DANGER + PrestigeUniverseRoleWarn.GOD_OF_DESTRUCTION);
            }
            if (kai) {
                send(player, UltraStyle.DANGER + PrestigeUniverseRoleWarn.SUPREME_KAI);
            }
            send(player, "§7Click again within 10 seconds.");
            MutableComponent row = Component.m_237113_(UltraStyle.LABEL)
                    .m_7220_(btn(UltraStyle.CONFIRM + "[Confirm Prestige]", "/prestige do confirm", "Complete prestige"))
                    .m_7220_(Component.m_237113_("  "))
                    .m_7220_(btn(UltraStyle.DANGER + "[Cancel]", "/prestige", "Cancel"));
            send(player, row);
            send(player, LmChat.DIVIDER);
        }
        return summary.toString();
    }

    private static String replyCap(ServerPlayer player, int held) {
        String summary = UltraStyle.DANGER + "You're holding as many as you can.\n"
                + UltraStyle.LABEL + "That's " + UltraStyle.GOLD + held + UltraStyle.LABEL + "/"
                + UltraStyle.VALUE + maxHeld() + ".\n"
                + UltraStyle.LABEL + "Spend one, then you can prestige again.";
        if (!preferGuiFeedback()) {
            send(player, "");
            send(player, LmChat.DIVIDER);
            send(player, UltraStyle.DANGER + "You're holding as many as you can.");
            send(player, UltraStyle.LABEL + "That's " + UltraStyle.GOLD + held + UltraStyle.LABEL + "/"
                    + UltraStyle.VALUE + maxHeld() + ".");
            send(player, UltraStyle.LABEL + "Spend one, then you can prestige again.");
            send(player, LmChat.DIVIDER);
        }
        return summary;
    }

    private static String replyRequirement(
            ServerPlayer player, int completed, int next, int required, int level
    ) {
        String summary = UltraStyle.DANGER + "Not high enough yet.\n"
                + UltraStyle.LABEL + "Reaching your current cap of " + UltraStyle.YELLOW
                + DmzRewards.formatWhole(required) + UltraStyle.LABEL + " power earns your next prestige.\n"
                + UltraStyle.LABEL + "You're at " + UltraStyle.VALUE + DmzRewards.formatWhole(level)
                + UltraStyle.LABEL + ".";
        if (!preferGuiFeedback()) {
            send(player, "");
            send(player, LmChat.DIVIDER);
            send(player, UltraStyle.DANGER + "Not high enough yet.");
            send(player, UltraStyle.LABEL + "Reaching your current cap of " + UltraStyle.YELLOW
                    + DmzRewards.formatWhole(required) + UltraStyle.LABEL + " power earns your next prestige.");
            send(player, UltraStyle.LABEL + "You're at " + UltraStyle.VALUE
                    + DmzRewards.formatWhole(level) + UltraStyle.LABEL + ".");
            send(player, UltraStyle.LABEL + "Prestige: " + UltraStyle.VALUE + completed);
            send(player, LmChat.DIVIDER);
        }
        return summary;
    }

    private static String reply(ServerPlayer player, String summary) {
        if (!preferGuiFeedback()) {
            DmzRewards.msg(player, summary);
        }
        return summary;
    }

    /** Inventory/CMI backends show results in the GUI header; chat backend keeps chat. */
    private static boolean preferGuiFeedback() {
        try {
            String backend = DifficultyConfig.get().guiBackend;
            if (backend == null || backend.isBlank()) {
                return true;
            }
            return !"chat".equalsIgnoreCase(backend.trim());
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * The next prestige's DMZ level gate: the player's level cap as Overhaul has it
     * ({@code PrestigeSystem.levelCap} through {@code DmzRevampPrestigeBridge}), which rises with held
     * prestige. LM adds no ladder of its own, and no stored need floor is read.
     */
    public static int requiredLevel(ServerPlayer player) {
        StatsData data = player == null ? null : DmzProgression.stats(player);
        return com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge.levelCap(data);
    }

    /** The gate: DMZ level at or above the cap. */
    public static boolean meetsRequirement(int level, int cap) {
        return level >= cap;
    }

    /** Most prestiges a player can hold: dmzrevamp's {@code maxPrestigeCount} (10 without it). */
    public static int maxHeld() {
        return com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath.configuredMaxPrestigeCount();
    }

    /** Held count clamped to 0..max. */
    public static int clampHeld(int held, int max) {
        return Math.max(0, Math.min(Math.max(0, max), held));
    }

    /** NBT held wallet only (not CNPC faction inflate). */
    public static int getHeldWallet(ServerPlayer player) {
        return heldCountForNeed(player);
    }

    /** Wallet NBT for Need — never CNPC faction (avoids 150k held≥3 gates). */
    public static int heldCountForNeed(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        CompoundTag tag = LmPlayerStore.root(player);
        if (PersistentDataAccess.isWritable(tag) && tag.m_128441_(KEY_HELD)) {
            return clampHeld(readStoredInt(tag, KEY_HELD), maxHeld());
        }
        return 0;
    }

    /**
     * First sync after Fabled: if the wallet was never written, keep the held number
     * that already lives on faction 4, the DMZ prestige skill, or the Overhaul count.
     */
    public static void ensureHeldRecorded(ServerPlayer player, int overhaulCount) {
        if (player == null) {
            return;
        }
        CompoundTag tag = LmPlayerStore.root(player);
        if (!PersistentDataAccess.isWritable(tag) || tag.m_128441_(KEY_HELD)) {
            return;
        }
        int faction = 0;
        Integer factionPoints = readFactionPoints(player, FACTION_HELD_ID);
        if (factionPoints != null) {
            faction = Math.max(0, factionPoints);
        }
        int skill = Math.max(0, DmzProgression.prestige(player));
        int seed = Math.max(0, Math.max(overhaulCount, Math.max(faction, skill)));
        seed = clampHeld(seed, maxHeld());
        tag.m_128405_(KEY_HELD, seed);
        mirrorFactionHeld(player, seed);
    }

    /**
     * Lifetime prestiges completed. Never decreases when held prestige is
     * spent on turn-in (DMZ {@code prestige} skill tracks the held wallet, not lifetime).
     */
    public static int getCompleted(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        CompoundTag tag = LmPlayerStore.root(player);
        if (staffOverrideActive(tag)) {
            return Math.max(0, readStoredInt(tag, KEY_TOTAL));
        }
        boolean hasKey = PersistentDataAccess.isWritable(tag) && tag.m_128441_(KEY_TOTAL);
        int stored = readStoredInt(tag, KEY_TOTAL);

        if (!hasKey) {
            // One-time migrate from the DMZ prestige skill when the wallet total is missing.
            int legacy = Math.max(0, DmzProgression.prestige(player));
            if (legacy > 0) {
                setCompletedPublic(player, legacy);
                return legacy;
            }
            // Shop evidence of a past prestige when total was zeroed by turn-in.
            if (inferCompletedFromShop(player)) {
                setCompletedPublic(player, 1);
                return 1;
            }
            return 0;
        }

        // DMZ prestige skill tracks the held wallet, not lifetime completed — never bump total from it.
        if (stored <= 0 && inferCompletedFromShop(player)) {
            setCompletedPublic(player, 1);
            return 1;
        }
        return Math.max(0, stored);
    }

    /** Points or permanent forms imply at least one completed prestige. */
    private static boolean inferCompletedFromShop(ServerPlayer player) {
        try {
            if (PrestigePointsSystem.getPoints(player) > 0) {
                return true;
            }
            if (PrestigePointsSystem.hasMajin(player)
                    || PrestigePointsSystem.hasMutant(player)
                    || PrestigePointsSystem.hasUltimate(player)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
    private static int readStoredInt(CompoundTag tag, String key) {
        if (!PersistentDataAccess.isWritable(tag) || !tag.m_128441_(key)) {
            return 0;
        }
        try {
            return Integer.parseInt(tag.m_128461_(key));
        } catch (Exception e) {
            return (int) tag.m_128451_(key);
        }
    }

    private static boolean staffOverrideActive(CompoundTag tag) {
        return PersistentDataAccess.isWritable(tag) && tag.m_128441_(KEY_TOTAL_STAFF_OVERRIDE)
                && tag.m_128471_(KEY_TOTAL_STAFF_OVERRIDE);
    }

    private static void clearStaffOverride(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CompoundTag tag = LmPlayerStore.root(player);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128473_(KEY_TOTAL_STAFF_OVERRIDE);
        }
    }
    public static int getHeld(ServerPlayer player) {
        CompoundTag tag = LmPlayerStore.root(player);
        if (PersistentDataAccess.isWritable(tag) && tag.m_128441_(KEY_HELD)) {
            return heldCountForNeed(player);
        }
        // Wallet not written yet — old Prestige NPC stored held on CNPC faction 4.
        Integer faction = readFactionPoints(player, FACTION_HELD_ID);
        if (faction != null) {
            return clampHeld(faction, maxHeld());
        }
        return 0;
    }

    private static void setCompleted(ServerPlayer player, int value) {
        clearStaffOverride(player);
        setCompletedPublic(player, value);
    }

    /** Staff {@code /padmin completed …} — value sticks even when DMZ skill / shop would infer higher. */
    public static void setCompletedStaff(ServerPlayer player, int value) {
        if (player == null) {
            return;
        }
        int clamped = Math.max(0, value);
        CompoundTag tag = LmPlayerStore.root(player);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128359_(KEY_TOTAL, Integer.toString(clamped));
            tag.m_128379_(KEY_TOTAL_STAFF_OVERRIDE, true);
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .syncFromLegacy(player);
        } catch (Throwable ignored) {
        }
    }

    /** Public for staff admin tools. */
    public static void setCompletedPublic(ServerPlayer player, int value) {
        CompoundTag tag = LmPlayerStore.root(player);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128359_(KEY_TOTAL, Integer.toString(Math.max(0, value)));
        }
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .syncFromLegacy(player);
        } catch (Throwable ignored) {
        }
    }

    private static void setHeld(ServerPlayer player, int value) {
        setHeldPublic(player, value);
    }

    /** Public for prestige-points turn-in and padmin. Wallet NBT is source of truth. */
    public static void setHeldPublic(ServerPlayer player, int value) {
        int clamped = clampHeld(value, maxHeld());
        CompoundTag tag = LmPlayerStore.root(player);
        if (PersistentDataAccess.isWritable(tag)) {
            tag.m_128405_(KEY_HELD, clamped);
        }
        mirrorFactionHeld(player, clamped);
        try {
            com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge
                    .scheduleSyncAfterStatsReset(player);
        } catch (Throwable ignored) {
        }
    }

    /** Live Prestige NPC.js — clear saga quests/dialogs after purchase. */
    private static void resetPrestigeProgress(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int[] quests = {26, 2, 27};
        int[] dialogs = {21, 20, 18, 19};
        try {
            Class<?> npcApi = Class.forName("noppes.npcs.api.NpcAPI");
            Object available = npcApi.getMethod("IsAvailable").invoke(null);
            if (!(available instanceof Boolean ok) || !ok) {
                return;
            }
            Object api = npcApi.getMethod("Instance").invoke(null);
            Object entity = api.getClass()
                    .getMethod("getIEntity", net.minecraft.world.entity.Entity.class)
                    .invoke(api, player);
            if (entity == null) {
                return;
            }
            for (int q : quests) {
                try {
                    entity.getClass().getMethod("removeQuest", int.class).invoke(entity, q);
                } catch (Throwable ignored) {
                }
            }
            for (int d : dialogs) {
                try {
                    entity.getClass().getMethod("removeDialog", int.class).invoke(entity, d);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Integer readFactionPoints(ServerPlayer player, int factionId) {
        try {
            Class<?> npcApi = Class.forName("noppes.npcs.api.NpcAPI");
            Object available = npcApi.getMethod("IsAvailable").invoke(null);
            if (available instanceof Boolean ok && ok) {
                Object api = npcApi.getMethod("Instance").invoke(null);
                Object entity = api.getClass()
                        .getMethod("getIEntity", net.minecraft.world.entity.Entity.class)
                        .invoke(api, player);
                if (entity != null) {
                    Object pts = entity.getClass()
                            .getMethod("getFactionPoints", int.class)
                            .invoke(entity, factionId);
                    if (pts instanceof Number n) {
                        return n.intValue();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Keep CNPC faction 4 equal to the wallet so a stale faction cannot disagree with Overhaul. */
    private static void mirrorFactionHeld(ServerPlayer player, int held) {
        int target = clampHeld(held, maxHeld());
        Integer current = readFactionPoints(player, FACTION_HELD_ID);
        if (current == null || current == target) {
            return;
        }
        addFactionPoints(player, FACTION_HELD_ID, target - current);
    }

    private static boolean addFactionPoints(ServerPlayer player, int factionId, int delta) {
        try {
            Class<?> npcApi = Class.forName("noppes.npcs.api.NpcAPI");
            Object available = npcApi.getMethod("IsAvailable").invoke(null);
            if (available instanceof Boolean ok && ok) {
                Object api = npcApi.getMethod("Instance").invoke(null);
                Object entity = api.getClass()
                        .getMethod("getIEntity", net.minecraft.world.entity.Entity.class)
                        .invoke(api, player);
                if (entity != null) {
                    entity.getClass()
                            .getMethod("addFactionPoints", int.class, int.class)
                            .invoke(entity, factionId, delta);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static MutableComponent btn(String label, String command, String hover) {
        return Component.m_237113_(label).m_6270_(Style.f_131099_
                .m_131142_(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .m_131144_(new HoverEvent(HoverEvent.Action.f_130831_, Component.m_237113_(hover))));
    }

    private static void send(ServerPlayer player, String text) {
        if (text == null || text.isEmpty()) {
            player.m_213846_(Component.m_237113_(""));
            return;
        }
        LmChat.send(player, text);
    }

    private static void send(ServerPlayer player, Component text) {
        player.m_213846_(text);
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid != null) {
            CONFIRM_UNTIL.remove(uuid);
        }
    }
}
