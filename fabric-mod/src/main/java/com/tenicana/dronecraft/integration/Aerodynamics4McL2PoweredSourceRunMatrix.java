package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceRunMatrix {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Run-Matrix-Packet";
	public static final String CAVEAT =
			"Powered-source run matrix records compact execution status only; current rows stay skipped until A4MC exposes powered source injection and the request readiness gate opens.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int RUN_SAMPLE_COUNT = 8;
	public static final int RUN_METRIC_COUNT = 41;
	public static final int SUMMARY_METRIC_ROW_COUNT = 15;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ RUN_SAMPLE_COUNT * RUN_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceRunMatrix() {
	}

	public record PoweredSourceRunSummary(
			String presetName,
			String spinState,
			String sourceMapId,
			String validationPacketId,
			String acceptanceGatePacketId,
			int rotorCount,
			int sourceTermCount,
			int gridCellCount,
			double cellSizeMeters,
			int steps,
			double inletSpeedMetersPerSecond,
			double totalThrustNewtons,
			double meanPressureJumpPascals,
			double maxPressureJumpPascals,
			double targetForceMagnitudeNewtons,
			double targetMomentMagnitudeNewtonMeters,
			double targetCenterOfForceOffsetMeters,
			boolean baselineForceMomentRequest,
			boolean poweredSourceApiRequired,
			boolean poweredSourceApiAvailable,
			boolean requestBuildAllowed,
			boolean readinessGateOpen,
			boolean requestExecutionAllowed,
			boolean requestForceMoment,
			boolean requestFlowAtlas,
			boolean invoked,
			boolean succeeded,
			boolean available,
			boolean hasForceMoment,
			double forceDeltaXNewtons,
			double forceDeltaYNewtons,
			double forceDeltaZNewtons,
			double forceDeltaMagnitudeNewtons,
			double momentDeltaXNewtonMeters,
			double momentDeltaYNewtonMeters,
			double momentDeltaZNewtonMeters,
			double momentDeltaMagnitudeNewtonMeters,
			double centerOfForceOffsetMeters,
			String status,
			String message,
			String runtimeInfo
	) {
	}

	public record PoweredSourceRunExtrema(
			int runSummaryCount,
			int hoverRunCount,
			int cruiseRunCount,
			int readinessGateOpenCount,
			int requestExecutionAllowedCount,
			int requestBuildAllowedCount,
			int poweredSourceApiAvailableCount,
			int invokedCount,
			int availableCount,
			int forceMomentCount,
			int skippedForReadinessCount,
			int pendingExecutorCount,
			int maxGridCellCount,
			double maxTargetForceMagnitudeNewtons,
			double maxMeanPressureJumpPascals
	) {
	}

	public record PoweredSourceRunMatrixAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int runSampleCount,
			int runMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceRunSummary> runs,
			PoweredSourceRunExtrema extrema
	) {
		public PoweredSourceRunMatrixAudit {
			runs = List.copyOf(runs);
		}
	}

	public static PoweredSourceRunMatrixAudit audit() {
		List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests =
				Aerodynamics4McL2PoweredSourceRequestPlan.audit().requests();
		return audit(false, false, false, requests);
	}

	public static PoweredSourceRunMatrixAudit audit(
			boolean poweredSourceApiAvailable,
			boolean poweredHoverAcceptanceGateOpen,
			boolean poweredCruiseAcceptanceGateOpen,
			List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests
	) {
		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary readiness =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(
						poweredSourceApiAvailable,
						poweredHoverAcceptanceGateOpen,
						poweredCruiseAcceptanceGateOpen,
						requests
				);
		return audit(readiness, requests);
	}

	public static PoweredSourceRunMatrixAudit audit(
			Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary apiSurface,
			boolean poweredHoverAcceptanceGateOpen,
			boolean poweredCruiseAcceptanceGateOpen,
			List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests
	) {
		Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary readiness =
				Aerodynamics4McL2PoweredSourceRequestReadinessGate.gate(
						apiSurface,
						poweredHoverAcceptanceGateOpen,
						poweredCruiseAcceptanceGateOpen,
						requests
				);
		return audit(readiness, requests);
	}

	private static PoweredSourceRunMatrixAudit audit(
			Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary readiness,
			List<Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest> requests
	) {
		List<PoweredSourceRunSummary> runs = requests.stream()
				.map(request -> summary(request, readiness))
				.toList();
		return new PoweredSourceRunMatrixAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				RUN_SAMPLE_COUNT,
				RUN_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				runs,
				extrema(runs)
		);
	}

	public static PoweredSourceRunSummary summary(
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request,
			Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary readiness
	) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null.");
		}
		if (readiness == null) {
			throw new IllegalArgumentException("readiness must not be null.");
		}
		boolean readinessOpen = readiness.requestExecutionAllowed();
		boolean requestExecutionAllowed = readinessOpen
				&& request.poweredSourceApiAvailable()
				&& request.requestBuildAllowed()
				&& request.poweredSourceApiRequired()
				&& request.baselineForceMomentRequest();
		String status = requestExecutionAllowed ? "PENDING" : "SKIPPED";
		String message = messageFor(request, readiness, requestExecutionAllowed);
		return new PoweredSourceRunSummary(
				request.presetName(),
				request.spinState(),
				request.sourceMapId(),
				request.validationPacketId(),
				request.acceptanceGatePacketId(),
				request.rotorCount(),
				request.sourceTermCount(),
				request.gridCellCount(),
				request.cellSizeMeters(),
				request.steps(),
				request.inletSpeedMetersPerSecond(),
				request.totalThrustNewtons(),
				request.meanPressureJumpPascals(),
				request.maxPressureJumpPascals(),
				request.netForceMagnitudeNewtons(),
				request.netMomentMagnitudeNewtonMeters(),
				request.centerOfThrustOffsetMeters(),
				request.baselineForceMomentRequest(),
				request.poweredSourceApiRequired(),
				request.poweredSourceApiAvailable(),
				request.requestBuildAllowed(),
				readinessOpen,
				requestExecutionAllowed,
				request.baselineForceMomentRequest(),
				false,
				false,
				false,
				false,
				false,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				status,
				message,
				request.runtimeInfo()
		);
	}

	private static String messageFor(
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request,
			Aerodynamics4McL2PoweredSourceRequestReadinessGate.PoweredSourceRequestReadinessSummary readiness,
			boolean requestExecutionAllowed
	) {
		if (requestExecutionAllowed) {
			return "powered-source-executor-not-invoked";
		}
		if (!readiness.requestExecutionAllowed()) {
			return "powered-source-readiness-gate-blocked";
		}
		if (!request.poweredSourceApiAvailable()) {
			return "powered-source-api-unavailable";
		}
		if (!request.requestBuildAllowed()) {
			return "powered-source-request-build-blocked";
		}
		if (!request.poweredSourceApiRequired()) {
			return "powered-source-api-not-required";
		}
		if (!request.baselineForceMomentRequest()) {
			return "baseline-force-moment-request-disabled";
		}
		return "powered-source-run-unavailable";
	}

	private static PoweredSourceRunExtrema extrema(List<PoweredSourceRunSummary> runs) {
		int hover = 0;
		int cruise = 0;
		int readinessOpen = 0;
		int executionAllowed = 0;
		int buildAllowed = 0;
		int apiAvailable = 0;
		int invoked = 0;
		int available = 0;
		int forceMoment = 0;
		int skippedForReadiness = 0;
		int pendingExecutor = 0;
		int maxGridCells = 0;
		double maxForce = 0.0;
		double maxMeanPressureJump = 0.0;
		for (PoweredSourceRunSummary run : runs) {
			if ("hover".equals(run.spinState())) {
				hover++;
			}
			if ("cruise".equals(run.spinState())) {
				cruise++;
			}
			if (run.readinessGateOpen()) {
				readinessOpen++;
			}
			if (run.requestExecutionAllowed()) {
				executionAllowed++;
			}
			if (run.requestBuildAllowed()) {
				buildAllowed++;
			}
			if (run.poweredSourceApiAvailable()) {
				apiAvailable++;
			}
			if (run.invoked()) {
				invoked++;
			}
			if (run.available()) {
				available++;
			}
			if (run.hasForceMoment()) {
				forceMoment++;
			}
			if ("powered-source-readiness-gate-blocked".equals(run.message())) {
				skippedForReadiness++;
			}
			if ("powered-source-executor-not-invoked".equals(run.message())) {
				pendingExecutor++;
			}
			maxGridCells = Math.max(maxGridCells, run.gridCellCount());
			maxForce = Math.max(maxForce, run.targetForceMagnitudeNewtons());
			maxMeanPressureJump = Math.max(maxMeanPressureJump, run.meanPressureJumpPascals());
		}
		return new PoweredSourceRunExtrema(
				runs.size(),
				hover,
				cruise,
				readinessOpen,
				executionAllowed,
				buildAllowed,
				apiAvailable,
				invoked,
				available,
				forceMoment,
				skippedForReadiness,
				pendingExecutor,
				maxGridCells,
				maxForce,
				maxMeanPressureJump
		);
	}
}
