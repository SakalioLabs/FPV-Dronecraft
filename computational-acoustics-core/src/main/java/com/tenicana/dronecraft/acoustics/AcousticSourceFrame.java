package com.tenicana.dronecraft.acoustics;

import java.util.List;
import java.util.Objects;

/**
 * Immutable hand-off from game telemetry to the audio engine.
 */
public record AcousticSourceFrame(
		long entityId,
		long simulationTimeNanos,
		AcousticVector positionMeters,
		AcousticVector velocityMetersPerSecond,
		AcousticVector rotorDiskNormal,
		double acousticApertureRadiusMeters,
		List<RotorAcousticState> rotors
) {
	public AcousticSourceFrame {
		Objects.requireNonNull(positionMeters, "positionMeters");
		Objects.requireNonNull(velocityMetersPerSecond, "velocityMetersPerSecond");
		Objects.requireNonNull(rotorDiskNormal, "rotorDiskNormal");
		rotors = List.copyOf(Objects.requireNonNull(rotors, "rotors"));
		if (simulationTimeNanos < 0L) {
			throw new IllegalArgumentException("simulationTimeNanos must be non-negative");
		}
		if (rotors.isEmpty()) {
			throw new IllegalArgumentException("at least one rotor is required");
		}
		if (rotorDiskNormal.length() <= 1.0e-12) {
			throw new IllegalArgumentException("rotorDiskNormal must be non-zero");
		}
		if (!Double.isFinite(acousticApertureRadiusMeters)
				|| acousticApertureRadiusMeters < 0.005
				|| acousticApertureRadiusMeters > 5.0) {
			throw new IllegalArgumentException("acousticApertureRadiusMeters must be in [0.005, 5.0]");
		}
		rotorDiskNormal = rotorDiskNormal.normalized();
	}
}
