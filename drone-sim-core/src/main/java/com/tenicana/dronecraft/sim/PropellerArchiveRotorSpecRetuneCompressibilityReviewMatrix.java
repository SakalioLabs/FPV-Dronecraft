package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Compressibility-Review-Matrix-Packet";
	public static final String CAVEAT =
			"RotorSpec retune compressibility review matrix converts accepted physical-budget rows with max-RPM compressibility markers into offline review runs only; it never enables playable reference export, DroneConfig patching, runtime coupling, or gameplay auto-apply.";
	public static final double NACA_TAKEOFF_CLIMB_EFFECT_LOW_MACH = 0.50;
	public static final double NACA_TAKEOFF_CLIMB_EFFECT_HIGH_MACH = 0.70;
	public static final double NACA_SERIOUS_LOSS_MACH = 0.91;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.SCENARIO_SAMPLE_COUNT;
	public static final int REVIEW_CASE_COUNT = 2;
	public static final int REVIEW_ROW_COUNT = 4;
	public static final int SCENARIO_METRIC_ROW_COUNT = 15;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REVIEW_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<CompressibilityReviewCaseDefinition> CASES = List.of(
			new CompressibilityReviewCaseDefinition(
					"runtime_curve_bounded_impact",
					"runtime_curve",
					"thrust_loss_load_torque_vibration",
					4,
					"execute-max-rpm-compressibility-runtime-curve-review"
			),
			new CompressibilityReviewCaseDefinition(
					"naca_takeoff_climb_band_position",
					"historical_reference",
					"tip_mach_loss_band_position",
					2,
					"compare-candidate-tip-mach-against-naca-thresholds"
			)
	);

	private PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix() {
	}

	public record CompressibilityReviewCaseDefinition(
			String reviewCaseName,
			String validationDomain,
			String targetMetric,
			int minSampleCount,
			String nextRequiredAction
	) {
	}

	public record RetuneCompressibilityReviewRow(
			String scenarioName,
			String presetName,
			String reviewCaseName,
			String validationDomain,
			String targetMetric,
			int minSampleCount,
			double candidateMaxTipMach,
			double candidateMaxThrustLossPercent,
			double candidateMaxLoadFactor,
			double candidateMaxReactionTorqueScale,
			double candidateMaxVibrationProxy,
			double referenceEffectBandLowMach,
			double referenceEffectBandHighMach,
			double referenceSeriousLossMach,
			boolean inNacaTakeoffClimbEffectBand,
			boolean belowSeriousLossMach,
			boolean physicalBudgetAccepted,
			boolean compressibilityReviewRequired,
			boolean validationRunPlanned,
			boolean validationResultRequired,
			boolean playableReferenceAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String nextRequiredAction
	) {
	}

	public record RetuneCompressibilityReviewScenarioSummary(
			boolean validationAcceptanceReady,
			boolean physicalBudgetAvailable,
			boolean reviewMatrixAvailable,
			int matrixRowCount,
			int plannedReviewCaseCount,
			int compressibilityReviewRequiredCount,
			int nacaBandCaseCount,
			int runtimeCurveCaseCount,
			int playableReferenceAllowedCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetuneCompressibilityReviewScenario(
			String scenarioName,
			RetuneCompressibilityReviewScenarioSummary summary
	) {
	}

	public record RetuneCompressibilityReviewExtrema(
			int scenarioCount,
			int reviewMatrixAvailableScenarioCount,
			int totalMatrixRowCount,
			int maxMatrixRowCount,
			int maxPlannedReviewCaseCount,
			int maxCompressibilityReviewRequiredCount,
			int maxNacaBandCaseCount,
			int maxRuntimeCurveCaseCount,
			int playableReferenceAllowedScenarioCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneCompressibilityReviewAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int reviewCaseCount,
			int reviewRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			double nacaTakeoffClimbEffectLowMach,
			double nacaTakeoffClimbEffectHighMach,
			double nacaSeriousLossMach,
			List<CompressibilityReviewCaseDefinition> cases,
			List<RetuneCompressibilityReviewRow> rows,
			List<RetuneCompressibilityReviewScenario> scenarios,
			RetuneCompressibilityReviewExtrema extrema
	) {
		public RetuneCompressibilityReviewAudit {
			cases = List.copyOf(cases);
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneCompressibilityReviewAudit audit() {
		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceAudit physical =
				PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.audit();
		List<RetuneCompressibilityReviewScenario> scenarios = physical.scenarios().stream()
				.map(scenario -> scenario(physical, scenario))
				.toList();
		List<RetuneCompressibilityReviewRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(physical, scenario.scenarioName()).stream())
				.toList();
		return new RetuneCompressibilityReviewAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				REVIEW_CASE_COUNT,
				REVIEW_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				NACA_TAKEOFF_CLIMB_EFFECT_LOW_MACH,
				NACA_TAKEOFF_CLIMB_EFFECT_HIGH_MACH,
				NACA_SERIOUS_LOSS_MACH,
				CASES,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static CompressibilityReviewCaseDefinition caseDefinition(String reviewCaseName) {
		return CASES.stream()
				.filter(caseDefinition -> caseDefinition.reviewCaseName().equals(reviewCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune compressibility review case: " + reviewCaseName));
	}

	public static RetuneCompressibilityReviewScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune compressibility review scenario: " + scenarioName));
	}

	public static RetuneCompressibilityReviewRow row(
			String scenarioName,
			String presetName,
			String reviewCaseName
	) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.reviewCaseName().equals(reviewCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune compressibility review row: "
								+ scenarioName + "/" + presetName + "/" + reviewCaseName));
	}

	private static RetuneCompressibilityReviewScenario scenario(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceAudit physical,
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenario scenario
	) {
		List<RetuneCompressibilityReviewRow> rows = rowsForScenario(physical, scenario.scenarioName());
		return new RetuneCompressibilityReviewScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetuneCompressibilityReviewScenarioSummary summary(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary physicalSummary,
			List<RetuneCompressibilityReviewRow> rows
	) {
		int planned = 0;
		int reviewRequired = 0;
		int naca = 0;
		int runtimeCurve = 0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneCompressibilityReviewRow row : rows) {
			if (row.validationRunPlanned()) {
				planned++;
			}
			if (row.compressibilityReviewRequired()) {
				reviewRequired++;
			}
			if ("naca_takeoff_climb_band_position".equals(row.reviewCaseName())) {
				naca++;
			}
			if ("runtime_curve_bounded_impact".equals(row.reviewCaseName())) {
				runtimeCurve++;
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
		boolean available = physicalSummary.physicalBudgetAvailable() && !rows.isEmpty();
		return new RetuneCompressibilityReviewScenarioSummary(
				physicalSummary.validationAcceptanceReady(),
				physicalSummary.physicalBudgetAvailable(),
				available,
				rows.size(),
				planned,
				reviewRequired,
				naca,
				runtimeCurve,
				playable,
				config,
				runtime,
				gameplay,
				status(physicalSummary, rows),
				message(physicalSummary, rows),
				physicalSummary.sourceRuntimeInfo()
		);
	}

	private static List<RetuneCompressibilityReviewRow> rowsForScenario(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceAudit physical,
			String scenarioName
	) {
		return physical.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceRow::physicalBudgetAccepted)
				.filter(PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceRow::compressibilityReviewRequired)
				.flatMap(row -> CASES.stream().map(caseDefinition -> row(row, caseDefinition)))
				.toList();
	}

	private static RetuneCompressibilityReviewRow row(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceRow physical,
			CompressibilityReviewCaseDefinition caseDefinition
	) {
		boolean inBand = physical.candidateMaxTipMach() >= NACA_TAKEOFF_CLIMB_EFFECT_LOW_MACH
				&& physical.candidateMaxTipMach() <= NACA_TAKEOFF_CLIMB_EFFECT_HIGH_MACH;
		boolean belowSerious = physical.candidateMaxTipMach() < NACA_SERIOUS_LOSS_MACH;
		return new RetuneCompressibilityReviewRow(
				physical.scenarioName(),
				physical.presetName(),
				caseDefinition.reviewCaseName(),
				caseDefinition.validationDomain(),
				caseDefinition.targetMetric(),
				caseDefinition.minSampleCount(),
				physical.candidateMaxTipMach(),
				physical.candidateMaxThrustLossPercent(),
				physical.candidateMaxLoadFactor(),
				physical.candidateMaxReactionTorqueScale(),
				physical.candidateMaxVibrationProxy(),
				NACA_TAKEOFF_CLIMB_EFFECT_LOW_MACH,
				NACA_TAKEOFF_CLIMB_EFFECT_HIGH_MACH,
				NACA_SERIOUS_LOSS_MACH,
				inBand,
				belowSerious,
				physical.physicalBudgetAccepted(),
				physical.compressibilityReviewRequired(),
				true,
				true,
				false,
				false,
				false,
				false,
				"PENDING_VALIDATION",
				message(caseDefinition),
				caseDefinition.nextRequiredAction()
		);
	}

	private static RetuneCompressibilityReviewExtrema extrema(
			List<RetuneCompressibilityReviewRow> rows,
			List<RetuneCompressibilityReviewScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxPlanned = 0;
		int maxReview = 0;
		int maxNaca = 0;
		int maxRuntimeCurve = 0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneCompressibilityReviewScenario scenario : scenarios) {
			RetuneCompressibilityReviewScenarioSummary summary = scenario.summary();
			if (summary.reviewMatrixAvailable()) {
				available++;
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
			maxRows = Math.max(maxRows, summary.matrixRowCount());
			maxPlanned = Math.max(maxPlanned, summary.plannedReviewCaseCount());
			maxReview = Math.max(maxReview, summary.compressibilityReviewRequiredCount());
			maxNaca = Math.max(maxNaca, summary.nacaBandCaseCount());
			maxRuntimeCurve = Math.max(maxRuntimeCurve, summary.runtimeCurveCaseCount());
		}
		return new RetuneCompressibilityReviewExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxPlanned,
				maxReview,
				maxNaca,
				maxRuntimeCurve,
				playable,
				config,
				runtime,
				gameplay
		);
	}

	private static String status(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary physicalSummary,
			List<RetuneCompressibilityReviewRow> rows
	) {
		if (!physicalSummary.physicalBudgetAvailable()) {
			return "BLOCKED";
		}
		return rows.isEmpty() ? "READY" : "PENDING_VALIDATION";
	}

	private static String message(
			PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary physicalSummary,
			List<RetuneCompressibilityReviewRow> rows
	) {
		if (!physicalSummary.physicalBudgetAvailable()) {
			return physicalSummary.message();
		}
		return rows.isEmpty()
				? "compressibility-review-not-required"
				: "compressibility-review-matrix-planned";
	}

	private static String message(CompressibilityReviewCaseDefinition caseDefinition) {
		if ("runtime_curve_bounded_impact".equals(caseDefinition.reviewCaseName())) {
			return "runtime-curve-impact-review-planned";
		}
		if ("naca_takeoff_climb_band_position".equals(caseDefinition.reviewCaseName())) {
			return "naca-band-position-review-planned";
		}
		return "compressibility-review-planned";
	}
}
