package com.dbzlegacy.adaptivedifficulty.event;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.server.events.DragonBallsHandler;
import com.dragonminez.server.world.data.DragonBallSavedData;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Test-only probe for the dragon balls (4.7.35; pistons 4.7.36). Lives in src/probe/java and ships only
 * in the testprobe jar ({@code LM_TEST_PROBES=1 bash build.sh}); never in mods/ or on live. Runs when the
 * server has {@code -Dlm.probe.dragonBalls=main|restart} (docs/LM_TEST_PROBES.md). Drives fake players through the real event paths (block break, radar left-click,
 * item pickup, block use) and logs one line per check: "dragon ball probe check NAME: OK|WRONG".
 *
 * <p>main: radar-only break (pickaxe, fist, wrong radar, right radar, Bi-Dimensional Radar), one set
 * per player (inventory, ender chest, backpack, partial set, Namek still allowed), a piston push, the
 * 3-minute respawn (none before 3 minutes, a full set after, at new spots), a 3-of-7 Namek pickup that
 * starts nothing, a summon and wish whose scatter refills the set, then the Earth set is collected
 * again and the probe logs "MAIN DONE" (stop the server there). restart: the set that was gone at boot
 * comes back 3 minutes after boot.
 */
public final class DragonBallProbe {
    private static final String MODE = System.getProperty("lm.probe.dragonBalls", "");
    private static final int SECOND = 20;
    private static final String DRAGON = "com.dragonminez.common.init.entities.dragon.DragonWishEntity";
    private static int step;
    private static int waitUntil;
    private static int startTick = -1;
    private static int timerStart;
    private static List<BlockPos> oldEarth = new ArrayList<>();
    private static List<BlockPos> namekLeft = new ArrayList<>();
    private static int namekBefore;
    private static UUID dragonId;
    private static boolean done;

    private DragonBallProbe() {}

    public static boolean active() {
        return !MODE.isEmpty() && !done;
    }

    /** Entry point DifficultyEvents finds by name each server tick (test builds only). */
    public static void tick(MinecraftServer server, int tick) {
        if (active()) {
            pulse(server, tick);
        }
        pistonTick(server, tick);
    }

    public static void pulse(MinecraftServer server, int tick) {
        if (!active() || server == null || tick < waitUntil) {
            return;
        }
        if (startTick < 0) {
            startTick = tick;
            waitUntil = tick + 10 * SECOND; // let DMZ's first spawn and LM's boot check run
            return;
        }
        try {
            if ("restart".equals(MODE)) {
                restart(server, tick);
            } else {
                main(server, tick);
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn("[{}] dragon ball probe failed at step {}: {}", AdaptiveDifficultyMod.MOD_ID, step,
                    t.toString(), t);
            done = true;
        }
    }

    private static void main(MinecraftServer server, int tick) {
        DragonBallSetDefinition earth = DragonBallDefinitions.getBallSet("earth");
        DragonBallSetDefinition namek = DragonBallDefinitions.getBallSet("namek");
        ServerLevel ow = DragonBallsHandler.getHomeLevel(server, earth);
        ServerLevel nl = DragonBallsHandler.getHomeLevel(server, namek);
        switch (step) {
            case 0 -> {
                log("start: earth " + count(server, earth) + " tracked, namek " + count(server, namek) + " tracked");
                materialize(ow, earth);
                materialize(nl, namek);
                next(tick, 10);
            }
            case 1 -> {
                if (pending(ow, earth) + pending(nl, namek) > 0) {
                    materialize(ow, earth);
                    materialize(nl, namek);
                    waitUntil = tick + 2 * SECOND;
                    return;
                }
                List<BlockPos> e = active(ow, earth);
                List<BlockPos> n = active(nl, namek);
                check("all 7 Earth and 7 Namek balls are placed", e.size() == 7 && n.size() == 7);
                // (c) radar-only break
                FakePlayer fp = player(ow, "lm_db_probe_e");
                BlockPos ball = e.get(0);
                hold(fp, new ItemStack(vanillaItem("diamond_pickaxe")));
                check("pickaxe cannot break an Earth ball", !breakBlock(fp, ball) && isBall(ow, ball));
                hold(fp, ItemStack.f_41583_);
                check("bare fist cannot break an Earth ball", !breakBlock(fp, ball) && isBall(ow, ball));
                hold(fp, radar("namekdball_radar"));
                leftClick(fp, ball);
                check("Namek radar left-click leaves an Earth ball", isBall(ow, ball) && carried(fp, earth) == 0);
                check("Namek radar cannot break an Earth ball", !breakBlock(fp, ball) && isBall(ow, ball));
                hold(fp, radar("dball_radar"));
                leftClick(fp, ball);
                check("Earth radar left-click takes an Earth ball", !isBall(ow, ball) && carried(fp, earth) == 1);
                hold(fp, radar("fused_dball_radar"));
                leftClick(fp, e.get(1));
                check("Bi-Dimensional Radar takes an Earth ball", !isBall(ow, e.get(1)) && carried(fp, earth) == 2);
                FakePlayer fn = player(nl, "lm_db_probe_n");
                hold(fn, radar("fused_dball_radar"));
                leftClick(fn, n.get(0));
                check("Bi-Dimensional Radar takes a Namek ball", !isBall(nl, n.get(0)) && carried(fn, namek) == 1);
                hold(fn, radar("dball_radar"));
                leftClick(fn, n.get(1));
                check("Earth radar left-click leaves a Namek ball", isBall(nl, n.get(1)) && carried(fn, namek) == 1);
                namekLeft = new ArrayList<>(n.subList(1, n.size()));
                // (b) one set per player
                holdTests(ow, nl, earth, namek, e.get(2));
                pistonTest(ow, earth);
                next(tick, 2);
            }
            case 2 -> {
                // (a) collect the rest of the Earth set, then 2 more Namek balls (3 of 7 in all)
                FakePlayer fp = player(ow, "lm_db_probe_e");
                hold(fp, radar("dball_radar"));
                oldEarth = new ArrayList<>();
                for (BlockPos p : active(ow, earth)) {
                    oldEarth.add(p);
                    leftClick(fp, p);
                }
                check("all 7 Earth balls collected (none left in the world)", count(server, earth) == 0 && carried(fp, earth) == 7);
                check("respawn timer armed at 3 minutes", DragonBallRespawn.secondsLeft(server, "earth") >= 179);
                FakePlayer fn = player(nl, "lm_db_probe_n");
                hold(fn, radar("namekdball_radar"));
                leftClick(fn, namekLeft.get(0));
                leftClick(fn, namekLeft.get(1));
                namekBefore = count(server, namek);
                check("3 of 7 Namek balls taken (4 left)", carried(fn, namek) == 3 && namekBefore == 4);
                check("a partial Namek set starts no timer", DragonBallRespawn.secondsLeft(server, "namek") < 0);
                timerStart = tick;
                waitUntil = timerStart + 170 * SECOND;
                step++;
            }
            case 3 -> {
                check("no Earth set before 3 minutes (170 s)", count(server, earth) == 0);
                waitUntil = timerStart + DragonBallRespawn.DELAY_TICKS + 3 * SECOND;
                step++;
            }
            case 4 -> {
                int now = count(server, earth);
                Set<Long> fresh = new HashSet<>();
                for (BlockPos p : known(ow, earth)) {
                    fresh.add(xz(p));
                }
                boolean reused = false;
                for (BlockPos p : oldEarth) {
                    reused |= p != null && fresh.contains(xz(p));
                }
                log("respawned Earth spots: " + known(ow, earth));
                check("Earth set back 3 minutes after the last pickup (7 of 7)", now == 7);
                check("respawned Earth balls are at new spots", !reused && fresh.size() == 7);
                check("partial Namek set still 4 of 7 (no respawn)", count(server, namek) == namekBefore);
                command(server, "dmzdragonballs status");
                materialize(ow, earth);
                next(tick, 5);
            }
            case 5 -> {
                if (pending(ow, earth) > 0) {
                    materialize(ow, earth);
                    waitUntil = tick + 2 * SECOND;
                    return;
                }
                // (d) collect again, place the 7 around a spot and summon
                FakePlayer fs = player(ow, "lm_db_probe_s");
                hold(fs, radar("dball_radar"));
                for (BlockPos p : active(ow, earth)) {
                    leftClick(fs, p);
                }
                check("summon run: set collected, timer armed", count(server, earth) == 0
                        && DragonBallRespawn.secondsLeft(server, "earth") > 0);
                BlockPos base = ow.m_220360_().m_7918_(40, 0, 40);
                base = new BlockPos(base.m_123341_(), ow.m_6924_(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        base.m_123341_(), base.m_123343_()), base.m_123343_());
                for (int star = 1; star <= 7; star++) {
                    BlockPos at = base.m_7918_(star - 4, 0, 0);
                    ow.m_46597_(at.m_7495_(), vanillaBlock("stone").m_49966_()); // a ball needs ground under it
                    ow.m_46597_(at.m_7494_(), Blocks.f_50016_.m_49966_());
                    ow.m_46597_(at, earth.getBlockForStar(star).m_49966_());
                }
                fs.m_150109_().m_6211_();
                check("placed set counts 7 and cancels the timer on the next pulse", count(server, earth) == 7);
                BlockPos click = base.m_7918_(-3, 0, 0);
                BlockState state = ow.m_8055_(click);
                hold(fs, ItemStack.f_41583_);
                Object used = state.m_60664_(ow, fs, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.m_82512_(click), Direction.UP, click, false));
                List<String> near = new ArrayList<>();
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            BlockPos q = click.m_7918_(dx, dy, dz);
                            if (isBall(ow, q)) {
                                near.add(dx + "," + dy + "," + dz);
                            }
                        }
                    }
                }
                log("summon click: block " + state.m_60734_() + " result " + used + ", dragon definition "
                        + DragonBallDefinitions.getDragonForSetAndDimension("earth", ow.m_46472_()) + ", balls near " + near);
                Entity dragon = null;
                for (Entity entity : ow.m_8583_()) {
                    if (entity != null && !entity.m_213877_() && entity.getClass().getName().equals(DRAGON)) {
                        dragon = entity;
                    }
                }
                check("summon: the dragon appears and the 7 balls are consumed", dragon != null && count(server, earth) == 0);
                dragonId = dragon == null ? null : dragon.m_20148_();
                next(tick, 3);
            }
            case 6 -> {
                check("no LM timer while the dragon is up", DragonBallRespawn.secondsLeft(server, "earth") < 0);
                Entity entity = dragonId == null ? null : ow.m_8791_(dragonId);
                if (entity != null) {
                    // What the wish screen does once a wish is granted (DragonWishEntity; GeckoLib is not on LM's classpath).
                    try {
                        entity.getClass().getMethod("setGrantedWish", boolean.class).invoke(entity, true);
                        entity.getClass().getMethod("beginDespawn").invoke(entity);
                    } catch (ReflectiveOperationException e) {
                        log("could not grant the wish: " + e);
                    }
                }
                next(tick, 6);
            }
            case 7 -> {
                check("after the wish DMZ scatters the Earth set again (7 of 7)", count(server, earth) == 7);
                materialize(ow, earth);
                next(tick, 5);
            }
            case 8 -> {
                if (pending(ow, earth) > 0) {
                    materialize(ow, earth);
                    waitUntil = tick + 2 * SECOND;
                    return;
                }
                FakePlayer fr = player(ow, "lm_db_probe_r");
                hold(fr, radar("dball_radar"));
                for (BlockPos p : active(ow, earth)) {
                    leftClick(fr, p);
                }
                check("restart run: Earth set collected before the stop", count(server, earth) == 0);
                log("MAIN DONE - stop the server now (Earth timer has " + DragonBallRespawn.secondsLeft(server, "earth") + " s left)");
                done = true;
            }
            default -> done = true;
        }
    }

    private static void restart(MinecraftServer server, int tick) {
        DragonBallSetDefinition earth = DragonBallDefinitions.getBallSet("earth");
        switch (step) {
            case 0 -> {
                check("boot: Earth set still gone", count(server, earth) == 0);
                check("boot: a fresh 3-minute timer runs", DragonBallRespawn.secondsLeft(server, "earth") > 150);
                waitUntil = startTick + 170 * SECOND;
                step++;
            }
            case 1 -> {
                check("boot: no Earth set before 3 minutes after boot", count(server, earth) == 0);
                waitUntil = startTick + DragonBallRespawn.DELAY_TICKS + 5 * SECOND;
                step++;
            }
            default -> {
                check("boot: Earth set back 3 minutes after boot (7 of 7)", count(server, earth) == 7);
                command(server, "dmzdragonballs status");
                log("RESTART DONE");
                done = true;
            }
        }
    }

    /** (b) A player carrying all 7 Earth stars takes no Earth ball; partial sets and other sets still go. */
    private static void holdTests(ServerLevel ow, ServerLevel nl, DragonBallSetDefinition earth, DragonBallSetDefinition namek,
            BlockPos placedEarth) {
        FakePlayer fh = player(ow, "lm_db_probe_h");
        fh.m_150109_().m_6211_();
        fh.m_36327_().m_6211_();
        for (int star = 1; star <= 7; star++) {
            fh.m_150109_().m_6836_(star, new ItemStack(earth.getBlockForStar(star))); // slot 0 is the hand
        }
        check("full set in the inventory: ground Earth ball stays", !pickUp(fh, ow, earth.getBlockForStar(3)));
        hold(fh, radar("dball_radar"));
        leftClick(fh, placedEarth);
        check("full set in the inventory: Earth radar left-click leaves the ball", isBall(ow, placedEarth));
        check("full set in the inventory: Earth radar break is refused", !breakBlock(fh, placedEarth) && isBall(ow, placedEarth));
        hold(fh, ItemStack.f_41583_);
        FakePlayer fhn = player(nl, "lm_db_probe_h");
        check("full Earth set: a ground Namek ball is still picked up", pickUp(fhn, nl, namek.getBlockForStar(2)));
        fhn.m_150109_().m_6211_();
        // ender chest
        fh.m_150109_().m_6211_();
        for (int star = 1; star <= 7; star++) {
            fh.m_36327_().m_6836_(star - 1, new ItemStack(earth.getBlockForStar(star)));
        }
        check("full set in the ender chest: ground Earth ball stays", !pickUp(fh, ow, earth.getBlockForStar(5)));
        // partial: 6 stars
        fh.m_36327_().m_6836_(6, ItemStack.f_41583_);
        check("6 of 7 carried: a ground Earth ball is picked up", pickUp(fh, ow, earth.getBlockForStar(7)));
        fh.m_150109_().m_6211_();
        fh.m_36327_().m_6211_();
        // backpack (Sophisticated Backpacks, when installed)
        Item pack = ForgeRegistries.ITEMS.getValue(new ResourceLocation("sophisticatedbackpacks", "backpack"));
        if (pack == null || pack == vanillaItem("air")) {
            log("Sophisticated Backpacks not installed: backpack checks skipped");
        } else {
            fh.m_150109_().m_6836_(9, new ItemStack(pack)); // a backpack in the main inventory
            IItemHandler inside = ((ICapabilityProvider) (Object) fh.m_150109_().m_8020_(9))
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            int stored = 0;
            for (int star = 1; star <= 7 && inside != null; star++) {
                ItemStack rest = inside.insertItem(star - 1, new ItemStack(earth.getBlockForStar(star)), false);
                stored += rest.m_41619_() ? 1 : 0;
            }
            check("backpack holds the 7 balls", stored == 7);
            check("full set in a backpack in the inventory: ground Earth ball stays", !pickUp(fh, ow, earth.getBlockForStar(1)));
            ItemStack moved = fh.m_150109_().m_8020_(9).m_41777_();
            fh.m_150109_().m_6211_();
            fh.m_36327_().m_6836_(0, moved);
            check("full set in a backpack in the ender chest: ground Earth ball stays", !pickUp(fh, ow, earth.getBlockForStar(1)));
            fh.m_36327_().m_6211_();
        }
        check("set stored elsewhere: the block lifts (ground Earth ball picked up)", pickUp(fh, ow, earth.getBlockForStar(4)));
        fh.m_150109_().m_6211_();
    }

    /**
     * Pistons cannot push, pull or break a dragon ball (4.7.36). Five rigs on a stone floor, each piston
     * facing north: a piston against a ball, a sticky piston pulling a ball, a piston pushing stone into a
     * ball, and a piston moving slime / honey with a ball stuck to its side. Read a second after they fire.
     */
    private static void pistonTest(ServerLevel ow, DragonBallSetDefinition earth) {
        BlockPos spawn = ow.m_220360_();
        int x0 = spawn.m_123341_() - 60;
        int z0 = spawn.m_123343_() - 40;
        int y = ow.m_6924_(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0) + 3;
        Block ball = earth.getBlockForStar(1);
        RIGS.clear();
        for (int i = 0; i < RIG_NAMES.length; i++) {
            BlockPos p = new BlockPos(x0 + i * 8, y, z0);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -5; dz <= 2; dz++) {
                    ow.m_46597_(p.m_7918_(dx, -1, dz), vanillaBlock("stone").m_49966_());
                    ow.m_46597_(p.m_7918_(dx, 0, dz), Blocks.f_50016_.m_49966_());
                    ow.m_46597_(p.m_7918_(dx, 1, dz), Blocks.f_50016_.m_49966_());
                }
            }
            BlockPos at = switch (i) {
                case 0 -> p.m_122012_();
                case 1 -> p.m_122013_(2);
                case 2 -> p.m_122013_(2);
                default -> p.m_122012_().m_122024_();
            };
            if (i != 1) {
                ow.m_46597_(at, ball.m_49966_());
            }
            if (i == 2) {
                ow.m_46597_(p.m_122012_(), vanillaBlock("stone").m_49966_());
            } else if (i == 3) {
                ow.m_46597_(p.m_122012_(), vanillaBlock("slime_block").m_49966_());
            } else if (i == 4) {
                ow.m_46597_(p.m_122012_(), vanillaBlock("honey_block").m_49966_());
            }
            ow.m_46597_(p, vanillaBlock(i == 1 ? "sticky_piston" : "piston").m_49966_());
            ow.m_46597_(p.m_122019_(), vanillaBlock("redstone_block").m_49966_());
            RIGS.add(new BlockPos[] {p, at});
        }
        pistonLevel = ow;
        pistonBall = ball;
        pistonStage = 0;
        pistonCheckAt = ow.m_7654_().m_129921_() + 6;
        log("piston rigs placed from " + RIGS.get(0)[0]);
    }

    private static final String[] RIG_NAMES = {"a piston pushing a ball", "a sticky piston pulling a ball",
            "a piston pushing stone into a ball", "a piston moving slime with a ball stuck to it",
            "a piston moving honey with a ball stuck to it"};
    private static final List<BlockPos[]> RIGS = new ArrayList<>();
    private static ServerLevel pistonLevel;
    private static Block pistonBall;
    private static int pistonStage;
    private static int pistonCheckAt = Integer.MAX_VALUE;

    /** Called every tick: stage 0 sets up the sticky pull, stage 1 reads every rig and cleans up. */
    public static void pistonTick(MinecraftServer server, int tick) {
        if (pistonLevel == null || tick < pistonCheckAt) {
            return;
        }
        if (pistonStage == 0) {
            // the sticky piston has extended into air; put the ball on its head and cut the power to pull it
            BlockPos[] sticky = RIGS.get(1);
            pistonLevel.m_46597_(sticky[1], pistonBall.m_49966_());
            pistonLevel.m_46597_(sticky[0].m_122019_(), Blocks.f_50016_.m_49966_());
            pistonStage = 1;
            pistonCheckAt = tick + 20;
            return;
        }
        for (int i = 0; i < RIGS.size(); i++) {
            BlockPos[] rig = RIGS.get(i);
            int dropped = 0;
            for (Entity entity : pistonLevel.m_8583_()) {
                if (entity instanceof ItemEntity item && !item.m_213877_() && DragonBallSetHold.setOf(item.m_32055_()) != null
                        && item.m_20183_().m_123331_(rig[1]) < 64) {
                    dropped++;
                    item.m_146870_(); // the probe's own ball; do not let it despawn into a lost-item respawn
                }
            }
            boolean stayed = isBall(pistonLevel, rig[1]);
            log("piston rig: " + RIG_NAMES[i] + " -> ball " + (stayed ? "stayed in place"
                    : dropped > 0 ? "was broken and dropped as an item" : "was moved"));
            check("piston: " + RIG_NAMES[i] + " leaves it in place", stayed && dropped == 0);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -5; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        pistonLevel.m_7471_(rig[0].m_7918_(dx, dy, dz), false);
                    }
                }
            }
        }
        pistonLevel = null;
    }

    // ---------------------------------------------------------------- helpers

    private static void next(int tick, int seconds) {
        step++;
        waitUntil = tick + seconds * SECOND;
    }

    private static FakePlayer player(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("lm-db-probe:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, name));
    }

    private static Item vanillaItem(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", id));
    }

    private static Block vanillaBlock(String id) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", id));
    }

    private static ItemStack radar(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("dragonminez", id)));
    }

    private static void hold(FakePlayer player, ItemStack stack) {
        player.m_21008_(InteractionHand.MAIN_HAND, stack);
    }

    private static boolean breakBlock(FakePlayer player, BlockPos pos) {
        player.m_6034_(pos.m_123341_() + 0.5, pos.m_123342_() + 1, pos.m_123343_() + 0.5);
        return player.f_8941_.m_9280_(pos);
    }

    private static void leftClick(FakePlayer player, BlockPos pos) {
        player.m_6034_(pos.m_123341_() + 0.5, pos.m_123342_() + 1, pos.m_123343_() + 0.5);
        ForgeHooks.onLeftClickBlock(player, pos, Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
    }

    /** Drop one ball item at the player and let them touch it; true when it was picked up. */
    private static boolean pickUp(FakePlayer player, ServerLevel level, Block ball) {
        ItemEntity item = new ItemEntity(level, player.m_20185_(), player.m_20186_(), player.m_20189_(), new ItemStack(ball));
        item.m_32061_();
        level.m_7967_(item);
        item.m_6123_(player);
        boolean taken = item.m_213877_() || item.m_32055_().m_41619_();
        if (!taken) {
            item.m_146870_();
        }
        return taken;
    }

    private static boolean isBall(ServerLevel level, BlockPos pos) {
        return level.m_8055_(pos).m_60734_() instanceof com.dragonminez.common.init.block.custom.DragonBallBlock;
    }

    private static int carried(FakePlayer player, DragonBallSetDefinition set) {
        int n = 0;
        for (int i = 0; i < player.m_150109_().m_6643_(); i++) {
            ItemStack stack = player.m_150109_().m_8020_(i);
            if (!stack.m_41619_() && set.getId().equals(DragonBallSetHold.setOf(stack))) {
                n += stack.m_41613_();
            }
        }
        return n;
    }

    private static int count(MinecraftServer server, DragonBallSetDefinition set) {
        return DragonBallRespawn.inWorld(server, set);
    }

    private static List<BlockPos> active(ServerLevel level, DragonBallSetDefinition set) {
        List<BlockPos> all = new ArrayList<>();
        DragonBallSavedData data = DragonBallSavedData.get(level);
        for (int star : set.getStars()) {
            all.addAll(data.getActiveBalls(set.getId(), star));
        }
        return all;
    }

    private static List<BlockPos> known(ServerLevel level, DragonBallSetDefinition set) {
        List<BlockPos> all = active(level, set);
        DragonBallSavedData data = DragonBallSavedData.get(level);
        for (int star : set.getStars()) {
            all.addAll(data.getPendingBalls(set.getId(), star));
        }
        return all;
    }

    private static int pending(ServerLevel level, DragonBallSetDefinition set) {
        int n = 0;
        DragonBallSavedData data = DragonBallSavedData.get(level);
        for (int star : set.getStars()) {
            n += data.getPendingBalls(set.getId(), star).size();
        }
        return n;
    }

    /** Load the chunk of every pending ball so DMZ places it. */
    private static void materialize(ServerLevel level, DragonBallSetDefinition set) {
        DragonBallSavedData data = DragonBallSavedData.get(level);
        for (int star : set.getStars()) {
            for (BlockPos target : new ArrayList<>(data.getPendingBalls(set.getId(), star))) {
                level.m_8602_(target.m_123341_() >> 4, target.m_123343_() >> 4, true);
            }
        }
    }

    private static long xz(BlockPos p) {
        return ((long) p.m_123341_() << 32) ^ (p.m_123343_() & 0xffffffffL);
    }

    private static void command(MinecraftServer server, String command) {
        server.m_129892_().m_230957_(server.m_129893_(), command);
    }

    private static void log(String what) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] dragon ball probe: {}", AdaptiveDifficultyMod.MOD_ID, what);
    }

    private static void check(String what, boolean ok) {
        AdaptiveDifficultyMod.LOGGER.info("[{}] dragon ball probe check {}: {}", AdaptiveDifficultyMod.MOD_ID, what,
                ok ? "OK" : "WRONG");
    }
}
