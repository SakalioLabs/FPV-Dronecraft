package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.ManagerAssetResumptionCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager",remap=false)
abstract class ManagerAssetResumptionTickMixin {
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void assetResumption$afterTick(Minecraft client,CallbackInfo ci) {
        ManagerAssetResumptionCapture.afterManagerTick(client);
    }
}
