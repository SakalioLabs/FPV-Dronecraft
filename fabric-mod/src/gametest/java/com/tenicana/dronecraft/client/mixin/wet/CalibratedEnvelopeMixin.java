package com.tenicana.dronecraft.client.mixin.wet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tenicana.dronecraft.client.sound.CalibratedGainCapture;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(targets = "com.tenicana.dronecraft.client.sound.DroneLoopSoundInstance", remap = false)
abstract class CalibratedEnvelopeMixin {
    @WrapMethod(method = "tick", remap = false)
    private void calibrated$tick(Operation<Void> original) {
        var r = CalibratedGainCapture.beforeTick(this); original.call(); CalibratedGainCapture.afterTick(this, r);
    }
}
