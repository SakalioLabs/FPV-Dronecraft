package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;

import java.util.Objects;

/**
 * Maps estimator output to conservative FDN controls. Wet scaling and
 * transition time are explicit engineering defaults [H].
 */
public final class FdnEnvironmentMapper {
	public static final double DEFAULT_TRANSITION_SECONDS = 0.2;
	public static final double MAXIMUM_WET_GAIN = 0.45;
	private static final double WET_ENERGY_SCALE = 0.4;

	private FdnEnvironmentMapper() {
	}

	public static Controls map(
			LateReverbEstimator.Parameters parameters
	) {
		Objects.requireNonNull(parameters, "parameters");
		double meanFirstReflectionEnergy =
				parameters.firstReflectionEnergy().totalEnergy() / 3.0;
		double wetGain = Math.min(
				MAXIMUM_WET_GAIN,
				Math.sqrt(meanFirstReflectionEnergy)
						* WET_ENERGY_SCALE
						* (1.0 - parameters.openness())
		);
		if (parameters.rt60Seconds().totalEnergy() == 0.0) {
			wetGain = 0.0;
		}
		return new Controls(
				parameters.snapshotGeneration(),
				parameters.rt60Seconds(),
				wetGain,
				DEFAULT_TRANSITION_SECONDS
		);
	}

	public record Controls(
			long snapshotGeneration,
			AcousticBands rt60Seconds,
			double wetGain,
			double transitionSeconds
	) {
		public Controls {
			Objects.requireNonNull(rt60Seconds, "rt60Seconds");
			if (!Double.isFinite(wetGain)
					|| wetGain < 0.0 || wetGain > MAXIMUM_WET_GAIN) {
				throw new IllegalArgumentException("invalid wetGain");
			}
			if (!Double.isFinite(transitionSeconds)
					|| transitionSeconds < 0.0) {
				throw new IllegalArgumentException(
						"invalid transitionSeconds"
				);
			}
		}
	}
}
