package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.DopplerPcmConformance;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;

import java.util.Objects;

/**
 * Binds synchronized Minecraft flight kinematics to a production-synthesizer
 * PCM16 frequency measurement.
 */
final class DopplerPcmRuntimeProbe {
	private static final double RATIO_TOLERANCE = 1.0e-12;

	private DopplerPcmRuntimeProbe() {
	}

	static Result capture(DroneAcousticRenderState state) {
		Objects.requireNonNull(state, "state");
		AcousticSourceFrame source = state.sourceFrame();
		AcousticListenerFrame listener = state.listenerFrame();
		if (source == null || listener == null) {
			throw new IllegalStateException(
					"acoustic render state is not ready"
			);
		}
		DopplerPcmConformance.Result motor = state.measureDopplerPcm(
				PhaseContinuousSynthesizer.Layer.MOTOR
		);
		DopplerPcmConformance.Result propeller = state.measureDopplerPcm(
				PhaseContinuousSynthesizer.Layer.PROPELLER
		);
		double renderRatio = state.dopplerFrequencyRatio();
		if (Math.abs(motor.dopplerFrequencyRatio() - renderRatio)
					> RATIO_TOLERANCE
				|| Math.abs(
						propeller.dopplerFrequencyRatio() - renderRatio
				) > RATIO_TOLERANCE) {
			throw new IllegalStateException(
					"PCM conformance ratio diverged from render state"
			);
		}
		return new Result(
				source.entityId(),
				source.simulationTimeNanos(),
				source.positionMeters(),
				source.velocityMetersPerSecond(),
				listener.positionMeters(),
				listener.velocityMetersPerSecond(),
				state.soundSpeedMetersPerSecond(),
				renderRatio,
				motor,
				propeller
		);
	}

	record Result(
			long entityId,
			long simulationTimeNanos,
			AcousticVector sourcePositionMeters,
			AcousticVector sourceVelocityMetersPerSecond,
			AcousticVector listenerPositionMeters,
			AcousticVector listenerVelocityMetersPerSecond,
			double soundSpeedMetersPerSecond,
			double renderStateDopplerRatio,
			DopplerPcmConformance.Result motor,
			DopplerPcmConformance.Result propeller
	) {
		Result {
			Objects.requireNonNull(sourcePositionMeters, "sourcePositionMeters");
			Objects.requireNonNull(
					sourceVelocityMetersPerSecond,
					"sourceVelocityMetersPerSecond"
			);
			Objects.requireNonNull(
					listenerPositionMeters,
					"listenerPositionMeters"
			);
			Objects.requireNonNull(
					listenerVelocityMetersPerSecond,
					"listenerVelocityMetersPerSecond"
			);
			Objects.requireNonNull(motor, "motor");
			Objects.requireNonNull(propeller, "propeller");
		}
	}
}
