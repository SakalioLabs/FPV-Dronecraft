package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.ManagerCatalogPublicationCapture;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class ManagerCatalogPublicationEngineMixin {
    @Inject(method="tick(Z)V",at=@At("HEAD"))
    private void catalogPublication$beforeTick(boolean paused,CallbackInfo ci) {
        ManagerCatalogPublicationCapture.beforeEngineTick(paused);
    }
    @Inject(method="tick(Z)V",at=@At("RETURN"))
    private void catalogPublication$afterTick(boolean paused,CallbackInfo ci) {
        ManagerCatalogPublicationCapture.afterEngineTick();
    }
}
