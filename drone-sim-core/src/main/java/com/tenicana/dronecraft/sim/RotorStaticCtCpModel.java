package com.tenicana.dronecraft.sim;

public final class RotorStaticCtCpModel {
	public static final String SOURCE_ID = "rotor-spec-static-ct-cp-model";
	public static final double DEFAULT_REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double EPSILON = 1.0e-9;

	private RotorStaticCtCpModel() {
	}

	public record StaticRotorSample(
			String presetName,
			String caseName,
			String sourceId,
			double rpm,
			double airDensityKgPerCubicMeter,
			double referenceAirDensityKgPerCubicMeter,
			double rotorRadiusMeters,
			double propellerDiameterMeters,
			double revolutionsPerSecond,
			double angularVelocityRadiansPerSecond,
			double thrustCoefficientCt,
			double powerCoefficientCp,
			double propulsiveEfficiencyEta,
			double thrustNewtons,
			double shaftPowerWatts,
			double shaftTorqueNewtonMeters,
			double diskAreaSquareMeters,
			double diskLoadingNewtonsPerSquareMeter,
			double idealInducedVelocityMetersPerSecond,
			double idealMomentumPowerWatts,
			double idealMomentumPowerOverShaftPower
	) {
	}

	public static StaticRotorSample sample(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double rpm,
			double airDensityKgPerCubicMeter
	) {
		return sample(
				presetName,
				caseName,
				rotor,
				rpm,
				airDensityKgPerCubicMeter,
				DEFAULT_REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER
		);
	}

	public static StaticRotorSample sample(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double rpm,
			double airDensityKgPerCubicMeter,
			double referenceAirDensityKgPerCubicMeter
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		if (!Double.isFinite(rpm) || rpm <= 0.0) {
			throw new IllegalArgumentException("rpm must be finite and positive.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(referenceAirDensityKgPerCubicMeter) || referenceAirDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("referenceAirDensityKgPerCubicMeter must be finite and positive.");
		}
		String normalizedPreset = presetName == null || presetName.isBlank() ? "unknown" : presetName;
		String normalizedCase = caseName == null || caseName.isBlank() ? "static_rotor_spec" : caseName;
		double diameter = rotor.radiusMeters() * 2.0;
		double revolutionsPerSecond = rpm / 60.0;
		double omega = revolutionsPerSecond * 2.0 * Math.PI;
		double ct = thrustCoefficientCt(rotor, referenceAirDensityKgPerCubicMeter);
		double cp = powerCoefficientCp(rotor, referenceAirDensityKgPerCubicMeter);
		double thrust = ct
				* airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(diameter, 4.0);
		double shaftPower = cp
				* airDensityKgPerCubicMeter
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(diameter, 5.0);
		double torque = omega > EPSILON ? shaftPower / omega : 0.0;
		double diskArea = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double diskLoading = diskArea > EPSILON ? thrust / diskArea : 0.0;
		double inducedVelocity = thrust > EPSILON
				? Math.sqrt(thrust / (2.0 * airDensityKgPerCubicMeter * diskArea))
				: 0.0;
		double idealMomentumPower = thrust * inducedVelocity;
		double momentumOverShaft = ratio(idealMomentumPower, shaftPower);
		return new StaticRotorSample(
				normalizedPreset,
				normalizedCase,
				SOURCE_ID,
				rpm,
				airDensityKgPerCubicMeter,
				referenceAirDensityKgPerCubicMeter,
				rotor.radiusMeters(),
				diameter,
				revolutionsPerSecond,
				omega,
				ct,
				cp,
				0.0,
				thrust,
				shaftPower,
				torque,
				diskArea,
				diskLoading,
				inducedVelocity,
				idealMomentumPower,
				momentumOverShaft
		);
	}

	public static double thrustCoefficientCt(
			RotorSpec rotor,
			double referenceAirDensityKgPerCubicMeter
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		if (!Double.isFinite(referenceAirDensityKgPerCubicMeter) || referenceAirDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("referenceAirDensityKgPerCubicMeter must be finite and positive.");
		}
		double diameter = rotor.radiusMeters() * 2.0;
		return rotor.thrustCoefficient()
				* Math.pow(2.0 * Math.PI, 2.0)
				/ (referenceAirDensityKgPerCubicMeter * Math.pow(diameter, 4.0));
	}

	public static double powerCoefficientCp(
			RotorSpec rotor,
			double referenceAirDensityKgPerCubicMeter
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		if (!Double.isFinite(referenceAirDensityKgPerCubicMeter) || referenceAirDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("referenceAirDensityKgPerCubicMeter must be finite and positive.");
		}
		double diameter = rotor.radiusMeters() * 2.0;
		return rotor.yawTorquePerThrustMeter()
				* rotor.thrustCoefficient()
				* Math.pow(2.0 * Math.PI, 3.0)
				/ (referenceAirDensityKgPerCubicMeter * Math.pow(diameter, 5.0));
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
