package com.tenicana.dronecraft.client.mixin.wet;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.sound.ManagerAssetResumptionCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneLoopSoundInstance",remap=false)
abstract class ManagerAssetResumptionEnvelopeMixin {
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void assetResumption$initialized(CallbackInfo ci) {
        ManagerAssetResumptionCapture.initialized(this);
    }

    @WrapMethod(method="tick",remap=false)
    private void assetResumption$tick(Operation<Void> original) {
        var row=ManagerAssetResumptionCapture.beforeSoundTick(this);boolean returnedNormally=false;
        try {original.call();returnedNormally=true;}
        finally {ManagerAssetResumptionCapture.afterSoundTick(this,row,returnedNormally);}
    }
}
