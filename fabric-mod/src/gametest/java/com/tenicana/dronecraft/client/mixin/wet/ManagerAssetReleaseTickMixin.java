package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.ManagerAssetReleaseCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager",remap=false)
abstract class ManagerAssetReleaseTickMixin {
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void assetRelease$afterTick(Minecraft client,CallbackInfo ci) {
        ManagerAssetReleaseCapture.afterManagerTick(client);
    }
}
