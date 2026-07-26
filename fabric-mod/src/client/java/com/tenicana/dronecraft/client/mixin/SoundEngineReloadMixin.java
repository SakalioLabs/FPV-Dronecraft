package com.tenicana.dronecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tenicana.dronecraft.client.sound.DroneSoundManager;

import net.minecraft.client.sounds.SoundEngine;

@Mixin(SoundEngine.class)
public abstract class SoundEngineReloadMixin {
	@Inject(method = "reload", at = @At("HEAD"))
	private void fpvdrone$recordSoundEngineReload(CallbackInfo callback) {
		DroneSoundManager.onSoundEngineReload();
	}
}
