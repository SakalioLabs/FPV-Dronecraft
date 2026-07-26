package com.tenicana.dronecraft.acoustics.diffraction;

/**
 * Immutable complex pressure transfer used for coherent tonal path handling.
 */
public record ComplexPressure(double real, double imaginary) {
	public static final ComplexPressure ZERO = new ComplexPressure(0.0, 0.0);
	public static final ComplexPressure ONE = new ComplexPressure(1.0, 0.0);

	public ComplexPressure {
		if (!Double.isFinite(real + imaginary)) {
			throw new IllegalArgumentException("complex pressure must be finite");
		}
	}

	public static ComplexPressure imaginary(double value) {
		return new ComplexPressure(0.0, value);
	}

	public static ComplexPressure fromPolar(double magnitude, double phaseRadians) {
		if (!(magnitude >= 0.0)
				|| !Double.isFinite(magnitude + phaseRadians)) {
			throw new IllegalArgumentException("polar values must be finite");
		}
		return new ComplexPressure(
				magnitude * Math.cos(phaseRadians),
				magnitude * Math.sin(phaseRadians)
		);
	}

	public ComplexPressure add(ComplexPressure other) {
		return new ComplexPressure(real + other.real, imaginary + other.imaginary);
	}

	public ComplexPressure multiply(ComplexPressure other) {
		return new ComplexPressure(
				real * other.real - imaginary * other.imaginary,
				real * other.imaginary + imaginary * other.real
		);
	}

	public ComplexPressure scale(double factor) {
		if (!Double.isFinite(factor)) {
			throw new IllegalArgumentException("factor must be finite");
		}
		return new ComplexPressure(real * factor, imaginary * factor);
	}

	public ComplexPressure pow(double exponent) {
		if (!Double.isFinite(exponent)) {
			throw new IllegalArgumentException("exponent must be finite");
		}
		double magnitude = magnitude();
		if (magnitude == 0.0) {
			return exponent == 0.0 ? ONE : ZERO;
		}
		double angle = Math.atan2(imaginary, real);
		double poweredMagnitude = Math.pow(magnitude, exponent);
		return fromPolar(poweredMagnitude, angle * exponent);
	}

	public double magnitude() {
		return Math.hypot(real, imaginary);
	}

	public double energy() {
		return real * real + imaginary * imaginary;
	}
}
