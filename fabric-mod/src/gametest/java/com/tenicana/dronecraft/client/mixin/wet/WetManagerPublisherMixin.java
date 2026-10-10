package com.tenicana.dronecraft.client.mixin.wet;

import com.tenicana.dronecraft.client.sound.MainContextOutputCapture;
import com.tenicana.dronecraft.client.sound.WetMainOutputCapture;

import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(targets = "com.tenicana.dronecraft.client.sound.DroneSoundManager$ListenerReverbController", remap = false)
abstract class WetManagerPublisherMixin {

    @Inject(method = "publishSources(Lcom/tenicana/dronecraft/acoustics/AcousticListenerFrame;Ljava/util/Map;)V", at = @At("TAIL"))
    private void fpvdrone$published(AcousticListenerFrame listener, Map<Integer, ?> activeSounds, CallbackInfo ci) {
        WetMainOutputCapture.publish(listener, activeSounds, this);
    }
}
