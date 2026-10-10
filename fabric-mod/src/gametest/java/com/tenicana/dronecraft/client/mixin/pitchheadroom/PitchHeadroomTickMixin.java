package com.tenicana.dronecraft.client.mixin.pitchheadroom;

import com.google.gson.JsonObject;
import com.tenicana.dronecraft.client.sound.PitchHeadroomCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe actual target returns; the last return in a tick is used by production. */
@Mixin(targets = "com.tenicana.dronecraft.client.sound.DroneLoopSoundInstance")
abstract class PitchHeadroomTickMixin {
    @Unique private final ThreadLocal<JsonObject> fpvdrone$headroomTick = new ThreadLocal<>();
    @Inject(method = "tick", at = @At("HEAD"))
    private void fpvdrone$beforeTick(CallbackInfo ci) {
        fpvdrone$headroomTick.set(PitchHeadroomCapture.beforeTick(this));
    }
    @Inject(method = "targetVolume", at = @At("RETURN"))
    private void fpvdrone$volumeTarget(CallbackInfoReturnable<Float> ci) {
        PitchHeadroomCapture.target(fpvdrone$headroomTick.get(), ci.getReturnValueF(), false);
    }
    @Inject(method = "targetPitch", at = @At("RETURN"))
    private void fpvdrone$pitchTarget(CallbackInfoReturnable<Float> ci) {
        PitchHeadroomCapture.target(fpvdrone$headroomTick.get(), ci.getReturnValueF(), true);
    }
    @Inject(method = "tick", at = @At("RETURN"))
    private void fpvdrone$afterTick(CallbackInfo ci) {
        PitchHeadroomCapture.afterTick(this, fpvdrone$headroomTick.get());
        fpvdrone$headroomTick.remove();
    }
}
