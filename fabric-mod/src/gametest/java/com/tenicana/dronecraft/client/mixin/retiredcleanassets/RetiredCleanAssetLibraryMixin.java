package com.tenicana.dronecraft.client.mixin.retiredcleanassets;

import com.mojang.blaze3d.audio.Library;
import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredCleanDeliveryCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Library.class)
abstract class RetiredCleanAssetLibraryMixin {
    @Inject(method="init",at=@At("RETURN"))
    private void retiredCleanAsset$init(CallbackInfo ci) {
        ManagerAssetRetiredCleanDeliveryCapture.libraryEvent("library_init_return",this);
    }
    @Inject(method="cleanup",at=@At("HEAD"))
    private void retiredCleanAsset$cleanupHead(CallbackInfo ci) {
        ManagerAssetRetiredCleanDeliveryCapture.libraryEvent("library_cleanup_head",this);
    }
    @Inject(method="cleanup",at=@At("RETURN"))
    private void retiredCleanAsset$cleanupReturn(CallbackInfo ci) {
        ManagerAssetRetiredCleanDeliveryCapture.libraryEvent("library_cleanup_return",this);
    }
}
