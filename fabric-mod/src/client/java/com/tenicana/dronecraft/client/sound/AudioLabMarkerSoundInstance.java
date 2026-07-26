package com.tenicana.dronecraft.client.sound;

import java.util.concurrent.CompletableFuture;

import com.tenicana.dronecraft.sound.DroneSoundEvents;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

final class AudioLabMarkerSoundInstance
		extends AbstractTickableSoundInstance
		implements FabricSoundInstance {
	private final double frequencyHz;

	AudioLabMarkerSoundInstance(double frequencyHz) {
		super(
				DroneSoundEvents.AUDIO_LAB_MARKER,
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
	public CompletableFuture<AudioStream> getAudioStream(
			SoundBufferLibrary soundBuffers,
			Identifier identifier,
			boolean repeatInstantly
	) {
		return CompletableFuture.completedFuture(
				new AudioLabMarkerAudioStream(frequencyHz)
		);
	}
}
