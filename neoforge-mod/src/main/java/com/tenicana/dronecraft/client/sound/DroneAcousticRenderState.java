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
import com.tenicana.dronecraft.acoustics.PreparedPhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.propagation.EmissionTransmission;
import com.tenicana.dronecraft.acoustics.propagation.DopplerShift;
import com.tenicana.dronecraft.entity.DroneEntity;

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
	private AcousticEmissionFrame omnidirectionalEmissionFrame = SILENT;
	private AcousticBands transmissionEnergyGain = UNITY_GAIN;
	private OrderTrackedRotorModel sourceModel =
			new OrderTrackedRotorModel(activeProfile.sourceModel());
	private volatile double dopplerFrequencyRatio = 1.0;
	private volatile double soundSpeedMetersPerSecond =
			AcousticPropagation.speedOfSoundMetersPerSecond(20.0);
	private volatile AudioSnapshot audioSnapshot;
	private DroneCallbackControlCoordinator callbackControls;
	private Object motorDistanceStream;
	private Object propellerDistanceStream;
	private boolean motorDistanceVerified;
	private boolean propellerDistanceVerified;
	private volatile boolean distanceOwnershipFailed;

	synchronized void registerDistanceStream(PhaseContinuousSynthesizer.Layer layer, Object stream) {
		if (layer == PhaseContinuousSynthesizer.Layer.MOTOR) {
			motorDistanceStream = stream;
			motorDistanceVerified = false;
		} else {
			propellerDistanceStream = stream;
			propellerDistanceVerified = false;
		}
	}

	synchronized boolean distanceStreamMayConfigure(PhaseContinuousSynthesizer.Layer layer, Object stream) {
		return !distanceOwnershipFailed && (layer == PhaseContinuousSynthesizer.Layer.MOTOR
				? motorDistanceStream == stream : propellerDistanceStream == stream);
	}

	synchronized boolean configureDistanceStream(PhaseContinuousSynthesizer.Layer layer, Object stream, boolean verified) {
		if (!distanceStreamMayConfigure(layer, stream)) return false;
		if (layer == PhaseContinuousSynthesizer.Layer.MOTOR && motorDistanceStream == stream) {
			motorDistanceVerified = verified;
			if (!verified) distanceOwnershipFailed = true;
		} else if (layer == PhaseContinuousSynthesizer.Layer.PROPELLER && propellerDistanceStream == stream) {
			propellerDistanceVerified = verified;
			if (!verified) distanceOwnershipFailed = true;
		}
		return verified;
	}

	synchronized void closeDistanceStream(PhaseContinuousSynthesizer.Layer layer, Object stream) {
		if (layer == PhaseContinuousSynthesizer.Layer.MOTOR && motorDistanceStream == stream) {
			motorDistanceStream = null;
			motorDistanceVerified = false;
		} else if (layer == PhaseContinuousSynthesizer.Layer.PROPELLER && propellerDistanceStream == stream) {
			propellerDistanceStream = null;
			propellerDistanceVerified = false;
		}
	}

	synchronized boolean distanceStreamsVerified() {
		return !distanceOwnershipFailed && motorDistanceVerified && propellerDistanceVerified;
	}

	synchronized boolean distanceStreamVerified(PhaseContinuousSynthesizer.Layer layer, Object stream) {
		return distanceStreamMayConfigure(layer, stream) && (layer == PhaseContinuousSynthesizer.Layer.MOTOR
				? motorDistanceVerified : propellerDistanceVerified);
	}

	boolean distanceOwnershipFailed() { return distanceOwnershipFailed; }

	synchronized void resetDistanceOwnership() {
		motorDistanceStream = null;
		propellerDistanceStream = null;
		motorDistanceVerified = false;
		propellerDistanceVerified = false;
		distanceOwnershipFailed = false;
	}

	void updateSource(DroneEntity drone, AcousticListenerFrame listener) {
		AcousticSourceFrame nextSource = DroneAcousticSourceMapper.map(drone);
		AcousticSourceProfile nextProfile = AcousticProfileResources.select(drone);
		if (nextProfile != activeProfile) {
			activeProfile = nextProfile;
			sourceModel = new OrderTrackedRotorModel(nextProfile.sourceModel());
		}
		omnidirectionalEmissionFrame = sourceModel.evaluate(
				nextSource,
				SAMPLE_RATE
		);
		sourceFrame = nextSource;
		soundSpeedMetersPerSecond =
				AcousticPropagation.speedOfSoundMetersPerSecond(
						safeTemperature(
								drone.getAmbientTemperatureCelsius()
						)
				);
		updateListener(listener);
	}

	/** Refreshes only listener-dependent math; no world reads or rotor fitting. */
	void updateListener(AcousticListenerFrame listener) {
		AcousticSourceFrame source = sourceFrame;
		if (source == null) {
			return;
		}
		AcousticEmissionFrame sourceEmission = AxisymmetricSourceDirectivity.apply(
				omnidirectionalEmissionFrame,
				source,
				listener,
				activeProfile.directivity()
		);
		DopplerShift.Result doppler = DopplerShift.apply(
				sourceEmission,
				source,
				listener,
				soundSpeedMetersPerSecond
		);
		preDopplerEmissionFrame = sourceEmission;
		rawEmissionFrame = doppler.emission();
		dopplerFrequencyRatio = doppler.frequencyRatio();
		listenerFrame = listener;
		rebuildEmission();
	}

	void resetTransmission() {
		transmissionEnergyGain = UNITY_GAIN;
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

	void useCallbackControls(DroneCallbackControlCoordinator controls) { callbackControls = controls; }

	AudioSnapshot callbackAudioSnapshot(Object stream, PhaseContinuousSynthesizer.Layer layer) {
		DroneCallbackControlCoordinator controls = callbackControls;
		if (controls == null || !(stream instanceof CallbackPcmStream candidate)
				|| !candidate.callbackControlsEnrolled()) return audioSnapshot;
		DroneAudioControlFrame frame = controls.committedFrame();
		AudioSnapshot selected = frame == null ? null : frame.drySnapshot(this, layer, stream);
		// A removed native owner keeps its last complete control until async close.
		return selected != null ? selected : (AudioSnapshot) candidate.previousCallbackControl();
	}

	synchronized Object distanceStream(PhaseContinuousSynthesizer.Layer layer) {
		return layer == PhaseContinuousSynthesizer.Layer.MOTOR ? motorDistanceStream : propellerDistanceStream;
	}

	boolean replacesLegacyRpmVolume() {
		return playbackCalibration().replacesLegacyRpmVolume();
	}

	double motorPlaybackAmplitude() {
		return playbackCalibration().motorPlaybackAmplitude();
	}

	double propellerPlaybackAmplitude() {
		return playbackCalibration().propellerPlaybackAmplitude();
	}

	AcousticSourceProfile.Calibration playbackCalibration() {
		AudioSnapshot snapshot = audioSnapshot;
		return snapshot == null ? activeProfile.calibration() : snapshot.calibration();
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
					soundSpeedMetersPerSecond,
					activeProfile.calibration()
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
			double soundSpeedMetersPerSecond,
			AcousticSourceProfile.Calibration calibration,
			PreparedPhaseContinuousSynthesizer.PreparedEmission preparedMotor,
			PreparedPhaseContinuousSynthesizer.PreparedEmission preparedPropeller
	) {
		AudioSnapshot(AcousticSourceFrame source, AcousticListenerFrame listener,
				AcousticEmissionFrame preDopplerEmission, AcousticEmissionFrame emission,
				double dopplerFrequencyRatio, double soundSpeedMetersPerSecond,
				AcousticSourceProfile.Calibration calibration) {
			this(source, listener, preDopplerEmission, emission, dopplerFrequencyRatio,
					soundSpeedMetersPerSecond, calibration,
					PreparedPhaseContinuousSynthesizer.prepare(emission, PhaseContinuousSynthesizer.Layer.MOTOR, SAMPLE_RATE),
					PreparedPhaseContinuousSynthesizer.prepare(emission, PhaseContinuousSynthesizer.Layer.PROPELLER, SAMPLE_RATE));
		}

		AudioSnapshot {
			java.util.Objects.requireNonNull(calibration, "calibration");
			java.util.Objects.requireNonNull(preparedMotor, "preparedMotor");
			java.util.Objects.requireNonNull(preparedPropeller, "preparedPropeller");
		}
	}

}
