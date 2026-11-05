package com.dbzlegacy.adaptivedifficulty.event;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.init.block.custom.DragonBallBlock;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.IItemHandler;

/**
 * One set per player (4.7.35). A player who already carries all seven balls of a set
 * cannot take another ball of that set. "Carries" spans the inventory, the ender chest,
 * and any item that holds items (Sophisticated Backpacks expose their contents as an item
 * handler), whether that bag sits in the inventory, the ender chest or a Curios slot.
 * Earth and Namek are counted apart; a partial set never blocks.
 */
public final class DragonBallSetHold {
    /** Bags inside bags: deep enough for a backpack in a backpack, bounded against loops. */
    private static final int MAX_DEPTH = 3;
    private static final long MESSAGE_GAP_MS = 3000L;
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
    private static volatile Method curiosInventory;
    private static volatile Method curiosEquipped;
    private static volatile boolean curiosChecked;

    private DragonBallSetHold() {}

    /** True when the player carries every star of {@code setId}. */
    public static boolean holdsFullSet(Player player, String setId) {
        if (player == null || setId == null) {
            return false;
        }
        DragonBallSetDefinition definition = DragonBallDefinitions.getBallSet(setId);
        if (definition == null || definition.getStars().isEmpty()) {
            return false;
        }
        Set<Integer> stars = new HashSet<>();
        scan(player.m_150109_(), definition, stars);
        scan(player.m_36327_(), definition, stars);
        IItemHandler curios = curios(player);
        if (curios != null) {
            scan(curios, definition, stars, 0);
        }
        return stars.containsAll(definition.getStars());
    }

    /** The set this ball belongs to, when the stack is a dragon ball; otherwise null. */
    public static String setOf(ItemStack stack) {
        if (stack == null || stack.m_41619_() || !(stack.m_41720_() instanceof BlockItem item)) {
            return null;
        }
        Block block = item.m_40614_();
        return block instanceof DragonBallBlock ball ? ball.getBallSetId() : null;
    }

    /** Short chat line, at most once every few seconds (a blocked item is touched every tick). */
    public static void tellAlreadyHeld(Player player, String setId) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(player.m_20148_());
        if (last != null && now - last < MESSAGE_GAP_MS) {
            return;
        }
        LAST_MESSAGE.put(player.m_20148_(), now);
        String name = setName(setId);
        player.m_5661_(Component.m_237113_("You already carry a full set of " + name
                        + " Dragon Balls (backpacks and ender chest count). Make a wish or leave some in a chest first.")
                .m_130940_(ChatFormatting.YELLOW), false);
    }

    /** Short action-bar line when a ball is hit without its radar. */
    public static void tellNeedsRadar(Player player, String setId) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(player.m_20148_());
        if (last != null && now - last < MESSAGE_GAP_MS) {
            return;
        }
        LAST_MESSAGE.put(player.m_20148_(), now);
        String radar = "namek".equals(setId) ? "a Namek Dragon Radar" : "a Dragon Radar";
        player.m_5661_(Component.m_237113_("Dragon Balls can't be broken. Left-click it with " + radar
                        + " or the Bi-Dimensional Radar to take it.")
                .m_130940_(ChatFormatting.YELLOW), true);
    }

    static String setName(String setId) {
        if ("earth".equals(setId)) {
            return "Earth";
        }
        if ("namek".equals(setId)) {
            return "Namek";
        }
        return setId == null || setId.isEmpty() ? "" : Character.toUpperCase(setId.charAt(0)) + setId.substring(1);
    }

    private static void scan(Container container, DragonBallSetDefinition definition, Set<Integer> stars) {
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.m_6643_(); i++) {
            check(container.m_8020_(i), definition, stars, 0);
        }
    }

    private static void scan(IItemHandler handler, DragonBallSetDefinition definition, Set<Integer> stars, int depth) {
        for (int i = 0; i < handler.getSlots(); i++) {
            check(handler.getStackInSlot(i), definition, stars, depth);
        }
    }

    private static void check(ItemStack stack, DragonBallSetDefinition definition, Set<Integer> stars, int depth) {
        if (stack == null || stack.m_41619_()) {
            return;
        }
        if (stack.m_41720_() instanceof BlockItem item) {
            Integer star = definition.getStarForBlock(item.m_40614_());
            if (star != null) {
                stars.add(star);
                return;
            }
        }
        if (depth >= MAX_DEPTH) {
            return;
        }
        try {
            // Forge patches ItemStack to be a capability provider; LM compiles against vanilla SRG.
            IItemHandler inner = ((ICapabilityProvider) (Object) stack).getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .orElse(null);
            if (inner != null) {
                scan(inner, definition, stars, depth + 1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Curios equipped items (a backpack worn on the back), when Curios is installed. */
    private static IItemHandler curios(Player player) {
        try {
            if (!curiosChecked) {
                synchronized (DragonBallSetHold.class) {
                    if (!curiosChecked) {
                        Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                        curiosInventory = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
                        curiosEquipped = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                                .getMethod("getEquippedCurios");
                        curiosChecked = true;
                    }
                }
            }
            if (curiosInventory == null) {
                return null;
            }
            Object lazy = curiosInventory.invoke(null, player);
            if (!(lazy instanceof net.minecraftforge.common.util.LazyOptional<?> optional)) {
                return null;
            }
            Object inventory = optional.orElse(null);
            return inventory == null ? null : (IItemHandler) curiosEquipped.invoke(inventory);
        } catch (Throwable t) {
            curiosChecked = true;
            curiosInventory = null;
            return null;
        }
    }
}
