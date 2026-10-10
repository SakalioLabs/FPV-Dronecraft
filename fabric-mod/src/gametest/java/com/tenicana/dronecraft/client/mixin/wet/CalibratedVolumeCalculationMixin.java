package com.tenicana.dronecraft.client.mixin.wet;
import com.tenicana.dronecraft.client.sound.CalibratedGainCapture;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SoundEngine.class)
abstract class CalibratedVolumeCalculationMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
    private void calibrated$calculation(SoundInstance sound, CallbackInfoReturnable<Float> ci) {
        CalibratedGainCapture.calculate(this, sound, ci.getReturnValue());
    }
}
