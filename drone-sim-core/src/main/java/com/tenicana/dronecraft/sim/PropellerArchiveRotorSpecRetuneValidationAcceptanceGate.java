package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveRotorSpecRetuneValidationAcceptanceGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Validation-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune validation acceptance remains closed until every planned offline validation row has explicit passing results; passing opens only manual DroneConfig patch review, while direct config patching, runtime coupling, and gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_ROW_COUNT = 16;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetuneValidationAcceptanceGate() {
	}

	public record RetuneValidationResult(
			String presetName,
			String validationCaseName,
			double primaryErrorRatio,
			double secondaryErrorRatio,
			int sampleCount,
			int physicalConstraintViolationCount,
			boolean passed,
			String status
	) {
	}

	public record RetuneValidationAcceptanceSummary(
			int plannedValidationRunCount,
			int observedResultCount,
			int missingResultCount,
			int unexpectedResultCount,
			int failedResultCount,
			int passedResultCount,
			int validationAcceptedRunCount,
			int manualConfigPatchReviewCandidateCount,
			boolean allPlannedRunsHaveResults,
			boolean allObservedResultsPassed,
			boolean validationAcceptanceReady,
			boolean manualConfigPatchReviewAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RetuneValidationAcceptanceScenario(
			String scenarioName,
			RetuneValidationAcceptanceSummary summary
	) {
	}

	public record RetuneValidationAcceptanceExtrema(
			int scenarioCount,
			int acceptedScenarioCount,
			int manualConfigPatchReviewAllowedScenarioCount,
			int maxPlannedValidationRunCount,
			int maxObservedResultCount,
			int maxMissingResultCount,
			int maxFailedResultCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneValidationAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<RetuneValidationAcceptanceScenario> scenarios,
			RetuneValidationAcceptanceExtrema extrema
	) {
		public RetuneValidationAcceptanceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneValidationAcceptanceAudit audit() {
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit current =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit();
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit ready =
				readyValidationRunAudit();
		List<PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow> plannedRows =
				plannedRows(ready);
		List<RetuneValidationResult> passingResults = passingResults(plannedRows);
		List<RetuneValidationResult> failedResults = new ArrayList<>(passingResults);
		failedResults.set(0, failingResult(plannedRows.get(0)));

		List<RetuneValidationAcceptanceScenario> scenarios = List.of(
				scenario("current_retune_validation_blocked", current, List.of(),
						"current-retune-validation-blocked"),
				scenario("synthetic_ready_validation_results_missing", ready, List.of(),
						"synthetic-ready-validation-results-missing"),
				scenario("synthetic_ready_validation_all_pass", ready, passingResults,
						"synthetic-ready-validation-all-pass"),
				scenario("synthetic_ready_validation_one_failed", ready, failedResults,
						"synthetic-ready-validation-one-failed")
		);
		return new RetuneValidationAcceptanceAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static RetuneValidationAcceptanceScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune validation acceptance scenario: " + scenarioName));
	}

	public static RetuneValidationResult result(
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow target,
			double primaryErrorRatio,
			double secondaryErrorRatio,
			int sampleCount,
			int physicalConstraintViolationCount
	) {
		if (target == null) {
			throw new IllegalArgumentException("target validation row must not be null.");
		}
		if (!target.validationRunPlanned()) {
			throw new IllegalArgumentException("target validation row must be planned before result capture.");
		}
		if (!Double.isFinite(primaryErrorRatio) || primaryErrorRatio < 0.0) {
			throw new IllegalArgumentException("primaryErrorRatio must be finite and nonnegative.");
		}
		if (!Double.isFinite(secondaryErrorRatio) || secondaryErrorRatio < 0.0) {
			throw new IllegalArgumentException("secondaryErrorRatio must be finite and nonnegative.");
		}
		if (sampleCount < 0) {
			throw new IllegalArgumentException("sampleCount must be nonnegative.");
		}
		if (physicalConstraintViolationCount < 0) {
			throw new IllegalArgumentException("physicalConstraintViolationCount must be nonnegative.");
		}
		boolean passed = primaryErrorRatio <= target.maxPrimaryErrorRatio()
				&& secondaryErrorRatio <= target.maxSecondaryErrorRatio()
				&& sampleCount >= target.minSampleCount()
				&& physicalConstraintViolationCount == 0;
		return new RetuneValidationResult(
				target.presetName(),
				target.validationCaseName(),
				primaryErrorRatio,
				secondaryErrorRatio,
				sampleCount,
				physicalConstraintViolationCount,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static RetuneValidationAcceptanceSummary gate(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit runAudit,
			List<RetuneValidationResult> results,
			String sourceRuntimeInfo
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (runAudit == null) {
			throw new IllegalArgumentException("runAudit must not be null.");
		}
		if (results == null) {
			throw new IllegalArgumentException("results must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		List<PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow> plannedRows =
				plannedRows(runAudit);
		Set<String> plannedKeys = new HashSet<>();
		Set<String> plannedPresetKeys = new HashSet<>();
		for (PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow row : plannedRows) {
			plannedKeys.add(key(row.presetName(), row.validationCaseName()));
			plannedPresetKeys.add(row.presetName());
		}
		Set<String> resultKeys = new HashSet<>();
		for (RetuneValidationResult result : results) {
			if (result == null || result.presetName() == null || result.validationCaseName() == null
					|| result.presetName().isBlank() || result.validationCaseName().isBlank()) {
				throw new IllegalArgumentException("results must include stable preset and validation case names.");
			}
			String key = key(result.presetName(), result.validationCaseName());
			if (!resultKeys.add(key)) {
				throw new IllegalArgumentException("duplicate RotorSpec retune validation result: " + key);
			}
		}

		int passed = 0;
		int failed = 0;
		int missing = 0;
		for (PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow row : plannedRows) {
			RetuneValidationResult result = findResult(results, row.presetName(), row.validationCaseName());
			if (result == null) {
				missing++;
				continue;
			}
			if (passes(row, result)) {
				passed++;
			} else {
				failed++;
			}
		}
		int unexpected = 0;
		for (RetuneValidationResult result : results) {
			if (!plannedKeys.contains(key(result.presetName(), result.validationCaseName()))) {
				unexpected++;
			}
		}

		boolean allPresent = plannedRows.size() > 0
				&& missing == 0
				&& unexpected == 0
				&& results.size() == plannedRows.size();
		boolean allPassed = plannedRows.size() > 0 && passed == plannedRows.size() && failed == 0;
		boolean acceptanceReady = allPresent && allPassed;
		boolean manualReviewAllowed = acceptanceReady;
		int manualReviewCandidates = acceptanceReady ? plannedPresetKeys.size() : 0;
		return new RetuneValidationAcceptanceSummary(
				plannedRows.size(),
				results.size(),
				missing,
				unexpected,
				failed,
				passed,
				passed,
				manualReviewCandidates,
				allPresent,
				allPassed,
				acceptanceReady,
				manualReviewAllowed,
				false,
				false,
				false,
				acceptanceReady ? "READY" : "BLOCKED",
				message(plannedRows.size(), missing, unexpected, failed),
				sourceRuntimeInfo
		);
	}

	private static RetuneValidationAcceptanceScenario scenario(
			String scenarioName,
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit runAudit,
			List<RetuneValidationResult> results,
			String sourceRuntimeInfo
	) {
		return new RetuneValidationAcceptanceScenario(
				scenarioName,
				gate(scenarioName, runAudit, results, sourceRuntimeInfo)
		);
	}

	private static PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit readyValidationRunAudit() {
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
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit delta =
				PropellerArchiveRotorSpecConfigDeltaReport.audit(bridge);
		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessAudit readiness =
				PropellerArchiveRotorSpecRetuneReadinessGate.audit(delta);
		return PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit(readiness);
	}

	private static List<PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow> plannedRows(
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit runAudit
	) {
		return runAudit.rows().stream()
				.filter(PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow::validationRunPlanned)
				.toList();
	}

	private static List<RetuneValidationResult> passingResults(
			List<PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow> rows
	) {
		return rows.stream()
				.map(PropellerArchiveRotorSpecRetuneValidationAcceptanceGate::passingResult)
				.toList();
	}

	private static RetuneValidationResult passingResult(
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow row
	) {
		return result(
				row,
				row.maxPrimaryErrorRatio() * 0.50,
				row.maxSecondaryErrorRatio() * 0.40,
				row.minSampleCount() + 4,
				0
		);
	}

	private static RetuneValidationResult failingResult(
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow row
	) {
		return result(
				row,
				row.maxPrimaryErrorRatio() * 1.50,
				row.maxSecondaryErrorRatio() * 0.40,
				row.minSampleCount() + 4,
				1
		);
	}

	private static RetuneValidationResult findResult(
			List<RetuneValidationResult> results,
			String presetName,
			String validationCaseName
	) {
		for (RetuneValidationResult result : results) {
			if (presetName.equals(result.presetName())
					&& validationCaseName.equals(result.validationCaseName())) {
				return result;
			}
		}
		return null;
	}

	private static boolean passes(
			PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow row,
			RetuneValidationResult result
	) {
		return result.passed()
				&& "PASS".equals(result.status())
				&& result.primaryErrorRatio() <= row.maxPrimaryErrorRatio()
				&& result.secondaryErrorRatio() <= row.maxSecondaryErrorRatio()
				&& result.sampleCount() >= row.minSampleCount()
				&& result.physicalConstraintViolationCount() == 0;
	}

	private static RetuneValidationAcceptanceExtrema extrema(List<RetuneValidationAcceptanceScenario> scenarios) {
		int accepted = 0;
		int manual = 0;
		int maxPlanned = 0;
		int maxObserved = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		int patch = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneValidationAcceptanceScenario scenario : scenarios) {
			RetuneValidationAcceptanceSummary summary = scenario.summary();
			if (summary.validationAcceptanceReady()) {
				accepted++;
			}
			if (summary.manualConfigPatchReviewAllowed()) {
				manual++;
			}
			if (summary.configPatchAllowed()) {
				patch++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxPlanned = Math.max(maxPlanned, summary.plannedValidationRunCount());
			maxObserved = Math.max(maxObserved, summary.observedResultCount());
			maxMissing = Math.max(maxMissing, summary.missingResultCount());
			maxFailed = Math.max(maxFailed, summary.failedResultCount());
		}
		return new RetuneValidationAcceptanceExtrema(
				scenarios.size(),
				accepted,
				manual,
				maxPlanned,
				maxObserved,
				maxMissing,
				maxFailed,
				patch,
				runtime,
				gameplay
		);
	}

	private static String message(int planned, int missing, int unexpected, int failed) {
		if (planned == 0) {
			return "validation-run-not-planned";
		}
		if (missing > 0) {
			return "validation-result-set-incomplete";
		}
		if (unexpected > 0) {
			return "validation-result-unexpected";
		}
		if (failed > 0) {
			return "validation-result-failed";
		}
		return "retune-validation-accepted-for-manual-config-review";
	}

	private static String key(String presetName, String validationCaseName) {
		return presetName + "/" + validationCaseName;
	}
}
