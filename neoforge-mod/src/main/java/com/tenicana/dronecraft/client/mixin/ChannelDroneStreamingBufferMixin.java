package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.DroneStreamingBufferSize;
import net.minecraft.client.sounds.AudioStream;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
abstract class ChannelDroneStreamingBufferMixin {
    @Shadow private int streamingBufferSize;

    @Inject(method = "attachBufferStream", at = @At(value = "FIELD",
            target = "Lcom/mojang/blaze3d/audio/Channel;streamingBufferSize:I",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void fpvdrone$shortenDroneQueue(AudioStream stream, CallbackInfo callback) {
        streamingBufferSize = DroneStreamingBufferSize.bytesPerBuffer(stream, streamingBufferSize);
    }
}
