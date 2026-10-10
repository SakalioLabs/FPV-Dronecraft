package com.tenicana.dronecraft.client.mixin;

import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.client.sound.ManagerWetPublicationCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(targets="com.tenicana.dronecraft.client.sound.DroneSoundManager$ListenerReverbController",remap=false)
abstract class ManagerWetPublicationMixin {
    @Inject(method="publishSources(Lcom/tenicana/dronecraft/acoustics/AcousticListenerFrame;Ljava/util/Map;)V",at=@At("RETURN"))
    private void fpvdrone$associateSourcePlan(AcousticListenerFrame listener,Map<Integer,?> sounds,CallbackInfo callback){ManagerWetPublicationCapture.record(this,listener,sounds);ManagerWetPublicationCapture.applyEnvironmentStimulus(this);}
}
