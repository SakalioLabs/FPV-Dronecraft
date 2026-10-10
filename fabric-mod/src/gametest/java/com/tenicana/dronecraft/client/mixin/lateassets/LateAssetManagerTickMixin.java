package com.tenicana.dronecraft.client.mixin.lateassets;

import com.tenicana.dronecraft.client.sound.ManagerAssetLateDeliveryCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager",remap=false)
abstract class LateAssetManagerTickMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void lateAsset$managerHead(Minecraft client,CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.beforeManagerTick();
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void lateAsset$managerReturn(Minecraft client,CallbackInfo ci) {
        if(ManagerAssetLateDeliveryCapture.requested())ManagerAssetLateDeliveryCapture.afterManagerTick();
    }
}
