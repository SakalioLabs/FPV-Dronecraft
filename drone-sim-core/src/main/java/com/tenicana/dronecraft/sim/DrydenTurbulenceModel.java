package com.tenicana.dronecraft.sim;

final class DrydenTurbulenceModel {
	private static final double FEET_PER_METER = 3.280839895;
	static final double TRANSVERSE_LEAD_LAG_SCALE = Math.sqrt(1.5);
	static final double TRANSVERSE_LAG_WEIGHT = 1.0 - 1.0 / Math.sqrt(3.0);

	private DrydenTurbulenceModel() {
	}

	static Parameters lowAltitude(double altitudeMeters, double wind20MetersPerSecond) {
		double altitude = MathUtil.clamp(Double.isFinite(altitudeMeters) ? altitudeMeters : 6.0, 1.0, 300.0);
		double wind20 = MathUtil.clamp(Double.isFinite(wind20MetersPerSecond) ? wind20MetersPerSecond : 0.0, 0.0, 80.0);
		double altitudeFeet = altitude * FEET_PER_METER;
		double wind20FeetPerSecond = wind20 * FEET_PER_METER;
		double denominator = 0.177 + 0.000823 * Math.max(1.0, altitudeFeet);
		double longitudinalScaleFeet = altitudeFeet / Math.pow(denominator, 1.2);
		double verticalScaleFeet = altitudeFeet;
		double verticalSigmaFeetPerSecond = 0.1 * wind20FeetPerSecond;
		double longitudinalSigmaFeetPerSecond = verticalSigmaFeetPerSecond / Math.pow(denominator, 0.4);
		double longitudinalScaleMeters = longitudinalScaleFeet / FEET_PER_METER;
		double verticalScaleMeters = verticalScaleFeet / FEET_PER_METER;
		double longitudinalSigmaMetersPerSecond = longitudinalSigmaFeetPerSecond / FEET_PER_METER;
		double verticalSigmaMetersPerSecond = verticalSigmaFeetPerSecond / FEET_PER_METER;
		double referenceSpeed = Math.max(0.1, wind20);

		return new Parameters(
				altitude,
				wind20,
				longitudinalScaleMeters,
				longitudinalScaleMeters,
				verticalScaleMeters,
				longitudinalSigmaMetersPerSecond,
				longitudinalSigmaMetersPerSecond,
				verticalSigmaMetersPerSecond,
				longitudinalScaleMeters / referenceSpeed,
				longitudinalScaleMeters / referenceSpeed,
				verticalScaleMeters / referenceSpeed
		);
	}

	record Parameters(
			double altitudeMeters,
			double wind20MetersPerSecond,
			double longitudinalScaleMeters,
			double lateralScaleMeters,
			double verticalScaleMeters,
			double longitudinalSigmaMetersPerSecond,
			double lateralSigmaMetersPerSecond,
			double verticalSigmaMetersPerSecond,
			double longitudinalTimeConstantSeconds,
			double lateralTimeConstantSeconds,
			double verticalTimeConstantSeconds
	) {
	}
}
