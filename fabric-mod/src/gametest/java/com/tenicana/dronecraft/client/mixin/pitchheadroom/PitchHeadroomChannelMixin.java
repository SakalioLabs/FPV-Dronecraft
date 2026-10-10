package com.tenicana.dronecraft.client.mixin.pitchheadroom;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.PitchHeadroomCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
abstract class PitchHeadroomChannelMixin {
    @Unique private final ThreadLocal<JsonObject> fpvdrone$pitchHeadroom = new ThreadLocal<>();
    @Inject(method = "setPitch", at = @At("HEAD"))
    private void fpvdrone$beforePitch(float pitch, CallbackInfo ci) {
        fpvdrone$pitchHeadroom.set(PitchHeadroomCapture.before(this, pitch));
    }
    @Inject(method = "setPitch", at = @At("RETURN"))
    private void fpvdrone$afterPitch(float pitch, CallbackInfo ci) {
        PitchHeadroomCapture.after(fpvdrone$pitchHeadroom.get());
        fpvdrone$pitchHeadroom.remove();
    }
}
