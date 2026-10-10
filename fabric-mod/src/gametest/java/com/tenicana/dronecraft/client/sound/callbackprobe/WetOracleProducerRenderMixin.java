package com.tenicana.dronecraft.client.sound.callbackprobe;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tenicana.dronecraft.client.sound.WetOracleTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Primitive return hook: no CallbackInfo or Operation allocation on render. */
@Mixin(targets = "com.tenicana.dronecraft.client.sound.ListenerReverbHistoryProducer", remap = false)
abstract class WetOracleProducerRenderMixin {
    @ModifyReturnValue(method = "renderWet(Lcom/tenicana/dronecraft/client/sound/ListenerReverbHistoryProducer$ActiveSession;Lcom/tenicana/dronecraft/client/sound/ListenerReverbState$Snapshot;[F[FI)Z",
            at = @At("RETURN"), remap = false)
    private boolean fpvdrone$observeWetRender(boolean accepted, @Coerce Object session,
            @Coerce Object snapshot, float[] mixed, float[] wet, int frames) {
        WetOracleTrace.wetRendered(this, session, snapshot, mixed, frames, accepted);
        return accepted;
    }
}
