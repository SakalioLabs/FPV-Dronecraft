package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceCouplingReviewHandoff {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Coupling-Review-Handoff-Packet";
	public static final String CAVEAT =
			"Review handoff may export powered-source coupling simulation evidence only after the final force/moment/wake and swirl angular-momentum conservation guard is clear and the nearfield wake plus OpenFOAM rotor-reference package is ready with coefficient lookup shape-guard evidence; it keeps playable use behind downstream review and never enables gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 9;
	public static final int SCENARIO_SAMPLE_COUNT = 2;
	public static final int SCENARIO_METRIC_COUNT = 60;
	public static final int SUMMARY_METRIC_ROW_COUNT = 21;
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
			boolean liveSwirlConservationEvidenceAccepted,
			boolean hoverLiveConservationAccepted,
			boolean cruiseLiveConservationAccepted,
			boolean hoverLiveSwirlConservationAccepted,
			boolean cruiseLiveSwirlConservationAccepted,
			boolean nearfieldReferencePackageExportAllowed,
			boolean nearfieldReferencePackageBlocker,
			int nearfieldExpectedReferenceRowCount,
			int nearfieldOpenFoamAvailableReferenceRowCount,
			boolean nearfieldOpenFoamCoefficientLookupShapeGuardReady,
			int nearfieldOpenFoamCoefficientLookupShapeGuardReadyRowCount,
			int nearfieldOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount,
			int nearfieldOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount,
			int nearfieldOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount,
			double nearfieldOpenFoamCoefficientArchiveCurveEtaFormulaResidual,
			double nearfieldOpenFoamCoefficientArchiveCurveCtIncrease,
			boolean conservationTargetSelfConsistent,
			boolean swirlConservationTargetSelfConsistent,
			int conservationRowCount,
			int liveConservationAcceptedCount,
			int sourceForceDeltaRequiredCount,
			int sourceMomentDeltaRequiredCount,
			int wakeResidualRequiredCount,
			int swirlConservationRowCount,
			int liveSwirlConservationAcceptedCount,
			int wakeTangentialVelocityRequiredCount,
			int wakeAngularMomentumResidualRequiredCount,
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio,
			double maxTargetTangentialWakeVelocityMetersPerSecond,
			double maxSwirlPowerFractionOfMomentumPower,
			double maxNetTorqueCancellationErrorRatio,
			boolean couplingReadinessBlocker,
			boolean conservationEvidenceBlocker,
			boolean hoverConservationBlocker,
			boolean cruiseConservationBlocker,
			boolean swirlConservationEvidenceBlocker,
			boolean hoverSwirlConservationBlocker,
			boolean cruiseSwirlConservationBlocker,
			boolean nearfieldReferenceBlocker,
			boolean targetModelSelfConsistencyBlocker,
			boolean swirlTargetSelfConsistencyBlocker,
			boolean gameplayAutoApplyLeakBlocker,
			boolean gameplayAutoApplyAllowed,
			boolean playableReviewRequiredBeforeUse,
			String reviewPayloadKind,
			String nearfieldReferencePayloadKind,
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
			int swirlConservationEvidenceBlockerScenarioCount,
			int nearfieldReferenceBlockerScenarioCount,
			int maxNearfieldOpenFoamCoefficientLookupShapeGuardReadyRowCount,
			int maxNearfieldOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount,
			int maxNearfieldOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount,
			int maxNearfieldOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount,
			double maxNearfieldOpenFoamCoefficientArchiveCurveEtaFormulaResidual,
			double maxNearfieldOpenFoamCoefficientArchiveCurveCtIncrease,
			int targetModelSelfConsistencyBlockerScenarioCount,
			int swirlTargetSelfConsistencyBlockerScenarioCount,
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
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit nearfieldAudit =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.audit();
		List<PoweredSourceCouplingReviewHandoffScenario> scenarios = List.of(
				new PoweredSourceCouplingReviewHandoffScenario(
						CURRENT_SCENARIO,
						handoff(
								blocker(blockerAudit, "current_coupling_and_conservation_blocked"),
								nearfield(nearfieldAudit, "current_hover_cruise_and_openfoam_reference_blocked"))),
				new PoweredSourceCouplingReviewHandoffScenario(
						READY_SCENARIO,
						handoff(
								blocker(blockerAudit, "coupling_and_conservation_ready"),
								nearfield(nearfieldAudit, "hover_cruise_and_openfoam_reference_ready")))
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
		return handoff(blocker, Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.audit()
				.scenarios()
				.stream()
				.filter(scenario -> "current_hover_cruise_and_openfoam_reference_blocked"
						.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary());
	}

	public static PoweredSourceCouplingReviewHandoffSummary handoff(
			Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport
					.PoweredSourceCouplingConservationBlockerSummary blocker,
			Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary nearfield
	) {
		if (blocker == null || nearfield == null) {
			throw new IllegalArgumentException("blocker summary must not be null.");
		}
		boolean targetSelfConsistent = blocker.conservationTargetSelfConsistentCount()
				== blocker.conservationRowCount();
		boolean swirlTargetSelfConsistent = blocker.swirlTargetSelfConsistentCount()
				== blocker.swirlConservationRowCount();
		boolean nearfieldReferenceBlocker = !nearfield.nearfieldReferencePackageExportAllowed();
		int blockerCount = blocker.blockerCount() + (nearfieldReferenceBlocker ? 1 : 0);
		boolean reviewAllowed = blocker.runtimePoweredSourceCouplingAllowed()
				&& blockerCount == 0
				&& nearfield.nearfieldReferencePackageExportAllowed()
				&& !blocker.gameplayAutoApplyLeakBlocker();
		return new PoweredSourceCouplingReviewHandoffSummary(
				reviewAllowed,
				reviewAllowed,
				blockerCount,
				blocker.runtimePoweredSourceCouplingAllowed(),
				blocker.runtimeCouplingReadinessAllowed(),
				blocker.poweredSourceApiSurfaceReady(),
				blocker.acceptanceBudgetGateReady(),
				blocker.allPoliciesRuntimeAllowed(),
				blocker.hoverAndCruiseCouplingAllowed(),
				blocker.liveConservationEvidenceAccepted(),
				blocker.liveSwirlConservationEvidenceAccepted(),
				blocker.hoverLiveConservationAccepted(),
				blocker.cruiseLiveConservationAccepted(),
				blocker.hoverLiveSwirlConservationAccepted(),
				blocker.cruiseLiveSwirlConservationAccepted(),
				nearfield.nearfieldReferencePackageExportAllowed(),
				nearfieldReferenceBlocker,
				nearfield.totalExpectedReferenceRowCount(),
				nearfield.openFoamAvailableReferenceRowCount(),
				nearfield.openFoamCoefficientLookupShapeGuardReady(),
				nearfield.openFoamCoefficientLookupShapeGuardReadyRowCount(),
				nearfield.openFoamCoefficientLookupShapeGuardInheritedScenarioCount(),
				nearfield.openFoamCoefficientLookupShapeGuardBlockedScenarioCount(),
				nearfield.openFoamCoefficientNegativeThrustTailExecutionInputRowCount(),
				nearfield.openFoamCoefficientArchiveCurveEtaFormulaResidual(),
				nearfield.openFoamCoefficientArchiveCurveCtIncrease(),
				targetSelfConsistent,
				swirlTargetSelfConsistent,
				blocker.conservationRowCount(),
				blocker.liveConservationAcceptedCount(),
				blocker.sourceForceDeltaRequiredCount(),
				blocker.sourceMomentDeltaRequiredCount(),
				blocker.wakeResidualRequiredCount(),
				blocker.swirlConservationRowCount(),
				blocker.liveSwirlConservationAcceptedCount(),
				blocker.wakeTangentialVelocityRequiredCount(),
				blocker.wakeAngularMomentumResidualRequiredCount(),
				blocker.maxMomentumClosureErrorRatio(),
				blocker.maxKineticPowerClosureErrorRatio(),
				blocker.maxTargetTangentialWakeVelocityMetersPerSecond(),
				blocker.maxSwirlPowerFractionOfMomentumPower(),
				blocker.maxNetTorqueCancellationErrorRatio(),
				blocker.couplingReadinessBlocker(),
				blocker.conservationEvidenceBlocker(),
				blocker.hoverConservationBlocker(),
				blocker.cruiseConservationBlocker(),
				blocker.swirlConservationEvidenceBlocker(),
				blocker.hoverSwirlConservationBlocker(),
				blocker.cruiseSwirlConservationBlocker(),
				nearfieldReferenceBlocker,
				blocker.targetModelSelfConsistencyBlocker(),
				blocker.swirlTargetSelfConsistencyBlocker(),
				blocker.gameplayAutoApplyLeakBlocker(),
				false,
				true,
				"powered-source-force-moment-wake-and-swirl-conservation-review-package",
				nearfield.referencePayloadKind(),
				reviewAllowed
						? "powered-source-coupling-evidence-ready-for-reviewed-reference-handoff"
						: nextRequiredAction(blocker, nearfieldReferenceBlocker),
				reviewAllowed ? "READY" : "BLOCKED",
				reviewAllowed
						? "powered-source-coupling-review-handoff-ready"
						: "powered-source-coupling-review-handoff-blocked"
		);
	}

	private static String nextRequiredAction(
			Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport
					.PoweredSourceCouplingConservationBlockerSummary blocker,
			boolean nearfieldReferenceBlocker
	) {
		if (blocker.blockerCount() == 0 && nearfieldReferenceBlocker) {
			return "complete-nearfield-wake-and-openfoam-reference-package";
		}
		return blocker.nextRequiredAction();
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

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary nearfield(
			Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit audit,
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
		int swirl = 0;
		int nearfield = 0;
		int maxCoefficientReadyRows = 0;
		int maxCoefficientInherited = 0;
		int maxCoefficientBlocked = 0;
		int maxCoefficientNegativeTail = 0;
		double maxCoefficientEta = 0.0;
		double maxCoefficientCt = 0.0;
		int target = 0;
		int swirlTarget = 0;
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
			if (summary.swirlConservationEvidenceBlocker()) {
				swirl++;
			}
			if (summary.nearfieldReferenceBlocker()) {
				nearfield++;
			}
			maxCoefficientReadyRows = Math.max(maxCoefficientReadyRows,
					summary.nearfieldOpenFoamCoefficientLookupShapeGuardReadyRowCount());
			maxCoefficientInherited = Math.max(maxCoefficientInherited,
					summary.nearfieldOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount());
			maxCoefficientBlocked = Math.max(maxCoefficientBlocked,
					summary.nearfieldOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount());
			maxCoefficientNegativeTail = Math.max(maxCoefficientNegativeTail,
					summary.nearfieldOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount());
			maxCoefficientEta = Math.max(maxCoefficientEta,
					summary.nearfieldOpenFoamCoefficientArchiveCurveEtaFormulaResidual());
			maxCoefficientCt = Math.max(maxCoefficientCt,
					summary.nearfieldOpenFoamCoefficientArchiveCurveCtIncrease());
			if (summary.targetModelSelfConsistencyBlocker()) {
				target++;
			}
			if (summary.swirlTargetSelfConsistencyBlocker()) {
				swirlTarget++;
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
				swirl,
				nearfield,
				maxCoefficientReadyRows,
				maxCoefficientInherited,
				maxCoefficientBlocked,
				maxCoefficientNegativeTail,
				maxCoefficientEta,
				maxCoefficientCt,
				target,
				swirlTarget,
				gameplayLeak,
				gameplayAuto,
				playableReview
		);
	}
}
