package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJLookupExecutionTargetRunQueue {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Execution-Target-Run-Queue-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup execution target run queue expands input readiness into per-target lab authorization rows; current rows stay blocked, authorized rows remain pending lab execution only, and runtime coupling/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int READINESS_SCENARIO_COUNT =
			PropellerArchiveCtCpJLookupExecutionInputReadinessGate.READINESS_SCENARIO_ROW_COUNT;
	public static final int TARGET_ROW_COUNT = PropellerArchiveCtCpJLookupAcceptanceGate.TARGET_ROW_COUNT;
	public static final int TARGET_RUN_ROW_COUNT = READINESS_SCENARIO_COUNT * TARGET_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ TARGET_RUN_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"execute-authorized-lab-lookup-targets-with-reviewed-ct-cp-j-payloads";

	private PropellerArchiveCtCpJLookupExecutionTargetRunQueue() {
	}

	public record LookupExecutionTargetRunRow(
			String scenarioName,
			int queueOrdinal,
			String presetName,
			String caseName,
			double queryAdvanceRatioJ,
			double queryRpm,
			int minimumNeighborRowsRequired,
			boolean requiresStaticAnchorPreservation,
			String downstreamUse,
			boolean fullSimulationCandidate,
			boolean performanceOnlyCandidate,
			boolean coefficientPayloadHandoffReady,
			boolean scatteredSurfaceFitHandoffReady,
			boolean lookupExecutionInputReady,
			int payloadLookupInputSlotCount,
			int exportedSurfaceFitInputRowCount,
			int archiveCurveShapeGuardReadyTargetCount,
			int negativeThrustTailExecutionInputRowCount,
			boolean lookupRunAuthorized,
			boolean lookupResultEvidencePresent,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record LookupExecutionTargetRunScenarioSummary(
			String scenarioName,
			int targetRunRowCount,
			int authorizedTargetCount,
			int blockedTargetCount,
			int fullSimulationAuthorizedTargetCount,
			int performanceOnlyAuthorizedTargetCount,
			int staticAnchorAuthorizedTargetCount,
			boolean coefficientPayloadHandoffReady,
			boolean scatteredSurfaceFitHandoffReady,
			boolean lookupExecutionInputReady,
			int payloadLookupInputSlotCount,
			int exportedSurfaceFitInputRowCount,
			int archiveCurveShapeGuardReadyTargetCount,
			int negativeThrustTailExecutionInputRowCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String nextRequiredAction
	) {
	}

	public record LookupExecutionTargetRunQueueSummary(
			int scenarioCount,
			int targetRunRowCount,
			int currentAuthorizedTargetCount,
			int currentBlockedTargetCount,
			int currentPayloadLookupInputSlotCount,
			int currentSurfaceFitExportedInputRowCount,
			int maxAuthorizedTargetCount,
			int maxFullSimulationAuthorizedTargetCount,
			int maxPerformanceOnlyAuthorizedTargetCount,
			int maxStaticAnchorAuthorizedTargetCount,
			int maxNegativeThrustTailExecutionInputRowCount,
			int authorizedScenarioCount,
			int blockedScenarioCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String firstAuthorizedScenario,
			String currentStatus,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupExecutionTargetRunQueueAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int readinessScenarioCount,
			int targetRowCount,
			int targetRunRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> targets,
			List<LookupExecutionTargetRunRow> rows,
			List<LookupExecutionTargetRunScenarioSummary> scenarioSummaries,
			LookupExecutionTargetRunQueueSummary summary
	) {
		public CtCpJLookupExecutionTargetRunQueueAudit {
			targets = List.copyOf(targets);
			rows = List.copyOf(rows);
			scenarioSummaries = List.copyOf(scenarioSummaries);
		}
	}

	public static CtCpJLookupExecutionTargetRunQueueAudit audit() {
		return audit(PropellerArchiveCtCpJLookupExecutionInputReadinessGate.audit(),
				PropellerArchiveCtCpJLookupAcceptanceGate.targets());
	}

	public static CtCpJLookupExecutionTargetRunQueueAudit audit(
			PropellerArchiveCtCpJLookupExecutionInputReadinessGate.CtCpJLookupExecutionInputReadinessGateAudit
					readinessAudit,
			List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> targets
	) {
		if (readinessAudit == null || targets == null) {
			throw new IllegalArgumentException("readinessAudit and targets are required.");
		}
		List<LookupExecutionTargetRunRow> rows = new ArrayList<>();
		List<LookupExecutionTargetRunScenarioSummary> scenarioSummaries = new ArrayList<>();
		for (PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario
				: readinessAudit.scenarios()) {
			List<LookupExecutionTargetRunRow> scenarioRows = rowsForScenario(scenario, targets);
			rows.addAll(scenarioRows);
			scenarioSummaries.add(summaryForScenario(scenario, scenarioRows));
		}
		return new CtCpJLookupExecutionTargetRunQueueAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				READINESS_SCENARIO_COUNT,
				TARGET_ROW_COUNT,
				TARGET_RUN_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				targets,
				rows,
				scenarioSummaries,
				summary(scenarioSummaries)
		);
	}

	public static List<LookupExecutionTargetRunRow> rowsForScenario(String scenarioName) {
		List<LookupExecutionTargetRunRow> rows = audit().rows()
				.stream()
				.filter(row -> row.scenarioName().equals(scenarioName))
				.toList();
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("unknown CT/CP/J lookup execution run queue scenario: " + scenarioName);
		}
		return rows;
	}

	public static LookupExecutionTargetRunScenarioSummary scenarioSummary(String scenarioName) {
		return audit().scenarioSummaries()
				.stream()
				.filter(summary -> summary.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J lookup execution run queue scenario: " + scenarioName));
	}

	public static LookupExecutionTargetRunRow row(String scenarioName, String presetName, String caseName) {
		return rowsForScenario(scenarioName)
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J lookup execution run target: "
								+ scenarioName + " / " + presetName + " / " + caseName));
	}

	private static List<LookupExecutionTargetRunRow> rowsForScenario(
			PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario,
			List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> targets
	) {
		List<LookupExecutionTargetRunRow> rows = new ArrayList<>();
		int ordinal = 1;
		for (PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target : targets) {
			rows.add(row(scenario, target, ordinal));
			ordinal++;
		}
		return rows;
	}

	private static LookupExecutionTargetRunRow row(
			PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario,
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target,
			int queueOrdinal
	) {
		if (scenario == null || target == null) {
			throw new IllegalArgumentException("scenario and target are required.");
		}
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract(target.presetName(), target.caseName());
		boolean payloadSlotAvailable = scenario.payloadLookupInputSlotCount() >= target.minNeighborRows();
		boolean surfaceTargetAvailable = scenario.lookupExecutionTargetReadyCount() >= queueOrdinal;
		boolean shapeGuardAvailable = scenario.archiveCurveShapeGuardReadyTargetCount() >= queueOrdinal;
		boolean authorized = scenario.lookupExecutionInputReady()
				&& payloadSlotAvailable
				&& surfaceTargetAvailable
				&& shapeGuardAvailable;
		boolean fullSimulation = target.downstreamUse().startsWith("full-simulation");
		boolean performanceOnly = target.downstreamUse().startsWith("performance-only");
		return new LookupExecutionTargetRunRow(
				scenario.scenarioName(),
				queueOrdinal,
				target.presetName(),
				target.caseName(),
				contract.queryAdvanceRatioJ(),
				contract.queryRpm(),
				target.minNeighborRows(),
				target.requiresStaticAnchorPreservation(),
				target.downstreamUse(),
				fullSimulation,
				performanceOnly,
				scenario.coefficientPayloadHandoffReady(),
				scenario.scatteredSurfaceFitHandoffReady(),
				scenario.lookupExecutionInputReady(),
				scenario.payloadLookupInputSlotCount(),
				scenario.exportedSurfaceFitInputRowCount(),
				scenario.archiveCurveShapeGuardReadyTargetCount(),
				scenario.negativeThrustTailExecutionInputRowCount(),
				authorized,
				false,
				false,
				false,
				authorized ? "AUTHORIZED" : "BLOCKED",
				nextAction(scenario, payloadSlotAvailable, surfaceTargetAvailable, shapeGuardAvailable),
				message(scenario, authorized, payloadSlotAvailable, surfaceTargetAvailable, shapeGuardAvailable)
		);
	}

	private static LookupExecutionTargetRunScenarioSummary summaryForScenario(
			PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario,
			List<LookupExecutionTargetRunRow> rows
	) {
		int authorized = 0;
		int full = 0;
		int performanceOnly = 0;
		int staticAnchors = 0;
		int runtime = 0;
		int gameplay = 0;
		for (LookupExecutionTargetRunRow row : rows) {
			if (row.lookupRunAuthorized()) {
				authorized++;
				if (row.fullSimulationCandidate()) {
					full++;
				}
				if (row.performanceOnlyCandidate()) {
					performanceOnly++;
				}
				if (row.requiresStaticAnchorPreservation()) {
					staticAnchors++;
				}
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new LookupExecutionTargetRunScenarioSummary(
				scenario.scenarioName(),
				rows.size(),
				authorized,
				rows.size() - authorized,
				full,
				performanceOnly,
				staticAnchors,
				scenario.coefficientPayloadHandoffReady(),
				scenario.scatteredSurfaceFitHandoffReady(),
				scenario.lookupExecutionInputReady(),
				scenario.payloadLookupInputSlotCount(),
				scenario.exportedSurfaceFitInputRowCount(),
				scenario.archiveCurveShapeGuardReadyTargetCount(),
				scenario.negativeThrustTailExecutionInputRowCount(),
				runtime,
				gameplay,
				status(authorized, rows.size()),
				authorized == rows.size() ? NEXT_REQUIRED_ACTION : scenario.nextRequiredAction()
		);
	}

	private static LookupExecutionTargetRunQueueSummary summary(
			List<LookupExecutionTargetRunScenarioSummary> scenarioSummaries
	) {
		int maxAuthorized = 0;
		int maxFull = 0;
		int maxPerformance = 0;
		int maxStatic = 0;
		int maxNegativeTail = 0;
		int authorizedScenarios = 0;
		int runtime = 0;
		int gameplay = 0;
		String firstAuthorized = "";
		int rowCount = 0;
		for (LookupExecutionTargetRunScenarioSummary scenario : scenarioSummaries) {
			rowCount += scenario.targetRunRowCount();
			maxAuthorized = Math.max(maxAuthorized, scenario.authorizedTargetCount());
			maxFull = Math.max(maxFull, scenario.fullSimulationAuthorizedTargetCount());
			maxPerformance = Math.max(maxPerformance, scenario.performanceOnlyAuthorizedTargetCount());
			maxStatic = Math.max(maxStatic, scenario.staticAnchorAuthorizedTargetCount());
			maxNegativeTail = Math.max(maxNegativeTail, scenario.negativeThrustTailExecutionInputRowCount());
			if (scenario.authorizedTargetCount() == scenario.targetRunRowCount()) {
				authorizedScenarios++;
				if (firstAuthorized.isBlank()) {
					firstAuthorized = scenario.scenarioName();
				}
			}
			runtime += scenario.runtimeCouplingAllowedCount();
			gameplay += scenario.gameplayAutoApplyAllowedCount();
		}
		LookupExecutionTargetRunScenarioSummary current = scenarioSummaries.get(0);
		return new LookupExecutionTargetRunQueueSummary(
				scenarioSummaries.size(),
				rowCount,
				current.authorizedTargetCount(),
				current.blockedTargetCount(),
				current.payloadLookupInputSlotCount(),
				current.exportedSurfaceFitInputRowCount(),
				maxAuthorized,
				maxFull,
				maxPerformance,
				maxStatic,
				maxNegativeTail,
				authorizedScenarios,
				scenarioSummaries.size() - authorizedScenarios,
				runtime,
				gameplay,
				firstAuthorized,
				current.status(),
				current.nextRequiredAction()
		);
	}

	private static String status(int authorizedCount, int targetCount) {
		if (authorizedCount == targetCount) {
			return "AUTHORIZED";
		}
		if (authorizedCount > 0) {
			return "PARTIAL";
		}
		return "BLOCKED";
	}

	private static String nextAction(
			PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario,
			boolean payloadSlotAvailable,
			boolean surfaceTargetAvailable,
			boolean shapeGuardAvailable
	) {
		if (!scenario.lookupExecutionInputReady()) {
			return scenario.nextRequiredAction();
		}
		if (!payloadSlotAvailable) {
			return "fill-reviewed-grid-input-slots-with-reviewed-ct-cp-payloads-before-lookup-run";
		}
		if (!surfaceTargetAvailable) {
			return "export-scattered-surface-fit-target-row-before-lookup-run";
		}
		if (!shapeGuardAvailable) {
			return "carry-archive-curve-shape-guard-into-lookup-execution";
		}
		return NEXT_REQUIRED_ACTION;
	}

	private static String message(
			PropellerArchiveCtCpJLookupExecutionInputReadinessGate.LookupExecutionInputReadinessScenario scenario,
			boolean authorized,
			boolean payloadSlotAvailable,
			boolean surfaceTargetAvailable,
			boolean shapeGuardAvailable
	) {
		if (authorized) {
			return "target authorized for lab CT/CP/J lookup execution; result evidence and runtime authority remain absent";
		}
		if (!scenario.lookupExecutionInputReady()) {
			return "target blocked before lab CT/CP/J lookup execution: " + scenario.message();
		}
		if (!payloadSlotAvailable) {
			return "target blocked by missing reviewed coefficient payload slots";
		}
		if (!surfaceTargetAvailable) {
			return "target blocked by missing scattered surface-fit execution row";
		}
		if (!shapeGuardAvailable) {
			return "target blocked by missing inherited archive curve-shape guard";
		}
		return "target blocked before lab CT/CP/J lookup execution";
	}
}
