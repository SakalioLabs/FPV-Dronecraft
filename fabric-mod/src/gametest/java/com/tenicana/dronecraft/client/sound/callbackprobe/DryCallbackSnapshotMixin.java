package com.tenicana.dronecraft.client.sound.callbackprobe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tenicana.dronecraft.client.sound.CallbackStreamCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.tenicana.dronecraft.client.sound.ProceduralDroneAudioStream", remap = false)
abstract class DryCallbackSnapshotMixin {
    @ModifyExpressionValue(method = "readCallback(JI)I", at = @At(value = "INVOKE",
            target = "Lcom/tenicana/dronecraft/client/sound/DroneAcousticRenderState;callbackAudioSnapshot(Ljava/lang/Object;Lcom/tenicana/dronecraft/acoustics/PhaseContinuousSynthesizer$Layer;)Lcom/tenicana/dronecraft/client/sound/DroneAcousticRenderState$AudioSnapshot;"), remap = false)
    @Coerce
    private Object fpvdrone$observeDrySnapshot(@Coerce Object snapshot) {
        CallbackStreamCapture.control(this, snapshot);
        return snapshot;
    }
}
