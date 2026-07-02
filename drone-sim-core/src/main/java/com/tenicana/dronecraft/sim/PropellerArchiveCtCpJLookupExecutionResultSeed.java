package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJLookupExecutionResultSeed {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Execution-Result-Seed-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup execution result seed defines the exact per-target evidence slots required after lab run authorization and before acceptance; current rows are unavailable, authorized synthetic rows remain pending, and no row enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int SEED_ROW_COUNT =
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.TARGET_RUN_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SEED_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String RECORD_RESULT_EVIDENCE_ACTION =
			"record-lookup-execution-result-evidence-before-acceptance";
	public static final String FEED_ACCEPTANCE_ACTION =
			"feed-lookup-execution-results-into-acceptance-gate";
	public static final String NEXT_REQUIRED_ACTION = RECORD_RESULT_EVIDENCE_ACTION;

	private static final double EPSILON = 1.0e-9;

	private PropellerArchiveCtCpJLookupExecutionResultSeed() {
	}

	public record LookupExecutionResultSeedRow(
			String scenarioName,
			int queueOrdinal,
			String presetName,
			String caseName,
			double queryAdvanceRatioJ,
			double queryRpm,
			int minimumNeighborRowsRequired,
			double maxCtShapeOvershootAllowed,
			double minCpCoefficientAllowed,
			double maxEtaResidualAllowed,
			double maxStaticAnchorErrorAllowed,
			boolean requiresStaticAnchorPreservation,
			String downstreamUse,
			boolean fullSimulationCandidate,
			boolean performanceOnlyCandidate,
			boolean lookupRunAuthorized,
			boolean lookupResultEvidencePresent,
			int observedNeighborRows,
			double ctCoefficient,
			double cpCoefficient,
			double eta,
			double maxCtShapeOvershoot,
			double minCpCoefficient,
			double maxEtaResidual,
			double staticAnchorError,
			boolean archiveCurveShapeGuardInherited,
			int negativeThrustTailExecutionInputRowCount,
			boolean lookupExecutionAccepted,
			String lookupExecutionStatus,
			String lookupExecutionMessage,
			boolean acceptanceResultReady,
			boolean acceptanceResultPassed,
			boolean compactReferenceExportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record LookupExecutionResultSeedScenarioSummary(
			String scenarioName,
			int seedRowCount,
			int authorizedSeedCount,
			int unavailableSeedCount,
			int pendingResultSeedCount,
			int evidencePresentSeedCount,
			int acceptanceResultReadyCount,
			int failedResultSeedCount,
			int fullSimulationReadyCount,
			int performanceOnlyReadyCount,
			int staticAnchorReadyCount,
			int maxObservedNeighborRows,
			int maxNegativeThrustTailExecutionInputRowCount,
			int compactReferenceExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String nextRequiredAction
	) {
	}

	public record LookupExecutionResultSeedSummary(
			int scenarioCount,
			int seedRowCount,
			int currentUnavailableSeedCount,
			int currentPendingResultSeedCount,
			int currentAcceptanceResultReadyCount,
			int maxPendingResultSeedCount,
			int maxAcceptanceResultReadyCount,
			int maxFullSimulationReadyCount,
			int maxPerformanceOnlyReadyCount,
			int maxStaticAnchorReadyCount,
			int evidencePresentSeedCount,
			int acceptanceResultReadyCount,
			int failedResultSeedCount,
			int compactReferenceExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String firstPendingScenario,
			String firstReadyScenario,
			String currentStatus,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupExecutionResultSeedAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int seedRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupExecutionResultSeedRow> rows,
			List<LookupExecutionResultSeedScenarioSummary> scenarioSummaries,
			LookupExecutionResultSeedSummary summary
	) {
		public CtCpJLookupExecutionResultSeedAudit {
			rows = List.copyOf(rows);
			scenarioSummaries = List.copyOf(scenarioSummaries);
		}
	}

	public static CtCpJLookupExecutionResultSeedAudit audit() {
		PropellerArchiveCtCpJLookupExecutionTargetRunQueue.CtCpJLookupExecutionTargetRunQueueAudit runQueue =
				PropellerArchiveCtCpJLookupExecutionTargetRunQueue.audit();
		List<LookupExecutionResultSeedRow> rows = runQueue.rows()
				.stream()
				.map(PropellerArchiveCtCpJLookupExecutionResultSeed::seed)
				.toList();
		List<LookupExecutionResultSeedScenarioSummary> scenarioSummaries = runQueue.scenarioSummaries()
				.stream()
				.map(summary -> summaryForScenario(summary.scenarioName(), rows))
				.toList();
		return new CtCpJLookupExecutionResultSeedAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SEED_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				scenarioSummaries,
				summary(scenarioSummaries)
		);
	}

	public static List<LookupExecutionResultSeedRow> rowsForScenario(String scenarioName) {
		List<LookupExecutionResultSeedRow> rows = audit().rows()
				.stream()
				.filter(row -> row.scenarioName().equals(scenarioName))
				.toList();
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("unknown CT/CP/J lookup execution result seed scenario: " + scenarioName);
		}
		return rows;
	}

	public static LookupExecutionResultSeedScenarioSummary scenarioSummary(String scenarioName) {
		return audit().scenarioSummaries()
				.stream()
				.filter(summary -> summary.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J lookup execution result seed scenario: " + scenarioName));
	}

	public static LookupExecutionResultSeedRow row(String scenarioName, String presetName, String caseName) {
		return rowsForScenario(scenarioName)
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J lookup execution result seed: "
								+ scenarioName + " / " + presetName + " / " + caseName));
	}

	public static LookupExecutionResultSeedRow seed(
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow
	) {
		if (runRow == null) {
			throw new IllegalArgumentException("runRow must not be null.");
		}
		return seed(runRow, null, false);
	}

	public static LookupExecutionResultSeedRow seed(
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow,
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult result
	) {
		if (runRow == null || result == null) {
			throw new IllegalArgumentException("runRow and result are required.");
		}
		validateResultMatchesRunRow(runRow, result);
		return seed(runRow, result, true);
	}

	public static PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult acceptanceResult(
			LookupExecutionResultSeedRow seed
	) {
		if (seed == null) {
			throw new IllegalArgumentException("seed must not be null.");
		}
		if (!seed.acceptanceResultReady()) {
			throw new IllegalArgumentException("CT/CP/J lookup execution result seed is not acceptance-ready: "
					+ seed.scenarioName() + " / " + seed.presetName() + " / " + seed.caseName());
		}
		return PropellerArchiveCtCpJLookupAcceptanceGate.result(
				seed.presetName(),
				seed.caseName(),
				seed.observedNeighborRows(),
				seed.maxCtShapeOvershoot(),
				seed.minCpCoefficient(),
				seed.maxEtaResidual(),
				seed.staticAnchorError()
		);
	}

	private static LookupExecutionResultSeedRow seed(
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow,
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult result,
			boolean hasResult
	) {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target =
				PropellerArchiveCtCpJLookupAcceptanceGate.target(runRow.presetName(), runRow.caseName());
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult acceptance = null;
		if (hasResult) {
			acceptance = PropellerArchiveCtCpJLookupAcceptanceGate.result(
					result.presetName(),
					result.caseName(),
					result.observedNeighborRows(),
					result.maxCtShapeOvershoot(),
					result.minCpCoefficient(),
					result.etaResidual(),
					result.staticAnchorError()
			);
		}
		boolean accepted = hasResult
				&& result.acceptedByLookupGate()
				&& result.archiveCurveShapeGuardInherited()
				&& !result.runtimeCouplingAllowed()
				&& !result.gameplayAutoApplyAllowed();
		boolean acceptanceReady = runRow.lookupRunAuthorized()
				&& accepted
				&& acceptance.passed();
		return new LookupExecutionResultSeedRow(
				runRow.scenarioName(),
				runRow.queueOrdinal(),
				runRow.presetName(),
				runRow.caseName(),
				runRow.queryAdvanceRatioJ(),
				runRow.queryRpm(),
				target.minNeighborRows(),
				target.maxCtShapeOvershoot(),
				target.minCpCoefficient(),
				target.maxEtaResidual(),
				target.maxStaticAnchorError(),
				target.requiresStaticAnchorPreservation(),
				target.downstreamUse(),
				runRow.fullSimulationCandidate(),
				runRow.performanceOnlyCandidate(),
				runRow.lookupRunAuthorized(),
				hasResult,
				hasResult ? result.observedNeighborRows() : 0,
				hasResult ? result.ctCoefficient() : 0.0,
				hasResult ? result.cpCoefficient() : 0.0,
				hasResult ? result.eta() : 0.0,
				hasResult ? result.maxCtShapeOvershoot() : 0.0,
				hasResult ? result.minCpCoefficient() : 0.0,
				hasResult ? result.etaResidual() : 0.0,
				hasResult ? result.staticAnchorError() : 0.0,
				hasResult && result.archiveCurveShapeGuardInherited(),
				hasResult ? result.negativeThrustTailExecutionInputRowCount()
						: runRow.negativeThrustTailExecutionInputRowCount(),
				accepted,
				hasResult ? result.status() : "",
				hasResult ? result.message() : "",
				acceptanceReady,
				acceptanceReady && acceptance.passed(),
				false,
				false,
				false,
				status(runRow, hasResult, acceptanceReady, accepted),
				nextAction(runRow, hasResult, acceptanceReady, accepted),
				message(runRow, hasResult, acceptanceReady, accepted)
		);
	}

	private static LookupExecutionResultSeedScenarioSummary summaryForScenario(
			String scenarioName,
			List<LookupExecutionResultSeedRow> allRows
	) {
		List<LookupExecutionResultSeedRow> rows = allRows.stream()
				.filter(row -> row.scenarioName().equals(scenarioName))
				.toList();
		int authorized = 0;
		int pending = 0;
		int evidence = 0;
		int ready = 0;
		int failed = 0;
		int full = 0;
		int performance = 0;
		int staticAnchors = 0;
		int maxObserved = 0;
		int maxNegativeTail = 0;
		int export = 0;
		int runtime = 0;
		int gameplay = 0;
		for (LookupExecutionResultSeedRow row : rows) {
			if (row.lookupRunAuthorized()) {
				authorized++;
				if (!row.lookupResultEvidencePresent()) {
					pending++;
				}
			}
			if (row.lookupResultEvidencePresent()) {
				evidence++;
			}
			if (row.acceptanceResultReady()) {
				ready++;
				if (row.fullSimulationCandidate()) {
					full++;
				}
				if (row.performanceOnlyCandidate()) {
					performance++;
				}
				if (row.requiresStaticAnchorPreservation()) {
					staticAnchors++;
				}
			}
			if (row.lookupResultEvidencePresent() && !row.acceptanceResultReady()) {
				failed++;
			}
			maxObserved = Math.max(maxObserved, row.observedNeighborRows());
			maxNegativeTail = Math.max(maxNegativeTail, row.negativeThrustTailExecutionInputRowCount());
			if (row.compactReferenceExportAllowed()) {
				export++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new LookupExecutionResultSeedScenarioSummary(
				scenarioName,
				rows.size(),
				authorized,
				rows.size() - authorized,
				pending,
				evidence,
				ready,
				failed,
				full,
				performance,
				staticAnchors,
				maxObserved,
				maxNegativeTail,
				export,
				runtime,
				gameplay,
				scenarioStatus(rows.size(), pending, evidence, ready),
				scenarioNextAction(rows)
		);
	}

	private static LookupExecutionResultSeedSummary summary(
			List<LookupExecutionResultSeedScenarioSummary> scenarioSummaries
	) {
		int rowCount = 0;
		int maxPending = 0;
		int maxReady = 0;
		int maxFull = 0;
		int maxPerformance = 0;
		int maxStatic = 0;
		int evidence = 0;
		int ready = 0;
		int failed = 0;
		int export = 0;
		int runtime = 0;
		int gameplay = 0;
		String firstPending = "";
		String firstReady = "";
		for (LookupExecutionResultSeedScenarioSummary scenario : scenarioSummaries) {
			rowCount += scenario.seedRowCount();
			maxPending = Math.max(maxPending, scenario.pendingResultSeedCount());
			maxReady = Math.max(maxReady, scenario.acceptanceResultReadyCount());
			maxFull = Math.max(maxFull, scenario.fullSimulationReadyCount());
			maxPerformance = Math.max(maxPerformance, scenario.performanceOnlyReadyCount());
			maxStatic = Math.max(maxStatic, scenario.staticAnchorReadyCount());
			evidence += scenario.evidencePresentSeedCount();
			ready += scenario.acceptanceResultReadyCount();
			failed += scenario.failedResultSeedCount();
			export += scenario.compactReferenceExportAllowedCount();
			runtime += scenario.runtimeCouplingAllowedCount();
			gameplay += scenario.gameplayAutoApplyAllowedCount();
			if (firstPending.isBlank() && scenario.pendingResultSeedCount() > 0) {
				firstPending = scenario.scenarioName();
			}
			if (firstReady.isBlank() && scenario.acceptanceResultReadyCount() > 0) {
				firstReady = scenario.scenarioName();
			}
		}
		LookupExecutionResultSeedScenarioSummary current = scenarioSummaries.get(0);
		return new LookupExecutionResultSeedSummary(
				scenarioSummaries.size(),
				rowCount,
				current.unavailableSeedCount(),
				current.pendingResultSeedCount(),
				current.acceptanceResultReadyCount(),
				maxPending,
				maxReady,
				maxFull,
				maxPerformance,
				maxStatic,
				evidence,
				ready,
				failed,
				export,
				runtime,
				gameplay,
				firstPending,
				firstReady,
				current.status(),
				current.nextRequiredAction()
		);
	}

	private static String scenarioStatus(int rowCount, int pending, int evidence, int ready) {
		if (ready == rowCount) {
			return "READY";
		}
		if (evidence > 0) {
			return "RESULT_BLOCKED";
		}
		if (pending > 0) {
			return "PENDING_RESULT";
		}
		return "UNAVAILABLE";
	}

	private static String scenarioNextAction(List<LookupExecutionResultSeedRow> rows) {
		for (LookupExecutionResultSeedRow row : rows) {
			if ("PENDING_RESULT".equals(row.status())) {
				return RECORD_RESULT_EVIDENCE_ACTION;
			}
		}
		for (LookupExecutionResultSeedRow row : rows) {
			if (!"READY".equals(row.status())) {
				return row.nextRequiredAction();
			}
		}
		return FEED_ACCEPTANCE_ACTION;
	}

	private static String status(
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow,
			boolean hasResult,
			boolean acceptanceReady,
			boolean accepted
	) {
		if (acceptanceReady) {
			return "READY";
		}
		if (hasResult) {
			return accepted ? "ACCEPTANCE_BLOCKED" : "RESULT_BLOCKED";
		}
		if (runRow.lookupRunAuthorized()) {
			return "PENDING_RESULT";
		}
		return "UNAVAILABLE";
	}

	private static String nextAction(
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow,
			boolean hasResult,
			boolean acceptanceReady,
			boolean accepted
	) {
		if (acceptanceReady) {
			return FEED_ACCEPTANCE_ACTION;
		}
		if (hasResult) {
			return accepted ? "fix-lookup-acceptance-result-before-reference-export"
					: "fix-lookup-execution-result-before-acceptance";
		}
		if (runRow.lookupRunAuthorized()) {
			return RECORD_RESULT_EVIDENCE_ACTION;
		}
		return runRow.nextRequiredAction();
	}

	private static String message(
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow,
			boolean hasResult,
			boolean acceptanceReady,
			boolean accepted
	) {
		if (acceptanceReady) {
			return "lookup execution result seed is ready for the CT/CP/J acceptance gate";
		}
		if (hasResult) {
			return accepted ? "lookup execution evidence is present but acceptance guards are not satisfied"
					: "lookup execution evidence is present but failed execution guards";
		}
		if (runRow.lookupRunAuthorized()) {
			return "authorized lab lookup target awaits CT/CP/J result evidence";
		}
		return "result seed unavailable before lab lookup authorization: " + runRow.message();
	}

	private static void validateResultMatchesRunRow(
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow,
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult result
	) {
		if (!runRow.presetName().equals(result.presetName()) || !runRow.caseName().equals(result.caseName())) {
			throw new IllegalArgumentException("lookup execution result does not match run row target.");
		}
		if (Math.abs(runRow.queryAdvanceRatioJ() - result.queryAdvanceRatioJ()) > EPSILON
				|| Math.abs(runRow.queryRpm() - result.queryRpm()) > EPSILON) {
			throw new IllegalArgumentException("lookup execution result does not match run row query.");
		}
	}
}
