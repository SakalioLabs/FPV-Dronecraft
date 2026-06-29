package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceCouplingConservationGuard {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Coupling-Conservation-Guard-Packet";
	public static final String CAVEAT =
			"Final powered-source coupling guard keeps runtime coupling closed until the existing coupling readiness gate is open and live hover plus cruise force/moment/wake and swirl angular-momentum conservation evidence is accepted; it never enables gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_COUNT = 35;
	public static final int SUMMARY_METRIC_ROW_COUNT = 18;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceCouplingConservationGuard() {
	}

	public record PoweredSourceCouplingConservationGuardSummary(
			String couplingReadinessScenarioName,
			boolean runtimeCouplingReadinessAllowed,
			boolean poweredSourceApiSurfaceReady,
			boolean acceptanceBudgetGateReady,
			boolean allPoliciesRuntimeAllowed,
			boolean hoverAndCruiseCouplingAllowed,
			int conservationRowCount,
			int conservationTargetSelfConsistentCount,
			int liveConservationAcceptedCount,
			int sourceForceDeltaRequiredCount,
			int sourceMomentDeltaRequiredCount,
			int wakeResidualRequiredCount,
			int swirlConservationRowCount,
			int swirlTargetSelfConsistentCount,
			int liveSwirlConservationAcceptedCount,
			int wakeTangentialVelocityRequiredCount,
			int wakeAngularMomentumResidualRequiredCount,
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio,
			double maxTargetTangentialWakeVelocityMetersPerSecond,
			double maxSwirlPowerFractionOfMomentumPower,
			double maxNetTorqueCancellationErrorRatio,
			boolean hoverLiveConservationAccepted,
			boolean cruiseLiveConservationAccepted,
			boolean hoverLiveSwirlConservationAccepted,
			boolean cruiseLiveSwirlConservationAccepted,
			boolean liveConservationEvidenceAccepted,
			boolean liveSwirlConservationEvidenceAccepted,
			boolean runtimePoweredSourceCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String conservationPayloadKind,
			String runtimeInfo,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceCouplingConservationGuardScenario(
			String scenarioName,
			PoweredSourceCouplingConservationGuardSummary summary
	) {
	}

	public record PoweredSourceCouplingConservationGuardExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int mechanicalCouplingReadyScenarioCount,
			int conservationEvidenceAcceptedScenarioCount,
			int runtimePoweredSourceCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int maxConservationRowCount,
			int maxConservationTargetSelfConsistentCount,
			int maxLiveConservationAcceptedCount,
			int maxSwirlConservationRowCount,
			int maxSwirlTargetSelfConsistentCount,
			int maxLiveSwirlConservationAcceptedCount,
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio,
			double maxTargetTangentialWakeVelocityMetersPerSecond,
			double maxSwirlPowerFractionOfMomentumPower,
			double maxNetTorqueCancellationErrorRatio
	) {
	}

	public record PoweredSourceCouplingConservationGuardAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceCouplingConservationGuardScenario> scenarios,
			PoweredSourceCouplingConservationGuardExtrema extrema
	) {
		public PoweredSourceCouplingConservationGuardAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceCouplingConservationGuardAudit audit() {
		return audit(
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(),
				Aerodynamics4McL2PoweredSourceConservationContract.audit(),
				Aerodynamics4McL2PoweredSourceSwirlConservationContract.audit()
		);
	}

	public static PoweredSourceCouplingConservationGuardAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(loader),
				Aerodynamics4McL2PoweredSourceConservationContract.audit(),
				Aerodynamics4McL2PoweredSourceSwirlConservationContract.audit()
		);
	}

	public static PoweredSourceCouplingConservationGuardAudit audit(
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit readinessAudit,
			Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractAudit conservationAudit
	) {
		return audit(
				readinessAudit,
				conservationAudit,
				Aerodynamics4McL2PoweredSourceSwirlConservationContract.audit()
		);
	}

	public static PoweredSourceCouplingConservationGuardAudit audit(
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit readinessAudit,
			Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractAudit conservationAudit,
			Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractAudit
					swirlAudit
	) {
		if (readinessAudit == null || conservationAudit == null || swirlAudit == null) {
			throw new IllegalArgumentException("readiness, conservation, and swirl audits are required.");
		}
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessScenario currentReadiness =
				readinessScenario(readinessAudit, "current_handoff_and_policy_blocked");
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessScenario readyReadiness =
				readinessScenario(readinessAudit, "handoffs_policy_and_api_surface_ready");
		List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> currentRows =
				conservationAudit.rows();
		List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> acceptedRows =
				acceptedRows(currentRows);
		List<Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow>
				currentSwirlRows = swirlAudit.rows();
		List<Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow>
				acceptedSwirlRows = acceptedSwirlRows(currentSwirlRows);
		List<PoweredSourceCouplingConservationGuardScenario> scenarios = List.of(
				new PoweredSourceCouplingConservationGuardScenario(
						"current_coupling_and_conservation_blocked",
						guard(currentReadiness.scenarioName(), currentReadiness.summary(), currentRows,
								currentSwirlRows)),
				new PoweredSourceCouplingConservationGuardScenario(
						"conservation_ready_coupling_blocked",
						guard(currentReadiness.scenarioName(), currentReadiness.summary(), acceptedRows,
								acceptedSwirlRows)),
				new PoweredSourceCouplingConservationGuardScenario(
						"coupling_ready_conservation_blocked",
						guard(readyReadiness.scenarioName(), readyReadiness.summary(), currentRows,
								currentSwirlRows)),
				new PoweredSourceCouplingConservationGuardScenario(
						"coupling_ready_force_moment_wake_ready_swirl_blocked",
						guard(readyReadiness.scenarioName(), readyReadiness.summary(), acceptedRows,
								currentSwirlRows)),
				new PoweredSourceCouplingConservationGuardScenario(
						"coupling_and_conservation_ready",
						guard(readyReadiness.scenarioName(), readyReadiness.summary(), acceptedRows,
								acceptedSwirlRows))
		);
		return new PoweredSourceCouplingConservationGuardAudit(
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

	public static PoweredSourceCouplingConservationGuardSummary guard(
			String couplingReadinessScenarioName,
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary readiness,
			List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> conservationRows
	) {
		return guard(
				couplingReadinessScenarioName,
				readiness,
				conservationRows,
				Aerodynamics4McL2PoweredSourceSwirlConservationContract.audit().rows()
		);
	}

	public static PoweredSourceCouplingConservationGuardSummary guard(
			String couplingReadinessScenarioName,
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary readiness,
			List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow>
					conservationRows,
			List<Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow>
					swirlRows
	) {
		if (couplingReadinessScenarioName == null || couplingReadinessScenarioName.isBlank()) {
			throw new IllegalArgumentException("couplingReadinessScenarioName must not be blank.");
		}
		if (readiness == null || conservationRows == null || swirlRows == null) {
			throw new IllegalArgumentException("readiness, conservation rows, and swirl rows are required.");
		}
		int targetSelfConsistent = 0;
		int liveAccepted = 0;
		int forceDelta = 0;
		int momentDelta = 0;
		int wakeResidual = 0;
		double maxMomentumRatio = 0.0;
		double maxPowerRatio = 0.0;
		boolean hoverAccepted = false;
		boolean cruiseAccepted = false;
		for (Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow row
				: conservationRows) {
			if (row == null) {
				throw new IllegalArgumentException("conservation rows must not contain null entries.");
			}
			if (row.targetModelSelfConsistent()) {
				targetSelfConsistent++;
			}
			if (row.liveConservationAccepted()) {
				liveAccepted++;
				if ("hover".equals(row.spinState())) {
					hoverAccepted = true;
				}
				if ("cruise".equals(row.spinState())) {
					cruiseAccepted = true;
				}
			}
			if (row.sourceForceDeltaRequired()) {
				forceDelta++;
			}
			if (row.sourceMomentDeltaRequired()) {
				momentDelta++;
			}
			if (row.wakeMomentumResidualRequired() && row.wakeKineticPowerResidualRequired()) {
				wakeResidual++;
			}
			maxMomentumRatio = Math.max(maxMomentumRatio, row.maxMomentumClosureErrorRatio());
			maxPowerRatio = Math.max(maxPowerRatio, row.maxKineticPowerClosureErrorRatio());
		}
		int swirlTargetSelfConsistent = 0;
		int liveSwirlAccepted = 0;
		int wakeTangentialVelocity = 0;
		int wakeAngularMomentumResidual = 0;
		double maxTangentialVelocity = 0.0;
		double maxSwirlPowerFraction = 0.0;
		double maxTorqueCancellation = 0.0;
		boolean hoverSwirlAccepted = false;
		boolean cruiseSwirlAccepted = false;
		for (Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow row
				: swirlRows) {
			if (row == null) {
				throw new IllegalArgumentException("swirl rows must not contain null entries.");
			}
			if (row.angularMomentumTargetSelfConsistent()) {
				swirlTargetSelfConsistent++;
			}
			if (row.liveSwirlConservationAccepted()) {
				liveSwirlAccepted++;
				if ("hover".equals(row.spinState())) {
					hoverSwirlAccepted = true;
				}
				if ("cruise".equals(row.spinState())) {
					cruiseSwirlAccepted = true;
				}
			}
			if (row.wakeTangentialVelocityRequired()) {
				wakeTangentialVelocity++;
			}
			if (row.wakeAngularMomentumResidualRequired()) {
				wakeAngularMomentumResidual++;
			}
			maxTangentialVelocity = Math.max(maxTangentialVelocity,
					row.maxTargetTangentialWakeVelocityMetersPerSecond());
			maxSwirlPowerFraction = Math.max(maxSwirlPowerFraction,
					row.maxSwirlPowerFractionOfMomentumPower());
			maxTorqueCancellation = Math.max(maxTorqueCancellation,
					row.maxNetTorqueCancellationErrorRatio());
		}
		boolean expectedRowsPresent = conservationRows.size()
				== Aerodynamics4McL2PoweredSourceConservationContract.SPIN_STATE_SAMPLE_COUNT;
		boolean linearEvidenceAccepted = expectedRowsPresent
				&& targetSelfConsistent == conservationRows.size()
				&& liveAccepted == conservationRows.size();
		boolean expectedSwirlRowsPresent = swirlRows.size()
				== Aerodynamics4McL2PoweredSourceSwirlConservationContract.SPIN_STATE_SAMPLE_COUNT;
		boolean liveSwirlEvidenceAccepted = expectedSwirlRowsPresent
				&& swirlTargetSelfConsistent == swirlRows.size()
				&& liveSwirlAccepted == swirlRows.size();
		boolean liveEvidenceAccepted = linearEvidenceAccepted && liveSwirlEvidenceAccepted;
		boolean targetModelsSelfConsistent = expectedRowsPresent
				&& expectedSwirlRowsPresent
				&& targetSelfConsistent == conservationRows.size()
				&& swirlTargetSelfConsistent == swirlRows.size();
		boolean allowed = readiness.runtimePoweredSourceCouplingAllowed() && liveEvidenceAccepted;
		return new PoweredSourceCouplingConservationGuardSummary(
				couplingReadinessScenarioName,
				readiness.runtimePoweredSourceCouplingAllowed(),
				readiness.poweredSourceApiSurfaceReady(),
				readiness.acceptanceBudgetGateReady(),
				readiness.allPoliciesRuntimeAllowed(),
				readiness.hoverAndCruiseCouplingAllowed(),
				conservationRows.size(),
				targetSelfConsistent,
				liveAccepted,
				forceDelta,
				momentDelta,
				wakeResidual,
				swirlRows.size(),
				swirlTargetSelfConsistent,
				liveSwirlAccepted,
				wakeTangentialVelocity,
				wakeAngularMomentumResidual,
				maxMomentumRatio,
				maxPowerRatio,
				maxTangentialVelocity,
				maxSwirlPowerFraction,
				maxTorqueCancellation,
				hoverAccepted,
				cruiseAccepted,
				hoverSwirlAccepted,
				cruiseSwirlAccepted,
				liveEvidenceAccepted,
				liveSwirlEvidenceAccepted,
				allowed,
				false,
				"hover-and-cruise-powered-source-force-moment-wake-and-swirl-conservation-evidence",
				allowed ? "synthetic-reviewed-conservation-ready" : "audit-only-conservation-guard",
				nextRequiredAction(readiness.runtimePoweredSourceCouplingAllowed(),
						targetModelsSelfConsistent, linearEvidenceAccepted, liveSwirlEvidenceAccepted),
				allowed ? "READY" : "BLOCKED",
				allowed ? "powered-source-coupling-conservation-clear"
						: "powered-source-coupling-conservation-blocked"
		);
	}

	private static String nextRequiredAction(
			boolean couplingReadinessAllowed,
			boolean targetSelfConsistent,
			boolean linearEvidenceAccepted,
			boolean liveSwirlEvidenceAccepted
	) {
		if (!couplingReadinessAllowed) {
			return "complete-powered-source-coupling-readiness-gate";
		}
		if (!targetSelfConsistent) {
			return "repair-powered-source-conservation-target-model";
		}
		if (!linearEvidenceAccepted && !liveSwirlEvidenceAccepted) {
			return "capture-live-a4mc-powered-source-force-moment-wake-and-swirl-residuals";
		}
		if (!linearEvidenceAccepted) {
			return "capture-live-a4mc-powered-source-force-moment-and-wake-residuals";
		}
		if (!liveSwirlEvidenceAccepted) {
			return "capture-live-a4mc-powered-source-swirl-angular-momentum-residuals";
		}
		return "runtime-powered-source-coupling-ready-after-conservation-review";
	}

	private static List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> acceptedRows(
			List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> rows
	) {
		return rows.stream()
				.map(Aerodynamics4McL2PoweredSourceCouplingConservationGuard::acceptedRow)
				.toList();
	}

	private static Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow acceptedRow(
			Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow row
	) {
		return new Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow(
				row.spinState(),
				row.footprintCount(),
				row.sourceTermCount(),
				row.maxTotalThrustNewtons(),
				row.maxMassFlowKilogramsPerSecond(),
				row.maxMomentumPowerWatts(),
				row.maxMomentumClosureErrorNewtons(),
				row.maxMomentumClosureErrorRatio(),
				row.maxKineticPowerClosureErrorWatts(),
				row.maxKineticPowerClosureErrorRatio(),
				row.maxWakeSpeedMetersPerSecond(),
				row.maxWakeTransitTimeSeconds(),
				row.recommendedWakeSampleCount(),
				row.targetModelSelfConsistent(),
				row.sourceForceDeltaRequired(),
				row.sourceMomentDeltaRequired(),
				row.wakeMomentumResidualRequired(),
				row.wakeKineticPowerResidualRequired(),
				true,
				true,
				true,
				false,
				false,
				"runtime-powered-source-coupling-ready-after-conservation-review",
				"ACCEPTED",
				"powered-source-conservation-contract-accepted"
		);
	}

	private static List<Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow>
	acceptedSwirlRows(
			List<Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow>
					rows
	) {
		return rows.stream()
				.map(Aerodynamics4McL2PoweredSourceCouplingConservationGuard::acceptedSwirlRow)
				.toList();
	}

	private static Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow
	acceptedSwirlRow(
			Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow row
	) {
		return new Aerodynamics4McL2PoweredSourceSwirlConservationContract
				.PoweredSourceSwirlConservationContractRow(
				row.spinState(),
				row.sourceMapCount(),
				row.rotorSwirlTargetCount(),
				row.recommendedSwirlProbeCount(),
				row.maxPerRotorReactionTorqueNewtonMeters(),
				row.maxSignedWakeAngularMomentumFluxNewtonMeters(),
				row.maxSwirlRadiusMeters(),
				row.maxTargetTangentialWakeVelocityMetersPerSecond(),
				row.maxSwirlKineticPowerWatts(),
				row.maxSwirlPowerFractionOfMomentumPower(),
				row.maxNetSignedReactionTorqueNewtonMeters(),
				row.maxNetTorqueCancellationErrorRatio(),
				row.angularMomentumTargetSelfConsistent(),
				row.sourceMomentDeltaRequired(),
				row.wakeTangentialVelocityRequired(),
				row.wakeAngularMomentumResidualRequired(),
				true,
				true,
				true,
				false,
				false,
				row.targetPayloadKind(),
				"runtime-powered-source-coupling-ready-after-swirl-conservation-review",
				"ACCEPTED",
				"powered-source-swirl-conservation-contract-accepted"
		);
	}

	private static Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessScenario readinessScenario(
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PoweredSourceCouplingConservationGuardExtrema extrema(
			List<PoweredSourceCouplingConservationGuardScenario> scenarios
	) {
		int ready = 0;
		int mechanicalReady = 0;
		int evidenceAccepted = 0;
		int runtimeAllowed = 0;
		int gameplayAuto = 0;
		int maxRows = 0;
		int maxTarget = 0;
		int maxAccepted = 0;
		int maxSwirlRows = 0;
		int maxSwirlTarget = 0;
		int maxSwirlAccepted = 0;
		double maxMomentum = 0.0;
		double maxPower = 0.0;
		double maxTangentialVelocity = 0.0;
		double maxSwirlPowerFraction = 0.0;
		double maxTorqueCancellation = 0.0;
		for (PoweredSourceCouplingConservationGuardScenario scenario : scenarios) {
			PoweredSourceCouplingConservationGuardSummary summary = scenario.summary();
			if (summary.status().equals("READY")) {
				ready++;
			}
			if (summary.runtimeCouplingReadinessAllowed()) {
				mechanicalReady++;
			}
			if (summary.liveConservationEvidenceAccepted()) {
				evidenceAccepted++;
			}
			if (summary.runtimePoweredSourceCouplingAllowed()) {
				runtimeAllowed++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplayAuto++;
			}
			maxRows = Math.max(maxRows, summary.conservationRowCount());
			maxTarget = Math.max(maxTarget, summary.conservationTargetSelfConsistentCount());
			maxAccepted = Math.max(maxAccepted, summary.liveConservationAcceptedCount());
			maxSwirlRows = Math.max(maxSwirlRows, summary.swirlConservationRowCount());
			maxSwirlTarget = Math.max(maxSwirlTarget, summary.swirlTargetSelfConsistentCount());
			maxSwirlAccepted = Math.max(maxSwirlAccepted, summary.liveSwirlConservationAcceptedCount());
			maxMomentum = Math.max(maxMomentum, summary.maxMomentumClosureErrorRatio());
			maxPower = Math.max(maxPower, summary.maxKineticPowerClosureErrorRatio());
			maxTangentialVelocity = Math.max(maxTangentialVelocity,
					summary.maxTargetTangentialWakeVelocityMetersPerSecond());
			maxSwirlPowerFraction = Math.max(maxSwirlPowerFraction,
					summary.maxSwirlPowerFractionOfMomentumPower());
			maxTorqueCancellation = Math.max(maxTorqueCancellation,
					summary.maxNetTorqueCancellationErrorRatio());
		}
		return new PoweredSourceCouplingConservationGuardExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				mechanicalReady,
				evidenceAccepted,
				runtimeAllowed,
				gameplayAuto,
				maxRows,
				maxTarget,
				maxAccepted,
				maxSwirlRows,
				maxSwirlTarget,
				maxSwirlAccepted,
				maxMomentum,
				maxPower,
				maxTangentialVelocity,
				maxSwirlPowerFraction,
				maxTorqueCancellation
		);
	}
}
