package com.tenicana.dronecraft.client.mixin.retiredassets;

import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredDeliveryCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class RetiredAssetSoundEngineTickMixin {
    @Inject(method="tick(Z)V",at=@At("HEAD"))
    private void retiredAsset$engineHead(boolean paused,CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.beforeEngineTick(paused);
    }
    @Inject(method="tickInGameSound",at=@At("HEAD"))
    private void retiredAsset$gameHead(CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.beforeGameSoundTick();
    }
    @Inject(method="tickInGameSound",at=@At("RETURN"))
    private void retiredAsset$gameReturn(CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.afterGameSoundTick();
    }
    @Inject(method="tick(Z)V",at=@At("RETURN"))
    private void retiredAsset$engineReturn(boolean paused,CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.afterEngineTick();
    }
    @WrapOperation(method="tick(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/ChannelAccess;scheduleTick()V"))
    private void retiredAsset$normalCleanupSubmission(ChannelAccess access,Operation<Void> original) {
        if(!ManagerAssetRetiredDeliveryCapture.requested()){original.call(access);return;}
        ManagerAssetRetiredDeliveryCapture.vanillaScheduleTick("scheduleTick_submit_head");
        original.call(access);
        ManagerAssetRetiredDeliveryCapture.vanillaScheduleTick("scheduleTick_submit_return");
    }
}
