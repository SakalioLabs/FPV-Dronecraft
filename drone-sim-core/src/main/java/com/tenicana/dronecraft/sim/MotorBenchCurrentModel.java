package com.tenicana.dronecraft.sim;

public final class MotorBenchCurrentModel {
	public static final double MQTB_HQ5X4X3_CURRENT_FIT_A = 1.4297926376886003;
	public static final double MQTB_HQ5X4X3_CURRENT_FIT_B = 1.1578910573663044;
	public static final double MQTB_HQ5X4X3_POWER_FIT_A = 23.004245948867702;
	public static final double MQTB_HQ5X4X3_POWER_FIT_B = 1.150640372104493;
	public static final double MQTB_HQ5X4X3_RADIUS_METERS = 0.0635;
	public static final double MQTB_HQ5X4X3_PITCH_TO_DIAMETER_RATIO = 0.80;
	public static final int MQTB_HQ5X4X3_BLADE_COUNT = 3;

	private MotorBenchCurrentModel() {
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

	private static double powerLaw(double thrustNewtons, double coefficient, double exponent) {
		if (!Double.isFinite(thrustNewtons) || thrustNewtons <= 0.0) {
			return 0.0;
		}
		return coefficient * Math.pow(thrustNewtons, exponent);
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
