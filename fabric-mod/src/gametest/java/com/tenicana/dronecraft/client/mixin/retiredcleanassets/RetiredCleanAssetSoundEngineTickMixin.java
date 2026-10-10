package com.tenicana.dronecraft.client.mixin.retiredcleanassets;

import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredCleanDeliveryCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class RetiredCleanAssetSoundEngineTickMixin {
    @Inject(method="tick(Z)V",at=@At("HEAD"))
    private void retiredCleanAsset$engineHead(boolean paused,CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.beforeEngineTick(paused);
    }
    @Inject(method="tickInGameSound",at=@At("HEAD"))
    private void retiredCleanAsset$gameHead(CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.beforeGameSoundTick();
    }
    @Inject(method="tickInGameSound",at=@At("RETURN"))
    private void retiredCleanAsset$gameReturn(CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.afterGameSoundTick();
    }
    @Inject(method="tick(Z)V",at=@At("RETURN"))
    private void retiredCleanAsset$engineReturn(boolean paused,CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.afterEngineTick();
    }
    @WrapOperation(method="tick(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/ChannelAccess;scheduleTick()V"))
    private void retiredCleanAsset$normalCleanupSubmission(ChannelAccess access,Operation<Void> original) {
        if(!ManagerAssetRetiredCleanDeliveryCapture.requested()){original.call(access);return;}
        ManagerAssetRetiredCleanDeliveryCapture.vanillaScheduleTick("scheduleTick_submit_head");
        original.call(access);
        ManagerAssetRetiredCleanDeliveryCapture.vanillaScheduleTick("scheduleTick_submit_return");
    }
}
