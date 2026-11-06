package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.mohistmelee.PrimaryStatRepair;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceStatsConfig;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Stats;
import com.mojang.authlib.GameProfile;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.RegistryObject;

/**
 * Test-only probe (4.7.41; ships only in the testprobe jar, {@code -Dlm.probe.bornFreshStats}): BrevinM's new
 * Frost Demon kept his Saiyan's six stats. A fake player holds main-sized stats (BrevinM's live values) and
 * the real DMZ Mohist melee fix saves them, as its 1-second tick does. Then:
 * <ul>
 *   <li>Control: the pre-4.7.41 reset (new StatsData, attribute bases set to 0 directly, as DMZ Ultra's
 *       DmzPart.clear and LM did) is undone by the first stat read: the bug, reproduced.</li>
 *   <li>{@link PrestigeCharacter#freshStats}: all six read 0, also after the melee fix's 5-second
 *       {@code ensure}, and its saved copy is 0.</li>
 *   <li>DMZ character creation ({@code initializeWithRaceAndClass}, Frost Demon warrior) then applies the
 *       race's base stats, not the main's.</li>
 *   <li>Switch into a played character: {@link PrestigeCharacter#meleeSnapshot} adopts that character's
 *       own stats as the saved copy.</li>
 * </ul>
 * Logs "born fresh stats probe check ...: OK|WRONG" and a final OK / FAILED line.
 */
public final class BornFreshStatsProbe {
    private static final UUID PROBE_ID = UUID.fromString("6c6d2d70-726f-6265-2d62-6f726e667273");
    /** BrevinM's Saiyan main, live 2026-11-05: STR SKP RES VIT PWR ENE. */
    private static final int[] MAIN = {30934, 9683, 35835, 30317, 38827, 45489};
    private static final List<RegistryObject<Attribute>> ATTRS = List.of(MainAttributes.STRENGTH,
            MainAttributes.STRIKE_POWER, MainAttributes.RESISTANCE, MainAttributes.VITALITY, MainAttributes.KI_POWER,
            MainAttributes.ENERGY);
    private static int failed;

    private BornFreshStatsProbe() {}

    public static void run(MinecraftServer server) {
        failed = 0;
        try {
            FakePlayer fp = FakePlayerFactory.get(server.m_129783_(), new GameProfile(PROBE_ID, "lm_born_probe"));
            StatsData data = DmzProgression.stats(fp);
            check("DMZ stats attached to the probe player", data != null);
            if (data == null) {
                return;
            }
            data.getStatus().setHasCreatedCharacter(true);

            main(fp);
            data.copyFrom(new StatsData((Player) fp));
            setBases(fp, new int[6]);
            int[] control = read(data);
            log("control (pre-4.7.41 reset, then one read)", control, fp);
            check("control: the old reset is undone by the melee fix on the first read (bug reproduced)",
                    Arrays.equals(control, MAIN));

            main(fp);
            PrestigeCharacter.freshStats(fp);
            int[] fresh = read(data);
            log("after freshStats", fresh, fp);
            check("freshStats: all six stats read 0", Arrays.equals(fresh, new int[6])
                    && PrestigeCharacter.keptStats(fp).isEmpty());
            PrimaryStatRepair.ensure(fp, "probe-tick");
            int[] afterTick = read(data);
            check("freshStats: still 0 after the melee fix's 5-second check, saved copy is 0",
                    Arrays.equals(afterTick, new int[6]) && Arrays.equals(saved(fp), new int[6]));

            RaceStatsConfig.BaseStats base = ConfigManager.getRaceStats("frostdemon").getClassStats("warrior").getBaseStats();
            int[] expect = {base.getStrength(), base.getStrikePower(), base.getResistance(), base.getVitality(),
                    base.getKiPower(), base.getEnergy()};
            data.getStatus().setHasCreatedCharacter(false);
            data.initializeWithRaceAndClass("frostdemon", "warrior", "male", 0, null, 0, 0, 0, 0, 0, 1.0f, "",
                    "#000000", "#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000", "#000000", "#FFFFFF");
            PrimaryStatRepair.ensure(fp, "probe-tick");
            int[] created = read(data);
            log("after DMZ creation as a Frost Demon warrior (race base " + Arrays.toString(expect) + ")", created, fp);
            check("creation: the Frost Demon's own base stats, not the main's", Arrays.equals(created, expect)
                    && Arrays.stream(created).allMatch(v -> v < 10_000));

            int[] played = {1200, 900, 1300, 800, 1100, 950};
            main(fp);
            setBases(fp, played);
            PrestigeCharacter.meleeSnapshot(fp, false);
            check("switch into a played character: the saved copy becomes that character's own stats",
                    Arrays.equals(saved(fp), played));

            PrimaryStatRepair.clear(PROBE_ID);
        } catch (Throwable t) {
            failed++;
            AdaptiveDifficultyMod.LOGGER.warn("[{}] born fresh stats probe threw: {}", AdaptiveDifficultyMod.MOD_ID, t.toString(), t);
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] born fresh stats probe {}", AdaptiveDifficultyMod.MOD_ID,
                failed == 0 ? "OK" : "FAILED: " + failed);
    }

    /** The main's stats on the attribute bases, saved by the melee fix the way its 1-second tick does. */
    private static void main(FakePlayer fp) {
        setBases(fp, MAIN);
        PrimaryStatRepair.snapshot(fp);
    }

    private static void setBases(FakePlayer fp, int[] values) {
        for (int i = 0; i < ATTRS.size(); i++) {
            AttributeInstance inst = fp.m_21051_(ATTRS.get(i).get());
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

    private static int[] saved(FakePlayer fp) {
        int[] out = new int[ATTRS.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = PrimaryStatRepair.savedFor(fp, ATTRS.get(i).get());
        }
        return out;
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        AdaptiveDifficultyMod.LOGGER.info("[{}] born fresh stats probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }

    private static void log(String step, int[] stats, FakePlayer fp) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] born fresh stats probe {}: STR/SKP/RES/VIT/PWR/ENE {}, melee saved {}",
                AdaptiveDifficultyMod.MOD_ID, step, Arrays.toString(stats), Arrays.toString(saved(fp)));
    }
}
