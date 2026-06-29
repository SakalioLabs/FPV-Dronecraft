package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Validation-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate validation acceptance remains closed until every planned cold-air derate validation row has explicit passing results; passing opens only manual derate review while playable reference export, DroneConfig patching, runtime coupling, and gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_ROW_COUNT = 18;
	public static final int SUMMARY_ROW_COUNT = 11;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate() {
	}

	public record DerateValidationResult(
			String presetName,
			String ambientCaseName,
			String validationCaseName,
			double primaryErrorRatio,
			double secondaryErrorRatio,
			int sampleCount,
			int physicalConstraintViolationCount,
			boolean passed,
			String status
	) {
	}

	public record DerateValidationAcceptanceSummary(
			int plannedValidationRunCount,
			int observedResultCount,
			int missingResultCount,
			int unexpectedResultCount,
			int failedResultCount,
			int passedResultCount,
			int validationAcceptedRunCount,
			int manualDerateReviewCandidateCount,
			boolean allPlannedRunsHaveResults,
			boolean allObservedResultsPassed,
			boolean derateValidationAcceptanceReady,
			boolean manualDerateReviewAllowed,
			boolean playableReferenceAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DerateValidationAcceptanceScenario(
			String scenarioName,
			DerateValidationAcceptanceSummary summary
	) {
	}

	public record DerateValidationAcceptanceExtrema(
			int scenarioCount,
			int acceptedScenarioCount,
			int manualDerateReviewAllowedScenarioCount,
			int maxPlannedValidationRunCount,
			int maxObservedResultCount,
			int maxMissingResultCount,
			int maxFailedResultCount,
			int playableReferenceAllowedScenarioCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record DerateValidationAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<DerateValidationAcceptanceScenario> scenarios,
			DerateValidationAcceptanceExtrema extrema
	) {
		public DerateValidationAcceptanceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static DerateValidationAcceptanceAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationAudit matrix =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.audit();
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow> currentRows =
						plannedRows(matrix, "current_retune_validation_blocked");
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow> readyRows =
						plannedRows(matrix, "synthetic_ready_validation_all_pass");
		List<DerateValidationResult> passingResults = passingResults(readyRows);
		List<DerateValidationResult> failedResults = new ArrayList<>(passingResults);
		failedResults.set(0, failingResult(readyRows.get(0)));

		List<DerateValidationAcceptanceScenario> scenarios = List.of(
				scenario("current_derate_validation_blocked", currentRows, List.of()),
				scenario("synthetic_derate_validation_results_missing", readyRows, List.of()),
				scenario("synthetic_derate_validation_all_pass", readyRows, passingResults),
				scenario("synthetic_derate_validation_one_failed", readyRows, failedResults)
		);
		return new DerateValidationAcceptanceAudit(
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

	public static DerateValidationAcceptanceScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate validation acceptance scenario: " + scenarioName));
	}

	public static DerateValidationResult result(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
					.RetuneAmbientCompressibilityDerateValidationRow target,
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
		return new DerateValidationResult(
				target.presetName(),
				target.ambientCaseName(),
				target.validationCaseName(),
				primaryErrorRatio,
				secondaryErrorRatio,
				sampleCount,
				physicalConstraintViolationCount,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static DerateValidationAcceptanceSummary gate(
			String scenarioName,
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
					.RetuneAmbientCompressibilityDerateValidationRow> plannedRows,
			List<DerateValidationResult> results
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (plannedRows == null) {
			throw new IllegalArgumentException("plannedRows must not be null.");
		}
		if (results == null) {
			throw new IllegalArgumentException("results must not be null.");
		}
		Set<String> plannedKeys = new HashSet<>();
		Set<String> plannedTargetKeys = new HashSet<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow row : plannedRows) {
			if (row == null) {
				throw new IllegalArgumentException("plannedRows must not contain null rows.");
			}
			plannedKeys.add(key(row.presetName(), row.ambientCaseName(), row.validationCaseName()));
			plannedTargetKeys.add(row.presetName() + "/" + row.ambientCaseName());
		}
		Set<String> resultKeys = new HashSet<>();
		for (DerateValidationResult result : results) {
			if (result == null || result.presetName() == null || result.ambientCaseName() == null
					|| result.validationCaseName() == null || result.presetName().isBlank()
					|| result.ambientCaseName().isBlank() || result.validationCaseName().isBlank()) {
				throw new IllegalArgumentException("results must include stable preset, ambient, and validation case names.");
			}
			String key = key(result.presetName(), result.ambientCaseName(), result.validationCaseName());
			if (!resultKeys.add(key)) {
				throw new IllegalArgumentException("duplicate RotorSpec ambient derate validation result: " + key);
			}
		}

		int missing = 0;
		int passed = 0;
		int failed = 0;
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow row : plannedRows) {
			DerateValidationResult result = findResult(
					results,
					row.presetName(),
					row.ambientCaseName(),
					row.validationCaseName()
			);
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
		for (DerateValidationResult result : results) {
			if (!plannedKeys.contains(key(result.presetName(), result.ambientCaseName(), result.validationCaseName()))) {
				unexpected++;
			}
		}
		boolean allPresent = plannedRows.size() > 0
				&& missing == 0
				&& unexpected == 0
				&& results.size() == plannedRows.size();
		boolean allPassed = plannedRows.size() > 0 && passed == plannedRows.size() && failed == 0;
		boolean accepted = allPresent && allPassed;
		int manualCandidates = accepted ? plannedTargetKeys.size() : 0;
		return new DerateValidationAcceptanceSummary(
				plannedRows.size(),
				results.size(),
				missing,
				unexpected,
				failed,
				passed,
				passed,
				manualCandidates,
				allPresent,
				allPassed,
				accepted,
				accepted,
				false,
				false,
				false,
				false,
				accepted ? "READY" : "BLOCKED",
				message(plannedRows.size(), missing, unexpected, failed, accepted)
		);
	}

	private static DerateValidationAcceptanceScenario scenario(
			String scenarioName,
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
					.RetuneAmbientCompressibilityDerateValidationRow> plannedRows,
			List<DerateValidationResult> results
	) {
		return new DerateValidationAcceptanceScenario(
				scenarioName,
				gate(scenarioName, plannedRows, results)
		);
	}

	private static List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
			.RetuneAmbientCompressibilityDerateValidationRow> plannedRows(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
					.RetuneAmbientCompressibilityDerateValidationAudit matrix,
			String scenarioName
	) {
		return matrix.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
						.RetuneAmbientCompressibilityDerateValidationRow::validationRunPlanned)
				.toList();
	}

	private static List<DerateValidationResult> passingResults(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
					.RetuneAmbientCompressibilityDerateValidationRow> plannedRows
	) {
		return plannedRows.stream()
				.map(row -> result(
						row,
						row.maxPrimaryErrorRatio() * 0.50,
						row.maxSecondaryErrorRatio() * 0.50,
						row.minSampleCount() + 2,
						0
				))
				.toList();
	}

	private static DerateValidationResult failingResult(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
					.RetuneAmbientCompressibilityDerateValidationRow row
	) {
		return result(
				row,
				row.maxPrimaryErrorRatio() * 1.10,
				row.maxSecondaryErrorRatio() * 0.50,
				row.minSampleCount() + 2,
				0
		);
	}

	private static boolean passes(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
					.RetuneAmbientCompressibilityDerateValidationRow row,
			DerateValidationResult result
	) {
		return result.passed()
				&& result.primaryErrorRatio() <= row.maxPrimaryErrorRatio()
				&& result.secondaryErrorRatio() <= row.maxSecondaryErrorRatio()
				&& result.sampleCount() >= row.minSampleCount()
				&& result.physicalConstraintViolationCount() == 0;
	}

	private static DerateValidationResult findResult(
			List<DerateValidationResult> results,
			String presetName,
			String ambientCaseName,
			String validationCaseName
	) {
		return results.stream()
				.filter(result -> presetName.equals(result.presetName())
						&& ambientCaseName.equals(result.ambientCaseName())
						&& validationCaseName.equals(result.validationCaseName()))
				.findFirst()
				.orElse(null);
	}

	private static DerateValidationAcceptanceExtrema extrema(
			List<DerateValidationAcceptanceScenario> scenarios
	) {
		int accepted = 0;
		int manual = 0;
		int maxPlanned = 0;
		int maxObserved = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (DerateValidationAcceptanceScenario scenario : scenarios) {
			DerateValidationAcceptanceSummary summary = scenario.summary();
			if (summary.derateValidationAcceptanceReady()) {
				accepted++;
			}
			if (summary.manualDerateReviewAllowed()) {
				manual++;
			}
			maxPlanned = Math.max(maxPlanned, summary.plannedValidationRunCount());
			maxObserved = Math.max(maxObserved, summary.observedResultCount());
			maxMissing = Math.max(maxMissing, summary.missingResultCount());
			maxFailed = Math.max(maxFailed, summary.failedResultCount());
			if (summary.playableReferenceAllowed()) {
				playable++;
			}
			if (summary.configPatchAllowed()) {
				config++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new DerateValidationAcceptanceExtrema(
				scenarios.size(),
				accepted,
				manual,
				maxPlanned,
				maxObserved,
				maxMissing,
				maxFailed,
				playable,
				config,
				runtime,
				gameplay
		);
	}

	private static String message(
			int plannedRows,
			int missing,
			int unexpected,
			int failed,
			boolean accepted
	) {
		if (plannedRows == 0) {
			return "cold-air-derate-validation-not-planned";
		}
		if (missing > 0 || unexpected > 0) {
			return "cold-air-derate-validation-result-set-incomplete";
		}
		if (failed > 0) {
			return "cold-air-derate-validation-result-failed";
		}
		if (accepted) {
			return "cold-air-derate-validation-accepted-for-manual-review";
		}
		return "cold-air-derate-validation-blocked";
	}

	private static String key(String presetName, String ambientCaseName, String validationCaseName) {
		return presetName + "/" + ambientCaseName + "/" + validationCaseName;
	}
}
