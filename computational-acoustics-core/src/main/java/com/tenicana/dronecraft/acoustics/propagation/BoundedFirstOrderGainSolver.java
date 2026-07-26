package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;

import java.util.Objects;

/**
 * D120 physically bounded gain stage for a direct path and six first-order
 * reflections. A solve call allocates no heap objects when its arrays and
 * workspaces are reused.
 */
public final class BoundedFirstOrderGainSolver {
	public static final double MINIMUM_DISTANCE_METERS = 0.25;
	public static final double MINIMUM_POSITIVE_ENERGY_GAIN = 1.0e-6;
	public static final double MAXIMUM_ENERGY_GAIN = 1.0;
	public static final double MINIMUM_CORRECTION_DB = -6.0;
	public static final double MAXIMUM_CORRECTION_DB = 3.0;

	private BoundedFirstOrderGainSolver() {
	}

	/**
	 * Computes path gains. Path zero is the direct-path reference and is one
	 * when visible. Facet material and correction index {@code path - 1}.
	 * Unsupported queries ignore every supplied correction.
	 */
	public static void solve(
			DdaFirstOrderBatchSolver.Workspace paths,
			AcousticMaterial[] facetMaterials,
			double[] lowCorrectionDb,
			double[] midCorrectionDb,
			double[] highCorrectionDb,
			boolean calibrationSupported,
			Workspace output
	) {
		Objects.requireNonNull(paths, "paths");
		requireSix(facetMaterials, "facetMaterials");
		requireSix(lowCorrectionDb, "lowCorrectionDb");
		requireSix(midCorrectionDb, "midCorrectionDb");
		requireSix(highCorrectionDb, "highCorrectionDb");
		Objects.requireNonNull(output, "output");

		double direct = paths.lengthMeters(DdaFirstOrderBatchSolver.DIRECT_PATH);
		if (!Double.isFinite(direct) || direct < 0.0) {
			throw new IllegalArgumentException(
					"direct path length must be finite and non-negative"
			);
		}
		double directGain = paths.topologyVisible(
				DdaFirstOrderBatchSolver.DIRECT_PATH
		) ? 1.0 : 0.0;
		output.low[0] = directGain;
		output.mid[0] = directGain;
		output.high[0] = directGain;

		for (int facet = 0; facet < 6; facet++) {
			int path = facet + 1;
			AcousticMaterial material = Objects.requireNonNull(
					facetMaterials[facet],
					"facet material"
			);
			double reflected = paths.lengthMeters(path);
			if (!Double.isFinite(reflected) || reflected < 0.0) {
				throw new IllegalArgumentException(
						"reflected path length must be finite and non-negative"
				);
			}
			if (!paths.topologyVisible(path)) {
				output.low[path] = 0.0;
				output.mid[path] = 0.0;
				output.high[path] = 0.0;
				continue;
			}
			AcousticBands absorption = material.surfaceAbsorption();
			output.low[path] = energyGain(
					direct,
					reflected,
					absorption.low(),
					material.scattering(),
					lowCorrectionDb[facet],
					calibrationSupported
			);
			output.mid[path] = energyGain(
					direct,
					reflected,
					absorption.mid(),
					material.scattering(),
					midCorrectionDb[facet],
					calibrationSupported
			);
			output.high[path] = energyGain(
					direct,
					reflected,
					absorption.high(),
					material.scattering(),
					highCorrectionDb[facet],
					calibrationSupported
			);
		}
	}

	public static double energyGain(
			double directDistanceMeters,
			double reflectedDistanceMeters,
			double absorption,
			double scattering,
			double correctionDb,
			boolean calibrationSupported
	) {
		if (!Double.isFinite(directDistanceMeters)
				|| directDistanceMeters < 0.0
				|| !Double.isFinite(reflectedDistanceMeters)
				|| reflectedDistanceMeters < 0.0) {
			throw new IllegalArgumentException(
					"path distances must be finite and non-negative"
			);
		}
		requireUnitInterval(absorption, "absorption");
		requireUnitInterval(scattering, "scattering");
		if (!Double.isFinite(correctionDb)) {
			throw new IllegalArgumentException(
					"calibration corrections must be finite"
			);
		}
		double distanceRatio = Math.max(
				directDistanceMeters,
				MINIMUM_DISTANCE_METERS
		) / Math.max(reflectedDistanceMeters, MINIMUM_DISTANCE_METERS);
		double spreading = distanceRatio * distanceRatio;
		double correction = Math.pow(
				10.0,
				clamp(
						calibrationSupported ? correctionDb : 0.0,
						MINIMUM_CORRECTION_DB,
						MAXIMUM_CORRECTION_DB
				) / 10.0
		);
		double raw = spreading * (1.0 - absorption)
				* (1.0 - scattering) * correction;
		if (raw <= 0.0) {
			return 0.0;
		}
		return clamp(
				raw,
				MINIMUM_POSITIVE_ENERGY_GAIN,
				MAXIMUM_ENERGY_GAIN
		);
	}

	private static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static void requireUnitInterval(double value, String label) {
		if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
			throw new IllegalArgumentException(label + " must be in [0, 1]");
		}
	}

	private static void requireSix(Object[] values, String label) {
		Objects.requireNonNull(values, label);
		if (values.length != 6) {
			throw new IllegalArgumentException(label + " must contain six values");
		}
	}

	private static void requireSix(double[] values, String label) {
		Objects.requireNonNull(values, label);
		if (values.length != 6) {
			throw new IllegalArgumentException(label + " must contain six values");
		}
	}

	public static final class Workspace {
		private final double[] low =
				new double[DdaFirstOrderBatchSolver.PATH_COUNT];
		private final double[] mid =
				new double[DdaFirstOrderBatchSolver.PATH_COUNT];
		private final double[] high =
				new double[DdaFirstOrderBatchSolver.PATH_COUNT];

		public double low(int pathIndex) {
			return low[checked(pathIndex)];
		}

		public double mid(int pathIndex) {
			return mid[checked(pathIndex)];
		}

		public double high(int pathIndex) {
			return high[checked(pathIndex)];
		}

		public double totalEnergy(int pathIndex) {
			int checked = checked(pathIndex);
			return low[checked] + mid[checked] + high[checked];
		}

		private static int checked(int pathIndex) {
			if (pathIndex < 0
					|| pathIndex >= DdaFirstOrderBatchSolver.PATH_COUNT) {
				throw new IndexOutOfBoundsException(pathIndex);
			}
			return pathIndex;
		}
	}
}
