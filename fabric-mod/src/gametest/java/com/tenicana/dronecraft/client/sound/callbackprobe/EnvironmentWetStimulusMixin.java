package com.tenicana.dronecraft.client.sound.callbackprobe;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.client.sound.EnvironmentWetStimulus;
import com.tenicana.dronecraft.client.sound.ManagerWetPublicationCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Changes only the test helper's off-callback environment publication result. */
@Mixin(value = ManagerWetPublicationCapture.class, remap = false)
abstract class EnvironmentWetStimulusMixin {
    @ModifyReturnValue(method = "environmentControl(Lcom/tenicana/dronecraft/acoustics/reverb/FdnEnvironmentMapper$Controls;)Lcom/tenicana/dronecraft/acoustics/reverb/FdnEnvironmentMapper$Controls;",
            at = @At("RETURN"), remap = false)
    private static FdnEnvironmentMapper.Controls fpvdrone$dynamicEnvironment(FdnEnvironmentMapper.Controls actual) {
        return EnvironmentWetStimulus.replace(actual);
    }
}
