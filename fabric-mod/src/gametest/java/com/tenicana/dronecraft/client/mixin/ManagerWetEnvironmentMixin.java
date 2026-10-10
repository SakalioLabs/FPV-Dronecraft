package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.client.sound.ManagerWetPublicationCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A declared generated environment for this manager experiment only. */
@Mixin(targets = "com.tenicana.dronecraft.client.sound.ListenerReverbState", remap = false)
abstract class ManagerWetEnvironmentMixin {
    @ModifyVariable(method = "publishEnvironment(Lcom/tenicana/dronecraft/acoustics/reverb/FdnEnvironmentMapper$Controls;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private FdnEnvironmentMapper.Controls fpvdrone$generatedEnvironment(FdnEnvironmentMapper.Controls actual) {
        return ManagerWetPublicationCapture.environmentControl(actual);
    }
}
