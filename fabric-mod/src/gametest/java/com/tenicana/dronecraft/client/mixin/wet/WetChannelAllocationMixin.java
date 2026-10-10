package com.tenicana.dronecraft.client.mixin.wet;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.WetChannelLifecycleCapture;
import com.tenicana.dronecraft.client.sound.WetMainOutputCapture;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(Channel.class)
abstract class WetChannelAllocationMixin {
    @Shadow @Final private AtomicBoolean initialized;

    @Inject(method = "create", at = @At("RETURN"))
    private static void fpvdrone$observeAllocation(CallbackInfoReturnable<Channel> callback) {
        Channel channel = callback.getReturnValue();
        if (channel != null && WetMainOutputCapture.requested())
            WetChannelLifecycleCapture.created(channel);
    }

    @WrapMethod(method = "attachBufferStream")
    private void fpvdrone$observeAttach(AudioStream stream, Operation<Void> original) {
        if (!WetMainOutputCapture.requested()) { original.call(stream); return; }
        synchronized (WetChannelLifecycleCapture.class) {
            JsonObject row = WetChannelLifecycleCapture.begin(this, "attach", "attachBufferStream", initialized.get(), stream);
            original.call(stream);
            WetChannelLifecycleCapture.end(row, initialized.get(), stream);
        }
    }

    @WrapMethod(method = "play")
    private void fpvdrone$observePlay(Operation<Void> original) {
        if (!WetMainOutputCapture.requested()) { original.call(); return; }
        synchronized (WetChannelLifecycleCapture.class) {
            JsonObject row = WetChannelLifecycleCapture.begin(this, "play", "play", initialized.get(), null);
            original.call();
            WetChannelLifecycleCapture.end(row, initialized.get(), null);
        }
    }

    @WrapMethod(method = "unpause")
    private void fpvdrone$observeUnpause(Operation<Void> original) {
        if (!WetMainOutputCapture.requested()) { original.call(); return; }
        synchronized (WetChannelLifecycleCapture.class) {
            JsonObject row = WetChannelLifecycleCapture.begin(this, "play", "unpause", initialized.get(), null);
            original.call();
            WetChannelLifecycleCapture.end(row, initialized.get(), null);
        }
    }

    @WrapMethod(method = "destroy")
    private void fpvdrone$observeDestroy(Operation<Void> original) {
        if (!WetMainOutputCapture.requested()) { original.call(); return; }
        synchronized (WetChannelLifecycleCapture.class) {
            JsonObject row = WetChannelLifecycleCapture.begin(this, "destroy", "destroy", initialized.get(), null);
            original.call();
            WetChannelLifecycleCapture.end(row, initialized.get(), null);
        }
    }
}
