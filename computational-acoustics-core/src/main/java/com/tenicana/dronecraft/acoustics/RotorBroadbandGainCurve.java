package com.tenicana.dronecraft.acoustics;

import java.util.List;
import java.util.Objects;

/** Independent, profile-owned low/mid/high energy gains over rotor RPM. */
public final class RotorBroadbandGainCurve {
	private static final AcousticBands UNITY_GAINS = new AcousticBands(1.0, 1.0, 1.0);
	private static final RotorBroadbandGainCurve UNITY = new RotorBroadbandGainCurve(
			List.of(new Anchor(1.0, 0.0, 0.0, 0.0))
	);

	private final List<Anchor> anchors;
	private final boolean unity;

	public RotorBroadbandGainCurve(List<Anchor> anchors) {
		this.anchors = List.copyOf(Objects.requireNonNull(anchors, "anchors"));
		if (this.anchors.isEmpty()) {
			throw new IllegalArgumentException("at least one broadband anchor is required");
		}
		double previousRpm = 0.0;
		boolean allUnity = true;
		for (Anchor anchor : this.anchors) {
			if (anchor.rpm() <= previousRpm) {
				throw new IllegalArgumentException("broadband RPM anchors must be strictly increasing");
			}
			previousRpm = anchor.rpm();
			allUnity &= anchor.lowGainDb() == 0.0
					&& anchor.midGainDb() == 0.0 && anchor.highGainDb() == 0.0;
		}
		unity = allUnity;
	}

	public static RotorBroadbandGainCurve unity() {
		return UNITY;
	}

	public List<Anchor> anchors() {
		return anchors;
	}

	public boolean isUnity() {
		return unity;
	}

	/** Gains are linear energies; interpolation is linear in dB and log RPM. */
	public AcousticBands evaluate(double rpm) {
		if (!Double.isFinite(rpm) || rpm < 0.0) {
			throw new IllegalArgumentException("rpm must be finite and non-negative");
		}
		if (unity) {
			return UNITY_GAINS;
		}
		if (rpm <= anchors.getFirst().rpm()) {
			return anchors.getFirst().gain();
		}
		if (rpm >= anchors.getLast().rpm()) {
			return anchors.getLast().gain();
		}
		for (int index = 1; index < anchors.size(); index++) {
			Anchor upper = anchors.get(index);
			if (rpm <= upper.rpm()) {
				Anchor lower = anchors.get(index - 1);
				double fraction = logRatio(rpm, lower.rpm()) / logRatio(upper.rpm(), lower.rpm());
				return gain(
						interpolate(lower.lowGainDb(), upper.lowGainDb(), fraction),
						interpolate(lower.midGainDb(), upper.midGainDb(), fraction),
						interpolate(lower.highGainDb(), upper.highGainDb(), fraction)
				);
			}
		}
		throw new IllegalStateException("broadband anchor search failed");
	}

	private static double logRatio(double upper, double lower) {
		double relative = (upper - lower) / lower;
		return Double.isFinite(relative) ? Math.log1p(relative) : Math.log(upper) - Math.log(lower);
	}

	private static double interpolate(double lower, double upper, double fraction) {
		return lower + fraction * (upper - lower);
	}

	private static AcousticBands gain(double lowDb, double midDb, double highDb) {
		return new AcousticBands(
				Math.pow(10.0, lowDb / 10.0),
				Math.pow(10.0, midDb / 10.0),
				Math.pow(10.0, highDb / 10.0)
		);
	}

	public record Anchor(double rpm, double lowGainDb, double midGainDb, double highGainDb) {
		public Anchor {
			if (!Double.isFinite(rpm) || rpm <= 0.0) {
				throw new IllegalArgumentException("anchor rpm must be finite and positive");
			}
			requireGainDb(lowGainDb, "lowGainDb");
			requireGainDb(midGainDb, "midGainDb");
			requireGainDb(highGainDb, "highGainDb");
		}

		private AcousticBands gain() {
			return RotorBroadbandGainCurve.gain(lowGainDb, midGainDb, highGainDb);
		}
	}

	private static void requireGainDb(double value, String name) {
		if (!Double.isFinite(value) || value < -120.0 || value > 60.0) {
			throw new IllegalArgumentException(name + " must be in [-120, 60] dB");
		}
	}
}
