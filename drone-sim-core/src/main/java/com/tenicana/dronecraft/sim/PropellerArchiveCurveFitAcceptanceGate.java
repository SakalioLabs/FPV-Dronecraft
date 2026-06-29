package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveCurveFitAcceptanceGate {
	public static final String SOURCE_ID = "User-Propeller-Archive-Curve-Fit-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"Curve-fit acceptance remains closed until reviewed import, preset coverage, curve execution, and every required CT/CP/eta/geometry fit result pass; compact reference export needs a later review and runtime/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_ROW_COUNT = 12;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final double MAX_CT_WEIGHTED_RMSE = 0.015;
	public static final double MAX_CP_WEIGHTED_RMSE = 0.012;
	public static final double MAX_ETA_RESIDUAL = 0.025;
	public static final double MAX_STATIC_COEFFICIENT_ERROR = 0.018;
	public static final double MAX_CHORD_STATION_RMSE = 0.025;
	public static final double MAX_BETA_STATION_ERROR_DEGREES = 1.5;

	private PropellerArchiveCurveFitAcceptanceGate() {
	}

	public record CurveFitAcceptanceTarget(
			String presetName,
			String curveName,
			String sourceTable,
			String validationMetric,
			double maxWeightedRmse,
			double maxAnchorError,
			int minValidationRowCount,
			int maxPhysicalConstraintViolationCount,
			String downstreamUse
	) {
	}

	public record CurveFitAcceptanceResult(
			String presetName,
			String curveName,
			double weightedRmse,
			double anchorError,
			int validationRowCount,
			int physicalConstraintViolationCount,
			boolean passed,
			String status
	) {
	}

	public record CurveFitAcceptanceSummary(
			boolean reviewedImportReady,
			boolean fitInputCoverageReady,
			boolean curveFitExecuted,
			boolean compactReferenceReviewed,
			int expectedTargetCount,
			int observedResultCount,
			int passedResultCount,
			int failedResultCount,
			int missingResultCount,
			int unexpectedResultCount,
			double maxWeightedRmse,
			double maxAnchorError,
			int maxPhysicalConstraintViolationCount,
			boolean allTargetsPresent,
			boolean allExpectedResultsPassed,
			boolean curveFitAcceptanceReady,
			boolean compactReferenceExportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record CurveFitAcceptanceScenario(
			String scenarioName,
			CurveFitAcceptanceSummary summary
	) {
	}

	public record CurveFitAcceptanceExtrema(
			int scenarioCount,
			int acceptedScenarioCount,
			int blockedScenarioCount,
			int maxExpectedTargetCount,
			int maxObservedResultCount,
			int maxMissingResultCount,
			int maxFailedResultCount,
			double maxWeightedRmse,
			int compactReferenceExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CurveFitAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CurveFitAcceptanceTarget> targets,
			List<CurveFitAcceptanceScenario> scenarios,
			CurveFitAcceptanceExtrema extrema
	) {
		public CurveFitAcceptanceAudit {
			targets = List.copyOf(targets);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CurveFitAcceptanceAudit audit() {
		List<CurveFitAcceptanceTarget> targets = targets();
		List<CurveFitAcceptanceResult> passingResults = passingResults(targets);
		List<CurveFitAcceptanceResult> failedResults = new ArrayList<>(passingResults);
		failedResults.set(failedResults.size() - 1, failingResult(targets.get(targets.size() - 1)));

		List<CurveFitAcceptanceScenario> scenarios = List.of(
				new CurveFitAcceptanceScenario(
						"current_no_reviewed_import_no_results",
						gate(false, false, false, false, targets, List.of(), "current-prop-archive-acceptance-audit")),
				new CurveFitAcceptanceScenario(
						"reviewed_import_coverage_blocked_no_results",
						gate(true, false, false, false, targets, List.of(), "reviewed-import-coverage-blocked")),
				new CurveFitAcceptanceScenario(
						"synthetic_all_curve_targets_pass",
						gate(true, true, true, true, targets, passingResults, "synthetic-reviewed-fit-quality-accepted")),
				new CurveFitAcceptanceScenario(
						"synthetic_one_curve_result_failed",
						gate(true, true, true, true, targets, failedResults, "synthetic-reviewed-fit-quality-failed"))
		);
		return new CurveFitAcceptanceAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				targets,
				scenarios,
				extrema(scenarios)
		);
	}

	public static List<CurveFitAcceptanceTarget> targets() {
		PropellerArchiveCurveFitPlan.CurveFitPlanAudit plan = PropellerArchiveCurveFitPlan.audit();
		List<CurveFitAcceptanceTarget> targets = new ArrayList<>();
		for (PropellerArchiveCurveFitPlan.PresetCurveFitPlan preset : plan.presets()) {
			for (PropellerArchiveCurveFitPlan.CurveFitContract curve : plan.curves()) {
				if (curve.requiredForSimulationFit()) {
					targets.add(target(preset.presetName(), curve));
				}
			}
		}
		return List.copyOf(targets);
	}

	public static CurveFitAcceptanceTarget target(String presetName, String curveName) {
		PropellerArchiveCurveFitPlan.CurveFitContract curve = PropellerArchiveCurveFitPlan.curve(curveName);
		PropellerArchiveCurveFitPlan.preset(presetName);
		return target(presetName, curve);
	}

	public static CurveFitAcceptanceResult result(
			String presetName,
			String curveName,
			double weightedRmse,
			double anchorError,
			int validationRowCount,
			int physicalConstraintViolationCount
	) {
		CurveFitAcceptanceTarget target = target(presetName, curveName);
		if (!Double.isFinite(weightedRmse) || weightedRmse < 0.0) {
			throw new IllegalArgumentException("weightedRmse must be finite and nonnegative.");
		}
		if (!Double.isFinite(anchorError) || anchorError < 0.0) {
			throw new IllegalArgumentException("anchorError must be finite and nonnegative.");
		}
		if (validationRowCount < 0) {
			throw new IllegalArgumentException("validationRowCount must be nonnegative.");
		}
		if (physicalConstraintViolationCount < 0) {
			throw new IllegalArgumentException("physicalConstraintViolationCount must be nonnegative.");
		}
		boolean passed = weightedRmse <= target.maxWeightedRmse()
				&& anchorError <= target.maxAnchorError()
				&& validationRowCount >= target.minValidationRowCount()
				&& physicalConstraintViolationCount <= target.maxPhysicalConstraintViolationCount();
		return new CurveFitAcceptanceResult(
				presetName,
				curveName,
				weightedRmse,
				anchorError,
				validationRowCount,
				physicalConstraintViolationCount,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static CurveFitAcceptanceSummary gate(
			boolean reviewedImportReady,
			boolean fitInputCoverageReady,
			boolean curveFitExecuted,
			boolean compactReferenceReviewed,
			List<CurveFitAcceptanceTarget> targets,
			List<CurveFitAcceptanceResult> results,
			String sourceRuntimeInfo
	) {
		if (targets == null || targets.isEmpty()) {
			throw new IllegalArgumentException("targets must not be empty.");
		}
		if (results == null) {
			throw new IllegalArgumentException("results must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		Set<String> targetKeys = new HashSet<>();
		for (CurveFitAcceptanceTarget target : targets) {
			if (target == null || target.presetName() == null || target.curveName() == null
					|| target.presetName().isBlank() || target.curveName().isBlank()) {
				throw new IllegalArgumentException("targets must include stable preset and curve names.");
			}
			if (!targetKeys.add(key(target.presetName(), target.curveName()))) {
				throw new IllegalArgumentException("duplicate curve fit target: "
						+ key(target.presetName(), target.curveName()));
			}
		}
		Set<String> resultKeys = new HashSet<>();
		for (CurveFitAcceptanceResult result : results) {
			if (result == null || result.presetName() == null || result.curveName() == null
					|| result.presetName().isBlank() || result.curveName().isBlank()) {
				throw new IllegalArgumentException("results must include stable preset and curve names.");
			}
			if (!resultKeys.add(key(result.presetName(), result.curveName()))) {
				throw new IllegalArgumentException("duplicate curve fit result: "
						+ key(result.presetName(), result.curveName()));
			}
		}

		int passed = 0;
		int failed = 0;
		int missing = 0;
		double maxWeightedRmse = 0.0;
		double maxAnchorError = 0.0;
		int maxViolations = 0;
		for (CurveFitAcceptanceTarget target : targets) {
			CurveFitAcceptanceResult result = findResult(results, target.presetName(), target.curveName());
			if (result == null) {
				missing++;
				continue;
			}
			maxWeightedRmse = Math.max(maxWeightedRmse, result.weightedRmse());
			maxAnchorError = Math.max(maxAnchorError, result.anchorError());
			maxViolations = Math.max(maxViolations, result.physicalConstraintViolationCount());
			if (result.passed()) {
				passed++;
			} else {
				failed++;
			}
		}
		int unexpected = 0;
		for (CurveFitAcceptanceResult result : results) {
			if (!targetKeys.contains(key(result.presetName(), result.curveName()))) {
				unexpected++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && results.size() == targets.size();
		boolean allPassed = failed == 0 && passed == targets.size();
		boolean acceptanceReady = reviewedImportReady
				&& fitInputCoverageReady
				&& curveFitExecuted
				&& allPresent
				&& allPassed;
		boolean exportAllowed = acceptanceReady && compactReferenceReviewed;
		String message = message(
				reviewedImportReady,
				fitInputCoverageReady,
				curveFitExecuted,
				allPresent,
				allPassed,
				compactReferenceReviewed
		);
		return new CurveFitAcceptanceSummary(
				reviewedImportReady,
				fitInputCoverageReady,
				curveFitExecuted,
				compactReferenceReviewed,
				targets.size(),
				results.size(),
				passed,
				failed,
				missing,
				unexpected,
				maxWeightedRmse,
				maxAnchorError,
				maxViolations,
				allPresent,
				allPassed,
				acceptanceReady,
				exportAllowed,
				false,
				false,
				acceptanceReady ? "READY" : "BLOCKED",
				message,
				sourceRuntimeInfo
		);
	}

	private static CurveFitAcceptanceTarget target(
			String presetName,
			PropellerArchiveCurveFitPlan.CurveFitContract curve
	) {
		double threshold = threshold(curve.curveName());
		double anchor = anchorThreshold(curve.curveName());
		int minRows = "geometry".equals(curve.sourceTable()) ? 8 : 24;
		return new CurveFitAcceptanceTarget(
				presetName,
				curve.curveName(),
				curve.sourceTable(),
				curve.validationMetric(),
				threshold,
				anchor,
				minRows,
				0,
				curve.downstreamUse()
		);
	}

	private static List<CurveFitAcceptanceResult> passingResults(List<CurveFitAcceptanceTarget> targets) {
		return targets.stream()
				.map(PropellerArchiveCurveFitAcceptanceGate::passingResult)
				.toList();
	}

	private static CurveFitAcceptanceResult passingResult(CurveFitAcceptanceTarget target) {
		return result(
				target.presetName(),
				target.curveName(),
				target.maxWeightedRmse() * 0.50,
				target.maxAnchorError() * 0.40,
				target.minValidationRowCount() + 4,
				0
		);
	}

	private static CurveFitAcceptanceResult failingResult(CurveFitAcceptanceTarget target) {
		return result(
				target.presetName(),
				target.curveName(),
				target.maxWeightedRmse() * 1.50,
				target.maxAnchorError() * 1.25,
				target.minValidationRowCount() + 4,
				1
		);
	}

	private static CurveFitAcceptanceResult findResult(
			List<CurveFitAcceptanceResult> results,
			String presetName,
			String curveName
	) {
		for (CurveFitAcceptanceResult result : results) {
			if (presetName.equals(result.presetName()) && curveName.equals(result.curveName())) {
				return result;
			}
		}
		return null;
	}

	private static CurveFitAcceptanceExtrema extrema(List<CurveFitAcceptanceScenario> scenarios) {
		int accepted = 0;
		int maxExpected = 0;
		int maxObserved = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		double maxWeightedRmse = 0.0;
		int exportAllowed = 0;
		int runtime = 0;
		int gameplay = 0;
		for (CurveFitAcceptanceScenario scenario : scenarios) {
			CurveFitAcceptanceSummary summary = scenario.summary();
			if (summary.curveFitAcceptanceReady()) {
				accepted++;
			}
			if (summary.compactReferenceExportAllowed()) {
				exportAllowed++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedTargetCount());
			maxObserved = Math.max(maxObserved, summary.observedResultCount());
			maxMissing = Math.max(maxMissing, summary.missingResultCount());
			maxFailed = Math.max(maxFailed, summary.failedResultCount());
			maxWeightedRmse = Math.max(maxWeightedRmse, summary.maxWeightedRmse());
		}
		return new CurveFitAcceptanceExtrema(
				scenarios.size(),
				accepted,
				scenarios.size() - accepted,
				maxExpected,
				maxObserved,
				maxMissing,
				maxFailed,
				maxWeightedRmse,
				exportAllowed,
				runtime,
				gameplay
		);
	}

	private static double threshold(String curveName) {
		return switch (curveName) {
			case "ct_vs_advance" -> MAX_CT_WEIGHTED_RMSE;
			case "cp_vs_advance" -> MAX_CP_WEIGHTED_RMSE;
			case "eta_consistency" -> MAX_ETA_RESIDUAL;
			case "static_ct_cp_vs_rpm" -> MAX_STATIC_COEFFICIENT_ERROR;
			case "chord_distribution" -> MAX_CHORD_STATION_RMSE;
			case "beta_distribution" -> MAX_BETA_STATION_ERROR_DEGREES;
			default -> throw new IllegalArgumentException("unknown curve fit target threshold: " + curveName);
		};
	}

	private static double anchorThreshold(String curveName) {
		return switch (curveName) {
			case "ct_vs_advance" -> 0.010;
			case "cp_vs_advance" -> 0.008;
			case "eta_consistency" -> 0.020;
			case "static_ct_cp_vs_rpm" -> 0.012;
			case "chord_distribution" -> 0.020;
			case "beta_distribution" -> 1.000;
			default -> throw new IllegalArgumentException("unknown curve fit target anchor threshold: " + curveName);
		};
	}

	private static String message(
			boolean reviewedImportReady,
			boolean fitInputCoverageReady,
			boolean curveFitExecuted,
			boolean allPresent,
			boolean allPassed,
			boolean compactReferenceReviewed
	) {
		if (!reviewedImportReady) {
			return "reviewed-import-missing";
		}
		if (!fitInputCoverageReady) {
			return "fit-input-coverage-blocked";
		}
		if (!curveFitExecuted) {
			return "curve-fit-results-missing";
		}
		if (!allPresent) {
			return "curve-fit-result-set-incomplete";
		}
		if (!allPassed) {
			return "curve-fit-result-failed";
		}
		if (!compactReferenceReviewed) {
			return "curve-fit-accepted-reference-review-blocked";
		}
		return "curve-fit-acceptance-ready";
	}

	private static String key(String presetName, String curveName) {
		return presetName + "::" + curveName;
	}
}
