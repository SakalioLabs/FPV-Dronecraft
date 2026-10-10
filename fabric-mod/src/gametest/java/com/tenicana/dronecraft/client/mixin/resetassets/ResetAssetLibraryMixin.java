package com.tenicana.dronecraft.client.mixin.resetassets;

import com.mojang.blaze3d.audio.Library;
import com.tenicana.dronecraft.client.sound.ManagerAssetResetDeliveryCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Library.class)
abstract class ResetAssetLibraryMixin {
    @Inject(method="init",at=@At("RETURN"))
    private void resetAsset$init(CallbackInfo ci) {
        ManagerAssetResetDeliveryCapture.libraryEvent("library_init_return",this);
    }
    @Inject(method="cleanup",at=@At("HEAD"))
    private void resetAsset$cleanupHead(CallbackInfo ci) {
        ManagerAssetResetDeliveryCapture.libraryEvent("library_cleanup_head",this);
    }
    @Inject(method="cleanup",at=@At("RETURN"))
    private void resetAsset$cleanupReturn(CallbackInfo ci) {
        ManagerAssetResetDeliveryCapture.libraryEvent("library_cleanup_return",this);
    }
}
