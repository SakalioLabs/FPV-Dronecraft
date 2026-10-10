package com.tenicana.dronecraft.client.sound.callbackprobe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer;
import com.tenicana.dronecraft.client.sound.CallbackStreamCapture;
import com.tenicana.dronecraft.client.sound.DroneAudioControlFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.tenicana.dronecraft.client.sound.DroneAcousticRenderState", remap = false)
abstract class DryCallbackFrameMixin {
    @ModifyExpressionValue(method = "callbackAudioSnapshot", at = @At(value = "INVOKE",
            target = "Lcom/tenicana/dronecraft/client/sound/DroneCallbackControlCoordinator;committedFrame()Lcom/tenicana/dronecraft/client/sound/DroneAudioControlFrame;"), remap = false)
    private DroneAudioControlFrame fpvdrone$observeCommittedDryFrame(
            DroneAudioControlFrame frame, Object stream, Layer layer) {
        CallbackStreamCapture.controlFrame(stream, frame);
        return frame;
    }
}
