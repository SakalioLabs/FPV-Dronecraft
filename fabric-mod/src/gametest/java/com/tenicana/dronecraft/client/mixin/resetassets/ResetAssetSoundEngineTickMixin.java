package com.tenicana.dronecraft.client.mixin.resetassets;

import com.tenicana.dronecraft.client.sound.ManagerAssetResetDeliveryCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class ResetAssetSoundEngineTickMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="stopAll")
    private void resetAsset$stopAll(Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call();return;}
        ManagerAssetResetDeliveryCapture.resetMethod("stopAll_head");original.call();ManagerAssetResetDeliveryCapture.resetMethod("stopAll_return");
    }
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="reload")
    private void resetAsset$reload(Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call();return;}
        ManagerAssetResetDeliveryCapture.resetMethod("engine_reload_head");original.call();ManagerAssetResetDeliveryCapture.resetMethod("engine_reload_return");
    }
    @Inject(method="tick(Z)V",at=@At("HEAD"))
    private void resetAsset$engineHead(boolean paused,CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.beforeEngineTick(paused);
    }
    @Inject(method="tickInGameSound",at=@At("HEAD"))
    private void resetAsset$gameHead(CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.beforeGameSoundTick();
    }
    @Inject(method="tickInGameSound",at=@At("RETURN"))
    private void resetAsset$gameReturn(CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.afterGameSoundTick();
    }
    @Inject(method="tick(Z)V",at=@At("RETURN"))
    private void resetAsset$engineReturn(boolean paused,CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.afterEngineTick();
    }
    @WrapOperation(method="tick(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/ChannelAccess;scheduleTick()V"))
    private void resetAsset$normalCleanupSubmission(ChannelAccess access,Operation<Void> original) {
        if(!ManagerAssetResetDeliveryCapture.requested()){original.call(access);return;}
        ManagerAssetResetDeliveryCapture.vanillaScheduleTick("scheduleTick_submit_head");
        original.call(access);
        ManagerAssetResetDeliveryCapture.vanillaScheduleTick("scheduleTick_submit_return");
    }
}
