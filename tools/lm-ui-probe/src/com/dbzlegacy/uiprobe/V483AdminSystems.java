package com.dbzlegacy.uiprobe;

import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.data.LmPlayerStore;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.goals.WeeklyGoals;
import com.dbzlegacy.adaptivedifficulty.progression.shop.SkillCheckService;
import com.dbzlegacy.adaptivedifficulty.quest.SagaResetService;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.stats.StatsData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * LM 4.8.3 Parts D-G (test only): {@code /lm admin saga}, {@code /lm admin skillcheck}, {@code /lm admin goals} and
 * {@code /lm admin difficulty tier}. Every leaf runs once from the console and once as the staff probe player, both
 * aimed at the probe player, and the check reads the state the leaf changed. The probe player's quest data, Skill
 * Check session, weekly-goal counters and tier grants are put back afterwards.
 *
 * <p>Lines: {@code V483 SYS <what>: PASS|FAIL | detail}.
 */
public final class V483AdminSystems {
    private V483AdminSystems() {}

    private static final String WEEKLY_KEY = "lm_weekly_goals";

    public static void run(ServerPlayer player, Consumer<String> note) {
        MinecraftServer server = player.m_20194_();
        CommandSourceStack console = server.m_129893_();
        CommandSourceStack self = player.m_20203_();
        String name = player.m_36316_().getName();
        try {
            help(server, console, note);
        } catch (Throwable t) {
            note.accept("V483 SYS help ERROR: FAIL | " + t);
        }
        try {
            saga(server, console, self, player, name, note);
        } catch (Throwable t) {
            note.accept("V483 SYS saga ERROR: FAIL | " + t);
        }
        try {
            skillCheck(server, console, self, player, name, note);
        } catch (Throwable t) {
            note.accept("V483 SYS skillcheck ERROR: FAIL | " + t);
        }
        try {
            goals(server, console, self, player, name, note);
        } catch (Throwable t) {
            note.accept("V483 SYS goals ERROR: FAIL | " + t);
        }
        try {
            tier(server, console, self, player, name, note);
        } catch (Throwable t) {
            note.accept("V483 SYS tier ERROR: FAIL | " + t);
        }
    }

    // ---- help ------------------------------------------------------------------------------------------------------

    private static void help(MinecraftServer server, CommandSourceStack console, Consumer<String> note) {
        String saga = String.join(" ", run(server, console, "lm admin saga"));
        String sagaHelp = String.join(" ", run(server, console, "lm admin saga help"));
        note.accept("V483 SYS /lm admin saga (bare and help) lists reset and unlock: " + pf(saga.contains("saga reset")
                && saga.contains("saga unlock") && sagaHelp.equals(saga)) + " | " + clip(saga));
        String sc = String.join(" ", run(server, console, "lm admin skillcheck help"));
        note.accept("V483 SYS /lm admin skillcheck help lists clear: " + pf(sc.contains("skillcheck clear")
                && String.join(" ", run(server, console, "lm admin skillcheck")).equals(sc)) + " | " + clip(sc));
        String g = String.join(" ", run(server, console, "lm admin goals help"));
        note.accept("V483 SYS /lm admin goals help lists inspect, reset, grant: " + pf(g.contains("goals inspect")
                && g.contains("goals reset") && g.contains("goals grant")
                && String.join(" ", run(server, console, "lm admin goals")).equals(g)) + " | " + clip(g));
        String t = String.join(" ", run(server, console, "lm admin difficulty tier"));
        note.accept("V483 SYS /lm admin difficulty tier lists grant and revoke: " + pf(t.contains("tier grant")
                && t.contains("tier revoke")) + " | " + clip(t));
        boolean noMark = !(saga + sc + g + t).contains("_saga") && !(saga + sc + g + t).contains("spar_wins");
        note.accept("V483 SYS help text has no snake_case keys: " + pf(noMark));
    }

    // ---- saga ------------------------------------------------------------------------------------------------------

    private static void saga(MinecraftServer server, CommandSourceStack console, CommandSourceStack self,
            ServerPlayer player, String name, Consumer<String> note) {
        StatsData stats = DmzProgression.stats(player);
        PlayerQuestData data = stats == null ? null : stats.getPlayerQuestData();
        Saga saga = firstSaga();
        if (data == null || saga == null || saga.getQuests() == null || saga.getQuests().isEmpty()) {
            note.accept("V483 SYS saga: the probe player has quest data and a saga is loaded: FAIL | data="
                    + (data != null) + " saga=" + (saga == null ? "none" : saga.getId()));
            return;
        }
        String id = saga.getId();
        String key = SagaResetService.commandKey(saga);
        CompoundTag snapshot = data.serializeNBT();
        try {
            note.accept("V483 SYS saga keys are readable and resolve back: " + pf(!key.contains("_")
                    && SagaResetService.findLoose(key) == saga
                    && SagaResetService.findLoose(key.toUpperCase(java.util.Locale.ROOT)) == saga) + " | " + key);
            List<String> unknown = run(server, console, "lm admin saga unlock " + name + " no-such-saga-probe");
            note.accept("V483 SYS unknown saga lists the valid names: " + pf(String.join(" ", unknown).contains(key))
                    + " | " + clip(String.join(" ", unknown)));

            // unlock, console then player
            data.setSagaUnlocked(id, true);
            boolean lockedFirst = data.isSagaLocked(id);
            List<String> c1 = run(server, console, "lm admin saga unlock " + name + " " + key);
            boolean consoleUnlocked = lockedFirst && !data.isSagaLocked(id);
            data.setSagaUnlocked(id, true);
            List<String> p1 = run(server, self, "lm admin saga unlock " + name + " " + key);
            boolean playerUnlocked = !data.isSagaLocked(id);
            note.accept("V483 SYS saga unlock clears the lock (console, player): " + pf(consoleUnlocked && playerUnlocked)
                    + " | " + clip(String.join(" ", c1)) + " / " + clip(String.join(" ", p1)));
            List<String> again = run(server, console, "lm admin saga unlock " + name + " " + key);
            note.accept("V483 SYS saga unlock on an unlocked saga says nothing changed: "
                    + pf(String.join(" ", again).contains("already unlocked")));

            // reset, console then player
            if (data.isInParty()) {
                note.accept("V483 SYS saga reset SKIP: the probe player is in a quest party");
                return;
            }
            String questKey = PlayerQuestData.sagaQuestKey(id, saga.getQuests().get(0).getId());
            data.acceptQuest(questKey);
            boolean started = status(player, id) != SagaResetService.Status.NOT_STARTED;
            List<String> ask = run(server, console, "lm admin saga reset " + name + " " + key);
            boolean keptWithoutConfirm = status(player, id) != SagaResetService.Status.NOT_STARTED;
            note.accept("V483 SYS saga reset without confirm only explains: " + pf(started && keptWithoutConfirm
                    && String.join(" ", ask).contains("confirm")) + " | " + clip(String.join(" ", ask)));
            List<String> c2 = run(server, console, "lm admin saga reset " + name + " " + key + " confirm");
            boolean consoleReset = status(player, id) == SagaResetService.Status.NOT_STARTED;
            data.acceptQuest(questKey);
            List<String> p2 = run(server, self, "lm admin saga reset " + name + " " + key.toUpperCase(java.util.Locale.ROOT)
                    + " confirm");
            boolean playerReset = status(player, id) == SagaResetService.Status.NOT_STARTED;
            note.accept("V483 SYS saga reset clears progress for free (console, player): " + pf(consoleReset && playerReset)
                    + " | " + clip(String.join(" ", c2)) + " / " + clip(String.join(" ", p2)));
        } finally {
            data.deserializeNBT(snapshot);
            try {
                QuestService.syncQuestState(player);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Saga firstSaga() {
        try {
            for (Saga s : QuestRegistry.getAllSagas().values()) {
                if (s != null && s.getId() != null && s.getQuests() != null && !s.getQuests().isEmpty()) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static SagaResetService.Status status(ServerPlayer player, String id) {
        SagaResetService.Offer o = SagaResetService.findOffer(player, id);
        return o == null ? null : o.status();
    }

    // ---- skill check -----------------------------------------------------------------------------------------------

    private static void skillCheck(MinecraftServer server, CommandSourceStack console, CommandSourceStack self,
            ServerPlayer player, String name, Consumer<String> note) {
        boolean had = SkillCheckService.inSession(player);
        try {
            SkillCheckService.markSession(player);
            List<String> c = run(server, console, "lm admin skillcheck clear " + name);
            boolean consoleCleared = !SkillCheckService.inSession(player) && String.join(" ", c).contains("Cleared");
            List<String> none = run(server, console, "lm admin skillcheck clear " + name);
            boolean saysNone = String.join(" ", none).contains("no Skill Check session");
            SkillCheckService.markSession(player);
            List<String> p = run(server, self, "lm admin skillcheck clear " + name);
            boolean playerCleared = !SkillCheckService.inSession(player) && String.join(" ", p).contains("Cleared");
            note.accept("V483 SYS skillcheck clear ends a session (console, player): " + pf(consoleCleared && playerCleared)
                    + " | " + clip(String.join(" ", c)) + " / " + clip(String.join(" ", p)));
            note.accept("V483 SYS skillcheck clear with no session says so: " + pf(saysNone) + " | "
                    + clip(String.join(" ", none)));
        } finally {
            if (had) {
                SkillCheckService.markSession(player);
            } else {
                SkillCheckService.clearSession(player);
            }
        }
    }

    // ---- weekly goals ----------------------------------------------------------------------------------------------

    private static void goals(MinecraftServer server, CommandSourceStack console, CommandSourceStack self,
            ServerPlayer player, String name, Consumer<String> note) {
        java.util.UUID id = player.m_20148_();
        CompoundTag root = LmPlayerStore.root(id);
        CompoundTag snapshot = root.m_128441_(WEEKLY_KEY) ? root.m_128469_(WEEKLY_KEY).m_6426_() : null;
        try {
            List<WeeklyGoals.Goal> week = WeeklyGoals.thisWeek();
            WeeklyGoals.Goal g = week.get(0);
            String key = WeeklyGoals.key(g);
            WeeklyGoals.adminReset(id);
            long one = g.minutes() ? 60_000L : 1L;
            List<String> c1 = run(server, console, "lm admin goals grant " + name + " " + key + " 1");
            boolean consoleGrant = WeeklyGoals.progress(id, g.id()) == Math.min(g.target(), one);
            List<String> p1 = run(server, self, "lm admin goals grant " + name + " " + key);
            boolean playerComplete = WeeklyGoals.progress(id, g.id()) == g.target();
            note.accept("V483 SYS goals grant adds progress then completes (console, player): " + pf(consoleGrant
                    && playerComplete) + " | " + clip(String.join(" ", c1)) + " / " + clip(String.join(" ", p1)));
            List<String> twice = run(server, console, "lm admin goals grant " + name + " " + key);
            note.accept("V483 SYS goals grant on a finished goal changes nothing: " + pf(WeeklyGoals.progress(id, g.id())
                    == g.target() && String.join(" ", twice).contains("already finished")));
            List<String> bad = run(server, console, "lm admin goals grant " + name + " no-such-goal");
            note.accept("V483 SYS goals grant with an unknown goal lists this week's goals: "
                    + pf(String.join(" ", bad).contains(key)) + " | " + clip(String.join(" ", bad)));

            String ci = String.join(" ", run(server, console, "lm admin goals inspect " + name));
            String pi = String.join(" ", run(server, self, "lm admin goals inspect " + name));
            boolean all = true;
            for (WeeklyGoals.Goal each : week) {
                all &= ci.contains(each.title()) && pi.contains(each.title());
            }
            note.accept("V483 SYS goals inspect shows the three goals, done state and week (console, player): " + pf(all
                    && ci.contains("Done: ") && ci.contains("Not done: ") && ci.contains("week starting")
                    && pi.contains("resets in")) + " | " + clip(ci));

            List<String> ask = run(server, console, "lm admin goals reset " + name);
            boolean kept = WeeklyGoals.progress(id, g.id()) == g.target();
            note.accept("V483 SYS goals reset without confirm only explains: " + pf(kept
                    && String.join(" ", ask).contains("confirm")));
            List<String> c2 = run(server, console, "lm admin goals reset " + name + " confirm");
            boolean consoleReset = WeeklyGoals.progress(id, g.id()) == 0L;
            WeeklyGoals.note(id, g.id(), 1L);
            List<String> p2 = run(server, self, "lm admin goals reset " + name + " confirm");
            boolean playerReset = WeeklyGoals.progress(id, g.id()) == 0L
                    && WeeklyGoals.storedWeek(id) == WeeklyGoals.week(System.currentTimeMillis());
            note.accept("V483 SYS goals reset starts the week over (console, player): " + pf(consoleReset && playerReset)
                    + " | " + clip(String.join(" ", c2)) + " / " + clip(String.join(" ", p2)));
        } finally {
            if (snapshot != null) {
                root.m_128365_(WEEKLY_KEY, snapshot);
            } else {
                root.m_128473_(WEEKLY_KEY);
            }
            LmPlayerStore.flush(id);
        }
    }

    // ---- difficulty tier grant -------------------------------------------------------------------------------------

    private static void tier(MinecraftServer server, CommandSourceStack console, CommandSourceStack self,
            ServerPlayer player, String name, Consumer<String> note) {
        PlayerDifficultyData data = DifficultyCache.data(player);
        UnlockTier pick = null;
        for (UnlockTier t : UnlockTier.values()) {
            if (!data.hasStaffGrantedTier(t.id) && !data.hasUnlockedTier(t.id) && !UnlockSystem.isEligible(player, t)) {
                pick = t;
            }
        }
        if (pick == null) {
            for (UnlockTier t : UnlockTier.values()) {
                if (!data.hasStaffGrantedTier(t.id)) {
                    pick = t;
                }
            }
        }
        if (pick == null) {
            note.accept("V483 SYS tier: a tier without a staff grant to test with: FAIL | every tier already granted");
            return;
        }
        final UnlockTier tier = pick;
        boolean qualifies = UnlockSystem.isEligible(player, tier);
        try {
            List<String> c1 = run(server, console, "lm admin difficulty tier grant " + name + " "
                    + tier.display.toLowerCase(java.util.Locale.ROOT));
            boolean granted = data.hasStaffGrantedTier(tier.id) && data.hasUnlockedTier(tier.id);
            UnlockSystem.syncUnlocks(player, data);
            boolean survives = data.hasStaffGrantedTier(tier.id) && data.hasUnlockedTier(tier.id)
                    && UnlockSystem.isEligible(player, tier);
            PlayerDifficultyData reloaded = new PlayerDifficultyData();
            reloaded.load(data.save());
            note.accept("V483 SYS tier grant (console) unlocks the tier, survives syncUnlocks and a save: " + pf(granted
                    && survives && reloaded.hasStaffGrantedTier(tier.id)) + " | " + tier.display + " | "
                    + clip(String.join(" ", c1)));
            List<String> c2 = run(server, console, "lm admin difficulty tier revoke " + name + " " + tier.id);
            UnlockSystem.syncUnlocks(player, data);
            boolean revoked = !data.hasStaffGrantedTier(tier.id) && data.hasUnlockedTier(tier.id) == qualifies;
            note.accept("V483 SYS tier revoke (console) removes only the grant: " + pf(revoked) + " | "
                    + clip(String.join(" ", c2)));

            List<String> p1 = run(server, self, "lm admin difficulty tier grant " + name + " t" + tier.id);
            UnlockSystem.syncUnlocks(player, data);
            boolean pGranted = data.hasStaffGrantedTier(tier.id) && data.hasUnlockedTier(tier.id);
            List<String> p2 = run(server, self, "lm admin difficulty tier revoke " + name + " "
                    + tier.display.toUpperCase(java.util.Locale.ROOT));
            UnlockSystem.syncUnlocks(player, data);
            boolean pRevoked = !data.hasStaffGrantedTier(tier.id) && data.hasUnlockedTier(tier.id) == qualifies;
            note.accept("V483 SYS tier grant and revoke as the player: " + pf(pGranted && pRevoked) + " | "
                    + clip(String.join(" ", p1)) + " / " + clip(String.join(" ", p2)));
            List<String> none = run(server, console, "lm admin difficulty tier revoke " + name + " " + tier.id);
            note.accept("V483 SYS tier revoke without a grant says so: " + pf(String.join(" ", none)
                    .contains("no staff grant")));
            PlayerDifficultyData old = new PlayerDifficultyData();
            CompoundTag legacy = data.save();
            legacy.m_128473_("staffGrantedTiers");
            old.load(legacy);
            note.accept("V483 SYS a record from before 4.8.3 loads with no grants: "
                    + pf(old.getStaffGrantedTiers().isEmpty()));
        } finally {
            if (data.hasStaffGrantedTier(tier.id)) {
                UnlockSystem.revokeStaffTier(player, tier.id);
            }
            UnlockSystem.syncUnlocks(player, data);
            DifficultyCache.save(player);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** Runs {@code command} as {@code base} with its chat output captured (success and failure lines). */
    private static List<String> run(MinecraftServer server, CommandSourceStack base, String command) {
        List<String> out = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void m_213846_(Component message) {
                out.add(strip(message.getString()));
            }

            @Override
            public boolean m_6999_() {
                return true;
            }

            @Override
            public boolean m_7028_() {
                return true;
            }

            @Override
            public boolean m_6102_() {
                return false;
            }
        };
        try {
            server.m_129892_().m_230957_(base.m_165484_(capture), command);
        } catch (Throwable t) {
            out.add("<error " + t + ">");
        }
        return out;
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static String clip(String s) {
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }

    private static String pf(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }
}
