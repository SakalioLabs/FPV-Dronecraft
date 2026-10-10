package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Library;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tenicana.dronecraft.client.sound.MainContextOutputCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.IntBuffer;

@Mixin(Library.class)
abstract class MainOutputLibraryMixin {
    @WrapOperation(method = "tryOpenDevice", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcOpenDevice(Ljava/lang/CharSequence;)J"))
    private static long mainOutput$open(CharSequence name, Operation<Long> original) {
        return MainContextOutputCapture.requested() ? MainContextOutputCapture.openDevice() : original.call(name);
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"))
    private long mainOutput$context(long device, IntBuffer attrs, Operation<Long> original) {
        if (!MainContextOutputCapture.requested()) return original.call(device, attrs);
        long context = original.call(device, MainContextOutputCapture.attributes(device, attrs));
        MainContextOutputCapture.created(device, context); return context;
    }

    @WrapOperation(method = "cleanup", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcDestroyContext(J)V"))
    private void mainOutput$destroy(long context, Operation<Void> original) {
        original.call(context);
        if (MainContextOutputCapture.requested()) MainContextOutputCapture.destroyed(context);
    }

    @WrapOperation(method = "cleanup", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCloseDevice(J)Z"))
    private boolean mainOutput$close(long device, Operation<Boolean> original) {
        boolean closed = original.call(device);
        if (MainContextOutputCapture.requested()) MainContextOutputCapture.closed(device, closed);
        return closed;
    }
}
