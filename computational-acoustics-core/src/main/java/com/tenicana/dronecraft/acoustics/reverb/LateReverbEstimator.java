package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionStatistics.LogRetention;

import java.util.Objects;

/**
 * Converts deterministic reflection statistics into energy-decay parameters.
 * The estimator is an engineering baseline [H], not a substitute for measured
 * room impulse responses.
 */
public final class LateReverbEstimator {
	private static final double SIXTY_DB_NEPERS = 6.0 * Math.log(10.0);
	private static final double MINIMUM_PROBABILITY = 1.0e-9;
	private static final double MAXIMUM_DRR_DB = 60.0;

	private LateReverbEstimator() {
	}

	public static Parameters estimate(
			ReflectionStatistics statistics,
			double soundSpeedMetersPerSecond
	) {
		Objects.requireNonNull(statistics, "statistics");
		if (!statistics.complete()) {
			throw new IllegalArgumentException(
					"reflection statistics must not contain truncated legs"
			);
		}
		if (!Double.isFinite(soundSpeedMetersPerSecond)
				|| soundSpeedMetersPerSecond <= 0.0) {
			throw new IllegalArgumentException(
					"sound speed must be finite and positive"
			);
		}
		if (statistics.surfaceHits() == 0) {
			return new Parameters(
					statistics.snapshotGeneration(),
					statistics.openness(),
					statistics.meanFreePathMeters(),
					statistics.meanScattering(),
					AcousticBands.SILENT,
					AcousticBands.SILENT,
					new AcousticBands(
							MAXIMUM_DRR_DB,
							MAXIMUM_DRR_DB,
							MAXIMUM_DRR_DB
					),
					AcousticBands.SILENT
			);
		}

		double continuationProbability = (double) statistics.surfaceHits()
				/ (statistics.surfaceHits() + statistics.escapedRays());
		AcousticBands rt60 = decayTime(
				statistics.logRetentionSum(),
				statistics.surfaceHits(),
				statistics.meanFreePathMeters(),
				continuationProbability,
				soundSpeedMetersPerSecond
		);
		AcousticBands edt = statistics.earlySurfaceHits() == 0
				? AcousticBands.SILENT
				: decayTime(
						statistics.earlyLogRetentionSum(),
						statistics.earlySurfaceHits(),
						statistics.earlyMeanFreePathMeters(),
						continuationProbability,
						soundSpeedMetersPerSecond
				);
		AcousticBands firstReflectionEnergy =
				statistics.firstHitReflectedEnergySum().multiply(
						1.0 / statistics.rayCount()
				);
		AcousticBands drr = firstReflectionEnergy.map(
				energy -> Math.min(
						MAXIMUM_DRR_DB,
						-10.0 * Math.log10(
								Math.max(MINIMUM_PROBABILITY, energy)
						)
				)
		);
		return new Parameters(
				statistics.snapshotGeneration(),
				statistics.openness(),
				statistics.meanFreePathMeters(),
				statistics.meanScattering(),
				rt60,
				edt,
				drr,
				firstReflectionEnergy
		);
	}

	private static AcousticBands decayTime(
			LogRetention logRetention,
			int hits,
			double meanFreePathMeters,
			double continuationProbability,
			double soundSpeedMetersPerSecond
	) {
		double escapeLog = Math.log(
				Math.max(MINIMUM_PROBABILITY, continuationProbability)
		);
		return new AcousticBands(
				decayTime(
						logRetention.low() / hits + escapeLog,
						meanFreePathMeters,
						soundSpeedMetersPerSecond
				),
				decayTime(
						logRetention.mid() / hits + escapeLog,
						meanFreePathMeters,
						soundSpeedMetersPerSecond
				),
				decayTime(
						logRetention.high() / hits + escapeLog,
						meanFreePathMeters,
						soundSpeedMetersPerSecond
				)
		);
	}

	private static double decayTime(
			double meanLogRetention,
			double meanFreePathMeters,
			double soundSpeedMetersPerSecond
	) {
		if (meanLogRetention >= 0.0 || meanFreePathMeters <= 0.0) {
			return 0.0;
		}
		return -SIXTY_DB_NEPERS * meanFreePathMeters
				/ (soundSpeedMetersPerSecond * meanLogRetention);
	}

	public record Parameters(
			long snapshotGeneration,
			double openness,
			double meanFreePathMeters,
			double diffusion,
			AcousticBands rt60Seconds,
			AcousticBands edtSeconds,
			AcousticBands directToReverberantDb,
			AcousticBands firstReflectionEnergy
	) {
		public Parameters {
			requireUnitInterval(openness, "openness");
			requireNonNegativeFinite(
					meanFreePathMeters,
					"meanFreePathMeters"
			);
			requireUnitInterval(diffusion, "diffusion");
			Objects.requireNonNull(rt60Seconds, "rt60Seconds");
			Objects.requireNonNull(edtSeconds, "edtSeconds");
			Objects.requireNonNull(
					directToReverberantDb,
					"directToReverberantDb"
			);
			Objects.requireNonNull(
					firstReflectionEnergy,
					"firstReflectionEnergy"
			);
		}
	}

	private static void requireUnitInterval(double value, String name) {
		if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
			throw new IllegalArgumentException(
					name + " must be in [0, 1]"
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
}
