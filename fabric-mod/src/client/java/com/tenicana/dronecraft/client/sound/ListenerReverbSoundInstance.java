package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.sound.DroneSoundEvents;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;

final class ListenerReverbSoundInstance
		extends AbstractTickableSoundInstance
		implements FabricSoundInstance {
	private final ListenerReverbState state;

	ListenerReverbSoundInstance(ListenerReverbState state) {
		super(
				DroneSoundEvents.LISTENER_REVERB_BUS,
				SoundSource.NEUTRAL,
				SoundInstance.createUnseededRandom()
		);
		this.state = state;
		looping = true;
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
		// Listener-relative bus; state is supplied atomically by the manager.
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
				new ListenerReverbAudioStream(state)
		);
	}

	void end() {
		stop();
	}
}
