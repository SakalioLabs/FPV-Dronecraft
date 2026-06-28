package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceCouplingBlockerReport {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Coupling-Blocker-Report-Packet";
	public static final String CAVEAT =
			"Blocker report decomposes the final powered-source coupling gate into audit reasons only; it does not enable runtime coupling or mutate gameplay configuration.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.SCENARIO_SAMPLE_COUNT;
	public static final int SCENARIO_METRIC_COUNT = 29;
	public static final int SUMMARY_METRIC_ROW_COUNT = 11;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceCouplingBlockerReport() {
	}

	public record PoweredSourceCouplingBlockerSummary(
			boolean runtimePoweredSourceCouplingAllowed,
			int blockerCount,
			boolean acceptanceHandoffBlocker,
			boolean validationBudgetBlocker,
			boolean policyRuntimeMutationBlocker,
			boolean poweredSourceApiBlocker,
			boolean gameplayCouplingBlocker,
			boolean solidDiskMaskBlocker,
			boolean rotorDiskMaskPolicyBlocker,
			boolean acceptanceBudgetGateReady,
			boolean allValidationBudgetsReady,
			boolean hoverAcceptanceHandoffReady,
			boolean cruiseAcceptanceHandoffReady,
			int readyHandoffCount,
			int expectedHandoffCount,
			boolean hoverValidationBudgetCandidate,
			boolean cruiseValidationBudgetCandidate,
			int validationBudgetCandidateCount,
			int expectedValidationBudgetGroupCount,
			int policyCount,
			int runtimeMutationAllowedPolicyCount,
			int poweredSourceApiAvailablePolicyCount,
			int poweredHoverGameplayCouplingAllowedPolicyCount,
			int poweredCruiseGameplayCouplingAllowedPolicyCount,
			int solidDiskMaskAllowedPolicyCount,
			int keepRotorDiskOpenPolicyCount,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceCouplingBlockerScenario(
			String scenarioName,
			PoweredSourceCouplingBlockerSummary summary
	) {
	}

	public record PoweredSourceCouplingBlockerExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int acceptanceHandoffBlockerScenarioCount,
			int validationBudgetBlockerScenarioCount,
			int policyRuntimeMutationBlockerScenarioCount,
			int poweredSourceApiBlockerScenarioCount,
			int gameplayCouplingBlockerScenarioCount,
			int solidDiskMaskBlockerScenarioCount,
			int rotorDiskMaskPolicyBlockerScenarioCount
	) {
	}

	public record PoweredSourceCouplingBlockerAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceCouplingBlockerScenario> scenarios,
			PoweredSourceCouplingBlockerExtrema extrema
	) {
		public PoweredSourceCouplingBlockerAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceCouplingBlockerAudit audit() {
		return audit(Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit());
	}

	public static PoweredSourceCouplingBlockerAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(loader));
	}

	public static PoweredSourceCouplingBlockerAudit audit(
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit readinessAudit
	) {
		if (readinessAudit == null) {
			throw new IllegalArgumentException("readinessAudit must not be null.");
		}
		List<PoweredSourceCouplingBlockerScenario> scenarios = readinessAudit.scenarios().stream()
				.map(scenario -> new PoweredSourceCouplingBlockerScenario(
						scenario.scenarioName(),
						report(scenario.summary())))
				.toList();
		return new PoweredSourceCouplingBlockerAudit(
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

	public static PoweredSourceCouplingBlockerSummary report(
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary readiness
	) {
		if (readiness == null) {
			throw new IllegalArgumentException("readiness summary must not be null.");
		}
		boolean handoffBlocker = !readiness.allHandoffsReady();
		boolean validationBudgetBlocker = !readiness.allValidationBudgetsReady();
		boolean runtimePolicyBlocker = !readiness.allPoliciesRuntimeAllowed();
		boolean sourceApiBlocker = readiness.policyCount() == 0
				|| readiness.poweredSourceApiAvailablePolicyCount() < readiness.policyCount();
		boolean gameplayBlocker = !readiness.hoverAndCruiseCouplingAllowed();
		boolean solidDiskBlocker = readiness.solidDiskMaskAllowedPolicyCount() > 0;
		boolean rotorMaskBlocker = !readiness.allPoliciesKeepRotorDisksOpen();
		int blockerCount = countTrue(
				handoffBlocker,
				validationBudgetBlocker,
				runtimePolicyBlocker,
				sourceApiBlocker,
				gameplayBlocker,
				solidDiskBlocker,
				rotorMaskBlocker);
		boolean allowed = readiness.runtimePoweredSourceCouplingAllowed() && blockerCount == 0;
		return new PoweredSourceCouplingBlockerSummary(
				allowed,
				blockerCount,
				handoffBlocker,
				validationBudgetBlocker,
				runtimePolicyBlocker,
				sourceApiBlocker,
				gameplayBlocker,
				solidDiskBlocker,
				rotorMaskBlocker,
				readiness.acceptanceBudgetGateReady(),
				readiness.allValidationBudgetsReady(),
				readiness.hoverAcceptanceHandoffReady(),
				readiness.cruiseAcceptanceHandoffReady(),
				readiness.readyHandoffCount(),
				readiness.expectedHandoffCount(),
				readiness.hoverValidationBudgetCandidate(),
				readiness.cruiseValidationBudgetCandidate(),
				readiness.validationBudgetCandidateCount(),
				readiness.expectedValidationBudgetGroupCount(),
				readiness.policyCount(),
				readiness.runtimeMutationAllowedPolicyCount(),
				readiness.poweredSourceApiAvailablePolicyCount(),
				readiness.poweredHoverGameplayCouplingAllowedPolicyCount(),
				readiness.poweredCruiseGameplayCouplingAllowedPolicyCount(),
				readiness.solidDiskMaskAllowedPolicyCount(),
				readiness.keepRotorDiskOpenPolicyCount(),
				nextRequiredAction(
						handoffBlocker,
						validationBudgetBlocker,
						sourceApiBlocker,
						runtimePolicyBlocker,
						gameplayBlocker,
						solidDiskBlocker,
						rotorMaskBlocker),
				allowed ? "READY" : "BLOCKED",
				allowed ? "powered-source-coupling-clear" : "powered-source-coupling-blocked"
		);
	}

	private static String nextRequiredAction(
			boolean handoffBlocker,
			boolean validationBudgetBlocker,
			boolean sourceApiBlocker,
			boolean runtimePolicyBlocker,
			boolean gameplayBlocker,
			boolean solidDiskBlocker,
			boolean rotorMaskBlocker
	) {
		if (handoffBlocker) {
			return "complete-hover-and-cruise-powered-source-acceptance-handoffs";
		}
		if (validationBudgetBlocker) {
			return "produce-hover-and-cruise-validation-error-budget-candidates";
		}
		if (sourceApiBlocker) {
			return "wait-for-porous-or-body-force-powered-source-api";
		}
		if (runtimePolicyBlocker) {
			return "allow-runtime-mutation-only-after-source-api-and-validation";
		}
		if (gameplayBlocker) {
			return "open-hover-and-cruise-powered-coupling-gates-after-validation";
		}
		if (solidDiskBlocker || rotorMaskBlocker) {
			return "keep-rotor-disks-open-and-reject-solid-disk-masks";
		}
		return "runtime-powered-source-coupling-ready-for-reviewed-activation";
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

	private static PoweredSourceCouplingBlockerExtrema extrema(
			List<PoweredSourceCouplingBlockerScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int handoff = 0;
		int validationBudget = 0;
		int runtime = 0;
		int source = 0;
		int gameplay = 0;
		int solid = 0;
		int rotorMask = 0;
		for (PoweredSourceCouplingBlockerScenario scenario : scenarios) {
			PoweredSourceCouplingBlockerSummary summary = scenario.summary();
			if (summary.runtimePoweredSourceCouplingAllowed()) {
				ready++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.acceptanceHandoffBlocker()) {
				handoff++;
			}
			if (summary.validationBudgetBlocker()) {
				validationBudget++;
			}
			if (summary.policyRuntimeMutationBlocker()) {
				runtime++;
			}
			if (summary.poweredSourceApiBlocker()) {
				source++;
			}
			if (summary.gameplayCouplingBlocker()) {
				gameplay++;
			}
			if (summary.solidDiskMaskBlocker()) {
				solid++;
			}
			if (summary.rotorDiskMaskPolicyBlocker()) {
				rotorMask++;
			}
		}
		return new PoweredSourceCouplingBlockerExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				handoff,
				validationBudget,
				runtime,
				source,
				gameplay,
				solid,
				rotorMask
		);
	}
}
