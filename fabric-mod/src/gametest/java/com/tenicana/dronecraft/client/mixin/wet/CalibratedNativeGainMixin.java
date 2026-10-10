package com.tenicana.dronecraft.client.mixin.wet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.sound.CalibratedGainCapture;
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(Channel.class)
abstract class CalibratedNativeGainMixin {
    @WrapMethod(method = "setVolume")
    private void calibrated$gain(float volume, Operation<Void> original) {
        var r = CalibratedGainCapture.beforeGain(this, volume); original.call(volume); CalibratedGainCapture.afterGain(this, r);
    }
}
