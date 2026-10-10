package com.tenicana.dronecraft.client.mixin.lateassets;

import com.mojang.blaze3d.audio.Library;
import com.tenicana.dronecraft.client.sound.ManagerAssetLateDeliveryCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Library.class)
abstract class LateAssetLibraryMixin {
    @Inject(method="init",at=@At("RETURN"))
    private void lateAsset$init(CallbackInfo ci) {
        ManagerAssetLateDeliveryCapture.libraryEvent("library_init_return",this);
    }
    @Inject(method="cleanup",at=@At("HEAD"))
    private void lateAsset$cleanupHead(CallbackInfo ci) {
        ManagerAssetLateDeliveryCapture.libraryEvent("library_cleanup_head",this);
    }
    @Inject(method="cleanup",at=@At("RETURN"))
    private void lateAsset$cleanupReturn(CallbackInfo ci) {
        ManagerAssetLateDeliveryCapture.libraryEvent("library_cleanup_return",this);
    }
}
