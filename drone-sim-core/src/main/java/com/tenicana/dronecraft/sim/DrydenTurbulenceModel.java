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

	static double longitudinalPoleHertz(Parameters parameters) {
		return poleHertz(parameters.longitudinalTimeConstantSeconds());
	}

	static double lateralPoleHertz(Parameters parameters) {
		return poleHertz(parameters.lateralTimeConstantSeconds());
	}

	static double verticalPoleHertz(Parameters parameters) {
		return poleHertz(parameters.verticalTimeConstantSeconds());
	}

	static double longitudinalSpectralMagnitudeRatio(double frequencyHertz, Parameters parameters) {
		return firstOrderSpectralMagnitudeRatio(frequencyHertz, parameters.longitudinalTimeConstantSeconds());
	}

	static double lateralSpectralMagnitudeRatio(double frequencyHertz, Parameters parameters) {
		return transverseSpectralMagnitudeRatio(frequencyHertz, parameters.lateralTimeConstantSeconds());
	}

	static double verticalSpectralMagnitudeRatio(double frequencyHertz, Parameters parameters) {
		return transverseSpectralMagnitudeRatio(frequencyHertz, parameters.verticalTimeConstantSeconds());
	}

	static double shapeTransverseAxis(double firstOrderValue, double laggedValue) {
		return TRANSVERSE_LEAD_LAG_SCALE * (firstOrderValue - TRANSVERSE_LAG_WEIGHT * laggedValue);
	}

	private static double poleHertz(double timeConstantSeconds) {
		if (!Double.isFinite(timeConstantSeconds) || timeConstantSeconds <= 0.0) {
			return 0.0;
		}
		return 1.0 / (2.0 * Math.PI * timeConstantSeconds);
	}

	private static double firstOrderSpectralMagnitudeRatio(double frequencyHertz, double timeConstantSeconds) {
		double frequency = Double.isFinite(frequencyHertz) ? Math.max(0.0, frequencyHertz) : 0.0;
		double timeConstant = Math.max(1.0e-9, Double.isFinite(timeConstantSeconds) ? timeConstantSeconds : 0.0);
		double omegaTau = 2.0 * Math.PI * frequency * timeConstant;
		return 1.0 / Math.sqrt(1.0 + omegaTau * omegaTau);
	}

	private static double transverseSpectralMagnitudeRatio(double frequencyHertz, double timeConstantSeconds) {
		double frequency = Double.isFinite(frequencyHertz) ? Math.max(0.0, frequencyHertz) : 0.0;
		double timeConstant = Math.max(1.0e-9, Double.isFinite(timeConstantSeconds) ? timeConstantSeconds : 0.0);
		double omegaTau = 2.0 * Math.PI * frequency * timeConstant;
		double denominator = 1.0 + omegaTau * omegaTau;
		double real = 1.0 - TRANSVERSE_LAG_WEIGHT / denominator;
		double imaginary = TRANSVERSE_LAG_WEIGHT * omegaTau / denominator;
		double leadLagMagnitude = TRANSVERSE_LEAD_LAG_SCALE * Math.sqrt(real * real + imaginary * imaginary);
		return firstOrderSpectralMagnitudeRatio(frequency, timeConstant) * leadLagMagnitude;
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
