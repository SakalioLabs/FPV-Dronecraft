package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.client.sound.MainContextOutputCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.ByteBuffer;

@Mixin(targets = "com.tenicana.dronecraft.client.sound.ProceduralDroneAudioStream", remap = false)
abstract class ProceduralOutputPcmMixin {
    @Inject(method = "read(I)Ljava/nio/ByteBuffer;", at = @At("RETURN"), remap = false)
    private void mainOutput$read(int requested, CallbackInfoReturnable<ByteBuffer> callback) {
        MainContextOutputCapture.record(this, callback.getReturnValue());
    }
}
