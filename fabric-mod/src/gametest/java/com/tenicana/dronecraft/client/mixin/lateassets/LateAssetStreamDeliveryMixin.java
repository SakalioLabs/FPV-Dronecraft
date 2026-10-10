package com.tenicana.dronecraft.client.mixin.lateassets;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tenicana.dronecraft.client.sound.ManagerAssetLateDeliveryCapture;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneLoopSoundInstance",remap=false)
abstract class LateAssetStreamDeliveryMixin {
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void lateAsset$constructor(CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.constructed(this);
    }
    @ModifyReturnValue(method="getAudioStream",at=@At("RETURN"),remap=false)
    private CompletableFuture<AudioStream> lateAsset$defer(CompletableFuture<AudioStream> original,
            SoundBufferLibrary library,Identifier id,boolean repeatInstantly) {
        return ManagerAssetLateDeliveryCapture.requested()
                ?ManagerAssetLateDeliveryCapture.streamReturned(this,original,repeatInstantly):original;
    }
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void lateAsset$beforeTick(CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.beforeSoundTick(this);
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void lateAsset$afterTick(CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.afterSoundTick(this);
    }
}
