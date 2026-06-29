package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Physical-Budget-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune physical budget acceptance gate aggregates validation, physical impact, tip-Mach, Reynolds, and compressibility impact evidence; accepted rows are manual review material only and never enable DroneConfig patching, runtime coupling, playable reference export, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int BUDGET_ROW_COUNT = 2;
	public static final int SCENARIO_METRIC_ROW_COUNT = 20;
	public static final int SUMMARY_ROW_COUNT = 17;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ BUDGET_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate() {
	}

	public record RetunePhysicalBudgetAcceptanceRow(
			String scenarioName,
			String presetName,
			boolean validationAcceptanceReady,
			boolean manualPatchReviewAllowed,
			boolean physicalImpactPreviewAvailable,
			boolean tipMachBudgetAccepted,
			boolean reynoldsBudgetAccepted,
			boolean compressibilityImpactAccepted,
			boolean compressibilityReviewRequired,
			double candidateHoverTipMach,
			double candidateMaxTipMach,
			double candidateHoverReynoldsNumber,
			double candidateMaxThrustLossPercent,
			double candidateMaxLoadFactor,
			double candidateMaxReactionTorqueScale,
			double candidateMaxVibrationProxy,
			boolean physicalBudgetAccepted,
			boolean manualPatchReviewReady,
			boolean playableReferenceAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetunePhysicalBudgetAcceptanceScenarioSummary(
			boolean validationAcceptanceReady,
			boolean reynoldsBudgetAvailable,
			boolean compressibilityImpactAvailable,
			boolean physicalBudgetAvailable,
			int budgetRowCount,
			int physicalBudgetAcceptedCount,
			int compressibilityReviewRequiredCount,
			int manualPatchReviewReadyCount,
			int playableReferenceAllowedCount,
			double minCandidateHoverReynoldsNumber,
			double maxCandidateMaxTipMach,
			double maxCandidateMaxThrustLossPercent,
			double maxCandidateMaxLoadFactor,
			double maxCandidateMaxReactionTorqueScale,
			double maxCandidateMaxVibrationProxy,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetunePhysicalBudgetAcceptanceScenario(
			String scenarioName,
			RetunePhysicalBudgetAcceptanceScenarioSummary summary
	) {
	}

	public record RetunePhysicalBudgetAcceptanceExtrema(
			int scenarioCount,
			int physicalBudgetAvailableScenarioCount,
			int totalBudgetRowCount,
			int maxBudgetRowCount,
			int maxPhysicalBudgetAcceptedCount,
			int maxCompressibilityReviewRequiredCount,
			int maxManualPatchReviewReadyCount,
			double minCandidateHoverReynoldsNumber,
			double maxCandidateMaxTipMach,
			double maxCandidateMaxThrustLossPercent,
			double maxCandidateMaxLoadFactor,
			double maxCandidateMaxReactionTorqueScale,
			double maxCandidateMaxVibrationProxy,
			int playableReferenceAllowedScenarioCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetunePhysicalBudgetAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int budgetRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<RetunePhysicalBudgetAcceptanceRow> rows,
			List<RetunePhysicalBudgetAcceptanceScenario> scenarios,
			RetunePhysicalBudgetAcceptanceExtrema extrema
	) {
		public RetunePhysicalBudgetAcceptanceAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetunePhysicalBudgetAcceptanceAudit audit() {
		PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactAudit compressibility =
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.audit();
		List<RetunePhysicalBudgetAcceptanceScenario> scenarios = compressibility.scenarios().stream()
				.map(scenario -> scenario(compressibility, scenario))
				.toList();
		List<RetunePhysicalBudgetAcceptanceRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(compressibility, scenario.scenarioName()).stream())
				.toList();
		return new RetunePhysicalBudgetAcceptanceAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				BUDGET_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static RetunePhysicalBudgetAcceptanceScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune physical budget acceptance scenario: " + scenarioName));
	}

	public static RetunePhysicalBudgetAcceptanceRow row(String scenarioName, String presetName) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune physical budget acceptance row: "
								+ scenarioName + "/" + presetName));
	}

	private static RetunePhysicalBudgetAcceptanceScenario scenario(
			PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactAudit compressibility,
			PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactScenario scenario
	) {
		List<RetunePhysicalBudgetAcceptanceRow> rows = rowsForScenario(compressibility, scenario.scenarioName());
		return new RetunePhysicalBudgetAcceptanceScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetunePhysicalBudgetAcceptanceScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactScenarioSummary impactSummary,
			List<RetunePhysicalBudgetAcceptanceRow> rows
	) {
		int accepted = 0;
		int review = 0;
		int manual = 0;
		int playable = 0;
		double minHoverRe = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxMach = 0.0;
		double maxLoss = 0.0;
		double maxLoad = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetunePhysicalBudgetAcceptanceRow row : rows) {
			if (row.physicalBudgetAccepted()) {
				accepted++;
			}
			if (row.compressibilityReviewRequired()) {
				review++;
			}
			if (row.manualPatchReviewReady()) {
				manual++;
			}
			if (row.playableReferenceAllowed()) {
				playable++;
			}
			minHoverRe = Math.min(minHoverRe, row.candidateHoverReynoldsNumber());
			maxMach = Math.max(maxMach, row.candidateMaxTipMach());
			maxLoss = Math.max(maxLoss, row.candidateMaxThrustLossPercent());
			maxLoad = Math.max(maxLoad, row.candidateMaxLoadFactor());
			maxTorque = Math.max(maxTorque, row.candidateMaxReactionTorqueScale());
			maxVibration = Math.max(maxVibration, row.candidateMaxVibrationProxy());
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
		boolean available = impactSummary.compressibilityImpactAvailable() && !rows.isEmpty();
		boolean allAccepted = available && accepted == rows.size();
		return new RetunePhysicalBudgetAcceptanceScenarioSummary(
				impactSummary.validationAcceptanceReady(),
				impactSummary.reynoldsBudgetAvailable(),
				impactSummary.compressibilityImpactAvailable(),
				available,
				rows.size(),
				accepted,
				review,
				manual,
				playable,
				minHoverRe,
				maxMach,
				maxLoss,
				maxLoad,
				maxTorque,
				maxVibration,
				config,
				runtime,
				gameplay,
				available ? "READY" : "BLOCKED",
				message(impactSummary, available, allAccepted, review),
				impactSummary.sourceRuntimeInfo()
		);
	}

	private static List<RetunePhysicalBudgetAcceptanceRow> rowsForScenario(
			PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactAudit compressibility,
			String scenarioName
	) {
		return compressibility.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactRow::compressibilityImpactAccepted)
				.map(PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate::row)
				.toList();
	}

	private static RetunePhysicalBudgetAcceptanceRow row(
			PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactRow impact
	) {
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary validation =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.scenario(impact.scenarioName()).summary();
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenarioSummary physical =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.scenario(impact.scenarioName()).summary();
		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetRow tipMach =
				PropellerArchiveRotorSpecRetuneTipMachBudgetGate.row(
						impact.scenarioName(),
						impact.presetName()
				);
		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetRow reynolds =
				PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.row(
						impact.scenarioName(),
						impact.presetName()
				);
		boolean accepted = validation.validationAcceptanceReady()
				&& physical.impactPreviewAvailable()
				&& tipMach.tipMachBudgetAccepted()
				&& reynolds.reynoldsBudgetAccepted()
				&& impact.compressibilityImpactAccepted();
		boolean manualReady = accepted && impact.manualPatchReviewAllowed();
		return new RetunePhysicalBudgetAcceptanceRow(
				impact.scenarioName(),
				impact.presetName(),
				validation.validationAcceptanceReady(),
				impact.manualPatchReviewAllowed(),
				physical.impactPreviewAvailable(),
				tipMach.tipMachBudgetAccepted(),
				reynolds.reynoldsBudgetAccepted(),
				impact.compressibilityImpactAccepted(),
				impact.candidateMaxCompressibilityReviewRequired(),
				impact.candidateHoverTipMach(),
				impact.candidateMaxTipMach(),
				reynolds.candidateHoverReynoldsNumber(),
				impact.candidateMaxThrustLossPercent(),
				impact.candidateMaxLoadFactor(),
				impact.candidateMaxReactionTorqueScale(),
				impact.candidateMaxVibrationProxy(),
				accepted,
				manualReady,
				false,
				false,
				false,
				false,
				accepted ? "READY" : "BLOCKED",
				accepted
						? (impact.candidateMaxCompressibilityReviewRequired()
								? "physical-budget-accepted-compressibility-review-required"
								: "physical-budget-accepted")
						: "physical-budget-incomplete"
		);
	}

	private static RetunePhysicalBudgetAcceptanceExtrema extrema(
			List<RetunePhysicalBudgetAcceptanceRow> rows,
			List<RetunePhysicalBudgetAcceptanceScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxAccepted = 0;
		int maxReview = 0;
		int maxManual = 0;
		double minHoverRe = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxMach = 0.0;
		double maxLoss = 0.0;
		double maxLoad = 0.0;
		double maxTorque = 1.0;
		double maxVibration = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetunePhysicalBudgetAcceptanceScenario scenario : scenarios) {
			RetunePhysicalBudgetAcceptanceScenarioSummary summary = scenario.summary();
			if (summary.physicalBudgetAvailable()) {
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
			maxRows = Math.max(maxRows, summary.budgetRowCount());
			maxAccepted = Math.max(maxAccepted, summary.physicalBudgetAcceptedCount());
			maxReview = Math.max(maxReview, summary.compressibilityReviewRequiredCount());
			maxManual = Math.max(maxManual, summary.manualPatchReviewReadyCount());
			if (summary.physicalBudgetAvailable()) {
				minHoverRe = Math.min(minHoverRe, summary.minCandidateHoverReynoldsNumber());
				maxMach = Math.max(maxMach, summary.maxCandidateMaxTipMach());
				maxLoss = Math.max(maxLoss, summary.maxCandidateMaxThrustLossPercent());
				maxLoad = Math.max(maxLoad, summary.maxCandidateMaxLoadFactor());
				maxTorque = Math.max(maxTorque, summary.maxCandidateMaxReactionTorqueScale());
				maxVibration = Math.max(maxVibration, summary.maxCandidateMaxVibrationProxy());
			}
		}
		return new RetunePhysicalBudgetAcceptanceExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxAccepted,
				maxReview,
				maxManual,
				minHoverRe,
				maxMach,
				maxLoss,
				maxLoad,
				maxTorque,
				maxVibration,
				playable,
				config,
				runtime,
				gameplay
		);
	}

	private static String message(
			PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactScenarioSummary impactSummary,
			boolean available,
			boolean allAccepted,
			int reviewRequiredCount
	) {
		if (!available) {
			return impactSummary.message();
		}
		if (!allAccepted) {
			return "physical-budget-incomplete";
		}
		if (reviewRequiredCount > 0) {
			return "physical-budget-accepted-compressibility-review-required";
		}
		return "physical-budget-accepted";
	}
}
