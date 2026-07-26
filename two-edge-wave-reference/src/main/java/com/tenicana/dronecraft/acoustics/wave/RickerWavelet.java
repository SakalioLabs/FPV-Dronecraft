package com.tenicana.dronecraft.acoustics.wave;

/**
 * Deterministic, zero-mean band-limited source used by the offline solver.
 */
public final class RickerWavelet {
	private RickerWavelet() {
	}

	public static double[] generate(
			int sampleCount,
			double timeStepSeconds,
			double centreFrequencyHertz,
			double centreTimeSeconds
	) {
		if (sampleCount <= 0) {
			throw new IllegalArgumentException("sampleCount must be positive");
		}
		requirePositiveFinite(timeStepSeconds, "timeStepSeconds");
		requirePositiveFinite(centreFrequencyHertz, "centreFrequencyHertz");
		requirePositiveFinite(centreTimeSeconds, "centreTimeSeconds");
		double[] result = new double[sampleCount];
		double angularScale = Math.PI * centreFrequencyHertz;
		for (int sample = 0; sample < sampleCount; sample++) {
			double offset = sample * timeStepSeconds - centreTimeSeconds;
			double squared = Math.pow(angularScale * offset, 2.0);
			result[sample] = (1.0 - 2.0 * squared) * Math.exp(-squared);
		}
		return result;
	}

	private static void requirePositiveFinite(double value, String name) {
		if (!(value > 0.0) || !Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be positive and finite");
		}
	}
}
