package com.dbzlegacy.adaptivedifficulty.event;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dragonminez.common.dragonball.DragonRadarDefinition;
import com.dragonminez.common.init.block.custom.DragonBallBlock;
import com.dragonminez.common.init.item.DragonRadarItem;
import com.dragonminez.server.events.DragonBallsHandler;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * One left-click with the radar for that ball set takes the ball into the
 * inventory. The Earth radar takes Earth balls. The Namek radar takes Namek
 * balls. A different radar leaves the ball where it is.
 *
 * <p>FTB Chunks cancels the left click, and GriefPrevention cancels the break.
 * This click still takes the ball. Every other block stays protected.
 */
public final class DragonBallRadarPickup {
    private static final Map<String, Long> TAKEN = new ConcurrentHashMap<>();
    private static volatile boolean griefPreventionHooked;

    private DragonBallRadarPickup() {}

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event == null || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        registerGriefPrevention();
        LevelSide side = LevelSide.of(event);
        if (side == null) {
            return;
        }
        try {
            BlockPos pos = event.getPos();
            if (pos == null) {
                return;
            }
            BlockState state = side.level.m_8055_(pos);
            if (!(state.m_60734_() instanceof DragonBallBlock ball)) {
                return;
            }
            if (!holdsMatchingRadar(side.player, ball)) {
                return;
            }
            if (!deposit(side.level, pos, side.player, ball)) {
                return;
            }
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
        } catch (Throwable ignored) {
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event == null) {
            return;
        }
        registerGriefPrevention();
        try {
            Player player = event.getPlayer();
            BlockState state = event.getState();
            if (player == null || state == null || !(state.m_60734_() instanceof DragonBallBlock ball)) {
                return;
            }
            if (!holdsMatchingRadar(player, ball)) {
                return;
            }
            if (event.getLevel() instanceof ServerLevel level && wasTaken(level, event.getPos())) {
                event.setCanceled(true);
                return;
            }
            event.setCanceled(false);
        } catch (Throwable ignored) {
        }
    }

    /** GriefPrevention listens on Bukkit. Uncancel only a matching radar break. */
    public static void registerGriefPrevention() {
        if (griefPreventionHooked) {
            return;
        }
        synchronized (DragonBallRadarPickup.class) {
            if (griefPreventionHooked) {
                return;
            }
            try {
                hookGriefPrevention();
                griefPreventionHooked = true;
            } catch (Throwable t) {
                AdaptiveDifficultyMod.LOGGER.debug(
                        "[{}] Dragon ball claim bypass not hooked yet: {}",
                        AdaptiveDifficultyMod.MOD_ID,
                        t.toString());
            }
        }
    }

    private static void hookGriefPrevention() throws ReflectiveOperationException {
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
        Object server = bukkit.getMethod("getServer").invoke(null);
        Object pluginManager = server.getClass().getMethod("getPluginManager").invoke(server);
        Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class)
                .invoke(pluginManager, "GriefPrevention");
        if (plugin == null) {
            throw new IllegalStateException("GriefPrevention not loaded");
        }
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> listenerClass = Class.forName("org.bukkit.event.Listener", true, loader);
        Class<?> executorClass = Class.forName("org.bukkit.plugin.EventExecutor", true, loader);
        Class<?> priorityClass = Class.forName("org.bukkit.event.EventPriority", true, loader);
        Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin", true, loader);
        Class<?> registeredClass = Class.forName("org.bukkit.plugin.RegisteredListener", true, loader);
        Object listener = Proxy.newProxyInstance(loader, new Class<?>[] {listenerClass}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectCall(proxy, method.getName(), args);
            }
            return null;
        });
        Object executor = Proxy.newProxyInstance(loader, new Class<?>[] {executorClass}, (proxy, method, args) -> {
            if ("execute".equals(method.getName()) && args != null && args.length >= 2 && args[1] != null) {
                onBukkitBreak(args[1]);
            }
            return null;
        });
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object priority = Enum.valueOf((Class<? extends Enum>) priorityClass.asSubclass(Enum.class), "LOWEST");
        Constructor<?> registeredCtor = registeredClass.getConstructor(
                listenerClass, executorClass, priorityClass, pluginClass, boolean.class);
        Object registered = registeredCtor.newInstance(listener, executor, priority, plugin, false);
        Class<?> breakEvent = Class.forName("org.bukkit.event.block.BlockBreakEvent", true, loader);
        Object handlers = breakEvent.getMethod("getHandlerList").invoke(null);
        handlers.getClass().getMethod("register", registeredClass).invoke(handlers, registered);
    }

    private static Object objectCall(Object proxy, String name, Object[] args) {
        if ("toString".equals(name)) {
            return "DragonBallRadarPickup";
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return proxy == (args == null || args.length == 0 ? null : args[0]);
        }
        return null;
    }

    private static void onBukkitBreak(Object event) {
        try {
            if (event == null || !event.getClass().getName().endsWith("BlockBreakEvent")) {
                return;
            }
            Object bukkitPlayer = event.getClass().getMethod("getPlayer").invoke(event);
            Object bukkitBlock = event.getClass().getMethod("getBlock").invoke(event);
            if (bukkitPlayer == null || bukkitBlock == null) {
                return;
            }
            Object handle = bukkitPlayer.getClass().getMethod("getHandle").invoke(bukkitPlayer);
            Object world = bukkitBlock.getClass().getMethod("getWorld").invoke(bukkitBlock);
            Object levelHandle = world.getClass().getMethod("getHandle").invoke(world);
            if (!(handle instanceof Player player) || !(levelHandle instanceof ServerLevel level)) {
                return;
            }
            int x = (Integer) bukkitBlock.getClass().getMethod("getX").invoke(bukkitBlock);
            int y = (Integer) bukkitBlock.getClass().getMethod("getY").invoke(bukkitBlock);
            int z = (Integer) bukkitBlock.getClass().getMethod("getZ").invoke(bukkitBlock);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.m_8055_(pos);
            if (!(state.m_60734_() instanceof DragonBallBlock ball) || !holdsMatchingRadar(player, ball)) {
                return;
            }
            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, false);
            if (wasTaken(level, pos)) {
                try {
                    event.getClass().getMethod("setDropItems", boolean.class).invoke(event, false);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean holdsMatchingRadar(Player player, DragonBallBlock ball) {
        if (player == null || ball == null) {
            return false;
        }
        ItemStack held = player.m_21205_();
        if (held == null || held.m_41619_() || !(held.m_41720_() instanceof DragonRadarItem radar)) {
            return false;
        }
        DragonRadarDefinition definition = radar.getDefinition();
        return definition != null && definition.supportsBallSet(ball.getBallSetId());
    }

    private static boolean deposit(ServerLevel level, BlockPos pos, Player player, DragonBallBlock ball) {
        if (!level.m_8055_(pos).m_60713_(ball)) {
            return false;
        }
        ItemStack stack = new ItemStack(ball);
        if (stack.m_41619_()) {
            return false;
        }
        markTaken(level, pos);
        level.m_46597_(pos, Blocks.f_50016_.m_49966_());
        // DMZ 2.2: DragonBallBlock.onRemove already unregisters the ball when it is set to air.
        // Repeat it here so a skipped onRemove cannot leave a tracked ball that maintainLevel
        // would call stale and respawn elsewhere (a duplicate). A second call is a no-op.
        try {
            DragonBallsHandler.onDragonBallRemoved(level, ball, pos.m_7949_());
        } catch (Throwable ignored) {
        }
        if (!player.m_150109_().m_36054_(stack)) {
            player.m_36176_(stack, false);
        }
        level.m_5594_(null, pos, SoundEvents.f_12019_, SoundSource.PLAYERS, 0.4f, 1.0f);
        return true;
    }

    private static void markTaken(ServerLevel level, BlockPos pos) {
        long now = level.m_46467_();
        TAKEN.put(key(level, pos), now);
        if (TAKEN.size() > 64) {
            TAKEN.entrySet().removeIf(entry -> now - entry.getValue() > 40L);
        }
    }

    private static boolean wasTaken(ServerLevel level, BlockPos pos) {
        Long tick = TAKEN.get(key(level, pos));
        return tick != null && level.m_46467_() - tick < 40L;
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.m_46472_().m_135782_() + " " + pos.m_121878_();
    }

    private static final class LevelSide {
        final ServerLevel level;
        final Player player;

        private LevelSide(ServerLevel level, Player player) {
            this.level = level;
            this.player = player;
        }

        static LevelSide of(PlayerInteractEvent.LeftClickBlock event) {
            if (event.getLevel() == null || event.getLevel().m_5776_()) {
                return null;
            }
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return null;
            }
            Player player = event.getEntity();
            if (player == null || player.m_5833_()) {
                return null;
            }
            return new LevelSide(level, player);
        }
    }
}
