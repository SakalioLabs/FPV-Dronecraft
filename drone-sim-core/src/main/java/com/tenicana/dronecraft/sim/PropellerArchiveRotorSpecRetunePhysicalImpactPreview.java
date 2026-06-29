package com.tenicana.dronecraft.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PropellerArchiveRotorSpecRetunePhysicalImpactPreview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Physical-Impact-Preview-Packet";
	public static final String CAVEAT =
			"RotorSpec retune physical impact preview converts validation-accepted manual review fields into hover RPM, disk loading, tip-speed, and yaw-torque diagnostics only; it never emits a DroneConfig patch, runtime coupling, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int IMPACT_ROW_COUNT = 2;
	public static final int IMPACT_PACKET_METRIC_ROW_COUNT_PER_PRESET = 6;
	public static final int SCENARIO_METRIC_ROW_COUNT = 12;
	public static final int SUMMARY_ROW_COUNT = 11;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ IMPACT_ROW_COUNT * IMPACT_PACKET_METRIC_ROW_COUNT_PER_PRESET
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetunePhysicalImpactPreview() {
	}

	public record RetunePhysicalImpactRow(
			String scenarioName,
			String presetName,
			int rotorCount,
			double hoverThrustPerRotorNewtons,
			double currentDiskLoadingNewtonsPerSquareMeter,
			double candidateDiskLoadingNewtonsPerSquareMeter,
			double diskLoadingRatioCandidateOverCurrent,
			double currentHoverRpm,
			double candidateHoverRpm,
			double hoverRpmDelta,
			double hoverRpmRatioCandidateOverCurrent,
			double currentMaxRpm,
			double candidateMaxRpm,
			double currentHoverTipSpeedMetersPerSecond,
			double candidateHoverTipSpeedMetersPerSecond,
			double currentHoverYawTorqueNewtonMeters,
			double candidateHoverYawTorqueNewtonMeters,
			double hoverYawTorqueRatioCandidateOverCurrent,
			boolean manualPatchReviewAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetunePhysicalImpactScenarioSummary(
			boolean validationAcceptanceReady,
			boolean manualPatchReviewAllowed,
			boolean impactPreviewAvailable,
			int impactRowCount,
			double maxHoverRpmDeltaAbs,
			double maxHoverRpmRatioError,
			double maxDiskLoadingRatioError,
			double maxHoverYawTorqueRatioError,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetunePhysicalImpactScenario(
			String scenarioName,
			RetunePhysicalImpactScenarioSummary summary
	) {
	}

	public record RetunePhysicalImpactExtrema(
			int scenarioCount,
			int impactPreviewAvailableScenarioCount,
			int totalImpactRowCount,
			int maxImpactRowCount,
			double maxHoverRpmDeltaAbs,
			double maxHoverRpmRatioError,
			double maxDiskLoadingRatioError,
			double maxHoverYawTorqueRatioError,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetunePhysicalImpactAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int impactRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<RetunePhysicalImpactRow> rows,
			List<RetunePhysicalImpactScenario> scenarios,
			RetunePhysicalImpactExtrema extrema
	) {
		public RetunePhysicalImpactAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetunePhysicalImpactAudit audit() {
		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewAudit review =
				PropellerArchiveRotorSpecRetuneManualPatchReview.audit();
		List<RetunePhysicalImpactScenario> scenarios = review.scenarios().stream()
				.map(scenario -> scenario(review, scenario))
				.toList();
		List<RetunePhysicalImpactRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(review, scenario.scenarioName()).stream())
				.toList();
		return new RetunePhysicalImpactAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				IMPACT_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static RetunePhysicalImpactScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune physical impact scenario: " + scenarioName));
	}

	public static RetunePhysicalImpactRow row(String scenarioName, String presetName) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune physical impact row: " + scenarioName + "/" + presetName));
	}

	private static RetunePhysicalImpactScenario scenario(
			PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewAudit review,
			PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewScenario scenario
	) {
		List<RetunePhysicalImpactRow> rows = rowsForScenario(review, scenario.scenarioName());
		return new RetunePhysicalImpactScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetunePhysicalImpactScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewScenarioSummary reviewSummary,
			List<RetunePhysicalImpactRow> rows
	) {
		double maxRpmDelta = 0.0;
		double maxRpmRatioError = 0.0;
		double maxDiskLoadingRatioError = 0.0;
		double maxYawRatioError = 0.0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetunePhysicalImpactRow row : rows) {
			maxRpmDelta = Math.max(maxRpmDelta, Math.abs(row.hoverRpmDelta()));
			maxRpmRatioError = Math.max(maxRpmRatioError, ratioError(row.hoverRpmRatioCandidateOverCurrent()));
			maxDiskLoadingRatioError = Math.max(maxDiskLoadingRatioError,
					ratioError(row.diskLoadingRatioCandidateOverCurrent()));
			maxYawRatioError = Math.max(maxYawRatioError,
					ratioError(row.hoverYawTorqueRatioCandidateOverCurrent()));
			if (row.configPatchAllowed()) {
				config++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		boolean available = reviewSummary.manualPatchReviewAllowed() && !rows.isEmpty();
		return new RetunePhysicalImpactScenarioSummary(
				reviewSummary.validationAcceptanceReady(),
				reviewSummary.manualPatchReviewAllowed(),
				available,
				rows.size(),
				maxRpmDelta,
				maxRpmRatioError,
				maxDiskLoadingRatioError,
				maxYawRatioError,
				config,
				runtime,
				gameplay,
				available ? "READY" : "BLOCKED",
				available ? "retune-physical-impact-preview-ready" : reviewSummary.message(),
				reviewSummary.sourceRuntimeInfo()
		);
	}

	private static List<RetunePhysicalImpactRow> rowsForScenario(
			PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewAudit review,
			String scenarioName
	) {
		List<PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow> reviewRows =
				review.rows().stream()
						.filter(row -> scenarioName.equals(row.scenarioName()))
						.toList();
		if (reviewRows.isEmpty()) {
			return List.of();
		}
		return reviewRows.stream()
				.map(PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow::presetName)
				.distinct()
				.map(presetName -> row(scenarioName, presetName, reviewRows))
				.toList();
	}

	private static RetunePhysicalImpactRow row(
			String scenarioName,
			String presetName,
			List<PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow> reviewRows
	) {
		Map<String, PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow> fields =
				fieldsForPreset(presetName, reviewRows);
		DroneConfig config = PropellerArchiveRotorSpecConfigDeltaReport.configFor(presetName);
		RotorSpec current = config.rotors().get(0);
		RotorSpec candidate = current
				.withRadiusMeters(value(fields, "rotor_radius_meters", true))
				.withBladePitchMeters(value(fields, "blade_pitch_meters", true))
				.withBladeCount((int) Math.round(value(fields, "blade_count", true)))
				.withThrustCoefficient(value(fields, "thrust_coefficient", true))
				.withYawTorquePerThrustMeter(value(fields, "yaw_torque_per_thrust_meters", true));
		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double currentDiskLoading = diskLoading(current, hoverThrust);
		double candidateDiskLoading = diskLoading(candidate, hoverThrust);
		double currentHoverOmega = hoverOmega(current, hoverThrust);
		double candidateHoverOmega = hoverOmega(candidate, hoverThrust);
		double currentHoverRpm = rpm(currentHoverOmega);
		double candidateHoverRpm = rpm(candidateHoverOmega);
		double currentMaxRpm = rpm(current.maxOmegaRadiansPerSecond());
		double candidateMaxRpm = rpm(candidate.maxOmegaRadiansPerSecond());
		double currentHoverTipSpeed = currentHoverOmega * current.radiusMeters();
		double candidateHoverTipSpeed = candidateHoverOmega * candidate.radiusMeters();
		double currentYawTorque = hoverThrust * current.yawTorquePerThrustMeter();
		double candidateYawTorque = hoverThrust * candidate.yawTorquePerThrustMeter();
		return new RetunePhysicalImpactRow(
				scenarioName,
				presetName,
				config.rotors().size(),
				hoverThrust,
				currentDiskLoading,
				candidateDiskLoading,
				ratio(candidateDiskLoading, currentDiskLoading),
				currentHoverRpm,
				candidateHoverRpm,
				candidateHoverRpm - currentHoverRpm,
				ratio(candidateHoverRpm, currentHoverRpm),
				currentMaxRpm,
				candidateMaxRpm,
				currentHoverTipSpeed,
				candidateHoverTipSpeed,
				currentYawTorque,
				candidateYawTorque,
				ratio(candidateYawTorque, currentYawTorque),
				true,
				false,
				false,
				false,
				"READY",
				"retune-physical-impact-preview-ready"
		);
	}

	private static Map<String, PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow> fieldsForPreset(
			String presetName,
			List<PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow> reviewRows
	) {
		Map<String, PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow> fields =
				new HashMap<>();
		for (PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow row : reviewRows) {
			if (presetName.equals(row.presetName())) {
				fields.put(row.fieldName(), row);
			}
		}
		return fields;
	}

	private static double value(
			Map<String, PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow> fields,
			String fieldName,
			boolean candidate
	) {
		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow row = fields.get(fieldName);
		if (row == null) {
			throw new IllegalArgumentException("missing manual patch review field: " + fieldName);
		}
		return candidate ? row.candidateValue() : row.currentValue();
	}

	private static RetunePhysicalImpactExtrema extrema(
			List<RetunePhysicalImpactRow> rows,
			List<RetunePhysicalImpactScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		double maxRpmDelta = 0.0;
		double maxRpmRatio = 0.0;
		double maxDiskLoading = 0.0;
		double maxYawRatio = 0.0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetunePhysicalImpactScenario scenario : scenarios) {
			RetunePhysicalImpactScenarioSummary summary = scenario.summary();
			if (summary.impactPreviewAvailable()) {
				available++;
			}
			if (summary.configPatchAllowedCount() > 0) {
				config++;
			}
			if (summary.runtimeCouplingAllowedCount() > 0) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowedCount() > 0) {
				gameplay++;
			}
			maxRows = Math.max(maxRows, summary.impactRowCount());
			maxRpmDelta = Math.max(maxRpmDelta, summary.maxHoverRpmDeltaAbs());
			maxRpmRatio = Math.max(maxRpmRatio, summary.maxHoverRpmRatioError());
			maxDiskLoading = Math.max(maxDiskLoading, summary.maxDiskLoadingRatioError());
			maxYawRatio = Math.max(maxYawRatio, summary.maxHoverYawTorqueRatioError());
		}
		return new RetunePhysicalImpactExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxRpmDelta,
				maxRpmRatio,
				maxDiskLoading,
				maxYawRatio,
				config,
				runtime,
				gameplay
		);
	}

	private static double diskLoading(RotorSpec rotor, double hoverThrustNewtons) {
		double diskArea = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		return hoverThrustNewtons / Math.max(1.0e-12, diskArea);
	}

	private static double hoverOmega(RotorSpec rotor, double hoverThrustNewtons) {
		return Math.sqrt(hoverThrustNewtons / Math.max(1.0e-12, rotor.thrustCoefficient()));
	}

	private static double rpm(double omegaRadiansPerSecond) {
		return omegaRadiansPerSecond * 60.0 / (2.0 * Math.PI);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static double ratioError(double ratio) {
		if (!Double.isFinite(ratio)) {
			return 1.0;
		}
		return Math.abs(ratio - 1.0);
	}
}
