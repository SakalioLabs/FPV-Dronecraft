package com.tenicana.dronecraft.client.sound.callbackprobe;

import com.tenicana.dronecraft.client.sound.WetOracleTrace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** All these lifecycle/shadow entry points execute outside the audio callback. */
@Mixin(targets = "com.tenicana.dronecraft.client.sound.ListenerReverbHistoryProducer", remap = false)
abstract class WetOracleProducerLifecycleMixin {
    @Shadow @Final private float[] shadowBuffer;
    @Shadow private volatile long shadowRenderCount;
    @Unique private long fpvdrone$shadowCountBefore;

    @Inject(method = "clearSignalState()V", at = @At("RETURN"), remap = false)
    private void fpvdrone$observeReset(CallbackInfo callback) {
        WetOracleTrace.signalReset(this);
    }

    @Inject(method = "advanceShadow(Lcom/tenicana/dronecraft/client/sound/PreparedListenerReverbMixer$PreparedSources;)V",
            at = @At("HEAD"), remap = false)
    private void fpvdrone$beforeShadow(@Coerce Object sources, CallbackInfo callback) {
        fpvdrone$shadowCountBefore = shadowRenderCount;
    }

    @Inject(method = "advanceShadow(Lcom/tenicana/dronecraft/client/sound/PreparedListenerReverbMixer$PreparedSources;)V",
            at = @At("RETURN"), remap = false)
    private void fpvdrone$afterShadow(@Coerce Object sources, CallbackInfo callback) {
        WetOracleTrace.shadowRendered(this, sources, shadowBuffer,
                shadowRenderCount == fpvdrone$shadowCountBefore + 1);
    }

    @Inject(method = "acquireWet(Lcom/tenicana/dronecraft/client/sound/ListenerReverbState$Snapshot;)Lcom/tenicana/dronecraft/client/sound/ListenerReverbHistoryProducer$ActiveSession;",
            at = @At("RETURN"), remap = false)
    private void fpvdrone$observeAcquisition(@Coerce Object snapshot, CallbackInfoReturnable<Object> callback) {
        WetOracleTrace.acquired(this, callback.getReturnValue(), snapshot);
    }
}
