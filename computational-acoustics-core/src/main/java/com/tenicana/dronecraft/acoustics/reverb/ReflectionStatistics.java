package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;

/**
 * Energy-domain listener-environment statistics. These are propagation
 * observations, not an impulse response and not release-calibrated material
 * truth.
 */
public record ReflectionStatistics(
		long snapshotGeneration,
		int rayCount,
		int escapedRays,
		int surfaceHits,
		int earlySurfaceHits,
		int truncatedLegs,
		int absorptionTerminatedRays,
		int maximumBounceTerminatedRays,
		double hitLegDistanceMeters,
		double earlyHitLegDistanceMeters,
		double scatteringSum,
		LogRetention logRetentionSum,
		LogRetention earlyLogRetentionSum,
		AcousticBands firstHitReflectedEnergySum
) {
	public ReflectionStatistics {
		if (rayCount < 1 || escapedRays < 0 || escapedRays > rayCount
				|| surfaceHits < 0 || earlySurfaceHits < 0
				|| earlySurfaceHits > surfaceHits || truncatedLegs < 0
				|| absorptionTerminatedRays < 0
				|| maximumBounceTerminatedRays < 0) {
			throw new IllegalArgumentException("invalid reflection counts");
		}
		requireNonNegativeFinite(
				hitLegDistanceMeters,
				"hitLegDistanceMeters"
		);
		requireNonNegativeFinite(
				earlyHitLegDistanceMeters,
				"earlyHitLegDistanceMeters"
		);
		requireNonNegativeFinite(scatteringSum, "scatteringSum");
		if (scatteringSum > surfaceHits) {
			throw new IllegalArgumentException(
					"scatteringSum cannot exceed surfaceHits"
			);
		}
		if (logRetentionSum == null || earlyLogRetentionSum == null
				|| firstHitReflectedEnergySum == null) {
			throw new NullPointerException("reflection band statistics");
		}
	}

	public boolean complete() {
		return truncatedLegs == 0;
	}

	public double openness() {
		return (double) escapedRays / rayCount;
	}

	public double meanFreePathMeters() {
		return surfaceHits == 0 ? 0.0 : hitLegDistanceMeters / surfaceHits;
	}

	public double earlyMeanFreePathMeters() {
		return earlySurfaceHits == 0
				? 0.0
				: earlyHitLegDistanceMeters / earlySurfaceHits;
	}

	public double meanScattering() {
		return surfaceHits == 0 ? 0.0 : scatteringSum / surfaceHits;
	}

	public record LogRetention(double low, double mid, double high) {
		public static final LogRetention ZERO =
				new LogRetention(0.0, 0.0, 0.0);

		public LogRetention {
			requireNonPositiveFinite(low, "low");
			requireNonPositiveFinite(mid, "mid");
			requireNonPositiveFinite(high, "high");
		}

		public LogRetention add(LogRetention other) {
			return new LogRetention(
					low + other.low,
					mid + other.mid,
					high + other.high
			);
		}
	}

	private static void requireNonNegativeFinite(
			double value,
			String name
	) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
					name + " must be finite and non-negative"
			);
		}
	}

	private static void requireNonPositiveFinite(
			double value,
			String name
	) {
		if (!Double.isFinite(value) || value > 0.0) {
			throw new IllegalArgumentException(
					name + " must be finite and non-positive"
			);
		}
	}
}
