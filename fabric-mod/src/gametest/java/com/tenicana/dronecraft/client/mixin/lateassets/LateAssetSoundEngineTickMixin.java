package com.tenicana.dronecraft.client.mixin.lateassets;

import com.tenicana.dronecraft.client.sound.ManagerAssetLateDeliveryCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class LateAssetSoundEngineTickMixin {
    @Inject(method="tick(Z)V",at=@At("HEAD"))
    private void lateAsset$engineHead(boolean paused,CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.beforeEngineTick(paused);
    }
    @Inject(method="tickInGameSound",at=@At("HEAD"))
    private void lateAsset$gameHead(CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.beforeGameSoundTick();
    }
    @Inject(method="tickInGameSound",at=@At("RETURN"))
    private void lateAsset$gameReturn(CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.afterGameSoundTick();
    }
    @Inject(method="tick(Z)V",at=@At("RETURN"))
    private void lateAsset$engineReturn(boolean paused,CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.afterEngineTick();
    }
    @WrapOperation(method="tick(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/ChannelAccess;scheduleTick()V"))
    private void lateAsset$normalCleanupSubmission(ChannelAccess access,Operation<Void> original) {
        if(!ManagerAssetLateDeliveryCapture.requested()){original.call(access);return;}
        ManagerAssetLateDeliveryCapture.vanillaScheduleTick("scheduleTick_submit_head");
        original.call(access);
        ManagerAssetLateDeliveryCapture.vanillaScheduleTick("scheduleTick_submit_return");
    }
}
