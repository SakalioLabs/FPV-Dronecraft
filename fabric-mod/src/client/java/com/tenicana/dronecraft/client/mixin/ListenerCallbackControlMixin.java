package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import com.tenicana.dronecraft.client.sound.DroneSoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Listener.class)
abstract class ListenerCallbackControlMixin {
    @Inject(method = "setTransform", at = @At("HEAD"), cancellable = true)
    private void fpvdrone$keepCommittedListener(ListenerTransform transform, CallbackInfo info) {
        if (DroneSoundManager.callbackControlsSuppressListenerMutation()) info.cancel();
    }
}
