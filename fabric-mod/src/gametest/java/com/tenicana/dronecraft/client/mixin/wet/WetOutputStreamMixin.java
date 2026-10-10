package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.MainContextOutputCapture;
import com.tenicana.dronecraft.client.sound.WetMainOutputCapture;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;

@Mixin(targets = "com.tenicana.dronecraft.client.sound.ListenerReverbAudioStream", remap = false)
abstract class WetOutputStreamMixin {
    @Shadow(remap = false) private float[] mixedInput;
    @WrapMethod(method = "read(I)Ljava/nio/ByteBuffer;")
    private ByteBuffer fpvdrone$observeRead(int requestedBytes, Operation<ByteBuffer> original) {
        Object stream = this;
        if (!WetMainOutputCapture.requested()) return original.call(requestedBytes);
        WetMainOutputCapture.bind(stream);
        try {
            ByteBuffer result = original.call(requestedBytes);
            if (result != null && result.hasRemaining()) {
                MainContextOutputCapture.record(stream, result);
                WetMainOutputCapture.mixedRead(stream, mixedInput, result.remaining() / Short.BYTES);
            }
            return result;
        } finally {
            WetMainOutputCapture.clearRead();
        }
    }
}
