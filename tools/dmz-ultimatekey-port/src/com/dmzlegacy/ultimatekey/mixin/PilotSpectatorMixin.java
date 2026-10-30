package com.dmzlegacy.ultimatekey.mixin;

import com.dmzlegacy.ultimatekey.agent.AgentPilot;
import com.dragonminez.common.network.C2S.StrikeAttackC2S;
import com.dragonminez.common.network.C2S.TaiyokenCastC2S;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={StrikeAttackC2S.class, TaiyokenCastC2S.class}, remap=false)
public abstract class PilotSpectatorMixin {
    @Inject(method={"handle"}, at={@At(value="HEAD")}, cancellable=true)
    private void dmzultimatekey$notTheSpectator(Supplier<NetworkEvent.Context> ctx, CallbackInfo ci) {
        if (AgentPilot.spectatingBody(ctx.get().getSender())) {
            ctx.get().setPacketHandled(true);
            ci.cancel();
        }
    }
}

