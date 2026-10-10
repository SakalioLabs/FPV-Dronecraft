package com.tenicana.dronecraft.client.mixin.wet;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.sound.CalibrationTransitionQueueCapture;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Channel.class)
abstract class CalibrationTransitionQueueMixin {
    @Shadow @Final private int source;
    @Shadow private AudioStream stream;
    @Inject(method = "method_19648(I)V", at = @At("TAIL"))
    private void transition$queued(int buffer, CallbackInfo ci) { CalibrationTransitionQueueCapture.queued(stream, source, buffer); }
}
