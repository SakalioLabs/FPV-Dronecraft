package com.tenicana.dronecraft.acoustics;

/**
 * Linear energy in three deliberately broad real-time bands.
 */
public record AcousticBands(double low, double mid, double high) {
	public static final AcousticBands SILENT = new AcousticBands(0.0, 0.0, 0.0);

	public AcousticBands {
		requireEnergy(low, "low");
		requireEnergy(mid, "mid");
		requireEnergy(high, "high");
	}

	public AcousticBands add(AcousticBands other) {
		return new AcousticBands(low + other.low, mid + other.mid, high + other.high);
	}

	public AcousticBands multiply(double gain) {
		if (!Double.isFinite(gain) || gain < 0.0) {
			throw new IllegalArgumentException("gain must be finite and non-negative");
		}
		return new AcousticBands(low * gain, mid * gain, high * gain);
	}

	public AcousticBands multiply(AcousticBands other) {
		return new AcousticBands(low * other.low, mid * other.mid, high * other.high);
	}

	public AcousticBands map(DoubleBandOperator operator) {
		return new AcousticBands(
				operator.apply(low),
				operator.apply(mid),
				operator.apply(high)
		);
	}

	public double totalEnergy() {
		return low + mid + high;
	}

	@FunctionalInterface
	public interface DoubleBandOperator {
		double apply(double value);
	}

	private static void requireEnergy(double value, String name) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(name + " energy must be finite and non-negative");
		}
	}
}
