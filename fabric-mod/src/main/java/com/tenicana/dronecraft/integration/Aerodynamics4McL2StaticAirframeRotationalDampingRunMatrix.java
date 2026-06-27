package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Rotational-Damping-Run-Matrix-Packet";
	public static final String CAVEAT =
			"Rotational damping run matrix records compact execution status for pitch, yaw, and roll body-rate targets; current A4MC L2 bridge support is plan-only, so rotational runs are skipped until a velocity-field or moving-boundary request exists.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SWEEP_CASE_SAMPLE_COUNT = 24;
	public static final int RUN_METRIC_COUNT = 40;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SWEEP_CASE_SAMPLE_COUNT * RUN_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix() {
	}

	public record RotationalDampingRunSummary(
			String presetName,
			String sweepName,
			String fitRole,
			String bodyAxisName,
			double bodyRateXRadPerSecond,
			double bodyRateYRadPerSecond,
			double bodyRateZRadPerSecond,
			double bodyRateMagnitudeRadPerSecond,
			double rateSign,
			double axisTangentialSpeedScale,
			double referenceRadiusMeters,
			double maxTangentialSpeedMetersPerSecond,
			double currentAngularDragCoefficient,
			double currentDampingTorqueMagnitudeNewtonMeters,
			int steps,
			int gridCellCount,
			double cellSizeMeters,
			double solidFraction,
			double referenceLengthMeters,
			boolean baselineStaticRequestReady,
			boolean rotationalFlowApiRequired,
			boolean rotationalFlowApiAvailable,
			boolean movingBoundaryOrVelocityFieldRequired,
			boolean expectedOpposingMomentSign,
			boolean requestForceMoment,
			boolean requestFlowAtlas,
			boolean rotationalRequestBuildable,
			boolean invoked,
			boolean succeeded,
			boolean available,
			boolean hasForceMoment,
			double momentXNewtonMeters,
			double momentYNewtonMeters,
			double momentZNewtonMeters,
			double momentMagnitudeNewtonMeters,
			boolean angularDampingMomentSignValid,
			boolean angularDragFitReady,
			String status,
			String message,
			String runtimeInfo
	) {
	}

	public record RotationalDampingRunExtrema(
			int runSummaryCount,
			int baselineStaticRequestReadyCount,
			int rotationalRequestBuildableCount,
			int invokedCount,
			int availableCount,
			int forceMomentCount,
			int angularDragFitReadyCount,
			int rotationalFlowApiAvailableCount,
			int rawFlowAtlasDisabledCount,
			int skippedForMissingRotationalApiCount,
			double maxTangentialSpeedMetersPerSecond,
			double maxCurrentDampingTorqueMagnitudeNewtonMeters
	) {
	}

	public record RotationalDampingRunMatrixAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int sweepCaseSampleCount,
			int runMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<RotationalDampingRunSummary> runs,
			RotationalDampingRunExtrema extrema
	) {
		public RotationalDampingRunMatrixAudit {
			runs = List.copyOf(runs);
		}
	}

	public static RotationalDampingRunMatrixAudit audit() {
		return audit(Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.audit());
	}

	public static RotationalDampingRunMatrixAudit audit(
			Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepAudit sweepPlan
	) {
		if (sweepPlan == null) {
			throw new IllegalArgumentException("sweepPlan must not be null.");
		}
		List<RotationalDampingRunSummary> runs = sweepPlan.sweepCases().stream()
				.map(Aerodynamics4McL2StaticAirframeRotationalDampingRunMatrix::summary)
				.toList();
		return new RotationalDampingRunMatrixAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SWEEP_CASE_SAMPLE_COUNT,
				RUN_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				runs,
				extrema(runs)
		);
	}

	public static RotationalDampingRunSummary summary(
			Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase sweepCase
	) {
		if (sweepCase == null) {
			throw new IllegalArgumentException("sweepCase must not be null.");
		}
		boolean requestFlowAtlas = false;
		boolean requestForceMoment = sweepCase.baselineStaticRequestReady();
		boolean rotationalRequestBuildable = requestForceMoment
				&& sweepCase.rotationalFlowApiAvailable()
				&& sweepCase.movingBoundaryOrVelocityFieldRequired();
		boolean invoked = false;
		boolean succeeded = false;
		boolean available = false;
		boolean hasForceMoment = false;
		boolean angularDampingMomentSignValid = false;
		boolean angularDragFitReady = false;
		String status = rotationalRequestBuildable ? "PENDING" : "SKIPPED";
		String message = messageFor(sweepCase, rotationalRequestBuildable);
		return new RotationalDampingRunSummary(
				sweepCase.presetName(),
				sweepCase.sweepName(),
				sweepCase.fitRole(),
				sweepCase.bodyAxisName(),
				sweepCase.bodyRateXRadPerSecond(),
				sweepCase.bodyRateYRadPerSecond(),
				sweepCase.bodyRateZRadPerSecond(),
				sweepCase.bodyRateMagnitudeRadPerSecond(),
				sweepCase.rateSign(),
				sweepCase.axisTangentialSpeedScale(),
				sweepCase.referenceRadiusMeters(),
				sweepCase.maxTangentialSpeedMetersPerSecond(),
				sweepCase.currentAngularDragCoefficient(),
				sweepCase.currentDampingTorqueMagnitudeNewtonMeters(),
				sweepCase.steps(),
				sweepCase.gridCellCount(),
				sweepCase.cellSizeMeters(),
				sweepCase.solidFraction(),
				sweepCase.referenceLengthMeters(),
				sweepCase.baselineStaticRequestReady(),
				sweepCase.rotationalFlowApiRequired(),
				sweepCase.rotationalFlowApiAvailable(),
				sweepCase.movingBoundaryOrVelocityFieldRequired(),
				sweepCase.expectedOpposingMomentSign(),
				requestForceMoment,
				requestFlowAtlas,
				rotationalRequestBuildable,
				invoked,
				succeeded,
				available,
				hasForceMoment,
				0.0,
				0.0,
				0.0,
				0.0,
				angularDampingMomentSignValid,
				angularDragFitReady,
				status,
				message,
				sweepCase.sourceRuntimeInfo()
		);
	}

	private static String messageFor(
			Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase sweepCase,
			boolean rotationalRequestBuildable
	) {
		if (!sweepCase.baselineStaticRequestReady()) {
			return "baseline-static-request-unavailable";
		}
		if (!sweepCase.rotationalFlowApiAvailable()) {
			return "rotational-flow-api-unavailable";
		}
		if (!sweepCase.movingBoundaryOrVelocityFieldRequired()) {
			return "rotational-flow-requirement-missing";
		}
		if (rotationalRequestBuildable) {
			return "rotational-run-not-invoked";
		}
		return "rotational-run-unavailable";
	}

	private static RotationalDampingRunExtrema extrema(List<RotationalDampingRunSummary> runs) {
		int baselineReady = 0;
		int buildable = 0;
		int invoked = 0;
		int available = 0;
		int forceMoment = 0;
		int fitReady = 0;
		int rotationalApi = 0;
		int rawDisabled = 0;
		int skipped = 0;
		double maxTangential = 0.0;
		double maxDampingTorque = 0.0;
		for (RotationalDampingRunSummary run : runs) {
			if (run.baselineStaticRequestReady()) {
				baselineReady++;
			}
			if (run.rotationalRequestBuildable()) {
				buildable++;
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
			if (run.angularDragFitReady()) {
				fitReady++;
			}
			if (run.rotationalFlowApiAvailable()) {
				rotationalApi++;
			}
			if (!run.requestFlowAtlas()) {
				rawDisabled++;
			}
			if ("rotational-flow-api-unavailable".equals(run.message())) {
				skipped++;
			}
			maxTangential = Math.max(maxTangential, run.maxTangentialSpeedMetersPerSecond());
			maxDampingTorque = Math.max(maxDampingTorque, run.currentDampingTorqueMagnitudeNewtonMeters());
		}
		return new RotationalDampingRunExtrema(
				runs.size(),
				baselineReady,
				buildable,
				invoked,
				available,
				forceMoment,
				fitReady,
				rotationalApi,
				rawDisabled,
				skipped,
				maxTangential,
				maxDampingTorque
		);
	}
}
