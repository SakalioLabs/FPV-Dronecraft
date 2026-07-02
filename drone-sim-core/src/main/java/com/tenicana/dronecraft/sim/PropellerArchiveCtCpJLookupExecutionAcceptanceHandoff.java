package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Execution-Acceptance-Handoff-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup execution acceptance handoff aggregates result seeds before the acceptance gate; current rows stay blocked, pending rows carry no coefficients, synthetic ready rows only prove the handoff contract, and runtime coupling/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int HANDOFF_SCENARIO_ROW_COUNT = 3;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ HANDOFF_SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"record-lookup-execution-result-evidence-before-acceptance";
	public static final String REVIEW_ACCEPTED_RESULTS_ACTION =
			"review-accepted-ct-cp-j-results-before-reference-export";

	private PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff() {
	}

	public record LookupExecutionAcceptanceHandoffScenario(
			String scenarioName,
			String resultSeedScenarioName,
			boolean reviewedImportReady,
			boolean interpolationPolicyReady,
			boolean lookupExecutionContractReady,
			boolean lookupInterpolationExecuted,
			boolean compactReferenceReviewed,
			int expectedTargetCount,
			int seedRowCount,
			int readySeedCount,
			int pendingSeedCount,
			int unavailableSeedCount,
			int failedSeedCount,
			int acceptanceResultCount,
			int passedAcceptanceResultCount,
			int failedAcceptanceResultCount,
			int missingAcceptanceResultCount,
			int unexpectedAcceptanceResultCount,
			int minObservedNeighborRows,
			double maxCtShapeOvershoot,
			double minCpCoefficient,
			double maxEtaResidual,
			double maxStaticAnchorError,
			boolean allTargetsPresent,
			boolean allExpectedResultsPassed,
			boolean lookupAcceptanceReady,
			boolean acceptanceHandoffReady,
			boolean compactReferenceExportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record LookupExecutionAcceptanceHandoffSummary(
			int scenarioCount,
			int handoffReadyScenarioCount,
			int blockedScenarioCount,
			int maxExpectedTargetCount,
			int maxReadySeedCount,
			int maxPendingSeedCount,
			int maxUnavailableSeedCount,
			int maxAcceptanceResultCount,
			int maxPassedAcceptanceResultCount,
			int maxMissingAcceptanceResultCount,
			int currentReadySeedCount,
			int currentAcceptanceResultCount,
			int currentMissingAcceptanceResultCount,
			int compactReferenceExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String firstHandoffReadyScenario,
			String firstPendingScenario,
			String currentStatus,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupExecutionAcceptanceHandoffAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int handoffScenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupExecutionAcceptanceHandoffScenario> scenarios,
			LookupExecutionAcceptanceHandoffSummary summary
	) {
		public CtCpJLookupExecutionAcceptanceHandoffAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupExecutionAcceptanceHandoffAudit audit() {
		PropellerArchiveCtCpJLookupExecutionResultSeed.CtCpJLookupExecutionResultSeedAudit resultSeedAudit =
				PropellerArchiveCtCpJLookupExecutionResultSeed.audit();
		List<LookupExecutionAcceptanceHandoffScenario> scenarios = List.of(
				scenario(
						"current_result_seeds_unavailable",
						"current_payload_and_surface_fit_blocked",
						seedsFor(resultSeedAudit, "current_payload_and_surface_fit_blocked"),
						false,
						false,
						false,
						false,
						"current-result-seeds-unavailable"),
				scenario(
						"authorized_runs_pending_results",
						"payload_and_surface_fit_ready",
						seedsFor(resultSeedAudit, "payload_and_surface_fit_ready"),
						true,
						true,
						true,
						false,
						"authorized-lab-runs-pending-results"),
				scenario(
						"synthetic_all_result_seeds_ready",
						"synthetic_all_result_seeds_ready",
						syntheticReadySeeds(),
						true,
						true,
						true,
						false,
						"synthetic-result-seeds-ready-for-acceptance")
		);
		return new CtCpJLookupExecutionAcceptanceHandoffAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				HANDOFF_SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				summary(scenarios)
		);
	}

	public static LookupExecutionAcceptanceHandoffScenario scenario(String scenarioName) {
		return audit().scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J lookup execution acceptance handoff scenario: " + scenarioName));
	}

	public static LookupExecutionAcceptanceHandoffScenario scenario(
			String scenarioName,
			String resultSeedScenarioName,
			List<PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow> seeds,
			boolean reviewedImportReady,
			boolean interpolationPolicyReady,
			boolean lookupExecutionContractReady,
			boolean compactReferenceReviewed,
			String sourceRuntimeInfo
	) {
		if (scenarioName == null || scenarioName.isBlank()
				|| resultSeedScenarioName == null || resultSeedScenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName and resultSeedScenarioName are required.");
		}
		if (seeds == null) {
			throw new IllegalArgumentException("seeds must not be null.");
		}
		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> targets =
				PropellerArchiveCtCpJLookupAcceptanceGate.targets();
		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> results =
				readyAcceptanceResults(seeds);
		boolean lookupExecuted = results.size() == targets.size();
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptanceSummary =
				PropellerArchiveCtCpJLookupAcceptanceGate.gate(
						reviewedImportReady,
						interpolationPolicyReady,
						lookupExecutionContractReady,
						lookupExecuted,
						compactReferenceReviewed,
						targets,
						results,
						sourceRuntimeInfo
				);
		int pending = 0;
		int unavailable = 0;
		int failed = 0;
		for (PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow seed : seeds) {
			if (seed.lookupRunAuthorized() && !seed.lookupResultEvidencePresent()) {
				pending++;
			}
			if (!seed.lookupRunAuthorized()) {
				unavailable++;
			}
			if (seed.lookupResultEvidencePresent() && !seed.acceptanceResultReady()) {
				failed++;
			}
		}
		return new LookupExecutionAcceptanceHandoffScenario(
				scenarioName,
				resultSeedScenarioName,
				reviewedImportReady,
				interpolationPolicyReady,
				lookupExecutionContractReady,
				lookupExecuted,
				compactReferenceReviewed,
				targets.size(),
				seeds.size(),
				acceptanceSummary.observedResultCount(),
				pending,
				unavailable,
				failed,
				acceptanceSummary.observedResultCount(),
				acceptanceSummary.passedResultCount(),
				acceptanceSummary.failedResultCount(),
				acceptanceSummary.missingResultCount(),
				acceptanceSummary.unexpectedResultCount(),
				acceptanceSummary.minObservedNeighborRows(),
				acceptanceSummary.maxCtShapeOvershoot(),
				acceptanceSummary.minCpCoefficient(),
				acceptanceSummary.maxEtaResidual(),
				acceptanceSummary.maxStaticAnchorError(),
				acceptanceSummary.allTargetsPresent(),
				acceptanceSummary.allExpectedResultsPassed(),
				acceptanceSummary.lookupAcceptanceReady(),
				acceptanceSummary.lookupAcceptanceReady(),
				acceptanceSummary.compactReferenceExportAllowed(),
				false,
				false,
				status(acceptanceSummary, pending, unavailable, failed),
				nextAction(acceptanceSummary, pending, unavailable, failed, seeds),
				message(acceptanceSummary, pending, unavailable, failed)
		);
	}

	private static List<PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow> seedsFor(
			PropellerArchiveCtCpJLookupExecutionResultSeed.CtCpJLookupExecutionResultSeedAudit audit,
			String scenarioName
	) {
		return audit.rows()
				.stream()
				.filter(row -> row.scenarioName().equals(scenarioName))
				.toList();
	}

	private static List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> readyAcceptanceResults(
			List<PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow> seeds
	) {
		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> results = new ArrayList<>();
		for (PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow seed : seeds) {
			if (seed.acceptanceResultReady()) {
				results.add(PropellerArchiveCtCpJLookupExecutionResultSeed.acceptanceResult(seed));
			}
		}
		return results;
	}

	private static List<PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow>
			syntheticReadySeeds() {
		return PropellerArchiveCtCpJLookupExecutionTargetRunQueue.rowsForScenario("payload_and_surface_fit_ready")
				.stream()
				.map(PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff::syntheticReadySeed)
				.toList();
	}

	private static PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow syntheticReadySeed(
			PropellerArchiveCtCpJLookupExecutionTargetRunQueue.LookupExecutionTargetRunRow runRow
	) {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target =
				PropellerArchiveCtCpJLookupAcceptanceGate.target(runRow.presetName(), runRow.caseName());
		double ct = 0.100;
		double cp = 0.050;
		double eta = runRow.queryAdvanceRatioJ() <= 1.0e-12 ? 0.0 : runRow.queryAdvanceRatioJ() * ct / cp;
		return new PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow(
				"synthetic_all_result_seeds_ready",
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
				true,
				true,
				target.minNeighborRows() + 2,
				ct,
				cp,
				eta,
				0.0,
				0.010,
				target.maxEtaResidual() * 0.50,
				target.requiresStaticAnchorPreservation()
						? target.maxStaticAnchorError() * 0.50
						: 0.0,
				true,
				runRow.negativeThrustTailExecutionInputRowCount(),
				true,
				"ACCEPTED",
				"synthetic-lookup-execution-result-ready",
				true,
				true,
				false,
				false,
				false,
				"READY",
				PropellerArchiveCtCpJLookupExecutionResultSeed.FEED_ACCEPTANCE_ACTION,
				"synthetic result seed is ready for acceptance handoff only"
		);
	}

	private static LookupExecutionAcceptanceHandoffSummary summary(
			List<LookupExecutionAcceptanceHandoffScenario> scenarios
	) {
		int ready = 0;
		int maxExpected = 0;
		int maxReadySeeds = 0;
		int maxPending = 0;
		int maxUnavailable = 0;
		int maxResults = 0;
		int maxPassed = 0;
		int maxMissing = 0;
		int export = 0;
		int runtime = 0;
		int gameplay = 0;
		String firstReady = "";
		String firstPending = "";
		for (LookupExecutionAcceptanceHandoffScenario scenario : scenarios) {
			if (scenario.acceptanceHandoffReady()) {
				ready++;
				if (firstReady.isBlank()) {
					firstReady = scenario.scenarioName();
				}
			}
			if (firstPending.isBlank() && scenario.pendingSeedCount() > 0) {
				firstPending = scenario.scenarioName();
			}
			maxExpected = Math.max(maxExpected, scenario.expectedTargetCount());
			maxReadySeeds = Math.max(maxReadySeeds, scenario.readySeedCount());
			maxPending = Math.max(maxPending, scenario.pendingSeedCount());
			maxUnavailable = Math.max(maxUnavailable, scenario.unavailableSeedCount());
			maxResults = Math.max(maxResults, scenario.acceptanceResultCount());
			maxPassed = Math.max(maxPassed, scenario.passedAcceptanceResultCount());
			maxMissing = Math.max(maxMissing, scenario.missingAcceptanceResultCount());
			if (scenario.compactReferenceExportAllowed()) {
				export++;
			}
			if (scenario.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (scenario.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		LookupExecutionAcceptanceHandoffScenario current = scenarios.get(0);
		return new LookupExecutionAcceptanceHandoffSummary(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxExpected,
				maxReadySeeds,
				maxPending,
				maxUnavailable,
				maxResults,
				maxPassed,
				maxMissing,
				current.readySeedCount(),
				current.acceptanceResultCount(),
				current.missingAcceptanceResultCount(),
				export,
				runtime,
				gameplay,
				firstReady,
				firstPending,
				current.status(),
				current.nextRequiredAction()
		);
	}

	private static String status(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptance,
			int pending,
			int unavailable,
			int failed
	) {
		if (acceptance.lookupAcceptanceReady()) {
			return "HANDOFF_READY";
		}
		if (failed > 0) {
			return "RESULT_BLOCKED";
		}
		if (pending > 0) {
			return "PENDING_RESULTS";
		}
		return "BLOCKED";
	}

	private static String nextAction(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptance,
			int pending,
			int unavailable,
			int failed,
			List<PropellerArchiveCtCpJLookupExecutionResultSeed.LookupExecutionResultSeedRow> seeds
	) {
		if (acceptance.lookupAcceptanceReady()) {
			return REVIEW_ACCEPTED_RESULTS_ACTION;
		}
		if (failed > 0) {
			return "fix-lookup-result-seeds-before-acceptance-handoff";
		}
		if (pending > 0) {
			return PropellerArchiveCtCpJLookupExecutionResultSeed.RECORD_RESULT_EVIDENCE_ACTION;
		}
		return seeds.isEmpty() ? NEXT_REQUIRED_ACTION : seeds.get(0).nextRequiredAction();
	}

	private static String message(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptance,
			int pending,
			int unavailable,
			int failed
	) {
		if (acceptance.lookupAcceptanceReady()) {
			return "lookup execution results are complete and ready for CT/CP/J acceptance handoff";
		}
		if (failed > 0) {
			return "lookup execution result seeds contain failed or non-acceptance-ready evidence";
		}
		if (pending > 0) {
			return "authorized lab lookup runs are waiting for CT/CP/J result evidence";
		}
		if (unavailable > 0) {
			return "lookup execution acceptance handoff blocked before result seed availability";
		}
		return acceptance.message();
	}
}
