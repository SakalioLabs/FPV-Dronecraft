package com.tenicana.dronecraft.sim;

public final class MotorBenchCurrentModel {
	public static final double MQTB_HQ5X4X3_CURRENT_FIT_A = 1.4297926376886003;
	public static final double MQTB_HQ5X4X3_CURRENT_FIT_B = 1.1578910573663044;
	public static final double MQTB_HQ5X4X3_POWER_FIT_A = 23.004245948867702;
	public static final double MQTB_HQ5X4X3_POWER_FIT_B = 1.150640372104493;

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

	private static double powerLaw(double thrustNewtons, double coefficient, double exponent) {
		if (!Double.isFinite(thrustNewtons) || thrustNewtons <= 0.0) {
			return 0.0;
		}
		return coefficient * Math.pow(thrustNewtons, exponent);
	}
}
