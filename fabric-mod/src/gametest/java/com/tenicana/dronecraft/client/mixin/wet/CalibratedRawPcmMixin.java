package com.tenicana.dronecraft.client.mixin.wet;
import com.tenicana.dronecraft.client.sound.CalibratedGainCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.ByteBuffer;
@Mixin(targets = "com.tenicana.dronecraft.client.sound.ProceduralDroneAudioStream", remap = false)
abstract class CalibratedRawPcmMixin {
    @Inject(method = "read(I)Ljava/nio/ByteBuffer;", at = @At("HEAD"), remap = false)
    private void calibrated$before(int requested, CallbackInfoReturnable<ByteBuffer> ci) { CalibratedGainCapture.beforeRead(this); }
    @Inject(method = "read(I)Ljava/nio/ByteBuffer;", at = @At("RETURN"), remap = false)
    private void calibrated$after(int requested, CallbackInfoReturnable<ByteBuffer> ci) { CalibratedGainCapture.afterRead(this, ci.getReturnValue()); }
}
