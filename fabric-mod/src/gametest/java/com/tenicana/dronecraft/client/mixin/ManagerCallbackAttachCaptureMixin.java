package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.CallbackStreamCapture;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preallocate observation storage on the owner, before callback attachment. */
@Mixin(value = Channel.class, priority = 1100)
abstract class ManagerCallbackAttachCaptureMixin {
    @Inject(method = "attachBufferStream", at = @At("HEAD"))
    private void fpvdrone$registerManagerCallback(AudioStream stream, CallbackInfo callback) {
        CallbackStreamCapture.registerManagerStream(stream);
    }
}
