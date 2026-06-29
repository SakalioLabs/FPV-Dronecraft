package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Blackbox-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook blackbox acceptance remains closed until every planned target-omega derate regression has explicit passing results; passing opens only manual control-hook review, while preset candidate derates, runtime coupling, playable export, and gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_ROW_COUNT = 8;
	public static final int SUMMARY_ROW_COUNT = 8;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate() {
	}

	public record DerateControlHookBlackboxRegressionResult(
			String scenarioName,
			String presetName,
			String regressionCaseName,
			double primaryErrorRatio,
			double secondaryErrorRatio,
			int sampleCount,
			int physicalConstraintViolationCount,
			boolean passed,
			String status
	) {
	}

	public record DerateControlHookBlackboxAcceptanceSummary(
			int plannedRegressionRunCount,
			int observedResultCount,
			int missingResultCount,
			int unexpectedResultCount,
			int failedResultCount,
			int passedResultCount,
			int acceptedRegressionRunCount,
			int manualControlHookReviewCandidateCount,
			boolean allPlannedRunsHaveResults,
			boolean allObservedResultsPassed,
			boolean blackboxRegressionAcceptanceReady,
			boolean manualControlHookReviewAllowed,
			boolean runtimeImplementationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record DerateControlHookBlackboxAcceptanceScenario(
			String scenarioName,
			DerateControlHookBlackboxAcceptanceSummary summary
	) {
	}

	public record DerateControlHookBlackboxAcceptanceExtrema(
			int scenarioCount,
			int acceptedScenarioCount,
			int manualControlHookReviewAllowedScenarioCount,
			int maxPlannedRegressionRunCount,
			int maxObservedResultCount,
			int maxMissingResultCount,
			int maxFailedResultCount,
			int runtimeImplementationAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int playableReferenceAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record DerateControlHookBlackboxAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<DerateControlHookBlackboxAcceptanceScenario> scenarios,
			DerateControlHookBlackboxAcceptanceExtrema extrema
	) {
		public DerateControlHookBlackboxAcceptanceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static DerateControlHookBlackboxAcceptanceAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionAudit matrix =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.audit();
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow> currentRows =
						plannedRows(matrix, "synthetic_derate_validation_all_pass");
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow> plannedReadyRows =
						plannedRows(matrix, "synthetic_control_hook_ready_reviewed");
		List<DerateControlHookBlackboxRegressionResult> currentResults = currentResults(currentRows);
		List<DerateControlHookBlackboxRegressionResult> passingResults = passingResults(plannedReadyRows);
		List<DerateControlHookBlackboxRegressionResult> failedResults = new ArrayList<>(passingResults);
		failedResults.set(0, failingResult(plannedReadyRows.get(0)));

		List<DerateControlHookBlackboxAcceptanceScenario> scenarios = List.of(
				scenario(
						"current_control_hook_blackbox_blocked",
						"synthetic_derate_validation_all_pass",
						matrix,
						currentResults,
						"current-target-omega-hook-blackbox-apDrone-forward-punchout-failed"
				),
				scenario(
						"synthetic_control_hook_blackbox_results_missing",
						"synthetic_control_hook_ready_reviewed",
						matrix,
						List.of(),
						"synthetic-control-hook-results-missing"
				),
				scenario(
						"synthetic_control_hook_blackbox_all_pass",
						"synthetic_control_hook_ready_reviewed",
						matrix,
						passingResults,
						"synthetic-control-hook-blackbox-all-pass"
				),
				scenario(
						"synthetic_control_hook_blackbox_one_failed",
						"synthetic_control_hook_ready_reviewed",
						matrix,
						failedResults,
						"synthetic-control-hook-blackbox-one-failed"
				)
		);
		return new DerateControlHookBlackboxAcceptanceAudit(
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

	public static DerateControlHookBlackboxAcceptanceScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate blackbox acceptance scenario: " + scenarioName));
	}

	public static DerateControlHookBlackboxRegressionResult result(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionRunRow target,
			double primaryErrorRatio,
			double secondaryErrorRatio,
			int sampleCount,
			int physicalConstraintViolationCount
	) {
		if (target == null) {
			throw new IllegalArgumentException("target regression row must not be null.");
		}
		if (!target.regressionRunPlanned()) {
			throw new IllegalArgumentException("target regression row must be planned before result capture.");
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
		return new DerateControlHookBlackboxRegressionResult(
				target.scenarioName(),
				target.presetName(),
				target.regressionCaseName(),
				primaryErrorRatio,
				secondaryErrorRatio,
				sampleCount,
				physicalConstraintViolationCount,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static DerateControlHookBlackboxAcceptanceSummary gate(
			String scenarioName,
			String matrixScenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionAudit matrix,
			List<DerateControlHookBlackboxRegressionResult> results,
			String sourceRuntimeInfo
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (matrixScenarioName == null || matrixScenarioName.isBlank()) {
			throw new IllegalArgumentException("matrixScenarioName must not be blank.");
		}
		if (matrix == null) {
			throw new IllegalArgumentException("matrix must not be null.");
		}
		if (results == null) {
			throw new IllegalArgumentException("results must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow> plannedRows = plannedRows(matrix, matrixScenarioName);
		Set<String> plannedKeys = new HashSet<>();
		Set<String> plannedPresetKeys = new HashSet<>();
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow row : plannedRows) {
			plannedKeys.add(key(row.scenarioName(), row.presetName(), row.regressionCaseName()));
			plannedPresetKeys.add(row.presetName());
		}
		Set<String> resultKeys = new HashSet<>();
		for (DerateControlHookBlackboxRegressionResult result : results) {
			if (result == null || result.scenarioName() == null || result.presetName() == null
					|| result.regressionCaseName() == null || result.scenarioName().isBlank()
					|| result.presetName().isBlank() || result.regressionCaseName().isBlank()) {
				throw new IllegalArgumentException(
						"results must include stable scenario, preset, and regression case names.");
			}
			String key = key(result.scenarioName(), result.presetName(), result.regressionCaseName());
			if (!resultKeys.add(key)) {
				throw new IllegalArgumentException("duplicate cold-air derate blackbox result: " + key);
			}
		}

		int passed = 0;
		int failed = 0;
		int missing = 0;
		for (PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow row : plannedRows) {
			DerateControlHookBlackboxRegressionResult result =
					findResult(results, row.scenarioName(), row.presetName(), row.regressionCaseName());
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
		for (DerateControlHookBlackboxRegressionResult result : results) {
			if (!plannedKeys.contains(key(result.scenarioName(), result.presetName(), result.regressionCaseName()))) {
				unexpected++;
			}
		}

		boolean allPresent = plannedRows.size() > 0
				&& missing == 0
				&& unexpected == 0
				&& results.size() == plannedRows.size();
		boolean allPassed = plannedRows.size() > 0 && passed == plannedRows.size() && failed == 0;
		boolean acceptanceReady = allPresent && allPassed;
		return new DerateControlHookBlackboxAcceptanceSummary(
				plannedRows.size(),
				results.size(),
				missing,
				unexpected,
				failed,
				passed,
				passed,
				acceptanceReady ? plannedPresetKeys.size() : 0,
				allPresent,
				allPassed,
				acceptanceReady,
				acceptanceReady,
				false,
				false,
				false,
				false,
				acceptanceReady ? "READY" : "BLOCKED",
				message(plannedRows.size(), missing, unexpected, failed),
				sourceRuntimeInfo
		);
	}

	private static DerateControlHookBlackboxAcceptanceScenario scenario(
			String scenarioName,
			String matrixScenarioName,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionAudit matrix,
			List<DerateControlHookBlackboxRegressionResult> results,
			String sourceRuntimeInfo
	) {
		return new DerateControlHookBlackboxAcceptanceScenario(
				scenarioName,
				gate(scenarioName, matrixScenarioName, matrix, results, sourceRuntimeInfo)
		);
	}

	private static List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
			.DerateControlHookBlackboxRegressionRunRow> plannedRows(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionAudit matrix,
			String scenarioName
	) {
		return matrix.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
						.DerateControlHookBlackboxRegressionRunRow::regressionRunPlanned)
				.toList();
	}

	private static List<DerateControlHookBlackboxRegressionResult> passingResults(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionRunRow> rows
	) {
		return rows.stream()
				.map(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						::passingResult)
				.toList();
	}

	private static List<DerateControlHookBlackboxRegressionResult> currentResults(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionRunRow> rows
	) {
		return rows.stream()
				.map(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						::currentResult)
				.toList();
	}

	private static DerateControlHookBlackboxRegressionResult currentResult(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionRunRow row
	) {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewRow result =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.row(row.presetName(), row.regressionCaseName());
		return result(
				row,
				result.primaryErrorRatio(),
				result.secondaryErrorRatio(),
				result.sampleCount(),
				result.physicalConstraintViolationCount()
		);
	}

	private static DerateControlHookBlackboxRegressionResult passingResult(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionRunRow row
	) {
		return result(
				row,
				row.maxPrimaryErrorRatio() * 0.45,
				row.maxSecondaryErrorRatio() * 0.40,
				row.minSampleCount() + 6,
				0
		);
	}

	private static DerateControlHookBlackboxRegressionResult failingResult(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionRunRow row
	) {
		return result(
				row,
				row.maxPrimaryErrorRatio() * 1.60,
				row.maxSecondaryErrorRatio() * 0.40,
				row.minSampleCount() + 6,
				1
		);
	}

	private static DerateControlHookBlackboxRegressionResult findResult(
			List<DerateControlHookBlackboxRegressionResult> results,
			String scenarioName,
			String presetName,
			String regressionCaseName
	) {
		for (DerateControlHookBlackboxRegressionResult result : results) {
			if (scenarioName.equals(result.scenarioName())
					&& presetName.equals(result.presetName())
					&& regressionCaseName.equals(result.regressionCaseName())) {
				return result;
			}
		}
		return null;
	}

	private static boolean passes(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
					.DerateControlHookBlackboxRegressionRunRow row,
			DerateControlHookBlackboxRegressionResult result
	) {
		return result.passed()
				&& "PASS".equals(result.status())
				&& result.primaryErrorRatio() <= row.maxPrimaryErrorRatio()
				&& result.secondaryErrorRatio() <= row.maxSecondaryErrorRatio()
				&& result.sampleCount() >= row.minSampleCount()
				&& result.physicalConstraintViolationCount() == 0;
	}

	private static DerateControlHookBlackboxAcceptanceExtrema extrema(
			List<DerateControlHookBlackboxAcceptanceScenario> scenarios
	) {
		int accepted = 0;
		int manual = 0;
		int maxPlanned = 0;
		int maxObserved = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		int runtimeImplementation = 0;
		int runtime = 0;
		int playable = 0;
		int gameplay = 0;
		for (DerateControlHookBlackboxAcceptanceScenario scenario : scenarios) {
			DerateControlHookBlackboxAcceptanceSummary summary = scenario.summary();
			if (summary.blackboxRegressionAcceptanceReady()) {
				accepted++;
			}
			if (summary.manualControlHookReviewAllowed()) {
				manual++;
			}
			if (summary.runtimeImplementationAllowed()) {
				runtimeImplementation++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.playableReferenceAllowed()) {
				playable++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxPlanned = Math.max(maxPlanned, summary.plannedRegressionRunCount());
			maxObserved = Math.max(maxObserved, summary.observedResultCount());
			maxMissing = Math.max(maxMissing, summary.missingResultCount());
			maxFailed = Math.max(maxFailed, summary.failedResultCount());
		}
		return new DerateControlHookBlackboxAcceptanceExtrema(
				scenarios.size(),
				accepted,
				manual,
				maxPlanned,
				maxObserved,
				maxMissing,
				maxFailed,
				runtimeImplementation,
				runtime,
				playable,
				gameplay
		);
	}

	private static String message(int planned, int missing, int unexpected, int failed) {
		if (planned == 0) {
			return "blackbox-regression-run-not-planned";
		}
		if (missing > 0) {
			return "blackbox-regression-result-set-incomplete";
		}
		if (unexpected > 0) {
			return "blackbox-regression-result-unexpected";
		}
		if (failed > 0) {
			return "blackbox-regression-result-failed";
		}
		return "target-omega-blackbox-regression-accepted-for-manual-control-hook-review";
	}

	private static String key(String scenarioName, String presetName, String regressionCaseName) {
		return scenarioName + "/" + presetName + "/" + regressionCaseName;
	}
}
