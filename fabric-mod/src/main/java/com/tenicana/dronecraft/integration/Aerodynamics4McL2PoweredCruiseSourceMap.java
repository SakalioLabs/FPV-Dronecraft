package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredCruiseSourceMap {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Source-Map-Packet";
	public static final String CAVEAT =
			"Forward-flight cruise source-map packet expands actuator-disk cruise pressure jumps into per-rotor force and moment source targets; use only as a future A4MC powered source-term request target.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int SOURCE_TERM_SAMPLE_COUNT = 16;
	public static final int SOURCE_MAP_METRIC_COUNT = 31;
	public static final int SOURCE_TERM_METRIC_COUNT = 18;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * SOURCE_MAP_METRIC_COUNT
			+ SOURCE_TERM_SAMPLE_COUNT * SOURCE_TERM_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSourceMap() {
	}

	public record PoweredCruiseSourceMap(
			String presetName,
			String spinState,
			int rotorCount,
			int sourceTermCount,
			double inletSpeedMetersPerSecond,
			double spinRatio,
			double totalThrustNewtons,
			double thrustToWeight,
			double totalOpenAreaSquareMeters,
			double meanOpenFraction,
			double meanPressureJumpPascals,
			double maxPressureJumpPascals,
			double netForceXNewtons,
			double netForceYNewtons,
			double netForceZNewtons,
			double netMomentXNewtonMeters,
			double netMomentYNewtonMeters,
			double netMomentZNewtonMeters,
			double netForceMagnitudeNewtons,
			double netMomentMagnitudeNewtonMeters,
			double centerOfThrustXBodyMeters,
			double centerOfThrustYBodyMeters,
			double centerOfThrustZBodyMeters,
			double centerOfThrustOffsetMeters,
			double totalMomentumPowerWatts,
			double idealInducedVelocityMetersPerSecond,
			double farWakeVelocityMetersPerSecond,
			double tipSpeedMetersPerSecond,
			double edgewiseAdvanceRatio,
			double representativeBladeReynoldsNumber,
			boolean poweredSourceApiRequired,
			boolean poweredSourceApiAvailable,
			List<Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm> sourceTerms
	) {
		public PoweredCruiseSourceMap {
			sourceTerms = List.copyOf(sourceTerms);
		}
	}

	public record PoweredCruiseSourceMapExtrema(
			double maxMeanPressureJumpPascals,
			double maxNetForceMagnitudeNewtons,
			double maxNetMomentMagnitudeNewtonMeters,
			double maxTotalMomentumPowerWatts,
			double maxEdgewiseAdvanceRatio,
			double maxRepresentativeBladeReynoldsNumber,
			double minMeanOpenFraction,
			int sourceMapCount,
			int sourceTermCount,
			int poweredSourceApiAvailableCount
	) {
	}

	public record PoweredCruiseSourceMapAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int sourceTermSampleCount,
			int sourceMapMetricCount,
			int sourceTermMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSourceMap> sourceMaps,
			PoweredCruiseSourceMapExtrema extrema
	) {
		public PoweredCruiseSourceMapAudit {
			sourceMaps = List.copyOf(sourceMaps);
		}
	}

	public static PoweredCruiseSourceMapAudit audit() {
		List<PoweredCruiseSourceMap> sourceMaps = List.of(
				sourceMap("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72),
				sourceMap("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64),
				sourceMap("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48),
				sourceMap("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80)
		);
		return new PoweredCruiseSourceMapAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				SOURCE_TERM_SAMPLE_COUNT,
				SOURCE_MAP_METRIC_COUNT,
				SOURCE_TERM_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				sourceMaps,
				extrema(sourceMaps)
		);
	}

	public static PoweredCruiseSourceMap sourceMap(String presetName, DroneConfig config, Vec3 inletVelocity, int steps) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Aerodynamics4McL2ActuatorDiskSourceMap.ActuatorDiskSourceMap map =
				Aerodynamics4McL2ActuatorDiskSourceMap.sourceMap(presetName, config, inletVelocity, steps, "cruise");
		Aerodynamics4McL2ActuatorDiskCalibration.ActuatorDiskLoad load =
				Aerodynamics4McL2ActuatorDiskCalibration.loads(presetName, config, inletVelocity, steps).stream()
						.filter(candidate -> "cruise".equals(candidate.spinState()))
						.findFirst()
						.orElseThrow();
		double inletSpeed = inletVelocity == null ? 0.0 : inletVelocity.length();
		return new PoweredCruiseSourceMap(
				map.presetName(),
				map.spinState(),
				map.rotorCount(),
				map.sourceTerms().size(),
				inletSpeed,
				load.spinRatio(),
				map.totalThrustNewtons(),
				map.thrustToWeight(),
				map.totalOpenAreaSquareMeters(),
				map.meanOpenFraction(),
				map.meanPressureJumpPascals(),
				map.maxPressureJumpPascals(),
				map.netForceBodyNewtons().x(),
				map.netForceBodyNewtons().y(),
				map.netForceBodyNewtons().z(),
				map.netMomentBodyNewtonMeters().x(),
				map.netMomentBodyNewtonMeters().y(),
				map.netMomentBodyNewtonMeters().z(),
				map.netForceMagnitudeNewtons(),
				map.netMomentMagnitudeNewtonMeters(),
				map.centerOfThrustBodyMeters().x(),
				map.centerOfThrustBodyMeters().y(),
				map.centerOfThrustBodyMeters().z(),
				map.centerOfThrustBodyMeters().length(),
				load.totalMomentumPowerWatts(),
				load.idealInducedVelocityMetersPerSecond(),
				load.farWakeVelocityMetersPerSecond(),
				load.tipSpeedMetersPerSecond(),
				load.edgewiseAdvanceRatio(),
				load.representativeBladeReynoldsNumber(),
				true,
				false,
				map.sourceTerms()
		);
	}

	private static PoweredCruiseSourceMapExtrema extrema(List<PoweredCruiseSourceMap> sourceMaps) {
		double maxMeanPressureJump = 0.0;
		double maxNetForce = 0.0;
		double maxNetMoment = 0.0;
		double maxPower = 0.0;
		double maxEdgewiseAdvance = 0.0;
		double maxReynolds = 0.0;
		double minOpenFraction = Double.POSITIVE_INFINITY;
		int sourceTermCount = 0;
		int poweredSourceApiAvailable = 0;
		for (PoweredCruiseSourceMap sourceMap : sourceMaps) {
			maxMeanPressureJump = Math.max(maxMeanPressureJump, sourceMap.meanPressureJumpPascals());
			maxNetForce = Math.max(maxNetForce, sourceMap.netForceMagnitudeNewtons());
			maxNetMoment = Math.max(maxNetMoment, sourceMap.netMomentMagnitudeNewtonMeters());
			maxPower = Math.max(maxPower, sourceMap.totalMomentumPowerWatts());
			maxEdgewiseAdvance = Math.max(maxEdgewiseAdvance, sourceMap.edgewiseAdvanceRatio());
			maxReynolds = Math.max(maxReynolds, sourceMap.representativeBladeReynoldsNumber());
			minOpenFraction = Math.min(minOpenFraction, sourceMap.meanOpenFraction());
			sourceTermCount += sourceMap.sourceTermCount();
			if (sourceMap.poweredSourceApiAvailable()) {
				poweredSourceApiAvailable++;
			}
		}
		return new PoweredCruiseSourceMapExtrema(
				maxMeanPressureJump,
				maxNetForce,
				maxNetMoment,
				maxPower,
				maxEdgewiseAdvance,
				maxReynolds,
				sourceMaps.isEmpty() ? 0.0 : minOpenFraction,
				sourceMaps.size(),
				sourceTermCount,
				poweredSourceApiAvailable
		);
	}
}
