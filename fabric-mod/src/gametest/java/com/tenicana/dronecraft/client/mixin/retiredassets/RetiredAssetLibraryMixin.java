package com.tenicana.dronecraft.client.mixin.retiredassets;

import com.mojang.blaze3d.audio.Library;
import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredDeliveryCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Library.class)
abstract class RetiredAssetLibraryMixin {
    @Inject(method="init",at=@At("RETURN"))
    private void retiredAsset$init(CallbackInfo ci) {
        ManagerAssetRetiredDeliveryCapture.libraryEvent("library_init_return",this);
    }
    @Inject(method="cleanup",at=@At("HEAD"))
    private void retiredAsset$cleanupHead(CallbackInfo ci) {
        ManagerAssetRetiredDeliveryCapture.libraryEvent("library_cleanup_head",this);
    }
    @Inject(method="cleanup",at=@At("RETURN"))
    private void retiredAsset$cleanupReturn(CallbackInfo ci) {
        ManagerAssetRetiredDeliveryCapture.libraryEvent("library_cleanup_return",this);
    }
}
