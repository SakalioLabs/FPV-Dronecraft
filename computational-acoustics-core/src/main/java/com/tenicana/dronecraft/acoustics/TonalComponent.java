package com.tenicana.dronecraft.acoustics;

public record TonalComponent(
		Kind kind,
		int rotorIndex,
		int order,
		double frequencyHz,
		double linearAmplitude,
		double phaseRadians
) {
	public enum Kind {
		SHAFT,
		BLADE_PASS,
		ELECTRICAL,
		COGGING_CANDIDATE
	}

	public TonalComponent {
		if (kind == null) {
			throw new NullPointerException("kind");
		}
		if (rotorIndex < 0 || order < 1) {
			throw new IllegalArgumentException("rotorIndex must be non-negative and order must be positive");
		}
		if (!Double.isFinite(frequencyHz) || frequencyHz <= 0.0) {
			throw new IllegalArgumentException("frequencyHz must be finite and positive");
		}
		if (!Double.isFinite(linearAmplitude) || linearAmplitude < 0.0) {
			throw new IllegalArgumentException("linearAmplitude must be finite and non-negative");
		}
		if (!Double.isFinite(phaseRadians)) {
			throw new IllegalArgumentException("phaseRadians must be finite");
		}
	}
}
