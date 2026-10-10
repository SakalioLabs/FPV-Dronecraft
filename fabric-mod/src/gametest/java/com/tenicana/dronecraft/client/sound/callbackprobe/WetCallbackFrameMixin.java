package com.tenicana.dronecraft.client.sound.callbackprobe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tenicana.dronecraft.client.sound.CallbackStreamCapture;
import com.tenicana.dronecraft.client.sound.DroneAudioControlFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.tenicana.dronecraft.client.sound.ListenerReverbState", remap = false)
abstract class WetCallbackFrameMixin {
    @ModifyExpressionValue(method = "callbackSnapshot", at = @At(value = "INVOKE",
            target = "Lcom/tenicana/dronecraft/client/sound/DroneCallbackControlCoordinator;committedFrame()Lcom/tenicana/dronecraft/client/sound/DroneAudioControlFrame;"), remap = false)
    private DroneAudioControlFrame fpvdrone$observeCommittedWetFrame(
            DroneAudioControlFrame frame, Object stream) {
        CallbackStreamCapture.controlFrame(stream, frame);
        return frame;
    }
}
