package com.tenicana.dronecraft.acoustics;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Sparse, independent BPF-harmonic amplitude gains over rotor RPM. */
public final class RotorHarmonicGainCurve {
	private static final RotorHarmonicGainCurve UNITY = new RotorHarmonicGainCurve(Map.of());

	private final Map<Integer, List<Anchor>> anchors;
	private final boolean unity;

	public RotorHarmonicGainCurve(Map<Integer, List<Anchor>> anchors) {
		Objects.requireNonNull(anchors, "anchors");
		Map<Integer, List<Anchor>> copied = new TreeMap<>();
		boolean allUnity = true;
		for (Map.Entry<Integer, List<Anchor>> entry : anchors.entrySet()) {
			int harmonic = Objects.requireNonNull(entry.getKey(), "harmonic");
			requireHarmonic(harmonic);
			List<Anchor> harmonicAnchors = List.copyOf(Objects.requireNonNull(entry.getValue(), "harmonic anchors"));
			if (harmonicAnchors.isEmpty()) {
				throw new IllegalArgumentException("each specified harmonic requires at least one anchor");
			}
			double previousRpm = 0.0;
			for (Anchor anchor : harmonicAnchors) {
				if (anchor.rpm() <= previousRpm) {
					throw new IllegalArgumentException("harmonic RPM anchors must be strictly increasing");
				}
				previousRpm = anchor.rpm();
				allUnity &= anchor.gainDb() == 0.0;
			}
			copied.put(harmonic, harmonicAnchors);
		}
		this.anchors = Collections.unmodifiableMap(copied);
		unity = allUnity;
	}

	public static RotorHarmonicGainCurve unity() {
		return UNITY;
	}

	public Map<Integer, List<Anchor>> anchors() {
		return anchors;
	}

	public boolean isUnity() {
		return unity;
	}

	/** Linear amplitude gain, interpolated in dB and log RPM; unspecified orders use unity. */
	public double evaluate(int harmonic, double rpm) {
		requireHarmonic(harmonic);
		if (!Double.isFinite(rpm) || rpm < 0.0) {
			throw new IllegalArgumentException("rpm must be finite and non-negative");
		}
		List<Anchor> harmonicAnchors = anchors.get(harmonic);
		if (unity || harmonicAnchors == null) {
			return 1.0;
		}
		if (rpm <= harmonicAnchors.getFirst().rpm()) {
			return harmonicAnchors.getFirst().gain();
		}
		if (rpm >= harmonicAnchors.getLast().rpm()) {
			return harmonicAnchors.getLast().gain();
		}
		for (int index = 1; index < harmonicAnchors.size(); index++) {
			Anchor upper = harmonicAnchors.get(index);
			if (rpm <= upper.rpm()) {
				Anchor lower = harmonicAnchors.get(index - 1);
				double fraction = logRatio(rpm, lower.rpm()) / logRatio(upper.rpm(), lower.rpm());
				return amplitude(lower.gainDb() + fraction * (upper.gainDb() - lower.gainDb()));
			}
		}
		throw new IllegalStateException("harmonic anchor search failed");
	}

	private static double logRatio(double upper, double lower) {
		double relative = (upper - lower) / lower;
		return Double.isFinite(relative) ? Math.log1p(relative) : Math.log(upper) - Math.log(lower);
	}

	private static double amplitude(double gainDb) {
		return Math.pow(10.0, gainDb / 20.0);
	}

	private static void requireHarmonic(int harmonic) {
		if (harmonic < 1 || harmonic > 64) {
			throw new IllegalArgumentException("harmonic must be in [1, 64]");
		}
	}

	public record Anchor(double rpm, double gainDb) {
		public Anchor {
			if (!Double.isFinite(rpm) || rpm <= 0.0) {
				throw new IllegalArgumentException("anchor rpm must be finite and positive");
			}
			if (!Double.isFinite(gainDb) || gainDb < -120.0 || gainDb > 60.0) {
				throw new IllegalArgumentException("gainDb must be in [-120, 60] dB");
			}
		}

		private double gain() {
			return amplitude(gainDb);
		}
	}
}
