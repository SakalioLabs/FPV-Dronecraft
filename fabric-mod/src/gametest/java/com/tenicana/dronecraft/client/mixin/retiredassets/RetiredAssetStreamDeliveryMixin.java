package com.tenicana.dronecraft.client.mixin.retiredassets;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredDeliveryCapture;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneLoopSoundInstance",remap=false)
abstract class RetiredAssetStreamDeliveryMixin {
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void retiredAsset$constructor(CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.constructed(this);
    }
    @ModifyReturnValue(method="getAudioStream",at=@At("RETURN"),remap=false)
    private CompletableFuture<AudioStream> retiredAsset$defer(CompletableFuture<AudioStream> original,
            SoundBufferLibrary library,Identifier id,boolean repeatInstantly) {
        return ManagerAssetRetiredDeliveryCapture.requested()
                ?ManagerAssetRetiredDeliveryCapture.streamReturned(this,original,repeatInstantly):original;
    }
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void retiredAsset$beforeTick(CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.beforeSoundTick(this);
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void retiredAsset$afterTick(CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.afterSoundTick(this);
    }
}
