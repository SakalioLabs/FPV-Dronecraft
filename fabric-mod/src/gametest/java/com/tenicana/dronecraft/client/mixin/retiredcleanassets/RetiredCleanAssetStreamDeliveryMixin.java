package com.tenicana.dronecraft.client.mixin.retiredcleanassets;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredCleanDeliveryCapture;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneLoopSoundInstance",remap=false)
abstract class RetiredCleanAssetStreamDeliveryMixin {
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void retiredCleanAsset$constructor(CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.constructed(this);
    }
    @ModifyReturnValue(method="getAudioStream",at=@At("RETURN"),remap=false)
    private CompletableFuture<AudioStream> retiredCleanAsset$defer(CompletableFuture<AudioStream> original,
            SoundBufferLibrary library,Identifier id,boolean repeatInstantly) {
        return ManagerAssetRetiredCleanDeliveryCapture.requested()
                ?ManagerAssetRetiredCleanDeliveryCapture.streamReturned(this,original,repeatInstantly):original;
    }
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void retiredCleanAsset$beforeTick(CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.beforeSoundTick(this);
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void retiredCleanAsset$afterTick(CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.afterSoundTick(this);
    }
}
