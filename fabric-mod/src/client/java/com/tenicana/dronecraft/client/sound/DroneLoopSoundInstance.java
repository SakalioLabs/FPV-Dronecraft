package com.tenicana.dronecraft.client.sound;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sound.DroneSoundEvents;
import com.tenicana.dronecraft.sound.DroneSoundPhysics;

final class DroneLoopSoundInstance extends AbstractTickableSoundInstance implements FabricSoundInstance {
	private static final float VOLUME_ATTACK = 0.30f;
	private static final float VOLUME_RELEASE = 0.16f;
	private static final float PITCH_RESPONSE = 0.24f;
	private static final String PROCEDURAL_AUDIO_PROPERTY = "fpvdrone.proceduralAudio";

	private final DroneEntity drone;
	private final Layer layer;
	private final DroneAcousticRenderState acousticState;

	DroneLoopSoundInstance(
			DroneEntity drone,
			Layer layer,
			DroneAcousticRenderState acousticState
	) {
		super(layer.soundEvent(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.drone = drone;
		this.layer = layer;
		this.acousticState = acousticState;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0f;
		this.pitch = proceduralAudioEnabled() ? 1.0f : fallbackPitch();
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.relative = false;
		updatePosition();
	}

	@Override
	public void tick() {
		if (drone.isRemoved() || !drone.isAlive() || !drone.level().isClientSide()) {
			stop();
			return;
		}

		updatePosition();
		float targetVolume = targetVolume();
		float targetPitch = targetPitch();
		if (!Float.isFinite(targetVolume)) {
			targetVolume = 0.0f;
		}
		if (!Float.isFinite(targetPitch)) {
			targetPitch = layer == Layer.MOTOR ? 0.62f : 0.70f;
		}
		if (!Float.isFinite(volume)) {
			volume = 0.0f;
		}
		if (!Float.isFinite(pitch)) {
			pitch = targetPitch;
		}
		float volumeResponse = targetVolume > volume ? VOLUME_ATTACK : VOLUME_RELEASE;
		volume += (targetVolume - volume) * volumeResponse;
		pitch += (targetPitch - pitch) * PITCH_RESPONSE;
		if (targetVolume == 0.0f && volume < 0.0005f) {
			volume = 0.0f;
		}
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public boolean canPlaySound() {
		return !drone.isSilent();
	}

	@Override
	public CompletableFuture<AudioStream> getAudioStream(
			SoundBufferLibrary soundBuffers,
			Identifier identifier,
			boolean repeatInstantly
	) {
		if (!Boolean.parseBoolean(System.getProperty(PROCEDURAL_AUDIO_PROPERTY, "true"))) {
			return soundBuffers.getStream(identifier, repeatInstantly);
		}
		return CompletableFuture.completedFuture(new ProceduralDroneAudioStream(
				acousticState,
				layer.synthesisLayer(),
				31 * drone.getId() + layer.ordinal()
		));
	}

	void end() {
		stop();
	}

	double internalDopplerFrequencyRatio() {
		return acousticState.dopplerFrequencyRatio();
	}

	Layer layer() {
		return layer;
	}

	private float targetVolume() {
		double rpm = drone.getAverageMotorRpm();
		if (!DroneSoundPhysics.isAudible(rpm)) {
			return 0.0f;
		}
		if (acousticState.replacesLegacyRpmVolume()) {
			double gain = layer == Layer.MOTOR
					? acousticState.motorPlaybackAmplitude()
					: acousticState.propellerPlaybackAmplitude();
			return (float) Math.max(0.0, Math.min(4.0, gain));
		}
		return switch (layer) {
			case MOTOR -> DroneSoundPhysics.motorVolume(rpm, drone.getMotorPower(), drone.getRotorCount());
			case PROPELLER -> DroneSoundPhysics.propellerVolume(
					rpm,
					drone.getMotorPower(),
					drone.getRotorAerodynamicLoadFactor(),
					drone.getAirspeedMetersPerSecond(),
					drone.getTurbulenceIntensity(),
					drone.getRotorCount()
			);
		};
	}

	private float targetPitch() {
		if (proceduralAudioEnabled()) {
			return 1.0f;
		}
		double rpm = drone.getAverageMotorRpm();
		return switch (layer) {
			case MOTOR -> DroneSoundPhysics.motorPitch(rpm, drone.getMotorPower());
			case PROPELLER -> DroneSoundPhysics.propellerPitch(
					rpm,
					drone.getAirspeedMetersPerSecond(),
					drone.getTurbulenceIntensity()
			);
		};
	}

	private float fallbackPitch() {
		return layer == Layer.MOTOR ? 0.62f : 0.70f;
	}

	private static boolean proceduralAudioEnabled() {
		return Boolean.parseBoolean(System.getProperty(PROCEDURAL_AUDIO_PROPERTY, "true"));
	}

	private void updatePosition() {
		x = drone.getX();
		y = drone.getY() + drone.getBbHeight() * 0.5;
		z = drone.getZ();
	}

	enum Layer {
		MOTOR(DroneSoundEvents.MOTOR_LOOP, PhaseContinuousSynthesizer.Layer.MOTOR),
		PROPELLER(DroneSoundEvents.PROPELLER_LOOP, PhaseContinuousSynthesizer.Layer.PROPELLER);

		private final SoundEvent soundEvent;
		private final PhaseContinuousSynthesizer.Layer synthesisLayer;

		Layer(SoundEvent soundEvent, PhaseContinuousSynthesizer.Layer synthesisLayer) {
			this.soundEvent = soundEvent;
			this.synthesisLayer = synthesisLayer;
		}

		SoundEvent soundEvent() {
			return soundEvent;
		}

		PhaseContinuousSynthesizer.Layer synthesisLayer() {
			return synthesisLayer;
		}
	}
}
