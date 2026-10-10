package com.tenicana.dronecraft.client.sound.callbackprobe;

import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.WetOracleTrace;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Owner registration precedes the callback attachment mixin's cancellation. */
@Mixin(value = Channel.class, priority = 1200)
abstract class WetOracleChannelAttachMixin {
    @Inject(method = "attachBufferStream", at = @At("HEAD"))
    private void fpvdrone$bindWetOracle(AudioStream stream, CallbackInfo callback) {
        WetOracleTrace.bindManagerStream(stream);
    }
}
