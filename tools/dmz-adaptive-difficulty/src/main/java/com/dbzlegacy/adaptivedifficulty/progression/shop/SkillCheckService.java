package com.dbzlegacy.adaptivedifficulty.progression.shop;

import com.dbzlegacy.adaptivedifficulty.command.LmCommandMessages;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.SkillsMenu;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.StaffAccess;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Donator / CNPC Skill Check — reuses Skills GUI data with player-facing branding.
 * Staff Progression {@code /skills} remains the full staff unlock browser.
 */
public final class SkillCheckService {
    private static final String TAG_NEEDLE = "lm_skillcheck";
    private static final long SESSION_MS = 120_000L;
    private static final Map<UUID, Long> SESSIONS = new ConcurrentHashMap<>();

    private SkillCheckService() {}

    public static boolean canUse(ServerPlayer player) {
        return StaffAccess.hasSkillCheck(player);
    }

    /** True while the player recently opened Skill Check (inventory title / nav branding). */
    public static boolean inSession(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        Long exp = SESSIONS.get(player.m_20148_());
        return exp != null && exp > System.currentTimeMillis();
    }

    public static boolean inSession(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long exp = SESSIONS.get(playerId);
        return exp != null && exp > System.currentTimeMillis();
    }

    public static void markSession(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SESSIONS.put(player.m_20148_(), System.currentTimeMillis() + SESSION_MS);
    }

    public static void clearSession(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SESSIONS.remove(player.m_20148_());
    }

    public static void clearSession(UUID playerId) {
        if (playerId != null) {
            SESSIONS.remove(playerId);
        }
    }

    /**
     * Open Skill Check UI for {@code page} (core / saga; advanced aliases to saga).
     * Requires {@link #canUse}.
     */
    public static void open(ServerPlayer player, String page) {
        if (player == null) {
            return;
        }
        if (!DifficultyConfig.get().enableSkillCheck || !DifficultyConfig.get().enableSkillUnlockService) {
            DmzRewards.msg(player, "§cSkill Check is disabled.");
            return;
        }
        if (!canUse(player) && !inSession(player)) {
            DmzRewards.msg(player, LmCommandMessages.SKILLCHECK_DONATOR);
            return;
        }
        markSession(player);
        String target = page == null || page.isBlank() ? "core" : page;
        SkillsMenu.openSkillCheck(player, target);
    }

    /**
     * CNPC right-click: a Skill Check NPC opens the menu for every player.
     * Slash {@code /skillcheck} still requires {@link #canUse}.
     */
    public static boolean tryOpenFromNpc(ServerPlayer player, Entity npc) {
        if (player == null || npc == null) {
            return false;
        }
        if (!(npc instanceof LivingEntity)) {
            return false;
        }
        if (!looksLikeCustomNpc(npc) || !isSkillCheckNpc(npc)) {
            return false;
        }
        if (!DifficultyConfig.get().enableSkillCheck || !DifficultyConfig.get().enableSkillUnlockService) {
            DmzRewards.msg(player, "§cSkill Check is disabled.");
            return true;
        }
        // Slash /skillcheck still uses canUse(player). This NPC is for every player.
        openAtNpc(player);
        return true;
    }

    /**
     * Right-click Skill Check NPC. Any online player may open it.
     * Slash {@code /skillcheck} still requires {@link #canUse}.
     */
    public static void openAtNpc(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!DifficultyConfig.get().enableSkillCheck || !DifficultyConfig.get().enableSkillUnlockService) {
            DmzRewards.msg(player, "§cSkill Check is disabled.");
            return;
        }
        markSession(player);
        com.dbzlegacy.adaptivedifficulty.gui.SkillsMenu.openSkillCheck(player, "core");
    }

    /** Trigger 21 / dialog script path. The NPC is the gate. Slash {@code /skillcheck} still uses {@link #canUse}. */
    public static void trigger21(ServerPlayer player) {
        openAtNpc(player);
    }

    public static boolean looksLikeCustomNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        String cn = entity.getClass().getName();
        if (cn == null) {
            return false;
        }
        String lower = cn.toLowerCase(Locale.ROOT);
        return lower.contains("noppes") || lower.contains("entitycustomnpc") || lower.contains("customnpc");
    }

    public static boolean isSkillCheckNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (hasSkillCheckTag(entity)) {
            return true;
        }
        String name = displayName(entity);
        if (name == null || name.isBlank()) {
            return false;
        }
        String hay = stripFormatting(name).toLowerCase(Locale.ROOT);
        List<String> needles = DifficultyConfig.get().skillCheckNpcNameContains;
        if (needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle == null || needle.isBlank()) {
                continue;
            }
            if (hay.contains(stripFormatting(needle).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSkillCheckTag(Entity entity) {
        try {
            Set<String> tags = entity.m_19880_(); // getTags
            if (tags != null) {
                for (String tag : tags) {
                    if (tag != null && tag.toLowerCase(Locale.ROOT).contains(TAG_NEEDLE)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object bukkit = entity.getClass().getMethod("getBukkitEntity").invoke(entity);
            if (bukkit != null) {
                Object tags = bukkit.getClass().getMethod("getScoreboardTags").invoke(bukkit);
                if (tags instanceof Iterable<?> it) {
                    for (Object tag : it) {
                        if (tag != null && String.valueOf(tag).toLowerCase(Locale.ROOT).contains(TAG_NEEDLE)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String displayName(Entity entity) {
        try {
            if (entity.m_8077_()) { // hasCustomName
                Component custom = entity.m_7770_(); // getCustomName
                if (custom != null) {
                    String s = custom.getString();
                    if (s != null && !s.isBlank()) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Component display = entity.m_7755_(); // getDisplayName / getName
            if (display != null) {
                return display.getString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String stripFormatting(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "");
    }
}
