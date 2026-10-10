package com.tenicana.dronecraft.client.mixin.wet;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.sound.ManagerCatalogPublicationCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneLoopSoundInstance",remap=false)
abstract class ManagerCatalogPublicationEnvelopeMixin {
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void catalogPublication$initialized(CallbackInfo ci) {
        ManagerCatalogPublicationCapture.initialized(this);
    }
    @WrapMethod(method="tick",remap=false)
    private void catalogPublication$tick(Operation<Void> original) {
        var row=ManagerCatalogPublicationCapture.beforeSoundTick(this);boolean returnedNormally=false;
        try{original.call();returnedNormally=true;}
        finally{ManagerCatalogPublicationCapture.afterSoundTick(this,row,returnedNormally);}
    }
}
