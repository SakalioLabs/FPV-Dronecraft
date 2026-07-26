package com.tenicana.dronecraft.acoustics;

/**
 * One rotor's telemetry at a simulation instant. Power and load are normalized
 * inputs until measurement-derived calibration is available.
 */
public record RotorAcousticState(
		double rpm,
		double normalizedPower,
		double normalizedLoad,
		double radiusMeters,
		int bladeCount,
		int motorPolePairs,
		int spinDirection,
		double phaseRadians
) {
	public RotorAcousticState {
		requireRange(rpm, 0.0, 200_000.0, "rpm");
		requireRange(normalizedPower, 0.0, 1.0, "normalizedPower");
		requireRange(normalizedLoad, 0.0, 2.0, "normalizedLoad");
		requireRange(radiusMeters, 0.005, 1.0, "radiusMeters");
		if (bladeCount < 1 || bladeCount > 16) {
			throw new IllegalArgumentException("bladeCount must be between 1 and 16");
		}
		if (motorPolePairs < 1 || motorPolePairs > 64) {
			throw new IllegalArgumentException("motorPolePairs must be between 1 and 64");
		}
		if (spinDirection != -1 && spinDirection != 1) {
			throw new IllegalArgumentException("spinDirection must be -1 or 1");
		}
		if (!Double.isFinite(phaseRadians)) {
			throw new IllegalArgumentException("phaseRadians must be finite");
		}
		phaseRadians = wrapPhase(phaseRadians);
	}

	public double shaftFrequencyHz() {
		return rpm / 60.0;
	}

	public double bladePassFrequencyHz() {
		return bladeCount * shaftFrequencyHz();
	}

	public double electricalFrequencyHz() {
		return motorPolePairs * shaftFrequencyHz();
	}

	private static double wrapPhase(double phase) {
		double wrapped = phase % (2.0 * Math.PI);
		return wrapped < 0.0 ? wrapped + 2.0 * Math.PI : wrapped;
	}

	private static void requireRange(double value, double minimum, double maximum, String name) {
		if (!Double.isFinite(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException(name + " must be in [" + minimum + ", " + maximum + "]");
		}
	}
}
