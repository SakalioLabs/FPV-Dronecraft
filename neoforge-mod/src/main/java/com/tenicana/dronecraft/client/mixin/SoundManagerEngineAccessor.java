package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;

@Mixin(SoundManager.class)
public interface SoundManagerEngineAccessor {
	@Accessor("soundEngine")
	SoundEngine fpvdrone$getSoundEngine();
}
