package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceCouplingConservationGuard {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Coupling-Conservation-Guard-Packet";
	public static final String CAVEAT =
			"Final powered-source coupling guard keeps runtime coupling closed until the existing coupling readiness gate is open and live hover plus cruise conservation evidence is accepted; it never enables gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 24;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
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
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio,
			boolean hoverLiveConservationAccepted,
			boolean cruiseLiveConservationAccepted,
			boolean liveConservationEvidenceAccepted,
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
			double maxMomentumClosureErrorRatio,
			double maxKineticPowerClosureErrorRatio
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
				Aerodynamics4McL2PoweredSourceConservationContract.audit()
		);
	}

	public static PoweredSourceCouplingConservationGuardAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(loader),
				Aerodynamics4McL2PoweredSourceConservationContract.audit()
		);
	}

	public static PoweredSourceCouplingConservationGuardAudit audit(
			Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit readinessAudit,
			Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractAudit conservationAudit
	) {
		if (readinessAudit == null || conservationAudit == null) {
			throw new IllegalArgumentException("readiness and conservation audits are required.");
		}
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessScenario currentReadiness =
				readinessScenario(readinessAudit, "current_handoff_and_policy_blocked");
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessScenario readyReadiness =
				readinessScenario(readinessAudit, "handoffs_policy_and_api_surface_ready");
		List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> currentRows =
				conservationAudit.rows();
		List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> acceptedRows =
				acceptedRows(currentRows);
		List<PoweredSourceCouplingConservationGuardScenario> scenarios = List.of(
				new PoweredSourceCouplingConservationGuardScenario(
						"current_coupling_and_conservation_blocked",
						guard(currentReadiness.scenarioName(), currentReadiness.summary(), currentRows)),
				new PoweredSourceCouplingConservationGuardScenario(
						"conservation_ready_coupling_blocked",
						guard(currentReadiness.scenarioName(), currentReadiness.summary(), acceptedRows)),
				new PoweredSourceCouplingConservationGuardScenario(
						"coupling_ready_conservation_blocked",
						guard(readyReadiness.scenarioName(), readyReadiness.summary(), currentRows)),
				new PoweredSourceCouplingConservationGuardScenario(
						"coupling_and_conservation_ready",
						guard(readyReadiness.scenarioName(), readyReadiness.summary(), acceptedRows))
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
		if (couplingReadinessScenarioName == null || couplingReadinessScenarioName.isBlank()) {
			throw new IllegalArgumentException("couplingReadinessScenarioName must not be blank.");
		}
		if (readiness == null || conservationRows == null) {
			throw new IllegalArgumentException("readiness and conservation rows are required.");
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
		boolean expectedRowsPresent = conservationRows.size()
				== Aerodynamics4McL2PoweredSourceConservationContract.SPIN_STATE_SAMPLE_COUNT;
		boolean liveEvidenceAccepted = expectedRowsPresent
				&& targetSelfConsistent == conservationRows.size()
				&& liveAccepted == conservationRows.size();
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
				maxMomentumRatio,
				maxPowerRatio,
				hoverAccepted,
				cruiseAccepted,
				liveEvidenceAccepted,
				allowed,
				false,
				"hover-and-cruise-powered-source-conservation-evidence",
				allowed ? "synthetic-reviewed-conservation-ready" : "audit-only-conservation-guard",
				nextRequiredAction(readiness.runtimePoweredSourceCouplingAllowed(),
						targetSelfConsistent == conservationRows.size(), liveEvidenceAccepted),
				allowed ? "READY" : "BLOCKED",
				allowed ? "powered-source-coupling-conservation-clear"
						: "powered-source-coupling-conservation-blocked"
		);
	}

	private static String nextRequiredAction(
			boolean couplingReadinessAllowed,
			boolean targetSelfConsistent,
			boolean liveEvidenceAccepted
	) {
		if (!couplingReadinessAllowed) {
			return "complete-powered-source-coupling-readiness-gate";
		}
		if (!targetSelfConsistent) {
			return "repair-powered-source-conservation-target-model";
		}
		if (!liveEvidenceAccepted) {
			return "capture-live-a4mc-powered-source-force-moment-and-wake-residuals";
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
		double maxMomentum = 0.0;
		double maxPower = 0.0;
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
			maxMomentum = Math.max(maxMomentum, summary.maxMomentumClosureErrorRatio());
			maxPower = Math.max(maxPower, summary.maxKineticPowerClosureErrorRatio());
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
				maxMomentum,
				maxPower
		);
	}
}
