package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2StaticAirframeRunMatrix {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Run-Matrix";
	public static final String CAVEAT =
			"Static-airframe L2 run matrix records compact force/moment summaries only; raw CFD atlas output stays disabled and missing A4MC runtime remains unavailable.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int RUN_METRIC_COUNT = 28;
	public static final int SUMMARY_METRIC_ROW_COUNT = 9;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * RUN_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeRunMatrix() {
	}

	public record StaticAirframeRunSummary(
			String presetName,
			int nx,
			int ny,
			int nz,
			int gridCellCount,
			double cellSizeMeters,
			int steps,
			double inletSpeedMetersPerSecond,
			double solidFraction,
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
			String status,
			String message,
			String runtimeInfo
	) {
	}

	public record StaticAirframeRunExtrema(
			int runSummaryCount,
			int invokedCount,
			int succeededCount,
			int availableCount,
			int forceMomentCount,
			int maxGridCellCount,
			double maxForceMagnitudeNewtons,
			double maxMomentMagnitudeNewtonMeters,
			double maxPressureCenterOffsetMeters
	) {
	}

	public record StaticAirframeRunMatrixAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int runMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<StaticAirframeRunSummary> runs,
			StaticAirframeRunExtrema extrema
	) {
		public StaticAirframeRunMatrixAudit {
			runs = List.copyOf(runs);
		}
	}

	public static StaticAirframeRunMatrixAudit audit() {
		return audit(Aerodynamics4McL2Bridge::run);
	}

	public static StaticAirframeRunMatrixAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(spec -> Aerodynamics4McL2Bridge.run(loader, spec));
	}

	public static StaticAirframeRunSummary summary(String presetName, DroneConfig config, Vec3 inletVelocity, int steps) {
		return summary(presetName, config, inletVelocity, steps, Aerodynamics4McL2Bridge::run);
	}

	private static StaticAirframeRunMatrixAudit audit(L2Runner runner) {
		List<StaticAirframeRunSummary> runs = List.of(
				summary("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72, runner),
				summary("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64, runner),
				summary("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48, runner),
				summary("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80, runner)
		);
		return new StaticAirframeRunMatrixAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				RUN_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				runs,
				extrema(runs)
		);
	}

	private static StaticAirframeRunSummary summary(
			String presetName,
			DroneConfig config,
			Vec3 inletVelocity,
			int steps,
			L2Runner runner
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		Aerodynamics4McL2Bridge.L2RequestSpec request = probe.requestSpec();
		int gridCellCount = probe.nx() * probe.ny() * probe.nz();
		Aerodynamics4McL2Bridge.L2RunResult result = runner.run(request);
		Aerodynamics4McL2Bridge.L2ForceMomentSample forceMoment = result.forceMoment();
		return new StaticAirframeRunSummary(
				presetName == null || presetName.isBlank() ? "custom" : presetName,
				probe.nx(),
				probe.ny(),
				probe.nz(),
				gridCellCount,
				probe.cellSizeMeters(),
				request.steps(),
				new Vec3(request.inletVx(), request.inletVy(), request.inletVz()).length(),
				probe.solidCellCount() / (double) Math.max(1, gridCellCount),
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
				result.status(),
				result.message().isBlank() ? "none" : result.message(),
				result.runtimeInfo().isBlank() ? "none" : result.runtimeInfo()
		);
	}

	private static StaticAirframeRunExtrema extrema(List<StaticAirframeRunSummary> runs) {
		int invoked = 0;
		int succeeded = 0;
		int available = 0;
		int forceMoment = 0;
		int maxGrid = 0;
		double maxForce = 0.0;
		double maxMoment = 0.0;
		double maxPressureCenter = 0.0;
		for (StaticAirframeRunSummary run : runs) {
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
			maxGrid = Math.max(maxGrid, run.gridCellCount());
			maxForce = Math.max(maxForce, run.forceMagnitudeNewtons());
			maxMoment = Math.max(maxMoment, run.momentMagnitudeNewtonMeters());
			maxPressureCenter = Math.max(maxPressureCenter, run.pressureCenterOffsetMeters());
		}
		return new StaticAirframeRunExtrema(
				runs.size(),
				invoked,
				succeeded,
				available,
				forceMoment,
				maxGrid,
				maxForce,
				maxMoment,
				maxPressureCenter
		);
	}

	@FunctionalInterface
	private interface L2Runner {
		Aerodynamics4McL2Bridge.L2RunResult run(Aerodynamics4McL2Bridge.L2RequestSpec spec);
	}
}
