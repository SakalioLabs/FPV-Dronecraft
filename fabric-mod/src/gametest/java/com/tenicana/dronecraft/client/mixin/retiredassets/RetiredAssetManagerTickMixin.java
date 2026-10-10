package com.tenicana.dronecraft.client.mixin.retiredassets;

import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredDeliveryCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager",remap=false)
abstract class RetiredAssetManagerTickMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void retiredAsset$managerHead(Minecraft client,CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.beforeManagerTick();
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void retiredAsset$managerReturn(Minecraft client,CallbackInfo ci) {
        if(ManagerAssetRetiredDeliveryCapture.requested())ManagerAssetRetiredDeliveryCapture.afterManagerTick();
    }
}
