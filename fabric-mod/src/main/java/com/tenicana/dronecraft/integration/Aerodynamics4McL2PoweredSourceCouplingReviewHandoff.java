package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceCouplingReviewHandoff {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Coupling-Review-Handoff-Packet";
	public static final String CAVEAT =
			"Review handoff may export powered-source coupling simulation evidence only after the final conservation guard is clear; it keeps playable use behind downstream review and never enables gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 2;
	public static final int SCENARIO_METRIC_COUNT = 32;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private static final String CURRENT_SCENARIO = "current_powered_source_coupling_review_blocked";
	private static final String READY_SCENARIO = "synthetic_powered_source_coupling_review_ready";

	private Aerodynamics4McL2PoweredSourceCouplingReviewHandoff() {
	}

	public record PoweredSourceCouplingReviewHandoffSummary(
			boolean poweredSourceCouplingReviewAllowed,
			boolean simulationReferenceMaterialExportAllowed,
			int blockerCount,
			boolean finalGuardRuntimePoweredSourceCouplingAllowed,
			boolean upstreamCouplingReadinessAllowed,
			boolean poweredSourceApiSurfaceReady,
			boolean acceptanceBudgetGateReady,
			boolean policyRuntimeAllowed,
			boolean hoverAndCruiseCouplingAllowed,
			boolean liveConservationEvidenceAccepted,
			boolean hoverLiveConservationAccepted,
			boolean cruiseLiveConservationAccepted,
			boolean conservationTargetSelfConsistent,
			int conservationRowCount,
			int liveConservationAcceptedCount,
			int sourceForceDeltaRequiredCount,
			int sourceMomentDeltaRequiredCount,
			int wakeResidualRequiredCount,
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio,
			boolean couplingReadinessBlocker,
			boolean conservationEvidenceBlocker,
			boolean hoverConservationBlocker,
			boolean cruiseConservationBlocker,
			boolean targetModelSelfConsistencyBlocker,
			boolean gameplayAutoApplyLeakBlocker,
			boolean gameplayAutoApplyAllowed,
			boolean playableReviewRequiredBeforeUse,
			String reviewPayloadKind,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceCouplingReviewHandoffScenario(
			String scenarioName,
			PoweredSourceCouplingReviewHandoffSummary summary
	) {
	}

	public record PoweredSourceCouplingReviewHandoffExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int reviewAllowedCount,
			int referenceMaterialExportAllowedCount,
			int maxBlockerCount,
			int couplingReadinessBlockerScenarioCount,
			int conservationEvidenceBlockerScenarioCount,
			int targetModelSelfConsistencyBlockerScenarioCount,
			int gameplayAutoApplyLeakBlockerScenarioCount,
			int gameplayAutoApplyAllowedCount,
			int playableReviewRequiredBeforeUseCount
	) {
	}

	public record PoweredSourceCouplingReviewHandoffAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceCouplingReviewHandoffScenario> scenarios,
			PoweredSourceCouplingReviewHandoffExtrema extrema
	) {
		public PoweredSourceCouplingReviewHandoffAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceCouplingReviewHandoffAudit audit() {
		return audit(Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.audit());
	}

	public static PoweredSourceCouplingReviewHandoffAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.audit(loader));
	}

	public static PoweredSourceCouplingReviewHandoffAudit audit(
			Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport
					.PoweredSourceCouplingConservationBlockerAudit blockerAudit
	) {
		if (blockerAudit == null) {
			throw new IllegalArgumentException("blockerAudit must not be null.");
		}
		List<PoweredSourceCouplingReviewHandoffScenario> scenarios = List.of(
				new PoweredSourceCouplingReviewHandoffScenario(
						CURRENT_SCENARIO,
						handoff(blocker(blockerAudit, "current_coupling_and_conservation_blocked"))),
				new PoweredSourceCouplingReviewHandoffScenario(
						READY_SCENARIO,
						handoff(blocker(blockerAudit, "coupling_and_conservation_ready")))
		);
		return new PoweredSourceCouplingReviewHandoffAudit(
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

	public static PoweredSourceCouplingReviewHandoffSummary handoff(
			Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport
					.PoweredSourceCouplingConservationBlockerSummary blocker
	) {
		if (blocker == null) {
			throw new IllegalArgumentException("blocker summary must not be null.");
		}
		boolean targetSelfConsistent = blocker.conservationTargetSelfConsistentCount()
				== blocker.conservationRowCount();
		boolean reviewAllowed = blocker.runtimePoweredSourceCouplingAllowed()
				&& blocker.blockerCount() == 0
				&& !blocker.gameplayAutoApplyLeakBlocker();
		return new PoweredSourceCouplingReviewHandoffSummary(
				reviewAllowed,
				reviewAllowed,
				blocker.blockerCount(),
				blocker.runtimePoweredSourceCouplingAllowed(),
				blocker.runtimeCouplingReadinessAllowed(),
				blocker.poweredSourceApiSurfaceReady(),
				blocker.acceptanceBudgetGateReady(),
				blocker.allPoliciesRuntimeAllowed(),
				blocker.hoverAndCruiseCouplingAllowed(),
				blocker.liveConservationEvidenceAccepted(),
				blocker.hoverLiveConservationAccepted(),
				blocker.cruiseLiveConservationAccepted(),
				targetSelfConsistent,
				blocker.conservationRowCount(),
				blocker.liveConservationAcceptedCount(),
				blocker.sourceForceDeltaRequiredCount(),
				blocker.sourceMomentDeltaRequiredCount(),
				blocker.wakeResidualRequiredCount(),
				blocker.maxMomentumClosureErrorRatio(),
				blocker.maxKineticPowerClosureErrorRatio(),
				blocker.couplingReadinessBlocker(),
				blocker.conservationEvidenceBlocker(),
				blocker.hoverConservationBlocker(),
				blocker.cruiseConservationBlocker(),
				blocker.targetModelSelfConsistencyBlocker(),
				blocker.gameplayAutoApplyLeakBlocker(),
				false,
				true,
				"powered-source-force-moment-wake-conservation-review-package",
				reviewAllowed
						? "powered-source-coupling-evidence-ready-for-reviewed-reference-handoff"
						: blocker.nextRequiredAction(),
				reviewAllowed ? "READY" : "BLOCKED",
				reviewAllowed
						? "powered-source-coupling-review-handoff-ready"
						: "powered-source-coupling-review-handoff-blocked"
		);
	}

	private static Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport
			.PoweredSourceCouplingConservationBlockerSummary blocker(
					Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport
							.PoweredSourceCouplingConservationBlockerAudit audit,
					String scenarioName
			) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static PoweredSourceCouplingReviewHandoffExtrema extrema(
			List<PoweredSourceCouplingReviewHandoffScenario> scenarios
	) {
		int ready = 0;
		int review = 0;
		int export = 0;
		int maxBlockers = 0;
		int coupling = 0;
		int conservation = 0;
		int target = 0;
		int gameplayLeak = 0;
		int gameplayAuto = 0;
		int playableReview = 0;
		for (PoweredSourceCouplingReviewHandoffScenario scenario : scenarios) {
			PoweredSourceCouplingReviewHandoffSummary summary = scenario.summary();
			if (summary.poweredSourceCouplingReviewAllowed()) {
				ready++;
				review++;
			}
			if (summary.simulationReferenceMaterialExportAllowed()) {
				export++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.couplingReadinessBlocker()) {
				coupling++;
			}
			if (summary.conservationEvidenceBlocker()) {
				conservation++;
			}
			if (summary.targetModelSelfConsistencyBlocker()) {
				target++;
			}
			if (summary.gameplayAutoApplyLeakBlocker()) {
				gameplayLeak++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplayAuto++;
			}
			if (summary.playableReviewRequiredBeforeUse()) {
				playableReview++;
			}
		}
		return new PoweredSourceCouplingReviewHandoffExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				review,
				export,
				maxBlockers,
				coupling,
				conservation,
				target,
				gameplayLeak,
				gameplayAuto,
				playableReview
		);
	}
}
