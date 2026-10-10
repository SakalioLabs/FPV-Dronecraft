package com.tenicana.dronecraft.client.mixin.wet;

import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.ManagerAssetReleaseCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
abstract class ManagerAssetReleaseChannelMixin {
    @Inject(method="stop",at=@At("HEAD"))
    private void assetRelease$beforeNativeStop(CallbackInfo ci) {
        ManagerAssetReleaseCapture.beforeChannelStop(this);
    }
}
