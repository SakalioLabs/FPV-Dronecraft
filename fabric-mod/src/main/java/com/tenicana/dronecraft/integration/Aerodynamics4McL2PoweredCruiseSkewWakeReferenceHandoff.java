package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Reference-Handoff-Packet";
	public static final String CAVEAT =
			"Reference handoff may export compact cruise skew-wake lab evidence only after validation acceptance and aggregate error budgets are ready; it never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 19;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff() {
	}

	public record PoweredCruiseSkewWakeReferenceHandoffSummary(
			boolean labValidationAccepted,
			int expectedErrorBudgetGroupCount,
			int observedErrorBudgetGroupCount,
			int readyErrorBudgetGroupCount,
			int validationCandidateGroupCount,
			int blockedErrorBudgetGroupCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalWindowErrorRatio,
			double maxMomentumErrorRatio,
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

	public record PoweredCruiseSkewWakeReferenceHandoffScenario(
			String scenarioName,
			PoweredCruiseSkewWakeReferenceHandoffSummary summary
	) {
	}

	public record PoweredCruiseSkewWakeReferenceHandoffExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int referenceMaterialExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int maxObservedErrorBudgetGroupCount,
			int maxReadyErrorBudgetGroupCount,
			int maxBlockedErrorBudgetGroupCount,
			double maxMomentumErrorRatio
	) {
	}

	public record PoweredCruiseSkewWakeReferenceHandoffAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeReferenceHandoffScenario> scenarios,
			PoweredCruiseSkewWakeReferenceHandoffExtrema extrema
	) {
		public PoweredCruiseSkewWakeReferenceHandoffAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredCruiseSkewWakeReferenceHandoffAudit audit() {
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceAudit acceptanceAudit =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.audit();
		Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetAudit budgetAudit =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit();
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary currentAcceptance =
				acceptance(acceptanceAudit, "current_probe_apis_unavailable");
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary accepted =
				acceptance(acceptanceAudit, "skew_and_transient_probe_all_targets_pass");
		List<Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup> currentGroups =
				budgetAudit.groups();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup> readyGroups =
				readyGroups(currentGroups);
		List<PoweredCruiseSkewWakeReferenceHandoffScenario> scenarios = List.of(
				new PoweredCruiseSkewWakeReferenceHandoffScenario(
						"current_lab_validation_blocked",
						handoff("current_lab_validation_blocked", currentAcceptance, currentGroups,
								"audit-only-cruise-skew-wake-reference-handoff")),
				new PoweredCruiseSkewWakeReferenceHandoffScenario(
						"lab_accepted_error_budget_ready",
						handoff("lab_accepted_error_budget_ready", accepted, readyGroups,
								"synthetic-live-cruise-skew-wake-reference-ready")),
				new PoweredCruiseSkewWakeReferenceHandoffScenario(
						"lab_accepted_error_budget_blocked",
						handoff("lab_accepted_error_budget_blocked", accepted, currentGroups,
								"synthetic-accepted-current-cruise-skew-wake-budget-blocked")),
				new PoweredCruiseSkewWakeReferenceHandoffScenario(
						"budget_ready_without_lab_acceptance",
						handoff("budget_ready_without_lab_acceptance", currentAcceptance, readyGroups,
								"synthetic-cruise-skew-wake-budget-ready-lab-acceptance-blocked"))
		);
		return new PoweredCruiseSkewWakeReferenceHandoffAudit(
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

	public static PoweredCruiseSkewWakeReferenceHandoffSummary handoff(
			String scenarioName,
			Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary acceptance,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup> groups,
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
		double maxMomentum = 0.0;
		for (Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup group
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
			maxArrival = Math.max(maxArrival, group.maxArrivalWindowErrorRatio());
			maxMomentum = Math.max(maxMomentum, group.maxMomentumErrorRatio());
		}
		boolean allGroupsReady = observed == Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.GROUP_SAMPLE_COUNT
				&& ready == Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.GROUP_SAMPLE_COUNT
				&& candidates == Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.GROUP_SAMPLE_COUNT
				&& blocked == 0;
		boolean exportAllowed = acceptance.labValidationAccepted() && allGroupsReady;
		return new PoweredCruiseSkewWakeReferenceHandoffSummary(
				acceptance.labValidationAccepted(),
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.GROUP_SAMPLE_COUNT,
				observed,
				ready,
				candidates,
				blocked,
				maxPressure,
				maxVelocity,
				maxArrival,
				maxMomentum,
				allGroupsReady,
				exportAllowed,
				false,
				false,
				"aggregate-cruise-skew-wake-reference-table",
				exportAllowed ? "READY" : "BLOCKED",
				messageFor(acceptance.labValidationAccepted(), allGroupsReady),
				acceptance.status(),
				sourceRuntimeInfo
		);
	}

	private static String messageFor(boolean labAccepted, boolean budgetReady) {
		if (!labAccepted) {
			return "cruise-skew-wake-lab-validation-not-accepted";
		}
		if (!budgetReady) {
			return "cruise-skew-wake-error-budget-not-ready";
		}
		return "cruise-skew-wake-reference-material-ready";
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary acceptance(
			Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup> readyGroups(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup> groups
	) {
		return groups.stream()
				.map(Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff::readyGroup)
				.toList();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup readyGroup(
			Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup group
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup(
				group.presetName(),
				group.spinState(),
				group.axialPlaneIndex(),
				group.sweepColumnIndex(),
				group.axialPlaneFraction(),
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
				group.meanTargetResultantDynamicPressurePascals(),
				0.0,
				0.0,
				group.meanTargetResultantWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				group.meanArrivalBandSeconds(),
				0.0,
				0.0,
				group.meanTargetAxialMomentumFluxNewtons(),
				0.0,
				0.0,
				true,
				"READY",
				"cruise-skew-wake-error-budget-ready"
		);
	}

	private static PoweredCruiseSkewWakeReferenceHandoffExtrema extrema(
			List<PoweredCruiseSkewWakeReferenceHandoffScenario> scenarios
	) {
		int ready = 0;
		int exportAllowed = 0;
		int runtimeAllowed = 0;
		int autoApply = 0;
		int maxObserved = 0;
		int maxReady = 0;
		int maxBlocked = 0;
		double maxMomentum = 0.0;
		for (PoweredCruiseSkewWakeReferenceHandoffScenario scenario : scenarios) {
			PoweredCruiseSkewWakeReferenceHandoffSummary summary = scenario.summary();
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
			maxMomentum = Math.max(maxMomentum, summary.maxMomentumErrorRatio());
		}
		return new PoweredCruiseSkewWakeReferenceHandoffExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				exportAllowed,
				runtimeAllowed,
				autoApply,
				maxObserved,
				maxReady,
				maxBlocked,
				maxMomentum
		);
	}
}
