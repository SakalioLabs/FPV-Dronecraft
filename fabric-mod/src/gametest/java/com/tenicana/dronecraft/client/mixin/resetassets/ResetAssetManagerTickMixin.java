package com.tenicana.dronecraft.client.mixin.resetassets;

import com.tenicana.dronecraft.client.sound.ManagerAssetResetDeliveryCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager",remap=false)
abstract class ResetAssetManagerTickMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void resetAsset$managerHead(Minecraft client,CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.beforeManagerTick();
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void resetAsset$managerReturn(Minecraft client,CallbackInfo ci) {
        if(ManagerAssetResetDeliveryCapture.requested())ManagerAssetResetDeliveryCapture.afterManagerTick();
    }
}
