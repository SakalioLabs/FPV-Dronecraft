package com.tenicana.dronecraft.client.sound;

import java.util.concurrent.CompletableFuture;

import com.tenicana.dronecraft.sound.DroneSoundEvents;


import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

final class AudioLabMarkerSoundInstance
		extends AbstractTickableSoundInstance {
	private final double frequencyHz;

	AudioLabMarkerSoundInstance(double frequencyHz) {
		super(
				DroneSoundEvents.audioLabMarker(),
				SoundSource.NEUTRAL,
				SoundInstance.createUnseededRandom()
		);
		this.frequencyHz = frequencyHz;
		looping = false;
		delay = 0;
		volume = 1.0F;
		pitch = 1.0F;
		attenuation = SoundInstance.Attenuation.NONE;
		relative = true;
		x = 0.0;
		y = 0.0;
		z = 0.0;
	}

	@Override
	public void tick() {
		// Finite stream completion stops the native channel.
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public CompletableFuture<AudioStream> getStream(
			SoundBufferLibrary soundBuffers,
			Sound streamSound,
			boolean repeatInstantly
	) {
		ResourceLocation identifier = streamSound.getPath();
		return CompletableFuture.completedFuture(
				new AudioLabMarkerAudioStream(frequencyHz)
		);
	}
}
