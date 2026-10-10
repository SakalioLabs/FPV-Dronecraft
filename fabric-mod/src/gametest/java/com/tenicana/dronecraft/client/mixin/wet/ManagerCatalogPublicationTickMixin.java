package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.ManagerCatalogPublicationCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager",remap=false)
abstract class ManagerCatalogPublicationTickMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void catalogPublication$beforeTick(Minecraft client,CallbackInfo ci) {
        ManagerCatalogPublicationCapture.beforeManagerTick(client);
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void catalogPublication$afterTick(Minecraft client,CallbackInfo ci) {
        ManagerCatalogPublicationCapture.afterManagerTick(client);
    }
}
