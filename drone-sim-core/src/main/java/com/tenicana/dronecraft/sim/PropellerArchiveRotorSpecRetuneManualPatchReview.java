package com.tenicana.dronecraft.sim;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class PropellerArchiveRotorSpecRetuneManualPatchReview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Manual-Patch-Review-Packet";
	public static final String CAVEAT =
			"RotorSpec retune manual patch review material is generated only after validation acceptance; it lists current-vs-candidate fields for human review and never emits a DroneConfig patch, runtime coupling, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int FIELD_COUNT_PER_PRESET = 7;
	public static final int ROTOR_SPEC_PATCH_FIELD_COUNT_PER_PRESET = 5;
	public static final int GEOMETRY_REFERENCE_FIELD_COUNT_PER_PRESET = 2;
	public static final int REVIEW_ROW_COUNT = 14;
	public static final int SCENARIO_METRIC_ROW_COUNT = 12;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REVIEW_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<FieldDefinition> FIELDS = List.of(
			new FieldDefinition("rotor_radius_meters", "m", true, false),
			new FieldDefinition("blade_pitch_meters", "m", true, false),
			new FieldDefinition("blade_count", "count", true, false),
			new FieldDefinition("thrust_coefficient", "N/(rad/s)^2", true, false),
			new FieldDefinition("yaw_torque_per_thrust_meters", "m", true, false),
			new FieldDefinition("blade_chord_to_radius", "ratio", false, true),
			new FieldDefinition("blade_beta_radians", "rad", false, true)
	);

	private PropellerArchiveRotorSpecRetuneManualPatchReview() {
	}

	public record FieldDefinition(
			String fieldName,
			String unit,
			boolean rotorSpecPatchField,
			boolean geometryReferenceField
	) {
	}

	public record RetuneManualPatchReviewRow(
			String scenarioName,
			String presetName,
			String fieldName,
			double currentValue,
			double candidateValue,
			double absoluteDelta,
			double ratioCandidateOverCurrent,
			String unit,
			boolean rotorSpecPatchField,
			boolean geometryReferenceField,
			boolean manualPatchReviewAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetuneManualPatchReviewScenarioSummary(
			boolean validationAcceptanceReady,
			boolean manualPatchReviewAllowed,
			int manualPatchCandidatePresetCount,
			int reviewRowCount,
			int rotorSpecPatchFieldRowCount,
			int geometryReferenceFieldRowCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetuneManualPatchReviewScenario(
			String scenarioName,
			RetuneManualPatchReviewScenarioSummary summary
	) {
	}

	public record RetuneManualPatchReviewExtrema(
			int scenarioCount,
			int manualPatchReviewAllowedScenarioCount,
			int totalReviewRowCount,
			int maxReviewRowCount,
			int maxManualPatchCandidatePresetCount,
			int maxRotorSpecPatchFieldRowCount,
			int maxGeometryReferenceFieldRowCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneManualPatchReviewAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int reviewRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<RetuneManualPatchReviewRow> rows,
			List<RetuneManualPatchReviewScenario> scenarios,
			RetuneManualPatchReviewExtrema extrema
	) {
		public RetuneManualPatchReviewAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneManualPatchReviewAudit audit() {
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceAudit acceptance =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.audit();
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit currentRun =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit();
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit currentDelta =
				PropellerArchiveRotorSpecConfigDeltaReport.audit();
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit readyRun =
				readyValidationRunAudit();
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit readyDelta =
				readyConfigDeltaAudit();
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary currentAcceptance =
				acceptance(acceptance, "current_retune_validation_blocked");
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary missingAcceptance =
				acceptance(acceptance, "synthetic_ready_validation_results_missing");
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary acceptedAcceptance =
				acceptance(acceptance, "synthetic_ready_validation_all_pass");
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary failedAcceptance =
				acceptance(acceptance, "synthetic_ready_validation_one_failed");
		List<RetuneManualPatchReviewRow> currentRows =
				reviewRows("current_retune_validation_blocked", currentAcceptance, currentRun, currentDelta);
		List<RetuneManualPatchReviewRow> missingRows =
				reviewRows("synthetic_ready_validation_results_missing", missingAcceptance, readyRun, readyDelta);
		List<RetuneManualPatchReviewRow> acceptedRows =
				reviewRows("synthetic_ready_validation_all_pass", acceptedAcceptance, readyRun, readyDelta);
		List<RetuneManualPatchReviewRow> failedRows =
				reviewRows("synthetic_ready_validation_one_failed", failedAcceptance, readyRun, readyDelta);

		List<RetuneManualPatchReviewScenario> scenarios = List.of(
				scenario("current_retune_validation_blocked",
						currentAcceptance,
						currentRows,
						"current-retune-manual-patch-review-blocked"),
				scenario("synthetic_ready_validation_results_missing",
						missingAcceptance,
						missingRows,
						"synthetic-ready-validation-results-missing"),
				scenario("synthetic_ready_validation_all_pass",
						acceptedAcceptance,
						acceptedRows,
						"synthetic-ready-manual-patch-review-material"),
				scenario("synthetic_ready_validation_one_failed",
						failedAcceptance,
						failedRows,
						"synthetic-ready-validation-one-failed")
		);
		List<RetuneManualPatchReviewRow> rows = Stream.of(currentRows, missingRows, acceptedRows, failedRows)
				.flatMap(List::stream)
				.toList();
		return new RetuneManualPatchReviewAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				REVIEW_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static RetuneManualPatchReviewScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune manual patch review scenario: " + scenarioName));
	}

	public static RetuneManualPatchReviewRow row(
			String scenarioName,
			String presetName,
			String fieldName
	) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune manual patch review row: "
								+ scenarioName + "/" + presetName + "/" + fieldName));
	}

	public static List<FieldDefinition> fields() {
		return FIELDS;
	}

	private static RetuneManualPatchReviewScenario scenario(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary acceptance,
			List<RetuneManualPatchReviewRow> rows,
			String sourceRuntimeInfo
	) {
		return new RetuneManualPatchReviewScenario(
				scenarioName,
				summary(acceptance, rows, sourceRuntimeInfo)
		);
	}

	private static RetuneManualPatchReviewScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary acceptance,
			List<RetuneManualPatchReviewRow> rows,
			String sourceRuntimeInfo
	) {
		if (acceptance == null) {
			throw new IllegalArgumentException("acceptance summary must not be null.");
		}
		if (rows == null) {
			throw new IllegalArgumentException("rows must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		int patchRows = 0;
		int geometryRows = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		Set<String> presets = new HashSet<>();
		for (RetuneManualPatchReviewRow row : rows) {
			presets.add(row.presetName());
			if (row.rotorSpecPatchField()) {
				patchRows++;
			}
			if (row.geometryReferenceField()) {
				geometryRows++;
			}
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
		boolean reviewAllowed = acceptance.manualConfigPatchReviewAllowed() && !rows.isEmpty();
		return new RetuneManualPatchReviewScenarioSummary(
				acceptance.validationAcceptanceReady(),
				reviewAllowed,
				presets.size(),
				rows.size(),
				patchRows,
				geometryRows,
				config,
				runtime,
				gameplay,
				reviewAllowed ? "REVIEW_READY" : "BLOCKED",
				reviewAllowed ? "manual-config-patch-review-material-ready" : acceptance.message(),
				sourceRuntimeInfo
		);
	}

	private static List<RetuneManualPatchReviewRow> reviewRows(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary acceptance,
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit runAudit,
			PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit deltaAudit
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (acceptance == null || runAudit == null || deltaAudit == null) {
			throw new IllegalArgumentException("acceptance, runAudit, and deltaAudit are required.");
		}
		if (!acceptance.manualConfigPatchReviewAllowed()) {
			return List.of();
		}
		Set<String> acceptedPresets = acceptedPresetNames(runAudit);
		return deltaAudit.rows().stream()
				.filter(row -> acceptedPresets.contains(row.presetName()))
				.flatMap(delta -> FIELDS.stream().map(field -> row(scenarioName, delta, field)))
				.toList();
	}

	private static RetuneManualPatchReviewRow row(
			String scenarioName,
			PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow delta,
			FieldDefinition field
	) {
		double current = currentValue(delta, field.fieldName());
		double candidate = candidateValue(delta, field.fieldName());
		double absoluteDelta = Math.abs(candidate - current);
		return new RetuneManualPatchReviewRow(
				scenarioName,
				delta.presetName(),
				field.fieldName(),
				current,
				candidate,
				absoluteDelta,
				ratio(candidate, current),
				field.unit(),
				field.rotorSpecPatchField(),
				field.geometryReferenceField(),
				true,
				false,
				false,
				false,
				"REVIEW_READY",
				field.rotorSpecPatchField()
						? "manual-patch-review-candidate"
						: "geometry-reference-review-required"
		);
	}

	private static PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary acceptance(
			PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit readyValidationRunAudit() {
		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessAudit readiness =
				PropellerArchiveRotorSpecRetuneReadinessGate.audit(readyConfigDeltaAudit());
		return PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit(readiness);
	}

	private static PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit readyConfigDeltaAudit() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff =
				PropellerArchiveCompactReferenceHandoff.audit().scenarios().stream()
						.filter(scenario -> "acceptance_ready_reference_reviewed".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit table =
				PropellerArchiveCompactReferenceTable.audit(handoff);
		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit bridge =
				PropellerArchiveRotorSpecFitBridge.audit(table);
		return PropellerArchiveRotorSpecConfigDeltaReport.audit(bridge);
	}

	private static Set<String> acceptedPresetNames(
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit runAudit
	) {
		Set<String> presets = new HashSet<>();
		for (PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow row : runAudit.rows()) {
			if (row.validationRunPlanned()) {
				presets.add(row.presetName());
			}
		}
		return presets;
	}

	private static RetuneManualPatchReviewExtrema extrema(
			List<RetuneManualPatchReviewRow> rows,
			List<RetuneManualPatchReviewScenario> scenarios
	) {
		int manualScenarios = 0;
		int maxRows = 0;
		int maxPresets = 0;
		int maxPatchRows = 0;
		int maxGeometryRows = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneManualPatchReviewScenario scenario : scenarios) {
			RetuneManualPatchReviewScenarioSummary summary = scenario.summary();
			if (summary.manualPatchReviewAllowed()) {
				manualScenarios++;
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
			maxRows = Math.max(maxRows, summary.reviewRowCount());
			maxPresets = Math.max(maxPresets, summary.manualPatchCandidatePresetCount());
			maxPatchRows = Math.max(maxPatchRows, summary.rotorSpecPatchFieldRowCount());
			maxGeometryRows = Math.max(maxGeometryRows, summary.geometryReferenceFieldRowCount());
		}
		return new RetuneManualPatchReviewExtrema(
				scenarios.size(),
				manualScenarios,
				rows.size(),
				maxRows,
				maxPresets,
				maxPatchRows,
				maxGeometryRows,
				config,
				runtime,
				gameplay
		);
	}

	private static double currentValue(
			PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow delta,
			String fieldName
	) {
		return switch (fieldName) {
			case "rotor_radius_meters" -> delta.currentRadiusMeters();
			case "blade_pitch_meters" -> delta.currentBladePitchMeters();
			case "blade_count" -> delta.currentBladeCount();
			case "thrust_coefficient" -> delta.currentThrustCoefficient();
			case "yaw_torque_per_thrust_meters" -> delta.currentYawTorquePerThrustMeters();
			case "blade_chord_to_radius" -> delta.currentChordToRadius();
			case "blade_beta_radians" -> delta.currentBetaRadians();
			default -> throw new IllegalArgumentException("unknown manual patch review field: " + fieldName);
		};
	}

	private static double candidateValue(
			PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow delta,
			String fieldName
	) {
		return switch (fieldName) {
			case "rotor_radius_meters" -> delta.candidateRadiusMeters();
			case "blade_pitch_meters" -> delta.candidateBladePitchMeters();
			case "blade_count" -> delta.candidateBladeCount();
			case "thrust_coefficient" -> delta.candidateThrustCoefficient();
			case "yaw_torque_per_thrust_meters" -> delta.candidateYawTorquePerThrustMeters();
			case "blade_chord_to_radius" -> delta.candidateChordToRadius();
			case "blade_beta_radians" -> delta.candidateBetaRadians();
			default -> throw new IllegalArgumentException("unknown manual patch review field: " + fieldName);
		};
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
