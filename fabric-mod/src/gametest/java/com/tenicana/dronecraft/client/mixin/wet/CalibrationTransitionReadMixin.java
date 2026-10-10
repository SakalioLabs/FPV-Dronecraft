package com.tenicana.dronecraft.client.mixin.wet;
import com.tenicana.dronecraft.client.sound.CalibrationTransitionQueueCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.ByteBuffer;
@Mixin(targets = {"com.tenicana.dronecraft.client.sound.ProceduralDroneAudioStream", "com.tenicana.dronecraft.client.sound.ListenerReverbAudioStream"}, remap = false)
abstract class CalibrationTransitionReadMixin {
    @Inject(method = "read(I)Ljava/nio/ByteBuffer;", at = @At("RETURN"), remap = false)
    private void transition$read(int requested, CallbackInfoReturnable<ByteBuffer> ci) { CalibrationTransitionQueueCapture.read(this, ci.getReturnValue()); }
}
