package com.tenicana.dronecraft.client.sound;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;



import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.AcousticProfileKey;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sound.DroneSoundEvents;
import com.tenicana.dronecraft.sound.DroneSoundPhysics;

final class DroneLoopSoundInstance extends AbstractTickableSoundInstance {
	private static final float VOLUME_ATTACK = 0.30f;
	private static final float VOLUME_RELEASE = 0.16f;
	private static final float PITCH_RESPONSE = 0.24f;
	private static final String PROCEDURAL_AUDIO_PROPERTY = "fpvdrone.proceduralAudio";

	private final DroneEntity drone;
	private final Layer layer;
	private final DroneAcousticRenderState acousticState;
	private final CalibratedStreamLifetime calibratedLifetime;
	private final boolean proceduralAudio;
	private final CalibratedAssetResources.Catalog assetCatalog;
	private final AcousticProfileKey assetKey;
	private boolean assetResolved;
	private VerifiedAssetPcm calibratedAsset;
	private ResourceLocation calibratedResource;
	private volatile double assetAmplitude = 1.0;
	private boolean assetOperatingPointValid;
	private float assetPitch = 1.0f;

	DroneLoopSoundInstance(
			DroneEntity drone,
			Layer layer,
			DroneAcousticRenderState acousticState
	) {
		super(layer.soundEvent(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.drone = drone;
		this.layer = layer;
		this.acousticState = acousticState;
		this.calibratedLifetime = new CalibratedStreamLifetime(acousticState);
		// Keep the stream, envelope and pitch on one backend for this lifetime.
		this.proceduralAudio = proceduralAudioEnabled();
		this.assetCatalog = CalibratedAssetResources.snapshot();
		this.assetKey = AcousticProfileResources.keyOf(drone);
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0f;
		this.pitch = proceduralAudio ? 1.0f : fallbackPitch();
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.relative = false;
		updatePosition();
	}

	@Override
	public void tick() {
		if (drone.isRemoved() || !drone.isAlive() || !drone.level().isClientSide()) {
			retireCalibratedStreams();
			stop();
			return;
		}

		updatePosition();
		updateAssetPlayback();
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

	boolean hasUnfinishedEnvelope() {
		return Float.isFinite(volume) && volume > 0.0f;
	}

	@Override
	public boolean canPlaySound() {
		return !drone.isSilent();
	}

	@Override
	public float getVolume() {
		// Asset calibration and its envelope replace event-level source gain.
		return calibratedAsset == null ? super.getVolume() : volume;
	}

	@Override
	public float getPitch() {
		return calibratedAsset == null ? super.getPitch() : pitch;
	}

	@Override
	public WeighedSoundEvents resolve(SoundManager manager) {
		WeighedSoundEvents events = super.resolve(manager);
		if (!proceduralAudio && !assetResolved && events != null) {
			assetResolved = true;
			calibratedResource = sound.getPath();
			calibratedAsset = assetCatalog.select(assetKey, layer.name(), calibratedResource);
			if (calibratedAsset != null) {
				assetAmplitude = calibratedAsset.calibration().referenceAmplitude();
				pitch = 1.0f;
				updateAssetPlayback();
			}
		}
		return events;
	}

	@Override
	public CompletableFuture<AudioStream> getStream(
			SoundBufferLibrary soundBuffers,
			Sound streamSound,
			boolean repeatInstantly
	) {
		ResourceLocation identifier = streamSound.getPath();
		if (!proceduralAudio) {
			if (calibratedAsset != null) {
				if (!identifier.equals(calibratedResource)) {
					return CompletableFuture.failedFuture(new java.io.IOException("calibrated resource identity changed"));
				}
				return CompletableFuture.completedFuture(new CalibratedAssetAudioStream(
						calibratedAsset, repeatInstantly, () -> assetAmplitude,
						acousticState, layer.synthesisLayer(), DroneSoundManager.internalPropagationEnabled(),
						calibratedLifetime));
			}
			return soundBuffers.getStream(identifier, repeatInstantly);
		}
		return CompletableFuture.completedFuture(new ProceduralDroneAudioStream(
				acousticState,
				layer.synthesisLayer(),
				31 * drone.getId() + layer.ordinal(),
				DroneSoundManager.internalPropagationEnabled()
		));
	}

	void end() {
		retireCalibratedStreams();
		stop();
	}

	void retireCalibratedStreams() {
		calibratedLifetime.retire();
	}

	double internalDopplerFrequencyRatio() {
		return acousticState.dopplerFrequencyRatio();
	}

	Layer layer() {
		return layer;
	}

	boolean usesProceduralAudio() {
		return proceduralAudio;
	}

	private float targetVolume() {
		double rpm = drone.getAverageMotorRpm();
		if (!DroneSoundPhysics.isAudible(rpm)) {
			return 0.0f;
		}
		if (!proceduralAudio && calibratedAsset != null) {
			return assetOperatingPointValid ? 1.0f : 0.0f;
		}
		if (proceduralAudio && acousticState.replacesLegacyRpmVolume()) {
			// Minecraft clamps SoundInstance volume to one. Keep the smoothed
			// envelope here; the procedural PCM carries the calibration separately.
			return 1.0f;
		}
		// An order-domain source profile does not calibrate an arbitrary Ogg asset.
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
		if (proceduralAudio) {
			return 1.0f;
		}
		if (calibratedAsset != null) return assetPitch;
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

	private void updateAssetPlayback() {
		if (proceduralAudio || calibratedAsset == null) return;
		assetOperatingPointValid = false;
		var snapshot = acousticState.audioSnapshot();
		double rpm = drone.getAverageMotorRpm();
		if (snapshot == null || !DroneSoundPhysics.isAudible(rpm)
				|| !assetKey.equals(AcousticProfileResources.keyOf(drone))) return;
		var direction = snapshot.listener().positionMeters().subtract(snapshot.source().positionMeters());
		double distance = direction.length();
		if (distance <= 1e-9) return;
		double cosine = Math.max(-1, Math.min(1, snapshot.source().rotorDiskNormal().dot(direction.multiply(1 / distance))));
		var reference = calibratedAsset.calibration();
		if (!reference.contains(rpm, cosine)) return;
		assetAmplitude = reference.amplitude(rpm, cosine);
		assetPitch = (float) (rpm / reference.referenceRpm());
		assetOperatingPointValid = true;
		// Retain the last valid C/pitch on release; E alone fades the queued waveform.
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
		MOTOR(DroneSoundEvents::motorLoop, PhaseContinuousSynthesizer.Layer.MOTOR),
		PROPELLER(DroneSoundEvents::propellerLoop, PhaseContinuousSynthesizer.Layer.PROPELLER);

		private final Supplier<SoundEvent> soundEvent;
		private final PhaseContinuousSynthesizer.Layer synthesisLayer;

		Layer(Supplier<SoundEvent> soundEvent, PhaseContinuousSynthesizer.Layer synthesisLayer) {
			this.soundEvent = soundEvent;
			this.synthesisLayer = synthesisLayer;
		}

		SoundEvent soundEvent() {
			return soundEvent.get();
		}

		PhaseContinuousSynthesizer.Layer synthesisLayer() {
			return synthesisLayer;
		}
	}
}
