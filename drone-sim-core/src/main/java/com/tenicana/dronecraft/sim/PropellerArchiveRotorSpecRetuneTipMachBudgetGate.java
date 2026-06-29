package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneTipMachBudgetGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Tip-Mach-Budget-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune tip-Mach budget gate checks validation-accepted physical previews against hover and max tip-Mach limits; it only produces audit material and never emits a DroneConfig patch, runtime coupling, or gameplay auto-apply.";
	public static final double AMBIENT_TEMPERATURE_CELSIUS = 15.0;
	public static final double HOVER_TIP_MACH_LIMIT = 0.35;
	public static final double MAX_TIP_MACH_LIMIT = 0.70;
	public static final double COMPRESSIBILITY_ONSET_MACH = 0.46;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int BUDGET_ROW_COUNT = 2;
	public static final int SCENARIO_METRIC_ROW_COUNT = 14;
	public static final int SUMMARY_ROW_COUNT = 11;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ BUDGET_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetuneTipMachBudgetGate() {
	}

	public record RetuneTipMachBudgetRow(
			String scenarioName,
			String presetName,
			double ambientTemperatureCelsius,
			double speedOfSoundMetersPerSecond,
			double currentHoverTipMach,
			double candidateHoverTipMach,
			double currentMaxTipMach,
			double candidateMaxTipMach,
			double hoverTipMachLimit,
			double maxTipMachLimit,
			double compressibilityOnsetMach,
			boolean candidateHoverMachWithinBudget,
			boolean candidateMaxMachWithinBudget,
			boolean candidateMaxCompressibilityReviewRequired,
			boolean tipMachBudgetAccepted,
			boolean manualPatchReviewAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetuneTipMachBudgetScenarioSummary(
			boolean validationAcceptanceReady,
			boolean impactPreviewAvailable,
			boolean tipMachBudgetAvailable,
			int budgetRowCount,
			int tipMachBudgetAcceptedCount,
			int compressibilityReviewRequiredCount,
			double maxCandidateHoverTipMach,
			double maxCandidateMaxTipMach,
			double minCandidateHoverMachMargin,
			double minCandidateMaxMachMargin,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetuneTipMachBudgetScenario(
			String scenarioName,
			RetuneTipMachBudgetScenarioSummary summary
	) {
	}

	public record RetuneTipMachBudgetExtrema(
			int scenarioCount,
			int tipMachBudgetAvailableScenarioCount,
			int totalBudgetRowCount,
			int maxBudgetRowCount,
			int maxTipMachBudgetAcceptedCount,
			int maxCompressibilityReviewRequiredCount,
			double maxCandidateHoverTipMach,
			double maxCandidateMaxTipMach,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneTipMachBudgetAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int budgetRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			double ambientTemperatureCelsius,
			double speedOfSoundMetersPerSecond,
			double hoverTipMachLimit,
			double maxTipMachLimit,
			double compressibilityOnsetMach,
			List<RetuneTipMachBudgetRow> rows,
			List<RetuneTipMachBudgetScenario> scenarios,
			RetuneTipMachBudgetExtrema extrema
	) {
		public RetuneTipMachBudgetAudit {
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneTipMachBudgetAudit audit() {
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactAudit impact =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.audit();
		List<RetuneTipMachBudgetScenario> scenarios = impact.scenarios().stream()
				.map(scenario -> scenario(impact, scenario))
				.toList();
		List<RetuneTipMachBudgetRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(impact, scenario.scenarioName()).stream())
				.toList();
		double speedOfSound = DroneEnvironment.speedOfSoundMetersPerSecond(AMBIENT_TEMPERATURE_CELSIUS);
		return new RetuneTipMachBudgetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				BUDGET_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				AMBIENT_TEMPERATURE_CELSIUS,
				speedOfSound,
				HOVER_TIP_MACH_LIMIT,
				MAX_TIP_MACH_LIMIT,
				COMPRESSIBILITY_ONSET_MACH,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static RetuneTipMachBudgetScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune tip-Mach budget scenario: " + scenarioName));
	}

	public static RetuneTipMachBudgetRow row(String scenarioName, String presetName) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune tip-Mach budget row: " + scenarioName + "/" + presetName));
	}

	private static RetuneTipMachBudgetScenario scenario(
			PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactAudit impact,
			PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenario scenario
	) {
		List<RetuneTipMachBudgetRow> rows = rowsForScenario(impact, scenario.scenarioName());
		return new RetuneTipMachBudgetScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetuneTipMachBudgetScenarioSummary summary(
			PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenarioSummary impactSummary,
			List<RetuneTipMachBudgetRow> rows
	) {
		int accepted = 0;
		int compressibility = 0;
		double maxHover = 0.0;
		double maxMax = 0.0;
		double minHoverMargin = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double minMaxMargin = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneTipMachBudgetRow row : rows) {
			if (row.tipMachBudgetAccepted()) {
				accepted++;
			}
			if (row.candidateMaxCompressibilityReviewRequired()) {
				compressibility++;
			}
			maxHover = Math.max(maxHover, row.candidateHoverTipMach());
			maxMax = Math.max(maxMax, row.candidateMaxTipMach());
			minHoverMargin = Math.min(minHoverMargin, row.hoverTipMachLimit() - row.candidateHoverTipMach());
			minMaxMargin = Math.min(minMaxMargin, row.maxTipMachLimit() - row.candidateMaxTipMach());
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
		boolean available = impactSummary.impactPreviewAvailable() && !rows.isEmpty();
		boolean allAccepted = available && accepted == rows.size();
		return new RetuneTipMachBudgetScenarioSummary(
				impactSummary.validationAcceptanceReady(),
				impactSummary.impactPreviewAvailable(),
				available,
				rows.size(),
				accepted,
				compressibility,
				maxHover,
				maxMax,
				minHoverMargin,
				minMaxMargin,
				config,
				runtime,
				gameplay,
				available ? "READY" : "BLOCKED",
				message(impactSummary, available, allAccepted, compressibility),
				impactSummary.sourceRuntimeInfo()
		);
	}

	private static List<RetuneTipMachBudgetRow> rowsForScenario(
			PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactAudit impact,
			String scenarioName
	) {
		return impact.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.map(PropellerArchiveRotorSpecRetuneTipMachBudgetGate::row)
				.toList();
	}

	private static RetuneTipMachBudgetRow row(
			PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactRow impact
	) {
		double speedOfSound = DroneEnvironment.speedOfSoundMetersPerSecond(AMBIENT_TEMPERATURE_CELSIUS);
		double currentHoverMach = impact.currentHoverTipSpeedMetersPerSecond() / speedOfSound;
		double candidateHoverMach = impact.candidateHoverTipSpeedMetersPerSecond() / speedOfSound;
		double currentMaxMach = currentHoverMach * ratio(impact.currentMaxRpm(), impact.currentHoverRpm());
		double candidateMaxMach = candidateHoverMach * ratio(impact.candidateMaxRpm(), impact.candidateHoverRpm());
		boolean hoverWithin = candidateHoverMach <= HOVER_TIP_MACH_LIMIT;
		boolean maxWithin = candidateMaxMach <= MAX_TIP_MACH_LIMIT;
		boolean compressibilityReview = candidateMaxMach >= COMPRESSIBILITY_ONSET_MACH;
		boolean accepted = hoverWithin && maxWithin;
		return new RetuneTipMachBudgetRow(
				impact.scenarioName(),
				impact.presetName(),
				AMBIENT_TEMPERATURE_CELSIUS,
				speedOfSound,
				currentHoverMach,
				candidateHoverMach,
				currentMaxMach,
				candidateMaxMach,
				HOVER_TIP_MACH_LIMIT,
				MAX_TIP_MACH_LIMIT,
				COMPRESSIBILITY_ONSET_MACH,
				hoverWithin,
				maxWithin,
				compressibilityReview,
				accepted,
				impact.manualPatchReviewAllowed(),
				false,
				false,
				false,
				accepted ? "READY" : "BLOCKED",
				accepted
						? (compressibilityReview
								? "tip-mach-budget-ready-compressibility-review-required"
								: "tip-mach-budget-ready")
						: "tip-mach-budget-exceeded"
		);
	}

	private static RetuneTipMachBudgetExtrema extrema(
			List<RetuneTipMachBudgetRow> rows,
			List<RetuneTipMachBudgetScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxAccepted = 0;
		int maxCompressibility = 0;
		double maxHover = 0.0;
		double maxMax = 0.0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneTipMachBudgetScenario scenario : scenarios) {
			RetuneTipMachBudgetScenarioSummary summary = scenario.summary();
			if (summary.tipMachBudgetAvailable()) {
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
			maxRows = Math.max(maxRows, summary.budgetRowCount());
			maxAccepted = Math.max(maxAccepted, summary.tipMachBudgetAcceptedCount());
			maxCompressibility = Math.max(maxCompressibility, summary.compressibilityReviewRequiredCount());
			maxHover = Math.max(maxHover, summary.maxCandidateHoverTipMach());
			maxMax = Math.max(maxMax, summary.maxCandidateMaxTipMach());
		}
		return new RetuneTipMachBudgetExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxAccepted,
				maxCompressibility,
				maxHover,
				maxMax,
				config,
				runtime,
				gameplay
		);
	}

	private static String message(
			PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenarioSummary impactSummary,
			boolean available,
			boolean allAccepted,
			int compressibilityReviewCount
	) {
		if (!available) {
			return impactSummary.message();
		}
		if (!allAccepted) {
			return "tip-mach-budget-exceeded";
		}
		if (compressibilityReviewCount > 0) {
			return "tip-mach-budget-ready-compressibility-review-required";
		}
		return "tip-mach-budget-ready";
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
