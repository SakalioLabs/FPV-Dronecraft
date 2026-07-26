package com.tenicana.dronecraft.acoustics;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, versioned source-model calibration.
 */
public record AcousticSourceProfile(
		int schemaVersion,
		String id,
		AcousticProfileKey key,
		Calibration calibration,
		Validation validation,
		List<Evidence> evidence,
		OrderTrackedRotorModel.Parameters sourceModel,
		AxisymmetricSourceDirectivity.Parameters directivity
) {
	public static final int CURRENT_SCHEMA_VERSION = 1;
	private static final Pattern PROFILE_ID = Pattern.compile(
			"[a-z0-9][a-z0-9_.-]{0,63}:[a-z0-9][a-z0-9_./-]{0,127}"
	);

	public AcousticSourceProfile {
		if (schemaVersion != CURRENT_SCHEMA_VERSION) {
			throw new IllegalArgumentException(
					"unsupported acoustic profile schema version " + schemaVersion
			);
		}
		Objects.requireNonNull(id, "id");
		if (!PROFILE_ID.matcher(id).matches()) {
			throw new IllegalArgumentException(
					"id must be a lowercase namespaced identifier"
			);
		}
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(calibration, "calibration");
		Objects.requireNonNull(validation, "validation");
		evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
		if (evidence.isEmpty()) {
			throw new IllegalArgumentException(
					"at least one evidence record is required"
			);
		}
		Objects.requireNonNull(sourceModel, "sourceModel");
		Objects.requireNonNull(directivity, "directivity");
		if (calibration.measured()) {
			if (!validation.passesReleaseGate()) {
				throw new IllegalArgumentException(
						"measured profiles require evaluated RPM and angle "
								+ "holdouts with maximum error <= 3 dB"
				);
			}
			if (evidence.stream().anyMatch(item -> item.sha256().isEmpty())) {
				throw new IllegalArgumentException(
						"measured profile evidence requires SHA-256"
				);
			}
		} else if (validation.evaluated()) {
			throw new IllegalArgumentException(
					"unmeasured profiles cannot claim evaluated holdouts"
			);
		}
	}

	public static AcousticSourceProfile researchFallback() {
		return new AcousticSourceProfile(
				CURRENT_SCHEMA_VERSION,
				"fpvdrone:research_unity_fallback",
				new AcousticProfileKey("racing_quad", 4, 3, 127, 7),
				Calibration.researchFallback(),
				Validation.notEvaluated(),
				List.of(new Evidence(
						"FPV Dronecraft provisional order-domain source model",
						URI.create("https://github.com/SakalioLabs/FPV-Dronecraft"),
						"Project license; no measured coefficients embedded",
						"Uncalibrated fallback used when no exact profile key matches",
						""
				)),
				OrderTrackedRotorModel.Parameters.researchDefaults(),
				AxisymmetricSourceDirectivity.Parameters.omnidirectional()
		);
	}

	public record Calibration(
			boolean measured,
			boolean replacesLegacyRpmVolume,
			double motorPlaybackGainDb,
			double propellerPlaybackGainDb
	) {
		public Calibration {
			requireGainDb(motorPlaybackGainDb, "motorPlaybackGainDb");
			requireGainDb(propellerPlaybackGainDb, "propellerPlaybackGainDb");
			if (measured != replacesLegacyRpmVolume) {
				throw new IllegalArgumentException(
						"measured profiles must replace legacy RPM volume, "
								+ "and unmeasured profiles must not"
				);
			}
		}

		public static Calibration researchFallback() {
			return new Calibration(false, false, 0.0, 0.0);
		}

		public double motorPlaybackAmplitude() {
			return Math.pow(10.0, motorPlaybackGainDb / 20.0);
		}

		public double propellerPlaybackAmplitude() {
			return Math.pow(10.0, propellerPlaybackGainDb / 20.0);
		}
	}

	public record Validation(
			boolean evaluated,
			int unseenRpmSamples,
			int unseenAngleSamples,
			int orderSpectrumSamples,
			double maximumUnseenRpmErrorDb,
			double maximumUnseenAngleErrorDb,
			double maximumOrderSpectrumErrorDb
	) {
		public static final double MAXIMUM_RELEASE_ERROR_DB = 3.0;

		public Validation {
			if (evaluated) {
				if (unseenRpmSamples < 1
						|| unseenAngleSamples < 1
						|| orderSpectrumSamples < 1) {
					throw new IllegalArgumentException(
							"evaluated validation requires RPM, angle and "
									+ "order-spectrum holdout samples"
					);
				}
				requireError(maximumUnseenRpmErrorDb, "maximumUnseenRpmErrorDb");
				requireError(
						maximumUnseenAngleErrorDb,
						"maximumUnseenAngleErrorDb"
				);
				requireError(
						maximumOrderSpectrumErrorDb,
						"maximumOrderSpectrumErrorDb"
				);
			} else if (unseenRpmSamples != 0
					|| unseenAngleSamples != 0
					|| orderSpectrumSamples != 0
					|| maximumUnseenRpmErrorDb != 0.0
					|| maximumUnseenAngleErrorDb != 0.0
					|| maximumOrderSpectrumErrorDb != 0.0) {
				throw new IllegalArgumentException(
						"unevaluated validation must have zero counts and errors"
				);
			}
		}

		public static Validation notEvaluated() {
			return new Validation(false, 0, 0, 0, 0.0, 0.0, 0.0);
		}

		public boolean passesReleaseGate() {
			return evaluated
					&& maximumUnseenRpmErrorDb <= MAXIMUM_RELEASE_ERROR_DB
					&& maximumUnseenAngleErrorDb <= MAXIMUM_RELEASE_ERROR_DB
					&& maximumOrderSpectrumErrorDb <= MAXIMUM_RELEASE_ERROR_DB;
		}

		private static void requireError(double value, String name) {
			if (!Double.isFinite(value) || value < 0.0 || value > 120.0) {
				throw new IllegalArgumentException(
						name + " must be finite and in [0, 120] dB"
				);
			}
		}
	}

	public record Evidence(
			String citation,
			URI source,
			String license,
			String measurementConditions,
			String sha256
	) {
		private static final Pattern SHA256 =
				Pattern.compile("[0-9a-f]{64}");

		public Evidence {
			citation = requireText(citation, "citation");
			Objects.requireNonNull(source, "source");
			if (!source.isAbsolute()) {
				throw new IllegalArgumentException("evidence source must be absolute");
			}
			license = requireText(license, "license");
			measurementConditions = requireText(
					measurementConditions,
					"measurementConditions"
			);
			Objects.requireNonNull(sha256, "sha256");
			sha256 = sha256.trim().toLowerCase();
			if (!sha256.isEmpty() && !SHA256.matcher(sha256).matches()) {
				throw new IllegalArgumentException(
						"sha256 must be empty or 64 lowercase hexadecimal characters"
				);
			}
		}
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > 1_024) {
			throw new IllegalArgumentException(
					name + " must contain 1 to 1024 characters"
			);
		}
		return normalized;
	}

	private static void requireGainDb(double value, String name) {
		if (!Double.isFinite(value) || value < -120.0 || value > 24.0) {
			throw new IllegalArgumentException(name + " must be in [-120, 24] dB");
		}
	}
}
