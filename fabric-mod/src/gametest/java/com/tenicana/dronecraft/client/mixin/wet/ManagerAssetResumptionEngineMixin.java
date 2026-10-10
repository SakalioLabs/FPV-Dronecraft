package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.ManagerAssetResumptionCapture;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation only: never drives, cancels, or replaces a SoundEngine tick. */
@Mixin(SoundEngine.class)
abstract class ManagerAssetResumptionEngineMixin {
    @Inject(method="tick(Z)V",at=@At("HEAD"))
    private void assetResumption$beforeTick(boolean paused,CallbackInfo ci) {
        ManagerAssetResumptionCapture.beforeEngineTick(paused);
    }
    @Inject(method="tick(Z)V",at=@At("RETURN"))
    private void assetResumption$afterTick(boolean paused,CallbackInfo ci) {
        ManagerAssetResumptionCapture.afterEngineTick();
    }
}
