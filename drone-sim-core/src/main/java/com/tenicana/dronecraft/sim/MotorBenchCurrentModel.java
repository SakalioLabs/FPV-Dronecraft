package com.tenicana.dronecraft.sim;

public final class MotorBenchCurrentModel {
	public static final double MQTB_HQ5X4X3_CURRENT_FIT_A = 1.4297926376886003;
	public static final double MQTB_HQ5X4X3_CURRENT_FIT_B = 1.1578910573663044;
	public static final double MQTB_HQ5X4X3_POWER_FIT_A = 23.004245948867702;
	public static final double MQTB_HQ5X4X3_POWER_FIT_B = 1.150640372104493;
	public static final double MQTB_HQ5X4X3_RADIUS_METERS = 0.0635;
	public static final double MQTB_HQ5X4X3_PITCH_TO_DIAMETER_RATIO = 0.80;
	public static final int MQTB_HQ5X4X3_BLADE_COUNT = 3;
	public static final String TYTO_X3NM_SOURCE_ID = "x3nm";
	public static final double TYTO_X3NM_MAX_THRUST_NEWTONS = 12.547278947987;
	public static final double TYTO_X3NM_MAX_CURRENT_AMPS = 22.185563411713;
	public static final double TYTO_X3NM_VOLTAGE_AT_MAX_THRUST = 24.213822555542;
	public static final double TYTO_X3NM_FIT_THRUST_COEFFICIENT = 1.7996539842396274e-6;
	public static final double TYTO_X3NM_FIT_R2 = 0.9988870109198886;
	public static final int TYTO_X3NM_FIT_POINT_COUNT = 7;

	private static final double RADIANS_PER_SECOND_TO_RPM = 60.0 / (2.0 * Math.PI);

	private MotorBenchCurrentModel() {
	}

	public record StaticPowertrainAudit(
			String referenceId,
			double configuredMaxRotorThrustNewtons,
			double configuredThrustCoefficient,
			double configuredMaxRpm,
			double referenceMaxThrustNewtons,
			double referenceMaxCurrentAmps,
			double referenceVoltageAtMaxThrust,
			double referenceThrustCoefficient,
			double referenceFitR2,
			int referenceFitPointCount,
			double referenceRpmAtMaxThrust,
			double referenceEquivalentRpmForConfiguredMaxThrust,
			double configuredMaxThrustOverReference,
			double configuredThrustCoefficientOverReference
	) {
	}

	public static double mqtbHq5x4x3CurrentAmpsForThrustNewtons(double thrustNewtons) {
		return powerLaw(thrustNewtons, MQTB_HQ5X4X3_CURRENT_FIT_A, MQTB_HQ5X4X3_CURRENT_FIT_B);
	}

	public static double mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(double thrustNewtons) {
		return powerLaw(thrustNewtons, MQTB_HQ5X4X3_POWER_FIT_A, MQTB_HQ5X4X3_POWER_FIT_B);
	}

	public static double mqtbHq5x4x3TotalCurrentAmps(DroneState state) {
		double total = 0.0;
		for (double thrust : state.rotorThrustNewtons()) {
			total += mqtbHq5x4x3CurrentAmpsForThrustNewtons(thrust);
		}
		return total;
	}

	public static double mqtbHq5x4x3TotalElectricalPowerWatts(DroneState state) {
		double total = 0.0;
		for (double thrust : state.rotorThrustNewtons()) {
			total += mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(thrust);
		}
		return total;
	}

	public static double totalMotorCurrentAmps(DroneState state) {
		double total = 0.0;
		for (double current : state.motorCurrentAmps()) {
			total += current;
		}
		return total;
	}

	public static double mqtbHq5x4x3CurrentRatio(DroneState state) {
		double referenceCurrent = mqtbHq5x4x3TotalCurrentAmps(state);
		if (referenceCurrent <= 1.0e-9) {
			return 0.0;
		}
		return totalMotorCurrentAmps(state) / referenceCurrent;
	}

	public static double mqtbHq5x4x3CurrentResidualAmps(DroneState state) {
		return totalMotorCurrentAmps(state) - mqtbHq5x4x3TotalCurrentAmps(state);
	}

	public static double mqtbHq5x4x3RotorSimilarity(RotorSpec rotor) {
		if (rotor == null) {
			return 0.0;
		}

		double radiusWeight = plateauWindow(rotor.radiusMeters(), MQTB_HQ5X4X3_RADIUS_METERS, 0.010, 0.025);
		double pitchWeight = plateauWindow(
				rotor.bladePitchToDiameterRatio(),
				MQTB_HQ5X4X3_PITCH_TO_DIAMETER_RATIO,
				0.075,
				0.22
		);
		double bladeWeight = 1.0 - MathUtil.clamp(
				Math.abs(rotor.bladeCount() - MQTB_HQ5X4X3_BLADE_COUNT),
				0.0,
				1.0
		);
		return MathUtil.clamp(radiusWeight * pitchWeight * bladeWeight, 0.0, 1.0);
	}

	public static StaticPowertrainAudit tytoX3nmStaticPowertrainAudit(DroneConfig config) {
		double configuredMaxThrustNewtons = averageMaxRotorThrustNewtons(config);
		double configuredThrustCoefficient = averageThrustCoefficient(config);
		double configuredMaxRpm = averageMaxRotorRpm(config);
		double referenceRpmAtMaxThrust = rpmForThrustAndCoefficient(
				TYTO_X3NM_MAX_THRUST_NEWTONS,
				TYTO_X3NM_FIT_THRUST_COEFFICIENT
		);
		double referenceEquivalentRpmForConfiguredMaxThrust = rpmForThrustAndCoefficient(
				configuredMaxThrustNewtons,
				TYTO_X3NM_FIT_THRUST_COEFFICIENT
		);
		return new StaticPowertrainAudit(
				TYTO_X3NM_SOURCE_ID,
				configuredMaxThrustNewtons,
				configuredThrustCoefficient,
				configuredMaxRpm,
				TYTO_X3NM_MAX_THRUST_NEWTONS,
				TYTO_X3NM_MAX_CURRENT_AMPS,
				TYTO_X3NM_VOLTAGE_AT_MAX_THRUST,
				TYTO_X3NM_FIT_THRUST_COEFFICIENT,
				TYTO_X3NM_FIT_R2,
				TYTO_X3NM_FIT_POINT_COUNT,
				referenceRpmAtMaxThrust,
				referenceEquivalentRpmForConfiguredMaxThrust,
				ratio(configuredMaxThrustNewtons, TYTO_X3NM_MAX_THRUST_NEWTONS),
				ratio(configuredThrustCoefficient, TYTO_X3NM_FIT_THRUST_COEFFICIENT)
		);
	}

	private static double powerLaw(double thrustNewtons, double coefficient, double exponent) {
		if (!Double.isFinite(thrustNewtons) || thrustNewtons <= 0.0) {
			return 0.0;
		}
		return coefficient * Math.pow(thrustNewtons, exponent);
	}

	private static double averageMaxRotorThrustNewtons(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.maxThrustNewtons();
		}
		return total / config.rotors().size();
	}

	private static double averageThrustCoefficient(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.thrustCoefficient();
		}
		return total / config.rotors().size();
	}

	private static double averageMaxRotorRpm(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}

		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.maxOmegaRadiansPerSecond() * RADIANS_PER_SECOND_TO_RPM;
		}
		return total / config.rotors().size();
	}

	private static double rpmForThrustAndCoefficient(double thrustNewtons, double thrustCoefficient) {
		if (!Double.isFinite(thrustNewtons)
				|| !Double.isFinite(thrustCoefficient)
				|| thrustNewtons <= 0.0
				|| thrustCoefficient <= 0.0) {
			return 0.0;
		}
		return Math.sqrt(thrustNewtons / thrustCoefficient) * RADIANS_PER_SECOND_TO_RPM;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static double plateauWindow(double value, double center, double innerHalfWidth, double outerHalfWidth) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}

		double distance = Math.abs(value - center);
		if (distance <= innerHalfWidth) {
			return 1.0;
		}
		if (distance >= outerHalfWidth) {
			return 0.0;
		}
		return 1.0 - (distance - innerHalfWidth) / (outerHalfWidth - innerHalfWidth);
	}
}
