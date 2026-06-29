package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneReadinessGate {
	public static final String SOURCE_ID = "User-Propeller-Archive-RotorSpec-Retune-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune readiness gates propeller-archive candidates before any DroneConfig patch; rows may become validation-required review material, but runtime coupling and gameplay auto-apply remain closed.";
	public static final double MAX_RADIUS_RATIO_ERROR_FOR_REVIEW = 0.02;
	public static final double MAX_BLADE_PITCH_RATIO_ERROR_FOR_REVIEW = 0.05;
	public static final int MAX_BLADE_COUNT_DELTA_FOR_REVIEW = 0;
	public static final double MAX_THRUST_COEFFICIENT_RATIO_ERROR_FOR_REVIEW = 0.25;
	public static final double MAX_YAW_TORQUE_RATIO_ERROR_FOR_REVIEW = 0.15;
	public static final double MAX_CHORD_RATIO_ERROR_FOR_REVIEW = 0.40;
	public static final double MAX_BETA_DELTA_RADIANS_FOR_REVIEW = 0.08;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int RETUNE_SAMPLE_COUNT = PropellerArchiveRotorSpecConfigDeltaReport.DELTA_SAMPLE_COUNT;
	public static final int RETUNE_METRIC_ROW_COUNT = 23;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ RETUNE_SAMPLE_COUNT * RETUNE_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetuneReadinessGate() {
	}

	public record RotorSpecRetuneReadinessRow(
			String presetName,
			boolean bridgeFullRotorSpecFitReady,
			boolean playableReferenceAllowed,
			boolean radiusWithinTolerance,
			boolean bladePitchWithinTolerance,
			boolean bladeCountMatches,
			boolean thrustCoefficientWithinTolerance,
			boolean yawTorqueWithinTolerance,
			boolean chordWithinTolerance,
			boolean betaWithinTolerance,
			int passedToleranceCount,
			int failedToleranceCount,
			double maxRatioError,
			double betaDeltaRadiansAbs,
			boolean directRetuneReviewReady,
			boolean calibrationValidationRequired,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String dominantBlocker,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record RotorSpecRetuneReadinessExtrema(
			int rowCount,
			int bridgeFullRotorSpecFitReadyCount,
			int directRetuneReviewReadyCount,
			int calibrationValidationRequiredCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int maxFailedToleranceCount,
			double maxRatioError,
			double maxBetaDeltaRadians
	) {
	}

	public record RotorSpecRetuneReadinessAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int retuneSampleCount,
			int retuneMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			double maxRadiusRatioErrorForReview,
			double maxBladePitchRatioErrorForReview,
			int maxBladeCountDeltaForReview,
			double maxThrustCoefficientRatioErrorForReview,
			double maxYawTorqueRatioErrorForReview,
			double maxChordRatioErrorForReview,
			double maxBetaDeltaRadiansForReview,
			List<RotorSpecRetuneReadinessRow> rows,
			RotorSpecRetuneReadinessExtrema extrema
	) {
		public RotorSpecRetuneReadinessAudit {
			rows = List.copyOf(rows);
		}
	}

	public static RotorSpecRetuneReadinessAudit audit() {
		return audit(PropellerArchiveRotorSpecConfigDeltaReport.audit());
	}

	public static RotorSpecRetuneReadinessAudit audit(
			PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit deltaAudit
	) {
		if (deltaAudit == null) {
			throw new IllegalArgumentException("deltaAudit must not be null.");
		}
		List<RotorSpecRetuneReadinessRow> rows = deltaAudit.rows()
				.stream()
				.map(PropellerArchiveRotorSpecRetuneReadinessGate::row)
				.toList();
		return new RotorSpecRetuneReadinessAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				RETUNE_SAMPLE_COUNT,
				RETUNE_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				MAX_RADIUS_RATIO_ERROR_FOR_REVIEW,
				MAX_BLADE_PITCH_RATIO_ERROR_FOR_REVIEW,
				MAX_BLADE_COUNT_DELTA_FOR_REVIEW,
				MAX_THRUST_COEFFICIENT_RATIO_ERROR_FOR_REVIEW,
				MAX_YAW_TORQUE_RATIO_ERROR_FOR_REVIEW,
				MAX_CHORD_RATIO_ERROR_FOR_REVIEW,
				MAX_BETA_DELTA_RADIANS_FOR_REVIEW,
				rows,
				extrema(rows)
		);
	}

	public static RotorSpecRetuneReadinessRow row(String presetName) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown RotorSpec retune row: " + presetName));
	}

	public static RotorSpecRetuneReadinessRow row(
			PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow delta
	) {
		if (delta == null) {
			throw new IllegalArgumentException("delta row must not be null.");
		}
		boolean radius = ratioWithin(delta.radiusRatioCandidateOverCurrent(), MAX_RADIUS_RATIO_ERROR_FOR_REVIEW);
		boolean pitch = ratioWithin(delta.bladePitchRatioCandidateOverCurrent(),
				MAX_BLADE_PITCH_RATIO_ERROR_FOR_REVIEW);
		boolean blades = Math.abs(delta.bladeCountDelta()) <= MAX_BLADE_COUNT_DELTA_FOR_REVIEW;
		boolean thrust = ratioWithin(delta.thrustCoefficientRatioCandidateOverCurrent(),
				MAX_THRUST_COEFFICIENT_RATIO_ERROR_FOR_REVIEW);
		boolean yaw = ratioWithin(delta.yawTorqueRatioCandidateOverCurrent(),
				MAX_YAW_TORQUE_RATIO_ERROR_FOR_REVIEW);
		boolean chord = ratioWithin(delta.chordRatioCandidateOverCurrent(), MAX_CHORD_RATIO_ERROR_FOR_REVIEW);
		boolean beta = Math.abs(delta.betaDeltaRadians()) <= MAX_BETA_DELTA_RADIANS_FOR_REVIEW;
		int passed = countTrue(radius, pitch, blades, thrust, yaw, chord, beta);
		int failed = 7 - passed;
		double maxRatioError = max(
				ratioError(delta.radiusRatioCandidateOverCurrent()),
				ratioError(delta.bladePitchRatioCandidateOverCurrent()),
				ratioError(delta.thrustCoefficientRatioCandidateOverCurrent()),
				ratioError(delta.yawTorqueRatioCandidateOverCurrent()),
				ratioError(delta.chordRatioCandidateOverCurrent())
		);
		double betaDeltaAbs = Math.abs(delta.betaDeltaRadians());
		boolean directReady = delta.bridgeFullRotorSpecFitReady()
				&& radius
				&& pitch
				&& blades
				&& thrust
				&& yaw
				&& chord
				&& beta;
		boolean validationRequired = directReady;
		String blocker = dominantBlocker(delta, radius, pitch, blades, thrust, yaw, chord, beta);
		return new RotorSpecRetuneReadinessRow(
				delta.presetName(),
				delta.bridgeFullRotorSpecFitReady(),
				delta.playableReferenceAllowed(),
				radius,
				pitch,
				blades,
				thrust,
				yaw,
				chord,
				beta,
				passed,
				failed,
				maxRatioError,
				betaDeltaAbs,
				directReady,
				validationRequired,
				false,
				false,
				false,
				blocker,
				nextRequiredAction(blocker),
				directReady ? "VALIDATION_REQUIRED" : "BLOCKED",
				directReady ? "rotor-spec-retune-validation-required-before-config-patch" : blocker
		);
	}

	private static RotorSpecRetuneReadinessExtrema extrema(List<RotorSpecRetuneReadinessRow> rows) {
		int bridge = 0;
		int direct = 0;
		int validation = 0;
		int patch = 0;
		int runtime = 0;
		int gameplay = 0;
		int maxFailed = 0;
		double maxRatio = 0.0;
		double maxBeta = 0.0;
		for (RotorSpecRetuneReadinessRow row : rows) {
			if (row.bridgeFullRotorSpecFitReady()) {
				bridge++;
			}
			if (row.directRetuneReviewReady()) {
				direct++;
			}
			if (row.calibrationValidationRequired()) {
				validation++;
			}
			if (row.configPatchAllowed()) {
				patch++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxFailed = Math.max(maxFailed, row.failedToleranceCount());
			maxRatio = Math.max(maxRatio, row.maxRatioError());
			maxBeta = Math.max(maxBeta, row.betaDeltaRadiansAbs());
		}
		return new RotorSpecRetuneReadinessExtrema(
				rows.size(),
				bridge,
				direct,
				validation,
				patch,
				runtime,
				gameplay,
				maxFailed,
				maxRatio,
				maxBeta
		);
	}

	private static String dominantBlocker(
			PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow delta,
			boolean radius,
			boolean pitch,
			boolean blades,
			boolean thrust,
			boolean yaw,
			boolean chord,
			boolean beta
	) {
		if (!delta.bridgeFullRotorSpecFitReady()) {
			if ("rotor-spec-geometry-reference-missing".equals(delta.message())) {
				return "rotor-spec-geometry-reference-missing";
			}
			return "rotor-spec-full-fit-gate-blocked";
		}
		if (!radius) {
			return "radius-large-delta-review-required";
		}
		if (!pitch) {
			return "blade-pitch-large-delta-review-required";
		}
		if (!blades) {
			return "blade-count-delta-review-required";
		}
		if (!thrust) {
			return "thrust-coefficient-large-delta-review-required";
		}
		if (!yaw) {
			return "yaw-torque-large-delta-review-required";
		}
		if (!chord) {
			return "blade-chord-large-delta-review-required";
		}
		if (!beta) {
			return "blade-beta-large-delta-review-required";
		}
		return "retune-candidate-ready-for-validation";
	}

	private static String nextRequiredAction(String blocker) {
		return switch (blocker) {
			case "rotor-spec-full-fit-gate-blocked" -> "complete-bridge-full-fit-before-retune-review";
			case "rotor-spec-geometry-reference-missing" -> "add-heavy-lift-geometry-source-or-surrogate";
			case "radius-large-delta-review-required" -> "review-prop-diameter-target-before-retune";
			case "blade-pitch-large-delta-review-required" -> "review-prop-pitch-target-before-retune";
			case "blade-count-delta-review-required" -> "review-blade-count-source-before-retune";
			case "thrust-coefficient-large-delta-review-required" ->
					"review-static-ct-anchor-or-current-thrust-coefficient-before-retune";
			case "yaw-torque-large-delta-review-required" ->
					"review-static-cp-anchor-or-current-yaw-torque-before-retune";
			case "blade-chord-large-delta-review-required" ->
					"review-geometry-chord-source-or-current-solidity-before-retune";
			case "blade-beta-large-delta-review-required" ->
					"review-geometry-beta-source-or-current-pitch-angle-before-retune";
			case "retune-candidate-ready-for-validation" ->
					"run-offline-hover-forward-flight-validation-before-config-patch";
			default -> "inspect-rotor-spec-retune-blocker";
		};
	}

	private static boolean ratioWithin(double ratio, double maxError) {
		return ratioError(ratio) <= maxError;
	}

	private static double ratioError(double ratio) {
		if (!Double.isFinite(ratio)) {
			return 1.0;
		}
		return Math.abs(ratio - 1.0);
	}

	private static int countTrue(boolean... values) {
		int count = 0;
		for (boolean value : values) {
			if (value) {
				count++;
			}
		}
		return count;
	}

	private static double max(double first, double... rest) {
		double value = first;
		for (double next : rest) {
			value = Math.max(value, next);
		}
		return value;
	}
}
