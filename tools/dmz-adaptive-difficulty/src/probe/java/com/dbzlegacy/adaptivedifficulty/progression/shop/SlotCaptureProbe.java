package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dmzultra.slots.SlotIndex;
import com.dmzultra.slots.SlotService;
import com.dmzultra.slots.SlotStore;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Stats;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.RegistryObject;

/**
 * Test-only probe (4.7.43; ships only in the testprobe jar, {@code -Dlm.probe.slotCapture}): DMZ Ultra 0.4.0
 * wrote a slot record only on a switch, so switching back to a character re-applied its last switch-out and
 * lost everything since (Kokopeli0007). A real (non-fake) ServerPlayer runs Ultra's own login, create, switch,
 * logout and stop handlers:
 * <ul>
 *   <li>Main at Koko's record stats; create an alt (Ultra parks the main); switch back to the main.</li>
 *   <li>Train to Koko's live stats: the main's record still holds the old ones (the bug's state).</li>
 *   <li>Logout: the record now holds the live stats, savedAt advanced, gen = the index's, the same keys and
 *       parts as the switch path's record, equal to a fresh {@code SlotService.record(player, 0, gen, false)}
 *       apart from savedAt; the live form and stats are untouched (no toBase).</li>
 *   <li>Login, switch main to alt to main: the main keeps the trained stats.</li>
 *   <li>Server stop with the player still listed: the record holds the newest stats.</li>
 * </ul>
 * Logs "slot capture probe check ...: OK|WRONG" and a final OK / FAILED line.
 */
public final class SlotCaptureProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d70-726f-6265-2d73-6c6f74636170");
    /** Kokopeli0007's main: slot_0.dat at 06:30 EST (STR SKP RES VIT PWR ENE; only STR/PWR are his). */
    private static final int[] OLD = {145133, 9000, 9000, 9000, 122727, 9000};
    /** His live .dat afterwards. */
    private static final int[] TRAINED = {168495, 9100, 9100, 9100, 142484, 9100};
    private static final int[] LATER = {170000, 9200, 9200, 9200, 144000, 9200};
    private static final List<RegistryObject<Attribute>> ATTRS = List.of(MainAttributes.STRENGTH,
            MainAttributes.STRIKE_POWER, MainAttributes.RESISTANCE, MainAttributes.VITALITY, MainAttributes.KI_POWER,
            MainAttributes.ENERGY);
    private static int failed;

    private SlotCaptureProbe() {}

    public static void run(MinecraftServer server) {
        failed = 0;
        try {
            ServerLevel level = server.m_129783_();
            ServerPlayer p = new ServerPlayer(server, level, new GameProfile(PROBE_ID, "lm_slot_probe"));
            Constructor<?> handler = Class.forName("net.minecraftforge.common.util.FakePlayer$FakePlayerNetHandler")
                    .getDeclaredConstructor(MinecraftServer.class, ServerPlayer.class);
            handler.setAccessible(true);
            p.f_8906_ = (ServerGamePacketListenerImpl) handler.newInstance(server, p);
            StatsData data = DmzProgression.stats(p);
            check("DMZ stats attached to the probe player", data != null);
            if (data == null) {
                return;
            }
            data.getStatus().setHasCreatedCharacter(true);
            data.load(data.save());

            SlotService.login(new PlayerEvent.PlayerLoggedInEvent(p));
            check("Ultra first login made an index, slot 1 active", index(server) != null && SlotService.activeSlot(p) == 0);

            setBases(p, OLD);
            Object refused = call("com.dmzultra.slots.Lifecycle", "create", new Class<?>[] {ServerPlayer.class,
                    String.class}, p, "Yoyo");
            check("created the alt and switched to it (refusal: " + text(refused) + ")",
                    refused == null && SlotService.activeSlot(p) == 1);
            switchTo(p, 0);
            check("switched back to the main: its stats are the parked ones", Arrays.equals(read(data), OLD));
            CompoundTag switchRecord = SlotStore.readRecord(server, PROBE_ID, 0);

            setBases(p, TRAINED);
            CompoundTag before = SlotStore.readRecord(server, PROBE_ID, 0);
            check("before logout the main's record holds the old stats (the live bug)",
                    Arrays.equals(recordStats(before), OLD));
            data.getCharacter().setActiveForm("probeforms", "probeform");
            String formBefore = data.getCharacter().getActiveFormGroup() + "/" + data.getCharacter().getActiveForm();

            long gen = index(server).gen;
            Thread.sleep(5);
            SlotService.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            CompoundTag after = SlotStore.readRecord(server, PROBE_ID, 0);
            log("main record after logout", after);
            check("logout: the main's record holds the trained stats", Arrays.equals(recordStats(after), TRAINED));
            check("logout: savedAt advanced", after.m_128454_("savedAt") > before.m_128454_("savedAt"));
            check("logout: v 1, account, slot 0, gen = the index's, dataVersion and ultraVersion as the switch's",
                    after.m_128451_("v") == 1 && PROBE_ID.equals(after.m_128342_("account"))
                            && after.m_128451_("slot") == 0 && after.m_128454_("gen") == gen
                            && after.m_128451_("dataVersion") == switchRecord.m_128451_("dataVersion")
                            && after.m_128461_("ultraVersion").equals(switchRecord.m_128461_("ultraVersion")));
            check("logout: same keys and parts as the switch path's record",
                    after.m_128431_().equals(switchRecord.m_128431_())
                            && after.m_128469_("parts").m_128431_().equals(switchRecord.m_128469_("parts").m_128431_()));
            CompoundTag fresh = (CompoundTag) call("com.dmzultra.slots.SlotService", "record", new Class<?>[] {
                    ServerPlayer.class, int.class, long.class, boolean.class}, p, 0, gen, false);
            check("logout: equal to SlotService.record(player, 0, gen, false) apart from savedAt",
                    withoutSavedAt(after).equals(withoutSavedAt(fresh)));
            String formAfter = data.getCharacter().getActiveFormGroup() + "/" + data.getCharacter().getActiveForm();
            check("logout: live form (" + formAfter + ") and stats untouched - no toBase",
                    formAfter.equals(formBefore) && Arrays.equals(read(data), TRAINED));
            data.getCharacter().clearActiveForm(p);

            SlotService.login(new PlayerEvent.PlayerLoggedInEvent(p));
            switchTo(p, 1);
            switchTo(p, 0);
            int[] back = read(data);
            log("main after main -> alt -> main", back);
            check("main -> alt -> main keeps the trained stats (the bug lost them)", Arrays.equals(back, TRAINED));

            setBases(p, LATER);
            // getPlayers() is a read-only view: list the probe player in PlayerList.players itself.
            List<ServerPlayer> listed = ObfuscationReflectionHelper.getPrivateValue(PlayerList.class, server.m_6846_(),
                    "f_11196_");
            listed.add(p);
            try {
                SlotService.stopped(new ServerStoppedEvent(server));
            } finally {
                listed.remove(p);
            }
            check("server stop with the player still online: the record holds the newest stats",
                    Arrays.equals(recordStats(SlotStore.readRecord(server, PROBE_ID, 0)), LATER));
        } catch (Throwable t) {
            failed++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] slot capture probe threw: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] slot capture probe {}", AdaptiveDifficultyMod.MOD_ID,
                failed == 0 ? "OK" : "FAILED: " + failed);
    }

    private static SlotIndex index(MinecraftServer server) {
        return SlotService.index(server, PROBE_ID);
    }

    /** Ultra's own switch (force: the probe ignores the cooldown). */
    private static void switchTo(ServerPlayer p, int slot) throws Exception {
        Object refused = call("com.dmzultra.slots.SwitchEngine", "request", new Class<?>[] {ServerPlayer.class, int.class,
                boolean.class, boolean.class}, p, slot, false, true);
        check("switch to slot " + (slot + 1) + " (refusal: " + text(refused) + ")",
                refused == null && SlotService.activeSlot(p) == slot);
    }

    private static Object call(String cls, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = Class.forName(cls).getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static String text(Object component) {
        return component == null ? "none" : String.valueOf(component);
    }

    private static CompoundTag withoutSavedAt(CompoundTag record) {
        CompoundTag copy = record.m_6426_();
        copy.m_128473_("savedAt");
        return copy;
    }

    private static int[] recordStats(CompoundTag record) {
        CompoundTag s = record.m_128469_("parts").m_128469_("dmz").m_128469_("Stats");
        return new int[] {s.m_128451_("STR"), s.m_128451_("SKP"), s.m_128451_("RES"), s.m_128451_("VIT"),
                s.m_128451_("PWR"), s.m_128451_("ENE")};
    }

    private static void setBases(ServerPlayer p, int[] values) {
        for (int i = 0; i < ATTRS.size(); i++) {
            AttributeInstance inst = p.m_21051_(ATTRS.get(i).get());
            if (inst != null) {
                inst.m_22100_(values[i]);
            }
        }
    }

    private static int[] read(StatsData data) {
        Stats s = data.getStats();
        return new int[] {s.getStrength(), s.getStrikePower(), s.getResistance(), s.getVitality(), s.getKiPower(),
                s.getEnergy()};
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] slot capture probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }

    private static void log(String step, CompoundTag record) {
        Set<String> parts = new HashSet<>(record.m_128469_("parts").m_128431_());
        AdaptiveDifficultyMod.LOGGER.info("[{}] slot capture probe {}: gen {}, savedAt {}, stats {}, parts {}",
                AdaptiveDifficultyMod.MOD_ID, step, record.m_128454_("gen"), record.m_128454_("savedAt"),
                Arrays.toString(recordStats(record)), parts);
    }

    private static void log(String step, int[] stats) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] slot capture probe {}: STR/SKP/RES/VIT/PWR/ENE {}",
                AdaptiveDifficultyMod.MOD_ID, step, Arrays.toString(stats));
    }
}
