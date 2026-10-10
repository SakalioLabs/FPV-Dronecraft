package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.MainContextOutputCapture;
import com.tenicana.dronecraft.client.sound.WetMainOutputCapture;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "com.tenicana.dronecraft.client.sound.ListenerReverbHistoryProducer", remap = false)
abstract class WetMixerStagesMixin {
    @Inject(method = "renderMixed(Ljava/util/List;[FI)V", at = @At("HEAD"))
    private void fpvdrone$begin(List<?> sources, float[] output, int frameCount, CallbackInfo ci) {
        WetMainOutputCapture.beginBatch(sources, frameCount);
    }

    @Inject(method = "addToMix([F[FDI)V", at = @At("RETURN"))
    private static void fpvdrone$layer(float[] output, float[] source, double amplitude, int frameCount, CallbackInfo ci) {
        WetMainOutputCapture.layer(output, source, amplitude, frameCount);
    }

    @Inject(method = "renderMixed(Ljava/util/List;[FI)V", at = @At("RETURN"))
    private void fpvdrone$finish(List<?> sources, float[] output, int frameCount, CallbackInfo ci) {
        WetMainOutputCapture.finishBatch(output, frameCount);
    }
}
