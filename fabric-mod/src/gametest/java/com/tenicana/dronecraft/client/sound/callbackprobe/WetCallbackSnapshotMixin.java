package com.tenicana.dronecraft.client.sound.callbackprobe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tenicana.dronecraft.client.sound.CallbackStreamCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.tenicana.dronecraft.client.sound.ListenerReverbAudioStream", remap = false)
abstract class WetCallbackSnapshotMixin {
    @ModifyExpressionValue(method = "readCallback(JI)I", at = @At(value = "INVOKE",
            target = "Lcom/tenicana/dronecraft/client/sound/ListenerReverbState;callbackSnapshot(Ljava/lang/Object;)Lcom/tenicana/dronecraft/client/sound/ListenerReverbState$Snapshot;"), remap = false)
    @Coerce
    private Object fpvdrone$observeWetSnapshot(@Coerce Object snapshot) {
        CallbackStreamCapture.control(this, snapshot);
        return snapshot;
    }
}
