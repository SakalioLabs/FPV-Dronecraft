package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Coupling-Conservation-Blocker-Report-Packet";
	public static final String CAVEAT =
			"Blocker report decomposes the final powered-source conservation guard into upstream coupling-readiness, live hover/cruise conservation evidence, and runtime/gameplay leak reasons only.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredSourceCouplingConservationGuard.SCENARIO_SAMPLE_COUNT;
	public static final int SCENARIO_METRIC_COUNT = 34;
	public static final int SUMMARY_METRIC_ROW_COUNT = 15;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport() {
	}

	public record PoweredSourceCouplingConservationBlockerSummary(
			boolean runtimePoweredSourceCouplingAllowed,
			int blockerCount,
			String couplingReadinessScenarioName,
			boolean couplingReadinessBlocker,
			boolean poweredSourceApiSurfaceBlocker,
			boolean acceptanceBudgetBlocker,
			boolean policyRuntimeBlocker,
			boolean hoverAndCruiseCouplingReadinessBlocker,
			boolean targetModelSelfConsistencyBlocker,
			boolean conservationEvidenceBlocker,
			boolean hoverConservationBlocker,
			boolean cruiseConservationBlocker,
			boolean gameplayAutoApplyLeakBlocker,
			boolean runtimeCouplingReadinessAllowed,
			boolean poweredSourceApiSurfaceReady,
			boolean acceptanceBudgetGateReady,
			boolean allPoliciesRuntimeAllowed,
			boolean hoverAndCruiseCouplingAllowed,
			boolean liveConservationEvidenceAccepted,
			boolean hoverLiveConservationAccepted,
			boolean cruiseLiveConservationAccepted,
			int conservationRowCount,
			int conservationTargetSelfConsistentCount,
			int liveConservationAcceptedCount,
			int sourceForceDeltaRequiredCount,
			int sourceMomentDeltaRequiredCount,
			int wakeResidualRequiredCount,
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio,
			String conservationPayloadKind,
			String runtimeInfo,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceCouplingConservationBlockerScenario(
			String scenarioName,
			PoweredSourceCouplingConservationBlockerSummary summary
	) {
	}

	public record PoweredSourceCouplingConservationBlockerExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int couplingReadinessBlockerScenarioCount,
			int conservationEvidenceBlockerScenarioCount,
			int hoverConservationBlockerScenarioCount,
			int cruiseConservationBlockerScenarioCount,
			int targetModelSelfConsistencyBlockerScenarioCount,
			int gameplayAutoApplyLeakBlockerScenarioCount,
			int runtimePoweredSourceCouplingAllowedCount,
			int maxLiveConservationAcceptedCount,
			int maxConservationTargetSelfConsistentCount,
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio
	) {
	}

	public record PoweredSourceCouplingConservationBlockerAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceCouplingConservationBlockerScenario> scenarios,
			PoweredSourceCouplingConservationBlockerExtrema extrema
	) {
		public PoweredSourceCouplingConservationBlockerAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceCouplingConservationBlockerAudit audit() {
		return audit(Aerodynamics4McL2PoweredSourceCouplingConservationGuard.audit());
	}

	public static PoweredSourceCouplingConservationBlockerAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2PoweredSourceCouplingConservationGuard.audit(loader));
	}

	public static PoweredSourceCouplingConservationBlockerAudit audit(
			Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardAudit guardAudit
	) {
		if (guardAudit == null) {
			throw new IllegalArgumentException("guardAudit must not be null.");
		}
		List<PoweredSourceCouplingConservationBlockerScenario> scenarios = guardAudit.scenarios().stream()
				.map(scenario -> new PoweredSourceCouplingConservationBlockerScenario(
						scenario.scenarioName(),
						report(scenario.summary())))
				.toList();
		return new PoweredSourceCouplingConservationBlockerAudit(
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

	public static PoweredSourceCouplingConservationBlockerSummary report(
			Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardSummary guard
	) {
		if (guard == null) {
			throw new IllegalArgumentException("guard summary must not be null.");
		}
		boolean couplingReadinessBlocker = !guard.runtimeCouplingReadinessAllowed();
		boolean apiSurfaceBlocker = !guard.poweredSourceApiSurfaceReady();
		boolean acceptanceBudgetBlocker = !guard.acceptanceBudgetGateReady();
		boolean policyRuntimeBlocker = !guard.allPoliciesRuntimeAllowed();
		boolean hoverCruiseCouplingBlocker = !guard.hoverAndCruiseCouplingAllowed();
		boolean targetModelBlocker = guard.conservationTargetSelfConsistentCount() < guard.conservationRowCount();
		boolean conservationEvidenceBlocker = !guard.liveConservationEvidenceAccepted();
		boolean hoverConservationBlocker = !guard.hoverLiveConservationAccepted();
		boolean cruiseConservationBlocker = !guard.cruiseLiveConservationAccepted();
		boolean gameplayLeakBlocker = guard.gameplayAutoApplyAllowed();
		int blockerCount = countTrue(
				couplingReadinessBlocker,
				apiSurfaceBlocker,
				acceptanceBudgetBlocker,
				policyRuntimeBlocker,
				hoverCruiseCouplingBlocker,
				targetModelBlocker,
				conservationEvidenceBlocker,
				hoverConservationBlocker,
				cruiseConservationBlocker,
				gameplayLeakBlocker);
		boolean allowed = guard.runtimePoweredSourceCouplingAllowed() && blockerCount == 0;
		return new PoweredSourceCouplingConservationBlockerSummary(
				allowed,
				blockerCount,
				guard.couplingReadinessScenarioName(),
				couplingReadinessBlocker,
				apiSurfaceBlocker,
				acceptanceBudgetBlocker,
				policyRuntimeBlocker,
				hoverCruiseCouplingBlocker,
				targetModelBlocker,
				conservationEvidenceBlocker,
				hoverConservationBlocker,
				cruiseConservationBlocker,
				gameplayLeakBlocker,
				guard.runtimeCouplingReadinessAllowed(),
				guard.poweredSourceApiSurfaceReady(),
				guard.acceptanceBudgetGateReady(),
				guard.allPoliciesRuntimeAllowed(),
				guard.hoverAndCruiseCouplingAllowed(),
				guard.liveConservationEvidenceAccepted(),
				guard.hoverLiveConservationAccepted(),
				guard.cruiseLiveConservationAccepted(),
				guard.conservationRowCount(),
				guard.conservationTargetSelfConsistentCount(),
				guard.liveConservationAcceptedCount(),
				guard.sourceForceDeltaRequiredCount(),
				guard.sourceMomentDeltaRequiredCount(),
				guard.wakeResidualRequiredCount(),
				guard.maxMomentumClosureErrorRatio(),
				guard.maxKineticPowerClosureErrorRatio(),
				guard.conservationPayloadKind(),
				guard.runtimeInfo(),
				nextRequiredAction(
						couplingReadinessBlocker,
						apiSurfaceBlocker,
						acceptanceBudgetBlocker,
						policyRuntimeBlocker,
						hoverCruiseCouplingBlocker,
						targetModelBlocker,
						hoverConservationBlocker,
						cruiseConservationBlocker,
						conservationEvidenceBlocker,
						gameplayLeakBlocker),
				allowed ? "READY" : "BLOCKED",
				allowed ? "powered-source-coupling-conservation-clear"
						: "powered-source-coupling-conservation-blocked"
		);
	}

	private static String nextRequiredAction(
			boolean couplingReadinessBlocker,
			boolean apiSurfaceBlocker,
			boolean acceptanceBudgetBlocker,
			boolean policyRuntimeBlocker,
			boolean hoverCruiseCouplingBlocker,
			boolean targetModelBlocker,
			boolean hoverConservationBlocker,
			boolean cruiseConservationBlocker,
			boolean conservationEvidenceBlocker,
			boolean gameplayLeakBlocker
	) {
		if (apiSurfaceBlocker) {
			return "wait-for-public-a4mc-powered-source-api-surface";
		}
		if (acceptanceBudgetBlocker) {
			return "complete-powered-source-acceptance-budget-gate";
		}
		if (policyRuntimeBlocker || hoverCruiseCouplingBlocker || couplingReadinessBlocker) {
			return "complete-powered-source-coupling-readiness-gate";
		}
		if (targetModelBlocker) {
			return "repair-powered-source-conservation-target-model";
		}
		if (hoverConservationBlocker && cruiseConservationBlocker) {
			return "capture-live-hover-and-cruise-powered-source-conservation-evidence";
		}
		if (hoverConservationBlocker) {
			return "capture-live-hover-powered-source-conservation-evidence";
		}
		if (cruiseConservationBlocker) {
			return "capture-live-cruise-powered-source-conservation-evidence";
		}
		if (conservationEvidenceBlocker) {
			return "capture-live-a4mc-powered-source-force-moment-and-wake-residuals";
		}
		if (gameplayLeakBlocker) {
			return "keep-gameplay-auto-apply-disabled-for-powered-source-coupling";
		}
		return "runtime-powered-source-coupling-ready-after-conservation-review";
	}

	private static PoweredSourceCouplingConservationBlockerExtrema extrema(
			List<PoweredSourceCouplingConservationBlockerScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int coupling = 0;
		int conservation = 0;
		int hover = 0;
		int cruise = 0;
		int target = 0;
		int gameplay = 0;
		int runtimeAllowed = 0;
		int maxAccepted = 0;
		int maxTarget = 0;
		double maxMomentum = 0.0;
		double maxPower = 0.0;
		for (PoweredSourceCouplingConservationBlockerScenario scenario : scenarios) {
			PoweredSourceCouplingConservationBlockerSummary summary = scenario.summary();
			if (summary.runtimePoweredSourceCouplingAllowed()) {
				ready++;
				runtimeAllowed++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.couplingReadinessBlocker()) {
				coupling++;
			}
			if (summary.conservationEvidenceBlocker()) {
				conservation++;
			}
			if (summary.hoverConservationBlocker()) {
				hover++;
			}
			if (summary.cruiseConservationBlocker()) {
				cruise++;
			}
			if (summary.targetModelSelfConsistencyBlocker()) {
				target++;
			}
			if (summary.gameplayAutoApplyLeakBlocker()) {
				gameplay++;
			}
			maxAccepted = Math.max(maxAccepted, summary.liveConservationAcceptedCount());
			maxTarget = Math.max(maxTarget, summary.conservationTargetSelfConsistentCount());
			maxMomentum = Math.max(maxMomentum, summary.maxMomentumClosureErrorRatio());
			maxPower = Math.max(maxPower, summary.maxKineticPowerClosureErrorRatio());
		}
		return new PoweredSourceCouplingConservationBlockerExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				coupling,
				conservation,
				hover,
				cruise,
				target,
				gameplay,
				runtimeAllowed,
				maxAccepted,
				maxTarget,
				maxMomentum,
				maxPower
		);
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
}
