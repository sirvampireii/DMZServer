package com.dbzlegacy.adaptivedifficulty.mixin.dmzultra;

import com.dbzlegacy.adaptivedifficulty.progression.shop.SlotRecordCapture;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 4.7.43: DMZ Ultra 0.4.0 saves a character's slot record only on a switch; this saves it at logout and server
 * stop too ({@link SlotRecordCapture}). The plugin applies it only when {@code com.dmzultra.slots.SlotService}
 * exists; {@code require = 0} keeps LM running if a later Ultra renames these handlers. The switch path, the
 * index summary and Ultra itself are unchanged.
 */
@Mixin(targets = "com.dmzultra.slots.SlotService", remap = false)
public abstract class DmzultraSlotCaptureMixin {

    @Inject(method = "logout", at = @At("HEAD"), require = 0, remap = false)
    private static void lm$slotGenAtLogout(PlayerEvent.PlayerLoggedOutEvent event, CallbackInfo ci) {
        try {
            SlotRecordCapture.beforeLogout(event == null ? null : event.getEntity());
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "logout", at = @At("RETURN"), require = 0, remap = false)
    private static void lm$saveSlotAtLogout(PlayerEvent.PlayerLoggedOutEvent event, CallbackInfo ci) {
        try {
            SlotRecordCapture.afterLogout(event == null ? null : event.getEntity());
        } catch (Throwable ignored) {
        }
    }

    /** Head, so Ultra's index cache is still filled when the online players are saved. */
    @Inject(method = "stopped", at = @At("HEAD"), require = 0, remap = false)
    private static void lm$saveSlotsAtStop(ServerStoppedEvent event, CallbackInfo ci) {
        try {
            SlotRecordCapture.onStopped(event == null ? null : event.getServer());
        } catch (Throwable ignored) {
        }
    }
}
