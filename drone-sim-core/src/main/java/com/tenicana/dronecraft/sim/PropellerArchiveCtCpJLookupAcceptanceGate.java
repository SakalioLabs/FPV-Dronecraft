package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveCtCpJLookupAcceptanceGate {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-Lookup-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup acceptance remains closed until reviewed import, interpolation policy, lookup execution, and every required query result pass neighbor, shape, power, eta, and static-anchor guards; runtime/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int TARGET_ROW_COUNT = 9;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_ROW_COUNT = 14;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ TARGET_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final double MAX_CT_SHAPE_OVERSHOOT = 0.0;
	public static final double MIN_CP_COEFFICIENT = 1.0e-6;
	public static final double MAX_ETA_RESIDUAL = 0.020;
	public static final double MAX_STATIC_ANCHOR_ERROR = 0.004;

	private PropellerArchiveCtCpJLookupAcceptanceGate() {
	}

	public record LookupAcceptanceTarget(
			String presetName,
			String caseName,
			int minNeighborRows,
			double maxCtShapeOvershoot,
			double minCpCoefficient,
			double maxEtaResidual,
			double maxStaticAnchorError,
			boolean requiresStaticAnchorPreservation,
			String downstreamUse
	) {
	}

	public record LookupAcceptanceResult(
			String presetName,
			String caseName,
			int observedNeighborRows,
			double maxCtShapeOvershoot,
			double minCpCoefficient,
			double maxEtaResidual,
			double staticAnchorError,
			boolean passed,
			String status
	) {
	}

	public record LookupAcceptanceSummary(
			boolean reviewedImportReady,
			boolean interpolationPolicyReady,
			boolean lookupInterpolationExecuted,
			boolean compactReferenceReviewed,
			int expectedTargetCount,
			int observedResultCount,
			int passedResultCount,
			int failedResultCount,
			int missingResultCount,
			int unexpectedResultCount,
			int minObservedNeighborRows,
			double maxCtShapeOvershoot,
			double minCpCoefficient,
			double maxEtaResidual,
			double maxStaticAnchorError,
			boolean allTargetsPresent,
			boolean allExpectedResultsPassed,
			boolean lookupAcceptanceReady,
			boolean compactReferenceExportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record LookupAcceptanceScenario(
			String scenarioName,
			LookupAcceptanceSummary summary
	) {
	}

	public record LookupAcceptanceExtrema(
			int scenarioCount,
			int acceptedScenarioCount,
			int blockedScenarioCount,
			int maxExpectedTargetCount,
			int maxObservedResultCount,
			int maxMissingResultCount,
			int maxFailedResultCount,
			double maxEtaResidual,
			int compactReferenceExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJLookupAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int targetRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupAcceptanceTarget> targets,
			List<LookupAcceptanceScenario> scenarios,
			LookupAcceptanceExtrema extrema
	) {
		public CtCpJLookupAcceptanceAudit {
			targets = List.copyOf(targets);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupAcceptanceAudit audit() {
		List<LookupAcceptanceTarget> targets = targets();
		List<LookupAcceptanceResult> passingResults = passingResults(targets);
		List<LookupAcceptanceResult> failedResults = new ArrayList<>(passingResults);
		failedResults.set(failedResults.size() - 1, failingResult(targets.get(targets.size() - 1)));

		List<LookupAcceptanceScenario> scenarios = List.of(
				new LookupAcceptanceScenario(
						"current_no_reviewed_import_no_results",
						gate(false, false, false, false, targets, List.of(),
								"current-ct-cp-j-lookup-acceptance-audit")),
				new LookupAcceptanceScenario(
						"reviewed_import_policy_ready_no_results",
						gate(true, true, false, false, targets, List.of(),
								"reviewed-import-policy-ready-no-lookup-results")),
				new LookupAcceptanceScenario(
						"synthetic_all_lookup_targets_pass",
						gate(true, true, true, true, targets, passingResults,
								"synthetic-reviewed-lookup-quality-accepted")),
				new LookupAcceptanceScenario(
						"synthetic_one_lookup_result_failed",
						gate(true, true, true, true, targets, failedResults,
								"synthetic-reviewed-lookup-quality-failed"))
		);
		return new CtCpJLookupAcceptanceAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				TARGET_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				targets,
				scenarios,
				extrema(scenarios)
		);
	}

	public static List<LookupAcceptanceTarget> targets() {
		return PropellerArchiveCtCpJLookupInterpolationPolicy.audit()
				.contracts()
				.stream()
				.filter(PropellerArchiveCtCpJLookupInterpolationPolicy
						.QueryInterpolationContract::postReviewCtCpJInterpolationAllowed)
				.map(PropellerArchiveCtCpJLookupAcceptanceGate::target)
				.toList();
	}

	public static LookupAcceptanceTarget target(String presetName, String caseName) {
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract(presetName, caseName);
		if (!contract.postReviewCtCpJInterpolationAllowed()) {
			throw new IllegalArgumentException("lookup acceptance target is not enabled after review: "
					+ presetName + " / " + caseName);
		}
		return target(contract);
	}

	public static LookupAcceptanceResult result(
			String presetName,
			String caseName,
			int observedNeighborRows,
			double maxCtShapeOvershoot,
			double minCpCoefficient,
			double maxEtaResidual,
			double staticAnchorError
	) {
		LookupAcceptanceTarget target = target(presetName, caseName);
		if (observedNeighborRows < 0) {
			throw new IllegalArgumentException("observedNeighborRows must be nonnegative.");
		}
		if (!Double.isFinite(maxCtShapeOvershoot) || maxCtShapeOvershoot < 0.0) {
			throw new IllegalArgumentException("maxCtShapeOvershoot must be finite and nonnegative.");
		}
		if (!Double.isFinite(minCpCoefficient) || minCpCoefficient < 0.0) {
			throw new IllegalArgumentException("minCpCoefficient must be finite and nonnegative.");
		}
		if (!Double.isFinite(maxEtaResidual) || maxEtaResidual < 0.0) {
			throw new IllegalArgumentException("maxEtaResidual must be finite and nonnegative.");
		}
		if (!Double.isFinite(staticAnchorError) || staticAnchorError < 0.0) {
			throw new IllegalArgumentException("staticAnchorError must be finite and nonnegative.");
		}
		boolean passed = observedNeighborRows >= target.minNeighborRows()
				&& maxCtShapeOvershoot <= target.maxCtShapeOvershoot()
				&& minCpCoefficient >= target.minCpCoefficient()
				&& maxEtaResidual <= target.maxEtaResidual()
				&& staticAnchorError <= target.maxStaticAnchorError();
		return new LookupAcceptanceResult(
				presetName,
				caseName,
				observedNeighborRows,
				maxCtShapeOvershoot,
				minCpCoefficient,
				maxEtaResidual,
				staticAnchorError,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static LookupAcceptanceSummary gate(
			boolean reviewedImportReady,
			boolean interpolationPolicyReady,
			boolean lookupInterpolationExecuted,
			boolean compactReferenceReviewed,
			List<LookupAcceptanceTarget> targets,
			List<LookupAcceptanceResult> results,
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
		for (LookupAcceptanceTarget target : targets) {
			if (target == null || target.presetName() == null || target.caseName() == null
					|| target.presetName().isBlank() || target.caseName().isBlank()) {
				throw new IllegalArgumentException("targets must include stable preset and case names.");
			}
			if (!targetKeys.add(key(target.presetName(), target.caseName()))) {
				throw new IllegalArgumentException("duplicate lookup acceptance target: "
						+ key(target.presetName(), target.caseName()));
			}
		}
		Set<String> resultKeys = new HashSet<>();
		for (LookupAcceptanceResult result : results) {
			if (result == null || result.presetName() == null || result.caseName() == null
					|| result.presetName().isBlank() || result.caseName().isBlank()) {
				throw new IllegalArgumentException("results must include stable preset and case names.");
			}
			if (!resultKeys.add(key(result.presetName(), result.caseName()))) {
				throw new IllegalArgumentException("duplicate lookup acceptance result: "
						+ key(result.presetName(), result.caseName()));
			}
		}
		int passed = 0;
		int failed = 0;
		int missing = 0;
		int minObservedNeighbors = Integer.MAX_VALUE;
		double maxCtShapeOvershoot = 0.0;
		double minCpCoefficient = Double.POSITIVE_INFINITY;
		double maxEtaResidual = 0.0;
		double maxStaticAnchorError = 0.0;
		for (LookupAcceptanceTarget target : targets) {
			LookupAcceptanceResult result = findResult(results, target.presetName(), target.caseName());
			if (result == null) {
				missing++;
				continue;
			}
			minObservedNeighbors = Math.min(minObservedNeighbors, result.observedNeighborRows());
			maxCtShapeOvershoot = Math.max(maxCtShapeOvershoot, result.maxCtShapeOvershoot());
			minCpCoefficient = Math.min(minCpCoefficient, result.minCpCoefficient());
			maxEtaResidual = Math.max(maxEtaResidual, result.maxEtaResidual());
			maxStaticAnchorError = Math.max(maxStaticAnchorError, result.staticAnchorError());
			if (result.passed()) {
				passed++;
			} else {
				failed++;
			}
		}
		int unexpected = 0;
		for (LookupAcceptanceResult result : results) {
			if (!targetKeys.contains(key(result.presetName(), result.caseName()))) {
				unexpected++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && results.size() == targets.size();
		boolean allPassed = failed == 0 && passed == targets.size();
		boolean acceptanceReady = reviewedImportReady
				&& interpolationPolicyReady
				&& lookupInterpolationExecuted
				&& allPresent
				&& allPassed;
		boolean exportAllowed = acceptanceReady && compactReferenceReviewed;
		return new LookupAcceptanceSummary(
				reviewedImportReady,
				interpolationPolicyReady,
				lookupInterpolationExecuted,
				compactReferenceReviewed,
				targets.size(),
				results.size(),
				passed,
				failed,
				missing,
				unexpected,
				minObservedNeighbors == Integer.MAX_VALUE ? 0 : minObservedNeighbors,
				maxCtShapeOvershoot,
				Double.isInfinite(minCpCoefficient) ? 0.0 : minCpCoefficient,
				maxEtaResidual,
				maxStaticAnchorError,
				allPresent,
				allPassed,
				acceptanceReady,
				exportAllowed,
				false,
				false,
				acceptanceReady ? "READY" : "BLOCKED",
				message(reviewedImportReady, interpolationPolicyReady, lookupInterpolationExecuted,
						allPresent, allPassed, compactReferenceReviewed),
				sourceRuntimeInfo
		);
	}

	private static LookupAcceptanceTarget target(
			PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract
	) {
		return new LookupAcceptanceTarget(
				contract.presetName(),
				contract.caseName(),
				contract.minimumPerformanceNeighborRows(),
				MAX_CT_SHAPE_OVERSHOOT,
				MIN_CP_COEFFICIENT,
				MAX_ETA_RESIDUAL,
				contract.staticAnchorPreserved() ? MAX_STATIC_ANCHOR_ERROR : 0.0,
				contract.staticAnchorPreserved(),
				contract.postReviewFullSimulationInterpolationAllowed()
						? "full-simulation-ct-cp-j-lookup-reference"
						: "performance-only-ct-cp-j-lookup-reference"
		);
	}

	private static List<LookupAcceptanceResult> passingResults(List<LookupAcceptanceTarget> targets) {
		return targets.stream()
				.map(PropellerArchiveCtCpJLookupAcceptanceGate::passingResult)
				.toList();
	}

	private static LookupAcceptanceResult passingResult(LookupAcceptanceTarget target) {
		return result(
				target.presetName(),
				target.caseName(),
				target.minNeighborRows() + 2,
				0.0,
				0.010,
				target.maxEtaResidual() * 0.50,
				target.requiresStaticAnchorPreservation()
						? target.maxStaticAnchorError() * 0.50
						: 0.0
		);
	}

	private static LookupAcceptanceResult failingResult(LookupAcceptanceTarget target) {
		return result(
				target.presetName(),
				target.caseName(),
				Math.max(0, target.minNeighborRows() - 1),
				0.010,
				0.0,
				target.maxEtaResidual() * 1.50,
				target.maxStaticAnchorError() + 0.010
		);
	}

	private static LookupAcceptanceResult findResult(
			List<LookupAcceptanceResult> results,
			String presetName,
			String caseName
	) {
		for (LookupAcceptanceResult result : results) {
			if (presetName.equals(result.presetName()) && caseName.equals(result.caseName())) {
				return result;
			}
		}
		return null;
	}

	private static LookupAcceptanceExtrema extrema(List<LookupAcceptanceScenario> scenarios) {
		int accepted = 0;
		int exportAllowed = 0;
		int runtime = 0;
		int gameplay = 0;
		int maxExpected = 0;
		int maxObserved = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		double maxEta = 0.0;
		for (LookupAcceptanceScenario scenario : scenarios) {
			LookupAcceptanceSummary summary = scenario.summary();
			if (summary.lookupAcceptanceReady()) {
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
			maxEta = Math.max(maxEta, summary.maxEtaResidual());
		}
		return new LookupAcceptanceExtrema(
				scenarios.size(),
				accepted,
				scenarios.size() - accepted,
				maxExpected,
				maxObserved,
				maxMissing,
				maxFailed,
				maxEta,
				exportAllowed,
				runtime,
				gameplay
		);
	}

	private static String message(
			boolean reviewedImportReady,
			boolean interpolationPolicyReady,
			boolean lookupInterpolationExecuted,
			boolean allPresent,
			boolean allPassed,
			boolean compactReferenceReviewed
	) {
		if (!reviewedImportReady) {
			return "reviewed-import-missing";
		}
		if (!interpolationPolicyReady) {
			return "lookup-interpolation-policy-blocked";
		}
		if (!lookupInterpolationExecuted) {
			return "lookup-interpolation-results-missing";
		}
		if (!allPresent) {
			return "lookup-result-set-incomplete";
		}
		if (!allPassed) {
			return "lookup-result-failed";
		}
		if (!compactReferenceReviewed) {
			return "lookup-accepted-reference-review-blocked";
		}
		return "lookup-acceptance-ready";
	}

	private static String key(String presetName, String caseName) {
		return presetName + "\u0000" + caseName;
	}
}
