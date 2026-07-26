package com.tenicana.dronecraft.acoustics.wave;

/**
 * Uniform two-dimensional acoustic grid.
 *
 * <p>The pressure samples are cell-centred. Horizontal and vertical particle
 * velocities are stored on the corresponding cell faces. The time step must
 * obey the two-dimensional Courant limit.</p>
 */
public record WaveGrid2d(
		int widthCells,
		int heightCells,
		double cellSizeMeters,
		double timeStepSeconds,
		double speedOfSoundMetersPerSecond,
		double airDensityKilogramsPerCubicMeter
) {
	private static final double MAX_COURANT = 1.0 / Math.sqrt(2.0);

	public WaveGrid2d {
		if (widthCells < 3 || heightCells < 3) {
			throw new IllegalArgumentException(
					"grid dimensions must each contain at least three cells"
			);
		}
		requirePositiveFinite(cellSizeMeters, "cellSizeMeters");
		requirePositiveFinite(timeStepSeconds, "timeStepSeconds");
		requirePositiveFinite(
				speedOfSoundMetersPerSecond,
				"speedOfSoundMetersPerSecond"
		);
		requirePositiveFinite(
				airDensityKilogramsPerCubicMeter,
				"airDensityKilogramsPerCubicMeter"
		);
		if (courantNumber(timeStepSeconds, cellSizeMeters,
				speedOfSoundMetersPerSecond) > MAX_COURANT + 1.0e-15) {
			throw new IllegalArgumentException(
					"timeStepSeconds violates the two-dimensional CFL limit"
			);
		}
	}

	public static WaveGrid2d withCourantSafety(
			int widthCells,
			int heightCells,
			double cellSizeMeters,
			double courantSafety,
			double speedOfSoundMetersPerSecond,
			double airDensityKilogramsPerCubicMeter
	) {
		if (!(courantSafety > 0.0 && courantSafety <= 1.0)
				|| !Double.isFinite(courantSafety)) {
			throw new IllegalArgumentException("courantSafety must be in (0, 1]");
		}
		double timeStepSeconds = courantSafety * cellSizeMeters
				/ (speedOfSoundMetersPerSecond * Math.sqrt(2.0));
		return new WaveGrid2d(
				widthCells,
				heightCells,
				cellSizeMeters,
				timeStepSeconds,
				speedOfSoundMetersPerSecond,
				airDensityKilogramsPerCubicMeter
		);
	}

	public double courantNumber() {
		return courantNumber(
				timeStepSeconds,
				cellSizeMeters,
				speedOfSoundMetersPerSecond
		);
	}

	public double sampleRateHertz() {
		return 1.0 / timeStepSeconds;
	}

	private static double courantNumber(
			double timeStepSeconds,
			double cellSizeMeters,
			double speedOfSoundMetersPerSecond
	) {
		return speedOfSoundMetersPerSecond * timeStepSeconds / cellSizeMeters;
	}

	private static void requirePositiveFinite(double value, String name) {
		if (!(value > 0.0) || !Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be positive and finite");
		}
	}
}
