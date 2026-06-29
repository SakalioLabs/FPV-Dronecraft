package com.tenicana.dronecraft.sim;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Manual-Review-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate manual review material is generated only after cold-air derate validation acceptance; it lists max-RPM derate targets and post-derate budgets for human review while keeping playable reference export, DroneConfig patching, runtime coupling, and gameplay auto-apply closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int FIELD_COUNT_PER_TARGET = 7;
	public static final int RPM_CONTROL_REVIEW_FIELD_COUNT_PER_TARGET = 3;
	public static final int PHYSICS_BUDGET_REVIEW_FIELD_COUNT_PER_TARGET = 4;
	public static final int REVIEW_ROW_COUNT = 14;
	public static final int SCENARIO_METRIC_ROW_COUNT = 18;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REVIEW_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<FieldDefinition> FIELDS = List.of(
			new FieldDefinition("target_max_tip_mach", "Mach", true, false),
			new FieldDefinition("target_max_rpm_scale", "ratio", true, false),
			new FieldDefinition("required_max_rpm_derate_percent", "percent", true, false),
			new FieldDefinition("post_derate_thrust_loss_percent", "percent", false, true),
			new FieldDefinition("post_derate_load_factor", "ratio", false, true),
			new FieldDefinition("post_derate_reaction_torque_scale", "ratio", false, true),
			new FieldDefinition("post_derate_vibration_proxy", "proxy", false, true)
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview() {
	}

	public record FieldDefinition(
			String fieldName,
			String unit,
			boolean rpmControlReviewField,
			boolean physicsBudgetReviewField
	) {
	}

	public record DerateManualReviewRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String fieldName,
			double beforeDerateValue,
			double reviewValue,
			double absoluteDelta,
			double ratioReviewOverBefore,
			String unit,
			String limitingBudget,
			boolean derateTargetWithinBudget,
			boolean rpmControlReviewField,
			boolean physicsBudgetReviewField,
			boolean manualDerateReviewAllowed,
			boolean playableReferenceAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DerateManualReviewScenarioSummary(
			boolean derateValidationAcceptanceReady,
			boolean manualDerateReviewAllowed,
			int manualDerateReviewCandidateCount,
			int reviewRowCount,
			int rpmControlReviewFieldRowCount,
			int physicsBudgetReviewFieldRowCount,
			int derateTargetWithinBudgetCount,
			double maxRequiredMaxRpmDeratePercent,
			double minTargetMaxRpmScale,
			double maxPostDerateThrustLossPercent,
			double maxPostDerateReactionTorqueScale,
			double maxPostDerateVibrationProxy,
			int playableReferenceAllowedCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message
	) {
	}

	public record DerateManualReviewScenario(
			String scenarioName,
			DerateManualReviewScenarioSummary summary
	) {
	}

	public record DerateManualReviewExtrema(
			int scenarioCount,
			int manualDerateReviewAllowedScenarioCount,
			int totalReviewRowCount,
			int maxReviewRowCount,
			int maxManualDerateReviewCandidateCount,
			int maxRpmControlReviewFieldRowCount,
			int maxPhysicsBudgetReviewFieldRowCount,
			double maxRequiredMaxRpmDeratePercent,
			double minTargetMaxRpmScale,
			double maxPostDerateThrustLossPercent,
			double maxPostDerateReactionTorqueScale,
			double maxPostDerateVibrationProxy,
			int playableReferenceAllowedScenarioCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record DerateManualReviewAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int reviewRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<DerateManualReviewRow> rows,
			List<DerateManualReviewScenario> scenarios,
			DerateManualReviewExtrema extrema
	) {
		public DerateManualReviewAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static DerateManualReviewAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationAcceptanceAudit acceptance =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.audit();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan
				.RetuneAmbientCompressibilityDerateAudit derate =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.audit();

		List<DerateManualReviewRow> currentRows = reviewRows(
				"current_derate_validation_blocked",
				acceptance(acceptance, "current_derate_validation_blocked"),
				derate,
				"current_retune_validation_blocked");
		List<DerateManualReviewRow> missingRows = reviewRows(
				"synthetic_derate_validation_results_missing",
				acceptance(acceptance, "synthetic_derate_validation_results_missing"),
				derate,
				"synthetic_ready_validation_all_pass");
		List<DerateManualReviewRow> acceptedRows = reviewRows(
				"synthetic_derate_validation_all_pass",
				acceptance(acceptance, "synthetic_derate_validation_all_pass"),
				derate,
				"synthetic_ready_validation_all_pass");
		List<DerateManualReviewRow> failedRows = reviewRows(
				"synthetic_derate_validation_one_failed",
				acceptance(acceptance, "synthetic_derate_validation_one_failed"),
				derate,
				"synthetic_ready_validation_all_pass");

		List<DerateManualReviewScenario> scenarios = List.of(
				scenario("current_derate_validation_blocked",
						acceptance(acceptance, "current_derate_validation_blocked"),
						currentRows),
				scenario("synthetic_derate_validation_results_missing",
						acceptance(acceptance, "synthetic_derate_validation_results_missing"),
						missingRows),
				scenario("synthetic_derate_validation_all_pass",
						acceptance(acceptance, "synthetic_derate_validation_all_pass"),
						acceptedRows),
				scenario("synthetic_derate_validation_one_failed",
						acceptance(acceptance, "synthetic_derate_validation_one_failed"),
						failedRows)
		);
		List<DerateManualReviewRow> rows = Stream.of(currentRows, missingRows, acceptedRows, failedRows)
				.flatMap(List::stream)
				.toList();
		return new DerateManualReviewAudit(
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

	public static DerateManualReviewScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate manual review scenario: " + scenarioName));
	}

	public static DerateManualReviewRow row(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String fieldName
	) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.ambientCaseName().equals(ambientCaseName)
						&& row.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate manual review row: "
								+ scenarioName + "/" + presetName + "/" + ambientCaseName
								+ "/" + fieldName));
	}

	public static List<FieldDefinition> fields() {
		return FIELDS;
	}

	private static DerateManualReviewScenario scenario(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
					.DerateValidationAcceptanceSummary acceptance,
			List<DerateManualReviewRow> rows
	) {
		return new DerateManualReviewScenario(
				scenarioName,
				summary(acceptance, rows)
		);
	}

	private static DerateManualReviewScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
					.DerateValidationAcceptanceSummary acceptance,
			List<DerateManualReviewRow> rows
	) {
		if (acceptance == null) {
			throw new IllegalArgumentException("acceptance summary must not be null.");
		}
		if (rows == null) {
			throw new IllegalArgumentException("rows must not be null.");
		}
		Set<String> candidates = new HashSet<>();
		Set<String> withinBudgetTargets = new HashSet<>();
		int rpmControl = 0;
		int budget = 0;
		double maxDerate = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (DerateManualReviewRow row : rows) {
			candidates.add(row.presetName() + "/" + row.ambientCaseName());
			if (row.derateTargetWithinBudget()) {
				withinBudgetTargets.add(row.presetName() + "/" + row.ambientCaseName());
			}
			if (row.rpmControlReviewField()) {
				rpmControl++;
			}
			if (row.physicsBudgetReviewField()) {
				budget++;
			}
			if ("required_max_rpm_derate_percent".equals(row.fieldName())) {
				maxDerate = Math.max(maxDerate, row.reviewValue());
			}
			if ("target_max_rpm_scale".equals(row.fieldName())) {
				minScale = Math.min(minScale, row.reviewValue());
			}
			if ("post_derate_thrust_loss_percent".equals(row.fieldName())) {
				maxLoss = Math.max(maxLoss, row.reviewValue());
			}
			if ("post_derate_reaction_torque_scale".equals(row.fieldName())) {
				maxTorque = Math.max(maxTorque, row.reviewValue());
			}
			if ("post_derate_vibration_proxy".equals(row.fieldName())) {
				maxVibration = Math.max(maxVibration, row.reviewValue());
			}
			if (row.playableReferenceAllowed()) {
				playable++;
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
		boolean reviewAllowed = acceptance.manualDerateReviewAllowed() && !rows.isEmpty();
		return new DerateManualReviewScenarioSummary(
				acceptance.derateValidationAcceptanceReady(),
				reviewAllowed,
				candidates.size(),
				rows.size(),
				rpmControl,
				budget,
				withinBudgetTargets.size(),
				maxDerate,
				rows.isEmpty() ? 0.0 : minScale,
				maxLoss,
				maxTorque,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay,
				reviewAllowed ? "REVIEW_READY" : "BLOCKED",
				reviewAllowed ? "manual-cold-air-derate-review-material-ready" : acceptance.message()
		);
	}

	private static List<DerateManualReviewRow> reviewRows(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
					.DerateValidationAcceptanceSummary acceptance,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan
					.RetuneAmbientCompressibilityDerateAudit derate,
			String deratePlanScenarioName
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (deratePlanScenarioName == null || deratePlanScenarioName.isBlank()) {
			throw new IllegalArgumentException("deratePlanScenarioName must not be blank.");
		}
		if (acceptance == null || derate == null) {
			throw new IllegalArgumentException("acceptance and derate audit are required.");
		}
		if (!acceptance.manualDerateReviewAllowed()) {
			return List.of();
		}
		return derate.rows().stream()
				.filter(row -> deratePlanScenarioName.equals(row.scenarioName()))
				.flatMap(derateRow -> FIELDS.stream().map(field -> row(scenarioName, derateRow, field)))
				.toList();
	}

	private static DerateManualReviewRow row(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan
					.RetuneAmbientCompressibilityDerateRow derate,
			FieldDefinition field
	) {
		double before = beforeDerateValue(derate, field.fieldName());
		double review = reviewValue(derate, field.fieldName());
		return new DerateManualReviewRow(
				scenarioName,
				derate.presetName(),
				derate.ambientCaseName(),
				field.fieldName(),
				before,
				review,
				Math.abs(review - before),
				ratio(review, before),
				field.unit(),
				derate.limitingBudget(),
				derate.derateTargetWithinBudget(),
				field.rpmControlReviewField(),
				field.physicsBudgetReviewField(),
				true,
				false,
				false,
				false,
				false,
				"REVIEW_READY",
				field.rpmControlReviewField()
						? "manual-max-rpm-derate-review-candidate"
						: "post-derate-budget-review-required"
		);
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
			.DerateValidationAcceptanceSummary acceptance(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
					.DerateValidationAcceptanceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static DerateManualReviewExtrema extrema(
			List<DerateManualReviewRow> rows,
			List<DerateManualReviewScenario> scenarios
	) {
		int reviewScenarios = 0;
		int maxRows = 0;
		int maxCandidates = 0;
		int maxRpm = 0;
		int maxBudget = 0;
		double maxDerate = 0.0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxLoss = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (DerateManualReviewScenario scenario : scenarios) {
			DerateManualReviewScenarioSummary summary = scenario.summary();
			if (summary.manualDerateReviewAllowed()) {
				reviewScenarios++;
			}
			if (summary.playableReferenceAllowedCount() > 0) {
				playable++;
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
			maxCandidates = Math.max(maxCandidates, summary.manualDerateReviewCandidateCount());
			maxRpm = Math.max(maxRpm, summary.rpmControlReviewFieldRowCount());
			maxBudget = Math.max(maxBudget, summary.physicsBudgetReviewFieldRowCount());
			if (summary.manualDerateReviewAllowed()) {
				maxDerate = Math.max(maxDerate, summary.maxRequiredMaxRpmDeratePercent());
				minScale = Math.min(minScale, summary.minTargetMaxRpmScale());
				maxLoss = Math.max(maxLoss, summary.maxPostDerateThrustLossPercent());
				maxTorque = Math.max(maxTorque, summary.maxPostDerateReactionTorqueScale());
				maxVibration = Math.max(maxVibration, summary.maxPostDerateVibrationProxy());
			}
		}
		return new DerateManualReviewExtrema(
				scenarios.size(),
				reviewScenarios,
				rows.size(),
				maxRows,
				maxCandidates,
				maxRpm,
				maxBudget,
				maxDerate,
				rows.isEmpty() ? 0.0 : minScale,
				maxLoss,
				maxTorque,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay
		);
	}

	private static double beforeDerateValue(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateRow derate,
			String fieldName
	) {
		return switch (fieldName) {
			case "target_max_tip_mach" -> derate.candidateMaxTipMach();
			case "target_max_rpm_scale" -> 1.0;
			case "required_max_rpm_derate_percent" -> 0.0;
			case "post_derate_thrust_loss_percent" -> derate.candidateMaxThrustLossPercent();
			case "post_derate_load_factor" -> derate.candidateMaxLoadFactor();
			case "post_derate_reaction_torque_scale" -> derate.candidateMaxReactionTorqueScale();
			case "post_derate_vibration_proxy" -> derate.candidateMaxVibrationProxy();
			default -> throw new IllegalArgumentException("unknown ambient derate manual review field: " + fieldName);
		};
	}

	private static double reviewValue(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateRow derate,
			String fieldName
	) {
		return switch (fieldName) {
			case "target_max_tip_mach" -> derate.targetMaxTipMach();
			case "target_max_rpm_scale" -> derate.targetMaxRpmScale();
			case "required_max_rpm_derate_percent" -> derate.requiredMaxRpmDeratePercent();
			case "post_derate_thrust_loss_percent" -> derate.postDerateThrustLossPercent();
			case "post_derate_load_factor" -> derate.postDerateLoadFactor();
			case "post_derate_reaction_torque_scale" -> derate.postDerateReactionTorqueScale();
			case "post_derate_vibration_proxy" -> derate.postDerateVibrationProxy();
			default -> throw new IllegalArgumentException("unknown ambient derate manual review field: " + fieldName);
		};
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
