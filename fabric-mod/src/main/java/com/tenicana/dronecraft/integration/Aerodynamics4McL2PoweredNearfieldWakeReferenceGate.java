package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredNearfieldWakeReferenceGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Nearfield-Wake-Reference-Gate-Packet";
	public static final String CAVEAT =
			"Nearfield wake reference gate combines hover surface-wake and cruise skew-wake lab reference handoffs into audit-only export readiness; it does not enable runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 30;
	public static final int SUMMARY_METRIC_ROW_COUNT = 13;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredNearfieldWakeReferenceGate() {
	}

	public record PoweredNearfieldWakeReferenceSummary(
			boolean nearfieldReferencePackageExportAllowed,
			int blockerCount,
			boolean hoverSurfaceWakeReferenceBlocker,
			boolean cruiseSkewWakeReferenceBlocker,
			boolean hoverLabValidationAccepted,
			boolean cruiseLabValidationAccepted,
			boolean hoverErrorBudgetReady,
			boolean cruiseErrorBudgetReady,
			boolean hoverReferenceMaterialExportAllowed,
			boolean cruiseReferenceMaterialExportAllowed,
			int hoverExpectedReferenceRowCount,
			int cruiseExpectedReferenceRowCount,
			int totalExpectedReferenceRowCount,
			int hoverReadyErrorBudgetGroupCount,
			int cruiseReadyErrorBudgetGroupCount,
			int hoverBlockedErrorBudgetGroupCount,
			int cruiseBlockedErrorBudgetGroupCount,
			double hoverMaxPressureErrorRatio,
			double hoverMaxWakeVelocityErrorRatio,
			double hoverMaxArrivalBandErrorRatio,
			double cruiseMaxPressureErrorRatio,
			double cruiseMaxWakeVelocityErrorRatio,
			double cruiseMaxArrivalWindowErrorRatio,
			double cruiseMaxMomentumErrorRatio,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String referencePayloadKind,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredNearfieldWakeReferenceScenario(
			String scenarioName,
			PoweredNearfieldWakeReferenceSummary summary
	) {
	}

	public record PoweredNearfieldWakeReferenceExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int hoverReferenceBlockerScenarioCount,
			int cruiseReferenceBlockerScenarioCount,
			int referencePackageExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int maxTotalExpectedReferenceRowCount,
			int maxHoverBlockedErrorBudgetGroupCount,
			int maxCruiseBlockedErrorBudgetGroupCount,
			double maxCruiseMomentumErrorRatio
	) {
	}

	public record PoweredNearfieldWakeReferenceAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredNearfieldWakeReferenceScenario> scenarios,
			PoweredNearfieldWakeReferenceExtrema extrema
	) {
		public PoweredNearfieldWakeReferenceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredNearfieldWakeReferenceAudit audit() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffAudit hoverAudit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.audit();
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffAudit cruiseAudit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.audit();
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hoverCurrent =
				hover(hoverAudit, "current_lab_validation_blocked");
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hoverReady =
				hover(hoverAudit, "lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruiseCurrent =
				cruise(cruiseAudit, "current_lab_validation_blocked");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruiseReady =
				cruise(cruiseAudit, "lab_accepted_error_budget_ready");
		List<PoweredNearfieldWakeReferenceScenario> scenarios = List.of(
				new PoweredNearfieldWakeReferenceScenario(
						"current_hover_and_cruise_reference_blocked",
						gate(hoverCurrent, cruiseCurrent)),
				new PoweredNearfieldWakeReferenceScenario(
						"hover_ready_cruise_reference_blocked",
						gate(hoverReady, cruiseCurrent)),
				new PoweredNearfieldWakeReferenceScenario(
						"cruise_ready_hover_reference_blocked",
						gate(hoverCurrent, cruiseReady)),
				new PoweredNearfieldWakeReferenceScenario(
						"hover_and_cruise_reference_ready",
						gate(hoverReady, cruiseReady))
		);
		return new PoweredNearfieldWakeReferenceAudit(
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

	public static PoweredNearfieldWakeReferenceSummary gate(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hover,
			Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruise
	) {
		if (hover == null || cruise == null) {
			throw new IllegalArgumentException("hover and cruise reference handoff summaries are required.");
		}
		boolean hoverBlocker = !hover.referenceMaterialExportAllowed();
		boolean cruiseBlocker = !cruise.referenceMaterialExportAllowed();
		int blockerCount = countTrue(hoverBlocker, cruiseBlocker);
		boolean exportAllowed = blockerCount == 0;
		return new PoweredNearfieldWakeReferenceSummary(
				exportAllowed,
				blockerCount,
				hoverBlocker,
				cruiseBlocker,
				hover.labValidationAccepted(),
				cruise.labValidationAccepted(),
				hover.allErrorBudgetGroupsReady(),
				cruise.allErrorBudgetGroupsReady(),
				hover.referenceMaterialExportAllowed(),
				cruise.referenceMaterialExportAllowed(),
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.REFERENCE_SAMPLE_COUNT,
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.REFERENCE_SAMPLE_COUNT,
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.REFERENCE_SAMPLE_COUNT
						+ Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.REFERENCE_SAMPLE_COUNT,
				hover.readyErrorBudgetGroupCount(),
				cruise.readyErrorBudgetGroupCount(),
				hover.blockedErrorBudgetGroupCount(),
				cruise.blockedErrorBudgetGroupCount(),
				hover.maxPressureErrorRatio(),
				hover.maxWakeVelocityErrorRatio(),
				hover.maxArrivalBandErrorRatio(),
				cruise.maxPressureErrorRatio(),
				cruise.maxWakeVelocityErrorRatio(),
				cruise.maxArrivalWindowErrorRatio(),
				cruise.maxMomentumErrorRatio(),
				false,
				false,
				"combined-hover-surface-and-cruise-skew-wake-reference-package",
				nextRequiredAction(hoverBlocker, cruiseBlocker),
				exportAllowed ? "READY" : "BLOCKED",
				exportAllowed ? "nearfield-wake-reference-package-ready" : "nearfield-wake-reference-package-blocked"
		);
	}

	private static String nextRequiredAction(boolean hoverBlocker, boolean cruiseBlocker) {
		if (hoverBlocker && cruiseBlocker) {
			return "complete-hover-surface-and-cruise-skew-wake-reference-handoffs";
		}
		if (hoverBlocker) {
			return "complete-hover-surface-wake-reference-handoff";
		}
		if (cruiseBlocker) {
			return "complete-cruise-skew-wake-reference-handoff";
		}
		return "nearfield-wake-reference-package-ready-for-reviewed-export";
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hover(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruise(
			Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static int countTrue(boolean... values) {
		int count = 0;
		for (boolean value : values) {
			if (value) {
				count++;
			}
		}
		return count;
	}

	private static PoweredNearfieldWakeReferenceExtrema extrema(
			List<PoweredNearfieldWakeReferenceScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int hover = 0;
		int cruise = 0;
		int exportAllowed = 0;
		int runtime = 0;
		int autoApply = 0;
		int maxRows = 0;
		int maxHoverBlocked = 0;
		int maxCruiseBlocked = 0;
		double maxMomentum = 0.0;
		for (PoweredNearfieldWakeReferenceScenario scenario : scenarios) {
			PoweredNearfieldWakeReferenceSummary summary = scenario.summary();
			if (summary.nearfieldReferencePackageExportAllowed()) {
				ready++;
				exportAllowed++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.hoverSurfaceWakeReferenceBlocker()) {
				hover++;
			}
			if (summary.cruiseSkewWakeReferenceBlocker()) {
				cruise++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				autoApply++;
			}
			maxRows = Math.max(maxRows, summary.totalExpectedReferenceRowCount());
			maxHoverBlocked = Math.max(maxHoverBlocked, summary.hoverBlockedErrorBudgetGroupCount());
			maxCruiseBlocked = Math.max(maxCruiseBlocked, summary.cruiseBlockedErrorBudgetGroupCount());
			maxMomentum = Math.max(maxMomentum, summary.cruiseMaxMomentumErrorRatio());
		}
		return new PoweredNearfieldWakeReferenceExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				hover,
				cruise,
				exportAllowed,
				runtime,
				autoApply,
				maxRows,
				maxHoverBlocked,
				maxCruiseBlocked,
				maxMomentum
		);
	}
}
