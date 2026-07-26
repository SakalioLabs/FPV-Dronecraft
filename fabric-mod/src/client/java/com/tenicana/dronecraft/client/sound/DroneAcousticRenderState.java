package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticPropagation;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import com.tenicana.dronecraft.acoustics.AxisymmetricSourceDirectivity;
import com.tenicana.dronecraft.acoustics.DopplerPcmConformance;
import com.tenicana.dronecraft.acoustics.OrderTrackedRotorModel;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.propagation.EmissionTransmission;
import com.tenicana.dronecraft.acoustics.propagation.DopplerShift;
import com.tenicana.dronecraft.entity.DroneEntity;
import net.minecraft.world.entity.Entity;

import java.util.List;

final class DroneAcousticRenderState {
	static final int SAMPLE_RATE = 48_000;
	private static final AcousticEmissionFrame SILENT = new AcousticEmissionFrame(
			List.of(), AcousticBands.SILENT
	);
	private static final AcousticBands UNITY_GAIN = new AcousticBands(1.0, 1.0, 1.0);
	private static final double OCCLUSION_ATTACK = 0.42;
	private static final double OCCLUSION_RELEASE = 0.20;
	private static final double MAX_GAIN_CHANGE_DB_PER_UPDATE = 3.0;
	private static final double MIN_SMOOTHING_ENERGY = 1.0e-9;

	private volatile AcousticSourceFrame sourceFrame;
	private volatile AcousticListenerFrame listenerFrame;
	private volatile AcousticEmissionFrame emissionFrame = SILENT;
	private volatile AcousticEmissionFrame preDopplerEmissionFrame = SILENT;
	private volatile AcousticSourceProfile activeProfile =
			AcousticSourceProfile.researchFallback();
	private AcousticEmissionFrame rawEmissionFrame = SILENT;
	private AcousticBands transmissionEnergyGain = UNITY_GAIN;
	private OrderTrackedRotorModel sourceModel =
			new OrderTrackedRotorModel(activeProfile.sourceModel());
	private volatile double dopplerFrequencyRatio = 1.0;
	private volatile double soundSpeedMetersPerSecond =
			AcousticPropagation.speedOfSoundMetersPerSecond(20.0);
	private volatile AudioSnapshot audioSnapshot;

	void updateSource(DroneEntity drone, Entity listener) {
		AcousticSourceFrame nextSource = DroneAcousticSourceMapper.map(drone);
		AcousticListenerFrame listenerFrame = DroneAcousticSourceMapper.mapListener(listener);
		AcousticSourceProfile nextProfile = AcousticProfileResources.select(drone);
		if (nextProfile != activeProfile) {
			activeProfile = nextProfile;
			sourceModel = new OrderTrackedRotorModel(nextProfile.sourceModel());
		}
		AcousticEmissionFrame sourceEmission = sourceModel.evaluate(
				nextSource,
				SAMPLE_RATE
		);
		sourceEmission = AxisymmetricSourceDirectivity.apply(
				sourceEmission,
				nextSource,
				listenerFrame,
				nextProfile.directivity()
		);
		double nextSoundSpeed =
				AcousticPropagation.speedOfSoundMetersPerSecond(
						safeTemperature(
								drone.getAmbientTemperatureCelsius()
						)
				);
		DopplerShift.Result doppler = DopplerShift.apply(
				sourceEmission,
				nextSource,
				listenerFrame,
				nextSoundSpeed
		);
		preDopplerEmissionFrame = sourceEmission;
		rawEmissionFrame = doppler.emission();
		dopplerFrequencyRatio = doppler.frequencyRatio();
		sourceFrame = nextSource;
		this.listenerFrame = listenerFrame;
		soundSpeedMetersPerSecond = nextSoundSpeed;
		rebuildEmission();
	}

	void applyTransmission(AcousticBands targetEnergyGain) {
		transmissionEnergyGain = new AcousticBands(
				smoothGain(transmissionEnergyGain.low(), targetEnergyGain.low()),
				smoothGain(transmissionEnergyGain.mid(), targetEnergyGain.mid()),
				smoothGain(transmissionEnergyGain.high(), targetEnergyGain.high())
		);
		rebuildEmission();
	}

	void relaxTransmission() {
		applyTransmission(UNITY_GAIN);
	}

	AcousticSourceFrame sourceFrame() {
		return sourceFrame;
	}

	AcousticEmissionFrame emissionFrame() {
		return emissionFrame;
	}

	AcousticEmissionFrame unoccludedEmissionFrame() {
		return rawEmissionFrame;
	}

	AcousticBands transmissionEnergyGain() {
		return transmissionEnergyGain;
	}

	double dopplerFrequencyRatio() {
		return dopplerFrequencyRatio;
	}

	AcousticListenerFrame listenerFrame() {
		return listenerFrame;
	}

	double soundSpeedMetersPerSecond() {
		return soundSpeedMetersPerSecond;
	}

	DopplerPcmConformance.Result measureDopplerPcm(
			PhaseContinuousSynthesizer.Layer layer
	) {
		AudioSnapshot snapshot = audioSnapshot;
		if (snapshot == null) {
			throw new IllegalStateException(
					"acoustic source/listener frames are not ready"
			);
		}
		return DopplerPcmConformance.measure(
				snapshot.preDopplerEmission(),
				snapshot.source(),
				snapshot.listener(),
				snapshot.soundSpeedMetersPerSecond(),
				layer
		);
	}

	AudioSnapshot audioSnapshot() {
		return audioSnapshot;
	}

	boolean replacesLegacyRpmVolume() {
		return activeProfile.calibration().replacesLegacyRpmVolume();
	}

	double motorPlaybackAmplitude() {
		return activeProfile.calibration().motorPlaybackAmplitude();
	}

	double propellerPlaybackAmplitude() {
		return activeProfile.calibration().propellerPlaybackAmplitude();
	}

	private void rebuildEmission() {
		emissionFrame = EmissionTransmission.apply(rawEmissionFrame, transmissionEnergyGain);
		AcousticSourceFrame source = sourceFrame;
		AcousticListenerFrame listener = listenerFrame;
		if (source != null && listener != null) {
			audioSnapshot = new AudioSnapshot(
					source,
					listener,
					preDopplerEmissionFrame,
					emissionFrame,
					dopplerFrequencyRatio,
					soundSpeedMetersPerSecond
			);
		}
	}

	private static double smoothGain(double current, double target) {
		double response = target < current ? OCCLUSION_ATTACK : OCCLUSION_RELEASE;
		double candidate = current + (target - current) * response;
		double reference = Math.max(current, MIN_SMOOTHING_ENERGY);
		double maximumRatio = Math.pow(10.0, MAX_GAIN_CHANGE_DB_PER_UPDATE / 10.0);
		return Math.max(
				reference / maximumRatio,
				Math.min(reference * maximumRatio, candidate)
		);
	}

	private static double safeTemperature(double temperatureCelsius) {
		if (!Double.isFinite(temperatureCelsius)) {
			return 20.0;
		}
		return Math.max(-80.0, Math.min(80.0, temperatureCelsius));
	}

	record AudioSnapshot(
			AcousticSourceFrame source,
			AcousticListenerFrame listener,
			AcousticEmissionFrame preDopplerEmission,
			AcousticEmissionFrame emission,
			double dopplerFrequencyRatio,
			double soundSpeedMetersPerSecond
	) {
	}

}
