package com.tenicana.dronecraft.client.mixin.resetassets;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tenicana.dronecraft.client.sound.ManagerAssetResetDeliveryCapture;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneLoopSoundInstance",remap=false)
abstract class ResetAssetStreamDeliveryMixin {
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void resetAsset$constructor(CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.constructed(this);
    }
    @ModifyReturnValue(method="getAudioStream",at=@At("RETURN"),remap=false)
    private CompletableFuture<AudioStream> resetAsset$defer(CompletableFuture<AudioStream> original,
            SoundBufferLibrary library,Identifier id,boolean repeatInstantly) {
        return ManagerAssetResetDeliveryCapture.requested()
                ?ManagerAssetResetDeliveryCapture.streamReturned(this,original,repeatInstantly):original;
    }
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void resetAsset$beforeTick(CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.beforeSoundTick(this);
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void resetAsset$afterTick(CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.afterSoundTick(this);
    }
}
