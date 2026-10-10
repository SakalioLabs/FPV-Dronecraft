package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.ManagerAssetResumptionCapture;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager$DroneSoundSet",remap=false)
abstract class ManagerAssetResumptionStopMixin {
    @Inject(method="stop",at=@At("HEAD"),remap=false)
    private void assetResumption$beforeStop(SoundManager manager,CallbackInfo ci) {
        ManagerAssetResumptionCapture.beforeManagerStop(this);
    }
}
