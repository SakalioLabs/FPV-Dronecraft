package com.tenicana.dronecraft.sim.flight;

import java.util.Arrays;

public record ActuatorOutput(
		double[] motorPower,
		double[] motorRpm,
		double[] rotorThrustNewtons
) {
	private static final double[] EMPTY = new double[0];

	public ActuatorOutput {
		motorPower = copyOrEmpty(motorPower);
		motorRpm = copyOrEmpty(motorRpm);
		rotorThrustNewtons = copyOrEmpty(rotorThrustNewtons);
	}

	public static ActuatorOutput empty() {
		return new ActuatorOutput(EMPTY, EMPTY, EMPTY);
	}

	@Override
	public double[] motorPower() {
		return Arrays.copyOf(motorPower, motorPower.length);
	}

	@Override
	public double[] motorRpm() {
		return Arrays.copyOf(motorRpm, motorRpm.length);
	}

	@Override
	public double[] rotorThrustNewtons() {
		return Arrays.copyOf(rotorThrustNewtons, rotorThrustNewtons.length);
	}

	public double averageMotorPower() {
		return average(motorPower);
	}

	public double averageMotorRpm() {
		return average(motorRpm);
	}

	public double averageRotorThrustNewtons() {
		return average(rotorThrustNewtons);
	}

	private static double[] copyOrEmpty(double[] values) {
		return values == null ? EMPTY : Arrays.copyOf(values, values.length);
	}

	private static double average(double[] values) {
		if (values.length == 0) {
			return 0.0;
		}
		double sum = 0.0;
		for (double value : values) {
			sum += value;
		}
		return sum / values.length;
	}
}
