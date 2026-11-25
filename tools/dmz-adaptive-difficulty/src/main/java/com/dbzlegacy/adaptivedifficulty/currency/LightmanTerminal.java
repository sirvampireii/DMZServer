package com.dbzlegacy.adaptivedifficulty.currency;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import io.github.lightman314.lightmanscurrency.LCText;
import io.github.lightman314.lightmanscurrency.api.misc.QuarantineAPI;
import io.github.lightman314.lightmanscurrency.common.menus.providers.TerminalMenuProvider;
import io.github.lightman314.lightmanscurrency.common.menus.validation.types.SimpleValidator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Opens Lightman's network terminal the same way {@code /lcterminal} does.
 * Replaces the Fabled Terminal skill (command mechanic {@code lcterminal} as OP)
 * and the CMI CustomAlias that force-cast it.
 */
public final class LightmanTerminal {
    private LightmanTerminal() {}

    /**
     * @return empty when the menu opened or Lightman already told the player why it did not;
     *         otherwise a chat line for the caller to send
     */
    public static String open(ServerPlayer player) {
        if (player == null) {
            return "§cPlayers only.";
        }
        if (!ModList.get().isLoaded("lightmanscurrency")) {
            return "§cLightman's Currency is not installed.";
        }
        try {
            if (QuarantineAPI.IsDimensionQuarantined(player)) {
                player.m_213846_(LCText.MESSAGE_DIMENSION_QUARANTINED_TERMINAL.get());
                return "";
            }
            TerminalMenuProvider.OpenMenu(player, SimpleValidator.NULL);
            return "";
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] /lm terminal failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return "§cCould not open the currency terminal.";
        }
    }
}
