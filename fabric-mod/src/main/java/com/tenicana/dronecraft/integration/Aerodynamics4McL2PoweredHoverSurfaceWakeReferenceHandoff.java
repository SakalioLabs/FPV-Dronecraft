package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Reference-Handoff-Packet";
	public static final String CAVEAT =
			"Reference handoff may export compact surface-wake lab evidence only after validation acceptance and aggregate error budgets are ready; it never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 18;
	public static final int SUMMARY_METRIC_ROW_COUNT = 9;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff() {
	}

	public record PoweredHoverSurfaceWakeReferenceHandoffSummary(
			boolean labValidationAccepted,
			int expectedErrorBudgetGroupCount,
			int observedErrorBudgetGroupCount,
			int readyErrorBudgetGroupCount,
			int validationCandidateGroupCount,
			int blockedErrorBudgetGroupCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalBandErrorRatio,
			boolean allErrorBudgetGroupsReady,
			boolean referenceMaterialExportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String referencePayloadKind,
			String status,
			String message,
			String acceptanceStatus,
			String sourceRuntimeInfo
	) {
	}

	public record PoweredHoverSurfaceWakeReferenceHandoffScenario(
			String scenarioName,
			PoweredHoverSurfaceWakeReferenceHandoffSummary summary
	) {
	}

	public record PoweredHoverSurfaceWakeReferenceHandoffExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int referenceMaterialExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int maxObservedErrorBudgetGroupCount,
			int maxReadyErrorBudgetGroupCount,
			int maxBlockedErrorBudgetGroupCount
	) {
	}

	public record PoweredHoverSurfaceWakeReferenceHandoffAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeReferenceHandoffScenario> scenarios,
			PoweredHoverSurfaceWakeReferenceHandoffExtrema extrema
	) {
		public PoweredHoverSurfaceWakeReferenceHandoffAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredHoverSurfaceWakeReferenceHandoffAudit audit() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceAudit acceptanceAudit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.audit();
		Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetAudit budgetAudit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit();
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary currentAcceptance =
				acceptance(acceptanceAudit, "current_transient_probe_unavailable");
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary accepted =
				acceptance(acceptanceAudit, "transient_probe_all_targets_pass");
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup> currentGroups =
				budgetAudit.groups();
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup> readyGroups =
				readyGroups(currentGroups);
		List<PoweredHoverSurfaceWakeReferenceHandoffScenario> scenarios = List.of(
				new PoweredHoverSurfaceWakeReferenceHandoffScenario(
						"current_lab_validation_blocked",
						handoff("current_lab_validation_blocked", currentAcceptance, currentGroups,
								"audit-only-surface-wake-reference-handoff")),
				new PoweredHoverSurfaceWakeReferenceHandoffScenario(
						"lab_accepted_error_budget_ready",
						handoff("lab_accepted_error_budget_ready", accepted, readyGroups,
								"synthetic-live-surface-wake-reference-ready")),
				new PoweredHoverSurfaceWakeReferenceHandoffScenario(
						"lab_accepted_error_budget_blocked",
						handoff("lab_accepted_error_budget_blocked", accepted, currentGroups,
								"synthetic-accepted-current-budget-blocked")),
				new PoweredHoverSurfaceWakeReferenceHandoffScenario(
						"budget_ready_without_lab_acceptance",
						handoff("budget_ready_without_lab_acceptance", currentAcceptance, readyGroups,
								"synthetic-budget-ready-lab-acceptance-blocked"))
		);
		return new PoweredHoverSurfaceWakeReferenceHandoffAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static PoweredHoverSurfaceWakeReferenceHandoffSummary handoff(
			String scenarioName,
			Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary acceptance,
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup> groups,
			String sourceRuntimeInfo
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (acceptance == null) {
			throw new IllegalArgumentException("acceptance summary must not be null.");
		}
		if (groups == null) {
			throw new IllegalArgumentException("groups must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		int observed = 0;
		int ready = 0;
		int candidates = 0;
		int blocked = 0;
		double maxPressure = 0.0;
		double maxVelocity = 0.0;
		double maxArrival = 0.0;
		for (Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup group
				: groups) {
			if (group == null) {
				throw new IllegalArgumentException("groups must not contain null entries.");
			}
			observed++;
			if (group.allRotorSeedsReady()) {
				ready++;
			}
			if (group.validationCandidate()) {
				candidates++;
			} else {
				blocked++;
			}
			maxPressure = Math.max(maxPressure, group.maxPressureErrorRatio());
			maxVelocity = Math.max(maxVelocity, group.maxWakeVelocityErrorRatio());
			maxArrival = Math.max(maxArrival, group.maxArrivalBandErrorRatio());
		}
		boolean allGroupsReady = observed == Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.GROUP_SAMPLE_COUNT
				&& ready == Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.GROUP_SAMPLE_COUNT
				&& candidates == Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.GROUP_SAMPLE_COUNT
				&& blocked == 0;
		boolean exportAllowed = acceptance.labValidationAccepted() && allGroupsReady;
		return new PoweredHoverSurfaceWakeReferenceHandoffSummary(
				acceptance.labValidationAccepted(),
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.GROUP_SAMPLE_COUNT,
				observed,
				ready,
				candidates,
				blocked,
				maxPressure,
				maxVelocity,
				maxArrival,
				allGroupsReady,
				exportAllowed,
				false,
				false,
				"aggregate-surface-wake-reference-table",
				exportAllowed ? "READY" : "BLOCKED",
				messageFor(acceptance.labValidationAccepted(), allGroupsReady),
				acceptance.status(),
				sourceRuntimeInfo
		);
	}

	private static String messageFor(boolean labAccepted, boolean budgetReady) {
		if (!labAccepted) {
			return "surface-wake-lab-validation-not-accepted";
		}
		if (!budgetReady) {
			return "surface-wake-error-budget-not-ready";
		}
		return "surface-wake-reference-material-ready";
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary acceptance(
			Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static List<Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup> readyGroups(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup> groups
	) {
		return groups.stream()
				.map(Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff::readyGroup)
				.toList();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup readyGroup(
			Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup group
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup(
				group.presetName(),
				group.spinState(),
				group.surfaceType(),
				group.clearanceOverRadius(),
				group.clearanceMeters(),
				group.expectedRotorSeedCount(),
				group.expectedRotorSeedCount(),
				0,
				0,
				group.expectedRotorSeedCount(),
				group.expectedRotorSeedCount(),
				0,
				0,
				true,
				true,
				true,
				group.meanTargetPressurePascals(),
				0.0,
				0.0,
				group.meanTargetWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				group.meanArrivalBandSeconds(),
				0.0,
				0.0,
				true,
				"READY",
				"surface-wake-error-budget-ready"
		);
	}

	private static PoweredHoverSurfaceWakeReferenceHandoffExtrema extrema(
			List<PoweredHoverSurfaceWakeReferenceHandoffScenario> scenarios
	) {
		int ready = 0;
		int exportAllowed = 0;
		int runtimeAllowed = 0;
		int autoApply = 0;
		int maxObserved = 0;
		int maxReady = 0;
		int maxBlocked = 0;
		for (PoweredHoverSurfaceWakeReferenceHandoffScenario scenario : scenarios) {
			PoweredHoverSurfaceWakeReferenceHandoffSummary summary = scenario.summary();
			if (summary.referenceMaterialExportAllowed()) {
				ready++;
				exportAllowed++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				autoApply++;
			}
			maxObserved = Math.max(maxObserved, summary.observedErrorBudgetGroupCount());
			maxReady = Math.max(maxReady, summary.readyErrorBudgetGroupCount());
			maxBlocked = Math.max(maxBlocked, summary.blockedErrorBudgetGroupCount());
		}
		return new PoweredHoverSurfaceWakeReferenceHandoffExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				exportAllowed,
				runtimeAllowed,
				autoApply,
				maxObserved,
				maxReady,
				maxBlocked
		);
	}
}
