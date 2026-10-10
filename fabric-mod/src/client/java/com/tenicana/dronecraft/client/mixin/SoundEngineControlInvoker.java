package com.tenicana.dronecraft.client.mixin;

import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Uses vanilla's complete resource/category/clamp calculation off callback. */
@Mixin(SoundEngine.class)
public interface SoundEngineControlInvoker {
    @Invoker("calculateVolume") float fpvdrone$calculateVolume(SoundInstance sound);
    @Invoker("calculatePitch") float fpvdrone$calculatePitch(SoundInstance sound);
    @Accessor("listener") Listener fpvdrone$getListener();
}
