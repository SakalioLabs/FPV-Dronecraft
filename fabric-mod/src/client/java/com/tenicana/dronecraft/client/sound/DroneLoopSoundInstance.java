package com.tenicana.dronecraft.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sound.DroneSoundEvents;
import com.tenicana.dronecraft.sound.DroneSoundPhysics;

final class DroneLoopSoundInstance extends AbstractTickableSoundInstance {
	private static final float VOLUME_ATTACK = 0.30f;
	private static final float VOLUME_RELEASE = 0.16f;
	private static final float PITCH_RESPONSE = 0.24f;

	private final DroneEntity drone;
	private final Layer layer;

	DroneLoopSoundInstance(DroneEntity drone, Layer layer) {
		super(layer.soundEvent(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.drone = drone;
		this.layer = layer;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0f;
		this.pitch = layer == Layer.MOTOR ? 0.62f : 0.70f;
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

	void end() {
		stop();
	}

	private float targetVolume() {
		double rpm = drone.getAverageMotorRpm();
		if (!DroneSoundPhysics.isAudible(rpm)) {
			return 0.0f;
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

	private void updatePosition() {
		x = drone.getX();
		y = drone.getY() + drone.getBbHeight() * 0.5;
		z = drone.getZ();
	}

	enum Layer {
		MOTOR(DroneSoundEvents.MOTOR_LOOP),
		PROPELLER(DroneSoundEvents.PROPELLER_LOOP);

		private final SoundEvent soundEvent;

		Layer(SoundEvent soundEvent) {
			this.soundEvent = soundEvent;
		}

		SoundEvent soundEvent() {
			return soundEvent;
		}
	}
}
