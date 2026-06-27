package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Multi-Axis-Run-Matrix-Packet";
	public static final String CAVEAT =
			"Multi-axis run matrix records compact force/moment summaries for each static-airframe sweep target; raw flow-atlas output remains disabled and live A4MC results are required before gameplay retuning.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SWEEP_CASE_SAMPLE_COUNT = 24;
	public static final int RUN_METRIC_COUNT = 37;
	public static final int SUMMARY_METRIC_ROW_COUNT = 11;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SWEEP_CASE_SAMPLE_COUNT * RUN_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix() {
	}

	public record StaticAirframeMultiAxisRunSummary(
			String presetName,
			String sweepName,
			String fitRole,
			int gridCellCount,
			double cellSizeMeters,
			int steps,
			double inletVxMetersPerSecond,
			double inletVyMetersPerSecond,
			double inletVzMetersPerSecond,
			double inletSpeedMetersPerSecond,
			double sweepAngleDegrees,
			double solidFraction,
			double projectedReferenceAreaSquareMeters,
			double referenceLengthMeters,
			boolean requestForceMoment,
			boolean requestFlowAtlas,
			boolean invoked,
			boolean succeeded,
			boolean available,
			boolean hasForceMoment,
			boolean hasFlowAtlas,
			int atlasValueCount,
			double forceXNewtons,
			double forceYNewtons,
			double forceZNewtons,
			double forceMagnitudeNewtons,
			double momentXNewtonMeters,
			double momentYNewtonMeters,
			double momentZNewtonMeters,
			double momentMagnitudeNewtonMeters,
			double pressureCenterOffsetMeters,
			double pressureCenterOffsetXBodyMeters,
			double pressureCenterOffsetYBodyMeters,
			double pressureCenterOffsetZBodyMeters,
			boolean coefficientFitReady,
			String status,
			String message,
			String runtimeInfo
	) {
	}

	public record StaticAirframeMultiAxisRunExtrema(
			int runSummaryCount,
			int invokedCount,
			int succeededCount,
			int availableCount,
			int forceMomentCount,
			int rawFlowAtlasDisabledCount,
			int maxGridCellCount,
			double maxForceMagnitudeNewtons,
			double maxMomentMagnitudeNewtonMeters,
			double maxPressureCenterOffsetMeters,
			int testRuntimeCount
	) {
	}

	public record StaticAirframeMultiAxisRunMatrixAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int sweepCaseSampleCount,
			int runMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<StaticAirframeMultiAxisRunSummary> runs,
			StaticAirframeMultiAxisRunExtrema extrema
	) {
		public StaticAirframeMultiAxisRunMatrixAudit {
			runs = List.copyOf(runs);
		}
	}

	public static StaticAirframeMultiAxisRunMatrixAudit audit() {
		return audit(Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.audit(), Aerodynamics4McL2Bridge::run);
	}

	public static StaticAirframeMultiAxisRunMatrixAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(
				Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.audit(),
				spec -> Aerodynamics4McL2Bridge.run(loader, spec)
		);
	}

	public static StaticAirframeMultiAxisRunMatrixAudit audit(
			Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.MultiAxisSweepAudit sweepPlan,
			L2Runner runner
	) {
		if (sweepPlan == null || runner == null) {
			throw new IllegalArgumentException("sweep plan and runner are required.");
		}
		List<StaticAirframeMultiAxisRunSummary> runs = sweepPlan.sweepCases().stream()
				.map(sweepCase -> summary(sweepCase, runner))
				.toList();
		return new StaticAirframeMultiAxisRunMatrixAudit(
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

	public static StaticAirframeMultiAxisRunSummary summary(
			Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase sweepCase
	) {
		return summary(sweepCase, Aerodynamics4McL2Bridge::run);
	}

	private static StaticAirframeMultiAxisRunSummary summary(
			Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan.StaticAirframeSweepCase sweepCase,
			L2Runner runner
	) {
		if (sweepCase == null || runner == null) {
			throw new IllegalArgumentException("sweep case and runner are required.");
		}
		DroneConfig config = configForPreset(sweepCase.presetName());
		Vec3 inlet = new Vec3(
				sweepCase.inletVxMetersPerSecond(),
				sweepCase.inletVyMetersPerSecond(),
				sweepCase.inletVzMetersPerSecond()
		);
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inlet, sweepCase.steps());
		Aerodynamics4McL2Bridge.L2RequestSpec request = probe.requestSpec();
		Aerodynamics4McL2Bridge.L2RunResult result = runner.run(request);
		Aerodynamics4McL2Bridge.L2ForceMomentSample forceMoment = result.forceMoment();
		int gridCellCount = probe.nx() * probe.ny() * probe.nz();
		boolean ready = sweepCase.coefficientFitReady()
				&& result.available()
				&& result.hasForceMoment()
				&& !result.hasFlowAtlas()
				&& sweepCase.projectedReferenceAreaSquareMeters() > 1.0e-12
				&& sweepCase.referenceLengthMeters() > 1.0e-12;
		return new StaticAirframeMultiAxisRunSummary(
				sweepCase.presetName(),
				sweepCase.sweepName(),
				sweepCase.fitRole(),
				gridCellCount,
				probe.cellSizeMeters(),
				request.steps(),
				request.inletVx(),
				request.inletVy(),
				request.inletVz(),
				inlet.length(),
				sweepCase.sweepAngleDegrees(),
				probe.solidCellCount() / (double) Math.max(1, gridCellCount),
				sweepCase.projectedReferenceAreaSquareMeters(),
				sweepCase.referenceLengthMeters(),
				request.computeForceMoment(),
				request.outputFlowAtlas(),
				result.invoked(),
				result.succeeded(),
				result.available(),
				result.hasForceMoment(),
				result.hasFlowAtlas(),
				result.atlasValueCount(),
				forceMoment == null ? 0.0 : forceMoment.forceX(),
				forceMoment == null ? 0.0 : forceMoment.forceY(),
				forceMoment == null ? 0.0 : forceMoment.forceZ(),
				forceMoment == null ? 0.0 : forceMoment.forceMagnitudeN(),
				forceMoment == null ? 0.0 : forceMoment.momentX(),
				forceMoment == null ? 0.0 : forceMoment.momentY(),
				forceMoment == null ? 0.0 : forceMoment.momentZ(),
				forceMoment == null ? 0.0 : forceMoment.momentMagnitudeNm(),
				forceMoment == null ? 0.0 : forceMoment.centerOfPressureOffsetMeters(),
				forceMoment == null ? 0.0 : forceMoment.centerOfPressureX() - forceMoment.referenceX(),
				forceMoment == null ? 0.0 : forceMoment.centerOfPressureY() - forceMoment.referenceY(),
				forceMoment == null ? 0.0 : forceMoment.centerOfPressureZ() - forceMoment.referenceZ(),
				ready,
				result.status(),
				result.message().isBlank() ? "none" : result.message(),
				result.runtimeInfo().isBlank() ? "none" : result.runtimeInfo()
		);
	}

	private static DroneConfig configForPreset(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unsupported static-airframe multi-axis preset: " + presetName);
		};
	}

	private static StaticAirframeMultiAxisRunExtrema extrema(List<StaticAirframeMultiAxisRunSummary> runs) {
		int invoked = 0;
		int succeeded = 0;
		int available = 0;
		int forceMoment = 0;
		int rawDisabled = 0;
		int testRuntime = 0;
		int maxGrid = 0;
		double maxForce = 0.0;
		double maxMoment = 0.0;
		double maxPressureCenter = 0.0;
		for (StaticAirframeMultiAxisRunSummary run : runs) {
			if (run.invoked()) {
				invoked++;
			}
			if (run.succeeded()) {
				succeeded++;
			}
			if (run.available()) {
				available++;
			}
			if (run.hasForceMoment()) {
				forceMoment++;
			}
			if (!run.requestFlowAtlas() && !run.hasFlowAtlas()) {
				rawDisabled++;
			}
			if ("test-runtime".equals(run.runtimeInfo())) {
				testRuntime++;
			}
			maxGrid = Math.max(maxGrid, run.gridCellCount());
			maxForce = Math.max(maxForce, run.forceMagnitudeNewtons());
			maxMoment = Math.max(maxMoment, run.momentMagnitudeNewtonMeters());
			maxPressureCenter = Math.max(maxPressureCenter, run.pressureCenterOffsetMeters());
		}
		return new StaticAirframeMultiAxisRunExtrema(
				runs.size(),
				invoked,
				succeeded,
				available,
				forceMoment,
				rawDisabled,
				maxGrid,
				maxForce,
				maxMoment,
				maxPressureCenter,
				testRuntime
		);
	}

	@FunctionalInterface
	public interface L2Runner {
		Aerodynamics4McL2Bridge.L2RunResult run(Aerodynamics4McL2Bridge.L2RequestSpec spec);
	}
}
