package com.tenicana.dronecraft.client.mixin.retiredcleanassets;

import com.tenicana.dronecraft.client.sound.ManagerAssetRetiredCleanDeliveryCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager",remap=false)
abstract class RetiredCleanAssetManagerTickMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void retiredCleanAsset$managerHead(Minecraft client,CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.beforeManagerTick();
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void retiredCleanAsset$managerReturn(Minecraft client,CallbackInfo ci) {
        if(ManagerAssetRetiredCleanDeliveryCapture.requested())ManagerAssetRetiredCleanDeliveryCapture.afterManagerTick();
    }
}
