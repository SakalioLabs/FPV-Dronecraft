package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupReferenceReviewReadinessGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Reference-Review-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup reference review readiness gate requires the execution acceptance handoff to be ready before compact reference review can export lookup reference material; current and pending result states stay blocked, and runtime coupling/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int REVIEW_SCENARIO_ROW_COUNT = 4;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REVIEW_SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"record-lookup-execution-result-evidence-before-reference-review";
	public static final String REVIEW_ACCEPTED_RESULTS_ACTION =
			"review-accepted-ct-cp-j-results-before-reference-export";
	public static final String MATERIALIZE_REFERENCE_ACTION =
			"materialize-ct-cp-j-lookup-reference-table-after-review";

	private PropellerArchiveCtCpJLookupReferenceReviewReadinessGate() {
	}

	public record LookupReferenceReviewReadinessScenario(
			String scenarioName,
			String acceptanceHandoffScenarioName,
			boolean acceptanceHandoffReady,
			boolean compactReferenceReviewed,
			boolean lookupAcceptanceReady,
			boolean lookupExecutionContractReady,
			boolean lookupInterpolationExecuted,
			int expectedReferenceRowCount,
			int expectedReferenceFieldCount,
			int observedReferenceFieldCount,
			int readySeedCount,
			int pendingSeedCount,
			int unavailableSeedCount,
			int acceptedLookupTargetCount,
			int blockedLookupTargetCount,
			boolean allReferenceFieldsPresent,
			boolean referenceReviewReady,
			boolean referenceMaterialExportAllowed,
			int performanceReferenceRowAvailableCount,
			int fullSimulationReferenceRowAvailableCount,
			int performanceOnlyReferenceRowAvailableCount,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record LookupReferenceReviewReadinessSummary(
			int scenarioCount,
			int reviewReadyScenarioCount,
			int blockedScenarioCount,
			int maxExpectedReferenceRowCount,
			int maxReadySeedCount,
			int maxPendingSeedCount,
			int maxUnavailableSeedCount,
			int maxAcceptedLookupTargetCount,
			int maxBlockedLookupTargetCount,
			int maxPerformanceReferenceRowAvailableCount,
			int maxFullSimulationReferenceRowAvailableCount,
			int maxPerformanceOnlyReferenceRowAvailableCount,
			int currentReadySeedCount,
			int currentAcceptedLookupTargetCount,
			int currentBlockedLookupTargetCount,
			int referenceMaterialExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String firstReviewReadyScenario,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupReferenceReviewReadinessGateAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int reviewScenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupReferenceReviewReadinessScenario> scenarios,
			LookupReferenceReviewReadinessSummary summary
	) {
		public CtCpJLookupReferenceReviewReadinessGateAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupReferenceReviewReadinessGateAudit audit() {
		PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.CtCpJLookupExecutionAcceptanceHandoffAudit
				handoffAudit = PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.audit();
		List<LookupReferenceReviewReadinessScenario> scenarios = List.of(
				scenario("current_result_seeds_unavailable_reference_blocked",
						handoff(handoffAudit, "current_result_seeds_unavailable"), false),
				scenario("authorized_runs_pending_results_reference_blocked",
						handoff(handoffAudit, "authorized_runs_pending_results"), false),
				scenario("acceptance_handoff_ready_reference_review_missing",
						handoff(handoffAudit, "synthetic_all_result_seeds_ready"), false),
				scenario("acceptance_handoff_ready_reference_reviewed",
						handoff(handoffAudit, "synthetic_all_result_seeds_ready"), true)
		);
		return new CtCpJLookupReferenceReviewReadinessGateAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				REVIEW_SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				summary(scenarios)
		);
	}

	public static LookupReferenceReviewReadinessScenario scenario(String scenarioName) {
		return audit().scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J lookup reference review readiness scenario: " + scenarioName));
	}

	public static LookupReferenceReviewReadinessScenario scenario(
			String scenarioName,
			PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario handoff,
			boolean compactReferenceReviewed
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (handoff == null) {
			throw new IllegalArgumentException("acceptance handoff scenario must not be null.");
		}
		int observedFields = PropellerArchiveCtCpJLookupReferenceHandoff.REFERENCE_FIELD_ROW_COUNT;
		boolean fieldsPresent = observedFields == PropellerArchiveCtCpJLookupReferenceHandoff.REFERENCE_FIELD_ROW_COUNT;
		boolean reviewReady = handoff.acceptanceHandoffReady()
				&& compactReferenceReviewed
				&& fieldsPresent;
		int performanceRows = reviewReady ? PropellerArchiveCtCpJLookupAcceptanceGate.TARGET_ROW_COUNT : 0;
		int fullSimulationRows = reviewReady ? fullSimulationTargetCount() : 0;
		int performanceOnlyRows = performanceRows - fullSimulationRows;
		return new LookupReferenceReviewReadinessScenario(
				scenarioName,
				handoff.scenarioName(),
				handoff.acceptanceHandoffReady(),
				compactReferenceReviewed,
				handoff.lookupAcceptanceReady(),
				handoff.lookupExecutionContractReady(),
				handoff.lookupInterpolationExecuted(),
				PropellerArchiveCtCpJLookupAcceptanceGate.TARGET_ROW_COUNT,
				PropellerArchiveCtCpJLookupReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				observedFields,
				handoff.readySeedCount(),
				handoff.pendingSeedCount(),
				handoff.unavailableSeedCount(),
				handoff.passedAcceptanceResultCount(),
				handoff.expectedTargetCount() - handoff.passedAcceptanceResultCount(),
				fieldsPresent,
				reviewReady,
				reviewReady,
				performanceRows,
				fullSimulationRows,
				performanceOnlyRows,
				false,
				false,
				reviewReady ? "REVIEW_READY" : "BLOCKED",
				nextAction(handoff, compactReferenceReviewed, fieldsPresent),
				message(handoff, compactReferenceReviewed, fieldsPresent)
		);
	}

	private static PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario
			handoff(
					PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.CtCpJLookupExecutionAcceptanceHandoffAudit audit,
					String scenarioName
			) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow();
	}

	private static LookupReferenceReviewReadinessSummary summary(
			List<LookupReferenceReviewReadinessScenario> scenarios
	) {
		int ready = 0;
		int maxRows = 0;
		int maxReadySeeds = 0;
		int maxPending = 0;
		int maxUnavailable = 0;
		int maxAccepted = 0;
		int maxBlocked = 0;
		int maxPerformance = 0;
		int maxFull = 0;
		int maxPerformanceOnly = 0;
		int export = 0;
		int runtime = 0;
		int gameplay = 0;
		String firstReady = "";
		for (LookupReferenceReviewReadinessScenario scenario : scenarios) {
			if (scenario.referenceReviewReady()) {
				ready++;
				if (firstReady.isBlank()) {
					firstReady = scenario.scenarioName();
				}
			}
			maxRows = Math.max(maxRows, scenario.expectedReferenceRowCount());
			maxReadySeeds = Math.max(maxReadySeeds, scenario.readySeedCount());
			maxPending = Math.max(maxPending, scenario.pendingSeedCount());
			maxUnavailable = Math.max(maxUnavailable, scenario.unavailableSeedCount());
			maxAccepted = Math.max(maxAccepted, scenario.acceptedLookupTargetCount());
			maxBlocked = Math.max(maxBlocked, scenario.blockedLookupTargetCount());
			maxPerformance = Math.max(maxPerformance, scenario.performanceReferenceRowAvailableCount());
			maxFull = Math.max(maxFull, scenario.fullSimulationReferenceRowAvailableCount());
			maxPerformanceOnly = Math.max(maxPerformanceOnly,
					scenario.performanceOnlyReferenceRowAvailableCount());
			if (scenario.referenceMaterialExportAllowed()) {
				export++;
			}
			if (scenario.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (scenario.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		LookupReferenceReviewReadinessScenario current = scenarios.get(0);
		return new LookupReferenceReviewReadinessSummary(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxRows,
				maxReadySeeds,
				maxPending,
				maxUnavailable,
				maxAccepted,
				maxBlocked,
				maxPerformance,
				maxFull,
				maxPerformanceOnly,
				current.readySeedCount(),
				current.acceptedLookupTargetCount(),
				current.blockedLookupTargetCount(),
				export,
				runtime,
				gameplay,
				firstReady,
				current.nextRequiredAction()
		);
	}

	private static int fullSimulationTargetCount() {
		int count = 0;
		for (PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target
				: PropellerArchiveCtCpJLookupAcceptanceGate.targets()) {
			if (target.downstreamUse().startsWith("full-simulation")) {
				count++;
			}
		}
		return count;
	}

	private static String nextAction(
			PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario handoff,
			boolean compactReferenceReviewed,
			boolean fieldsPresent
	) {
		if (!handoff.acceptanceHandoffReady()) {
			return handoff.nextRequiredAction();
		}
		if (!compactReferenceReviewed) {
			return REVIEW_ACCEPTED_RESULTS_ACTION;
		}
		if (!fieldsPresent) {
			return "complete-ct-cp-j-reference-payload-schema-before-export";
		}
		return MATERIALIZE_REFERENCE_ACTION;
	}

	private static String message(
			PropellerArchiveCtCpJLookupExecutionAcceptanceHandoff.LookupExecutionAcceptanceHandoffScenario handoff,
			boolean compactReferenceReviewed,
			boolean fieldsPresent
	) {
		if (!handoff.acceptanceHandoffReady()) {
			return "reference review blocked by lookup execution acceptance handoff: " + handoff.message();
		}
		if (!compactReferenceReviewed) {
			return "accepted CT/CP/J lookup results require compact reference review before export";
		}
		if (!fieldsPresent) {
			return "CT/CP/J lookup reference payload schema is incomplete";
		}
		return "CT/CP/J lookup reference review is ready for table materialization";
	}
}
