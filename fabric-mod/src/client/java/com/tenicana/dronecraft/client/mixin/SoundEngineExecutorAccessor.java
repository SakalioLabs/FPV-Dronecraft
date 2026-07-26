package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;

@Mixin(SoundEngine.class)
public interface SoundEngineExecutorAccessor {
	@Accessor("executor")
	SoundEngineExecutor fpvdrone$getExecutor();
}
