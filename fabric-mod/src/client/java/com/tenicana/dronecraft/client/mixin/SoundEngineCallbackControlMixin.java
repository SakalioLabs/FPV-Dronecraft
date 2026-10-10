package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.client.sound.DroneSoundManager;
import com.tenicana.dronecraft.client.sound.CalibratedStreamLifecycle;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class SoundEngineCallbackControlMixin {
    @Inject(method = "refreshCategoryVolume", at = @At("RETURN"))
    private void fpvdrone$stageCategoryControls(SoundSource source, CallbackInfo info) {
        DroneSoundManager.stageCallbackControls();
    }

    @Inject(method = "stopAll", at = @At("HEAD"))
    private void fpvdrone$invalidateControlTasks(CallbackInfo info) {
        CalibratedStreamLifecycle.stopAll();
        DroneSoundManager.invalidateCallbackControls();
    }

    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
    private void fpvdrone$retireCalibratedStream(SoundInstance sound, CallbackInfo info) {
        CalibratedStreamLifecycle.stop(sound);
    }

    @Inject(method = "emergencyShutdown", at = @At("HEAD"))
    private void fpvdrone$retireEmergencyStreams(CallbackInfo info) {
        CalibratedStreamLifecycle.stopAll();
    }
}
