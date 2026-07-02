package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupExecutionInputReadinessGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Execution-Input-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup execution input readiness gate requires both reviewed coefficient payload handoff and scattered surface fit execution handoff before lookup targets can run; it exposes lab readiness only and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int READINESS_SCENARIO_ROW_COUNT = 4;
	public static final int SUMMARY_ROW_COUNT = 17;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ READINESS_SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"complete-payload-and-surface-fit-handoffs-before-lookup-execution";

	private PropellerArchiveCtCpJLookupExecutionInputReadinessGate() {
	}

	public record LookupExecutionInputReadinessScenario(
			String scenarioName,
			String coefficientPayloadHandoffScenarioName,
			String scatteredSurfaceFitHandoffScenarioName,
			boolean coefficientPayloadHandoffReady,
			boolean scatteredSurfaceFitHandoffReady,
			boolean sourceRowsReviewed,
			boolean scatteredSurfaceFitContractReady,
			int expectedCoefficientPayloadSlotCount,
			int payloadLookupInputSlotCount,
			int expectedSurfaceFitTargetCount,
			int exportedSurfaceFitInputRowCount,
			int archiveCurveShapeGuardReadyTargetCount,
			int negativeThrustTailExecutionInputRowCount,
			int fullSimulationExecutionInputRowCount,
			int performanceOnlyExecutionInputRowCount,
			int lookupExecutionTargetReadyCount,
			boolean lookupExecutionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record LookupExecutionInputReadinessSummary(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxPayloadLookupInputSlotCount,
			int maxSurfaceFitExportedInputRowCount,
			int maxLookupExecutionTargetReadyCount,
			boolean currentCoefficientPayloadHandoffReady,
			boolean currentScatteredSurfaceFitHandoffReady,
			int currentPayloadLookupInputSlotCount,
			int currentSurfaceFitExportedInputRowCount,
			int currentLookupExecutionTargetReadyCount,
			int currentNegativeThrustTailExecutionInputRowCount,
			String currentStatus,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String firstReadyScenario,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupExecutionInputReadinessGateAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int readinessScenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupExecutionInputReadinessScenario> scenarios,
			LookupExecutionInputReadinessSummary summary
	) {
		public CtCpJLookupExecutionInputReadinessGateAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupExecutionInputReadinessGateAudit audit() {
		PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff
				.CtCpJLookupCoefficientPayloadReviewHandoffAudit payloadAudit =
				PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.audit();
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.CtCpJScatteredSurfaceFitExecutionHandoffAudit
				surfaceFitAudit = PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.audit();
		List<LookupExecutionInputReadinessScenario> scenarios = List.of(
				scenario("current_payload_and_surface_fit_blocked",
						payload(payloadAudit, "current_evidence_blocked_no_payload_review"),
						"current_source_review_blocked_no_execution_input",
						surfaceFit(surfaceFitAudit, "current_source_review_blocked_no_execution_input")),
				scenario("payload_ready_surface_fit_blocked",
						payload(payloadAudit, "worksheet_open_payload_ready"),
						"current_source_review_blocked_no_execution_input",
						surfaceFit(surfaceFitAudit, "current_source_review_blocked_no_execution_input")),
				scenario("surface_fit_ready_payload_blocked",
						payload(payloadAudit, "current_evidence_blocked_no_payload_review"),
						"surface_fit_ready_execution_input_handoff",
						surfaceFit(surfaceFitAudit, "surface_fit_ready_execution_input_handoff")),
				scenario("payload_and_surface_fit_ready",
						payload(payloadAudit, "worksheet_open_payload_ready"),
						"surface_fit_ready_execution_input_handoff",
						surfaceFit(surfaceFitAudit, "surface_fit_ready_execution_input_handoff"))
		);
		return new CtCpJLookupExecutionInputReadinessGateAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				READINESS_SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				summary(scenarios)
		);
	}

	public static LookupExecutionInputReadinessScenario scenario(String scenarioName) {
		return audit().scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J lookup execution input readiness scenario: " + scenarioName));
	}

	private static LookupExecutionInputReadinessScenario scenario(
			String scenarioName,
			PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.CoefficientPayloadReviewHandoffScenario
					payload,
			String surfaceFitScenarioName,
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary surfaceFit
	) {
		boolean payloadReady = payload.reviewedPayloadHandoffReady();
		boolean surfaceReady = surfaceFit.executionInputHandoffReady();
		boolean ready = payloadReady && surfaceReady;
		int readyTargets = ready ? surfaceFit.exportedExecutionInputRowCount() : 0;
		return new LookupExecutionInputReadinessScenario(
				scenarioName,
				payload.scenarioName(),
				surfaceFitScenarioName,
				payloadReady,
				surfaceReady,
				surfaceFit.sourceRowsReviewed(),
				surfaceFit.scatteredSurfaceFitContractReady(),
				payload.expectedSlotCount(),
				payloadReady ? payload.lookupExecutionInputSlotCount() : 0,
				surfaceFit.expectedExecutionInputRowCount(),
				surfaceFit.exportedExecutionInputRowCount(),
				surfaceFit.archiveCurveShapeGuardReadyTargetCount(),
				surfaceFit.negativeThrustTailExecutionInputRowCount(),
				surfaceFit.fullSimulationExecutionInputRowCount(),
				surfaceFit.performanceOnlyExecutionInputRowCount(),
				readyTargets,
				ready,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				nextAction(payloadReady, surfaceReady, payload, surfaceFit),
				message(payloadReady, surfaceReady, payload, surfaceFit)
		);
	}

	private static LookupExecutionInputReadinessSummary summary(
			List<LookupExecutionInputReadinessScenario> scenarios
	) {
		int ready = 0;
		int maxPayload = 0;
		int maxSurface = 0;
		int maxTargets = 0;
		int runtime = 0;
		int gameplay = 0;
		String firstReady = "";
		for (LookupExecutionInputReadinessScenario scenario : scenarios) {
			if (scenario.lookupExecutionInputReady()) {
				ready++;
				if (firstReady.isBlank()) {
					firstReady = scenario.scenarioName();
				}
			}
			maxPayload = Math.max(maxPayload, scenario.payloadLookupInputSlotCount());
			maxSurface = Math.max(maxSurface, scenario.exportedSurfaceFitInputRowCount());
			maxTargets = Math.max(maxTargets, scenario.lookupExecutionTargetReadyCount());
			if (scenario.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (scenario.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		LookupExecutionInputReadinessScenario current = scenarios.get(0);
		return new LookupExecutionInputReadinessSummary(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxPayload,
				maxSurface,
				maxTargets,
				current.coefficientPayloadHandoffReady(),
				current.scatteredSurfaceFitHandoffReady(),
				current.payloadLookupInputSlotCount(),
				current.exportedSurfaceFitInputRowCount(),
				current.lookupExecutionTargetReadyCount(),
				current.negativeThrustTailExecutionInputRowCount(),
				current.status(),
				runtime,
				gameplay,
				firstReady,
				current.nextRequiredAction()
		);
	}

	private static PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff
			.CoefficientPayloadReviewHandoffScenario payload(
					PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff
							.CtCpJLookupCoefficientPayloadReviewHandoffAudit audit,
					String scenarioName
			) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"missing CT/CP/J coefficient payload review handoff scenario: " + scenarioName));
	}

	private static PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary surfaceFit(
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.CtCpJScatteredSurfaceFitExecutionHandoffAudit audit,
			String scenarioName
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"missing CT/CP/J scattered surface fit execution handoff scenario: " + scenarioName))
				.summary();
	}

	private static String nextAction(
			boolean payloadReady,
			boolean surfaceReady,
			PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.CoefficientPayloadReviewHandoffScenario
					payload,
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary surfaceFit
	) {
		if (!payloadReady) {
			return payload.nextRequiredAction();
		}
		if (!surfaceReady) {
			return PropellerArchiveCtCpJLookupExecutionContract.NEXT_REQUIRED_ACTION;
		}
		return "run-lookup-execution-contract-with-reviewed-payload-and-surface-fit-inputs";
	}

	private static String message(
			boolean payloadReady,
			boolean surfaceReady,
			PropellerArchiveCtCpJLookupCoefficientPayloadReviewHandoff.CoefficientPayloadReviewHandoffScenario
					payload,
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary surfaceFit
	) {
		if (!payloadReady) {
			return "coefficient payload review handoff blocked: " + payload.message();
		}
		if (!surfaceReady) {
			return "scattered surface fit execution handoff blocked: " + surfaceFit.message();
		}
		return "lookup execution input ready for reviewed payload and surface fit handoffs";
	}
}
