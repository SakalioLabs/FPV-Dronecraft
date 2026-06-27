package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredCruiseExperimentPlan {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Experiment-Plan";
	public static final String CAVEAT =
			"Experiment plan pairs forward-flight static-airframe L2 force/moment requests with cruise actuator-disk source targets; keep offline until A4MC exposes powered source-term injection for edgewise rotor validation.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int EXPERIMENT_METRIC_COUNT = 39;
	public static final int SUMMARY_METRIC_ROW_COUNT = 11;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * EXPERIMENT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseExperimentPlan() {
	}

	public record PoweredCruiseExperiment(
			String presetName,
			int rotorCount,
			int sourceTermCount,
			int nx,
			int ny,
			int nz,
			int gridCellCount,
			double cellSizeMeters,
			int steps,
			double inletVxMetersPerSecond,
			double inletVyMetersPerSecond,
			double inletVzMetersPerSecond,
			double inletSpeedMetersPerSecond,
			String spinState,
			double spinRatio,
			boolean baselineForceMomentRequest,
			boolean baselineFlowAtlasOutput,
			boolean poweredSourceApiRequired,
			boolean poweredSourceApiAvailable,
			boolean actuatorDiskRepresentationRequired,
			boolean porousOrBodyForceSourceApiAvailable,
			double meanPressureJumpPascals,
			double maxPressureJumpPascals,
			double thrustToWeight,
			double targetForceXNewtons,
			double targetForceYNewtons,
			double targetForceZNewtons,
			double targetMomentXNewtonMeters,
			double targetMomentYNewtonMeters,
			double targetMomentZNewtonMeters,
			double targetForceMagnitudeNewtons,
			double targetMomentMagnitudeNewtonMeters,
			double totalMomentumPowerWatts,
			double idealInducedVelocityMetersPerSecond,
			double farWakeVelocityMetersPerSecond,
			double tipSpeedMetersPerSecond,
			double edgewiseAdvanceRatio,
			double axialInletOverInducedVelocity,
			double representativeBladeReynoldsNumber,
			String runtimeInfo
	) {
	}

	public record PoweredCruiseExperimentExtrema(
			int maxGridCellCount,
			double maxInletSpeedMetersPerSecond,
			double maxMeanPressureJumpPascals,
			double maxTargetForceMagnitudeNewtons,
			double maxTargetMomentMagnitudeNewtonMeters,
			double maxTotalMomentumPowerWatts,
			double maxEdgewiseAdvanceRatio,
			double maxAxialInletOverInducedVelocity,
			double maxRepresentativeBladeReynoldsNumber,
			int experimentCount,
			int sourceTermCount
	) {
	}

	public record PoweredCruiseExperimentAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int experimentMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseExperiment> experiments,
			PoweredCruiseExperimentExtrema extrema
	) {
		public PoweredCruiseExperimentAudit {
			experiments = List.copyOf(experiments);
		}
	}

	public static PoweredCruiseExperimentAudit audit() {
		List<PoweredCruiseExperiment> experiments = List.of(
				experiment("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72),
				experiment("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64),
				experiment("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48),
				experiment("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80)
		);
		return new PoweredCruiseExperimentAudit(
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

	public static PoweredCruiseExperiment experiment(String presetName, DroneConfig config, Vec3 inletVelocity, int steps) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures =
				Aerodynamics4McL2RotorDiskAperture.apertures(config, probe);
		Aerodynamics4McL2ActuatorDiskSourceMap.ActuatorDiskSourceMap sourceMap =
				Aerodynamics4McL2ActuatorDiskSourceMap.sourceMap(presetName, config, apertures, "cruise");
		Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad cruiseLoad =
				Aerodynamics4McL2ActuatorDiskCalibration.load(presetName, config, apertures, "cruise");
		Aerodynamics4McL2Bridge.L2RequestSpec request = probe.requestSpec();
		Vec3 inlet = new Vec3(request.inletVx(), request.inletVy(), request.inletVz());
		return new PoweredCruiseExperiment(
				presetName == null || presetName.isBlank() ? "custom" : presetName,
				sourceMap.rotorCount(),
				sourceMap.sourceTerms().size(),
				probe.nx(),
				probe.ny(),
				probe.nz(),
				probe.nx() * probe.ny() * probe.nz(),
				probe.cellSizeMeters(),
				request.steps(),
				request.inletVx(),
				request.inletVy(),
				request.inletVz(),
				inlet.length(),
				sourceMap.spinState(),
				cruiseLoad.spinRatio(),
				request.computeForceMoment(),
				request.outputFlowAtlas(),
				true,
				false,
				true,
				false,
				sourceMap.meanPressureJumpPascals(),
				sourceMap.maxPressureJumpPascals(),
				sourceMap.thrustToWeight(),
				sourceMap.netForceBodyNewtons().x(),
				sourceMap.netForceBodyNewtons().y(),
				sourceMap.netForceBodyNewtons().z(),
				sourceMap.netMomentBodyNewtonMeters().x(),
				sourceMap.netMomentBodyNewtonMeters().y(),
				sourceMap.netMomentBodyNewtonMeters().z(),
				sourceMap.netForceMagnitudeNewtons(),
				sourceMap.netMomentMagnitudeNewtonMeters(),
				cruiseLoad.totalMomentumPowerWatts(),
				cruiseLoad.idealInducedVelocityMetersPerSecond(),
				cruiseLoad.farWakeVelocityMetersPerSecond(),
				cruiseLoad.tipSpeedMetersPerSecond(),
				cruiseLoad.edgewiseAdvanceRatio(),
				cruiseLoad.axialInletOverInducedVelocity(),
				cruiseLoad.representativeBladeReynoldsNumber(),
				"plan-only-powered-source-api-unavailable"
		);
	}

	private static PoweredCruiseExperimentExtrema extrema(List<PoweredCruiseExperiment> experiments) {
		int maxGridCells = 0;
		double maxInlet = 0.0;
		double maxMeanPressureJump = 0.0;
		double maxForce = 0.0;
		double maxMoment = 0.0;
		double maxPower = 0.0;
		double maxEdgewiseAdvance = 0.0;
		double maxAxialInletRatio = 0.0;
		double maxReynolds = 0.0;
		int sourceTerms = 0;
		for (PoweredCruiseExperiment experiment : experiments) {
			maxGridCells = Math.max(maxGridCells, experiment.gridCellCount());
			maxInlet = Math.max(maxInlet, experiment.inletSpeedMetersPerSecond());
			maxMeanPressureJump = Math.max(maxMeanPressureJump, experiment.meanPressureJumpPascals());
			maxForce = Math.max(maxForce, experiment.targetForceMagnitudeNewtons());
			maxMoment = Math.max(maxMoment, experiment.targetMomentMagnitudeNewtonMeters());
			maxPower = Math.max(maxPower, experiment.totalMomentumPowerWatts());
			maxEdgewiseAdvance = Math.max(maxEdgewiseAdvance, experiment.edgewiseAdvanceRatio());
			maxAxialInletRatio = Math.max(maxAxialInletRatio, experiment.axialInletOverInducedVelocity());
			maxReynolds = Math.max(maxReynolds, experiment.representativeBladeReynoldsNumber());
			sourceTerms += experiment.sourceTermCount();
		}
		return new PoweredCruiseExperimentExtrema(
				maxGridCells,
				maxInlet,
				maxMeanPressureJump,
				maxForce,
				maxMoment,
				maxPower,
				maxEdgewiseAdvance,
				maxAxialInletRatio,
				maxReynolds,
				experiments.size(),
				sourceTerms
		);
	}
}
