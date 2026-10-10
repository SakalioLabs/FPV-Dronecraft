package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.client.sound.CallbackStreamCapture;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
        "com.tenicana.dronecraft.client.sound.ProceduralDroneAudioStream",
        "com.tenicana.dronecraft.client.sound.ListenerReverbAudioStream"
}, remap = false)
abstract class CallbackStreamCaptureMixin {
    @ModifyVariable(method = "readCallback(JI)I", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int fpvdrone$beginCallback(int requested) {
        CallbackStreamCapture.begin(this);
        return requested;
    }
    @ModifyReturnValue(method = "readCallback(JI)I", at = @At("RETURN"), remap = false)
    private int fpvdrone$captureCallback(int returned, long samples, int requested) {
        CallbackStreamCapture.record(this, samples, requested, returned);
        return returned;
    }

    @Inject(method = "read(I)Ljava/nio/ByteBuffer;", at = @At("HEAD"), remap = false)
    private void fpvdrone$countQueuedRead(int requested, CallbackInfoReturnable<java.nio.ByteBuffer> callback) {
        CallbackStreamCapture.countReads(this);
    }
}
