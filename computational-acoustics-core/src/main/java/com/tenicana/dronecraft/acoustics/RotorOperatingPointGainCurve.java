package com.tenicana.dronecraft.acoustics;

import java.util.List;
import java.util.Objects;

/**
 * Profile-owned acoustic gains over rotor RPM. Gains are interpolated in
 * log-RPM and dB space because measured rotor, motor, and broadband trends do
 * not share a universal power law.
 */
public final class RotorOperatingPointGainCurve {
	private static final double MINIMUM_GAIN_DB = -120.0;
	private static final double MAXIMUM_GAIN_DB = 60.0;
	private static final RotorOperatingPointGainCurve UNITY =
			new RotorOperatingPointGainCurve(List.of(
					new Anchor(1.0, 0.0, 0.0, 0.0)
			));

	private final List<Anchor> anchors;

	public RotorOperatingPointGainCurve(List<Anchor> anchors) {
		this.anchors = List.copyOf(Objects.requireNonNull(anchors, "anchors"));
		if (this.anchors.isEmpty()) {
			throw new IllegalArgumentException("at least one operating-point anchor is required");
		}
		double previousRpm = 0.0;
		for (Anchor anchor : this.anchors) {
			if (anchor.rpm() <= previousRpm) {
				throw new IllegalArgumentException(
						"operating-point RPM anchors must be strictly increasing"
				);
			}
			previousRpm = anchor.rpm();
		}
	}

	public static RotorOperatingPointGainCurve unity() {
		return UNITY;
	}

	public List<Anchor> anchors() {
		return anchors;
	}

	public Gain evaluate(double rpm) {
		if (!Double.isFinite(rpm) || rpm < 0.0) {
			throw new IllegalArgumentException("rpm must be finite and non-negative");
		}
		if (anchors.size() == 1 || rpm <= anchors.getFirst().rpm()) {
			return anchors.getFirst().gain();
		}
		if (rpm >= anchors.getLast().rpm()) {
			return anchors.getLast().gain();
		}
		for (int index = 1; index < anchors.size(); index++) {
			Anchor upper = anchors.get(index);
			if (rpm <= upper.rpm()) {
				Anchor lower = anchors.get(index - 1);
				double fraction = Math.log(rpm / lower.rpm())
						/ Math.log(upper.rpm() / lower.rpm());
				return Gain.fromDb(
						interpolate(lower.rotorTonalGainDb(), upper.rotorTonalGainDb(), fraction),
						interpolate(lower.motorTonalGainDb(), upper.motorTonalGainDb(), fraction),
						interpolate(lower.broadbandGainDb(), upper.broadbandGainDb(), fraction)
				);
			}
		}
		throw new IllegalStateException("operating-point anchor search failed");
	}

	private static double interpolate(double lower, double upper, double fraction) {
		return lower + fraction * (upper - lower);
	}

	public record Anchor(
			double rpm,
			double rotorTonalGainDb,
			double motorTonalGainDb,
			double broadbandGainDb
	) {
		public Anchor {
			if (!Double.isFinite(rpm) || rpm <= 0.0) {
				throw new IllegalArgumentException("anchor rpm must be finite and positive");
			}
			requireGainDb(rotorTonalGainDb, "rotorTonalGainDb");
			requireGainDb(motorTonalGainDb, "motorTonalGainDb");
			requireGainDb(broadbandGainDb, "broadbandGainDb");
		}

		private Gain gain() {
			return Gain.fromDb(
					rotorTonalGainDb,
					motorTonalGainDb,
					broadbandGainDb
			);
		}
	}

	public record Gain(
			double rotorTonalAmplitude,
			double motorTonalAmplitude,
			double broadbandEnergy
	) {
		private static Gain fromDb(
				double rotorTonalGainDb,
				double motorTonalGainDb,
				double broadbandGainDb
		) {
			return new Gain(
					Math.pow(10.0, rotorTonalGainDb / 20.0),
					Math.pow(10.0, motorTonalGainDb / 20.0),
					Math.pow(10.0, broadbandGainDb / 10.0)
			);
		}

		public Gain {
			requirePositiveFinite(rotorTonalAmplitude, "rotorTonalAmplitude");
			requirePositiveFinite(motorTonalAmplitude, "motorTonalAmplitude");
			requirePositiveFinite(broadbandEnergy, "broadbandEnergy");
		}
	}

	private static void requireGainDb(double value, String name) {
		if (!Double.isFinite(value)
				|| value < MINIMUM_GAIN_DB
				|| value > MAXIMUM_GAIN_DB) {
			throw new IllegalArgumentException(name + " must be in [-120, 60] dB");
		}
	}

	private static void requirePositiveFinite(double value, String name) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(name + " must be finite and positive");
		}
	}
}
