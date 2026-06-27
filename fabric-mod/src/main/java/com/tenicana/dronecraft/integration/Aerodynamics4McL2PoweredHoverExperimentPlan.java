package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredHoverExperimentPlan {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Experiment-Plan";
	public static final String CAVEAT =
			"Experiment plan pairs static-airframe L2 force/moment requests with hover actuator-disk force targets; run only as offline validation until A4MC exposes a powered source-term API.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int EXPERIMENT_METRIC_COUNT = 22;
	public static final int SUMMARY_METRIC_ROW_COUNT = 8;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * EXPERIMENT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverExperimentPlan() {
	}

	public record PoweredHoverExperiment(
			String presetName,
			int rotorCount,
			int sourceTermCount,
			int nx,
			int ny,
			int nz,
			int gridCellCount,
			double cellSizeMeters,
			int steps,
			double inletSpeedMetersPerSecond,
			boolean baselineForceMomentRequest,
			boolean baselineFlowAtlasOutput,
			boolean poweredSourceApiRequired,
			boolean poweredSourceApiAvailable,
			double meanPressureJumpPascals,
			double maxPressureJumpPascals,
			double targetForceXNewtons,
			double targetForceYNewtons,
			double targetForceZNewtons,
			double targetMomentXNewtonMeters,
			double targetMomentYNewtonMeters,
			double targetMomentZNewtonMeters,
			double targetForceMagnitudeNewtons,
			double targetMomentMagnitudeNewtonMeters,
			double centerOfThrustOffsetMeters
	) {
	}

	public record PoweredHoverExperimentExtrema(
			int maxGridCellCount,
			double maxInletSpeedMetersPerSecond,
			double maxMeanPressureJumpPascals,
			double maxTargetForceMagnitudeNewtons,
			double maxTargetMomentMagnitudeNewtonMeters,
			double maxCenterOfThrustOffsetMeters,
			int experimentCount,
			int sourceTermCount
	) {
	}

	public record PoweredHoverExperimentAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int experimentMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverExperiment> experiments,
			PoweredHoverExperimentExtrema extrema
	) {
		public PoweredHoverExperimentAudit {
			experiments = List.copyOf(experiments);
		}
	}

	public static PoweredHoverExperimentAudit audit() {
		List<PoweredHoverExperiment> experiments = List.of(
				experiment("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72),
				experiment("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64),
				experiment("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48),
				experiment("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80)
		);
		return new PoweredHoverExperimentAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				EXPERIMENT_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				experiments,
				extrema(experiments)
		);
	}

	public static PoweredHoverExperiment experiment(String presetName, DroneConfig config, Vec3 inletVelocity, int steps) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		Aerodynamics4McL2ActuatorDiskSourceMap.ActuatorDiskSourceMap sourceMap =
				Aerodynamics4McL2ActuatorDiskSourceMap.sourceMap(presetName, config, inletVelocity, steps, "hover");
		Aerodynamics4McL2Bridge.L2RequestSpec request = probe.requestSpec();
		double inletSpeed = new Vec3(request.inletVx(), request.inletVy(), request.inletVz()).length();
		return new PoweredHoverExperiment(
				presetName == null || presetName.isBlank() ? "custom" : presetName,
				sourceMap.rotorCount(),
				sourceMap.sourceTerms().size(),
				probe.nx(),
				probe.ny(),
				probe.nz(),
				probe.nx() * probe.ny() * probe.nz(),
				probe.cellSizeMeters(),
				request.steps(),
				inletSpeed,
				request.computeForceMoment(),
				request.outputFlowAtlas(),
				true,
				false,
				sourceMap.meanPressureJumpPascals(),
				sourceMap.maxPressureJumpPascals(),
				sourceMap.netForceBodyNewtons().x(),
				sourceMap.netForceBodyNewtons().y(),
				sourceMap.netForceBodyNewtons().z(),
				sourceMap.netMomentBodyNewtonMeters().x(),
				sourceMap.netMomentBodyNewtonMeters().y(),
				sourceMap.netMomentBodyNewtonMeters().z(),
				sourceMap.netForceMagnitudeNewtons(),
				sourceMap.netMomentMagnitudeNewtonMeters(),
				sourceMap.centerOfThrustBodyMeters().length()
		);
	}

	private static PoweredHoverExperimentExtrema extrema(List<PoweredHoverExperiment> experiments) {
		int maxGridCells = 0;
		double maxInlet = 0.0;
		double maxMeanPressureJump = 0.0;
		double maxForce = 0.0;
		double maxMoment = 0.0;
		double maxCenterOffset = 0.0;
		int sourceTerms = 0;
		for (PoweredHoverExperiment experiment : experiments) {
			maxGridCells = Math.max(maxGridCells, experiment.gridCellCount());
			maxInlet = Math.max(maxInlet, experiment.inletSpeedMetersPerSecond());
			maxMeanPressureJump = Math.max(maxMeanPressureJump, experiment.meanPressureJumpPascals());
			maxForce = Math.max(maxForce, experiment.targetForceMagnitudeNewtons());
			maxMoment = Math.max(maxMoment, experiment.targetMomentMagnitudeNewtonMeters());
			maxCenterOffset = Math.max(maxCenterOffset, experiment.centerOfThrustOffsetMeters());
			sourceTerms += experiment.sourceTermCount();
		}
		return new PoweredHoverExperimentExtrema(
				maxGridCells,
				maxInlet,
				maxMeanPressureJump,
				maxForce,
				maxMoment,
				maxCenterOffset,
				experiments.size(),
				sourceTerms
		);
	}
}
