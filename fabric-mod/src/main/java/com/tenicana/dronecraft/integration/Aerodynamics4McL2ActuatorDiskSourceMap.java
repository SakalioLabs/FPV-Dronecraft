package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2ActuatorDiskSourceMap {
	public static final String SOURCE_ID = "A4MC-L2-Actuator-Disk-Source-Map-Packet";
	public static final String CAVEAT =
			"Hover source-map packet converts actuator-disk pressure jumps into per-rotor force and moment targets; use as an A4MC L2 validation target before runtime powered-source integration.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int SOURCE_MAP_METRIC_COUNT = 20;
	public static final int SUMMARY_METRIC_ROW_COUNT = 8;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * SOURCE_MAP_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2ActuatorDiskSourceMap() {
	}

	public record RotorDiskSourceTerm(
			int rotorIndex,
			Vec3 centerBodyMeters,
			Vec3 thrustAxisBody,
			double diskRadiusMeters,
			double openAreaSquareMeters,
			double openFraction,
			double pressureJumpPascals,
			double thrustNewtons,
			Vec3 forceBodyNewtons,
			Vec3 momentBodyNewtonMeters
	) {
	}

	public record ActuatorDiskSourceMap(
			String presetName,
			String spinState,
			int rotorCount,
			double totalThrustNewtons,
			double thrustToWeight,
			double totalOpenAreaSquareMeters,
			double meanOpenFraction,
			double meanPressureJumpPascals,
			double maxPressureJumpPascals,
			Vec3 netForceBodyNewtons,
			Vec3 netMomentBodyNewtonMeters,
			double netForceMagnitudeNewtons,
			double netMomentMagnitudeNewtonMeters,
			Vec3 centerOfThrustBodyMeters,
			List<RotorDiskSourceTerm> sourceTerms
	) {
		public ActuatorDiskSourceMap {
			sourceTerms = List.copyOf(sourceTerms);
		}
	}

	public record ActuatorDiskSourceMapExtrema(
			double maxMeanPressureJumpPascals,
			double maxNetForceMagnitudeNewtons,
			double maxNetMomentMagnitudeNewtonMeters,
			double maxTotalOpenAreaSquareMeters,
			double minMeanOpenFraction,
			double maxCenterOfThrustOffsetMeters,
			int sourceMapCount,
			int sourceTermCount
	) {
	}

	public record ActuatorDiskSourceMapAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int sourceMapMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<ActuatorDiskSourceMap> sourceMaps,
			ActuatorDiskSourceMapExtrema extrema
	) {
		public ActuatorDiskSourceMapAudit {
			sourceMaps = List.copyOf(sourceMaps);
		}
	}

	public static ActuatorDiskSourceMapAudit audit() {
		List<ActuatorDiskSourceMap> maps = List.of(
				sourceMap("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72, "hover"),
				sourceMap("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64, "hover"),
				sourceMap("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48, "hover"),
				sourceMap("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80, "hover")
		);
		return new ActuatorDiskSourceMapAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				SOURCE_MAP_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				maps,
				extrema(maps)
		);
	}

	public static ActuatorDiskSourceMap sourceMap(
			String presetName,
			DroneConfig config,
			Vec3 inletVelocity,
			int steps,
			String spinState
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures =
				Aerodynamics4McL2RotorDiskAperture.apertures(config, probe);
		return sourceMap(presetName, config, apertures, spinState);
	}

	public static ActuatorDiskSourceMap sourceMap(
			String presetName,
			DroneConfig config,
			List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures,
			String spinState
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (apertures == null || apertures.size() != config.rotors().size()) {
			throw new IllegalArgumentException("apertures must match rotor count.");
		}
		List<RotorDiskSourceTerm> terms = new ArrayList<>(config.rotors().size());
		Vec3 netForce = Vec3.ZERO;
		Vec3 netMoment = Vec3.ZERO;
		Vec3 weightedCenter = Vec3.ZERO;
		double totalThrust = 0.0;
		double totalOpenArea = 0.0;
		double openFractionSum = 0.0;
		double pressureJumpSum = 0.0;
		double maxPressureJump = 0.0;
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture aperture = apertures.get(i);
			double spinRatio = Aerodynamics4McL2ActuatorDiskCalibration.spinRatioForState(config, rotor, spinState);
			double thrust = rotor.maxThrustNewtons() * spinRatio * spinRatio;
			double openArea = Math.max(1.0e-12, aperture.openAreaSquareMeters());
			double pressureJump = thrust / openArea;
			Vec3 force = aperture.thrustAxisBody().multiply(thrust);
			Vec3 moment = aperture.centerBodyMeters().cross(force);
			terms.add(new RotorDiskSourceTerm(
					i,
					aperture.centerBodyMeters(),
					aperture.thrustAxisBody(),
					aperture.radiusMeters(),
					openArea,
					aperture.openFraction(),
					pressureJump,
					thrust,
					force,
					moment
			));
			netForce = netForce.add(force);
			netMoment = netMoment.add(moment);
			weightedCenter = weightedCenter.add(aperture.centerBodyMeters().multiply(thrust));
			totalThrust += thrust;
			totalOpenArea += openArea;
			openFractionSum += aperture.openFraction();
			pressureJumpSum += pressureJump;
			maxPressureJump = Math.max(maxPressureJump, pressureJump);
		}
		double weight = config.massKg() * config.gravityMetersPerSecondSquared();
		Vec3 centerOfThrust = totalThrust <= 1.0e-12 ? Vec3.ZERO : weightedCenter.multiply(1.0 / totalThrust);
		String name = presetName == null || presetName.isBlank() ? "custom" : presetName;
		return new ActuatorDiskSourceMap(
				name,
				spinState == null || spinState.isBlank() ? "custom" : spinState.trim(),
				config.rotors().size(),
				totalThrust,
				ratio(totalThrust, weight),
				totalOpenArea,
				openFractionSum / config.rotors().size(),
				pressureJumpSum / config.rotors().size(),
				maxPressureJump,
				netForce,
				netMoment,
				netForce.length(),
				netMoment.length(),
				centerOfThrust,
				terms
		);
	}

	private static ActuatorDiskSourceMapExtrema extrema(List<ActuatorDiskSourceMap> maps) {
		double maxMeanPressureJump = 0.0;
		double maxNetForce = 0.0;
		double maxNetMoment = 0.0;
		double maxOpenArea = 0.0;
		double minOpenFraction = Double.POSITIVE_INFINITY;
		double maxCenterOffset = 0.0;
		int sourceTermCount = 0;
		for (ActuatorDiskSourceMap map : maps) {
			maxMeanPressureJump = Math.max(maxMeanPressureJump, map.meanPressureJumpPascals());
			maxNetForce = Math.max(maxNetForce, map.netForceMagnitudeNewtons());
			maxNetMoment = Math.max(maxNetMoment, map.netMomentMagnitudeNewtonMeters());
			maxOpenArea = Math.max(maxOpenArea, map.totalOpenAreaSquareMeters());
			minOpenFraction = Math.min(minOpenFraction, map.meanOpenFraction());
			maxCenterOffset = Math.max(maxCenterOffset, map.centerOfThrustBodyMeters().length());
			sourceTermCount += map.sourceTerms().size();
		}
		return new ActuatorDiskSourceMapExtrema(
				maxMeanPressureJump,
				maxNetForce,
				maxNetMoment,
				maxOpenArea,
				minOpenFraction,
				maxCenterOffset,
				maps.size(),
				sourceTermCount
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
