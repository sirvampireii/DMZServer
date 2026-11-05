package com.dbzlegacy.legacytextures.client;

import com.dbzlegacy.legacytextures.LegacyTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The client half of /legacytextures status: whether each hook is live, which sets are loaded, and
 * whether every DragonMineZ cache cleared on the last switch. A hook that never ran shows "not used
 * yet" (its target changed, or nothing has drawn through it since start); one that threw shows FAILED.
 */
public final class ClientStatus {
    private ClientStatus() {
    }

    public static void report() {
        say(Component.m_237110_("legacytextures.status.title", new Object[] {LegacyTextures.VERSION}), ChatFormatting.GOLD);
        say(Component.m_237110_("legacytextures.status.hooks", new Object[] {
                hook("TextureManager.getTexture", LtHooks.binds),
                hook("ReloadableResourceManager.getResource", LtHooks.lookups),
                hook("GeoModel.getBakedModel", LtHooks.models)}), ChatFormatting.GRAY);
        say(Component.m_237110_("legacytextures.status.sets", new Object[] {
                String.join(", ", ClientState.registry().all().keySet()),
                Component.m_237115_(ClientState.worldHdLoaded() ? "legacytextures.status.yes" : "legacytextures.status.no")}),
                ChatFormatting.GRAY);
        if (CacheFlush.failed().isEmpty()) {
            say(Component.m_237115_("legacytextures.status.cache_ok"), ChatFormatting.GRAY);
        } else {
            say(Component.m_237110_("legacytextures.status.cache_bad", new Object[] {String.join(", ", CacheFlush.failed())}),
                    ChatFormatting.RED);
        }
    }

    private static Component hook(String name, long calls) {
        if (LtHooks.failedHooks().contains(name)) {
            return Component.m_237115_("legacytextures.status.hook.failed").m_130940_(ChatFormatting.RED);
        }
        return calls > 0 ? Component.m_237115_("legacytextures.status.hook.active").m_130940_(ChatFormatting.GREEN)
                : Component.m_237115_("legacytextures.status.hook.idle").m_130940_(ChatFormatting.YELLOW);
    }

    private static void say(Component text, ChatFormatting color) {
        Minecraft.m_91087_().f_91065_.m_93076_().m_93785_(text.m_6881_().m_130940_(color));
    }
}
